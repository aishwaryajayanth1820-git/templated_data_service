# 0013. Schema evolution policy

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Aabel, Zypher
- **Affects:** `ddl.migration` package, Studio publish dialog, grammar §10

## Decision

- A template's `name` and each field's `type` are **immutable after their first
  publish** (v1). A type change means adding a new field.
- Renames use `renamedFrom`. Studio sets it automatically, and the migration
  uses `RENAME COLUMN` (or the column mapping in a rebuild).
- Each migration step is classified as `metadata`, `safe`, `checked` (pre-check
  counts must be zero), `destructive` (the admin types the table name to
  confirm), or `blocked`.
- Each publish runs in **one transaction**. SQLite constraint changes use the
  official table rebuild: foreign keys off on a dedicated connection, then
  create `t__new`, copy, drop, rename, recreate the indexes, and run
  `foreign_key_check` before commit.
- Every publish stores the template JSON, the plan and the exact SQL applied in
  `tds_template_version`.

## Consequences

- No silent data loss; every structural change is reviewable and auditable.
- Large tables on SQLite pay a copy cost for constraint changes. Acceptable at
  development scale.
