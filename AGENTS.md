# AGENTS.md

Guidance for AI coding agents working on this repository. People should start with [README.md](README.md).

## What this is
Templated Data Service (TDS): one JSON template (grammar `tds/v1`) defines a table, its UI, role permissions and actions. TDS creates the database table, a REST API and a React UI for it, with no code change and no restart. One Spring Boot JAR serves the API and the React app.

## Setup and checks
JDK 21 and Maven 3.9+; Node 22+ only for UI development. Run commands from the project folder so `templates/` and `scripts/` are found.

```bash
mvn verify                    # backend unit + integration tests, ArchUnit rules
mvn -Pui verify               # also builds ui/ into the jar
cd ui && npm test && npm run build   # UI tests (Vitest) and type-checked build
java -jar target/templated-data-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev   # http://localhost:8080
```

## Code map
Backend root `src/main/java/io/github/aishwaryajayanth1820/tds/`. Each package exposes a small service API and keeps its internals package-private.

- `grammar`: the `tds/v1` model, parser, structural and semantic validators, JsonLogic engine. Pure Java.
- `catalog`: template lifecycle (draft, validate, plan, publish), versions, the immutable runtime snapshot.
- `ddl`: SQL dialects, DDL generation, migration planning and execution, value encoding.
- `data`: generic CRUD, query, validation and lookups for any published template.
- `access`, `security`: permissions per role, operation and field; users, roles, login.
- `script`: sandboxed GraalJS action scripts, hot-reloaded from `scripts/`.
- `meta`, `admin`, `web`, `config`, `jdbc`: UI metadata, admin APIs, error mapping, wiring, JDBC foundation.
- `ui/`: React + Mantine app. `schema/`: the grammar meta-schema shared by server and Studio. `testdata/parity/`: vectors shared by JUnit and Vitest.

Full design: [docs/02-architecture.md](docs/02-architecture.md) and [docs/03-application-design.md](docs/03-application-design.md).

## Rules
- ArchUnit tests (`ArchitectureTest`) enforce boundaries: `grammar` depends on no framework and no other TDS package, `jdbc` depends on no other TDS package, packages have no cycles, and REST controllers live only in `security`, `data`, `meta`, `admin` or `script`.
- Validation must behave identically in Java and TypeScript. When you change a rule or JsonLogic operator, update both sides and add vectors to `testdata/parity/`.
- SQL goes through `SqlDialect`; keep it portable (SQLite today, PostgreSQL planned).
- A change to the grammar, public REST API, database schema policy, security model, technology choice or module boundaries needs an ADR in `docs/adr/` first (see ADR-0001), and the design docs are updated in the same change.
- Done means: tests written, `mvn verify` green, `npm test && npm run build` green for UI changes, no new compiler warnings, and no `TODO` without a story ID (`TDS-<milestone><nn>`).
- Errors are returned as RFC 9457 `ProblemDetail`. Action scripts must stay inside the sandbox: no `Java.type`, file IO or threads.
