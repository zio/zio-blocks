# schema-toon, schema-xml and schema-yaml Scaladoc design

Follows `docs/superpowers/specs/2026-11-04-typeid-scaladoc-design.md` and the `scaladoc-rollout` process. Backlog §13 row 7, second half (the sibling `schema-formats-scaladoc` does avro, bson, csv and thrift).

## Problem

The three text-format codec modules have comments on 184 of 542 public API members (34%):

| Module | Documented | % |
|---|---|---|
| `schema-toon` | 37 / 202 | 18 |
| `schema-xml` | 120 / 223 | 53 |
| `schema-yaml` | 27 / 117 | 23 |

Nobody has checked that the existing comments are true; earlier rollouts found 19–36% of inherited comments wrong or imprecise, mostly claims about reader and writer behaviour (quoting, escaping, whitespace, nulls and empty values, key naming, discriminators). Two `{{{` blocks (`XmlBuilder`, `XmlSelection`) are never compiled. `--drift` is empty for all three. The library has about 24 known bugs in these modules (toon 8, yaml 16), several of which a correct comment must mention.

## Goal

Every public member of the three modules has a true, short, useful comment. The tier-1 types have an overview, a compiled example and a reference-page link. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0. `--drift` stays empty. Coverage 542/542.

## Decisions (Milad, 2026-11-05)

- **One PR** (tooling, the two existing blocks made to compile, Scaladoc for all three modules).
- **Tier 1:**
  - toon: `ToonFormat`, `ToonCodec`, `ToonCodecDeriver`, `ReaderConfig`, `WriterConfig`;
  - xml: `Xml`, `XmlCodec`, `XmlFormat`, `syntax` (both copies), `XmlSelection`, `XmlPatch`;
  - yaml: `Yaml`, `YamlCodec`, `YamlFormat`, `YamlOptions`, the `yaml"..."` interpolator (`package.scala`, both copies).
- **Scala.js:** `<module>JS/doc` gate, plus a scala-cli Scala.js probe for every comment that claims how numbers are formatted (`Double`, `Float`, `BigDecimal` output), where JVM and JS differ.
- **AGENTS.md:** exact diff shown to Milad before editing.

## Layout

| Module | Sources | Version-specific | Package |
|---|---|---|---|
| `schema-toon` | `schema-toon/src/main/scala/zio/blocks/schema/toon/` (12 files, `CrossType.Pure`) | none | `zio.blocks.schema.toon` |
| `schema-xml` | `schema-xml/shared/src/main/scala/.../xml/` (15 files) | `syntax.scala` in `scala-2.12-2.13/` and `scala-3/` | `zio.blocks.schema.xml` |
| `schema-yaml` | `schema-yaml/shared/src/main/scala/.../yaml/` (13 files, `CrossType.Full`, no jvm/js sources) | `package.scala` in `scala-2/` and `scala-3/` | `zio.blocks.schema.yaml` |

`scaladoc-check` reads each module's `Compile / unmanagedSources`, so the version directories (`BuildHelper` adds `2.13+` and `2.12-2.13` on 2.13) are picked per Scala version. All three use the default `stdSettings` versions (3.9.0, 3.10.0, 2.13.18), the same as `scaladoc-check`. Their packages fit `ScaladocSnippets.moduleImport`, so the tooling change is three `scaladocCheckedModules` entries.

The sources equal `v0.0.56` except three toon files whose differences since then are comment-only (#1773, #1887), so every probe runs against the published 0.0.56 artifacts with scala-cli, and bug citations use base `a7014433f` lines ("lines at v0.0.56 = a7014433f").

Reference pages: `docs/reference/schema/built-in-codecs/{toon,xml,yaml,index}.md` (`https://zioblocks.com/reference/schema/built-in-codecs/<fmt>/`), `docs/reference/schema/format.md`, `codec.md`. Errors there go to the coordinator as docs-issues entries; no page edits here.

## Known traps

- `yaml"..."` does not compile on Scala 2.13 outside the `zio.blocks.schema` package (a known bug). Examples that use it get `// scala-3-only`, or a 2.13-friendly form if one compiles.
- Examples run inside a generated `scaladoccheck` object, which is outside `zio.blocks.schema`, the same place a user's code is.
- The known yaml reader and writer bugs (misread or dropped input, `#` in strings, `BigDecimal`, kebab-case keys, sealed traits, empty strings, derivation reuse) and the toon ones decide what several comments must say.

## Tasks

- **Task 1 (controller):** the three `scaladocCheckedModules` entries; an extractor test for a `CrossType.Pure` path without `shared/` and a `scala-2.12-2.13` path; the style-guide module list; the AGENTS.md lines (diff approved). Run `scaladoc-check/test` once to see the two xml blocks' state.
- **Task 2 (three Opus writers in parallel, one per module):** tier 1, fact-check of every existing comment in the module (table: member, claim, kind, evidence), the two xml blocks, suspected bugs. Probes with scala-cli on 2.13.18 and 3.9.0; no sbt.
- **Task 3 (three Sonnet writers in parallel, after Task 2):** tier 2 and 3 gaps per module, from the coverage script's undocumented list. Suspects go to a file, not into prose.
- **Task 4 (Opus bug-writer):** turn suspects into full, run-verified entries.
- **Task 5 (controller, one sbt runner):** gates.
- **Task 6:** fresh-context Opus fact-check review (split by module), fix round with the authors resumed, Sonnet re-review of the fix diff.
- **Task 7:** merge `origin/main`, push, PR, CI.

## Gates

1. `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test`
2. `<module>JVM/doc` and `<module>JS/doc` for the three modules on 3.9.0 and 2.13.18: no new warnings, no `[error]` (read the log, not the exit code)
3. `python3 -I .claude/scripts/scaladoc-coverage.py <module>` (542/542 total) and `--drift` (empty)
4. `comments-only.py a7014433f <module-dir>` for each module: `0 with code changes`
5. `++2.13.18; check; headerCheckAll; ++3.9.0; check; headerCheckAll`; `scalafmtSbt` for `build.sbt`
6. The modules' own tests do not scan source text (checked: no `src/main` / `readAllLines` / `readString` in their tests), so no extra gate.

## Out of scope

schema-avro, -bson, -csv, -thrift (sibling session), schema-messagepack, other modules, library code changes, reference-page rewrites, `docs/dev/library-issues.md`, `docs-issues.md`, `backlog.md`.
