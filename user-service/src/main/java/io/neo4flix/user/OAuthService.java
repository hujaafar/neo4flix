package io.neo4flix.user;

import io.neo4flix.common.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OAuthService {

    private final Graph graph;
    private final AuthService auth;

    public OAuthService(Graph graph, AuthService auth) {
        this.graph = graph;
        this.auth = auth;
    }

    record Pending(
        GoogleIdentity identity,
        String mode,
        String userId,
        long version,
        boolean twoFactor,
        String returnUrl,
        long expires
    ) {}

    @SuppressWarnings("unchecked")
    Pending begin(GoogleIdentity identity, String returnUrl) {
        var linked = graph.list(
            "MATCH (u:User {googleSubject:$subject}) RETURN properties(u) AS user",
            Map.of("subject", identity.subject())
        );
        Map<String, Object> user = linked.isEmpty()
            ? null
            : (Map<String, Object>) linked.get(0).get("user");
        String mode = "login";
        if (user == null) {
            var existing = graph.list(
                "MATCH (u:User {email:$email}) RETURN properties(u) AS user",
                Map.of("email", identity.email())
            );
            user = existing.isEmpty() ? null : (Map<String, Object>) existing.get(0).get("user");
            mode = user == null ? "register" : "link";
            if (user != null && user.containsKey("googleSubject")) throw new ApiException(
                409,
                "A different Google account is already connected. Sign in with your password."
            );
        }
        boolean twoFactor = user != null && Boolean.TRUE.equals(user.get("twoFactorEnabled"));
        return new Pending(
            identity,
            mode,
            user == null ? "" : (String) user.get("id"),
            user == null ? 0 : AuthService.number(user, "tokenVersion", 0),
            twoFactor,
            safeReturnUrl(returnUrl),
            Instant.now().getEpochSecond() + 300
        );
    }

    AuthService.Session complete(Pending pending, String name, String password, String code) {
        if (pending.expires() <= Instant.now().getEpochSecond()) throw new ApiException(
            401,
            "Google sign-in expired. Please start again."
        );
        var identity = pending.identity();
        Map<String, Object> user;
        if (pending.mode().equals("register")) {
            if (
                name == null || name.strip().length() < 2 || name.strip().length() > 80
            ) throw new ApiException(400, "Use a display name between 2 and 80 characters.");
            // Creating the user and binding the subject are one atomic write, with unique constraints.
            user = auth.register(identity.email(), name, password, identity.subject());
        } else {
            user = pending.mode().equals("link")
                ? auth.authenticate(identity.email(), password == null ? "" : password, code)
                : auth.authenticateGoogle(identity.subject(), code);
            if (
                !pending.userId().equals(user.get("id")) ||
                pending.version() != AuthService.number(user, "tokenVersion", 0)
            ) throw new ApiException(
                401,
                "Account security changed. Please start Google sign-in again."
            );
            if (pending.mode().equals("link")) {
                var rows = graph.write(tx ->
                    tx
                        .run(
                            "MATCH (u:User {id:$id}) SET u.authLock=coalesce(u.authLock,0)+1 WITH u WHERE u.tokenVersion=$version AND u.googleSubject IS NULL SET u.googleSubject=$subject RETURN u.id AS id",
                            Map.of(
                                "id",
                                pending.userId(),
                                "version",
                                pending.version(),
                                "subject",
                                identity.subject()
                            )
                        )
                        .list()
                );
                if (rows.isEmpty()) throw new ApiException(
                    409,
                    "Account security changed. Please start again."
                );
                // Keep the version proven above: a concurrent security change must make session() fail.
                user = new HashMap<>(user);
                user.put("googleSubject", identity.subject());
            }
        }
        return auth.session(user);
    }

    static String safeReturnUrl(String value) {
        // Only application routes; disallow encoded separators, controls and callback loops.
        if (
            value == null ||
            value.length() > 2000 ||
            !value.startsWith("/") ||
            value.startsWith("//") ||
            value.contains("\\") ||
            value.chars().anyMatch(c -> c < 32 || c == 127)
        ) return "/";
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.matches(".*%(?:2f|5c|0[0-9a-f]|1[0-9a-f]|7f).*")) return "/";
        String path = value.split("[?#]", 2)[0];
        return path.equals("/") ||
            path.matches("/(?:movies|share)/[A-Za-z0-9-]+") ||
            Set.of(
                "/recommendations",
                "/watchlist",
                "/ratings",
                "/shares",
                "/account",
                "/admin",
                "/graph"
            ).contains(path)
            ? value
            : "/";
    }
}
