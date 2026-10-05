package com.lnw.tds.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 422 with per-field messages ({@code errors}) and record-level messages ({@code general}). */
public class ValidationException extends ApiException {

    public ValidationException(Map<String, String> fieldErrors, List<String> general) {
        super(ErrorCode.VALIDATION, "Validation failed", summary(fieldErrors, general), properties(fieldErrors, general));
    }

    public static ValidationException field(String field, String message) {
        return new ValidationException(Map.of(field, message), List.of());
    }

    private static String summary(Map<String, String> fieldErrors, List<String> general) {
        int n = fieldErrors.size() + general.size();
        return n == 1 ? "1 problem found" : n + " problems found";
    }

    private static Map<String, Object> properties(Map<String, String> fieldErrors, List<String> general) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("errors", new LinkedHashMap<>(fieldErrors));
        p.put("general", List.copyOf(general));
        return p;
    }
}
