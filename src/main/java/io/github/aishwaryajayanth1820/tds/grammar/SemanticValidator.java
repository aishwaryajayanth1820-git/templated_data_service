package io.github.aishwaryajayanth1820.tds.grammar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.github.aishwaryajayanth1820.tds.grammar.Model.ActionSpec;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Constraints;
import io.github.aishwaryajayanth1820.tds.grammar.Model.EnumValue;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Field;
import io.github.aishwaryajayanth1820.tds.grammar.Model.FieldOp;
import io.github.aishwaryajayanth1820.tds.grammar.Model.IndexSpec;
import io.github.aishwaryajayanth1820.tds.grammar.Model.ManageType;
import io.github.aishwaryajayanth1820.tds.grammar.Model.OnDelete;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Rule;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Template;

import tools.jackson.databind.JsonNode;

/** Cross-reference checks that JSON Schema cannot express (grammar §11). Same codes as the Schema Studio. */
public final class SemanticValidator {

    private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");
    private static final Pattern ENUM_VALUE = Pattern.compile("^[A-Za-z0-9_\\-]{1,64}$");

    public List<Issue> validate(Template t, ValidationContext ctx) {
        List<Issue> out = new ArrayList<>();
        Set<String> known = t.referenceableNames();

        if (!NAME.matcher(t.name()).matches() || t.name().startsWith("tds_")) {
            out.add(Issue.error("E003", "Table name \"" + t.name() + "\" must be snake_case and must not start with tds_", "name"));
        }

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < t.fields().size(); i++) {
            Field f = t.fields().get(i);
            String p = "fields[" + i + "]";
            String n = f.name();
            if (!seen.add(n)) {
                out.add(Issue.error("E001", "Duplicate field name \"" + n + "\"", p));
            }
            if (Model.AUDIT_COLUMNS.contains(n)) {
                out.add(Issue.error("E003", "\"" + n + "\" is a reserved audit column", p));
            }
            Constraints c = f.constraints();
            if (f.type() == FieldType.ENUM) {
                List<String> vals = f.values().stream().map(EnumValue::value).toList();
                if (new HashSet<>(vals).size() != vals.size()) {
                    out.add(Issue.error("E021", n + ": duplicate enum value", p));
                }
                vals.stream().filter(v -> !ENUM_VALUE.matcher(v).matches()).forEach(v ->
                        out.add(Issue.error("S005", n + ": enum value \"" + v + "\" must match [A-Za-z0-9_-]{1,64}", p)));
                if (f.defaultValue() instanceof Model.DefaultValue.Literal lit && lit.value().isString()
                        && !vals.contains(lit.value().asString())) {
                    out.add(Issue.error("E020", n + ": default \"" + lit.value().asString() + "\" is not one of the enum values", p));
                }
            }
            if (f.type() == FieldType.REF && f.ref() != null) {
                String target = f.ref().target();
                Optional<Template> tgt = target.equals(t.name()) ? Optional.of(t) : ctx.templates().find(target);
                if (tgt.isEmpty()) {
                    out.add(Issue.error("E010", n + ": ref target \"" + target + "\" does not exist", p));
                } else {
                    if (!target.equals(t.name()) && !ctx.templates().isPublished(target)) {
                        out.add(Issue.error("E010", n + ": ref target \"" + target + "\" is not published yet. Publish it first", p));
                    }
                    if (!tgt.get().referenceableNames().contains(f.ref().display())) {
                        out.add(Issue.error("E011", n + ": display field \"" + f.ref().display() + "\" not found on " + target, p));
                    }
                }
                if (f.ref().onDelete() == OnDelete.SET_NULL && f.required()) {
                    out.add(Issue.error("E012", n + ": onDelete SET_NULL needs a nullable field", p));
                }
            }
            if (f.type() == FieldType.STRING && c.maxLength() != null && c.maxLength() > f.stringLength()) {
                out.add(Issue.error("E022", n + ": maxLength " + c.maxLength() + " exceeds column length " + f.stringLength(), p));
            }
            if (c.min() != null && c.max() != null && c.min().isNumber() && c.max().isNumber()
                    && c.min().asDouble() > c.max().asDouble()) {
                out.add(Issue.error("E023", n + ": min is greater than max", p));
            }
            if (c.minLength() != null && c.maxLength() != null && c.minLength() > c.maxLength()) {
                out.add(Issue.error("E023", n + ": minLength is greater than maxLength", p));
            }
            if (c.pattern() != null) {
                try {
                    Pattern.compile(c.pattern());
                } catch (PatternSyntaxException e) {
                    out.add(Issue.error("S006", n + ": pattern is not a valid regex", p));
                }
            }
            logic(f.requiredWhen(), p, n + ".requiredWhen", known, out);
            logic(f.ui().visibleWhen(), p, n + ".ui.visibleWhen", known, out);
            logic(f.ui().readonlyWhen(), p, n + ".ui.readonlyWhen", known, out);
            if (f.access() != null) {
                for (Map.Entry<String, Set<FieldOp>> e : f.access().entrySet()) {
                    if (!ctx.roles().contains(e.getKey())) {
                        out.add(Issue.warning("W001", n + ": role \"" + e.getKey() + "\" does not exist", p));
                    }
                    Set<String> tops = t.effectiveAccess().getOrDefault(e.getKey(), Set.of());
                    for (FieldOp op : e.getValue()) {
                        String opName = op.name().toLowerCase(java.util.Locale.ROOT);
                        if (!"admin".equals(e.getKey()) && !tops.contains(opName)) {
                            out.add(Issue.warning("W004", n + ": grants \"" + opName + "\" to " + e.getKey()
                                    + " but the table does not, so it has no effect", p));
                        }
                    }
                }
            }
            if (t.manageType() == ManageType.DATA_SOURCE && f.type() != FieldType.ID && f.json().has("ui")
                    && f.json().get("ui").size() > 0) {
                out.add(Issue.warning("W002", n + ": ui settings are ignored for DATA_SOURCE", p));
            }
        }
        long ids = t.fields().stream().filter(f -> f.type() == FieldType.ID).count();
        if (ids != 1) {
            out.add(Issue.error("E002", "Exactly one field of type \"id\" is required (found " + ids + ")", "fields"));
        }

