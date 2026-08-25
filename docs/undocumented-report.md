---
id: undocumented-report
title: "Documentation Coverage Report"
---

# Documentation Coverage Report

Full re-scan of documentation coverage across every library module aggregated by the `root` project. Replaces the 2026-02-13 report, which predated most of the current `docs/reference` tree and covered only 12 modules.

**Progress since this report was generated:** the `http-model` typed header surface has been documented — `docs/reference/http-model/headers.md` (660 lines) and `server-sent-event.md` (296 lines) are new, and `model.md`'s `## Headers` section was rewritten, taking the module from 31% to 55% explained coverage and from 101 absent types to 6. `otel` is complete at 100%, though that item as originally written was mis-scoped — most of the module is `private[otel]`; see section 4 for what was actually missing and the lesson for other low-ratio rows. Tier 1 items 1, 3, and 7 are complete, and the totals below account for both.

Earlier: the `schema` search/update subsystem has been documented — `docs/reference/schema/schema-search.md` (251 lines, mdoc-verified) covers `SchemaMatch`, `SearchTraversal`, and `Reflect.Updater`/`Term.Updater`. Rescanning the whole `schema` module (not just this page's own additions — the module's absent/unexplained counts also reflect every other doc improvement made since the 2026-08-21 baseline, since matching is name-based across the whole `docs/` tree) puts it at 76% explained coverage and 86 absent types, down from 48%/210 at that baseline. Tier 1 item 5 is complete; the coverage table has been updated to match.

Earlier: the `htmx` header side has been documented — `docs/reference/htmx/response-headers.md` (229 lines, mdoc-verified) covers the 22 request/response header types in `HtmxHeaders.scala` that the attribute-DSL pages never mentioned, taking the module from 54% to 85% explained coverage and from 26 absent types to 0. Tier 1 item 4 is complete; the coverage table has been updated to match.

Earlier: the `config` family has been documented — `docs/reference/config.md` was replaced by a seven-page `docs/reference/config/` directory (2,212 lines, all code blocks mdoc-verified), taking the family from 31% to 72% explained coverage and from 43 absent types to 3. Tier 1 item 2 is complete; the numbers in this report have been updated to match.

**What changed since the previous (2026-02-13) report:** every published module now has a reference page, and every page is linked from `docs/sidebars.js`. There are no longer any modules with zero documentation, and four of the six "critical missing pages" from the old report now exist (`media-type.md`, `schema/schema-expr.md`, `schema/schema-error.md`, `built-in-codecs/json/json-patch.md`). The remaining gaps are (a) whole subsystems inside otherwise-documented modules, (b) pages far too short for the surface they cover, and (c) an almost complete absence of task-oriented guides.

## Summary

| Metric | Count |
|--------|-------|
| Library modules aggregated by `root` | 38 |
| Modules with no reference page | **0** |
| Reference pages | 164 |
| Guides | 9 |
| Public types found (`class` / `trait` / `object` / `enum`, non-`private`) | 1,828 |
| Types never named anywhere in `docs/` | **469** |
| Types with no prose or heading reference | **793** |
| Name-mention coverage | **74%** |
| Explained-type coverage | **56%** |

Two coverage numbers are reported because they answer different questions.

- **Name-mention coverage** counts a type as covered if its name appears anywhere in `docs/`, including inside an example code block. Its complement — 469 types — is the set that is entirely absent from the documentation.
- **Explained-type coverage** is stricter: it requires the name in prose (inline code) or in a heading. Its complement — 793 types — additionally captures the 324 types that appear only as tokens inside examples and are never explained.

This report file is excluded from the scan, so listing a type here does not make it count as documented. Counts are per module, so a name defined in two modules is counted twice; the distinct-name totals are 1,632 types and 465 absent. Both figures are name-based lower bounds; see *Methodology*.

---

## Module Coverage Table

`types` = public types in `*/src/main/**`. `absent` = never named anywhere in `docs/`. `unexplained` = no prose or heading reference. `ratio` = documentation lines / source lines for that module's pages.

