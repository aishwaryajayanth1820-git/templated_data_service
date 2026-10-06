package io.github.aishwaryajayanth1820.tds.grammar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.aishwaryajayanth1820.tds.grammar.Model.ActionPlacement;
import io.github.aishwaryajayanth1820.tds.grammar.Model.ActionSpec;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Constraints;
import io.github.aishwaryajayanth1820.tds.grammar.Model.DefaultFn;
import io.github.aishwaryajayanth1820.tds.grammar.Model.DefaultValue;
import io.github.aishwaryajayanth1820.tds.grammar.Model.EnumValue;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Field;
import io.github.aishwaryajayanth1820.tds.grammar.Model.FieldOp;
import io.github.aishwaryajayanth1820.tds.grammar.Model.FieldUi;
import io.github.aishwaryajayanth1820.tds.grammar.Model.IndexSpec;
import io.github.aishwaryajayanth1820.tds.grammar.Model.ManageType;
import io.github.aishwaryajayanth1820.tds.grammar.Model.OnDelete;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Options;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Placement;
import io.github.aishwaryajayanth1820.tds.grammar.Model.RefSpec;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Rule;
import io.github.aishwaryajayanth1820.tds.grammar.Model.SortDir;
import io.github.aishwaryajayanth1820.tds.grammar.Model.SortSpec;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Template;
import io.github.aishwaryajayanth1820.tds.grammar.Model.ViewSpec;

import tools.jackson.databind.JsonNode;

/**
 * Binds a structurally valid template document to the {@link Model}. Hand-written rather than annotation
 * driven so that JSON keys such as {@code default}, {@code if} and {@code assert} map without surprises.
 */
final class TemplateBinder {

    private TemplateBinder() {}

