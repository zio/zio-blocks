# ZIO Blocks: research for a tutorials and how-to guides backlog

Repository snapshot: branch `tutorials-and-guides-lists`, HEAD `b5f9fb71`, 2026-10-19.

**Purpose.** This file is input for building a backlog of candidate **tutorials** and **how-to guides** for the documentation site. Every fact is taken from the code, the build or the docs, and cites a path. Anything not confirmed in source is marked *unverified*.

**Definitions (Diátaxis).**
- **Tutorial:** a learning-oriented lesson that takes a newcomer from nothing to a working result.
- **How-to guide:** a goal-oriented recipe for someone who already knows the basics.

**Sources of truth used.**
- Build: `build.sbt` and `project/BuildHelper.scala`.
- Module sources and tests.
- The 11 `*-examples/` directories.
- Docs: `docs/reference/**`, `docs/guides/**`, `docs/sidebars.js`, `docs/index.md`, `docs/undocumented-report.md`, `docs/adr/`, `docs/plans/`.
- `git log` for maturity signals.

## Table of contents

1. [Orientation](#1-orientation)
   - 1.1 Module catalog at a glance
   - 1.2 Build and publishing facts every guide must respect
   - 1.3 How docs and examples are wired today
2. [Foundation data types](#2-foundation-data-types): chunk, maybe, typeid, markdown
3. [Schema core](#3-schema-core-zio-blocks-schema)
4. [Schema formats and schema-adjacent tooling](#4-schema-formats-and-schema-adjacent-tooling): avro, bson, csv, messagepack, thrift, toon, xml, yaml, openapi, codegen, smithy, scalaNextTests
5. [Configuration, observability, security, media types](#5-configuration-observability-security-media-types): config, config-hocon, config-json, config-yaml, telemetry, otel, jwt, mediatype
6. [Runtime, resources and concurrency](#6-runtime-resources-and-concurrency): scope, context, combinators, async, ringbuffer, mux, streams
7. [Persistence](#7-persistence): sql, sql-zio, dataMigration, projection
8. [HTTP and web UI](#8-http-and-web-ui): http-model, http-model-schema, endpoint, html, datastar, htmx
9. [Cross-cutting analysis](#9-cross-cutting-analysis)
   - 9.1 Module dependency map
   - 9.2 End-to-end scenarios spanning several modules
   - 9.3 Existing guides inventory
   - 9.4 Coverage gaps summary
   - 9.5 Blockers and prerequisites for specific guides
   - 9.6 Doc inaccuracies found during research
   - 9.7 Comparable libraries (index for web research)
10. [Appendix: non-published projects and example builds](#10-appendix-non-published-projects-and-example-builds)

## 1. Orientation

### 1.1 Module catalog at a glance

There are 41 published modules; `config` and its three format adapters count as four. Abbreviations used in the table:

- **Scala:** "all" means 2.13, 3.3.7 and 3.9.0; "3" means 3.3.7 and 3.9.0 only.
- **Guide?:** whether at least one guide in `docs/guides/` is primarily about the module.

| Module | Artifact | Platforms | Scala | zio-blocks deps (`build.sbt`) | Guide? |
|---|---|---|---|---|---|
| chunk | `zio-blocks-chunk` | JVM, JS | all | — | no |
| maybe | `zio-blocks-maybe` | JVM, JS | all | typeid | no |
| typeid | `zio-blocks-typeid` | JVM, JS | all | chunk | no |
| markdown | `zio-blocks-markdown` | JVM, JS | all | chunk | no |
| mediatype | `zio-blocks-mediatype` | JVM, JS | all | — | no |
| combinators | `zio-blocks-combinators` | JVM, JS | all | — | no |
| context | `zio-blocks-context` | JVM, JS | all | typeid | partial (inside the scope guide) |
| scope | `zio-blocks-scope` | JVM, JS | all | context, chunk | **yes** |
| async | `zio-blocks-async` | JVM, JS | all (+3.8.3 Golem cross-test) | combinators | **yes** |
| ringbuffer | `zio-blocks-ringbuffer` | JVM, JS (primitive variants JVM only) | all | — | no |
| mux | `zio-blocks-mux` | JVM, JS | all | ringbuffer (JVM only) | **yes** |
| streams | `zio-blocks-streams` | JVM, JS | all (+3.8.3) | scope, chunk, combinators, ringbuffer, async | no |
| schema | `zio-blocks-schema` | JVM, JS | all | typeid, chunk, markdown, maybe | partial (migration-from-zio-schema; Query DSL series) |
| schema-avro | `zio-blocks-schema-avro` | JVM | all | schema (+ apache avro) | no |
| schema-bson | `zio-blocks-schema-bson` | JVM | all | schema (+ mongodb bson) | no |
| schema-thrift | `zio-blocks-schema-thrift` | JVM | all | schema (+ libthrift) | no |
| schema-csv | `zio-blocks-schema-csv` | JVM, JS | all | schema | no |
| schema-messagepack | `zio-blocks-schema-messagepack` | JVM, JS | all | schema | no |
| schema-toon | `zio-blocks-schema-toon` | JVM, JS | all | schema | no |
| schema-xml | `zio-blocks-schema-xml` | JVM, JS | all | schema | no |
| schema-yaml | `zio-blocks-schema-yaml` | JVM, JS | all | schema, markdown | no |
| openapi | `zio-blocks-openapi` | JVM, JS | all | schema, markdown | no |
| codegen | `zio-blocks-codegen` | JVM | all | — | no |
| smithy | `zio-blocks-smithy` | JVM | all | — | no |
| config | `zio-blocks-config` | JVM, JS | all | schema, scope, maybe | no |
| config-yaml | `zio-blocks-config-yaml` | JVM, JS | all | config, schema-yaml | no |
| config-json | `zio-blocks-config-json` | JVM, JS | all | config, schema | no |
| config-hocon | `zio-blocks-config-hocon` | JVM, JS (file loading JVM only) | all | config | no |
| telemetry | `zio-blocks-telemetry` | JVM, JS (JVM `-release 21`) | all | context, chunk (both declared but not imported) | **yes** (deep dive) |
| otel | `zio-blocks-telemetry-otel` | JVM (`-release 21`) | all | telemetry | partial (telemetry guide, Part 5) |
| jwt | `zio-blocks-jwt` | JVM, JS | all | chunk | no |
| http-model | `zio-blocks-http-model` | JVM, JS | all | chunk, mediatype, streams, maybe | no |
| http-model-schema | `zio-blocks-http-model-schema` | JVM, JS | all | http-model, schema | no |
| endpoint | `zio-blocks-endpoint` | JVM, JS | all (unions need 3; bulk DSL needs 3.7+) | http-model, schema, combinators, mediatype, markdown | no |
| html | `zio-blocks-html` | JVM, JS | all | chunk, schema | no |
| datastar | `zio-blocks-datastar` | JVM, JS | **3** | html, http-model | no |
| htmx | `zio-blocks-http-htmx` | JVM, JS | **3** | html, schema, http-model | no |
| sql | `zio-blocks-sql` | JVM (JS has types only, no backend) | **3** | schema, scope (unused), streams | partial (transactions, checked interpolation) |
| sql-zio | `zio-blocks-sql-zio` | JVM | **3** | sql (+ ZIO 2.1.27) | no |
| dataMigration | `zio-blocks-data-migration` | JVM (JS declared, no sources) | **3** | sql, schema, maybe | no |
| projection | `zio-blocks-projection` | JVM | **3** | schema, sql, scope (+ ZIO, zio-streams) | no |

### 1.2 Build and publishing facts every guide must respect

- **Scala 3.7+ artifact names.** On Scala 3.7+, every module is published as `zio-blocks-next-*` instead of `zio-blocks-*` (`project/BuildHelper.scala:169-176`).
  - Most install snippets show only `zio-blocks-*`. Only `docs/reference/endpoint/index.md` mentions `-next`.
  - Every tutorial's install step must handle this.
- **Third-party dependencies.** "Zero dependency" means *no third-party runtime deps* for most modules. The exceptions are:

  | Module | Runtime dependency |
  |---|---|
  | schema-avro | Apache Avro |
  | schema-thrift | libthrift |
  | schema-bson | MongoDB bson |
  | sql-zio | ZIO |
  | projection | ZIO, zio-streams |

  `sql` needs a JDBC driver supplied by the user. Tests use sqlite-jdbc and the PostgreSQL driver.
- **Scala 3-only modules:** sql, sql-zio, dataMigration, projection, datastar and htmx. A tutorial spanning these modules cannot target Scala 2.13.
- **The mdoc classpath** (`lazy val docs`, `build.sbt:1698-1735`) includes almost every module but **not `sql-zio` or `dataMigration`**.
  - Pages for those two modules cannot have compiled code blocks today.
  - Their existing pages (`sql-zio.md`, `sql/transactor-zio.md`, `data-migration.md`, `guides/sql-transactions.md`) have 0 mdoc blocks.
- **Example projects are pinned.**
  - Every `*-examples/` directory is a standalone sbt build. They pin `zioBlocksVersion = "0.0.56"` and Scala 3.9.0, and use the `zio-blocks-next-*` artifacts.
  - They were made standalone in commit `fc78b4b6`. CI compiles each one (`project/CiWorkflow.scala:359`).
  - **Consequence:** an example can only use APIs that exist in the published release. Example run instructions in several file headers still use the old `sbt "<project>/runMain"` form.
- **Telemetry on the JVM** uses `java.lang.ScopedValue` and virtual threads.
  - The build raises `-release` to 21. The guide says "JDK 25+ specific" (`docs/guides/telemetry-guide.md:1032`).
  - The installation sections do not state the JDK requirement.

### 1.3 How docs and examples are wired today

- **Sidebars.** `docs/sidebars.js` defines two sidebars:
  - `sidebar`: the reference docs, grouped as Meta Programming, Resource Management, Dependency Injection, Configuration & Feature Flags, Web & HTTP, Data Types, Concurrency, Streams, Telemetry, Persistence, and Tooling & Codegen.
  - `guides`: a flat list, plus a "Query DSL" category.
- **Guides index.** `docs/guides/index.md` renders cards from the guides sidebar (`docs/SidebarCards.jsx`), so a new guide only needs a sidebar entry.
  - The navbar "Guides" link points to `/guides/async-getting-started` (`website/docusaurus.config.js:114`).
  - `docs/index.md:492-502` keeps a hand-maintained "Guides" list. It is stale: it lists 9 and omits `sql-transactions` and `sql-checked-interpolation`.
- **Embedding examples.** Example files are embedded with `SourceFile.print("...")` (`zio-blocks-docs/src/main/scala/SourceFile.scala`). Inline code uses `scala mdoc` fences.
- **Coverage report.** `docs/undocumented-report.md` is the repo's own coverage audit.
  - It says "Guides: 9" (stale; there are now 11).
  - Its "Conceptual and Guide Gaps" section (lines 349-375) proposes guides and cross-cutting docs. These are reused in §9.4.
- **ADR.** `docs/adr/` has one ADR, `2026-07-18-data-migration.md`. `telemetry/ContextStorage.scala:31` references `docs/adr/0001-scoped-context-no-set.md`, **which does not exist**.
- **Plans.** `docs/plans/` contains only config follow-up plans (`config-follow-up-prs.md`, `config-pr-assessment-roadmap.md`). They include planned `ConfigSource.fromEnv` and secrets-dir/`_FILE` sources.
- **Orphan page.** `docs/reference/datastar.md` (346 lines) is an orphan from the datastar page split; it is not in the sidebar.

## 2. Foundation data types

### 2.1 `chunk` (`zio-blocks-chunk`)

**Purpose.** `Chunk` is an immutable, indexed sequence specialized for primitives, with lazy balanced concatenation (Conc-trees). The module also provides `NonEmptyChunk`, `ChunkMap` (an insertion-ordered map) and `ChunkBuilder`. It is the most widely used base type in the repo.

**Key types / entry points**
- `Chunk[+A]` (`chunk/shared/src/main/scala/zio/blocks/chunk/Chunk.scala:590`, companion at `:1717`):
  - factories `fromArray`, `fromByteBuffer`, `fromIterable`, `unfold`
  - `materialize`, which flattens a lazy concatenation tree
  - unboxed accessors: `int`, `long`, `byte`, `double`, …
  - bit views: `asBits`, `toPackedByte/Int/Long`
  - `asString`
  - collection operations: `split`, `splitWhere`, `dedupe`, `mapAccum`, `foldWhile`
- `ChunkBuilder` (`Chunk.scala:46`)
- `NonEmptyChunk` (`Chunk.scala:3805`)
- `ChunkMap[K, +V]` (`ChunkMap.scala:42`)
- Platform-specific code lives in `chunk/{jvm,js}/.../Chunk{,Builder}PlatformSpecific.scala`.

**Dependencies.** None. Platforms: JVM + JS; Scala 2.13, 3.3 and 3.9. Main code in nearly every other module uses it:

| Module | Main source files using `chunk` |
|---|---:|
| schema | 62 |
| streams | 22 |
| http-model | 14 |
| html | 14 |
| typeid | 11 |
| schema-xml | 9 |
| markdown | 9 |

`jwt`, `scope`, `sql`, `endpoint`, `openapi` and `projection` use it too.

**Real-world use cases**
- Binary and bit packing (`BitChunk{Byte,Int,Long}Spec`, `ChunkPackedBooleanSpec`, `ChunkBufferSpec`).
- Repeated append, prepend and concat (`benchmarks/src/main/scala/zio/blocks/chunk/*Benchmark.scala`).
- Ordered JSON objects and records via `ChunkMap` and `Schema.chunkMap`.
- Payloads of `DynamicValue`, `Json`, stream elements and HTTP bodies.

**Integration points**
- `Schema[Chunk[A]]` and `Schema.chunkMap` (`schema/shared/src/main/scala/zio/blocks/schema/Schema.scala:323,328`).
- `streams` emits `Chunk`s; `sql` `queryChunked` returns `Stream[Throwable, Chunk[A]]`.
- `http-model` `Body.toChunk`.

**Existing docs coverage**
- `docs/reference/chunk.md` is very thorough (3140 lines, 166 mdoc blocks).
- Its "Running the Examples" section (`chunk.md:3116`) is generic and names no example files.
- No guide exists.
- No interop how-to covers converting to and from `zio.Chunk`, `fs2.Chunk`, `Array` or `ByteBuffer`.
- `zio-schema-migration.md` says NonEmptyChunk schemas are "Not available".

**Examples.** Only `schema-examples/src/main/scala/ChunkExample.scala`, a trivial 10-line example that no doc references.

**Maturity.** Last change 2026-09-11; recent work is performance-focused (#1447, #1292, #1672). 0 TODO. Stable.

**Comparable libraries**
- `zio.Chunk`: nearly identical API, including BitChunk and NonEmptyChunk.
- `fs2.Chunk`.
- `ArraySeq`, `Vector`, `List`, `Array`.
- cats `NonEmptyChain` / `NonEmptyVector`.
- `VectorMap` / `ListMap` (for `ChunkMap`).
- scodec `ByteVector` / `BitVector` (for the byte and bit use cases).

### 2.2 `maybe` (`zio-blocks-maybe`)

**Purpose.** A low-allocation alternative to `Option`:
- **Scala 3:** `opaque type Maybe[+A] = A | Absent.type | Present[A]`. A `Present` wrapper is allocated only for nested or null values.
- **Scala 2.13:** a sealed trait.

**Key types / entry points**
- Scala 3: `maybe/shared/src/main/scala-3/zio/blocks/maybe/Maybe.scala`. Scala 2: `scala-2/.../Maybe.scala`.
- Supporting files: `MaybeSyntax.scala`, `MaybeCompat.scala`.
- Constructors: `Maybe.apply` (null becomes absent), `present`, `absent`, `fromOption`.
- Operations: `map`, `flatMap`, `filter`/`withFilter`, `fold`, `getOrElse`, `orElse`, `zip`, `toOption`, `getOrNull`, `collect`, `flatten`.
- Pattern matching: `case Present(v)` / `case Absent` (`WildcardImportSpec`).

**Dependencies**
- Declares a dependency on `typeid` (`build.sbt:378`), but no import of it was found; the purpose is unverified.
- Used by `schema` (`Schema.maybe*`), `sql` (`Repo#find`, `queryOne`), `config` (every lookup), `http-model` and `datastar`.
- Platforms: JVM + JS; all Scala versions.

**Use cases**
- Hot-path optional lookups (`docs/reference/sql/repo.md:61`, `docs/reference/config/config-source.md:12`).
- Optional modifier state in builders (`datastar/.../InitModifier.scala:50`).
- Optional schema fields; boxing is measured in `benchmarks/.../json/MaybeBoxingBenchmark.scala`.

**Docs**
- `docs/reference/maybe.md` is thorough (943 lines, 48 mdoc blocks), but the "Allocation Profile" section comes before "Motivation" and "Installation".
- No guide.
- **Stale:** `docs/index.md:125` still says "backed by `null`". The null encoding was replaced with `Absent` in #1532.

**Examples.** None.

**Maturity.** Introduced in #1336; encoding redesigned in #1532 (2026-08-25). Young, but small (595 LOC).

**Comparable libraries.** `scala.Option`, Kyo `Maybe`, Java `Optional`.

### 2.3 `typeid` (`zio-blocks-typeid`)

**Purpose.** `TypeId[A <: AnyKind]` is a macro-derived, pure-data type identity. It carries the type's owner, type parameters and arguments, kind, variance, alias and opaque representation, annotations and parents. It needs no runtime reflection and works on Scala 2.13 and 3, JVM and JS.

**Key types / entry points**
- `TypeId` (`typeid/shared/src/main/scala-3/zio/blocks/typeid/TypeId.scala:39`):
  - `TypeId.of[A]`
  - subtype checks: `isSubtypeOf`, `isSupertypeOf`, `isEquivalentTo`
  - type tests: `isOption`, `isEither`, `isMaybe`
  - manual constructors: `nominal`, `alias`, `opaque`, `applied`
  - extractors: `Nominal`, `Alias`, `Opaque`, `Sealed`, `Enum`
  - structural operations: `normalize`, `structurallyEqual`, `structuralHash` (`TypeIdOps.scala`)
  - `TypeId.Erased`
- Supporting types: `Owner`, `TypeRepr`, `TypeParam`, `Kind`, `Variance`, `TypeBounds`, `Member`, `Annotation`.
- `IsNominalType` / `IsNominalIntersection`: compile-time witnesses that a type is concrete.

**Dependencies**
- Depends on `chunk`.
- Used by `schema` (33 files; every `Reflect` node carries a `TypeId`), `context` (registry keys), `maybe`, `projection`, `otel` (`OtelContext` has an `IsNominalType`) and others.

**Use cases**
- Type-keyed registries and caches (`context/shared/src/main/scala/zio/blocks/context/Cache.scala:22-24`).
- Telling opaque types and aliases apart from their underlying types.
- Runtime subtype dispatch.
- Dispatch on singleton types.
- Derivation overrides keyed by `TypeId` (`DerivationBuilder.instance(typeId, …)`).
- Neotype consistency (`NeotypeTypeIdConsistencySpec`).
- Recursive types (`RecursiveTypeIdSpec`).

**Docs**
- `docs/reference/typeid.md` is thorough (2124 lines, 123 mdoc blocks, 4 embedded examples) and includes a "Comparison with Alternatives" section.
- Gaps (`undocumented-report.md:306-315`): the `Member` ADT, Owner segment kinds, the `TypeIdOps` surface.
- No guide. The undocumented report suggests "Reflecting on types at compile time".

**Examples** (`schema-examples/src/main/scala/typeid/`)
- `TypeIdBasicExample`
- `TypeIdSubtypingExample`
- `TypeIdNormalizationExample`
- `OpaqueTypesExample`
- `SingletonTypeTest` (not referenced by any doc)

**Maturity.** Last change 2026-08-14 (Scala 3.10 macro fix, #1584). 0 TODO. Mature.

**Comparable libraries.**
- `ClassTag`, `TypeTag`, Scala 3 `TypeTest` and `Mirror`.
- izumi-reflect `LightTypeTag` and ZIO `Tag`/`EnvironmentTag`: the closest equivalents, not mentioned in the docs.
- shapeless `Typeable`.

### 2.4 `markdown` (`zio-blocks-markdown`, package `zio.blocks.docs`)

**Purpose.** A pure GitHub-Flavored Markdown library. It provides:
- an immutable ADT (`Doc`, `Block`, `Inline`)
- a strict parser
- GFM, HTML and ANSI-terminal renderers
- a `ToMarkdown` type class
- a compile-time-checked `md"..."` interpolator

**Key types / entry points** (`markdown/shared/src/main/scala/zio/blocks/docs/`)
- `Doc(blocks: Chunk[Block], metadata)` with `++`, `normalize`, `toHtml`, `toHtmlFragment`, `toTerminal`.
- `Parser.parse` returns `Either[ParseError, Doc]`.
- `Renderer.render`, `HtmlRenderer.render`, `TerminalRenderer.render`.
- `ToMarkdown[-A]`.
- `md"..."` (`scala-3/.../MdInterpolator.scala`).

**Dependencies**
- Depends on `chunk`.
- Used by `schema` (`Schema#doc` returns `Doc`; `DocsSchemas.scala` gives a `Schema[Doc]`), `endpoint` (endpoint and codec docs), `openapi` (every description field) and `schema-yaml`.
- `Doc` appears in every `Deriver` signature.

**Use cases**
- Attaching docs to schemas, endpoints and OpenAPI objects.
- Rendering README or HTML output from code.
- CLI help in the terminal (`TerminalRendererSpec`).
- GFM round trips (`RoundTripSpec`).
- Serializing `Doc` via a schema (`MarkdownSchemaSpec`).

**Docs**
- `docs/reference/docs.md` is thorough (1539 lines, 71 mdoc blocks).
- The page id and label are "Docs" while the module is `markdown`, which can confuse readers.
- No guide and no examples.

**Maturity.** 8 commits; last change 2026-03-22. Stable and quiet.

**Comparable libraries.** flexmark-java, commonmark-java, Laika, the Pandoc AST. Nothing in common Scala use is equivalent to `md"..."`.

## 3. Schema core (`zio-blocks-schema`)

### 3.1 Purpose

`Schema[A]` wraps `Reflect`, a reified, derivable description of a Scala type's structure. That one value drives:

- codecs: JSON is built in, and Avro, BSON, CSV, MessagePack, Thrift, TOON, XML and YAML live in sibling modules
- optics
- `DynamicValue` conversion
- diff and patch
- `Into`/`As` conversions and `Migration`
- validation
- JSON Schema
- derivation of arbitrary type classes

It is by far the largest module: 85,027 main LOC, 153 specs, about 7,300 tests. It is also the hub most other modules plug into.

### 3.2 Key types / entry points

Paths are relative to `schema/shared/src/main/`.

**Core**

- `Schema[A]` (`scala/zio/blocks/schema/Schema.scala:35`)
  - Derivation: `Schema.derived` / `derives Schema` (`scala-3/.../SchemaCompanionVersionSpecific.scala:29`), `derive(format)`, `deriving(deriver)`.
  - Codecs and conversions: `jsonCodec`, `toDynamicValue`/`fromDynamicValue`, `toDynamicSchema`, `toJsonSchema`, `Schema.fromJsonSchema`.
  - Navigation and change: `get(optic)`, `updated`, `diff`/`patch`.
  - Wrappers and metadata: `transform[B](to, from)` for newtypes and wrappers, `@@ SchemaAspect`, `doc`, `examples`, `defaultValue`, `modifier(s)`.
  - Scala 3 JVM-only: `.structural`.
- `Reflect[F, A]` (`Reflect.scala:29`)
  - Node kinds: Record, Variant, Sequence, Map, Dynamic, Primitive, Wrapper, Deferred.
  - Related: `ReflectTransformer`, `RebindTransformer`, `ReflectPrinter`.
- `Binding` (`binding/Binding.scala`), `BindingResolver` (`Registry.bind`, `++`), and the register machinery: `Constructor`, `Deconstructor`, `Registers`.

**Dynamic values**

- `DynamicValue` (`DynamicValue.scala:54`), plus `DynamicValueSelection` and `DynamicValueMergeStrategy`.
- `DynamicSchema` (`DynamicSchema.scala:73`): `check`, `conforms`, `toSchema`, `rebind[A](resolver)`, `toJsonSchema`. It is serializable, which makes the schema-registry use case possible.

**Optics and expressions**

- Optics (`Optic.scala`): `Lens`, `Prism`, `Optional`, `Traversal`, `SearchTraversal`.
  - Operations: `get`, `replace`, `modify`, `modifyOrFail`, `check`.
  - Macros: `scala-3/.../CompanionOptics.scala` gives `$(_.a.b)`, `.when[B]`, `.each`, `.atKey`, `.searchFor[B]`; `DerivedOptics` exposes `Person.name`.
- `DynamicOptic` and the `p"..."` path interpolator (`scala-3/.../PathInterpolator.scala`), plus schema search (`SchemaRepr`, `SchemaParser`, `SchemaMatch`).
- `SchemaExpr` / `DynamicSchemaExpr` (`SchemaExpr.scala:36`): logical, relational, arithmetic, bitwise and conversion operators. These are the basis of the Query DSL guides.

**Change and evolution**

- `Patch` / `DynamicPatch` (`patch/Patch.scala:49`)
  - Operations: `set`, `increment`, `append`, `insertAt`, `deleteAt`, `addKey`, `editString`, …
  - Supporting: `Differ`, `LCS`, `PatchMode` (Strict, Lenient, Clobber).
- `Into[-A,+B]` / `As[A,B]` (`Into.scala`, `As.scala`), with macro derivation in `scala-3/.../IntoVersionSpecific.scala` (3003 lines). Errors accumulate.
- `Migration[A,B]` (`migration/Migration.scala:52`), `DynamicMigration`, `MigrationAction`
  - Actions: AddField, DropField, RenameField, TransformField, MandateField, OptionalizeField, ChangeFieldType, RenameCase, TransformCase, …
  - Builder: `Migration.newBuilder[A,B]` with selector macros and compile-time completeness validation (`scala-3/.../migration/MigrationBuilder.scala`, `MigrationValidationMacros.scala`).

**Codecs and derivation**

- Codec / Format (`codec/{Codec,BinaryCodec,TextCodec,Format}.scala`): `BinaryFormat` and `TextFormat` each carry a MIME type and a `Deriver`.
- JSON:
  - `JsonFormat`, `JsonCodec` (bytes, String, streams, ByteBuffer; `ReaderConfig`/`WriterConfig`).
  - The `Json` ADT (2688 lines), with `JsonSelection`, `JsonPatch` (RFC 6902-like) and `JsonDiffer`.
  - `JsonSchema` (draft 2020-12) and `JsonSchemaToReflect`.
  - The `json"..."` interpolator, `NameMapper`, `DiscriminatorKind`.
- Derivation: `derive/Deriver.scala` (`derivePrimitive/Record/Variant/Sequence/Map/Dynamic/Wrapper`) and `DerivationBuilder` (`.instance(optic | typeId, tc)`, `.modifier(...)`). `Deriver.withInstance/withModifier` (#1348) works for every format.

**Validation and constraints**

- `Validation` (numeric and string constraints), `Modifier` (rename, transient, alias, config, discriminator, fieldNaming, …), `SchemaAspect`, and `SchemaError` (accumulating).
- `comptime/Allows[A, S]`: a compile-time shape grammar for restricting generic APIs.

**Other**

- Structural types: `ToStructural`, plus JVM-only `StructuralReflection`. Scala 3.5+ bindings use the experimental `Symbol.newClass`, so they are not available on 3.3 LTS.
- `syntax` extensions: `toJson`, `toJsonString`, `fromJson`, `diff`, `applyPatch`, `show`.

The eight operations a newcomer needs:

1. `Schema.derived`
2. `.jsonCodec` / `derive(Format)`
3. `toDynamicValue`
4. optics `$(_.x)`
5. `diff`/`patch`
6. `Into`/`As.derived`
7. `Migration.newBuilder`
8. `toJsonSchema`/`fromJsonSchema`

### 3.3 Dependencies

- `schema` depends on `typeid`, `chunk`, `markdown` and `maybe`.
- It runs on JVM and JS, with Scala 2.13, 3.3 and 3.9.
- Test-only dependencies are zio-prelude and neotype (Scala 3).
- Coverage gate is 85/81.
- Direct downstream modules:
  - all `schema-*` formats
  - `openapi`
  - `config`, `config-json`
  - `http-model-schema`
  - `endpoint`
  - `html`, `htmx`
  - `sql`, `dataMigration`, `projection`
  - `scalaNextTests`, `benchmarks`
- `datastar` uses it transitively through `html`.

### 3.4 Real-world use cases (with evidence)

- **One type, many formats.** `docs/index.md:185-199`.
- **Override one type's codec everywhere** (LocalDate as `dd/MM/yyyy`). `schema-examples/src/main/scala/customcodec/CustomCodecOverrideExample.scala`; `schema/shared/src/test/scala/zio/blocks/schema/derive/DeriverWithInstanceSpec.scala`.
- **Validated newtypes via `transform`.** `Schema.scala:126-186`; `schema-examples/.../schemaerror/SchemaErrorExample.scala`.
- **Map DTOs to a validated domain model with accumulated errors.** `into/IntoDomainBoundaryExample.scala`, `into/IntoErrorAccumulationExample.scala`.
- **Version data V1 to V2 with rollback.**
  - `as/AsSchemaEvolutionExample.scala`, `into/IntoSchemaEvolutionExample.scala`.
  - Migrating against structural shapes of old versions: `schema/jvm/src/test/.../migration/StructuralMigrationSpec.scala`.
  - Compile-time completeness: `MigrationBuildValidationSpec.scala`.
- **Schema registry: persist `DynamicSchema`, rebind later.** `bindingresolvers/BindingResolverSchemaRegistryExample.scala`, `dynamicschema/DynamicSchemaRegistryExample.scala`.
- **Validate untyped payloads against a stored schema.** `dynamicschema/DynamicSchemaValidationExample.scala`.
- **Collaborative-editing change log with JSON Patch.** `jsonpatch/CompleteJsonPatchExample.scala` (310 lines).
- **Typed query DSL and SQL generation from optics.** `querydsl*/` example dirs (4 series).
- **JSON Schema:** generate it, validate against it, round-trip it, and pass the official draft 2020-12 suite (`SchemaToJsonSchemaSpec`, `SchemaFromJsonSchemaSpec`, `JsonSchemaToSchemaSpec`, `schema/jvm/src/test/.../JsonSchemaOfficialTestSuiteSpec.scala`).
- **Restrict generic APIs to data shapes** (CSV rows, events, RDBMS rows, document stores, GraphQL trees). `comptime/*.scala`.
- **Derive your own type class** (Show, Gen). `typeclassderivation/DeriveShowExample.scala`, `DeriveGenExample.scala`.
- **Duck typing via structural schemas.** `structural/*.scala`.
- **Scala 3 named tuples.** `scalaNextTests/src/test/scala-3/.../SchemaVersionSpecificScalaNextSpec.scala` covers schema derivation, optics and >22 fields. This is not mentioned anywhere in the schema docs.

### 3.5 Integration points

`Schema` is the integration spine. Each consumer below implements a `Deriver`/`Format` or consumes a `Schema`:

| Module | How it uses Schema |
|---|---|
| `sql` | `DbCodecDeriver` |
| `config` | `ConfigDecoderDeriver` |
| `http-model-schema` | `QueryFormat`, `HeaderFormat` |
| every format module | its own `*CodecDeriver` |
| `openapi` | `SchemaOps.toOpenAPISchema`, via `toJsonSchema` |
| `endpoint` | a `Schema` per codec atom |
| `html` | `ToJs.fromSchema` |
| `datastar` | `Signal :=` |
| `htmx` | `HxVals.from` |
| `dataMigration`, `projection` | `Migration` |
| `projection` | `@Modifier.id` for entity IDs, `SchemaHash` for rebuilds |

`Schema#doc` returns `zio.blocks.docs.Doc` (markdown).

### 3.6 Existing docs coverage

There are 46 reference pages, about 24k lines, under `docs/reference/schema/**`.

**Thorough** (examples):

| Page | Lines |
|---|---:|
| `schema.md` | 674 |
| `optics.md` | 1617 |
| `type-class-derivation.md` | 2005 |
| `path-interpolator.md` | 779 |
| `dynamic-value.md` | 828 |
| `schema-evolution/into.md` | 881 |
| `as.md` | 503 |
| `allows.md` | 652 |
| `json/json.md` | 939 |
| `json-patch.md` | 732 |
| `json-schema.md` | 596 |
| `dynamic-schema.md` | 509 |
| `schema-error.md` | 568 |
| `schema-expr.md` | 604 |
| `binding-resolver.md` | 469 |

**Thin or partial:**

- `format.md` (92 lines). It wrongly says YAML and TOON are `TextFormat`; both are `BinaryFormat`.
- `index.md` (52 lines). It doesn't list migration, binding-resolver or schema-search.
- `schema-evolution/index.md` (50).
- `migration.md` (297, 5 mdoc blocks). The error ADT and selector syntax are undocumented.
- `reflect.md` (3/21 blocks are mdoc).
- `binding.md` (3/17 blocks are mdoc).

**Pages where code is not compiled:** `patch.md` (0 mdoc), `validation.md` (0 mdoc), `built-in-codecs/yaml.md` (0 mdoc).

**Guides touching schema:**

- `zio-schema-migration.md` (1202 lines; 0 mdoc; backed by `schema-examples/ziosschemamigration/*`). It is a migration guide *from* zio-schema 1.x.
- The 4 Query DSL guides (Parts 1-4, 472/1132/802/1028 lines).

**Gaps** (confirmed in `docs/undocumented-report.md:349-375`):

- No getting-started or JSON round-trip tutorial.
- No architecture explainer (Reflect → Binding → Schema, registers, the Deriver pattern).
- No custom Format/Deriver how-to.
- No how-tos for:
  - migration
  - Into/As
  - schema registry
  - JSON Schema validation
  - diff/patch
  - Allows
  - structural types
  - DynamicValue manipulation
  - modifiers/aspects
  - named tuples

### 3.7 Examples available

All under `schema-examples/src/main/scala/`. The build is standalone, pinned to published 0.0.56 and Scala 3.9.0.

- `as/` (4) and `into/` (6): **none are referenced by any doc.** They are ready-made seeds for an "evolving data" tutorial.
- `bindingresolvers/` (3): not referenced by `binding-resolver.md`.
- `dynamicschema/` (4): embedded in `dynamic-schema.md`.
- `comptime/` (6): embedded in `allows.md`.
- `customcodec/CustomCodecOverrideExample.scala` and `deriveroverrides/CompleteExample.scala`: unreferenced.
- `typeclassderivation/DeriveShowExample.scala` and `DeriveGenExample.scala`.
- `jsonpatch/` (5), including the 310-line `CompleteJsonPatchExample.scala`.
- `structural/` (4): only the Tuple example is named in `structural-types.md`.
- `schemaerror/SchemaErrorExample.scala`.
- `querydslexpressions/`, `querydslsql/`, `querydslextended/`, `querydslbuilder/`: step-wise companions to the Query DSL guides.
- `ziosschemamigration/` (5 steps).
- `context/` (3): belongs to the `context` module.

**Missing examples:** typed Migration, Patch, standalone optics, Validation, JsonSchema, `p"..."`, aspects/modifiers.

`schema-examples/build.sbt` declares the toon, msgpack, avro, thrift and bson dependencies, but **no file uses them**.

### 3.8 Maturity signals

- Very active: last change 2026-10-14.
- 21 TODO/FIXME, almost all "replace with `Math.unsignedMultiplyHigh` after dropping JDK 17" in `JsonReader`/`JsonWriter`.
- Structural bindings are experimental: Scala 3.5+ and JVM-only.
- Explained-type coverage is 55% (466 public types).

### 3.9 Comparable libraries

| zio-blocks feature | Comparable libraries |
|---|---|
| The whole module | zio-schema 1.x (direct predecessor; a migration guide exists) |
| `Deriver` | magnolia, shapeless, Scala 3 `Mirror` |
| JSON codecs | jsoniter-scala, zio-json (both benchmarked), circe |
| `Json` ADT | circe `Json` / ujson |
| Optics | monocle, quicklens (both benchmarked) |
| `Into`/`As` | chimney, ducktape |
| `JsonPatch` | diffson |
| `JsonSchema` | networknt/everit validators; tapir schema generation |
| DynamicSchema + BindingResolver | Confluent Schema Registry |
| Query DSL guides | Quill, Slick |
| Newtypes | zio-prelude Newtype, neotype, iron |

## 4. Schema formats and schema-adjacent tooling

### Facts that apply to every module in this section

- **No guide covers any of these 11 modules.**
  - `docs/guides/zio-schema-migration.md` touches them only to swap dependencies (lines 35-47) and in `derive(AvroFormat)` (line 544).
- **Example projects declare the format dependencies but never use them.**
  - `schema-examples/build.sbt` declares dependencies on toon, msgpack, avro, thrift and bson, yet no source file uses any of them.
  - The only runnable examples in this group are the 4 in `smithy-examples`.
- **Format kinds:**
  - `CsvFormat` is a `TextFormat`.
  - Avro, MessagePack, TOON, XML, Thrift and YAML are all `BinaryFormat`. `docs/reference/schema/format.md:59` wrongly calls YAML and TOON text formats.
  - BSON has **no Format object**. You derive it with `schema.derive(BsonCodecDeriver)`.
- **Overrides:** `Deriver.withInstance` / `withModifier` work for every format (#1348; evidence: `schema-csv/src/test/scala/zio/blocks/schema/csv/DeriverWithInstanceCsvSpec.scala`).
- **Cross-module wiring:**
  - No production code connects `openapi`, `codegen`, `smithy` and `endpoint` to each other.
  - The `markdown` dependency of `openapi` and `schema-yaml` is redundant, because `schema` already depends on `markdown`.

### Summary table

| Module | Platforms | Third-party dependency | Reference page (lines) | Examples | First commit |
|---|---|---|---|---|---|
| `schema-avro` | JVM | `org.apache.avro:avro:1.12.2` | `built-in-codecs/avro.md` (450) | none | 2025-12-18 |
| `schema-bson` | JVM | `org.mongodb:bson:5.13.0` | `bson.md` (494) + `schema-bson/README.md` | none | 2026-01-24 |
| `schema-csv` | JVM+JS | — | `csv.md` (564) | none | 2026-03-13 |
| `schema-messagepack` | JVM+JS | — | `messagepack.md` (507) + README | none | 2026-01-25 |
| `schema-thrift` | JVM | `libthrift 0.25.0` | `thrift.md` (432) + README | none | 2026-01-23 |
| `schema-toon` | JVM+JS | — | `toon.md` (1050) + README | none | 2026-01-15 |
| `schema-xml` | JVM+JS | — | `xml.md` (1034) | none | 2026-02-26 |
| `schema-yaml` | JVM+JS | — | `yaml.md` (552) | none | 2026-03-11 |
| `openapi` | JVM+JS | — | `openapi.md` (1301) | none | 2026-03-10 |
| `codegen` | JVM | — (no zio-blocks deps) | `codegen/` (10 pages, 3024) | none (inline only) | 2026-03-18 |
| `smithy` | JVM | — (no zio-blocks deps) | `smithy.md` (881) | `smithy-examples/` (4) | 2026-03-11 |

All of these modules build for Scala 2.13, 3.3 and 3.9.

### 4.1 `schema-avro`

**Purpose.** Derives Apache Avro binary codecs and Avro schemas (`.avsc`) from `Schema[A]`.

**Key types and entry points.**
- `AvroFormat` (`schema-avro/src/main/scala/zio/blocks/schema/avro/AvroFormat.scala`).
- `AvroCodec[A]`, with these operations:
  - `avroSchema`
  - `encode(value)` and `encode(value, OutputStream)`
  - `decode(bytes | InputStream)`
  - `encodeValue` / `decodeValue` over Avro's `BinaryEncoder` / `BinaryDecoder`
  - `maxCollectionSize`
- `AvroCodecDeriver` takes no configuration.

**Use cases.** Evidence: `AvroFormatSpec.scala` (1086 lines; primitives, records, sequences, maps, variants, wrappers, DynamicValue) and the Scala 3 neotype spec.
- Kafka-style event payloads.
- Emitting `.avsc` files.
- Recursive types, unions, newtypes.

**Interop caveats.** These belong in a how-to.
- `Instant` is encoded as a record `{epochSecond, nano}`, not as Avro's `timestamp-*` logical type (`AvroCodec.scala:272-276`).
- `UUID` is encoded as `fixed(16)`.
- Not supported: container files (`DataFileWriter`), reader/writer schema resolution, Schema Registry.

**Integration points.**
- Benchmarked against avro4s 5.0.15 and zio-schema-avro 1.8.6 (`benchmarks/src/main/scala/zio/blocks/schema/avro/AvroListOfRecordsBenchmark.scala`).

**Docs and gaps.**
- The reference page is a thorough API reference with Patterns 1-4.
- Missing: schema evolution and compatibility, a Kafka/Registry recipe, a logical-type interop table.

**Maturity.** Oldest codec. Coverage gate 96%.

**Comparable libraries.**
- avro4s (closest), vulcan, zio-schema-avro.
- avrohugger and SpecificRecord generate code in the opposite direction.

### 4.2 `schema-bson`

**Purpose.** Derives MongoDB BSON encoders and decoders from `Schema[A]`.

**Key types and entry points.**
- In `BsonTypes.scala`:
  - `BsonEncoder[A]` (`toBsonValue`, `contramap`)
  - `BsonDecoder[A]` (`fromBsonValue`, `map`, `mapOrFail`)
  - `BsonCodec[A]`
  - `BsonTrace`
- `BsonCodecDeriver` has `with*` configuration methods.
- `BsonSchemaCodec` is a compatibility facade:
  - `Config` with `withSumTypeHandling` (`WrapperWithClassNameField` / `DiscriminatorField` / `NoDiscriminator`), `withClassNameMapping`, `withIgnoreExtraFields`, `withNativeObjectId`.
- `ObjectIdSupport`.
- `bsonValue.as[A]`.

**Use cases.** Evidence: 13 specs, including `BsonCodecAnnotationPortSpec` (1010 lines, which ports zio-schema-bson annotation behaviour), `BsonCodecObjectIdSpec` and `BsonCodecJsonSpec`.
- Case classes as Mongo documents.
- Choosing an ADT encoding.
- Native `ObjectId` ids.
- Storing schema `Json` values as semantic BSON (#1340).

**Integration points.**
- Honours the `fieldNaming`, `noExtraFields`, `caseNaming` and `discriminator` modifiers.
- Interop with schema `Json`.
- **No MongoDB driver bridge** (`CodecProvider` / `CodecRegistry`). Users must convert through `BsonDocument` themselves.

**Docs and gaps.**
- `bson.md` is thorough.
- `schema-bson/README.md` says bson 5.2.1, but the build uses 5.13.0.
- Missing: an end-to-end "persist with the Mongo Java driver" recipe.

**Maturity.** Coverage gate 66/58, the lowest in this group.

**Comparable libraries.** mongo-scala-driver `Macros.createCodecProvider`, reactivemongo BSON macros, zio-schema-bson, mongo4cats.

### 4.3 `schema-csv`

**Purpose.** RFC 4180 CSV codec derived from `Schema[A]`. Only flat records and wrappers are supported.

**Key types and entry points.**
- `CsvCodec[A] extends TextCodec[A]`. It handles **one row** and exposes `headerNames`.
- `CsvFormat`.
- `CsvCodecDeriver`.
- `CsvConfig(delimiter, quoteChar, lineTerminator, hasHeader, nullValue)`, with `default` and `tsv` presets.
- `CsvReader.readRow` / `readHeader` / `readAll`.
- `CsvWriter.writeRow` / `writeHeader` / `writeAll`.
- `CsvError.ParseError` / `TypeError`, each with row and column.

**Use cases.**
- Weather-data rows (`CsvWeatherDataSpec`).
- Quoting and escaping round trips (`CsvIntegrationSpec`).
- A custom `LocalDate` format via `withInstance`.
- Rejection of variants, sequences and maps (`CsvRejectionSpec`).

**Integration gaps.**
- No "file to `List[A]`" helper exists, so users combine `readAll` with the per-row codec themselves.
- No `streams` integration for large files.
- **The derived codecs hardcode `CsvConfig.default`** (`CsvCodecDeriver.scala:174,181`). `CsvConfig` only affects the low-level reader and writer.

**Docs and gaps.**
- `csv.md` "Pattern 2" (lines 130-147) claims you can pass a custom `CsvConfig` to derivation, then admits you cannot.
- Missing: TSV workflows, header validation, multi-row ingestion, streaming.

**Examples.** None. `schema-examples/.../comptime/AllowsCsvExample.scala` hand-writes CSV and does not use this module.

**Comparable libraries.** kantan.csv (closest), fs2-data-csv, scala-csv, Jackson CSV.

### 4.4 `schema-messagepack`

**Purpose.** A compact MessagePack codec derived from `Schema[A]`, with a zero-dependency reader and writer.

**Key types and entry points.**
- `MessagePackFormat`.
- `MessagePackCodec[A]`:
  - `encode` / `decode` over `Array[Byte]` or `ByteBuffer`
  - thread-local reader and writer pools
- `MessagePackCodecDeriver`.
- Public `MessagePackReader` / `MessagePackWriter`.

**Use cases.**
- Cache and RPC payloads.
- Cross-language binary exchange.
- `DynamicValue` transport (`MessagePackDynamicValueSpec`).

**Docs and gaps.**
- `messagepack.md` "Pattern 3: Stream Large Datasets" (line 162) shows no streaming.
- Missing: interop conventions with other languages (only the README has an "Encoding Conventions" section), and streaming many messages.

**Comparable libraries.** msgpack4z, upickle/upack, msgpack-java / jackson-dataformat-msgpack, zio-schema-msg-pack.

### 4.5 `schema-thrift`

**Purpose.** An Apache Thrift `TBinaryProtocol` codec derived from `Schema[A]`.

**Key types and entry points.**
- `ThriftFormat`.
- `ThriftCodec[A]`:
  - `encodeValue` / `decodeValue` over a `TProtocol`
  - `encode` / `decode` (always `TBinaryProtocol` over an in-memory transport)
- `ThriftCodecDeriver`.
  - **Field IDs are positional** (`idx + 1`, `ThriftCodecDeriver.scala:167,243`). There is no annotation for explicit IDs.
  - Unknown fields are skipped.

**Use cases.**
- Talking to legacy Thrift services.
- Forward-compatible decoding.
- Sealed traits as Thrift unions.
- **Pitfall:** reordering case-class fields changes the wire IDs.

**Docs and gaps.**
- `schema-thrift/README.md` says Thrift 0.22.0, but the build uses 0.25.0.
- It also claims "compact integers" under `TBinaryProtocol`, which is likely wrong (unverified).
- Missing: field-ID stability, other protocols, interop with Scrooge peers.

**Comparable libraries.** Twitter Scrooge, Apache Thrift generated classes, zio-schema-thrift.

### 4.6 `schema-toon`

**Purpose.** A codec for TOON (Token-Oriented Object Notation), an indentation-based encoding of the JSON data model. Its README claims it is 30-60% smaller than JSON and aimed at LLM prompts (`schema-toon/README.md:7`).

**Key types and entry points.**
- `ToonFormat` (`text/toon`, but declared as a `BinaryFormat`).
- `ToonCodec[A]` (1475 lines).
- `ToonCodecDeriver` with a rich `with*` configuration:
  - field and case name mappers
  - discriminator kind
  - `ArrayFormat` (Inline / Tabular / List / Auto)
  - delimiter
  - transient None / empty / default handling
  - reject extra fields
- `WriterConfig`: indent, delimiter, `KeyFolding`, flattenDepth.
- `ReaderConfig`: `PathExpansion`, strict mode.
- `ToonReader` / `ToonWriter`.

**Use cases.**
- Compact structured data in LLM prompts, e.g. tabular `users[2]{id,name}:`.
- Parsing LLM output back into case classes.
- Spec conformance (`ToonSpecConformanceSpec`, 2949 lines).

**Integration points.**
- Benchmarked against toon4s 0.9.1.
- Shares `NameMapper` with JSON.

**Docs and gaps.**
- `toon.md` is very thorough.
- **Missing an "LLM prompt/response round trip" how-to, even though that is the module's headline use case.**

**Maturity.** 1 FIXME: a huge-exponent `BigDecimal` throws `BufferOverflowException` (`ToonCodecDeriverSpec.scala:344`).

**Comparable libraries.** toon4s, plus the reference TypeScript implementation and JToon (names unverified).

### 4.7 `schema-xml`

**Purpose.** A zero-dependency XML codec derived from `Schema[A]`, plus:
- an XML AST
- navigation (`XmlSelection`)
- patching (`XmlPatch`)
- a builder

**Key types and entry points.**
- `XmlFormat` and `XmlCodec`.
- The `Xml` AST: `Element`, `Text`, `CData`, `Comment`, `ProcessingInstruction`, with `print`, `printPretty` and `select`.
- `XmlBuilder`.
- `XmlSelection`: `descendant`, `elements`, `getAttribute`, `text`, `filter`, `one`, `all`, …
- `XmlPatch`: `add`, `remove`, `replace`, `setAttribute`, with paths given as a `DynamicOptic`.
- Annotations: `@xmlAttribute`, `@xmlNamespace`.
- `WriterConfig.pretty` / `withDeclaration`.
- Syntax: `toXml`, `toXmlString`, `"…".fromXml[A]`.

**Use cases.** Evidence: 15 specs.
- Case classes with attributes and namespaces.
- Parsing RSS, Atom and sitemaps (`xml.md:895-1034`).
- Querying and transforming documents without codecs.
- Patching config-like XML.

**Docs and gaps.**
- `xml.md` is thorough and includes real-world examples.
- `undocumented-report.md:331` lists remaining gaps: deriver customization, error handling, `XmlWriter`, `SetAttribute` / `RemoveAttribute`.

**Comparable libraries.** scala-xml (`\`, `\\`), phobos (closest for derived codecs), scalaxb (XSD to Scala), xtract, advxml, Jackson XML.

### 4.8 `schema-yaml`

**Purpose.** A zero-dependency YAML 1.2 toolkit:
- reader and writer
- `Yaml` AST
- codecs derived from `Schema[A]`
- a compile-time-checked `yaml"..."` interpolator
- YAML↔JSON interop

**Key types and entry points.**
- `YamlFormat`.
- `YamlCodec[A]`: `encodeValue` / `decodeValue`, `decode(String | bytes)`, `encodeToString`.
- The `Yaml` AST: `Mapping`, `Sequence`, `Scalar`, `NullValue`, with `toJson` and `Yaml.fromJson`.
- `YamlReader.read`.
- `YamlWriter.write(yaml, YamlOptions)`, with `default`, `pretty` and `flow` presets.
- `YamlJsonInterop`.
- `YamlSyntax`: `toYaml`, `"…".fromYaml[A]`.
- The `yaml"..."` macro (`scala-3/.../package.scala`).

**Use cases.**
- Typed config files.
- Generating Kubernetes and CI YAML.
- Converting OpenAPI JSON to YAML.
- Backing `config-yaml`.

**Limitations (unverified).** No anchor/alias support was found, and multi-document streams are likely unsupported.

**Integration points.**
- `config-yaml` uses `YamlReader` (`config-yaml/shared/.../YamlConfigSource.scala`).
- `openapi.md:305` suggests piping OpenAPI JSON through schema-yaml, but no code or test does this.

**Docs and gaps.**
- `yaml.md` (0 mdoc blocks) **never mentions the `yaml"..."` interpolator, `YamlSyntax`, `YamlTag`, or the standalone `YamlReader` / `YamlWriter`**.
- Its limitations are undocumented.

**Comparable libraries.** scala-yaml (VirtusLab, closest), circe-yaml, moultingyaml, SnakeYAML / Jackson YAML, zio-config-yaml.

### 4.9 `openapi`

**Purpose.** A typed OpenAPI 3.1 data model (case classes) with hand-written, spec-compliant JSON codecs, plus `Schema[A]` → OpenAPI `SchemaObject` conversion via JSON Schema.

**Key types and entry points.**
- `OpenAPI.scala` (1173 lines) defines `OpenAPI`, `Info`, `Server`, `Paths`, `PathItem`, `Operation`, `Parameter`, `RequestBody`, `Response(s)`, `Components`, `SchemaObject`, `Reference`, `ReferenceOr`, `SecurityScheme`, `OAuthFlows`, `Tag`, …
  - Descriptions are `zio.blocks.docs.Doc`.
- `OpenAPICodec.openAPICodec` (line 1078).
- `package.scala`: `schema.toOpenAPISchema`, `toInlineSchema`, `toRefSchema`.
- `OpenAPIGen.schema[A]` / `schemas(...)`.

**Pitfall.**
- The codecs are `JsonASTCodec`. Their streaming `decodeValue(JsonReader)` / `encodeValue(x, JsonWriter)` are `???` (`OpenAPICodec.scala:204-208`, verified).
- Only the AST path works: `encodeValue(api): Json` / `decodeValue(json)`.
- Calling byte- or string-level `encode` / `decode` will likely throw `NotImplementedError`.
- This is undocumented.

**Use cases.** Evidence: 13 specs plus fixtures `petstore`, `minimal` and `with-security` JSON.
- Authoring a spec in Scala.
- Generating component schemas from case classes.
- Modelling security schemes.
- Parsing an existing spec.
- Emitting JSON (or YAML) for Swagger UI / Redoc.

The round-trip tests only assert that `Json.parse` succeeds, which is weak evidence that full spec parsing works.

**Integration points.**
- `schema` (`toJsonSchema` → `SchemaObject.fromJsonSchema`).
- `markdown`.
- **Not `endpoint`.** `docs/reference/endpoint/index.md:210` claims "`zio-blocks-openapi` consumes `Endpoint` values to generate OpenAPI 3.1 specifications". This is false (verified).

**Docs and gaps.**
- `openapi.md` is thorough, but its intro signature (lines 10-17) is stale.
- `OpenAPIGen` is undocumented.
- Missing: a recipe for loading a spec file, a YAML export recipe, and an Endpoint→OpenAPI story.

**Maturity.** Dormant since about #1258. Coverage gate 81/54.

**Comparable libraries.** tapir-openapi-docs + `sttp.apispec` (closest), zio-http `OpenAPI` / `OpenAPIGen` (same names), swagger-parser / swagger-core, openapi-generator, guardrail.

### 4.10 `codegen`

**Purpose.** A dependency-free IR for Scala source code, plus a pure `ScalaEmitter` that renders either Scala 3 or Scala 2 syntax. It is meant as a building block for generators.

**Key types and entry points.**
- `ir/ScalaFile`, `PackageDecl`.
- `Import` variants: Single, Wildcard, Rename, Group.
- `TypeDefinition`: `CaseClass`, `SealedTrait`, `Trait`, `AbstractClass`, `OpaqueType`, `Enum`, `ObjectDef`, `CompanionObject`, `Newtype`, `TypeAlias`.
- `ObjectMember`.
- `Field`, `Method`, `ParamList` (Normal / Implicit / Using).
- `TypeParam`, `TypeRef`, `Annotation`.
- `emit/ScalaEmitter.emit(file, config)`.
- `EmitterConfig`, with `default` and `scala2` presets.

**Use cases.** Evidence: `CodegenIntegrationSpec` (758 lines).
- Generating API DTOs.
- Producing Scala 2 vs Scala 3 output from the same IR (enum vs sealed trait, `derives`, `given` vs `implicit`).
- Import organization.
- Keyword escaping.

**Integration points.**
- **None in code.** smithy and openapi do not use it.
- The docs show only *pseudocode* for Smithy/OpenAPI → codegen (`docs/reference/codegen/examples.md:485-516`).

**Docs and gaps.**
- 10 thorough pages.
- `ParamList` / `ExtensionBlock` are absent from the docs.
- Missing: a real end-to-end generator, and an sbt `sourceGenerators` wiring how-to.

**Maturity.** 2 commits; landed in #1155.

**Comparable libraries.** scalameta (closest), treehugger, KotlinPoet / JavaPoet (the analogous design), smithy4s / guardrail internal emitters.

### 4.11 `smithy`

**Purpose.** A pure-Scala parser, AST and pretty-printer for the Smithy 2.0 IDL.

**Key types and entry points.**
- `SmithyModel`: `parse(String)` returns `Either[SmithyError, SmithyModel]`; also `findShape`, `allShapeIds`, `prettyPrint`.
- `SmithyParser`, `SmithyPrinter`.
- The `Shape` ADT:
  - simple shapes
  - `EnumShape`, `ListShape`, `MapShape`, `StructureShape`, `UnionShape`
  - `ServiceShape`, `OperationShape`, `ResourceShape`
- `ShapeId`.
- `TraitApplication` helpers: `required`, `documentation`, `http`, `error`.
- `NodeValue`.
- `SmithyError`, with line and column.

**Use cases.** Evidence: 12 specs.
- Parsing a `.smithy` file and inspecting services and operations.
- Linting a model for missing docs or deprecated shapes.
- Building models in code and printing IDL.
- Round-trip formatting.

**Limitations.**
- Today, code generation from a model is done with `println` string building (`smithy-examples/.../BookStoreAPI.scala:391-420`), not with `codegen`.
- Not found (unverified): the JSON AST model format, mixins, model assembly and validation.

**Examples** (`smithy-examples/src/main/scala/smithyexample/`):
- `BasicParsingAndQuerying.scala`
- `BuildingModelsAndTraits.scala`
- `ValidationAndAnalysis.scala`
- `BookStoreAPI.scala` (474 lines)

Their header run commands are stale; the correct form is `cd smithy-examples && sbt "runMain …"`.

**Docs and gaps.**
- `smithy.md` is thorough and includes Use-Cases 1-3.
- Missing: a smithy + codegen tutorial, and a list of unsupported features.

**Comparable libraries.** AWS `smithy-model` (Java), smithy4s (end-to-end codegen + runtime), smithy-translate.

### 4.12 `scalaNextTests` (test-only, not published)

- Scala 3.9.0 only.
- Tests `Schema.derived` and `Binding.of` for **Scala 3 named tuples**:
  - generic, recursive and higher-kinded cases
  - more than 22 fields
  - optics via `$(_.i)`
  - DynamicValue round trips
- Files: `scalaNextTests/src/test/scala-3/zio/blocks/schema/SchemaVersionSpecificScalaNextSpec.scala` (442 lines) and `.../binding/BindingOfVersionSpecificScalaNextSpec.scala`.
- Named-tuple schema support is **not documented anywhere**. That makes it a how-to candidate.

## 5. Configuration, observability, security, media types

### 5.1 `config` (`zio-blocks-config`)

**Purpose.** Synchronous, effect-free typed configuration loading from flat dotted-key string sources, with per-field provenance and secret redaction. The same module also provides feature flags (`StaticFlag`, `DynamicFlag`) with a deterministic percentage-rollout expression language.

**Key types / entry points** (`config/shared/src/main/scala/zio/blocks/config/`)
- `Config` (`Config.scala`):
  - `load[A](source)` returns `Either[::[ConfigError], A]` and accumulates every error.
  - `loadOrThrow` throws `ConfigLoadException`.
  - `loadWithProvenance` returns a `ProvenanceMap[A]`.
  - `wire[A]` / `wire[A](prefix)` return `Wire.Shared[ConfigSource, A]`, which ties config into `scope`.
- `ConfigSource` (`ConfigSource.scala`):
  - `get` returns `Maybe[SourceValue[String]]`.
  - Combinators: `orElse`, `prefix`, `keyMapper`, `keyFormat`.
  - Constructors: `ConfigSource.fromMap` / `MapSource`.
  - Platform sources:
    - JVM: `EnvSource` (maps `DB_HOST` to `db.host`) and `SysPropSource` (`config/jvm/.../PlatformConfigSource.scala`).
    - JS: `EnvSource` reads `process.env`; `SysPropSource` is always empty.
- `ConfigDecoder[A]` / `ConfigDecoder.derive`, built on `ConfigDecoderDeriver` (458 lines, a schema `Deriver`). It handles records, Option, defaults, sequences (indexed keys or comma-separated), maps, sealed traits via a discriminator key, and wrappers.
- `ConfigError`: `MissingKey`, `InvalidValue`, `ParseError`, `MissingDiscriminatorKey`, `UnknownDiscriminator`, `Composite`, …
- Provenance and secrets:
  - `Provenance` and `ProvenanceMap`. `ProvenanceMap.dump(prefix)` renders a table with sensitive keys redacted.
  - `Secret`, `Displayable` and `Sensitive` handle redaction.
- `KeyMapper` and `KeyFormat` (CamelCase / SnakeCase / KebabCase / UpperSnakeCase).
- Flags:
  - `FlagSource.Registry` is a global registry; registration order is priority.
  - `StaticFlag[A](default)` is resolved once at object init.
  - `DynamicFlag[A](default, expr)` offers `apply(key, attrs*)`, `update`, `reload()`, `counters` and `updateHistory`.
  - `Flag.dump` and `Flag.nearMissWarnings`.
- `Rollout`:
  - Grammar is `value@path/segments/NN%; fallback`.
  - Bucketing is deterministic: `MurmurHash3.stringHash(key) % 100`.
  - `validate` warns on unreachable choices or totals above 100%.
- **Reload is manual.** There is no polling, file watching or scheduler; the user calls `DynamicFlag.reload()`.

**Dependencies.**
- Depends on `schema`, `scope`, `maybe` (`build.sbt:869`). It also uses `context` (via scope).
- Used by `config-yaml`, `config-json`, `config-hocon`. No other module depends on it.
- JVM + JS; Scala 2.13 / 3.3 / 3.9.
- JVM tests run serially because `Registry` is a global singleton.

**Real-world use cases** (tests and docs)
- Layer env over file over defaults, with per-field provenance (`ConfigSourceCompositionSpec` "four-level chain").
- Map `UPPER_SNAKE` env vars onto camelCase fields (`KeyMapperSpec`).
- Decode nested app config including Option, defaults, `List[String]`, `Map[String,Int]`, and sealed-trait variants selected by a type key (`ConfigDecoderSpec`).
- Report every config error at startup (`loadOrThrow`).
- Print a redacted startup dump (`FlagDumpSpec`).
- Restart-only tuning knobs with `StaticFlag` that fail fast with `ExceptionInInitializerError`.
- Percentage or region rollouts with sticky bucketing, runtime update and reload, and bounded evaluation counters (`DynamicFlagSpec`, `RolloutSpec`).
- Catch typo'd flag names with near-miss warnings.
- Wire config into a `scope` dependency graph (`ConfigSpec` "Config.wire", "integrates with Resource.from").

**Integration points.**
- `schema`: `Schema[A]` drives decoding.
- `scope`: `Config.wire` with `Resource.from` and `Scope.global.scoped`, plus `Context[ConfigSource]` (`config/shared/src/test/.../ConfigSpec.scala:22,81-92`; `docs/reference/config/index.md` "Wiring Config into a Dependency Graph").
- `maybe`: every lookup returns `Maybe`.
- **No evidence** that config is used for telemetry `ExporterConfig`, JWT keys, `JdbcTransactor` URLs or endpoints. These cross-module recipes are unwritten.

**Existing docs coverage.**
- **Thorough reference.** `docs/reference/config/` has 7 pages:

  | Page | Lines |
  |---|---:|
  | `index.md` | 286 |
  | `config-source.md` | 392 |
  | `config-decoder.md` | 389 |
  | `errors.md` | 251 |
  | `flags.md` | 339 |
  | `rollout.md` | 283 |
  | `formats.md` | 272 |

- **No guide and no example files.** Missing task content includes:
  - layered production config, end to end;
  - 12-factor env mapping;
  - a feature-flag rollout runbook;
  - reloading flags from a remote source on a timer;
  - testing with flags via `Registry.clear`;
  - a comparison with pureconfig / zio-config / ciris.

**Examples available.** None, apart from mdoc blocks.

**Maturity signals.**
- 3 commits, 2026-06-24 to 2026-08-31: #1426 initial, #1481, #1649 "close config review findings".
- 0 TODO/FIXME.
- Coverage gate 80/70; MiMa set to non-failing.
- `docs/plans/config-follow-up-prs.md` and `config-pr-assessment-roadmap.md` list planned follow-ups, such as `ConfigSource.fromEnv` and secrets-dir / `_FILE` sources.
- Flags rely on global mutable state and class-init order ("Register before first touch" warning in `flags.md`).

**Comparable libraries.**
- pureconfig: `Config.load` with derived decoders.
- ciris: env/prop sources and `Secret`.
- zio-config: schema-derived decoders, `orElse`, key mapping; its report feature roughly matches `ProvenanceMap.dump`.
- Typesafe / Lightbend Config: HOCON and layering.
- Hoplite (Kotlin): the closest conceptual analogue, with multi-format layered config.
- Flags: LaunchDarkly, Unleash, OpenFeature, Flagsmith, FF4J, Togglz. ZIO Blocks has no remote-provider SDK.

### 5.2 `config-hocon` (`zio-blocks-config-hocon`)

**Purpose.** A self-contained HOCON parser (no Typesafe dependency) that flattens its input into a `ConfigSource`. File loading with includes is JVM-only.

**Key types / entry points.**
- `ConfigSource.fromHocon(str, sourceId)`, enabled by `import zio.blocks.config.hocon._`.
- JVM-only: `ConfigSource.fromFile(path, allowedBase, maxIncludeDepth)` and `fromFileWithLimits(..., maxFileBytes)` (`config-hocon/jvm/.../ConfigSourceHoconPlatformSyntax.scala`, `HoconConfigSourcePlatform.scala`).
- `HoconParser` (672 lines), the `HoconValue` ADT, and `HoconError`.
- Supported features: `=` and `:`, optional braces and commas, comments, triple-quoted strings, key-path desugaring with deep merge, `${}` and `${?x}` substitutions, cycle detection, `+=`, and includes via a callback.
- Env-var fallback inside substitutions: no `System.getenv` call was found (**unverified**).

**Dependencies.** Depends only on `config`. JVM + JS.

**Use cases.**
- Migrating a Lightbend-style `application.conf`.
- Sandboxed file loading: `allowedBase` containment, include-depth limits, and `maxFileBytes` (`HoconConfigSourceSpec`).
- Composing HOCON under env.

**Docs.** `docs/reference/config/formats.md` (HOCON section is about lines 132-252), partial. There is no Typesafe-Config migration guide and no list of unsupported HOCON features.

**Examples.** None.

**Maturity.** Same commits as `config`. Coverage gate 76/66. Bridge overloads for `fromFile` already exist, so the API has changed once.

**Comparable libraries.** Typesafe Config (`ConfigFactory.parseString` / `parseFile`), sconfig, pureconfig-hocon.

### 5.3 `config-json` (`zio-blocks-config-json`)

**Purpose.** Parses a JSON string with the `schema` JSON parser and flattens it to a dotted-key `ConfigSource`.

**Key types / entry points.**
- `ConfigSource.fromJson(json, sourceId)` (`config-json/shared/.../json/package.scala`).
- Arrays become indexed keys, nulls are skipped, and scalars become strings.
- Input must be a string; there is **no file loader**.

**Dependencies.** Depends on `config` and `schema`. JVM + JS.

**Use cases** (`JsonConfigSourceSpec`, 210 lines).
- Nested and array flattening, with provenance.
- Parse errors don't leak document content.

**Docs.** `formats.md` JSON section (about lines 101-131), stub-level. Not covered: how to read a file, and browser usage.

**Examples.** None.

**Maturity.** Coverage gate 91/85.

**Comparable libraries.** circe-config, zio-config-typesafe JSON sources.

### 5.4 `config-yaml` (`zio-blocks-config-yaml`)

**Purpose.** Parses YAML through `schema-yaml` and flattens it to a `ConfigSource`.

**Key types / entry points.** `ConfigSource.fromYaml(yaml, sourceId)` (`config-yaml/shared/.../yaml/package.scala`). Input must be a string.

**Dependencies.** Depends on `config` and `schema-yaml`. JVM + JS.

**Use cases** (`YamlConfigSourceSpec`, 227 lines).
- Nested mappings, block and flow sequences, `~` null, quoted strings, error redaction.
- The test "malformed YAML treated as scalar value" shows lenient parsing that may surprise users. It deserves a doc callout.

**Docs.** `formats.md` YAML section (about lines 49-100), partial.

**Examples.** None.

**Maturity.** Coverage gate 71/65, the lowest of the adapters.

**Comparable libraries.** zio-config-yaml, circe-yaml combined with pureconfig.

### 5.5 `telemetry` (`zio-blocks-telemetry`)

**Purpose.** Effect-free, low-allocation tracing, structured logging and metrics, aligned with the OpenTelemetry data model and with no third-party dependencies. The global entry points `log`, `trace` and `metric` work with zero setup.

**Key types / entry points** (`telemetry/shared/src/main/scala/zio/blocks/telemetry/`, 78 files)
- **Logging:**
  - `log.trace|debug|info|warn|error|fatal(msg, enrichments*)` are macros that capture source location and stamp the active span.
  - Rate-limited variants: `xxxEvery(n, …)` and `xxxAtMost(ms, …)`.
  - `log.annotated(k -> v)(body)`.
  - Hierarchical levels: `setMinSeverity(prefix, sev)`.
  - `install`, `addProcessor`.
  - Supporting types: `LoggerProvider`, `LogRecord`, `LogRecordProcessor`, `Severity` (the 24 OTel levels), `LogEnrichment[A]`.
  - Formatters: `TextLogFormatter`, `JsonLogFormatter`. Writers: `StdoutWriter`, `StderrWriter`.
  - The JVM `FileLogWriter` is `private[telemetry]`, so users must write their own file writer.
- **Tracing:**
  - `trace.span(name[, kind[, attrs]])(f: Span => A)`, `trace.collectedSpans` (for tests), `install(provider)`.
  - Supporting types: `TracerProvider`, `Tracer`, `Span`, `SpanBuilder`, `SpanContext`, `SpanData`, `SpanKind`, `SpanStatus`.
  - Samplers: only `AlwaysOn`, `AlwaysOff` and `ParentBased`. There is **no ratio sampler**.
  - `InMemorySpanProcessor`.
- **Metrics:**
  - `metric.counter / upDownCounter / histogram / gauge`.
  - `MeterProvider`, `Meter`, the `Observable*` instruments, `LabeledInstruments` (pre-bound attributes), `MetricReader`, `MetricData`.
- **Common:** `Attributes`, `AttributeKey`, `AttributeValue`, `Resource`, `InstrumentationScope`, `ContextStorage`.
  - On the JVM, `ContextStorage` uses `java.lang.ScopedValue`; the guide (line 1032) says "JDK 25+ specific".
  - `PlatformExecutor` uses virtual threads.

**Dependencies.**
- The build declares `dependsOn(context, chunk)`, but a grep found **no imports** of either in `telemetry/*/src/main`. The declared dependencies look unused (intent unverified).
- JVM + JS; Scala 2.13 / 3.x. The JVM build raises `-release` to 21.
- **The JDK requirement (21 or 25) is not stated** in the installation sections.
- No zio-blocks module uses telemetry: `sql`, `endpoint`, `streams`, `mux` and `async` have no imports of it.

**Real-world use cases.**
- Zero-config stdout logging, switched to JSON on stderr in production.
- Per-package log levels.
- Throttling log spam at hot call sites.
- Request-scoped annotations such as a request id.
- Correlating traces and logs through a shared `ContextStorage` (guide line 569).
- Nested spans.
- Asserting spans and logs in tests (`trace.collectedSpans`; guide lines 332 and 537).
- Latency histograms, request counters, queue-depth gauges.
- Labeled instruments to avoid per-call allocation.

**Integration points.**
- `otel` adds the `Context[R]` bridge.
- The guide's HTTP integration (Part 6, line 971) is generic, built on `(Req, String) => Option[String]`. It is **not** wired to `http-model` or `endpoint`.
- There is no SLF4J/JUL bridge (guide line 1016).

**Existing docs coverage.**
- **Reference:** about 40 pages under `docs/reference/telemetry/` (index 311 lines plus the tracing, logging, metrics and common sub-trees). Many sub-pages are 24-60 lines: `span-data` 32, `span-context` 39, `log-record` 44, `instrumentation-scope` 24, `resource` 34.
- **Guide:** `docs/guides/telemetry-guide.md` (1130 lines) is a reference-style deep dive (Parts 1-7: architecture, logging, tracing, metrics, production setup, integration patterns, FAQ). **It is not a beginner tutorial.**
- **Missing how-tos:**
  - testing with in-memory telemetry;
  - per-package levels;
  - a custom file or rotating `LogWriter`;
  - a ratio sampler;
  - log/trace correlation;
  - instrumenting an `http-model` / `endpoint` server;
  - Scala.js usage.
- `ContextStorage.scala:31` references the ADR `docs/adr/0001-scoped-context-no-set.md`, **which does not exist**.

**Examples available.** None.

**Maturity signals.**
- Commits 2026-06-24 to 2026-07-09: #1226 initial, #1488, #1490 "coverage ~51% to ~80%", #1491, #1516.
- 0 TODO. Coverage gate 82/72. Several coverage-padding specs.
- build.sbt defines `telemetryBenchmarks` (against scribe 3.15.3), but the `telemetry-benchmarks/` directory **does not exist** in this worktree.

**Comparable libraries.**
- opentelemetry-java API/SDK: near 1:1 naming.
- otel4s, zio-telemetry: effectful Scala OTel APIs.
- Logging: log4cats, scribe, logback/SLF4J, zio-logging, odin.
- Metrics: Kamon, Micrometer, Prometheus simpleclient, Dropwizard Metrics.

### 5.6 `otel` (`zio-blocks-telemetry-otel`, JVM-only)

**Purpose.** Gets telemetry out of the process as OTLP/HTTP **JSON** (not protobuf or gRPC). It also propagates trace context across processes with W3C `traceparent` and B3, and bridges span context into `zio.blocks.context.Context[R]`.

**Key types / entry points** (`otel/src/main/scala/zio/blocks/telemetry/otel/`)
- **Public:**
  - `ExporterConfig(endpoint = "http://localhost:4318", headers, timeout, maxQueueSize, maxBatchSize, flushIntervalMillis)`. Headers are redacted in `toString`.
  - `HttpSender` and `HttpSender.jdk(timeout)`.
  - `ExportResult`.
  - `OtlpJsonEncoder` (`encodeTraces` / `encodeLogs` / `encodeMetrics`).
  - `Propagator`, `W3CTraceContextPropagator`, `B3Propagator.single/multi`.
  - `OtelContext` (`current`, `withSpan`; has an implicit `IsNominalType`, so it works as a `Context` key).
- **Internal (`private[otel]`):** `OtlpJsonTraceExporter`, `OtlpJsonLogExporter`, `OtlpJsonMetricExporter`, `OtlpJsonExporter`, `BatchProcessor`.
  - `docs/reference/telemetry/otel/index.md` (around line 157) says "There is no public way to construct one yet".
  - The guide (Part 5, around line 727) works around this by putting bootstrap code in `package zio.blocks.telemetry.otel`.
  - The metric exporter has no background thread, so the user must schedule `exportMetrics()`.

**Dependencies.**
- `telemetry.jvm` only. Compiled with `-release 21`. Tests are forked because of JDK 25 crashes (`build.sbt` comment around line 727).
- Coverage gate is 0/0.

**Use cases.**
- Send traces, logs and metrics to an OTel Collector, Jaeger, Tempo or a vendor endpoint.
- Propagate W3C or B3 headers between services (`W3CTraceContextSpec`, `B3PropagatorSpec`).
- Build a custom exporter (`docs/reference/telemetry/otel/custom-exporter.md`, 210 lines).
- Configure from `OTEL_EXPORTER_OTLP_*` env vars (guide line 922 uses plain `sys.env`, not `config`).
- Test without a network using a fake `HttpSender`.

**Docs.** `otel/index.md` (212 lines, partial), `custom-exporter.md` (210 lines), and guide Part 5. No how-to exists for:
- a local collector plus Jaeger;
- injecting and extracting headers with `http-model` `Headers`;
- scheduling metric export;
- choosing between W3C and B3.

**Examples.** None.

**Maturity.** 3 commits (#1226, #1488, #1604). The private exporters are the main sign of immaturity, and **they block any "export to a collector" tutorial**.

**Comparable libraries.**
- opentelemetry-java `opentelemetry-exporter-otlp` (`OtlpHttpSpanExporter`, `BatchSpanProcessor`).
- otel4s-sdk-exporter.
- opentelemetry-java trace propagators extension; Brave B3.

### 5.7 `jwt` (`zio-blocks-jwt`)

**Purpose.** Zero-dependency, cross-platform JWT (JWS compact) signing and verification. Parsing is strict and size-limited, the crypto backend is pluggable, and there is a pure-Scala HMAC/SHA-2 fallback.

**Key types / entry points** (`jwt/shared/src/main/scala/zio/blocks/jwt/`)
- `Jwt` (`Jwt.scala`, 1142 lines):
  - `sign(claims, key: Array[Byte], alg[, header | options | backend])` returns `Either[JwtError, String]`.
  - `decode(token, key, alg, …)` / `decode(token, key, alg, JwtDecodeOptions)` verify the signature before parsing the payload.
  - `decodeWithoutVerification(token, limits)`.
- Options and limits: `JwtDecodeOptions(issuer, expectedAudience, clockSkewSeconds, limits, nowSeconds)`, `JwtSignOptions`, `JwtLimits` (token, segment and JSON chars; depth; fields; array elements).
- Claims and header:
  - `JwtClaims(iss, sub, aud, exp, nbf, iat, jti, extra: Map[String, JwtValue])`.
  - `JwtAudience.Single/Multiple(Chunk)`.
  - The `JwtValue` JSON ADT.
  - `JwtHeader(alg, typ, kid)`.
- `Algorithm`: HS256/384/512, RS*, PS*, ES*, EdDSA.
- `JwtError` (15 cases, including `InvalidClaim`, `AlgorithmMismatch`, `InvalidKey`, `TokenTooLarge`, `TooDeep`).
- Crypto backends:
  - `JwtCrypto.install`, plus `compareAndSet` / `getAndSet` for the global atomic backend.
  - JVM: `JvmJwtCryptoBackend.install()`. Keys are PKCS8 DER (private) and X.509 SPKI DER (public) (`jwt/jvm/.../JvmJwtCryptoBackend.scala:185-188`).
  - JS: `JsJwtCryptoBackend.install()` for Node crypto. PS* and EdDSA are unsupported on JS.
- **Not present:** JWK/JWKS, PEM parsing, JWE, `kid`-based key resolution.

**Dependencies.**
- Depends on `chunk` only. JVM + JS; Scala 2.13 / 3.x.
- **No zio-blocks module uses it.** `endpoint`'s `AuthType.Bearer` (`endpoint/shared/.../AuthType.scala:82`) produces a header but never verifies a JWT.

**Use cases** (tests)
- HS256 session/API tokens, checked against jwt.io vectors.
- RS/PS/ES/EdDSA tokens with JVM `KeyPairGenerator` keys (`JwtJvmSpec`).
- exp/nbf checks with clock skew and a deterministic `nowSeconds` for tests.
- Issuer and audience checks.
- Custom nested claims such as roles.
- Hardening against hostile tokens: size, depth and field limits, and "deep malformed arrays cannot StackOverflow".
- Algorithm-confusion protection.
- Weak-key rejection: HMAC minimums, RSA keys of at least 2048 bits, EC curve checks.
- HMAC-only browser builds (`JwtJsSecuritySpec`).

**Docs.** `docs/reference/jwt.md` (180 lines), partial. It has no frontmatter and starts with `# JWT`. Problems:
- **Inaccurate:** it says an audience mismatch yields `InvalidToken`, but the code returns `JwtError.InvalidClaim` (`Jwt.scala:451-453`).
- `InvalidClaim` never appears on the page, and the error table has no `InvalidKey` row.
- No asymmetric-key example and no explanation of key formats.
- `decodeWithoutVerification` and custom backends are undocumented.
- No guide.

**Examples.** None.

**Maturity.**
- A single commit (#1428, 2026-09-01), making it the newest module.
- `mimaPreviousArtifacts := Set()`, with the build comment "JWT is new/unreleased". Coverage 72/70.

**Comparable libraries.**
- jwt-scala (pdi): the closest analogue.
- auth0 java-jwt.
- jjwt.
- jose4j and Nimbus JOSE+JWT: fuller, with JWK, JWE and JWKS (the gaps here).
- tsec-jwt.

### 5.8 `mediatype` (`zio-blocks-mediatype`)

**Purpose.** Type-safe IANA media types:
- about 2,600 predefined constants generated from jshttp/mime-db;
- a `mediaType"..."` interpolator validated at compile time;
- parsing, wildcard matching, and file-extension lookup.

**Key types / entry points.**
- `MediaType(mainType, subType, compressible, binary, fileExtensions, extensions, parameters)` with `fullType` and `matches(other, ignoreParameters)` (`mediatype/shared/.../MediaType.scala`).
- `MediaType.parse`, `unsafeFromString`, `forFileExtension`.
- `MediaTypes`: 12,332 generated lines with objects `application`, `text`, `image`, …, plus `allMediaTypes`.
- The interpolator comes from `import zio.blocks.mediatype._`, with Scala 2 and Scala 3 macros.
- Generator: `project/GenerateMediaTypes.scala`, run as `sbt generateMediaTypes`. Its output is checked in.

**Dependencies.**
- No dependencies. JVM + JS; all Scala versions.
- **Used by `http-model`** (`ContentType`, `Body`, Accept `MediaRange`) **and `endpoint`** (`Endpoint.in(mediaType, schema)`, `HttpCodec.requestBody(mediaTypes = …)`).

**Use cases.**
- Content negotiation with wildcards.
- File-type detection for static serving or uploads.
- Parsing `Content-Type` with charset (`MediaTypeParseSpec`).
- Compile-time-checked constants (`InterpolatorSpec`).

**Docs.** `docs/reference/media-type.md` (460 lines), thorough for the type itself. It has **no cross-links** to `ContentType`, `Header.Accept` / `MediaRange` (q-values) or endpoint `.in`/`.out`.

**Examples.** Only `endpoint-examples/.../HttpCodecConstruction.scala` uses `MediaTypes.application.json`.

**Maturity.** Oldest and most stable module of this group: commits 2026-02 to 2026-03 (#1002 initial). Coverage gate 99/93.

**Comparable libraries.** http4s `MediaType` (also mime-db generated, with an interpolator; near 1:1), akka-http / pekko-http `MediaTypes`, zio-http `MediaType`, sttp-model `MediaType`, Guava `MediaType`, Apache Tika (detection).

## 6. Runtime, resources and concurrency

### Facts that apply to every module in this group

- **Example projects**
  - `scope-examples`, `async-examples`, `mux-examples` and `streams-examples` are standalone builds pinned to the published 0.0.56, on Scala 3.9.0. `scope-examples` also builds for 3.3.7.
  - Ringbuffer examples live in the oddly named `zio-blocks-examples/`.
  - Context examples live in `schema-examples/src/main/scala/context/`.
  - CI compiles every example dir (`project/CiWorkflow.scala:359`).
- **No TODO/FIXME** in any of the 7 modules. The only deprecation is `scope.InStack`.
- **Install snippets are missing the Scala 3.7+ artifact name.** None of the install snippets for these modules show the `zio-blocks-next-*` coordinate used on Scala 3.7+. Only `docs/reference/endpoint/index.md` mentions it. This affects every tutorial's install step.

### 6.1 `scope` (`zio-blocks-scope`)

**Purpose.**
- Compile-time-checked resource lifetimes for synchronous Scala. Each scope tags its values with an opaque `$[A]` type, and finalizers run last-in, first-out (LIFO).
- Constructor-based dependency injection via `Wire` and `Resource.from`.

**Key types / entry points** (`scope/shared/src/main/scala/zio/blocks/scope/`)

- `Scope`
  - `Scope.global`: on the JVM its finalizers run in a shutdown hook; on JS they never run.
  - `scoped(f)`: the result type must have an `Unscoped[A]` instance.
  - `allocate(Resource)` and `allocate[A <: AutoCloseable]`.
  - `open()` returns an explicit `OpenScope`; `close()` returns a `Finalization`.
  - The `$` access macro, with N-ary forms for 2 to 5 arguments.
  - `lower`, `isOwner` for thread ownership, and `leak`, which emits a warning.
- `Resource[+A]` (`Resource.scala:58`)
  - Constructors: `acquireRelease`, `fromAutoCloseable`, `shared` (reference-counted), `unique`.
  - Combinators: `map`, `flatMap`, `zip`, `use`.
  - Macros: `Resource.from[T]` and `Resource.from[T](wires*)`.
- `Wire[-In,+Out]` (`Wire.scala:65`)
  - `Shared` and `Unique` variants.
  - `Wire.shared[T]` and `Wire.unique[T]` macros.
  - `Wire.Shared.fromFunction`, `make(scope, ctx)`.
- `Unscoped[A]`, derivable with `derives Unscoped`.
- `Finalizer` / `defer`, `DeferHandle.cancel()`.
- `Finalization` (`errors: Chunk[Throwable]`, `orThrow`, `suppress`).

**Dependencies.**
- Depends on `context` and `chunk`.
- JVM + JS; all Scala versions.
- **Real users of scope:**
  - `config`, via `Config.wire`.
  - `streams`, via `fromResource` and JVM `start(implicit scope)`.
- **Nominal only:** `sql` and `projection` declare scope as a dependency but never import it. `projection` uses ZIO's `Scope` instead.

**Use cases** (examples and tests)
- DB connection lifecycle and connection pools.
- Temp files.
- Transaction boundaries.
- HTTP client pipelines.
- A shared, cached logger.
- A layered web service with dependency injection.
- A config reader.
- A plugin architecture.
- Legacy `AutoCloseable` interop.
- An integration-test harness.
- Thread ownership.
- Breaking circular dependencies.
- The macro error catalog E1-E17 (`WireMacroErrorSpec`, `ErrorMessagesSpec`).

**Integration points.**
- `Wire.make(scope, Context[In])`: `scope/shared/src/main/scala-3/zio/blocks/scope/internal/WireCodeGen.scala`.
- `Config.wire[A]: Wire.Shared[ConfigSource, A]`: `config/shared/src/main/scala/zio/blocks/config/Config.scala:31`; documented at `wire.md:507`.
- `Stream.fromResource` (`Stream.scala:3158`).
- `Async.Running` is an `AutoCloseable`, so `Scope.allocate` can manage it. This is inferred only; there is no test or example.

**Docs.**
- Reference pages are thorough:

  | Page | Lines |
  |---|---:|
  | `scope.md` | 1300 |
  | `resource.md` | 832 |
  | `wire.md` | 579 |
  | `finalization.md` | 286 |
  | `defer-handle.md` | 246 |
  | `finalizer.md` | 167 |
  | `unscoped.md` | 125 |

- **Guide:** `compile-time-resource-safety-with-scope.md` (1002 lines, a 12-section tutorial).
- Gaps:
  - `scope.leak` is undocumented.
  - `unscoped.md` never mentions derivation.
  - There is no catalog of the E1-E17 compile errors.
  - `WireInfo` and `WireKind` are unexplained.
  - There is no comparison with, or migration from, ZLayer, cats-effect `Resource`, macwire or `Using`.
  - Scala.js caveats get a single sentence.

**Examples** (`scope-examples/src/main/scala/`)
- `scope/examples/`: `DatabaseConnectionExample`, `ConnectionPoolExample`, `TempFileHandlingExample`, `TransactionBoundaryExample`, `HttpClientPipelineExample`, `CachingSharedLoggerExample`, `LayeredWebServiceExample`, `ConfigReaderExample`, `PluginArchitectureExample`, `ThreadOwnershipExample`, `CircularDependencyDemoExample`, `LegacyLibraryInteropExample`, `IntegrationTestHarnessExample`, `ScopedForComprehensionExample`.
- `wire/`: `WireBasicExample`, `WireFromFunctionExample`, `WireSharedUniqueExample`.
- `com/example/external/LeakFromExternalPackageTest.scala`.

**Maturity.**
- Stable. Notable changes: a lock-free Treiber-stack finalizer that made Scope 3-5x faster (#1212), and N-ary `$` (#1193).
- `scope-benchmarks` measures internal costs only; there is no competitor comparison.

**Comparable libraries.**

| Library | Corresponds to |
|---|---|
| cats-effect `Resource` | `Resource` |
| ZIO `Scope` / `acquireRelease` | `Scope`, `Resource.acquireRelease` |
| ZIO `ZLayer`, macwire `wire[T]`, distage | `Wire`, `Resource.from` |
| `scala.util.Using`, scala-arm, try-with-resources | `Resource.use`, `allocate[AutoCloseable]` |

### 6.2 `context` (`zio-blocks-context`)

**Purpose.** `Context[+R]` is an immutable, type-indexed heterogeneous map. Its phantom intersection type `R` records which types are present, and lookups are cached. It carries dependencies for `Wire`.

**Key types / entry points.**
- `Context` (`context/shared/src/main/scala/zio/blocks/context/Context.scala:32`)
  - `Context.empty`, `Context(a1..a10)`.
  - `add[A]` returns a `Context[R & A]`.
  - `get[A >: R]` also matches supertypes.
  - `getOption`, `update`, `++`, `prune`.
  - Every key requires an `IsNominalType[A]` instance.
- `ContextHas[Ctx, A]`: compile-time evidence, added in #1483.

**Dependencies.**
- Depends on `typeid`.
- Used by `scope` (Wire), `otel` (`OtelContext`) and the `config` tests.
- `telemetry` declares context as a dependency but does not import it.

**Use cases.**
- A DI carrier for `Wire`.
- Threading request or server context.
  - `ContextHas`'s scaladoc shows a `serve[ReqCtx, Ctx]` signature. **No such function exists** in the repo; it is aspirational.
- OTel span-context threading.

**Docs.**
- `context.md` (553 lines) includes a comparison with `ZEnvironment`.
- **`ContextHas` is undocumented.**
- `context.md:484` calls a non-existent companion method `Wire.make[...]`. The code is labelled pseudocode.
- No guide.

**Examples.** `schema-examples/src/main/scala/context/`: `ContextConstructionExample`, `ContextRetrievalExample`, `ContextModificationExample`.

**Maturity.** Small (865 LOC) and stable. `ContextBenchmark` lives in `scope-benchmarks`.

**Comparable libraries.** ZIO `ZEnvironment` (closest), shapeless `HMap`, distage `Locator`, a `Map[Class[_], Any]` service locator, Kyo `Env`.

### 6.3 `combinators` (`zio-blocks-combinators`)

**Purpose.** Typeclasses, resolved at compile time, that compose and decompose:
- tuples, with flattening;
- eithers, canonicalised to left-nested form;
- Scala 3 unions;
- "concat" result types.

They are plumbing for other modules' type signatures.

**Key types / entry points** (`combinators/shared/src/main/scala*/zio/blocks/combinators/`)
- `Tuples.Tuples[L,R]`: `combine`, `separate`, and the `Flatten` match type.
- `Eithers.Eithers[L,R]`.
- `Unions.Unions[L,R]`: Scala 3 only.
- `Concat[L,R]` (`Concat.scala:42`), in shared code. Its result type is chosen in this order:
  1. the same type;
  2. a supertype;
  3. a meaningful least upper bound;
  4. otherwise `Either` on Scala 2, or `L | R` on Scala 3.

**Dependencies.**
- None.
- Used by:
  - `streams`: `Stream#&&` uses `Tuples`; `Stream#++` uses `Concat` for both the error and value types (`Stream.scala:56-90`).
  - `async`: `Async#orElse` uses `Concat`.
  - `endpoint`: path, query and error type accumulation.

**Use cases.** Typed builders that accumulate flattened tuples, and merging error channels. Tests: `TuplesSpec`, `EithersSpec`, `UnionsSpec`, `ConcatSpec`, `LargeTupleSpec`.

**Docs.** `combinators.md` (524 lines) is **stale**:
- It documents a **`Choices` module that does not exist** in the source (verified).
- It calls `Concat` "Scala 2 Only".
- It writes `Concat.Concat.WithOut` where the real name is `Concat.WithOut`.
- Its "Integration Points" section doesn't name the real consumers.

There are no examples and no guide. A how-to is probably low priority; correcting the reference page matters more.

**Comparable libraries.** shapeless `Tupler`/`Prepend`, Scala 3 `Tuple.Concat`, ZIO/zio-http `Zippable`/`Combiner` (closest), tapir `ParamConcat`.

### 6.4 `async` (`zio-blocks-async`)

**Purpose.** `Async[A]` is a small, **eager**, allocation-free asynchronous effect for JVM and Scala.js, on Scala 2.13 and 3.x.
- A value is ready, failed with a `Throwable`, or suspended on a `Pollable`.
- It supports direct-style `Async.async { x.await }`, callback bridging, and cancellable `start`.

**Key types / entry points** (`async/shared/src/main/scala/zio/blocks/async/`)
- `type Async[+A]` (`package.scala:46`).
- `Async.succeed / fail / attempt / promise`.
- `Async.async { … .await }`, implemented three ways:
  - dotty-cps-async on Scala 3;
  - macros on Scala 2;
  - a native backend on JS for Scala 3.8.
- `start`, `collectAll`, `selector`.
- Syntax: `map`, `flatMap`, `catchAll`, `mapError`, `foldCause`, `either`, `zip`, `tap`, `ensuring`, `orElse`, `block`, `await`.
  - On JS, `block` throws if the value is still pending.
- `Completer[A]`, `Pollable[A]` (the suspension SPI), `Async.Running` / `Cancelable` (which extends `AutoCloseable`), `AsyncSelector`.
- Interop:
  - JVM: `fromFuture`, `fromCompletionStage`, `toFuture`, `toCompletableFuture`.
  - JS: `fromJsPromise` / `toJsPromise`.

**Dependencies.**
- Depends on `combinators`.
- Used by `streams` (every `*Async` terminal returns `Async[Either[E,Z]]`) and by `http-model` (`Body.textAsync`).
- `streams` and `async` are also cross-tested against Scala 3.8.3 for the external golemcloud/golem Scala SDK (the `golemStreamsAsyncVersion` alias).

**Use cases.**
- An order-fulfilment pipeline (`AsyncShowcaseExample`).
- Bridging callback APIs.
- Direct-style sequential code.
- Fork/join with cancellation.
- Future/CompletionStage interop for gradual adoption.
- Fan-out with `collectAll`.
- Bracketing with `ensuring`.

**Docs.**
- `async.md` (1499 lines) is thorough.
- **Guide:** `async-getting-started.md` (417 lines, a tutorial). It skips:
  - interop;
  - `ensuring`, `collectAll`, `zip`;
  - custom `Pollable`;
  - `AsyncSelector`.
- Missing how-tos:
  - wrap a callback or NIO library as a `Pollable`;
  - migrate from `Future`;
  - Scala.js: never block.

**Examples.**
- `async-examples/src/main/scala/async/AsyncShowcaseExample.scala` (332 lines).
- `async-examples/src/main/scala/zio/blocks/async/gettingstarted/`: `ReadyValues`, `ErrorHandling`, `DirectStyle`, `Attempt`, `CallbackBridge`, `Forking`, `Complete`.
- The showcase's run instruction refers to the in-repo `golemStreamsAsyncVersion` alias, which looks stale for a standalone build (unverified).

**Maturity.**
- **Very new**: 2 commits (#1496 and #1672).
- `async-benchmarks` compares against `scala.concurrent.Future`, cats-effect `IO` and Kyo.
- There are also JS and Scala 2 macro benchmark projects.

**Comparable libraries.**
- `scala.concurrent.Future` / `Promise`: closest, also eager; `async.md:171` makes this comparison.
- cats-effect `IO`, ZIO, Kyo: lazy.
- `CompletableFuture`, JS `Promise`.
- dotty-cps-async, scala-async.
- Ox, gears.

### 6.5 `ringbuffer` (`zio-blocks-ringbuffer`)

**Purpose.** Bounded, lock-free, cache-line-padded ring buffers in four shapes (SPSC, SPMC, MPSC, MPMC), plus zero-boxing primitive SPSC variants. They are the concurrency substrate under `mux` and the JVM `streams` concurrency layer.

**Key types / entry points.**
- Location: `ringbuffer/jvm/src/main/scala/zio/blocks/ringbuffer/`. The JS versions are simpler.
- `SpscRingBuffer` (FastFlow algorithm), `SpmcRingBuffer`, `MpscRingBuffer` (in the style of JCTools), `MpmcRingBuffer` (sequence-stamped).
- Operations: `offer`, `take`, `drain(consumer, limit)`, `fill(supplier, limit)`.
- **JVM-only** primitive variants: `Int/Long/Float/DoubleSpscRingBuffer`, with `offerDone()` and `pollPacked()`.
- Capacity must be a power of two. Nulls are rejected.

**Dependencies.**
- None.
- Used by:
  - `mux` JVM: `PlatformMux` keeps two `MpscRingBuffer(256)` per stream.
  - `streams` JVM: the `Concurrent*Reader` internals, plus the public wrappers `zio.blocks.streams.queues.Blocking{Spsc,Mpsc,Mpmc}Queue`.

**Use cases.** Producer/consumer ping-pong, batch fill/drain, multi-producer aggregation, work distribution, event loops.

**Docs.**
- `docs/reference/ringbuffer/*.mdx`: 6 pages with interactive diagrams, including a comparison with `ConcurrentLinkedQueue` and Disruptor.
- `advanced.mdx`'s "Integration" section **does not mention that `mux` and `streams` are built on it**.
- The `Blocking*Queue` wrappers are undocumented (0 hits).
- No benchmark numbers are published.
- No guide.

**Examples.** `zio-blocks-examples/src/main/scala/ringbuffer/`: `SpscExample`, `BatchExample`, `MpscExample`, `MpmcExample`.

**Maturity.**
- Stable after a JMH optimisation campaign (#1458).
- `ringbuffer-benchmarks` compares against **JCTools 4.0.7** Spsc/Mpsc/Spmc/MpmcArrayQueue.

**Comparable libraries.** JCTools (direct ancestor), LMAX Disruptor, Agrona queues, `ArrayBlockingQueue` / `ConcurrentLinkedQueue`.

### 6.6 `mux` (`zio-blocks-mux`)

**Purpose.** A protocol-agnostic multiplexer for ID-keyed bidirectional streams over one transport (HTTP/2, QUIC, multiplexed WebSocket). It provides:
- a stream registry;
- capacity limits;
- per-stream inbound and outbound FIFO queues;
- a half-close state machine.

**Key types / entry points.**
- `Mux[Id, In, Out]` (`mux/shared/src/main/scala-3/zio/blocks/mux/Mux.scala:31`)
  - `Mux(capacity)`.
  - `open(id)` returns `MuxStream | MuxError` on Scala 3 and `Either` on Scala 2.
  - `get`, `cancel(id, reason)`, `closeAll`, `activeCount`.
- `MuxStream`
  - User side: `send` and `receive()`, which returns `Option[Out] | MuxError`.
  - Protocol side: `offerInbound` and `takeOutbound`.
  - Lifecycle: `halfClose`, `signalRemoteClose`, `close`.
- `MuxError`: `StreamClosed`, `CapacityExceeded`, `QueueFull`, `Cancelled`, `MuxClosed`, `ProtocolError`.
- Concurrency contract: `send` and `offerInbound` are multi-producer safe; `receive` and `takeOutbound` are single-consumer.

**Dependencies.**
- The JVM build depends on `ringbuffer`.
- **No other zio-blocks module uses mux.**
- `receive` is poll-based; there is no `Async` or await API.

**Use cases.** Implementing the stream layer of an HTTP/2- or QUIC-like protocol, isolating logical channels, graceful half-close, RST-style cancellation, and handling back-pressure via `QueueFull`.

**Docs.**
- `mux.mdx` (828 lines) has a diagram.
- **Guide:** `getting-started-with-mux.md` (759 lines, a tutorial).
- Gaps:
  - Half-closed state docs are incomplete.
  - `mux.mdx:37` says "lock-based on JVM", but lines 818-822 say "No `synchronized`".
  - **There is no real transport example**, such as framing over a socket.
  - "See Also" suggests pairing mux with `streams` and `async`, but no code, example or test does this.

**Examples.** `mux-examples/src/main/scala/mux/`: `Example1CreatingAMux` through `Example6ThreadSafety`, plus `CompleteExample`, with a README.

**Maturity.** 3 commits (#1421, #1488, #1491), coverage gate 0, no benchmarks.

**Comparable libraries** (analogies only):
- Netty `Http2MultiplexHandler`;
- yamux and smux (Go);
- the Rust `h2` stream store;
- quinn and quiche stream maps;
- gRPC transport stream management.

### 6.7 `streams` (`zio-blocks-streams`)

**Purpose.** A lazy, pull-based, resource-safe `Stream[+E, +A]`:
- Typed errors, with no effect-system requirement.
- Zero-boxing primitive lanes for `Int`, `Long`, `Float`, `Double` and `Byte`.
- Stack-safe deep pipelines.
- Two execution modes:
  - JVM blocking terminals, which return `Either[E,Z]`;
  - cross-platform `*Async` terminals, which return `Async[Either[E,Z]]`.
- Bounded concurrency: `mapPar`, `flatMapPar`, `mergeAll`, `buffer`.
- NIO and JS `ReadableStream` I/O.

**Key types / entry points** (`streams/shared/src/main/scala/zio/blocks/streams/`)
- `Stream` (`Stream.scala:54`, 11,139 lines)
  - Constructors: `range`, `fromChunk`, `fromIterable`, `unfold`, `fromInputStream`, `fromAcquireRelease(Async)`, `fromResource`, `mergeAll`, …
  - Operators: `map`, `filter`, `flatMap`, `scan`, `mapAccum`, `grouped`, `sliding`, `catchAll`, `ensuring`, `++`, `&&`, `via`, `buffer`, `mapPar`, `flatMapPar`, plus `*Async` variants.
  - Terminals: `runCollectAsync`, `runFoldAsync`, `runForeachAsync`, `startAsync`, …
  - The JVM adds blocking `run*` terminals and `start(implicit scope)`.
- `Sink` (`Sink.scala:37`): `collectAll`, `foldLeft`, `foreach`, `head`, `createAsync`, `createBoth`, `fromOutputStream`, …
- `Pipeline` (`Pipeline.scala:35`): `andThen`, `andThenSink`.
- Low-level types:
  - `io.Reader`: a pull primitive using a sentinel protocol.
  - `io.Writer`.
- JVM NIO: `NioStreams`, `NioSinks`, `NioReaders`, `NioWriters`, `AsyncNioReaders`.
- JS: `ReadableStreamReaders`.
- JVM `queues.Blocking*Queue`.

**Dependencies.**
- Depends on `scope`, `chunk`, `combinators`, `ringbuffer` and `async`.
- Downstream:
  - `sql`: `Frag.queryStream`/`queryChunked` return `Stream[Throwable, Chunk[A]]`.
  - `http-model`: `Body` wraps `Stream[Nothing, Byte]`.
  - The external Golem SDK.
- **The sentinel performance policy** (`AGENTS.md:28-38`) forbids per-element work in the primitive-lane loops. A sentinel colliding with EOF is an accepted, documented edge case.

**Use cases** (examples and 145 test files, including law specs, `ResourceLeakSpec` and `AsyncSoakSpec`)
- Binary telemetry ingestion over NIO.
- Segmented ledger ingest (`mergeAll` + `mapPar` + `Writer`).
- Mixed-feed audits.
- Deep pipeline recovery.
- Scientific computing on `Double` lanes.
- Windowing.
- Resource-managed file reading.
- Async order pipelines.
- Parallel async children.
- Manual pull with `start`.

**Docs.**
- The reference is thorough (about 8,449 lines):

  | Page | Lines |
  |---|---:|
  | `index.md` | 741 |
  | `core/stream.md` | 2426 |
  | `core/sink.md` | 930 |
  | `core/pipeline.md` | 560 |
  | `primitives/reader.md` | 1533 |
  | `primitives/writer.md` | 722 |
  | `async-execution.md` | 662 |
  | `zero-boxing.md` | 393 |
  | `platform-differences.md` | 297 |
  | `scala-2-compatibility.md` | 88 |

- **No guide or tutorial.** `undocumented-report.md:358` flags this.
- Other gaps:
  - `Blocking*Queue` is undocumented, and NIO reader/writer coverage is thin.
  - `index.md:78` lists Kyo RC6 and Ox 1.0.6, but the build pins RC7 and 1.1.0.
  - The comparison cells are mostly "Not verified here".
  - There is no user-facing how-to on the sentinel/EOF caveat.
  - No example uses `Stream.fromResource`, and no guide explains `fromResource` vs `fromAcquireRelease`.

**Examples** (`streams-examples/src/main/scala/`, 38 files)
- `stream/`, 17 files, including:
  - `StreamBasicUsageExample`, `StreamFlatMapExample`, `StreamErrorHandlingExample`, `StreamResourceExample`, `StreamWindowingExample`, `ManualPullUsingStart`, `StreamAsync*Example`, `MapParAsyncExample`, `FlatMapParAsyncChildrenExample`;
  - three walkthroughs: `DeepTelemetryRecovery`, `MixedFeedAudit`, `SegmentedLedgerIngest`.
- `sink/` (8): including `SinkScientificComputing`, `SinkTelemetry`, `SinkSentinelGuard`, `SinkSentinelLimitation`.
- `pipeline/` (4).
- `reader/` (3).
- `writer/` (5).
- `nio/` (2): `NioBinaryTelemetryWalkthrough`, `AsyncChannelReaderExample`.

**Maturity.**
- Very active: #1451 (concurrency), #1458 (union errors and the JMH campaign), #1672 (async execution), #1699.
- `streams-benchmark` compares against **fs2 3.14.0, Pekko Streams 1.7.0, Kyo 1.0.0-RC7 and Ox 1.1.0**. **zio-streams is not benchmarked.**
- `bytecode-allowlist.json` guards against boxing.

**Comparable libraries.**
- fs2.
- ZIO Streams: `ZStream`/`ZSink`/`ZPipeline` map almost 1:1 onto `Stream`/`Sink`/`Pipeline`.
- Akka/Pekko Streams.
- Kyo `Stream`.
- Ox `Flow`: closest in its "no effect system" spirit.
- `java.util.stream`, Reactive Streams / JDK `Flow`.

## 7. Persistence

Build facts that matter across all four modules:
- `sql`, `sql-zio` and `projection` are **Scala 3 only** (3.9.0 and 3.3.7). `dataMigration` also has `crossScalaVersions := 3.9.0, 3.3.7`.
- `sql/js` contains only `.gitkeep`, so there is no JS database backend: on JS only the shared types (`Frag`, `DbCodec`, `Table` rendering) exist. `docs/index.md:153,155` still advertises sql and data-migration as "JVM · JS".
- The `docs` mdoc project depends on `sql.jvm` and `projection.jvm`, but **not** on `sql-zio` or `dataMigration`. Their pages cannot be mdoc-checked today.

### 7.1 `sql` (`zio-blocks-sql`)

**Purpose.** Schema-driven relational database access without an ORM. It provides:
- codecs derived from `Schema[A]`;
- parameterised fragments built with a `sql"..."` interpolator;
- compile-time-derived CRUD repositories;
- a synchronous JDBC transactor with commit, rollback and savepoints.

The core has no effect-system dependency (`docs/reference/sql/index.md:15`).

**Key types / entry points** (`sql/shared/src/main/scala/zio/blocks/sql/`)

Codecs:
- `DbCodec[A]` (`DbCodec.scala:34`):
  - `builder`, `derivedWith`, `jsonb`, `jsonbOption`, `transform`.
  - Scala 3 `derives DbCodec`.
  - Opaque types via `As[A,B]` (`DbCodecOpaqueMacro.scala`); tuples via `DbCodecTupleMacro.scala`.
- `DbParam[A]`.
- `DbValue`, an ADT of about 19 cases.
- `PgCodec` (`sql/jvm/.../PgCodec.scala`) for PostgreSQL JSON and arrays. It is undocumented.

Fragments:
- `Frag` (`Frag.scala:41`): `++`, `Frag.literal`, `sequence`, `values(rows)`, `keysetAfter`.
- Execution: `query[A]`, `queryOne[A]` (returns `Maybe`), `queryLimit`, `update`, `updateReturningKeys`.
- Streaming: `queryStream[A]` / `queryChunked[A](n)` return `zio.blocks.streams.Stream[Throwable, Chunk[A]]` (`Frag.scala:355,396`).
- The interpolator (`SqlInterpolator.scala`) has two forms:
  - `sql"..."` validates quotes and parentheses at compile time.
  - `sql(table)"..."` checks identifiers against tables and suggests "did you mean" fixes.
  - `SqlLiteral` is the escape hatch.

Tables, repositories and upserts:
- `Table[A]` (`Table.scala:32`): `Table.derived[A]`, `derived(name | TableNamingPolicy)`, `createTable(dialect)`, `dropTable`.
- `SqlNameMapper`.
- `@Modifier.config` keys: `sql.table_name`, `sql.inline`, `sql.inline_fields`.
- `Repo[E, ID]` (`Repo.scala:63`):
  - `Repo.derived` finds the ID by `@Modifier.id`, then a unique type match, then a field named `id`, then `<entity>Id`.
  - Operations: `all`, `find`, `findAll`, `exists`, `count`, `pageAfter`, `insert`, `insertReturning`, `insertBatch`, `insertOrUpdate`, `update`, `delete`, `clear`.
- `Upsert` (`Upsert.scala`) builds `ON CONFLICT` statements: `insertDoNothing` / `insertDoUpdate`.

Connections and transactions:
- `Transactor` (`Transactor.scala:65`): `connect(f: DbCon ?=> A)`, `transact(f: DbTx ?=> A)`, `transact(isolation, readOnly)(f)`.
- `JdbcTransactor` (`sql/jvm/.../JdbcTransactor.scala`): `fromUrl`, `fromDataSource`, `postgres(ds)`, `sqlite(ds)`. On SQLite it sets `busy_timeout` and uses IMMEDIATE transactions.
- `DbCon`.
- `DbTx` with savepoints (`transactNested`).
- `TransactionIsolation`, `SqlLogger`.
- The backend SPI: `DbConnection`, `DbResultReader`, `DbParamWriter`.

Dialects:
- `SqlDialect` supports only **PostgreSQL** and **SQLite**.
- `Ddl`.
- `Dialect` (`Dialect.scala`) is a separate, migration-oriented trait (queue, shadow and trigger DDL) used by `dataMigration`. It is undocumented.

Query IR and dumps:
- Query IR in `query/`:
  - `SqlQuery.from(table).innerJoin/leftJoin(Rel).where/groupBy/having/orderBy/limit`.
  - `toFrag`, `sql(dialect)`, `explain(dialect)`.
  - `Rel.manyToOne/oneToMany`, with selector macros.
- Compile-time dumps: `Dump.dumpTable` / `dumpQuery` write `.sql` files when `-Dzib.sql.dumpDir` is set (`Dump.scala`).

**Dependencies.**
- `build.sbt:481` declares `schema`, `scope`, `streams`. **`scope` is unused**: no main source imports it.
- Also uses `maybe`, `chunk`, `typeid`.
- Downstream: `sql-zio`, `dataMigration`, `projection`.
- Test databases: sqlite-jdbc and postgresql.

**Real-world use cases.**
- CRUD repository on SQLite with enums, generated keys and batches (`sql/jvm/src/test/.../RepoIntegrationSpec.scala`, `JdbcTransactorSQLiteSpec.scala`, `RepoInsertManySpec.scala`).
- Idempotent upsert (`RepoUpsertSpec`, `UpsertSpec`).
- Keyset (cursor) pagination, e.g. "walk 100 rows in pages of 7" (`RepoKeysetSpec`).
- Transactions: isolation, read-only, 3-level nested savepoints, inner rollback with outer commit (`TransactorIsolationSpec`, `NestedTransactSpec`).
- HikariCP with virtual threads (prose recipe only, `docs/guides/sql-transactions.md:189-268`).
- Compile-time-checked hand-written SQL (`SqlIdentifierCheckerSpec`; `docs/guides/sql-checked-interpolation.md`).
- DDL generation from a schema (`DdlSpec`, `DDLVerificationSpec`).
- Joins plus `explain` and dumps (`ExplainSpec`, `ExplainDumpGoldenSpec`, `query/QueryRenderSpec`).
- Opaque-type, JSONB and Option/Maybe columns (`DbCodecOpaqueSpec`, `DbCodecSpec`).
- PostgreSQL JSON and arrays (`PgCodecSpec`).
- `Instant` time zones on PostgreSQL (`JdbcInstantTimezoneSpec`).
- Streaming large result sets (`docs/reference/sql/frag.md:182-215`).

**Integration points.**
- `schema`: all derivation goes through `Deriver` / `Reflect`, using modifiers `rename`, `transient`, `config` and `id`.
- `maybe`: `queryOne` and `find` return `Maybe`.
- `streams` + `chunk`: `queryStream` returns a zio-blocks `Stream`. **The sql index page does not mention this.**
- `dataMigration` consumes `Repo`, `Transactor` and `Dialect`.
- `projection` uses `Table.derived` and `JdbcTransactor`.

**Existing docs coverage.**
- **Reference:** 19 pages in `docs/reference/sql/`.
  - Thorough: `index` 306, `db-codec` 643, `repo` 580, `transactor` 345, `table` 334, `db-con` 260, `frag` 235.
  - Small: 11 supporting pages of 55-149 lines.
  - Stub: `db-value` 41.
- **Guides:**
  - `sql-transactions.md` (286 lines, **0 mdoc blocks**).
  - `sql-checked-interpolation.md` (156 lines, reference-like).
  - The Query DSL series. Parts 1, 3 and 4 **don't use `zio.blocks.sql`**: they hand-build SQL strings from schema optics. Part 4 even defines its own `Table[S]`, which collides with `zio.blocks.sql.Table`. Only Part 2's tail (`query-dsl-sql.md:729-1121`) covers sql's `Upsert`, keyset pagination, `SqlQuery` and `Dump`, plus dataMigration's `previewSql`.
- **Gaps:**
  - `SqlQuery` / `Rel` / joins, `Upsert`, keyset pagination and `Dump` have no reference page; they live only in that guide.
  - `PgCodec` and `Dialect` are undocumented.
  - There is no getting-started tutorial, no PostgreSQL setup guide, and no testing guide.
- **Doc/code mismatch:** `sql-transactions.md:39` says the default `transact` is Serializable, but `JdbcTransactor.scala:150-153` keeps the driver's default isolation level.

**Examples available.**
- `sql/shared/src/main/scala/zio/blocks/sql/JoinsDumpExample.scala` ships in *main* sources. It shows a User/Repo/Star join with dumps and contains a stale `sqlChecked` comment.
- `sql-benchmarks/.../RealWorldBenchmark.scala` covers realistic Repo workloads on PostgreSQL.
- There is no sql examples project.

**Maturity signals.**
- About 15 commits since 2026-07-09 (#1224); very active from July to September: #1632 dumps, #1633 upsert/keyset, #1634 transactions, #1635 checked interpolation, #1656 perf.
- 0 TODO. Coverage gate 0; MiMa set to non-failing.
- Explicit limits:
  - composite keyset cursors are deferred to "v2" (`Frag.scala:156`);
  - only two dialects;
  - savepoints throw outside JDBC.
- About 612 tests.

**Benchmarks.** `sql-benchmarks` compares hand-written JDBC (the floor), zio-blocks sql and **kyo-sql** on PostgreSQL 17 (point lookups, list, streamed export, bulk insert). `SqlDecodeBenchmark` measures decode allocation. **No doc page cites these results.**

**Comparable libraries.**
- **Magnum**: the closest match, with near-identical names (`DbCodec`, `Repo`, `DbCon`/`DbTx`, `Transactor`, `Frag`).
- Doobie: `Fragment`, `Transactor`, `Read`/`Write`, `check`.
- ScalikeJDBC and Anorm.
- zio-jdbc.
- Skunk.
- Quill (`SqlQuery` is a much smaller analogue).
- Slick (`docs/reference/sql/table.md:305-317` compares against it).
- kyo-sql.

### 7.2 `sql-zio` (`zio-blocks-sql-zio`, JVM-only, Scala 3)

**Purpose.** ZIO adapter for `sql`. It provides:
- ZLayers that build a `Transactor` from a `DataSource`;
- `TransactorZIO`, which runs JDBC bodies on the blocking pool and brackets connections with `acquireRelease`.

**Key types / entry points.**
- `TransactorZIO` (`sql-zio/src/main/scala/zio/blocks/sql/zio/TransactorZIO.scala`):
  - Blocking wrappers: `connect` / `transact` (and `transact(isolation, readOnly)`) return `Task`.
  - Effectful bodies: `connectZIO` / `transactZIO` take a `DbCon ?=> ZIO[R,E,A]`.
  - Constructors: `fromUrl`, `fromDataSource`, `layer(url, dialect)`.
  - It does **not** extend `Transactor`.
- `JdbcTransactorLayers.scala`: `JdbcTransactor.postgresLayer` and `sqliteLayer`, both `ZLayer[DataSource, Nothing, Transactor]`.

**Dependencies.** `sql.jvm` plus ZIO 2.1.27. The only module that depends on it is `docs`, and not even that (it is absent from the mdoc classpath).

**Use cases.**
- A pooled `DataSource` turned into a ZLayer (`JdbcTransactorLayersSpec`).
- Effectful transaction bodies that roll back on failure (`TransactorZIOSpec`).
- The Postgres "shop" quick start in `docs/index.md:450-467`.

**Integration gaps.**
- **No bridge from `zio.blocks.streams.Stream` to `ZStream`** for `queryStream`.
- `projection` does not use `sql-zio`, yet `docs/reference/projection.md:19-20` tells users to add it.

**Docs.**
- `docs/reference/sql-zio.md` (112 lines) and `docs/reference/sql/transactor-zio.md` (399 lines) overlap. Both have 0 mdoc blocks.
- No guide for:
  - a ZIO service layer (Repo + TransactorZIO + Hikari layer);
  - ZStream streaming;
  - zio-test recipes;
  - choosing between `transactZIO` and `transact`.

**Examples.** None.

**Maturity.** 4 commits (#1224, #1524, #1630, #1634). About 5 tests, coverage gate 0. Leftover `// NEW:` comments.

**Comparable libraries.** magnum-zio (closest), zio-jdbc, Quill-ZIO layers, doobie with zio-interop-cats.

### 7.3 `dataMigration` (`zio-blocks-data-migration`, package `zio.blocks.data.migration`)

**Purpose.** **Online row migration** between typed table versions. This is *not* versioned DDL scripts. It applies a `schema` `Migration[A, B]` to rows read through a V1 `Repo` and writes them through a V2 `Repo`. There are three execution tiers, a dirty-key queue, an optional shadow table with atomic swap, and capture triggers (`docs/reference/data-migration.md:6-25`; ADR `docs/adr/2026-07-18-data-migration.md`).

**Key types / entry points** (`dataMigration/shared/src/main/scala/zio/blocks/data/migration/`)
- `TinyMigrator(transactor)`: override `run()(using DbTx)` with startup DDL, then call `migrate()`.
- `SmallMigrator[A,B,ID1,ID2](repoV1, repoV2, migration, queueTable, batchSize, target, captureTriggers)(using Transactor, DbCodec[ID1], Dialect, ID1 =:= ID2)`:
  - Methods: `init()`, `processBatch()`, `complete()`, `previewSql()`.
  - Deletes target rows that are orphaned.
- `LargeMigrator`:
  - `init` → `fence` → `drain` → `complete`.
  - `pause` / `resume`, `run()`, `pendingCount`, `previewSql()`.
- `TargetStrategy`: `InPlace` or `ShadowTable(suffix)`. Also `ExecutionModel` and `DataVersion`.
- `QueueTable`: `create`, `installTriggers`, `enqueue`, `dequeue` (uses SKIP LOCKED on PostgreSQL), `pending`.
- `ShadowTable` with `create` and `swap`.
- `MigrationMetadata`: the `data_migrations` provenance table. It is **not mentioned** in the reference, and neither are `DataVersion` or `ExecutionModel`.
- `zio.blocks.sql.Dialect.{Postgres, SQLite}` must be in scope as a given. SQLite throws on shadow-table creation.

**Dependencies.** `sql`, `schema`, `maybe`. The build declares JVM + JS, but there is no `js` source directory. The API is synchronous and the caller drives the loop. No ZIO.

**Use cases.**
- Startup DDL (`TinyMigratorSpec`).
- Backfill a new column, e.g. `UserV1` to `UserV2` adding a defaulted `age` (reference lines 200-262).
- Zero-downtime cutover of a large PostgreSQL table: shadow table, capture triggers, then fence, drain and swap (`dataMigration/jvm/src/test/.../PostgresMigrationIntegrationSpec.scala:145-231`). It needs `PGHOST` and is skipped otherwise.
- SQLite single worker: triggers coalesce changes, failure resilience (`MigrationIntegrationSpec`).
- Dry-run SQL preview without a connection (`MigrationPreviewSpec`).
- Pause and resume, with a stalled-queue safety valve (`LargeMigratorSpec`).

**Docs.**
- `docs/reference/data-migration.md` (269 lines, **0 mdoc blocks**) and the ADR (123 lines, 10 decisions; excludes schema diffing, a CLI, composite keys and retries).
- **Probable doc bug:** the "Full Example" omits the `ID1 =:= ID2` evidence, the `transactor` and the `Dialect` import (`SmallMigrator.scala:37`).
- `docs/reference/schema/migration.md` does not link to data-migration.
- **No guide.**

**Examples.** None runnable.

**Maturity.**
- 4 commits; landed in #1534 (2026-08-26).
- 48 tests, coverage gate 0.
- The migration state machine lives in memory and is not persisted (ADR trade-offs).

**Comparable libraries.**
- Not Flyway or Liquibase, though `TinyMigrator` is close to a Flyway Java migration.
- `LargeMigrator` resembles online schema-change tools: gh-ost, pt-online-schema-change, pgroll, Shopify LHM (analogy).
- Rails and Django data migrations and backfill jobs.

### 7.4 `projection` (`zio-blocks-projection`, JVM-only, Scala 3, depends on ZIO)

**Purpose.** An event-sourcing **read-model engine** on ZIO.
- Declarative `Projection[A]` specs map events from named `EventStore`s into entities.
- Each projection is persisted in its own SQLite file (WAL mode).
- A catch-up fiber processes past events, then a `Hub` delivers live events.
- Rebuilds are driven by a schema hash. Event-tag renames go through `Migration`.

**Key types / entry points** (`projection/shared/src/main/scala/zio/blocks/projection/`)
- `Projection[A: Schema: EntityPath](name)` and `Projection.global`:
  - `.from(source)`.
  - Routing: `.routeToSelf`, `.routeToAll` or `.routedBy(f)`.
  - `.on[E]`, then `.insert`, `.delete`, `.custom`, `.aggregate(FieldUpdate*)` or `.update(_.field)(f)`.
- `ProjectionScope`: PerEntity, CrossEntity, Global.
- `ProjectionAction`; `FieldUpdate` (Set, Increment, Decrement, Max, Min).
- `EntityPath` and `@path`; the ID comes from `@Modifier.id`. `@eventTag(n)`.
- `EventStore[E]`:
  - `append`, `readFrom` (returns a `ZStream`), `subscribe` (returns a `Hub`).
  - `SQLiteEventStore.make` and its variants.
- `ProjectionStore`, `SQLiteProjectionStore`.
- `TransactorCache`: an LRU of per-file `JdbcTransactor`s.
- `ProjectionEngine.make / makeWithConfig / makeWithStores`, then `start: ZIO[Scope, …]`, `query(spec, id)`, `registerMigration`.
- `ProjectionEngineConfig`.
- `SchemaHash`, `SchemaEvolution`.
- `TagResolver.resolve[E](migration)` handles `RenameCase` chains.
- Testing kit: `TestEngine.make(...)` with `append` / `query`, and `InMemoryProjectionStore`. Without sqlite-jdbc on the classpath the engine falls back to in-memory stores.

**Dependencies.** `schema`, `sql`, `scope` (it actually uses ZIO's `Scope`, not `zio.blocks.scope`), plus ZIO and zio-streams 2.1.27. It does **not** use `sql-zio`.

**Use cases.**
- Per-entity user profile.
- Cross-entity "repos owned by user X" view.
- Global dashboard counters (`AggregateSpec`).
- Projection schema evolution: rebuild, or an `ADD COLUMN` shortcut (`SchemaEvolutionSpec`).
- Renaming an event type without rewriting history (`TagResolverSpec`).
- Unit-testing projections in memory (`TestProjectionEngineSpec`).
- Per-file SQLite storage and LRU eviction (`projection/jvm/src/test/.../ProjectionStoreSpec.scala`, `TransactorCacheSpec.scala`).

**Docs.**
- `docs/reference/projection.md` (654 lines, 16 mdoc blocks), thorough.
- Mismatches:
  - The API table gives `.update`'s signature wrongly; the real method is `update(selector)(f)` (`Projection.scala:260`).
  - The install block lists the artifact twice and recommends `sql-zio` without reason.
- **No guide.** Missing:
  - production wiring (`ProjectionEngine` + `SQLiteEventStore` + `TransactorCache` in a `ZIOAppDefault`);
  - operations (file layout, backups, rebuilds).

**Examples.** `projection-examples/src/main/scala/projection/examples/ProjectionExamples.scala` (375 lines, a `ZIOAppDefault`) has five demos: PerEntity, CrossEntity, Global aggregate, schema evolution, and tag rename.
- **All five use the in-memory `TestEngine`. None uses SQLite.**
- It is a standalone build using published `zio-blocks-next-projection` 0.0.56. Its run hint at line 32 is probably stale.

**Maturity.**
- 2 commits; landed whole in #1631 (2026-08-30).
- About 663 tests, many of them coverage-padding specs. Coverage gate 75/67.
- Code smells:
  - `SQLiteEventStore.readFrom` reflectively reads a private transactor field (`EventStore.scala:159-164`);
  - leftover "M4:" comments;
  - a `println` in main code.
- Treat as early-stage.

**Comparable libraries.** Akka Projection / Pekko Projection (offsets, grouped handlers), Lagom `ReadSideProcessor`, Axon tracking processors, Marten projections, EventStoreDB projections, edomata / endless (unverified), and zio-kafka or fs2-kafka read-model pipelines.

## 8. HTTP and web UI

### Facts that apply to every module in this section (read first)

**zio-blocks contains no HTTP server or client.**
- A grep for `com.sun.net.httpserver`, `HttpServer.create`, `java.net.http.HttpClient`, `ServerSocket` and a zio-http dependency finds nothing in code.
- The docs say so directly:
  - `docs/reference/datastar/index.md:26`: "it does not ship a server … which you serve with whatever HTTP layer you already have".
  - `docs/reference/endpoint/index.md:23`: an `Endpoint` "can be handed to a ZIO HTTP server … None of that interpretation code lives here".
  - `Endpoint.scala:31`: "Pure data — no `implement*` methods, no `Invocation`".
- Every example only `println`s the values it builds.
- **Consequence:** any end-to-end "serve a page / handle a request" tutorial must bring its own server. The options are zio-http, JDK `com.sun.net.httpserver`, or a pure `Request => Response` function tested in-process. No adapter exists in the repo today.

**Package names deliberately mirror zio-http.**

| Module | Package |
|---|---|
| `http-model` | `zio.http` |
| `http-model-schema` | `zio.http.schema` |
| `datastar` | `zio.http.datastar` |
| `htmx` | `zio.http.htmx` |
| `endpoint` | `zio.blocks.endpoint` |
| `html` | `zio.blocks.html` |

The endpoint types (`HttpCodec`, `RoutePattern`, `PathCodec`, `SegmentCodec`, `AuthType`) match zio-http's Endpoint API by design (`AuthType.scala:23`, `SegmentCodec.scala:254`).

**Nothing in the build depends on `endpoint`, `html`, `datastar` or `htmx`.**

**No guide exists for any module in this section** (confirmed).

### 8.1 `http-model` (`zio-blocks-http-model`, package `zio.http`)

**Purpose.** An immutable, runtime-independent HTTP data model. It covers request, response, URL, headers (a typed catalog), body, cookies, forms and SSE envelopes. It sends and receives nothing.

**Key types / entry points** (`http-model/shared/src/main/scala/zio/http/`)
- `Request`
  - Constructors: `Request.get/post/...`
  - Accessors: `header(codec)`, `contentType`, `cookies`, `queryParams`
  - Builders: `addHeader`, `body(...)`
- `Response`
  - `Response.ok/notFound/...`
  - `Response.text/json/redirect/seeOther`
  - `addCookie`
- `URL`: `URL.parse` (returns `Either`), `/`, `??`, `encode`.
- `Path`, `QueryParams`, `Method`, `Status`, `Version`, `ContentType`, `Charset`.
- `Headers`: `get[A](Header.Codec[A])`, `add`, `set`, `remove`.
- `Header.scala` (1861 lines): 75 typed headers, `Header.Codec`, `Header.Typed`.
- `Body`
  - Wraps `zio.blocks.streams.Stream[Nothing, Byte]` plus a content type.
  - Constructors: `fromString`, `fromChunk`, `fromStream`.
  - Readers: `text`, `toChunk` (may block), and `toChunkAsync`/`textAsync`, which return `Async`.
- `Cookie`, `Form`, `ServerSentEvent[A]` (`render` uses `SseDataEncoder`).

**Dependencies.**
- Depends on `chunk`, `mediatype`, `streams` and `maybe`, plus `async` via `Body`.
- JVM + JS; Scala 2.13 / 3.x.
- Used by `http-model-schema`, `endpoint`, `datastar` and `htmx`.

**Use cases.**
- URL and query parsing (`URLSpec`, `QueryParamsSpec`).
- Typed headers grouped by concern (`http-model/shared/src/test/scala/zio/http/headers/*Spec`).
- Form posts and cookies.
- Async body reading (`BodyAsyncSpec`).
- SSE payloads (`ServerSentEventSpec`).
- Modelling a full exchange in tests or adapters.

**Docs.**

| Page | Lines | Depth |
|---|---|---|
| `http-model/index.md` | 49 | stub |
| `http-model/model.md` | 1509 | thorough |
| `http-model/headers.md` | 660 | thorough |
| `http-model/server-sent-event.md` | 296 | thorough |

- **Wrong artifact name:** `zio-http-model` appears in `model.md:73,79`, `index.md:9`, `datastar/sse.md:233` and `datastar/index.md:237`. The correct name is `zio-blocks-http-model`.
- Open bug #1618: `AcceptEncoding` parses unknown values as `GZip` (`undocumented-report.md:154`).
- Missing guides:
  - using the model with a real server or client;
  - testing handlers as pure functions;
  - streaming bodies and SSE end to end;
  - cookie sessions.

**Examples** (`http-model-examples/src/main/scala/httpmodel/`)
- `BasicHttpRequest`
- `HeadersAndQueryParams`
- `FormAndCookies`
- `CompleteHttpExchange`

**Maturity.** Commits from 2026-03 to 2026-09; strictest coverage gate in the repo (95/94); about 1,112 tests.

**Comparable libraries.**
- zio-http core model (same package and names)
- sttp-model
- http4s-core
- akka/pekko-http model
- JDK `java.net.http`

### 8.2 `http-model-schema` (`zio-blocks-http-model-schema`, package `zio.http.schema`)

**Purpose.** Schema-driven, validated extraction of typed query parameters and headers from http-model values. It also derives `QueryCodec`/`HeaderCodec`, which map a case class to `QueryParams`/`Headers` and back.

**Key types / entry points.**
- Implicit ops on `Request`, `Response`, `Headers` and `QueryParams`:
  - `query[T](key)` returns `Either[QueryParamError, T]`; also `queryAll`, `queryOrElse`.
  - `header[T](name)`, `headerAll`, `headerOrElse`.
- `QueryCodec`/`HeaderCodec`, derived with `Schema[T].derive(DefaultQueryFormat | DefaultHeaderFormat)`.
- `QueryParamError`/`HeaderError`, each `Missing`/`Malformed`.

**Dependencies.** Depends on `http-model` and `schema`. Nothing uses it, and `endpoint` has its own parallel typed-parameter mechanism.

**Use cases.**
- Pagination and filter params with typed errors.
- A typed `X-Request-Id` header.
- Derived codecs (`HeaderCodecSpec`, `QueryCodecSpec`):
  - camelCase fields map to Kebab-Case headers;
  - `Option` means the key may be absent;
  - lists become repeated params;
  - newtypes work;
  - `BindingInstance` overrides work.

**Docs.**
- `http-model/schema.md` (610 lines) and `schema-codecs.md` (463 lines) are thorough.
- Wrong artifact name in `schema.md:59,65`.
- `schema.md:68` says "Scala 3.x only". This is wrong: the module also builds for 2.13.
- No example and no guide (for instance, "validate request input and return 400").

**Maturity.** Coverage gate 67/51.

**Comparable libraries.** zio-http `QueryCodec`/`HeaderCodec`, tapir `Codec` for query and header inputs, http4s `QueryParamDecoder`, Play `QueryStringBindable`.

### 8.3 `endpoint` (`zio-blocks-endpoint`)

**Purpose.** Pure-data, type-safe HTTP endpoint descriptors. An endpoint combines:
- a typed `RoutePattern`;
- request, response and error `HttpCodec`s;
- an `AuthType`;
- a `Doc`.

The module also includes a fast `RouteTree` routing trie. **It has no server, client or OpenAPI interpreter.**

**Key types / entry points** (`endpoint/shared/src/main/scala/zio/blocks/endpoint/`)
- `Endpoint[PathInput, Input, Err, Output, Auth]`
  - Inputs: `.in(...)`, `.query`, `.header`.
  - Outputs: `.out(...)` (9 overloads), `.outHeader`.
  - Errors: `.outError(status, schema)` builds `Either`; `.orOutError` builds a Scala 3 union.
  - `.auth`, `.doc`.
- `HttpCodec`
  - Nodes: `Empty`, `++`, `|`, `Query`, `Header`, `Body`, `StatusCodec`.
  - Auth codecs: `basicAuth`, `bearerAuth`, `digestAuth`.
- `RoutePattern`
  - Built as `Method.GET / "users" / PathCodec.int("id")`.
  - Operations: `decode`, `encode`, `render` (uses the OpenAPI `{name}` convention), `nest`.
- `PathCodec`, `SegmentCodec`: `~` composition; ambiguous combinations are rejected at compile time.
- `RouteTree`: `add`, `get(method, path)` (HEAD falls back to GET), `merge`.
- `AuthType`
  - Variants: `None`, `Basic`, `Bearer`, `Digest`, `Custom`, `Or`, `WithStatus`.
  - The default unauthorized status is **404**, for information hiding.
  - It does not use `jwt`.
- Scala 3.7+ only: the `endpoints { ... }` macro builds a NamedTuple group (`scala-3.7/.../EndpointGroupMacro.scala`).

**Dependencies.**
- Depends on `http-model`, `schema`, `combinators`, `mediatype` and `markdown`.
- JVM + JS; Scala 2.13 / 3.x. Union errors need Scala 3; bulk creation needs 3.7+.

**Use cases.**
- A typed CRUD API with bearer auth, typed errors and a versioned prefix (`CompleteApiDefinition.scala`).
- Routing with `RouteTree` in a hand-written adapter (`RouteTreeSpec`).
- Typed path params: UUID, transform to a domain id, `.unused`.
- Basic-or-Bearer auth; choosing 401 vs 404.
- Bulk-declaring endpoint groups (`EndpointGroupSpec`).

**Docs.**
- 9 pages, 1827 lines total.
- **False claims about OpenAPI** (verified: no interpreter exists):
  - `endpoint/index.md:210`: "`zio-blocks-openapi` consumes `Endpoint` values to generate OpenAPI 3.1 specifications".
  - `endpoint.md:298`: "Documentation attached here flows through to OpenAPI generation".
  - `index.md:33`: "every interpreter (server, client, OpenAPI) updates automatically".
- `undocumented-report.md:256-267` lists open items: `Alternator`, `CanCombine`, `WithStatus`, …
- Missing guides:
  - Interpreting an `Endpoint` (writing a server adapter). There is no `HttpCodec` decode/encode, only path and route decoding.
  - Building an OpenAPI doc from endpoints by hand.
  - Scala 2 vs Scala 3 differences.

**Examples** (`endpoint-examples/src/main/scala/endpointexamples/`)
- `BasicEndpointDefinition`
- `HttpCodecConstruction`
- `PathAndSegmentCodecs`
- `AuthTypePatterns`
- `CompleteApiDefinition` (121 lines: users CRUD, union errors, `nest`, `RouteTree`)
- `scala-3.7/BulkEndpointsExample`

**Maturity.**
- The most active module in this section (2026-05 to 2026-09; #1614 bulk DSL, #1658).
- Coverage gate 0/0.
- A spec named "PathVar backward compatibility (Todo 1)" suggests the API is still in flux.

**Comparable libraries.**
- zio-http Endpoint API (closest; nearly identical vocabulary)
- tapir
- endpoints4s
- smithy4s service descriptors
- http4s `Router`, trie routers

### 8.4 `html` (`zio-blocks-html`)

**Purpose.** Type-safe HTML, CSS and JS templating. It provides:
- a `Dom` ADT and an element/attribute DSL;
- compile-time interpolators `html"..."`, `css"..."`, `js"..."`, `selector"..."`;
- context-aware XSS escaping;
- typed content models;
- jsoup-like CSS-selector querying of a `Dom`.

**Key types / entry points** (`html/shared/src/main/scala/zio/blocks/html/`)
- `Dom` (`Dom.scala`, 1140 lines)
  - Rendering: `render`, `renderMinified`.
  - Traversal: `select(CssSelector)` returns a `DomSelection`; `find`, `transform`.
  - `Dom.Element.Void` enforces void elements at compile time.
  - Typed content-model markers: `Li`, `Tr`, `Td`, `Opt`.
- `import zio.blocks.html._` brings in the whole DSL (`HtmlElements`, `TemplateInterpolators`).
- `Css` ADT, `CssSelector`, `DomSelection`, `Js`.
- `Escape.sanitizeUrl` rewrites `javascript:` URLs to `unsafe:`.
- Type classes: `ToDom`, `ToText`, `ToAttrValue`, `ToCss`, `ToJs`. `ToJs.fromSchema` JSON-encodes any `Schema` value into JS.

**Dependencies.**
- Depends on `chunk` and `schema`.
- JVM + JS; Scala 2.13 / 3.x.
- Used by `datastar` and `htmx`.
- Does **not** depend on `http-model`, so there is no `Response.html(dom)` helper.

**Use cases.**
- Server-rendering pages and components (the `html.md` dashboard example).
- Safe interpolation of user input (`EscapeSpec`, `ContextAwareHtmlSpec`).
- Embedding typed JSON state in a `<script>` (`ToJsSpec`).
- Typed CSS.
- Querying or transforming a DOM in tests (`DomSelectionSpec`).
- Compile-time valid list, table and select structure (`ContentModelsSpec`).

**Docs.**
- `docs/reference/html.md` is a single 1300-line page.
- The undocumented report ranks html as the **#1 gap**: 38% type coverage, 40 of 110 types absent, "code outrunning docs" (`undocumented-report.md:54,96`).
- It never links to `htmx` or `datastar`.
- Candidate for a split into an `html/` directory (see the `docs-split-reference` skill).
- No example project and no guide.

**Maturity.** **Three recent breaking changes** (#1535 void elements, #1536 and #1620 typed content models). Tutorials must use the typed factories. Coverage gate 90/90; about 792 tests.

**Comparable libraries.**
- scalatags (closest)
- zio-http `template`/`template2`
- Laminar (client-side)
- Twirl
- scala-xml literals
- kotlinx.html
- jsoup (for `DomSelection`)
- ScalaCSS

### 8.5 `datastar` (`zio-blocks-datastar`, package `zio.http.datastar`, Scala 3 only)

**Purpose.** A typed Scala SDK for the Datastar hypermedia framework. It has two parts:
- typed `data-*` attributes and signals, rendered into the html DSL;
- builders for Datastar SSE events (patch elements, patch signals, execute script, remove elements), rendered via http-model's `ServerSentEvent`.

**Key types / entry points** (`datastar/shared/src/main/scala/zio/blocks/datastar/`)
- `Signal[A]("name")`: the name is checked at compile time. `:=` produces a `SignalUpdate` using a `Schema`; `.ref`.
- `DatastarEvent`
  - `patchElements(dom)`, with `.selector`, `.mode`, `.viewTransition`, `.renderSSE`.
  - `patchSignals(updates*)`, `removeElements`, `executeScript`.
- `DatastarAttributes`
  - Signals and binding: `dataSignals`, `dataBind`, `dataText`, `dataShow`, `dataClass`.
  - Events: `dataOn` with modifiers, `dataOnInterval`, `dataOnIntersect`.
  - Derived state and indicators: `dataComputed`, `dataIndicator`.
- `ToDatastarExpr` rejects raw `String`s, so expressions must use `js"..."` or a `Signal`.

**Dependencies.**
- Depends on `html` and `http-model` (and `schema` transitively).
- Scala 3.9 / 3.3 only, yet dead `scala-2/` sources remain.

**Use cases.**
- A counter end to end (`datastar/index.md`).
- Derived state.
- Debounce/throttle.
- Fragment patches.
- Live dashboards with polling.
- Lazy loading.

**Missing compared with the official SDKs and zio-http's SDK** (all verified absent by grep):
- request-side `readSignals`;
- a `datastarScript` helper;
- typed `@get`/`@post` actions.

**Docs.**

| Page | Lines |
|---|---|
| `datastar/index.md` | 243 |
| `datastar/signals.md` | 213 |
| `datastar/attributes.md` | 286 |
| `datastar/events.md` | 222 |
| `datastar/sse.md` | 235 |

- 98% type coverage.
- **`docs/reference/datastar.md` (346 lines) is an orphan** left over from the split in #1638. It is not in `sidebars.js` and has no mdoc. It should probably be deleted.
- No example project and no guide. Nothing shows serving a `text/event-stream` `Response` built with `Body.fromStream`, or reading signals back.

**Maturity.** 2 commits (2026-05); untouched since. About 224 tests.

**Comparable libraries.**
- Official Datastar SDKs (Go, TypeScript, Kotlin, …).
- **zio-http-datastar-sdk**: same package name, and it has `readSignals` and `datastarScript`. The obvious candidate for a migration or comparison guide.

### 8.6 `htmx` (`zio-blocks-http-htmx`, package `zio.http.htmx`, Scala 3 only)

**Purpose.** A typed HTMX attribute DSL on top of `html`, plus typed HTMX request and response headers implemented as http-model `Header.Typed`. Raw strings for structured attributes are rejected at compile time.

**Key types / entry points** (`htmx/shared/src/main/scala/zio/http/htmx/`)
- `HtmxAttributes`
  - Requests: `hxGet`/`hxPost`/…
  - Behaviour: `hxSwap`, `hxTrigger`, `hxTarget`, `hxBoost`, `hxPushUrl`, `hxSelect`, `hxSwapOob`, `hxConfirm`, `hxIndicator`, `hxInclude`, `hxParams`, `hxSync`, `hxVals`, `hxHeaders`, `hxEncoding`, …
  - Events: `hxOn.click := js"..."`.
- `HxSwap`: transition, settle, scroll.
- `HxTrigger`: `.delay`, `.changed`, `.once`, `.throttle`, polling, `HxTriggerSet`.
- `HxTarget`, `HxParams`, `HxSync`.
- `HxVals.from[A](value)`: uses a `Schema`.
- `headers/HtmxHeaders.scala`
  - Request headers: `HxRequest`, `HxBoosted`, `HxTarget`, …
  - Response headers: `HxLocation`, `HxRedirect`, `HxRefresh`, `HxReswap`, `HxRetarget`, `HxTriggerHeader`, …

**Dependencies.**
- Depends on `html`, `schema` and `http-model`.
- Scala 3 only.
- Artifact name: `zio-blocks-http-htmx`.

**Use cases.**
- Debounced live search, category filters and pagination (`CompleteExample.scala`).
- Boosted links and forms.
- Returning a fragment vs a full page based on `request.header(HxRequest)`.
- Redirecting or triggering client events after a form submit via response headers (`docs/reference/htmx/response-headers.md:208-221`).
- Typed `hxVals`.
- Compile-time rejection of raw strings (`HtmxCompileTimeSpec`).

**Docs.**
- 10 pages, 2750 lines: the most heavily documented module in this section.
- No guide covering an end-to-end server-rendered HTMX app (serving pages and fragments, OOB swaps, loading `htmx.js`).

**Examples** (`zio-blocks-htmx-examples/src/main/scala/zioBlocksHtmx/`)
- `BasicUsage`
- `AdvancedPatterns`
- `CompleteExample` (e-commerce search UI, prints HTML)

These pin 0.0.56, which may predate html's typed-content-model breaking changes (unverified).

**Maturity.** 1 commit (#1394, 2026-05-10). Coverage gate 85/75; about 73 tests.

**Comparable libraries.**
- htmx4s (eikek)
- scalatags with raw `attr("hx-get")`
- zio-http htmx support (unverified)
- django-htmx (its header helpers correspond to `HtmxHeaders`)
- Go templ + htmx

## 9. Cross-cutting analysis

### 9.1 Module dependency map

The layers below come from the `dependsOn` declarations in `build.sbt`. Read the diagram bottom-up: a module depends only on modules in lower layers. Edges marked `*` are declared in the build but no main source imports anything from the target module.

```
Layer 0  (no zio-blocks deps)
  chunk   combinators   ringbuffer   mediatype   codegen   smithy

Layer 1
  typeid ─────► chunk
  markdown ───► chunk
  jwt ────────► chunk
  async ──────► combinators
  mux ────────► ringbuffer (JVM only)

Layer 2
  maybe ──────► typeid*
  context ────► typeid

Layer 3
  scope ──────► context, chunk
  telemetry ──► context*, chunk*
  schema ─────► typeid, chunk, markdown, maybe

Layer 4
  streams ────► scope, chunk, combinators, ringbuffer, async
  otel ───────► telemetry  (JVM only; OtelContext uses context/typeid transitively)
  html ───────► chunk, schema
  openapi ────► schema, markdown*
  schema-avro / -bson / -thrift (JVM) ─► schema
  schema-csv / -messagepack / -toon / -xml ─► schema
  schema-yaml ─► schema, markdown*
  config ─────► schema, scope, maybe

Layer 5
  config-json ─► config, schema
  config-yaml ─► config, schema-yaml
  config-hocon ► config
  http-model ──► chunk, mediatype, streams, maybe   (+ async via Body)
  sql ─────────► schema, scope*, streams             (Scala 3)

Layer 6
  http-model-schema ─► http-model, schema
  endpoint ──────────► http-model, schema, combinators, mediatype, markdown
  datastar ──────────► html, http-model            (Scala 3)
  htmx ──────────────► html, schema, http-model    (Scala 3)
  sql-zio ───────────► sql (+ZIO)                  (JVM, Scala 3)
  dataMigration ─────► sql, schema, maybe          (Scala 3)
  projection ────────► schema, sql, scope* (+ZIO, zio-streams)  (JVM, Scala 3)
```

Observations that matter for cross-module tutorials:

- **`schema` is the hub.** Twenty-two published modules depend on it directly or transitively (8 format modules, openapi, the 4 config modules, html, http-model-schema, endpoint, datastar, htmx, sql, sql-zio, dataMigration, projection).
- **`streams` is the runtime hub.** `http-model` (`Body`) and `sql` (`queryStream`) both reach it.
- **Several plausible pairings have no code at all:**
  - `openapi` with `endpoint`
  - `jwt` with `endpoint`
  - `telemetry` with `http-model` or `endpoint`
  - `codegen` with `smithy` or `openapi`
  - `mux` with `streams` or `async`
  - `config` with `telemetry`, `jwt` or `sql`
  - `sql-zio` streaming into `ZStream`

  A guide that joins any of these would be writing glue code that does not exist yet. That is a legitimate tutorial topic, but the guide must say it is user-written glue.
- **Some declared dependencies are nominal.** `sql` and `projection` both declare `scope` without importing it, and `projection` uses ZIO's `Scope` instead.

### 9.2 End-to-end scenarios spanning several modules

Each scenario is marked with an **Evidence** level:

- **E:** a runnable example exists in an `*-examples/` directory.
- **T:** tests only.
- **D:** docs only.
- **G:** greenfield. The pieces exist, but nothing in the repo wires them together.

**A. Data and schema**

| # | Scenario | Modules | Evidence | Key paths |
|---|---|---|---|---|
| A1 | One case class, many wire formats (JSON, Avro, TOON, MessagePack, Thrift, BSON, XML, YAML, CSV) | schema + schema-* | D (no example uses a non-JSON format) | `docs/index.md:185-199`; `docs/reference/schema/built-in-codecs/*`; `schema-examples/build.sbt` declares the format deps but no file uses them |
| A2 | Override a codec for one type across all derived codecs | schema (+ any format) | E, T | `schema-examples/src/main/scala/customcodec/CustomCodecOverrideExample.scala`; `schema-csv/src/test/.../DeriverWithInstanceCsvSpec.scala`; `docs/reference/schema/built-in-codecs/bson.md:405-419` |
| A3 | Schema registry: serialize `DynamicSchema`, store it, rebind it to types later | schema + typeid + JSON | E | `schema-examples/src/main/scala/bindingresolvers/BindingResolverSchemaRegistryExample.scala`; `dynamicschema/DynamicSchemaRegistryExample.scala`, `DynamicSchemaSerializationExample.scala` |
| A4 | Schema to JSON Schema to OpenAPI component schemas | schema + openapi + markdown | T | `openapi/shared/src/main/scala/zio/blocks/openapi/package.scala` (`toOpenAPISchema`); `OpenAPIGen.scala`; `SchemaToOpenAPISpec.scala` |
| A5 | Author an OpenAPI 3.1 document in Scala and export JSON, then YAML | openapi + schema JSON + schema-yaml | D (JSON); G (YAML, suggested at `openapi.md:305`) | `OpenAPICodec.scala:1078`; `schema-yaml/.../YamlJsonInterop.scala`. Pitfall: the streaming encode in `OpenAPICodec` is `???` (lines 204-208) |
| A6 | Smithy model to codegen IR to Scala sources (sbt source generator) | smithy + codegen | G (pseudocode only) | `docs/reference/codegen/examples.md:485-516`; `smithy-examples/.../BookStoreAPI.scala:391-420` builds strings by hand |
| A7 | OpenAPI document to codegen IR to Scala DTOs | openapi + codegen | G (pseudocode) | `docs/reference/codegen/examples.md:497-512` |
| A8 | Evolve data V1 to V2 safely: `Into`/`As`, then `Migration` with compile-time completeness checks | schema | E, T | `schema-examples/src/main/scala/{as,into}/*` (10 files, none referenced by docs); `MigrationBuildValidationSpec.scala`; `StructuralMigrationSpec.scala` |
| A9 | Typed query DSL: optics + SchemaExpr to a SQL string, then a fluent builder | schema (+ sql at the end of Part 2) | E (4-part guide series) | `docs/guides/query-dsl-*.md`; `schema-examples/src/main/scala/querydsl*/` |
| A10 | Collaborative-editing change log with JSON Patch (diff, compose, serialize, replay) | schema JSON | E | `schema-examples/src/main/scala/jsonpatch/CompleteJsonPatchExample.scala` |
| A11 | YAML to typed config; YAML and JSON interop | schema-yaml + config-yaml | T | `config-yaml/shared/.../YamlConfigSource.scala`; `YamlJsonInteropSpec.scala` |
| A12 | Store schema `Json` values in MongoDB as semantic BSON | schema + schema-bson | T | `schema-bson/src/test/.../BsonCodecJsonSpec.scala` |
| A13 | Scala 3 named tuples: schema + optics + DynamicValue | schema | T (not documented) | `scalaNextTests/src/test/scala-3/.../SchemaVersionSpecificScalaNextSpec.scala` |
| A14 | Restrict generic APIs to data shapes with `Allows` (CSV rows, events, RDBMS rows, document store, GraphQL tree) | schema comptime | E | `schema-examples/src/main/scala/comptime/*` |

**B. Configuration and dependency injection**

| # | Scenario | Modules | Evidence | Key paths |
|---|---|---|---|---|
| B1 | Layered typed configuration: env over YAML/HOCON/JSON file over defaults, with provenance dump and secret redaction | config + config-yaml/hocon/json + schema | T, D | `ConfigSourceCompositionSpec`; `HoconConfigSourceSpec` "composes with map source"; `docs/reference/config/formats.md:24` |
| B2 | Config-driven DI: `Config.wire[A]` feeds `Resource.from[App](...)` inside `Scope.global.scoped` | config + scope + context + schema | T, D | `config/shared/src/main/scala/zio/blocks/config/Config.scala:31-41`; `ConfigSpec.scala:22,81-92`; `docs/reference/resource-management/wire.md:507` |
| B3 | Wire a layered service graph with `Resource.from` / `Wire` (shared vs unique, diamond dependencies, overrides) | scope + context | E | `scope-examples/src/main/scala/scope/examples/LayeredWebServiceExample.scala`; `wire/*`; `DependencySharingSpec.scala` |
| B4 | Feature flags with percentage rollout and runtime reload (manual timer) | config (flags/rollout) | T, D | `DynamicFlagSpec`, `RolloutSpec`; `docs/reference/config/flags.md`, `rollout.md` |

**C. Runtime, streaming and concurrency**

| # | Scenario | Modules | Evidence | Key paths |
|---|---|---|---|---|
| C1 | Resource-safe streaming of files and sockets (`fromAcquireRelease`, `fromResource`, JVM `start(implicit scope)`) | streams + scope | E (except `fromResource`, which only tests cover) | `streams-examples/src/main/scala/stream/StreamResourceExample.scala`, `ManualPullUsingStart.scala`, `reader/ReaderCompositionExample.scala`; `Stream.scala:3158` |
| C2 | Async stream execution: `*Async` terminals return `Async[Either[E,Z]]`, `mapParAsync`, `flatMapPar` | streams + async | E | `streams-examples/.../StreamAsync*Example.scala`, `MapParAsyncExample.scala`, `FlatMapParAsyncChildrenExample.scala`; `docs/reference/streams/execution-and-compatibility/async-execution.md` |
| C3 | Parallel ingest pipeline with `mergeAll` + `mapPar` + `Writer` on ring-buffer-backed operators | streams + ringbuffer + scope | E | `streams-examples/.../SegmentedLedgerIngestWalkthrough.scala`; `streams/jvm/.../internal/*Concurrent*Reader.scala` |
| C4 | Zero-boxing binary I/O over NIO channels and ByteBuffers, with the sentinel guard | streams (JVM NIO) | E | `streams-examples/src/main/scala/nio/NioBinaryTelemetryWalkthrough.scala`, `sink/SinkSentinelGuardExample.scala`; `AGENTS.md:28-38` |
| C5 | Bridge callback, Future, CompletionStage or JS Promise APIs into `Async`, and cancel them | async (+ scope via `AutoCloseable`, inferred) | E | `async-examples/src/main/scala/async/AsyncShowcaseExample.scala`; `async/jvm/.../AsyncInterop.scala` |
| C6 | Multiplexed protocol over one transport (HTTP/2-style framing, half-close, back-pressure) | mux + ringbuffer | E for mux alone; G for a real transport | `mux-examples/*`; `mux/jvm/src/main/scala-3/zio/blocks/mux/PlatformMux.scala` |
| C7 | Mux channels exposed as `Stream`s with async receive | mux + streams + async | G (suggested in `mux.mdx` "See Also") | — |
| C8 | Union and tuple-typed composition of streams and errors | combinators + streams/async | E | `streams-examples/.../MixedFeedAuditWalkthrough.scala` (`Stream[Nothing, Int \| String]`); `Stream.scala:56-90` |

**D. Persistence**

| # | Scenario | Modules | Evidence | Key paths |
|---|---|---|---|---|
| D1 | CRUD repository from a case class on SQLite: `Table.derived`, `Repo.derived`, `JdbcTransactor`, DDL generation | schema + sql + maybe | T, D (no example project) | `sql/jvm/src/test/.../RepoIntegrationSpec.scala`, `JdbcTransactorSQLiteSpec.scala`; `docs/index.md:404-467` |
| D2 | Stream large result sets: `queryStream`/`queryChunked` into a zio-blocks `Stream` | sql + streams + chunk | D | `sql/shared/src/main/scala/zio/blocks/sql/Frag.scala:355-399`; `docs/reference/sql/frag.md:182-215` |
| D3 | ZIO service layer: `TransactorZIO` + Hikari `DataSource` ZLayer + `Repo` | sql + sql-zio + ZIO | T, D | `sql-zio/src/test/.../JdbcTransactorLayersSpec.scala`; `docs/guides/sql-transactions.md:189-268` |
| D4 | Zero-downtime online data migration: `Migration[A,B]` applied by `SmallMigrator`/`LargeMigrator` with queue, shadow table and triggers | schema + sql + dataMigration | T, D | `dataMigration/jvm/src/test/.../PostgresMigrationIntegrationSpec.scala:145-231`, `MigrationIntegrationSpec.scala`; `docs/reference/data-migration.md:200-262`; ADR |
| D5 | Event-sourced read models: `EventStore` → `ProjectionEngine` → per-entity SQLite files, with schema-hash rebuilds and event-tag renames via `Migration` | schema + sql + projection + ZIO | E (in-memory only), T (SQLite) | `projection-examples/src/main/scala/projection/examples/ProjectionExamples.scala`; `projection/jvm/.../SQLiteProjectionStore.scala`, `TransactorCacheLive.scala` |
| D6 | Inspect and dump generated SQL: `explain`, compile-time `Dump`, typed joins with `Rel` | sql | T (an example lives in main sources) | `sql/shared/src/main/scala/zio/blocks/sql/JoinsDumpExample.scala`; `ExplainDumpGoldenSpec.scala` |
| D7 | Bridge `sql` `queryStream` into `ZStream` | sql + sql-zio + streams + zio-streams | G | — |

**E. HTTP and web**

| # | Scenario | Modules | Evidence | Key paths |
|---|---|---|---|---|
| E1 | Typed REST API description: `Endpoint` with schema bodies, typed errors, bearer auth, versioned prefix, `RouteTree` lookups | endpoint + schema + http-model + mediatype + combinators | E (description only; no serving) | `endpoint-examples/src/main/scala/endpointexamples/CompleteApiDefinition.scala` |
| E2 | Pure `Request => Response` router: `RouteTree.get` → `RoutePattern.decode` → `req.query[Int]` → `Response.json` | endpoint + http-model + http-model-schema + schema | G | `RouteTree.scala:56`, `RoutePattern.scala:50`, `http-model-schema/.../RequestSchemaOps.scala:25`, `http-model/.../Response.scala:78-89` |
| E3 | Serve zio-blocks models from a real server (JDK `HttpServer` or zio-http adapter) | http-model (+ endpoint) | G (no server in the repo) | — |
| E4 | Endpoint → OpenAPI document, hand-assembled from `RoutePattern.render` + `toOpenAPISchema` + `Doc` | endpoint + openapi + schema + markdown | G (the docs falsely claim this exists) | `docs/reference/endpoint/route-pattern.md:183-194`; `openapi/.../package.scala:41` |
| E5 | Protect an endpoint with a JWT bearer token: `AuthType.Bearer` + `Jwt.decode` | endpoint + jwt + http-model (+ config for keys) | G | `endpoint/shared/.../AuthType.scala:82`; `jwt/shared/.../Jwt.scala` |
| E6 | Server-rendered HTMX app: html DSL + htmx attributes, a fragment when `HxRequest` is present, response headers such as `HxRedirect` | html + htmx + http-model + schema | E (rendering only), D (headers) | `zio-blocks-htmx-examples/src/main/scala/zioBlocksHtmx/CompleteExample.scala`; `docs/reference/htmx/response-headers.md:208-221` |
| E7 | Reactive Datastar UI: signals + `patchElements`/`patchSignals` rendered as SSE through `ServerSentEvent` and `Body.fromStream` | html + datastar + http-model + streams | D | `docs/reference/datastar/index.md` "A Counter, End to End"; `datastar/.../DatastarEvent.scala:22,67,85`; `Body.scala:153` |
| E8 | "One domain type, many surfaces": the same `Schema` drives endpoint bodies, query/header codecs, `ToJs`, Datastar signals and `HxVals` | schema + endpoint + http-model-schema + html + datastar + htmx | T, D | `ToJs.scala:134`, `Signal.scala:41`, `HxAttributeValues.scala:76`, `QueryFormat.scala` |
| E9 | Media types end to end: `MediaType` → `ContentType`/`Body`, `Accept` `MediaRange` q-values, `Endpoint.in/out(mediaType, schema)` | mediatype + http-model + endpoint | E (partial) | `endpoint-examples/.../HttpCodecConstruction.scala`; `http-model/.../{ContentType,Header}.scala` |

**F. Observability and security**

| # | Scenario | Modules | Evidence | Key paths |
|---|---|---|---|---|
| F1 | Full observability bootstrap: providers, shared `ContextStorage` for log/trace correlation, OTLP exporters, JSON logs, shutdown hooks | telemetry + otel | D (needs code placed in `package zio.blocks.telemetry.otel` because the exporters are private) | `docs/guides/telemetry-guide.md:727-850` |
| F2 | Distributed trace propagation (W3C or B3) through HTTP headers | otel + http-model | D (generic `(Req, String) => Option[String]` only); G with zio-blocks `Headers` | `docs/guides/telemetry-guide.md:971-1012` |
| F3 | Span context carried in `Context[R & OtelContext]` | otel + context + typeid | T | `otel/src/main/scala/zio/blocks/telemetry/otel/OtelContext.scala`; `OtelContextSpec.scala` |
| F4 | Configure exporters and log levels from `config` / `StaticFlag` instead of `sys.env` | config + telemetry + otel | G | — |
| F5 | Issue and verify access tokens (HMAC and asymmetric keys, clock skew, audience/issuer, hostile-token limits) | jwt (+ config `Secret` for keys) | T | `jwt/jvm/src/test/.../JwtJvmSpec.scala`, `JwtJsSecuritySpec` |

### 9.3 Existing guides inventory

`docs/guides/` contains 11 guides plus `index.md`. The sidebar entry for each is in `docs/sidebars.js:436-454`. The Diátaxis column uses the definitions at the top of this file.

| Guide file | Lines | Topic | Module(s) | Diátaxis type | Notes |
|---|---:|---|---|---|---|
| `async-getting-started.md` | 417 | Create, compose and run `Async[A]` | async | **Tutorial** | 12 numbered sections plus "What You've Learned". Embeds `async-examples/.../gettingstarted/*`. Skips interop, `ensuring`, `collectAll`, `Pollable`, `AsyncSelector` |
| `compile-time-resource-safety-with-scope.md` | 1002 | Scope, `$`, Resource, Unscoped, finalizers, `open`, sharing, Wire DI, thread ownership | scope, context | **Tutorial** | Learning objectives and troubleshooting; the most complete tutorial in the repo |
| `getting-started-with-mux.md` | 759 | Create a mux, queues, lifecycle, capacity, thread safety | mux | **Tutorial** | Backed by `mux-examples/*`. No real transport |
| `telemetry-guide.md` | 1130 | Architecture, logging, tracing, metrics, production setup, integration patterns, FAQ | telemetry, otel | **Hybrid**: explanation and deep dive, with how-to parts (Part 5 production setup, Part 6 integration) | Not a beginner tutorial. Part 5 works around private exporters |
| `zio-schema-migration.md` | 1202 | Migrating from ZIO Schema 1.x to ZIO Blocks Schema | schema (plus format deps) | **How-to** (migration from another library) | 0 mdoc blocks. Backed by `schema-examples/ziosschemamigration/*`. Not about DB or data migration (name-collision risk) |
| `query-dsl-reified-optics.md` (Part 1) | 472 | Build query expressions with optics and SchemaExpr | schema | **Tutorial series** (advanced, builds a DSL step by step) | Embeds `querydslexpressions/*` |
| `query-dsl-sql.md` (Part 2) | 1132 | Translate expressions to SQL strings | schema (+ sql reference material appended) | **Tutorial series**. Its tail (lines 729-1121) is misplaced *reference* for sql `Upsert`, keyset pagination, `SqlQuery`, `Dump` and dataMigration `previewSql` | Hand-builds SQL; does not use `zio.blocks.sql` until the tail |
| `query-dsl-extending.md` (Part 3) | 802 | Custom Expr ADT, aggregates, CASE WHEN | schema | **Tutorial series** | `querydslextended/*` |
| `query-dsl-fluent-builder.md` (Part 4) | 1028 | Fluent SELECT, UPDATE, INSERT and DELETE builder | schema | **Tutorial series** | Defines its own `Table[S]`, which collides in name with `zio.blocks.sql.Table` |
| `sql-transactions.md` | 286 | Isolation, read-only, savepoints, HikariCP, virtual threads | sql, sql-zio | **How-to** (with reference tables) | 0 mdoc blocks. Line 39 wrongly says the default isolation is Serializable (`JdbcTransactor.scala:150-153` keeps the driver default) |
| `sql-checked-interpolation.md` | 156 | `sql(table)"..."` identifier checking, limits, escape hatch | sql | **Reference/explanation** more than how-to | Short, feature-page style |

Summary:
- **Tutorials:** async, scope, mux, and the 4-part Query DSL series.
- **How-tos:** zio-schema migration, sql transactions.
- **Hybrid or explanation:** telemetry, sql checked interpolation.
- **Missing:** a whole-library "Getting Started" tutorial. `docs/index.md` is a catalog, and its "Getting Started" section is a few lines (`docs/index.md:24-52`).

### 9.4 Coverage gaps summary

**Modules with no guide at all.** These are ordered roughly by user-facing importance, judged from module size, number of dependents and the evidence above.

| Module(s) | What exists | Most obvious missing guide(s) |
|---|---|---|
| **schema** (core) | 46 reference pages; migration-from-zio-schema and Query DSL guides only | Getting-started tutorial (derive, JSON round trip); architecture explainer; custom Format/Deriver how-to; evolving data (Into/As then Migration); JSON Schema validate/generate; schema registry; diff/patch; validation/newtypes; named tuples |
| **streams** | About 8.4k lines of reference; 38 example files | Tutorial "first streaming pipeline"; how-tos for resource-safe file/socket streaming, parallel processing, async terminals, NIO binary I/O, the sentinel caveat, migrating from fs2/ZIO Streams |
| **http-model + http-model-schema + endpoint** | Thorough reference; examples print values only | Tutorial "describe a typed API"; how-tos for serving with a JDK/zio-http adapter, request validation returning 400, routing with `RouteTree`, auth patterns, hand-building OpenAPI |
| **html + htmx + datastar** | Large reference sets; htmx examples print HTML | Tutorial "build a hypermedia page"; how-tos for an HTMX fragment/full-page pattern, OOB swaps, serving a Datastar SSE stream, reading signals |
| **config (+ yaml/json/hocon)** | 7 thorough reference pages; no examples | Tutorial "typed configuration from env + file"; how-tos for layering, provenance dump, secrets, feature-flag rollout runbook, migrating from Typesafe Config/pureconfig |
| **sql (getting started) / sql-zio / dataMigration / projection** | Thorough sql reference; 2 sql how-tos; single reference pages for the others | Tutorial "first repository on SQLite"; how-tos for Postgres setup, upsert, keyset pagination, joins/explain, streaming results, ZIO layer, zero-downtime backfill, event-sourced read model with SQLite |
| **jwt** | 1 partial page (with errors) | How-tos for issuing/verifying tokens, asymmetric keys, protecting an endpoint, testing with a fixed clock |
| **openapi + codegen + smithy** | Thorough references; smithy has examples | Tutorials or how-tos for generating Scala from a Smithy or OpenAPI model with codegen; exporting OpenAPI JSON/YAML |
| **Format modules** (avro, bson, csv, messagepack, thrift, toon, xml, yaml) | One reference page each; no examples | How-tos for Kafka/Avro payloads, MongoDB persistence, CSV file ingestion, LLM prompts with TOON, RSS/XML parsing, YAML generation |
| **otel** | Reference + telemetry guide Part 5 | How-tos for a local collector + Jaeger, propagation over http-model headers (blocked: see §9.5) |
| **typeid** | Thorough reference + 5 examples | How-to "type-keyed registries / reflecting on types" |
| **chunk, maybe** | Very thorough references | How-tos "Chunk vs Vector/Array and interop", "Maybe vs Option on hot paths" |
| **markdown** | Thorough reference | How-to "render docs to HTML/terminal; attach docs to schemas" |
| **ringbuffer** | Thorough reference + 4 examples | How-to "choose a ring buffer; producer/consumer pipelines" |
| **context, combinators** | Reference only (combinators page is stale) | Low priority; better as sections inside scope/streams/endpoint guides |
| **mediatype** | Thorough reference | How-to "content negotiation and file-type detection in an HTTP handler" (pairs with http-model) |

**Modules that have a guide but with notable holes:**
- **async:** interop and advanced APIs are not in the tutorial.
- **scope:** no comparison or migration from cats-effect `Resource`, ZLayer or macwire.
- **mux:** no real transport.
- **telemetry:** no beginner tutorial.
- **sql:** no getting-started tutorial.

**Cross-cutting documents proposed by `docs/undocumented-report.md:367-375`, none of which exist yet:**
- a whole-library Getting Started;
- an Architecture Overview;
- a zero-dependency and cross-platform contract;
- a Performance guide;
- a Custom codec how-to.

### 9.5 Blockers and prerequisites for specific guides

Some guides need a code change, a release, or an explicit "this is user glue" disclaimer before they can be written:

1. **OTLP export guide.** The exporters are `private[otel]` (verified in `otel/src/main/scala/zio/blocks/telemetry/otel/OtlpJson*Exporter.scala`, `BatchProcessor.scala`). A supported "export to a collector" how-to needs a public constructor first.
2. **Anything that serves HTTP.** There is no server or adapter in the repo, so the guide must bring JDK `HttpServer` or zio-http and write the adapter itself. `endpoint` has no `HttpCodec` decode/encode, only path decoding.
3. **Endpoint → OpenAPI.** No interpreter exists. Either write the glue in the guide or build the feature first. The current docs claim it exists.
4. **sql-zio / dataMigration pages with compiled code.** These modules must be added to the `docs` mdoc classpath (`build.sbt:1698-1735`) before their code blocks can compile.
5. **Examples can only use released APIs** (pinned 0.0.56). Features newer than that release cannot appear in `*-examples/` until the next release; they need mdoc-only snippets until then.
6. **Scala 3-only modules** (sql, sql-zio, dataMigration, projection, datastar, htmx). Tutorials that combine them with schema must target Scala 3.
7. **The `next` artifact prefix** on Scala 3.7+ must appear in every install step.
8. **Telemetry's JDK requirement** (21+ via `-release`; guide says ScopedValue needs JDK 25) must be stated in any telemetry tutorial.
9. **CSV with a custom delimiter via derivation** is not possible: `CsvCodecDeriver` hardcodes `CsvConfig.default`. A TSV how-to must use the low-level reader/writer.
10. **OpenAPI byte/string encoding** throws (`???`). Guides must use `encodeValue(api): Json` and then print the `Json`.

### 9.6 Doc inaccuracies found during research

These matter because they will mislead a guide author who copies from them.

| Location | Problem | Evidence |
|---|---|---|
| `docs/reference/endpoint/index.md:210`, `endpoint.md:298`, `index.md:33` | Claim openapi consumes `Endpoint` / docs flow to OpenAPI | No dependency or import exists (`build.sbt:1030,1249`) |
| `docs/reference/combinators.md:16,42,137,504,517` | Documents a non-existent `Choices` module; says `Concat` is "Scala 2 Only"; uses `Concat.Concat.WithOut` | `combinators/shared/src/main/scala*/` has only Tuples, Eithers, Unions, Concat |
| `docs/reference/context.md:484` | `Wire.make[...]` companion call does not exist | `Wire.scala:180` (instance method) |
| `docs/reference/jwt.md` | Audience mismatch documented as `InvalidToken`; `InvalidClaim` and `InvalidKey` missing; no key-format guidance; no frontmatter | `jwt/shared/src/main/scala/zio/blocks/jwt/Jwt.scala:451-453`; `JvmJwtCryptoBackend.scala:185-188` |
| `docs/guides/sql-transactions.md:39` | Default `transact` said to be Serializable | `sql/jvm/.../JdbcTransactor.scala:150-153` |
| `docs/reference/data-migration.md` (Full Example, line 196) | Missing `ID1 =:= ID2` evidence, `Dialect` given, transactor definition; not mdoc-checked | `dataMigration/shared/.../SmallMigrator.scala:37` |
| `docs/reference/projection.md` | `.update` signature wrong; artifact listed twice; recommends `sql-zio` without reason | `projection/shared/.../Projection.scala:260`; `build.sbt:534-555` |
| `docs/index.md:153,155` | sql and data-migration advertised as JVM + JS | `sql/js/src/main/scala/zio/blocks/sql/.gitkeep` only |
| `docs/index.md:125` | Maybe "backed by `null`" | Replaced by `Absent` in #1532 |
| `docs/index.md:492-502` | Guides list omits the two sql guides | `docs/sidebars.js:436-454` |
| `docs/reference/http-model/model.md:73,79`, `index.md:9`, `datastar/sse.md:233`, `datastar/index.md:237` | Artifact `zio-http-model` should be `zio-blocks-http-model` | `build.sbt:976` |
| `docs/reference/http-model/schema.md:59,65,68` | Artifact name wrong; "Scala 3.x only" wrong | `build.sbt:998` (default Scala versions include 2.13) |
| `docs/reference/schema/format.md:59` | YAML and TOON called `TextFormat` | Both are `BinaryFormat` |
| `docs/reference/schema/built-in-codecs/csv.md:130-147` | Claims custom `CsvConfig` can be passed to derivation | `CsvCodecDeriver.scala:174,181` |
| `docs/reference/schema/built-in-codecs/messagepack.md:162` | "Stream Large Datasets" pattern shows no streaming | — |
| `docs/reference/openapi.md:10-17` | Stale `OpenAPI` signature | `openapi/shared/.../OpenAPI.scala` |
| `docs/reference/streams/index.md:78` | Kyo RC6 / Ox 1.0.6 vs build RC7 / 1.1.0 | `build.sbt:1427-1438` |
| `docs/reference/mux.mdx:37` vs `:818-822` | "lock-based on JVM" vs "No `synchronized`" | Unverified which is correct |
| `docs/reference/datastar.md` | Orphan page, not in sidebar, no mdoc | `docs/sidebars.js:190-195` |
| `schema-bson/README.md`, `schema-thrift/README.md` | bson 5.2.1 vs 5.13.0; Thrift 0.22.0 vs 0.25.0 | `build.sbt` |
| Example file headers (`smithy-examples`, `projection-examples`, `async-examples`) | Run commands refer to in-repo projects; the examples are now standalone builds | Commit `fc78b4b6` |
| `docs/undocumented-report.md` | Says 9 guides; omits dataMigration and projection | — |
| `telemetry/.../ContextStorage.scala:31` | References a non-existent ADR | `docs/adr/` |
| `build.sbt` `telemetryBenchmarks` | Points at a `telemetry-benchmarks/` directory that does not exist | — |

### 9.7 Comparable libraries (index for web research)

This is the list to search for "what tutorials do users of X look for".

| zio-blocks module(s) | Comparable libraries (closest first) |
|---|---|
| schema (core) | zio-schema 1.x, magnolia, shapeless / Scala 3 Mirror, monocle, quicklens, chimney, ducktape |
| schema JSON | jsoniter-scala, zio-json, circe, ujson/upickle, diffson (JSON Patch), networknt/everit (JSON Schema) |
| schema-avro | avro4s, vulcan, zio-schema-avro, avrohugger |
| schema-bson | mongo-scala-driver macros, reactivemongo BSON, zio-schema-bson, mongo4cats |
| schema-csv | kantan.csv, fs2-data-csv, scala-csv, Jackson CSV |
| schema-messagepack | msgpack4z, upickle upack, msgpack-java, zio-schema-msg-pack |
| schema-thrift | Scrooge, Apache Thrift Java, zio-schema-thrift |
| schema-toon | toon4s, official TOON implementations |
| schema-xml | phobos, scala-xml, scalaxb, xtract, advxml |
| schema-yaml, config-yaml | scala-yaml (VirtusLab), circe-yaml, moultingyaml, SnakeYAML, zio-config-yaml |
| openapi | tapir + sttp.apispec, zio-http OpenAPI/OpenAPIGen, swagger-parser, openapi-generator, guardrail |
| codegen | scalameta, treehugger, KotlinPoet/JavaPoet |
| smithy | smithy4s, AWS smithy-model, smithy-translate |
| typeid | izumi-reflect / ZIO Tag, ClassTag/TypeTag, shapeless Typeable |
| chunk | zio.Chunk, fs2.Chunk, ArraySeq/Vector, scodec ByteVector |
| maybe | Option, Kyo Maybe |
| markdown | flexmark-java, commonmark-java, Laika |
| mediatype | http4s MediaType, zio-http MediaType, akka/pekko-http MediaTypes, sttp-model |
| scope | cats-effect Resource, ZIO Scope, scala.util.Using |
| scope Wire + context | ZLayer / ZEnvironment, macwire, distage |
| combinators | ZIO Zippable, shapeless Tupler, tapir ParamConcat |
| async | scala.concurrent.Future, cats-effect IO, ZIO, Kyo, dotty-cps-async, Ox, gears |
| ringbuffer | JCTools, LMAX Disruptor, Agrona |
| mux | Netty HTTP/2 multiplexing, yamux, Rust h2 |
| streams | fs2, ZIO Streams, Pekko/Akka Streams, Kyo Stream, Ox Flow |
| config (+ formats, flags) | pureconfig, ciris, zio-config, Typesafe Config, Hoplite; LaunchDarkly, Unleash, OpenFeature, FF4J |
| telemetry, otel | opentelemetry-java, otel4s, zio-telemetry, log4cats, scribe, logback, zio-logging, Micrometer, Kamon, Prometheus client |
| jwt | jwt-scala, auth0 java-jwt, jjwt, jose4j, Nimbus JOSE+JWT |
| http-model | zio-http model, sttp-model, http4s-core, pekko-http model |
| http-model-schema, endpoint | zio-http Endpoint API, tapir, endpoints4s |
| html | scalatags, zio-http template2, Twirl, Laminar, kotlinx.html |
| htmx | htmx4s, django-htmx, scalatags + htmx |
| datastar | zio-http-datastar-sdk, official Datastar SDKs (Go/TS/Kotlin) |
| sql | Magnum (near-identical API), doobie, ScalikeJDBC, Anorm, zio-jdbc, Quill, Slick, Skunk, kyo-sql (benchmark rival) |
| sql-zio | magnum-zio, zio-jdbc, Quill-ZIO |
| dataMigration | Flyway/Liquibase (contrast), gh-ost, pt-online-schema-change, pgroll |
| projection | Akka/Pekko Projection, Lagom ReadSideProcessor, Axon, Marten, EventStoreDB projections |

## 10. Appendix: non-published projects and example builds

**Non-published sbt projects** (`publish / skip := true`):

| Project | Directory | Purpose / what it compares against |
|---|---|---|
| `root` | `.` | Aggregate |
| `docs` | `zio-blocks-docs/` | mdoc + unidoc; `SourceFile` helper |
| `scalaNextTests` | `scalaNextTests/` | Scala 3.9-only named-tuple schema tests (see §4.12) |
| `benchmarks` | `benchmarks/` | schema JSON vs jsoniter-scala and zio-json; Avro vs avro4s and zio-schema-avro; TOON vs toon4s; optics vs monocle and quicklens; chunk vs ArraySeq |
| `sql-benchmarks` | `sql-benchmarks/` | zio-blocks sql vs raw JDBC and kyo-sql on PostgreSQL 17; decode allocation on SQLite |
| `scope-benchmarks` | `scope-benchmarks/` | Internal Scope, Wiring and Context costs (no competitor) |
| `ringbufferBenchmarks` | `ringbuffer-benchmarks/` | vs JCTools 4.0.7 queues |
| `streams-benchmark` | `streams-benchmark/` | vs fs2 3.14.0, Pekko Streams 1.7.0, Kyo 1.0.0-RC7, Ox 1.1.0 (not zio-streams) |
| `async-benchmarks`, `async-benchmarks-scala2`, `async-benchmarks-js` | `async-benchmarks*` | vs Future, cats-effect IO, Kyo; Scala 2 macro path; Scala.js |
| `telemetryBenchmarks` | `telemetry-benchmarks/` (**missing directory**) | vs scribe 3.15.3 (declared only; not in the root aggregate) |

**No docs page publishes benchmark results** for sql, streams, async or ringbuffer. A "Performance guide" (proposed in the undocumented report) would be the place for them.

**Standalone example builds.** Each pins published 0.0.56 and Scala 3.9.0.

| Directory | Modules | Files | Referenced by docs? |
|---|---|---:|---|
| `schema-examples/` | schema, typeid, context, chunk (declares formats but does not use them) | ~80 | Partially. `as/`, `into/`, `bindingresolvers/`, `customcodec/`, `deriveroverrides/` and `ChunkExample` are unreferenced |
| `scope-examples/` | scope | 18 | Yes (scope reference and guide) |
| `async-examples/` | async | 8 | Yes (async guide) |
| `mux-examples/` | mux | 7 | Yes (mux guide) |
| `streams-examples/` | streams, chunk | 38 | Yes (streams reference) |
| `endpoint-examples/` | endpoint, http-model, schema | 6 | Yes (`endpoint/index.md`) |
| `http-model-examples/` | http-model | 4 | Yes (`model.md`) |
| `projection-examples/` | projection | 1 (5 demos, in-memory only) | Unverified |
| `smithy-examples/` | smithy | 4 | Yes (`smithy.md`) |
| `zio-blocks-examples/` | ringbuffer only | 4 | Ringbuffer pages |
| `zio-blocks-htmx-examples/` | htmx, html | 3 | Yes (`htmx/index.md`) |

**Modules with no example project at all:**
- chunk (except one trivial file), maybe, markdown, mediatype, combinators
- the schema format modules except smithy
- openapi, codegen
- config, config-yaml, config-json, config-hocon
- telemetry, otel, jwt
- http-model-schema, html, datastar
- sql, sql-zio, dataMigration

Of these, only sql has a `JoinsDumpExample` in its main sources.

