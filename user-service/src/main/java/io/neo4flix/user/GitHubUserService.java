package io.neo4flix.user;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.*;
import org.springframework.web.client.RestClient;

/** GitHub OAuth has no ID token. Verify its stable user ID and primary email through its APIs. */
class GitHubUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService users = new DefaultOAuth2UserService();
    private final RestClient api;

    GitHubUserService() {
        var factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        );
        factory.setReadTimeout(Duration.ofSeconds(10));
        api = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        if (!request.getClientRegistration().getRegistrationId().equals("github")) throw invalid();
        try {
            var user = users.loadUser(request);
            Object id = user.getAttribute("id");
            if (
                !(id instanceof Number) || !id.toString().matches("[1-9][0-9]{0,18}")
            ) throw invalid();
            String subject = Long.toString(Long.parseLong(id.toString()));
            // This URI is fixed in the server registration, not supplied by the browser or profile.
            var emails = api
                .get()
                .uri(
                    request
                        .getClientRegistration()
                        .getProviderDetails()
                        .getUserInfoEndpoint()
                        .getUri() + "/emails"
                )
                .headers(h -> {
                    h.setBearerAuth(request.getAccessToken().getTokenValue());
                    h.set("Accept", "application/vnd.github+json");
                    h.set("X-GitHub-Api-Version", "2022-11-28");
                })
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            var primary =
                emails == null
                    ? Optional.<Map<String, Object>>empty()
                    : emails
                          .stream()
                          .filter(
                              e ->
                                  Boolean.TRUE.equals(e.get("primary")) &&
                                  Boolean.TRUE.equals(e.get("verified"))
                          )
                          .findFirst();
            if (
                primary.isEmpty() || !(primary.get().get("email") instanceof String email)
            ) throw invalid();
            String name = user.getAttribute("name");
            if (name == null || name.isBlank()) name = user.getAttribute("login");
            var identity = new OAuthIdentity(OAuthProvider.GITHUB, subject, email, name);
            return new VerifiedUser(user, identity);
        } catch (Exception failure) {
            // Do not expose response bodies, tokens, or provider account information in errors.
            throw invalid();
        }
    }

    private static OAuth2AuthenticationException invalid() {
        return new OAuth2AuthenticationException(
            new OAuth2Error("invalid_github_identity"),
            "GitHub could not verify your account and primary email."
        );
    }

    static final class VerifiedUser extends DefaultOAuth2User {

        final OAuthIdentity identity;

        VerifiedUser(OAuth2User user, OAuthIdentity identity) {
            super(
                user.getAuthorities(),
                Map.of("id", identity.subject(), "name", identity.name()),
                "id"
            );
            this.identity = identity;
        }
    }
}
