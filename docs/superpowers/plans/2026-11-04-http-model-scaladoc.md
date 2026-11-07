# http-model Scaladoc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run the tasks. Checkbox (`- [ ]`) steps track progress. The process is the `scaladoc-rollout` skill.

**Spec:** `docs/superpowers/specs/2026-11-04-http-model-scaladoc-design.md`

## In plain English

**The problem.** The `http-model` module holds plain data types for HTTP: requests, responses, URLs, headers, cookies, status codes. Only 5% of its public members have a comment, nobody has checked that those comments are true, and it has no code examples. The build has a helper, `scaladoc-check`, that compiles the code examples written inside comments, but it assumes every module's package is `zio.blocks.<folder>`, and this module's package is `zio.http`.

**The goal.** Every public member has a true, short comment. The 12 main types (`Request`, `Response`, `Body`, `URL`, `Path`, `QueryParams`, `Headers`, `Header`, `Method`, `Status`, `Cookie`, `ServerSentEvent`) also get an overview, a compiled example and a link to their reference page. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0.

**The steps.**
1. **Tooling.** Teach `scaladoc-check` that the `http-model` folder holds package `zio.http`, with a small exception table and a test. Add the module to the checker's list, and update the style guide and two `AGENTS.md` lines.
2. **Main types and fact-check (Opus).** A strong-model agent writes the 12 main types in full and checks every existing comment against the code, running small programs against the published library to confirm behaviour.
3. **The rest (two Sonnet agents in parallel).** One writes the ~75 typed headers in `Header.scala`; the other writes the small helper types (`Scheme`, `Version`, `Form`, `Charset`, ...).
4. **Review.** Two fresh agents each check half of the changed comments against the code, with real inputs. The authors fix what they find; a last agent re-checks only the fixes.
5. **Ship.** One pull request. Bugs are not fixed here: each is written up with a reproducer and sent to the docs-backlog coordinator.

## Global Constraints

Full text: `.superpowers/sdd/2026-11-04-http-model-scaladoc/global-constraints.md` (skill template with `http-model` filled in, plus http-model rules).

## Task 1 (controller): tooling

**Files:** `project/ScaladocSnippets.scala`, `scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala`, `build.sbt` (`scaladocCheckedModules` and its comment), `docs/dev/scaladoc-style.md` (module list), `AGENTS.md` (approved diff).

- [ ] Step 1: add `packageOverrides`; `moduleImport` and `objectName` use it; test for an `http-model` path and an unchanged module.
- [ ] Step 2: add `` `http-model`.jvm `` to `scaladocCheckedModules`; update the comment.
- [ ] Step 3: style guide and AGENTS.md.
- [ ] Step 4: `scaladoc-check/test` on 3.9.0, 3.10.0, 2.13.18; `scalafmtSbt`; commit.

## Task 2 (Opus): tier 1, their files, fact-check

**Files:** `Request`, `Response`, `Body`, `URL`, `Path`, `QueryParams`, `Method`, `scala-2/` and `scala-3/` `Status`, `Headers`, `Cookie`, `ServerSentEvent`, and in `Header.scala` only `trait Header`, `object Header`'s `Codec`, `Typed`, `Custom`, `Cookie`, `SetCookie`.

**Reference pages:** `docs/reference/http-model/model.md`, `headers.md`, `server-sent-event.md`.
**Known issues:** the `http-model` entries in the coordinator copy of `library-issues.md` (`/home/milad/sources/scala/zio-blocks-worktrees/tutorials-and-guides-lists/docs/dev/library-issues.md`).

- [ ] Step 1: research each file, its tests and the reference pages.
- [ ] Step 2: tier 1 Scaladoc with compiled examples; tier 2 for every other member in these files; identical text in the `Status` copies.
- [ ] Step 3: fact-check table for every existing comment in the module (all 57), with probes.
- [ ] Step 4: gates, one commit per file group, report and issues file.

## Task 3a (Sonnet): typed headers

**File:** `Header.scala` below the parts Task 2 owns. Reflect the `WWW-Authenticate` quoting and `Authorization.Basic` `toString` entries.

- [ ] Step 1: list undocumented members with the coverage script.
- [ ] Step 2: comments; probe each `parse` / `render` claim; no unverified absolute words.
- [ ] Step 3: format with `scala-cli fmt`, commit per quarter of the file, report. No sbt (Task 3b runs in parallel).

## Task 3b (Sonnet): helper types

**Files:** `Scheme`, `Version`, `Form`, `FormField`, `ContentType`, `Charset`, jvm/js `CharsetPlatformSpecific`, `Boundary`, `PercentEncoder`.

- [ ] Steps as 3a.

## Task 4: review and ship

- [ ] Controller runs all gates once after 3a and 3b.
- [ ] Two Opus fact-check reviews (fresh context, disjoint halves), fix round (authors resumed), Sonnet scoped re-review.
- [ ] Merge `origin/main`, push `khajavi http-model-scaladoc`, open PR, watch CI to green.
- [ ] Report coverage before/after, bugs, the §13e text and metric to the coordinator.
