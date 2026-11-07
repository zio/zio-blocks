# Chunk module Scaladoc: design

Date: 2026-10-29. Branch: `scaladoc-chunk`. Replays the `schema` and `streams` rollouts (see `2026-10-21-schema-scaladoc-design.md` and `2026-10-26-streams-scaladoc-design.md`). `chunk` is the pilot for the remaining modules (`docs/dev/backlog.md` §13).

## Problem

Most of the `chunk` public API already has a Scaladoc comment, but nobody has checked that the comments are true, and none of them has a code example.

- `.claude/scripts/scaladoc-coverage.py chunk` on `main` at `9d8bb9d70` counts 205 API members, and 153 (74%) have a comment. There is no drift between the jvm and js copies.

  | File | Members | Undocumented |
  |---|---|---|
  | `shared/.../Chunk.scala` (4,204 lines) | 185 | 51 |
  | `shared/.../ChunkMap.scala` (657 lines) | 20 | 1 |
  | `jvm/` and `js/` `ChunkPlatformSpecific`, `ChunkBuilderPlatformSpecific` | 0 | 0 |

- The 52 gaps: `addOne`, `clear` and `result` on the eight primitive `ChunkBuilder`s (24), `ChunkBuilder.from`, the `BitOps` operators (10), `IsText` and its three instances (5), `BitChunk.Endianness` (5), `NonEmptyChunk#reverse`, and the companions `Chunk`, `ChunkBuilder`, `NonEmptyChunk`, `ChunkIterator` and `ChunkMap`.
- About 220 `override` members (`map`, `filter`, ... on `Chunk` and `ChunkMap`) carry no comment and are not counted. Scaladoc copies the parent's comment onto them, so they are documented on `/api` and in IDE hovers.
- The 153 existing comments came from ZIO 2's `Chunk` and were never fact-checked against this code. They are the main risk.
- There are no `{{{ }}}` blocks at all, so nothing needs to be made to compile first.
- `chunk` is on `/api` (`chunk.jvm` is in `unidocProjectFilter`), and IDE hovers see the `js/` copies.
- No known bug in `docs/dev/library-issues.md` or `docs/dev/docs-issues.md` is about `chunk`.
- `git diff v0.0.56 HEAD -- chunk/*/src/main` is empty, so probes against the published `zio-blocks-chunk:0.0.56` match the branch.

## Goal

Every public type and member of `chunk` has accurate Scaladoc that reads well on `/api` and in IDE hovers on Scala 2.13 and 3. The four core types each have a compiled example, every example compiles in CI, and every existing comment has been checked against the code.

## Decisions (user, 2026-10-29)

| Question | Decision |
|---|---|
| Audience | `/api` and IDE hovers. `jvm/` and `js/` copies get identical text. |
| Tier 1 | `Chunk`, `NonEmptyChunk`, `ChunkBuilder`, `ChunkMap`: the types `docs/reference/chunk.md` covers. |
| Existing comments | Fact-checked in full, including comments on `override` members that have one. |
| Overrides with no comment | Left alone (they inherit). Document one only if it behaves differently from the collection contract it overrides. |
| Tier 3 | `BitOps`, `BitChunk*`, `ChunkPackedBoolean`, `private[chunk]` members: one line or nothing. |
| Checker | Add `chunk.jvm` to `scaladocCheckedModules`. No override is needed (package `zio.blocks.chunk`, same cross versions as `schema`). |
| PR shape | One PR: tooling and the whole wave. |
| Implementers | Split, to answer the §13.4 open question: an Opus implementer writes tier 1 and fact-checks every existing comment; a Sonnet implementer writes the tier-2 and tier-3 gaps. One Opus fact-check review covers both. |
| W2 (`maybe`, `combinators`) | Out of scope; a later task. |
| AGENTS.md | Two lines updated in this PR (approved by the user): `chunk*` downstream gains `scaladoc-check`, and the Scaladoc bullet names `chunk`. |
| Backlog | Not edited here; the coordinator applies the §13 update. |

## Gates

1. `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test` exits 0.
2. `++3.9.0; chunkJVM/doc; ++2.13.18; chunkJVM/doc; ++3.9.0; chunkJS/doc` adds no `warn` lines compared with `main`.
3. `python3 -I .claude/scripts/scaladoc-coverage.py chunk --drift` shows no drift.
4. Comments-only: with comments and whitespace stripped, every changed file under `chunk/` equals its version at the base commit.
5. `++2.13.18; check; headerCheckAll; ++3.9.0; check; headerCheckAll`.
6. `chunk` has no tests that read source text, so there is no module-specific gate.

## Done

- Coverage 205 / 205, no drift, every snippet compiles on the three versions.
- Every existing comment has been checked; the report counts how many were false and fixed.
- The PR is green and merged by the user.

## Out of scope

Behaviour changes (bugs are filed with the coordinator), other modules, test sources, docs pages other than `docs/reference/chunk.md`, and `docs/dev/` files other than `docs/dev/scaladoc-style.md`.
