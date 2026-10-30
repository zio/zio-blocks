# Scope module Scaladoc design

Follows `docs/superpowers/specs/2026-10-29-chunk-scaladoc-design.md` and the `scaladoc-rollout` process.

## Problem

`scope` has comments on 125 of 223 public API members (56%): `scope` 99/176, `scope.internal` 26/47. Nobody has checked that the existing comments are true; the `chunk` pilot found about one in five inherited comments wrong. The module has 23 `{{{` example blocks (18 of them under an `@example` tag) that no build step compiles, and several refer to undefined names such as `Database` or `stream`. Twelve members have different text in their `scala-2/` and `scala-3/` copies. The module has ten known bugs (closed `AutoCloseable` escaping on Scala 3, single-use `Resource.from` vals, eager `Wire(value)`, and others) that its comments must describe truthfully.

## Goal

Every public member of `scope` has a true, short, useful comment. The seven core types have an overview, a compiled example and a reference-page link. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0.

## Decisions (Milad, 2026-10-30)

- **Tier 1:** `Scope`, `Resource`, `Wire`, `Finalizer`, `Unscoped`, `Finalization`, `DeferHandle`, each with its key methods and companion.
- **One PR** (tooling, 23 blocks fixed, Scaladoc), because the existing blocks are few enough to fix in the same change.
- **Macros** (`scala-2/` and `scala-3/` macro files, `internal/`): tier 3, one line per entry point and helper, nothing deeper.
- **Examples:** follow `scope/AGENTS.md` (`import scope.*`, unqualified `$`). Sample data is resources (connections, files, pools, loggers, counters), strings and numbers. Zionomicon at most once or twice per file, preferably never.
- **Per-version differences** (macro `$`/`scoped`, `Unscoped` derivation) get one shared text that names the difference.
- **`@example` tags** are dropped; the plain `{{{` block stays and is made self-contained.

## Layout

`scope/shared/src/main/scala/zio/blocks/scope/` (7 files plus `internal/` 3), `scala-2/` (8 plus 2), `scala-3/` (11 plus 3), `jvm/` and `js/` one `PlatformScope` each. Copies carry identical text (`--drift`).

## Tiers and waves

- **Task 1 (Opus):** add `scope.jvm` to `scaladocCheckedModules`, add an extractor test, update the style guide count and the two AGENTS.md lines. Make the 23 existing blocks compile. Write tier 1 for the seven types. Fact-check every existing comment in the module and record a table (member, claim, kind, evidence). Describe the ten known bugs as "a known bug".
- **Task 2 (Sonnet), after Task 1:** tier 2 and 3 members: the `Unscoped` instances on both Scala versions, `InStack`, `PlatformScope`, `internal/ErrorMessages`, `Finalizers`, `ScopeImpl`, macro files (one line each).
- **Review:** one fresh-context Opus fact-check over every changed comment with probes against 0.0.56, a fix round by the authors, a Sonnet re-review of the fix diff.

## Gates

1. `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test`
2. `scopeJVM/doc` and `scopeJS/doc` without new warnings (3.9.0 and 2.13.18)
3. `python3 -I .claude/scripts/scaladoc-coverage.py scope` and `--drift`: no drift
4. `scripts/comments-only.py` against the base, for the wave files (the tooling files are excluded)
5. `++2.13; check; headerCheckAll; ++3.9; check; headerCheckAll`; `scalafmtSbt` for `build.sbt`

## Out of scope

Library code changes, reference-page rewrites, `docs/dev/` files, other modules. Bugs go to the coordinator as ready-to-paste entries.
