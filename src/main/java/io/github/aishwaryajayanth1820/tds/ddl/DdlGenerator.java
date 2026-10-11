package io.github.aishwaryajayanth1820.tds.ddl;

import static io.github.aishwaryajayanth1820.tds.ddl.SqlIdentifiers.quote;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.aishwaryajayanth1820.tds.grammar.FieldType;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Constraints;
import io.github.aishwaryajayanth1820.tds.grammar.Model.DefaultFn;
import io.github.aishwaryajayanth1820.tds.grammar.Model.DefaultValue;
import io.github.aishwaryajayanth1820.tds.grammar.Model.EnumValue;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Field;
import io.github.aishwaryajayanth1820.tds.grammar.Model.IndexSpec;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Rule;
import io.github.aishwaryajayanth1820.tds.grammar.Model.Template;

/** CREATE TABLE / INDEX statements for a template (grammar §4–§7). Mirrors the Studio prototype's DDL. */
public final class DdlGenerator {

    private final SqlDialect dialect;

    public DdlGenerator(SqlDialect dialect) {
        this.dialect = dialect;
    }

    public SqlDialect dialect() {
        return dialect;
    }

    /** CREATE TABLE plus all indexes. */
    public List<String> createTable(Template t) {
        List<String> out = new ArrayList<>();
        out.add(createTableStatement(t, t.name()));
        out.addAll(createIndexes(t));
        return out;
    }

    public String createTableStatement(Template t, String tableName) {
        List<String> lines = new ArrayList<>();
        for (Field f : t.fields()) {
            lines.add(columnDefinition(f));
        }
        String ts = dialect.timestampType();
        if (t.hasAuditColumns()) {
            lines.add(quote("created_at") + " " + ts + " NOT NULL DEFAULT " + dialect.nowDefault());
            lines.add(quote("created_by") + " " + dialect.userType());
            lines.add(quote("updated_at") + " " + ts);
            lines.add(quote("updated_by") + " " + dialect.userType());
        }
        if (t.hasRowVersion()) {
            lines.add(quote("row_version") + " " + dialect.versionType() + " NOT NULL DEFAULT 0");
        }
        for (Rule r : t.rules()) {
            ruleCheck(t, r).ifPresent(sql -> lines.add("CONSTRAINT " + quote(constraintName(t.name(), r.id())) + " CHECK (" + sql + ")"));
        }
        return "CREATE TABLE " + quote(tableName) + " (\n  " + String.join(",\n  ", lines) + "\n)";
    }

    public List<String> createIndexes(Template t) {
        List<String> out = new ArrayList<>();
        for (IndexSpec ix : t.indexes()) {
            out.add("CREATE " + (ix.unique() ? "UNIQUE " : "") + "INDEX " + quote(ix.name()) + " ON " + quote(t.name())
                    + " (" + ix.fields().stream().map(SqlIdentifiers::quote).collect(Collectors.joining(", ")) + ")");
        }
        for (Field f : t.fields()) {
            boolean covered = t.indexes().stream().anyMatch(ix -> ix.fields().getFirst().equals(f.name()));
            if (f.type() == FieldType.REF && !covered && !f.unique()) {
                out.add("CREATE INDEX " + quote(fkIndexName(t.name(), f.name())) + " ON " + quote(t.name()) + " (" + quote(f.name()) + ")");
            }
        }
        return out;
    }

    public String columnDefinition(Field f) {
        String col = quote(f.name());
        if (f.type() == FieldType.ID) {
            return col + " " + dialect.idColumnDefinition();
        }
        StringBuilder sb = new StringBuilder(col).append(' ').append(dialect.columnType(f));
        if (f.required()) {
            sb.append(" NOT NULL");
        }
        if (f.unique()) {
            sb.append(" UNIQUE");
        }
        defaultClause(f).ifPresent(d -> sb.append(" DEFAULT ").append(d));
        List<String> checks = columnChecks(f, col);
        if (!checks.isEmpty()) {
            sb.append(" CHECK (").append(String.join(" AND ", checks)).append(')');
        }
        if (f.type() == FieldType.REF && f.ref() != null) {
            sb.append(" REFERENCES ").append(quote(f.ref().target())).append("(\"id\") ON DELETE ")
                    .append(f.ref().onDelete().name().replace('_', ' '));
        }
        return sb.toString();
    }

