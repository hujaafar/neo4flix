package io.neo4flix.common;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ServiceClient {

    private final RestClient client;

    public ServiceClient() {
        var factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
        );
        factory.setReadTimeout(Duration.ofSeconds(8));
        client = RestClient.builder().requestFactory(factory).build();
    }

    public Object get(String url, String token) {
        return client
            .get()
            .uri(url)
            .headers(h -> h.setBearerAuth(token))
            .retrieve()
            .body(Object.class);
    }
}
