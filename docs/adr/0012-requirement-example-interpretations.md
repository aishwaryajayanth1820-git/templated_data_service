# 0012. Interpretation of the requirement's example schemas

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Aabel, Zypher
- **Affects:** `templates/*.json`, `scripts/alerts/create_ticket.js`

## Context

The pseudo-schemas in `project_requirement.md` leave some details open or look
inconsistent. These interpretations were proposed in 01-schema-grammar §12 and
accepted with the Studio prototype review.

## Decision

1. `operator_name` (written as `datetime`) is a **string(255)**. We read the
   original as a typo.
2. `alert_group` ("varchar(255) via FK alert_groups") stores the **FK id**. The
   UI and API also return the group name as `alert_group$display`.
3. `alert_type` enum values are `CRITICAL`, `MAJOR`, `MINOR`, `INFO`.
4. `run:create_ticket` is granted to `admin` and `viewer` (it only produces a
   text file).
5. The unnamed `MANAGE_VIEW` example is `operator_settings`, unique on
   `(operator_name, jurisdictional_name)`.
6. `minSpinTime` becomes `min_spin_time` (snake_case naming rule).
7. UI terms: `row_label` → `ui.placement: column`; `Label` → `detail`;
   `NIL` → `hidden`; `Roles: Nil` → system-managed and read-only.

## Consequences

The seed templates in `templates/` implement exactly these. Changing any of them
later is a requirement change and needs a new ADR.
