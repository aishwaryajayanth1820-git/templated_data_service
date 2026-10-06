# 0014. Add a `jdbc` foundation package

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Aabel, Zypher
- **Affects:** 03-application-design §2 (package map), `security`, `ddl`

## Context

M1 found that `security` already needs vendor-aware SQL (the "now" expression
for `updated_at`) and must read timestamps that are ISO text on SQLite and
`TIMESTAMPTZ` on PostgreSQL. The design placed `DialectResolver` in `ddl`. That
would make `security` depend on the DDL engine, which is the wrong direction.

## Decision

Add `io.github.aishwaryajayanth1820.tds.jdbc` as a foundation package with no dependencies on other TDS
packages (enforced by `ArchitectureTest.jdbcIsFoundation`):

- `DbVendor`: `SQLITE` or `POSTGRESQL`, with `nowSql()`.
- `DbVendorResolver`: detects the vendor once from the connection metadata.
  This replaces `ddl.DialectResolver`; `ddl` builds its `SqlDialect` from
  `DbVendor`.
- `JdbcTime`: reads `Instant` values from either storage form.

## Consequences

- `security`, `catalog` and `ddl` share one vendor source without depending on
  each other.
- `ddl.SqlDialect` stays focused on DDL and DML generation.
