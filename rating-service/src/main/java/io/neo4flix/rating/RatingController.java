package io.neo4flix.rating;

import io.neo4flix.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ratings")
public class RatingController {

    private final RatingStore store;

    public RatingController(RatingStore store) {
        this.store = store;
    }

    public record RatingInput(@Min(1) @Max(5) int score, @Size(max = 1000) String review) {}

    @GetMapping("/me")
    public List<?> history(@AuthenticationPrincipal Jwt jwt) {
        return store.history(jwt.getSubject());
    }

    @GetMapping("/me/{movieId}")
    public Object get(@AuthenticationPrincipal Jwt jwt, @PathVariable String movieId) {
        return store.get(jwt.getSubject(), movieId);
    }

    @RequestMapping(path = "/me/{movieId}", method = { RequestMethod.PUT, RequestMethod.POST })
    public Object rate(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String movieId,
        @Valid @RequestBody RatingInput input
    ) {
        return store.save(jwt.getSubject(), movieId, input.score(), input.review());
    }

    @DeleteMapping("/me/{movieId}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String movieId) {
        store.delete(jwt.getSubject(), movieId);
    }
}
