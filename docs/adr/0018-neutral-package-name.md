# 0018. Use a neutral package and group ID

- **Status:** Accepted
- **Date:** 2026-10-06
- **Deciders:** Aabel, Zypher
- **Affects:** `pom.xml`, all Java sources and tests, logging configuration, docs (02 §15, 03, ADR-0014)

## Context

The project started with a company-style Java package and Maven group ID. The
repository is now public and is the companion to a Medium
article, so its identifiers should not suggest it belongs to a company.

## Decision

- Maven group ID: `io.github.aishwaryajayanth1820`
- Java package root: `io.github.aishwaryajayanth1820.tds`
- Artifact ID stays `templated-data-service`.

`io.github.<account>` is the usual convention for projects hosted on GitHub: it
names something the author controls, not a company domain.

## Consequences

- Package names are longer; behaviour is unchanged.
- The files were moved with `git mv`, so each file's history follows it. Commits
  made before this ADR still contain the old package name in their history. Removing it from
  history would need a history rewrite and a force-push, which was not done.
