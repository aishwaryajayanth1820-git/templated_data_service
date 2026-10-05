# 0005. Spring Boot 4.1 with Spring JDBC (no JPA)

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Aabel, Zypher
- **Affects:** whole backend

## Context

The requirement asks for a Spring Boot service compatible with Java 21.
Managed tables are created at runtime from templates, so no compile-time entity
classes can exist for them.

## Decision

- Spring Boot **4.1.x** (Spring Framework 7, Jackson 3), compiled with
  `--release 21`, run on `C:\InstalledSofts\jdk-21.0.11`.
- **Spring JDBC** (`JdbcClient`) for both the dynamic tables and the system
  tables. One access style everywhere; no JPA or Hibernate.
- Maven build; versions come from the Spring Boot BOM wherever possible.

## Consequences

- The SQL is explicit and dialect-aware, which is easy to reason about for
  dynamic DDL.
- Mapping system-table rows is hand-written (a few small repositories).
- Jackson 3 packages (`tools.jackson.*`) apply; libraries must be on their
  Jackson 3 lines (for example networknt json-schema-validator 3.x).
