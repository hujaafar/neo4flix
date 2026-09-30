package io.neo4flix.movie;

import io.neo4flix.common.ApiException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/** Only the fixed TMDB HTTPS origin receives the private read token. */
@Component
public class TmdbClient {

    private final String token;
    private final RestClient client;

    @org.springframework.beans.factory.annotation.Autowired
    public TmdbClient(@Value("${app.tmdb-token:}") String token) {
        this(token, "https://api.themoviedb.org/3");
    }

    TmdbClient(String token, String baseUrl) {
        this.token = token.strip();
        var factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
        );
        factory.setReadTimeout(Duration.ofSeconds(12));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public boolean configured() {
        return !token.isBlank();
    }

    public record Film(
        long tmdbId,
        String title,
        LocalDate releaseDate,
        List<String> genres,
        String overview,
        String director,
        int runtime,
        String posterPath,
        String backdropPath,
        double tmdbRating,
        long tmdbVoteCount
    ) {
        Map<String, Object> properties() {
            var p = new LinkedHashMap<String, Object>();
            p.put("id", "tmdb-" + tmdbId);
            p.put("tmdbId", tmdbId);
            p.put("source", "TMDB");
            p.put("title", title);
            p.put("releaseDate", releaseDate.toString());
            p.put("year", releaseDate.getYear());
            p.put("genres", genres);
            p.put("overview", overview);
            p.put("director", director);
            p.put("runtime", runtime);
            p.put("artwork", "default");
            p.put("posterPath", posterPath);
            p.put("backdropPath", backdropPath);
            p.put("tmdbRating", tmdbRating);
            p.put("tmdbVoteCount", tmdbVoteCount);
            return p;
        }
    }

    public List<Film> catalogue(String selection, int count) {
        if (!configured()) throw new ApiException(
            503,
            "Configure the TMDB read access token first."
        );
        if (
            !Set.of("popular", "top_rated").contains(selection) || count < 1 || count > 50
        ) throw new ApiException(400, "Choose popular or top_rated and 1 to 50 films.");
        var films = new LinkedHashMap<Long, Film>();
        // Bounded calls; no complete-database scraping or unbounded retry loop.
        for (int page = 1; page <= 5 && films.size() < count; page++) {
            JsonNode list = get("/movie/" + selection + "?language=en-US&page=" + page);
            if (!list.path("results").isArray()) throw invalid();
            for (JsonNode item : list.path("results")) {
                long id = item.path("id").asLong();
                if (id <= 0) throw invalid();
                if (item.path("adult").asBoolean() || films.containsKey(id)) continue;
                JsonNode detail = get(
                    "/movie/" + id + "?language=en-US&append_to_response=credits"
                );
                if (detail.path("id").asLong() != id) throw invalid();
                Film film = parse(detail);
                if (film != null) films.put(id, film);
                if (films.size() == count) break;
            }
        }
        if (films.size() != count) throw new ApiException(
            502,
            "TMDB did not return enough complete films. The current catalogue was kept."
        );
        return List.copyOf(films.values());
    }

    private JsonNode get(String path) {
        try {
            JsonNode result = client
                .get()
                .uri(path)
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .body(JsonNode.class);
            if (result == null || !result.isObject()) throw invalid();
            return result;
        } catch (RestClientResponseException e) {
            if (
                e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403
            ) throw new ApiException(
                502,
                "TMDB rejected the token. Check the API Read Access Token."
            );
            if (e.getStatusCode().value() == 429) throw new ApiException(
                503,
                "TMDB is limiting requests. Wait before trying again."
            );
            throw new ApiException(
                502,
                "TMDB could not provide this catalogue. The current films were kept."
            );
        } catch (RestClientException e) {
            // Do not expose provider payloads, request headers, or internal exception details.
            throw new ApiException(502, "Cannot reach TMDB securely. The current films were kept.");
        }
    }

    static Film parse(JsonNode data) {
        if (data.path("adult").asBoolean()) return null;
        String date = data.path("release_date").asText("");
        int runtime = data.path("runtime").asInt();
        if (date.isBlank() || runtime < 1 || runtime > 600) return null;
        LocalDate released;
        try {
            released = LocalDate.parse(date);
        } catch (RuntimeException e) {
            throw invalid();
        }
        long id = data.path("id").asLong();
        String title = text(data.path("title").asText(""), 180);
        String overview = text(data.path("overview").asText(""), 2500);
        if (
            id <= 0 || title.isBlank() || overview.isBlank() || !data.path("genres").isArray()
        ) return null;
        var genres = new TreeSet<String>();
        for (JsonNode g : data.path("genres")) {
            String name = text(g.path("name").asText(""), 40);
            if (!name.isBlank()) genres.add(name);
        }
        if (genres.isEmpty()) return null;
        var directors = new LinkedHashSet<String>();
        JsonNode crew = data.path("credits").path("crew");
        if (!crew.isArray()) throw invalid();
        for (JsonNode member : crew)
            if ("Director".equals(member.path("job").asText())) {
                String name = member.path("name").asText("").strip();
                if (!name.isBlank()) directors.add(name);
            }
        double rating = data.path("vote_average").asDouble();
        long votes = data.path("vote_count").asLong();
        if (!Double.isFinite(rating) || rating < 0 || rating > 10 || votes < 0) throw invalid();
        return new Film(
            id,
            title,
            released,
            genres.stream().limit(8).toList(),
            overview,
            text(directors.isEmpty() ? "Not listed" : String.join(", ", directors), 120),
            runtime,
            imagePath(data.path("poster_path").asText("")),
            imagePath(data.path("backdrop_path").asText("")),
            rating,
            votes
        );
    }

    private static String text(String value, int max) {
        String result = value.strip();
        return result.length() <= max ? result : result.substring(0, max);
    }

    static String imagePath(String path) {
        if (path.isEmpty()) return "";
        if (!path.matches("/[A-Za-z0-9_-]{1,100}\\.(jpg|png|webp)")) throw invalid();
        return path;
    }

    private static ApiException invalid() {
        return new ApiException(
            502,
            "TMDB returned invalid movie data. The current catalogue was kept."
        );
    }
}
