package io.neo4flix.movie;

import io.neo4flix.common.ApiException;
import io.neo4flix.common.Graph;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Backup and replacement either both commit, or neither does. Account credentials are never copied. */
@Component
public class CatalogueReplacement {

    private final Graph graph;

    public CatalogueReplacement(Graph graph) {
        this.graph = graph;
    }

    public Map<String, Object> replace(String importId, List<TmdbClient.Film> films) {
        if (
            films.isEmpty() ||
            films.size() > 50 ||
            films.stream().map(TmdbClient.Film::tmdbId).distinct().count() != films.size()
        ) throw new ApiException(400, "A complete, unique catalogue is required.");
        var movies = films.stream().map(TmdbClient.Film::properties).toList();
        return graph.write(tx -> {
            // Lock the seed marker also used by startup, preventing the original films from returning.
            tx.run("MERGE (s:Seed {id:'catalog-v1'}) SET s.lock=coalesce(s.lock,0)+1").consume();
            if (
                tx
                    .run("MATCH (b:CatalogBackup {id:$id}) RETURN b.id", Map.of("id", importId))
                    .hasNext()
            ) throw new ApiException(409, "This import has already been applied.");
            var snapshot = new LinkedHashMap<String, Object>();
            snapshot.put(
                "movies",
                tx
                    .run("MATCH (m:Movie) RETURN properties(m) AS data")
                    .list(r -> r.get("data").asMap())
            );
            snapshot.put(
                "genres",
                tx
                    .run("MATCH (g:Genre) RETURN properties(g) AS data")
                    .list(r -> r.get("data").asMap())
            );
            snapshot.put(
                "shares",
                tx
                    .run(
                        "MATCH (s:Share)-[:RECOMMENDS]->(:Movie) RETURN DISTINCT properties(s) AS data"
                    )
                    .list(r -> r.get("data").asMap())
            );
            snapshot.put(
                "relationships",
                tx
                    .run(
                        """
                        MATCH (a)-[r]->(b) WHERE a:Movie OR b:Movie OR (b:Share AND EXISTS { MATCH (b)-[:RECOMMENDS]->(:Movie) })
                        RETURN labels(a) AS fromLabels, coalesce(a.id,a.name) AS fromId,
                               type(r) AS type, properties(r) AS data, labels(b) AS toLabels, coalesce(b.id,b.name) AS toId
                        """
                    )
                    .list(r -> r.asMap())
            );
            String json = JsonMapper.builder().build().writeValueAsString(snapshot);
            int oldCount = ((List<?>) snapshot.get("movies")).size();
            tx.run(
                "CREATE (:CatalogBackup {id:$id,createdAt:toString(datetime()),movieCount:$count,payload:$payload})",
                Map.of("id", importId, "count", oldCount, "payload", json)
            ).consume();
            tx.run("MATCH (s:Share)-[:RECOMMENDS]->(:Movie) DETACH DELETE s").consume();
            tx.run("MATCH (m:Movie) DETACH DELETE m").consume();
            tx.run(
                "MATCH (g:Genre) WHERE NOT EXISTS { MATCH (g)<-[:IN_GENRE]-(:Movie) } DETACH DELETE g"
            ).consume();
            tx.run(
                """
                UNWIND $movies AS data CREATE (m:Movie) SET m = data
                WITH m UNWIND m.genres AS name MERGE (g:Genre {name:name}) CREATE (m)-[:IN_GENRE]->(g)
                """,
                Map.of("movies", movies)
            ).consume();
            tx.run(
                "MATCH (s:Seed {id:'catalog-v1'}) SET s.complete=true, s.source='TMDB', s.importedAt=toString(datetime()),s.backupId=$id",
                Map.of("id", importId)
            ).consume();
            return Map.<String, Object>of(
                "imported",
                movies.size(),
                "removed",
                oldCount,
                "backupId",
                importId
            );
        });
    }
}
