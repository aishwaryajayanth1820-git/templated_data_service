package com.lnw.tds.grammar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;

/**
 * JsonLogic subset with JavaScript semantics (ADR-0003). Mirrored by {@code ui/src/grammar/jsonlogic.ts};
 * both are checked against {@code testdata/parity/jsonlogic.json}.
 *
 * <p>Values are {@code null}, {@link Boolean}, {@link Number}, {@link String}, {@link List} or {@link Map}.
 */
public final class JsonLogic {

    public static final Set<String> OPERATORS = Set.of("var", "==", "===", "!=", "!==", "!", "!!", "and", "or", "if",
            "<", "<=", ">", ">=", "in", "+", "-", "*", "/", "%", "present");

    private JsonLogic() {}

    public static class UnsupportedOperatorException extends RuntimeException {
        public UnsupportedOperatorException(String op) {
            super("Unsupported JsonLogic operator \"" + op + "\"");
        }
    }

    /** Throws {@link UnsupportedOperatorException} for operators outside the subset. */
    public static void check(JsonNode rule) {
        if (rule == null) {
            return;
        }
        if (rule.isArray()) {
            rule.values().forEach(JsonLogic::check);
        } else if (isOperation(rule)) {
            String op = rule.propertyNames().iterator().next();
            if (!OPERATORS.contains(op)) {
                throw new UnsupportedOperatorException(op);
            }
            check(rule.get(op));
        }
    }

    /** First path segment of every {@code var}, e.g. {@code record.a.b} → {@code record}. */
    public static Set<String> variables(JsonNode rule) {
        Set<String> out = new LinkedHashSet<>();
        collect(rule, out);
        return out;
    }

    private static void collect(JsonNode rule, Set<String> out) {
        if (rule == null) {
            return;
        }
        if (rule.isArray()) {
            rule.values().forEach(r -> collect(r, out));
        } else if (rule.isObject()) {
            for (Map.Entry<String, JsonNode> e : rule.properties()) {
                if (e.getKey().equals("var")) {
                    JsonNode p = e.getValue().isArray() ? e.getValue().get(0) : e.getValue();
                    if (p != null && p.isString() && !p.asString().isEmpty()) {
                        out.add(p.asString().split("\\.")[0]);
                    }
                } else {
                    collect(e.getValue(), out);
                }
            }
        }
    }

    public static boolean test(JsonNode rule, Map<String, Object> data) {
        return truthy(apply(rule, data));
    }

    public static Object apply(JsonNode rule, Map<String, Object> data) {
        if (rule == null || rule.isNull()) {
            return null;
        }
        if (rule.isArray()) {
            List<Object> out = new ArrayList<>();
            rule.values().forEach(r -> out.add(apply(r, data)));
            return out;
        }
        if (!isOperation(rule)) {
            return literal(rule);
        }
        String op = rule.propertyNames().iterator().next();
        JsonNode raw = rule.get(op);
        List<JsonNode> args = new ArrayList<>();
        if (raw.isArray()) {
            raw.values().forEach(args::add);
        } else {
            args.add(raw);
        }
        switch (op) {
            case "if": {
                int i = 0;
                for (; i < args.size() - 1; i += 2) {
                    if (truthy(apply(args.get(i), data))) {
                        return apply(args.get(i + 1), data);
                    }
                }
                return i < args.size() ? apply(args.get(i), data) : null;
            }
            case "and": {
                Object v = true;
                for (JsonNode a : args) {
                    v = apply(a, data);
                    if (!truthy(v)) {
                        return v;
                    }
                }
                return v;
            }
            case "or": {
                Object v = false;
                for (JsonNode a : args) {
                    v = apply(a, data);
                    if (truthy(v)) {
                        return v;
                    }
                }
                return v;
            }
            default:
                break;
        }
        List<Object> v = new ArrayList<>();
        args.forEach(a -> v.add(apply(a, data)));
        Object a = v.isEmpty() ? null : v.get(0);
        Object b = v.size() > 1 ? v.get(1) : null;
        return switch (op) {
            case "var" -> variable(a, b, data);
            case "==" -> looseEquals(a, b);
            case "===" -> strictEquals(a, b);
            case "!=" -> !looseEquals(a, b);
            case "!==" -> !strictEquals(a, b);
            case "!" -> !truthy(a);
            case "!!" -> truthy(a);
            case "<" -> v.size() == 3 ? lessThan(a, b, false) && lessThan(b, v.get(2), false) : lessThan(a, b, false);
            case "<=" -> v.size() == 3 ? lessThan(a, b, true) && lessThan(b, v.get(2), true) : lessThan(a, b, true);
            case ">" -> lessThan(b, a, false);
            case ">=" -> lessThan(b, a, true);
            case "in" -> in(a, b);
            case "+" -> v.stream().mapToDouble(JsonLogic::toNumber).sum();
            case "-" -> v.size() == 1 ? -toNumber(a) : toNumber(a) - toNumber(b);
            case "*" -> v.stream().mapToDouble(JsonLogic::toNumber).reduce(1, (x, y) -> x * y);
            case "/" -> toNumber(a) / toNumber(b);
            case "%" -> toNumber(a) % toNumber(b);
            case "present" -> present(a);
            default -> throw new UnsupportedOperatorException(op);
        };
    }

