package io.neo4flix.common;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import org.neo4j.driver.Driver;
import org.neo4j.driver.exceptions.TransientException;
import org.neo4j.ogm.drivers.bolt.driver.BoltDriver;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;
import org.neo4j.ogm.transaction.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** One OGM session per transaction: no shared identity cache or partially loaded aggregate saves. */
@Component
public class Ogm {

    private final SessionFactory sessions;

    @Autowired
    public Ogm(Driver driver) {
        // Spring owns the native driver and its lifecycle; both graph APIs share its connection pool.
        sessions = new SessionFactory(new BoltDriver(driver), "io.neo4flix.common.model");
    }

    Ogm(SessionFactory sessions) {
        this.sessions = sessions;
    }

    public <T> T read(Function<Session, T> operation) {
        return transact(Transaction.Type.READ_ONLY, operation);
    }

    public <T> T write(Function<Session, T> operation) {
        return transact(Transaction.Type.READ_WRITE, operation);
    }

    private <T> T transact(Transaction.Type type, Function<Session, T> operation) {
        for (int attempt = 0; ; attempt++) {
            try {
                return once(type, operation);
            } catch (TransientException exception) {
                // Neo4j rolls back deadlocked transactions. Retry the whole operation with a fresh identity map.
                if (attempt >= 4) throw new ApiException(
                    503,
                    "Database is busy. Please try again."
                );
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(25, 75) * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new ApiException(503, "Request interrupted. Please try again.");
                }
            }
        }
    }

    private <T> T once(Transaction.Type type, Function<Session, T> operation) {
        Session session = sessions.openSession();
        try (var transaction = session.beginTransaction(type)) {
            T result = operation.apply(session);
            transaction.commit();
            return result;
        } finally {
            session.clear();
        }
    }
}
