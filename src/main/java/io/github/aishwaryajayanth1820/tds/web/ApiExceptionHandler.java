package io.github.aishwaryajayanth1820.tds.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Maps every error to an RFC 9457 ProblemDetail carrying a stable {@code code}. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException e) {
        ProblemDetail pd = Problems.of(e.code(), e.title(), e.getMessage(), e.properties());
        return ResponseEntity.status(e.code().status()).body(pd);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> authentication(AuthenticationException e) {
        String detail = e instanceof DisabledException ? "This account is disabled" : "Invalid username or password";
        return api(new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication failed", detail));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> accessDenied(AccessDeniedException e) {
        return api(ApiException.forbidden("You do not have permission for this operation"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        String ref = UUID.randomUUID().toString();
        log.error("Unhandled error [ref {}]", ref, e);
        return api(new ApiException(ErrorCode.INTERNAL, "Internal error", "Unexpected error (ref " + ref + ")"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        List<String> general = ex.getBindingResult().getGlobalErrors().stream().map(e -> e.getDefaultMessage()).toList();
        ValidationException ve = new ValidationException(errors, general);
        return ResponseEntity.status(ve.code().status())
                .body(Problems.of(ve.code(), ve.title(), ve.getMessage(), ve.properties()));
    }

    /**
     * Framework-raised errors (404, 405, unreadable body …) get a code too. Hooked here rather
     * than in {@code handleExceptionInternal} because the ProblemDetail body is only built there.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
            WebRequest request) {
        if (body instanceof ProblemDetail pd && (pd.getProperties() == null || !pd.getProperties().containsKey("code"))) {
            pd.setProperty("code", ErrorCode.forStatus(statusCode.value()).name());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
