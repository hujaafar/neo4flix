package io.neo4flix.rating;

import io.neo4flix.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ratings")
public class RatingController {

    private final Graph graph;

    public RatingController(Graph graph) {
        this.graph = graph;
    }

    public record RatingInput(@Min(1) @Max(5) int score, @Size(max = 1000) String review) {}

    @GetMapping("/me")
    public List<?> history(@AuthenticationPrincipal Jwt jwt) {
        return graph
            .list(
                "MATCH (:User {id:$user})-[r:RATED]->(m:Movie) RETURN r {.*, movie:m{.*}} AS rating ORDER BY r.updatedAt DESC",
                Map.of("user", jwt.getSubject())
            )
            .stream()
            .map(x -> x.get("rating"))
            .toList();
    }

    @GetMapping("/me/{movieId}")
    public Object get(@AuthenticationPrincipal Jwt jwt, @PathVariable String movieId) {
        return graph
            .one(
                "MATCH (:User {id:$user})-[r:RATED]->(m:Movie {id:$movie}) RETURN r{.*,movie:m{.*}} AS rating",
                Map.of("user", jwt.getSubject(), "movie", movieId)
            )
            .get("rating");
    }

    @RequestMapping(path = "/me/{movieId}", method = { RequestMethod.PUT, RequestMethod.POST })
    public Object rate(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String movieId,
        @Valid @RequestBody RatingInput input
    ) {
        var p = Map.<String, Object>of(
            "user",
            jwt.getSubject(),
            "movie",
            movieId,
            "score",
            input.score(),
            "review",
            input.review() == null ? "" : input.review(),
            "id",
            UUID.randomUUID().toString()
        );
        graph.write(tx -> {
            // Take a user write lock before MERGE: concurrent submissions create exactly one relationship.
            var result = tx.run(
                """
                MATCH (u:User {id:$user}) SET u.mutationVersion=coalesce(u.mutationVersion,0)+1
                WITH u MATCH (m:Movie {id:$movie}) MERGE (u)-[r:RATED]->(m)
                ON CREATE SET r.id=$id, r.createdAt=toString(datetime())
                SET r.score=$score,r.review=$review,r.updatedAt=toString(datetime()) RETURN r.id AS id
                """,
                p
            );
            if (!result.hasNext()) throw new ApiException(404, "Movie or user not found");
            result.consume();
            return null;
        });
        return get(jwt, movieId);
    }

    @DeleteMapping("/me/{movieId}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String movieId) {
        graph.write(tx -> {
            var result = tx.run(
                "MATCH (:User {id:$user})-[r:RATED]->(:Movie {id:$movie}) DELETE r RETURN count(r) AS n",
                Map.of("user", jwt.getSubject(), "movie", movieId)
            );
            if (result.single().get("n").asLong() == 0) throw new ApiException(
                404,
                "Rating not found"
            );
            return null;
        });
    }
}
