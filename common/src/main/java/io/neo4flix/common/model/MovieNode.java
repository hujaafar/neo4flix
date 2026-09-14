package io.neo4flix.common.model;

import java.util.*;
import org.neo4j.ogm.annotation.*;

@NodeEntity(label = "Movie")
public class MovieNode {

    @Id
    public String id;

    public String title;
    public String releaseDate;
    public int year;
    public List<String> genres = new ArrayList<>();
    public String overview;
    public String director;
    public int runtime;
    public String artwork;

    @Relationship(type = "IN_GENRE")
    public List<GenreNode> genreNodes = new ArrayList<>();

    public MovieNode() {}

    /** Explicit API projection keeps OGM identity and relationship bookkeeping off the wire. */
    public Map<String, Object> view() {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", id);
        result.put("title", title);
        result.put("releaseDate", releaseDate);
        result.put("year", year);
        result.put("genres", genres);
        result.put("overview", overview);
        result.put("director", director);
        result.put("runtime", runtime);
        result.put("artwork", artwork);
        return result;
    }
}
