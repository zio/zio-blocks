# Schema format modules Scaladoc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run the tasks. Checkbox (`- [ ]`) steps track progress. The process is the `scaladoc-rollout` skill.

**Spec:** `docs/superpowers/specs/2026-11-05-schema-formats-scaladoc-design.md`

## In plain English

**The problem.** Four modules turn Scala values into the Avro, BSON (MongoDB), CSV and Thrift formats. A *codec* is the object that does the converting in both directions: encoding a value to bytes or text, and decoding it back. Only 44 of their 233 public members have a comment, nobody has checked that those comments are true (two are known to be false), and no comment has a code example. The build's example checker, `scaladoc-check`, does not look at these modules.

**The goal.** Every public member has a true, short comment. The 13 types a user meets first have an overview, a code example that the build compiles, and a link to the reference page. CI compiles every example on Scala 2.13.18, 3.9.0 and 3.10.0. The module code itself does not change: bugs found along the way are written up and reported, not fixed.

**The steps.**
1. **Tooling.** Add the four modules to `scaladoc-check`'s list in `build.sbt`, so their examples get compiled. Add them to two `AGENTS.md` lines (the controller does this, with Milad's approval of the diff).
2. **Avro and Thrift (Opus agent A).** The two binary formats have the same shape (a `Format`, a `Codec` with built-in codecs per Scala type, a `Deriver`). Agent A writes their comments, checks every existing one against the code, and runs the build checks for both halves.
3. **BSON and CSV (Opus agent B, at the same time).** Agent B writes and checks the comments of the other two modules. It does not run sbt (memory is tight and two sbt builds at once fail); it compiles its examples with scala-cli and hands back to agent A for the build checks.
4. **Review.** Two fresh agents, one per half, test every changed claim against the code on Scala 2.13 and 3, against the published 0.0.56 library. The authors fix what they find; a cheaper agent re-checks only the fixes.
5. **Ship.** One pull request. Each bug goes to the docs-backlog coordinator as a ready-to-paste entry with a reproducer.

## Global Constraints

Full text: `.superpowers/sdd/2026-11-05-schema-formats-scaladoc/global-constraints.md` (skill template with the four modules filled in). Base for the comments-only check: `c2e4a39fd`.

## Task 1 (Opus A, runs sbt): tooling, schema-avro, schema-thrift, gates

**Files:** `build.sbt` (`scaladocCheckedModules` only: `` `schema-avro` ``, `` `schema-bson` ``, `` `schema-csv`.jvm ``, `` `schema-thrift` ``), `schema-avro/src/main/scala/zio/blocks/schema/avro/{AvroFormat,AvroCodec,AvroCodecDeriver}.scala`, `schema-thrift/src/main/scala/zio/blocks/schema/thrift/{ThriftFormat,ThriftCodec,ThriftCodecDeriver}.scala`.

