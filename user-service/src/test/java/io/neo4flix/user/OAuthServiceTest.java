package io.neo4flix.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.neo4flix.common.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OAuthServiceTest {

    final Graph graph = mock(Graph.class);
    final AuthService auth = mock(AuthService.class);
    final OAuthService oauth = new OAuthService(graph, auth);

    OAuthIdentity identity(OAuthProvider provider) {
        return new OAuthIdentity(provider, "provider-sub", "viewer@example.test", "Viewer");
    }

    final Map<String, Object> user = Map.of(
        "id",
        "user-1",
        "twoFactorEnabled",
        true,
        "tokenVersion",
        7L
    );

    @ParameterizedTest
    @EnumSource(OAuthProvider.class)
    void requiresPasswordProofForAnExistingEmailAndDoesNotAutomaticallyLinkIt(
        OAuthProvider provider
    ) {
        var identity = identity(provider);
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

    @ParameterizedTest
    @EnumSource(OAuthProvider.class)
    void linkedIdentityStillUsesLocalTotpAndDetectsSecurityChanges(OAuthProvider provider) {
        var identity = identity(provider);
        when(graph.list(anyString(), anyMap())).thenReturn(List.of(Map.of("user", user)));
        var pending = oauth.begin(identity, "/ratings");
        assertEquals("login", pending.mode());
        when(auth.authenticateProvider(provider, identity.subject(), "")).thenThrow(
            new ApiException(401, "Authenticator required")
        );
        assertThrows(ApiException.class, () -> oauth.complete(pending, "", "", ""));
        when(auth.authenticateProvider(provider, identity.subject(), "123456")).thenReturn(
            Map.of("id", "user-1", "tokenVersion", 8L)
        );
        assertThrows(ApiException.class, () -> oauth.complete(pending, "", "", "123456"));
        verify(auth, never()).session(anyMap());
    }

    @ParameterizedTest
    @EnumSource(OAuthProvider.class)
    void rejectsReplacingADifferentProviderIdentityEvenWhenEmailMatches(OAuthProvider provider) {
        var identity = identity(provider);
        when(graph.list(anyString(), anyMap())).thenReturn(
            List.of(),
            List.of(
                Map.of("user", Map.of("id", "user-1", provider.subjectProperty, "different-sub"))
            )
        );
        assertThrows(ApiException.class, () -> oauth.begin(identity, "/"));
    }

    @ParameterizedTest
    @EnumSource(OAuthProvider.class)
    void newSignupCreatesSubjectAndAccountTogetherAndValidatesName(OAuthProvider provider) {
        var identity = identity(provider);
        when(graph.list(anyString(), anyMap())).thenReturn(List.of());
        var pending = oauth.begin(identity, "/");
        assertEquals("register", pending.mode());
        assertThrows(ApiException.class, () -> oauth.complete(pending, " ", "password", ""));
        when(
            auth.register(
                identity.email(),
                "Viewer",
                "StrongPassword!123",
                provider,
                identity.subject()
            )
        ).thenReturn(user);
        oauth.complete(pending, "Viewer", "StrongPassword!123", "");
        verify(auth).register(
            identity.email(),
            "Viewer",
            "StrongPassword!123",
            provider,
            identity.subject()
        );
        verify(auth).session(user);
    }

    @ParameterizedTest
    @EnumSource(OAuthProvider.class)
    void expiredProofNeverAuthenticatesOrIssuesASession(OAuthProvider provider) {
        var identity = identity(provider);
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

    @ParameterizedTest
    @EnumSource(OAuthProvider.class)
    void linkingPreservesTheOtherProviderAndAuthenticatedSecurityVersion(OAuthProvider provider) {
        var identity = identity(provider);
        var other = provider == OAuthProvider.GOOGLE ? OAuthProvider.GITHUB : OAuthProvider.GOOGLE;
        var account = new HashMap<>(user);
        account.put(other.subjectProperty, "other-provider-subject");
        when(graph.list(anyString(), anyMap())).thenReturn(
            List.of(),
            List.of(Map.of("user", account))
        );
        var pending = oauth.begin(identity, "/");
        assertEquals("link", pending.mode());
        when(auth.authenticate(identity.email(), "password", "123456")).thenReturn(account);
        when(graph.write(any())).thenReturn(List.of(mock(org.neo4j.driver.Record.class)));
        oauth.complete(pending, "", "password", "123456");
        var expected = new HashMap<>(account);
        expected.put(provider.subjectProperty, identity.subject());
        verify(auth).session(expected);
        verify(graph).list(contains("{" + provider.subjectProperty + ":$subject}"), anyMap());
    }
}
