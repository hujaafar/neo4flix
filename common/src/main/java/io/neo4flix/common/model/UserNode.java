package io.neo4flix.common.model;

import java.util.*;
import org.neo4j.ogm.annotation.*;

/** Rating aggregate root. Authentication properties remain owned by the user service. */
@NodeEntity(label = "User")
public class UserNode {

    @Id
    public String id;

    @Relationship(type = "RATED")
    public List<RatingRelationship> ratings = new ArrayList<>();

    public UserNode() {}
}
