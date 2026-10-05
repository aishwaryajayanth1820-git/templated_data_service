package com.lnw.tds.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void validationErrorsBecome422WithFieldMap() {
        ResponseEntity<ProblemDetail> r = handler.api(
                new ValidationException(Map.of("alert_name", "Required"), List.of("A ticket needs a site")));

        assertThat(r.getStatusCode().value()).isEqualTo(422);
        ProblemDetail pd = r.getBody();
        assertThat(pd.getProperties()).containsEntry("code", "VALIDATION")
                .containsEntry("errors", Map.of("alert_name", "Required"))
                .containsEntry("general", List.of("A ticket needs a site"));
    }

    @Test
    void apiExceptionCarriesCodeTitleAndDetail() {
        ResponseEntity<ProblemDetail> r = handler.api(ApiException.notFound("Template alerts"));

        assertThat(r.getStatusCode().value()).isEqualTo(404);
        assertThat(r.getBody().getTitle()).isEqualTo("Not found");
        assertThat(r.getBody().getDetail()).isEqualTo("Template alerts was not found");
        assertThat(r.getBody().getProperties()).containsEntry("code", "NOT_FOUND");
    }

    @Test
    void badCredentialsBecomeGeneric401() {
        ResponseEntity<ProblemDetail> r = handler.authentication(new BadCredentialsException("x"));

        assertThat(r.getStatusCode().value()).isEqualTo(401);
        assertThat(r.getBody().getDetail()).isEqualTo("Invalid username or password");
        assertThat(r.getBody().getProperties()).containsEntry("code", "UNAUTHENTICATED");
    }

    @Test
    void unexpectedErrorsHideDetailsButGiveReference() {
        ResponseEntity<ProblemDetail> r = handler.unexpected(new IllegalStateException("db password is hunter2"));

        assertThat(r.getStatusCode().value()).isEqualTo(500);
        assertThat(r.getBody().getDetail()).startsWith("Unexpected error (ref ").doesNotContain("hunter2");
    }
}
