package io.neo4flix.movie;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.neo4flix.common.ApiException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class TmdbImportsTest {

    @Test
    void stagesBeforeReplacementEnforcesOwnerAndRejectsConcurrentOrRepeatedApply()
        throws Exception {
        var client = mock(TmdbClient.class);
        var replacement = mock(CatalogueReplacement.class);
        when(client.configured()).thenReturn(true);
        var gate = new CountDownLatch(1);
        var film = new TmdbClient.Film(
            7,
            "Film",
            LocalDate.of(2020, 1, 1),
            List.of("Drama"),
            "Synopsis",
            "Director",
            120,
            "/poster.jpg",
            "",
            8,
            10
        );
        when(client.catalogue("popular", 1)).thenAnswer(i -> {
            assertTrue(gate.await(5, TimeUnit.SECONDS));
            return List.of(film);
        });
        try (
            var unused = new AutoCloseable() {
                public void close() {
                    gate.countDown();
                }
            }
        ) {
            var imports = new TmdbImports(client, replacement);
            try {
                var job = imports.start("owner", "popular", 1);
                String id = (String) job.get("id");
                assertEquals(
                    409,
                    assertThrows(ApiException.class, () -> imports.apply("owner", id)).status
                );
                assertEquals(
                    409,
                    assertThrows(ApiException.class, () ->
                        imports.start("owner", "popular", 1)
                    ).status
                );
                assertEquals(
                    404,
                    assertThrows(ApiException.class, () -> imports.get("other", id)).status
                );
                verifyNoInteractions(replacement);
                gate.countDown();
                awaitState(imports, id, "READY");
                when(replacement.replace(id, List.of(film))).thenReturn(Map.of("imported", 1));
                assertEquals("APPLIED", imports.apply("owner", id).get("state"));
                assertEquals(
                    409,
                    assertThrows(ApiException.class, () -> imports.apply("owner", id)).status
                );
                verify(replacement, times(1)).replace(id, List.of(film));
            } finally {
                imports.close();
            }
        }
    }

    @Test
    void failedFetchNeverWritesOrDeletesCatalogue() throws Exception {
        var client = mock(TmdbClient.class);
        var replacement = mock(CatalogueReplacement.class);
        when(client.configured()).thenReturn(true);
        when(client.catalogue("popular", 1)).thenThrow(
            new ApiException(502, "Provider unavailable")
        );
        var imports = new TmdbImports(client, replacement);
        try {
            String id = (String) imports.start("owner", "popular", 1).get("id");
            awaitState(imports, id, "FAILED");
            assertEquals(
                409,
                assertThrows(ApiException.class, () -> imports.apply("owner", id)).status
            );
            verifyNoInteractions(replacement);
        } finally {
            imports.close();
        }
    }

    @Test
    void emptyReplacementNeverTouchesDatabase() {
        var graph = mock(io.neo4flix.common.Graph.class);
        assertEquals(
            400,
            assertThrows(ApiException.class, () ->
                new CatalogueReplacement(graph).replace("id", List.of())
            ).status
        );
        verifyNoInteractions(graph);
    }

    static void awaitState(TmdbImports imports, String id, String state) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!state.equals(imports.get("owner", id).get("state")) && System.nanoTime() < end)
            Thread.sleep(10);
        assertEquals(state, imports.get("owner", id).get("state"));
    }
}
