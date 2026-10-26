# Streams module Scaladoc: design

Date: 2026-10-26. Branch: `streams-scaladocs`. Replays the `schema` rollout (see `2026-10-21-schema-scaladoc-design.md`).

## Problem

Most of the `streams` public API already has a Scaladoc comment, but nobody has checked that the comments are true, and almost none of them has a compiled example.

- `.claude/scripts/scaladoc-coverage.py streams` on `main` at `bbc713bef` (2026-10-26) counts 561 API members, and 361 (64%) have a comment.

  | Package | Members | Documented | % |
  |---|---|---|---|
  | `streams` | 296 | 244 | 82 |
  | `streams.io` | 136 | 93 | 68 |
  | `streams.internal` | 110 | 5 | 4 |
  | `streams.queues` | 19 | 19 | 100 |

- The undocumented members are few and concentrated: `OpTag` 86 and `StreamState` 14 (both internal), `Reader` 42, `Stream` 18, `JvmType` 9, the Scala.js `ReadableStreamReaders` 9, and about 25 spread over small platform files.
- The 361 existing comments were never fact-checked. The `schema` rollout showed that careful, source-reading authors still wrote false claims ("fills with zeros", "validated against the schema"), so existing text is a risk, not an asset.
- One `{{{ }}}` block exists (in `Reader.scala`) and nothing compiles it.
- The jvm and js copies of `PlatformSpecific` carry different Scaladoc text.
- `streams` is on `/api` (`streams.jvm` is in `unidocProjectFilter`), and IDE hovers see the `js/` and `scala-2/` copies.
- Several known bugs touch the module (`docs/dev/issues.md`: `catchAll` inside `flatMap` followed by a stateful operator, `object ... extends App` deadlock, `flatMap` followed by a primitive `map`, `fromAcquireRelease` defects). The docs must describe the real behaviour.

## Goal

Every public type and member of `streams` has accurate Scaladoc that reads well on `/api` and in IDE hovers on Scala 2.13 and 3, every code example compiles in CI, and the sentinel/EOF edge case is described exactly as the AGENTS.md performance policy defines it.

## Decisions

| Question | Decision |
|---|---|
| Audience | `/api` and IDE hovers. `jvm/`, `js/`, `scala-2/` and `scala-3/` copies are documented too, with identical text. |
| Depth | Tiered (`docs/dev/scaladoc-style.md`). |
| Tier 1 | `Stream`, `Sink`, `Pipeline`, `Reader`, `Writer`: the five types with reference pages. |
| Existing comments | Fact-checked in full. Each wave reviews every public member of its files, existing or new. |
| Internals (`streams.internal`) | Tier 3: one line per member, in the last wave. |
| Examples | Compiled in CI by one shared checker (below). |
| Checker | One general `scaladoc-check` sbt project for every module, replacing `schema-scaladoc-check`. Approved by the user (the AGENTS.md "ask first" for new projects). |
| Rollout | PR 0 (tooling), three content waves, a close-out. |
| Sentinel/EOF | Described on the members it affects, matching the AGENTS.md policy. Docs never suggest per-element fixes. |
| PR 0 scope | Tooling only. The existing `Reader` snippet is made to compile there; the `PlatformSpecific` drift is fixed in wave 3. |

## The shared checker

`scaladoc-check` is one unpublished sbt project (JVM only) that depends on the JVM project of every module on a list in `build.sbt`: `schema` and `streams` at first. A later module is added with one list entry. The shared tooling lives in `project/ScaladocSnippets.scala`.

- Each snippet is wrapped with the import of its own module, worked out from the source path: a file under `streams/` gets `import zio.blocks.streams._`.
- Wrapper object names carry the module name, so equal file names in two modules cannot collide.
- The project must use Scala versions that every listed module supports. `schema` and `streams` both build on 2.13.18, 3.9.0 and 3.10.0 today. (`streams` and `async` also accept 3.8.3 only through the `golemStreamsAsyncVersion` alias, which the checker ignores.)
- The `-experimental` handling stays as in the schema checker: dropped on the 3.9 main line, kept on 3.10+.
- JDK 17: `streams` compiles on JDK 17 (it calls the virtual-thread API through reflection in `PlatformSpecific`), so the shared checker builds there too.
- The `schema` snippets must keep compiling unchanged. The existing extractor tests pin this, plus one new test for `streams`.

## Waves

| Wave | Files | Main tier |
|---|---|---|
| 1. Stream, Sink, Pipeline | `Stream.scala`, `Sink.scala`, `Pipeline.scala`, `ElementRepresentation`, `StreamPlatformSpecific` and `SinkCompanionPlatformSpecific` (jvm and js), `LowPriorityJvmTypeInferPlatform` (scala-2 and scala-3) | 1, 2 |
| 2. io | `io/Reader.scala`, `io/Writer.scala`, `AsyncReaderPlatform` (jvm and js), `AsyncToSyncReader` | 1, 2 |
| 3. Platform and internals | `JvmType`, `Platform`, `PlatformSpecific` (jvm and js, drift fix), `Nio*` readers, writers, sinks and streams, `AsyncNioReaders`, `queues/*`, `ReadableStreamReaders` (js), and `internal/*` (`OpTag`, `StreamState`, `SyncInterpreter`, `StreamError`, `package`, the `InternalVersionSpecific` copies) | 2, 3 |

A wave that grows past about 3,000 changed lines splits into `a` and `b`. Wave 1 is the likeliest to split (`Stream`, then `Sink` and `Pipeline`). The waves touch disjoint files.

## Workflow per wave

As in `schema`: an opus implementer researches, writes and runs the gates; the controller verifies its claims (comments-only check, drift); a fresh opus reviewer fact-checks every claim and probes behaviour against the published artifact; a fix round per ruling; a sonnet scoped re-review; the PR; bug entries handed to the `article-coordinator` session as ready-to-paste text. The user merges every PR.

## Gates

1. `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test` exits 0.
2. `++3.9.0; streamsJVM/doc; ++2.13.18; streamsJVM/doc` adds no `warn` lines compared with `main`.
3. `python3 -I .claude/scripts/scaladoc-coverage.py streams --drift` shows no drift for the wave's files.
4. Comments-only: with comments and whitespace stripped, every changed file equals its `origin/main` version.
5. `++2.13.18; check; headerCheckAll; ++3.9.0; check; headerCheckAll`.
6. `streamsJVM/testOnly zio.blocks.streams.SpecializationStaticCompletenessSpec` on 2.13.18, 3.9.0 and 3.10.0: it scans raw source lines, comments included, against a checked-in inventory, so a doc line can change it.
7. Sentinel-adjacent text is checked by the reviewer against the AGENTS.md policy. Because the change is comments only, the JMH benchmarks are not rerun.

## Done

- Every wave and the close-out are merged.
- Every tier 1 and tier 2 public member has Scaladoc. The close-out classifies what remains.
- Every snippet compiles on 2.13.18, 3.9.0 and 3.10.0.
- Cross-version copies carry the same text.
- No known Scaladoc contradicts the code, except where the contradiction is filed.

## Out of scope

- Behaviour changes. Bugs found are filed with the coordinator, not fixed. In particular no change to sentinel handling.
- Other modules (the shared checker is built so they can be added later).
- Test sources.
