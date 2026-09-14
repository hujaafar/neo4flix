package io.neo4flix.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.neo4flix.common.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class OAuthServiceTest {

    final Graph graph = mock(Graph.class);
    final AuthService auth = mock(AuthService.class);
    final OAuthService oauth = new OAuthService(graph, auth);
    final GoogleIdentity identity = new GoogleIdentity(
        "google-sub",
        "viewer@example.test",
        "Viewer"
    );
    final Map<String, Object> user = Map.of(
        "id",
        "user-1",
        "twoFactorEnabled",
        true,
        "tokenVersion",
        7L
    );

    @Test
    void requiresPasswordProofForAnExistingEmailAndDoesNotAutomaticallyLinkIt() {
        when(graph.list(anyString(), anyMap())).thenReturn(
            List.of(),
            List.of(Map.of("user", user))
        );
        var pending = oauth.begin(identity, "/watchlist");
        assertEquals("link", pending.mode());
        assertTrue(pending.twoFactor());
        verify(graph, never()).execute(anyString(), anyMap());
        when(auth.authenticate(identity.email(), "wrong", "123456")).thenThrow(
            new ApiException(401, "Invalid proof")
        );
        assertThrows(ApiException.class, () ->
            oauth.complete(pending, "Viewer", "wrong", "123456")
        );
        verify(auth, never()).session(anyMap());
    }

    @Test
    void linkedIdentityStillUsesLocalTotpAndDetectsSecurityChanges() {
        when(graph.list(anyString(), anyMap())).thenReturn(List.of(Map.of("user", user)));
        var pending = oauth.begin(identity, "/ratings");
        assertEquals("login", pending.mode());
        when(auth.authenticateGoogle(identity.subject(), "")).thenThrow(
            new ApiException(401, "Authenticator required")
        );
        assertThrows(ApiException.class, () -> oauth.complete(pending, "", "", ""));
        when(auth.authenticateGoogle(identity.subject(), "123456")).thenReturn(
            Map.of("id", "user-1", "tokenVersion", 8L)
        );
        assertThrows(ApiException.class, () -> oauth.complete(pending, "", "", "123456"));
        verify(auth, never()).session(anyMap());
    }

    @Test
    void rejectsReplacingADifferentGoogleIdentityEvenWhenEmailMatches() {
        when(graph.list(anyString(), anyMap())).thenReturn(
            List.of(),
            List.of(Map.of("user", Map.of("id", "user-1", "googleSubject", "different-sub")))
        );
        assertThrows(ApiException.class, () -> oauth.begin(identity, "/"));
    }

    @Test
    void newSignupCreatesSubjectAndAccountTogetherAndValidatesName() {
        when(graph.list(anyString(), anyMap())).thenReturn(List.of());
        var pending = oauth.begin(identity, "/");
        assertEquals("register", pending.mode());
        assertThrows(ApiException.class, () -> oauth.complete(pending, " ", "password", ""));
        when(
            auth.register(identity.email(), "Viewer", "StrongPassword!123", identity.subject())
        ).thenReturn(user);
        oauth.complete(pending, "Viewer", "StrongPassword!123", "");
        verify(auth).register(identity.email(), "Viewer", "StrongPassword!123", identity.subject());
        verify(auth).session(user);
    }

    @Test
    void expiredProofNeverAuthenticatesOrIssuesASession() {
        var expired = new OAuthService.Pending(
            identity,
            "login",
            "user-1",
            7,
            true,
            "/",
            Instant.now().getEpochSecond() - 1
        );
        assertThrows(ApiException.class, () -> oauth.complete(expired, "", "", "123456"));
        verifyNoInteractions(auth);
    }

    @Test
    void redirectAllowlistPreservesMovieAndCollectionLinksButRejectsExternalOrEncodedTricks() {
        for (String valid : List.of(
            "/",
            "/movies/m-1",
            "/share/s-1",
            "/recommendations?genre=Science%20Fiction",
            "/watchlist"
        ))
            assertEquals(valid, OAuthService.safeReturnUrl(valid));
        for (String bad : List.of(
            "https://evil.example",
            "//evil.example",
            "/\\evil",
            "/%2fevil",
            "/%5cevil",
            "/%0aevil",
            "/login",
            "/oauth2/complete",
            "/api/auth/oauth2/callback/google",
            "/movies/../../evil",
            "/\nevil"
        ))
            assertEquals("/", OAuthService.safeReturnUrl(bad));
    }
}
