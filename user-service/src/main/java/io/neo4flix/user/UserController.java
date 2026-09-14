package io.neo4flix.user;

import io.neo4flix.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import org.neo4j.driver.TransactionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final Graph graph;
    private final AuthService auth;
    private final SecretCipher cipher;
    private final PasswordEncoder passwords;
    private final ServiceClient services;
    private final String ratingUrl;

    public UserController(
        Graph graph,
        AuthService auth,
        SecretCipher cipher,
        PasswordEncoder passwords,
        ServiceClient services,
        @Value("${app.rating-url}") String ratingUrl
    ) {
        this.graph = graph;
        this.auth = auth;
        this.cipher = cipher;
        this.passwords = passwords;
        this.services = services;
        this.ratingUrl = ratingUrl;
    }

    public record ProfileInput(@NotBlank @Size(min = 2, max = 80) String name) {}

    public record Proof(
        @NotBlank @Size(max = 72) String password,
        @Pattern(regexp = "[0-9]{6}|^$") String code
    ) {}

    public record PasswordInput(
        @NotBlank @Size(max = 72) String password,
        @NotBlank @Size(max = 72) String newPassword,
        @Pattern(regexp = "[0-9]{6}|^$") String code
    ) {}

    @GetMapping
    public Object profile(@AuthenticationPrincipal Jwt jwt) {
        return auth.profile(auth.user(jwt.getSubject()));
    }

    @PatchMapping
    public Object update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileInput input) {
        graph.execute(
            "MATCH (u:User {id:$id}) SET u.name=$name",
            Map.of("id", jwt.getSubject(), "name", input.name().strip())
        );
        return profile(jwt);
    }

    @GetMapping("/ratings")
    public Object history(@AuthenticationPrincipal Jwt jwt) {
        return services.get(ratingUrl + "/api/ratings/me", jwt.getTokenValue());
    }

    @PutMapping("/password")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void password(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody PasswordInput input
    ) {
        AuthService.strongPassword(input.newPassword());
        String hash = passwords.encode(input.newPassword());
        sensitive(jwt, new Proof(input.password(), input.code()), false, (tx, u) -> {
            tx.run(
                "MATCH (u:User {id:$id}) SET u.passwordHash=$hash",
                Map.of("id", jwt.getSubject(), "hash", hash)
            ).consume();
            revoke(tx, jwt.getSubject());
        });
    }

    @DeleteMapping
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Proof proof) {
        sensitive(jwt, proof, false, (tx, u) -> {
            tx.run(
                "MATCH (:User {id:$id})-[:HAS_SESSION|SHARED]->(owned) DETACH DELETE owned",
                Map.of("id", jwt.getSubject())
            ).consume();
            tx.run(
                "MATCH (u:User {id:$id}) DETACH DELETE u",
                Map.of("id", jwt.getSubject())
            ).consume();
        });
    }

    @DeleteMapping("/oauth2/{providerId}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void disconnectProvider(
        @PathVariable String providerId,
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody Proof proof
    ) {
        var provider = OAuthProvider.fromId(providerId);
        sensitive(jwt, proof, false, (tx, u) -> {
            tx.run(
                "MATCH (u:User {id:$id}) REMOVE u." + provider.subjectProperty,
                Map.of("id", jwt.getSubject())
            ).consume();
            revoke(tx, jwt.getSubject());
        });
    }

    @PostMapping("/2fa/setup")
    public Object setup(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Proof proof) {
        String secret = Totp.secret();
        String encrypted = cipher.encrypt(secret);
        long until = Instant.now().getEpochSecond() + 600;
        sensitive(jwt, proof, false, (tx, u) -> {
            if (Boolean.TRUE.equals(u.get("twoFactorEnabled"))) throw new ApiException(
                409,
                "Two-factor authentication is already enabled"
            );
            tx.run(
                "MATCH (u:User {id:$id}) SET u.pendingTotp=$secret,u.pendingUntil=$until",
                Map.of("id", jwt.getSubject(), "secret", encrypted, "until", until)
            ).consume();
        });
        String email = (String) auth.user(jwt.getSubject()).get("email");
        return Map.of(
            "secret",
            secret,
            "uri",
            "otpauth://totp/" +
                URLEncoder.encode("Neo4flix:" + email, StandardCharsets.UTF_8) +
                "?secret=" +
                secret +
                "&issuer=Neo4flix&algorithm=SHA1&digits=6&period=30"
        );
    }

    @PostMapping("/2fa/confirm")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void confirm(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Proof proof) {
        sensitive(jwt, proof, true, (tx, u) -> {
            tx.run(
                "MATCH (u:User {id:$id}) SET u.totpSecret=u.pendingTotp,u.twoFactorEnabled=true REMOVE u.pendingTotp,u.pendingUntil",
                Map.of("id", jwt.getSubject())
            ).consume();
            revoke(tx, jwt.getSubject());
        });
    }

    @DeleteMapping("/2fa")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void disable(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Proof proof) {
        sensitive(jwt, proof, false, (tx, u) -> {
            if (!Boolean.TRUE.equals(u.get("twoFactorEnabled"))) throw new ApiException(
                409,
                "Two-factor authentication is not enabled"
            );
            tx.run(
                "MATCH (u:User {id:$id}) SET u.twoFactorEnabled=false,u.lastTotpStep=-1 REMOVE u.totpSecret,u.pendingTotp,u.pendingUntil",
                Map.of("id", jwt.getSubject())
            ).consume();
            revoke(tx, jwt.getSubject());
        });
    }

    private void sensitive(
        Jwt jwt,
        Proof proof,
        boolean pending,
        BiConsumer<TransactionContext, Map<String, Object>> action
    ) {
        long now = Instant.now().getEpochSecond();
        int result = graph.write(tx -> {
            var found = tx.run(
                "MATCH (u:User {id:$id}) SET u.authLock=coalesce(u.authLock,0)+1 RETURN properties(u) AS user",
                Map.of("id", jwt.getSubject())
            );
            if (!found.hasNext()) throw new ApiException(401, "Please sign in");
            var u = found.single().get("user").asMap();
            if (AuthService.number(u, "lockedUntil", 0) > now) return 429;
            boolean valid = passwords.matches(proof.password(), (String) u.get("passwordHash"));
            long step = -1;
            if (pending || Boolean.TRUE.equals(u.get("twoFactorEnabled"))) {
                String encrypted = (String) u.get(pending ? "pendingTotp" : "totpSecret");
                if (
                    encrypted != null &&
                    (!pending || AuthService.number(u, "pendingUntil", 0) > now)
                ) step = Totp.verify(
                    cipher.decrypt(encrypted),
                    proof.code(),
                    AuthService.number(u, "lastTotpStep", -1),
                    now
                );
                valid = valid && step >= 0;
            }
            if (!valid) {
                long failures = AuthService.number(u, "failedAttempts", 0) + 1;
                tx.run(
                    "MATCH (u:User {id:$id}) SET u.failedAttempts=$attempts,u.lockedUntil=$until",
                    Map.of(
                        "id",
                        jwt.getSubject(),
                        "attempts",
                        failures >= 5 ? 0 : failures,
                        "until",
                        failures >= 5 ? now + 60 : 0
                    )
                ).consume();
                return 401;
            }
            tx.run(
                "MATCH (u:User {id:$id}) SET u.failedAttempts=0,u.lockedUntil=0,u.lastTotpStep=$step",
                Map.of("id", jwt.getSubject(), "step", step)
            ).consume();
            action.accept(tx, u);
            return 200;
        });
        if (result == 429) throw new ApiException(
            429,
            "Too many attempts. Try again in one minute."
        );
        if (result != 200) throw new ApiException(
            401,
            "Password or authenticator code is incorrect"
        );
    }

    private void revoke(TransactionContext tx, String id) {
        tx.run(
            "MATCH (u:User {id:$id}) SET u.tokenVersion=u.tokenVersion+1 WITH u OPTIONAL MATCH (u)-[:HAS_SESSION]->(r:Refresh) DETACH DELETE r",
            Map.of("id", id)
        ).consume();
    }

    @GetMapping("/watchlist")
    public List<?> watchlist(@AuthenticationPrincipal Jwt jwt) {
        return graph
            .list(
                "MATCH (:User {id:$user})-[w:WATCHLISTED]->(m:Movie) OPTIONAL MATCH (:User)-[r:RATED]->(m) WITH m,w,coalesce(avg(r.score),0.0) AS averageRating,count(r) AS ratingCount RETURN m{.*,averageRating:averageRating,ratingCount:ratingCount,watchlistNote:w.note} AS movie,w.addedAt AS added ORDER BY added DESC",
                Map.of("user", jwt.getSubject())
            )
            .stream()
            .map(x -> x.get("movie"))
            .toList();
    }

    public record WatchInput(@Size(max = 500) String note) {}

    @RequestMapping(path = "/watchlist/{id}", method = { RequestMethod.POST, RequestMethod.PUT })
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void saveWatch(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String id,
        @Valid @RequestBody(required = false) WatchInput input
    ) {
        graph.write(tx -> {
            var r = tx.run(
                "MATCH (u:User {id:$user}) SET u.mutationVersion=coalesce(u.mutationVersion,0)+1 WITH u MATCH (m:Movie {id:$id}) MERGE (u)-[w:WATCHLISTED]->(m) ON CREATE SET w.addedAt=toString(datetime()) SET w.note=$note RETURN m.id AS id",
                Map.of(
                    "user",
                    jwt.getSubject(),
                    "id",
                    id,
                    "note",
                    input == null || input.note() == null ? "" : input.note()
                )
            );
            if (!r.hasNext()) throw new ApiException(404, "Movie not found");
            r.consume();
            return null;
        });
    }

    @DeleteMapping("/watchlist/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void removeWatch(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        graph.execute(
            "MATCH (:User {id:$user})-[w:WATCHLISTED]->(:Movie {id:$id}) DELETE w",
            Map.of("user", jwt.getSubject(), "id", id)
        );
    }
}
