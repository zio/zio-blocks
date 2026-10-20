# ZIO Blocks: issues found while writing docs

Internal list of problems found while writing the tutorials and guides in `docs/dev/backlog.md`. Each entry says where it was found, how it was confirmed, and what the fix is, so it can be filed on GitHub as is. When an entry is filed, add the issue link under its heading. When it is fixed, add the PR and move it to [Resolved](#resolved).

"Verified" means a session reproduced the problem by running code. "From source" means it was found by reading the code and has not been run.

**Standard for library bug entries** (maintainer request, 2026-10-20): each entry has a complete introduction and a reproducible example. That means where it was found, a summary, a reproduction run against the published release with its real output, the expected behaviour, the root cause with file:line, the impact, the test gap and a suggested fix. The entries for `Schema.fromJsonSchema`, `YamlReader` and the config/config-hocon bugs from M-06 follow it. The shorter entries still need a reproduction section before they are filed.

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

### `YamlReader` accepts unterminated flow collections and quoted strings, and drops their last character

Found 2026-10-20 while writing the migration guide `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `zio.blocks.schema.yaml.YamlReader` does not check that a flow sequence (`[...`), a flow mapping (`{...`) or a quoted scalar (`"...`, `'...`) is closed. An unterminated one parses without an error, and the reader strips the closing delimiter that isn't there, so the last real character of the value is lost. A typo in a YAML file therefore loads as different data instead of failing: a database port of `5432` becomes `543`. Everything built on `YamlReader` is affected, including `ConfigSource.fromYaml` in `config-yaml`.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config-yaml` 0.0.56, which brings in `schema-yaml`):

```scala
import zio.blocks.schema.yaml.YamlReader
import zio.blocks.config._
import zio.blocks.config.yaml._

println(YamlReader.read("tags: [scala, zio"))
// Mapping(Chunk((Scalar(tags,None),Sequence(Chunk(Scalar(scala,None),Scalar(zi,None))))))

println(YamlReader.read("title: \"Zionomicon"))
// Mapping(Chunk((Scalar(title,None),Scalar(Zionomico,None))))

println(YamlReader.read("title: 'Zionomicon"))
// Mapping(Chunk((Scalar(title,None),Scalar(Zionomico,None))))

println(YamlReader.read("db: {host: localhost, port: 5432"))
// Mapping(Chunk((Scalar(db,None),Mapping(Chunk((Scalar(host,None),Scalar(localhost,None)),(Scalar(port,None),Scalar(543,None)))))))

println(YamlReader.read("tags:\n  [scala,\n   zio"))
// Mapping(Chunk((Scalar(tags,None),Sequence(Chunk(Scalar(scala,None),Scalar(zi,None))))))

println(ConfigSource.fromYaml("tags: [scala", "bookstore.yaml"))
// Right(MapSource(Map(tags.0 -> scal),bookstore.yaml))
```

**Expected.** Each call fails with a parse error that names the unclosed `[`, `{` or quote (for `ConfigSource.fromYaml`, `Left(ConfigError.ParseError(...))`). No value is changed.

**Root cause.** In `schema-yaml/shared/src/main/scala/zio/blocks/schema/yaml/YamlReader.scala`:

- `parseFlowMappingInline` (`:459`) and `parseFlowSequenceInline` (`:483`) take `s.substring(1, s.length - 1)` without checking that the last character is `}` or `]`.
- `unquoteSingle` (`:522`) and `unquoteDouble` (`:525`) do the same for the closing quote.
- For a flow collection that starts on its own line, `collectFlowContent` (`:381`) keeps reading lines while `depth > 0 && lineIdx < lines.length` (`:393`) and simply returns when the input runs out, still unbalanced. `parseFlowMapping` (`:333`) and `parseFlowSequence` (`:364`) then strip the last character the same way.

**Impact.** Silent data corruption. A missing `]`, `}` or quote, which is an easy typo, changes the value that is loaded rather than being reported, and the changed value often still passes type checks (`543` is a valid port).

**Test gap.** `YamlReaderSpec` has no test with an unterminated flow collection or quoted scalar.

**Suggested fix.** Before stripping delimiters, check that the value ends with the matching closing character (taking quotes into account), and fail with a parse error otherwise. Make `collectFlowContent` fail when it reaches the end of input with `depth > 0`. Add `YamlReaderSpec` tests for each unterminated form, including the multi-line flow sequence.

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

### config: `Config.load` returns one `Composite` for many errors, so `loadOrThrow` reports "1 error(s)"

Found 2026-10-19 while writing the tutorial `docs/guides/typed-config-loader.md` (backlog T-05), and reproduced 2026-10-20 while writing `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running. PR #1761 fixed the report but was closed without merging, so the bug is still open.

**Summary.** `Config.load` returns `Either[::[ConfigError], A]`, and its Scaladoc says it "returns all accumulated `ConfigError`s". When a case class has several bad fields, though, the list holds a single `ConfigError.Composite` that wraps them. Code that counts or walks the list sees one error. `Config.loadOrThrow` builds its report from that list, so it says `failed with 1 error(s)`, prints the `-` bullet only before the first message, and puts the other messages on unindented lines. `ConfigLoadException#errors` also has length 1.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config` 0.0.56):

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

case class DbConfig(host: String, port: Int)
case class BookstoreConfig(name: String, port: Int, db: DbConfig)
object BookstoreConfig { implicit val schema: Schema[BookstoreConfig] = Schema.derived }

val source = ConfigSource.fromMap(Map("port" -> "eighty", "db.port" -> "5432"), "application.conf")

Config.load[BookstoreConfig](source) match {
  case Left(errors) =>
    println(errors.length)
    println(errors.head.getClass.getSimpleName)
  case Right(config) => println(config)
}
// 1
// Composite

try Config.loadOrThrow[BookstoreConfig](source)
catch { case e: ConfigLoadException => print(e.getMessage); println(e.errors.length) }
// Configuration loading failed with 1 error(s):
//   - Missing required key 'name' in source 'application.conf'
// Invalid value 'eighty' for key 'port' (expected Int) in source 'application.conf': For input string: "eighty"
// Missing required key 'db.host' in source 'application.conf'
// 1
```

**Expected.** `errors.length` is `3`, with one entry per problem. The report says `failed with 3 error(s):` and puts each message on its own `  - ` line. `e.errors.length` is `3`.

**Root cause.** The record decoder collects every field error, but when there is more than one it returns `Left(::(ConfigError.Composite(errors), Nil))`, a one-element list (`config/shared/src/main/scala/zio/blocks/config/ConfigDecoderDeriver.scala:122-127`, in `deriveRecord` at `:61`). The sequence and map decoders return the errors as a flat list instead (`:249-251` and `:304-306`), so the shape of `Left` depends on the type being decoded. `Config.formatErrors` counts `errors.length` and writes one `  - ` bullet per list element (`config/shared/src/main/scala/zio/blocks/config/Config.scala:131-142`), and `Composite#message` joins its children with `"\n"` (`ConfigError.scala:126-130`), which is where the unindented lines come from.

**Impact.** The headline count in every startup failure report is wrong whenever more than one field is bad, which is the case `Config.load` is designed for. Callers who match on the list (for example to group errors by kind) have to know to unwrap `Composite` first.

**Test gap.** `ConfigSpec` checks only that `e.errors.head` is a `Composite` and that `e.report.contains("error(s)")` (`config/shared/src/test/scala/zio/blocks/config/ConfigSpec.scala:115`, `:143`). No test asserts the count or the full report.

**Suggested fix.** Return the record errors as a flat list, as the sequence and map decoders already do, and keep `Composite` only for callers that build one themselves. At the least, flatten `Composite` in `formatErrors` (this is what PR #1761 did). Assert the full report string in `ConfigSpec`.

### config: layered load errors name the whole chain as the source

Found 2026-10-19 while writing T-05. Verified.

**Summary.** An error from `env.orElse(yaml).orElse(defaults)` names the source as `env|bookstore.yaml|defaults`, not the layer that held the bad value. That makes it hard to find which file or variable to fix.

### config-hocon: array concatenation `[a] [b]` becomes an empty string

Found 2026-10-20 while writing the migration guide `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** HOCON lets two arrays written next to each other concatenate into one list (`tags = [scala] [zio]` is `[scala, zio]` in the HOCON spec and in Typesafe Config). `config-hocon` accepts the syntax but turns the value into the empty string `""`. There is no error, so a `List[String]` field loads as one empty element or fails far from the cause.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config-hocon` 0.0.56):

```scala
import zio.blocks.config._
import zio.blocks.config.hocon._

def keys(doc: String): Unit =
  ConfigSource.fromHocon(doc) match {
    case Right(source) => println(source.all("").map { case (k, v) => s"$k = '${v.value}'" }.toList.sorted)
    case Left(error)   => println(error.message)
  }

keys("tags = [scala] [zio]")
// List(tags = '')

keys("tags = [scala, zio]")
// List(tags.0 = 'scala', tags.1 = 'zio')
```

**Expected.** `tags = [scala] [zio]` produces `tags.0 = 'scala'` and `tags.1 = 'zio'`, like the single array. If concatenation is not going to be supported, the parser should report an error instead.

**Root cause.** Adjacent values are parsed into an `MConcat`, and `resolveSubstitutions` turns every `MConcat` into a string by appending `hoconValueToString` of each part (`config-hocon/shared/src/main/scala/zio/blocks/config/hocon/HoconParser.scala:634-645`). `hoconValueToString` returns `""` for arrays and objects (`:652`, with the comment "objects/arrays don't stringify well in concat"). Object concatenation goes through the same code path: `db = {host: a} {port: 1}` produces the single key `db = ''`.

**Impact.** Silent data loss for a documented HOCON feature, often hit when an `application.conf` written for Typesafe Config is loaded with `config-hocon`.

**Test gap.** `HoconParserSpec` has no test for array or object concatenation.

**Suggested fix.** In the `MConcat` case, when every part resolves to an array, concatenate the arrays; when every part resolves to an object, merge them as HOCON does. Report a parse error when arrays or objects are mixed with strings. Add `HoconParserSpec` tests for both cases.

### config-hocon: `ConfigSource.fromHocon` silently drops every `include`

Found 2026-10-20 while writing the migration guide `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `ConfigSource.fromHocon` parses HOCON text with no way to resolve an `include`, and the parser silently skips an include it cannot resolve. Every `include` in a string is therefore dropped without an error, and an object whose only content is an include disappears. The public `ConfigSource` API has no way to pass an include resolver; only the JVM-only `ConfigSource.fromFile` resolves includes.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config-hocon` 0.0.56):

```scala
import zio.blocks.config._
import zio.blocks.config.hocon._

def keys(doc: String): Unit =
  ConfigSource.fromHocon(doc) match {
    case Right(source) => println(source.all("").map { case (k, v) => s"$k = '${v.value}'" }.toList.sorted)
    case Left(error)   => println(error.message)
  }

keys("""db { include "db.conf" }, name = "Scala Bookstore"""")
// List(name = 'Scala Bookstore')

keys("""include "missing.conf", name = "Scala Bookstore"""")
// List(name = 'Scala Bookstore')
```

The only workaround is to bypass `ConfigSource.fromHocon` and call the parser directly:

```scala
val files = Map("db.conf" -> """host = "localhost"""")

val source = HoconParser
  .parse("""db { include "db.conf" }, name = "Scala Bookstore"""", files.get)
  .map(value => ConfigSource.fromMap(HoconValue.flatten(value), "application.conf"))

println(source.map(_.all("").keys.toList.sorted))
// Right(List(db.host, name))
```

**Expected.** Either `fromHocon` reports that an include cannot be resolved, or it accepts an include resolver so that callers on Scala.js, or with a classpath resource, can supply the included text.

**Root cause.** `ConfigSource.fromHocon` (`config-hocon/shared/src/main/scala/zio/blocks/config/hocon/ConfigSourceHoconSyntax.scala:23-24`) calls `HoconConfigSource.fromStringWithId`, which calls `HoconParser.parse(hocon)` with the default `includeCallback = _ => None` (`HoconParser.scala:42`). `handleInclude` treats a `None` as a missing include and ignores it on purpose (`HoconParser.scala:452`, tested by `HoconParserSpec` "silently ignores missing includes"). `HoconConfigSource.fromStringWithCallback` exists (`HoconConfigSource.scala:35`), but the whole object is `private[hocon]` (`:24`).

**Impact.** A configuration split across files loads with whole blocks missing and no error. Missing keys then show up as `MissingKey` errors (or as `None` for optional fields), far from the `include` that caused them.

**Test gap.** `HoconConfigSourceSpec` has no test with an `include`.

**Suggested fix.** Add a public overload, for example `ConfigSource.fromHocon(hocon, sourceId, includeResolver: String => Option[String])`. Also consider an option, or a different default, that makes an unresolved `include` an error. HOCON's `include required(...)` form, which `config-hocon` does not parse yet, is the standard way to say an include must exist.

### config-hocon: `${VAR}` and `${?VAR}` never fall back to environment variables

Found 2026-10-20 while writing the migration guide `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** The HOCON spec has a section on substitution fallback to environment variables, and Typesafe Config implements it: a `${VAR}` or `${?VAR}` that is not a path in the document is looked up as an environment variable. `config-hocon` resolves substitutions inside the document only. `${?DB_PASSWORD}`, the usual way to read a secret in an `application.conf`, silently resolves to nothing, and `${DB_PASSWORD}` is a parse error, even when the variable is set.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config-hocon` 0.0.56; run in an environment where `HOME` is set):

```scala
import zio.blocks.config._
import zio.blocks.config.hocon._

def keys(doc: String): Unit =
  ConfigSource.fromHocon(doc) match {
    case Right(source) => println(source.all("").map { case (k, v) => s"$k = '${v.value}'" }.toList.sorted)
    case Left(error)   => println(error.message)
  }

println(sys.env.contains("HOME"))
// true

keys("home = ${?HOME}")
// List()

keys("home = ${HOME}")
// Parse error for key '' (expected valid HOCON) in source 'hocon:string'
```

**Expected.** If this is meant to match Typesafe Config: `home = ${?HOME}` and `home = ${HOME}` both produce `home = '<value of HOME>'`. If the omission is deliberate (environment access is layered with `EnvSource` instead), the parse error for `${HOME}` should say that environment substitution is not supported, and `formats.md` should say so in its HOCON section.

**Root cause.** `resolveSubstitutions` looks a substitution up with `lookupPath` in the parsed document only (`config-hocon/shared/src/main/scala/zio/blocks/config/hocon/HoconParser.scala:621-631`, `:655`). When the path is missing, an optional substitution becomes `HoconValue.Null`, which `HoconValue.flatten` drops, and a required one fails with `Unresolved substitution` (`:630`). No code in `config-hocon` reads the environment.

**Impact.** The most common migration step from Typesafe Config or PureConfig silently stops working: the secret is not loaded, and the error, if any, appears later as a missing key.

**Test gap.** None for the current behaviour; there is no test that pins either the fallback or its absence.

**Suggested fix.** Decide and document. Either add an opt-in fallback (for example a resolver parameter that defaults to no fallback, so `config-hocon` stays deterministic and works on Scala.js), or keep the current behaviour and make both cases visible: name environment substitution in the `Unresolved substitution` error, and document it in `docs/reference/config/formats.md`.

### config: `ProvenanceMap#dump` misreports env overrides through a key-formatted `EnvSource`

Found 2026-10-20 while writing the migration guide `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `EnvSource.keyFormat(KeyFormat.UpperSnakeCase)` is how a camelCase field such as `featuredTitle` is read from `FEATURED_TITLE`. Loading through it works, and `ProvenanceMap#provenanceOf` names the right variable, but `ProvenanceMap#dump` lists the override under a made-up key, `featured.title`. It also keeps the file's overridden `featuredTitle` row, which now looks like the value in effect.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config` and `zio-blocks-next-config-hocon` 0.0.56; run with `BOOKSTORE_FEATURED_TITLE="Programming in Scala"` set):

```scala
import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.schema.Schema

case class Bookstore(featuredTitle: String, port: Int)
object Bookstore { implicit val schema: Schema[Bookstore] = Schema.derived }

val file = ConfigSource.fromHocon("""featured-title = "Zionomicon", port = 8080""", "application.conf").toOption.get
val env  = EnvSource.keyFormat(KeyFormat.UpperSnakeCase).prefix("bookstore")

val provenance = Config.loadWithProvenance[Bookstore](env.orElse(file.keyFormat(KeyFormat.KebabCase))).toOption.get

println(provenance.value)
// Bookstore(Programming in Scala,8080)

println(provenance.provenanceOf("featuredTitle"))
// Resolved(env,BOOKSTORE_FEATURED_TITLE,Programming in Scala)

println(provenance.dump())
// ┌────────────────┬──────────────────────┬──────────────────┐
// │ Key            │ Value                │ Source           │
// ├────────────────┼──────────────────────┼──────────────────┤
// │ featured.title │ Programming in Scala │ env              │
// │ featuredTitle  │ Zionomicon           │ application.conf │
// │ port           │ 8080                 │ application.conf │
// └────────────────┴──────────────────────┴──────────────────┘
```

**Expected.** One row, `featuredTitle | Programming in Scala | env`, and no `featured.title` row.

**Root cause.** `dump` lists `source.all(prefix)`. `EnvSource.all` turns each variable name back into a key with `fromEnvKey`, which lower-cases it and replaces every `_` with `.` (`config/jvm/src/main/scala/zio/blocks/config/PlatformConfigSource.scala:37-48`, `:53-54`; the same on Scala.js at `config/js/.../PlatformConfigSource.scala:49-50`). So `BOOKSTORE_FEATURED_TITLE` becomes `bookstore.featured.title`, and the word boundary is lost. The key-format wrapper's `all` then calls `KeyMapper.toCanonical` (`config/shared/src/main/scala/zio/blocks/config/ConfigSource.scala:100-103`), which leaves `featured.title` alone because it has no `_` or `-`. `orElse.all` merges the layers by key (`ConfigSource.scala:54-60`), and `featured.title` never meets the file's `featuredTitle`.

**Impact.** The provenance table, whose job is to answer "where did this value come from?", shows the wrong value as active for every multi-word field that is overridden from the environment.

**Test gap.** `ConfigSpec` "dump produces formatted table" uses a plain map source, and `ConfigSourceCompositionSpec` "keyMapper composes with orElse" checks `get` only. Nothing checks `all` or `dump` through a key-formatted `EnvSource`.

**Suggested fix.** In the key-format wrapper's `all`, map environment-style keys with the target format in mind: for `UpperSnakeCase`, treat each `_`-separated word of the original variable name as a camelCase word boundary. One way is for `EnvSource.all` to return keys whose segments keep `_`, so `toCanonical` can rebuild `featuredTitle`. Add a `dump` test for a key-formatted env layer over a file layer.

### config-hocon: parse errors lose their line and column

Found 2026-10-20 while writing the migration guide `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** A syntax error in a HOCON document is reported as `Parse error for key '' (expected valid HOCON) in source '<name>'`, with no hint of where the error is. The parser does record a message with the line and column, but it is held only in the `cause` field of `ConfigError.ParseError`. It is not part of `message` or `getMessage`, and `getCause` returns `null`. Errors found while resolving substitutions report line 0, column 0 even there.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config-hocon` 0.0.56):

