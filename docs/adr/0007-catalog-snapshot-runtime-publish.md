# 0007. In-memory catalog snapshot; publish without restart

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Aabel, Zypher
- **Affects:** `catalog`, `data`, `meta` packages; React data pages

## Context

The requirement says a template must create a table, an API and a UI "without
any code change". Requests must never see a half-published template.

## Decision

- Published templates are compiled into an immutable `CatalogSnapshot`, held in
  a `volatile` field.
- `PublishService` applies the migration in one DB transaction, stores the
  version, and only **after commit** swaps in a new snapshot with generation+1.
- Each request reads the snapshot once and uses it throughout.
- Every API response carries an `X-TDS-Catalog-Generation` header. The SPA
  refetches `/api/meta` when the value changes.
- The REST layer and the React UI are generic and driven entirely by the
  snapshot.

## Consequences

- No restart or rebuild to add or change a table.
- Single-instance only for v1. Multi-instance would need snapshot refresh
  (polling the version table or a notification), to be recorded as a new ADR
  when needed.
