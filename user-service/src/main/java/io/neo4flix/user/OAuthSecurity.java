package io.neo4flix.user;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.ArrayList;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;

@Configuration
public class OAuthSecurity {

    static final String RETURN_URL = "neo4flix.oauth.return";

    @Bean
    @Order(1)
    SecurityFilterChain oauthSecurity(
        HttpSecurity http,
        OAuthService oauth,
        ObjectProvider<ClientRegistrationRepository> configuredClients,
        @Value("${app.origin}") String origin,
        @Value("${app.oauth2.google-client-id:}") String id,
        @Value("${app.oauth2.google-client-secret:}") String secret,
        @Value("${app.oauth2.github-client-id:}") String githubId,
        @Value("${app.oauth2.github-client-secret:}") String githubSecret
    ) throws Exception {
        http.securityMatcher("/api/auth/oauth2/**")
            // State protects the external callback; every cookie-authenticated POST checks exact Origin.
            .csrf(c -> c.disable())
            .requestCache(c -> c.disable())
            .logout(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            // A provider principal must never authenticate JWT APIs or bypass the local TOTP challenge.
            .securityContext(c -> c.securityContextRepository(new NullSecurityContextRepository()))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        var registrations = new ArrayList<ClientRegistration>();
        if (!id.isBlank() && !secret.isBlank()) registrations.add(
            ClientRegistration.withRegistrationId("google")
                .clientId(id)
                .clientSecret(secret)
                .clientName("Google")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(origin + "/api/auth/oauth2/callback/google")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .issuerUri("https://accounts.google.com")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .build()
        );
        if (!githubId.isBlank() && !githubSecret.isBlank()) registrations.add(
            ClientRegistration.withRegistrationId("github")
                .clientId(githubId)
                .clientSecret(githubSecret)
                .clientName("GitHub")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(origin + "/api/auth/oauth2/callback/github")
                .scope("read:user", "user:email")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("id")
                .build()
        );
        if (registrations.isEmpty()) return http.build();
        var clients = configuredClients.getIfAvailable(() ->
            new InMemoryClientRegistrationRepository(registrations)
        );
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(
            clients,
            "/api/auth/oauth2/authorize"
        );
        resolver.setAuthorizationRequestCustomizer(
            OAuth2AuthorizationRequestCustomizers.withPkce()
        );
        var requests = new OAuth2AuthorizationRequestResolver() {
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return remember(request, resolver.resolve(request));
            }

            public OAuth2AuthorizationRequest resolve(
                HttpServletRequest request,
                String registrationId
            ) {
                return remember(request, resolver.resolve(request, registrationId));
            }

            private OAuth2AuthorizationRequest remember(
                HttpServletRequest request,
                OAuth2AuthorizationRequest result
            ) {
                if (result != null) {
                    if (request.getSession(false) != null) request.getSession(false).invalidate();
                    var session = request.getSession(true);
                    session.setMaxInactiveInterval(600);
                    session.setAttribute(
                        RETURN_URL,
                        OAuthService.safeReturnUrl(request.getParameter("returnUrl"))
                    );
                }
                return result;
            }
        };
        http.oauth2Login(login ->
            login
                .clientRegistrationRepository(clients)
                .authorizedClientRepository(new DiscardProviderTokens())
                .loginPage("/login")
                .authorizationEndpoint(a -> a.authorizationRequestResolver(requests))
                .redirectionEndpoint(r -> r.baseUri("/api/auth/oauth2/callback/*"))
                .userInfoEndpoint(u -> u.userService(new GitHubUserService()))
                .successHandler((request, response, authentication) -> {
                    try {
                        if (
                            !(authentication instanceof OAuth2AuthenticationToken token)
                        ) throw new IllegalArgumentException("Unexpected provider");
                        OAuthIdentity identity;
                        if (
                            token.getAuthorizedClientRegistrationId().equals("google") &&
                            token.getPrincipal() instanceof OidcUser user
                        ) {
                            identity = GoogleIdentity.from(user);
                        } else if (
                            token.getAuthorizedClientRegistrationId().equals("github") &&
                            token.getPrincipal() instanceof GitHubUserService.VerifiedUser user
                        ) {
                            identity = user.identity;
                        } else throw new IllegalArgumentException("Unexpected provider");
                        var session = request.getSession(false);
                        String returnUrl =
                            session == null ? "/" : (String) session.getAttribute(RETURN_URL);
                        var pending = oauth.begin(identity, returnUrl);
                        if (session != null) session.invalidate();
                        session = request.getSession(true);
                        session.setMaxInactiveInterval(300);
                        session.setAttribute(OAuthController.PENDING, pending);
                        response.sendRedirect(origin + "/oauth2/complete");
                    } catch (Exception failure) {
                        fail(request, response, origin);
                    }
                })
                .failureHandler((request, response, failure) -> fail(request, response, origin))
        );
        return http.build();
    }

    private static void fail(
        HttpServletRequest request,
        HttpServletResponse response,
        String origin
    ) throws IOException {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        String provider = request.getRequestURI().endsWith("/github") ? "github" : "google";
        response.sendRedirect(origin + "/login?oauthError=" + provider);
    }

    /** Provider access tokens are used only during login and are never retained as application credentials. */
    static class DiscardProviderTokens implements OAuth2AuthorizedClientRepository {

        public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
            String id,
            Authentication principal,
            HttpServletRequest request
        ) {
            return null;
        }

        public void saveAuthorizedClient(
            OAuth2AuthorizedClient client,
            Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response
        ) {}

        public void removeAuthorizedClient(
            String id,
            Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response
        ) {}
    }
}