```scala
import zio.blocks.config._
import zio.blocks.config.hocon._

List("name = \"Scala Bookstore\"\ntags = [scala,\n", "a = ${missing}").foreach { doc =>
  ConfigSource.fromHocon(doc, "application.conf") match {
    case Left(e: ConfigError.ParseError) =>
      println(e.message)
      println(e.getCause)
      println(e.cause.map(_.getMessage))
    case other =>
      println(other)
  }
}
// Parse error for key '' (expected valid HOCON) in source 'application.conf'
// null
// Some(Unterminated array, expected ']' after ',' (line 3, column 1))
// Parse error for key '' (expected valid HOCON) in source 'application.conf'
// null
// Some(Unresolved substitution: ${missing} (line 0, column 0))
```

**Expected.** `message` (and so `getMessage` and any log line) includes the parser's message, for example `... in source 'application.conf': Unterminated array, expected ']' after ',' (line 3, column 1)`. `getCause` returns the `HoconError`. Resolution errors carry the position of the substitution.

**Root cause.** `HoconConfigSource.toConfigError` builds `ConfigError.ParseError("", sourceId, "valid HOCON", Some(e))` with an empty path (`config-hocon/shared/src/main/scala/zio/blocks/config/hocon/HoconConfigSource.scala:55-56`). `ParseError#message` appends the cause only when `path` is non-empty (`config/shared/src/main/scala/zio/blocks/config/ConfigError.scala:165-173`). `ConfigError` extends `NoStackTrace` and never passes `cause` to `Throwable` (`ConfigError.scala:30-33`), so `getCause` is `null`. Errors from the resolution pass are wrapped as `HoconError(msg, 0, 0)` (`HoconParser.scala:55`).

