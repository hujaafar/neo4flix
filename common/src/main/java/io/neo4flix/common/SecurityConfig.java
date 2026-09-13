package io.neo4flix.common;

import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.jwt-public-key}") String path, Graph graph)
        throws Exception {
        String pem = Files.readString(Path.of(path))
            .replaceAll("-----[^-]+-----", "")
            .replaceAll("\\s", "");
        var key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
            new X509EncodedKeySpec(Base64.getDecoder().decode(pem))
        );
        var decoder = NimbusJwtDecoder.withPublicKey(key).build();
        OAuth2TokenValidator<Jwt> audience = jwt ->
            jwt.getAudience().contains("neo4flix-api")
                ? OAuth2TokenValidatorResult.success()
                : invalid();
        OAuth2TokenValidator<Jwt> active = jwt -> {
            try {
                var rows = graph.list(
                    "MATCH (u:User {id:$id}) WHERE u.tokenVersion=$version RETURN u.id AS id",
                    Map.of(
                        "id",
                        jwt.getSubject(),
                        "version",
                        ((Number) jwt.getClaims().get("ver")).longValue()
                    )
                );
                return rows.isEmpty() ? invalid() : OAuth2TokenValidatorResult.success();
            } catch (Exception e) {
                return invalid();
            }
        };
        decoder.setJwtValidator(
            new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("neo4flix"),
                audience,
                active
            )
        );
        return decoder;
    }

    private static OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token", "Session expired or revoked", null)
        );
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        // APIs use an explicit Authorization header; refresh cookie endpoints validate Origin separately.
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a ->
                a
                    .requestMatchers("/api/auth/**", "/actuator/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            )
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))
            .headers(h -> h.contentTypeOptions(c -> {}).frameOptions(f -> f.deny()))
            .build();
    }
}
