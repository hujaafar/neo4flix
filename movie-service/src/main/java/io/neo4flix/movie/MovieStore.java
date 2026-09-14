package io.neo4flix.movie;

import io.neo4flix.common.*;
import io.neo4flix.common.model.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class MovieStore {

    private final Ogm ogm;

    public MovieStore(Ogm ogm) {
        this.ogm = ogm;
    }

    public MovieNode find(String id) {
        return ogm.read(session -> {
            MovieNode movie = session.load(MovieNode.class, id, 1);
            if (movie == null) throw new ApiException(404, "Movie not found");
            return movie;
        });
    }

    public void save(String id, MovieController.MovieInput input, boolean update) {
        ogm.write(session -> {
            if (update) {
                // Serialize concurrent edits before loading OGM's snapshot of the genre relationships.
                var rows = session.query(
                    "MATCH (m:Movie {id:$id}) SET m.mutationVersion=coalesce(m.mutationVersion,0)+1 RETURN m.id AS id",
                    Map.of("id", id)
                );
                if (!rows.iterator().hasNext()) throw new ApiException(404, "Movie not found");
            }
            var genreNames = input
                .genres()
                .stream()
                .map(String::strip)
                .distinct()
                .sorted()
                .toList();
            // Write queries clear OGM's mapping context. Create shared genres before loading the movie snapshot.
            session.query(
                "UNWIND $names AS name MERGE (g:Genre {name:name}) RETURN count(g) AS count",
                Map.of("names", genreNames)
            );
            MovieNode movie = update ? session.load(MovieNode.class, id, 1) : new MovieNode();
            movie.id = id;
            movie.title = input.title().strip();
            movie.releaseDate = input.releaseDate().toString();
            movie.year = input.releaseDate().getYear();
            movie.genres = genreNames;
            movie.overview = input.overview();
            movie.director = input.director();
            movie.runtime = input.runtime();
            movie.artwork = input.artwork();
            var genreNodes = new ArrayList<GenreNode>();
            for (String name : genreNames) {
                genreNodes.add(session.load(GenreNode.class, name, 0));
            }
            movie.genreNodes = genreNodes;
            session.save(movie, 1);
            return null;
        });
    }
}
