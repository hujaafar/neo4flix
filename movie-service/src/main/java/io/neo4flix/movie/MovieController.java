package io.neo4flix.movie;

import io.neo4flix.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final Graph graph;
    private final ServiceClient services;
    private final String recommendations;
    public static final String PROJECTION =
        " m {.*, averageRating:averageRating, ratingCount:ratingCount} AS movie";

    public MovieController(
        Graph graph,
        ServiceClient services,
        @Value("${app.recommendation-url}") String recommendations
    ) {
        this.graph = graph;
        this.services = services;
        this.recommendations = recommendations;
    }

    public record MovieInput(
        @NotBlank @Size(max = 180) String title,
        @NotNull LocalDate releaseDate,
        @NotEmpty @Size(max = 8) List<@NotBlank @Size(max = 40) String> genres,
        @NotBlank @Size(max = 2500) String overview,
        @NotBlank @Size(max = 120) String director,
        @Min(1) @Max(600) int runtime,
        @NotBlank @Pattern(regexp = "[a-z-]+") @Size(max = 50) String artwork
    ) {}

    @GetMapping
    public List<?> list(
        @RequestParam(defaultValue = "") @Size(max = 180) String q,
        @RequestParam(defaultValue = "") @Size(max = 40) String genre,
        @RequestParam(required = false) @Min(1888) @Max(2200) Integer year,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size
    ) {
        if (from != null && to != null && from.isAfter(to)) throw new ApiException(
            400,
            "Start date must precede end date"
        );
        var p = new HashMap<String, Object>();
        p.put("q", q.toLowerCase(Locale.ROOT));
        p.put("genre", genre);
        p.put("year", year);
        p.put("from", from == null ? null : from.toString());
        p.put("to", to == null ? null : to.toString());
        p.put("skip", (long) page * size);
        p.put("size", size);
        return graph
            .list(
                """
                MATCH (m:Movie) WHERE (toLower(m.title) CONTAINS $q OR any(g IN m.genres WHERE toLower(g) CONTAINS $q) OR toString(m.year)=$q)
                AND ($genre='' OR $genre IN m.genres) AND ($year IS NULL OR m.year=$year)
                AND ($from IS NULL OR m.releaseDate >= $from) AND ($to IS NULL OR m.releaseDate <= $to)
                OPTIONAL MATCH (:User)-[r:RATED]->(m)
                WITH m,coalesce(avg(r.score),0.0) AS averageRating,count(r) AS ratingCount
                RETURN """ +
                    PROJECTION +
                    " ORDER BY movie.title SKIP $skip LIMIT $size",
                p
            )
            .stream()
            .map(x -> x.get("movie"))
            .toList();
    }

    @GetMapping("/genres")
    public List<?> genres() {
        return graph
            .list(
                "MATCH (m:Movie) UNWIND m.genres AS genre RETURN DISTINCT genre ORDER BY genre",
                Map.of()
            )
            .stream()
            .map(x -> x.get("genre"))
            .toList();
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable String id) {
        return graph
            .one(
                "MATCH (m:Movie {id:$id}) OPTIONAL MATCH (:User)-[r:RATED]->(m) WITH m,coalesce(avg(r.score),0.0) AS averageRating,count(r) AS ratingCount RETURN " +
                    PROJECTION,
                Map.of("id", id)
            )
            .get("movie");
    }

    @GetMapping("/{id}/related")
    public List<?> related(@PathVariable String id) {
        return graph
            .list(
                """
                MATCH (source:Movie {id:$id})-[:IN_GENRE]->(g:Genre)<-[:IN_GENRE]-(m:Movie) WHERE source<>m
                WITH m,count(DISTINCT g) AS overlap OPTIONAL MATCH (:User)-[r:RATED]->(m)
                WITH m,overlap,coalesce(avg(r.score),0.0) AS averageRating,count(r) AS ratingCount
                RETURN """ +
                    PROJECTION +
                    ", overlap ORDER BY overlap DESC, movie.averageRating DESC, movie.title LIMIT 6",
                Map.of("id", id)
            )
            .stream()
            .map(x -> x.get("movie"))
            .toList();
    }

    @GetMapping("/recommendations")
    public Object recommendations(@AuthenticationPrincipal Jwt jwt) {
        return services.get(recommendations + "/api/recommendations", jwt.getTokenValue());
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Object create(@Valid @RequestBody MovieInput input) {
        return save(UUID.randomUUID().toString(), input, false);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Object update(@PathVariable String id, @Valid @RequestBody MovieInput input) {
        return save(id, input, true);
    }

    private Object save(String id, MovieInput input, boolean exists) {
        var props = new HashMap<String, Object>();
        props.put("title", input.title().strip());
        props.put("releaseDate", input.releaseDate().toString());
        props.put("year", input.releaseDate().getYear());
        props.put("genres", input.genres().stream().distinct().toList());
        props.put("overview", input.overview());
        props.put("director", input.director());
        props.put("runtime", input.runtime());
        props.put("artwork", input.artwork());
        graph.write(tx -> {
            if (
                exists && !tx.run("MATCH (m:Movie {id:$id}) RETURN m", Map.of("id", id)).hasNext()
            ) throw new ApiException(404, "Movie not found");
            tx.run(
                "MERGE (m:Movie {id:$id}) SET m += $props WITH m OPTIONAL MATCH (m)-[old:IN_GENRE]->() DELETE old",
                Map.of("id", id, "props", props)
            ).consume();
            tx.run(
                "MATCH (m:Movie {id:$id}) UNWIND m.genres AS name MERGE (g:Genre {name:name}) MERGE (m)-[:IN_GENRE]->(g)",
                Map.of("id", id)
            ).consume();
            return null;
        });
        return detail(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable String id) {
        graph.write(tx -> {
            var r = tx
                .run(
                    "MATCH (m:Movie {id:$id}) DETACH DELETE m RETURN count(m) AS n",
                    Map.of("id", id)
                )
                .single();
            if (r.get("n").asLong() == 0) throw new ApiException(404, "Movie not found");
            return null;
        });
    }
}
