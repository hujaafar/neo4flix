package io.neo4flix.common.model;

import org.neo4j.ogm.annotation.*;

@NodeEntity(label = "Genre")
public class GenreNode {

    @Id
    public String name;

    public GenreNode() {}
}
