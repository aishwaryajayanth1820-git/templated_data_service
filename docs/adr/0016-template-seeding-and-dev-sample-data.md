# 0016. Template seeding imports new templates only; dev profile publishes and loads sample rows

- **Status:** Accepted
- **Date:** 2026-10-05
- **Deciders:** Aabel, Zypher
- **Affects:** grammar §10 (seed behaviour), `catalog.TemplateSeeder`, `data.SampleDataSeeder`, `application-dev.yml`

## Context

Grammar §10 said a template file "whose checksum is newer" would be
imported as a draft at startup. In practice, that would overwrite drafts an
admin is editing in the Studio whenever the file on disk differs. We also need a
fast way to get a testable system: published seed tables with realistic rows.

## Decision

- At startup, `templates/*.json` files whose template name is **not in the
  database yet** are imported as drafts. Existing drafts are never overwritten
  from files; use Studio → Import JSON to replace one deliberately.
- `tds.seed.auto-publish` (on in the `dev` profile) publishes never-published
  drafts in reference order (ref targets first).
- `tds.seed.sample-data` (on in the `dev` profile) fills **empty** published
  tables from `templates/sample-data/<table>.json`. The rows are inserted
  directly, bypassing app validation, the way an external process would load
  `VIEW` / `DATA_SOURCE` data. That's why a sample row can show the ⚠ rule flag.
- Production defaults: import on, auto-publish off, sample data off.

## Consequences

- Studio edits are safe from file changes on restart.
- `java -jar … --spring.profiles.active=dev` gives a ready-to-test system on an
  empty database.