| Module | types | absent | unexplained | explained cov | src LOC | doc LOC | ratio | Primary page |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| **http-model-schema** | 18 | 14 | 14 | **22%** | 1,249 | 607 | 0.49 | `reference/http-model/schema.md` |
| **smithy** | 42 | 16 | 32 | 24% | 2,585 | 533 | 0.21 | `reference/smithy.md` |
| **async** | 24 | 17 | 18 | 25% | 6,540 | 1,291 | 0.20 | `reference/async.md` |
| **html** | 86 | 33 | 54 | 37% | 4,725 | 1,117 | 0.24 | `reference/html.md` |
| **datastar** | 50 | 23 | 28 | 44% | 1,881 | 346 | 0.18 | `reference/datastar.md` |
| **scope** | 24 | 8 | 12 | 50% | 7,085 | 3,577 | 0.50 | `reference/resource-management/` |
| **typeid** | 91 | 26 | 44 | 52% | 6,493 | 2,124 | 0.33 | `reference/typeid.md` |
| **telemetry** | 134 | 25 | 58 | 57% | 7,509 | 2,856 | 0.38 | `reference/telemetry/` |
| http-model | 191 | 6 | 85 | 55% | 4,712 | 3,127 | 0.66 | `reference/http-model/` |
| otel | 12 | 0 | 0 | **100%** | 1,325 | 421 | 0.32 | `reference/telemetry/otel/` |
| schema-xml | 35 | 6 | 15 | 57% | 3,334 | 1,034 | 0.31 | `built-in-codecs/xml.md` |
| schema-bson | 18 | 1 | 7 | 61% | 1,790 | 472 | 0.26 | `built-in-codecs/bson.md` |
| codegen | 46 | 6 | 17 | 63% | 2,100 | 3,024 | 1.44 | `reference/codegen/` |
| streams | 29 | 9 | 10 | 66% | 18,434 | 5,725 | 0.31 | `reference/streams/` |
| combinators | 6 | 2 | 2 | 67% | 1,132 | 524 | 0.46 | `reference/combinators.md` |
| endpoint | 58 | 16 | 18 | 69% | 2,805 | 1,757 | 0.63 | `reference/endpoint/` |
| config (+ `-yaml`/`-json`/`-hocon`) | 77 | 3 | 21 | 72% | 3,914 | 2,212 | 0.57 | `reference/config/index.md` |
| schema-yaml | 28 | 6 | 8 | 71% | 2,753 | 552 | 0.20 | `built-in-codecs/yaml.md` |
| chunk | 20 | 2 | 5 | 75% | 5,069 | 3,140 | 0.62 | `reference/chunk.md` |
| **schema** | 516 | 86 | 124 | 76% | 84,969 | 24,096 | 0.28 | `reference/schema/` |
| markdown | 46 | 3 | 10 | 78% | 2,596 | 1,539 | 0.59 | `reference/docs.md` |
| schema-toon | 28 | 2 | 5 | 82% | 4,690 | 1,050 | 0.22 | `built-in-codecs/toon.md` |
| **htmx** | 82 | 0 | 12 | 85% | 1,548 | 2,750 | 1.78 | `reference/htmx/` |
| mux | 13 | 2 | 2 | 85% | 1,222 | 823 | 0.67 | `reference/mux.mdx` |
| openapi | 45 | 1 | 6 | 87% | 2,431 | 1,301 | 0.54 | `reference/openapi.md` |
| schema-csv | 9 | 0 | 1 | 89% | 1,247 | 564 | 0.45 | `built-in-codecs/csv.md` |
| sql | 53 | 1 | 3 | 94% | 3,736 | 4,008 | 1.07 | `reference/sql/` |
| mediatype | 16 | 2 | 11 | 31% | 12,676 | 460 | 0.04 | `reference/media-type.md` |
| maybe | 9 | 6 | 6 | 33% | 491 | 826 | 1.68 | `reference/maybe.md` |
| context | 2 | 1 | 1 | 50% | 865 | 553 | 0.64 | `reference/context.md` |
| ringbuffer | 8 | 0 | 0 | **100%** | 2,360 | 989 | 0.42 | `reference/ringbuffer/` |
| schema-avro | 3 | 0 | 0 | **100%** | 1,922 | 450 | 0.23 | `built-in-codecs/avro.md` |
| schema-messagepack | 5 | 0 | 0 | **100%** | 1,933 | 507 | 0.26 | `built-in-codecs/messagepack.md` |
| schema-thrift | 3 | 0 | 0 | **100%** | 868 | 432 | 0.50 | `built-in-codecs/thrift.md` |
| sql-zio | 1 | 0 | 0 | **100%** | 218 | 112 | 0.51 | `reference/sql-zio.md` |

Notes on reading this table:

- The four `config*` modules share one page directory, so the family is shown as one row (`config` 65/1/14, `config-hocon` 11/2/7, `config-yaml` 1/0/0, `config-json` 0/0/0).
- `mediatype`'s 0.04 ratio and 31% explained coverage are both artifacts: 12,332 of its 12,676 source lines are the generated `MediaTypes.scala` lookup table, and the 11 unexplained types are generated media-type groupings. Its hand-written surface is ~340 lines and is adequately covered.
- `maybe`, `htmx`, `codegen`, and `sql` have ratios above 1.0 — more documentation than implementation. They are the model to aim for.
- `async`'s low coverage is mostly benign: the unnamed types are CPS-transform internals (see *Deliberately Undocumented*). Its 0.20 ratio is the real signal.
- Five modules are fully covered at the type level: `ringbuffer`, `schema-avro`, `schema-messagepack`, `schema-thrift`, `sql-zio`.

---

## Critical Gaps

Type names below are **unexplained**: no prose reference, no heading. Names marked ✗ are **absent** — they never appear in `docs/` at all, not even inside an example.

### 1. `config` — RESOLVED

Was the worst gap in the repository: one 158-line page covering `config`, `config-yaml`, `config-json`, and `config-hocon` (3,914 source lines combined), with 53 of 77 public types unexplained and 43 absent.

`docs/reference/config.md` is now `docs/reference/config/`, seven pages totalling 2,212 lines with every code block mdoc-verified:

| Page                | Covers                                                                                     |
| ------------------- | ------------------------------------------------------------------------------------------ |
| `index.md`          | Module narrative, installation, data flow, the four `Config` entry points, integration points |
| `config-source.md`  | `ConfigSource`, `MapSource`, `EnvSource`, `SysPropSource`, composition, `KeyMapper`, `KeyFormat`, `SourceValue`, `Provenance`, `ProvenanceMap`, `Secret`, `Displayable` |
| `config-decoder.md` | `ConfigDecoder`, `ConfigDecoderDeriver`, one mapping rule per schema shape, the primitive parsing table, discriminators, error accumulation |
| `errors.md`         | `ConfigError` and its four category traits, every constructor, `ConfigLoadException`        |
| `flags.md`          | `FlagSource`, `Registry`, `StaticFlag`, `DynamicFlag`, `Flag.Reader`, `Flag.Source`, `FlagException`, `Flag.dump` |
| `rollout.md`        | Rollout grammar, `Choice`/`Selector`/`Segment`, bucketing, `Flag.ReloadResult`, `UpdateRecord`, counters |
| `formats.md`        | YAML, JSON, and HOCON adapters, flattening rules, substitutions, includes, `HoconValue`, JVM file loading |

The family is now at 72% explained coverage with 3 absent types. Writing the pages required adding the four config modules to the `docs` project in `build.sbt` — they were absent from its classpath, which is why no config code block had ever been compiled.

Three behaviours that the source made non-obvious and the new pages now state explicitly: a rollout selector must match a path's segment count **exactly** (the bucketing key is itself the first segment); flag durations use a `30s` suffix grammar while config durations require ISO-8601 `PT30S`; and a flattened `null` is indistinguishable from an absent key, so it cannot be used to unset a lower-priority layer.

What remains, all minor:

- [ ] Name `ConfigSourceHoconSyntax` and `ConfigSourceHoconPlatformSyntax` in `formats.md`, or make them private — the mechanism is described but the traits are not named
- [ ] `DisplayableLowPriority` is an implicit-priority helper and is deliberately skipped; consider tightening its visibility
- [ ] `ConfigError.DuplicateKey` and `ConfigError.Unauthorized` are documented as unused by the module; decide whether they should exist at all
- [ ] `ConfigValidationError` is sealed with zero implementations, so matching on it can never match; either give it a constructor or remove it

### 2. `http-model` — RESOLVED

