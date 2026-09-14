package io.neo4flix.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.neo4flix.common.*;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.neo4j.driver.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Runs the real password/TOTP authentication code; only the transactional graph adapter is stubbed. */
class OAuthTotpTest {

    @TempDir
    Path directory;

    AuthService auth;
    final Map<String, Object> user = new HashMap<>();
    final String secret = Totp.secret();

    @BeforeEach
    void setup() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        Path privateKey = directory.resolve("private.pem"),
            publicKey = directory.resolve("public.pem");
        Files.writeString(
            privateKey,
            Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded())
        );
        Files.writeString(
            publicKey,
            Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())
        );
        byte[] encryption = new byte[32];
        new SecureRandom().nextBytes(encryption);
        var cipher = new SecretCipher(Base64.getEncoder().encodeToString(encryption));
        var passwords = new BCryptPasswordEncoder(4);
        user.putAll(
            Map.of(
                "id",
                "test-user",
                "passwordHash",
                passwords.encode("TestPassword!123"),
                "twoFactorEnabled",
                true,
                "totpSecret",
                cipher.encrypt(secret),
                "lastTotpStep",
                -1L,
                "failedAttempts",
                0L,
                "lockedUntil",
                0L
            )
        );
        var graph = mock(Graph.class);
        var tx = mock(TransactionContext.class);
        when(graph.write(any())).thenAnswer(a -> {
            Function<TransactionContext, Object> operation = a.getArgument(0);
            return operation.apply(tx);
        });
        when(tx.run(anyString(), anyMap())).thenAnswer(a -> {
            String query = a.getArgument(0);
            Map<String, Object> parameters = a.getArgument(1);
            var result = mock(Result.class);
            if (query.contains("RETURN properties(u) AS user")) {
                var row = mock(org.neo4j.driver.Record.class);
                when(row.get("user")).thenReturn(Values.value(new HashMap<>(user)));
                when(result.hasNext()).thenReturn(true);
                when(result.single()).thenReturn(row);
            } else if (parameters.containsKey("step")) {
                user.put("lastTotpStep", parameters.get("step"));
                user.put("failedAttempts", 0L);
                user.put("lockedUntil", 0L);
            } else if (parameters.containsKey("failures")) {
                user.put("failedAttempts", parameters.get("failures"));
                user.put("lockedUntil", parameters.get("until"));
            }
            return result;
        });
        auth = new AuthService(
            graph,
            passwords,
            cipher,
            privateKey.toString(),
            publicKey.toString()
        );
    }

    @ParameterizedTest
    @EnumSource(OAuthProvider.class)
    void providerCannotBypassMissingInvalidOrReplayedTotp(OAuthProvider provider) {
        assertThrows(ApiException.class, () -> auth.authenticateProvider(provider, "subject", ""));
        String wrong = Totp.code(secret, Instant.now().getEpochSecond() / 30 - 10);
        // Use a malformed value in the astronomically unlikely case of a six-digit collision.
        if (Totp.verify(secret, wrong, -1, Instant.now().getEpochSecond()) >= 0) wrong = "invalid";
        final String invalid = wrong;
        assertThrows(ApiException.class, () ->
            auth.authenticateProvider(provider, "subject", invalid)
        );
        String code = Totp.code(secret, Instant.now().getEpochSecond() / 30);
        assertEquals("test-user", auth.authenticateProvider(provider, "subject", code).get("id"));
        assertThrows(ApiException.class, () ->
            auth.authenticateProvider(provider, "subject", code)
        );
    }

    @ParameterizedTest
    @EnumSource(OAuthProvider.class)
    void linkedProviderHonorsAccountLockAndPasswordLoginStillNeedsPassword(OAuthProvider provider) {
        user.put("lockedUntil", Instant.now().getEpochSecond() + 60);
        assertThrows(ApiException.class, () ->
            auth.authenticateProvider(
                provider,
                "subject",
                Totp.code(secret, Instant.now().getEpochSecond() / 30)
            )
        );
        user.put("lockedUntil", 0L);
        user.put("twoFactorEnabled", false);
        assertThrows(ApiException.class, () -> auth.authenticate("test@example.test", "wrong", ""));
        assertEquals("test-user", auth.authenticateProvider(provider, "subject", "").get("id"));
    }
}
