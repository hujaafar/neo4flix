package io.neo4flix.user;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import io.neo4flix.common.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final Graph graph;
    private final PasswordEncoder passwords;
    private final SecretCipher cipher;
    private final JwtEncoder jwtEncoder;
    private final String dummyHash;
    private final SecureRandom random = new SecureRandom();

    public AuthService(
        Graph graph,
        PasswordEncoder passwords,
        SecretCipher cipher,
        @Value("${app.jwt-private-key}") String privatePath,
        @Value("${app.jwt-public-key}") String publicPath
    ) throws Exception {
        this.graph = graph;
        this.passwords = passwords;
        this.cipher = cipher;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
        var factory = KeyFactory.getInstance("RSA");
        var privateKey = (RSAPrivateKey) factory.generatePrivate(
            new PKCS8EncodedKeySpec(pem(privatePath))
        );
        var publicKey = (RSAPublicKey) factory.generatePublic(
            new X509EncodedKeySpec(pem(publicPath))
        );
        var key = new com.nimbusds.jose.jwk.RSAKey.Builder(publicKey)
            .privateKey(privateKey)
            .keyID("neo4flix-1")
            .build();
        jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    private static byte[] pem(String path) throws Exception {
        return Base64.getDecoder().decode(
            Files.readString(Path.of(path)).replaceAll("-----[^-]+-----", "").replaceAll("\\s", "")
        );
    }

    public static void strongPassword(String password) {
        if (
            password == null ||
            password.length() < 12 ||
            password.getBytes(StandardCharsets.UTF_8).length > 72 ||
            !password.matches("(?s).*[A-Z].*") ||
            !password.matches("(?s).*[a-z].*") ||
            !password.matches("(?s).*[0-9].*") ||
            !password.matches("(?s).*[^A-Za-z0-9\\s].*")
        ) throw new ApiException(
            400,
            "Use 12 or more characters with uppercase, lowercase, a number and a symbol (maximum 72 UTF-8 bytes)"
        );
    }

    public Map<String, Object> register(String email, String name, String password) {
        strongPassword(password);
        String id = UUID.randomUUID().toString();
        var p = Map.<String, Object>of(
            "id",
            id,
            "email",
            email.strip().toLowerCase(Locale.ROOT),
            "name",
            name.strip(),
            "hash",
            passwords.encode(password)
        );
        graph.execute(
            "CREATE (u:User {id:$id,email:$email,name:$name,passwordHash:$hash,role:'USER',twoFactorEnabled:false,tokenVersion:0,failedAttempts:0,lockedUntil:0,lastTotpStep:-1,createdAt:toString(datetime())})",
            p
        );
        return user(id);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> user(String id) {
        return (Map<String, Object>) graph
            .one("MATCH (u:User {id:$id}) RETURN properties(u) AS user", Map.of("id", id))
            .get("user");
    }

    public Map<String, Object> profile(Map<String, Object> user) {
        return Map.of(
            "id",
            user.get("id"),
            "name",
            user.get("name"),
            "email",
            user.get("email"),
            "role",
            user.get("role"),
            "twoFactorEnabled",
            user.get("twoFactorEnabled")
        );
    }

    public Map<String, Object> authenticate(String email, String password, String code) {
        long now = Instant.now().getEpochSecond();
        var attempt = graph.write(tx -> {
            var result = tx.run(
                "MATCH (u:User {email:$email}) SET u.authLock=coalesce(u.authLock,0)+1 RETURN properties(u) AS user",
                Map.of("email", email.strip().toLowerCase(Locale.ROOT))
            );
            if (!result.hasNext()) {
                passwords.matches(password, dummyHash);
                return Map.<String, Object>of(
                    "error",
                    "Invalid email, password or authenticator code"
                );
            }
            Map<String, Object> u = result.single().get("user").asMap();
            if (number(u, "lockedUntil", 0) > now) return Map.<String, Object>of("locked", true);
            boolean correct = passwords.matches(password, (String) u.get("passwordHash"));
            long step = -1;
            if (correct && Boolean.TRUE.equals(u.get("twoFactorEnabled"))) {
                step = Totp.verify(
                    cipher.decrypt((String) u.get("totpSecret")),
                    code,
                    number(u, "lastTotpStep", -1),
                    now
                );
                correct = step >= 0;
            }
            if (!correct) {
                long failures = number(u, "failedAttempts", 0) + 1;
                tx.run(
                    "MATCH (u:User {id:$id}) SET u.failedAttempts=$failures,u.lockedUntil=$until",
                    Map.of(
                        "id",
                        u.get("id"),
                        "failures",
                        failures >= 5 ? 0 : failures,
                        "until",
                        failures >= 5 ? now + 60 : 0
                    )
                ).consume();
                return Map.<String, Object>of(
                    "error",
                    "Invalid email, password or authenticator code"
                );
            }
            tx.run(
                "MATCH (u:User {id:$id}) SET u.failedAttempts=0,u.lockedUntil=0,u.lastTotpStep=$step",
                Map.of("id", u.get("id"), "step", step)
            ).consume();
            return u;
        });
        if (attempt.containsKey("locked")) throw new ApiException(
            429,
            "Too many attempts. Try again in one minute."
        );
        if (attempt.containsKey("error")) throw new ApiException(
            401,
            (String) attempt.get("error")
        );
        return attempt;
    }

    public record Session(String accessToken, String refreshToken, Map<String, Object> user) {}

    public Session session(Map<String, Object> user) {
        String raw = randomToken();
        long expires = Instant.now().plus(Duration.ofDays(7)).getEpochSecond();
        graph.write(tx -> {
            var result = tx.run(
                "MATCH (u:User {id:$user}) SET u.authLock=coalesce(u.authLock,0)+1 WITH u WHERE u.tokenVersion=$version CREATE (u)-[:HAS_SESSION]->(:Refresh {hash:$hash,expires:$expires,used:false}) RETURN u.id AS id",
                Map.of(
                    "user",
                    user.get("id"),
                    "version",
                    number(user, "tokenVersion", 0),
                    "hash",
                    hash(raw),
                    "expires",
                    expires
                )
            );
            if (!result.hasNext()) throw new ApiException(
                401,
                "Session changed. Please sign in again."
            );
            result.consume();
            return null;
        });
        return new Session(access(user), raw, profile(user));
    }

    public Session refresh(String raw) {
        if (raw == null || raw.length() > 150) throw new ApiException(401, "Please sign in");
        String next = randomToken();
        long now = Instant.now().getEpochSecond();
        var result = graph.write(tx -> {
            var r = tx.run(
                "MATCH (u:User)-[:HAS_SESSION]->(r:Refresh {hash:$hash}) SET u.authLock=coalesce(u.authLock,0)+1 RETURN properties(u) AS user,properties(r) AS refresh",
                Map.of("hash", hash(raw))
            );
            if (!r.hasNext()) return Map.<String, Object>of("error", true);
            var row = r.single();
            var u = row.get("user").asMap();
            var refresh = row.get("refresh").asMap();
            if (Boolean.TRUE.equals(refresh.get("used"))) {
                tx.run(
                    "MATCH (u:User {id:$id}) SET u.tokenVersion=u.tokenVersion+1 WITH u MATCH (u)-[:HAS_SESSION]->(r:Refresh) DETACH DELETE r",
                    Map.of("id", u.get("id"))
                ).consume();
                return Map.<String, Object>of("error", true);
            }
            if (number(refresh, "expires", 0) <= now) return Map.<String, Object>of("error", true);
            tx.run(
                "MATCH (u:User {id:$id})-[:HAS_SESSION]->(r:Refresh {hash:$old}) SET r.used=true CREATE (u)-[:HAS_SESSION]->(:Refresh {hash:$next,expires:$expires,used:false})",
                Map.of(
                    "id",
                    u.get("id"),
                    "old",
                    hash(raw),
                    "next",
                    hash(next),
                    "expires",
                    number(refresh, "expires", 0)
                )
            ).consume();
            return u;
        });
        if (result.containsKey("error")) throw new ApiException(
            401,
            "Session expired. Please sign in again."
        );
        return new Session(access(result), next, profile(result));
    }

    public void logout(String raw) {
        if (raw == null) return;
        graph.execute(
            "MATCH (u:User)-[:HAS_SESSION]->(:Refresh {hash:$hash}) SET u.tokenVersion=u.tokenVersion+1 WITH u MATCH (u)-[:HAS_SESSION]->(r:Refresh) DETACH DELETE r",
            Map.of("hash", hash(raw))
        );
    }

    public String access(Map<String, Object> user) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
            .issuer("neo4flix")
            .audience(List.of("neo4flix-api"))
            .subject((String) user.get("id"))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .id(UUID.randomUUID().toString())
            .claim("roles", List.of(user.get("role")))
            .claim("ver", number(user, "tokenVersion", 0))
            .build();
        return jwtEncoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(SignatureAlgorithm.RS256).keyId("neo4flix-1").build(),
                    claims
                )
            )
            .getTokenValue();
    }

    private String randomToken() {
        byte[] value = new byte[48];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static long number(Map<String, Object> map, String key, long fallback) {
        return map.get(key) instanceof Number n ? n.longValue() : fallback;
    }
}