**Impact.** Every HOCON syntax error in a real file has to be found by hand. JSON and YAML parse failures are worse off: `ConfigSource.fromJson` and `ConfigSource.fromYaml` build `ParseError("", sourceId, "valid JSON")` (or `"valid YAML"`) with no cause at all, so not even the `cause` field says what went wrong.

**Test gap.** `ConfigErrorSpec` tests `ParseError` messages only with a non-empty path, and no test checks the message of a HOCON parse failure.

**Suggested fix.** Append the cause message in `ParseError#message` whatever the path, and override `getCause` to return the cause. Record the position of each substitution when parsing, and use it for resolution errors. Add a `HoconConfigSourceSpec` test that asserts the full message of a syntax error.

### schema: error paths leak the internal `.wrapped` segment for transformed fields

Found 2026-10-20 while writing H-05 and M-01. Verified on 0.0.56.

**Summary.** When a field's schema is a wrapper (built with `Schema#transform`, or the `Schema[Json]` returned by `Schema.fromJsonSchema`), decode errors end with an internal path segment: `... at: .orderedOn.wrapped`, or ` at: .wrapped` for `fromJsonSchema`. Users see a field name that does not exist in their data. The message is also the raw exception text from the `transform` function (`Text '2025-03-14' could not be parsed at index 2`).

