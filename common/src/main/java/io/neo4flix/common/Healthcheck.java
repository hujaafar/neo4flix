package io.neo4flix.common;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** Dependency-free container readiness probe, run in a small separate JVM. */
public class Healthcheck {

    public static void main(String[] args) {
        int port = switch (System.getenv("SERVICE")) {
            case "movie-service" -> 8081;
            case "user-service" -> 8082;
            case "rating-service" -> 8083;
            case "recommendation-service" -> 8084;
            default -> 0;
        };
        try {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            var request = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/actuator/health")
            )
                .timeout(Duration.ofSeconds(3))
                .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.exit(response.statusCode() == 200 && response.body().contains("UP") ? 0 : 1);
        } catch (Exception e) {
            System.exit(1);
        }
    }
}
