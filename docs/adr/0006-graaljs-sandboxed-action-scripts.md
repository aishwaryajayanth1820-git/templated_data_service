# 0006. Action scripts as files, run in a GraalJS sandbox

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Aabel, Zypher
- **Affects:** `script` package, grammar §8

## Context

UI buttons run injectable JavaScript. The scripts must be stored as files and
loaded again when the service reloads.

## Options considered

1. **Nashorn (standalone):** in maintenance mode, ES5-era, weak sandboxing.
2. **Rhino:** old, and sandboxing is hand-rolled.
3. **GraalJS polyglot:** modern ECMAScript with explicit sandbox options (no
   host access, IO, threads or native access). Published on Maven Central and
   runs on a stock JDK 21.

## Decision

- Use **GraalJS** (`org.graalvm.polyglot:polyglot` + `js-community`, 25.x).
- Scripts live in `scripts/**.js` and may only declare functions at top level.
  `ScriptRegistry` loads them at startup and a watcher hot-reloads them. A
  broken file keeps its last good version.
- Each run gets a fresh `Context` on a shared `Engine`, with
  `HostAccess.NONE`, no IO, no threads and no class lookup. `ctx` (plain-value
  proxies) is the only bridge. A watchdog cancels the run at `timeoutMs`, and
  concurrency is capped by a semaphore.
- File output only goes through `ctx.files.writeText`, which stays under
  `data/action-output/<template>/`.

## Consequences

- On a stock JDK the engine runs in interpreter mode (slower, but fine for small
  scripts). Running on GraalVM JDK 21 speeds it up without code changes.
- Script authors get a documented `ctx` API and nothing else.