`Header.scala` is 1,861 lines defining 76 header types, of which 75 are typed built-ins. The `## Headers` section of `model.md` was 36 lines showing only the untyped `String` API; 101 of the module's 191 public types were absent.

Two new pages, 956 lines together, with every code block mdoc-verified:

| Page                   | Covers                                                                                     |
| ---------------------- | ------------------------------------------------------------------------------------------ |
| `headers.md`           | `Header`, `Header.Codec`, `Header.Typed`, `Header.Custom`, all 75 built-ins catalogued in ten groups with wire names and ADT variants, the six read methods, the parse cache, write operations, `HeadersBuilder`, validation and injection safety, writing a custom codec |
| `server-sent-event.md` | `ServerSentEvent`, its constructors and metadata builders, validation, render order, `SseDataEncoder` and its instances, custom encoders |

The `## Headers` section of `model.md` was rewritten to cover the collection itself — creation, raw reads, append versus replace — and now links out for the typed model rather than omitting it. The module is at 55% explained coverage with 6 absent types.

Three behaviours the source made non-obvious, now stated with worked output:

- **`Headers#get` discards parse errors.** A malformed header is indistinguishable from an absent one, and a malformed entry followed by a well-formed one silently yields the latter. No collection read surfaces the error.
- **The parse cache is keyed by codec identity, compared by reference.** A codec constructed inline per request never reuses its cached values, and `Headers#add` drops the cache entirely.
- **`Headers#toString` prints credentials verbatim.** There is no redaction, so anything logging a `Request` logs its `authorization` and `cookie` values.

Writing the catalog also surfaced a genuine defect, documented in a warning admonition and worth fixing in the source:

- [ ] `Header.AcceptEncoding.parseSingle` ends with `case _ => GZip(weight)` (`Header.scala:1252`), so any unrecognized encoding name silently parses as `GZip` — `accept-encoding: bogus` reads as a gzip request. Its `parse` fails only on an empty value. The sibling ADTs handle the same situation correctly: `Authorization` has an `Unparsed` case and `Connection` has `Other`. `AcceptEncoding` should either gain an equivalent case or return `Left`.

What remains is the report's own items 4 and 5 rather than anything new:

- [ ] Document `PercentEncoder`, `QueryKey`, `QueryValue`, and `QueryParamsBuilder` in the URL and query sections of `model.md`
- [ ] Document `ComponentType` and `UserInfo`

### 3. `http-model-schema` — the codec layer is invisible

`schema.md` (607 lines) documents the extension-class surface (`QueryParamsSchemaOps`, `HeadersSchemaOps`, `RequestSchemaOps`, `ResponseSchemaOps`) but none of the machinery underneath, so there is no way to learn how to extend it. 14 of 18 public types are absent: `HeaderCodec` ✗, `QueryCodec` ✗, `HeaderCodecDeriver` ✗ (236 lines), `QueryCodecDeriver` ✗ (217 lines), `HeaderFormat` ✗, `QueryFormat` ✗, `DefaultHeaderFormat` ✗, `DefaultQueryFormat` ✗, `FieldCodec` ✗, `DecodeErrorFactory` ✗, `SinglePrimitive` ✗, `OptionalValue` ✗, `SequenceValue` ✗, `WrappedValue` ✗. `ParamCodecSupport` (264 lines) is also absent.

Actions:

- [ ] Add a *How extraction works* section covering `HeaderCodec` / `QueryCodec` and their derivers
- [ ] Document `HeaderFormat` / `QueryFormat` and the `Default*Format` instances — the naming and multi-value rules
- [ ] Document the shape classification (`SinglePrimitive`, `OptionalValue`, `SequenceValue`, `WrappedValue`) so users can predict how a case class maps to headers or query params
- [ ] Document `DecodeErrorFactory` for custom error messages

### 4. `otel` — RESOLVED, and this item was mis-scoped

The original entry called for splitting the 162-line page into four, including an `otlp-exporters.md`. That was wrong, and the reason is worth recording because it applies to other rows in the table.

`OtlpJsonTraceExporter`, `OtlpJsonLogExporter`, `OtlpJsonMetricExporter`, `BatchProcessor`, `OtlpJsonExporter`, and `JdkHttpSender` are all `private[otel]` — six of the module's eighteen declarations. An `otlp-exporters.md` page would have documented internals. The 0.12 ratio that flagged this row is misleading for the same reason `mediatype`'s 0.04 is: roughly 60% of the module's lines are not public surface, and the existing page already covered six of the ten public types well, including a paragraph explaining that the exporters are unreachable.

The real gap was four public types and one missing recipe:

- `OtlpJsonEncoder` (527 lines) and `NamedMetric` — the only public way to produce OTLP payloads, and therefore the answer to the dead end the old page described rather than resolved
- `ExportResult` and its `fromHttpResponse` classification
- `OtelContext`, which bridges `ContextStorage` with `Context[R]`

Resolved by adding `custom-exporter.md` (210 lines) covering the encoder, the encoding rules, `ExportResult`, and a worked flush function that assembles the public pieces into a working exporter; and by extending `index.md` with an `OtelContext` section, the `HttpResponse` shape, and a replacement for the dead-end paragraph. The module is now at 100% — 0 absent, 0 unexplained, all 12 public types documented.

Two findings from writing it:

- **`MetricData` carries no name.** `MetricReader#collectAllMetrics` returns `Seq[MetricData]` and `OtlpJsonEncoder.encodeMetrics` needs `Seq[NamedMetric]`, but nothing public recovers which instrument produced which element. Documented as a warning; worth an API fix.
- **`ExporterConfig`'s three sizing fields have no public reader.** `maxQueueSize`, `maxBatchSize`, and `flushIntervalMillis` are only consumed by the private `BatchProcessor`, so a hand-rolled exporter must implement queueing, chunking, and interval flushing itself. The new page lists what that means.

**Lesson for the remaining rows:** check the public/private split before trusting a low ratio. Rows where most lines may be internal should be verified the same way before being scoped as multi-page splits.

### 5. `schema` — 268 unexplained types clustered in seven subsystems

At 84,969 source lines and 23,842 documentation lines, `schema` is the best-documented module in absolute terms and still holds the largest absolute gap. The unexplained types are not scattered; they cluster.

