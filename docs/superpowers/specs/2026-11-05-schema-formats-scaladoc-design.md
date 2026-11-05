# Schema format modules Scaladoc design (avro, bson, csv, thrift)

Follows `docs/superpowers/specs/2026-11-04-typeid-scaladoc-design.md` and the `scaladoc-rollout` process. Backlog §13 row 7, first half.

## Problem

Four codec modules turn `Schema`-described Scala values into Avro, BSON, CSV and Thrift. Their public API has comments on 44 of 233 members (19%):

| Module | Documented | Notes |
|---|---|---|
| `schema-avro` | 0 / 53 | no comment at all, not even on `AvroFormat` or `AvroCodec` |
| `schema-bson` | 17 / 110 | `BsonSchemaCodec.Config#useNativeObjectId` describes behaviour that does not exist (a known bug) |
| `schema-csv` | 20 / 22 | mostly documented, but `CsvConfig.nullValue` is documented as used and is never read (a known bug) |
| `schema-thrift` | 7 / 48 | |

Nobody has checked the 44 existing comments; earlier rollouts found 19-36% of inherited comments wrong or imprecise, and codecs are where such claims go wrong (field order, `Option` and `null`, defaults, error messages, binary layout). No `{{{` example exists, and `scaladoc-check` does not compile these modules. `--drift` is empty (each module has one copy of every file).

## Goal

Every public member of the four modules has a true, short, useful comment. The tier-1 types have an overview, a compiled example and a reference-page link. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0. Coverage 233/233, `--drift` empty, the module sources unchanged apart from comments.

## Decisions (Milad, 2026-11-05)

- **Tier 1 (13 types):** `AvroFormat`, `AvroCodec`, `BsonSchemaCodec` (with `Config`), `BsonCodec`, `BsonEncoder`, `BsonDecoder`, `CsvFormat`, `CsvCodec`, `CsvConfig`, `CsvError`, `ThriftFormat`, `ThriftCodec`. Everything else is tier 2, deriver internals tier 3.
- **schema-csv on Scala.js:** doc build gate only (`schema-csvJS/doc`); behaviour is probed on the JVM, since JVM and JS share one source tree.
- **One PR** for all four modules.
- **Sample domains:** Avro sensor readings, BSON orders, CSV an export of sensor readings, Thrift an event log. No books.
- **AGENTS.md** (diff shown to Milad before editing): add the four modules to the `scaladoc-check` bullet; add a downstream line for them.

## Layout

| Module | sbt project(s) | Files (`<module>/src/main/scala/zio/blocks/schema/<fmt>/`) | Reference page |
|---|---|---|---|
| `schema-avro` | `schema-avro` (JVM) | `AvroFormat`, `AvroCodec`, `AvroCodecDeriver` | `docs/reference/schema/built-in-codecs/avro.md` |
| `schema-bson` | `schema-bson` (JVM) | `BsonSchemaCodec`, `BsonTypes`, `BsonCodecDeriver`, `ObjectIdSupport`, `package` | `.../bson.md` |
| `schema-csv` | `schema-csvJVM`, `schema-csvJS` (Pure) | `CsvFormat`, `CsvCodec`, `CsvConfig`, `CsvError`, `CsvReader`, `CsvWriter`, `CsvCodecDeriver` | `.../csv.md` |
| `schema-thrift` | `schema-thrift` (JVM) | `ThriftFormat`, `ThriftCodec`, `ThriftCodecDeriver` | `.../thrift.md` |

Packages are `zio.blocks.schema.<fmt>`, so the wrapper import needs no override; the extractor test already covers the `schema-avro` path. Cross versions equal `scaladoc-check`'s. The third-party jars (avro 1.12.2, bson 5.13.0, libthrift 0.25.0) are Compile dependencies, so `dependsOn` brings them onto the checker's classpath. No module test reads source text. Module sources are identical to `v0.0.56`, so claims are probed against the published 0.0.56 artifacts.

## Known issues to reflect

`library-issues.md`: csv `CsvError` row/column counting, nested case class fields, errors never name the column, `CsvConfig.nullValue` never read, failing `Schema#transform` check throws; avro `Array[Byte]` as an array of `int`, trailing bytes accepted, bytes of one type decode as another; bson `Option` as `{"Some": ...}`, `Config#withNativeObjectId` has no effect; the CSV test that uses `.contains`. The Scaladoc describes real behaviour and calls these "a known bug".

## Tasks

- **Task 1 (Opus A, runs sbt):** tooling (`scaladocCheckedModules` entries), `schema-avro` and `schema-thrift` (tier 1 `AvroFormat`, `AvroCodec`, `ThriftFormat`, `ThriftCodec`; all other members; fact-check of every existing comment), all gates.
- **Task 2 (Opus B, in parallel, no sbt):** `schema-bson` and `schema-csv` (tier 1 `BsonSchemaCodec`+`Config`, `BsonCodec`, `BsonEncoder`, `BsonDecoder`, `CsvFormat`, `CsvCodec`, `CsvConfig`, `CsvError`; all other members; fact-check of every existing comment). Compiles its examples with scala-cli in a copy of the checker wrapper and hands back READY_FOR_GATES; Task 1's agent runs the sbt gates over both halves.
- **Review:** two fresh-context Opus fact-checkers in parallel (A: avro + thrift; B: bson + csv), probing on 2.13.18 and 3.9.0 and re-running every bug reproducer; fix rounds by resuming the authors; Sonnet scoped re-reviews of the fix diffs.

## Gates

1. `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test`
2. `schema-avro/doc`, `schema-bson/doc`, `schema-csvJVM/doc`, `schema-csvJS/doc`, `schema-thrift/doc` on 3.9.0 and 2.13.18: no new warnings
3. `python3 -I .claude/scripts/scaladoc-coverage.py <module>` (233/233 in total) and `--drift` (empty)
4. `comments-only.py` against the base for each module (`build.sbt` and AGENTS.md are the only non-comment changes)
5. `++2.13.18; check; headerCheckAll; ++3.9.0; check; headerCheckAll`; `scalafmtSbt` for `build.sbt`

## Out of scope

`schema-toon`, `schema-xml`, `schema-yaml`, `schema-messagepack` (row 7, second half); library code changes (bugs are reported, not fixed); reference-page rewrites (errors go to `docs-issues.md` entries via the coordinator); `docs/dev/` files, including the style guide's module list.
