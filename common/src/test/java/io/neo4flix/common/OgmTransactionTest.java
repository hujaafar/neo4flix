package io.neo4flix.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.exceptions.TransientException;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;
import org.neo4j.ogm.transaction.Transaction;

class OgmTransactionTest {

    @Test
    void retriesRolledBackDeadlockInANewSession() {
        var factory = mock(SessionFactory.class);
        var first = mock(Session.class);
        var second = mock(Session.class);
        var aborted = mock(Transaction.class);
        var committed = mock(Transaction.class);
        when(factory.openSession()).thenReturn(first, second);
        when(first.beginTransaction(Transaction.Type.READ_WRITE)).thenReturn(aborted);
        when(second.beginTransaction(Transaction.Type.READ_WRITE)).thenReturn(committed);
        var attempts = new AtomicInteger();
        String result = new Ogm(factory).write(session -> {
            if (attempts.getAndIncrement() == 0) {
                throw new TransientException(
                    "Neo.TransientError.Transaction.DeadlockDetected",
                    "deadlock"
                );
            }
            assertSame(second, session);
            return "saved";
        });
        assertEquals("saved", result);
        verify(aborted, never()).commit();
        verify(aborted).close();
        verify(first).clear();
        verify(committed).commit();
        verify(committed).close();
        verify(second).clear();
    }

    @Test
    void doesNotRetryApplicationErrors() {
        var factory = mock(SessionFactory.class);
        var session = mock(Session.class);
        when(factory.openSession()).thenReturn(session);
        when(session.beginTransaction(Transaction.Type.READ_WRITE)).thenReturn(
            mock(Transaction.class)
        );
        var error = assertThrows(ApiException.class, () ->
            new Ogm(factory).write(s -> {
                throw new ApiException(404, "Movie not found");
            })
        );
        assertEquals(404, error.status);
        verify(factory, times(1)).openSession();
        verify(session).clear();
    }
}
