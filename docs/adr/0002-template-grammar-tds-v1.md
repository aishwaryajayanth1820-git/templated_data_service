# 0002. Template grammar `tds/v1`

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Aabel, Zypher
- **Affects:** `docs/01-schema-grammar.md`, `schema/tds-template.schema.json`, `grammar` package, Schema Studio

## Context

The requirement asks for a schema format that maps each field to a DB type, a
UI element, role permissions and constraints, including conditional ones ("if x
is present, y must be too"). We looked for an established standard to adopt.

## Options considered

1. **JSON Schema + a UI schema (JSON Forms / RJSF):** describes documents, not
   relational tables. It has no FK, index or RBAC concepts, and we'd end up
   maintaining two schemas per table.
2. **Copy one product's format (Directus, Frappe DocType, Strapi):** each one is
   tied to its product. None covers actions, and Frappe uses unsafe `eval:`
   strings.
3. **Our own small vocabulary shaped like Directus and Frappe, using standards
   for the sub-parts.**

## Decision

Option 3: `tds/v1`. One JSON document per table. Each field has a storage part
(`type`, constraints), a UI part (`ui`) and an access part (`access`). Rules use
JsonLogic ([0003](0003-jsonlogic-in-house-evaluators.md)). The document is
validated by a JSON Schema 2020-12 meta-schema and then by semantic checks
(codes E0xx/W0xx). Keys are camelCase; table and field names are snake_case.

## Consequences

- One source of truth per table drives the DDL, the REST API, the UI and the
  validation.
- We own the grammar's evolution. The `grammar` key (`tds/v1`) lets us version
  it.
- Studio and the server must interpret the grammar identically. This is enforced
  by the parity test vectors (see 03-application-design §12).
