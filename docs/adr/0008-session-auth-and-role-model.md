# 0008. Session auth, built-in `admin`/`viewer`, field-level narrowing

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Aabel, Zypher
- **Affects:** `security`, `access` packages; grammar §6

## Context

The requirement: keep auth simple (username + password); roles are managed
separately; an admin creates roles and assigns them to users; templates map
roles to UI and schema; viewer is the default role.

## Decision

- **Authentication:** Spring Security, JSON login at `/api/auth/login`,
  server-side HTTP session (HttpOnly cookie), BCrypt(12), CSRF token in a cookie
  for the SPA.
- **Bootstrap:** if there are no users, create `admin`. The password comes from
  `TDS_ADMIN_PASSWORD`, or is generated and logged once. The user must change it
  on first login (`must_change_password`). Until then, every API outside
  `/api/auth/**` answers 403 `PASSWORD_CHANGE_REQUIRED`.
- **Roles:** `admin` (super role, never narrowed) and `viewer` (implicitly held
  by every user, never stored per user) are built in and can't be deleted. Other
  roles are free-form and assigned by an admin.
- **Permissions:** table operations `read`, `create`, `update`, `delete`,
  `run:<action>`. Field operations `read`, `create`, `update` only narrow the
  table level. Effective permission is the union over the user's roles.

## Consequences

- No external IdP for v1. SSO can be added later as a new ADR without changing
  the role model.
- Sessions are in memory, so they don't survive a restart (users log in again).
  Acceptable for v1.
