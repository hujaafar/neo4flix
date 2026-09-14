package io.neo4flix.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
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

/** Real Spring authorization-code/PKCE/OIDC filters against a local signed-token provider.
 * Only application persistence/session issuance is mocked here; no Google credentials are used. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(
    classes = {
        OAuthSecurityTest.Config.class,
        OAuthSecurity.class,
        OAuthController.class,
        ApiErrors.class,
    }
)
@TestPropertySource(
    properties = {
        "app.origin=https://localhost:9443",
        "app.oauth2.google-client-id=test-client",
        "app.oauth2.google-client-secret=test-secret",
    }
)
class OAuthSecurityTest {

    static final String ORIGIN = "https://localhost:9443";
    static final Map<String, Map<String, String>> codes = new ConcurrentHashMap<>();
    static final RSAKey key;
    static final HttpServer provider;
    static final String providerUrl;

    static {
        try {
            key = new RSAKeyGenerator(2048).keyID("test-key").generate();
            provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            providerUrl = "http://127.0.0.1:" + provider.getAddress().getPort();
            provider.createContext("/jwks", e ->
                json(e, 200, new JWKSet(key.toPublicJWK()).toString())
            );
            provider.createContext("/userinfo", e ->
                json(
                    e,
                    200,
                    "{\"sub\":\"google-subject\",\"email\":\"oauth@example.test\",\"email_verified\":true,\"name\":\"OAuth Viewer\"}"
                )
            );
            provider.createContext("/token", OAuthSecurityTest::token);
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
                ClientRegistration.withRegistrationId("google")
                    .clientId("test-client")
                    .clientSecret("test-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(ORIGIN + "/api/auth/oauth2/callback/google")
                    .scope("openid", "profile", "email")
                    .authorizationUri(providerUrl + "/authorize")
                    .tokenUri(providerUrl + "/token")
                    .jwkSetUri(providerUrl + "/jwks")
                    .issuerUri("https://accounts.google.com")
                    .userInfoUri(providerUrl + "/userinfo")
                    .userNameAttributeName("sub")
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
            new AuthService.Session("application-jwt", "random-refresh", Map.of("id", "user-1"))
        );
    }

    @AfterAll
    static void stop() {
        provider.stop(0);
    }

    record Start(MockHttpSession session, Map<String, String> params) {}

    Start start(String returnUrl) throws Exception {
        var result = mvc
            .perform(get("/api/auth/oauth2/authorize/google").param("returnUrl", returnUrl))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        return new Start(
            (MockHttpSession) result.getRequest().getSession(false),
            params(URI.create(result.getResponse().getRedirectedUrl()).getRawQuery())
        );
    }

    MvcResult callback(Start start, String variant) throws Exception {
        String code = UUID.randomUUID().toString();
        var attributes = new HashMap<>(start.params());
        attributes.put("variant", variant);
        codes.put(code, attributes);
        return mvc
            .perform(
                get("/api/auth/oauth2/callback/google")
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
    void codeFlowUsesPkceStateNonceAndRequiresLocalCompletionBeforeIssuingJwt() throws Exception {
        Start start = start("/share/test-share");
        assertEquals("S256", start.params().get("code_challenge_method"));
        assertTrue(start.params().get("code_challenge").length() >= 43);
        assertNotNull(start.params().get("state"));
        assertNotNull(start.params().get("nonce"));
        assertFalse(start.params().containsKey("client_secret"));
        var callback = callback(start, "valid");
        assertEquals(ORIGIN + "/oauth2/complete", callback.getResponse().getRedirectedUrl());
        verify(oauth).begin(
            new OAuthIdentity(
                OAuthProvider.GOOGLE,
                "google-subject",
                "oauth@example.test",
                "OAuth Viewer"
            ),
            "/share/test-share"
        );
        verify(oauth, never()).complete(any(), any(), any(), any());
        var session = (MockHttpSession) callback.getRequest().getSession(false);
        assertNotEquals(start.session().getId(), session.getId());
        assertNull(session.getAttribute("SPRING_SECURITY_CONTEXT"));
        mvc.perform(get("/api/auth/oauth2/pending").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.twoFactor").value(true));
        var complete = mvc
            .perform(
                post("/api/auth/oauth2/complete")
                    .session(session)
                    .header("Origin", ORIGIN)
                    .contentType("application/json")
                    .content("{\"name\":\"\",\"password\":\"\",\"code\":\"123456\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("application-jwt"))
            .andReturn();
        String cookie = complete.getResponse().getHeader("Set-Cookie");
        assertTrue(
            cookie.contains("HttpOnly") &&
                cookie.contains("Secure") &&
                cookie.contains("SameSite=Strict")
        );
        assertTrue(session.isInvalid());
        mvc.perform(
            post("/api/auth/oauth2/complete")
                .header("Origin", ORIGIN)
                .contentType("application/json")
                .content("{}")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongStateMissingBrowserSessionAndProviderCancellation() throws Exception {
        Start start = start("/");
        mvc.perform(
            get("/api/auth/oauth2/callback/google")
                .session(start.session())
                .param("code", "stolen")
                .param("state", "wrong")
        ).andExpect(redirectedUrl(ORIGIN + "/login?oauthError=google"));
        mvc.perform(
            get("/api/auth/oauth2/callback/google")
                .param("code", "stolen")
                .param("state", start.params().get("state"))
        ).andExpect(redirectedUrl(ORIGIN + "/login?oauthError=google"));
        start = start("/");
        mvc.perform(
            get("/api/auth/oauth2/callback/google")
                .session(start.session())
                .param("error", "access_denied")
                .param("state", start.params().get("state"))
        ).andExpect(redirectedUrl(ORIGIN + "/login?oauthError=google"));
        verifyNoInteractions(oauth);
    }

    @Test
    void rejectsForgedExpiredWrongAudienceWrongIssuerWrongNonceAndUnverifiedIdentities()
        throws Exception {
        for (String variant : List.of(
            "signature",
            "expired",
            "audience",
            "issuer",
            "nonce",
            "unverified"
        )) {
            var result = callback(start("/"), variant);
            assertEquals(
                ORIGIN + "/login?oauthError=google",
                result.getResponse().getRedirectedUrl(),
                variant
            );
        }
        verifyNoInteractions(oauth);
    }

    @Test
    void completionRequiresSameOriginAndExpiredOrCancelledProofCannotBeUsed() throws Exception {
        var result = callback(start("https://evil.example"), "valid");
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(
            post("/api/auth/oauth2/complete")
                .session(session)
                .header("Origin", "https://evil.example")
                .contentType("application/json")
                .content("{}")
        ).andExpect(status().isForbidden());
        mvc.perform(
            post("/api/auth/oauth2/complete")
                .session(session)
                .contentType("application/json")
                .content("{}")
        ).andExpect(status().isForbidden());
        verify(oauth).begin(any(), eq("/"));
        verify(oauth, never()).complete(any(), any(), any(), any());
        mvc.perform(
            post("/api/auth/oauth2/cancel").session(session).header("Origin", ORIGIN)
        ).andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        var expired = new MockHttpSession();
        expired.setAttribute(
            OAuthController.PENDING,
            new OAuthService.Pending(
                new OAuthIdentity(OAuthProvider.GOOGLE, "s", "a@example.test", "Viewer"),
                "login",
                "id",
                0,
                false,
                "/",
                1
            )
        );
        mvc.perform(get("/api/auth/oauth2/pending").session(expired)).andExpect(
            status().isUnauthorized()
        );
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

    static void token(HttpExchange exchange) {
        try {
            var request = params(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
            );
            var authorization = codes.remove(request.get("code"));
            String expectedAuth =
                "Basic " +
                Base64.getEncoder().encodeToString(
                    "test-client:test-secret".getBytes(StandardCharsets.UTF_8)
                );
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
                !expectedAuth.equals(exchange.getRequestHeaders().getFirst("Authorization")) ||
                !challenge.equals(authorization.get("code_challenge")) ||
                !request.get("redirect_uri").equals(ORIGIN + "/api/auth/oauth2/callback/google")
            ) {
                json(exchange, 400, "{\"error\":\"invalid_grant\"}");
                return;
            }
            String variant = authorization.get("variant");
            Instant now = Instant.now();
            var claims = new JWTClaimsSet.Builder()
                .issuer(
                    variant.equals("issuer")
                        ? "https://evil.example"
                        : "https://accounts.google.com"
                )
                .subject("google-subject")
                .audience(variant.equals("audience") ? "someone-else" : "test-client")
                .issueTime(Date.from(now.minusSeconds(60)))
                .expirationTime(Date.from(now.plusSeconds(variant.equals("expired") ? -600 : 300)))
                .claim("nonce", variant.equals("nonce") ? "wrong" : authorization.get("nonce"))
                .claim("email", "oauth@example.test")
                .claim("email_verified", !variant.equals("unverified"))
                .claim("name", "OAuth Viewer")
                .build();
            var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims
            );
            jwt.sign(
                new RSASSASigner(
                    variant.equals("signature") ? new RSAKeyGenerator(2048).generate() : key
                )
            );
            json(
                exchange,
                200,
                "{\"access_token\":\"provider-token\",\"token_type\":\"Bearer\",\"expires_in\":300,\"scope\":\"openid\",\"id_token\":\"" +
                    jwt.serialize() +
                    "\"}"
            );
        } catch (Exception e) {
            try {
                json(exchange, 500, "{\"error\":\"server_error\"}");
            } catch (Exception ignored) {}
        }
    }

    static void json(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
