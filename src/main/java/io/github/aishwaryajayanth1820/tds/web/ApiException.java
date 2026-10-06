package io.github.aishwaryajayanth1820.tds.web;

import java.util.Map;

/** Base of every error the API reports deliberately; mapped to ProblemDetail by {@link ApiExceptionHandler}. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final String title;
    private final transient Map<String, Object> properties;

    public ApiException(ErrorCode code, String title, String detail) {
        this(code, title, detail, Map.of());
    }

    public ApiException(ErrorCode code, String title, String detail, Map<String, Object> properties) {
        super(detail);
        this.code = code;
        this.title = title;
        this.properties = Map.copyOf(properties);
    }

    public ErrorCode code() {
        return code;
    }

    public String title() {
        return title;
    }

    /** Extra ProblemDetail members, e.g. {@code errors} for validation failures. */
    public Map<String, Object> properties() {
        return properties;
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, "Not found", what + " was not found");
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(ErrorCode.FORBIDDEN, "Forbidden", detail);
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(ErrorCode.BAD_REQUEST, "Bad request", detail);
    }

    public static ApiException conflict(ErrorCode code, String detail) {
        return new ApiException(code, "Conflict", detail);
    }
}
