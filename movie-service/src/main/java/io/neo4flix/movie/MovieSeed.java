package io.neo4flix.movie;

import io.neo4flix.common.Graph;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class MovieSeed {

    @Bean
    ApplicationRunner seed(Graph graph) {
        return args -> {
            graph.execute(
                "CREATE CONSTRAINT movie_id IF NOT EXISTS FOR (m:Movie) REQUIRE m.id IS UNIQUE",
                Map.of()
            );
            graph.execute(
                "CREATE CONSTRAINT genre_name IF NOT EXISTS FOR (g:Genre) REQUIRE g.name IS UNIQUE",
                Map.of()
            );
            graph.execute(
                "CREATE CONSTRAINT seed_id IF NOT EXISTS FOR (s:Seed) REQUIRE s.id IS UNIQUE",
                Map.of()
            );
            List<Map<String, Object>> movies = new ArrayList<>();
            try (
                var reader = new BufferedReader(
                    new InputStreamReader(
                        new ClassPathResource("movies.psv").getInputStream(),
                        StandardCharsets.UTF_8
                    )
                )
            ) {
                for (String line : reader.lines().toList()) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] v = line.split("\\|", -1);
                    movies.add(
                        Map.of(
                            "id",
                            v[0],
                            "title",
                            v[1],
                            "releaseDate",
                            v[2],
                            "year",
                            Integer.parseInt(v[2].substring(0, 4)),
                            "genres",
                            List.of(v[3].split(",")),
                            "director",
                            v[4],
                            "runtime",
                            Integer.parseInt(v[5]),
                            "overview",
                            v[6],
                            "artwork",
                            v[0]
                        )
                    );
                }
            }
            graph.write(tx -> {
                tx.run(
                    "MERGE (s:Seed {id:'catalog-v1'}) ON CREATE SET s.complete=false SET s.lock=coalesce(s.lock,0)+1",
                    Map.of()
                ).consume();
                if (
                    tx
                        .run("MATCH (s:Seed {id:'catalog-v1'}) RETURN s.complete AS complete")
                        .single()
                        .get("complete")
                        .asBoolean()
                ) return null;
                tx.run(
                    "UNWIND $movies AS data MERGE (m:Movie {id:data.id}) ON CREATE SET m += data WITH m UNWIND m.genres AS name MERGE (g:Genre {name:name}) MERGE (m)-[:IN_GENRE]->(g)",
                    Map.of("movies", movies)
                ).consume();
                tx.run("MATCH (s:Seed {id:'catalog-v1'}) SET s.complete=true").consume();
                return null;
            });
        };
    }
}
