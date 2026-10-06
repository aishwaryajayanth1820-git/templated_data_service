# 0019. The MANAGE_VIEW example is vendor items

- **Status:** Accepted
- **Date:** 2026-10-06
- **Deciders:** Aabel, Zypher
- **Supersedes:** items 1, 5 and 6 of [0012](0012-requirement-example-interpretations.md)
- **Affects:** `templates/`, `templates/sample-data/`, tests, README, grammar §12, Studio prototype, Medium article

## Context

The requirement's `MANAGE_VIEW` example changed from operator settings
(`operator_name`, `jurisdictional_name`, `minSpinTime`) to a vendor example:

```
"vendor_name": datetime     -> UI: text, Roles: [Admin -> Add, Update, Delete]
"item_name":   varchar(255) -> UI: text, Roles: [Admin -> Add, Update, Delete]
"wattage":     float        -> UI: text, Roles: [Admin -> Add, Update, Delete]
```

## Decision

The `operator_settings` seed template is replaced by `vendor_items`, using the
same interpretation rules as ADR-0012:

1. `vendor_name` (written as `datetime`) is a **string(255)**. We read it as the
   same typo as in the earlier example.
2. The table is `vendor_items` ("Vendor Items"). A vendor + item pair is unique
   (`ux_vendor_item`).
3. `wattage` is a `double` with a minimum of 0, shown as `0.0 'W'`.
4. Every field is required. Admin can add, update and delete; viewer can read.

## Consequences

- New installations seed `vendor_items` with sample rows in the dev profile.
- An existing local database keeps its published `operator_settings` table.
  Seeding never deletes tables (ADR-0016). Delete the `data/` folder to start
  fresh with the new example.
