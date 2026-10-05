# 0001. Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Aabel, Zypher
- **Affects:** process

## Context

The project is agile: requirements will change as we build. Without a record,
later readers can't tell why the design looks the way it does, or which parts of
the design docs are still current.

## Decision

- Every requirement change, architecture change and significant design decision
  gets an ADR in `docs/adr/`, using the template in `0000-template.md`.
- An ADR is written **before** the code that implements it, and it is linked
  from the story that delivers it.
- Accepted ADRs are immutable; changes come as a new ADR that supersedes the old
  one.
- The design docs (`01-schema-grammar`, `02-architecture`,
  `03-application-design`, `04-development-plan`) are kept current and link the
  relevant ADRs.

**When an ADR is needed:** a change to the grammar, public REST API, database
schema policy, security model, technology choice, or module boundaries, or any
reinterpretation of `project_requirement.md`. Refactors inside one class and bug
fixes don't need one.

## Consequences

- There's a small overhead per decision, in exchange for a traceable history.
- ADRs 0002–0013 retroactively record the decisions made during the grammar and
  architecture phase.
