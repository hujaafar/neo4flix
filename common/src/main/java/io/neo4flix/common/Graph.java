package io.neo4flix.common;

import java.util.*;
import java.util.function.Function;
import org.neo4j.driver.*;
import org.springframework.stereotype.Component;

/** Parameterized graph operations; each write callback is one retryable transaction. */
@Component
public class Graph {

    private final Driver driver;

    public Graph(Driver driver) {
        this.driver = driver;
    }

    public <T> T read(Function<TransactionContext, T> operation) {
        try (var session = driver.session()) {
            return session.executeRead(operation::apply);
        }
    }

    public <T> T write(Function<TransactionContext, T> operation) {
        try (var session = driver.session()) {
            return session.executeWrite(operation::apply);
        }
    }

    public List<Map<String, Object>> list(String cypher, Map<String, Object> parameters) {
        return read(tx -> tx.run(cypher, parameters).list(r -> r.asMap()));
    }

    public Map<String, Object> one(String cypher, Map<String, Object> parameters) {
        return read(tx -> {
            var result = tx.run(cypher, parameters);
            if (!result.hasNext()) throw new ApiException(404, "Record not found");
            return result.next().asMap();
        });
    }

    public void execute(String cypher, Map<String, Object> parameters) {
        write(tx -> {
            tx.run(cypher, parameters).consume();
            return null;
        });
    }
}
