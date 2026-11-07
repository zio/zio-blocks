# Streams Module Scaladoc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run the waves. Checkbox (`- [ ]`) steps track progress. The process is the `scaladoc-rollout` skill (`~/.claude/skills/scaladoc-rollout/`).

**Spec:** `docs/superpowers/specs/2026-10-26-streams-scaladoc-design.md`

## In plain English

**The problem.** The `streams` module has comments on 64% of its public members, but nobody has checked that the comments are true, and the code examples inside them are never compiled. A wrong comment is worse than none: it teaches a false rule. The 361 existing comments are therefore as much a risk as the 200 missing ones.

**The goal.** Every public member of `streams` has a comment that is true, short and useful. Every code example in those comments compiles in CI on Scala 2.13.18, 3.9.0 and 3.10.0. The comments about the end-of-stream marker follow the performance policy in `AGENTS.md`.

**The steps.**
1. **PR 0 (tooling, no doc content).** The build already has a helper project that compiles the code examples of `schema` comments. We turn it into one general project, `scaladoc-check`, that serves every module, then add `streams` to it. "Compile an example" means: pull it out of the comment, wrap it in a small Scala object, and let the compiler check it. We also make the one existing `streams` example compile, update the style guide and `AGENTS.md`, and add a progress table.
2. **Wave 1: `Stream`, `Sink`, `Pipeline`.** The three main types. Each gets an overview, a compiled example and a link to its reference page, and every method is fact-checked.
3. **Wave 2: `io` (`Reader`, `Writer`).** The low-level pull and push interfaces.
4. **Wave 3: platform files and internals.** `JvmType`, `Platform`, the NIO readers and sinks, the queues, the Scala.js readers, and one-line comments for `streams.internal`. The jvm/js text mismatch in `PlatformSpecific` is fixed here.
5. **Each wave has the same loop.** One agent writes the comments and runs the checks. A second agent, with no memory of the first, tests every claim against the code. Mistakes are fixed, a third agent re-checks only the fixes, and then the pull request is opened. You approve and merge every pull request. Real bugs found along the way are not fixed here; each is written up with a reproducer and sent to the `article-coordinator` session.
6. **Close-out.** List what is still undocumented, sort real gaps from false alarms, document the real gaps, fix tooling problems the waves found, and mark the progress table done.

## Global Constraints

The full text is in `.superpowers/sdd/2026-10-26-streams-scaladoc/global-constraints.md` (copy of `references/global-constraints.md` with this module filled in). Summary:

- Scaladoc and tooling only. No behaviour change in `streams` main sources. Bugs go to the coordinator in `task-<n>-issues.md`; `docs/dev/issues.md` is not edited.
- **Sentinel performance policy (AGENTS.md).** Docs about the end-of-stream marker (`Long.MaxValue`, `Double.MaxValue`, ...) describe the accepted edge case and the approved remedies. They never suggest per-element checks, flag reads, boxing or allocation, and the change touches no hot loop. A reviewer checks this wording on every wave.
- Identical text across `scala-2`/`scala-3` and `jvm`/`js` copies.
- Every claim matches the code or a test. When the code looks wrong, describe what it really does, say "a known bug", and file it.
- No mention of `docs/dev/` in Scaladoc.
- Examples: `{{{ }}}` blocks compile on 2.13.18, 3.9.0 and 3.10.0 after an implicit `import zio.blocks.streams._`. Braces, not indentation. Sample data is real Scala books (Zionomicon: John A. De Goes, Adam Fraser and Milad Khajavi). Result comments come from a real run. No `}}}` inside a code line. `$name` is expanded by Scaladoc, so take care in interpolations.
- Let scalafmt wrap (`docstrings.wrapMaxColumn = 80`). Format with `++3.9.0; fmtDirty` and `++2.13.18; fmtDirty` (3.9.0 skips `scala-2/`).
- Every sbt run uses the AGENTS.md template and names the Scala version. Shut sbt down at the end of a task.
- Gates for every wave:
  1. `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test`
  2. `++3.9.0; streamsJVM/doc; ++2.13.18; streamsJVM/doc`: no new warnings.
  3. `python3 -I .claude/scripts/scaladoc-coverage.py streams --drift`: no drift in the wave's files.
  4. Comments-only check against `origin/main`.
  5. `++2.13.18; check; headerCheckAll; ++3.9.0; check; headerCheckAll`.
  6. `++3.9.0; streamsJVM/testOnly zio.blocks.streams.SpecializationStaticCompletenessSpec`, and the same on `++2.13.18` and `++3.10.0`. This spec scans the raw source lines of `streams/*/src/main`, comments included, and checks them against `streams/jvm/src/test/resources/specialization-conformance-inventory.txt`. A doc line that matches its patterns (`: Reader[` or `: Reader.`, `def ...reader...`, `.readInt(` and the other `read<Lane>` calls, `new SyncReader[`, a `specialization-id:` marker) adds or removes an inventory entry; `new Reader[...]` in an example even throws. Prefer examples that avoid those spellings. When a doc line unavoidably changes the inventory, update the resource by hand to match (remove the stale ID or add the new one) and say so in the report.
