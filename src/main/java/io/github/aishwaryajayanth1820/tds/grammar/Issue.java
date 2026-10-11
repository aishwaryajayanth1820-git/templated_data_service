package io.github.aishwaryajayanth1820.tds.grammar;

/** A template validation finding. {@code path} points into the document, e.g. {@code fields[3]} or {@code rules[0]}. */
public record Issue(Severity severity, String code, String message, String path) {

    public enum Severity { ERROR, WARNING }

    public static Issue error(String code, String message, String path) {
        return new Issue(Severity.ERROR, code, message, path);
    }

    public static Issue warning(String code, String message, String path) {
        return new Issue(Severity.WARNING, code, message, path);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isError() {
        return severity == Severity.ERROR;
    }
}
