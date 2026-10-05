package com.lnw.tds.ddl;

import java.util.List;

/** What publishing a draft will do to the database (ADR-0013). */
public record MigrationPlan(String table, List<Step> steps, List<PreCheck> preChecks, List<String> statements, boolean rebuild) {

    public enum StepClass { METADATA, SAFE, CHECKED, DESTRUCTIVE, BLOCKED }

    public record Step(StepClass cls, String description) {}

    /** A query counting existing rows that would break the change; any count above zero blocks the publish. */
    public record PreCheck(String description, String countSql) {}

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public boolean hasBlocked() {
        return steps.stream().anyMatch(s -> s.cls() == StepClass.BLOCKED);
    }

    public boolean hasDestructive() {
        return steps.stream().anyMatch(s -> s.cls() == StepClass.DESTRUCTIVE);
    }

    public boolean changesSchema() {
        return !statements.isEmpty();
    }
}
