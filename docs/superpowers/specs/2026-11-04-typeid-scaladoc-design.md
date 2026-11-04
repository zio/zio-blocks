# TypeId module Scaladoc design

Follows `docs/superpowers/specs/2026-10-30-config-scaladoc-design.md` and the `scaladoc-rollout` process.

## Problem

The `typeid` module has comments on 162 of 390 public API members (41%). The `TypeId` pair (`scala-2/` and `scala-3/` copies) is the biggest gap: 127 members, 17 documented. Nobody has checked that the existing comments are true; the `chunk`, `scope` and `config` rollouts found that 19%, 31% and 36% of inherited comments were wrong or imprecise. Three `{{{` blocks (`Owner.scala` x2, `TermPath.scala` x1) are never compiled. Thirteen members have different text in their `scala-2/` and `scala-3/` copies (`trait TypeId`, `isApplied`, `isEither`, `isEquivalentTo`, `isOption`, `isProduct`, `isSubtypeOf`, `isSum`, `isSupertypeOf`, `isTuple`, `unapplied`, and `TypeIdMacros.derived` / `derivedImpl`). Some of that drift reflects real differences: on Scala 3, `TypeId[A <: AnyKind]` accepts higher-kinded types, and `isSubtypeOf` also handles union and intersection aliases and enum cases.

## Goal

Every public member of `typeid` has a true, short, useful comment. The tier-1 types have an overview, a compiled example and a reference-page link. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0. `--drift` is empty.

## Decisions (Milad, 2026-11-04)

- **One PR** (tooling, existing blocks made to compile, Scaladoc).
- **Tier 1:** `TypeId` (both copies, trait and companion), `TypeRepr`, `TypeDefKind`, `Owner`, `IsNominalType`, `IsNominalIntersection`, each with key methods and companion.
- **Scala 2/3 differences:** identical text in both copies; each real difference is named in that text ("On Scala 3, ..."), and verified by a probe on both versions.
- **AGENTS.md:** add `typeid` to the `scaladoc-check` bullet, and a downstream line `typeid*` → `scaladoc-check`, `maybe*`, `context*`, `schema*`, `docs` (diff approved).

## Layout

`typeid/shared/src/main/scala/` (15 files), `scala-2/` and `scala-3/` (`TypeId`, `TypeIdMacros`, `TypeIdPlatformSpecific`, `IsNominalTypeVersionSpecific`, `IsNominalIntersectionVersionSpecific`; Scala 3 also `TypeIdLowPriority`), `jvm/` and `js/` (`TypeIdPlatformMethods`). Package `zio.blocks.typeid`, so the wrapper import needs no override. The module is on `/api`. Reference page: `docs/reference/typeid.md` (`https://zioblocks.com/reference/typeid/`).

`main` differs from `v0.0.56` only in `IsNominal*VersionSpecific` (#1826 fixed the "accepts an abstract type parameter" bug). Claims about `IsNominal*` are probed against a local publish of `main`; everything else against 0.0.56.

## Tasks

- **Task 1 (Opus):** add `typeid.jvm` to `scaladocCheckedModules`; extractor test; style-guide module list. Make the 3 blocks compile. Unify the 13 drifting members. Tier 1. Fact-check every existing comment (table: member, claim, kind, evidence). Bug write-ups.
- **Task 2 (Sonnet), after Task 1:** tier 2 and 3 gaps from the coverage report (macros are tier 3).
- **Review:** fresh-context Opus fact-check over every changed comment (probes on 2.13.18 and 3.9.0; Scala.js where `TypeIdPlatformMethods` differs), a fix round, a Sonnet re-review of the fix diff.

## Gates

1. `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test`
2. `typeidJVM/doc` and `typeidJS/doc` on 3.9.0 and 2.13.18: no new warnings
3. `python3 -I .claude/scripts/scaladoc-coverage.py typeid` (390/390) and `--drift` (empty)
4. `comments-only.py` against the base, excluding tooling files
5. `++2.13.18; check; headerCheckAll; ++3.9.0; check; headerCheckAll`; `scalafmtSbt` for `build.sbt`

## Out of scope

Library code changes, reference-page rewrites (errors go to `docs-issues.md` entries via the coordinator), `docs/dev/` files, other modules.
