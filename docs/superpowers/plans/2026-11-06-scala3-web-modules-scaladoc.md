# projection, dataMigration, htmx and datastar Scaladoc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run the tasks. Checkbox (`- [ ]`) steps track progress. The process is the `scaladoc-rollout` skill.

**Spec:** `docs/superpowers/specs/2026-11-06-scala3-web-modules-scaladoc-design.md`

## In plain English

**The problem.** Four modules lack comments on 597 of their 987 public members: `projection` (event-sourced read models), `dataMigration` (moving table data between shapes), `htmx` and `datastar` (HTML attributes and headers for two browser libraries). Nobody has checked that the existing comments are true, and the 9 code examples in comments are never compiled.

**The goal.** Every public member has a true, short comment. The types a user meets first have an overview, a compiled example and a link to their reference page. CI compiles every example on Scala 3.9 and 3.10.

**The steps.**
1. **Tooling.** Add the four modules to the existing Scala-3-only checker project and tell it their package names (three of them don't follow their folder). Update the style guide and AGENTS.md.
2. **Projection (strong model).** One agent documents it and checks every existing comment against the code and real probes.
3. **htmx (strong model).** Same for htmx, asserting the exact text it renders.
4. **datastar and dataMigration (strong model).** Same; migration claims are tried against SQLite.
5. **Gaps (cheaper model).** One agent writes the short comments still missing, probing every behavioural claim.
6. **Review.** Fresh agents test every claim against the code and the published 0.0.56 library; the authors fix what they find; a last agent re-checks only the fixes.
7. **Ship.** One pull request. Bugs are not fixed here: each is written up with a reproducer and sent to the docs-backlog coordinator.

## Global Constraints

Full text: `.superpowers/sdd/2026-11-06-scala3-web-modules-scaladoc/global-constraints.md` (skill template with the four modules filled in). The checker is `scaladoc-check-scala3` (3.9.0, 3.10.0); no markers needed. Scaladoc and tooling only; comments-only check against base `07f71de18`. No `docs/dev/` links. Probes run against 0.0.56 (`//> using scala 3.9.0`, `--server=false`), except projection files changed since 0.0.56 (local publish).

## Task 0 (controller): tooling

**Files:** `build.sbt` (`scaladocCheckedScala3Modules`), `project/ScaladocSnippets.scala` (`packageOverrides`), `scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala` (three tests), `docs/dev/scaladoc-style.md` (module list), `AGENTS.md`.

- [ ] Step 1: add `projection.jvm`, `dataMigration.jvm`, `htmx.jvm`, `datastar.jvm`, the overrides and tests.
- [ ] Step 2: check `free -g`, run `scaladoc-check/test` (3.9.0) and `scaladoc-check-scala3/test` (3.9.0; expected to fail only on the 9 existing blocks); check the `zio.blocks.sql` file's imports.
- [ ] Step 3: commit `build: compile projection, dataMigration, htmx and datastar Scaladoc examples`, then `docs: ...` for the style guide and AGENTS.md.

## Task 1 (Opus): projection

**Files:** all 24 main files under `projection/`. **Tier 1:** the nine types in the spec. **Reference pages:** `docs/reference/projection/*.md`. **Probes:** query, index and storage behaviour; files changed since 0.0.56 against a local publish.

- [ ] Step 1: research, then make the 5 existing blocks compile.
- [ ] Step 2: tier 1, then a fact-check table of every existing comment (member, claim, kind, evidence).
- [ ] Step 3: commit per file group, report and issues file.

## Task 2 (Opus): htmx

**Files:** 13 files under `htmx/` and the package object. **Tier 1:** `HxSwap`, `HxTarget`, `HxTrigger`, `HxParams`, `HxSync`, `HtmxHeaders`, `HxLocation`, attribute values. **Reference pages:** `docs/reference/htmx/*.md`. **Known bugs to reflect:** the seven in the spec. Every rendered attribute or header example asserts the full string.

- [ ] Step 1: research; step 2: tier 1 and fact-check table; step 3: commits, report, issues.

## Task 3 (Opus): datastar and dataMigration

**Files:** 25 datastar files (both `SignalVersionSpecific` copies identical) and 8 dataMigration files. **Tier 1:** the datastar and dataMigration types in the spec. **Reference pages:** `docs/reference/datastar/*.md`, `docs/reference/data-migration.md`. **Known bugs to reflect:** datastar 1, data-migration 4. Migration probes use SQLite via JDBC in scala-cli.

- [ ] Step 1: research; step 2: tier 1 and fact-check table; step 3: commits, report, issues.

## Task 4 (Sonnet): tier 2 and 3 gaps

- [ ] Step 1: list every member the coverage script still reports undocumented.
- [ ] Step 2: write comments; a probe per behavioural claim; no absolute words unless verified.
- [ ] Step 3: gates, commit, report; suspected bugs go to `task-4-suspects.md`.

## Task 5: review and ship

- [ ] Fresh Opus fact-check reviews on disjoint module groups, each re-running its share of bug reproducers; fix round (authors resumed); Sonnet scoped re-review.
- [ ] Merge origin/main, re-run both checkers and the gates, push `khajavi scala3-web-modules-scaladoc`, open the PR, watch CI to green. Do not merge.
- [ ] Send the coordinator the bug file (one file, target line per entry), the backlog §13q text and the metric.
