package com.lnw.tds.ddl;

import static com.lnw.tds.ddl.SqlIdentifiers.quote;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.lnw.tds.ddl.MigrationPlan.PreCheck;
import com.lnw.tds.ddl.MigrationPlan.Step;
import com.lnw.tds.ddl.MigrationPlan.StepClass;
import com.lnw.tds.grammar.FieldType;
import com.lnw.tds.grammar.Model.EnumValue;
import com.lnw.tds.grammar.Model.Field;
import com.lnw.tds.grammar.Model.IndexSpec;
import com.lnw.tds.grammar.Model.Rule;
import com.lnw.tds.grammar.Model.Template;

/**
 * Diffs the published template against the draft (ADR-0013) and produces classified steps, pre-checks
 * and the SQL to run. On SQLite every structural change is applied with the documented table rebuild.
 */
public final class MigrationPlanner {

    private final DdlGenerator ddl;

    public MigrationPlanner(DdlGenerator ddl) {
        this.ddl = ddl;
    }

    public MigrationPlan plan(Template published, Template draft) {
        if (published == null) {
            return new MigrationPlan(draft.name(),
                    List.of(new Step(StepClass.SAFE, "Create table " + draft.name() + " with " + draft.fields().size() + " fields")),
                    List.of(), ddl.createTable(draft), false);
        }
        List<Step> steps = new ArrayList<>();
        List<PreCheck> checks = new ArrayList<>();
        String table = quote(published.name());
        Map<String, Field> pub = new LinkedHashMap<>();
        published.fields().forEach(f -> pub.put(f.name(), f));
        Set<String> used = new HashSet<>();
        boolean structural = false;

        if (!published.name().equals(draft.name())) {
            steps.add(new Step(StepClass.BLOCKED, "Renaming a published template is not supported (" + published.name() + " → " + draft.name() + ")"));
        }

        for (Field f : draft.fields()) {
            Field old = pub.get(f.name());
            if (old == null && f.renamedFrom() != null) {
                old = pub.get(f.renamedFrom());
            }
            if (old == null) {
                structural = true;
                if (f.required() && ddl.defaultClause(f).isEmpty()) {
                    steps.add(new Step(StepClass.CHECKED, "Add required column " + f.name() + " with no default. Existing rows would have no value"));
                    checks.add(new PreCheck("Rows that need a value for " + f.name(), "SELECT COUNT(*) FROM " + table));
                } else {
                    steps.add(new Step(StepClass.SAFE, "Add column " + f.name() + " (" + f.type().json() + ")"));
                }
                continue;
            }
            used.add(old.name());
            String oldCol = quote(old.name());
            if (!old.name().equals(f.name())) {
                structural = true;
                steps.add(new Step(StepClass.SAFE, "Rename column " + old.name() + " → " + f.name()));
            }
            if (old.type() != f.type()) {
                steps.add(new Step(StepClass.BLOCKED, "Change type of " + f.name() + ": " + old.type().json() + " → "
                        + f.type().json() + " is not allowed in v1. Add a new field instead"));
                continue;
            }
            if (!old.required() && f.required()) {
                structural = true;
                steps.add(new Step(StepClass.CHECKED, "Make " + f.name() + " required (NOT NULL)"));
                checks.add(new PreCheck("Rows with no " + f.name(), "SELECT COUNT(*) FROM " + table + " WHERE " + oldCol + " IS NULL"));
            }
            if (old.required() && !f.required()) {
                structural = true;
                steps.add(new Step(StepClass.SAFE, "Allow empty " + f.name()));
            }
            if (!old.unique() && f.unique()) {
                structural = true;
                steps.add(new Step(StepClass.CHECKED, "Make " + f.name() + " unique"));
                checks.add(new PreCheck("Duplicate values in " + f.name(), "SELECT COUNT(*) FROM (SELECT " + oldCol + " FROM " + table
                        + " WHERE " + oldCol + " IS NOT NULL GROUP BY " + oldCol + " HAVING COUNT(*) > 1) d"));
            }
            if (old.unique() && !f.unique()) {
                structural = true;
                steps.add(new Step(StepClass.SAFE, "Drop unique on " + f.name()));
            }
            if (f.type() == FieldType.STRING && f.stringLength() != old.stringLength()) {
                structural = true;
                if (f.stringLength() < old.stringLength()) {
                    steps.add(new Step(StepClass.CHECKED, "Shrink " + f.name() + " length " + old.stringLength() + " → " + f.stringLength()));
                    checks.add(new PreCheck("Values in " + f.name() + " longer than " + f.stringLength(),
                            "SELECT COUNT(*) FROM " + table + " WHERE length(" + oldCol + ") > " + f.stringLength()));
                } else {
                    steps.add(new Step(StepClass.SAFE, "Grow " + f.name() + " length " + old.stringLength() + " → " + f.stringLength()));
                }
            }
            if (f.type() == FieldType.ENUM) {
                Set<String> a = old.values().stream().map(EnumValue::value).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
                Set<String> b = f.values().stream().map(EnumValue::value).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
                List<String> added = b.stream().filter(x -> !a.contains(x)).toList();
                List<String> removed = a.stream().filter(x -> !b.contains(x)).toList();
                if (!added.isEmpty()) {
                    structural = true;
                    steps.add(new Step(StepClass.SAFE, "Add enum value(s) " + String.join(", ", added) + " to " + f.name()));
                }
                if (!removed.isEmpty()) {
                    structural = true;
                    steps.add(new Step(StepClass.DESTRUCTIVE, "Remove enum value(s) " + String.join(", ", removed) + " from " + f.name()));
                    checks.add(new PreCheck("Rows using removed values of " + f.name(), "SELECT COUNT(*) FROM " + table + " WHERE " + oldCol
                            + " IN (" + removed.stream().map(SqlDialect::quoteString).collect(Collectors.joining(", ")) + ")"));
                }
            }
            if (!Objects.equals(old.constraints(), f.constraints())) {
                structural = true;
                steps.add(new Step(StepClass.CHECKED, "Change constraints on " + f.name()));
                List<String> conds = ddl.columnChecks(f, oldCol);
                if (!conds.isEmpty()) {
                    checks.add(new PreCheck("Rows breaking the new constraints on " + f.name(), "SELECT COUNT(*) FROM " + table
                            + " WHERE " + oldCol + " IS NOT NULL AND NOT (" + String.join(" AND ", conds) + ")"));
                }
            }
            if (!Objects.equals(old.ref(), f.ref())) {
                structural = true;
                steps.add(new Step(StepClass.CHECKED, "Change reference of " + f.name() + " (checked by the foreign-key check)"));
            }
            if (!Objects.equals(old.defaultValue(), f.defaultValue())) {
                structural = true;
                steps.add(new Step(StepClass.SAFE, "Change default of " + f.name()));
            }
            List<String> meta = new ArrayList<>();
            if (!Objects.equals(old.label(), f.label())) {
                meta.add("label");
            }
            if (!Objects.equals(old.ui(), f.ui()) || !Objects.equals(old.json().get("ui"), f.json().get("ui"))) {
                meta.add("ui");
            }
            if (!Objects.equals(old.access(), f.access())) {
                meta.add("access");
            }
            if (!Objects.equals(old.requiredWhen(), f.requiredWhen())) {
                meta.add("requiredWhen");
            }
            if (!meta.isEmpty()) {
                steps.add(new Step(StepClass.METADATA, f.name() + ": " + String.join(", ", meta)));
            }
        }
        for (Field f : published.fields()) {
            if (!used.contains(f.name())) {
                structural = true;
                steps.add(new Step(StepClass.DESTRUCTIVE, "Drop column " + f.name() + ". Its data will be lost"));
            }
        }

        Map<String, IndexSpec> pix = index(published.indexes());
        Map<String, IndexSpec> dix = index(draft.indexes());
        for (IndexSpec ix : draft.indexes()) {
            IndexSpec before = pix.get(ix.name());
            if (before == null || !before.equals(ix)) {
                structural = true;
                steps.add(new Step(ix.unique() ? StepClass.CHECKED : StepClass.SAFE, (before == null ? "Create " : "Recreate ")
                        + (ix.unique() ? "unique " : "") + "index " + ix.name()));
            }
        }
        for (IndexSpec ix : published.indexes()) {
            if (!dix.containsKey(ix.name())) {
                structural = true;
                steps.add(new Step(StepClass.SAFE, "Drop index " + ix.name()));
            }
        }

        Map<String, Rule> prule = new LinkedHashMap<>();
        published.rules().forEach(r -> prule.put(r.id(), r));
        Set<String> draftRuleIds = new HashSet<>();
        for (Rule r : draft.rules()) {
            draftRuleIds.add(r.id());
            Rule before = prule.get(r.id());
            boolean sameCheck = before != null && ddl.ruleCheck(published, before).equals(ddl.ruleCheck(draft, r));
            if (before != null && before.equals(r)) {
                continue;
            }
            var check = ddl.ruleCheck(draft, r);
            if (check.isPresent() && !sameCheck) {
                structural = true;
                steps.add(new Step(StepClass.CHECKED, (before == null ? "Add" : "Change") + " rule " + r.id() + " (CHECK constraint)"));
                checks.add(new PreCheck("Rows breaking rule " + r.id(), "SELECT COUNT(*) FROM " + table + " WHERE NOT (" + check.get() + ")"));
            } else {
                steps.add(new Step(StepClass.METADATA, (before == null ? "Add" : "Change") + " rule " + r.id() + " (enforced by the service)"));
            }
        }
        for (Rule r : published.rules()) {
            if (!draftRuleIds.contains(r.id())) {
                boolean sqlRule = ddl.ruleCheck(published, r).isPresent();
                structural |= sqlRule;
                steps.add(new Step(sqlRule ? StepClass.SAFE : StepClass.METADATA, "Remove rule " + r.id()));
            }
        }
        if (!published.options().equals(draft.options())) {
            structural = true;
            steps.add(new Step(StepClass.SAFE, "Change table options (audit columns / row version)"));
        }
        for (String key : List.of("label", "description", "manageType", "actions", "access", "view")) {
            if (!Objects.equals(published.json().get(key), draft.json().get(key))) {
                steps.add(new Step(StepClass.METADATA, "Template " + key + " changed"));
            }
        }

        if (!structural) {
            return new MigrationPlan(draft.name(), steps, checks, List.of(), false);
        }
        if (!ddl.dialect().isSqlite()) {
            steps.add(new Step(StepClass.BLOCKED, "Structural changes on PostgreSQL are planned for M7; only metadata changes can be published"));
            return new MigrationPlan(draft.name(), steps, checks, List.of(), false);
        }
        return new MigrationPlan(draft.name(), steps, checks, rebuild(published, draft), true);
    }