        for (int i = 0; i < t.indexes().size(); i++) {
            IndexSpec ix = t.indexes().get(i);
            for (String n : ix.fields()) {
                if (!known.contains(n)) {
                    out.add(Issue.error("E030", "Index " + ix.name() + " references unknown field \"" + n + "\"", "indexes[" + i + "]"));
                }
            }
        }

        Set<String> ruleIds = new HashSet<>();
        for (int i = 0; i < t.rules().size(); i++) {
            Rule r = t.rules().get(i);
            String p = "rules[" + i + "]";
            if (!ruleIds.add(r.id())) {
                out.add(Issue.error("E001", "Duplicate rule id \"" + r.id() + "\"", p));
            }
            List<String> refs = new ArrayList<>(r.targets());
            if (r instanceof Rule.Requires req) {
                refs.add(req.ifField());
            }
            refs.stream().filter(n -> !known.contains(n)).forEach(n ->
                    out.add(Issue.error("E030", "Rule " + r.id() + " references unknown field \"" + n + "\"", p)));
            logic(r.when(), p, "Rule " + r.id() + ".when", known, out);
            if (r instanceof Rule.Expr e) {
                logic(e.assertion(), p, "Rule " + r.id() + ".assert", known, out);
            }
        }

        List<String> viewRefs = new ArrayList<>();
        if (t.view().titleField() != null) {
            viewRefs.add(t.view().titleField());
        }
        t.view().defaultSort().forEach(s -> viewRefs.add(s.field()));
        viewRefs.addAll(t.view().search());
        viewRefs.addAll(t.view().filters());
        viewRefs.stream().filter(n -> !known.contains(n)).distinct().forEach(n ->
                out.add(Issue.error("E030", "View references unknown field \"" + n + "\"", "view")));

        Set<String> actionNames = new HashSet<>();
        for (int i = 0; i < t.actions().size(); i++) {
            ActionSpec a = t.actions().get(i);
            String p = "actions[" + i + "]";
            actionNames.add(a.name());
            if (!ctx.scripts().exists(a.script())) {
                out.add(Issue.error("E040", "Action " + a.name() + ": script file \"" + a.script() + "\" not found", p));
            } else if (!ctx.scripts().hasFunction(a.script(), a.function())) {
                out.add(Issue.error("E040", "Action " + a.name() + ": function " + a.function() + "() not found in " + a.script(), p));
            }
            logic(a.visibleWhen(), p, "Action " + a.name() + ".visibleWhen", known, out);
        }
        if (t.access() != null) {
            for (Map.Entry<String, Set<String>> e : t.access().entrySet()) {
                if (!ctx.roles().contains(e.getKey())) {
                    out.add(Issue.warning("W001", "Access: role \"" + e.getKey() + "\" does not exist", "access"));
                }
                e.getValue().stream().filter(o -> o.startsWith("run:") && !o.equals("run:*"))
                        .filter(o -> !actionNames.contains(o.substring(4)))
                        .forEach(o -> out.add(Issue.error("E041", "Access: " + e.getKey() + " grants " + o
                                + " but there is no such action", "access")));
            }
        }
        if (t.manageType() == ManageType.MANAGE_VIEW && t.fields().stream().allMatch(f -> f.type() == FieldType.ID)) {
            out.add(Issue.warning("W003", "MANAGE_VIEW has no editable field yet", "fields"));
        }
        return out;
    }

    private static void logic(JsonNode rule, String path, String what, Set<String> known, List<Issue> out) {
        if (rule == null) {
            return;
        }
        for (String v : JsonLogic.variables(rule)) {
            if (!v.startsWith("$") && !known.contains(v)) {
                out.add(Issue.error("E031", what + " references unknown field \"" + v + "\"", path));
            }
        }
        try {
            JsonLogic.check(rule);
        } catch (JsonLogic.UnsupportedOperatorException e) {
            out.add(Issue.error("E032", what + ": " + e.getMessage(), path));
        }
    }
}