    /** JsonLogic truthiness: JavaScript truthiness, except that an empty array is false. */
    public static boolean truthy(Object v) {
        return switch (v) {
            case null -> false;
            case Boolean bool -> bool;
            case Number n -> {
                double d = n.doubleValue();
                yield d != 0 && !Double.isNaN(d);
            }
            case String s -> !s.isEmpty();
            case List<?> l -> !l.isEmpty();
            default -> true;
        };
    }

    /** Grammar §4.4: not null and, for strings, not blank. */
    public static boolean present(Object v) {
        return v != null && !(v instanceof String s && s.isBlank());
    }

    /** JSON literal → plain Java value (numbers become {@link Double}, as in JavaScript). */
    public static Object literal(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        if (n.isBoolean()) {
            return n.asBoolean();
        }
        if (n.isNumber()) {
            return n.asDouble();
        }
        if (n.isString()) {
            return n.asString();
        }
        if (n.isArray()) {
            List<Object> out = new ArrayList<>();
            n.values().forEach(x -> out.add(literal(x)));
            return out;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        n.properties().forEach(e -> out.put(e.getKey(), literal(e.getValue())));
        return out;
    }

    private static boolean isOperation(JsonNode n) {
        return n.isObject() && n.size() == 1;
    }

    private static Object variable(Object path, Object fallback, Map<String, Object> data) {
        if (path == null || "".equals(path)) {
            return data;
        }
        String p = path instanceof Double d && d == Math.floor(d) ? String.valueOf(d.longValue()) : String.valueOf(path);
        Object cur = data;
        for (String part : p.split("\\.")) {
            if (cur instanceof Map<?, ?> m) {
                cur = m.get(part);
            } else if (cur instanceof List<?> l && part.matches("\\d+") && Integer.parseInt(part) < l.size()) {
                cur = l.get(Integer.parseInt(part));
            } else {
                return fallback;
            }
            if (cur == null) {
                return fallback;
            }
        }
        return cur;
    }

    static double toNumber(Object v) {
        return switch (v) {
            case null -> 0;
            case Boolean b -> b ? 1 : 0;
            case Number n -> n.doubleValue();
            case String s -> {
                String t = s.strip();
                if (t.isEmpty()) {
                    yield 0;
                }
                try {
                    yield Double.parseDouble(t);
                } catch (NumberFormatException e) {
                    yield Double.NaN;
                }
            }
            default -> Double.NaN;
        };
    }

    static boolean looseEquals(Object a, Object b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        if (a instanceof String sa && b instanceof String sb) {
            return sa.equals(sb);
        }
        if (a instanceof Boolean ba && b instanceof Boolean bb) {
            return ba.equals(bb);
        }
        if (isScalar(a) && isScalar(b)) {
            return toNumber(a) == toNumber(b);
        }
        return a == b;
    }

    static boolean strictEquals(Object a, Object b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return na.doubleValue() == nb.doubleValue();
        }
        if (a instanceof String || a instanceof Boolean) {
            return a.equals(b);
        }
        return a == b;
    }

    private static boolean isScalar(Object o) {
        return o instanceof Number || o instanceof String || o instanceof Boolean;
    }

    private static boolean lessThan(Object a, Object b, boolean orEqual) {
        if (a instanceof String sa && b instanceof String sb) {
            int c = sa.compareTo(sb);
            return orEqual ? c <= 0 : c < 0;
        }
        double x = toNumber(a);
        double y = toNumber(b);
        return orEqual ? x <= y : x < y;
    }

    private static boolean in(Object needle, Object haystack) {
        if (haystack instanceof String s) {
            return needle != null && s.contains(String.valueOf(needle instanceof Double d && d == Math.floor(d)
                    ? String.valueOf(d.longValue()) : needle));
        }
        if (haystack instanceof List<?> l) {
            return l.stream().anyMatch(x -> strictEquals(x, needle));
        }
        return false;
    }
}
