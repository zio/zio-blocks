# openapi, codegen, smithy, mux and ringbuffer Scaladoc design

Follows `docs/superpowers/specs/2026-11-05-jwt-scaladoc-design.md` and the `scaladoc-rollout` process.

## Problem

Five modules still have gaps in their Scaladoc, measured on `main` at `07f71de18` with `python3 -I .claude/scripts/scaladoc-coverage.py <module>`:

| Module | Public members | Documented | % |
|---|---|---|---|
| openapi | 130 | 43 | 33 |
| codegen | 107 | 73 | 68 |
| smithy | 64 | 55 | 85 |
| mux | 46 | 43 | 93 |
| ringbuffer | 122 | 82 | 67 |
| Total | 469 | 296 | 63 |

Nobody has checked that the existing comments are true; earlier rollouts found 15% to 42% of inherited comments wrong or imprecise. `mux` and `ringbuffer` are concurrent code, where a false claim about thread safety, ordering, blocking or capacity is worse than none. Known bugs touch the comments: openapi (`encodeToString`/`encode` throw `NotImplementedError`, `md"..."` descriptions are not `==` after a round trip, descriptions end with `\n\n`), codegen (`given val` rendering, `Enum` loses its companion, unescaped `*/`, `macro` not escaped, `derives` and `Field.doc` never emitted), smithy (Smithy 2.0 syntax rejected, `use` traits resolved to `smithy.api`) and mux (two threads on `takeOutbound`/`receive`, half-closed `receive()` returns `None` forever, a closed stream leaves with queued messages). Nine existing `{{{` blocks (codegen 8 files, smithy 1) are never compiled. `--drift` shows 5 lines in `mux` (scala-2 vs scala-3 `Mux.scala`) and 30 in `ringbuffer` (jvm vs js).

## Goal

Every public member of the five modules has a true, short, useful comment. Tier-1 types have an overview, a compiled example and a link to the reference page. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0. `--drift` is empty, or each remaining line is justified in the PR.

## Decisions (Milad, 2026-11-06)

- **One PR** for all five modules (tooling, existing blocks made to compile, all Scaladoc).
- **Tier 1:**
  - openapi: `OpenAPI`, `OpenAPICodec`, `OpenAPIGen`, `Operation`, `PathItem`, `Components`, `ReferenceOr`
  - codegen: `ScalaEmitter`, `EmitterConfig`, `ScalaFile`, `TypeDefinition`, `CaseClass`, `SealedTrait`, `TypeRef`, `Field`
  - smithy: `SmithyModel`, `SmithyPrinter`, `ShapeId`, `Shape`, `StructureShape`, `ServiceShape`, `OperationShape`
  - mux: `Mux`, `MuxStream`, `MuxError`
  - ringbuffer: `SpscRingBuffer`, `MpscRingBuffer`, `SpmcRingBuffer`, `MpmcRingBuffer`
- **AGENTS.md:** add the five modules to the `scaladoc-check` module list only (diff shown to Milad before the edit). Downstream lists are untouched.

## Layout

- `openapi`, `mux`, `ringbuffer`: `crossProject(JSPlatform, JVMPlatform)`, `CrossType.Full`. `mux` JVM depends on `ringbuffer.jvm`; `mux` has `scala-2/` and `scala-3/` copies of `Mux.scala`.
- `codegen`, `smithy`: JVM `project`s. Packages `zio.blocks.codegen` (`.ir`, `.emit`) and `zio.blocks.smithy`.
- All five packages fit `zio.blocks.<module-dir>`, so no `packageOverrides`. The checker list gets `openapi.jvm`, `codegen`, `smithy`, `mux.jvm` and `ringbuffer.jvm`. Their cross versions must equal `scaladoc-check`'s (2.13.18, 3.9.0, 3.10.0); the first sbt run confirms this with `show <project>/crossScalaVersions`. A mismatch stops the work and goes to the coordinator.
- `ringbuffer` JS copies are separate implementations. Each copy's text is written from its own code; where behaviour differs (blocking, what thread safety means on one thread), the shared text states the difference inline so the copies stay identical.
- `mux` Scala 2 and 3 copies differ in signatures (`Either[MuxError, X]` vs `X | MuxError`). One shared text names both result shapes.
- Reference pages: `docs/reference/openapi.md`, `smithy.md`, `mux.mdx`, `docs/reference/codegen/`, `docs/reference/ringbuffer/`, `docs/reference/tooling-and-codegen/`. Errors found there go to the coordinator as docs-issues entries, not fixed here.

## Concurrency rule

Every claim about thread safety, ordering, capacity, blocking, wait-freedom or lock-freedom in `ringbuffer` and `mux` needs evidence from the code and, where possible, a JVM probe (concurrent producers/consumers, then check for lost or duplicated elements). A name alone is no evidence. Wording names only what was verified.

## Tasks

- **Task 1 (Opus):** tooling (`build.sbt` list, an extractor test if the pattern needs one, the style-guide module list), then `ringbuffer` and `mux`: tier 1, every gap, fact-check of every existing comment with a table (member, claim, kind, evidence), concurrency probes, drift cleanup, bug write-ups. Only Task 1 runs sbt.
- **Task 2 (Opus, parallel, disjoint files, scala-cli only):** `openapi`, `codegen`, `smithy`: tier 1, make the 9 existing `{{{` blocks compile (or `// no-check: <reason>`), fact-check of every existing comment, probes against published 0.0.56, bug write-ups. It reports READY_FOR_GATES; Task 1 runs the gates for both.
- **Task 3 (Sonnet, after Task 2 lists its tier-1 files):** tier-2 gaps on the openapi data classes, with a member list and probes; suspected bugs go to a suspects list.
- **Review:** fresh-context Opus reviewers (one for `mux`+`ringbuffer`, one for the other three), each re-running probes and every bug reproducer; a fix round; a Sonnet re-review of the fix diff.

## Gates

1. `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test`
2. `<module>/doc` for each (JVM and JS where cross-built) on 3.9.0 and 2.13.18, no new warnings (read the logs; the client can exit 0 on a failed task)
3. coverage 100% per module, `--drift` empty or justified
4. `comments-only.py 07f71de18 <module>` prints 0 with code changes, for each module
5. `++2.13.18; check; headerCheckAll`, `scalafmtAll`, `scalafmtSbt`
6. Before every sbt run: `free -g` shows at least 6 GB available, in its own command; `sbt --client shutdown` after each gate round.

## Bug reporting

All entries go to the coordinator as one file, each with a target line (file and section; AMEND names the exact existing `###` title), after a reviewer has re-run every reproducer. Scaladoc calls known bugs "a known bug" and never links `docs/dev/`. A real security weakness goes to the coordinator marked SECURITY and stays out of every tracked file.

## Out of scope

Library code changes, reference-page rewrites, `docs/dev/` files, CI files, other modules.
