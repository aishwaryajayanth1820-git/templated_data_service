# 0011. Offset pagination with total count

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Aabel, Zypher
- **Affects:** `data` package, `/api/data/{t}` list contract

## Decision

List endpoints use `page` (0-based) and `size` (10, 25, 50 or 100; maximum 500
for API clients). Responses include `total` (a `COUNT(*)` with the same
filters). Sorting always appends `id` as a tiebreaker so that pages are stable.

## Consequences

- Simple page-number navigation in the UI.
- Deep pages on very large tables (millions of rows) get slower. If that shows
  up, add keyset paging (`after=<sort key>`) in a new ADR. The response shape
  leaves room for a `next` cursor.
