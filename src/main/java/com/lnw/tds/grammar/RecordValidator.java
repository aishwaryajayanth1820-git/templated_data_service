package com.lnw.tds.grammar;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.lnw.tds.grammar.Model.Constraints;
import com.lnw.tds.grammar.Model.Field;
import com.lnw.tds.grammar.Model.Rule;
import com.lnw.tds.grammar.Model.Template;

import tools.jackson.databind.JsonNode;

/**
 * Validates one record against its template: required / requiredWhen, constraints and rules
 * (grammar §4.4, §7). Uniqueness is left to the database. Values are canonical API values:
 * strings (incl. ISO dates), numbers, booleans, maps/lists for json, or null.
 */
public final class RecordValidator {

    public enum WriteOp { CREATE, UPDATE }

    public record Result(Map<String, String> fieldErrors, List<String> general) {
        public boolean ok() {
            return fieldErrors.isEmpty() && general.isEmpty();
        }

        public List<String> messages() {
            List<String> all = new ArrayList<>();
            fieldErrors.forEach((k, v) -> all.add(k + ": " + v));
            all.addAll(general);
            return all;
        }
    }

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

    public Result validate(Template t, Map<String, Object> record, WriteOp op, String username, Set<String> roles) {
        Map<String, String> errs = new LinkedHashMap<>();
        List<String> general = new ArrayList<>();
        Map<String, Object> data = context(record, op, username, roles);

        for (Field f : t.fields()) {
            if (f.type() == FieldType.ID) {
                continue;
            }
            Object v = record.get(f.name());
            boolean required = f.required() || (f.requiredWhen() != null && safeTest(f.requiredWhen(), data));
            if (!JsonLogic.present(v)) {
                if (required) {
                    errs.putIfAbsent(f.name(), "Required");
                }
                continue;
            }
            String problem = check(f, v);
            if (problem != null) {
                errs.putIfAbsent(f.name(), problem);
            }
        }

        for (Rule r : t.rules()) {
            if (r.when() != null && !safeTest(r.when(), data)) {
                continue;
            }
            List<String> targets = r.targets();
            boolean ok;
            switch (r) {
                case Rule.Requires req -> {
                    targets = JsonLogic.present(record.get(req.ifField()))
                            ? req.then().stream().filter(n -> !JsonLogic.present(record.get(n))).toList() : List.of();
                    ok = targets.isEmpty();
                }
                case Rule.Exclusive ex -> ok = ex.fields().stream().filter(n -> JsonLogic.present(record.get(n))).count() <= 1;
                case Rule.AtLeastOne al -> ok = al.fields().stream().anyMatch(n -> JsonLogic.present(record.get(n)));
                case Rule.Expr e -> ok = safeTest(e.assertion(), data);
            }
            if (!ok) {
                String msg = r.message() != null ? r.message() : "Rule " + r.id() + " failed";
                if (targets.isEmpty()) {
                    general.add(msg);
                } else {
                    targets.forEach(n -> errs.putIfAbsent(n, msg));
                }
            }
        }
        return new Result(errs, general);
    }

    /** JsonLogic data: record fields plus {@code $op} and {@code $user} (grammar §7). */
    public static Map<String, Object> context(Map<String, Object> record, WriteOp op, String username, Set<String> roles) {
        Map<String, Object> data = new HashMap<>(record);
        data.put("$op", op.name().toLowerCase(java.util.Locale.ROOT));
        data.put("$user", Map.of("username", username, "roles", List.copyOf(roles)));
        return data;
    }

    private static String check(Field f, Object v) {
        Constraints c = f.constraints();
        FieldType type = f.type();
        if (type.isNumeric()) {
            if (!(v instanceof Number n)) {
                return "Must be a number";
            }
            if (type.isIntegral() && n.doubleValue() != Math.rint(n.doubleValue())) {
                return "Must be a whole number";
            }
            if (c.min() != null && c.min().isNumber() && n.doubleValue() < c.min().asDouble()) {
                return "Must be ≥ " + num(c.min());
            }
            if (c.max() != null && c.max().isNumber() && n.doubleValue() > c.max().asDouble()) {
                return "Must be ≤ " + num(c.max());
            }
        }
        if (type.isString()) {
            String s = String.valueOf(v);
            int max = Math.min(type == FieldType.STRING ? f.stringLength() : Integer.MAX_VALUE,
                    c.maxLength() != null ? c.maxLength() : Integer.MAX_VALUE);
            if (s.length() > max) {
                return "At most " + max + " characters";
            }
            if (c.minLength() != null && s.length() < c.minLength()) {
                return "At least " + c.minLength() + " characters";
            }
            if (c.notBlank() && s.isBlank()) {
                return "Must not be blank";
            }
            if (c.pattern() != null && !pattern(c.pattern()).matcher(s).matches()) {
                return "Must match " + c.pattern();
            }
            if ("email".equals(c.format()) && !EMAIL.matcher(s).matches()) {
                return "Must be an email address";
            }
            if ("url".equals(c.format()) && !isUrl(s)) {
                return "Must be a URL";
            }
        }
        if (type.isTemporal()) {
            String s = String.valueOf(v);
            if (c.min() != null && s.compareTo(c.min().asString()) < 0) {
                return "Must be on or after " + c.min().asString();
            }
            if (c.max() != null && s.compareTo(c.max().asString()) > 0) {
                return "Must be on or before " + c.max().asString();
            }
        }
        if (type == FieldType.ENUM && f.enumValue(String.valueOf(v)).isEmpty()) {
            return "Not an allowed value";
        }
        return null;
    }

    private static boolean safeTest(JsonNode rule, Map<String, Object> data) {
        try {
            return JsonLogic.test(rule, data);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Pattern pattern(String regex) {
        return PATTERNS.computeIfAbsent(regex, Pattern::compile);
    }

    private static boolean isUrl(String s) {
        try {
            URI u = new URI(s);
            return u.getScheme() != null && u.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static String num(JsonNode n) {
        double d = n.asDouble();
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
