# openapi, codegen, smithy, mux and ringbuffer Scaladoc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run the tasks. Checkbox (`- [ ]`) steps track progress. The process is the `scaladoc-rollout` skill.

**Spec:** `docs/superpowers/specs/2026-11-06-tools-modules-scaladoc-design.md`

## In plain English

**The problem.** Five small tool modules (`openapi` describes web APIs, `codegen` writes Scala source, `smithy` reads and writes Smithy service models, `ringbuffer` holds fast queues for threads, `mux` multiplexes streams over them) have 173 public members without comments, and nobody has checked that the 296 existing comments are true. The queue and mux comments make claims about threads; a wrong claim there can lead a user to share a queue between threads that it does not support. Nine code examples in comments are never compiled.

**The goal.** Every public member has a true, short comment. The main types have a compiled example and a link to the reference page. Where the JVM and Scala.js versions of a queue behave differently, the text says so. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0.

**The steps.**
1. **Tooling.** The helper project `scaladoc-check` pulls code examples out of comments and compiles them. Add the five modules to its list in `build.sbt`, update the style guide's module list and one `AGENTS.md` line.
2. **Queues and mux (Opus).** A strong-model agent writes the comments for `ringbuffer` and `mux`, checks every existing comment against the code, and tests every thread-safety claim with real concurrent runs.
3. **API, codegen and Smithy (Opus, at the same time, other files).** A second agent does the same for `openapi`, `codegen` and `smithy` using scala-cli against the published 0.0.56 library, and makes the nine old examples compile.
4. **Small gaps (Sonnet).** A cheaper agent fills the plain data-class members in `openapi` once the other agent has written the tier-1 types.
5. **Review.** Two fresh agents test every changed claim and every bug reproducer. The authors fix what they find; a last agent re-checks only the fixes.
6. **Ship.** One pull request. Bugs are not fixed here: each is sent to the docs-backlog coordinator with a reproducer. A real security weakness, if found, stays out of every tracked file.

## Global Constraints

Full text: `.superpowers/sdd/2026-11-06-tools-modules-scaladoc/global-constraints.md` (skill template filled for the five modules, plus the concurrency rule from the spec and the no-`docs/dev/`-links rule).

## Task 1 (Opus): tooling, ringbuffer, mux

**Files:** `build.sbt` (`scaladocCheckedModules`: add `openapi.jvm`, `codegen`, `smithy`, `mux.jvm`, `ringbuffer.jvm`), `docs/dev/scaladoc-style.md` (module list only), `ringbuffer/{shared,jvm,js}/src/main/**`, `mux/shared/src/main/{scala,scala-2,scala-3}/**`. `AGENTS.md` is edited by the controller (diff approved by Milad).

**Tier 1:** `SpscRingBuffer`, `MpscRingBuffer`, `SpmcRingBuffer`, `MpmcRingBuffer`; `Mux`, `MuxStream`, `MuxError`.
**Reference pages:** `docs/reference/ringbuffer/` (`https://zioblocks.com/reference/ringbuffer/spsc/` etc.), `docs/reference/mux.mdx`.
**Known issues:** the three mux bugs (two threads on `takeOutbound`/`receive`, half-closed `receive()`, closed stream with queued messages).

- [ ] Step 1: add the five modules to the list; run `show <project>/crossScalaVersions` and the checker; stop and report if a version is missing.
- [ ] Step 2: tier 1 and every gap; one shared text per jvm/js and scala-2/scala-3 pair, stating real differences inline.
- [ ] Step 3: fact-check table for every existing comment, with JVM concurrency probes (producers and consumers, counting lost or duplicated elements, capacity edges, full and empty returns) and a Scala.js run where claims cover it.
- [ ] Step 4: gates for all five modules (Task 2's included), one commit per file group, report.

## Task 2 (Opus, parallel, scala-cli only): openapi, codegen, smithy

**Files:** `openapi/**/src/main/**`, `codegen/src/main/**`, `smithy/src/main/**`. No `build.sbt`, no sbt.

**Tier 1:** see the spec. **Reference pages:** `docs/reference/openapi.md`, `docs/reference/codegen/`, `docs/reference/smithy.md`.
**Known issues:** the 5 openapi, 5 codegen and 2 smithy bugs in the spec.

- [ ] Step 1: make the 9 `{{{` blocks compile in a scala-cli copy of the checker wrapper (or `// no-check: <reason>`).
- [ ] Step 2: tier 1 and the gaps in those files; commit only your own paths.
- [ ] Step 3: fact-check table for every existing comment, with probes against 0.0.56 (Scala version pinned, `--server=false`); bug write-ups in `task-2-issues.md`.
- [ ] Step 4: hand back READY_FOR_GATES with the list of files left for Task 3.

## Task 3 (Sonnet): openapi data-class gaps

- [ ] Step 1: list undocumented members of the openapi files Task 2 names.
- [ ] Step 2: write one-to-three-sentence comments; probe behaviour with scala-cli; no absolute words unless verified; suspects to `task-3-suspects.md`.
- [ ] Step 3: format only own paths, commit, report. No sbt.

## Task 4: review and ship

- [ ] Two fresh Opus fact-check reviews, a fix round (authors resumed, every suggested sentence re-probed), Sonnet scoped re-review.
- [ ] Merge `origin/main` (normal merge), re-run the gates, push to `khajavi tools-modules-scaladoc`, open the PR, watch CI to green. No force-push, no merge.
- [ ] Send the coordinator the bug file, the §13p text and the metric.
