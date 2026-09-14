package io.neo4flix.user;

import io.neo4flix.common.ApiException;

/** The property names used in queries come only from this closed list. */
enum OAuthProvider {
    GOOGLE("google", "Google", "googleSubject"),
    GITHUB("github", "GitHub", "githubSubject");

    final String id;
    final String label;
    final String subjectProperty;

    OAuthProvider(String id, String label, String subjectProperty) {
        this.id = id;
        this.label = label;
        this.subjectProperty = subjectProperty;
    }

    static OAuthProvider fromId(String id) {
        for (var provider : values()) if (provider.id.equals(id)) return provider;
        throw new ApiException(404, "Unknown sign-in provider");
    }
}
