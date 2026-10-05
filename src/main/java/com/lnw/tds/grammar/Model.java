package com.lnw.tds.grammar;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import tools.jackson.databind.JsonNode;

/**
 * The {@code tds/v1} template model (grammar §2–§9, application design §5.1). Immutable; collections are
 * never null. Each element keeps its source JSON so APIs can pass definitions through unchanged.
 */
public final class Model {

    private Model() {}

    public enum ManageType { VIEW, MANAGE_VIEW, DATA_SOURCE }

    public enum Placement { COLUMN, DETAIL, HIDDEN }

    public enum FieldOp { READ, CREATE, UPDATE }

    public enum OnDelete { RESTRICT, CASCADE, SET_NULL }

    public enum DefaultFn { NOW, TODAY, UUID, CURRENT_USER }

    public enum ActionPlacement { ROW, TOOLBAR, SELECTION }

    public enum SortDir { ASC, DESC }

    public static final List<String> AUDIT_COLUMNS =
            List.of("created_at", "created_by", "updated_at", "updated_by", "row_version");

    public record Options(boolean audit, boolean optimisticLock) {
        public static final Options DEFAULT = new Options(true, true);
    }

    public record EnumValue(String value, String label, String color) {
        public String displayLabel() {
            return label != null ? label : value;
        }
    }

    public record RefSpec(String target, String display, OnDelete onDelete) {}

    public sealed interface DefaultValue {
        record Literal(JsonNode value) implements DefaultValue {}

        record Fn(DefaultFn fn) implements DefaultValue {}
    }

    public record Constraints(boolean notBlank, Integer minLength, Integer maxLength, String pattern, String format,
                              JsonNode min, JsonNode max) {
        public static final Constraints NONE = new Constraints(false, null, null, null, null, null, null);
    }

    public record FieldUi(Placement placement, Widget widget, Integer order, String group, JsonNode visibleWhen,
                          JsonNode readonlyWhen) {
        public static final FieldUi NONE = new FieldUi(null, null, null, null, null, null);
    }

    public record Field(String name, String label, FieldType type, Integer length, Integer precision, Integer scale,
                        List<EnumValue> values, RefSpec ref, boolean required, boolean unique, DefaultValue defaultValue,
                        Constraints constraints, JsonNode requiredWhen, FieldUi ui,
                        Map<String, Set<FieldOp>> access, String renamedFrom, JsonNode json) {

        public String displayLabel() {
            return label != null ? label : name;
        }

        public Placement placement() {
            if (ui.placement() != null) {
                return ui.placement();
            }
            return type == FieldType.ID ? Placement.HIDDEN : Placement.COLUMN;
        }

        public Widget widget() {
            return ui.widget() != null ? ui.widget() : type.widgets().isEmpty() ? null : type.widgets().getFirst();
        }

        public int stringLength() {
            return length != null ? length : 255;
        }

        public Optional<EnumValue> enumValue(String value) {
            return values.stream().filter(v -> v.value().equals(value)).findFirst();
        }
    }

    public record IndexSpec(String name, List<String> fields, boolean unique) {}

    public sealed interface Rule {
        String id();

        JsonNode when();

        String message();

        /** Fields to highlight when the rule fails. */
        List<String> targets();

        record Requires(String id, String ifField, List<String> then, JsonNode when, String message) implements Rule {
            public List<String> targets() {
                return then;
            }
        }

        record Exclusive(String id, List<String> fields, JsonNode when, String message) implements Rule {
            public List<String> targets() {
                return fields;
            }
        }

        record AtLeastOne(String id, List<String> fields, JsonNode when, String message) implements Rule {
            public List<String> targets() {
                return fields;
            }
        }

        record Expr(String id, JsonNode assertion, List<String> fields, JsonNode when, String message) implements Rule {
            public List<String> targets() {
                return fields;
            }
        }
    }

    public record ActionSpec(String name, String label, ActionPlacement placement, String script, String function,
                             String confirm, JsonNode visibleWhen, int timeoutMs, JsonNode json) {}

    public record SortSpec(String field, SortDir dir) {}

    public record ViewSpec(String titleField, List<SortSpec> defaultSort, int pageSize, List<String> search,
                           List<String> filters) {
        public static final ViewSpec DEFAULT = new ViewSpec(null, List.of(), 25, List.of(), List.of());
    }

    public record Template(String name, String label, String description, ManageType manageType, Options options,
                           List<Field> fields, List<IndexSpec> indexes, List<Rule> rules, List<ActionSpec> actions,
                           Map<String, Set<String>> access, ViewSpec view, JsonNode json) {

        public String displayLabel() {
            return label != null ? label : name;
        }

        public Optional<Field> field(String fieldName) {
            return fields.stream().filter(f -> f.name().equals(fieldName)).findFirst();
        }

        public Field idField() {
            return fields.stream().filter(f -> f.type() == FieldType.ID).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Template " + name + " has no id field"));
        }

        public Optional<ActionSpec> action(String actionName) {
            return actions.stream().filter(a -> a.name().equals(actionName)).findFirst();
        }

        /** {@code access} as written, or the grammar §6.1 defaults when the template has none. */
        public Map<String, Set<String>> effectiveAccess() {
            if (access != null) {
                return access;
            }
            return Map.of("admin", Set.of("read", "create", "update", "delete", "run:*"),
                    "viewer", manageType == ManageType.DATA_SOURCE ? Set.of() : Set.of("read"));
        }

        public boolean hasAuditColumns() {
            return options.audit();
        }

        public boolean hasRowVersion() {
            return options.optimisticLock();
        }

        /** Field names plus audit columns that are present: everything a sort, filter or index may reference. */
        public Set<String> referenceableNames() {
            java.util.LinkedHashSet<String> s = new java.util.LinkedHashSet<>();
            fields.forEach(f -> s.add(f.name()));
            if (options.audit()) {
                s.addAll(AUDIT_COLUMNS.subList(0, 4));
            }
            if (options.optimisticLock()) {
                s.add("row_version");
            }
            return s;
        }
    }
}
