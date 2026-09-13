package io.neo4flix.common;

public class ApiException extends RuntimeException {

    public final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }
}