**Tier 1 and key methods:**
- `AvroFormat` (what it is, `AvroFormat.codec`-style use via `Schema#derive`, MIME type).
- `AvroCodec` (`avroSchema`, `encode(value)`, `encode(value, OutputStream)`, `decode(Array[Byte])`, `decode(InputStream)`, `encodeValue`/`decodeValue`; the companion's built-in codecs and the Avro type each one writes).
- `ThriftFormat`, `ThriftCodec` (the same set for Thrift: `encode`/`decode` on bytes and `ByteBuffer` as inherited from `BinaryCodec`, `encodeValue`/`decodeValue` on a `TProtocol`, the built-in codecs and the Thrift type each writes, for example `Float` as a Thrift `double`).
- Deriver internals (`AvroCodecDeriver`, `ThriftCodecDeriver`, their field-info classes): tier 2 for the public `Deriver` object, tier 3 for the rest.

**Reference pages:** `docs/reference/schema/built-in-codecs/avro.md`, `thrift.md` (`https://zioblocks.com/reference/schema/built-in-codecs/avro/`, `.../thrift/`); guides `docs/guides/encode-and-decode-records-with-avro.md`, `build-a-schema-registry.md`, `one-type-many-formats.md`.
**Known issues to reflect:** `library-issues.md` "Avro: `Array[Byte]` encodes as an Avro array of `int`, not `bytes`" (8105), "Avro `decode` accepts input with trailing bytes and returns the first record" (13955), "Avro bytes from one type decode silently as a different type" (13997) (coordinator copy). Check whether the Thrift codec has the same trailing-bytes and cross-type behaviour.

- [ ] Step 1: Record base `doc` warning counts (3.9.0, 2.13.18) for the five projects. Add the four list entries; run `scaladoc-check/test` once on 3.9.0 to see the empty baseline compile. Commit `build: compile schema-avro, -bson, -csv and -thrift Scaladoc examples`.
- [ ] Step 2: Research avro and thrift (source, tests, reference pages); write the Scaladoc; fact-check every existing comment (table: member, claim, verdict, evidence). Probes on 2.13.18 and 3.9.0 with scala-cli against 0.0.56.
- [ ] Step 3: Commit per module. Write `task-1-report.md` and `task-1-issues.md`.
- [ ] Step 4: When Task 2 hands back READY_FOR_GATES, run all sbt gates over all four modules, fix compile errors in your own files, report Task 2's to the controller, and record the results.

## Task 2 (Opus B, parallel, no sbt): schema-bson, schema-csv

**Files:** `schema-bson/src/main/scala/zio/blocks/schema/bson/{BsonSchemaCodec,BsonTypes,BsonCodecDeriver,ObjectIdSupport,package}.scala`, `schema-csv/src/main/scala/zio/blocks/schema/csv/{CsvFormat,CsvCodec,CsvConfig,CsvError,CsvReader,CsvWriter,CsvCodecDeriver}.scala`.

**Tier 1 and key methods:**
- `BsonSchemaCodec` (`bsonCodec`/`bsonEncoder`/`bsonDecoder` with and without `Config`, `Config` and its `with*` methods, `SumTypeHandling` cases, `Codecs`).
- `BsonCodec` (`encoder`, `decoder`, `transform`, `transformOrFail`, the companion's built-in codecs and the BSON type each writes), `BsonEncoder` (`encode`, `toBsonValue`, `contramap`, `isAbsent`), `BsonDecoder` (`decode`, `fromBsonValue`, `map`, `mapOrFail`, `Error`).
- `CsvFormat`, `CsvCodec` (`headerNames`, `encode`/`decode` as inherited from `TextCodec`), `CsvConfig` (each field, `default`, `tsv`), `CsvError` (each case, `row`, `column`, `message`).
- Tier 2: `BsonTrace`, `BsonBuilder`, `ObjectIdSupport`, the `package` syntax, `BsonCodecDeriver` (public `with*` methods), `CsvReader`, `CsvWriter`, `CsvCodecDeriver`. Tier 3: deriver internals.

**Reference pages:** `docs/reference/schema/built-in-codecs/bson.md`, `csv.md`; guides `docs/guides/store-case-classes-in-mongodb.md`, `read-and-write-csv-and-tsv-files.md`, `one-type-many-formats.md`.
**Known issues to reflect:** csv: "`CsvError` row and column count from the start of the failing row" (2828), "a nested case class field is accepted and written as one cell with a line break inside" (2866), "decode errors never name the column, and `CsvError.TypeError` is never produced" (2915), "`CsvConfig.nullValue` is never read" (2958), "a failing `Schema#transform` check makes `CsvCodec#decode` throw" (2989); bson: "`Option` is stored as a `{"Some": ...}` sub-document" (8646), "`BsonSchemaCodec.Config#withNativeObjectId` has no effect" (8738).

- [ ] Step 1: Research bson and csv; write the Scaladoc; fact-check every existing comment (table). Probes on 2.13.18 and 3.9.0 with scala-cli against 0.0.56.
- [ ] Step 2: Compile every new `{{{` block with scala-cli in a copy of the checker wrapper (`package scaladoccheck; import zio.blocks.schema.<fmt>._; object Snippet { <code> }`) on 2.13.18 and 3.9.0 (`dev.zio::zio-blocks-schema-<fmt>:0.0.56`); Task 1's sbt gate covers 3.10.0.
- [ ] Step 3: Format, commit per module (explicit paths), write `task-2-report.md` and `task-2-issues.md`, hand back READY_FOR_GATES.

## Task 3: review and ship

- [ ] Two fresh Opus fact-check reviews in parallel (A: avro + thrift, B: bson + csv); rulings in `progress.md`; fix rounds (authors resumed); Sonnet scoped re-reviews until APPROVE.
- [ ] Controller: AGENTS.md lines (approved diff), `check; headerCheckAll` on 2.13.18 and 3.9.0, `scalafmtSbt`, merge `origin/main`, push `khajavi schema-formats-scaladoc`, open the PR, watch CI to green.
- [ ] Send the coordinator the bug entries, the PR number, review counts, CI result, §13g text and the metric.
