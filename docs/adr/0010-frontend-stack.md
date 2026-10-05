# 0010. Frontend stack: React, Mantine, TanStack Table/Query

- **Status:** Accepted (grid part superseded by [0015](0015-grid-on-mantine-table.md))
- **Date:** 2026-10-03
- **Deciders:** Aabel, Zypher
- **Affects:** `ui/`

## Options considered

1. **Mantine + TanStack Table:** a complete component set with light and dark
   themes, and a headless grid we style ourselves. All MIT-licensed.
2. **AG Grid Community:** a feature-rich grid out of the box, but heavy, harder
   to theme consistently, and with enterprise features behind a licence.
3. **MUI:** a solid component set, but its data grid's advanced features are
   commercial.

## Decision

React 19 + TypeScript + Vite; **Mantine** for components; **TanStack Table** for
grids (manual or server-side mode); **TanStack Query** for server state; **React
Router**; **Monaco** (lazy-loaded) for the Studio's JSON and script editors;
**ajv** for the meta-schema in the Studio.

## Consequences

- We build grid features ourselves (column resize, sticky header, expand rows),
  as the prototype already does.
- A small and consistent dependency set.