**Reproduction.** See the `Schema[LocalDate]` usability trap below (last line of output), and the `fromJsonSchema` entry above.

**Fix.** Drop the wrapper segment when rendering the error path for wrapper schemas.

### `Table#createTable` declares no `PRIMARY KEY`, so derived tables accept duplicate IDs and reject `Repo#insertOrUpdate`

Found 2026-10-20 while writing the how-to guide `docs/guides/test-database-code-against-sqlite.md` (backlog H-47), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. This is a usability trap rather than a crash: `docs/reference/sql/repo.md`, `table.md` and `ddl.md` already document it, but nothing in the API steers users away from it.

**Summary.** `Table.derived[A].createTable(dialect)` renders every column as `name type [NOT NULL]` and never adds `PRIMARY KEY`, even when the table backs a `Repo` that knows its ID column. A table created this way silently stores two rows with the same ID, and `Repo#insertOrUpdate` / `Repo#insertOrUpdateBatch` fail on it, because their `ON CONFLICT (id)` clause needs a primary key or unique constraint. Tests suffer most: a test that checks that a duplicate insert is rejected can never pass on a derived schema, so users have to hand-write the DDL and keep it in sync with the case class.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-sql` 0.0.56, `sqlite-jdbc` 3.53.4.0):

```scala
import java.nio.file.Files
import scala.util.Try
import zio.blocks.schema.Schema
import zio.blocks.sql.*

case class Book(id: Long, title: String, stock: Int)
object Book { given Schema[Book] = Schema.derived }

val books = Repo.derived[Book, Long]
val file  = Files.createTempFile("pk-repro", ".db")
val xa    = JdbcTransactor.fromUrl(s"jdbc:sqlite:$file", SqlDialect.SQLite)

xa.connect {
  Table.derived[Book].createTable(SqlDialect.SQLite).update
  books.insert(Book(1L, "Zionomicon", 5))
  books.insert(Book(1L, "Zionomicon", 5))
  println(books.count)
  // 2
  println(Try(books.insertOrUpdate(Book(1L, "Zionomicon", 7))))
  // Failure(org.sqlite.SQLiteException: [SQLITE_ERROR] SQL error or missing database (ON CONFLICT clause does not match any PRIMARY KEY or UNIQUE constraint))
}
```

**Expected.** A table created for a `Repo` declares its ID column as the primary key, so the second insert fails with `SQLITE_CONSTRAINT_PRIMARYKEY` and `insertOrUpdate` updates the row.

**Root cause.** `Ddl.createTable` (`sql/shared/src/main/scala/zio/blocks/sql/Ddl.scala:38-47`) only knows column names, types and nullability; `ColumnDef` has no key flag, and `Table#createTable` (`sql/shared/src/main/scala/zio/blocks/sql/Table.scala:54-60`) has no way to pass one. The ID column is known only to `Repo` (`Repo#validatedIdColumn`), which has no DDL method of its own.

**Suggested fix.** Add a `primaryKey: Option[String]` (or a `ColumnDef#primaryKey` flag) to `Ddl.createTable` and an overload `Table#createTable(dialect, primaryKey: String)`, plus a `Repo#createTable(dialect)` that passes its ID column. Keep the current output for callers that pass nothing. Add a `RepoUpsertSpec` test that runs `insertOrUpdate` on a table created by `Repo#createTable`.

**Related (testing ergonomics).** `JdbcTransactor` has no way to hold one connection open across blocks, so `jdbc:sqlite::memory:` loses its tables between `connect`/`transact` calls, and there is no rollback-only `transact` for tests. The repo's own `RepoIntegrationSpec.withFreshDb` (`sql/jvm/src/test/scala/zio/blocks/sql/RepoIntegrationSpec.scala:58-84`) works around the first by subclassing `JdbcTransactor` and overriding `connect` with the `private[sql]` `JdbcConnection`, which users can't copy; its `transact` (not overridden) would close the shared connection. The H-47 guide uses a temporary file per test and a sentinel-exception `rollingBack` helper instead. A public single-connection transactor for tests (whose `close` is a no-op until the test ends) would make both unnecessary.

### html: `html"..."` escapes interpolated attribute values twice

Filed as #1764. Found 2026-10-20 while writing T-09. Verified on 0.0.56. `&` renders as `&amp;amp;`, which breaks URLs with query strings.

### html: `selector"..."` never matches in `Dom#select`