    static Template bind(JsonNode root) {
        List<Field> fields = new ArrayList<>();
        for (JsonNode f : list(root, "fields")) {
            fields.add(field(f));
        }
        List<IndexSpec> indexes = new ArrayList<>();
        for (JsonNode ix : list(root, "indexes")) {
            indexes.add(new IndexSpec(text(ix, "name"), strings(ix, "fields"), bool(ix, "unique")));
        }
        List<Rule> rules = new ArrayList<>();
        for (JsonNode r : list(root, "rules")) {
            rules.add(rule(r));
        }
        List<ActionSpec> actions = new ArrayList<>();
        for (JsonNode a : list(root, "actions")) {
            actions.add(new ActionSpec(text(a, "name"), text(a, "label"),
                    Enums.lower(ActionPlacement.class, text(a, "placement")).orElse(ActionPlacement.ROW),
                    text(a, "script"), text(a, "function"), text(a, "confirm"), node(a, "visibleWhen"),
                    a.has("timeoutMs") ? a.get("timeoutMs").asInt() : 3000, a));
        }
        Map<String, Set<String>> access = null;
        if (root.has("access") && root.get("access").isObject()) {
            access = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> e : root.get("access").properties()) {
                access.put(e.getKey(), new LinkedHashSet<>(stringList(e.getValue())));
            }
        }
        JsonNode o = root.path("options");
        Options options = new Options(!o.has("audit") || o.get("audit").asBoolean(),
                !o.has("optimisticLock") || o.get("optimisticLock").asBoolean());
        return new Template(text(root, "name"), text(root, "label"), text(root, "description"),
                ManageType.valueOf(text(root, "manageType")), options, List.copyOf(fields), List.copyOf(indexes),
                List.copyOf(rules), List.copyOf(actions), access == null ? null : Map.copyOf(access), view(root.path("view")),
                root);
    }

    private static Field field(JsonNode f) {
        FieldType type = FieldType.fromJson(text(f, "type"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown type " + text(f, "type")));
        List<EnumValue> values = new ArrayList<>();
        for (JsonNode v : list(f, "values")) {
            values.add(v.isString() ? new EnumValue(v.asString(), null, null)
                    : new EnumValue(text(v, "value"), text(v, "label"), text(v, "color")));
        }
        RefSpec ref = null;
        if (f.has("ref")) {
            JsonNode r = f.get("ref");
            ref = new RefSpec(text(r, "target"), text(r, "display"),
                    r.has("onDelete") ? OnDelete.valueOf(text(r, "onDelete")) : OnDelete.RESTRICT);
        }
        DefaultValue def = null;
        if (f.has("default")) {
            JsonNode d = f.get("default");
            def = d.isObject() && d.has("fn")
                    ? new DefaultValue.Fn(Enums.lower(DefaultFn.class, text(d, "fn")).orElseThrow())
                    : new DefaultValue.Literal(d);
        }
        Constraints constraints = Constraints.NONE;
        if (f.has("constraints")) {
            JsonNode c = f.get("constraints");
            constraints = new Constraints(bool(c, "notBlank"), integer(c, "minLength"), integer(c, "maxLength"),
                    text(c, "pattern"), text(c, "format"), node(c, "min"), node(c, "max"));
        }
        FieldUi ui = FieldUi.NONE;
        if (f.has("ui")) {
            JsonNode u = f.get("ui");
            ui = new FieldUi(Enums.lower(Placement.class, text(u, "placement")).orElse(null),
                    Enums.lower(Widget.class, text(u, "widget")).orElse(null), integer(u, "order"), text(u, "group"),
                    node(u, "visibleWhen"), node(u, "readonlyWhen"));
        }
        Map<String, Set<FieldOp>> access = null;
        if (f.has("access")) {
            access = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> e : f.get("access").properties()) {
                Set<FieldOp> ops = new LinkedHashSet<>();
                for (String op : stringList(e.getValue())) {
                    ops.add(FieldOp.valueOf(op.toUpperCase(java.util.Locale.ROOT)));
                }
                access.put(e.getKey(), Set.copyOf(ops));
            }
            access = Map.copyOf(access);
        }
        return new Field(text(f, "name"), text(f, "label"), type, integer(f, "length"), integer(f, "precision"),
                integer(f, "scale"), List.copyOf(values), ref, bool(f, "required"), bool(f, "unique"), def, constraints,
                node(f, "requiredWhen"), ui, access, text(f, "renamedFrom"), f);
    }

    private static Rule rule(JsonNode r) {
        String id = text(r, "id");
        JsonNode when = node(r, "when");
        String message = text(r, "message");
        return switch (text(r, "kind")) {
            case "requires" -> new Rule.Requires(id, text(r, "if"), strings(r, "then"), when, message);
            case "exclusive" -> new Rule.Exclusive(id, strings(r, "fields"), when, message);
            case "atLeastOne" -> new Rule.AtLeastOne(id, strings(r, "fields"), when, message);
            case "expr" -> new Rule.Expr(id, node(r, "assert"), strings(r, "fields"), when, message);
            default -> throw new IllegalArgumentException("Unknown rule kind " + text(r, "kind"));
        };
    }

    private static ViewSpec view(JsonNode v) {
        if (v.isMissingNode() || !v.isObject()) {
            return ViewSpec.DEFAULT;
        }
        List<SortSpec> sort = new ArrayList<>();
        for (JsonNode s : list(v, "defaultSort")) {
            sort.add(new SortSpec(text(s, "field"), "desc".equals(text(s, "dir")) ? SortDir.DESC : SortDir.ASC));
        }
        return new ViewSpec(text(v, "titleField"), List.copyOf(sort), v.has("pageSize") ? v.get("pageSize").asInt() : 25,
                strings(v, "search"), strings(v, "filters"));
    }

    static List<JsonNode> list(JsonNode n, String key) {
        JsonNode a = n.get(key);
        if (a == null || !a.isArray()) {
            return List.of();
        }
        List<JsonNode> out = new ArrayList<>();
        a.values().forEach(out::add);
        return out;
    }

    private static List<String> strings(JsonNode n, String key) {
        JsonNode a = n.get(key);
        return a == null ? List.of() : List.copyOf(stringList(a));
    }

    private static List<String> stringList(JsonNode a) {
        List<String> out = new ArrayList<>();
        if (a != null && a.isArray()) {
            a.values().forEach(x -> out.add(x.asString()));
        }
        return out;
    }

    static String text(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? null : v.asString();
    }

    private static Integer integer(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v == null || !v.isNumber() ? null : v.asInt();
    }

    private static boolean bool(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v != null && v.asBoolean();
    }

    private static JsonNode node(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? null : v;
    }
}
