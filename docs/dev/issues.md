# ZIO Blocks: issues found while writing docs

Internal list of problems found while writing the tutorials and guides in `docs/dev/backlog.md`. Each entry says where it was found, how it was confirmed, and what the fix is, so it can be filed on GitHub as is. When an entry is filed, add the issue link under its heading. When it is fixed, add the PR and move it to [Resolved](#resolved).

"Verified" means a session reproduced the problem by running code. "From source" means it was found by reading the code and has not been run.

Contents:

- [Library bugs](#library-bugs)
- [Usability traps](#usability-traps)
- [Test and CI](#test-and-ci)
- [Documentation errors](#documentation-errors)
- [Resolved](#resolved)

## Library bugs

### `Schema.fromJsonSchema` treats every property as required, ignoring `"required"`

Filed as [zio/zio-blocks#1776](https://github.com/zio/zio-blocks/issues/1776).

Found 2026-10-20 while writing the how-to guide `docs/guides/generate-json-schema-and-validate-payloads.md` (backlog H-05), on `main` at `32866f71`. The code is the same at tag `v0.0.56`.

**Summary.** The JSON decoder of the `Schema[Json]` returned by `Schema.fromJsonSchema` rejects any object that leaves out a property listed in `"properties"`, even when `"required"` does not list it. A payload that conforms to the JSON Schema (`JsonSchema#check` returns `None`) fails to decode with `missing required field`.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-schema` 0.0.56):

```scala
import zio.blocks.schema._
import zio.blocks.schema.json._

val jsonSchema: JsonSchema = JsonSchema
  .parse("""{
    "type": "object",
    "properties": {
      "title": { "type": "string" },
      "isbn":  { "type": "string" },
      "tags":  { "type": "array", "items": { "type": "string" } }
    },
    "required": ["title"]
  }""")
  .fold(e => throw e, identity)

val payload = """{"title":"Zionomicon"}"""

println(jsonSchema.check(Json.parse(payload).toOption.get))
// None  (the payload conforms)

println(Schema.fromJsonSchema(jsonSchema).jsonCodec.decode(payload))
// Left(zio.blocks.schema.SchemaError: missing required field "isbn" at: .wrapped)

println(Schema.fromJsonSchema(jsonSchema).jsonCodec.decode("""{"title":"Zionomicon","isbn":"x"}"""))
// Left(zio.blocks.schema.SchemaError: missing required field "tags" at: .wrapped)
```

**Expected.** `decode(payload)` returns `Right(json)`: properties that `"required"` does not list may be absent. Only `title` should be required.

**Root cause.** `JsonSchemaToReflect.buildRecord` receives the `required` set but never uses it — the parameter is marked `@scala.annotation.unused` (`schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchemaToReflect.scala:311`). Every property becomes a plain `Term` with no default value and no optional wrapper (`:316-318`), so the derived JSON codec treats each one as a required field. The record constructor already turns a missing register into `DynamicValue.Null` (`:327-328`), but the codec fails with `missing required field` before the constructor runs.

**Impact.** `Schema.fromJsonSchema` is unusable for most real-world schemas, which almost always have optional properties. Its Scaladoc says values are validated against the JSON Schema, but valid values are rejected before that validation runs.

**Test gap.** `SchemaFromJsonSchemaSpec` only decodes objects that contain every listed property, so it never covers an absent optional property.

**Suggested fix.** In `buildRecord`, make each property outside `required` optional, so that the codec accepts its absence: give the term a default value, or wrap it as an optional field. Leave it out of the rebuilt `Json` object when it was absent, so that `Json.fromDynamicValue` does not produce `"isbn": null` and fail `check` against `{"type":"string"}`. Add a `SchemaFromJsonSchemaSpec` test that decodes an object without an optional property.

**Related (minor, same code path).** Errors raised by the `JsonSchema#check` inside `fromJsonSchema` end with ` at: .wrapped`, which exposes the internal wrapper's name in the path (for example `String length 0 is less than minLength 1 at: .title at: .wrapped`).

### schema-toon: `ArrayFormat.Tabular` with a non-primitive field writes output it cannot decode

Filed as #1778. Found 2026-10-20 while writing H-12 (`docs/guides/llm-data-with-toon.md`). Verified on 0.0.56; schema-toon is unchanged since.

**Summary.** With `withArrayFormat(ArrayFormat.Tabular)`, a record list whose records have a non-primitive field (for example `tags: List[String]`) does not fall back to list form, as the `Tabular` Scaladoc says (`ArrayFormat.scala:34-37`). The writer emits a hybrid row such as `Zionomicon,...,2024,[2]:` followed by `- zio` lines. Decoding it fails with `Array count mismatch: expected 2 items but got 0 at: .books.at(0).tags`.

**Root cause.** `deriveSequence` chooses tabular from `element.isRecord` alone (`ToonCodecDeriver.scala:832-835`), not from `hasOnlyPrimitiveFields` (`:845-846`), and `writeTabularValue` (`:1496-1507`) then writes the non-primitive field as a multi-line block inside the row.

**Fix.** Fall back to list form when any field is not primitive, as documented, or reject the configuration at derive time. Add a round-trip test.

### schema-toon: a top-level tabular list cannot be decoded

Filed as #1779. Found 2026-10-20 while writing H-12. Verified on 0.0.56.

**Summary.** A top-level list written in tabular form (`[N]{f1,f2}:`), including one the codec itself wrote, fails to decode with `Expected key:value, no colon found at: .at(0)`. The same table as a record field (`books[N]{...}:`) decodes fine, so encode then decode is not a round trip for a top-level `List[Record]`.

**Root cause.** `deriveSequence.decodeValue` (`ToonCodecDeriver.scala:873-884`) decodes only `values(0)` of each row. No test covers the top-level case.

**Fix.** Decode the whole row for top-level tables. Add a round-trip test for `List[Record]` with `ArrayFormat.Tabular`.

### schema-toon: `withFieldNameMapper` is ignored in tabular headers

Filed as #1780. Found 2026-10-20 while writing H-12. Verified on 0.0.56.

**Summary.** `withFieldNameMapper(NameMapper.SnakeCase)` has no effect on tabular headers: `{title,firstAuthor,...}` is printed unchanged.

**Root cause.** The header is built with `caseNameMapper` instead of `fieldNameMapper` (`ToonCodecDeriver.scala:855`).

### `JsonSchema#check` enforces `pattern` only when the pattern is anchored

Found 2026-10-20 while writing H-05. From source (`schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala:1124`).

**Summary.** JSON Schema says `pattern` matches anywhere in the string (it is not implicitly anchored), so `"pattern": "\\d"` must reject `"abc"`. `check` appears to apply `pattern` only when it is written as `^...$` and to skip it silently otherwise.

**Next step.** Confirm by running `check` with an unanchored pattern against a non-matching value. If confirmed, use a find-anywhere match, and check why the official test suite (said to pass, research §3.4) does not catch it.

### scope: on Scala 3, a closed `AutoCloseable` can escape its scope

Found 2026-10-20 while writing M-05 (`docs/guides/resource-and-di-migration.md`). Verified with mdoc.

**Summary.** On Scala 3, `Unscoped` is derived automatically for any case class whose fields are all `Unscoped`, even when the class extends `AutoCloseable`. So `Resource.fromAutoCloseable(Conn("x")).use(c => c)` compiles and returns a resource that has already been closed. This is a hole in the compile-time escape check that the Scope tutorial and M-05 present as the main advantage over cats-effect `Resource`.

**Fix.** Do not derive `Unscoped` for types that extend `AutoCloseable` (or for types with a `close()` finalizer), or require an explicit opt-in. Add a negative compile test.

### scope: a `Resource.from[T]` stored in a `val` can be used only once

Found 2026-10-20 while writing M-05. Verified with mdoc.

**Summary.** After the first scope that uses `val r = Resource.from[T](...)` closes, a second use throws `IllegalStateException: Cannot allocate from a destroyed shared resource`. A `def` works. Several doc pages teach `val appResource = Resource.from(...)`, so readers hit this.

**Next step.** Decide whether a `Resource` value is meant to be reusable. If yes, reset or recreate the shared state per allocation. If no, document it prominently and change the docs to `def`.

### scope: `Wire(value)` evaluates eagerly

Found 2026-10-20 while writing M-05. Verified with mdoc.

**Summary.** `Wire(new Db(...))` builds the `Db` when the wire is created, not when it is allocated. `wire.md:28` calls a Wire a "lazy recipe". Either make `Wire.apply` take a by-name argument or document the eager behaviour.

### scope: `Wire.Shared.fromFunction` never closes an `AutoCloseable` result

Found 2026-10-20 while writing M-05. Verified with mdoc.

**Summary.** When the function passed to `Wire.Shared.fromFunction` returns an `AutoCloseable`, its `close()` is not registered as a finalizer, so the resource leaks. `Resource.from[T]` registers it; `fromFunction` should too, or document the difference.

### scope: a dependency cycle crashes the macro instead of reporting E10

Filed as #1767. Found 2026-10-20 while writing H-31 (`docs/guides/fix-scope-and-wire-compile-errors.md`). Verified with mdoc.

**Summary.** `class InventoryRepo(p: PriceService); class PriceService(i: InventoryRepo); class Catalog(i: InventoryRepo); Resource.from[Catalog]()` fails with `Exception occurred while executing macro expansion. java.util.NoSuchElementException: key not found: InventoryRepo` instead of the E10 "Dependency cycle detected" error.

**Root cause.** `resolveWire` records each type in `aliasMap` before it recurses, and its `aliasMap.contains` early return runs before the cycle check, so `chain.contains` can never fire. The same in Scala 3 (`ResourceMacros.scala:252-259`) and Scala 2 (`ResourceVersionSpecific.scala`, around lines 354-361).

**Fix.** Run the cycle check before the `aliasMap` early return. `scope-examples/.../CircularDependencyDemoExample` promises a cycle diagram that never appears.

### scope: a dependency on a Java class with constructor parameters crashes the macro

Found 2026-10-20 while writing H-31. Verified with mdoc. Not filed.

**Summary.** `Wire.shared[java.util.ArrayList[String]]` fails with `TypeError: is not a value type, cannot be used in intersection & Int`. `class Catalog(l: java.util.ArrayList[String]); Resource.from[Catalog]()` fails with an `AssertionError` in `MacroCore.computeInType` (an `AndType` of `TypeBounds <FromJavaObject>`). Both are compiler crashes rather than error messages.

**Fix.** Handle Java-defined constructor parameter types in `computeInType`, or reject them with a clear error.

### scope: E12 (duplicate parameter type) is unreachable on Scala 3

Found 2026-10-20 while writing H-31. Verified with mdoc. Not filed.

**Summary.** `class Catalog(primary: Db, replica: Db)` compiles through both `Resource.from[Catalog]()` and `Wire.shared[Catalog]`, and both parameters receive the same `Db` instance (`eq` is true). The user almost certainly wanted two different databases.

**Root cause.** `computeInType` builds `Db & Db`, and `flattenIntersection` simplifies it to `Db`, so the duplicate check never sees two entries.

### scope: the E3 hint prints invalid Scala

Found 2026-10-20 while writing H-31. Verified with mdoc. Not filed.

**Summary.** The suggested wrapper is rendered as `case class WrappedMdocApp2.this.InventoryRepo(value: ...)`, or `case class Wrappedcom.x.InventoryRepo` in a package. `ErrorMessages.renderSubtypeConflict` interpolates the fully qualified type name where it needs the simple name.

### scope: dead and unreachable error codes

Found 2026-10-20 while writing H-31. From source unless marked verified.

- `renderMissingDependency` is never called: missing dependencies are auto-created, or fail with E7 or E8.
- E5 sits behind the E4 check in both Scala versions (verified on Scala 3: a constructor with `(using Finalizer, Clock)` reports E4).
- E6 is reachable only with `???` (verified).
- No Scala trigger was found for E2 or E9 (`Resource.from[java.lang.Object]` compiles).

Either make these reachable or remove them, so the error catalog matches what users can see.

### config: `ProvenanceMap#dump` over an unprefixed `EnvSource` prints the whole environment

Found 2026-10-19 while writing T-05 (`docs/guides/typed-config-loader.md`). Verified.

**Summary.** Dumping provenance for a config that layers a bare `EnvSource` lists every process environment variable. Only names that match the `Sensitive` markers are redacted, so a value like `DATABASE_URL=postgres://user:pass@host/db` is printed in clear text.

**Fix.** Limit `dump` to keys the decoded config actually read, or warn when the source is an unprefixed `EnvSource`. T-05 recommends `EnvSource.prefix("bookstore")` as the workaround.

### config: `loadOrThrow` reports the wrong error count for a `Composite` error

Found 2026-10-19 while writing T-05. Verified.

**Summary.** When the single top-level error is a `Composite`, the report says `failed with 1 error(s):`, then lists two messages, and only the first is indented. `Config.formatErrors` does not flatten `Composite`.

**Status.** PR #1761 fixed this but was closed without merging, so the bug is still open.

### config: layered load errors name the whole chain as the source

Found 2026-10-19 while writing T-05. Verified.

**Summary.** An error from `env.orElse(yaml).orElse(defaults)` names the source as `env|bookstore.yaml|defaults`, not the layer that held the bad value. That makes it hard to find which file or variable to fix.

### config: `ProvenanceMap#dump` misreports env overrides through a key-formatted `EnvSource`

Found 2026-10-20 while writing M-06 (`docs/guides/config-library-migration.md`). Verified on 0.0.56.

**Summary.** With `EnvSource.keyFormat(KeyFormat.UpperSnakeCase).prefix("bookstore")` layered over a HOCON file and `BOOKSTORE_FEATURED_TITLE` set, the loaded value is correct and `provenanceOf("featuredTitle")` correctly names `BOOKSTORE_FEATURED_TITLE`. But `dump` shows a spurious row `featured.title | <env value> | env`, and still shows the overridden `featuredTitle | Zionomicon | application.conf` row as if it were in effect.

**Root cause.** `EnvSource.all` maps environment keys back with `fromEnvKey` (lowercase, `_` to `.`) (`config/jvm/src/main/scala/zio/blocks/config/PlatformConfigSource.scala:53-54`). `KeyMapper.toCanonical` cannot recover the camelCase name because the key no longer contains `_` or `-`. `orElse.all` then merges both maps by key (`ConfigSource.scala:53-62`), so the two spellings never meet.

**Fix.** Make `EnvSource.all` return keys that round-trip through the key mapper, or have `keyMapper(...).all` map keys through `fromCanonical(..., UpperSnakeCase)`. Add a `dump` test for a key-formatted env layer.

### config-hocon: `ConfigSource.fromHocon` silently drops every `include`

Found 2026-10-20 while writing M-06. Verified on 0.0.56.

**Summary.** `fromHocon` passes no include callback, so every `include` directive is dropped without an error. An object whose only content is an include disappears: `db { include "db.conf" }` yields no `db` keys. `HoconConfigSource.fromStringWithCallback` is `private[hocon]`, so callers of the public `ConfigSource` API cannot supply a resolver.

**Fix.** Either fail on an unresolved include, or expose a public way to pass an include resolver (and a file-relative default for `fromFile`).

### config-hocon: array concatenation `[a] [b]` becomes an empty string

Found 2026-10-20 while writing M-06. Verified on 0.0.56.

**Summary.** HOCON array concatenation (`xs = [a] [b]`) silently produces `""` instead of the merged list `[a, b]` or a parse error. Cause: `hoconValueToString` in `HoconParser.scala` (around line 647).

### config-hocon: parse errors lose their line and column

Found 2026-10-20 while writing M-06. Verified on 0.0.56 (resolution-pass part from source).

**Summary.** A HOCON syntax error surfaces only as `Parse error for key '' (expected valid HOCON) in source 'hocon:string'`. `ConfigError.ParseError` appends its cause only when `path` is non-empty (`ConfigError.scala`, around lines 165-173), and `fromHocon` uses the empty path, so the `HoconError` with the position never reaches `.message`. Errors from the resolution pass (circular or unresolved substitution) carry line 0, column 0 even in the cause.

**Fix.** Always include the cause message for `ParseError`, and record real positions for resolution errors.

### sql: `Table.createTable` emits no `PRIMARY KEY`, so `Repo.insertOrUpdate` fails

Found 2026-10-20 while writing T-07 (`docs/guides/your-first-sqlite-repository.md`). Verified on SQLite.

**Summary.** The DDL that `Table`/`Ddl` generate has no `PRIMARY KEY` constraint. `Repo.insertOrUpdate` relies on one, so on SQLite it fails with `ON CONFLICT clause does not match any PRIMARY KEY or UNIQUE constraint`.

**Status.** #1760 documents the caveat in `repo.md`, `table.md` and `ddl.md`. The code is unchanged. Fix by emitting a primary key for the `Repo` ID column.

### html: `html"..."` escapes interpolated attribute values twice

Filed as #1764. Found 2026-10-20 while writing T-09. Verified on 0.0.56. `&` renders as `&amp;amp;`, which breaks URLs with query strings.

### html: `selector"..."` never matches in `Dom#select`

Filed as #1765. Found 2026-10-20 while writing T-09. Verified on 0.0.56. The interpolator builds `CssSelector.Raw`, which `select` never matches (`DomSelection.scala:296`; `DomSelectionSpec` asserts this).

### html and schema: `p(...)` is ambiguous on Scala 3 when both packages are imported

Found 2026-10-20 while writing T-09. Verified. Not filed.

**Summary.** `import zio.blocks.html._` together with `import zio.blocks.schema._` makes `p(...)` ambiguous on Scala 3, because schema exports the `p"..."` path interpolator. Any code that renders HTML from schema-backed data with wildcard imports hits this. Options: rename one of them, or move the path interpolator out of the package object.

## Usability traps

These behave as designed but surprise users. Each should at least be documented where users meet it.

- **`toJsonString` ignores codecs built with `Schema#derive`.** `toJson`, `toJsonString` and `fromJson` always use `schema.jsonCodec`, the default deriver (`Schema.scala:38`, `syntax.scala:32-40`). A customised codec must be called directly. Found while writing H-02; confirmed while writing H-03.
- **An implicit `JsonCodec[LocalDate]` in scope is ignored by derivation.** `JsonCodecDeriver` always uses its built-in `localDateCodec` (`JsonCodecDeriver.scala:310`). Users coming from circe expect a codec in implicit scope to be picked up. Use `Deriver#withInstance` instead. Found while writing H-03.
- **Deriver-level overrides beat builder-level overrides for the same key.** `DerivationBuilder` merges `instanceOverrides ++ deriver.instanceOverrides` and `.toMap` keeps the last entry (`DerivationBuilder.scala:125`), so a one-off `DerivationBuilder#instance` cannot override a `Deriver#withInstance` for the same type. Most users expect the narrower scope to win. Documented in H-03, but worth reconsidering. Found while writing H-03.
- **`@Modifier.rename` drops the original name.** An `Option` field sent under its Scala name silently decodes to `None`. Use `@Modifier.alias` to accept both. Found while writing H-02.
- **`.each`, `.when` and `.atKey` work only inside the companion.** They are members of `CompanionOptics`, so `Inventory.$(_.shelves.each...)` written at a call site does not compile (`value each is not a member of List[Shelf]`). Found while writing T-03.
- **A chatty TOON reply decodes as if it were clean.** A prose line ending in `:` (for example `Sure! Here is my pick:`) is read as an unknown key and skipped by default. `withRejectExtraFields(true)` catches it. Found while writing H-12.
- **JSON Schema error paths are imprecise.** An `additionalProperties: false` failure reports `Schema rejects all values at: .`, at the object rather than the extra field. Errors raised inside `fromJsonSchema` end with ` at: .wrapped`. Found while writing H-05.
- **HOCON `${?VAR}` and `${VAR}` never read environment variables.** Typesafe Config falls back to the environment; config-hocon resolves substitutions only inside the document (`HoconParser.scala`, `lookupPath`, around line 655). `${?DB_PASSWORD}` silently drops the key, and a non-optional `${VAR}` is a parse error. This is the biggest trap for teams migrating from Typesafe Config: layer `EnvSource` with `orElse` instead. Document it in `formats.md`, or add an opt-in environment fallback. Found while writing M-06.
- **Durations and sizes use Java syntax only.** `10s` is rejected because durations go through `java.time.Duration.parse` (ISO-8601, e.g. `PT10S`; `ConfigDecoderDeriver.scala:416`), and size units like `512M` are not parsed. Typesafe Config's `getDuration`/`getBytes` accept both. Found while writing M-06.
- **Scope and Wire error codes never appear in compiler output.** `ErrorMessages.scala` numbers them E1-E17, RE1-RE3 and W1, but the messages users see carry no code, so docs must index errors by message text. Found while writing H-31.
- **`defer` on a closed scope is a silent no-op** (`Scope.scala:202`), while `allocate` and `$` throw. Found while writing H-31.
- **`jdbc:sqlite::memory:` gives every connection its own empty database.** Code that opens more than one connection loses its tables. Found while writing T-07; now noted in `transactor.md`.

## Test and CI

### Flaky streams test: `unfoldAsync - reset invalidates a stale completion and restarts from initial state`

Seen 2026-10-20 on PR #1773 (docs only, touches nothing in `streams/` or `async/`). In `testJVM (25, JVM, 3.3.x)`, this test hit its 30 s timeout while the other 12,760 tests passed. Rerunning is the current workaround. Needs investigation: a timing dependency in the test, or a real race in `unfoldAsync` reset.

### mdoc: `mdoc:fail` on Scala 3 ignores earlier `:reset` sections

Seen 2026-10-20 while writing H-02. A `mdoc:fail` block after a `mdoc:silent:reset` reports bogus "already defined" errors for names from before the reset, so the expected compile error cannot be shown. H-02 described that error in prose instead. Pages that rely on `mdoc:fail` (H-31) should check their rendered output.

Related, seen while writing H-31: `mdoc:fail` and `mdoc:warn` inline the earlier `compile-only` blocks of the same section, so a `compile-only` fix block that redefines a name breaks a later `mdoc:warn` with no reason given. Use `mdoc:silent:reset` for such fix blocks. H-31 added this to the docs-mdoc-conventions skill.

### `DeriverWithInstanceCsvSpec` asserts with `.contains`

Found 2026-10-20 while writing H-03. The schema-csv spec checks rendered output with `.contains`, against the AGENTS.md rule to assert the full rendered string with `==`. A regression in field order or separators would pass.

### `schema-examples` has no scalafmt setup

Found 2026-10-20 while writing H-03. The standalone `schema-examples/` build has no scalafmt plugin or config, so `sbt scalafmtAll` in the root build does not format it. Sessions format example files by hand or with `cs launch scalafmt -- --config .scalafmt.conf`. Add the plugin (or a `.scalafmt.conf` symlink) to the example builds, or document the command in AGENTS.md.

### Docs build memory with many sbt servers

Seen 2026-10-20: a full `sbt check` was OOM-killed while several worktrees each ran an sbt server (about 4 GB free). Shut down idle servers in finished worktrees with `sbt --client shutdown`.

## Documentation errors

Pages that contradict the source. None of these were copied into the new guides.

**schema / JSON**

- `json-config.md` (around lines 146-175) says `implicit val nameMapper: NameMapper = NameMapper.SnakeCase` in the companion makes `toJson` emit snake_case. Nothing reads an implicit `NameMapper`. The real ways are `JsonCodecDeriver.withFieldNameMapper` with `Schema#derive`, or `@Modifier.fieldNaming`. The output comments hide the problem because the blocks are compile-only. Severity: high, because it teaches code that silently does nothing.
- `json-config.md` "Custom Mappers" lists only `Identity`, `SnakeCase` and `KebabCase`; `CamelCase`, `PascalCase` and `Custom(f)` also exist.
- `modifier.md`, `transient`: does not say that a transient field's JSON value is ignored on decode and the default is used.
- `schema-error.md` (lines 56-57, 171-172, 283-289, 313-316) shows `Missing field 'x' at: .path` as the decode result. JSON decoding reports `missing required field "x" at: .path` (`JsonReader.scala:89`).
- `syntax.md` shows `show` output as `Record { name = Alice, age = 30 }`; the real output is the EJSON form (`DynamicValue.toString`).
- `Schema.fromJsonSchema` Scaladoc (`Schema.scala:357-358`) says values are validated "during construction"; validation happens at decode time (`:366-370`).
- `validation.md:356-373` says `Validation` constraints become `minimum`, `maxLength`, `pattern` and so on in `toJsonSchema`. They do not; only parsing a JSON Schema maps them (`JsonSchemaToReflect.scala:261-285`).
- `validation.md:304` uses `JsonDecoder[Order].decodeString`; `JsonDecoder` was removed in #1240.
- `json-schema.md:366` shows an error message without the ` at: <path>` suffix that `SchemaError.message` adds. The page also does not mention the `pattern` anchoring behaviour above.
- `ValidationOptions` Scaladoc (`JsonSchema.scala:376-377`) says format checks are annotation-only by default; the code defaults to `validateFormats = true`.
- `codec.md` never mentions `Deriver#withInstance` or `Deriver#withModifier` (#1348). Its "Using Schema.deriving for Customization" section shows only the per-derivation `DerivationBuilder` path. Found while writing H-03.
- `type-class-derivation.md:170`: the first example (`customDateCodec`) contains a `// ... AST overrides ...` placeholder, so it is not complete, compilable code. Found while writing H-03.

**schema / optics and paths**

- `path-interpolator.md:294-305`: the escape table implies all six escapes work in both strings and chars. Strings accept only `\"`, `\\`, `\n`, `\t`, `\r`; chars accept only `\'`, `\\`, `\n`, `\t`, `\r` (`PathParser.scala:399-442`).
- `path-interpolator.md:686`: `data.get(p"...")` reads as returning a value; it returns a `DynamicValueSelection` (`DynamicValue.scala:166`). Show `.one` to extract.
- `optics.md:52` says `OpticCheck` includes the actual value encountered; only `UnexpectedCase` does. `optics.md` has no `OpticCheck` section, and `dynamic-optic.md` "Failure Path in OpticCheck" omits `DynamicConversionError`.
- `optics.md:1061-1089` says optic `toString` matches the macro syntax. Map-key steps print the placeholder `.atKey(<key>)` (`Optic.scala:1227-1228`, `2682`, `2686`).

**schema-toon (`toon.md`)**

- Lines 924-985 show key folding and path expansion on case classes; only the `DynamicValue` codec reads them (`ToonCodec.scala:826-827`, `1000-1005`).
- Line 636 and the `WriterConfig` Scaladoc say `WriterConfig`/`ReaderConfig.withDelimiter` sets the delimiter for derived arrays; `ToonCodecDeriver.withDelimiter` does, and the reader takes it from the array header.
- Line 693 shows `items:` for `ArrayFormat.List`; the output is `items[N]:`.
- Line 1044 shows `Expected sequence, got: invalid`; the source message is `Expected array header with [`.
- Lines 14 and 659 say there are three array formats; there are four.

**config (`formats.md`, HOCON section)**

- Line 191 says `include` directives are resolved through a callback, so includes "come from a classpath, an archive, or a test fixture". Through the public `ConfigSource` API there is no callback (see the dropped-includes bug) and no classpath loader.
- Line 162 says infinite values keep their `Double` rendering. From HOCON text this is unreachable: the parser rejects `NaN`/`Infinity` and keeps them as strings. From source.
- Line 247 says `HoconError` carries the line and column. That holds only for lexer errors; resolution errors report 0:0. From source.
- The "Loading from a File" section omits `maxFileBytes` / `fromFileWithLimits` (default 1 MiB) and the file-too-large error.
- The HOCON section has no warnings for the dropped includes, environment substitutions, array concatenation and duration/size syntax above.

**scope**

- `scope.md:1065` lists a non-existent `andThen`; `scope.md:19-22` have wrong `scoped`, `allocate` and `open` signatures; `scope.md:943` quotes `Cannot acquire resource: scope has already been closed.`; the real text is `Cannot allocate resource: scope is already closed.`
- `wire.md:66` (`new UserService($(db)(identity))`) does not compile; `wire.md:28` calls Wire a "lazy recipe" (see the eager `Wire(value)` bug).
- `finalization.md` says `abstract class` in five places (the source is `final class`); line 173 has wrong LIFO comments; line 280 names the private `runFinalizers`.
- Thread ownership (`scope.md:952`, Scope tutorial lines 690, 706, 749): only `scoped` checks the owner thread; `allocate`, `defer` and `$` do not.
- Scope tutorial: lines 162-163 blame rejected `$` lambdas on `Unscoped` instead of the `$` macro. Section 12 ("Common Errors and Troubleshooting"): it says `defer` on a closed scope throws (it is a silent no-op); line 726 quotes `IllegalStateException: Scope is closed` (the real text is `Cannot allocate resource: scope is already closed.` and similar); line 820 says `Wire.shared[Database]` fails with "no Wire[String] for url" (it compiles; the error comes from `Resource.from` as E7 `Cannot auto-create java.lang.String`); the first runtime-error fence reads `mdoc:compile-only mdoc:silent:reset`.
- `wire.md:85`, `:506`, `:515` and `resource-management/index.md:18` promise compile-time cycle detection and "missing dependency" errors. Cycles crash the macro (#1767), and missing dependencies are auto-created or fail with E7/E8. `scope.md:1254-1267` presents `CircularDependencyDemoExample` as cycle detection.
- `unscoped.md:114` and the Scope tutorial (lines 316, 332) do not mention Scala 3 automatic derivation (see the `AutoCloseable` escape bug).
- Several pages teach `val appResource = Resource.from(...)` (see the single-use bug).

**Other modules**

- `jwt.md` documents an audience mismatch as `InvalidToken`; the source returns `InvalidClaim("aud", ...)` (`Jwt.scala:451-453`). It also omits `InvalidClaim` and `InvalidKey` and gives no key-format guidance. H-37 (stalled) includes this fix.
- `html.md`: artifact name for Scala 3.7+, `Css.Declaration`, `hx-*` URL attributes, `selector""`. Filed as #1766.
- `streams/index.md:78` lists Kyo RC6 and Ox 1.0.6; the build pins RC7 and 1.1.0.
- `docs/index.md` and `built-in-codecs/json/index.md:39,45` give `zio-blocks-schema` for every Scala version; Scala 3.7+ uses `zio-blocks-next-schema`. H-01 (stalled) includes this fix.
- `combinators.md` documents a `Choices` module that does not exist (research §9.6).
- `docs/dev/research.md` cites stale line numbers (for example `Transactor.scala:65` is now `:261`/`:275`, `Table.scala:32` is now `:262`).

## Resolved

- Stale example: `schema-examples/src/main/scala/deriveroverrides/CompleteExample.scala` predated #1348 and showed no codec override. Rewritten in #1781 to demonstrate type, term-name and optic overrides with a round trip and the current run command.
- Docs build: a broken relative link in `docs/reference/schema/modifier.md:395` (`../guides/` instead of `../../guides/`), added by #1775, failed Build Docs on main. Fixed in #1772.
- Config: `formats.md:97` (YAML parse error type) and `config-source.md` (`EnvSource` and `keyFormat`). Fixed in #1771.
- sql: `sql-transactions.md` default isolation and PostgreSQL facts; `repo.md`/`table.md`/`ddl.md` upserts, `pageAfter` and the missing `PRIMARY KEY` caveat; `:memory:` warnings in `transactor.md` and `transactor-zio.md`; `db-con.md` `DbTx` members; the backlog's "sql has no JS backend" note. Fixed in #1760.
- Codecs: `format.md` (`sealed trait`, real `BinaryFormat`/`TextFormat` signatures, which formats are binary); TOON `ArrayFormat.Auto` Scaladoc and `toon.md:706`; `csv.md` (`CsvConfig` ignored by derived codecs, runtime rejection, `Option` rejected); `xml.md` stale version; `built-in-codecs/index.md` artifact prefix. In PR #1773 (open).
