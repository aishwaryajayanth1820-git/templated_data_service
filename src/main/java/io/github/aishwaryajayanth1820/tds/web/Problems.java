package io.github.aishwaryajayanth1820.tds.web;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** Builds ProblemDetail bodies; also writes them directly from servlet filters, which run outside MVC. */
@Component
public class Problems {

    private final JsonMapper mapper;

    public Problems(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public static ProblemDetail of(ErrorCode code, String title, String detail, Map<String, Object> properties) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.status(), detail);
        pd.setTitle(title);
        pd.setProperty("code", code.name());
        properties.forEach(pd::setProperty);
        return pd;
    }

    public void write(HttpServletResponse response, ErrorCode code, String title, String detail) throws IOException {
        ProblemDetail pd = of(code, title, detail, Map.of());
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), pd);
    }
}
