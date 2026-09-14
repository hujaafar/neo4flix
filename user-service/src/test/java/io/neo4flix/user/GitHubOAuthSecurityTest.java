package io.neo4flix.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sun.net.httpserver.*;
import io.neo4flix.common.ApiErrors;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.test.context.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** Real HTTP code exchange, PKCE and GitHub user/email lookups. Application persistence is mocked. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(
    classes = {
        GitHubOAuthSecurityTest.Config.class,
        OAuthSecurity.class,
        OAuthController.class,
        ApiErrors.class,
    }
)
@TestPropertySource(
    properties = {
        "app.origin=https://localhost:9443",
        "app.oauth2.github-client-id=github-test",
        "app.oauth2.github-client-secret=github-secret",
    }
)
class GitHubOAuthSecurityTest {

    static final String ORIGIN = "https://localhost:9443";
    static final Map<String, Map<String, String>> codes = new ConcurrentHashMap<>();
    static final Map<String, String> tokens = new ConcurrentHashMap<>();
    static final HttpServer provider;
    static final String providerUrl;

    static {
        try {
            provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            providerUrl = "http://127.0.0.1:" + provider.getAddress().getPort();
            provider.createContext("/token", GitHubOAuthSecurityTest::token);
            provider.createContext("/user", e -> {
                String variant = variant(e);
                if (variant == null) {
                    json(e, 401, "{}");
                    return;
                }
                // Public profile email is deliberately untrusted and never used to link an account.
                json(
                    e,
                    200,
                    "{\"id\":" +
                        (variant.equals("bad-id") ? "null" : "123456") +
                        ",\"login\":\"octocat\",\"name\":null,\"email\":\"untrusted@example.test\"}"
                );
            });
            provider.createContext("/user/emails", e -> {
                String variant = variant(e);
                if (variant == null || variant.equals("api-error")) {
                    json(e, 403, "{}");
                    return;
                }
                json(
                    e,
                    200,
                    "[{\"email\":\"secondary@example.test\",\"primary\":false,\"verified\":true}," +
                        "{\"email\":\"GitHub@Example.test\",\"primary\":" +
                        !variant.equals("no-primary") +
                        ",\"verified\":" +
                        !variant.equals("unverified") +
                        "}]"
                );
            });
            provider.start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class Config {

        @Bean
        OAuthService oauth() {
            return mock(OAuthService.class);
        }

        @Bean
        AuthController auth() {
            return new AuthController(mock(AuthService.class), ORIGIN, true);
        }

        @Bean
        ClientRegistrationRepository clients() {
            return new InMemoryClientRegistrationRepository(
                ClientRegistration.withRegistrationId("github")
                    .clientId("github-test")
                    .clientSecret("github-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(ORIGIN + "/api/auth/oauth2/callback/github")
                    .scope("read:user", "user:email")
                    .authorizationUri(providerUrl + "/authorize")
                    .tokenUri(providerUrl + "/token")
                    .userInfoUri(providerUrl + "/user")
                    .userNameAttributeName("id")
                    .build()
            );
        }
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    OAuthService oauth;

    MockMvc mvc;

    @BeforeEach
    void setup() {
        reset(oauth);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(oauth.begin(any(), any())).thenAnswer(a ->
            new OAuthService.Pending(
                a.getArgument(0),
                "login",
                "user-1",
                0,
                true,
                a.getArgument(1),
                Instant.now().getEpochSecond() + 300
            )
        );
        when(oauth.complete(any(), any(), any(), any())).thenReturn(
            new AuthService.Session("app-jwt", "app-refresh", Map.of("id", "user-1"))
        );
    }

    @AfterAll
    static void stop() {
        provider.stop(0);
    }

    record Start(MockHttpSession session, Map<String, String> params) {}

    Start start() throws Exception {
        var r = mvc
            .perform(get("/api/auth/oauth2/authorize/github").param("returnUrl", "/watchlist"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        return new Start(
            (MockHttpSession) r.getRequest().getSession(false),
            params(URI.create(r.getResponse().getRedirectedUrl()).getRawQuery())
        );
    }

    MvcResult callback(Start start, String variant) throws Exception {
        String code = UUID.randomUUID().toString();
        var attributes = new HashMap<>(start.params());
        attributes.put("variant", variant);
        codes.put(code, attributes);
        return mvc
            .perform(
                get("/api/auth/oauth2/callback/github")
                    .secure(true)
                    .with(r -> {
                        r.setScheme("https");
                        r.setServerName("localhost");
                        r.setServerPort(9443);
                        return r;
                    })
                    .session(start.session())
                    .param("code", code)
                    .param("state", start.params().get("state"))
            )
            .andExpect(status().is3xxRedirection())
            .andReturn();
    }

    @Test
    void githubWorksIndependentlyOfGoogleAndUsesPkceStateAndVerifiedPrimaryEmail()
        throws Exception {
        mvc.perform(get("/api/auth/oauth2/providers"))
            .andExpect(jsonPath("$[0].enabled").value(false))
            .andExpect(jsonPath("$[1].enabled").value(true));
        var start = start();
        assertEquals("S256", start.params().get("code_challenge_method"));
        assertNotNull(start.params().get("state"));
        assertFalse(start.params().containsKey("client_secret"));
        assertEquals(
            Set.of("read:user", "user:email"),
            Set.of(start.params().get("scope").split(" "))
        );
        var result = callback(start, "valid");
        assertEquals(ORIGIN + "/oauth2/complete", result.getResponse().getRedirectedUrl());
        verify(oauth).begin(
            new OAuthIdentity(OAuthProvider.GITHUB, "123456", "github@example.test", "octocat"),
            "/watchlist"
        );
        verify(oauth, never()).complete(any(), any(), any(), any());
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotEquals(start.session().getId(), session.getId());
        assertNull(session.getAttribute("SPRING_SECURITY_CONTEXT"));
        mvc.perform(get("/api/auth/oauth2/pending").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("github"))
            .andExpect(jsonPath("$.providerName").value("GitHub"))
            .andExpect(jsonPath("$.twoFactor").value(true));
        mvc.perform(
            post("/api/auth/oauth2/complete")
                .session(session)
                .header("Origin", "https://evil.test")
                .contentType("application/json")
                .content("{}")
        ).andExpect(status().isForbidden());
        mvc.perform(
            post("/api/auth/oauth2/complete")
                .session(session)
                .header("Origin", ORIGIN)
                .contentType("application/json")
                .content("{\"code\":\"123456\"}")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("app-jwt"));
        assertTrue(session.isInvalid());
        mvc.perform(
            post("/api/auth/oauth2/complete")
                .header("Origin", ORIGIN)
                .contentType("application/json")
                .content("{}")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnverifiedMissingPrimaryMissingIdAndUnavailableEmailApi() throws Exception {
        for (String variant : List.of("unverified", "no-primary", "bad-id", "api-error")) {
            assertEquals(
                ORIGIN + "/login?oauthError=github",
                callback(start(), variant).getResponse().getRedirectedUrl(),
                variant
            );
        }
        verifyNoInteractions(oauth);
    }

    @Test
    void rejectsWrongStateMissingSessionAndCancelledAuthorization() throws Exception {
        var start = start();
        mvc.perform(
            get("/api/auth/oauth2/callback/github")
                .session(start.session())
                .param("code", "stolen")
                .param("state", "wrong")
        ).andExpect(redirectedUrl(ORIGIN + "/login?oauthError=github"));
        mvc.perform(
            get("/api/auth/oauth2/callback/github").param("code", "stolen").param("state", "wrong")
        ).andExpect(redirectedUrl(ORIGIN + "/login?oauthError=github"));
        start = start();
        mvc.perform(
            get("/api/auth/oauth2/callback/github")
                .session(start.session())
                .param("error", "access_denied")
                .param("state", start.params().get("state"))
        ).andExpect(redirectedUrl(ORIGIN + "/login?oauthError=github"));
        verifyNoInteractions(oauth);
    }

    @Test
    void rejectsCallbackForAnotherProviderAndWrongPkce() throws Exception {
        var start = start();
        mvc.perform(
            get("/api/auth/oauth2/callback/google")
                .session(start.session())
                .param("code", "stolen")
                .param("state", start.params().get("state"))
        ).andExpect(redirectedUrl(ORIGIN + "/login?oauthError=google"));
        start = start();
        start.params().put("code_challenge", "tampered");
        assertEquals(
            ORIGIN + "/login?oauthError=github",
            callback(start, "valid").getResponse().getRedirectedUrl()
        );
        verifyNoInteractions(oauth);
    }

    static String variant(HttpExchange e) {
        String bearer = e.getRequestHeaders().getFirst("Authorization");
        return bearer == null || !bearer.startsWith("Bearer ")
            ? null
            : tokens.get(bearer.substring(7));
    }

    static Map<String, String> params(String query) {
        var result = new HashMap<String, String>();
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            result.put(
                URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                URLDecoder.decode(pair.length == 2 ? pair[1] : "", StandardCharsets.UTF_8)
            );
        }
        return result;
    }

    static void token(HttpExchange e) throws java.io.IOException {
        try {
            var request = params(
                new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
            );
            var authorization = codes.remove(request.get("code"));
            String challenge = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(
                        request
                            .getOrDefault("code_verifier", "")
                            .getBytes(StandardCharsets.US_ASCII)
                    )
                );
            if (
                authorization == null ||
                !"github-test".equals(request.get("client_id")) ||
                !"github-secret".equals(request.get("client_secret")) ||
                !challenge.equals(authorization.get("code_challenge")) ||
                !(ORIGIN + "/api/auth/oauth2/callback/github").equals(request.get("redirect_uri"))
            ) {
                json(e, 400, "{\"error\":\"invalid_grant\"}");
                return;
            }
            String token = UUID.randomUUID().toString();
            tokens.put(token, authorization.get("variant"));
            json(
                e,
                200,
                "{\"access_token\":\"" +
                    token +
                    "\",\"token_type\":\"bearer\",\"scope\":\"read:user,user:email\"}"
            );
        } catch (Exception ex) {
            json(e, 500, "{\"error\":\"server_error\"}");
        }
    }

    static void json(HttpExchange e, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().add("Content-Type", "application/json");
        e.sendResponseHeaders(status, bytes.length);
        try (var out = e.getResponseBody()) {
            out.write(bytes);
        }
    }
}