- Conventional Commits (`docs(streams): ...`, `build: ...`). One commit per file group and per group of review fixes. Push only to the `khajavi` remote, and only the controller pushes.

## Review Focus (PR 0)

1. **Module imports.** A `streams` snippet gets the `streams` import and a `schema` snippet still gets the `schema` import. Pinned by extractor tests for both modules.
2. **Name collisions.** Two modules with a same-named file must not generate the same wrapper object. Pinned by a test.
3. **Stale wrappers.** A moved or removed snippet leaves no stale generated file.
4. **Scala 3 syntax in shared files, unclosed blocks, code lines that look like comment margin.** Same tests as for `schema`.
5. **Scala versions.** `show scaladoc-check/crossScalaVersions` equals the versions both modules build on.
6. **Nothing else renamed by accident.** Every reference to `schema-scaladoc-check` (`build.sbt`, `AGENTS.md`, the style guide, `project/*.scala` comments, tests) is updated; the two historical `schema` documents keep their text.

## File Structure (PR 0)

| File | Change |
|---|---|
| `project/ScaladocSnippets.scala` | `render` and the object naming take the module's package and import from the source path. |
| `schema-scaladoc-check/` | Renamed to `scaladoc-check/`; its spec gains tests for `streams` and for the collision case. |
| `build.sbt` | `scaladoc-check` project depending on `schema.jvm` and `streams.jvm` through one module list; root aggregate; CI test commands. |
| `streams/shared/.../io/Reader.scala` | The one existing `{{{` block is made to compile. |
| `docs/dev/scaladoc-style.md` | Module-neutral intro, tier-1 list per module, new checker command. |
| `AGENTS.md` | New checker command in the Scaladoc "Always" bullet and in the `streams` downstream list. |
| `docs/dev/backlog.md` | New section "Scaladoc: streams module" with one row per wave. |

---

## PR 0: tooling

Worktree `/home/milad/sources/scala/zio-blocks-worktrees/streams-scaladocs`, branch `streams-scaladocs`.

### Task 1: Ledger and baseline

- [ ] Create `.superpowers/sdd/2026-10-26-streams-scaladoc/` (git-ignored) with `global-constraints.md`, `progress.md` (decisions D1 to D6 from the spec, then rulings numbered R1, R2, ...), and `coverage-baseline.txt` (`python3 -I .claude/scripts/scaladoc-coverage.py streams --drift`).
- [ ] Confirm versions: `.github/workflows/ci.yml` (2.13.x, 3.9.x, 3.10.x), `show schemaJVM/crossScalaVersions`, `show streamsJVM/crossScalaVersions`. Record them in `progress.md`.

### Task 2: Generalize the extractor

