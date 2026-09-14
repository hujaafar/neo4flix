package io.neo4flix.user;

import io.neo4flix.common.ApiException;
import java.util.Locale;

/** Constructed only from a server-verified provider response, never a request body. */
record OAuthIdentity(OAuthProvider provider, String subject, String email, String name) {
    OAuthIdentity {
        if (
            provider == null ||
            subject == null ||
            subject.isBlank() ||
            subject.length() > 255 ||
            email == null ||
            email.length() > 254 ||
            !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
        ) {
            throw new ApiException(
                401,
                "The sign-in provider could not verify this identity and email."
            );
        }
        email = email.toLowerCase(Locale.ROOT);
        if (name == null || name.strip().length() < 2) name = "Film lover";
        name = name.strip();
        name = name.substring(0, Math.min(80, name.length()));
    }
}
