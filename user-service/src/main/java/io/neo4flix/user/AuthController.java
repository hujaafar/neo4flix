package io.neo4flix.user;

import io.neo4flix.common.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final String origin;
    private final boolean secure;

    public AuthController(
        AuthService auth,
        @Value("${app.origin}") String origin,
        @Value("${app.cookie-secure}") boolean secure
    ) {
        this.auth = auth;
        this.origin = origin;
        this.secure = secure;
    }

    public record Registration(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 2, max = 80) String name,
        @NotBlank @Size(max = 72) String password
    ) {}

    public record Login(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password,
        @Pattern(regexp = "[0-9]{6}|^$") String code
    ) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(
        @Valid @RequestBody Registration body,
        HttpServletRequest request
    ) {
        checkOrigin(request);
        return response(
            auth.session(auth.register(body.email(), body.name(), body.password())),
            201
        );
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody Login body, HttpServletRequest request) {
        checkOrigin(request);
        return response(
            auth.session(auth.authenticate(body.email(), body.password(), body.code())),
            200
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
        @CookieValue(name = "neo4flix_refresh", required = false) String cookie,
        HttpServletRequest request
    ) {
        checkOrigin(request);
        return response(auth.refresh(cookie), 200);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
        @CookieValue(name = "neo4flix_refresh", required = false) String cookie,
        HttpServletRequest request
    ) {
        checkOrigin(request);
        auth.logout(cookie);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO))
            .build();
    }

    private void checkOrigin(HttpServletRequest request) {
        if (!origin.equals(request.getHeader("Origin"))) throw new ApiException(
            403,
            "Request origin is not allowed"
        );
    }

    private ResponseEntity<?> response(AuthService.Session session, int status) {
        return ResponseEntity.status(status)
            .header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), Duration.ofDays(7)))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(
                Map.of(
                    "accessToken",
                    session.accessToken(),
                    "expiresIn",
                    900,
                    "user",
                    session.user()
                )
            );
    }

    private String cookie(String token, Duration age) {
        return ResponseCookie.from("neo4flix_refresh", token)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path("/api/auth")
            .maxAge(age)
            .build()
            .toString();
    }
}