**`Into` conversions** — the entire primitive conversion matrix is absent: `ByteToInt` ✗, `ByteToLong` ✗, `ByteToShort` ✗, `ByteToFloat` ✗, `ByteToDouble` ✗, `ByteToString` ✗, `IntToByte` ✗, `IntToChar` ✗, `IntToShort` ✗, `IntToLong` ✗, `IntToFloat` ✗, `IntToDouble` ✗, `IntToString` ✗, `LongTo*` ✗, `ShortTo*` ✗, `FloatTo*` ✗, `DoubleTo*` ✗, `CharToInt` ✗, `CharToString` ✗, `BooleanToString` ✗, `StringToBoolean` ✗, `StringToByte` ✗, `StringToShort` ✗, `StringToInt` ✗, `StringToLong` ✗, `StringToFloat` ✗, `StringToDouble` ✗, plus `ConversionType` ✗ and `DynamicConversionError` ✗.
- [ ] Add a conversion-matrix table — which conversions exist, which are lossy, which can fail and how

**`SchemaExpr` operators** — `BitwiseOperator` ✗, `LeftShift` ✗, `RightShift` ✗, `UnsignedRightShift` ✗, `Xor` ✗, `Pow` ✗, `Modulo` ✗, `IsIntegral` ✗, `NumericPrimitiveType` ✗, plus `Divide` and `NumericTypeTag` unexplained.
- [ ] Add an operator reference to `schema-expr.md`, including which operators require `IsIntegral` vs `IsNumeric`

**Migration** — `migration.md` and `schema-evolution/` exist, but the error model does not appear: `MigrationError` ✗, `MigrationErrorKind` ✗, `MissingDefault` ✗, `MandateFailed` ✗, `TransformFailed` ✗, `FieldName` ✗, `MigrationSelectorSyntax` ✗, plus `MigrationBuilderSyntax` (854 lines) unexplained.
- [ ] Document the migration error ADT and what each failure means for a migration run
- [ ] Document the selector syntax surface used to target fields

**Patch operations** — `DynamicPatchOp` ✗, `MapEdit` ✗, `MapOp` ✗, `SeqOp` ✗, `SequenceEdit` ✗, `BigIntDelta` ✗, `ForInstant` ✗, `ForLocalDate` ✗, `ForPeriod` ✗.
- [ ] Document the patch operation ADT, the map/sequence edit encodings, and the temporal delta types

**Search, traversal, and transformation.** **Done for the search/update half:** `reference/schema/schema-search.md` (251 lines, mdoc-verified) now documents `SchemaMatch`'s structural matching rules, `SearchTraversal`'s `fold`/`modify`/`modifyOption`/`modifyOrFail`/`check` and its composition with other optics, and `Reflect.Updater`/`Term.Updater` (including how `Term.Updater` deletes a field/case by returning `None`) — `TypeSearch`/`SchemaSearch` themselves were already covered by `dynamic-optic.md`'s `## Search Optics` section, which now cross-links to the new page. `SchemaRepr` is explained incidentally as the pattern language `SchemaMatch` matches against, but its own dedicated reference (every constructor, `render`, the derived `Schema[SchemaRepr]`) is still open. Still absent: `Frame` ✗, `ReflectTransformer` ✗ (230 lines), `OnlyMetadata` ✗, `RebindTransformer` (237 lines), `SchemaAspect`, and `SchemaParser` (344 lines).
- [x] Write `reference/schema/schema-search.md` covering `SchemaSearch` / `SchemaMatch` / `TypeSearch` / `SearchTraversal` / `Updater` — **done**: 251 lines, mdoc-verified, wired into `sidebars.js` and cross-linked from `dynamic-optic.md` and `schema.md`
- [ ] Write `reference/schema/reflect-transformer.md` covering `ReflectTransformer` and `RebindTransformer`
- [ ] Document `SchemaAspect` and `SchemaRepr`

**Derivation overrides** — `type-class-derivation.md` never names the override subtypes: `InstanceOverrideByType` ✗, `InstanceOverrideByOptic` ✗, `InstanceOverrideByTypeAndTermName` ✗, `ModifierReflectOverrideByType` ✗, `ModifierReflectOverrideByOptic` ✗, `ModifierTermOverrideByType` ✗, `ModifierTermOverrideByOptic` ✗.
- [ ] Document each override form with the selection rule that distinguishes it

**Optic and rebuild errors** — `CaseNotFound` ✗, `FieldNotFound` ✗, `FieldAlreadyExists` ✗, `PathNotFound` ✗, `TypeMismatch` ✗, `InvalidValue` ✗, `EmptyRecord` ✗, `EmptyVariant` ✗, `RebuildRecord` ✗, `RebuildVariant` ✗, `RebuildSequence` ✗, `RebuildMap` ✗, `RebuildObject` ✗, `RebuildArray` ✗, plus `EmptySequence` unexplained.
- [ ] Add an error-case table to `schema-error.md` and `optics.md`

**JSON Schema and refinements** — `Anchor` ✗, `UriReference` ✗, `EvaluationResult` ✗, `JsonMatch` ✗ (153 lines), `FieldInfo` ✗, plus `ValidationOptions`, `RegexPattern`, `NonBlank`, `NonNegative`, `NonNegativeInt`, `Positive`, `PositiveNumber`, `Negative`, `NonPositive` unexplained.
- [ ] Document `$anchor` / `$ref` handling (`Anchor`, `UriReference`) and `ValidationOptions` in `built-in-codecs/json/json-schema.md`
- [ ] Document the refinement types — they appear in public signatures

**`comptime` grammar** — `GrammarNode` ✗, `GRecord` ✗, `GUnion` ✗, `GMap` ✗, `GOptional` ✗, `GPrimitive` ✗, `GSequence` ✗, `GSeqList` ✗, `GSeqVector` ✗, `GSeqSet` ✗, `GSeqChunk` ✗, `GSeqArray` ✗, `GDynamic` ✗, `GIsType` ✗, `GSelf` ✗, `GWrapped` ✗ back the `Allows` mechanism documented in `allows.md`.
- [ ] Decide whether the grammar ADT is public; if yes, document it in `allows.md`; if not, mark it `private[schema]`

Also absent: `DocsSchemas` (1,327 lines) and `DerivedOptics` (581 lines) — check whether either is meant to be public.

### 6. `telemetry` — the value ADT and the log-emitter layer

58 unexplained types across two clusters.