    /** SQLite 12-step rebuild (https://www.sqlite.org/lang_altertable.html#otheralter), run with foreign keys off. */
    List<String> rebuild(Template published, Template draft) {
        String tmp = draft.name() + "__new";
        List<String> target = new ArrayList<>();
        List<String> source = new ArrayList<>();
        Set<String> pubNames = published.referenceableNames();
        for (Field f : draft.fields()) {
            String from = pubNames.contains(f.name()) && published.field(f.name()).isPresent() ? f.name()
                    : f.renamedFrom() != null && published.field(f.renamedFrom()).isPresent() ? f.renamedFrom() : null;
            if (from != null) {
                target.add(quote(f.name()));
                source.add(quote(from));
            }
        }
        for (String audit : List.of("created_at", "created_by", "updated_at", "updated_by", "row_version")) {
            if (pubNames.contains(audit) && draft.referenceableNames().contains(audit)) {
                target.add(quote(audit));
                source.add(quote(audit));
            }
        }
        List<String> sql = new ArrayList<>();
        sql.add(ddl.createTableStatement(draft, tmp));
        sql.add("INSERT INTO " + quote(tmp) + " (" + String.join(", ", target) + ") SELECT " + String.join(", ", source)
                + " FROM " + quote(published.name()));
        sql.add("DROP TABLE " + quote(published.name()));
        sql.add("ALTER TABLE " + quote(tmp) + " RENAME TO " + quote(draft.name()));
        sql.addAll(ddl.createIndexes(draft));
        return sql;
    }

    private static Map<String, IndexSpec> index(List<IndexSpec> list) {
        Map<String, IndexSpec> m = new LinkedHashMap<>();
        list.forEach(ix -> m.put(ix.name(), ix));
        return m;
    }
}
