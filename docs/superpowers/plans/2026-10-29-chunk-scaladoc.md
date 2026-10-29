# Chunk Module Scaladoc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run the tasks. Checkbox (`- [ ]`) steps track progress. The process is the `scaladoc-rollout` skill (`~/.claude/skills/scaladoc-rollout/`).

**Spec:** `docs/superpowers/specs/2026-10-29-chunk-scaladoc-design.md`

## In plain English

**The problem.** The `chunk` module (`Chunk`, `NonEmptyChunk`, `ChunkBuilder`, `ChunkMap`) has comments on 74% of its public members. Nobody has checked that those comments are true; most came over from ZIO 2's `Chunk`. None of them shows a code example, and the build doesn't compile examples from `chunk` comments.

**The goal.** Every public member of `chunk` has a comment that is true, short and useful. The four core types each have a code example, and CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0.

**The steps.**
1. **Tooling (small).** The build has a helper project, `scaladoc-check`, that pulls code examples out of comments, wraps each in a small Scala object and compiles it. Today it covers `schema` and `streams`; we add `chunk` with one list entry and one test. The style guide and two `AGENTS.md` lines are updated to match.
2. **Core types and fact-check (Opus).** One agent on the strongest model writes the overview, example and reference-page link for the four core types. It then checks every existing comment in the module against the code and fixes the false ones.
3. **Gaps (Sonnet).** A second agent on the cheaper model writes the 48 small, mechanical comments that are still missing (builder methods, bit operators, small companions). This is an experiment: it tells us whether the cheaper model is good enough for that kind of work in later modules.
4. **Review.** A third agent, with no memory of the first two, tests every claim against the code and the published library, and records which agent wrote each mistake. The authors fix their own mistakes, and a fourth agent re-checks only the fixes.
5. **Ship.** One pull request. Bugs found are not fixed here: each is written up with a reproducer and sent to the `article-coordinator` session.

## Global Constraints

The full text is in `.superpowers/sdd/2026-10-29-chunk-scaladoc/global-constraints.md` (the skill's template with `chunk` filled in). Summary:

- Scaladoc and tooling only. No behaviour change in `chunk` main sources. Bugs go to the coordinator in `task-<n>-issues.md`; `docs/dev/library-issues.md` and `docs/dev/docs-issues.md` are not edited.
- Identical text across `jvm`/`js` copies.
- Every claim matches the code or a test. When the code looks wrong, describe what it really does, say "a known bug", and file it.
- No mention of `docs/dev/` in Scaladoc.
- Examples: `{{{ }}}` blocks compile on 2.13.18, 3.9.0 and 3.10.0 after an implicit `import zio.blocks.chunk._`. Braces, not indentation. Sample data is real Scala books (Zionomicon: John A. De Goes, Adam Fraser and Milad Khajavi). Result comments come from a real run. No `}}}` inside a code line. `$name` is expanded by Scaladoc.
- Let scalafmt wrap (`docstrings.wrapMaxColumn = 80`); format with `++3.9.0; fmtDirty` and `++2.13.18; fmtDirty`.
- Every sbt run uses the AGENTS.md template and names the Scala version. Only one sbt-running agent at a time. Shut sbt down at the end of a task.
- Gates: see the spec.
- Conventional Commits (`build: ...`, `docs(chunk): ...`). One commit per file group and per group of review fixes. Only the controller pushes, and only to `khajavi`.

## Task 1 (Opus): tooling, tier 1, fact-check of existing comments

**Files:** `build.sbt` (`scaladocCheckedModules`), `scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala`, `docs/dev/scaladoc-style.md`, `AGENTS.md` (lines 113 and 178 only), all six `chunk` main sources.

**Tier 1 and key methods** (the ones `docs/reference/chunk.md` shows): `Chunk` (overview; `apply`, `fromArray`, `++`, `map`, `materialize`), `NonEmptyChunk` (overview; `apply`, `fromChunk`, `head`, `reduce`, `reduceMapLeft`, `toChunk`), `ChunkBuilder` (overview; `make`, `+=`/`addOne`, `result`), `ChunkMap` (overview; `apply`, `fromChunk`, `get`, `indexed`, `keyAtIndex`). Also the four companions `object Chunk`, `object ChunkBuilder`, `object NonEmptyChunk`, `object ChunkMap`.
**Reference page:** `docs/reference/chunk.md` (`https://zioblocks.com/reference/chunk/`; anchors such as `#nonemptychunk` and `#chunkmap`).
**Known issues:** none.

- [ ] Step 1: add `chunk.jvm` to `scaladocCheckedModules`, add an extractor test for a `chunk` path, update the style guide and the two `AGENTS.md` lines; run the checker on all three versions; commit (`build: compile chunk Scaladoc examples`).
- [ ] Step 2: research the source, tests and reference page; write tier 1.
- [ ] Step 3: fact-check every existing comment in the six files, fix the false ones, and count them (with the kind of error) for the pilot metric.
- [ ] Step 4: gates, commits per file group, report.

## Task 2 (Sonnet): tier-2 and tier-3 gaps

**Files:** `chunk/shared/src/main/scala/zio/blocks/chunk/Chunk.scala` only, after Task 1 is committed.

**Items (48):** `addOne`, `clear` and `result` on each primitive `ChunkBuilder` (`Boolean`, `Byte`, `Char`, `Double`, `Float`, `Int`, `Long`, `Short`: 24); `ChunkBuilder.from`; `IsText`, `IsText#convert`, `object IsText` and its `byteIsText`, `charIsText`, `strIsText`; the `BitOps` members (`zero`, `one`, `reverse`, `<<`, `>>`, `|`, `&`, `^`, `invert`, `classTag`; tier 3); `object BitChunk`, `Endianness`, `object Endianness`, `BigEndian`, `LittleEndian`; `object ChunkIterator`; `NonEmptyChunk#reverse`.

- [ ] Step 1: read each member's implementation and tests; write the comments (tier 2: one to three sentences; tier 3: one line).
- [ ] Step 2: gates, commit, report.

## Task 3: review and fix rounds

- [ ] Save `git diff 9d8bb9d70..HEAD`; dispatch the Opus fact-check reviewer (fresh context). Findings are tagged T1 or T2 by the commit that wrote the line.
- [ ] Rule on the findings in `progress.md`. Resume each implementer with its own findings. A Sonnet re-reviewer checks each fix round until APPROVE.

## Task 4: ship and close

- [ ] Push to `khajavi`, open the PR `docs(chunk): Scaladoc for Chunk and ChunkMap, compiled examples` with a plain-English body (problem, fix, verification, coverage before and after, review counts).
- [ ] Send bug entries, the backlog §13 text and the pilot metric (time, rounds, findings by implementer, false existing comments, whether Sonnet was good enough) to `article-coordinator`.
- [ ] Watch CI until every check passes. The user merges.
