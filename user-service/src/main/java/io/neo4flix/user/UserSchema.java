package io.neo4flix.user;

import io.neo4flix.common.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;

@Configuration
public class UserSchema {

    @Bean
    ApplicationRunner users(
        Graph graph,
        AuthService auth,
        @Value("${app.bootstrap-admin-email}") String email,
        @Value("${app.bootstrap-admin-password}") String password
    ) {
        return args -> {
            graph.execute(
                "CREATE CONSTRAINT user_github_subject IF NOT EXISTS FOR (u:User) REQUIRE u.githubSubject IS UNIQUE",
                Map.of()
            );
            graph.execute(
                "CREATE CONSTRAINT user_google_subject IF NOT EXISTS FOR (u:User) REQUIRE u.googleSubject IS UNIQUE",
                Map.of()
            );
            graph.execute(
                "CREATE CONSTRAINT user_id IF NOT EXISTS FOR (u:User) REQUIRE u.id IS UNIQUE",
                Map.of()
            );
            graph.execute(
                "CREATE CONSTRAINT user_email IF NOT EXISTS FOR (u:User) REQUIRE u.email IS UNIQUE",
                Map.of()
            );
            graph.execute(
                "CREATE CONSTRAINT refresh_hash IF NOT EXISTS FOR (r:Refresh) REQUIRE r.hash IS UNIQUE",
                Map.of()
            );
            graph.execute(
                "CREATE CONSTRAINT share_id IF NOT EXISTS FOR (s:Share) REQUIRE s.id IS UNIQUE",
                Map.of()
            );
            graph.execute(
                "MATCH (r:Refresh) WHERE r.expires < timestamp()/1000 DETACH DELETE r",
                Map.of()
            );
            if (
                !email.isBlank() &&
                !password.isBlank() &&
                graph
                    .list(
                        "MATCH (u:User {email:$email}) RETURN u.id AS id",
                        Map.of("email", email.toLowerCase(Locale.ROOT))
                    )
                    .isEmpty()
            ) {
                var user = auth.register(email, "Administrator", password);
                graph.execute(
                    "MATCH (u:User {id:$id}) SET u.role='ADMIN'",
                    Map.of("id", user.get("id"))
                );
            }
        };
    }
}