    /** All SQL-enforceable column constraints, written against {@code column} (lets pre-checks reuse them). */
    public List<String> columnChecks(Field f, String column) {
        List<String> checks = new ArrayList<>(dialect.engineChecks(f, column));
        Constraints c = f.constraints();
        if (c.notBlank()) {
            checks.add("trim(" + column + ") <> ''");
        }
        if (c.minLength() != null) {
            checks.add("length(" + column + ") >= " + c.minLength());
        }
        if (c.maxLength() != null) {
            checks.add("length(" + column + ") <= " + c.maxLength());
        }
        if (c.min() != null && !c.min().isNull()) {
            checks.add(column + " >= " + dialect.literal(c.min(), f));
        }
        if (c.max() != null && !c.max().isNull()) {
            checks.add(column + " <= " + dialect.literal(c.max(), f));
        }
        if (f.type() == FieldType.ENUM) {
            checks.add(column + " IN (" + f.values().stream().map(EnumValue::value).map(SqlDialect::quoteString)
                    .collect(Collectors.joining(", ")) + ")");
        }
        return checks;
    }

    public Optional<String> defaultClause(Field f) {
        DefaultValue d = f.defaultValue();
        if (d == null) {
            return Optional.empty();
        }
        if (d instanceof DefaultValue.Literal lit) {
            return lit.value().isNull() ? Optional.empty() : Optional.of(dialect.literal(lit.value(), f));
        }
        DefaultFn fn = ((DefaultValue.Fn) d).fn();
        return switch (fn) {
            case NOW -> Optional.of(dialect.nowDefault());
            case TODAY -> Optional.of(dialect.todayDefault());
            case UUID -> dialect.isSqlite() ? Optional.empty() : Optional.of("gen_random_uuid()");
            case CURRENT_USER -> Optional.empty();
        };
    }

    /** SQL "present" test for a field (grammar §4.4). */
    public String presentSql(Template t, String fieldName) {
        String col = quote(fieldName);
        boolean stringy = t.field(fieldName).map(f -> f.type().isString()).orElse(false);
        return stringy ? "(" + col + " IS NOT NULL AND trim(" + col + ") <> '')" : col + " IS NOT NULL";
    }

    /** CHECK expression for rules that SQL can enforce; empty for guarded ({@code when}) and {@code expr} rules. */
    public Optional<String> ruleCheck(Template t, Rule r) {
        if (r.when() != null) {
            return Optional.empty();
        }
        return switch (r) {
            case Rule.Requires q -> Optional.of("NOT " + presentSql(t, q.ifField()) + " OR ("
                    + q.then().stream().map(n -> presentSql(t, n)).collect(Collectors.joining(" AND ")) + ")");
            case Rule.Exclusive x -> Optional.of(x.fields().stream()
                    .map(n -> "(CASE WHEN " + presentSql(t, n) + " THEN 1 ELSE 0 END)").collect(Collectors.joining(" + ")) + " <= 1");
            case Rule.AtLeastOne a -> Optional.of(a.fields().stream().map(n -> presentSql(t, n)).collect(Collectors.joining(" OR ")));
            case Rule.Expr e -> Optional.empty();
        };
    }

    /** Human-readable script for the Studio: statements plus notes on app-enforced constraints. */
    public String preview(Template t) {
        StringBuilder sb = new StringBuilder();
        if (dialect.isSqlite()) {
            sb.append("PRAGMA foreign_keys = ON;\n\n");
        }
        createTable(t).forEach(s -> sb.append(s).append(";\n"));
        List<String> notes = new ArrayList<>();
        for (Field f : t.fields()) {
            List<String> app = new ArrayList<>();
            if (f.constraints().pattern() != null) {
                app.add("pattern");
            }
            if (f.constraints().format() != null) {
                app.add("format " + f.constraints().format());
            }
            if (f.requiredWhen() != null) {
                app.add("requiredWhen");
            }
            if (f.defaultValue() instanceof DefaultValue.Fn fn && defaultClause(f).isEmpty()) {
                app.add("default " + fn.fn().name().toLowerCase(java.util.Locale.ROOT));
            }
            if (!app.isEmpty()) {
                notes.add("-- " + f.name() + ": " + String.join(", ", app) + " enforced by the service");
            }
        }
        for (Rule r : t.rules()) {
            if (ruleCheck(t, r).isEmpty()) {
                notes.add("-- rule " + r.id() + ": enforced by the service (JsonLogic)");
            }
        }
        if (!notes.isEmpty()) {
            sb.append('\n').append(String.join("\n", notes)).append('\n');
        }
        return sb.toString();
    }

    static String constraintName(String table, String ruleId) {
        String n = "ck_" + table + "_" + ruleId;
        return n.length() <= 63 ? n : n.substring(0, 63);
    }

    static String fkIndexName(String table, String field) {
        String n = "ix_" + table + "_" + field;
        return n.length() <= 63 ? n : n.substring(0, 63);
    }
}
