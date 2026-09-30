package io.neo4flix.movie;

import io.neo4flix.common.ApiException;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

/** Bounded staging jobs keep slow external requests outside the catalogue transaction. */
@Component
public class TmdbImports {

    private final TmdbClient client;
    private final CatalogueReplacement replacement;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tmdb-import");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private boolean fetching;

    private static class Job {

        final String id = UUID.randomUUID().toString();
        final String owner;
        final Instant created = Instant.now();
        String state = "FETCHING",
            error = "";
        List<TmdbClient.Film> films = List.of();
        Map<String, Object> result = Map.of();

        Job(String owner) {
            this.owner = owner;
        }

        synchronized Map<String, Object> view() {
            return Map.of(
                "id",
                id,
                "state",
                state,
                "error",
                error,
                "films",
                films.stream().map(TmdbClient.Film::properties).toList(),
                "result",
                result
            );
        }
    }

    public TmdbImports(TmdbClient client, CatalogueReplacement replacement) {
        this.client = client;
        this.replacement = replacement;
    }

    public boolean configured() {
        return client.configured();
    }

    public synchronized Map<String, Object> start(String owner, String selection, int count) {
        if (!client.configured()) throw new ApiException(
            503,
            "Configure the TMDB read access token first."
        );
        if (
            !Set.of("popular", "top_rated").contains(selection) || count < 1 || count > 50
        ) throw new ApiException(400, "Choose popular or top_rated and 1 to 50 films.");
        if (fetching) throw new ApiException(
            409,
            "An import is already fetching films. Wait for it to finish."
        );
        jobs.values().removeIf(j -> j.created.isBefore(Instant.now().minusSeconds(1800)));
        while (jobs.size() >= 8) jobs.remove(jobs.keySet().iterator().next());
        Job job = new Job(owner);
        jobs.put(job.id, job);
        fetching = true;
        worker.submit(() -> {
            try {
                var films = client.catalogue(selection, count);
                synchronized (job) {
                    job.films = films;
                    job.state = "READY";
                }
            } catch (Exception e) {
                synchronized (job) {
                    job.error =
                        e instanceof ApiException
                            ? e.getMessage()
                            : "Import failed. The current catalogue was kept.";
                    job.state = "FAILED";
                }
            } finally {
                synchronized (this) {
                    fetching = false;
                }
            }
        });
        return job.view();
    }

    private synchronized Job find(String owner, String id) {
        Job job = jobs.get(id);
        if (
            job == null ||
            !job.owner.equals(owner) ||
            job.created.isBefore(Instant.now().minusSeconds(1800))
        ) throw new ApiException(404, "Import not found or expired. Fetch a new preview.");
        return job;
    }

    public Map<String, Object> get(String owner, String id) {
        return find(owner, id).view();
    }

    public Map<String, Object> apply(String owner, String id) {
        Job job = find(owner, id);
        synchronized (job) {
            if (!job.state.equals("READY")) throw new ApiException(
                409,
                "Only a completed preview can replace the catalogue."
            );
            job.result = replacement.replace(job.id, job.films);
            job.state = "APPLIED";
            return job.view();
        }
    }

    @PreDestroy
    public void close() {
        worker.shutdownNow();
    }
}
