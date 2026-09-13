package io.neo4flix.common;

import java.util.Map;
import org.neo4j.driver.exceptions.ClientException;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> api(ApiException e) {
        return error(e.status, e.getMessage());
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        jakarta.validation.ConstraintViolationException.class,
        IllegalArgumentException.class,
    })
    ResponseEntity<?> invalid(Exception e) {
        if (e instanceof MethodArgumentNotValidException v) {
            var first = v.getBindingResult().getFieldErrors().stream().findFirst();
            return error(
                400,
                first
                    .map(f -> f.getField() + ": " + f.getDefaultMessage())
                    .orElse("Invalid request")
            );
        }
        return error(400, "Invalid request values");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> denied() {
        return error(403, "You do not have access to this action");
    }

    @ExceptionHandler(ClientException.class)
    ResponseEntity<?> database(ClientException e) {
        if (e.code().contains("ConstraintValidationFailed")) return error(
            409,
            "This record already exists"
        );
        return unexpected(e);
    }

    @ExceptionHandler(RestClientException.class)
    ResponseEntity<?> upstream() {
        return error(503, "A required service is temporarily unavailable");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e) {
        LoggerFactory.getLogger(getClass()).error("Request failed", e);
        return error(500, "Something went wrong. Please try again.");
    }

    private ResponseEntity<?> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status, "message", message));
    }
}