Filed as #1765. Found 2026-10-20 while writing T-09. Verified on 0.0.56. The interpolator builds `CssSelector.Raw`, which `select` never matches (`DomSelection.scala:296`; `DomSelectionSpec` asserts this).

### html and schema: `p(...)` is ambiguous on Scala 3 when both packages are imported

Found 2026-10-20 while writing T-09. Verified. Not filed.

**Summary.** `import zio.blocks.html._` together with `import zio.blocks.schema._` makes `p(...)` ambiguous on Scala 3, because schema exports the `p"..."` path interpolator. Any code that renders HTML from schema-backed data with wildcard imports hits this. Options: rename one of them, or move the path interpolator out of the package object.

## Usability traps

These behave as designed but surprise users. Each should at least be documented where users meet it.

### schema-toon: `ArrayFormat.List` still writes a table for a record list inside a list item

Found 2026-10-20 while writing H-12 (`docs/guides/llm-data-with-toon.md`). Verified on 0.0.56. Intended behaviour (a conformance test asserts it), but it contradicts the `ArrayFormat.List` Scaladoc.

**Summary.** With one deriver set to `ArrayFormat.List`, the same `chapters` field is written as a table when its record sits inside a list item, and as a `- ` list when it does not. The `ArrayFormat.List` Scaladoc says "Force list format with `- ` markers" (`ArrayFormat.scala:46-48`), and `toon.md`'s ArrayFormat section says the same, so users get a table they did not ask for.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-schema-toon` 0.0.56, run from `schema-examples`):

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class Chapter(title: String, pages: Int)
object Chapter { implicit val schema: Schema[Chapter] = Schema.derived }

case class Book(title: String, chapters: List[Chapter])
object Book { implicit val schema: Schema[Book] = Schema.derived }

case class Shelf(books: List[Book])
object Shelf { implicit val schema: Schema[Shelf] = Schema.derived }

val listFormat = ToonCodecDeriver.withArrayFormat(ArrayFormat.List)
val shelf      = Shelf(List(Book("Zionomicon", List(Chapter("Essentials", 30), Chapter("Fibers", 25)))))

val codec = Shelf.schema.derive(listFormat)
val toon  = codec.encodeToString(shelf)
println(toon)
// books[1]:
//   - title: Zionomicon
//     chapters[2]{title,pages}:
//         Essentials,30
//         Fibers,25

println(codec.decode(toon) == Right(shelf))
// true

println(Book.schema.derive(listFormat).encodeToString(shelf.books.head))
// title: Zionomicon
// chapters[2]:
//   - title: Essentials
//     pages: 30
//   - title: Fibers
//     pages: 25
```

**Expected (per the Scaladoc).** `chapters` is written in list form in both cases.

**Root cause.** `encodeAsField` decides with `useTabularFormat || (out.isInListItemContext && hasOnlyPrimitiveFields)` (`ToonCodecDeriver.scala:983`). The second part ignores `arrayFormat`, so it applies under every format.

**Why intended.** The output round-trips, and `ToonSpecConformanceSpec` ("roundtrips deep nesting with three levels", around lines 2486-2528) uses `deriveCodec[OrgWrapper](_.withArrayFormat(ArrayFormat.List))` and expects `projects[2]{name,hours}:`.

**Fix.** Document the exception in the `ArrayFormat.List` Scaladoc and `toon.md`, unless the maintainers decide the conformance test encodes a mistake, in which case make `List` apply at every depth.

**Side note, not checked against the TOON spec.** The table rows are indented 4 spaces under their header rather than 2, and the conformance test expects the same.

### An implicit field `Schema` is picked up by `Schema.derived`, but an implicit codec is not

Found 2026-10-20 while writing M-01 (`docs/guides/json-library-migration.md`). Verified on Scala 3.9.0 and 2.13.16 against 0.0.56.

**Summary.** `Schema.derived` searches implicit scope for a `Schema[T]` for each field type (`scala-3/.../SchemaCompanionVersionSpecific.scala:373`), so a custom `implicit val Schema[LocalDate]` in scope changes how that field is derived. An implicit `JsonCodec[LocalDate]` in scope, by contrast, is ignored (see the entry above). A circe user's "put it in implicit scope" instinct therefore works for `Schema` but not for codecs, and neither H-03 nor M-01 mentions the `Schema` route.

**Reproduction** (run with scala-cli):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
import zio.blocks.schema._
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val partnerDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

implicit val partnerDateSchema: Schema[LocalDate] =
  Schema[String].transform[LocalDate](s => LocalDate.parse(s, partnerDateFormat), d => partnerDateFormat.format(d))

case class Order(title: String, orderedOn: LocalDate)
object Order {
  implicit val schema: Schema[Order] = Schema.derived
}

@main def run(): Unit = {
  val order = Order("Zionomicon", LocalDate.of(2025, 3, 14))
  println(order.toJsonString)
  // {"title":"Zionomicon","orderedOn":"14/03/2025"}
  println("""{"title":"Zionomicon","orderedOn":"14/03/2025"}""".fromJson[Order])
  // Right(Order(Zionomicon,2025-03-14))
  println("""{"title":"Zionomicon","orderedOn":"2025-03-14"}""".fromJson[Order])
  // Left(zio.blocks.schema.SchemaError: Text '2025-03-14' could not be parsed at index 2 at: .orderedOn.wrapped)
}
```

The output is identical on Scala 2.13.16 with `zio-blocks-schema` 0.0.56 (plus `scala-reflect`, code wrapped in `object Main extends App`).

**Impact.** The override changes the representation in every format, not only JSON, and turns the field into a wrapper (which may also change the generated JSON Schema; unverified). Decode errors show the raw `DateTimeParseException` text plus an internal `.wrapped` path segment (see the library bug below).

**Suggested fix (docs).** Document that an implicit field `Schema` is honoured by derivation and changes every format. Add it to H-03's "Which Tool When" table as the all-formats option, next to `Deriver#withInstance` for one format.

### A per-derivation `DerivationBuilder#instance` override silently loses to a deriver-level `Deriver#withInstance` override

Found 2026-10-20 while writing the how-to guide `docs/guides/override-codec-for-one-type.md` (backlog H-03), on `main` at `64b2f666`. The code is the same at tag `v0.0.56`. Verified by running against the published 0.0.56.

**Summary.** When a deriver already carries a `withInstance` override for a type, a `DerivationBuilder#instance` override for the same type on one `.derive` call is ignored without any warning. The narrower, more explicit request loses to the broader one, which is the opposite of what users expect from every other override mechanism (optic beats field name beats type).

**Reproduction** (Scala 3.9.0, `zio-blocks-next-schema` 0.0.56):

