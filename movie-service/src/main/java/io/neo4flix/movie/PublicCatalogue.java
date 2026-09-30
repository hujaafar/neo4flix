package io.neo4flix.movie;

import io.neo4flix.common.Graph;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** Only public movie metadata; no personal ratings, profiles or private notes. */
@RestController
public class PublicCatalogue {

    private final Graph graph;

    public PublicCatalogue(Graph graph) {
        this.graph = graph;
    }

    @GetMapping("/api/catalogue/featured")
    public Object featured() {
        return graph
            .list(
                "MATCH (m:Movie) RETURN m {.id,.title,.year,.genres,.runtime,.artwork,.source,.tmdbId,.posterPath} AS movie ORDER BY coalesce(m.tmdbVoteCount,0) DESC,m.title LIMIT 12",
                Map.of()
            )
            .stream()
            .map(r -> r.get("movie"))
            .toList();
    }
}
