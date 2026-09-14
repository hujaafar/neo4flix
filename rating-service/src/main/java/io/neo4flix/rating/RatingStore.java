package io.neo4flix.rating;

import io.neo4flix.common.*;
import io.neo4flix.common.model.*;
import java.time.Instant;
import java.util.*;
import org.neo4j.ogm.session.Session;
import org.springframework.stereotype.Repository;

@Repository
public class RatingStore {

    private final Ogm ogm;

    public RatingStore(Ogm ogm) {
        this.ogm = ogm;
    }

    private RatingRelationship find(Session session, String userId, String movieId) {
        return session.queryForObject(
            RatingRelationship.class,
            "MATCH (u:User {id:$user})-[r:RATED]->(m:Movie {id:$movie}) RETURN u,r,m",
            Map.of("user", userId, "movie", movieId)
        );
    }

    public List<?> history(String userId) {
        return ogm.read(session -> {
            var results = new ArrayList<RatingRelationship>();
            session
                .query(
                    RatingRelationship.class,
                    "MATCH (u:User {id:$user})-[r:RATED]->(m:Movie) RETURN u,r,m",
                    Map.of("user", userId)
                )
                .forEach(results::add);
            results.sort(Comparator.comparing((RatingRelationship r) -> r.updatedAt).reversed());
            return results.stream().map(RatingRelationship::view).toList();
        });
    }

    public Object get(String userId, String movieId) {
        return ogm.read(session -> {
            var rating = find(session, userId, movieId);
            if (rating == null) throw new ApiException(404, "Rating not found");
            return rating.view();
        });
    }

    public Object save(String userId, String movieId, int score, String review) {
        return ogm.write(session -> {
            var rows = session.query(
                "MATCH (u:User {id:$user}) SET u.mutationVersion=coalesce(u.mutationVersion,0)+1 RETURN u.id AS id",
                Map.of("user", userId)
            );
            if (!rows.iterator().hasNext()) throw new ApiException(404, "User not found");
            var rating = find(session, userId, movieId);
            if (rating == null) {
                var movie = session.load(MovieNode.class, movieId, 0);
                if (movie == null) throw new ApiException(404, "Movie not found");
                rating = new RatingRelationship();
                rating.id = UUID.randomUUID().toString();
                rating.user = session.load(UserNode.class, userId, 0);
                rating.movie = movie;
                rating.createdAt = Instant.now().toString();
                rating.user.ratings.add(rating);
            }
            rating.score = score;
            rating.review = review == null ? "" : review;
            rating.updatedAt = Instant.now().toString();
            session.save(rating, 1);
            return rating.view();
        });
    }

    public void delete(String userId, String movieId) {
        ogm.write(session -> {
            var rows = session.query(
                "MATCH (u:User {id:$user}) SET u.mutationVersion=coalesce(u.mutationVersion,0)+1 RETURN u.id AS id",
                Map.of("user", userId)
            );
            if (!rows.iterator().hasNext()) throw new ApiException(404, "User not found");
            var rating = find(session, userId, movieId);
            if (rating == null) throw new ApiException(404, "Rating not found");
            session.delete(rating);
            return null;
        });
    }
}