```scala
import zio.blocks.schema._
import zio.blocks.schema.json._
import zio.blocks.typeid.TypeId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

def dateCodec(f: DateTimeFormatter): JsonCodec[LocalDate] = new JsonCodec[LocalDate] {
  def decodeValue(in: JsonReader): LocalDate           = LocalDate.parse(in.readString(), f)
  def encodeValue(x: LocalDate, out: JsonWriter): Unit = out.writeVal(f.format(x))
}

case class Order(title: String, orderedOn: LocalDate)
object Order { implicit val schema: Schema[Order] = Schema.derived }

val order = Order("Zionomicon", LocalDate.of(2025, 3, 14))

// The application-wide deriver writes dd/MM/yyyy
val deriver = JsonCodecDeriver.withInstance[LocalDate](dateCodec(DateTimeFormatter.ofPattern("dd/MM/yyyy")))

// One endpoint asks for yyyyMMdd for this single codec
val codec = Schema[Order]
  .deriving(deriver)
  .instance(TypeId.of[LocalDate], dateCodec(DateTimeFormatter.BASIC_ISO_DATE))
  .derive

println(codec.encodeToString(order))
// {"title":"Zionomicon","orderedOn":"14/03/2025"}
```

**Expected.** `{"title":"Zionomicon","orderedOn":"20250314"}`: the override given for this one derivation should win over the deriver's default, or the conflict should at least be reported.

**Root cause.** `DerivationBuilder.derive` merges the two lists as `instanceOverrides ++ deriver.instanceOverrides` and then builds maps with `.toMap`, so for the same key the later entry, the deriver's, wins (`schema/shared/src/main/scala/zio/blocks/schema/derive/DerivationBuilder.scala:125-130`; the same applies to modifiers at `:126`). The Scaladoc of `Deriver#withInstance` documents this order (`schema/shared/src/main/scala/zio/blocks/schema/derive/Deriver.scala:108-113`), so it is intended, but nothing tells the user at the call site.

**Impact.** A team that configures one shared deriver cannot make an exception for a single codec with `DerivationBuilder#instance`; the call compiles and silently does nothing. The only workaround is to build a second deriver without the conflicting override.

**Test gap.** `DeriverWithInstanceSpec` tests a deriver-level override and a builder-level override separately (`schema/shared/src/test/scala/zio/blocks/schema/derive/DeriverWithInstanceSpec.scala:111-240`), but never both for the same type, so the precedence is untested.

**Suggested fix.** Swap the merge order to `deriver.instanceOverrides ++ instanceOverrides` (and the same for `modifierOverrides`) so that per-derivation overrides refine the deriver's defaults, update the Scaladoc at `Deriver.scala:108-113`, and add a `DeriverWithInstanceSpec` test that combines both levels for the same type. If the current order must stay, document it next to `DerivationBuilder#instance` as well, since that is where users look.

### A custom codec is silently not used when given as an implicit or through a configured deriver plus `toJsonString`

Found 2026-10-20 while writing the how-to guide `docs/guides/override-codec-for-one-type.md` (backlog H-03), on `main` at `64b2f666`. The code is the same at tag `v0.0.56`. Verified by running against the published 0.0.56.

**Summary.** Users coming from circe or zio-json expect an `implicit JsonCodec[LocalDate]` in scope to change how derived codecs encode `LocalDate`. In ZIO Blocks it has no effect, and no warning is emitted. Separately, after a user registers an override with `Deriver#withInstance`, the `toJsonString` and `fromJson` extension methods still use the default codec, again silently.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-schema` 0.0.56):

```scala
import zio.blocks.schema._
import zio.blocks.schema.json._
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

implicit val europeanDate: JsonCodec[LocalDate] = new JsonCodec[LocalDate] {
  def decodeValue(in: JsonReader): LocalDate           = LocalDate.parse(in.readString(), fmt)
  def encodeValue(x: LocalDate, out: JsonWriter): Unit = out.writeVal(fmt.format(x))
}

case class Order(title: String, orderedOn: LocalDate)
object Order { implicit val schema: Schema[Order] = Schema.derived }

val order = Order("Zionomicon", LocalDate.of(2025, 3, 14))

// 1. The implicit codec in scope is ignored by derivation
println(order.toJsonString)
// {"title":"Zionomicon","orderedOn":"2025-03-14"}
println(Schema[Order].derive(JsonFormat).encodeToString(order))
// {"title":"Zionomicon","orderedOn":"2025-03-14"}

// 2. A configured deriver works, but toJsonString does not use it
val codec = Schema[Order].deriving(JsonCodecDeriver.withInstance[LocalDate](europeanDate)).derive
println(codec.encodeToString(order))
// {"title":"Zionomicon","orderedOn":"14/03/2025"}
println(order.toJsonString)
// {"title":"Zionomicon","orderedOn":"2025-03-14"}
```

**Expected.** Either the implicit codec is picked up, or the user is told it will not be. Users reasonably assume `toJsonString` uses "the" JSON codec of their type.

**Root cause.** Derivation is driven by the schema, never by implicit search: `JsonCodecDeriver` maps every primitive to a built-in codec, for example `case _: PrimitiveType.LocalDate => localDateCodec` (`schema/shared/src/main/scala/zio/blocks/schema/json/JsonCodecDeriver.scala:310`), and overrides only come from `Deriver#withInstance` or `DerivationBuilder#instance`. `toJsonString` is `schema.jsonCodec.encodeToString(self)` (`schema/shared/src/main/scala-3/zio/blocks/schema/syntax.scala:34`, same in `scala-2/`), and `Schema#jsonCodec` is `getInstance(JsonFormat)`, which always uses the default `JsonFormat` deriver (`schema/shared/src/main/scala/zio/blocks/schema/Schema.scala:38-40`).