- **`AnyValue` / attribute types**: `BoolValue` ✗, `IntValue` ✗, `ArrayValue` ✗, `StringSeqValue` ✗, `LongSeqValue` ✗, `DoubleSeqValue` ✗, `BooleanSeqValue` ✗, `StringSeqType` ✗, `LongSeqType` ✗, `DoubleSeqType` ✗, `BooleanSeqType` ✗, `StringStringKV` ✗, `StringLongKV` ✗, `StringIntKV` ✗, `StringDoubleKV` ✗, `StringBooleanKV` ✗, plus `StringValue`, `BooleanType`, `LongType`, `DoubleType`, `StringType` unexplained
- **Signal detail**: `LogState` ✗, `SourceLocation` ✗, `Templated` ✗, `AttributesKind` ✗, `EnrichmentKind` ✗, `FallbackKind` ✗, `SeverityKind` ✗, `StringBodyKind` ✗, `ThrowableKind` ✗, plus `SpanEvent`, `SpanLink`, `Measurement`, `GaugeDataPoint`, `HistogramDataPoint`, `SamplingDecision`, `LogMessage`, `LogRecordBuilder` unexplained, and the `Severity` numbered variants (`Trace2`–`Trace4`, `Debug2`–`Debug4`, `Info2`–`Info4`, `Warn2`–`Warn4`, `Error2`–`Error4`, `Fatal2`–`Fatal4`) unexplained

Absent implementation types with a public entry point: `LogEmitter` ✗ (101 lines), `FormattedLogEmitter` ✗, `FileLogWriter` ✗ (163 lines), `StdoutLogRecordProcessor` ✗ (134 lines), `SyncInstruments` ✗.

Actions:

- [ ] Write `reference/telemetry/common/any-value.md` — the typed attribute value ADT and the `KV` shortcuts
- [ ] Add `SpanEvent` and `SpanLink` sections to `tracing/span.md`
- [ ] Add `Measurement`, `GaugeDataPoint`, `HistogramDataPoint` to `metrics/metric-data.md`
- [ ] Add `SamplingDecision` to `tracing/sampler.md`
- [ ] Write `reference/telemetry/logging/log-emitter.md` — `LogEmitter`, `FormattedLogEmitter`, `FileLogWriter`, `StdoutLogRecordProcessor`
- [ ] Document the full `Severity` scale, including the numbered sub-levels
- [ ] Document the log-record `*Kind` classifiers or make them private

### 7. `endpoint` — combinators and segment shortcuts

18 unexplained types: `Alternator` ✗, `CanCombine` ✗, `PathVarsCombiner` ✗, `RoutePathVarsCombiner` ✗, `SegmentCodecOps` ✗, `SinglePathVarPathCodecOps` ✗, `WithStatus` ✗, `ErrorBuilder` ✗, `EndpointUnionErrorBuilder` ✗, `IntSeg` ✗, `LongSeg` ✗, `BoolSeg` ✗, `StringSeg` ✗, `UUIDSeg` ✗, plus `Ignored`, `PathVar`, and `PathCodecRuntime` unexplained.

`Alternator` and `CanCombine` are the type-level machinery that decides what `++` and `|` produce — without them the combinator signatures in `endpoint.md` and `http-codec.md` cannot be read.

Actions:

- [ ] Add a *Type-level combination* section covering `Alternator` and `CanCombine` with the resulting-type rules
- [ ] Document `PathVarsCombiner` / `RoutePathVarsCombiner` and how path variables accumulate into a tuple
- [ ] Document the `*Seg` shortcuts in `segment-codec.md`
- [ ] Document `WithStatus`, `ErrorBuilder`, and `EndpointUnionErrorBuilder` in the error section of `endpoint.md`

### 8. `htmx` — response headers

**Done.** The attribute DSL was well covered (2,521 doc lines, ratio 1.63), but the header side — `HtmxHeaders` (334 lines) and its 22 request/response header types — was absent. `reference/htmx/response-headers.md` (229 lines, mdoc-verified) now covers both directions: the request headers HTMX sends (`HxRequest`, `HxBoosted`, `HxCurrentUrl`, `HxTargetId`, `HxTriggerId`, `HxTriggerName`, `HxHistoryRestoreRequest`, `HxPrompt`), the response headers a handler sets (`HxLocation`, `HxPushUrl`, `HxReplaceUrl`, `HxRedirect`, `HxRefresh`, `HxReswap`, `HxRetarget`, `HxReselect`, `HxTriggerHeader`, `HxTriggerAfterSettle`, `HxTriggerAfterSwap`, `HxEventPayload`), and how each reuses `HxSwap`/`HxTarget`/`HxUrlUpdate`/`CssSelector` from the attribute DSL. `HxTriggerValue`, `HxOnKey`, `PartialHxOn`, `Changed`, and `Threshold` from the original absent-types list are attribute-DSL types (not headers) and remain covered by `hx-trigger.md`/`attribute-values.md`. The internal `HtmxHeaderSupport` parsing helper is `private[headers]` and intentionally left undocumented as an implementation detail, not a public integration point.

Actions:

- [x] Write `reference/htmx/response-headers.md` — **done**: 229 lines, mdoc-verified, wired into `sidebars.js` and cross-linked from `reference/htmx/index.md`

### 9. `smithy` — 32 unexplained types, mostly the shape catalog

`smithy.md` covers parsing, querying, and building, but the shape ADT is incomplete. Absent: `BlobShape` ✗, `DocumentShape` ✗, `TimestampShape` ✗, `EnumShape` ✗, `EnumMember` ✗, `IntEnumShape` ✗, `IntEnumMember` ✗, `ResourceShape` ✗, `ShapeRef` ✗, `ByteShape` ✗, `ShortShape` ✗, `LongShape` ✗, `FloatShape` ✗, `DoubleShape` ✗, `BigIntegerShape` ✗, `BigDecimalShape` ✗. Unexplained but present in examples: `StringShape`, `StructureShape`, `ListShape`, `MapShape`, `UnionShape`, `OperationShape`, `ServiceShape`, `BooleanShape`, `IntegerShape`, `ShapeDefinition`, `ShapeId`, `MemberDefinition`, `NodeValue`, `ApplyStatement`, `SmithyModel`.

Actions:

- [ ] Complete the shape catalog in the *Core Types* section — every `ShapeDefinition` subtype, in prose not just examples
- [ ] Document `EnumShape` / `IntEnumShape` and their member types
- [ ] Document `ResourceShape` and `ShapeRef` resolution

