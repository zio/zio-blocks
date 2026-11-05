# Async Scaladoc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run the tasks. Checkbox (`- [ ]`) steps track progress. The process is the `scaladoc-rollout` skill.

**Spec:** `docs/superpowers/specs/2026-11-05-async-scaladoc-design.md`

## In plain English

**The problem.** The `async` module provides `Async[A]`, a value that is either ready now or will be ready later, plus tools to combine such values, cancel them, and convert them to and from `Future`, `CompletableFuture` and JavaScript promises. 72% of its public members have comments, but nobody has checked that those comments are true, and many of them describe tricky behaviour: what happens on cancel, which thread runs a callback, how the JVM and the browser differ. Earlier modules had about one wrong comment in four or five. Three code examples in comments are never compiled, and seven members say different things in their Scala 2 and Scala 3 (or JVM and JS) copies.

**The goal.** Every public member has a true, short comment, identical in all its copies. Where Scala versions or platforms really behave differently, the shared text says so. The main types have a compiled example and a link to the reference page. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0.

**The steps.**
1. **Tooling.** The helper project `scaladoc-check` pulls code examples out of comments and compiles them. Add `async` to its list, add one test, and update the style guide and two `AGENTS.md` lines (Milad approves the AGENTS.md diff first).
2. **Shared files (Opus, Task 1a).** A strong-model agent makes the 3 examples compile and writes the overview, example and link for the main types. It also checks every existing comment in the shared files against the code, and makes the Scala 2 and Scala 3 texts identical.
3. **Platform files and internals (Opus, Task 1b, at the same time).** A second strong-model agent does the same for the JVM and JS files, the macros and the internals. It probes Scala.js claims on Scala.js. It does not run sbt: one sbt at a time on this machine.
4. **Gaps (Sonnet, Task 2).** A cheaper-model agent writes the small comments still missing in the shared files, after step 2.
5. **Review.** Two fresh agents test every changed claim against the code, each on half of the files. The authors fix what they find; a last agent re-checks only the fixes.
6. **Ship.** One pull request. Bugs are not fixed here: each is written up with a reproducer and sent to the docs-backlog coordinator.

## Global Constraints

Full text: `.superpowers/sdd/2026-11-05-async-scaladoc/global-constraints.md` (skill template with `async` filled in, plus the async-specific rules).

## Task 1a (Opus): tooling, shared files, tier 1, fact-check

**Files:** `build.sbt` (`scaladocCheckedModules`), `scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala`, `docs/dev/scaladoc-style.md` (module list only), and in `async/shared/src/main/`: `scala/zio/blocks/async/*.scala` (not `internal/`), `scala-2/.../AsyncCompanionVersionSpecific.scala`, `scala-2/.../AsyncSyntaxVersionSpecific.scala`, `scala-3/.../AsyncCompanionVersionSpecific.scala`, `scala-3/.../AsyncSyntaxVersionSpecific.scala`. The controller edits `AGENTS.md` (after Milad approves the diff).

**Tier 1 and key methods:** `Async` (type alias in `package.scala`; companion: `succeed`, `fail`, `attempt`, `promise`, `async`, `start`, `never`, `collectAll`, `selector`; syntax: `map`, `flatMap`, `catchAll`, `await`, `block`, `zip`/`zipWith`, `ensuring`, `orElse`, `either`), `Async.Running` (`cancel`, `poll`), `Completer` (`succeed`, `fail`, `peek`), `Cancelable`, `Failure`, `Pollable` (`poll`, `cancel`), `AsyncSelector` (`select`, `replace`, `shutdown`).
**Reference page:** `docs/reference/async.md`.
**Known issues** (coordinator copy `/home/milad/sources/scala/zio-blocks-worktrees/tutorials-and-guides-lists/docs/dev/library-issues.md`): 12384 (two consumers can hang), 13013 (cancel never reaches `fromFuture`/`promise`/...), 13470 (Scala 3 `(c: Completer[A]) => ...` never completes), 15045 (2.13 `promise` needs `implicit c =>`).

- [ ] Step 1: add `async.jvm`, the extractor test, run the checker, fix the 3 blocks.
- [ ] Step 2: unify `attempt`, `flatten`, `orElse`, `unit` (probe each per-version claim on 2.13.18 and 3.9.0).
- [ ] Step 3: tier 1 Scaladoc; fact-check table for every existing comment in the files.
- [ ] Step 4: gates, one commit per file group, report.

## Task 1b (Opus): platform files, internals, `AsyncInterop`

**Files:** everything under `async/jvm/src/main/` and `async/js/src/main/`, `async/shared/src/main/scala/zio/blocks/async/internal/`, `async/shared/src/main/scala-2/zio/blocks/async/internal/AsyncMacros.scala`, `async/shared/src/main/scala-3-dca/`, `async/shared/src/main/scala-3-dca-direct/`.

**Tier 1:** `AsyncInterop` (jvm: `toFuture`, `toCompletableFuture`, `fromFuture`, `fromCompletionStage`; js: `toFuture`, `toJsPromise`, `fromFuture`, `fromJsPromise`), with identical text where the API is shared.
**Known issues:** 13013, 12384, 7201 (Scala.js `ReadableStream` rejection, for any claim about JS promise rejection).

- [ ] Step 1: unify `toFuture` (jvm/js, scala-2/scala-3) and `fromFuture` (jvm/js); probe the JVM and Scala.js behaviour each text states.
- [ ] Step 2: tier 1 for `AsyncInterop`; tier 2/3 for the rest, including the gaps (`AsyncInteropOps`, `Parker`, `AsyncCpsMonad`).
- [ ] Step 3: fact-check table for every existing comment in the files. No sbt: scala-cli probes against 0.0.56 only.
- [ ] Step 4: commit per file group, report. Task 1a or the controller runs the sbt gates over these files.

## Task 2 (Sonnet): gaps in the shared files

- [ ] Step 1: list every member the coverage script still reports undocumented in Task 1a's files (after 1a commits).
- [ ] Step 2: write tier-2/3 comments; probe anything behavioural; no absolute words unless verified.
- [ ] Step 3: gates, commit, report.

## Task 3: review and ship

- [ ] Two Opus fact-check reviews (fresh context, A: 1a+2 files, B: 1b files); fix round (authors resumed); Sonnet scoped re-review.
- [ ] Merge `origin/main`, re-run gates, push `khajavi async-scaladoc`, open the PR, watch CI to green.
- [ ] Report to the coordinator: PR number, coverage before and after, review counts, CI counts, §13j text and metric.