**Impact.** The most common first attempt at a custom date format (an implicit codec) compiles and produces the default format, so users conclude the feature is missing (see zio-json issue #1036 for how often this is asked). After finding `withInstance`, the convenience methods from the getting-started tutorial quietly bypass it.

**Suggested fix.** Both behaviours follow from the design, so the cheapest fix is documentation: state both points in the Scaladoc of `syntax.toJsonString`/`fromJson` and `Deriver#withInstance` (the H-03 guide and `codec.md` now do). A library-level option is a `toJsonString(codec)`/`fromJson(codec)` overload, or a way to attach a configured deriver to a `Schema` so that `Schema#jsonCodec` uses it.

- **`@Modifier.rename` drops the original name.** An `Option` field sent under its Scala name silently decodes to `None`. Use `@Modifier.alias` to accept both. Found while writing H-02.
- **`.each`, `.when` and `.atKey` work only inside the companion.** They are members of `CompanionOptics`, so `Inventory.$(_.shelves.each...)` written at a call site does not compile (`value each is not a member of List[Shelf]`). Found while writing T-03.
- **A chatty TOON reply decodes as if it were clean.** A prose line ending in `:` (for example `Sure! Here is my pick:`) is read as an unknown key and skipped by default. `withRejectExtraFields(true)` catches it. Found while writing H-12.
- **JSON Schema error paths are imprecise.** An `additionalProperties: false` failure reports `Schema rejects all values at: .`, at the object rather than the extra field. Errors raised inside `fromJsonSchema` end with ` at: .wrapped`. Found while writing H-05.
- **Durations and sizes use Java syntax only.** `10s` is rejected because durations go through `java.time.Duration.parse` (ISO-8601, e.g. `PT10S`; `ConfigDecoderDeriver.scala:416`), and size units like `512M` are not parsed. Typesafe Config's `getDuration`/`getBytes` accept both. Found while writing M-06.
- **ZIO Blocks rejects numbers sent as strings.** `"year":"2024"` for an `Int` field fails with `illegal number at: .year`. circe and zio-json both accept numeric strings; jsoniter-scala rejects them too. A common migration surprise for APIs that send numbers as strings. Documented in M-01. Found while writing M-01.
- **A missing `List` field decodes to `Nil`.** circe fails with `Missing required field` and zio-json with `.tags(missing)`; ZIO Blocks (and jsoniter-scala) give an empty list because `requireCollectionFields` defaults to false. Usually convenient, but it silently accepts payloads that other libraries reject. Found while writing M-01.
- **`withRequireCollectionFields(true)` and `withRequireDefaultValueFields(true)` also change the encoder.** Besides making decoding strict, they make the encoder write empty collections and default values, because `emptyCollectionConstructor` and `getDefaultValue` become null/None (`JsonCodecDeriver.scala:586-590`, `731-739`, `3027-3029`, `3075-3078`). Users who set them for strict decoding get different output. Found while writing M-01.
- **Nested and concurrent access inside a test transaction on SQLite.** Inside a transaction that will roll back, a second connection sees only committed rows, and a nested `JdbcTransactor#transact` blocks and fails with `SQLITE_BUSY` after about 5 s. `DbTx#transact` (a savepoint) works. Verified while writing H-47; the guide warns about it.
- **Scope and Wire error codes never appear in compiler output.** `ErrorMessages.scala` numbers them E1-E17, RE1-RE3 and W1, but the messages users see carry no code, so docs must index errors by message text. Found while writing H-31.
- **`defer` on a closed scope is a silent no-op** (`Scope.scala:202`), while `allocate` and `$` throw. Found while writing H-31.
- **`jdbc:sqlite::memory:` gives every connection its own empty database.** Code that opens more than one connection loses its tables. Found while writing T-07; now noted in `transactor.md`.

## Test and CI

### Flaky streams test: `unfoldAsync - reset invalidates a stale completion and restarts from initial state`

Seen 2026-10-20 on PR #1773 (docs only, touches nothing in `streams/` or `async/`). In `testJVM (25, JVM, 3.3.x)`, this test hit its 30 s timeout while the other 12,760 tests passed. Rerunning is the current workaround. Needs investigation: a timing dependency in the test, or a real race in `unfoldAsync` reset.

Seen again 2026-10-20 on PR #1774 (H-12, docs only): an async-reader test hit the same 30 s timeout in `testJVM (17, JVM, 3.9.x)`. Later pushes replaced that run. Two sightings on unrelated docs PRs in one day suggest the timeouts are too tight for loaded CI runners.

### mdoc: `mdoc:fail` on Scala 3 ignores earlier `:reset` sections

Seen 2026-10-20 while writing H-02. A `mdoc:fail` block after a `mdoc:silent:reset` reports bogus "already defined" errors for names from before the reset, so the expected compile error cannot be shown. H-02 described that error in prose instead. Pages that rely on `mdoc:fail` (H-31) should check their rendered output.

Related, seen while writing H-31: `mdoc:fail` and `mdoc:warn` inline the earlier `compile-only` blocks of the same section, so a `compile-only` fix block that redefines a name breaks a later `mdoc:warn` with no reason given. Use `mdoc:silent:reset` for such fix blocks. H-31 added this to the docs-mdoc-conventions skill.

### `DeriverWithInstanceCsvSpec` asserts with `.contains`

Found 2026-10-20 while writing H-03. The schema-csv spec checks rendered output with `.contains`, against the AGENTS.md rule to assert the full rendered string with `==`. A regression in field order or separators would pass.

### `schema-examples` has no scalafmt setup

Found 2026-10-20 while writing H-03. The standalone `schema-examples/` build has no scalafmt plugin or config, so `sbt scalafmtAll` in the root build does not format it. Sessions format example files by hand or with `cs launch scalafmt -- --config .scalafmt.conf`. Add the plugin (or a `.scalafmt.conf` symlink) to the example builds, or document the command in AGENTS.md.

### Example projects' tests never run in CI

Found 2026-10-20 while writing H-47. CI's "Compile example projects" step (`project/CiWorkflow.scala:360`, generated into `.github/workflows/ci.yml`) runs `sbt compile` in each `*-examples/` build, so `src/test` sources are never compiled or run. `sql-examples` is the first examples build with tests (H-47's `BookshopSpec`, 4 tests, passing locally against 0.0.56). Switching the step to `sbt test` (and regenerating `ci.yml`) would cover them. This is a CI change for the maintainer to decide; H-47's attempt to make it was blocked by a permission check, so it was left alone.

### Docs build memory with many sbt servers

Seen 2026-10-20: a full `sbt check` was OOM-killed while several worktrees each ran an sbt server (about 4 GB free). Shut down idle servers in finished worktrees with `sbt --client shutdown`.

## Documentation errors

Pages that contradict the source. None of these were copied into the new guides.

**schema / JSON**