### 10. `streams` — the I/O adapter surface

`sink.md` documents `NioSinks`, but the reader side and the queue primitives do not appear: `NioReaders` ✗, `NioWriters` ✗, `SinkError` ✗, `StreamState` ✗, `OpTag` ✗, `BlockingSpscQueue` ✗, `BlockingMpscQueue` ✗, `BlockingMpmcQueue` ✗, plus `ByteBufferReader` (554 lines), `ChannelReader`, and `ChannelWriter` unexplained.

Actions:

- [ ] Add a *JVM NIO Readers* section to `reader.md` mirroring the NIO section in `sink.md` (`NioReaders`, `ByteBufferReader`, `ChannelReader`)
- [ ] Add `NioWriters` / `ChannelWriter` to `writer.md`
- [ ] Document `SinkError`
- [ ] State the sentinel-based EOF design in `reader.md` — it is enforced in review (`AGENTS.md`, *Sentinel performance policy*) but never explained to users

### 11. `typeid` — `Member` subtypes and segment kinds

44 unexplained types. Absent: `Def` ✗, `Val` ✗, `Param` ✗, `TypeMember` ✗, `EnumCaseParam` ✗, `TupleElement` ✗, `PkgSegment` ✗, `TermSegment` ✗, `TypeSegment` ✗, `SegmentInfo` ✗, plus `TypeIdOps` ✗ (333 lines). Unexplained but present in examples: `TypeBounds`, `ThisType`, `TypeProjection`, `TypeSelect`, `ParamRef`, `Repeated`, `ByName`, `Annotated`, `ArrayArg`, `ClassOf`, `EnumValue`, `Covariant`, `Contravariant`, `Invariant`, and the `*Const` literal types.

Actions:

- [ ] Document the `Member` ADT (`Def`, `Val`, `Param`, `TypeMember`, `EnumCaseParam`) in `typeid.md`
- [ ] Document `Owner` segments (`PkgSegment`, `TermSegment`, `TypeSegment`) and `SegmentInfo`
- [ ] Document `TypeBounds` and `TupleElement`
- [ ] Document the `TypeIdOps` extension surface
- [ ] Add a `TypeRepr` pattern-matching reference covering the variance and literal variants

### 12. Smaller module gaps

- **`html`** (54 unexplained) — the CSS/DOM ADT node types: `PseudoClass` ✗, `PseudoElement` ✗, `Descendant` ✗, `AdjacentSibling` ✗, `GeneralSibling` ✗, `AttributeMatch` ✗, `StartsWith` ✗, `EndsWith` ✗, `WhitespaceContains` ✗, `Rgb` ✗, `Rgba` ✗, `Hsl` ✗, `AddAttr` ✗, `AddChild` ✗, `AddChildren` ✗, `AddEffects` ✗, `DomModifier` ✗, `ToDom` ✗, `ToText` ✗, `AttrValue` ✗, `StyleArg` ✗, `ScriptArg` ✗, `HtmlElements` ✗ (335 lines). The behaviour is documented through the DSL, so this is a type-naming gap, not a conceptual one. **Lower priority.**
  - [ ] Add an ADT reference section naming the selector, colour, and modifier types behind the DSL
- **`datastar`** (28 unexplained) — same shape: `EventModifier` ✗, `CaseModifier` ✗, `InitModifier` ✗, `IntersectModifier` ✗, `OnIntervalModifier` ✗, `OnSignalPatchModifier` ✗, `DataOn` ✗, `PatchSignals` ✗, `PatchElements` ✗, `DatastarAttributes` ✗, `DatastarAttrKey` ✗, `ToDatastarExpr` ✗, `DataSignalsBuilder` ✗, `EventType` ✗. The 0.18 ratio is the bigger problem: 346 lines for 1,881 source lines.
  - [ ] Expand `datastar.md` — each SSE event type needs a worked example; add an attribute-DSL type reference
- **`schema-xml`** (15 unexplained) — `XmlCodecError` ✗, `XmlWriter` ✗, `XmlCodecDeriver` ✗ (657 lines), `SetAttribute` ✗, `RemoveAttribute` ✗, `ElementBuilder` ✗
  - [ ] Add error-handling and deriver/customization sections to `built-in-codecs/xml.md`
- **`schema-yaml`** (8 unexplained) — `YamlCodecError` ✗, `YamlTag` ✗, `YamlSyntax` ✗, `YamlStringContext` ✗; 552 doc lines for 2,753 source lines
  - [ ] Document `YamlTag`, the `yaml""` interpolator, and the error type
- **`codegen`** (17 unexplained) — `ParamList` ✗, `ParamListModifier` ✗, `ExtensionBlock` ✗, `NestedType` ✗, `GroupImport` ✗, `RenameImport` ✗, plus `SingleImport`, `WildcardImport`, `SimpleCase`, `ParameterizedCase`, `CompanionObject`, `DefMember`, `ValMember` unexplained despite a 1.44 ratio
  - [ ] Add the import forms and `ExtensionBlock` / `NestedType` to `reference/codegen/`
- **`scope`** (12 unexplained) — `WireInfo` ✗, `WireKind` ✗ in `resource-management/wire.md`; `InStack`, `Destroyed`, `Uninitialized` unexplained
- **`mux`** — `HalfClosedLocal` ✗, `HalfClosedRemote` ✗ stream states
  - [ ] Complete the stream-state lifecycle in `mux.mdx`
- **`context`** — `ContextHas` ✗ (55 lines), `ContextEntries` ✗ (241 lines)
- **`combinators`** — `TuplesLowPriority` ✗, `TuplesLowPriority1` ✗ (implicit-priority helpers; safe to skip)
- **`maybe`** — `MaybeSyntax` ✗, `MaybeValue` ✗, `MaybeWithFilter` ✗, `WithFilter` ✗, but the page already exceeds the source in size; **skip**
- **`openapi`** — `OpenAPIGen` ✗ (32 lines) only
- **`sql`** — `PgCodec` ✗ (169 lines, PostgreSQL type mapping) only
- **`markdown`** — `MdInterpolator` ✗, `MdStringContext` ✗: the `md"..."` interpolator is documented; the runtime types are not. **Skip.**
- **`chunk`**, **`schema-bson`**, **`schema-csv`** — one or two unexplained internals each; effectively complete

