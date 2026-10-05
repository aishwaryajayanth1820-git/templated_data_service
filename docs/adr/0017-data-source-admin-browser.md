# 0017. DATA_SOURCE tables get an admin-only data browser

- **Status:** Accepted
- **Date:** 2026-10-05
- **Deciders:** Aabel, Zypher
- **Affects:** grammar §3, `/admin/data/:name`, `MetaController` navigation

## Context

The requirement says `DATA_SOURCE` tables are filled directly and have "no
manage UI". Their rows (for example alert groups) still need occasional
maintenance, and the REST API already allows it for permitted roles.

## Decision

- No **end-user** UI: `DATA_SOURCE` tables never appear in the Data navigation
  or on the home page.
- Admins get a **data browser** at `/admin/data/:name`, listed under
  Administration. It's the generic grid with create, edit and delete, gated by
  the template's access rules like everything else.
- Non-admin roles can still read or write `DATA_SOURCE` rows through the REST
  API if the template grants it.

## Consequences

- Lookup values can be fixed without SQL access, but only by admins.
- If even admins must not edit a `DATA_SOURCE` from the UI, remove `create`,
  `update` and `delete` for admin in that template's access. Admin is a super
  role, though (ADR-0008), so this needs a new ADR if it's ever required.
