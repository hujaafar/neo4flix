package io.neo4flix.common.model;

import java.util.*;
import org.neo4j.ogm.annotation.*;

@RelationshipEntity(type = "RATED")
public class RatingRelationship {

    @Id
    public String id;

    @StartNode
    public UserNode user;

    @EndNode
    public MovieNode movie;

    public int score;
    public String review;
    public String createdAt;
    public String updatedAt;

    public RatingRelationship() {}

    public Map<String, Object> view() {
        return Map.of(
            "id",
            id,
            "score",
            score,
            "review",
            review == null ? "" : review,
            "createdAt",
            createdAt,
            "updatedAt",
            updatedAt,
            "movie",
            movie.view()
        );
    }
}
