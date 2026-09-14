package io.neo4flix.common;

import java.util.Map;
import org.neo4j.driver.exceptions.ClientException;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> api(ApiException e) {
        return error(e.status, e.getMessage());
    }

    @ExceptionHandler({
        jakarta.validation.ConstraintViolationException.class,
        IllegalArgumentException.class,
    })
    ResponseEntity<?> invalid(Exception e) {
        return error(400, "Invalid request values");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception exception,
        Object body,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        // Preserve Spring's status and protocol headers (Allow, Accept, etc.), without exposing input or internals.
        String message = switch (status.value()) {
            case 404 -> "Resource not found";
            case 405 -> "HTTP method is not supported for this resource";
            case 406 -> "Requested response format is not supported";
            case 413 -> "Request is too large";
            case 415 -> "Request content type is not supported";
            default -> status.is4xxClientError()
                ? "Invalid request values"
                : "Something went wrong. Please try again.";
        };
        return new ResponseEntity<>(
            Map.of("status", status.value(), "message", message),
            headers,
            status
        );
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