- [ ] Change `project/ScaladocSnippets.scala` so `render` takes the module's import and package, and `objectName` names a wrapper `Snippet_<module>_<path>_<line>`. `generate` receives each source's module with it. Keep the margin, marker (`// scala-2-only`, `// scala-3-only`, `// no-check: <reason>`), unclosed-block and `}}}` rules unchanged.
- [ ] Move the extractor spec to `scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala`. Update the existing assertions and add tests: a `streams` file renders `import zio.blocks.streams._`; a `schema` file still renders `import zio.blocks.schema._`; two modules with a same-named file produce different object names.

### Task 3: One `scaladoc-check` project

- [ ] `git mv schema-scaladoc-check scaladoc-check`. In `build.sbt`, rename the project to `scaladoc-check`, set `stdSettings("zio-blocks-scaladoc-check")`, and drive it from one list `Seq("schema" -> schema.jvm, "streams" -> streams.jvm)` for `dependsOn`, for the source generator (each module's `Compile / unmanagedSources`) and for the fatal-flag lookup. Keep the `-experimental` handling and the generated-directory wipe.
- [ ] Replace `schema-scaladoc-check/test` with `scaladoc-check/test` in `testJVMScala2Command`, `testJVMScala3Command` and the root aggregate.
- [ ] Update every other reference found by `grep -rn "schema-scaladoc-check" --exclude-dir=.git --exclude-dir=target .` except the two historical `schema` documents and `docs/dev/issues.md`, which keep their text. Ask the coordinator about `issues.md` mentions.

### Task 4: Seed and gates

- [ ] Run the checker on all three versions and make the one `Reader` snippet compile (fix compilation only, keep its sample domain). Mark it `// no-check: <reason>` only if the code cannot compile on its own, and say so in the report.
- [ ] Run the extractor tests, the coverage script tests (`python3 -I .claude/scripts/test_scaladoc_coverage.py`), `fmtDirty` (3.9.0 and 2.13.18), `scalafmtSbt`, and `check; headerCheckAll` on 2.13.18 and 3.9.0. Check `schema` still compiles its snippets as before.

### Task 5: Docs and PR 0

- [ ] Style guide: add the `streams` tier-1 list (`Stream`, `Sink`, `Pipeline`, `Reader`, `Writer`) and the new command; keep `schema`'s list.
- [ ] `AGENTS.md`: replace the checker command in the "Always" Scaladoc bullet, and add `scaladoc-check` to the downstream list of `streams`.
- [ ] `docs/dev/backlog.md`: section "Scaladoc: streams module" with rows for waves 1 to 3 and the close-out, columns wave, status, PR, coverage before, coverage after.
- [ ] A fresh reviewer reads all of PR 0 against the Review Focus. Fix. Ask the user, then push to `khajavi` and open the PR `build: one scaladoc-check project for every module, add streams`. The user merges it before the waves branch.

---

## Content waves

### Wave procedure (applies to Tasks 6 to 8)

Branch `git worktree add ../streams-scaladoc-wave-<n> -b streams-scaladoc-wave-<n> origin/main`. Then the skill's loop: brief in `task-<n>-brief.md`, implementer (opus), controller verification of its report, fact-check review (opus), rulings in `progress.md`, fix round, scoped re-review (sonnet) until APPROVE, PR `docs(streams): Scaladoc for <wave name>` with a plain-English body, bug entries to `article-coordinator`, CI watch. Imprecise "Minor" findings join the fix round.

Every wave also: reads the matching reference pages first; describes the real behaviour for the known issues listed; checks sentinel wording against the AGENTS.md policy; fact-checks every existing comment in its files.

### Task 6: Wave 1, `Stream`, `Sink`, `Pipeline`

**Files:** `streams/shared/src/main/scala/zio/blocks/streams/{Stream,Sink,Pipeline,ElementRepresentation}.scala`; `streams/{jvm,js}/src/main/scala/zio/blocks/streams/{StreamPlatformSpecific,SinkCompanionPlatformSpecific}.scala` (same text in both); `streams/shared/src/main/scala-{2,3}/zio/blocks/streams/LowPriorityJvmTypeInferPlatform.scala`.

**Tier 1:** `Stream` (overview; constructors such as `fromIterable` and `fromAcquireRelease`; `map`, `flatMap`, `catchAll`, `grouped`; the blocking and async terminals), `Sink` (overview, `fold`, `collect`), `Pipeline` (overview, `andThen`, applying one to a stream).
**Reference pages:** `docs/reference/streams/core/{stream,sink,pipeline}.md` (URLs `https://zioblocks.com/reference/streams/core/<id>/`) and `docs/reference/streams/execution-and-compatibility/*`.
**Known issues to reflect:** `issues.md` entries on `catchAll` inside `flatMap` followed by a stateful operator (`AsyncBoundaryRequired`), the `extends App` deadlock with `catchAll`, `flatMap` followed by a primitive `map`, and `fromAcquireRelease` defects. Split into 1a (`Stream`) and 1b (`Sink`, `Pipeline`, platform files) if the diff passes about 3,000 lines.

- [ ] Step 1: baseline coverage, then research (source, tests under `streams/shared/src/test`, reference pages).
- [ ] Step 2: write the Scaladoc for `Stream` and its platform parts.
- [ ] Step 3: write it for `Sink`, `Pipeline` and the rest.
- [ ] Step 4: fact-check every claim, run the gates, commit per file group, report.

### Task 7: Wave 2, `io`

**Files:** `streams/shared/src/main/scala/zio/blocks/streams/io/{Reader,Writer}.scala`; `streams/jvm/src/main/scala/zio/blocks/streams/io/AsyncToSyncReader.scala`; `streams/{jvm,js}/src/main/scala/zio/blocks/streams/io/AsyncReaderPlatform.scala`.

**Tier 1:** `Reader` (overview of sync and async readers, the pull protocol, `read`, `readN`, `close`, the end-of-stream marker and `lastReadWasEOF`), `Writer`.
**Reference pages:** `docs/reference/streams/primitives/{reader,writer}.md` (`https://zioblocks.com/reference/streams/primitives/<id>/`), `zero-boxing.md`.
**Known issues to reflect:** any `issues.md` entry that names a `Reader` or `Writer` member (search again at brief time).

- [ ] Steps as in Task 6. The existing `Reader` example is reused (from PR 0). Sentinel wording gets the closest review in this wave.

### Task 8: Wave 3, platform files and internals

**Files:** `streams/shared/.../{JvmType,Platform}.scala`; `streams/{jvm,js}/.../PlatformSpecific.scala` (fix the drift); `streams/jvm/.../{NioReaders,NioWriters,NioSinks,NioStreams,AsyncNioReaders}.scala`; `streams/jvm/.../queues/*`; `streams/js/.../ReadableStreamReaders.scala`; `streams/shared/.../internal/*` and the `scala-{2,3}` `InternalVersionSpecific` pair.

**Depth:** tier 2 for the public platform types, tier 3 (one line) for everything in `streams.internal`. `OpTag` (86 members) and `StreamState` (14) get one-line comments. Do not document a private implementation detail as if it were API.
**Reference pages:** `docs/reference/streams/execution-and-compatibility/{platform-differences,async-execution}.md`.
**Known issues to reflect:** the flaky `unfoldAsync` entries are test problems, not API, so they stay out. `NioSinks.fromByteBufferLong/Double` must document that a sentinel-valued element throws (AGENTS.md remedy 2).

- [ ] Steps as in Task 6.

### Task 9: Close-out

Follow `~/.claude/skills/scaladoc-rollout/references/closeout.md`: classify what is still undocumented, document the real gaps, fix tooling problems found by the waves (with unit tests), correct version drift in `AGENTS.md` and the style guide, mark the backlog done with the final numbers, and confirm each `issues.md` doc-error entry for `streams` is resolved or still correctly open. After the merge, tell the coordinator and give the user the summary.
