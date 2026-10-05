# TDS Development Plan

Status: **Active** · Owner: Zypher / Aabel · Started 2026-10-03
Design: [03-application-design.md](03-application-design.md) · Decisions: [ADR log](adr/README.md)

## Working agreement

- **Iterations** map to milestones (M1–M7). Each ends with a demo and a review
  before the next one starts.
- **Story IDs** are `TDS-<milestone><nn>`, used in commit messages
  (`TDS-103: JSON login endpoint`).
- **Requirement change →** an ADR (Proposed) → review → Accepted → a story in
  the backlog → design docs updated in the same change as the code.
- **Definition of Done** for every story:
  1. The code follows 03-application-design (or the doc is updated in the same
     change).
  2. Tests are written and `mvn verify` is green; for UI stories,
     `npm test && npm run build` is green.
  3. No new compiler warnings; no `TODO` without a story ID.
  4. The behaviour is demonstrable (HTTP file, test, or UI).
  5. An ADR exists if the change meets the ADR-0001 criteria.

**Status legend:** ☐ to do · ◐ in progress · ☑ done

---

## M1 — Skeleton (iteration 1) ☑ done 2026-10-03

Goal: an empty but production-shaped service you can log in to.

| ID | Story | Acceptance criteria | Status |
|---|---|---|---|
| TDS-101 | Maven project on Spring Boot 4.1.1, Java 21 | `mvn verify` runs on `C:\InstalledSofts\jdk-21.0.11`; `--release 21`; `ArchitectureTest` in place | ☑ |
| TDS-102 | SQLite datasource | The DB file is created under `./data/`; `foreign_keys=ON`, `journal_mode=WAL` and `busy_timeout` are verified by a test; the PostgreSQL profile is present but not used yet | ☑ |
| TDS-103 | System tables via Flyway | `V1__system.sql` for sqlite **and** postgresql: `tds_user`, `tds_role`, `tds_user_role`, `tds_template`, `tds_template_version`, `tds_action_log`; built-in roles are seeded | ☑ |
| TDS-104 | Error model | `ApiException` family + `ApiExceptionHandler` → `ProblemDetail` with `code`; unit-tested | ☑ |
| TDS-105 | Authentication | `GET /api/auth/csrf`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`; session cookie; CSRF enforced; 401 JSON for anonymous `/api/**` | ☑ |
| TDS-106 | Bootstrap admin and forced password change | First start creates `admin` (env password or generated and logged once) with `must_change_password`; other `/api/**` calls get 403 `PASSWORD_CHANGE_REQUIRED` until `POST /api/auth/password` succeeds | ☑ |
| TDS-107 | UI scaffold | `ui/` with Vite + React + TS + Mantine + Router + Query; login page, forced change-password modal, app shell with user menu and logout, light/dark | ☑ |
| TDS-108 | Single-jar build | `mvn -Pui package` bundles the UI; `java -jar` serves the SPA and the API on `:8080`; deep links forward to `index.html` | ☑ |

**Demo:** start the jar, log in as `admin`, get forced to change the password,
see an empty shell, log out.

## M2 — Grammar & catalog (iteration 2) ☑ done 2026-10-05

| ID | Story | Acceptance criteria | Status |
|---|---|---|---|
| TDS-201 | Java grammar model + parser | The 3 seed templates parse; invalid ones give S-codes with paths | ☑ `GrammarTest` |
| TDS-202 | Structural validator (networknt 3.x, shared meta-schema) | Same accept/reject results as ajv on the 3 + 9 fixtures | ☑ seeds and broken fixtures; paths in Studio format |
| TDS-203 | JsonLogic evaluator (Java + TS) + parity vectors | `testdata/parity/jsonlogic.json` passes in JUnit and Vitest | ☑ 28 vectors on both sides |
| TDS-204 | Semantic validator | One failing fixture per E/W code; messages match the Studio | ◐ E001/E010/E020/E030/E031/E032/E040/E041 covered; the rest exercised only end to end |
| TDS-205 | Dialects + DDL generator | DDL for the seeds × 2 dialects matches the prototype | ☑ `DdlAndPlannerTest` (key-line assertions rather than golden files) |
| TDS-206 | Migration planner | Classification table of ADR-0013 covered by unit tests | ☑ |
| TDS-207 | Migration executor (SQLite rebuild) | Rename, drop, enum add, NOT NULL on real SQLite; pre-check blocks; `foreign_key_check` enforced | ☑ `PublishIT`. PostgreSQL ALTER path → M7 |
| TDS-208 | Catalog snapshot, drafts, publish API | `/api/admin/templates/**`; generation header; stale checksum 409 | ☑ |
| TDS-209 | Seeder | Absent templates imported as drafts; dev profile auto-publishes in ref order and loads sample rows | ☑ [ADR-0016](adr/0016-template-seeding-and-dev-sample-data.md) |

## M3 — Data API (iteration 3) ☑ done 2026-10-05

| ID | Story | Status |
|---|---|---|
| TDS-301 | `ValueCodec` for every type, both dialects | ☑ SQLite tested; PostgreSQL conversions are written but untested until M7 |
| TDS-302 | Query parsing, filters, search, sort, paging (ADR-0011) | ☑ `RecordServiceIT` |
| TDS-303 | CRUD with field-level shaping, defaults, audit columns, optimistic lock | ☑ |
| TDS-304 | Record validator; DB error translation (FK/unique/check) | ☑ Server-side flags (`$issues`) replace a client-side record-validator port |
| TDS-305 | Lookup options for `ref` fields | ☑ |
| TDS-306 | `/api/meta` navigation + per-template metadata | ☑ |
| TDS-307 | Permission matrix IT (role × op × field × manage type) | ◐ Key cases covered (viewer read-only, DATA_SOURCE hidden, field hide/narrow); a full matrix is still to do |

## M4 — Scripts (iteration 4) ☑ done 2026-10-05

| ID | Story | Status |
|---|---|---|
| TDS-401 | `ScriptRegistry` load + function discovery + last-good-on-error | ☑ `ActionIT` |
| TDS-402 | `ScriptWatcher` hot reload | ☑ Implemented; covered manually, no automated test yet |
| TDS-403 | Sandboxed `ActionRunner` (timeouts, semaphore, ctx API) | ☑ No host access, timeout, read-only ctx tested |
| TDS-404 | `ActionOutputStore` + download endpoint | ☑ Path traversal refused |
| TDS-405 | Action endpoints + `tds_action_log` | ☑ |
| TDS-406 | Scripts admin API (list, read, write) | ◐ The "test a function with a sample record" endpoint is not built |

## M5 — Generic UI (iteration 5) ☑ done 2026-10-05

| ID | Story | Status |
|---|---|---|
| TDS-501 | Nav from `/api/meta`; generation-change refresh | ☑ |
| TDS-502 | `DataGrid` (server-side sort, filter, paging, URL state, expand rows, ⚠ flags) | ☑ [ADR-0015](adr/0015-grid-on-mantine-table.md) |
| TDS-503 | `RecordDrawer` forms (widgets, groups, conditions, server error mapping, 409 handling) | ☑ |
| TDS-504 | Actions (buttons, confirm, result modal, download) | ☑ |
| TDS-505 | Roles admin page; users admin page | ☑ |
| TDS-506 | DATA_SOURCE data browser (admin) | ☑ [ADR-0017](adr/0017-data-source-admin-browser.md) |

## M6 — Schema Studio in React (iteration 6) ☑ done 2026-10-05

| ID | Story | Status |
|---|---|---|
| TDS-601 | Template list, create, import/export | ☑ |
| TDS-602 | Fields tab + inspector (rename and delete cascade) | ☑ |
| TDS-603 | Rules, actions (script editor on the real file), access, view tabs | ☑ |
| TDS-604 | JSON tab, drafts saved with checksum | ◐ Plain monospace editor plus server validation; Monaco with schema autocomplete not yet. Explicit "Save draft" rather than autosave |
| TDS-605 | Publish dialog (server plan, pre-check counts, DDL for both dialects, confirm) | ☑ |
| TDS-606 | Preview tab | ☐ Not built: use "Open table" for published templates |
| TDS-607 | Versions tab (history with the SQL applied) | ☑ added |

## M7 — PostgreSQL (iteration 7) ☐

| ID | Story |
|---|---|
| TDS-701 | `postgres` profile + Testcontainers IT suite |
| TDS-702 | ALTER-based migration strategy for PostgreSQL (structural changes are blocked on PostgreSQL until then) |
| TDS-703 | `migrate` command (SQLite → PostgreSQL copy, sequence reset, verification) |
| TDS-704 | Runbook in `docs/` |

## Known gaps / refinement backlog

| ID | Item |
|---|---|
| TDS-801 | Dropping a published table from the Studio (only unpublished drafts can be deleted today) |
| TDS-802 | Monaco editor with meta-schema autocomplete for JSON and scripts (TDS-604) |
| TDS-803 | Studio preview tab with sample rows for unpublished drafts (TDS-606) |
| TDS-804 | Script test endpoint and button (TDS-406) |
| TDS-805 | Full permission-matrix IT and one fixture per semantic code (TDS-204, TDS-307) |
| TDS-806 | Playwright e2e suite in the repo (the browser flows were verified with a scratch Puppeteer script during development) |

---

## Change log

| Date | Change | ADR |
|---|---|---|
| 2026-10-03 | Plan created; JsonLogic library replaced by in-house evaluators | [0003](adr/0003-jsonlogic-in-house-evaluators.md) |
| 2026-10-03 | M1 done: 23 backend + 9 UI tests. `jdbc` foundation package added; `ApiException` simplified to one class with factories; SPA fallback done as a resource resolver | [0014](adr/0014-jdbc-foundation-package.md) |
| 2026-10-04 | Grid built on Mantine `Table` instead of TanStack Table | [0015](adr/0015-grid-on-mantine-table.md) |
| 2026-10-05 | M2–M6 done: 49 backend unit + 24 integration + 45 UI tests; all requirement flows verified in a browser on the packaged jar. Seeding policy and admin data browser recorded | [0016](adr/0016-template-seeding-and-dev-sample-data.md), [0017](adr/0017-data-source-admin-browser.md) |
