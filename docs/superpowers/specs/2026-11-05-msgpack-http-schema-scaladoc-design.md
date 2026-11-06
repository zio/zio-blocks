# schema-messagepack and http-model-schema Scaladoc design

Follows `docs/superpowers/specs/2026-11-05-html-scaladoc-design.md` and the `scaladoc-rollout` process.

## Problem

`schema-messagepack` has comments on 5 of 134 public API members (3%) and `http-model-schema` on 6 of 55 (10%): 11 of 189 in all. Nobody has checked that the existing comments are true; earlier rollouts found that 15% to 36% of inherited comments were wrong or imprecise. The riskiest claims here are the wire details of the MessagePack codec (integer and float widths, string versus binary, maps versus arrays for records, `Option` and `null`, enum and variant encoding, error positions) and the decoding rules of the HTTP codecs (repeated query parameters, missing versus empty values, case-insensitive header names, lists, defaults, error messages). Neither module has a `{{{` example, and five library bugs touch them (four in `http-model-schema`, one in `schema-messagepack`).

## Goal

Every public member of both modules has a true, short, useful comment. The tier-1 types have an overview, a compiled example and a reference-page link. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0. `--drift` stays empty. The Scala diff in both modules is comments only.

## Decisions (Milad, 2026-11-05)

- **One PR** for both modules (tooling and all Scaladoc).
- **Tier 1:** `MessagePackFormat`, `MessagePackCodec`; `QueryCodec`, `HeaderCodec`, the `Request`/`Response`/`Headers`/`QueryParams` schema extension methods (`RequestSchemaOps`, `ResponseSchemaOps`, `HeadersSchemaOps`, `QueryParamsSchemaOps`), `StringDecoder`.
- **Tier 2:** every other public member (the Reader and Writer types, the format and error types, the deriver classes), one to three sentences.
- **Tier 3:** `private[...]` code and plumbing: one line or nothing.
- **Examples:** sample data that fits the API (sensor readings as MessagePack bytes; an orders API's query parameters and headers). No books, no Zionomicon.
- **Agents:** Opus fact-checks and writes tier 1; Sonnet may write tier 2. Only one agent runs sbt at a time (memory).

## Layout

- `schema-messagepack`: `crossProject`, `CrossType.Pure`; five files in `schema-messagepack/src/main/scala/zio/blocks/schema/msgpack/` (`MessagePackCodecDeriver` 724 lines, `MessagePackCodec` 438, `MessagePackReader` 400, `MessagePackWriter` 350, `MessagePackFormat` 21). Package `zio.blocks.schema.msgpack`, not `...messagepack`.
- `http-model-schema`: `crossProject`, `CrossType.Full`; 13 shared files in `http-model-schema/shared/src/main/scala/zio/http/schema/`, including `package.scala` (a `package object schema` in `zio.http`). Package `zio.http.schema`.
- `moduleOf` takes the first path segment, so `http-model-schema/...` is already told apart from `http-model/...`; a test pins it down. Both modules use `crossProjectSettings`, so their cross versions equal `scaladoc-check`'s.
- Reference pages: `docs/reference/schema/built-in-codecs/messagepack.md`, `docs/reference/http-model/schema.md`, `docs/reference/http-model/schema-codecs.md`. Errors found there go to the coordinator as docs-issues entries, not fixed here.

## Tooling changes

- `project/ScaladocSnippets.scala`: add `"schema-messagepack" -> "zio.blocks.schema.msgpack"` and `"http-model-schema" -> "zio.http.schema"` to `packageOverrides`; extend `ScaladocSnippetsSpec` (import and object name for both, plus `http-model-schema` versus `http-model`).
- `build.sbt`: add `` `schema-messagepack`.jvm `` and `` `http-model-schema`.jvm `` to `scaladocCheckedModules`; update the comment above it.
- `docs/dev/scaladoc-style.md` module list: not edited here (the coordinator updates it).
- AGENTS.md: shown to Milad as an exact diff before commit.

## Known issues to reflect (cite by title in the coordinator's `library-issues.md`)

`http-model-schema`: `request.header[T](name)` and `response.header[T](name)` do not compile; derived `QueryCodec` and `HeaderCodec` ignore field default values; `query[T]` and `header[T]` compile for any schema but decode only primitives; `queryOrElse` and `headerOrElse` also swallow malformed values. `schema-messagepack`: a `DynamicSchema` written as MessagePack reads back as accept-anything. Grep the coordinator copy for `messagepack`, `msgpack` and `http-model-schema` for more.

## Tasks

- **Task 1 (Opus):** tooling; `schema-messagepack` tier 1 and the fact-check of every existing comment; probes against the published 0.0.56 (scala-cli, Scala pinned).
- **Task 2 (Sonnet):** `schema-messagepack` tier 2 (`MessagePackReader`, `MessagePackWriter`, `MessagePackCodecDeriver`).
- **Task 3 (Opus):** `http-model-schema` tier 1 and fact-check, then tier 2 for the rest.
- **Review:** fresh-context Opus fact-check that re-runs probes and every bug reproducer; fix round; Sonnet re-review of the fix diff.

## Gates

1. `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test`
2. `schema-messagepackJVM/doc`, `schema-messagepackJS/doc`, `http-model-schemaJVM/doc`, `http-model-schemaJS/doc` on 3.9.0 and 2.13.18: no new warnings, read from the log (sbt `--client` can exit 0 on failure)
3. `python3 -I .claude/scripts/scaladoc-coverage.py <module>` (134/134 and 55/55) and `--drift` (empty)
4. `comments-only.py aa8164217 <module>` for both modules: 0 code changes
5. `++2.13.18; check; headerCheckAll`; `scalafmtAll`
Before every sbt run: `free -g`, available at least 6 GB; `sbt --client shutdown` after each gate round.

## Out of scope

Library code changes, reference-page rewrites, `docs/dev/` files, `CLAUDE.md`, `README.md`, other modules.
