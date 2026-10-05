# 0015. Build the data grid on Mantine `Table` (no TanStack Table)

- **Status:** Accepted
- **Date:** 2026-10-04
- **Deciders:** Aabel, Zypher
- **Supersedes:** the "TanStack Table" part of [0010](0010-frontend-stack.md)
- **Affects:** `ui/src/data/DataGrid.tsx`

## Context

ADR-0010 chose TanStack Table as a headless grid. In the implementation, the
grid is fully server-driven: sorting, filtering and paging are all API
parameters (ADR-0011), mirrored one-to-one in the page URL. TanStack Table's
main value, client-side row models, is unused. Its current major version (v9)
also has a different API from the well-known v8.

## Decision

Render the grid with Mantine `Table` plus a small amount of our own code for
sortable headers, expandable detail rows and row actions. The rest of ADR-0010
stands (Mantine, TanStack Query, React Router, Monaco later for the Studio).

## Consequences

- One fewer dependency and no adapter layer between two table models.
- Client-side features such as column resizing and virtualisation are not
  available out of the box. If needed later, revisit with a new ADR.
