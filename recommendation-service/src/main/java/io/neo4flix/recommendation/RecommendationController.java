package io.neo4flix.recommendation;

import io.neo4flix.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final Graph graph;

    public RecommendationController(Graph graph) {
        this.graph = graph;
    }

    @GetMapping
    public List<?> recommendations(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "") @Size(max = 40) String genre,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(defaultValue = "24") @Min(1) @Max(100) int limit
    ) {
        if (from != null && to != null && from.isAfter(to)) throw new ApiException(
            400,
            "Start date must precede end date"
        );
        var params = new HashMap<String, Object>();
        params.put("user", jwt.getSubject());
        params.put("genre", genre);
        params.put("from", from == null ? null : from.toString());
        params.put("to", to == null ? null : to.toString());
        params.put("limit", limit);
        return graph
            .list(
                """
                MATCH (u:User {id:$user})
                CALL { WITH u
                  OPTIONAL MATCH (u)-[liked:RATED]->(seen:Movie) WHERE liked.score>=4
                  RETURN collect(DISTINCT id(seen)) AS likedIds
                }
                CALL { WITH u,likedIds
                  OPTIONAL MATCH (u)-[a:RATED]->(:Movie)<-[b:RATED]-(peer:User)
                  WHERE peer<>u AND a.score>=4 AND b.score>=4
                  WITH DISTINCT peer,likedIds
                  OPTIONAL MATCH (peer)-[r:RATED]->(seen:Movie) WHERE r.score>=4
                  WITH peer,likedIds,collect(DISTINCT id(seen)) AS peerLikedIds
                  WITH peer,CASE WHEN peer IS NULL THEN 0.0
                    ELSE gds.similarity.jaccard(likedIds,peerLikedIds) END AS similarity
                  RETURN collect(CASE WHEN peer IS NULL THEN null
                    ELSE {user:peer,similarity:similarity} END) AS peers
                }
                MATCH (m:Movie)
                WHERE NOT (u)-[:RATED]->(m) AND NOT (u)-[:DISMISSED]->(m)
                  AND ($genre='' OR $genre IN m.genres)
                  AND ($from IS NULL OR m.releaseDate >= $from) AND ($to IS NULL OR m.releaseDate <= $to)
                CALL { WITH peers,m
                  UNWIND peers AS neighbor
                  WITH neighbor.user AS peer,neighbor.similarity AS similarity,m
                  MATCH (peer)-[c:RATED]->(m) WHERE c.score>=4
                  RETURN coalesce(sum(similarity),0.0) AS collaborative,count(peer) AS neighbors
                }
                CALL { WITH u,m
                  OPTIONAL MATCH (u)-[liked:RATED]->(:Movie)-[:IN_GENRE]->(g:Genre)<-[:IN_GENRE]-(m)
                  WHERE liked.score>=4 RETURN count(DISTINCT g) AS affinity
                }
                CALL { WITH m OPTIONAL MATCH (:User)-[r:RATED]->(m)
                  RETURN coalesce(avg(r.score),0.0) AS averageRating,count(r) AS ratingCount
                }
                WITH m,neighbors,collaborative,affinity,averageRating,ratingCount,
                  (collaborative*3.0 + affinity*1.5 + (averageRating*ratingCount + 3.0*5)/(ratingCount+5.0)) AS score
                RETURN m{.*,averageRating:averageRating,ratingCount:ratingCount,
                  recommendationScore:score,collaborativeScore:collaborative,
                  algorithm:'gds.similarity.jaccard',reason:CASE WHEN neighbors>0 THEN 'Loved by viewers with similar taste'
                  WHEN affinity>0 THEN 'More from genres you enjoy' ELSE 'Explore something new' END} AS movie
                ORDER BY score DESC, m.title LIMIT $limit
                """,
                params
            )
            .stream()
            .map(x -> x.get("movie"))
            .toList();
    }

    @GetMapping("/dismissed")
    public List<?> dismissed(@AuthenticationPrincipal Jwt jwt) {
        return graph
            .list(
                "MATCH (:User {id:$user})-[d:DISMISSED]->(m:Movie) RETURN m{.*} AS movie ORDER BY d.updatedAt DESC",
                Map.of("user", jwt.getSubject())
            )
            .stream()
            .map(x -> x.get("movie"))
            .toList();
    }

    @RequestMapping(path = "/dismissed/{id}", method = { RequestMethod.POST, RequestMethod.PUT })
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void dismiss(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        graph.write(tx -> {
            var r = tx.run(
                "MATCH (u:User {id:$user}),(m:Movie {id:$id}) MERGE (u)-[d:DISMISSED]->(m) SET d.updatedAt=toString(datetime()) RETURN m.id AS id",
                Map.of("user", jwt.getSubject(), "id", id)
            );
            if (!r.hasNext()) throw new ApiException(404, "Movie not found");
            r.consume();
            return null;
        });
    }

    @DeleteMapping("/dismissed/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void restore(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        graph.execute(
            "MATCH (:User {id:$user})-[d:DISMISSED]->(:Movie {id:$id}) DELETE d",
            Map.of("user", jwt.getSubject(), "id", id)
        );
    }

    public record ShareInput(
        @NotBlank @Size(max = 100) String movieId,
        @NotBlank @Size(max = 500) String note
    ) {}

    @PostMapping("/shares")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Object share(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ShareInput input) {
        String id = UUID.randomUUID().toString() + UUID.randomUUID().toString().substring(0, 8);
        graph.write(tx -> {
            var r = tx.run(
                "MATCH (u:User {id:$user}),(m:Movie {id:$movie}) CREATE (u)-[:SHARED]->(s:Share {id:$id,note:$note,createdAt:toString(datetime())})-[:RECOMMENDS]->(m) RETURN s.id AS id",
                Map.of(
                    "user",
                    jwt.getSubject(),
                    "movie",
                    input.movieId(),
                    "id",
                    id,
                    "note",
                    input.note()
                )
            );
            if (!r.hasNext()) throw new ApiException(404, "Movie not found");
            r.consume();
            return null;
        });
        return shared(id);
    }

    @GetMapping("/shares")
    public List<?> shares(@AuthenticationPrincipal Jwt jwt) {
        return graph
            .list(
                "MATCH (:User {id:$user})-[:SHARED]->(s:Share)-[:RECOMMENDS]->(m:Movie) RETURN s{.*,movie:m{.*}} AS share ORDER BY s.createdAt DESC",
                Map.of("user", jwt.getSubject())
            )
            .stream()
            .map(x -> x.get("share"))
            .toList();
    }

    @GetMapping("/shares/{id}")
    public Object shared(@PathVariable String id) {
        return graph
            .one(
                "MATCH (:User)-[:SHARED]->(s:Share {id:$id})-[:RECOMMENDS]->(m:Movie) RETURN s{.*,movie:m{.*}} AS share",
                Map.of("id", id)
            )
            .get("share");
    }

    public record NoteInput(@NotBlank @Size(max = 500) String note) {}

    @PutMapping("/shares/{id}")
    public Object updateShare(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String id,
        @Valid @RequestBody NoteInput input
    ) {
        graph.write(tx -> {
            var r = tx.run(
                "MATCH (:User {id:$user})-[:SHARED]->(s:Share {id:$id}) SET s.note=$note RETURN s.id AS id",
                Map.of("user", jwt.getSubject(), "id", id, "note", input.note())
            );
            if (!r.hasNext()) throw new ApiException(404, "Share not found");
            r.consume();
            return null;
        });
        return shared(id);
    }

    @DeleteMapping("/shares/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void deleteShare(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        graph.write(tx -> {
            var r = tx.run(
                "MATCH (:User {id:$user})-[:SHARED]->(s:Share {id:$id}) DETACH DELETE s RETURN count(s) AS n",
                Map.of("user", jwt.getSubject(), "id", id)
            );
            if (r.single().get("n").asLong() == 0) throw new ApiException(404, "Share not found");
            return null;
        });
    }
}
