# 0009. Single deployable (React bundled into the Spring Boot jar)

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Aabel, Zypher
- **Affects:** build (`pom.xml`, `ui/`), `web` package

## Decision

- `mvn -Pui package` builds `ui/` (Vite) with `frontend-maven-plugin` and
  copies `ui/dist` into `classpath:/static`. `SpaForwardController` serves
  `index.html` for client routes.
- In development: Vite on `:5173` proxies `/api` to Spring Boot on `:8080`.
- Without `-Pui` the build is backend-only (fast inner loop, CI backend job).

## Consequences

- One artifact to deploy and version, with same-origin requests: no CORS, and
  cookies just work.
- The UI and API are released together, which is fine at this scale.
