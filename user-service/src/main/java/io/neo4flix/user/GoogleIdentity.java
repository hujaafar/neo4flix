package io.neo4flix.user;

import io.neo4flix.common.ApiException;
import java.util.Locale;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** Application checks after Spring validates the ID token signature, issuer, audience and nonce. */
record GoogleIdentity(String subject, String email, String name) {
    static GoogleIdentity from(OidcUser user) {
        String issuer = user.getIssuer().toString();
        String email = user.getEmail();
        if (
            !(
                issuer.equals("https://accounts.google.com") || issuer.equals("accounts.google.com")
            ) ||
            !Boolean.TRUE.equals(user.getEmailVerified()) ||
            email == null ||
            email.length() > 254 ||
            !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") ||
            user.getSubject() == null ||
            user.getSubject().isBlank() ||
            user.getSubject().length() > 255
        ) {
            throw new ApiException(
                401,
                "Google could not verify this account's identity and email."
            );
        }
        String name = user.getFullName();
        if (name == null || name.strip().length() < 2) name = "Film lover";
        name = name.strip();
        return new GoogleIdentity(
            user.getSubject(),
            email.toLowerCase(Locale.ROOT),
            name.substring(0, Math.min(80, name.length()))
        );
    }
}
