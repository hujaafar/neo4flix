package io.neo4flix.movie;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/movies/tmdb")
@PreAuthorize("hasRole('ADMIN')")
public class TmdbController {

    private final TmdbImports imports;

    public TmdbController(TmdbImports imports) {
        this.imports = imports;
    }

    public record ImportInput(
        @NotBlank @Pattern(regexp = "popular|top_rated") String selection,
        @Min(1) @Max(50) int count
    ) {}

    @GetMapping("/status")
    public Object status() {
        return Map.of("configured", imports.configured());
    }

    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Object start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ImportInput input) {
        return imports.start(jwt.getSubject(), input.selection(), input.count());
    }

    @GetMapping("/imports/{id}")
    public Object get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return imports.get(jwt.getSubject(), id);
    }

    @PostMapping("/imports/{id}/replace")
    public Object apply(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return imports.apply(jwt.getSubject(), id);
    }
}