---

## Conceptual and Guide Gaps

`docs/guides/` holds 9 files covering `async`, `scope`, `mux`, the SQL query DSL (4 files), `telemetry`, and migration *from* zio-schema. Every other module has reference documentation only.

No guide exists for:

| Module | src LOC | Suggested guide |
|---|---:|---|
| **schema** | 84,969 | Deriving your first schema; encode/decode round trip |
| **streams** | 18,434 | Building a streaming pipeline end to end |
| **http-model** + **endpoint** | 7,517 | Describing and consuming an HTTP API |
| **html** + **htmx** + **datastar** | 8,154 | Building a hypermedia page |
| **typeid** | 6,493 | Reflecting on types at compile time |
| **chunk** | 5,069 | Choosing `Chunk` over `Vector` / `Array` |
| **openapi** + **smithy** | 5,016 | Generating clients from a service description |
| **config** | 3,914 | Loading typed configuration and feature flags |
| **codegen** | 2,100 | Generating Scala sources |

Cross-cutting documents that do not exist:

- [ ] **Getting Started** — add dependencies, define a case class, derive a schema, encode to JSON. `docs/index.md` is a block catalog, not an on-ramp.
- [ ] **Architecture Overview** — module dependency graph, the register-based zero-allocation design, the `Reflect` → `Binding` → `Schema` layering, the `Deriver` pattern
- [ ] **Zero-dependency and cross-platform contract** — what is JVM-only, what is JS-safe, and what the `scala-2` / `scala-3` source splits mean for users
- [ ] **Performance guide** — `Chunk.materialize`, register allocation, derivation caching, the streams sentinel design, and the labeled-instrument allocation trade-off (currently explained only inside `labeled-instruments.md`)
- [ ] **Custom codec how-to** — implementing a `Format` and its `Deriver` end to end

---

## Deliberately Undocumented

These naming patterns are internal by construction. Do not write documentation for them; if any are public by accident, tighten their visibility instead.

| Pattern | Reason | Examples |
|---|---|---|
| `*Macros`, `*MacroOps`, `MacroUtils`, `MacroCore` | Compile-time implementation | `PathMacros`, `SelectorMacros`, `MigrationValidationMacros`, `CommonMacroOps`, `DbCodecOpaqueMacro` |
| `*VersionSpecific`, `*PlatformSpecific`, `Platform*`, `*Compat` | Scala 2/3 and JVM/JS source-split shims | `SchemaVersionSpecific`, `TypeIdPlatformSpecific`, `PlatformConfigSource`, `PlatformMux`, `MaybeCompat` |
| `*LowPriority`, `*LowPriority1` | Implicit-resolution priority helpers | `TuplesLowPriority`, `TypeIdLowPriority`, `PathVarsCombinerLowPriority`, `DisplayableLowPriority` |
| `*Impl`, `*Runtime`, `*CodeGen` | Private implementations behind a public façade | `ScopeImpl`, `PathCodecRuntime`, `InterpolatorRuntime`, `JsonInterpolatorRuntime`, `WireCodeGen` |
| `*StringContext` | Interpolator plumbing; document the interpolator syntax instead | `JsonStringContext`, `YamlStringContext`, `CssStringContext`, `MediaTypeStringContext` |
| Generated primitive-lane readers | Machine-generated specializations of one documented shape | `LongConcurrentMapParReader`, `IntConcurrentMergeReader`, `DoubleConcurrentMapParReader`, and siblings |
| `PathParser` error states | Internal parser states | `EmptyChar`, `InvalidEscape`, `UnexpectedChar`, `UnterminatedString`, `IntegerOverflow`, `MultiCharLiteral` |
| JSON interpolator states | Internal state machine | `TopLevel`, `InString`, `AfterValue`, `ExpectingKey`, `ExpectingColon`, `ExpectingValue` |
| `*Delta` / `*Dummy` in `patch` | Internal patch encodings | `ByteDelta`, `FloatDelta`, `PeriodDelta`, `DurationDummy`, `PeriodDummy` |
| `scope/internal/*` | Error-rendering internals | `Colors`, `DepNode`, `DepStatus`, `ErrorMessages` |
| async CPS internals | Direct-style transform machinery, not user-facing | `AsyncCpsMonad`, `AwaitCall`, `CollectAwaitCall`, `FoldLeftAwaitCall`, `HofAwaitCall`, `TypedHofMap`, `WaitingMarker`, `NullCauseMarker`, `WithFilterChain`, `PartialFunctionLiteral`, `SingleArgFunction`, `TwoArgFunction`, `AsyncDcaTransform`, `AsyncRunner`, `Parker` |
| `private[...]` parsers and printers | Not part of the public surface | `SmithyParser`, `SmithyPrinter`, `HoconParser`, `SchemaParser`, `ReflectPrinter`, `TypeIdPrinter` |

---

## Prioritized Action List

Ordered by user impact per unit of writing effort.

**Tier 1 — new pages for missing subsystems**

1. - [x] `reference/http-model/headers.md` — **done**: 660 lines cataloguing all 75 built-ins, mdoc-verified
2. - [x] Split `reference/config.md` into `reference/config/` — **done**: seven pages, 2,212 lines, mdoc-verified; family went from 31% to 72% explained coverage
3. - [x] ~~Split `reference/telemetry/otel/` into four pages~~ — **done differently**: the four-page split was mis-scoped (the exporters are `private[otel]`); resolved with one new page plus index additions, module now at 100%
4. - [x] `reference/htmx/response-headers.md` — **done**: 229 lines, mdoc-verified
5. - [x] `reference/schema/schema-search.md` (`SchemaSearch`, `SchemaMatch`, `TypeSearch`, `SearchTraversal`, `Updater`) — **done**: 251 lines, mdoc-verified
6. - [ ] `reference/telemetry/common/any-value.md`
7. - [x] `reference/http-model/server-sent-event.md` — **done**: 296 lines
8. - [ ] `reference/schema/reflect-transformer.md`
9. - [ ] `reference/telemetry/logging/log-emitter.md`

**Tier 2 — sections in existing pages**

