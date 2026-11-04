# TypeId Scaladoc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run the tasks. Checkbox (`- [ ]`) steps track progress. The process is the `scaladoc-rollout` skill.

**Spec:** `docs/superpowers/specs/2026-11-04-typeid-scaladoc-design.md`

## In plain English

**The problem.** The `typeid` module describes Scala types as values (a `TypeId` holds a type's name, owner, type parameters and parents, and can answer "is this a subtype of that?"). Only 41% of its public members have comments, nobody has checked that those comments are true, and the Scala 2 and Scala 3 copies of the main type say different things. Three code examples in comments are never compiled by the build.

**The goal.** Every public member has a true, short comment, identical in the Scala 2 and Scala 3 copies. Where the two Scala versions really behave differently, the shared text says so. The main types have a compiled example and a link to the reference page. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0.

**The steps.**
1. **Tooling.** The helper project `scaladoc-check` pulls code examples out of comments and compiles them. Add `typeid` to its list, add one test, update the style guide and two `AGENTS.md` lines.
2. **Core types and fact-check (Opus).** A strong-model agent makes the 3 examples compile, makes the Scala 2 and Scala 3 texts identical, writes an overview, example and link for the main types, and checks every existing comment against the code.
3. **Gaps (Sonnet).** A cheaper-model agent writes the small comments still missing, including one-liners for the macro internals.
4. **Review.** A fresh agent tests every changed claim against the code, on Scala 2.13 and Scala 3, against the published 0.0.56 library (and a local build of `main` for `IsNominalType` / `IsNominalIntersection`). The authors fix what it finds; a last agent re-checks only the fixes.
5. **Ship.** One pull request. Bugs are not fixed here: each is written up with a reproducer and sent to the docs-backlog coordinator.

## Global Constraints

Full text: `.superpowers/sdd/2026-11-04-typeid-scaladoc/global-constraints.md` (skill template with `typeid` filled in, plus the typeid-specific rules).

## Task 1 (Opus): tooling, seed, drift, tier 1, fact-check

**Files:** `build.sbt` (`scaladocCheckedModules`), `scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala`, `docs/dev/scaladoc-style.md` (module list only), and all of `typeid/`. `AGENTS.md` is edited by the controller (diff approved).

**Tier 1 and key methods:** `TypeId` (`of`, `derived`, `nominal`, `alias`, `opaque`, `applied`, `name`, `fullName`, `owner`, `typeParams`, `typeArgs`, `defKind`, `isSubtypeOf`, `normalize`, `erased`, `clazz`, extractors), `TypeRepr`, `TypeDefKind`, `Owner`, `IsNominalType`, `IsNominalIntersection`.
**Reference page:** `docs/reference/typeid.md` (`https://zioblocks.com/reference/typeid/`, anchors allowed).
**Known issues:** the `typeid` entries in `library-issues.md` (full coordinator copy: `/home/milad/sources/scala/zio-blocks-worktrees/tutorials-and-guides-lists/docs/dev/library-issues.md`), in particular the resolved `IsNominalType` entry and its two remaining Scala 2/3 differences, and the named-tuple note (a named tuple is an applied `NamedTuple`, not `TypeRepr.Tuple`).

- [ ] Step 1: add `typeid.jvm`, the extractor test, run the checker, fix the 3 blocks.
- [ ] Step 2: unify the 13 drifting members (state per-version behaviour, probes on 2.13.18 and 3.9.0).
- [ ] Step 3: tier 1 Scaladoc; fact-check table for all existing comments.
- [ ] Step 4: gates, one commit per file group, report.

## Task 2 (Sonnet): tier 2 and 3 gaps

- [ ] Step 1: list every member the coverage script reports undocumented (after Task 1).
- [ ] Step 2: write comments; probe anything behavioural; no absolute words unless verified.
- [ ] Step 3: gates, commit, report.

## Task 3: review and ship

- [ ] Opus fact-check review (fresh context), fix round (authors resumed), Sonnet scoped re-review.
- [ ] Push `khajavi scaladoc-typeid`, open PR, watch CI to green.
- [ ] Report coverage before/after and the §13d text and metric to the coordinator.
