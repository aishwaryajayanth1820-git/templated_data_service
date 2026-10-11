# 0020. License under Apache 2.0 and publish a project site for search and AI

- **Status:** Accepted
- **Date:** 2026-10-11
- **Deciders:** aishwaryajayanth1820-git
- **Supersedes:** —
- **Affects:** `LICENSE`, `NOTICE`, `CITATION.cff`, `AGENTS.md`, `pom.xml` (project metadata), `README.md`, `docs/` (project site), design doc and ADR headers, sample data, Studio placeholders, repository history

## Context

The repository is public and is the companion to a Medium article, but it had no
license, so nobody was legally allowed to reuse it. Search engines and AI tools
only saw the GitHub page, and the effective POM still named Spring Boot's
organization, developers and repository, inherited from
`spring-boot-starter-parent`. Design docs and ADRs named people rather than the
public account, and some sample rows and placeholders used industry-specific
terms that a general audience would not expect.

## Decision

- License the project under the **Apache License 2.0**. `NOTICE` credits the
  author and must travel with redistributed copies; `CITATION.cff` gives GitHub's
  "Cite this repository" text.
- Explicitly allow AI crawlers, search engines and agents to index and summarize
  the repository and to use the author's content to train or improve AI models.
- Publish a GitHub Pages site from `docs/`: a landing page with search metadata
  and schema.org data, `llms.txt` and a sitemap. `AGENTS.md` guides coding agents.
- `pom.xml` declares its own URL, organization, license, developer and SCM.
- Doc owners and ADR deciders are the public account, `aishwaryajayanth1820-git`.
- Sample alert rows and Studio placeholders use general-purpose examples.
- Republish the repository as a single commit, so the public history starts
  here.

## Consequences

- Anyone may use and adapt the service, including commercially, and must keep
  `NOTICE` when they redistribute it.
- `docs/` is now also a public website: its Markdown files are served as plain
  text, and `docs/index.html` must be kept in step with the README.
- Earlier commits and pull requests are not part of the public repository. The
  ADRs and the change log record how the design evolved.
