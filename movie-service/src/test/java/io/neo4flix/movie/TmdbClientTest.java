package io.neo4flix.movie;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.*;
import io.neo4flix.common.ApiException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;

class TmdbClientTest {

    HttpServer provider;
    String base;
    AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void open() throws Exception {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + provider.getAddress().getPort();
    }

    @AfterEach
    void close() {
        provider.stop(0);
    }

    static String film(long id) {
        return """
        {"id":%d,"title":"Fixture film","adult":false,"release_date":"2020-06-01","runtime":120,
         "overview":"A film synopsis.","genres":[{"name":"Drama"}],"poster_path":"/poster.jpg","backdrop_path":null,
         "credits":{"crew":[{"name":"A Director","job":"Director"}]},"vote_average":8.4,"vote_count":1000}
        """.formatted(id);
    }

    void response(HttpExchange e, int status, String body) throws java.io.IOException {
        requests.incrementAndGet();
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", "application/json");
        e.sendResponseHeaders(status, bytes.length);
        e.getResponseBody().write(bytes);
        e.close();
    }

    @Test
    void fetchesDetailsAndKeepsCommunityScoreSeparateFromLocalRatings() {
        provider.createContext("/movie/top_rated", e ->
            response(
                e,
                200,
                "{\"results\":[{\"id\":7},{\"id\":7},{\"id\":8,\"adult\":true},{\"id\":9}]}"
            )
        );
        provider.createContext("/movie/7", e -> {
            assertEquals("Bearer fixture-token", e.getRequestHeaders().getFirst("Authorization"));
            response(e, 200, film(7));
        });
        provider.createContext("/movie/9", e -> response(e, 200, film(9)));
        provider.start();
        var films = new TmdbClient("fixture-token", base).catalogue("top_rated", 2);
        assertEquals(2, films.size());
        assertEquals(3, requests.get());
        var props = films.get(0).properties();
        assertEquals("tmdb-7", props.get("id"));
        assertEquals(8.4, props.get("tmdbRating"));
        assertFalse(props.containsKey("averageRating"));
        assertEquals("A Director", props.get("director"));
        assertEquals("", props.get("backdropPath"));
    }

    @Test
    void partialFetchFailureDoesNotReturnPartialCatalogueOrExposeToken() {
        provider.createContext("/movie/popular", e ->
            response(e, 200, "{\"results\":[{\"id\":7},{\"id\":9}]}")
        );
        provider.createContext("/movie/7", e -> response(e, 200, film(7)));
        provider.createContext("/movie/9", e -> response(e, 401, "{\"error\":\"fixture-token\"}"));
        provider.start();
        var e = assertThrows(ApiException.class, () ->
            new TmdbClient("fixture-token", base).catalogue("popular", 2)
        );
        assertEquals(502, e.status);
        assertFalse(e.getMessage().contains("fixture-token"));
    }

    @Test
    void rateLimitsReturnRetryableFailure() {
        provider.createContext("/movie/popular", e -> response(e, 429, "{}"));
        provider.start();
        assertEquals(
            503,
            assertThrows(ApiException.class, () ->
                new TmdbClient("fixture", base).catalogue("popular", 1)
            ).status
        );
    }

    @Test
    void doesNotFollowProviderRedirectWithPrivateCredential() {
        provider.createContext("/movie/popular", e -> {
            e.getResponseHeaders().set("Location", base + "/stolen");
            response(e, 302, "{}");
        });
        provider.createContext("/stolen", e -> response(e, 200, "{}"));
        provider.start();
        assertThrows(ApiException.class, () ->
            new TmdbClient("fixture", base).catalogue("popular", 1)
        );
        assertEquals(1, requests.get());
    }

    @Test
    void missingConfigurationAndInvalidCountsNeverMakeRequests() {
        provider.start();
        assertEquals(
            503,
            assertThrows(ApiException.class, () ->
                new TmdbClient("", base).catalogue("popular", 1)
            ).status
        );
        var c = new TmdbClient("fixture", base);
        assertEquals(
            400,
            assertThrows(ApiException.class, () -> c.catalogue("../../secret", 1)).status
        );
        assertEquals(
            400,
            assertThrows(ApiException.class, () -> c.catalogue("popular", 51)).status
        );
        assertEquals(0, requests.get());
    }

    @Test
    void refusesInvalidImagePathsAndSkipsIncompleteOrAdultFilms() {
        var mapper = JsonMapper.builder().build();
        assertThrows(ApiException.class, () ->
            TmdbClient.imagePath("https://evil.example/poster.jpg")
        );
        assertThrows(ApiException.class, () -> TmdbClient.imagePath("/../poster.jpg"));
        assertThrows(ApiException.class, () ->
            TmdbClient.parse(mapper.readTree(film(7).replace("/poster.jpg", "/poster.svg")))
        );
        assertNull(
            TmdbClient.parse(mapper.readTree(film(7).replace("\"adult\":false", "\"adult\":true")))
        );
        assertNull(
            TmdbClient.parse(mapper.readTree(film(7).replace("\"runtime\":120", "\"runtime\":0")))
        );
    }
}
