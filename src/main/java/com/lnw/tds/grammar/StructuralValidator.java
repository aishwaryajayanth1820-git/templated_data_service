package com.lnw.tds.grammar;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import tools.jackson.databind.JsonNode;

/** Validates a template document against {@code tds-template.schema.json} (JSON Schema 2020-12), shared with the Studio. */
public final class StructuralValidator {

    public static final String SCHEMA_RESOURCE = "/tds/tds-template.schema.json";

    private final Schema schema;

    public StructuralValidator() {
        try (InputStream in = StructuralValidator.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Meta-schema not on classpath: " + SCHEMA_RESOURCE);
            }
            this.schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Issues with code {@code S100}; duplicate messages (common with {@code oneOf}) are collapsed. */
    public List<Issue> validate(JsonNode document) {
        Set<String> seen = new LinkedHashSet<>();
        List<Issue> issues = new ArrayList<>();
        for (Error e : schema.validate(document)) {
            String path = path(e.getInstanceLocation().toString());
            String message = e.getMessage();
            if (seen.add(path + "|" + message)) {
                issues.add(Issue.error("S100", message, path));
            }
        }
        return issues;
    }

    /**
     * Instance location → the path style the Studio links on: {@code /fields/3/type} (JSON Pointer) or
     * {@code $.fields[3].type} → {@code fields[3].type}; the document root becomes empty.
     */
    static String path(String location) {
        if (location.isEmpty() || location.equals("$") || location.equals("/")) {
            return "";
        }
        if (location.startsWith("$.")) {
            return location.substring(2);
        }
        if (!location.startsWith("/")) {
            return location;
        }
        StringBuilder sb = new StringBuilder();
        for (String raw : location.substring(1).split("/")) {
            String seg = raw.replace("~1", "/").replace("~0", "~");
            if (seg.matches("\\d+")) {
                sb.append('[').append(seg).append(']');
            } else {
                sb.append(sb.isEmpty() ? "" : ".").append(seg);
            }
        }
        return sb.toString();
    }
}
