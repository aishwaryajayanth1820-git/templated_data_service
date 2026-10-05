# 0003. JsonLogic subset with in-house evaluators (Java + TS)

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Aabel, Zypher
- **Affects:** `02-architecture.md` §2 (replaces the `json-logic-java` / `json-logic-js` entries), `grammar.logic` package, `ui/src/grammar/jsonlogic.ts`

## Context

Conditions (`requiredWhen`, `visibleWhen`, `readonlyWhen`, rule `when` and
`assert`) must give **identical** results in the browser (instant feedback) and
on the server (authoritative). The architecture draft named `json-logic-java`
and `json-logic-js`. Looking closer:

- `json-logic-java` (1.1.0) parses the rule text on every evaluation, brings in
  Gson alongside Jackson 3, and implements the full operator set. Its edge cases
  (loose equality, truthiness of empty arrays) would need to match
  `json-logic-js` exactly.
- We need the custom `present` operator on both sides anyway.
- The accepted Studio prototype already contains a small, well-defined operator
  subset.

## Decision

- Use **JsonLogic as the rule format**, with a **documented operator subset**:
  `var`, `==`, `===`, `!=`, `!==`, `!`, `!!`, `and`, `or`, `if`, `<`, `<=`, `>`,
  `>=`, `in`, `+`, `-`, `*`, `/`, `%`, `present`.
- Implement two **small evaluators of our own**: Java (`JsonLogic`, compiled once
  into an AST per template) and TypeScript (ported from the prototype).
- Shared parity vectors in `testdata/parity/jsonlogic.json` run in JUnit and
  Vitest.
- Unsupported operators are rejected when a template is validated (E032).

## Consequences

- No Gson, and no per-evaluation parsing: rules are compiled when the catalog
  snapshot is built.
- Adding an operator means changing both evaluators and the vectors. That's
  deliberate.
- We could swap in a library later without changing the template format.
