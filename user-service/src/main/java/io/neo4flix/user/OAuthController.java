package io.neo4flix.user;

import io.neo4flix.common.ApiException;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/oauth2")
public class OAuthController {

    static final String PENDING = "neo4flix.oauth.pending";
    private final OAuthService oauth;
    private final AuthController auth;
    private final boolean enabled;

    public OAuthController(
        OAuthService oauth,
        AuthController auth,
        @Value("${app.oauth2.google-client-id:}") String clientId,
        @Value("${app.oauth2.google-client-secret:}") String clientSecret
    ) {
        this.oauth = oauth;
        this.auth = auth;
        enabled = !clientId.isBlank() && !clientSecret.isBlank();
    }

    @GetMapping("/providers")
    public Object providers() {
        return List.of(Map.of("id", "google", "name", "Google", "enabled", enabled));
    }

    @GetMapping("/pending")
    public ResponseEntity<?> pending(HttpServletRequest request) {
        var pending = pendingIdentity(request.getSession(false));
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(
                Map.of(
                    "mode",
                    pending.mode(),
                    "email",
                    pending.identity().email(),
                    "name",
                    pending.identity().name(),
                    "twoFactor",
                    pending.twoFactor(),
                    "returnUrl",
                    pending.returnUrl()
                )
            );
    }

    public record Completion(
        @Size(max = 80) String name,
        @Size(max = 72) String password,
        @Pattern(regexp = "[0-9]{6}|^$") String code
    ) {}

    @PostMapping("/complete")
    public ResponseEntity<?> complete(
        @Valid @RequestBody Completion input,
        HttpServletRequest request
    ) {
        auth.checkOrigin(request);
        HttpSession session = request.getSession(false);
        if (session == null) throw expired();
        // One browser-bound proof may mint at most one application session, even with concurrent requests.
        synchronized (session) {
            var pending = pendingIdentity(session);
            int attempts = session.getAttribute("oauthAttempts") instanceof Integer n ? n : 0;
            if (attempts >= 5) {
                session.invalidate();
                throw expired();
            }
            session.setAttribute("oauthAttempts", attempts + 1);
            var result = oauth.complete(pending, input.name(), input.password(), input.code());
            session.invalidate();
            return auth.response(result, 200);
        }
    }

    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(HttpServletRequest request) {
        auth.checkOrigin(request);
        if (request.getSession(false) != null) request.getSession(false).invalidate();
    }

    private OAuthService.Pending pendingIdentity(HttpSession session) {
        if (!enabled || session == null) throw expired();
        try {
            if (
                session.getAttribute(PENDING) instanceof OAuthService.Pending pending &&
                pending.expires() > Instant.now().getEpochSecond()
            ) return pending;
        } catch (IllegalStateException ignored) {
            throw expired();
        }
        throw expired();
    }

    private ApiException expired() {
        return new ApiException(
            401,
            "Google sign-in expired or is unavailable. Please start again."
        );
    }
}
