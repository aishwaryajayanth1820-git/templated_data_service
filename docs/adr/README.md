# Architecture Decision Records

We work in an agile way. Any change to requirements, architecture or a design
decision is recorded here as an ADR **before** the code changes. The design
documents (`docs/01…04`) describe the current state. ADRs explain how we got
there and why.

## How to add an ADR

1. Copy [`0000-template.md`](0000-template.md) to `NNNN-short-title.md`, using
   the next free number.
2. Fill in Context, Decision and Consequences. Keep it to one page.
3. Set **Status: Proposed** and get it reviewed. Once agreed, set **Accepted**
   and the date.
4. Update the affected design doc sections and link the ADR from them.
5. Never edit an accepted ADR's decision. Write a new ADR that **supersedes** it,
   and mark the old one `Superseded by NNNN`.

Statuses: `Proposed` → `Accepted` → (`Superseded by NNNN` | `Deprecated`). A
rejected proposal is kept with status `Rejected`.

## Index

| # | Title | Status | Date |
|---|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted | 2026-10-03 |
| [0002](0002-template-grammar-tds-v1.md) | Template grammar `tds/v1` | Accepted | 2026-10-03 |
| [0003](0003-jsonlogic-in-house-evaluators.md) | JsonLogic subset with in-house evaluators (Java + TS) | Accepted | 2026-10-03 |
| [0004](0004-portable-sql-sqlite-first.md) | Portable SQL: SQLite first, PostgreSQL-compatible | Accepted | 2026-10-03 |
| [0005](0005-spring-boot-4-and-spring-jdbc.md) | Spring Boot 4.1 with Spring JDBC (no JPA) | Accepted | 2026-10-03 |
| [0006](0006-graaljs-sandboxed-action-scripts.md) | Action scripts as files, run in a GraalJS sandbox | Accepted | 2026-10-03 |
| [0007](0007-catalog-snapshot-runtime-publish.md) | In-memory catalog snapshot; publish without restart | Accepted | 2026-10-03 |
| [0008](0008-session-auth-and-role-model.md) | Session auth, built-in `admin`/`viewer`, field-level narrowing | Accepted | 2026-10-03 |
| [0009](0009-single-deployable.md) | Single deployable (React bundled into the Spring Boot jar) | Accepted | 2026-10-03 |
| [0010](0010-frontend-stack.md) | Frontend stack: React, Mantine, TanStack Table/Query | Accepted | 2026-10-03 |
| [0011](0011-offset-pagination.md) | Offset pagination with total count | Accepted | 2026-10-03 |
| [0012](0012-requirement-example-interpretations.md) | Interpretation of the requirement's example schemas | Accepted | 2026-10-03 |
| [0013](0013-schema-evolution-policy.md) | Schema evolution policy | Accepted | 2026-10-03 |
| [0014](0014-jdbc-foundation-package.md) | Add a `jdbc` foundation package | Accepted | 2026-10-03 |
| [0015](0015-grid-on-mantine-table.md) | Build the data grid on Mantine `Table` (supersedes part of 0010) | Accepted | 2026-10-04 |
| [0016](0016-template-seeding-and-dev-sample-data.md) | Seeding imports new templates only; dev profile publishes and loads sample rows | Accepted | 2026-10-05 |
| [0017](0017-data-source-admin-browser.md) | DATA_SOURCE tables get an admin-only data browser | Accepted | 2026-10-05 |
| [0018](0018-neutral-package-name.md) | Use a neutral package and group ID | Accepted | 2026-10-06 |
