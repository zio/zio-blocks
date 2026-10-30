# Scope Module Scaladoc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run the tasks. Checkbox (`- [ ]`) steps track progress. The process is the `scaladoc-rollout` skill.

**Spec:** `docs/superpowers/specs/2026-10-30-scope-scaladoc-design.md`

## In plain English

**The problem.** The `scope` module (`Scope`, `Resource`, `Wire` and friends) has comments on 56% of its public members. Nobody has checked that they are true. It has 23 code examples in comments that the build never compiles, and some of them use names that don't exist. Twelve comments differ between the Scala 2 and Scala 3 copies of the same member.

**The goal.** Every public member has a true, short comment. The seven core types each have a compiled example and a link to their reference page. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0.

**The steps.**
1. **Tooling.** The build has a helper project, `scaladoc-check`, that pulls code examples out of comments and compiles them. We add `scope` to its list, add one test, and update the style guide and two `AGENTS.md` lines.
2. **Core types and fact-check (Opus).** A strong-model agent makes the 23 examples compile, writes the overview, example and link for the seven core types, and checks every existing comment against the code.
3. **Gaps (Sonnet).** A cheaper-model agent writes the small mechanical comments still missing (the `Unscoped` instances, error-message helpers, macro one-liners).
4. **Review.** A fresh agent tests every changed claim against the code and the published 0.0.56 library. The authors fix what it finds; a last agent re-checks only the fixes.
5. **Ship.** One pull request. Bugs are not fixed here: each is written up with a reproducer and sent to the `article-coordinator` session.

## Global Constraints

Full text: `.superpowers/sdd/2026-10-30-scope-scaladoc/global-constraints.md` (skill template with `scope` filled in, plus the module and sample-data rules from the spec).

## Task 1 (Opus): tooling, seed, tier 1, fact-check

**Files:** `build.sbt` (`scaladocCheckedModules`), `scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala`, `docs/dev/scaladoc-style.md` (module list only), `AGENTS.md` (lines 113 and 178 only), and all of `scope/`.

**Tier 1 and key methods:** `Scope` (`scoped`, `allocate`, `$`, `defer`, `lower`, `leak`, `Scope.global`), `Resource` (`apply`, `fromAutoCloseable`, `acquireRelease`, `from[T]`, `shared`, `map`/`flatMap`/`zip`), `Wire` (`apply`, `shared`, `make`, `toResource`, `fromFunction`), `Finalizer` (`defer`), `Unscoped` (the typeclass and derivation), `Finalization`, `DeferHandle`, and their companions.
**Reference pages:** `docs/reference/resource-management/{scope,resource,wire,finalizer,finalization,defer-handle,unscoped}.md` (`https://zioblocks.com/reference/resource-management/<page>/`).
**Known issues:** the ten `scope:` entries in `library-issues.md` (lines 261-562) and the telemetry/scope import entry (9695).

- [ ] Step 1: merge `origin/main`, add `scope.jvm`, add the extractor test, run the checker and fix the 23 blocks.
- [ ] Step 2: unify the 12 drifting members (one text, differences stated).
- [ ] Step 3: tier 1 Scaladoc; fact-check table for all existing comments.
- [ ] Step 4: gates, one commit per file group, report.

## Task 2 (Sonnet): tier 2 and 3 gaps

**Files:** `Unscoped` instances (scala-2 and scala-3), `UnscopedVersionSpecific`, `InStack*`, `PlatformScope` (jvm, js), `internal/*` (`ErrorMessages`, `Finalizers`, `ScopeImpl*`, `MacroCore`, `WireCodeGen`), macro files (`ScopeMacros`, `ResourceMacros`, `UseMacros`, `LeakMacros`): one line each.

- [ ] Step 1: list every member the coverage script reports undocumented.
- [ ] Step 2: write comments; probe anything behavioural; no absolute words unless verified.
- [ ] Step 3: gates, commit, report.

## Task 3: review and ship

- [ ] Opus fact-check review (fresh context), fix round (authors resumed), Sonnet scoped re-review.
- [ ] Push `khajavi scaladoc-scope`, open PR, watch CI to green.
- [ ] Report coverage before/after and the §13b table to the coordinator.