10. - [ ] `Into` conversion matrix (schema)
11. - [ ] `SchemaExpr` operator reference (`schema-expr.md`)
12. - [ ] Migration error ADT (`migration.md`)
13. - [ ] Patch operation ADT (`patch.md`)
14. - [ ] Derivation overrides (`type-class-derivation.md`)
15. - [ ] Codec layer in `http-model/schema.md` (`HeaderCodec`, `QueryCodec`, formats, derivers)
16. - [ ] `Alternator` / `CanCombine` type-level rules (`endpoint/`)
17. - [ ] Complete the Smithy shape catalog (`smithy.md`)
18. - [ ] `SpanEvent` / `SpanLink` / `SamplingDecision` / data points (`telemetry/`)
19. - [ ] NIO readers and writers (`streams/reader.md`, `streams/writer.md`)
20. - [ ] `Member` ADT and owner segments (`typeid.md`)
21. - [ ] XML and YAML error types and derivers (`built-in-codecs/`)
22. - [ ] Optic and rebuild error tables (`schema-error.md`, `optics.md`)
23. - [ ] JSON Schema `$anchor` / `$ref` and the refinement types
24. - [ ] Codegen import forms, `ExtensionBlock`, `NestedType`

**Tier 3 — depth on thin pages**

25. - [ ] Expand `datastar.md` (ratio 0.18)
26. - [ ] Expand `async.md` (ratio 0.20) — the user-facing direct-style surface, not the CPS internals
27. - [ ] Expand `smithy.md` (ratio 0.21)
28. - [ ] Expand `html.md` (ratio 0.24) with the ADT reference

**Tier 4 — conceptual documents**

29. - [ ] Getting Started
30. - [ ] Architecture Overview
31. - [ ] Zero-dependency / cross-platform contract
32. - [ ] Performance guide
33. - [ ] Guides for `schema`, `streams`, `http-model` + `endpoint`, hypermedia, `config`

**Visibility cleanups (instead of documentation)**

34. - [ ] Decide the public status of the `comptime` `G*` grammar ADT, `DocsSchemas`, `DerivedOptics`, `ContextEntries`, `HtmxHeaders`, and the telemetry log-record `*Kind` classifiers; tighten visibility where they are not public API

---

## Methodology

Reproducible with the script below. It extracts non-`private` type declarations from every module's `src/main` sources, then diffs them against the identifier set found in `docs/`. Two doc sets are built: every identifier anywhere in `docs/` (yields *absent*), and only identifiers in inline code or headings (yields *unexplained*).

```bash
#!/usr/bin/env bash
# Documentation coverage scan for zio-blocks.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
OUT="${1:-/tmp/doc-scan}"; mkdir -p "$OUT"

MODULES="async chunk codegen combinators config config-hocon config-json config-yaml
context datastar endpoint html htmx http-model http-model-schema markdown maybe
mediatype mux openapi otel ringbuffer schema schema-avro schema-bson schema-csv
schema-messagepack schema-thrift schema-toon schema-xml schema-yaml scope smithy
sql sql-zio streams telemetry typeid"

# Every identifier that appears anywhere in the docs tree.
grep -rhoE '[A-Za-z_][A-Za-z0-9_]*' docs/ \
  --include='*.md' --include='*.mdx' --include='*.jsx' | sort -u > "$OUT/docwords.txt"

# Stricter set: only names in inline code or in headings.
{ grep -rhoE '`[A-Za-z_][A-Za-z0-9_]*`' docs/ | tr -d '`'
  grep -rhoE '^#+ .*' docs/ | grep -oE '[A-Za-z_][A-Za-z0-9_]*'; } | sort -u > "$OUT/documented.txt"

for m in $MODULES; do
  [ -d "$m" ] || continue
  find "$m" -path '*/src/main/*' -name '*.scala' -print0 \
    | xargs -0 grep -hE '^[[:space:]]*(final |sealed |abstract |implicit |case |transparent |inline )*(class|trait|object|enum)[[:space:]]+[A-Za-z_]' \
    | grep -vE '\bprivate\b|\bprotected\b' \
    | sed -E 's/.*(class|trait|object|enum)[[:space:]]+([A-Za-z_][A-Za-z0-9_]*).*/\2/' \
    | sort -u > "$OUT/types-$m.txt"
  comm -23 "$OUT/types-$m.txt" "$OUT/docwords.txt"   > "$OUT/absent-$m.txt"
  comm -23 "$OUT/types-$m.txt" "$OUT/documented.txt" > "$OUT/unexplained-$m.txt"
  printf '%s\t%s\t%s\t%s\n' "$m" \
    "$(wc -l < "$OUT/types-$m.txt")" \
    "$(wc -l < "$OUT/absent-$m.txt")" \
    "$(wc -l < "$OUT/unexplained-$m.txt")"
done | sort -t$'\t' -k3,3rn
```

A second pass catches types the declaration scan misses because of a visibility modifier but which are still absent from the docs, keyed on file name:

```bash
for m in $MODULES; do
  find "$m" -path '*/src/main/*' -name '*.scala' | while read -r f; do
    b=$(basename "$f" .scala)
    case "$b" in package|*Macros|*PlatformSpecific|*VersionSpecific|*Compat|*LowPriority) continue;; esac
    grep -qxF "$b" "$OUT/docwords.txt" || printf '%s\t%s\t%s\n' "$m" "$b" "$(wc -l < "$f")"
  done
done
```

Known limitations:

- Matching is name-based, so a type whose name collides with an ordinary English word (`Default`, `Private`, `Public`, `Wildcard`, `Flag`, `Origin`, `Date`, `Host`) can be scored as covered when the page never discusses it. Both gap counts are therefore lower bounds.
- Nested types are counted individually, which inflates ADT-heavy modules such as `http-model` and `schema`. This is intentional: each variant is something a user can pattern match on.
- The `unexplained` column under-counts on well-written pages. The writing-style rules require method references to be qualified (`ConfigSource#orElse`, `Flag.Reader.scalar`), and nested types read naturally as `Provenance.Resolved` or `KeyFormat.KebabCase` — none of which the bare-name match sees. A page that follows the style guide will therefore show unexplained types it actually explains.
- Method-level coverage is not measured. The `ratio` column (doc LOC / src LOC) is the proxy used instead.
- The `ratio` column is meaningless for modules dominated by generated code — `mediatype` is the clearest case.

---

*Report regenerated 2026-08-21 against commit `a33dc448`. 1,828 public types scanned across 38 modules and 164 reference pages.*
