package io.neo4flix.movie;

import io.neo4flix.common.Graph;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Bounded, deliberately projected graph inspection; never serialize raw User nodes. */
@RestController
@RequestMapping("/api/movies/graph")
@PreAuthorize("hasRole('ADMIN')")
public class GraphController {

    private final Graph graph;

    public GraphController(Graph graph) {
        this.graph = graph;
    }

    @GetMapping
    public Map<String, Object> snapshot() {
        return graph.read(tx -> {
            var nodes = new LinkedHashMap<String, Map<String, Object>>();
            var edges = new ArrayList<Map<String, Object>>();
            var movieIds = new ArrayList<String>();
            var movies = tx.run(
                "MATCH (m:Movie) WITH m ORDER BY m.title LIMIT 40 OPTIONAL MATCH (m)-[:IN_GENRE]->(g:Genre) RETURN m.id AS id,m.title AS title,collect(g.name) AS genres"
            );
            for (var movie : movies.list()) {
                String id = movie.get("id").asString();
                movieIds.add(id);
                nodes.put(
                    "movie:" + id,
                    Map.of(
                        "id",
                        "movie:" + id,
                        "label",
                        movie.get("title").asString(),
                        "kind",
                        "Movie"
                    )
                );
                for (String genre : movie.get("genres").asList(v -> v.asString())) {
                    String genreId = "genre:" + genre;
                    nodes.put(genreId, Map.of("id", genreId, "label", genre, "kind", "Genre"));
                    edges.add(
                        Map.of("source", "movie:" + id, "target", genreId, "type", "IN_GENRE")
                    );
                }
            }
            var users = tx.run(
                "MATCH (u:User) WITH u ORDER BY u.id LIMIT 25 OPTIONAL MATCH (u)-[r:RATED]->(m:Movie) WHERE m.id IN $movies RETURN u.id AS id,collect(CASE WHEN m IS NULL THEN null ELSE {movie:m.id,score:r.score,createdAt:r.createdAt,updatedAt:r.updatedAt} END) AS ratings",
                Map.of("movies", movieIds)
            );
            for (var user : users.list()) {
                String id = user.get("id").asString();
                String nodeId = "user:" + id;
                nodes.put(
                    nodeId,
                    Map.of(
                        "id",
                        nodeId,
                        "label",
                        "Viewer " + id.substring(0, Math.min(id.length(), 6)),
                        "kind",
                        "User"
                    )
                );
                for (var rating : user.get("ratings").asList(v -> v.asMap())) {
                    var edge = new LinkedHashMap<String, Object>(rating);
                    edge.put("source", nodeId);
                    edge.put("target", "movie:" + rating.get("movie"));
                    edge.put("type", "RATED");
                    edge.remove("movie");
                    edges.add(edge);
                }
            }
            String gdsVersion = tx
                .run("RETURN gds.version() AS version")
                .single()
                .get("version")
                .asString();
            return Map.of(
                "nodes",
                new ArrayList<>(nodes.values()),
                "relationships",
                edges,
                "movieLimit",
                40,
                "userLimit",
                25,
                "gdsVersion",
                gdsVersion
            );
        });
    }
}