- `json-config.md` (around lines 146-175) says `implicit val nameMapper: NameMapper = NameMapper.SnakeCase` in the companion makes `toJson` emit snake_case (confirmed wrong by mdoc while writing M-01: the field stays `firstAuthor`). Nothing reads an implicit `NameMapper`. The real ways are `JsonCodecDeriver.withFieldNameMapper` with `Schema#derive`, or `@Modifier.fieldNaming`. The output comments hide the problem because the blocks are compile-only. Severity: high, because it teaches code that silently does nothing.
- `customize-json-encoding.md` (H-02; the table around lines 357-361 and the prose around line 431) presents `withRequireCollectionFields` and `withRequireDefaultValueFields` as decoder-only settings. Setting either to true also makes the encoder write empty collections and default values (see Usability traps). Found while writing M-01.
- `json-config.md` "Custom Mappers" lists only `Identity`, `SnakeCase` and `KebabCase`; `CamelCase`, `PascalCase` and `Custom(f)` also exist.
- `modifier.md`, `transient`: does not say that a transient field's JSON value is ignored on decode and the default is used.
- `schema-error.md` (lines 56-57, 171-172, 283-289, 313-316) shows `Missing field 'x' at: .path` as the decode result. JSON decoding reports `missing required field "x" at: .path` (`JsonReader.scala:89`).
- `syntax.md` shows `show` output as `Record { name = Alice, age = 30 }`; the real output is the EJSON form (`DynamicValue.toString`).
- `Schema.fromJsonSchema` Scaladoc (`Schema.scala:357-358`) says values are validated "during construction"; validation happens at decode time (`:366-370`).
- `validation.md:356-373` says `Validation` constraints become `minimum`, `maxLength`, `pattern` and so on in `toJsonSchema`. They do not; only parsing a JSON Schema maps them (`JsonSchemaToReflect.scala:261-285`).
- `validation.md:304` uses `JsonDecoder[Order].decodeString`; `JsonDecoder` was removed in #1240.
- `json-schema.md:366` shows an error message without the ` at: <path>` suffix that `SchemaError.message` adds. The page also does not mention the `pattern` anchoring behaviour above.
- `ValidationOptions` Scaladoc (`JsonSchema.scala:376-377`) says format checks are annotation-only by default; the code defaults to `validateFormats = true`.
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
- Line 247 says `HoconError` carries the line and column. That holds only for lexer errors; resolution errors report 0:0. From source.
- The HOCON section has no warnings for the dropped includes, environment substitutions, array concatenation and duration/size syntax above.

### docs: `formats.md` says infinite HOCON numbers keep their `Double` rendering

Found 2026-10-20 while writing `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `docs/reference/config/formats.md:164` says: "Non-integral and infinite values keep their `Double` rendering." The HOCON parser never produces an infinite number. A literal too large for a `Double` (such as `1e400`), `Infinity` and `-1e400` are all kept as the text that was written, as strings.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config-hocon` 0.0.56):

```scala
import zio.blocks.config._
import zio.blocks.config.hocon._

List("big = 1e400", "big = Infinity", "big = 1.5", "big = -1e400").foreach { doc =>
  println(ConfigSource.fromHocon(doc).map(_.all("").map { case (k, v) => s"$k='${v.value}'" }.toList))
}
// Right(List(big='1e400'))
// Right(List(big='Infinity'))
// Right(List(big='1.5'))
// Right(List(big='-1e400'))

println(HoconParser.parse("big = 1e400"))
// Right(Obj(Map(big -> Str(1e400))))
```

**Root cause.** `interpretUnquoted` keeps a number only when `!d.isNaN && !d.isInfinite`, and otherwise falls back to a string (`config-hocon/shared/src/main/scala/zio/blocks/config/hocon/HoconParser.scala:392-393`).

**Suggested fix.** Replace the sentence with: "Non-integral values keep their `Double` rendering. A number too large for a `Double`, such as `1e400`, is kept as the text that was written."

### docs: `formats.md` leaves out the 1 MiB file-size limit of `ConfigSource.fromFile`

Found 2026-10-20 while writing `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** The "Loading from a File" section of `docs/reference/config/formats.md` (`:199-228`) lists `allowedBase` and `maxIncludeDepth` as the two guards on `ConfigSource.fromFile`. It does not mention the third one: `fromFile` refuses a file or include larger than 1 MiB, and the only way to raise the limit is `ConfigSource.fromFileWithLimits(..., maxFileBytes = ...)`, which the page never names. A reader whose generated configuration grows past 1 MiB gets a parse error that the page does not explain.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config-hocon` 0.0.56, JVM):

```scala
import zio.blocks.config._
import zio.blocks.config.hocon._
import java.nio.file.Files

val file = Files.createTempDirectory("bookstore").resolve("application.conf")
Files.writeString(file, "bookstore.name = \"Scala Bookstore\"\n" + ("# padding\n" * 120000))
println(Files.size(file))

ConfigSource.fromFile(file.toString) match {
  case Left(e: ConfigError.ParseError) => println(e.cause.map(_.getMessage))
  case other                           => println(other)
}

ConfigSource.fromFileWithLimits(file.toString, maxFileBytes = 4L * 1024 * 1024).map(_.all("").keys.toList).foreach(println)
// 1200035
// Some(File size exceeds limit (1048576 bytes) (line 0, column 0))
// List(bookstore.name)
```

**Root cause.** `fromFile` delegates to `fromFileWithLimits` with `DefaultMaxFileBytes = 1024L * 1024L` (`config-hocon/jvm/src/main/scala/zio/blocks/config/hocon/HoconConfigSourcePlatform.scala:28`, `:204`), and the read throws `File size exceeds limit` above it (`:36`, `:54`). The docs table was written before the limit existed (added by PR #1649).

**Suggested fix.** Add a third row to the table: `maxFileBytes` | `1 MiB` | "Maximum size of the file and of each include; a larger one is a parse failure. Set it with `ConfigSource.fromFileWithLimits`." Then change "Two parameters guard ..." to "Three parameters guard ...", and add the size failure to the failures paragraph.

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

- `codec.md` never mentioned `Deriver#withInstance`/`withModifier`, and its BinaryCodec/TextCodec paragraph was wrong (`CsvCodec` extends `TextCodec`; XML and YAML extend `BinaryCodec`; `BsonCodec` extends neither). New "Overriding a Type on a Deriver" subsection and fix in PR #1784 (open).
- Stale example: `schema-examples/src/main/scala/deriveroverrides/CompleteExample.scala` predated #1348 and showed no codec override. Rewritten in #1781 to demonstrate type, term-name and optic overrides with a round trip and the current run command.
- Docs build: a broken relative link in `docs/reference/schema/modifier.md:395` (`../guides/` instead of `../../guides/`), added by #1775, failed Build Docs on main. Fixed in #1772.
- Config: `formats.md:97` (YAML parse error type) and `config-source.md` (`EnvSource` and `keyFormat`). Fixed in #1771.
- sql: `sql-transactions.md` default isolation and PostgreSQL facts; `repo.md`/`table.md`/`ddl.md` upserts, `pageAfter` and the missing `PRIMARY KEY` caveat; `:memory:` warnings in `transactor.md` and `transactor-zio.md`; `db-con.md` `DbTx` members; the backlog's "sql has no JS backend" note. Fixed in #1760.
- Codecs: `format.md` (`sealed trait`, real `BinaryFormat`/`TextFormat` signatures, which formats are binary); TOON `ArrayFormat.Auto` Scaladoc and `toon.md:706`; `csv.md` (`CsvConfig` ignored by derived codecs, runtime rejection, `Option` rejected); `xml.md` stale version; `built-in-codecs/index.md` artifact prefix. In PR #1773 (open).
