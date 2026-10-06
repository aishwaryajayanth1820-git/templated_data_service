package io.github.aishwaryajayanth1820.tds.web;

import org.springframework.http.HttpStatus;

/** Stable machine-readable error codes carried in every ProblemDetail (application design §3.2). */
public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    CONFLICT_VERSION(HttpStatus.CONFLICT),
    CONFLICT_REFERENCE(HttpStatus.CONFLICT),
    CONFLICT_UNIQUE(HttpStatus.CONFLICT),
    PUBLISH_BLOCKED(HttpStatus.CONFLICT),
    VALIDATION(HttpStatus.UNPROCESSABLE_CONTENT),
    TOO_MANY_ACTIONS(HttpStatus.TOO_MANY_REQUESTS),
    ACTION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT),
    ACTION_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    /** Best-effort code for framework errors that carry only a status. */
    public static ErrorCode forStatus(int status) {
        return switch (status) {
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHENTICATED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> CONFLICT_VERSION;
            case 422 -> VALIDATION;
            case 429 -> TOO_MANY_ACTIONS;
            default -> status >= 500 ? INTERNAL : BAD_REQUEST;
        };
    }
}
