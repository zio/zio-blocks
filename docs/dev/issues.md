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

### schema-yaml: `YamlWriter` leaves a value that starts with `#` unquoted, so YAML readers drop it as a comment

Found 2026-10-21 while writing the how-to guide `docs/guides/write-and-export-openapi.md` (backlog H-39), on `main` at `a44dc6db`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `Yaml#print` and `Yaml#printPretty` write a string scalar that starts with `#` without quotes. In YAML, a `#` that follows whitespace starts a comment, so `key: #value` means `key` with a null value. Any YAML reader that follows the spec loses the value. This breaks every OpenAPI document exported through `Yaml.fromJson`, because every local reference is a `$ref` whose value starts with `#` (`#/components/schemas/Book`): Swagger UI and client generators see `$ref: null`. ZIO Blocks' own `YamlReader` is lenient about this case and keeps the value, so a round trip inside ZIO Blocks does not show the problem.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-schema-yaml` 0.0.56; SnakeYAML 2.3 only to read the output with a spec-following reader):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema-yaml:0.0.56
//> using dep org.yaml:snakeyaml:2.3

import zio.blocks.schema.json.Json
import zio.blocks.schema.yaml.{Yaml, YamlReader}

@main def yamlHash(): Unit = {
  val json = Json.Object(
    "$ref"   -> Json.String("#/components/schemas/Book"),
    "hashtag" -> Json.String("#scala"),
    "inside" -> Json.String("C# and F#")
  )
  val text = Yaml.fromJson(json).printPretty
  println(text)
  // ---
  // $ref: #/components/schemas/Book
  // hashtag: #scala
  // inside: C# and F#

  // Read the text with SnakeYAML, a YAML 1.1 reader that follows the spec on comments
  println(new org.yaml.snakeyaml.Yaml().load[Object](text))
  // {$ref=null, hashtag=null, inside=C# and F#}

  // ZIO Blocks' own reader keeps the values, so a round trip inside ZIO Blocks hides the problem
  println(YamlReader.read(text).toJson.print)
  // {"$ref":"#/components/schemas/Book","hashtag":"#scala","inside":"C# and F#"}
}
```

**Expected.** `$ref: "#/components/schemas/Book"` and `hashtag: "#scala"`, so that every YAML reader reads the strings back.

**Root cause.** `schema-yaml/shared/src/main/scala/zio/blocks/schema/yaml/YamlWriter.scala`, `needsQuoting` (`:203`). The list of first characters that force quotes (`:213-214`) has no `#`, and the loop quotes a `#` only when a space comes before it (`:223`). The same list also misses the other YAML indicators that cannot start a plain scalar, such as `,`, `]`, `}`, and `-`, `:` or `?` followed by a space (from source, not run). The lenient reader matches the writer: `YamlReader.scala:508` treats `#` as a comment only when a space comes before it inside the value, which hides the bug from round-trip tests.

**Impact.** Silent data loss for any YAML consumer outside ZIO Blocks. For OpenAPI documents, every `$ref` is lost, so the documented way to export an OpenAPI document as YAML (`Yaml.fromJson(openAPICodec.encodeValue(api))`) produces a broken document. The H-39 guide works around it by quoting `$ref` values with a regular expression after printing.

**Test gap.** `YamlWriterSpec` tests a `#` after a space (`"value #comment"`, `:281`) but no value that starts with `#` or with another indicator character, and no test reads the writer's output with a reader other than `YamlReader`.

**Suggested fix.** In `needsQuoting`, quote a value whose first character is any YAML indicator (`#`, `,`, `[`, `]`, `{`, `}`, `&`, `*`, `!`, `|`, `>`, `'`, `"`, `%`, `@`, `` ` ``), or that starts with `-`, `?` or `:` followed by a space, and a value with leading or trailing spaces. Make `YamlReader` treat `#` at the start of a value as a comment, as the spec says. Add writer tests for each indicator and a test that the OpenAPI petstore document survives `Yaml.fromJson(...).printPretty` and a spec-following read.

**Addendum (2026-10-21, H-15).** Its suggested fix already names the other cases from source; they are now verified by running. A value that starts with `- ` or ends with `:` is written unquoted and rejected by SnakeYAML, and leading or trailing spaces are lost by every reader, including `YamlReader`. Found 2026-10-21 while writing H-15 (`docs/guides/read-and-write-yaml.md`), `main` at `4ec6dd90`, same at `v0.0.56`.

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema-yaml:0.0.56
//> using dep org.yaml:snakeyaml:2.3
import zio.blocks.schema.yaml._

@main def writerQuoting(): Unit = {
  val snake = new org.yaml.snakeyaml.Yaml()
  for (value <- List("- Zionomicon", "Zionomicon:", " Zionomicon", "Zionomicon ")) {
    val text = Yaml.Mapping.fromStringKeys("title" -> Yaml.Scalar(value)).print
    val read = scala.util.Try(snake.load[Object](text)).fold(e => "error: " + e.getMessage.linesIterator.next(), v => s"'$v'")
    println(s"[$value] is written as <$text>; SnakeYAML reads $read; YamlReader reads ${YamlReader.read(text)}")
  }
}
// Output:
// [- Zionomicon] is written as <title: - Zionomicon>; SnakeYAML reads error: sequence entries are not allowed here; YamlReader reads Mapping(Chunk((Scalar(title,None),Scalar(- Zionomicon,None))))
// [Zionomicon:] is written as <title: Zionomicon:>; SnakeYAML reads error: mapping values are not allowed here; YamlReader reads Mapping(Chunk((Scalar(title,None),Scalar(Zionomicon:,None))))
// [ Zionomicon] is written as <title:  Zionomicon>; SnakeYAML reads '{title=Zionomicon}'; YamlReader reads Mapping(Chunk((Scalar(title,None),Scalar(Zionomicon,None))))
// [Zionomicon ] is written as <title: Zionomicon >; SnakeYAML reads '{title=Zionomicon}'; YamlReader reads Mapping(Chunk((Scalar(title,None),Scalar(Zionomicon,None))))
```

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

### scope: `Resource.from` never reports a dependency cycle (E10); the macro crashes instead

Filed: [zio/zio-blocks#1767](https://github.com/zio/zio-blocks/issues/1767).

Found 2026-10-20 while writing the how-to guide `docs/guides/fix-scope-and-wire-compile-errors.md` (backlog H-31), on `main` at `64b2f666`. Reproduced against the published `0.0.56`.

**Summary.** `ErrorMessages.renderDependencyCycle` (E10) draws a cycle diagram, but `Resource.from` can never reach it. A constructor cycle crashes the macro with `java.util.NoSuchElementException: key not found` and a long compiler stack trace instead.

**Reproduction** (`scala-cli run .`; Scala 3.9.0, `zio-blocks-next-scope` 0.0.56):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-scope:0.0.56
//> using options -experimental

import zio.blocks.scope.*

class InventoryRepo(prices: PriceService)
class PriceService(inventory: InventoryRepo)
class Catalog(inventory: InventoryRepo)

@main def cycle(): Unit = {
  val catalog = Resource.from[Catalog]()
  println(catalog)
}
// -- Error: Cycle.scala:12:38
// 12 |  val catalog = Resource.from[Catalog]()
//    |                ^^^^^^^^^^^^^^^^^^^^^^^^
//    |Exception occurred while executing macro expansion.
//    |java.util.NoSuchElementException: key not found: InventoryRepo
//    |	at scala.collection.immutable.Map$EmptyMap$.apply(Map.scala:270)
//    |	at scala.collection.immutable.Map$EmptyMap$.apply(Map.scala:270)
//    |	at zio.blocks.scope.ResourceMacros$.buildDepAcquisition$1(ResourceMacros.scala:515)
//    |	at zio.blocks.scope.ResourceMacros$.createResourceExpr$1$$anonfun$5$$anonfun$1(ResourceMacros.scala:529)
//    |	... (about 40 more compiler frames)
```

**Expected.** The E10 error: `Dependency cycle detected`, with a diagram of `InventoryRepo ──► PriceService` and the hints "Introducing an interface/trait", "Using lazy initialization" and "Restructuring dependencies".

**Root cause.** `resolveWire` runs two checks in this order: `if (aliasMap.contains(requiredKey)) return`, then `if (chain.contains(requiredKey)) abortDependencyCycle(...)`. It records a type in `aliasMap` *before* it recurses into that type's dependencies, so every key on `chain` is already in `aliasMap`, and the early return always wins. The cycle check is dead code. Resolution then "succeeds" with a cyclic wire map, the topological sort visits each node once, and code generation looks up a dependency value that was never produced. Both macros have the bug: Scala 3 `scope/shared/src/main/scala-3/zio/blocks/scope/ResourceMacros.scala:248-259`, Scala 2 `scope/shared/src/main/scala-2/zio/blocks/scope/ResourceVersionSpecific.scala:350-361`.

**Impact.** Every dependency cycle, the most common wiring mistake, produces an unreadable macro crash. `scope-examples/.../CircularDependencyDemoExample.scala`, `docs/reference/resource-management/wire.md` and `index.md` all promise compile-time cycle detection.

**Test gap.** No test triggers E10 through the macro. `ErrorMessagesSpec` only tests the string renderer.

**Suggested fix.** Run the cycle check before the `aliasMap` early return, in both macros. Diamonds still work, because a shared dependency reached through two branches is never on the current `chain`. Add a `typeCheck` test to `WireMacroErrorSpec`.

### scope: A dependency on a Java class with constructor parameters crashes the `Resource.from` / `Wire.shared` macro

Found 2026-10-20 while writing the how-to guide `docs/guides/fix-scope-and-wire-compile-errors.md` (backlog H-31), on `main` at `64b2f666`. Reproduced against the published `0.0.56`. The same crash happens for `Wire.shared[java.util.ArrayList[String]]` (seen in mdoc).

**Summary.** When `Resource.from` has to auto-create a Java class whose constructors take parameters, or `Wire.shared` derives one, the macro throws an internal compiler `AssertionError` instead of reporting a Scope error.

**Reproduction** (`scala-cli run .`; Scala 3.9.0, `zio-blocks-next-scope` 0.0.56):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-scope:0.0.56
//> using options -experimental

import zio.blocks.scope.*

class Catalog(titles: java.util.ArrayList[String])

@main def javaDep(): Unit = {
  val catalog = Resource.from[Catalog]()
  println(catalog)
}
// -- Error: JavaDep.scala:10:38
// 10 |  val catalog = Resource.from[Catalog]()
//    |                ^^^^^^^^^^^^^^^^^^^^^^^^
//    |Exception occurred while executing macro expansion.
//    |java.lang.AssertionError: assertion failed: TypeBounds(TypeRef(ThisType(TypeRef(NoPrefix,module class scala)),class Nothing),TypeRef(ThisType(TypeRef(NoPrefix,module class <special-ops>)),type <FromJavaObject>)) in in intersection  & Int
//    |	at scala.runtime.Scala3RunTime$.assertFailed(Scala3RunTime.scala:10)
//    |	at dotty.tools.dotc.core.Types$.expectValueTypeOrWildcard(Types.scala:3777)
//    |	at dotty.tools.dotc.core.Types$AndType$.apply(Types.scala:3587)
//    |	at scala.quoted.runtime.impl.QuotesImpl$reflect$AndType$.apply(QuotesImpl.scala:2175)
//    |	at scala.quoted.runtime.impl.QuotesImpl$reflect$AndType$.apply(QuotesImpl.scala:2175)
//    |	at zio.blocks.scope.internal.MacroCore$.computeInType$$anonfun$1(MacroCore.scala:101)
//    |	at scala.collection.AbstractIterable.reduceLeftOption(Iterable.scala:979)
//    |	at zio.blocks.scope.internal.MacroCore$.computeInType(MacroCore.scala:101)
//    |	at zio.blocks.scope.internal.WireCodeGen$.deriveWire(WireCodeGen.scala:78)
//    |	at zio.blocks.scope.ResourceMacros$.autoCreateWire$1(ResourceMacros.scala:236)
//    |	... (more frames)
```

**Expected.** A Scope error that says the Java class can't be derived and that you should provide its wire, for example `Wire(new java.util.ArrayList[String]())`.

**Root cause** (from the stack trace and source, not debugged further). `WireCodeGen.deriveWire` takes the parameter types of the class's "primary constructor" (`WireCodeGen.scala:66-70`). For a Java class, Scala 3 hands back constructor parameters whose types are not value types: here a `TypeBounds` built from Java's `Object` (`<FromJavaObject>`). `MacroCore.computeInType` (`scala-3/.../internal/MacroCore.scala:101`) passes every dependency type to `AndType(_, _)`, which asserts that its arguments are value types.

**Impact.** Any graph that contains a JDK or other Java class with constructor parameters fails with a compiler crash that doesn't name the cause.

**Test gap.** No test derives a wire for a Java-defined class.

**Suggested fix.** In `deriveWire` and `autoCreateWire`, detect Java-defined classes (`sym.flags.is(Flags.JavaDefined)`) and abort with a Scope error asking for an explicit wire. Alternatively, reject any dependency type that is not a value type before calling `AndType`.

### scope: Duplicate constructor parameter types (E12) are never reported in Scala 3; both parameters get the same instance

Found 2026-10-20 while writing the how-to guide `docs/guides/fix-scope-and-wire-compile-errors.md` (backlog H-31), on `main` at `64b2f666`. Reproduced against the published `0.0.56`.

**Summary.** `ErrorMessages.renderDuplicateParamType` (E12) says a constructor can't take two parameters of the same type, because the graph holds one value per type. In Scala 3 the check never fires: the code compiles, and both parameters silently receive the same instance.

**Reproduction** (`scala-cli run .`; Scala 3.9.0, `zio-blocks-next-scope` 0.0.56):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-scope:0.0.56
//> using options -experimental

import zio.blocks.scope.*

class Db
class Catalog(val primary: Db, val replica: Db)

@main def dupParam(): Unit = {
  val sameDb: Boolean = Scope.global.scoped { scope =>
    import scope.*
    val catalog = allocate(Resource.from[Catalog]())
    $(catalog)(c => c.primary.eq(c.replica))
  }
  println(s"primary eq replica: $sameDb")
}
// primary eq replica: true
```

`Wire.shared[Catalog]` also compiles (seen in mdoc).

**Expected.** E12: `Constructor of Catalog has multiple parameters of type Db`, with the hint to wrap one parameter in a distinct type.

**Root cause** (from source). `MacroCore.computeInType` builds `Db & Db` (`scala-3/.../internal/MacroCore.scala:99-102`). `MacroCore.flattenIntersection` calls `.dealias.simplified` on it (`:113`), which collapses `Db & Db` to `Db`. So the wire's `inTypes` is `List(Db)`, and the duplicate check in `ResourceMacros.scala:306-312` never sees two entries. `WireCodeGen.deriveWire` (used by `Wire.shared`) has no duplicate check at all. The Scala 2 macro was not checked.

**Impact.** A class that needs two different values of one type, such as a primary and a replica database, silently gets one instance in both places. The bug shows up only at run time.

**Test gap.** No test triggers E12 through the macro. `ErrorMessagesSpec` only tests the string renderer.

**Suggested fix.** Run the duplicate check on the constructor's parameter types before they are combined into an intersection (in `WireCodeGen.deriveWire` and in `ResourceMacros`), and abort with `abortDuplicateParamType`. Add `typeCheck` tests for `Wire.shared` and `Resource.from`.

### scope: The E3 subtype-conflict hint suggests code that isn't valid Scala

Found 2026-10-20 while writing the how-to guide `docs/guides/fix-scope-and-wire-compile-errors.md` (backlog H-31), on `main` at `64b2f666`. Reproduced against the published `0.0.56`.

**Summary.** The hint of the E3 "Dependency type conflict" error builds a wrapper name by pasting `Wrapped` in front of the fully qualified type name. For any type in a package, the suggested `case class` and `opaque type` don't compile.

**Reproduction** (`scala-cli run .`; Scala 3.9.0, `zio-blocks-next-scope` 0.0.56):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-scope:0.0.56
//> using options -experimental

package com.example.bookstore

import zio.blocks.scope.*

class InventoryRepo
class SqlInventoryRepo extends InventoryRepo
class Catalog(inventory: InventoryRepo, sql: SqlInventoryRepo)

@main def subtype(): Unit = {
  val wire = Wire.shared[Catalog]
  println(wire)
}
// -- Error: Subtype.scala:14:24
// 14 |  val wire = Wire.shared[Catalog]
//    |             ^^^^^^^^^^^^^^^^^^^^
//    |── Scope Error ─────────────────────────────────────────────────────────────────
//    |
//    |  Dependency type conflict in com.example.bookstore.Catalog
//    |
//    |  com.example.bookstore.SqlInventoryRepo is a subtype of com.example.bookstore.InventoryRepo.
//    |
//    |  When both types are dependencies, Context cannot reliably distinguish
//    |  them. The more specific type may be retrieved when the more general
//    |  type is requested.
//    |
//    |  To fix this, wrap one or both types in a distinct wrapper:
//    |
//    |    case class Wrappedcom.example.bookstore.InventoryRepo(value: com.example.bookstore.InventoryRepo)
//    |    or
//    |    opaque type Wrappedcom.example.bookstore.InventoryRepo = com.example.bookstore.InventoryRepo
//    |
//    |────────────────────────────────────────────────────────────────────────────────
```

**Expected.** `case class WrappedInventoryRepo(value: com.example.bookstore.InventoryRepo)`, and the same for the `opaque type`.

**Root cause.** `ErrorMessages.renderSubtypeConflict` interpolates the full `supertype` string as the identifier: `s"case class Wrapped$supertype(value: $supertype)"` and `s"opaque type Wrapped$supertype = $supertype"` (`scope/shared/src/main/scala/zio/blocks/scope/internal/ErrorMessages.scala:311-313`). The macros pass `tpe.show` / `toString`, which is fully qualified. E12's hint (`:559-561`) has the same pattern with `paramType`.

**Impact.** Copying the hint gives a syntax error. The hint also suggests an `opaque type` to Scala 2 users.

**Test gap.** `ErrorMessagesSpec` tests the renderer only with unqualified names.

**Suggested fix.** Build the identifier from the simple name (the text after the last `.`, with type arguments removed) and keep the full name only on the right-hand side. Show the `opaque type` alternative only in Scala 3.

### scope: Four Scope error codes can't be reached, and `renderMissingDependency` is dead code

Found 2026-10-20 while writing the how-to guide `docs/guides/fix-scope-and-wire-compile-errors.md` (backlog H-31), on `main` at `64b2f666`. Reproduced against the published `0.0.56`.

**Summary.** `ErrorMessages` defines messages that user code can't trigger, or can trigger only with nonsense input:

- **E5 (unsupported implicit parameter)** sits behind the E4 check in both macros, so an implicit `Clock` parameter is reported as an ordinary E4 dependency.
- **E2 (no primary constructor) and E9 (no constructor for auto-create)** need a class without a primary constructor. Every Scala class has one, and Java classes either compile or crash the macro (see the Java-class entry above).
- **E6 (cannot extract wire types)** fires only for an argument whose static type isn't `Wire[In, Out]`, which in practice means `???` or `null`.
- **`renderMissingDependency`** (`ErrorMessages.scala:687`), the most elaborate renderer (resolution stack, dependency tree, hints), is never called by either macro. Only `ErrorMessagesSpec` calls it. Missing dependencies are auto-created instead, or fail as E7/E8.

**Reproduction** (`scala-cli run .` per file; Scala 3.9.0, `zio-blocks-next-scope` 0.0.56; each file starts with `//> using scala 3.9.0`, `//> using dep dev.zio::zio-blocks-next-scope:0.0.56` and `//> using options -experimental`):

```scala
// E5.scala: expected E5, got E4
import zio.blocks.scope.*

class Clock
class Catalog()(using Finalizer, Clock)

@main def e5(): Unit = println(Resource.from[Catalog])
// -- Error: E5.scala:10:44
//    |── Scope Error ─────────────────────────────────────────────────────────────────
//    |
//    |  Resource.from[Catalog] cannot be derived.
//    |
//    |  Catalog has dependencies that must be provided:
//    |    • Clock
```

```scala
// E2.scala: no E2 or E9, both lines compile
import zio.blocks.scope.*

class Catalog(owner: java.lang.Object)

@main def e2(): Unit = {
  println(Resource.from[java.lang.Object])
  println(Resource.from[Catalog]())
  println("both compiled")
}
// zio.blocks.scope.Resource$Shared@34340fab
// zio.blocks.scope.Resource$Shared@2eee9593
// both compiled
```

```scala
// E6.scala: the only trigger found for E6
import zio.blocks.scope.*

class Catalog

@main def e6(): Unit = println(Resource.from[Catalog](???))
// -- Error: E6.scala:9:53
//    |── Scope Error ─────────────────────────────────────────────────────────────────
//    |
//    |  Cannot extract types from wire expression.
//    |
//    |  Wire type: scala.Nothing
```

**Expected.** Each defined error is reachable by the mistake it describes, or is removed.

**Root cause** (from source). E5: `ResourceMacros.deriveResourceImpl` aborts with E4 (`scala-3/.../ResourceMacros.scala:75-77`) for any non-`Finalizer` parameter before it builds the constructor call where E5 lives (`:99`). Scala 2 has the same order (`scala-2/.../ResourceVersionSpecific.scala:111-112` before `:129-134`). E2/E9: the `primaryConstructor == Symbol.noSymbol` checks never hold for a class definition. `renderMissingDependency` has no call site in `scope/shared/src/main`.

**Impact.** The error catalog and `ErrorMessagesSpec` suggest coverage that the macros don't have. The best-designed message, missing dependency, is never shown.

**Test gap.** `ErrorMessagesSpec` tests the renderers in isolation, so dead renderers stay green.

**Suggested fix.** Either wire E5 into the dependency loop (check implicit parameter lists before E4) and call `renderMissingDependency` where auto-creation is not wanted, or delete the unreachable renderers and their tests. Add `typeCheck` tests through the macros for each code that stays.

### scope: A failing `scoped` body adds a confusing "Ambiguous given instances" error

Found 2026-10-20 while writing the how-to guide `docs/guides/fix-scope-and-wire-compile-errors.md` (backlog H-31), on `main` at `64b2f666`. Reproduced against the published `0.0.56`.

**Summary.** When the last expression of a `scoped` block fails to type-check, the compiler can't infer the block's result type, and the `Unscoped[A]` search for that type then fails with a second error that names two unrelated built-in instances. The real mistake (calling a method directly on `$[Db]`) is the first error, but the second one points the reader at `Unscoped` instances for `Int` and `FiniteDuration`.

**Reproduction** (`scala-cli run .`; Scala 3.9.0, `zio-blocks-next-scope` 0.0.56):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-scope:0.0.56
//> using options -experimental

import zio.blocks.scope.*

class Db extends AutoCloseable {
  def countBooks(): Int = 42
  def close(): Unit     = ()
}

@main def ambig(): Unit = {
  val n = Scope.global.scoped { scope =>
    import scope.*
    val db = allocate(Resource(new Db))
    db.countBooks()
  }
  println(n)
}
// -- [E008] Not Found Error: Ambig.scala:16:7
// 16 |    db.countBooks()
//    |    ^^^^^^^^^^^^^
//    |    value countBooks is not a member of scope.$[Db]
// -- [E172] Type Error: Ambig.scala:17:3
// 17 |  }
//    |   ^
//    |Ambiguous given instances: both given instance given_Unscoped_FiniteDuration in object Unscoped and given instance given_Unscoped_Int in object Unscoped match type zio.blocks.scope.Unscoped[A] of parameter ev of method scoped in trait ScopeVersionSpecific
// 2 errors found
```

**Expected.** Only the first error.

**Root cause** (from the output). `scoped[A](f: ... => A)(using ev: Unscoped[A])`: when `f`'s body is an error, `A` stays uninstantiated, and implicit search for `Unscoped[A]` finds several candidates (`scala-3/.../ScopeVersionSpecific.scala:55`). In mdoc, with `A` inferred as `Any`, the message instead names `given_Unscoped_BigInt` and `given_Unscoped_FiniteDuration`.

**Impact.** A reader fixing the second error first goes looking for an `Unscoped` problem that doesn't exist. This is a usability trap rather than a wrong result.

**Suggested fix.** Hard to avoid fully, because the compiler drives this. Consider `@implicitNotFound` / `@implicitAmbiguous` messages on `Unscoped` that say "if another error is reported inside the block, fix that first". `@implicitAmbiguous` on the low-priority instances would also make the real escape error (no instance) read better.

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

### config: `Config.wire[A](prefix)` reports keys without the prefix, so the report doesn't say which section failed

Found 2026-10-20 while writing the how-to guide `docs/guides/feed-config-into-a-wire-graph.md` (backlog H-30), on `main` at `b4179489`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `Config.wire[A](prefix)` reads one section of a `ConfigSource`, such as `db` or `catalog`, while a `Resource.from` graph is built. When the section is wrong, the `ConfigLoadException` report names the keys relative to the section: `Missing required key 'url'` instead of `'db.url'`. An application usually has several sections with the same field names (`url`, `port`, `host`), so the report can't tell the operator which section to fix. Decoding the same sections as fields of one case class with `Config.load` reports the full paths.

**Reproduction** (`scala-cli run .`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-config:0.0.56

import zio.blocks.config.*
import zio.blocks.context.Context
import zio.blocks.schema.Schema
import zio.blocks.scope.*

case class DbConfig(url: String, poolSize: Int)
object DbConfig { implicit val schema: Schema[DbConfig] = Schema.derived }

case class CatalogConfig(url: String, pageSize: Int)
object CatalogConfig { implicit val schema: Schema[CatalogConfig] = Schema.derived }

@main def wirePrefix(): Unit = {
  val source = ConfigSource.fromMap(Map("db.poolSize" -> "ten", "catalog.url" -> "https://books.example"), "app.yaml")

  for (wire <- List(Config.wire[DbConfig]("db"), Config.wire[CatalogConfig]("catalog")))
    try Scope.global.scoped(scope => wire.make(scope, Context[ConfigSource](source)): Unit)
    catch { case e: ConfigLoadException => println(e.getMessage) }

  // The same sections decoded as fields of one class report the full path:
  case class AppConfig(db: DbConfig, catalog: CatalogConfig)
  given Schema[AppConfig] = Schema.derived
  println(Config.load[AppConfig](source).left.map(_.head.message))
}
// Configuration loading failed with 1 error(s):
//   - Missing required key 'url' in source 'app.yaml'
// Invalid value 'ten' for key 'poolSize' (expected Int) in source 'app.yaml': For input string: "ten"
//
// Configuration loading failed with 1 error(s):
//   - Missing required key 'pageSize' in source 'app.yaml'
//
// Left(Missing required key 'db.url' in source 'app.yaml'
// Invalid value 'ten' for key 'db.poolSize' (expected Int) in source 'app.yaml': For input string: "ten"
// Missing required key 'catalog.pageSize' in source 'app.yaml')
```

(The `1 error(s)` count is the separate `Composite` bug above.)

**Expected.** The first report names `db.url` and `db.poolSize`, and the second names `catalog.pageSize`, as `Config.load[AppConfig]` does.

**Root cause.** `Config.wire[A](prefix)` re-roots the source with `ConfigSource#prefix` and then decodes it at the empty path: `loadOrThrow[A](ctx.get[ConfigSource].prefix(prefix))` (`config/shared/src/main/scala/zio/blocks/config/Config.scala:40-43`), which calls `decoder.decode(source, "")` (`Config.scala:66-69`). The decoder builds each error's key from the path it was given (`ConfigDecoderDeriver.scala:396`, `:438`), so the prefix lives only inside the wrapped source and never reaches the error. The prefixed source also keeps the original `sourceId` (`ConfigSource.scala:72`), so nothing in the message shows the section.

**Impact.** Every application that uses `Config.wire` with a prefix, which is the pattern for giving each service its own section, gets startup reports that don't name the section. The guide H-30 tells readers that `pageSize` in the report means `catalog.pageSize`.

**Test gap.** The `Config.wire` suite in `ConfigSpec` tests the prefixed wire only on success (`config/shared/src/test/scala/zio/blocks/config/ConfigSpec.scala:89-95`); its failure test uses the unprefixed wire and checks only `report.contains("error(s)")` (`:103-116`).

**Suggested fix.** Decode the original source at the prefix instead of re-rooting it: `ConfigDecoder.derive[A].decode(ctx.get[ConfigSource], prefix)`, then throw `ConfigLoadException` with the formatted report as `loadOrThrow` does (for example through a `private[config]` overload `loadOrThrow[A](source, prefix)`). Run against 0.0.56, `ConfigDecoder.derive[DbConfig].decode(source, "db")` already reports `Missing required key 'db.url'` and `'db.poolSize'`. Add a failure test for the prefixed wire that asserts the full report.

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

**Note (2026-10-21, found while writing H-34).** Hiding the cause is partly deliberate: `ParseError#message` drops it so that a cause quoting a line of the document, which may hold a secret, never reaches a log, and `YamlConfigSourceSpec` pins that ("parse error redaction" suite, `config-yaml/shared/src/test/scala/zio/blocks/config/yaml/YamlConfigSourceSpec.scala:174-215`, including "ParseError with empty path hides cause text even if cause contains secret"). A fix should add the line and column to the message without the cause's text, rather than appending the cause as is.

### Streams: a blocking terminal throws the internal `AsyncBoundaryRequired` signal when `catchAll` sits inside `flatMap` and a stateful operator follows

Found 2026-10-20 while writing the tutorial `docs/guides/your-first-streaming-pipeline.md` (backlog T-06), on `main` at `db589bd0`. The `streams/` and `async/` sources are the same at tag `v0.0.56`.

**Summary.** Recovering per element — `catchAll` (or `||`) on the inner stream of a `flatMap` — works on its own, but as soon as a stateful operator such as `grouped`, `sliding`, `scan` or `mapAccum` follows it, every JVM blocking terminal (`runCollect`, `run`, `runForeach`, …) throws `zio.blocks.streams.Stream$AsyncBoundaryRequired$: asynchronous stream boundary`. That object is a `private[streams]` control-flow signal with no stack trace; users are never supposed to see it. The `*Async` terminals return the right result for the same stream.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-streams` 0.0.56, run with `scala-cli run .`):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-streams:0.0.56
//> using option -experimental

import zio.blocks.async._
import zio.blocks.streams._

@main def repro1(): Unit = {
  final case class Bad(line: String)

  def parse(line: String): Stream[Bad, Int] =
    line.toIntOption match {
      case Some(n) => Stream.succeed(n)
      case None    => Stream.fail(Bad(line))
    }

  val lines = Stream("1", "x", "3")

  // Recover per element: catchAll on the inner stream of flatMap.
  val numbers: Stream[Nothing, Int] = lines.flatMap(line => parse(line).catchAll(_ => Stream.empty))

  println(numbers.runCollect)
  // Right(Chunk(1,3))

  println(numbers.runCollectAsync.block)
  // Right(Chunk(1,3))

  try println(numbers.grouped(2).runCollect)
  catch { case t: Throwable => println(s"threw ${t.getClass.getName}: ${t.getMessage}") }
  // threw zio.blocks.streams.Stream$AsyncBoundaryRequired$: asynchronous stream boundary
}
```

The same exception is thrown with `sliding(2, 2)`, `scan`, `mapAccum`, `grouped(2).run(sink)` and `grouped(2).runForeach(...)`, and with `parse(line) || Stream.empty` in place of `catchAll`. Moving `catchAll` outside the `flatMap` (`lines.flatMap(parse).catchAll(...).grouped(2)`) does not throw.

**Expected.** `numbers.grouped(2).runCollect` returns `Right(Chunk(Chunk(1,3)))`, the same value as `numbers.grouped(2).runCollectAsync.block`.

**Root cause.** Recovery nodes cannot run in the synchronous interpreter, so `CatchAll.compileInterpreter` throws the internal signal (`streams/shared/src/main/scala/zio/blocks/streams/Stream.scala:3755`; `CatchDefect` at `:3782` and `AsyncUnary` at `:5024` do the same). The only handler is `Stream.compileBlockingFallback` (`Stream.scala:3379-3383`), which guards the up-front compile of the whole stream and falls back to the reader-based engine. With a stateful operator downstream, the blocking run uses the `SyncInterpreter`, which compiles each inner stream of `flatMap` lazily, while pulling: `SyncInterpreter.handlePush` calls `innerStreamRef.compileInterpreter(this)` (`streams/shared/src/main/scala/zio/blocks/streams/internal/SyncInterpreter.scala:1394`). A `jdb` trace shows the signal thrown there, rethrown by `handlePush`'s rollback handler (`:1410-1416`) and by the read's failure handler (`:713`), and leaving `Stream.runBlocking` (`Stream.scala:1428`) because no fallback exists at pull time.

**Impact.** Per-element error recovery inside `flatMap` is the usual way to skip bad input, and batching with `grouped` is the usual next step, so ordinary pipelines crash on the JVM blocking terminals with an exception that names an internal type. The docs tutorial works around it by recovering on the outer stream, which stops the stream at the first bad element.

**Test gap.** No spec combines a `flatMap` whose inner stream uses `catchAll`, `||` or another recovery node with a stateful operator downstream and a blocking terminal.

**Suggested fix.** Before compiling a pushed inner stream in `SyncInterpreter.handlePush`, check whether it can run synchronously, and when it cannot, compile it with `stream.compile` and bridge the resulting reader in, as `compileBlockingFallback` does for the whole stream. Alternatively, make the up-front blocking compile detect recovery nodes under `flatMap` and pick the fallback engine from the start. Add a regression spec for each stateful operator listed above.

### Streams: a blocking terminal deadlocks inside an object initializer once `catchAll` is in the pipeline

Found 2026-10-20 while building the companion examples for `docs/guides/your-first-streaming-pipeline.md` (backlog T-06), on `main` at `db589bd0`. The `streams/` and `async/` sources are the same at tag `v0.0.56`.

**Summary.** On the JVM, a blocking terminal such as `runCollect` or `run` on a stream that contains `catchAll` runs the stream on `ForkJoinPool.commonPool` and parks the calling thread until it finishes. When the caller is a class initializer — the body of a Scala 3 `object ... extends App`, or any `val` in an `object` — and a function in the pipeline reads a field of that object, the program hangs forever: the worker waits for the class to finish initializing, and the initializer waits for the worker.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-streams` 0.0.56, run with `scala-cli run .`):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-streams:0.0.56
//> using option -experimental

import zio.blocks.streams._

object Repro2 extends App {
  val header = "header"

  def parse(line: String): Stream[String, Int] =
    line.toIntOption match {
      case Some(n) => Stream.succeed(n)
      case None    => Stream.fail(line)
    }

  val numbers: Stream[Nothing, Int] =
    Stream("header", "1", "2", "x")
      .filter(line => line != header) // reads the object's field `header`
      .flatMap(parse)
      .catchAll(_ => Stream.empty)

  println("running")
  println(numbers.runCollect) // never returns
  println("done")
}
// running
// (the program hangs here; killed by `timeout 120`)
```

The same program prints `Right(Chunk(1,2))` and `done` when the code moves into `def main(args: Array[String]): Unit`, or when the filter compares with the literal `"header"` instead of the field. Without `catchAll` it prints `Left(x)` and `done`. In the tutorial's companion examples, `runAsync(sink).block` hung in the same way.

A thread dump of the tutorial's companion example `Concept8CatchAllExample` (the same shape, hung in `object ... extends App`) shows the two threads:

```text
"main" WAITING (parking)
  at zio.blocks.async.Async$slowPath$.block(Async.scala:2360)
  at zio.blocks.streams.io.AsyncToSyncReader.drive(AsyncToSyncReader.scala:168)
  at zio.blocks.streams.io.AsyncToSyncReader.read(AsyncToSyncReader.scala:277)
  at zio.blocks.streams.Sink$.foldSyncReader(Sink.scala:1210)
  at zio.blocks.streams.Stream.runBlocking(Stream.scala:1428)
  at zio.blocks.streams.StreamPlatformSpecific.run(StreamPlatformSpecific.scala:66)
  at yourfirststreamingpipeline.Concept8CatchAllExample$.<clinit>(Concept8CatchAllExample.scala:75)

"ForkJoinPool.commonPool-worker-1"
  at yourfirststreamingpipeline.Concept8CatchAllExample$.header(Concept8CatchAllExample.scala:35)
  - waiting on the Class initialization monitor for yourfirststreamingpipeline.Concept8CatchAllExample$
  at zio.blocks.streams.internal.SyncInterpreter$.adaptFilter$$anonfun$9(SyncInterpreter.scala:1731)
  at zio.blocks.streams.io.Reader$FilteredRef.read(Reader.scala:3341)
  at zio.blocks.streams.internal.AsyncInterpreter$InterpreterSyncReader.read(AsyncInterpreter.scala:2702)
  ...
  at zio.blocks.async.internal.AsyncRunner$SuspendedRunning.$anonfun$2$$anonfun$1(AsyncRunner.scala:211)
```

**Expected.** A blocking terminal runs the stream on the calling thread, or at least never deadlocks because of where it is called from: the program prints `Right(Chunk(1,2))` and `done`.

**Root cause.** `catchAll` cannot run in the synchronous interpreter, so `Stream.compileBlockingFallback` (`streams/shared/src/main/scala/zio/blocks/streams/Stream.scala:3379-3383`) compiles the stream with the async reader engine. The blocking read then goes through `AsyncToSyncReader.drive` (`streams/jvm/src/main/scala/zio/blocks/streams/io/AsyncToSyncReader.scala:160-168`), which starts the read with `Async.startRegistered` — scheduled on the `commonPool` by `AsyncRunner` (`async/jvm/src/main/scala/zio/blocks/async/internal/AsyncRunner.scala:211`) — and parks the caller with `running.block`. User functions in the pipeline therefore run on a pool thread while the caller holds the JVM class-initialization lock. Scala 3 lambdas are static methods of the enclosing object, so calling them, and reading the object's fields, waits for that lock.

**Impact.** `object Main extends App` is the first thing most Scala users write, and the streams module's own examples use it. A pipeline works until someone adds `catchAll`, then hangs silently with no error. The tutorial's companion examples hit this and had to switch to `def main`; the tutorial page carries a caution about it.

**Test gap.** No spec runs a blocking terminal from inside a class initializer, or checks which thread runs user functions under a blocking terminal.

**Suggested fix.** For blocking terminals, drive the async engine on the calling thread (poll the pollable in a loop on the caller instead of scheduling it on the pool and parking), so user code never moves to another thread. If that is not possible, document on the blocking terminals that the stream may run on `ForkJoinPool.commonPool`, and that calling them from a class initializer can deadlock.

### Streams: `flatMap` followed by a primitive `map` throws `ClassCastException` (or passes `null`) on the blocking interpreter

Found 2026-10-20 while writing the how-to guide `docs/guides/stream-large-query-results.md` (backlog H-20), on `main` at `b4179489`. The `streams/` sources are the same at tag `v0.0.56`.

**Summary.** Take a stream whose `flatMap` produces reference elements (`String`, a row case class, a `Chunk`). Map those elements to a primitive (`Int`, `Long`, `Double`, `Boolean`, `Char`). Then add an operator that compiles through `SyncInterpreter`: `grouped`/`chunked`, `sliding`, `take`, `takeWhile`, `scan` or `mapAccum`. A JVM blocking terminal such as `runCollect` then throws `java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Number`. The exception is thrown out of `runCollect`, not returned as a `Left`. The same pipeline is correct with `runCollectAsync.block`, and it is also correct without the trailing operator. The real case was a SQL row stream: `rows.flatMap(c => Stream.fromChunk(c)).filter(...).map(_.id).grouped(20).map(_.size).runCollect` threw `ClassCastException: class zio.blocks.chunk.Chunk$AnyRefArray cannot be cast to class java.lang.Number`. The mirror case also exists: a `flatMap` typed as `Any` whose inner stream holds primitives, followed by a primitive `map`, throws nothing. Instead it calls the user's `map` function with `null` and returns wrong values.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-streams` 0.0.56, run with `scala-cli run .`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-streams:0.0.56

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams._

@main def flatMapLaneRepro(): Unit = {
  def show(label: String)(run: => Any): Unit = {
    val result =
      try run.toString
      catch { case e: Throwable => s"THROWS ${e.getClass.getName}: ${e.getMessage}" }
    println(s"$label -> $result")
  }

  // Each outer element is a chunk of Strings; flatMap flattens it to a Stream[String].
  val words: Stream[Nothing, String] =
    Stream.fromChunk(Chunk(Chunk("a", "bb"), Chunk("ccc"))).flatMap(c => Stream.fromChunk(c))

  // 1. Failing: flatMap with reference inner elements, then a map to a primitive,
  //    then an operator that compiles through SyncInterpreter.
  show("map(Int).grouped(2)")(words.map(_.length).grouped(2).runCollect)
  show("map(Long).grouped(2)")(words.map(_.length.toLong).grouped(2).runCollect)
  show("filter.map(Int).grouped(2)")(words.filter(_.nonEmpty).map(_.length).grouped(2).runCollect)
  show("map(Int).sliding(2, 1)")(words.map(_.length).sliding(2, 1).runCollect)
  show("map(Int).take(2)")(words.map(_.length).take(2).runCollect)
  show("map(Int).scan(0)(_ + _)")(words.map(_.length).scan(0)(_ + _).runCollect)

  // 2. The same pipeline is correct on the async interpreter.
  show("async: map(Int).grouped(2)")(words.map(_.length).grouped(2).runCollectAsync.block)

  // 3. Controls that work on the blocking path.
  show("map(Int) only")(words.map(_.length).runCollect)
  show("map(String).grouped(2)")(words.map(_ + "!").grouped(2).runCollect)
  show("map inside flatMap, grouped(2)")(
    Stream
      .fromChunk(Chunk(Chunk("a", "bb"), Chunk("ccc")))
      .flatMap(c => Stream.fromChunk(c).map(_.length))
      .grouped(2)
      .runCollect
  )
  show("no flatMap: range.map(Long).grouped(20)")(
    Stream.range(0, 100).map(_.toLong).grouped(20).map(_.size).runCollect
  )

  // 4. Mirror case: the inner stream is primitive but the flatMap is typed as Any.
  //    No exception, but the map callback is handed null instead of the element.
  val erased: Stream[Nothing, Any] =
    Stream.fromChunk(Chunk(1)).flatMap(_ => (Stream.fromChunk(Chunk(5, 6)): Stream[Nothing, Any]))
  show("erased: map(Int) only")(erased.map(x => if (x == null) -1 else 1).runCollect)
  show("erased: map(Int).grouped(2)")(erased.map(x => if (x == null) -1 else 1).grouped(2).runCollect)
}

// Output:
// map(Int).grouped(2) -> THROWS java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Number (java.lang.String and java.lang.Number are in module java.base of loader 'bootstrap')
// map(Long).grouped(2) -> THROWS java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Number (java.lang.String and java.lang.Number are in module java.base of loader 'bootstrap')
// filter.map(Int).grouped(2) -> THROWS java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Number (java.lang.String and java.lang.Number are in module java.base of loader 'bootstrap')
// map(Int).sliding(2, 1) -> THROWS java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Number (java.lang.String and java.lang.Number are in module java.base of loader 'bootstrap')
// map(Int).take(2) -> THROWS java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Number (java.lang.String and java.lang.Number are in module java.base of loader 'bootstrap')
// map(Int).scan(0)(_ + _) -> THROWS java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Number (java.lang.String and java.lang.Number are in module java.base of loader 'bootstrap')
// async: map(Int).grouped(2) -> Right(Chunk(Chunk(1,2),Chunk(3)))
// map(Int) only -> Right(Chunk(1,2,3))
// map(String).grouped(2) -> Right(Chunk(Chunk(a!,bb!),Chunk(ccc!)))
// map inside flatMap, grouped(2) -> Right(Chunk(Chunk(1,2),Chunk(3)))
// no flatMap: range.map(Long).grouped(20) -> Right(Chunk(20,20,20,20,20))
// erased: map(Int) only -> Right(Chunk(1,1))
// erased: map(Int).grouped(2) -> Right(Chunk(Chunk(-1,-1)))
```

Stack trace of the first case (top frames):

```
java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Number
	at zio.blocks.streams.internal.SyncInterpreter$.$init$$$anonfun$17(SyncInterpreter.scala:2125)
	at zio.blocks.streams.internal.SyncInterpreter.readIntStorage(SyncInterpreter.scala:607)
	at zio.blocks.streams.internal.SyncInterpreter.readInt(SyncInterpreter.scala:572)
	at zio.blocks.streams.io.Reader$SyncReader.readIntPhysical(Reader.scala:790)
	at zio.blocks.streams.io.Reader$SyncReader.readN(Reader.scala:365)
	at zio.blocks.streams.internal.SyncInterpreter.readN(SyncInterpreter.scala:856)
	at zio.blocks.streams.Stream$$anon$2.read(Stream.scala:157)
	at zio.blocks.streams.internal.SyncInterpreter.readRefChecked(SyncInterpreter.scala:1606)
	at zio.blocks.streams.internal.SyncInterpreter.read(SyncInterpreter.scala:220)
	at zio.blocks.streams.Sink$$anon$3.drain(Sink.scala:177)
	...
	at zio.blocks.streams.Stream.runBlocking(Stream.scala:1428)
```

**Expected.** Every line should print the same value as the async run and the no-flatMap control: `Right(Chunk(Chunk(1,2),Chunk(3)))` for `grouped(2)`, `Right(Chunk(Chunk(1,2),Chunk(2,3)))` for `sliding(2, 1)`, `Right(Chunk(1,2))` for `take(2)`, and `Right(Chunk(Chunk(1,1)))` for the erased case. In particular `runCollect` must not throw, and a `map` callback must never receive `null` for a non-null element.

**Root cause.** The bug is in how `SyncInterpreter` (`streams/shared/src/main/scala/zio/blocks/streams/internal/SyncInterpreter.scala`) joins a `flatMap`'s inner stream to the rest of the pipeline.

The interpreter keeps each element in one of five typed slots called "lanes": `Int`, `Long`, `Float`, `Double` and reference. Every operator reads from one lane and writes to one lane. A `flatMap` is stored as a PUSH op. The ops written after it (here `map(_.length)`) go into the "outgoing" array (`addAdaptedMap`, lines 1071–1074), and they read from the lane the PUSH produces. When an inner stream starts at run time, `handlePush` (lines 1381–1411) compiles it into the same interpreter. If the inner stream's lane differs from the expected one, `handlePush` adds a "bridge" map to convert between them. The problem is which lane counts as expected:

- Lines 1384–1385 take `outerOL = outputLane` and `outerType = logicalOutputType`. Those are the lane and type at the very end of the pipeline, after all outgoing ops. For `words.map(_.length)` that is the `Int` lane, because the `map` already ran at compile time and overwrote both fields (`addAdaptedMap`, lines 1080–1081).
- `addPush` (lines 1087–1102) never stores the PUSH op's own output type. Unlike `appendRead` (line 1150), it writes nothing to `incomingType`, and there is no outgoing type array. So `handlePush` cannot look up the right target.
- Lines 1401–1406 compare the inner lane (reference, for `Stream.fromChunk(Chunk("a","bb"))`) with `outerOL` (`Int`). The two differ, so the code adds the bridge `logicalBridgeFn(AnyRef, Int)`. That is the reference-to-`Int` function `a.asInstanceOf[java.lang.Number].intValue()` at line 2125 (`Long` and `Double` use the same pattern at 2126–2128).
- At run time the inner `String` reaches the bridge at line 607 (`case 20` in `readIntStorage`) before the user's `map`, and the `Number` cast fails. In the correct design the inner `String` would go untouched to the outgoing `map(_.length)`, which reads the reference lane.

In plain English: the inner stream's elements are converted to the type the whole pipeline ends with, when they should be converted to the type the `flatMap` itself produces. The step after the `flatMap` then gets a value in the wrong form. For a reference value the conversion is a `Number` cast, so it throws. The mirror case fails the other way. The `flatMap` is declared as producing `Any` (reference lane), the inner stream produces `Int`s, and the pipeline ends in `Int`. The lanes look equal (`Int` == `Int`), so no bridge is added. The outgoing `map` (`AnyRef => Int`, tag 20) then reads the reference slot, which was never filled, and gets `null`.

A bridge between two primitive lanes happens to be harmless. It writes the target slot, and the outgoing `map` still reads the untouched source slot. The bridge from a reference to a primitive is the only one that casts, so that is where the bug shows. When `flatMap`s are nested, every PUSH bridges to the final lane. The first one whose inner elements are references fails. That is why the SQL case reported `Chunk$AnyRefArray`: the page stream was itself a `flatMap` result, and its inner `Chunk` pages were the first references to reach a bridge.

Why only some pipelines fail: `FlatMapped.compileBlocking` normally builds a dedicated `Reader.FlatMappedRef` (`Stream.scala:5663–5688`), which does not go through `SyncInterpreter`. So `words.map(_.length).runCollect` works. `grouped` is `chunked` (`Stream.scala:318`), a `StatefulReaderStream`. That type has no `compileBlocking` of its own, so it uses `compileBlockingFallback` → `SyncInterpreter.fromStream` (`Stream.scala:3379–3384`). Its `compileInterpreter` then compiles the upstream through the interpreter too (`Stream.scala:6002–6004` → `compileToReader(source, pipeline)` at 3582–3584 → `FlatMapped.compileInterpreter` at 5689–5691). `take`, `takeWhile`, `scan`, `mapAccum` and `sliding` take the same path. The chunk reader then calls `readN` (`io/Reader.scala:365`), which uses the `Int` lane because the interpreter's `jvmType` is `Int`. That lands in `readIntStorage`. `AsyncInterpreter` gets this right: it stores each PUSH's target type in `incomingType(ip)` / `outgoingType(out)` (`AsyncInterpreter.scala:852`, `846`) and bridges to it (`AsyncInterpreter.scala:1734`, `1857`, `2559–2564`). That is why `runCollectAsync.block` is correct.

**Impact.** Any JVM blocking terminal (`runCollect`, `runFold`, `runForeach`, ...) can hit this. The pipeline shape is common: flatten a stream of batches (`Chunk`s of rows, lists of records), pick a numeric field, then batch, window, limit or scan it. This is the standard shape for paging through large query results. Results:

- When the inner elements are references, the pipeline throws `ClassCastException` out of `runCollect` as a defect, not as a `Left`, so typed error handling never sees it.
- When the `flatMap` is typed as `Any`/erased over primitive inner elements, the user's callback silently gets `null`. That produces wrong results, or a `NullPointerException` inside user code (`erased.map(_.toString.length).grouped(2)` throws an NPE at `_.toString`).

Scala.js uses the same shared `SyncInterpreter`, so its synchronous terminals are presumably affected too (not run). Scala 2.13 could not be checked from published artifacts: `zio-blocks-next-streams_2.13:0.0.56` is not on Maven Central. Because the code is shared, a 2.13 build from source has the same bug. The async interpreter is not affected.

**Test gap.** No test covers a reference-lane inner stream followed by a primitive-lane op after the PUSH:

- `SyncInterpreterSpec.scala:1248–1295` (tests "4. flatMap(Int→Int) then map(Int→Long)", "5. ... map(Int→Double)", "6. ... map(Int→AnyRef)") always use an `Int` inner stream. The bad bridge is then primitive-to-primitive (or a box to reference), which does not throw. The outgoing map reads the untouched source slot, so the wrong bridge is never seen.
- `SyncInterpreterSpec.scala:576–615` covers `Boolean` PUSH output types and an erased `Any` inner stream, but with no lane-changing op after the PUSH.
- The `addMap(LANE_R, OUT_I/L/D)` cases in `SyncInterpreterCoverageSpec.scala:206–221` and `SyncInterpreterSpec.scala:271` have no PUSH.
- No test in `streams/shared/src/test` chains `flatMap` with `grouped`/`chunked`/`sliding` at all (searched within 4 lines of every `flatMap`). No test runs a `flatMap(...).map(primitive)` pipeline through an interpreter-only operator (`grouped`, `take`, `scan`, `takeWhile`, `mapAccum`) and checks the blocking result against `runCollectAsync`.

**Suggested fix.** Make `SyncInterpreter` bridge an inner stream to the PUSH op's own output type, as `AsyncInterpreter` already does:

1. In `addPush` (`SyncInterpreter.scala:1087–1102`), record `outType` for the PUSH op. For the incoming branch use `incomingType(idx) = outType`. For the outgoing branch add an `outgoingType: Array[JvmType]`, allocated and grown next to `outgoingPrim`/`outgoingRef` in `ensureOutgoing`/`growOutgoing` and cleared in `rollbackPush`, the way `AsyncInterpreter` does at lines 846/852.
2. Pass the op index to `handlePush` from the incoming loops too (they pass `-1` today). There are 50 call sites: five read loops (`read`, `readDouble`, `readFloat`, `readIntStorage`, `readLong`) × 5 incoming + 5 outgoing PUSH tags.
3. In `handlePush`, look up `targetType = if (fromOutgoing) outgoingType(idx) else incomingType(idx)`. Add the bridge only when the inner stream's lane differs from `targetType`'s lane, using `logicalBridgeFn(innerType, targetType)`, with the same `Boolean` special case as `AsyncInterpreter.scala:2562–2564`. Keep `outputLane = outerOL`, `logicalOutputType = outerType` and the saved state unchanged: those still control how the final value is emitted and restored by `popAt`.

The change runs once per inner stream, when `handlePush` materializes it: one array read and one lane comparison. It adds nothing per element. It does not touch the hot drain loops, the primitive sentinels or the `readN` loops in `io/Reader.scala`, so it is within the AGENTS.md sentinel performance policy. Still run the `flatMap` JMH benchmarks before and after, since the bridge op count per inner stage can change. Add regression tests:

- interpreter-level: `appendRead(ref reader)` → `addPush(AnyRef, AnyRef)` → `addMap(AnyRef, Int/Long/Double)`, read with `readInt`/`readLong`/`readDouble`/`readN`, and the erased `addPush(Int, AnyRef)` + inner `Int` stream + `addMap(AnyRef, Int)` case;
- stream-level: the reproduction above for `grouped`, `sliding`, `take`, `takeWhile`, `scan` and `mapAccum`, plus a nested `flatMap` of pages, each checked against `runCollectAsync.block`.

### jwt: `Jwt.sign` writes a registered claim twice when `extra` also contains it, and verifiers read the `extra` value

Documented in #1789 (H-37, merged). Not filed on GitHub yet.

Found 2026-10-20 while writing the how-to guide `docs/guides/issue-and-verify-jwts.md` (backlog H-37), on `main` at `ad0821d2`. The jwt sources are the same at tag `v0.0.56`. Verified by running against the published 0.0.56.

**Summary.** `JwtClaims` has typed fields for the registered claims (`iss`, `sub`, `aud`, `exp`, `nbf`, `iat`, `jti`) and a free-form `extra` map. If `extra` also contains one of these names, `Jwt.sign` writes the claim twice into the signed payload. Every JSON parser that keeps the last duplicate, including this module's own decoder, then reads the `extra` value, not the typed field. The issuer believes it signed `iss = "bookstore-auth"`; every verifier sees `iss = "partner-auth"`. RFC 7519 §4 says claim names within a claims set MUST be unique, so the issuer produces a non-conforming token without any error.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-jwt` 0.0.56, run with `scala-cli run`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-jwt:0.0.56
import zio.blocks.jwt._
import java.nio.charset.StandardCharsets.UTF_8

val secret = "sample-bookstore-secret-32-bytes".getBytes(UTF_8)

// The issuer sets iss = "bookstore-auth"; a custom claim map built elsewhere also contains "iss"
val claims = JwtClaims(
  iss = Some("bookstore-auth"),
  sub = Some("reader-1024"),
  extra = Map("iss" -> JwtValue.Str("partner-auth"))
)
val token = Jwt.sign(claims, secret, Algorithm.HS256).fold(e => throw e, identity)

println(Base64Url.decode(token.split('.')(1)).map(new String(_, UTF_8)))
println(Jwt.decode(token, secret, Algorithm.HS256, JwtDecodeOptions(nowSeconds = Some(0L))).map(_.iss))
println(Jwt.decode(token, secret, Algorithm.HS256, JwtDecodeOptions(issuer = Some("bookstore-auth"), nowSeconds = Some(0L))))
// Right({"iss":"bookstore-auth","sub":"reader-1024","iss":"partner-auth"})
// Right(Some(partner-auth))
// Left(zio.blocks.jwt.JwtError$InvalidClaim: Invalid JWT claim 'iss': expected bookstore-auth but found partner-auth)
```

**Expected.** `Jwt.sign` never emits duplicate claim names. Either it rejects the claims (for example `Left(InvalidClaim("iss", "registered claim name must not appear in extra"))`), or the typed field wins and the `extra` entry is dropped. The decoded `iss` should be `Some(bookstore-auth)`.

**Root cause.** `JwtClaims.render` writes the typed fields and then every `extra` entry without filtering registered names (`jwt/shared/src/main/scala/zio/blocks/jwt/JwtClaims.scala:91-93`). The parser's object builder keeps the last value for a repeated key (`fields.update(key, value)`, `jwt/shared/src/main/scala/zio/blocks/jwt/Jwt.scala:622` and `:671`), and `parseExtra` drops registered names from `extra` (`JwtClaims.scala:98-104`), so the duplicate silently replaces the typed field. The decoder's last-wins behaviour is allowed by RFC 7515 §5.2; the bug is on the signing side.

**Impact.** Code that merges custom claims from another source (a user profile map, a config file) into `extra` can override `iss`, `sub`, `aud` or `exp` without noticing. An `exp` in `extra` can extend a token's lifetime past the typed `exp`; a `sub` can change whose token it is. The JWT reference page now tells users not to put registered names in `extra`, but nothing enforces it.

**Test gap.** No test signs claims whose `extra` contains a registered name. `JwtFeatureSpec` only round-trips non-reserved custom claims.

**Suggested fix.** Validate in `Jwt.sign` (or in `JwtClaims.render`) that `extra.keySet` and the registered names are disjoint, and return `Left(JwtError.InvalidClaim(name, ...))` otherwise. Add a test per registered name.

### jwt: `JwtSignOptions.limits` does not apply the JSON-shape limits, and two Scaladocs contradict the code

Documented in #1789 (H-37, merged). Not filed on GitHub yet.

Found 2026-10-20 while writing the how-to guide `docs/guides/issue-and-verify-jwts.md` (backlog H-37). Verified by running against the published 0.0.56.

**Summary.** `Jwt.sign` applies only the size limits of `JwtSignOptions.limits` (`maxJsonChars`, `maxSegmentChars`, `maxTokenChars`). It ignores `maxDepth`, `maxFields` and `maxArrayElements`, so an issuer can sign a token that a verifier configured with the same `JwtLimits` rejects. Separately, the Scaladoc of `JwtDecodeOptions` says an audience mismatch yields `InvalidToken`, but the code returns `InvalidClaim("aud", ...)`.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-jwt` 0.0.56, run with `scala-cli run`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-jwt:0.0.56
import zio.blocks.jwt._
import java.nio.charset.StandardCharsets.UTF_8

val secret = "sample-bookstore-secret-32-bytes".getBytes(UTF_8)
val limits = JwtLimits(maxDepth = 3)

// A custom claim nested five arrays deep
val deep   = (1 to 5).foldLeft(JwtValue.Str("Zionomicon"): JwtValue)((v, _) => JwtValue.Arr(v))
val signed = Jwt.sign(JwtClaims(extra = Map("shelf" -> deep)), secret, Algorithm.HS256, JwtSignOptions(limits = limits))

println(signed.isRight)
println(signed.flatMap(t => Jwt.decode(t, secret, Algorithm.HS256, JwtDecodeOptions(limits = limits, nowSeconds = Some(0L)))))
// true
// Left(zio.blocks.jwt.JwtError$TooDeep: JWT JSON too deep: depth 4 exceeds limit 3)
```

**Expected.** Signing with `JwtSignOptions(limits = limits)` fails with `Left(TooDeep(...))` when the decoder with the same `limits` would, or the Scaladoc says which limits apply on sign.

**Root cause.** The sign path checks `maxJsonChars`, `maxSegmentChars` and `maxTokenChars` only (`jwt/shared/src/main/scala/zio/blocks/jwt/Jwt.scala:245-259`); it renders JSON with `JwtClaims.render` and never runs the depth, field or element checks that the parser applies on decode. The `JwtSignOptions` Scaladoc describes `limits` as "resource limits for header JSON validation" (`Jwt.scala:169`), which matches neither behaviour. The `JwtDecodeOptions` Scaladoc says "mismatch yields `InvalidToken`" (`Jwt.scala:131-132`), while `validateAudience` returns `JwtError.InvalidClaim("aud", ...)` (`Jwt.scala:450-453`, tested in `JwtFeatureSpec.scala:354-372`).

**Impact.** Low. An issuer that shares one `JwtLimits` value between signing and verifying can mint tokens its own verifiers reject, and only finds out at verification time. The wrong `InvalidToken` statement in the Scaladoc was also copied into `docs/reference/jwt.md` (fixed in the H-37 PR).

**Test gap.** No test signs with tightened `JwtSignOptions.limits`. No test sends an `alg: none` header (the code returns `UnsupportedAlgorithm("none")`, or `InvalidToken` when the signature segment is empty, but nothing asserts it).

**Suggested fix.** Either apply all six limits on sign (parse the rendered JSON with the same limits before encoding), or document that only the size limits apply on sign. Change the `JwtDecodeOptions` Scaladoc to "mismatch yields `InvalidClaim("aud", ...)`". Add tests for both, plus an `alg: none` test in `JwtEdgeCasesSpec`.

### config: Rollout buckets depend on the key alone, so every percentage rollout picks the same users

Documented in #1788 (H-32, merged). Not filed on GitHub yet.

Found 2026-10-20 while writing the how-to guide `docs/guides/roll-out-feature-gradually.md` (backlog H-32), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`.

**Summary.** `DynamicFlag` buckets a call with `Rollout.bucketFor(key)`, which hashes only the key. The flag's name is not part of the hash. Two features that are both rolled out to 10% of users, keyed by the same user id, reach exactly the same users. A user in a low bucket gets every early rollout at once, and a bug report from that group cannot be attributed to one feature. Rollout tools hash the flag key together with the user key so that rollouts are independent.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config` 0.0.56, run with scala-cli):

```scala
import zio.blocks.config._

object RecommendationsPanel extends DynamicFlag[Boolean](false, "true@*/10%; false")
object WishlistSharing      extends DynamicFlag[Boolean](false, "true@*/10%; false")

@main def repro(): Unit = {
  val readers  = (1 to 1000).map(i => s"reader-$i")
  val panel    = readers.filter(RecommendationsPanel.evaluate(_))
  val wishlist = readers.filter(WishlistSharing.evaluate(_))
  println(s"${panel.size} ${wishlist.size} ${panel.intersect(wishlist).size}")
  // 97 97 97
}
```

**Expected.** About 10% of each rollout's users are also in the other one (roughly 10 of 97), because the two decisions are independent.

**Root cause.** `Rollout.bucketFor` is `math.abs(MurmurHash3.stringHash(key) % 100)` (`config/shared/src/main/scala/zio/blocks/config/Rollout.scala:93-94`), and `DynamicFlag#evaluateInternal` passes the raw key (`DynamicFlag.scala:146`).

**Impact.** Rollouts are correlated across all flags in a process. Users must salt the key by hand (`flag(s"recommendations:$id")`, which the guide shows), and must use the same salt at every call site of the flag.

**Test gap.** `RolloutSpec` checks that `bucketFor` is stable and spread out, but nothing checks that two flags bucket the same key independently.

**Suggested fix.** Bucket on `name + ":" + key` inside `DynamicFlag` (keep `Rollout.bucketFor(key)` as is for direct use), or add an optional salt parameter that defaults to the flag name. This moves existing users between buckets, so mention it in the release notes.

### config: `Rollout.validate` assumes percentages are slices, but evaluation treats them as thresholds

Documented in #1788 (H-32, merged). Not filed on GitHub yet.

Found 2026-10-20 while writing the how-to guide `docs/guides/roll-out-feature-gradually.md` (backlog H-32), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`.

**Summary.** Each targeted choice matches when `bucket < percentage`, and the first match wins, so percentages on one pattern are upper bounds on the same 0–99 bucket, not shares that add up. `Rollout.validate` instead sums the percentages per pattern and warns when the sum exceeds 100%. Under the real semantics this warning fires on a correct split and stays silent on a choice that can never match.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config` 0.0.56, run with scala-cli):

```scala
import zio.blocks.config._

def share(expr: String, value: String): Int = {
  val choices = Rollout.parseChoices(expr).toOption.get
  (0 until 100).count(b => Rollout.evaluateIndex(choices, "k", b).contains(value))
}

@main def repro(): Unit = {
  val split = "a@*/40%; b@*/80%; c"
  println(s"$split -> ${Rollout.validate(split)}  a=${share(split, "a")} b=${share(split, "b")} c=${share(split, "c")}")
  // a@*/40%; b@*/80%; c -> Right(List(Cumulative percentage for pattern '*' is 120% (exceeds 100%)))  a=40 b=40 c=20

  val shadow = "a@*/30%; b@*/20%; c"
  println(s"$shadow -> ${Rollout.validate(shadow)}  a=${share(shadow, "a")} b=${share(shadow, "b")} c=${share(shadow, "c")}")
  // a@*/30%; b@*/20%; c -> Right(List())  a=30 b=0 c=70
}
```

**Expected.** No warning for `a@*/40%; b@*/80%; c` (a valid 40/40/20 split). A warning for `a@*/30%; b@*/20%; c`, whose `b` can never match because every bucket below 20 already matched `a`.

**Root cause.** `Rollout.validate` folds the percentages of all targeted choices with the same pattern into a sum (`config/shared/src/main/scala/zio/blocks/config/Rollout.scala:104-110`), while `Rollout.matches` compares each choice independently with `bucket < pct.get` (`Rollout.scala:187`).

**Impact.** Operators who follow the warning rewrite correct splits into wrong ones, and real dead rules pass validation. `docs/reference/config/rollout.md` repeats the slice reading ("a percentage behaves as the literal share of keys it names"; "a pattern whose percentages sum above 100 ... usually means someone added a rule instead of adjusting one"), which holds only for the first choice on a pattern.

**Test gap.** `RolloutSpec` asserts the sum-based warning for `a@path/60%; b@path/60%` (`config/shared/src/test/scala/zio/blocks/config/RolloutSpec.scala:211`) but never compares a warning with what `evaluateIndex` actually returns.

**Suggested fix.** Pick one meaning. Either keep thresholds and make `validate` warn when a choice's percentage is less than or equal to an earlier choice's on the same pattern (it is shadowed), dropping the sum check; or switch evaluation to slices (each choice takes the next `n` buckets) so the sum check is correct. Update `rollout.md` to match.

### config: `Flag.dump` prints `StaticFlag[String]` values even when the flag's name marks it as sensitive

Documented in #1788 (H-32, merged). Not filed on GitHub yet.

Found 2026-10-20 while writing the how-to guide `docs/guides/roll-out-feature-gradually.md` (backlog H-32), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`.

**Summary.** `Flag.dump()` masks a value only when the flag's type is `Secret`. A `StaticFlag[String]` named `PaymentToken`, `DbPassword` or `ApiKey` prints its value in clear, although the module already has a name-based check (`Sensitive.isSensitive`) and uses it to hide the same value in `FlagValueParseException` messages and in the config provenance dump.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config` 0.0.56, run with scala-cli; the flag is a top-level object, so its name is `PaymentToken`):

```scala
import zio.blocks.config._

object PaymentToken extends StaticFlag[String]("none")
object FreeShipping extends StaticFlag[Int](35)

@main def repro(): Unit = {
  FlagSource.Registry.register(FlagSource.fromMap(Map("PaymentToken" -> "tok-live-123"), "ops"))
  PaymentToken()
  FreeShipping()
  println(Flag.dump())
  // ┌──────────────┬──────────────────────┬──────────────┬────────────────┐
  // │ Name         │ Type                 │ Value        │ Source         │
  // ├──────────────┼──────────────────────┼──────────────┼────────────────┤
  // │ FreeShipping │ Default              │ 35           │ schema-default │
  // │ PaymentToken │ FlagSourceValue(ops) │ tok-live-123 │ ops            │
  // └──────────────┴──────────────────────┴──────────────┴────────────────┘
}
```

**Expected.** `PaymentToken` shows `<secret>`, as a parse failure for the same flag would (`FlagException.FlagValueParseException` prints `'<secret>'` for sensitive names).

**Root cause.** `Flag.dump` takes `sf.displayValue` as is (`config/shared/src/main/scala/zio/blocks/config/FlagReader.scala:182`); only `FlagException.FlagValueParseException#getMessage` consults `Sensitive.isSensitive(flagName)` (`FlagException.scala:67`).

**Impact.** Startup reports, which are usually logged, can leak credentials kept in string flags.

**Related (minor).** In the same table, a static flag that fell back to its default shows the source `schema-default` (`Provenance.Default.sourceId`, `Provenance.scala:43-45`), although no schema is involved; `default` would be clearer.

**Test gap.** `FlagDumpSpec` checks masking only for a `StaticFlag[Secret]`.

**Suggested fix.** In `Flag.dump`, render `<secret>` when `Sensitive.isSensitive(name)` holds, whatever the type, and apply the same rule to a dynamic flag's expression.

### config: `DynamicFlag` accepts expressions whose values its reader cannot parse, and a selector without `%` silently becomes a path

Documented in #1788 (H-32, merged). Not filed on GitHub yet.

Found 2026-10-20 while writing the how-to guide `docs/guides/roll-out-feature-gradually.md` (backlog H-32), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`.

**Summary.** `DynamicFlag#update`, `reload` and the initial expression check only the rollout grammar. Two typos therefore pass with `Right(())` and switch the feature off for everyone. First, `maybe` for a `DynamicFlag[Boolean]` is accepted; every call then falls back to the default, and only `parseErrorCount` shows it. Second, `true@*/50` (missing `%`) parses as the two-segment path `*` / `50`, which never matches a one-segment key.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config` 0.0.56, run with scala-cli):

```scala
import zio.blocks.config._

object RecommendationsPanel extends DynamicFlag[Boolean](false, "true@*/10%; false")

@main def repro(): Unit = {
  val readers = (1 to 1000).map(i => s"reader-$i")
  println(RecommendationsPanel.update("maybe"))
  // Right(())
  println(s"${RecommendationsPanel("reader-1")} parseErrors=${RecommendationsPanel.parseErrorCount}")
  // false parseErrors=1
  println(RecommendationsPanel.update("true@*/50"))
  // Right(())
  println(s"${readers.count(RecommendationsPanel.evaluate(_))} of 1000")
  // 0 of 1000
}
```

**Expected.** `update("maybe")` returns `Left(ConfigError.InvalidValue(..., "Boolean ..."))`, because the flag already holds the `Flag.Reader` that will reject the value. `true@*/50` is at least reported by `Rollout.validate` as a likely missing `%`.

**Root cause.** `update` and `reload` call `Rollout.parseChoices` only (`config/shared/src/main/scala/zio/blocks/config/DynamicFlag.scala:82-93`, `:96-113`); values are parsed lazily per call in `evaluateInternal`, which swallows the error into `parseErrorCount`. `Rollout.parseSelector` treats a selector that does not end in `%` as a plain path (`Rollout.scala:168-169`).

**Impact.** A mistyped push to a flag service is accepted, recorded in `updateHistory` as a successful change, and turns the feature off without an error.

**Test gap.** `DynamicFlagSpec` checks `parseErrorCount` after an unparsable value but does not expect `update` to reject it.

**Suggested fix.** In `update`, `reload` and initialization, parse every choice's value with the flag's reader and return the first failure as `Left` (or `Flag.ReloadResult.Failed`). In `Rollout.validate`, warn when a selector's last segment is all digits.

**Related (documentation).** The `DynamicFlag` Scaladoc example `DynamicFlag[Boolean](false, "true@beta/50%; false")` (`DynamicFlag.scala:32`) matches only the key `beta`, because the key is the first path segment; `true@*/50%; false` is what the example means.

### schema: error paths leak the internal `.wrapped` segment for transformed fields

Found 2026-10-20 while writing H-05 and M-01. Verified on 0.0.56.

**Summary.** When a field's schema is a wrapper (built with `Schema#transform`, or the `Schema[Json]` returned by `Schema.fromJsonSchema`), decode errors end with an internal path segment: `... at: .orderedOn.wrapped`, or ` at: .wrapped` for `fromJsonSchema`. Users see a field name that does not exist in their data. The message is also the raw exception text from the `transform` function (`Text '2025-03-14' could not be parsed at index 2`).

**Reproduction.** See the `Schema[LocalDate]` usability trap below (last line of output), and the `fromJsonSchema` entry above.

**Fix.** Drop the wrapper segment when rendering the error path for wrapper schemas.

**Also seen in H-04** (2026-10-21, `docs/guides/validated-newtypes.md`). `DynamicOptic#toString` renders the same step as `~`, so `SchemaError.Single#source.toString` gives `.isbn.~` while `#message` (which uses `toScalaString`) gives `.isbn.wrapped`; `SchemaError.Message#message` uses `toString` (`SchemaError.scala:194`), the other cases `toScalaString`, so one error can show either spelling. The guide builds field names from the `DynamicOptic.Node.Field` steps to avoid both.

### schema: `Schema.derived` crashes the compiler for a sealed trait nested in a class when its case classes sit in its companion

Found 2026-10-21 while writing the how-to guide `docs/guides/derive-your-own-type-class.md` (backlog H-08), on `main` at `b4179489`. The code is the same at tag `v0.0.56`.

**Summary.** When a sealed trait is declared inside a class (or a `def`), and its case classes are declared inside the trait's companion object, `Schema.derived` crashes the compiler, both directly (`Schema.derived[Format]`) and when it derives a record with a field of that type. Scala 3 fails with an `AssertionError` in the Erasure phase; Scala 2.13 reports an internal error. Case objects alone work, and so does a sealed trait whose cases are direct members of the class. mdoc compiles every docs block as a member of a class, so any docs page with the common model below hits it; mdoc shows only `skipping file, the compiler produced no classfiles and reported no errors`.

**Reproduction** (run with `scala-cli run Repro.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
import zio.blocks.schema._

class Bookstore {
  sealed trait Format
  object Format {
    case object Hardcover                  extends Format
    final case class Ebook(sizeMb: Double) extends Format
  }

  final case class Book(title: String, format: Format)
  object Book {
    implicit val schema: Schema[Book] = Schema.derived[Book]
  }
}

object Main {
  def main(args: Array[String]): Unit =
    println(new Bookstore().Book.schema.reflect.typeId.name)
}
// Exception in thread "main" java.lang.AssertionError: assertion failed: missing outer accessor in anonymous class zio.blocks.schema.binding.Constructor {...}
// Compilation failed
```

The same file on Scala 2.13 (`//> using scala 2.13.18`, no `-experimental`, `dev.zio::zio-blocks-schema:0.0.56`):

```
Repro.scala:14: error: Internal error: unable to find the outer accessor symbol of class Bookstore
    implicit val schema: Schema[Book] = Schema.derived[Book]
                                                      ^
```

Moving the derivation into the companion works around it. With `implicit val schema: Schema[Format] = Schema.derived[Format]` added inside `object Format`, the program prints `Book`. The same crash also happens with a Scala 3 `enum Format { case Ebook(sizeMb: Double); case Print }` declared in a class, and with a sealed trait plus companion declared inside a `def`; the latter gives the message from the closed issue #511 (`missing outer accessor in anonymous class Object with zio.blocks.schema.binding.Discriminator`).

**Expected.** Both programs compile and print `Book`, as they do when `Bookstore` is an `object`.

**Root cause.** `CommonMacroOps.directSubTypes` builds each case's type from its symbol, with the owner's `this` as prefix: `symbol.typeRef` in Scala 3 (`schema/shared/src/main/scala-3/zio/blocks/schema/CommonMacroOps.scala:228`; the generic branch at `:231-232` reuses that prefix) and `classSymbol.toType` in Scala 2 (`schema/shared/src/main/scala-2/zio/blocks/schema/CommonMacroOps.scala:79`). For `Ebook` that is `Format$.this.Ebook` (Scala 3) or `Format.this.Ebook` (Scala 2), a path that only exists inside `object Format`. The macro then generates `new Format$.this.Ebook(...)` in an anonymous `Constructor` (`ClassInfo.constructor`, `schema/shared/src/main/scala-3/zio/blocks/schema/SchemaCompanionVersionSpecific.scala:584`; `q"new $tpe(...$argss)"`, `schema/shared/src/main/scala-2/zio/blocks/schema/SchemaCompanionVersionSpecific.scala:466`) and a type test in the discriminator (Scala 3 `:1383`, Scala 2 `:921`). Called from outside `object Format`, the compiler finds no outer accessor that leads to the nested `Format` instance. Records work because their type comes from user-written source, which has a proper path; case objects work because their branch uses `Ref(symbol).tpe`; and top-level objects work because their `this` is static.

**Impact.** Models declared inside a class, a trait or a method (test classes, `def`-local models, and every mdoc page) cannot derive a schema for an ADT whose cases follow the usual companion-object layout, and the Scala 3 failure is a compiler crash with no source position. `directSubTypes` is shared by `Binding.of`, `Into`/`As` and the derived optics, which are probably affected too (not tested).

**Test gap.** Tests declare ADTs in top-level spec objects, or derive them inside the companion with `derives Schema` (for example `schema/shared/src/test/scala-3/zio/blocks/schema/SchemaVersionSpecificSpec.scala:282`), which is the shape that works. No test derives a class-nested or `def`-local sealed trait with companion-held cases from outside the companion, and #511 was closed without a regression test.

**Suggested fix.** In `directSubTypes`, build each case's type from the parent's prefix instead of the owner's `this`: walk from the case's owner up to the parent's owner, and select each module on the way (Scala 3: `prefix.select(owner.companionModule)` and then `.select(symbol)`; Scala 2: `c.internal.singleType(prefix, owner.asClass.module)` and then `c.internal.typeRef(prefix, symbol, Nil)`). For the repro this yields `Bookstore.this.Format.Ebook`. Apply it to the generic branch too, and add tests for both Scala versions: a class-nested sealed trait with companion-held cases derived from the class body and from another companion, a `def`-local variant, and a Scala 3 enum with class cases inside a class.

### schema: on Scala 2, `DerivedOptics#optics` compiles only inside the `zio.blocks.schema` package

Found 2026-10-21 while making the schema Scaladoc examples compile (`schema-scaladoc-check`, branch `schema-scaladoc`, on top of `1945f601`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run Repro.scala` against the published `0.0.56`.

**Summary.** On Scala 2, every call to `optics` on a companion that extends `DerivedOptics` (or `DerivedOptics.Of`) fails to compile in user code: the macro expansion calls the package-private `DerivedOpticsMacros.getOrCreate`. Scala 3 is not affected.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

case class Book(title: String, pages: Int)
object Book extends DerivedOptics {
  implicit val schema: Schema[Book] = Schema.derived
}

object Main {
  def main(args: Array[String]): Unit = {
    val title: Lens[Book, String] = Book.optics.title
    println(title.get(Book("Zionomicon", 870)))
  }
}
// [error] ./Repro.scala:12:42
// [error] object DerivedOpticsMacros in package schema cannot be accessed as a member of package zio.blocks.schema from object Main
// [error]     val title: Lens[Book, String] = Book.optics.title
// [error]                                          ^
// Compilation failed
```

The same file on Scala 3.3.7 prints `Zionomicon`.

**Expected.** The program compiles and prints `Zionomicon`, as the Scala 2 Scaladoc of `DerivedOptics` shows.

**Root cause.** `DerivedOpticsMacros` and its `getOrCreate` are `private[schema]` (`schema/shared/src/main/scala-2/zio/blocks/schema/DerivedOptics.scala:213`, `:224`), but the trees the macro returns call `_root_.zio.blocks.schema.DerivedOpticsMacros.getOrCreate(...)` (`:324`, `:359`, `:382`). The wrapper branch also calls the `private[schema]` `DerivedOptics.wrapperAsRecord` (`:107`, used at `:313` and `:387`). Scala 2 macros expand at the call site, where these members are not accessible.

**Impact.** `DerivedOptics` is unusable on Scala 2 outside the library. The two Scala 2 Scaladoc examples on `DerivedOptics` are marked `// no-check` until this is fixed.

**Test gap.** `DerivedOpticsSpec` (`schema/shared/src/test/scala/zio/blocks/schema/DerivedOpticsSpec.scala`) is in package `zio.blocks.schema`, so the private members are visible to it.

**Suggested fix.** Make `DerivedOpticsMacros.getOrCreate` and `DerivedOptics.wrapperAsRecord` public (they can stay out of the user-facing docs), or move the runtime helpers to a public object that the expansion calls. Add a Scala 2 test in a package outside `zio.blocks.schema`, or rely on the `schema-scaladoc-check` examples once the `// no-check` markers are removed.

### schema: on Scala 2, `NonNegativeInt.literal` and `PositiveNumber.literal` compile only inside `zio.blocks.schema.json`

Found 2026-10-21 while making the schema Scaladoc examples compile (`schema-scaladoc-check`, branch `schema-scaladoc`, on top of `1945f601`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run Repro.scala` against the published `0.0.56`.

**Summary.** On Scala 2, the compile-time `literal` constructors expand to `new NonNegativeInt(...)` and `new PositiveNumber(...)`, whose constructors are `private[json]`. Any call outside the `zio.blocks.schema.json` package fails to compile, even for a valid literal. Scala 3 is not affected.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema.json.{NonNegativeInt, PositiveNumber}

object Main {
  def main(args: Array[String]): Unit = {
    val minLength: NonNegativeInt  = NonNegativeInt.literal(3)
    val multipleOf: PositiveNumber = PositiveNumber.literal(5)
    println((minLength, multipleOf))
  }
}
// [error] ./Repro.scala:7:60
// [error] constructor NonNegativeInt in class NonNegativeInt cannot be accessed in object Main from object Main
// [error]     val minLength: NonNegativeInt  = NonNegativeInt.literal(3)
// [error]                                                            ^
// [error] ./Repro.scala:8:60
// [error] constructor PositiveNumber in class PositiveNumber cannot be accessed in object Main from object Main
// [error]     val multipleOf: PositiveNumber = PositiveNumber.literal(5)
// [error]                                                            ^
// Compilation failed
```

**Expected.** The program compiles and prints the two values.

**Root cause.** The Scala 2 macros emit `q"new _root_.zio.blocks.schema.json.NonNegativeInt($value)"` (`schema/shared/src/main/scala-2/zio/blocks/schema/json/NonNegativeIntCompanionVersionSpecific.scala:47`) and `q"new _root_.zio.blocks.schema.json.PositiveNumber(BigDecimal($value))"` (`.../PositiveNumberCompanionVersionSpecific.scala:47`), but both constructors are `private[json]` (`schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala:33`, `:54`). The Scala 3 versions call the public `unsafe` instead (`schema/shared/src/main/scala-3/zio/blocks/schema/json/NonNegativeIntCompanionVersionSpecific.scala:36`, `.../PositiveNumberCompanionVersionSpecific.scala:36`).

**Impact.** `literal` is unusable on Scala 2. The Scala 2 Scaladoc examples of both methods are marked `// no-check` until this is fixed.

**Test gap.** No test calls `literal` on either Scala version.

**Suggested fix.** Emit `_root_.zio.blocks.schema.json.NonNegativeInt.unsafe($value)` and `_root_.zio.blocks.schema.json.PositiveNumber.unsafe(BigDecimal($value))`, as the Scala 3 versions do, and add tests for both versions in a package outside `zio.blocks.schema.json`, including a `typeCheck` test for the rejected literal.

### schema: on Scala 2, `Schema#structural` returns `Schema[ts.StructuralType]`, not the structural type

Found 2026-10-21 while making the schema Scaladoc examples compile (`schema-scaladoc-check`, branch `schema-scaladoc`, on top of `1945f601`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run Repro.scala` against the published `0.0.56`.

**Summary.** On Scala 2, the `ToStructural` macro is blackbox, so the compiler never sees the refined `StructuralType` of the instance it returns. `Schema#structural` therefore has the type `Schema[ts.StructuralType]`, which cannot be assigned to a `Schema` of the structural type, as the Scaladoc of `Schema#structural` and `ToStructural` showed. On Scala 3 the same code compiles.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

case class Book(title: String, pages: Int)

object Main {
  def main(args: Array[String]): Unit = {
    val structuralSchema: Schema[{ def title: String; def pages: Int }] =
      Schema.derived[Book].structural
    println(structuralSchema)
  }
}
// [error] ./Repro.scala:10:7
// [error] type mismatch;
// [error]  found   : zio.blocks.schema.Schema[ts.StructuralType] where val ts: zio.blocks.schema.ToStructural[Book]
// [error]  required: zio.blocks.schema.Schema[AnyRef{def title: String; def pages: Int}]
// [error]       Schema.derived[Book].structural
// [error]       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// Compilation failed
```

The same file on Scala 3.3.7 prints the record schema `record {pages:Int,title:String}`.

**Expected.** The program compiles, as it does on Scala 3.

**Root cause.** `ToStructural.toStructural` is a blackbox macro (`schema/shared/src/main/scala-2/zio/blocks/schema/ToStructuralVersionSpecific.scala:24`, `:27`, `:31`). A blackbox macro's result keeps the declared type `ToStructural[A]`, so the `type StructuralType = ...` refinement that the expansion defines is lost, and `structural`'s dependent result type `Schema[ts.StructuralType]` (`schema/shared/src/main/scala-2/zio/blocks/schema/SchemaVersionSpecific.scala:48`) stays abstract.

**Impact.** On Scala 2 a structural schema can be created but not used with its structural type. The Scala 2 Scaladoc of `Schema#structural` now shows the untyped form, and the `ToStructural` example is marked `// scala-3-only`.

**Test gap.** The Scala 2 structural tests (`schema/jvm/src/test/scala-2/zio/blocks/schema/structural/`) call `schema.structural` without a type ascription and only check the result at run time.

**Suggested fix.** Make the macro whitebox (`whitebox.Context`), so the implicit's inferred type keeps the `StructuralType` refinement (the usual `Aux` pattern), and add a Scala 2 test that assigns the result to `Schema[{ def name: String; def age: Int }]`.

### schema: `DerivedOptics` gives no usable lens for an opaque type

Found 2026-10-21 while making the schema Scaladoc examples compile (`schema-scaladoc-check`, branch `schema-scaladoc`, on top of `1945f601`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run Repro.scala` against the published `0.0.56`, on Scala 3.9.0 and 3.3.7.

**Summary.** The Scala 3 `DerivedOptics` Scaladoc showed an opaque type with a wrapper schema getting a `value` lens. For an opaque type `PageCount = Int` declared in its own object, the lens has the wrong type: the direct form `PageCount.value` is a `Lens[PageCount, PageCount]` on both Scala versions, and `PageCount.optics.value` is a `Lens[PageCount, Int]` on 3.9.0 but a `Lens[Int, Int]` on 3.3.7. `object PageCount extends DerivedOptics` (without `.Of`) does not compile at all.

**Reproduction** (Scala 3.9.0, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

object Pages {
  opaque type PageCount = Int
  object PageCount extends DerivedOptics.Of[PageCount] {
    def apply(i: Int): PageCount = i
    extension (p: PageCount) def toInt: Int = p

    given schema: Schema[PageCount] = Schema[Int].transform(PageCount.apply, _.toInt)
  }
}
import Pages.PageCount

object Main {
  def main(args: Array[String]): Unit = {
    val viaOptics: Lens[PageCount, Int] = PageCount.optics.value
    println(viaOptics.get(PageCount(870)))

    val direct: Lens[PageCount, Int] = PageCount.value
    println(direct.get(PageCount(870)))
  }
}
// [error] ./Repro.scala:21:40
// [error] Found:    (zio.blocks.schema.OpticsHolder{
// [error]   val value: zio.blocks.schema.Lens[Pages.PageCount, Pages.PageCount]}#value :
// [error]   zio.blocks.schema.Lens[Pages.PageCount, Pages.PageCount])
// [error] Required: zio.blocks.schema.Lens[Pages.PageCount, Int]
// [error]     val direct: Lens[PageCount, Int] = PageCount.value
// [error]                                        ^^^^^^^^^^^^^^^
// Compilation failed
```

On 3.9.0, without the two `direct` lines, the program prints `870`. The same file with `//> using scala 3.3.7` fails on both lines:

```scala
// [error] ./Repro.scala:18:43
// [error] Found:    (zio.blocks.schema.OpticsHolder{val value: zio.blocks.schema.Lens[Int, Int]}#
// [error]   value : zio.blocks.schema.Lens[Int, Int])
// [error] Required: zio.blocks.schema.Lens[Pages.PageCount, Int]
// [error]     val viaOptics: Lens[PageCount, Int] = PageCount.optics.value
// [error]                                           ^^^^^^^^^^^^^^^^^^^^^^
// [error] ./Repro.scala:21:40
// [error] Found:    (zio.blocks.schema.OpticsHolder{
// [error]   val value: zio.blocks.schema.Lens[Pages.PageCount, Pages.PageCount]}#value :
// [error]   zio.blocks.schema.Lens[Pages.PageCount, Pages.PageCount])
// [error] Required: zio.blocks.schema.Lens[Pages.PageCount, Int]
// [error]     val direct: Lens[PageCount, Int] = PageCount.value
// [error]                                        ^^^^^^^^^^^^^^^
// Compilation failed
```

With plain `DerivedOptics` instead of `DerivedOptics.Of` (Scala 3.9.0, `zio-blocks-schema` 0.0.56), `optics` does not compile:

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

object Pages {
  opaque type PageCount = Int
  object PageCount extends DerivedOptics {
    def apply(i: Int): PageCount = i
    extension (p: PageCount) def toInt: Int = p

    given schema: Schema[PageCount] = Schema[Int].transform(PageCount.apply, _.toInt)
  }
}
import Pages.PageCount

object Main {
  def main(args: Array[String]): Unit = {
    val viaOptics = PageCount.optics.value
    println(viaOptics)
  }
}
// [error] ./Repro.scala:18:21
// [error] Cannot find companion class for $proxy1.PageCount
// [error]     val viaOptics = PageCount.optics.value
// [error]                     ^^^^^^^^^^^^^^^^
// Compilation failed
```

**Expected.** Both forms give a `Lens[PageCount, Int]` on every Scala 3 version.

**Root cause** (from source, consistent with the output). `DerivedOpticsMacros.opticsImpl` takes the lens's source type from `S` and the value type from `tpe.dealias` (`schema/shared/src/main/scala-3/zio/blocks/schema/DerivedOptics.scala:367`), and `buildWrapperOptics` builds `Lens[S, u]` from them (`:379-380`, `:393-396`). Through the conversion, `OpticsFor.derived[S]` (`:296`) is inferred at the call site outside `Pages`, where `PageCount` is opaque, so `dealias` returns `PageCount` itself. Through `optics`, `S` is the type argument of the `DerivedOptics.Of[PageCount]` parent, written inside `object Pages` where the alias is visible; 3.9.0 keeps `PageCount` as `S` and dealiases the value type to `Int`, while 3.3.7 dealiases both. Plain `DerivedOptics` looks up the companion class of the type (`:132`, `:341-344`), and an opaque type has none, so it fails with `Cannot find companion class`.

**Impact.** Optics for opaque types cannot be derived portably. The `DerivedOptics` Scaladoc example now uses a value class (`case class Age(value: Int) extends AnyVal` with a `transform` schema), which works on all versions, and says that opaque types are not supported yet.

**Test gap.** No test derives optics for an opaque type.

**Suggested fix.** In `opticsImpl`, when `tpe.typeSymbol` is an opaque type alias, keep `S` as the opaque type and take the underlying type from `tpe.translucentSuperType`, instead of relying on `tpe.dealias`. Support plain `DerivedOptics` on an opaque type's companion by falling back to the companion module's type when there is no companion class. Add tests for both access forms on an opaque type declared in a separate object, on 3.3.x and 3.9.x.

### schema: on Scala 3, a `DerivedOptics` lens for a value class with a wrapper schema works only when its field is named `value`

Found 2026-10-21 in review of the `DerivedOptics` Scaladoc fix (`schema-scaladoc-check`, branch `schema-scaladoc`, on top of `79458a88`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run Repro.scala` against the published `0.0.56`, on Scala 3.9.0 and 3.3.7.

**Summary.** For a value class whose schema is a wrapper (built with `transform`), `DerivedOptics` gives the companion a lens named after the case field, so `Isbn.code` and `Isbn.optics.code` type-check. At run time the wrapper schema's only field is named `value`, so reading the lens throws `No optic found for: code`. The lens works only when the field happens to be named `value`.

**Reproduction** (Scala 3.9.0, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

case class Isbn(code: String) extends AnyVal
object Isbn extends DerivedOptics {
  given schema: Schema[Isbn] = Schema[String].transform(Isbn(_), _.code)
}

object Main {
  def main(args: Array[String]): Unit = {
    val zionomicon = Isbn("978-0-9000000-0-1")
    println(scala.util.Try(Isbn.code.get(zionomicon)))
    println(scala.util.Try(Isbn.optics.code.get(zionomicon)))
  }
}
// Failure(java.lang.RuntimeException: No optic found for: code)
// Failure(java.lang.RuntimeException: No optic found for: code)
```

The same file with `//> using scala 3.3.7` prints the same two lines. (scala-cli also reports one deprecation warning on 3.9.0, the `OpticsFor` one, since fixed in #1743 (see Resolved), and one feature warning on both.)

**Expected.** Both lines print `Success(978-0-9000000-0-1)`: the lens that type-checks as `code` reads the field.

**Root cause.** A value class is a case class, so `opticsImpl` takes the case-class path (`schema/shared/src/main/scala-3/zio/blocks/schema/DerivedOptics.scala:374`, `:377`). That path names each accessor of the refined type after the case field (`:437-438`). At run time it falls back to `DerivedOptics.wrapperAsRecord` for a wrapper schema (`:451`), which always builds a single term named `"value"` (`:171`, `:175`), and registers each lens under its term name (`:458`). `OpticsHolder.selectDynamic("code")` then finds nothing (`:282-283`). The Scala 2 macro looks the lens up by index instead (`schema/shared/src/main/scala-2/zio/blocks/schema/DerivedOptics.scala:316`), so it would not have this problem (from source; Scala 2 `optics` does not compile outside the library, see above).

**Impact.** A value class with a wrapper schema and a field named anything but `value` gets a lens that compiles and always fails at run time. The Scala 3 `DerivedOptics` Scaladoc now says the field must be named `value`.

**Test gap.** The only wrapper test (`DerivedOpticsSpec`, "robustness: wrapperAsRecord strategy works for custom wrapper types", `schema/shared/src/test/scala/zio/blocks/schema/DerivedOpticsSpec.scala:372-393`) uses a field named `value`.

**Suggested fix.** In the case-class path, when the schema is a wrapper and the case class has one field, register the lens under that field's name (or look it up by index, as Scala 2 does), and add a test with a value class whose field is not named `value`.

### schema: `DynamicOptic#toString` prints paths that `p"..."` rejects: wrapper steps, the root, and `Char` keys with an escape

Found 2026-10-21 while writing the Scaladoc of `DynamicOptic` and `p"..."` (wave 4 of the schema Scaladoc, branch `scaladoc-wave-4`, on top of `bffadae7`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproductions below were run with `scala-cli` against the published `0.0.56`.

**Summary.** `DynamicOptic#toString` is documented as the `p"..."` syntax, so its output can be pasted back into the interpolator (`docs/reference/schema/dynamic-optic.md`, "Dual rendering"; `docs/reference/schema/path-interpolator.md`, "Debug-Friendly toString"). Three renderings cannot: a wrapper step prints `.~` and the empty path prints `.`, which the parser rejects, and a `Char` map key with an escape prints with a doubled backslash, `{'\\n'}` for a newline. The doubled backslash also appears in `toScalaString`, as `.atKey('\\n')`, which is not the Scala literal for a newline. `String` keys are escaped correctly.

**Reproduction** (Scala 3.9.0, `zio-blocks-schema` 0.0.56; the same file with `//> using scala 2.13.18` prints the same five lines):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

object Main {
  def main(args: Array[String]): Unit = {
    println(DynamicOptic.root.field("isbn").wrapped)
    println(DynamicOptic.root)
    println(DynamicOptic.root.atKey('\n'))
    println(DynamicOptic.root.atKey('\n').toScalaString)
    println(p"""{'\n'}""" == DynamicOptic.root.atKey('\n'))
  }
}
// .isbn.~
// .
// {'\\n'}
// .atKey('\\n')
// true
```

Pasting the three renderings back into the interpolator (Scala 3.9.0, `zio-blocks-schema` 0.0.56; output of `scala-cli compile --server=false Repro.scala`, with the scratch directory removed from the file paths):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

object Main {
  def main(args: Array[String]): Unit = {
    println(p".isbn.~")
    println(p".")
    println(p"""{'\\n'}""")
  }
}
// -- Error: Repro.scala:7:12
// 7 |    println(p".isbn.~")
//   |            ^^^^^^^^^^
//   |            Invalid identifier at position 6
// -- Error: Repro.scala:8:12
// 8 |    println(p".")
//   |            ^^^^
//   |            Invalid identifier at position 1
// -- Error: Repro.scala:9:12
// 9 |    println(p"""{'\\n'}""")
//   |            ^^^^^^^^^^^^^^
//   |            Char literal contains multiple characters at position 1
// 3 errors found
// Compilation failed
```

**Expected.** Every `toString` output parses back with `p"..."` to an equal path, and a `Char` key renders like a `String` key: `{'\n'}` and `.atKey('\n')`.

**Root cause.** `DynamicOptic.renderString` prints `.~` for `Node.Wrapped` (`schema/shared/src/main/scala/zio/blocks/schema/DynamicOptic.scala:210`) and `.` for an empty path (`:173`), but `PathParser.parseNode` requires an identifier after `.` (`schema/shared/src/main/scala/zio/blocks/schema/PathParser.scala:154-156`) and has no syntax for a wrapper step. In `renderDynamicValue`, the `Char` branch appends `"\\\\n"`, `"\\\\t"`, `"\\\\r"`, `"\\\\'"` and `"\\\\\\\\"` (`DynamicOptic.scala:291-295`), one level of escaping more than the `String` branch (`:277-281`), so it prints two backslashes where `parseChar` expects one (`PathParser.scala:467-496`).

**Impact.** A path copied from a log, an error message or `toString` into `p"..."` fails to compile when it contains a wrapper step, which `toDynamic` produces for every `.wrapped[...]` step and which the error paths of transformed fields contain (see the `.wrapped` entry above), or a `Char` key with an escape. `docs/reference/schema/path-interpolator.md` listed `p".~"` as the syntax for `DynamicOptic.wrapped`; that row is corrected in the same PR.

**Test gap.** `DynamicOpticSpec` checks `toString` for a plain `Char` key only (`'A'`), and `PathInterpolatorSpec`'s round-trip tests cover search steps only; no test parses `toString` output of the other node kinds back.

**Suggested fix.** Give the parser a wrapper step that matches the rendering (accept `.~`), and render the empty path as the empty string or accept `.` as the root. In the `Char` branch, append `"\\n"`, `"\\t"`, `"\\r"`, `"\\'"` and `"\\\\"`, as the `String` branch does. Add a property test that `p`-parses `toString` of every node kind back to an equal `DynamicOptic` (through `PathParser.parse`).

### schema: a `searchFor` traversal reads map keys but `modify` never updates them

Found 2026-10-22 in review of the wave 4 Scaladoc (`docs/dev/backlog.md` section 11, branch `scaladoc-wave-4`, on top of `bffadae7`; line numbers are those of the commit that last edits this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`.

**Summary.** A `searchFor` traversal reads a different set of values than it modifies. `fold` and `check` also visit the keys of maps, so a key that decodes with the focus schema is a match. `modify`, `modifyOption` and `modifyOrFail` skip map keys, so the same match is read but never updated.

**Reproduction** (Scala 3.9.0, `zio-blocks-schema` 0.0.56; the same file with `//> using scala 2.13.18` prints the same two lines):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

case class Library(index: Map[String, String], titles: List[String])
object Library extends CompanionOptics[Library] {
  implicit val schema: Schema[Library] = Schema.derived
  val strings: Traversal[Library, String] = $(_.searchFor[String])
}

object Main {
  def main(args: Array[String]): Unit = {
    val library = Library(Map("isbn" -> "zionomicon"), List("Programming in Scala"))
    println(Library.strings.fold[List[String]](library)(Nil, (acc, s) => s :: acc).reverse)
    println(Library.strings.modify(library, _.toUpperCase))
  }
}
// List(isbn, zionomicon, Programming in Scala)
// Library(Map(isbn -> ZIONOMICON),List(PROGRAMMING IN SCALA))
```

**Expected.** `fold` and `modify` agree on the matches. Either `modify` also gives `Map(ISBN -> ZIONOMICON)`, or `fold` leaves out `isbn`.

**Root cause.** `SearchTraversal.searchCollectDecoded` and `searchHasMatch` push both the key and the value of each map entry (`schema/shared/src/main/scala/zio/blocks/schema/Optic.scala:3656`, `:3686`). `searchModify` (`:3710-3711`) uses `DynamicValue.iterativeTransform`, which visits only map values (`schema/shared/src/main/scala/zio/blocks/schema/DynamicValue.scala:1423`). The `DynamicValue`-level search skips keys on purpose (`DynamicValue.scala:1354`: "Search map values only (not keys), consistent with iterativeTransform"), so the typed traversal is the odd one out.

**Impact.** For `searchFor[String]` over any type with a `Map[String, _]`, `fold` returns keys that `modify` does not change. A caller that counts matches with `fold` and then updates them with `modify` gets inconsistent results.

**Test gap.** `SearchTraversalSpec` searches maps only for record values (`"searches through Map values"`, `"modifies values in Map"`). No test has a match among the keys.

**Suggested fix.** Make `searchCollectDecoded` and `searchHasMatch` skip map keys, consistent with `iterativeTransform` and `DynamicValue`'s own search. Rewriting keys could merge entries, so skipping them is the safer choice. Add a test with a `Map[String, String]` that checks `fold` and `modify` agree.

### schema: `DerivedOptics` gives one type the cached optics of another type with the same printed name

Found 2026-10-22 in review of the wave 4 Scaladoc (`docs/dev/backlog.md` section 11, branch `scaladoc-wave-4`, on top of `bffadae7`; line numbers are those of the commit that last edits this entry). The cache code is the same at tag `v0.0.56`. Verified: the reproductions below were run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`.

**Summary.** `DerivedOptics` caches the optics of each type in a global map keyed by the type's printed name. Two different types that print the same name share one entry, for example local case classes named `Book` in two methods, or same-named classes in two mdoc pages built in one JVM (all under `repl.MdocSession.MdocApp.`). The second type gets the first type's optics, and using them fails with a `ClassCastException` or `No optic found`.

**Reproduction** (Scala 3.9.0, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._
import scala.util.Try

object Main {
  def first(): Unit = {
    case class Book(title: String, pages: Int)
    object Book extends DerivedOptics {
      implicit val schema: Schema[Book] = Schema.derived
    }
    println(Try(Book.optics.title.get(Book("Zionomicon", 870))))
  }

  def second(): Unit = {
    case class Book(isbn: String, title: String)
    object Book extends DerivedOptics {
      implicit val schema: Schema[Book] = Schema.derived
    }
    println(Try(Book.optics.title.get(Book("978-0-9000000-0-1", "Programming in Scala"))))
    println(Try(Book.optics.isbn.get(Book("978-0-9000000-0-1", "Programming in Scala"))))
  }

  def main(args: Array[String]): Unit = {
    first()
    second()
  }
}
// Success(Zionomicon)
// Failure(java.lang.ClassCastException: class Main$Book$4 cannot be cast to class Main$Book$1 (Main$Book$4 and Main$Book$1 are in unnamed module of loader 'app'))
// Failure(java.lang.RuntimeException: No optic found for: isbn)
```

Scala 2 behaves the same (Scala 2.13.18, `zio-blocks-schema` 0.0.56). The file is in package `zio.blocks.schema` because `optics` compiles only there on Scala 2 (see the entry above):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
// In package zio.blocks.schema because of the Scala 2 `optics` access bug (see its own entry).
package zio.blocks.schema

import scala.util.Try

object Main {
  def first(): Unit = {
    case class Book(title: String, pages: Int)
    implicit val schema: Schema[Book] = Schema.derived
    object BookOptics extends DerivedOptics.Of[Book]
    println(Try(BookOptics.optics.title.get(Book("Zionomicon", 870))))
  }

  def second(): Unit = {
    case class Book(isbn: String, title: String)
    implicit val schema: Schema[Book] = Schema.derived
    object BookOptics extends DerivedOptics.Of[Book]
    println(Try(BookOptics.optics.title.get(Book("978-0-9000000-0-1", "Programming in Scala"))))
  }

  def main(args: Array[String]): Unit = {
    first()
    second()
  }
}
// Success(Zionomicon)
// Failure(java.lang.ClassCastException: class zio.blocks.schema.Main$Book$4 cannot be cast to class zio.blocks.schema.Main$Book$1 (zio.blocks.schema.Main$Book$4 and zio.blocks.schema.Main$Book$1 are in unnamed module of loader 'app'))
```

scala-cli also prints `warning: 2 feature warnings; re-run with -feature for details` for the Scala 2 file.

**Expected.** Each type gets its own optics: `second()` prints `Success(Programming in Scala)` and `Success(978-0-9000000-0-1)`.

**Root cause.** On Scala 3 the cache key is `tpe.show` (`schema/shared/src/main/scala-3/zio/blocks/schema/DerivedOptics.scala:457`, `:497`, `:555`), stored in a global `ConcurrentHashMap` (`:379-381`). On Scala 2 it is `tpe.toString` (`schema/shared/src/main/scala-2/zio/blocks/schema/DerivedOptics.scala:379`, `:414`, `:437`, cache at `:279-281`). Both print a local class without its enclosing method, so `getOrCreate` returns the first type's optics for the second.

**Impact.** Two same-named types in one program silently share optics, which then fail at run time far from the cause. mdoc wraps every page in `repl.MdocSession.MdocApp`, so two documentation pages that both declare a `Book` with `DerivedOptics` can break each other in one docs build.

**Test gap.** `DerivedOpticsSpec` checks that `Box[Int]` and `Box[String]` get different optics (`!(BoxInt.optics eq BoxString.optics)`), but never two distinct types with the same printed name.

**Suggested fix.** Key the cache by something that identifies the type: the class's runtime `Class` object (from a `ClassTag`, generated by the macro) plus the printed type arguments. Or drop the global cache and keep the optics in a `lazy val` of the companion, which the macro can generate. Add a test with two local case classes of the same name.

### schema: `Traversal#check` reports `atIndices` elements of a `List` as out of bounds when another step follows

Found 2026-10-22 in review of the wave 4 Scaladoc (`docs/dev/backlog.md` section 11, branch `scaladoc-wave-4`, on top of `bffadae7`; line numbers are those of the commit that last edits this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`.

**Summary.** `Traversal#check` on `atIndices(i, j)` followed by another step wrongly reports every index after the first as out of bounds when the sequence is not indexed, for example a `List`. `fold` and `modify` reach those elements correctly, so the optic works while `check` (and the `Left` of a failing `modifyOrFail`, which comes from `check`) describes a problem that does not exist.

**Reproduction** (Scala 3.9.0, `zio-blocks-schema` 0.0.56; the same file with `//> using scala 2.13.18` prints the same three lines):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

case class Book(title: String, year: Int)
object Book { implicit val schema: Schema[Book] = Schema.derived }

case class Shelf(books: List[Book])
object Shelf extends CompanionOptics[Shelf] {
  implicit val schema: Schema[Shelf] = Schema.derived
  val firstAndThirdTitle: Traversal[Shelf, String] = $(_.books.atIndices(0, 2).title)
  val firstAndThird: Traversal[Shelf, Book]        = $(_.books.atIndices(0, 2))
}

object Main {
  def main(args: Array[String]): Unit = {
    val shelf = Shelf(
      List(Book("Zionomicon", 2021), Book("Programming in Scala", 2021), Book("Functional Programming in Scala", 2014))
    )
    println(Shelf.firstAndThirdTitle.fold[List[String]](shelf)(Nil, (acc, t) => t :: acc).reverse)
    println(Shelf.firstAndThirdTitle.check(shelf).map(_.message))
    println(Shelf.firstAndThird.check(shelf).map(_.message))
  }
}
// List(Zionomicon, Functional Programming in Scala)
// Some(During attempted access at .books.atIndices(0, 2).title, encountered a sequence out of bounds at .books.atIndices(0, 2): index is 2, but size is 2)
// None
```

**Expected.** `check` returns `None` for both optics: the list has three elements, and `fold` reaches indices 0 and 2.

**Root cause.** In `TraversalImpl.checkRecursive`, the non-indexed branch for `AtIndicesBinding` skips ahead with `currIdx`, then reads the element with `it.next()` for the next step without incrementing `currIdx` (`schema/shared/src/main/scala/zio/blocks/schema/Optic.scala:2477`). For the next index, `currIdx` is one behind the iterator, so the loop runs out one element early. `foldRecursive` increments `currIdx` after the same call (`:2617-2618`). When `atIndices` is the last step, no element is consumed, so the count stays right.

**Impact.** `check` and `modifyOrFail` report false `SequenceIndexOutOfBounds` warnings for `atIndices` on lists followed by a field or any other step.

**Test gap.** The `OpticSpec` tests that compose `atIndices` with a following step use collections too short for the first index (`"index is 1, but size is 1"`, `schema/shared/src/test/scala/zio/blocks/schema/OpticSpec.scala:2773`, `:2794`), so the missing increment never shows.

**Suggested fix.** Add `currIdx += 1` after the recursive `checkRecursive` call in that branch, as `foldRecursive` does, and add a test that composes `atIndices(0, 2)` with a field on a three-element `List`.

### schema: a `SchemaError` thrown by `f` below a `wrapped` step is reported as a wrapper failure, and `modify` ignores it

Found 2026-10-22 in review of the wave 4 Scaladoc (`docs/dev/backlog.md` section 11, branch `scaladoc-wave-4`, on top of `bffadae7`; line numbers are those of the commit that last edits this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`, on Scala 3.9.0 and 2.13.18.

**Summary.** When an `Optional` or a `Traversal` has a `wrapped` step, a `SchemaError` thrown by the user's `f` in `modify`, `modifyOption` or `modifyOrFail` is caught as if the wrapper's `wrap` had thrown it. `modify` returns the value unchanged, `modifyOption` returns `None`, and `modifyOrFail` returns a `WrappingError` at `.wrapped`, with `f`'s message. On a plain lens the same `f` throws. A caller cannot tell an `f` that rejects its input from a wrapper that rejects the result.

**Reproduction** (Scala 3.9.0, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._
import scala.util.Try

case class Isbn(value: String)
object Isbn {
  implicit val schema: Schema[Isbn] = Schema[String].transform(Isbn(_), _.value)
}

case class Book(title: String, isbn: Isbn)
object Book extends CompanionOptics[Book] {
  implicit val schema: Schema[Book] = Schema.derived
  val isbnValue: Optional[Book, String] = $(_.isbn.wrapped[String])
  val title: Lens[Book, String]         = $(_.title)
}

object Main {
  def main(args: Array[String]): Unit = {
    val b                            = Book("Zionomicon", Isbn("978-0-9000000-0-1"))
    val reject: String => String     = _ => throw SchemaError("bad")
    println(Try(Book.isbnValue.modify(b, reject)))
    println(Try(Book.isbnValue.modifyOrFail(b, reject)))
    println(Try(Book.title.modify(b, reject)))
  }
}
// Success(Book(Zionomicon,Isbn(978-0-9000000-0-1)))
// Success(Left(zio.blocks.schema.OpticCheck: During attempted access at .isbn.wrapped, encountered an error at .isbn.wrapped: bad))
// Failure(zio.blocks.schema.SchemaError: bad)
```

On Scala 2.13.18 (the same file with `//> using scala 2.13.18`) the output is the same three lines, once `import zio.blocks.schema.binding.Binding` is added; without that import the `wrapped` step does not compile on Scala 2 (see the next entry).

**Expected.** The `SchemaError` from `f` propagates, as it does on a plain lens: all three calls print `Failure(zio.blocks.schema.SchemaError: bad)`. Only a `SchemaError` thrown by the wrapper's own `wrap` or `unwrap` should become a `WrappingError`.

**Root cause.** In `OptionalImpl.modifyRecursive` (`schema/shared/src/main/scala/zio/blocks/schema/Optic.scala:1689-1696`) and `TraversalImpl.modifyRecursive` (`:3091-3098`), the whole block that calls `f` (or recurses into the next steps, which call `f`) is the argument of `wrapperBinding.wrap(...)`, inside `try ... catch { case error: SchemaError => throw toOpticCheckBuilder(idx, error) }`. The argument is evaluated inside the `try`, so the `catch` also sees exceptions from `f`. `modify` and `modifyOption` then turn the `OpticCheckBuilder` into "unchanged" or `None`.

**Impact.** Validation code that throws a `SchemaError` from `f` (a natural choice in this library) is silently ignored by `modify` and misreported by `modifyOrFail` whenever the path goes through a `wrapped` step, such as a field of a `transform` schema reached with `.wrapped[...]`. The Scaladoc of `Optic#modify`, `modifyOption` and `modifyOrFail` describes the current behaviour and points here.

**Test gap.** No test has an `f` that throws. The `SchemaError` uses in `OpticSpec` (`schema/shared/src/test/scala/zio/blocks/schema/OpticSpec.scala:2043`, `:2399`, `:3519-3525`) are all wrapper fixtures.

**Suggested fix.** Evaluate the inner block before the `try`, so that only the `wrap` call is guarded:

```scala
val inner =
  if (idx + 1 == bindings.length) f(x1.asInstanceOf[A])
  else modifyRecursive(registers, idx + 1, x1, f)
try wrapperBinding.wrap(inner.asInstanceOf[Wrapped])
catch { case error: SchemaError => throw toOpticCheckBuilder(idx, error) }
```

Add tests for `modify`, `modifyOption` and `modifyOrFail` with an `f` that throws a `SchemaError` below a `wrapped` step.

### schema: on Scala 2, a `.wrapped[B]` step in `$`/`optic` compiles only when `Binding` is imported

Found 2026-10-22 in review of the wave 4 Scaladoc (`docs/dev/backlog.md` section 11, branch `scaladoc-wave-4`, on top of `bffadae7`; line numbers are those of the commit that last edits this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`.

**Summary.** On Scala 2, the `CompanionOptics` macro expands a `.wrapped[B]` step to code that names `Reflect.Wrapper[Binding, ...]` without a package prefix. With only `import zio.blocks.schema._` in scope, `Binding` (in `zio.blocks.schema.binding`) is not found and the optic does not compile. Scala 3 is not affected.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56; the file from the previous entry with `//> using scala 2.13.18`; output of `scala-cli run --server=false Repro.scala`, with the scratch directory removed from the file path):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._
import scala.util.Try

case class Isbn(value: String)
object Isbn {
  implicit val schema: Schema[Isbn] = Schema[String].transform(Isbn(_), _.value)
}

case class Book(title: String, isbn: Isbn)
object Book extends CompanionOptics[Book] {
  implicit val schema: Schema[Book] = Schema.derived
  val isbnValue: Optional[Book, String] = $(_.isbn.wrapped[String])
  val title: Lens[Book, String]         = $(_.title)
}
// (Main as in the previous entry)
// Repro.scala:14: error: not found: type Binding
//   val isbnValue: Optional[Book, String] = $(_.isbn.wrapped[String])
//                                            ^
// 1 error
// Compilation failed
```

Adding `import zio.blocks.schema.binding.Binding` makes the file compile, and it prints the same three lines as on Scala 3.

**Expected.** The program compiles with `import zio.blocks.schema._` only, as on Scala 3.

**Root cause.** Both `.wrapped` branches of the Scala 2 macro emit `x.wrapper.asInstanceOf[Reflect.Wrapper[Binding, $parentTpe, $wrappedTpe]]` (`schema/shared/src/main/scala-2/zio/blocks/schema/CompanionOptics.scala:283`, `:290`), with unqualified `Reflect` and `Binding`. Every other tree in the macro uses `_root_.zio.blocks.schema...` names.

**Impact.** `.wrapped[...]` steps fail to compile on Scala 2 in user code, with an error that does not point at the missing import. `Reflect` also resolves only through the user's wildcard import.

**Test gap.** The tests that use `.wrapped` in `$`/`optic` are in package `zio.blocks.schema` and import `zio.blocks.schema.binding._`, so the unqualified names resolve there.

**Suggested fix.** Emit `_root_.zio.blocks.schema.Reflect.Wrapper[_root_.zio.blocks.schema.binding.Binding, $parentTpe, $wrappedTpe]` in both branches, and add a Scala 2 test of `.wrapped` in a package outside `zio.blocks.schema` that imports only `zio.blocks.schema._`.

### sql: `queryStream` throws, instead of returning `Left`, when the `SELECT` has fewer columns than the row type, and logs the failed query as a success

Found 2026-10-20 while writing the how-to guide `docs/guides/stream-large-query-results.md` (backlog H-20), on `main` at `b4179489`. The code is the same at tag `v0.0.56`: `git diff v0.0.56 main -- sql/ streams/` is empty, and the stack trace from the 0.0.56 jar points at the same line numbers as `main`.

**Summary.** `Frag#queryStream` and `Frag#queryChunked` promise that "SQL failures surface as typed errors (`Left`) from terminal operations such as `runCollect`". That is true when the database rejects the statement and when one row fails to decode. It is not true when the result set has fewer columns than the row type needs, for example `SELECT id, title` decoded as a four-field `Sale`. Then `runCollect` throws `java.sql.SQLException: column 3 out of bounds [1,2]` out of the call, so code that matches on the `Either` never sees the error. The `SqlLogger` also records the failed query as `onSuccess` with `rowCount=0`, and no `onError` event is emitted. The statement and result set are still closed (checked by tracking the `PreparedStatement` through a `java.sql.Connection` proxy and calling `isClosed` before the connection closed), so nothing leaks.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-sql` 0.0.56 + sqlite-jdbc 3.53.4.0, run with `scala-cli run .`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0

import java.nio.file.Files
import java.sql.DriverManager
import scala.util.Try
import zio.blocks.schema.Schema
import zio.blocks.sql.*

case class Sale(id: Long, title: String, quantity: Int, soldOn: String)
object Sale { given Schema[Sale] = Schema.derived }

val logger = new SqlLogger {
  def onSuccess(e: SqlLogger.SuccessEvent): Unit = println(s"  [log] onSuccess rowCount=${e.rowCount}: ${e.sql}")
  def onError(e: SqlLogger.ErrorEvent): Unit     = println(s"  [log] onError ${e.error.getMessage}: ${e.sql}")
}

@main def queryStreamThrows(): Unit = {
  val url = s"jdbc:sqlite:${Files.createTempFile("sales", ".db")}"
  val tx  = new JdbcTransactor(() => DriverManager.getConnection(url), SqlDialect.SQLite)
  tx.connect {
    Table.derived[Sale].createTable(SqlDialect.SQLite).update
    Frag.literal("INSERT INTO sale VALUES (1, 'Zionomicon', 3, '2026-10-01')").update
    ()
  }
  val logged = new JdbcTransactor(() => DriverManager.getConnection(url), SqlDialect.SQLite, logger)

  // The SELECT lists 2 of the 4 columns Sale needs.
  println(Try(logged.connect(sql"SELECT id, title FROM sale".queryStream[Sale].runCollect)))
  // An unknown column, for comparison.
  println(Try(logged.connect(sql"SELECT nope FROM sale".queryStream[Sale].runCollect)))
  // The List API, for comparison.
  println(Try(logged.connect(sql"SELECT id, title FROM sale".query[Sale])))
}

//   [log] onSuccess rowCount=0: SELECT id, title FROM sale
// Failure(java.sql.SQLException: column 3 out of bounds [1,2])
//   [log] onError [SQLITE_ERROR] SQL error or missing database (no such column: nope): SELECT nope FROM sale
// Success(Left(org.sqlite.SQLiteException: [SQLITE_ERROR] SQL error or missing database (no such column: nope)))
//   [log] onError column 3 out of bounds [1,2]: SELECT id, title FROM sale
// Failure(java.sql.SQLException: column 3 out of bounds [1,2])
```

The first `Failure` is the bug. The exception starts in the sqlite-jdbc driver (`CoreResultSet.checkCol`); its first `zio.blocks` frames are `JdbcResultReader.columnLabel(JdbcResultReader.scala:277)` ← `Frag$.selectLabels(Frag.scala:216)` ← `Frag$.rowDecoder(Frag.scala:237)` ← `Frag$.queryChunked` (`Frag.scala:435`) ← `Stream$FromAcquireRelease.compileInterpreter(Stream.scala:5870)`. The third line is the expected behavior of the `List` API, which throws by design and does log `onError`.

**Expected.** The first call prints `Success(Left(java.sql.SQLException: column 3 out of bounds [1,2]))`, and the logger records one `onError` event and no `onSuccess`, the same as for `SELECT nope`.

**Root cause.** `queryChunked` (`sql/shared/src/main/scala/zio/blocks/sql/Frag.scala:396-470`) handles errors in two places, and the column-mapping step falls between them:

1. Running the statement happens inside `Stream.attempt` (`Frag.scala:399-418`). Its `catch` (`:409-417`) closes the statement, logs `onError` and rethrows, and `Stream.attempt` turns the exception into a typed error.
2. Reading each row happens inside the `SyncReader#read` `try`/`catch` (`Frag.scala:442-463`), which logs `onError` and calls `failSource(e)`, another typed error.

Between the two, the `use` function of `Stream.fromAcquireRelease` (`Frag.scala:433-435`) calls `rowDecoder(reader, codec)` once to decide how rows map to columns. `rowDecoder` (`Frag.scala:231-240`) falls back to `selectLabels` (`Frag.scala:213-217`), which, for each codec column that the result set doesn't have by name, asks for the label at the same position: `reader.columnLabel(offset + 1)`. With a two-column result and a four-column codec, position 3 doesn't exist, and the JDBC driver throws. `Stream.fromAcquireRelease` documents that exceptions thrown by `use` are defects (`streams/shared/src/main/scala/zio/blocks/streams/Stream.scala:2955-2960`). `FromAcquireRelease#compileInterpreter` (`Stream.scala:5864-5882`) runs `release` and rethrows (`:5876-5881`), and `runCollect` lets the defect escape.

The wrong log event comes from the same gap. `release` (`Frag.scala:422-432`) logs `onSuccess` whenever the shared `mark.logged` flag is still unset (`:428-431`), because it assumes that an unlogged release means the consumer stopped early. The error paths in steps 1 and 2 set that flag before logging `onError`; the `rowDecoder` failure sets nothing, so `release` reports a success with `mark.count` = 0.

The query is only wrong for a codec column that is missing by name *and* lies past the last result column. A missing column at a position that exists is read from that position instead: `SELECT id, title, quantity, sold_on AS x` returns `Right` with `soldOn` filled from `x`, and any per-row failure there happens inside `read` and becomes a `Left`. A one-column `SELECT id FROM sale` decoded as `(Long, String)` throws `column 2 out of bounds [1,1]` the same way.

**Impact.** Users of the streaming API are told they can handle every SQL failure through the `Either`. A projection that leaves out columns, which is an easy mistake when a query is edited, instead crashes the caller (for example a request handler that only matches on `Left`/`Right`). Monitoring built on `SqlLogger` counts the broken query as a successful zero-row read, so dashboards and alerts don't see it. `Frag#query`, `queryOne` and `queryLimit` are not affected: they throw by design, and they log `onError` correctly.

**Test gap.** The streaming tests are in `sql/jvm/src/test/scala/zio/blocks/sql/TransactorSpec.scala:539-645`. "surfaces acquisition failure as Left" (`:595`) only covers a failure inside `executeQuery` (unknown table). "surfaces row decode failure as Left" (`:607`) uses `failingCodec` (`:124-135`), which delegates to the real codec and then throws inside `readValue`, so it only covers the per-row path inside `read`. No test makes `rowDecoder` itself fail, meaning a `SELECT` with fewer columns than the codec, and no test in `sql/shared/src/test` or `sql/jvm/src/test` decodes a narrower projection through `query` or `queryChunked`.

**Suggested fix.** Resolve the decoder during acquisition, so that it fails through the existing `catch`. In the `Stream.attempt` block, after `ps.executeQuery()`, compute `val decode = rowDecoder(rs.reader, codec)` and return it with the resource (`(ps, rs, decode, FinishMark(start))`). Closing `ps` in that `catch` also closes `rs`. The `use` function then only builds the reader. Another option is to keep the call in `use`, wrap it in a `try` that sets `mark.logged`, logs `onError` and returns `Stream.fail(e)`. `release` still closes the statement and, with the flag set, won't log a second event. Either way, add a `TransactorSpec` case like "frag.queryChunked surfaces a column-count mismatch as Left with exactly one error log": decode a one-column `SELECT id FROM t` as `(Long, String)` and assert `result.isLeft`, `logger.errors.size == 1` and `logger.successes.isEmpty`.

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

### sql-zio: `transactZIO` commits instead of rolling back when its body throws while being built, or when it is interrupted

Found 2026-10-21 while writing the how-to guide `docs/guides/sql-from-zio.md` (backlog H-21), on `main` at `a44dc6db`. The code is the same at tag `v0.0.56`: `git diff v0.0.56 main -- sql-zio/` is empty. Verified by running the reproduction below against the published 0.0.56 artifacts.

**Summary.** `TransactorZIO#transactZIO` promises to commit on success and roll back on failure, and its class Scaladoc says the connection is "always closed even on interruption". A typed failure (`ZIO.fail`) and a defect inside the body's effect (`ZIO.die`) do roll back. Two other ways out of the body commit instead: (1) a statement written directly in the body, outside any ZIO constructor, that throws while the body's effect is being built; (2) interruption of the running transaction from outside, for example `transactZIO { ... }.timeout(d)`, `race`, or a fiber interrupt. In both cases every statement that already ran is committed, and the caller sees a defect or a timeout, so it believes nothing was written.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-sql-zio` 0.0.56, sqlite-jdbc 3.53.4.0, run with `scala-cli run .`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql-zio:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0

import java.nio.file.Files
import zio.*
import zio.blocks.sql.zio.*
import zio.blocks.sql.*

object TransactZIOCommits extends ZIOAppDefault {
  val tx = TransactorZIO.fromUrl(s"jdbc:sqlite:${Files.createTempFile("repro", ".db")}", SqlDialect.SQLite)

  def rows = tx.connect(sql"SELECT x FROM t ORDER BY x".query[Int])

  def run = for {
    _ <- tx.connect(sql"CREATE TABLE t (x INTEGER NOT NULL)".update)

    // 1. A statement in the body throws while the body's effect is being built.
    r1 <- tx.transactZIO {
            sql"INSERT INTO t VALUES (1)".update
            sql"INSERT INTO t VALUES (NULL)".update // NOT NULL violation, thrown here
            ZIO.unit
          }.exit
    _  <- Console.printLine(s"1. died: ${r1.causeOption.exists(_.isDie)}")
    _  <- rows.flatMap(r => Console.printLine(s"   rows after a body that threw: $r"))

    // 2. The body's effect is interrupted, here by a timeout.
    r2 <- tx.transactZIO {
            ZIO.attemptBlocking(sql"INSERT INTO t VALUES (2)".update) *> ZIO.never
          }.timeout(200.millis)
    _  <- Console.printLine(s"2. timeout result: $r2")
    _  <- rows.flatMap(r => Console.printLine(s"   rows after an interrupted transaction: $r"))

    // 3. For comparison: a typed failure rolls back.
    r3 <- tx.transactZIO {
            ZIO.attemptBlocking(sql"INSERT INTO t VALUES (3)".update) *> ZIO.fail("boom")
          }.either
    _  <- Console.printLine(s"3. typed failure: $r3")
    _  <- rows.flatMap(r => Console.printLine(s"   rows after a failed transaction: $r"))
  } yield ()
}

// 1. died: true
//    rows after a body that threw: List(1)
// 2. timeout result: None
//    rows after an interrupted transaction: List(1, 2)
// 3. typed failure: Left(boom)
//    rows after a failed transaction: List(1, 2)
```

Rows `1` and `2` are the bug: both come from transactions that did not succeed. Row `3`, from the typed failure, is correctly rolled back.

**Expected.** Both transactions roll back: the rows stay `List()` after case 1 and after case 2. Case 1 still dies with the `SQLiteException` (or fails with it), and case 2 still returns `None`.

**Root cause.** The rollback lives only in the `flatMap` after `f(using tx).exit` (`sql-zio/src/main/scala/zio/blocks/sql/zio/TransactorZIO.scala:200-210`). The release finalizer of the `acquireRelease` (`TransactorZIO.scala:175-189`) never rolls back; it restores the previous settings and calls `conn.setAutoCommit(prevAutoCommit)` (`:185`) before closing. `prevAutoCommit` is `true` for a fresh connection, and the JDBC contract of `Connection#setAutoCommit` is that "if this method is called during a transaction and the auto-commit mode is changed, the transaction is committed". So any path that skips the `flatMap` commits.

1. `f(using tx)` is evaluated inside the `flatMap` lambda at `:190` before `.exit` is applied. A body such as `{ sql"...".update; ZIO.unit }` runs its statements right there; when one throws, ZIO turns the exception into a defect of the outer `flatMap`, `.exit` never sees it, and the release commits the earlier statements.
2. When the fiber is interrupted while `f(using tx)` runs, the continuation of `.exit.flatMap` does not run (the region is interruptible), so the explicit rollback at `:209` is skipped, and the release commits.

**Impact.** Data corruption without an error to show for it. `ZIO#timeout` is the standard way to bound a request in ZIO, and a timed-out `transactZIO` leaves partial writes behind. Shutting down an application (which interrupts running fibers) commits half-done transactions. Users of `TransactorZIO#transact` are not affected: it delegates to `JdbcTransactor#transact`, which rolls back in a `catch`. The guide tells readers to wrap every statement in `ZIO.attemptBlocking` and to use `ZIO#timeoutFail` inside the body until this is fixed.

**Test gap.** `sql-zio/src/test/scala/zio/blocks/sql/zio/TransactorZIOSpec.scala` has four tests (`:106-178`): commit failure, commit on success, isolation-setup failure and default isolation. None runs a body that fails, dies, throws while being built or is interrupted, and none checks that rows are absent afterwards. `connectZIO` has no test at all.

**Suggested fix.** Make the release decide: use `ZIO.acquireReleaseExit` (or keep `acquireRelease` and always call `conn.rollback()` first in the finalizer, which is a no-op after a successful commit) so that the finalizer rolls back before it restores `autoCommit`. Evaluate the body lazily with `ZIO.suspend(f(using tx))` so an eager throw becomes part of the body's `Exit`. Add `TransactorZIOSpec` cases on a real SQLite file that assert no rows remain after a `ZIO.fail`, a `ZIO.die`, an eager throw, and `transactZIO(...).timeout(...)` around `ZIO.never`.

### sql-zio: the package `zio.blocks.sql.zio` shadows the root `zio` package after `import zio.blocks.sql.*`

Found 2026-10-21 while writing the how-to guide `docs/guides/sql-from-zio.md` (backlog H-21), on `main` at `a44dc6db`. The code is the same at tag `v0.0.56`. Verified by running the reproduction below against the published 0.0.56 artifacts.

**Summary.** `sql-zio` puts `TransactorZIO` and the `JdbcTransactor.postgresLayer`/`sqliteLayer` extensions in the package `zio.blocks.sql.zio`. Once the `sql-zio` jar is on the classpath, the wildcard `import zio.blocks.sql.*` also imports that subpackage under the name `zio`, and it shadows the root `zio` package for the rest of the scope. Every later `import zio.blocks...` and every fully qualified `zio.X` then fails to compile with `value blocks is not a member of zio.blocks.sql.zio`. Code that compiled before stops compiling as soon as a project adds the `sql-zio` dependency, even if it never uses it.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-sql-zio` 0.0.56, run with `scala-cli run .`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql-zio:0.0.56

import zio.blocks.sql.*
import zio.blocks.schema.Schema

case class Book(id: Long, title: String)
object Book { given Schema[Book] = Schema.derived }

@main def shadow(): Unit = println(Table.derived[Book].name)

// [error] ./Shadow.scala:6:8
// [error] value blocks is not a member of zio.blocks.sql.zio
// [error] import zio.blocks.schema.Schema
// [error]        ^^^^^^^^^^
// Error compiling project (Scala 3.9.0, JVM (21))
```

With `dev.zio::zio-blocks-next-sql:0.0.56` instead of `sql-zio` in the `using dep` line, the same file compiles and prints `book`.

**Expected.** Adding `sql-zio` doesn't change what `zio` means in existing code.

**Root cause.** The package clause `package zio.blocks.sql.zio` (`sql-zio/src/main/scala/zio/blocks/sql/zio/TransactorZIO.scala:17`, `JdbcTransactorLayers.scala:17`). A wildcard import brings in member packages too, and an imported name takes precedence over the top-level package `zio`. Writing `import zio.blocks.sql.zio.*` before `import zio.blocks.sql.*` works, but only as long as nothing after it starts with `zio.`.

**Impact.** Every sql page in the docs used the failing order, so putting `sql-zio` on the docs classpath for H-21 broke 16 docs pages and 23 files in `sql-examples` (including the H-18, H-20 and T-15 pages and examples merged while H-21 was open), all fixed on the H-21 branch by putting `import zio.blocks.sql.*` last (plus an invisible `import _root_.zio` before self-contained `compile-only` blocks that mdoc compiles in a page's shared scope). `docs/reference/sql-zio.md` and `docs/reference/sql/transactor-zio.md` showed the failing order in every snippet, so none of them compiled. Users hit it the first time they add ZIO support to an existing sql code base.

**Test gap.** The sql-zio tests live inside `package zio.blocks.sql.zio` and import `_root_.zio.*` (`TransactorZIOSpec.scala:19-21`), so they can't see the problem. No test compiles user-style code with `import zio.blocks.sql.*` followed by another `zio` import against a classpath that contains `sql-zio`.

**Suggested fix.** Rename the package to one whose last segment isn't `zio`, for example `zio.blocks.sqlzio` or `zio.blocks.sql.ziointerop`. The module is pre-1.0 (0.0.x), so a rename is cheap now and gets more expensive with every release. Add a compile test in a package outside `zio.blocks` that imports `zio.blocks.sql.*` and then `zio.blocks.schema.Schema`.

### sql: `Frag.keysetAfter` leaves the column unquoted, so a keyword column such as `order` renders invalid SQL

Found 2026-10-21 while writing the how-to guide `docs/guides/upsert-and-paginate-with-keyset-cursors.md` (backlog H-18), on `main` at `a44dc6db`. The code is the same at tag `v0.0.56`. Verified. Not filed.

**Summary.** `Frag.keysetAfter(table, orderCol, lastValue, limit)` and its table-less overload validate `orderCol` with `SqlIdentifier.validate` and then splice it into the SQL without quotes: `WHERE order > ? ORDER BY order ASC LIMIT n`. Every other SQL path in the module double-quotes identifiers since #1656 (`Repo`, `Upsert`, the query renderer), so a column that works everywhere else, such as a case-class field named `order`, passes `keysetAfter`'s validation and then fails at execution with a syntax error. `Repo#pageAfter` on the same table works, because it quotes its ID column.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-sql` 0.0.56, `sqlite-jdbc` 3.53.4.0, run with `scala-cli run KeysetReservedWord.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
import zio.blocks.sql.*
import zio.blocks.schema.Schema
import scala.util.Try

final case class Shipment(id: Long, order: Long)
object Shipment { given Schema[Shipment] = Schema.derived }

@main def run(): Unit = {
  val dbFile = java.nio.file.Files.createTempFile("keyset", ".db")
  dbFile.toFile.deleteOnExit()
  val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
  val shipments  = Repo.derived[Shipment, Long]

  transactor.connect {
    sql"""CREATE TABLE shipment ("id" INTEGER PRIMARY KEY, "order" INTEGER NOT NULL)""".update
    println(shipments.insertBatch(List(Shipment(1, 10), Shipment(2, 20), Shipment(3, 30))))
    // 3

    // Repo quotes the column: works
    println(shipments.pageAfter(1L, 10))
    // List(Shipment(2,20), Shipment(3,30))

    // keysetAfter accepts "order" (valid identifier, in table.columns) but doesn't quote it
    val page = sql"SELECT * FROM shipment" ++ Frag.keysetAfter(shipments.table, "order", DbValue.DbLong(10L), 10)
    println(page.sql(SqlDialect.SQLite))
    // SELECT * FROM shipment WHERE order > ? ORDER BY order ASC LIMIT 10
    println(Try(page.query[Shipment]))
    // Failure(org.sqlite.SQLiteException: [SQLITE_ERROR] SQL error or missing database (near "order": syntax error))
  }
}
```

**Expected.** `keysetAfter` renders `WHERE "order" > ? ORDER BY "order" ASC LIMIT 10`, like `Repo#pageAfter` does for its ID column, and the query returns `List(Shipment(2,20), Shipment(3,30))`.

**Root cause.** `sql/shared/src/main/scala/zio/blocks/sql/Frag.scala:169-172` (table overload) and `:187-190` (table-less overload) build the parts as `s" WHERE $validated > "` and `s" ORDER BY $validated ASC LIMIT $limit"`. #1656 ("quoted identifiers") switched `Repo` (`Repo.scala:106-110`, `pageAfter` at `:172-179`) and `Upsert` (`Upsert.scala:35`, `:58`) to double-quoted identifiers but did not touch these two lines. `SqlIdentifier.validate` (`SqlIdentifier.scala:20-29`) only checks the `[A-Za-z_][A-Za-z0-9_]*` shape, so it does not reject keywords.

**Impact.** Any keyset page over a column whose name is an SQL keyword (`order`, `group`, `limit`, `from`, `to` on some databases) fails at run time on both SQLite and PostgreSQL, although the same column works with every other `Repo`/`Upsert` method. Mixed-case column names behave differently from the rest of the module too: unquoted, PostgreSQL folds them to lower case, while `Repo` keeps the exact name.

**Test gap.** `sql/shared/src/test/scala/zio/blocks/sql/KeysetSpec.scala:33-47` asserts the unquoted strings (`" WHERE id > ? ORDER BY id ASC LIMIT 20"`), so the inconsistency is pinned by the tests rather than caught. `sql/jvm/src/test/scala/zio/blocks/sql/RepoKeysetSpec.scala:145-163` runs `keysetAfter` only on the column `id`. No test uses a keyword column.

**Suggested fix.** Quote the validated column in both overloads (`s""" WHERE "$validated" > """` and `s""" ORDER BY "$validated" ASC LIMIT $limit"""`), update the `KeysetSpec` golden strings, and add a `RepoKeysetSpec` case that pages a table with an `order` column through both `keysetAfter` overloads. Update the `keysetAfter` text in `docs/guides/query-dsl-sql.md` and the caution in `docs/guides/upsert-and-paginate-with-keyset-cursors.md` at the same time.

### sql: a tuple row type reads every repeated column name from the first table, so joins of tables that share `id` return wrong values

Found 2026-10-21 while writing the how-to guide `docs/guides/write-joins-and-inspect-generated-sql.md` (backlog H-19), on `main` at `9447794b`. The code is the same at tag `v0.0.56`: `git diff v0.0.56 main -- sql/` is empty.

**Summary.** A join returns the columns of two tables in one row, and the natural way to read it is a tuple of case classes, `query[(Book, Author)]`. When both tables have a column with the same name, which is the normal case for an `id` primary key, every field with that name is read from the *first* column that has it. Each `Author` then carries its book's ID. Nothing fails: the SQL is correct, the rows have the right length, and the wrong values look like real data. In a left join, the missing side's key comes back as `Some(<other table's id>)` instead of `None`. It happens for `SqlQuery` joins and for hand-written `sql"..."` joins alike, through `query`, `queryOne`, `queryLimit`, `queryStream` and `queryChunked`.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-sql` 0.0.56 + sqlite-jdbc 3.53.4.0, run with `scala-cli run .`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0

import java.nio.file.Files
import zio.blocks.schema.Schema
import zio.blocks.sql.*
import zio.blocks.sql.query.*

case class Author(id: Int, name: String)
object Author { given Schema[Author] = Schema.derived }

case class Book(id: Int, title: String, authorId: Int)
object Book { given Schema[Book] = Schema.derived }

@main def joinDecodesWrongId(): Unit = {
  val tx      = JdbcTransactor.fromUrl(s"jdbc:sqlite:${Files.createTempFile("join", ".db")}", SqlDialect.SQLite)
  val authors = Table.derived[Author]
  val books   = Table.derived[Book]
  tx.connect {
    sql"CREATE TABLE author (id INTEGER PRIMARY KEY, name TEXT NOT NULL)".update
    sql"CREATE TABLE book (id INTEGER PRIMARY KEY, title TEXT NOT NULL, author_id INTEGER NOT NULL)".update
    sql"INSERT INTO author VALUES (1, 'Martin Odersky'), (2, 'John A. De Goes')".update
    sql"INSERT INTO book VALUES (10, 'Zionomicon', 2)".update
  }
  val query = SqlQuery.from(books).join(Rel.manyToOne(books, _.authorId, authors, _.id))
  println(query.sql(SqlDialect.SQLite))
  tx.connect {
    println(query.toFrag(SqlDialect.SQLite).query[(Book, Author)])
    println(sql"SELECT b.id, b.title, b.author_id, a.id, a.name FROM book b JOIN author a ON b.author_id = a.id".query[(Book, Author)])
  }
}

// SELECT t0."id", t0."title", t0."author_id", t1."id", t1."name" FROM "book" AS t0 INNER JOIN "author" AS t1 ON t0."author_id" = t1."id"
// List((Book(10,Zionomicon,2),Author(10,John A. De Goes)))
// List((Book(10,Zionomicon,2),Author(10,John A. De Goes)))
```

The author of Zionomicon has ID 2, but the decoded `Author` has ID 10, the book's ID. With a `LEFT JOIN` from `author` to `book`, an author without books decodes its all-`Option` book row as `(Some(<author id>), None, None, ...)`.

**Expected.** `List((Book(10,Zionomicon,2),Author(2,John A. De Goes)))` for both queries: each tuple element reads its own columns, by position.

**Root cause.**

1. The tuple codec names its columns `_1_id`, `_1_title`, ..., `_2_id`, ... (`sql/shared/src/main/scala/zio/blocks/sql/DbCodecTupleMacro.scala:47-62`), so they never match the result set's labels.
2. `rowDecoder` (`sql/shared/src/main/scala/zio/blocks/sql/Frag.scala:231-240`) only takes the positional path `codec.readValue(r, 1)` (`:235`) when every codec column matches the result label at the same position. Otherwise it calls `selectLabels` (`Frag.scala:213-217`), which replaces each label it can't find with the label at the same position (`:216`). For a join that gives `id, title, author_id, id, name`, with `id` twice.
3. The tuple codec slices those labels per element (`DbCodecTupleMacro.scala:65-76`), and the case-class codec reads by label: `JdbcResultReader.getInt(label)` (`sql/jvm/src/main/scala/zio/blocks/sql/JdbcResultReader.scala:73-74`) calls `ResultSet.getInt(String)`, and JDBC resolves a repeated label to the first column that has it.

**Impact.** Joins are the main use of tuple row types, and an `id` key in every table is the most common naming convention. Every such join silently returns wrong keys, and code that follows those keys (updates, links, caches) then works on the wrong rows. A left join can't tell a missing row from a present one by its key. The only workarounds are to give every column a name that is unique across the joined tables, or to write the join by hand with `AS` aliases.

**Test gap.** `QueryRenderSpec` (`sql/shared/src/test/scala/zio/blocks/sql/query/QueryRenderSpec.scala`) and `ExplainDumpGoldenSpec` only check the rendered SQL; no test runs a join. No test in `sql/jvm/src/test` decodes a tuple whose elements share a column name (`DbCodecOptionSpec` is the only spec that decodes a tuple at all).

**Suggested fix.** When the codec's labels don't all match the result set by name, decode by position instead of mapping to possibly repeated labels: in `rowDecoder`, fall back to `r => codec.readValue(r, 1)` whenever `selectLabels` would produce a repeated label (or whenever the codec is a tuple codec). Better still, make the tuple codec's label-based `readValue` read its elements by position too. Add a `TransactorSpec` case that joins two tables with an `id` column each and decodes `(A, B)`, for `query` and `queryStream`, plus a left-join case with an all-`Option` row.

### sql: `Dump.dumpQuery` writes a `sql"..."` filter without its `?`, and unnamed dumps in one object overwrite each other

Found 2026-10-21 while writing the how-to guide `docs/guides/write-joins-and-inspect-generated-sql.md` (backlog H-19), on `main` at `9447794b`. The code is the same at tag `v0.0.56`.

**Summary.** `Dump.dumpQuery` writes a `SqlQuery`'s SQL to `<dumpDir>/<name>_query-<dialect>.sql` at compile time, for review. Two things make the files wrong without any warning:

1. A filter written with the `sql"..."` interpolator, `where(sql"""t0."year" > ${2010}""")`, is dumped as `WHERE t0."year" > ` with no placeholder. The dumped SQL doesn't parse, and it no longer matches what `explain` prints for the same query. Only filters built with the low-level `Frag(IndexedSeq(...), IndexedSeq(DbValue.DbInt(...)))` constructor, which every test uses, keep their `?`.
2. A `Dump.dumpQuery` call that isn't assigned to a `val` is named after its owner. In an object body that owner is the compiler's local dummy, so the file is called `_local_<Object>__query-<dialect>.sql`, and every other unassigned call in the same object writes the same file: the last one wins. The `sql"..."` interpolator's own dump files (named after the enclosing method) clobber each other the same way.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-sql` 0.0.56, sbt 1.13.0). The property must reach the JVM that runs the compiler, so this uses an sbt build rather than `scala-cli`. `build.sbt`:

```scala
scalaVersion := "3.9.0"
scalacOptions += "-experimental"
libraryDependencies += "dev.zio" %% "zio-blocks-next-sql" % "0.0.56"
```

`src/main/scala/DumpRepro.scala`:

```scala
package dumprepro

import zio.blocks.schema.Schema
import zio.blocks.sql.*
import zio.blocks.sql.query.*

case class Book(id: Int, title: String, year: Int)
object Book { given Schema[Book] = Schema.derived }

object Queries {
  val books = Table.derived[Book]

  inline def recent: SqlQuery[Book] = SqlQuery.from(books).where(sql"""t0."year" > ${2010}""")
  inline def classic: SqlQuery[Book] = SqlQuery.from(books).where(sql"""t0."year" < ${2010}""")
}

object Dumps {
  // Assigned to vals: one file per val
  val recent = Dump.dumpQuery(Queries.recent)

  // Not assigned: both calls write the same file
  Dump.dumpQuery(Queries.recent)
  Dump.dumpQuery(Queries.classic)

  def main(args: Array[String]): Unit = {
    println(Queries.recent.explain(SqlDialect.SQLite))
    println(Queries.classic.explain(SqlDialect.SQLite))
  }
}
```

Run `sbt -Dzib.sql.dumpDir=target/sql-dumps clean compile "runMain dumprepro.Dumps"`, then print every file:

```text
SELECT t0."id", t0."title", t0."year" FROM "book" AS t0 WHERE t0."year" > ?1
-- params: 1:Int
SELECT t0."id", t0."title", t0."year" FROM "book" AS t0 WHERE t0."year" < ?1
-- params: 1:Int
// target/sql-dumps/_local_Dumps__-postgresql.sql
//   t0."year" < ?
// target/sql-dumps/_local_Dumps___query-postgresql.sql
//   SELECT t0."id", t0."title", t0."year" FROM "book" AS t0 WHERE t0."year" < 
// target/sql-dumps/_local_Dumps___query-sqlite.sql
//   SELECT t0."id", t0."title", t0."year" FROM "book" AS t0 WHERE t0."year" < 
// target/sql-dumps/_local_Dumps__-sqlite.sql
//   t0."year" < ?
// target/sql-dumps/main-postgresql.sql
//   t0."year" < ?
// target/sql-dumps/main-sqlite.sql
//   t0."year" < ?
// target/sql-dumps/recent-postgresql.sql
//   t0."year" > ?
// target/sql-dumps/recent_query-postgresql.sql
//   SELECT t0."id", t0."title", t0."year" FROM "book" AS t0 WHERE t0."year" > 
// target/sql-dumps/recent_query-sqlite.sql
//   SELECT t0."id", t0."title", t0."year" FROM "book" AS t0 WHERE t0."year" > 
// target/sql-dumps/recent-sqlite.sql
//   t0."year" > ?
```

The compile prints no warning. `recent_query-*.sql` and `_local_Dumps___query-*.sql` end in `> ` and `< ` with no `?`. The `recent` dump of the unassigned call was overwritten by `classic` (only `<` survives), and `main-*.sql` holds only the second of the two `sql"..."` fragments inlined into `main`.

**Expected.** `WHERE t0."year" > ?`, the same SQL as `explain` without the numbering. Each `Dump.dumpQuery` call writes its own file, or the macro fails (or warns) when two calls would write the same path.

**Root cause.**

1. `decodeFrag` (`sql/shared/src/main/scala/zio/blocks/sql/Dump.scala:408-530`) recognises only the `Frag(...)` constructor and `Frag.literal`. The expansion of the `sql"..."` macro falls through to the "generic fallback" (`:512-528`). It keeps only the string literals that "look like SQL" (`:516-518`), which drops the empty trailing part `""` and with it the boundary where the parameter goes. It counts parameters by looking for `DbValue.DbInt(...)`-style applications (`countParams`, `:435-448`), but the interpolator binds values through `DbParam`, so the count is 0 (`:524`). `tryDecodeIr` then adds `?` only when the count is positive (`:853`).
2. `ownerDerivedName` (`Dump.scala:74-91`) skips synthetic symbols, `<init>` and `$anon`, but not the local dummy that owns statements in a template body, so the name is the sanitized `<local Dumps$>`. `dumpQueryIrImpl` uses that owner whenever the argument's name is generic (`:596-621`), and `emit` (`:35-72`) overwrites an existing file with different content without checking that it came from the same call. `SqlMacros.sqlImpl` repeats the same owner logic (`SqlMacros.scala:47-68` and `:99-120`).

**Impact.** The dump files are meant for code review and DBA review, so a reviewer reads SQL that is not what runs: a filter with a missing operand, or one query's SQL under another query's file. Neither problem produces a compile warning. The second also makes the set of files depend on declaration order.

**Test gap.** `ExplainDumpGoldenSpec` (`sql/jvm/src/test/scala/zio/blocks/sql/ExplainDumpGoldenSpec.scala`) builds every filter with `Frag(IndexedSeq(...), IndexedSeq(DbValue...))` and puts each dumped query in its own fixture object, so it never covers `sql"..."` filters or two dumps in one owner.

**Suggested fix.** Render the dump from the query value instead of re-implementing the renderer on the syntax tree: evaluate the inline query at compile time where possible, or decode the `sql"..."` expansion by its `StringContext` parts (the parts joined with `?` are already available in `SqlMacros.sqlImpl`). Skip the local dummy in `ownerDerivedName` (use its owner class), and fail when two calls in one compilation unit target the same file. Add golden cases for a `where(sql"...")` filter and for two unassigned dumps in one object.

### html: `html"..."` escapes interpolated attribute values twice

Filed as #1764. Found 2026-10-20 while writing T-09. Verified on 0.0.56. `&` renders as `&amp;amp;`, which breaks URLs with query strings.

### html: `selector"..."` never matches in `Dom#select`

Filed as #1765. Found 2026-10-20 while writing T-09. Verified on 0.0.56. The interpolator builds `CssSelector.Raw`, which `select` never matches (`DomSelection.scala:296`; `DomSelectionSpec` asserts this).

### html and schema: `p(...)` is ambiguous on Scala 3 when both packages are imported

Found 2026-10-20 while writing T-09. Verified. Not filed.

**Summary.** `import zio.blocks.html._` together with `import zio.blocks.schema._` makes `p(...)` ambiguous on Scala 3, because schema exports the `p"..."` path interpolator. Any code that renders HTML from schema-backed data with wildcard imports hits this. Options: rename one of them, or move the path interpolator out of the package object.

### schema-csv: `CsvError` row and column count from the start of the failing row, not the document

Found 2026-10-20 while writing the how-to guide `docs/guides/read-and-write-csv-and-tsv-files.md` (backlog H-11), on `main` at `b4179489`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `CsvError#row` is documented as "Row number where the error occurred (1-based)", and `CsvReader.readAll` parses a whole document. But the row in a `ParseError` from `readAll` is counted from the start of the row that failed, so it is almost always 1. A stray character on line 4 of a file is reported as `row 1`; an unclosed quote that opens on line 3 is reported as `row 3`, the number of lines the open cell spans.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-schema-csv` 0.0.56, run with scala-cli):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema-csv:0.0.56

import zio.blocks.schema.csv._

@main def repro(): Unit = {
  // The stray 'x' after the closing quote is on line 4 of the document.
  val stray = "title,year\nZionomicon,2024\nProgramming in Scala,2008\n\"Functional Programming in Scala\"x,2014\n"
  println(CsvReader.readAll(stray, CsvConfig.default))
  // Left(zio.blocks.schema.csv.CsvError$ParseError: CSV error at row 1, column 34: Unexpected character 'x' after closing quote)

  // The unclosed quote opens on line 3 and runs to the end of the document (line 5).
  val unclosed = "title,year\nZionomicon,2024\n\"Programming in Scala,2008\nFunctional Programming in Scala,2014\n"
  println(CsvReader.readAll(unclosed, CsvConfig.default))
  // Left(zio.blocks.schema.csv.CsvError$ParseError: CSV error at row 3, column 1: Unclosed quoted field)
}
```

**Expected.** `row 4, column 34` for the stray character. For the unclosed quote, the line where the quote opens (`row 3, column 1`) or the end of the document (`row 6, column 1`), but counted from the start of the document either way.

**Root cause.** `CsvReader.readRow` starts its counters at `var row = 1` and `var col = 1` on every call (`schema-csv/src/main/scala/zio/blocks/schema/csv/CsvReader.scala:65-66`), and `readAll` calls it once per row (`:237`, `:249`) without adding the number of lines already consumed. `readRow` has no parameter for a starting row, so a caller that loops over `readRow` can't fix the number either, except by counting lines itself and adding `error.row - 1`.

**Impact.** Every parse error from `readAll` points at the wrong line, so on a large file the user has to find the bad row by hand. The reference page `csv.md` promises "the exact row and column where parsing ... failed".

**Test gap.** `CsvReaderSpec` "returns error for malformed data row" (`schema-csv/src/test/scala/zio/blocks/schema/csv/CsvReaderSpec.scala:153`) checks only that `readAll` returns a `Left`, never the row it reports.

**Suggested fix.** Count rows across the document in `readAll`: track the line number of each row's start (one plus the line breaks consumed so far, including those inside quoted fields) and return `ParseError(message, startLine + row - 1, col)`. Optionally add a `readRow` overload that takes the starting line. Add a test that asserts the row and column of an error on line 4.

### schema-csv: a nested case class field is accepted and written as one cell with a line break inside

Found 2026-10-20 while writing the how-to guide `docs/guides/read-and-write-csv-and-tsv-files.md` (backlog H-11), on `main` at `b4179489`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** The `CsvFormat` Scaladoc says it supports "flat case classes (records)", and `csv.md` says "Flat case classes (records with no variant or sequence fields) are fully supported". A record with a field whose type is another case class is not rejected: the deriver derives a record codec for the inner type and uses its whole encoded line, including its `\r\n` terminator, as the value of one cell. The cell is quoted because it contains a comma and a line break, `headerNames` lists one column for it, and spreadsheets show it as a two-line value.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-schema-csv` 0.0.56, run with scala-cli):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
//> using dep dev.zio::zio-blocks-next-schema-csv:0.0.56

import java.nio.CharBuffer
import zio.blocks.schema._
import zio.blocks.schema.csv._

case class Edition(number: Int, year: Int)
object Edition { implicit val schema: Schema[Edition] = Schema.derived }

case class EditionRow(title: String, edition: Edition)
object EditionRow { implicit val schema: Schema[EditionRow] = Schema.derived }

@main def repro(): Unit = {
  val codec  = EditionRow.schema.derive(CsvFormat) // no exception
  println(codec.headerNames)
  // Vector(title, edition)
  val buffer = CharBuffer.allocate(256)
  codec.encode(EditionRow("Programming in Scala", Edition(5, 2021)), buffer)
  buffer.flip()
  val line = buffer.toString
  println(line.replace("\r\n", "\\r\\n"))
  // Programming in Scala,"5,2021\r\n"\r\n
  println(codec.decode(CharBuffer.wrap(line)))
  // Right(EditionRow(Programming in Scala,Edition(5,2021)))
}
```

**Expected.** Either the deriver rejects a nested record with an `UnsupportedOperationException`, like sequences and maps, or it flattens it into separate columns (for example `edition.number,edition.year`) with matching `headerNames`.

**Root cause.** `CsvCodecDeriver.deriveRecord` gets a codec for each field with `D.instance(fieldReflect.metadata)` (`schema-csv/src/main/scala/zio/blocks/schema/csv/CsvCodecDeriver.scala:128`) and never checks whether the field is itself a record. The inner record codec writes a full row with `CsvWriter.writeRow(..., CsvConfig.default)` (`:174`), line terminator included, and the outer codec stores that string as one field. `headerNames` is built from the outer field names only (`:139`).

**Impact.** A user who follows the "flat records" rule by accident (one nested value object, such as an address or a money type) gets no error, and a file whose cells contain line breaks. It round-trips through the same codec, so tests that only round-trip don't notice.

**Test gap.** Neither `CsvRejectionSpec` nor `CsvRecordCodecSpec` derives a codec for a record with a nested record field.

**Suggested fix.** Decide on one behaviour and test it. The smaller change is to reject: in `deriveRecord`, throw `UnsupportedOperationException("CSV does not support nested record fields. Flatten them into the row type.")` when a field's `Reflect` is a record (and not a wrapper). Then update the Scaladoc of `CsvFormat` and the "Record Type Support" section of `csv.md`.

### schema-csv: decode errors never name the column, and `CsvError.TypeError` is never produced

Found 2026-10-20 while writing the how-to guide `docs/guides/read-and-write-csv-and-tsv-files.md` (backlog H-11), on `main` at `b4179489`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** When a cell can't be converted to its field's type, `CsvCodec#decode` returns a `SchemaError` with an empty path (`at: .`), so the message names the bad value but not the column. `CsvError.TypeError`, which has a `fieldName`, a `row` and a `column` for exactly this case, is defined but no code in the module constructs it.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-schema-csv` 0.0.56, run with scala-cli):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
//> using dep dev.zio::zio-blocks-next-schema-csv:0.0.56

import java.nio.CharBuffer
import zio.blocks.schema._
import zio.blocks.schema.csv._

case class BookRow(title: String, year: Int, copies: Int)
object BookRow { implicit val schema: Schema[BookRow] = Schema.derived }

@main def repro(): Unit = {
  val codec = BookRow.schema.derive(CsvFormat)
  // Both Int columns get a bad value; the error does not say which column failed.
  println(codec.decode(CharBuffer.wrap("Zionomicon,MMXXIV,12")))
  // Left(zio.blocks.schema.SchemaError: Cannot parse 'MMXXIV' as Int: For input string: "MMXXIV" at: .)
  println(codec.decode(CharBuffer.wrap("Zionomicon,2024,twelve")))
  // Left(zio.blocks.schema.SchemaError: Cannot parse 'twelve' as Int: For input string: "twelve" at: .)
  codec.decode(CharBuffer.wrap("Zionomicon,2024,twelve")).left.foreach(e => println(e.errors.head.source.nodes))
  // ArraySeq()
}
```

**Expected.** The path names the field, as the JSON codec does: `... at: .copies`.

**Root cause.** The record decoder returns the field codec's error unchanged: `case Left(err) => return new Left(err)` (`schema-csv/src/main/scala/zio/blocks/schema/csv/CsvCodecDeriver.scala:194`). The primitive codecs build their errors with an empty path (`SchemaError.expectationMismatch(Nil, ...)` in `typeError`, `:309-310`). `CsvError.TypeError` (`schema-csv/src/main/scala/zio/blocks/schema/csv/CsvError.scala:63`) is only constructed in `CsvErrorSpec`.

**Impact.** On a file with several numeric columns, the user can't tell which column held the bad value. `csv.md` says "CSV errors report the exact row and column where parsing or type conversion failed" and describes `TypeError` as the error for a failed conversion, so readers write `case CsvError.TypeError(...)` handlers that never match.

**Test gap.** `CsvPrimitiveCodecSpec` checks only `isLeft` for a bad cell (for example `:77`), and `CsvRecordCodecSpec` never decodes a record with a bad cell, so nothing asserts which field an error names.

**Suggested fix.** In the record decoder, return `new Left(err.atField(fieldNames(idx)))`; `SchemaError#atField` already exists for this. Either remove `CsvError.TypeError` or document it as reserved, and fix the `csv.md` text.

### schema-csv: `CsvConfig.nullValue` is never read

Found 2026-10-20 while writing the how-to guide `docs/guides/read-and-write-csv-and-tsv-files.md` (backlog H-11), on `main` at `b4179489`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `CsvConfig` has a `nullValue` parameter, documented as "The string representation for null values (default: "")", and `csv.md` lists it and sets it to `"NULL"` in an example. No code in `schema-csv` reads it: neither `CsvReader`, `CsvWriter` nor the derived codecs.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-schema-csv` 0.0.56, run with scala-cli):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema-csv:0.0.56

import zio.blocks.schema.csv._

@main def repro(): Unit = {
  val config = CsvConfig(nullValue = "NULL")
  // nullValue = "NULL" does not turn the NULL cell into anything else.
  println(CsvReader.readAll("title,isbn\nZionomicon,NULL\nScala with Cats,\n", config))
  // Right((Vector(title, isbn),Vector(Vector(Zionomicon, NULL), Vector(Scala with Cats, ))))
}
```

**Root cause.** `nullValue` is declared at `schema-csv/src/main/scala/zio/blocks/schema/csv/CsvConfig.scala:40` and used nowhere else (`grep -rn nullValue schema-csv/src/main` finds only `CsvConfig.scala`). The derived codecs reject `Option` fields, so there is no value that could map to it.

**Impact.** Low. A user who sets `nullValue` expects some effect and gets none, silently.

**Test gap.** `CsvConfigSpec` only checks the field's default value.

**Suggested fix.** Remove `nullValue`, or give it a meaning once `Option` fields are supported (write `None` as `nullValue`, read `nullValue` as `None`). Until then, document it as unused.

### schema-csv: a failing `Schema#transform` check makes `CsvCodec#decode` throw instead of returning `Left`

Found 2026-10-21 while writing the how-to guide `docs/guides/validated-newtypes.md` (backlog H-04), on `main` at `f87e6beb`. The code is the same at tag `v0.0.56`. Verified. Not filed.

**Summary.** A newtype whose schema is built with `Schema#transform` signals a failed check by throwing (`SchemaError` is the documented choice). The JSON, TOON and Avro codecs catch that exception and return `Left` with the field path. The derived CSV codec does not: `CsvCodec#decode` throws the `SchemaError` out of a method whose return type is `Either[SchemaError, A]`. The same `Schema[Book]` is safe to decode from JSON and crashes the caller when decoding CSV.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-schema-csv` 0.0.56, run with `scala-cli run CsvWrapperThrows.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema-csv:0.0.56

import zio.blocks.schema._
import zio.blocks.schema.csv._
import java.nio.CharBuffer
import scala.util.Try

final case class Isbn private (value: String)
object Isbn {
  def parse(s: String): Either[SchemaError, Isbn] =
    if (s.length == 13 && s.forall(_.isDigit)) Right(new Isbn(s))
    else Left(SchemaError.validationFailed(s"ISBN must have 13 digits, got '$s'"))
  implicit val schema: Schema[Isbn] = Schema[String].transform(s => parse(s).fold(e => throw e, identity), _.value)
}
final case class Book(isbn: Isbn, title: String)
object Book { implicit val schema: Schema[Book] = Schema.derived }

@main def repro(): Unit = {
  val codec = Book.schema.derive(CsvFormat)
  println(codec.decode(CharBuffer.wrap("9781617290657,Functional Programming in Scala\n")))
  // Right(Book(Isbn(9781617290657),Functional Programming in Scala))

  println(Try(codec.decode(CharBuffer.wrap("978-ZIO,Zionomicon\n"))))
  // Failure(zio.blocks.schema.SchemaError: ISBN must have 13 digits, got '978-ZIO')

  // The JSON codec derived from the same schema returns Left:
  println(Book.schema.jsonCodec.decode("""{"isbn":"978-ZIO","title":"Zionomicon"}"""))
  // Left(zio.blocks.schema.SchemaError: ISBN must have 13 digits, got '978-ZIO' at: .isbn.wrapped)
}
```

**Expected.** `codec.decode` returns `Left(SchemaError)` for the bad row, with the field in the path, as the JSON codec does.

**Root cause.** The wrapper codec in `schema-csv/src/main/scala/zio/blocks/schema/csv/CsvCodecDeriver.scala:271-275` calls `wrap(b)` outside any `try`: `case Right(b) => new Right(wrap(b))`. The JSON deriver wraps the same call in `try ... catch { case err if NonFatal(err) => wrappedError(err) }` (`schema/shared/src/main/scala/zio/blocks/schema/json/JsonCodecDeriver.scala:2914-2918`), and so do TOON (`ToonCodecDeriver.scala:1220`) and Avro (`AvroCodecDeriver.scala:768`).

**Impact.** Any CSV import of a model with validated newtypes crashes on the first bad row instead of reporting it, so one bad line in an uploaded file becomes an uncaught exception (a 500 instead of a 400). The guide tells readers to wrap CSV decoding in `Try` until this is fixed.

**Test gap.** `schema-csv/src/test/scala/zio/blocks/schema/csv/CsvWrapperCodecSpec.scala` covers only `AnyVal` wrappers whose `wrap` cannot fail (round trips and `headerNames`). No test decodes a value that a wrapper rejects.

**Suggested fix.** Catch `NonFatal` around `wrap(b)` and return `Left(SchemaError)` with the wrapper step in the path, matching `JsonCodecDeriver`. Add a `CsvWrapperCodecSpec` case with a validating `Schema#transform`.

### schema: `Into.derived` reports every field error at the root path, so callers cannot tell which field failed

Found 2026-10-21 while writing the how-to guide `docs/guides/validated-newtypes.md` (backlog H-04), on `main` at `f87e6beb`. The code is the same at tag `v0.0.56`. Verified on Scala 2.13.18 and 3.9.0. Not filed.

**Summary.** `Into.derived[Raw, Valid]` collects one error per failing field, which is what makes it attractive for validating a request DTO. But every collected `SchemaError.Single` has `source` = `.` (the root). The field name appears only inside the message text (`converting field BookSubmission.isbn to Book.isbn failed`). Code that builds a per-field error response, or highlights fields in a form, has to parse that English text. `Schema#fromDynamicValue` on the same data reports `.isbn`, `.price` and so on.

**Reproduction** (Verified; Scala 2.13.18, `zio-blocks-schema` 0.0.56, run with `scala-cli run IntoRootPath.scala`; Scala 3.9.0 gives the same paths, with the errors in reverse order):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56

import zio.blocks.schema._

final case class Isbn private (value: String)
object Isbn {
  def parse(s: String): Either[SchemaError, Isbn] =
    if (s.length == 13 && s.forall(_.isDigit)) Right(new Isbn(s))
    else Left(SchemaError.validationFailed(s"ISBN must have 13 digits, got '$s'"))
}
final case class Price private (amount: BigDecimal)
object Price {
  def parse(a: BigDecimal): Either[SchemaError, Price] =
    if (a > 0) Right(new Price(a)) else Left(SchemaError.validationFailed(s"price must be positive, got $a"))
}
final case class BookSubmission(isbn: String, price: BigDecimal)
final case class Book(isbn: Isbn, price: Price)

object Repro extends App {
  implicit val isbnInto: Into[String, Isbn]       = (s: String) => Isbn.parse(s)
  implicit val priceInto: Into[BigDecimal, Price] = (a: BigDecimal) => Price.parse(a)

  Into.derived[BookSubmission, Book].into(BookSubmission("978-ZIO", BigDecimal(-45))).left.foreach { err =>
    err.errors.foreach(e => println(s"source=${e.source.toScalaString} | ${e.message.replace("\n", " / ")}"))
  }
  // source=. | converting field BookSubmission.isbn to Book.isbn failed /   Caused by: ISBN must have 13 digits, got '978-ZIO'
  // source=. | converting field BookSubmission.price to Book.price failed /   Caused by: price must be positive, got -45
}
```

**Expected.** Each error's `source` is the target field (`.isbn`, `.price`), as with `Schema#fromDynamicValue`, so `SchemaError#errors` can be mapped to fields without parsing messages.

**Root cause.** The macros wrap each field failure with `SchemaError.conversionFailed(contextMessage, cause)` (`schema/shared/src/main/scala-2/zio/blocks/schema/IntoVersionSpecific.scala:721` and `:1166`; `scala-3/.../IntoVersionSpecific.scala:1516`), and that overload always uses `DynamicOptic.root` (`schema/shared/src/main/scala/zio/blocks/schema/SchemaError.scala:60-70`). The field is known at that point but only goes into the message.

**Impact.** The guide steers readers to `Schema#fromDynamicValue` for per-field errors and mentions `Into` only in a tip. The Scala 2 and Scala 3 macros also report the errors in opposite orders (declaration order on 2.13, reverse on 3.9), so output that a test pins on one version fails on the other.

**Test gap.** `schema-examples/src/main/scala/into/IntoErrorAccumulationExample.scala` prints the messages but never looks at `source`; no spec asserts the path of an accumulated field error.

**Suggested fix.** Put the target field in the path (for example `SchemaError.conversionFailed(contextMessage, cause).atField(fieldName)`, or a `conversionFailed` overload that takes the trace), and make both macros accumulate in declaration order.

**Also: a `ConversionFailed` with a cause never shows its path.** Found 2026-10-21 while writing the Scaladoc of `SchemaError` (wave 1 of the schema Scaladoc, branch `scaladoc-wave-1` at `b0956ec6`); the code is the same at tag `v0.0.56`. Verified with `scala-cli run --server=false CausePath.scala`. `ConversionFailed#message` appends ` at: <path>` only when `cause` is `None`; with a cause it prints `details` and the cause's messages and drops `source`. So the suggested `.atField(fieldName)` would fix `source`, but the rendered message would still not name the field.

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56

import zio.blocks.schema._

object Repro extends App {
  val withCause = SchemaError.conversionFailed("Book is invalid", SchemaError("title must not be blank")).atField("book")
  println(withCause.errors.head.source.toScalaString)
  // .book
  println(withCause.message)
  // Book is invalid
  //   Caused by: title must not be blank

  val withoutCause = SchemaError.conversionFailed(Nil, "Book is invalid").atField("book")
  println(withoutCause.message)
  // Book is invalid at: .book
}
```

Expected: the first line of the message with a cause also ends with ` at: .book` when the path is not the root. Root cause: `schema/shared/src/main/scala/zio/blocks/schema/SchemaError.scala:255-273` (the `cause match` in `ConversionFailed#message`); the path is appended only in the `case _` branch at `:270-272`. Impact: errors from `Into.derived` and other wrapped conversions never show where they happened, even after the suggested `.atField` fix. Test gap: no spec renders a `ConversionFailed` that has both a cause and a non-root path. Fix: append ` at: ${source.toScalaString}` to `details` in the `Some(causeErr)` branch when `source.nodes` is not empty, as the `None` branch does. The `SchemaError` Scaladoc describes the current behaviour.

### schema (Scala 3): `Into.derived` to an opaque type fails in the default package, ignores a `given Into`, and puts the error's class name in the message

Found 2026-10-21 while writing the how-to guide `docs/guides/validated-newtypes.md` (backlog H-04), on `main` at `f87e6beb`. The code is the same at tag `v0.0.56`. Verified. Not filed.

**Summary.** For a field whose target type is an opaque type, `Into.derived` looks for an `apply` (returning `Either`) or `unsafe` method in the opaque type's companion. Three things go wrong. (1) When the opaque type is declared in the default package (scala-cli scripts, worksheets, mdoc pages), the companion is not found and compilation fails with "no 'apply' or 'unsafe' method found in companion object", although the method is there; the same code inside `package shop` compiles. (2) A `given Into[String, Title]` in the companion is never consulted for an opaque target, although case-class targets use such instances; the same compile error results. (3) When `apply` returns `Either[SchemaError, Isbn]`, the error text is built with string interpolation, so the message reads `Validation failed for field 'isbn': zio.blocks.schema.SchemaError: Invalid ISBN: 978-ZIO`.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-schema` 0.0.56, three files run with `scala-cli run <file>`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56

// (1) OpaqueIntoDefaultPackage.scala: no package clause
import zio.blocks.schema.*

object Domain {
  opaque type Isbn = String
  object Isbn {
    def apply(s: String): Either[String, Isbn] =
      if (s.length == 13) Right(s) else Left(s"Invalid ISBN: $s")
  }
}
import Domain.*

final case class BookSubmission(isbn: String)
final case class Book(isbn: Isbn)

@main def repro(): Unit =
  println(Into.derived[BookSubmission, Book].into(BookSubmission("978-ZIO")))
// [error] ./OpaqueIntoDefaultPackage.scala:21:11
// [error] Cannot convert to opaque type Domain.Isbn: no 'apply' or 'unsafe' method found in companion object
```

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56

// (2) OpaqueIntoGiven.scala
package shop

import zio.blocks.schema.*

object Domain {
  opaque type Title = String
  object Title {
    def parse(s: String): Either[SchemaError, Title] =
      if (s.trim.nonEmpty) Right(s.trim) else Left(SchemaError.validationFailed("title must not be blank"))
    given Into[String, Title] = (s: String) => parse(s)
  }
}
import Domain.*

final case class TitledSubmission(title: String)
final case class Titled(title: Title)

@main def repro(): Unit =
  println(Into.derived[TitledSubmission, Titled].into(TitledSubmission("  ")))
// [error] ./OpaqueIntoGiven.scala:23:11
// [error] Cannot convert to opaque type shop.Domain.Title: no 'apply' or 'unsafe' method found in companion object
```

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56

// (3) OpaqueIntoPackage.scala: same as (1), but in a package and with a SchemaError error type
package shop

import zio.blocks.schema.*

object Domain {
  opaque type Isbn = String
  object Isbn {
    def apply(s: String): Either[SchemaError, Isbn] =
      if (s.length == 13) Right(s) else Left(SchemaError.validationFailed(s"Invalid ISBN: $s"))
  }
}
import Domain.*

final case class BookSubmission(isbn: String)
final case class Book(isbn: Isbn)

@main def repro(): Unit =
  println(Into.derived[BookSubmission, Book].into(BookSubmission("978-ZIO")))
// Left(zio.blocks.schema.SchemaError: converting field BookSubmission.isbn to Book.isbn failed
//   Caused by: Validation failed for field 'isbn': zio.blocks.schema.SchemaError: Invalid ISBN: 978-ZIO)
```

**Expected.** (1) compiles and prints the same `Left` as the packaged version. (2) uses the `given Into[String, Title]`, as for a case-class target. (3) keeps the `SchemaError` as the cause (`Caused by: Invalid ISBN: 978-ZIO`) instead of its `toString`.

**Root cause.** All in `schema/shared/src/main/scala-3/zio/blocks/schema/IntoVersionSpecific.scala` at `v0.0.56`. The opaque path (`:2355-2391`) tries only `findOpaqueValidationMethod` (`:2222-2256`, an `apply` returning `Either`) and `findOpaqueUnsafeMethod`, then aborts with `opaqueTypeNoCompanionError`; it never searches for an implicit `Into`, which explains (2). The companion lookup is `getOpaqueCompanion` (`:2279-2335`), which falls back to building a module path from `owner.fullName` when `companionModule` is empty; in the default package that lookup fails while in a named package it succeeds. Which step fails was not traced further (from behaviour). (3) comes from `:2372`, `SchemaError.conversionFailed(Nil, s"Validation failed for field '...': $err")`, which interpolates the error value.

**Impact.** Scala 3 users who model newtypes as opaque types cannot reuse their smart constructors through `Into` the way case-class users can, and every script or doc page in the default package fails to compile. The guide shows opaque types only with `Schema#transform` and `Schema#fromDynamicValue`, which work.

**Test gap.** `schema/shared/src/test/scala-3/zio/blocks/schema/IntoVersionSpecificSpec.scala` defines its opaque types (`OpaqueTypeDefs`, `OpaqueAge`) in `package zio.blocks.schema`, always with both `apply: Either[String, _]` and `unsafe`. No test uses the default package, a `given Into`, or a `SchemaError` error type.

**Suggested fix.** Before the `apply`/`unsafe` search, look for an implicit `Into[Source, Opaque]` (as the case-class path does). Find the companion through `tpe.typeSymbol.companionModule` or the owner's declarations first, and handle the empty package in the `fullName` fallback. When the error type is `SchemaError`, pass it as the cause instead of interpolating it.

### schema: `PrimitiveValue.Int#compare` overflows, so `Int` values far apart compare the wrong way round

Found 2026-10-21 while writing the Scaladoc of `PrimitiveValue` (schema Scaladoc wave 2, branch `scaladoc-wave-2`, on top of `5fd81ef4`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run Repro.scala` against the published `0.0.56`.

**Summary.** `PrimitiveValue.Int#compare` returns `value - x.value`. The subtraction overflows when the two values are more than `Int.MaxValue` apart, so `>`, `<`, `PrimitiveValue.ordering` and `DynamicValue#compare` give the wrong answer: `Int(Int.MaxValue) > Int(-1)` is `false`, and the maximum of `0`, `Int.MaxValue` and `-1` is `-1`.

**Reproduction** (Scala 3.3.7, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

@main def run(): Unit = {
  val max   = PrimitiveValue.Int(Int.MaxValue)
  val minus = PrimitiveValue.Int(-1)
  println(max.compare(minus))
  println(max > minus)
  println(List[PrimitiveValue](PrimitiveValue.Int(0), max, minus).max)
  println(DynamicValue.Primitive(max) > DynamicValue.Primitive(minus))
}
// -2147483648
// false
// Int(-1)
// false
```

**Expected.** A positive number, `true`, `Int(2147483647)` and `true`.

**Root cause.** `case x: Int => value - x.value` (`schema/shared/src/main/scala/zio/blocks/schema/PrimitiveValue.scala:167`). The `Byte` and `Short` cases subtract too, but their values widen to `Int` first, so they cannot overflow; `Long` and the other cases use `compare` or `compareTo`. `DynamicValue.Primitive#compare` delegates to it (`DynamicValue.scala:523`).

**Impact.** Sorting, `min`/`max` and comparisons of `PrimitiveValue`s or `DynamicValue`s that hold `Int`s can be wrong, and the ordering is not transitive. The Scaladoc of `PrimitiveValue` and `PrimitiveValue.Int` now states the overflow.

**Test gap.** `PrimitiveValueSpec` compares only `Int(1)` with `Int(0)` (`schema/shared/src/test/scala/zio/blocks/schema/PrimitiveValueSpec.scala:76-78`).

**Suggested fix.** Use `java.lang.Integer.compare(value, x.value)`, and add a test with `Int.MaxValue` and `Int.MinValue`.

### schema: `PrimitiveType#fromDynamicValue` accepts lossy conversions between numeric types and `Char`

Found 2026-10-21 while writing the Scaladoc of `PrimitiveType` (schema Scaladoc wave 2, branch `scaladoc-wave-2`, on top of `5fd81ef4`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run Repro.scala` against the published `0.0.56`. The four `Char` cases were added after review 1 of wave 2.

**Summary.** The numeric `PrimitiveType`s and `Char` accept a value of another numeric type or `Char` only when converting it there and back gives the same value. Because the JVM conversions saturate or wrap, that check passes for some values that change:

- From floating point to `Int` and `Long`, the conversion saturates: a `Float` of `2^31` decodes as `Int.MaxValue`, a `Float` or `Double` of `2^63` as `Long.MaxValue`.
- `Int.MaxValue` and `Long.MaxValue` round up when converted to `Float` or `Double`: `Int.MaxValue` decodes as the `Float` `2^31`, `Long.MaxValue` as the `Float` or `Double` `2^63`.
- `Char` is unsigned and `Byte`/`Short` are signed, and the conversions between them wrap: a `Char` of 65408 or more decodes as a negative `Byte`, a `Char` of 32768 or more as a negative `Short`, and a negative `Byte` or `Short` as a `Char` of 65408 or 32768 or more (`-1` as `Char.MaxValue`, 65535).

A `Double` of `2^31` is rejected, as it should be, because `Int.MaxValue` is exact in a `Double`, and so is an `Int` of `-1` for `Char`, because `(-1).toChar.toInt` is 65535.

**Reproduction** (Scala 3.3.7, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

case class Counter(hits: Long)
object Counter {
  implicit val schema: Schema[Counter] = Schema.derived
}

@main def run(): Unit = {
  def prim(pv: PrimitiveValue) = DynamicValue.Primitive(pv)
  val int   = PrimitiveType.Int(Validation.None)
  val long  = PrimitiveType.Long(Validation.None)
  val float = PrimitiveType.Float(Validation.None)
  val dbl   = PrimitiveType.Double(Validation.None)
  val byte  = PrimitiveType.Byte(Validation.None)
  val short = PrimitiveType.Short(Validation.None)
  val char  = PrimitiveType.Char(Validation.None)
  println(int.fromDynamicValue(prim(PrimitiveValue.Float(2147483648.0f))))
  println(long.fromDynamicValue(prim(PrimitiveValue.Double(9.223372036854775808e18))))
  println(float.fromDynamicValue(prim(PrimitiveValue.Int(Int.MaxValue))))
  println(dbl.fromDynamicValue(prim(PrimitiveValue.Long(Long.MaxValue))))
  println(byte.fromDynamicValue(prim(PrimitiveValue.Char(65535.toChar))))
  println(short.fromDynamicValue(prim(PrimitiveValue.Char(32768.toChar))))
  println(char.fromDynamicValue(prim(PrimitiveValue.Byte(-1))).map(_.toInt))
  println(char.fromDynamicValue(prim(PrimitiveValue.Short(-1))).map(_.toInt))
  println(int.fromDynamicValue(prim(PrimitiveValue.Double(2147483648.0))))
  println(char.fromDynamicValue(prim(PrimitiveValue.Int(-1))))
  println(Counter.schema.fromDynamicValue(DynamicValue.Record("hits" -> prim(PrimitiveValue.Double(1e19)))))
  println(Counter.schema.fromDynamicValue(DynamicValue.Record("hits" -> prim(PrimitiveValue.Double(9.223372036854775808e18)))))
}
// Right(2147483647)
// Right(9223372036854775807)
// Right(2.1474836E9)
// Right(9.223372036854776E18)
// Right(-1)
// Right(-32768)
// Right(65535)
// Right(65535)
// Left(zio.blocks.schema.SchemaError: Expected Int at: .)
// Left(zio.blocks.schema.SchemaError: Expected Char at: .)
// Left(zio.blocks.schema.SchemaError: Expected Long at: .hits)
// Right(Counter(9223372036854775807))
```

**Expected.** The first eight conversions return `Left(... Expected Int/Long/Float/Double/Byte/Short/Char ...)`, like the ninth and tenth, and the last line fails like the one before it: `2^63` is outside the `Long` range, `2^31` outside the `Int` range, 65535 and 32768 outside the `Byte` and `Short` ranges, and `-1` outside the `Char` range.

**Root cause.** In `schema/shared/src/main/scala/zio/blocks/schema/PrimitiveType.scala`:

- `Byte` from `Char` checks `ch.toByte.toChar == ch` (`:182`), and `Short` from `Char` checks `ch.toShort.toChar == ch` (`:223`), which holds for every `Char`.
- `Int` from `Float` checks `f.toInt.toFloat == f` (`:266`).
- `Long` from `Float` and `Double` check `f.toLong.toFloat == f` and `d.toLong.toDouble == d` (`:308-309`).
- `Float` from `Int` and `Long` check `i.toFloat.toInt == i` and `l.toFloat.toLong == l` (`:348`, `:350`).
- `Double` from `Long` checks `l.toDouble.toLong == l` (`:391`).
- `Char` from `Byte` and `Short` check `b.toChar.toByte == b` and `s.toChar.toShort == s` (`:424-425`), which holds for every `Short`.

`2^31` saturates to `Int.MaxValue`, which converts back to `2^31` as a `Float`; `2^63` saturates to `Long.MaxValue`, which converts back to `2^63` as a `Float` or `Double`; the 16-bit `Char` and `Short` conversions keep the bits and change only the sign.

**Impact.** `Schema#fromDynamicValue` and every other path through `Reflect#fromDynamicValue` silently change such values instead of rejecting them, for example when a `DynamicValue` built from another source holds a floating-point number for a `Long` field, or a negative `Short` for a `Char` field. The Scaladoc of `PrimitiveType#fromDynamicValue` now lists these cases.

**Test gap.** The out-of-range tests in `PrimitiveTypeSpec` use values far outside the range or with a fraction, such as `Long(3000000000L)` and `Float(1.5f)` (`schema/shared/src/test/scala/zio/blocks/schema/PrimitiveTypeSpec.scala:798-923`); none is at the limit, and none converts a `Char` to `Byte` or `Short` or a negative `Byte` or `Short` to `Char`.

**Suggested fix.** Compare in an exact type, for example `scala.BigDecimal(f) == scala.BigDecimal(f.toInt)` and `ch.toInt == ch.toByte.toInt`, or check the range before converting (`f >= -2^31 && f < 2^31`, `b >= 0` for `Char`). Add tests with `2^31`, `2^63`, `Int.MaxValue`, `Long.MaxValue`, `Char.MaxValue` and `-1`.

### schema: `Reflect#toString` prints a shared node that is not recursive as `deferred => Name`

Found 2026-10-21 while writing the Scaladoc of `Reflect` and `ReflectPrinter` (schema Scaladoc wave 2, branch `scaladoc-wave-2`, on top of `5fd81ef4`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run Repro.scala` against the published `0.0.56`.

**Summary.** `ReflectPrinter` prints `deferred => Name` instead of a record, variant, sequence, map or wrapper node it has already printed, to stop at recursive references. It remembers every such node until the whole `toString` call ends, so when two fields share one of them, such as the schema of a case class used for both, the second field is printed as `deferred => Author` although nothing is recursive. Primitive and dynamic nodes are never remembered and always print in full.

**Reproduction** (Scala 3.3.7, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

case class Author(name: String)
object Author {
  implicit val schema: Schema[Author] = Schema.derived
}

case class Book(title: String, author: Author, editor: Author)
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

@main def run(): Unit =
  println(Book.schema.reflect)
// record Book {
//   title: String
//   author: record Author {
//     name: String
//   }
//   editor: deferred => Author
// }
```

**Expected.** `editor` is printed in full, like `author`.

**Root cause.** `printRecord`, `printVariant`, `printSequence`, `printMap` and `printWrapper` add their node to the shared `visited` set and never remove it (`schema/shared/src/main/scala/zio/blocks/schema/ReflectPrinter.scala:55`, `:84`, `:106`, `:135`, `:170`), and `printReflect` prints `deferred => ...` for any node in the set (`:243`). Primitive and dynamic nodes are printed without being added (`:246`, `:254`). `Reflect.Deferred#toString` does it the other way, removing the node in a `finally` once it is printed (`Reflect.scala:2120`).

**Impact.** The `toString` of a schema whose record or collection types are used more than once, which is common with shared address, money or ID types, hides their structure and suggests recursion that is not there. `docs/reference/schema/reflect.md` (Debug-Friendly toString) and the `Reflect` and `ReflectPrinter` Scaladoc now describe this behaviour.

**Test gap.** `ReflectToStringSpec` has nested and recursive records, but no record that uses the same node twice (`schema/shared/src/test/scala/zio/blocks/schema/tostring/ReflectToStringSpec.scala:97`, `:282`, `:292`).

**Suggested fix.** Treat `visited` as the set of nodes on the current path: remove the node after printing its children, as `Deferred#toString` does.

### schema: a forced `Lazy` runs again when it is part of another `Lazy`'s chain

Found 2026-10-21 while writing the Scaladoc of `Lazy` (schema Scaladoc wave 2, branch `scaladoc-wave-2`, on top of `5fd81ef4`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run Repro.scala` against the published `0.0.56`.

**Summary.** This is a trap in the trampolined design of `Lazy`, not a broken promise: forcing the same instance twice runs it once, as `docs/reference/schema/lazy.md:24` says. `Lazy#force` keeps its cache in the instance it is called on, and the trampoline that runs a chain does not read the caches of the instances inside it. So when a `Lazy` that was already forced is the start of a `map` or `flatMap` chain, forcing the chain runs its computation again.

**Reproduction** (Scala 3.3.7, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema.Lazy

@main def run(): Unit = {
  var runs = 0
  val title = Lazy { runs += 1; "Zionomicon" }
  title.force
  title.force
  println(s"after two forces: $runs")
  title.map(_.toUpperCase).force
  println(s"after map(...).force: $runs")
  title.flatMap(t => Lazy(t.length)).force
  println(s"after flatMap(...).force: $runs")
}
// after two forces: 1
// after map(...).force: 2
// after flatMap(...).force: 3
```

**Expected.** A reader who knows that `force` caches would expect `1` on all three lines, with the chains reusing the cached `"Zionomicon"`.

**Root cause.** `force` checks the cache only of the instance it is called on (`schema/shared/src/main/scala/zio/blocks/schema/Lazy.scala:123`). Its loop walks into `FlatMap#first` and calls `Defer#thunk()` directly (`:110-120`), without looking at the cached `value` or `error` of those inner instances.

**Impact.** Work is repeated, and side effects run more than once, whenever a forced `Lazy` is reused in a chain. The JSON codec deriver builds the instance of a node from the instances of its children with `D.instance(...).map` and `.zip` (`schema/shared/src/main/scala/zio/blocks/schema/json/JsonCodecDeriver.scala:933`, `:1437`, `:2788`, `:2908`), so a child instance that was forced elsewhere would run again; this was not observed in practice. The Scaladoc of `Lazy` now states that the cache belongs to the instance. A related detail, documented but not a bug: `Lazy.collectAll` forces its elements from the last to the first.

**Test gap.** `LazySpec` checks memoization only by forcing the same instance twice (`schema/shared/src/test/scala/zio/blocks/schema/LazySpec.scala:47`).

**Suggested fix.** In the loop, when the current node `isEvaluated`, use its cached value (or rethrow its cached error) instead of running it.

### schema: `Reflect#transform` leaves entries in a per-thread cache, after a direct call or when a result's `Deferred` nodes are first used

Found 2026-10-21 while writing the Scaladoc of `Reflect#transform` (schema Scaladoc wave 2, branch `scaladoc-wave-2`, on top of `5fd81ef4`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run Repro.scala` against the published `0.0.56`. The case of a scoped call was added after review 1 of wave 2.

**Summary.** `Reflect.Deferred#transform` caches its result in a thread-local map, keyed by the node and the transformer, so that transforming a recursive type terminates. `Reflect#noBinding`, `DynamicSchema#rebind` and `DerivationBuilder#derive` wrap their call in the `private[schema]` `Reflect.withTransformCache`, which clears the map when they return. Two cases leave entries in it:

- `Reflect#transform` is public, but a caller outside the library cannot use that scope, so a direct call leaves its entries in the map.
- The scoped calls force only the root of the result. The `Deferred` nodes in the result transform their value when they are first used, after the scope has closed, and that transformation adds entries again.

The entries keep the transformer and the transformed nodes alive until the same thread runs one of the scoped calls.

**Reproduction** (Scala 3.3.7, `zio-blocks-schema` 0.0.56; the file is in package `zio.blocks.schema` only to read the `private[schema]` map):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema:0.0.56
// Declared in this package only to read the private[schema] cache.
package zio.blocks.schema

case class Tree(value: Int, children: List[Tree])
object Tree {
  implicit val schema: Schema[Tree] = Schema.derived
}

@main def run(): Unit = {
  println(s"before: ${Reflect.transformCache.get.size}")
  Tree.schema.reflect.transform(DynamicOptic.root, ReflectTransformer.noBinding()).force
  println(s"after a direct transform: ${Reflect.transformCache.get.size}")
  val nb = Tree.schema.reflect.noBinding
  println(s"after noBinding: ${Reflect.transformCache.get.size}")
  println(nb.asRecord.get.fields(1).value.isSequence)
  println(s"after using its children field: ${Reflect.transformCache.get.size}")
}
// before: 0
// after a direct transform: 1
// after noBinding: 0
// true
// after using its children field: 1
```

**Expected.** `0` after the direct transform and after using the `children` field too.

**Root cause.** `Deferred#transform` puts its result in `Reflect.transformCache` (`schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala:1893-1904`, map at `:2154`); only `withTransformCache` (`:2175`) removes it, and it is `private[schema]`. The result is a new `Deferred` whose thunk runs `value.transform(path, f).force` (`:1900`), so the nested `Deferred` nodes reach the cache only when that thunk runs. `noBinding` (`:502-503`) and `DynamicSchema#rebind` (`DynamicSchema.scala:271`) force only the root inside the scope.

**Impact.** A long-lived thread, such as a server worker, that calls `transform` directly with a new transformer per request, or that walks into the `Deferred` nodes of a `noBinding` or `rebind` result, keeps every transformer and transformed tree until a scoped call happens to run on it. The Scaladoc of `Reflect#transform` now describes both cases.

**Test gap.** No test checks the cache after a `transform`; `withTransformCache` and `transformCache` do not appear in `schema/shared/src/test`.

**Suggested fix.** Do not share the cache through a thread-local at all: pass the memo map down the `transform` calls, capturing it in the thunk of each `Deferred` result, so that it lives as long as the result and no longer. Making `withTransformCache` public would fix only the direct call.

### schema: `DynamicSchema#rebind` returns normally when a binding behind a `Deferred` node is missing, and throws later

Found 2026-10-21 while writing the Scaladoc of `RebindException` and `RebindTransformer` (schema Scaladoc wave 2, branch `scaladoc-wave-2`, on top of `5fd81ef4`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run Repro.scala` against the published `0.0.56`. Found by review 1 of wave 2 and re-run by the implementer.

**Summary.** The Scaladoc of `DynamicSchema#rebind` says `@throws RebindException If any required binding is missing from the resolver`. For a node behind a `Reflect.Deferred`, such as the recursive field of a recursive type, `rebind` returns a schema, and the `RebindException` comes later, from the first operation that reaches that node.

**Reproduction** (Scala 3.3.7, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.binding._

case class Tree(value: Int, children: List[Tree])
object Tree {
  implicit val schema: Schema[Tree] = Schema.derived
}

@main def run(): Unit = {
  val dynamic  = Tree.schema.toDynamicSchema
  // No binding for List: rebind should throw RebindException.
  val resolver = BindingResolver.empty.bind(Binding.of[Tree]).bind(Binding.of[Int])
  val rebound =
    try Some(dynamic.rebind[Tree](resolver))
    catch { case e: RebindException => println(s"rebind threw: ${e.getMessage}"); None }
  println(s"rebind returned: ${rebound.isDefined}")
  rebound.foreach { schema =>
    try println(schema.toDynamicValue(Tree(1, Nil)))
    catch { case e: RebindException => println(s"toDynamicValue threw: ${e.getMessage}") }
  }
}
// rebind returned: true
// toDynamicValue threw: Missing Sequence binding for type scala.collection.immutable.List at path: .children
```

**Expected.** `rebind threw: Missing Sequence binding for type scala.collection.immutable.List at path: .children`, as the `@throws` tag promises.

**Root cause.** `rebind` forces only the root of the transformed tree (`schema/shared/src/main/scala/zio/blocks/schema/DynamicSchema.scala:271`). Derivation puts the `children` field behind a `Reflect.Deferred`, and `Deferred#transform` returns a new `Deferred` whose value is transformed, and its bindings looked up, on first use (`Reflect.scala:1900`). The tag is at `DynamicSchema.scala:255-256`.

**Impact.** A caller that catches `RebindException` around `rebind`, for example to report a schema that was received over the wire and cannot be bound, gets a schema that fails later, at an arbitrary place, with an exception the caller no longer expects. The Scaladoc of `RebindException` and `RebindTransformer` now says when the exception is thrown.

**Test gap.** The `RebindException` tests in `DynamicSchemaRebindSpec` use flat records, variants, primitives, wrappers and dynamic nodes (`schema/shared/src/test/scala/zio/blocks/schema/DynamicSchemaRebindSpec.scala:133-332`); none has a binding missing behind a `Deferred`.

**Suggested fix.** After the transform, walk the result inside the same scope and force every `Deferred` node once, with a visited set for the cycles, so that a missing binding throws from `rebind`. Otherwise, change the `@throws` text to say that bindings behind a `Deferred` are looked up on first use.

### http-model: `WWW-Authenticate` and the other auth-parameter headers neither escape nor parse quoted strings

Found 2026-10-21 while writing the how-to guide `docs/guides/protect-endpoints-with-jwt.md` (backlog H-38), on `main` at `a44dc6db`. The http-model sources are the same at tag `v0.0.56`. Verified by running against the published 0.0.56. Not filed on GitHub yet.

**Summary.** `Header.WWWAuthenticate(scheme, params)` renders every parameter as `key="value"` with the value copied as is. RFC 7235 §2.1 makes an auth-param value a token or a `quoted-string`, and RFC 7230 §3.2.6 requires a `"` or `\` inside a quoted string to be escaped with a backslash. A value that contains `"` therefore renders an invalid header. Parsing has the mirror problem: it splits the parameter list on every comma, including commas inside quoted strings, and keeps backslash escapes in the value. A bearer API that sends RFC 6750's `error_description` (a free-text, quoted parameter) hits both: a description with a quote makes a malformed challenge, and one with a comma does not survive `render` then `parse`.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-http-model` 0.0.56, run with `scala-cli run`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-http-model:0.0.56

import zio.http.Header

@main def run(): Unit = {
  // RFC 6750 §3: error_description is a quoted-string; a quote inside must be escaped as \"
  val quoted = Header.WWWAuthenticate("Bearer", Map("error_description" -> "token \"abc\" expired"))
  println(quoted.renderedValue)

  // A comma inside a quoted value does not survive render then parse
  val comma = Header.WWWAuthenticate("Bearer", Map("realm" -> "bookstore", "error_description" -> "expired, sign in again"))
  println(comma.renderedValue)
  println(Header.WWWAuthenticate.parse(comma.renderedValue))
  println(Header.WWWAuthenticate.parse(comma.renderedValue) == Right(comma))

  // An RFC 7235 escaped quote is kept with its backslash
  println(Header.WWWAuthenticate.parse("""Bearer error_description="token \"abc\" expired""""))
}
// Bearer error_description="token "abc" expired"
// Bearer realm="bookstore", error_description="expired, sign in again"
// Right(WWWAuthenticate(Bearer,Map(realm -> bookstore, error_description -> "expired)))
// false
// Right(WWWAuthenticate(Bearer,Map(error_description -> token \"abc\" expired)))
```

**Expected.** The first line is `Bearer error_description="token \"abc\" expired"`. Parsing the second line gives back `comma` (`error_description -> expired, sign in again`), so the round trip is `true`. Parsing the last header gives `error_description -> token "abc" expired`, without backslashes.

**Root cause.** `WWWAuthenticate.render` interpolates the raw value between quotes (`http-model/shared/src/main/scala/zio/http/Header.scala:213-215`), and `WWWAuthenticate.parseParams` splits on `","` and strips only the outer quotes (`Header.scala:217-232`). The same code is copied into `Authorization` for `Digest` (`renderParams` at `Header.scala:120-121`, `parseParams` at `Header.scala:102-118`), `ProxyAuthorization` and `ProxyAuthenticate` (`Header.scala:256-276`); those copies are from source, not run.

**Impact.** A server that puts a dynamic message in a challenge (an error description, a realm read from config) can send a header that clients reject or split differently, and a client that reads challenges loses or corrupts values with commas. The H-38 guide works around it by using fixed parameter values and warns readers not to put request data or `JwtError` messages in the challenge. Header injection is not possible this way, because CR and LF are not touched and the JDK server rejects them, but the quote breaks the parameter structure.

**Test gap.** `AuthHeadersSpec` tests `WWWAuthenticate` only with `Map.empty` and `realm="example"` (`http-model/shared/src/test/scala/zio/http/headers/AuthHeadersSpec.scala:153-185`), and the render test checks only `startsWith("Bearer ")`. No test has a quote, a backslash or a comma in a value, and none round-trips more than one parameter.

**Suggested fix.** Add one shared helper for auth-param lists: render each value as a quoted string with `\` and `"` escaped, and parse with a small scanner that respects quoted strings and unescapes them, instead of `split(",")`. Use it from all four headers. Add round-trip property tests with values that contain `"`, `\`, `,` and `=`.

### openapi: `openAPICodec.encodeToString` and `encode` throw `NotImplementedError`, and `decode` on a `String` returns a `Left`

Found 2026-10-21 while writing the how-to guide `docs/guides/write-and-export-openapi.md` (backlog H-39), on `main` at `a44dc6db`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `openAPICodec` is a `JsonCodec[OpenAPI]`, so it offers all of `JsonCodec`'s text and byte methods. Only the `Json` methods work. `encodeToString` and every `encode` overload throw `scala.NotImplementedError: an implementation is missing`, and `decode(input: String)` returns `Left(SchemaError("an implementation is missing"))` for a valid document. The codecs of every OpenAPI type behave the same way. The working path is `encodeValue(api): Json` with `Json#print`, and `Json.parse(text).flatMap(openAPICodec.decode)`.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-openapi` 0.0.56):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-openapi:0.0.56

import zio.blocks.openapi._
import zio.blocks.openapi.OpenAPICodec._
import zio.blocks.schema.json.Json

@main def openApiText(): Unit = {
  val api = OpenAPI(openapi = "3.1.0", info = Info(title = "Bookstore API", version = "1.0.0"))

  println(openAPICodec.encodeValue(api).print)
  // {"openapi":"3.1.0","info":{"title":"Bookstore API","version":"1.0.0"}}
  println(scala.util.Try(openAPICodec.encodeToString(api)))
  // Failure(scala.NotImplementedError: an implementation is missing)
  println(scala.util.Try(openAPICodec.encode(api)))
  // Failure(scala.NotImplementedError: an implementation is missing)
  println(openAPICodec.decode("""{"openapi":"3.1.0","info":{"title":"Bookstore API","version":"1.0.0"}}"""))
  // Left(zio.blocks.schema.SchemaError: an implementation is missing at: .)
  println(Json.parse("""{"openapi":"3.1.0","info":{"title":"Bookstore API","version":"1.0.0"}}""").flatMap(openAPICodec.decode))
  // Right(OpenAPI(3.1.0,Info(Bookstore API,1.0.0,None,None,None,None,None,ChunkMap()),None,Chunk(),None,ChunkMap(),None,Chunk(),Chunk(),None,ChunkMap()))
}
```

**Expected.** `encodeToString(api)` returns the same text as `encodeValue(api).print`, and `decode(text)` returns the same `Right` as `Json.parse(text).flatMap(decode)`.

**Root cause.** `openapi/shared/src/main/scala/zio/blocks/openapi/OpenAPICodec.scala:204-208`: the base class `JsonASTCodec` implements the streaming methods as `def decodeValue(in: JsonReader): A = ???` (`:205`) and `def encodeValue(x: A, out: JsonWriter): Unit = ???` (`:207`). `JsonCodec#encodeToString` and `encode` write through a `JsonWriter` (`JsonCodec.scala:366`, `:402`), which calls the streaming `encodeValue` and hits `???`. `JsonCodec#decode(String)` reads through a `JsonReader` and catches the error into a `Left` (`JsonCodec.scala:381`). The stubs came in with #1240, which removed `JsonEncoder`/`JsonDecoder`.

**Impact.** The obvious way to produce the text of a document fails at run time, with an error that doesn't say what to do instead, and `decode` on a string reports a valid document as an error. `docs/reference/openapi.md` never prints a document as text, so a reader has no working example to copy.

**Test gap.** The openapi tests use only `encodeValue(api): Json` and `decodeValue(json)`; none calls `encodeToString`, `encode` or `decode(String)`.

**Suggested fix.** Implement the streaming methods in `JsonASTCodec` by going through the AST: `encodeValue(x, out)` writes `encodeValue(x)` with `Json.jsonCodec.encodeValue(_, out)`, and `decodeValue(in)` reads a `Json` with `Json.jsonCodec.decodeValue(in, ...)` and calls `decodeValue(json)`. Add tests that `encodeToString` and `decode(String)` agree with the `Json` path for the petstore document.

### openapi: a document with `md"..."` descriptions is not `==` to itself after a JSON round trip

Found 2026-10-21 while writing the how-to guide `docs/guides/write-and-export-openapi.md` (backlog H-39), on `main` at `a44dc6db`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** Encoding an `OpenAPI` value with `openAPICodec.encodeValue` and decoding the result gives a value that is not `==` to the original when a description was written with the `md"..."` interpolator or `Parser.parse`, which is how the reference page and the guides write descriptions. The JSON of both values is the same. The `markdown` module has two classes for plain text, the top-level `zio.blocks.docs.Text` (made by `md"..."` and `Parser.parse`) and `zio.blocks.docs.Inline.Text`. The OpenAPI decoder converts every `Text` into `Inline.Text`, and `Doc#equals` does not treat the two as equal.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-openapi` 0.0.56):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-openapi:0.0.56

import zio.blocks.docs._
import zio.blocks.openapi._
import zio.blocks.openapi.OpenAPICodec._

@main def openApiDocEq(): Unit = {
  val api = OpenAPI(
    openapi = "3.1.0",
    info = Info(title = "Bookstore API", version = "1.0.0", description = Some(md"Books about Scala."))
  )
  val json     = openAPICodec.encodeValue(api)
  val reloaded = openAPICodec.decode(json)

  println(json.print)
  // {"openapi":"3.1.0","info":{"title":"Bookstore API","version":"1.0.0","description":"Books about Scala.\n\n"}}
  println(reloaded == Right(api))
  // false
  println(reloaded.map(r => openAPICodec.encodeValue(r) == json))
  // Right(true)
  println(api.info.description.get.blocks.head.asInstanceOf[Paragraph].content.head.getClass.getName)
  // zio.blocks.docs.Text
  println(reloaded.toOption.get.info.description.get.blocks.head.asInstanceOf[Paragraph].content.head.getClass.getName)
  // zio.blocks.docs.Inline$Text
  println(md"Books about Scala." == Parser.parse("Books about Scala.").toOption.get)
  // true
}
```

**Expected.** `reloaded == Right(api)` is `true`, as it is for documents whose descriptions are built from `Inline.Text`.

**Root cause.** `openapi/shared/src/main/scala/zio/blocks/openapi/OpenAPICodec.scala`: `docCodec` (`:212`) parses the string and passes the result through `normalizeDoc`, whose `normalizeInline` maps the top-level `Text` to `Inline.Text` (`:179`) and likewise for the other inline types. `Doc#equals` (`markdown/shared/src/main/scala/zio/blocks/docs/Doc.scala:84`) compares normalized blocks, but `Doc.normalize` does not unify the top-level and `Inline` classes, so the values differ. The underlying cause is the duplicated inline hierarchy in `markdown/shared/src/main/scala/zio/blocks/docs/Inline.scala` (`Inline.Text` at `:37`, top-level `Text` at `:126`).

**Impact.** Tests that check `decode(encode(api)) == api`, and code that compares a loaded document with an expected one, fail for documents written the way the docs show. The H-39 guide compares the re-encoded JSON instead.

**Test gap.** The openapi round-trip tests build every `Doc` with a helper that uses `Inline.Text` (`OpenAPIJsonSerializationSpec.scala:28`); no openapi test uses `md"..."` or `Parser.parse`.

**Suggested fix.** Make `Doc.normalize` (or `Doc#equals`) map the top-level inline classes to their `Inline` counterparts, the way `normalizeInline` in `OpenAPICodec` does, so that equal Markdown compares equal whichever class built it. Longer term, keep one inline hierarchy in `markdown`. Add an openapi round-trip test with `md"..."` descriptions.

### openapi: every description in the exported JSON ends with `\n\n`

Found 2026-10-21 while writing the how-to guide `docs/guides/write-and-export-openapi.md` (backlog H-39), on `main` at `a44dc6db`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `Doc` fields (descriptions and summaries) are encoded with the Markdown renderer, which ends every paragraph with a blank line. A one-line description therefore appears as `"description": "Books about Scala.\n\n"` in the JSON, and as a quoted string with escapes in the YAML. Markdown readers such as Swagger UI ignore the newlines, but the exported file is noisier than a hand-written one and differs from what other OpenAPI tools write.

**Reproduction.** The program in the round-trip entry above prints `{"openapi":"3.1.0","info":{"title":"Bookstore API","version":"1.0.0","description":"Books about Scala.\n\n"}}` (Scala 3.9.0, `zio-blocks-next-openapi` 0.0.56).

**Expected.** `"description": "Books about Scala."`.

**Root cause.** `docCodec.encodeValue` writes `Renderer.render(x)` as is (`openapi/shared/src/main/scala/zio/blocks/openapi/OpenAPICodec.scala:222`), and `Renderer` renders a `Paragraph` as `s"${renderInlines(content)}\n\n"` (`markdown/shared/src/main/scala/zio/blocks/docs/Renderer.scala:57-58`).

**Impact.** Cosmetic, but it shows up in every exported document, and string comparisons in tests have to include the newlines.

**Test gap.** The openapi serialization tests do not assert the encoded text of a description.

**Suggested fix.** Strip trailing newlines in `docCodec.encodeValue` (`Renderer.render(x).stripTrailing`, or a renderer option that does not end the last block with a blank line). Decoding is unaffected, because the parser accepts both forms.

### schema: indented `Json#print` writes a whitespace-only line inside an empty array or object

Found 2026-10-21 while writing the how-to guide `docs/guides/write-and-export-openapi.md` (backlog H-39), on `main` at `a44dc6db`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** With an indenting `WriterConfig`, an empty array prints as `[`, a line of spaces, and `]` instead of `[]`, and an empty object likewise. The output is valid JSON, but it has trailing whitespace and looks like a missing value. In an OpenAPI document it shows up for every bearer-token security requirement, whose scope list is empty.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-schema` 0.0.56; spaces shown as `·`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56

import zio.blocks.schema.json.{Json, WriterConfig}

@main def emptyIndent(): Unit = {
  val json = Json.Object("scopes" -> Json.Array(), "extensions" -> Json.Object())
  println(json.print(WriterConfig.withIndentionStep2).replace(" ", "·"))
  // {
  // ··"scopes":·[
  // ····
  // ··],
  // ··"extensions":·{
  // ····
  // ··}
  // }
}
```

**Expected.** `"scopes": []` and `"extensions": {}`.

**Root cause.** `JsonWriter.writeNestedStart` writes the opening bracket followed at once by a newline and the deeper indentation, and `writeNestedEnd` writes another newline and indentation before the closing bracket, without checking whether anything was written in between (`schema/jvm/src/main/scala/zio/blocks/schema/json/JsonWriter.scala:1237`, `:1248`; the Scala.js copy at `schema/js/src/main/scala/zio/blocks/schema/json/JsonWriter.scala:1290`, `:1301`).

**Impact.** Cosmetic. Every indented document with an empty collection has whitespace-only lines, which linters and `git diff` flag.

**Test gap.** No `Json` printer test covers an empty array or object with indentation (from a search of the schema tests).

**Suggested fix.** Defer the newline and indentation after an opening bracket until the first element or key is written, and write the closing bracket directly when nothing was written.

**Also seen in H-06** (2026-10-21, `docs/guides/json-without-case-classes.md`). `Json#toString` uses the same indenting writer, so `println(json)` on any tree with an empty list shows the blank line too: `{"title": "Zionomicon", "tags": []}` prints `"tags": [`, a line of four spaces, `]`. Verified with `zio-blocks-next-schema` 0.0.56 on Scala 3.9.0.

### telemetry: the default text log line prints the full class name and two numbers for any class in a package

Found 2026-10-21 while writing the tutorial `docs/guides/instrument-a-program-with-logs-traces-and-metrics.md` (backlog T-12), on `main` at `a44dc6db`. The telemetry sources are the same at tag `v0.0.56`. Verified by running against the published 0.0.56. Not filed on GitHub yet.

**Summary.** The text format puts the source location in brackets: the short class name, the method and the line, as in `[RestockJob$.main:10]`. When the class sits in a package, `TextLogFormatter` prints the full name followed by two numbers instead: `[(bookstore.RestockJob$,10,21).main:10]`. `TextLogFormatter` is what the zero-setup `log.info` uses, so almost every user sees this on their very first log line. The default console processor, which takes over once a writer or processor is added, prints the location correctly, so the same call can print two different brackets.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-telemetry` 0.0.56, JDK 21, run with `scala-cli run`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-telemetry:0.0.56
package bookstore

import zio.blocks.telemetry._

object RestockJob {
  def main(args: Array[String]): Unit = {
    log.info("default console output")
    log.writer(TextLogFormatter, StdoutWriter)
    log.info("default console plus TextLogFormatter writer")
  }
}
// 2026-10-21T09:03:25.964Z INFO  [(bookstore.RestockJob$,10,21).main:10] default console output
// 2026-10-21T09:03:25.979Z INFO  [RestockJob$.main:12] default console plus TextLogFormatter writer
// 2026-10-21T09:03:25.979Z INFO  [(bookstore.RestockJob$,10,21).main:12] default console plus TextLogFormatter writer
```

**Expected.** Every line shows `[RestockJob$.main:N]`, the part of the name after the last dot.

**Root cause.** `TextLogFormatter.format` and `TextLogFormatter.formatRecord` call `sb.append(namespace, lastDot + 1, namespace.length)` (`telemetry/shared/src/main/scala/zio/blocks/telemetry/LogFormatter.scala:106` and `:194`). `sb` is a `scala.collection.mutable.StringBuilder`, which has no three-argument `append` like `java.lang.StringBuilder#append(CharSequence, int, int)`. The compiler auto-tuples the three arguments into one `(String, Int, Int)` and calls `append(x: Any)`, which appends the tuple's `toString`. `StdoutLogRecordProcessor` uses `namespace.substring(lastDot + 1)` (`StdoutLogRecordProcessor.scala:66`) and is correct. A namespace without a dot takes the `else` branch, which is why scripts in the empty package look right.

**Impact.** The zero-setup path (`Logger.emitter` is a `FormattedLogEmitter(TextLogFormatter, StdoutWriter)` when only the console processor is registered, `Logger.scala:53-55`) and every `log.writer(TextLogFormatter, ...)` print a long, misleading location for all real applications, which always use packages. Log parsers that expect `[Class.method:line]` break. The JSON format is not affected.

**Test gap.** `LogFormatterSpec.scala:181` checks the rendered line with `.contains("com.example.Service")`, which the tuple text also contains, and `:226-228` tests only a namespace without dots ("handles a namespace without dots"). No test asserts the full bracket for a dotted namespace. AGENTS.md's rule to assert the full rendered string with `==` would have caught it.

**Suggested fix.** Use `sb.append(namespace.substring(lastDot + 1))`, or `sb.underlying.append(namespace, lastDot + 1, namespace.length)` to keep the allocation-free form, at both lines. Add a test that asserts the full line for `code.namespace = "com.example.Service"` through both `format` and `formatRecord`. Building with `-Xlint:adapted-args` (Scala 2) would have flagged the auto-tupling.

### telemetry: on Scala 2.13, `log.info` and the other `log.*` calls don't compile outside the library's package

Found 2026-10-21 while writing T-12, on `main` at `a44dc6db`. The telemetry sources are the same at tag `v0.0.56`. Verified by running against the published 0.0.56. Not filed on GitHub yet.

**Summary.** On Scala 2.13, every `log.trace`, `log.debug`, `log.info`, `log.warn`, `log.error` and `log.fatal` call in user code fails to compile, in any package and in the empty package. The Scala 2 macro expands to code that reads `GlobalLogState` and other objects that are `private[telemetry]`, and Scala 2 type-checks a macro expansion with the access rights of the call site. The module is published for Scala 2.13, and the reference pages say it supports 2.13, but its main logging API can't be used there. Spans and metrics are not affected.

**Reproduction** (Scala 2.13.16, `zio-blocks-telemetry` 0.0.56, JDK 21, `scala-cli compile`):

```scala
//> using scala 2.13.16
//> using dep dev.zio::zio-blocks-telemetry:0.0.56
package bookstore

import zio.blocks.telemetry._

object RestockJob {
  def main(args: Array[String]): Unit =
    log.info("default console output")
}
// [error] ./Repro.scala:9:13
// [error] object GlobalLogState in package telemetry cannot be accessed as a member of package zio.blocks.telemetry from object RestockJob in package bookstore
// [error]     log.info("default console output")
// [error]             ^
```

Without the `package bookstore` line, the error is the same except that it ends with `from object RestockJob`.

**Expected.** The call compiles and prints the line, as it does on Scala 3.

**Root cause.** The expansion in `telemetry/shared/src/main/scala-2/zio/blocks/telemetry/LogMacros.scala` refers to `_root_.zio.blocks.telemetry.GlobalLogState` (`:357-358`), `AttributeBuilderPool`, `LogAnnotations` and `EpochClock`, all `private[telemetry]` (`GlobalLogState.scala:53`, `jvm/.../AttributeBuilderPool.scala:19`, `jvm/.../LogAnnotations.scala:26`, `EpochClock.scala:29`). The Scala 3 macro refers to the same objects, but code inside a Scala 3 quote is not access-checked at the call site, so Scala 3 works.

**Impact.** Logging is unusable on Scala 2.13 in 0.0.56. A Scala 2.13 user who follows the reference page's installation section gets a compile error on the first `log.info`.

**Test gap.** Every telemetry test, including the Scala 2 runs, is in `package zio.blocks.telemetry`, where `private[telemetry]` members are visible. No test calls the macros from another package.

**Suggested fix.** Make the members the expansion needs public and mark them as internal in their Scaladoc (or move them to an `internal` object that is public but documented as not for users), then add a test in a different package, for example `package zio.blocks.telemetry.external`, that calls each `log.*` method on Scala 2.13 and Scala 3.

### telemetry: after `log.removeAll()`, `log.writer` and `log.addProcessor` never bring logging back

Found 2026-10-21 while writing T-12, on `main` at `a44dc6db`. The telemetry sources are the same at tag `v0.0.56`. Verified by running against the published 0.0.56. Not filed on GitHub yet.

**Summary.** The Scaladoc of `log.removeAll()` says that "log calls are no-ops until you add a processor or install a logger", and the logging reference page says they are no-ops "until you add an output again". In fact, `log.writer(...)` and `log.addProcessor(...)` after `removeAll` add the output but nothing is ever written to it. Only `log.install(...)` or `log.setMinSeverity(...)` restores logging. Code that clears the defaults with `removeAll` and then adds its own JSON writer, which is the natural way to say "only JSON, please", logs nothing at all, with no error.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-telemetry` 0.0.56, JDK 21, run with `scala-cli run`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-telemetry:0.0.56
package bookstore

import zio.blocks.telemetry._

object RemoveAllRepro {
  def main(args: Array[String]): Unit = {
    log.removeAll()
    log.writer(TextLogFormatter, StdoutWriter)
    log.info("1: after removeAll + writer")
    log.addProcessor(new ConsoleLogRecordProcessor)
    log.info("2: after addProcessor too")
    log.setMinSeverity(Severity.Trace)
    log.info("3: after setMinSeverity")
  }
}
// 2026-10-21T09:10:30.808Z INFO  [(bookstore.RemoveAllRepro$,10,25).main:16] 3: after setMinSeverity
// 2026-10-21T09:10:30.808Z INFO  [RemoveAllRepro$.main:16] 3: after setMinSeverity
```

Lines 1 and 2 are never printed. (The two forms of the bracket in line 3 are the `TextLogFormatter` bug above.)

**Expected.** Line 1 is printed by the writer, and line 2 by both outputs, as the Scaladoc promises.

**Root cause.** `removeAll` installs `silentState`, whose minimum severity is `Int.MaxValue`, and sets `globalMinLevel` to `Int.MaxValue` (`telemetry/shared/src/main/scala/zio/blocks/telemetry/GlobalLogState.scala:67-75` and `:130-132`; called from `log.scala:65-73`). `log.writer` rebuilds the state with `currentState.minSeverity` (`log.scala:104-113`), and `log.addProcessor` also keeps it (`log.scala:46-55`), so the floor stays above every severity. `log.setMinSeverity` (`log.scala:30-31`) and `log.install` (`log.scala:58-59`) reset it.

**Impact.** Silent loss of all logs in a program that calls `removeAll` before adding outputs. The reference page `docs/reference/telemetry/logging/index.md:176` repeats the Scaladoc's promise.

**Test gap.** No test logs through a writer or processor added after `log.removeAll()`. The tests that call `removeAll` use it as cleanup at the end.

**Suggested fix.** Make `removeAll` keep the minimum severity and mark the state as having no outputs instead of raising the floor, or have `writer` and `addProcessor` restore the previous minimum severity when they leave the silent state. Add a test for `removeAll` followed by each of `writer`, `addProcessor` and `install`.

### http-model: `QueryParams` keeps `+` as a plus sign, so values from HTML `GET` forms arrive with `+` instead of spaces

Not filed on GitHub yet.

Found 2026-10-21 while writing the how-to guide `docs/guides/serve-http-model-requests.md` (backlog H-35), on `main` at `a44dc6db`. The http-model sources are the same at tag `v0.0.56`. Verified by running against the published 0.0.56.

**Summary.** When a browser submits an HTML form with `method="get"`, it encodes the fields as `application/x-www-form-urlencoded` in the query string, so a space becomes `+` and a literal plus becomes `%2B`. `URL.parse` decodes the query with `QueryParams.fromEncoded`, which percent-decodes but treats `+` as a literal plus. A search for `zio blocks` therefore arrives as `zio+blocks`. Worse, `+` and `%2B` both decode to `+`, so once the query is a `QueryParams` the handler can no longer tell a space from a real plus sign. The module's own `Form.fromString` decodes the same string correctly, so the two types disagree about one wire format.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-http-model` 0.0.56, run with `scala-cli run`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-http-model:0.0.56

import zio.http._

object PlusInQuery {
  def main(args: Array[String]): Unit = {
    // What a browser sends for <form method="get"> with q = "zio blocks"
    val url = URL.parse("/search?q=zio+blocks&tag=a%2Bb").toOption.get
    println(url.queryParams.getFirst("q"))
    println(url.queryParams.getFirst("tag"))
    // The same pair decoded as form data
    println(Form.fromString("q=zio+blocks&tag=a%2Bb").get("q"))
    // Encoding a space in a query value
    println(QueryParams("q" -> "zio blocks").encode)
  }
}
// Some(zio+blocks)
// Some(a+b)
// Some(zio blocks)
// q=zio%20blocks
```

**Expected.** `url.queryParams.getFirst("q")` returns `Some(zio blocks)` and `getFirst("tag")` returns `Some(a+b)`, as `URLSearchParams` does in browsers and `Form.fromString` does in this module.

**Root cause.** `QueryParams.fromEncoded` decodes each key and value with `PercentEncoder.decode` (`http-model/shared/src/main/scala/zio/http/QueryParams.scala:196-197`), and `PercentEncoder.decode` (`http-model/shared/src/main/scala/zio/http/PercentEncoder.scala:103`) only handles `%XY` escapes. `Form` replaces `+` with a space before decoding (`http-model/shared/src/main/scala/zio/http/Form.scala:98-99`), but `QueryParams` does not.

**Impact.** Any page that submits a search or filter form with `method="get"` gets `+` instead of spaces. This hits the server adapter in H-35 and the planned query-validation guide (H-36) and HTMX live-search tutorial (T-10) as soon as a plain form is used. The workaround is to bypass `QueryParams` and decode the raw query string with `Form.fromString`, which needs access to the raw query (for example `URI#getRawQuery` in a JDK `HttpExchange`). The H-35 guide documents the behaviour in a note.

**Test gap.** `QueryParamsSpec` (`http-model/shared/src/test/scala/zio/http/QueryParamsSpec.scala`) has no test with `+` in a key or value. `FormSpec` tests "decodes plus as space" for `Form` only.

**Suggested fix.** In `QueryParams.fromEncoded`, replace `+` with a space before calling `PercentEncoder.decode`, as `Form` does, and add tests for `q=zio+blocks` and `tag=a%2Bb`. `QueryParams#encode` already writes a space as `%20`; encode a literal `+` in a query key or value as `%2B` (today `+` is in the allowed set for `QueryKey` and `QueryValue`, `PercentEncoder.scala:58-65`) so that encoding and decoding round-trip.

### http-model-schema: `request.header[T](name)` and `response.header[T](name)` do not compile

Not filed on GitHub yet.

Found 2026-10-21 while writing the how-to guide `docs/guides/validate-query-parameters-and-headers.md` (backlog H-36), on `main` at `a44dc6db` plus the unmerged H-35 branch. The http-model and http-model-schema sources are the same at tag `v0.0.56`. Verified by compiling against the published 0.0.56 on Scala 3.9.0 and 2.13.16.

**Summary.** `RequestSchemaOps` and `ResponseSchemaOps` add `header[T](name: String)(implicit Schema[T])` to `Request` and `Response` through an implicit conversion. Both classes already have a member `header[A](headerCodec: Header.Codec[A]): Option[A]`. Scala only looks for an implicit conversion when a member with that name does not exist, so `request.header[Int]("X-Count")` resolves to the member and fails to compile: the string is not a `Header.Codec`. The typed header accessor that the reference page advertises on `Request` and `Response` is unreachable without writing `new RequestSchemaOps(request)`. `headerAll` and `headerOrElse` work, because `Request` has no members with those names.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-http-model-schema` 0.0.56, `scala-cli compile`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-http-model-schema:0.0.56

import zio.http._
import zio.http.schema._

object HeaderOnRequest {
  def main(args: Array[String]): Unit = {
    val request = Request.get(URL.parse("/books").toOption.get).addHeader("X-Request-Id", "42")
    println(request.header[Int]("X-Request-Id"))
  }
}
// [error] ./r1/HeaderOnRequest.scala:11:33
// [error] Found:    ("X-Request-Id" : String)
// [error] Required: zio.http.Header.Codec[Int]
// [error]     println(request.header[Int]("X-Request-Id"))
// [error]                                 ^^^^^^^^^^^^^^
```

The same file with `//> using scala 2.13.16` and `zio-blocks-http-model-schema` fails the same way:

```
// [error] type mismatch;
// [error]  found   : String("X-Request-Id")
// [error]  required: zio.http.Header.Codec[Int]
```

**Expected.** `request.header[Int]("X-Request-Id")` returns `Left(HeaderError.Malformed(...))` or `Right(42)`, as `request.headers.header[Int]("X-Request-Id")` does.

**Root cause.** `Request#header` (`http-model/shared/src/main/scala/zio/http/Request.scala:36`) and `Response#header` (`http-model/shared/src/main/scala/zio/http/Response.scala:33`) shadow `RequestSchemaOps#header` (`http-model-schema/shared/src/main/scala/zio/http/schema/RequestSchemaOps.scala:34`) and `ResponseSchemaOps#header` (`ResponseSchemaOps.scala:25`). A member with the right name always wins over an implicit conversion, even when its parameter types don't fit.

**Impact.** Everyone who follows `docs/reference/http-model/schema.md` ("Exposes all methods from `QueryParamsSchemaOps` and `HeadersSchemaOps` directly on `Request`") hits a confusing type error that names `Header.Codec`. The workaround is `request.headers.header[T](name)`. The H-36 guide uses it and explains why in a warning.

**Test gap.** `RequestSchemaSpec` (`http-model-schema/shared/src/test/scala/zio/http/schema/RequestSchemaSpec.scala:51`) and `ResponseSchemaSpec` (`ResponseSchemaSpec.scala:29`) call `new RequestSchemaOps(request)` and `new ResponseSchemaOps(response)` directly, so they never go through the implicit conversion that users rely on.

**Suggested fix.** Either add `header[T](name: String)(implicit Schema[T])` overloads to `Request` and `Response` themselves (http-model would then depend on schema, which it does not today), or rename the extension (for example `headerAs[T]`) and keep `header[T]` only on `Headers`. Change both specs to call the methods through `import zio.http.schema._` so the tests compile the way users write the code.

### http-model-schema: derived `QueryCodec` and `HeaderCodec` ignore field default values

Not filed on GitHub yet.

Found 2026-10-21 while writing the how-to guide `docs/guides/validate-query-parameters-and-headers.md` (backlog H-36), on `main` at `a44dc6db` plus the unmerged H-35 branch. The http-model-schema sources are the same at tag `v0.0.56`. Verified by running against the published 0.0.56.

**Summary.** A case class field with a default value, such as `limit: Int = 20`, is still required by a codec derived with `Schema#derive(DefaultQueryFormat)` or `Schema#derive(DefaultHeaderFormat)`. A missing parameter or header fails with `Missing field` instead of taking the default. The schema carries the defaults, and the JSON codec derived from the same schema uses them, so the two formats disagree about the same case class. Paging parameters are the most common case: `?limit=5` without `offset` is rejected.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-http-model-schema` 0.0.56, `scala-cli run`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-http-model-schema:0.0.56

import zio.blocks.schema.Schema
import zio.blocks.schema.json.JsonCodec
import zio.http._
import zio.http.schema._

final case class Paging(limit: Int = 20, offset: Int = 0)
object Paging { implicit val schema: Schema[Paging] = Schema.derived }

final case class ClientInfo(xApiVersion: Int = 1)
object ClientInfo { implicit val schema: Schema[ClientInfo] = Schema.derived }

object DefaultsIgnored {
  def main(args: Array[String]): Unit = {
    val query = Schema[Paging].derive(DefaultQueryFormat)
    println(query.decode(QueryParams.fromEncoded("limit=5")))
    println(query.decode(QueryParams.empty))
    val headers = Schema[ClientInfo].derive(DefaultHeaderFormat)
    println(headers.decode(Headers.empty))
    // The schema knows the defaults, and the JSON codec uses them:
    println(Schema[Paging].jsonCodec.decode("""{"limit":5}"""))
  }
}
// Left(zio.blocks.schema.SchemaError: Missing field 'offset' at: .)
// Left(zio.blocks.schema.SchemaError: Missing field 'limit' at: .)
// Left(zio.blocks.schema.SchemaError: Missing field 'X-API-VERSION' at: .)
// Right(Paging(5,0))
```

**Expected.** `Right(Paging(5,0))`, `Right(Paging(20,0))` and `Right(ClientInfo(1))`, as the JSON codec does.

**Root cause.** `QueryCodecDeriver#deriveRecord` (`http-model-schema/shared/src/main/scala/zio/http/schema/QueryCodecDeriver.scala:49-112`) and `HeaderCodecDeriver#deriveRecord` (`HeaderCodecDeriver.scala:48-112`) build one `FieldCodec` per field and never read the field's default (`Reflect#getDefaultValue`, `schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala:150`). `SinglePrimitive#decodeAny` returns `SchemaError.missingField` for an absent value (`ParamCodecSupport.scala:53`).

**Impact.** Users who model optional parameters with defaults get 400s for valid requests, or have to switch every such field to `Option` and apply the defaults by hand. The H-36 guide shows the behaviour in a warning and uses `Option` fields.

**Test gap.** `QueryCodecSpec` and `HeaderCodecSpec` have no case class with a default value.

**Suggested fix.** In both `deriveRecord`s, read `field.value.getDefaultValue` and, when the raw value is absent and a default exists, store the default instead of calling `decodeAny`. Add tests for a missing defaulted field, a present defaulted field and a malformed defaulted field (which should still fail).

### schema: `json"..."` does not compile on Scala 2.13 outside the `zio.blocks.schema` package

Found 2026-10-21 while writing the how-to guide `docs/guides/json-without-case-classes.md` (backlog H-06), on `main` at `0740013c`. The code is the same at tag `v0.0.56` (no file under `schema/` outside the tests changed since the tag). Verified. Not filed.

**Summary.** On Scala 2.13, every use of the `json"..."` interpolator in user code fails to compile, with or without spliced values. The Scala 2 macro expands to a call on `JsonInterpolatorRuntime`, which is `private[schema]`, so the expansion compiles only when the call site is itself inside the `zio.blocks.schema` package, as the library's own tests are. On Scala 3 the same code compiles and works.

**Reproduction** (Verified; Scala 2.13.18, `zio-blocks-schema` 0.0.56, run with `scala-cli run JsonInterpolatorScala2.scala`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema.json._

object JsonInterpolatorScala2 {
  def main(args: Array[String]): Unit = {
    val title = "Zionomicon"
    println(json"""{"title": $title, "price": 49.99}""".print)
  }
}
// [error] ./JsonInterpolatorScala2.scala:8:13
// [error] object JsonInterpolatorRuntime in package json cannot be accessed as a member of package zio.blocks.schema.json from object JsonInterpolatorScala2
// [error]     println(json"""{"title": $title, "price": 49.99}""".print)
// [error]             ^
```

**Expected.** The program compiles and prints `{"title":"Zionomicon","price":49.99}`, as it does on Scala 3.9.0 with `zio-blocks-next-schema` 0.0.56.

**Root cause.** `schema/shared/src/main/scala-2/zio/blocks/schema/json/package.scala:88` (`JsonInterpolatorRuntime.validateJsonLiteral`, run inside the macro, which is fine) and `:92` (`reify(JsonInterpolatorRuntime.jsonWithContexts(...))`, which is emitted into the caller's code). `reify` keeps the reference as written, and `JsonInterpolatorRuntime` is declared `private[schema] object JsonInterpolatorRuntime` at `schema/shared/src/main/scala/zio/blocks/schema/json/JsonInterpolatorRuntime.scala:28`. The Scala 3 macro (`scala-3/zio/blocks/schema/json/package.scala`) is inlined differently and is not affected.

**Impact.** The `json"..."` interpolator, which `json.md` presents as a main way to write JSON, is unusable on Scala 2.13. Scala 2 users have to fall back to the `Json` constructors or `Json.parse`.

**Test gap.** `schema/shared/src/test/scala/zio/blocks/schema/json/JsonInterpolatorSpec.scala:17` is in `package zio.blocks.schema.json`, so the `private[schema]` reference resolves there. No test calls `json"..."` from a package outside `zio.blocks.schema`, and mdoc runs only on Scala 3.

**Suggested fix.** Make `JsonInterpolatorRuntime` public (it can stay undocumented, like other macro runtimes), or emit a fully qualified call to a public forwarder. Add a Scala 2 test in a package outside `zio.blocks.schema` (for example `zio.blocksjsontest`) that calls `json"..."` with and without splices.

### schema: `Json#delete` through `[*]` removes the array elements that lack the field

Found 2026-10-21 while writing the how-to guide `docs/guides/json-without-case-classes.md` (backlog H-06), on `main` at `0740013c`. The code is the same at tag `v0.0.56` (no file under `schema/` outside the tests changed since the tag). Verified. Not filed.

**Summary.** `json.delete(p".books[*].isbn")` is meant to remove the `isbn` field from every book. It also removes every book that has no `isbn` field, and when no book has it, it empties the array. `Json#deleteOrFail` returns the same damaged document as a `Right`. `Json#modify` on the same path keeps the elements it can't change.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-schema` 0.0.56, run with `scala-cli run DeleteWildcard.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
import zio.blocks.schema.*
import zio.blocks.schema.json.*

@main def run(): Unit = {
  val doc = Json.parseUnsafe(
    """{"books":[{"title":"Zionomicon","isbn":"978-0-9961-1250-4"},{"title":"Programming in Scala"}]}"""
  )

  // Remove "isbn" from every book: the book without "isbn" disappears too
  println(doc.delete(p".books[*].isbn").print)
  // {"books":[{"title":"Zionomicon"}]}

  // No book has "edition": every book disappears
  println(doc.delete(p".books[*].edition").print)
  // {"books":[]}

  // deleteOrFail reports nothing wrong
  println(doc.deleteOrFail(p".books[*].isbn").map(_.print))
  // Right({"books":[{"title":"Zionomicon"}]})

  // modify on the same elements keeps both books
  println(doc.modify(p".books[*]")(_.delete(p".isbn")).print)
  // {"books":[{"title":"Zionomicon"},{"title":"Programming in Scala"}]}
}
```

**Expected.** `{"books":[{"title":"Zionomicon"},{"title":"Programming in Scala"}]}` for the first and third calls, and the document unchanged for the second (or a `Left` from `deleteOrFail` when no element has the field).

**Root cause.** `schema/shared/src/main/scala/zio/blocks/schema/json/Json.scala:1979`, in `deleteAtPathRecursive`: for a non-last `Elements` node it builds `new Array(arr.value.flatMap(e => deleteAtPathRecursive(e, nodes, idx + 1)))`. The recursive call returns `None` when the element has no such field (`Json.scala:1935-1937`, `if (filtered.length != fields.length) ... else None`), and `flatMap` over that `Option` drops the element. The `modify` counterpart (`Json.scala:1705-1712`) maps `None` back to the original element.

**Impact.** Silent data loss: a cleanup step such as removing an internal field before publishing a document deletes whole records whenever the field is optional. Nothing reports it.

**Test gap.** `schema/shared/src/test/scala/zio/blocks/schema/json/JsonSpec.scala:2992-3004` ("delete with Elements non-last applies recursively through elements") uses two objects that both contain `x`. No test deletes `[*].field` where some element lacks the field.

**Suggested fix.** Mirror `modifyAtPathRecursive`: `arr.value.map(e => deleteAtPathRecursive(e, nodes, idx + 1).getOrElse(e))`, and return `None` from the `Elements` case only when no element changed, so `deleteOrFail` can still report a path that matches nothing. Add tests for a partly missing and a fully missing field.

### schema: `Json.Number(Double)` keeps a trailing zero, so `Json.Number(49.99)` prints `49.990`

Found 2026-10-21 while writing the how-to guide `docs/guides/json-without-case-classes.md` (backlog H-06), on `main` at `0740013c`. The code is the same at tag `v0.0.56` (no file under `schema/` outside the tests changed since the tag). Verified. Not filed.

**Summary.** `Json.Number(value: Double)` converts the `Double` with `JsonWriter.toBigDecimal`, which leaves one trailing zero in the scale whenever the shortest decimal form has an odd number of them. `49.99` becomes `49.990` (scale 3), `2.5` becomes `2.50`. The value compares equal to `Json.Number(BigDecimal("49.99"))`, but it prints differently, so generated JSON doesn't match what the user wrote. The JSON codec for `Double` uses the same factory when it encodes to a `Json` tree, so `Json.from(value)` pads every `Double` field, while `toJsonString` on the same value does not.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-schema` 0.0.56, run with `scala-cli run NumberFromDouble.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
import zio.blocks.schema.*
import zio.blocks.schema.json.*

final case class Price(amount: Double)
object Price { given Schema[Price] = Schema.derived }

@main def run(): Unit = {
  println(Json.Number(49.99).print)
  // 49.990
  println(Json.Number(2.5).print)
  // 2.50
  println(Json.Number(10.0).print)
  // 10.0
  println(Json.Number(49.99).value.scale)
  // 3

  // the same value from a BigDecimal or from parsed text prints as written
  println(Json.Number(BigDecimal("49.99")).print)
  // 49.99
  println(Json.parseUnsafe("49.99").print)
  // 49.99

  // a Double field encoded to a Json tree is padded; encoded straight to text it is not
  println(Json.from(Price(49.99)).print)
  // {"amount":49.990}
  println(Price(49.99).toJsonString)
  // {"amount":49.99}
}
```

**Expected.** `49.99`, `2.5` and `10.0` (or `10`): the shortest text that reads back as the same `Double`, as `java.lang.Double.toString` and `JsonWriter#writeVal(Double)` produce.

**Root cause.** `schema/jvm/src/main/scala/zio/blocks/schema/json/JsonWriter.scala:3665` (`toBigDecimal(x: Double)`; the JS copy is at `schema/js/src/main/scala/zio/blocks/schema/json/JsonWriter.scala:3830`) strips trailing zeros from the decimal mantissa only in pairs: both loops at `:3730-3750` divide by 100. One zero is left when their count is odd. `Json.Number.apply(Double)` (`schema/shared/src/main/scala/zio/blocks/schema/json/Json.scala:837`) and `apply(Float)` (`:829`) call it, and `JsonCodec.doubleCodec.encodeValue(x: Double): Json` (`JsonCodec.scala:771`) calls `Json.Number(x)`. Encoding to text goes through `JsonWriter#writeVal(Double)` instead (`JsonCodec.scala:764`), which is why `toJsonString` is not affected.

**Impact.** Any JSON built from `Double` values through `Json.Number` or `Json.from` prints padded numbers: prices like `49.990` in API responses and test fixtures that don't match their expected text. Parsed numbers and `BigDecimal` inputs are not affected.

**Test gap.** No test prints a `Json.Number` built from a `Double` with a fractional part; `JsonSpec.scala` builds numbers from `Int` literals (for example `:35`, `:51`).

**Suggested fix.** After the pair loop, strip one more zero when `m10 % 10 == 0`, or build the `BigDecimal` with `stripTrailingZeros` and keep the `10.0`-style scale only for integral values. Add a test that `Json.Number(49.99).print == "49.99"` on the JVM and JS.

### schema: decoding a fractional JSON number as `Int` or `Long` reports "value is too large"

Found 2026-10-21 while writing the how-to guide `docs/guides/json-without-case-classes.md` (backlog H-06), on `main` at `0740013c`. The code is the same at tag `v0.0.56` (no file under `schema/` outside the tests changed since the tag). Verified. Not filed.

**Summary.** `json.as[Int]` on `49.99` fails with `value is too large for int`. The value is not too large: it has a fractional part. The same message is used for a real overflow, so the user can't tell the two apart.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-schema` 0.0.56, run with `scala-cli run FractionAsInt.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
import zio.blocks.schema.json.*

@main def run(): Unit = {
  val doc = Json.parseUnsafe("""{"price": 49.99, "pages": 3000000000}""")
  println(doc.get("price").as[Int])
  // Left(zio.blocks.schema.SchemaError: value is too large for int at: .)
  println(doc.get("price").as[Long])
  // Left(zio.blocks.schema.SchemaError: value is too large for long at: .)

  // a real overflow gets the same message
  println(doc.get("pages").as[Int])
  // Left(zio.blocks.schema.SchemaError: value is too large for int at: .)
}
```

**Expected.** A message that names the problem, such as `expected an integer, got 49.99 at: .`, for the fractional case, and `value is too large for int` only for an overflow.

**Root cause.** `schema/shared/src/main/scala/zio/blocks/schema/json/JsonCodec.scala:674-679`: `decodeValue(json: Json)` calls `num.value.bigDecimal.intValueExact` and maps every `ArithmeticException` to `"value is too large for int"`. `intValueExact` throws both for overflow and for a non-zero fraction. The `Byte` (`:612`), `Short` (`:645`) and `Long` (`:711`) codecs do the same.

**Impact.** Misleading errors when an untyped read (`selection.as[Int]`) or a typed decode meets a decimal where an integer was expected, which is common with third-party payloads.

**Test gap.** `JsonCodecDeriverSpec.scala:96-97` tests only real overflows (`2147483648`, `-2147483649`), and only through the text reader, not through `decodeValue(json: Json)`.

**Suggested fix.** Check `num.value.isWhole` first and report a separate message for a fraction; keep the overflow message for the `ArithmeticException` that remains. Add tests for `1.5` read as `Byte`, `Short`, `Int` and `Long` from a `Json` value.

### schema: JSON parse errors carry no line, column or offset

Found 2026-10-21 while writing the how-to guide `docs/guides/json-without-case-classes.md` (backlog H-06), on `main` at `0740013c`. The code is the same at tag `v0.0.56` (no file under `schema/` outside the tests changed since the tag). Verified. Not filed.

**Summary.** A `SchemaError` from `Json.parse` names what the reader expected and the structural path it was in (`at: .authors`), but never a line, column or character offset. For an error directly in an object, the path is only `.`, so in a large document the user has to search for the problem by hand.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-schema` 0.0.56, run with `scala-cli run ParseErrorPosition.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
import zio.blocks.schema.json.*

@main def run(): Unit = {
  // a missing comma on line 3 of a 4-line document
  val text =
    """{
      |  "title": "Zionomicon",
      |  "authors": ["John A. De Goes" "Adam Fraser"],
      |  "price": 49.99
      |}""".stripMargin
  println(Json.parse(text))
  // Left(zio.blocks.schema.SchemaError: expected ']' or ',' at: .authors)

  // a trailing comma: only the root is named
  println(Json.parse("""{"title": "Zionomicon", "price": 49.99,}"""))
  // Left(zio.blocks.schema.SchemaError: expected '"' at: .)
}
```

**Expected.** The message includes the position, for example `expected ']' or ',' at: .authors (line 3, column 34)`, or the `SchemaError` exposes the offset.

**Root cause.** `schema/jvm/src/main/scala/zio/blocks/schema/json/JsonReader.scala:1657` raises `new JsonCodecError(Nil, msg)` with the message alone; the reader knows `pos` at that point but drops it. `JsonCodec.scala:515-540` turns the error into `SchemaError.ExpectationMismatch(path, msg)` (`SchemaError.scala:179-181`), which has no place for a position.

**Impact.** Debugging malformed payloads (webhooks, config files, hand-written fixtures) takes longer than with other JSON libraries, which all report a position.

**Test gap.** `JsonSpec.scala:1356-1365` asserts only `isLeft` for malformed input; no test asserts a position.

**Suggested fix.** Append the byte offset (and, for `String` input, line and column) to the message in `decodeError`, or add a position to `ExpectationMismatch`. The same change helps `ConfigSource.fromJson` (see the config-hocon entry above).

### schema: `Json#project` with a path that matches nothing returns `null`

Found 2026-10-21 while writing the how-to guide `docs/guides/json-without-case-classes.md` (backlog H-06), on `main` at `0740013c`. The code is the same at tag `v0.0.56` (no file under `schema/` outside the tests changed since the tag). Verified. Not filed.

**Summary.** `Json#project(paths*)` keeps only the given paths. When none of them exists, it returns JSON `null` instead of an empty object (or an error). With at least one matching path the result is an object, so the return value changes kind depending on the data.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-schema` 0.0.56, run with `scala-cli run ProjectMissing.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
import zio.blocks.schema.*
import zio.blocks.schema.json.*

@main def run(): Unit = {
  val book = Json.parseUnsafe("""{"title": "Zionomicon", "tags": []}""")
  println(book.project(p".title").print)
  // {"title":"Zionomicon"}
  println(book.project(p".isbn").print)
  // null
  println(book.project(p".title", p".isbn").print)
  // {"title":"Zionomicon"}
}
```

**Expected.** `{}` for `project(p".isbn")` on an object, so callers can always treat the result as an object.

**Root cause.** `schema/shared/src/main/scala/zio/blocks/schema/json/Json.scala:390` delegates to `Json.project` (`:1237-1241`), which rebuilds the result with `fromKVUnsafe` from the matched `(path, value)` pairs. `fromKVUnsafe` (`:1466-1470`) returns `Null` for an empty list and folds from `Null` otherwise, so with no match the result is `null`.

**Impact.** Code that projects a payload and then reads from the result (`projected.get("title")`) fails with `Cannot get field 'title' from non-object JSON value` instead of a not-found error.

**Test gap.** The `project` tests in `JsonSpec.scala` use paths that exist.

**Suggested fix.** Start from an empty container of the root's kind, or return `Json.Object.empty` when the root is an object and nothing matched.

### schema: `Json#delete` and `Json#insert` ignore a quoted-key path step, which `get`, `set` and `modify` follow

Found 2026-10-21 while writing the how-to guide `docs/guides/json-without-case-classes.md` (backlog H-06), on `main` at `0740013c`, during the docs-reviewer fact-check. The code is the same at tag `v0.0.56`. Verified. Not filed.

**Summary.** A JSON key that is not a Scala identifier, such as `first-edition`, can only be written in `p"..."` as a quoted key, `p"""{"first-edition"}"""`, which parses to a `DynamicOptic.Node.AtMapKey` step. `Json#get`, `Json#set` and `Json#modify` treat that step as an object key. `Json#delete` and `Json#insert` silently return the document unchanged, `Json#deleteOrFail` reports `Path not found` for a key that exists, and `Json#insertOrFail` reports `Insert not supported for path node type: AtMapKey(...)`. The same key built with `DynamicOptic.root.field(...)` works with every method.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-schema` 0.0.56, run with `scala-cli run QuotedKeyPath.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
import zio.blocks.schema.*
import zio.blocks.schema.json.*

@main def run(): Unit = {
  val book = Json.parseUnsafe("""{"title": "Zionomicon", "first-edition": 2021}""")
  // the only way to name a non-identifier key in p"..." is the quoted-key form
  val edition = p"""{"first-edition"}"""

  // get, set and modify follow it
  println(book.get(edition).as[Int])
  // Right(2021)
  println(book.set(edition, Json.Number(2022)).print)
  // {"title":"Zionomicon","first-edition":2022}

  // delete ignores it, and deleteOrFail says the existing key is not there
  println(book.delete(edition).print)
  // {"title":"Zionomicon","first-edition":2021}
  println(book.deleteOrFail(edition))
  // Left(zio.blocks.schema.SchemaError: Path not found: {"first-edition"})

  // insert ignores it, and insertOrFail rejects the node
  println(book.insert(p"""{"second-edition"}""", Json.Number(2025)).print)
  // {"title":"Zionomicon","first-edition":2021}
  println(book.insertOrFail(p"""{"second-edition"}""", Json.Number(2025)))
  // Left(zio.blocks.schema.SchemaError: Insert not supported for path node type: AtMapKey("second-edition"))

  // the same keys as field nodes work with every method
  println(book.delete(DynamicOptic.root.field("first-edition")).print)
  // {"title":"Zionomicon"}
  println(book.insert(DynamicOptic.root.field("second-edition"), Json.Number(2025)).print)
  // {"title":"Zionomicon","first-edition":2021,"second-edition":2025}
}
```

**Expected.** `delete` removes `first-edition` and `insert` adds `second-edition`, as `get`, `set` and `modify` already treat the step as an object key; or all five methods reject `AtMapKey` on a `Json` the same way.

**Root cause.** `schema/shared/src/main/scala/zio/blocks/schema/json/Json.scala:1600-1623` (`getAtPath`) and `:1747-1768` (`modifyAtPathRecursive`) handle `AtMapKey` with a string key as an object field. `deleteAtPathRecursive` falls through to `case _ => None` at `:1989`; the comment at `:1987` calls `AtMapKey` an "architectural no-op" because "Json has no Map/Variant types", which contradicts the `get`/`modify` handling. The insert helper rejects it at `:2126`. `Json#deleteOrFail` turns the `None` into `Path not found` (`:173`).

**Impact.** A guide or user who removes a field whose name has a dash (common in HTTP-style payloads, for example `content-type`) with the path syntax gets no change and no error, while reads and updates on the same path work.

**Test gap.** `schema/shared/src/test/scala/zio/blocks/schema/json/JsonSpec.scala:1902-1929` ("AtMapKey operations") tests `get` and `modify` only; no test deletes or inserts through `AtMapKey`.

**Suggested fix.** Handle `AtMapKey` with a string key in `deleteAtPathRecursive` and in the insert helpers the same way as `Field`, and add delete/insert cases to the "AtMapKey operations" suite.

### schema: `DynamicPatch#toString` and `JsonPatch#toString` don't indent a multi-line value added to a map or object

Found 2026-10-21 while writing H-07 (`docs/guides/diff-and-patch.md`), on `main` at `7d8e0b44`. Verified on Scala 2.13.18 (`zio-blocks-schema` 0.0.56) and Scala 3.9.0 (`zio-blocks-next-schema` 0.0.56, `-experimental`); the renderers are the same at tag `v0.0.56`.

**Summary.** When a patch adds a map entry (or a JSON object key) whose value is a record, `toString` appends the value's own multi-line rendering without indenting it. The value's lines start at column 0, and the closing `}}` is not aligned with the `+`, so the patch is hard to read. The guide's catalog patch shows it.

**Reproduction** (run with scala-cli; the same output on both versions, the Scala 3 file uses `//> using scala 3.9.0`, `//> using options -experimental` and `zio-blocks-next-schema`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.json._
import zio.blocks.schema.patch._

final case class Book(title: String, tags: List[String])
object Book { implicit val schema: Schema[Book] = Schema.derived }
final case class Catalog(books: Map[String, Book])
object Catalog { implicit val schema: Schema[Catalog] = Schema.derived }

object Main extends App {
  val zio    = Book("Zionomicon", List("zio"))
  val pis    = Book("Programming in Scala", List("scala"))
  val before = Catalog(Map("zionomicon" -> zio))
  val after  = Catalog(Map("zionomicon" -> zio, "programming-in-scala" -> pis))
  println(Catalog.schema.diff(before, after))
  // DynamicPatch {
  //   .books:
  //     + {"programming-in-scala": {
  //   title: "Programming in Scala",
  //   tags: ["scala"]
  // }}
  // }
  println(Json.from(before).diff(Json.from(after)))
  // JsonPatch {
  //   root:
  //     ~ {"books"}:
  //       root:
  //         + {"programming-in-scala": {
  //   "title": "Programming in Scala",
  //   "tags": [
  //     "scala"
  //   ]
  // }}
  // }
}
```

**Expected.** The value's lines are indented under the `+` line, for example `    + {"programming-in-scala": {` followed by `        title: "Programming in Scala",`.

**Root cause.** `DynamicPatch.renderMapOp` appends `a.value` (the `DynamicValue`'s multi-line `toString`) as is (`schema/shared/src/main/scala/zio/blocks/schema/patch/DynamicPatch.scala:213`); `JsonPatch.renderObjectOp` does the same with the `Json` value (`schema/shared/src/main/scala/zio/blocks/schema/json/JsonPatch.scala:683-684`). The sequence renderers (`renderSeqOp`) have the same pattern for inserted records.

**Impact.** Cosmetic, but patches are printed for audit logs and debugging, and a catalog-sized patch becomes hard to read.

**Test gap.** `DynamicPatchSpec` "DynamicPatch.toString" (`DynamicPatchSpec.scala:1626-1654`) asserts only with `.contains` on primitive deltas; no test renders a map or sequence insert of a record.

**Suggested fix.** Render the value on one line (`Json#print` / a compact `DynamicValue` rendering), or indent every line after the first by the current indent.

### schema (Scala 3): `BindingResolver.reflection` reads the wrong fields of a case class that extends a trait with a `val`

Found 2026-10-21 while writing the Scaladoc of the `binding` package (wave 3 of the schema Scaladoc, branch `scaladoc-wave-3`, on top of `5fd81ef4`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run Repro.scala` against the published `0.0.56` on Scala 3.3.7 and 2.13.18.

**Summary.** On the JVM, `BindingResolver.reflection` builds a case class's deconstructor from the first N entries of `Class#getDeclaredFields`, where N is the number of constructor parameters. On Scala 3, the field of a `val` that the case class inherits from a trait comes first in that array, so the deconstructor reads the trait's field in place of the first constructor field and shifts the others by one. When the types happen to match, encoding silently writes the wrong values; otherwise it throws `IllegalArgumentException`. Decoding is not affected, because the constructor takes its arguments from the registers in parameter order. On Scala 2 the trait's field comes after the constructor fields, so the same code works.

**Reproduction** (Scala 3.3.7, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.binding._

trait Catalogued { val catalogue: String = "Scala books" }

case class Book(title: String, author: String) extends Catalogued
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

case class Edition(title: String, year: Int) extends Catalogued
object Edition {
  implicit val schema: Schema[Edition] = Schema.derived
}

object Main {
  def main(args: Array[String]): Unit = {
    val resolver = BindingResolver.reflection ++ BindingResolver.defaults
    val book     = Book.schema.toDynamicSchema.rebind[Book](resolver)
    println(book.toDynamicValue(Book("Zionomicon", "John A. De Goes")))
    println(book.fromDynamicValue(Schema[Book].toDynamicValue(Book("Zionomicon", "John A. De Goes"))))
    val edition = Edition.schema.toDynamicSchema.rebind[Edition](resolver)
    println(scala.util.Try(edition.toDynamicValue(Edition("Zionomicon", 2021))))
  }
}
// {
//   title: "Scala books",
//   author: "Zionomicon"
// }
// Right(Book(Zionomicon,John A. De Goes))
// Failure(java.lang.IllegalArgumentException: Attempt to get java.lang.String field "Edition.title" with illegal data type conversion to int)
```

Still present on `main` (checked 2026-10-22 at `bffadae7` plus the wave 3 commits, with a local publish of `zio-blocks-schema` built with Scala 3.9.0, consumed on 3.9.0 with `-experimental`): the output is the same three lines.

The same file with `//> using scala 2.13.18` prints `{ title: "Zionomicon", author: "John A. De Goes" }`, the same `Right(...)`, and `Success({ title: "Zionomicon", year: 2021 })`. On Scala 3.3.7, `classOf[Edition].getDeclaredFields.map(_.getName)` is `catalogue, title, year`.

**Expected.** The rebound schemas encode `Book("Zionomicon", "John A. De Goes")` as `{ title: "Zionomicon", author: "John A. De Goes" }` and `Edition("Zionomicon", 2021)` as `{ title: "Zionomicon", year: 2021 }`, as on Scala 2.

**Root cause.** `schema/jvm/src/main/scala/zio/blocks/schema/binding/BindingResolverPlatformSpecific.scala:127` takes `clazz.getDeclaredFields.take(paramCount)`, and the deconstructor reads `fields(idx)` for parameter `idx` (`:162`-`:173`). `getDeclaredFields` returns the fields in no particular order (its Javadoc says so), and the Scala 3 compiler emits the fields of mixed-in trait `val`s first. The constructor is also picked as `clazz.getConstructors` head (`:121`), which is likewise not ordered.

**Impact.** Any Scala 3 case class that mixes in a trait with a `val` (a common way to share constants) is encoded wrongly, or fails to encode, after `DynamicSchema#rebind` with `BindingResolver.reflection`. When the types line up there is no error at all.

**Test gap.** `BindingResolverSpec` (`schema/shared/src/test/scala/zio/blocks/schema/BindingResolverSpec.scala:230-297`) round-trips only case classes without a parent trait.

**Suggested fix.** Read each field by its constructor parameter: for a case class, deconstruct through `Product#productElement(idx)`, which follows the parameter order, or look the fields up by the parameter names. Pick the constructor whose parameters match the case class's fields instead of the first one, and add a Scala 3 test with a trait that has a `val`.

### schema: `JsonCodec#encode` into a too-small `ByteBuffer` throws after writing part of the value, and leaves `position` past the partial JSON

Found 2026-10-21 while writing the how-to guide `docs/guides/tune-json-throughput.md` (backlog H-50), on `main` at `7e8ac787`. `JsonCodec`, `JsonReader`, `JsonWriter`, `ReaderConfig`, `WriterConfig` and `Schema` are the same at tag `v0.0.56`. Verified by running against the published 0.0.56. Not filed on GitHub yet.

**Summary.** `JsonCodec#encode(value, output: ByteBuffer)` throws `java.nio.BufferOverflowException` when the value doesn't fit, which is the JDK convention. The JDK's own bulk `ByteBuffer#put` also leaves the buffer unchanged when it throws: `position` stays where it was. The codec doesn't. For a heap buffer it has already written the bytes that fit into the backing array and moves `position` to the end of them. For a direct buffer and a value larger than the writer's internal buffer (`WriterConfig#preferredBufSize`, 32 KiB by default), it has already copied whole chunks into the buffer, and `position` is left after them. Either way the buffer now ends with a truncated JSON value, and a caller that catches the exception and moves on, for example to flush the buffer and retry, sends the fragment.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56, `scala-cli run`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56

import java.nio.{BufferOverflowException, ByteBuffer}
import zio.blocks.schema._

case class Book(title: String, authors: List[String], year: Int)
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

object OverflowRepro {
  def attempt(buffer: ByteBuffer, value: Book): Unit =
    try Book.schema.jsonCodec.encode(value, buffer)
    catch {
      case e: BufferOverflowException =>
        println(s"${e.getClass.getSimpleName}: position ${buffer.position()} of limit ${buffer.limit()}")
    }

  def main(args: Array[String]): Unit = {
    val book = Book("Zionomicon", List("John A. De Goes", "Adam Fraser"), 2024)
    println(s"JSON size: ${Book.schema.jsonCodec.encode(book).length}")

    val heap = ByteBuffer.allocate(20)
    attempt(heap, book)
    heap.flip()
    println("heap buffer now holds: " + new String(heap.array(), 0, heap.limit(), "UTF-8"))

    // The JDK's own bulk put leaves the position unchanged on overflow
    val jdk = ByteBuffer.allocate(20)
    try jdk.put(new Array[Byte](60))
    catch { case _: BufferOverflowException => println(s"ByteBuffer#put: position ${jdk.position()}") }

    // A direct buffer is written in chunks once the value is larger than the writer's 32 KiB buffer
    val big    = (1 to 8000).toList.map(i => s"Author $i")
    val large  = book.copy(authors = big)
    val size   = Book.schema.jsonCodec.encode(large).length
    val direct = ByteBuffer.allocateDirect(size - 1)
    println(s"large JSON size: $size")
    attempt(direct, large)
  }
}
// JSON size: 78
// BufferOverflowException: position 9 of limit 20
// heap buffer now holds: {"title":
// ByteBuffer#put: position 0
// large JSON size: 110939
// BufferOverflowException: position 98283 of limit 110938
```

A direct buffer and a value smaller than 32 KiB are not affected: the writer encodes into its own buffer and copies it in one `put` at the end, which throws before writing anything.

**Expected.** On overflow, `position` is where it was before the call, as with `ByteBuffer#put`, so the caller can flush or grow the buffer and encode again. At least the Scaladoc of `JsonCodec#encode(value, output: ByteBuffer)` should say that the buffer is left with partial output.

**Root cause.** Heap buffer: `JsonWriter.write(codec, x, bbuf, config)` writes straight into `bbuf.array` and, in its `finally` block, sets `bbuf.position(count - offset)` whether or not encoding succeeded (`schema/jvm/src/main/scala/zio/blocks/schema/json/JsonWriter.scala:1171-1188`); the `ArrayIndexOutOfBoundsException` from `flushAndGrowBuf` (`:2798`) becomes `BufferOverflowException` at `:1182`, after `count` has advanced. Direct buffer: `flushAndGrowBuf` calls `bbuf.put(buf, 0, pos)` each time the internal buffer fills (`:2790-2793`), so earlier chunks are already in the target when the last `put` (`:1197`) throws. `JsonCodec#encode(value, output, config)` delegates without a `try` (`schema/shared/src/main/scala/zio/blocks/schema/json/JsonCodec.scala:171-175`). The Scala.js writer has the same structure.

**Impact.** Code that encodes several messages into one reusable `ByteBuffer`, or that sizes a buffer optimistically and falls back on overflow, writes corrupt output unless it saves and restores `position` itself. The H-50 guide shows that workaround (`encodeOrRewind`).

**Test gap.** No test in `schema` encodes into a `ByteBuffer` that is too small; `BufferOverflowException` appears in no test file.

**Suggested fix.** Remember `bbuf.position()` on entry and restore it before rethrowing (heap path: set `bbuf.position(start)` in the `catch`; direct path: catch `BufferOverflowException` and reset to the saved position). Add tests for both buffer kinds, with values smaller and larger than `preferredBufSize`.

### schema: `ReaderConfig#maxBufSize` below 32 KiB depends on the thread's earlier calls, and can fail with an internal index error

Found 2026-10-21 while writing the how-to guide `docs/guides/tune-json-throughput.md` (backlog H-50), on `main` at `7e8ac787`. `JsonReader` and `ReaderConfig` are the same at tag `v0.0.56`. Verified by running against the published 0.0.56. Not filed on GitHub yet.

**Summary.** For `InputStream` and `ByteBuffer` input, `ReaderConfig#maxBufSize` caps how far the reader's byte buffer may grow, for example while it holds a whole object to find a discriminator field that comes last. The pooled reader of each thread starts with a 32768-byte buffer, and a call only ever grows that buffer to the config's preferred size, never shrinks it on entry. A `maxBufSize` smaller than the current buffer is therefore not applied: on a thread's first call, or after any call with a larger config, input that should hit the limit decodes successfully. When such input is also larger than the current buffer, the reader "grows" the buffer down to `maxBufSize` and fails with a leaked internal message (`Range [32768, 32768 + -30720) out of bounds for length 2048`) instead of `too long part of input exceeded 'maxBufSize'`. The same request can pass on one thread and fail on another.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56, `scala-cli run`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56

import java.io.ByteArrayInputStream
import zio.blocks.schema._
import zio.blocks.schema.json._

sealed trait Edition
object Edition {
  case class Print(isbn: String, pages: Int) extends Edition
  case class Ebook(url: String)              extends Edition
  implicit val schema: Schema[Edition] = Schema.derived
}

object MaxBufSizeRepro {
  // The discriminator comes last, so the reader must buffer the whole object
  def print(isbnLength: Int): Array[Byte] =
    s"""{"isbn":"${"9" * isbnLength}","pages":1,"type":"Print"}""".getBytes("UTF-8")

  def main(args: Array[String]): Unit = {
    val codec  = Edition.schema.derive(JsonCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("type")))
    val strict = ReaderConfig.withPreferredBufSize(1024).withMaxBufSize(2048)

    // The same 5 KB input and config, twice on one thread
    println(codec.decode(new ByteArrayInputStream(print(5000)), strict).isRight)
    println(codec.decode(new ByteArrayInputStream(print(5000)), strict))

    // A call with the default config, then a 40 KB input with the strict config
    println(codec.decode(new ByteArrayInputStream(print(5000))).isRight)
    println(codec.decode(new ByteArrayInputStream(print(40000)), strict))
  }
}
// true
// Left(zio.blocks.schema.SchemaError: too long part of input exceeded 'maxBufSize' at: .)
// true
// Left(zio.blocks.schema.SchemaError: Range [32768, 32768 + -30720) out of bounds for length 2048 at: .)
```

**Expected.** The first two calls give the same result (`Left` with `too long part of input exceeded 'maxBufSize'`), whatever the thread did before, and the last call fails with that message too.

**Root cause.** `JsonReader.read(codec, in, config)` reallocates the buffer only when it is smaller than `config.preferredBufSize` (`schema/jvm/src/main/scala/zio/blocks/schema/json/JsonReader.scala:1747`; the direct `ByteBuffer` path does the same at `:1809`), and shrinks it only in the `finally` block (`:1753`). `growBuf` (`:5061-5068`) raises `tooLongInputError` only when `buf.length == maxBufSize`; when the buffer is already larger, it doubles the length, clamps it to `maxBufSize` and copies the buffer into a smaller array, so the next `loadMore` read fails with `IndexOutOfBoundsException`, which `JsonCodec#decode` turns into a `Left` with the exception's message. The reader's buffer starts at 32768 bytes (`JsonReader.scala:62`).

**Impact.** Low for the defaults (32 MiB), but the setting exists to protect public endpoints, and a service that lowers it below 32 KiB gets a limit that applies only some of the time. The H-50 guide tells readers to keep `maxBufSize` at 32768 or more.

**Test gap.** `ReaderConfigSpec` checks the setters' validation only. No test decodes from a stream with a `maxBufSize` smaller than the reader's initial buffer, or on a fresh thread.

**Suggested fix.** On entry to the stream and direct-buffer `read` overloads, reallocate when `buf.length > config.maxBufSize` (not only when it is smaller than `preferredBufSize`), and make `growBuf` raise `tooLongInputError` when `bufLen >= maxBufSize`.

### config-hocon: a self-reference such as `path = ${path}":/opt/bookstore/bin"` is rejected as circular

Found 2026-10-21 while writing the how-to guide `docs/guides/load-configuration-files.md` (backlog H-34), on `main` at `7ebf5bca`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** The HOCON spec allows a key to refer to its own earlier value, the usual way to extend a default: `path = ${path}":/extra"`. Typesafe Config resolves it to the previous value plus the suffix. `config-hocon` fails the whole document with `Circular substitution detected`. A substitution of a different key works.

**Reproduction** (save as `Repro.sc` and run `scala-cli run Repro.sc`; the script has top-level statements):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-config-hocon:0.0.56
import zio.blocks.config._
import zio.blocks.config.hocon._

def show(doc: String): Unit =
  HoconParser.parse(doc) match {
    case Right(value) => println(HoconValue.flatten(value))
    case Left(error)  => println(error.getMessage)
  }

show("path = \"/usr/bin\"\npath = ${path}\":/opt/bookstore/bin\"")
show("base = \"/usr/bin\"\npath = ${base}\":/opt/bookstore/bin\"")
// Circular substitution detected for path: path (line 0, column 0)
// Map(base -> /usr/bin, path -> /usr/bin:/opt/bookstore/bin)
```

**Expected.** The first document resolves to `Map(path -> /usr/bin:/opt/bookstore/bin)`, as the HOCON spec's "Self-Referential Substitutions" section describes.

**Root cause.** Duplicate keys are merged first, so the later `path` replaces the earlier one in the tree, and substitutions are then resolved against that merged root (`config-hocon/shared/src/main/scala/zio/blocks/config/hocon/HoconParser.scala:620-631`). `${path}` looks up the new `path`, which is the substitution itself, and the `resolving` check reports a cycle (`HoconParser.scala:622-623`). The previous value is gone by then.

**Impact.** A common Typesafe Config idiom, extending a value from an included `defaults.conf`, stops the whole file from loading. `formats.md` says only that circular references are reported, so users don't know the self-referential form is unsupported.

**Test gap.** `HoconParserSpec` "circular substitution produces error" (`config-hocon/shared/src/test/scala/zio/blocks/config/hocon/HoconParserSpec.scala:386`) tests a real cycle; no test covers a self-reference to an earlier value.

**Suggested fix.** While merging, when a later value of a key contains a substitution of that same key, resolve it against the key's previous value (as the spec says), and only then replace it. Until then, document in `formats.md` that self-references are rejected and that `+=` works for lists.

### config-hocon: an undefined `${?path}` inside a concatenation becomes the text `null`

Found 2026-10-21 while writing the how-to guide `docs/guides/load-configuration-files.md` (backlog H-34), on `main` at `7ebf5bca`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** The HOCON spec says an undefined optional substitution inside a value concatenation contributes nothing, so `"jdbc:postgresql://localhost:5432/"${?db.name}` is `jdbc:postgresql://localhost:5432/` when `db.name` is not set, and Typesafe Config behaves that way. `config-hocon` inserts the four characters `null` instead. A key whose whole value is `${?path}` is left out correctly.

**Reproduction** (save as `Repro.sc` and run `scala-cli run Repro.sc`; the script has top-level statements):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-config-hocon:0.0.56
import zio.blocks.config._
import zio.blocks.config.hocon._

def show(doc: String): Unit =
  println(ConfigSource.fromHocon(doc).map(_.all("").map { case (k, v) => s"$k=${v.value}" }.toList.sorted))

show("db.password = ${?db.secret}\ndb.user = bookstore")
show("db.url = \"jdbc:postgresql://localhost:5432/\"${?db.name}")
show("db.url = \"jdbc:postgresql://localhost:5432/\"${?db.name}\"?ssl=true\"")
// Right(List(db.user=bookstore))
// Right(List(db.url=jdbc:postgresql://localhost:5432/null))
// Right(List(db.url=jdbc:postgresql://localhost:5432/null?ssl=true))
```

**Expected.** `db.url=jdbc:postgresql://localhost:5432/` and `db.url=jdbc:postgresql://localhost:5432/?ssl=true`.

**Root cause.** An undefined optional substitution resolves to `HoconValue.Null` (`config-hocon/shared/src/main/scala/zio/blocks/config/hocon/HoconParser.scala:629`). The `MConcat` case appends every part with `hoconValueToString` (`HoconParser.scala:634-644`), which renders `Null` as `"null"` (`HoconParser.scala:651`). `HoconValue.flatten` drops a `Null` only when it is the whole value.

**Impact.** A URL, path or name built from an optional part silently contains `null`, and the service connects to the wrong database or reads the wrong folder instead of failing.

**Test gap.** `HoconParserSpec` "optional substitution resolves to null when absent" (`config-hocon/shared/src/test/scala/zio/blocks/config/hocon/HoconParserSpec.scala:378`) tests a whole-value substitution only.

**Suggested fix.** In the `MConcat` case, skip a part that resolved from an undefined optional substitution, and add a concatenation case to the spec.

### config-hocon: `HoconParser.parse` drops an `include` inside an included resource

Found 2026-10-21 while writing the how-to guide `docs/guides/load-configuration-files.md` (backlog H-34), on `main` at `7ebf5bca`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `HoconParser.parse(input, includeCallback)` is the only public way to resolve includes without a filesystem (from the classpath, from files embedded in a Scala.js bundle, or in tests). It resolves the includes of the root document only. An `include` inside text the callback returned is skipped without an error, even when the callback can supply that file.

**Reproduction** (save as `Repro.sc` and run `scala-cli run Repro.sc`; the script has top-level statements):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-config-hocon:0.0.56
import zio.blocks.config.hocon._

val files = Map(
  "db.conf"   -> "url = \"jdbc:postgresql://localhost:5432/bookstore\"\ninclude \"pool.conf\"",
  "pool.conf" -> "poolSize = 10"
)

println(HoconParser.parse("db { include \"db.conf\" }", files.get).map(HoconValue.flatten))
println(HoconParser.parse("db { include \"pool.conf\" }", files.get).map(HoconValue.flatten))
// Right(Map(db.url -> jdbc:postgresql://localhost:5432/bookstore))
// Right(Map(db.poolSize -> 10))
```

**Expected.** The first call returns `Map(db.url -> ..., db.poolSize -> 10)`: the callback can supply `pool.conf`, so the nested include is resolved with it, as `ConfigSource.fromFile` resolves nested includes from disk.

**Root cause.** `HoconParser.parse` wraps each callback result as `IncludedResource(content)` (`config-hocon/shared/src/main/scala/zio/blocks/config/hocon/HoconParser.scala:44`), whose `includeResolver` defaults to `_ => None` (`HoconParser.scala:30`). `handleInclude` parses the included text with that resolver (`HoconParser.scala:445-447`), so every nested include resolves to `None` and is ignored (`HoconParser.scala:452`). The JVM file loader passes a real nested resolver through the `private[hocon]` `parseWithResolver`, which is why nesting works there.

**Impact.** A split configuration loaded from the classpath or from a browser bundle silently loses every file that is included from an included file. The missing keys surface later as `Missing required key` errors, far from the include.

**Test gap.** `HoconParserSpec` "includes content via callback" (`HoconParserSpec.scala:531`) has one level of include only.

**Suggested fix.** Wrap the callback recursively: `resource => includeCallback(resource).map(content => IncludedResource(content, nested))`, where `nested` is the same wrapper, and count depth as `fromFile` does to stop cycles. Alternatively make `parseWithResolver` public.

### config-hocon: `ConfigSource.fromFile` looks for an absolute `include` path inside the including folder, so the include is silently skipped

Found 2026-10-21 while writing the how-to guide `docs/guides/load-configuration-files.md` (backlog H-34), on `main` at `7ebf5bca`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `include "/etc/bookstore/db.conf"` in a file loaded with `ConfigSource.fromFile` is resolved as `<folder of the including file>/etc/bookstore/db.conf`. That file normally doesn't exist, so the include is skipped like any missing include, and the configuration loads without the included keys and without an error. Typesafe Config reads an absolute include path as given.

**Reproduction** (save as `Repro.sc` and run `scala-cli run Repro.sc`; the script has top-level statements):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-config-hocon:0.0.56
import java.nio.file.Files
import zio.blocks.config._
import zio.blocks.config.hocon._

val shared  = Files.createTempDirectory("shared")
val dbConf  = Files.write(shared.resolve("db.conf"), "db.url = \"jdbc:postgresql://localhost:5432/bookstore\"".getBytes("UTF-8"))
val appDir  = Files.createTempDirectory("app")
val appConf = Files.write(appDir.resolve("application.conf"), s"include \"$dbConf\"\ncatalog.pageSize = 20".getBytes("UTF-8"))

println(Files.exists(dbConf))
println(ConfigSource.fromFile(appConf.toString).map(_.all("").keys.toList.sorted))
// true
// Right(List(catalog.pageSize))
```

**Expected.** `Right(List(catalog.pageSize, db.url))`: the absolute path is read as given, and, when `allowedBase` is set, checked against it like any other include.

**Root cause.** The include resolver builds the file with `new java.io.File(baseDir, resource)` (`config-hocon/jvm/src/main/scala/zio/blocks/config/hocon/HoconConfigSourcePlatform.scala:122`). For an absolute `resource`, `java.io.File(File, String)` treats it as relative to `baseDir` on Unix, so the path is joined to the folder instead of replacing it.

**Impact.** A configuration that keeps shared settings in a fixed system path loads without them and without any error; the missing keys show up as `Missing required key` errors or, worse, are filled in by defaults.

**Test gap.** `HoconConfigSourcePlatformSpec` has no test with an absolute include path.

**Suggested fix.** Resolve with `baseDir.toPath.resolve(resource)` (which returns an absolute `resource` unchanged) before canonicalizing, so the `allowedBase` check still applies to the result.

### `YamlReader` stops at the first line it cannot read and drops the rest of the document without an error

Found 2026-10-21 while writing the how-to guide `docs/guides/load-configuration-files.md` (backlog H-34), on `main` at `7ebf5bca`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `zio.blocks.schema.yaml.YamlReader` stops parsing a block mapping at the first line that isn't a `key: value` entry at the expected indentation, and returns what it has read so far. The lines after it are never looked at, and no error is raised. A key missing the space after its colon (`pageSize:20`), a line indented one space too far, or a stray plain line each drop the rest of the document. `ConfigSource.fromYaml` in `config-yaml` uses this reader, so a typo in a YAML configuration file removes every setting after it. This is separate from the unterminated flow collection entry above.

**Reproduction** (save as `Repro.sc` and run `scala-cli run Repro.sc`; the script has top-level statements):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-config-yaml:0.0.56
import zio.blocks.config._
import zio.blocks.config.yaml._

def keys(yaml: String): Unit =
  println(ConfigSource.fromYaml(yaml).map(_.all("").keys.toList.sorted))

keys("catalog:\n  featuredBook: Zionomicon\n  pageSize: 20\ndb:\n  url: jdbc:postgresql://localhost:5432/bookstore\n")
keys("catalog:\n  featuredBook: Zionomicon\n  pageSize:20\ndb:\n  url: jdbc:postgresql://localhost:5432/bookstore\n")
keys("catalog:\n  featuredBook: Zionomicon\n   pageSize: 20\ndb:\n  url: jdbc:postgresql://localhost:5432/bookstore\n")
keys("catalog:\n  featuredBook: Zionomicon\n  Programming in Scala\ndb:\n  url: jdbc:postgresql://localhost:5432/bookstore\n")
keys("- Zionomicon\ndb:\n  url: jdbc:postgresql://localhost:5432/bookstore\n")
// Right(List(catalog.featuredBook, catalog.pageSize, db.url))
// Right(List(catalog.featuredBook))
// Right(List(catalog.featuredBook))
// Right(List(catalog.featuredBook))
// Right(List(0))
```

**Expected.** The first document loads as shown. Each of the other four is a YAML syntax error, so `fromYaml` returns `Left(ConfigError.ParseError(...))` instead of a source with the rest of the file missing.

**Root cause.** `parseBlockMapping` returns its entries as soon as a line has a different indentation (`schema-yaml/shared/src/main/scala/zio/blocks/schema/yaml/YamlReader.scala:107`) or no mapping colon followed by a space (`YamlReader.scala:112`). `YamlParser#parse` returns the value of `parseValue(0)` (`YamlReader.scala:36-48`) without checking that every line was consumed, so the unread lines are dropped silently.

**Impact.** A one-character typo in a YAML configuration file loads as a valid, smaller configuration. Settings with defaults then take their defaults without a trace; only settings without a default show up, as `Missing required key` errors that name the key, not the line.

**Test gap.** `YamlReaderSpec` has no test that malformed block YAML is rejected or that the whole input is consumed.

**Suggested fix.** At the end of `YamlParser#parse`, skip blank and comment lines and throw a parse error naming the line number if any line is left. Inside `parseBlockMapping`, raise an error for a line at greater indentation than the mapping that doesn't belong to a nested value.

### config: `ProvenanceMap` prints the raw value of a `Secret` field

Found 2026-10-21 while writing the how-to guide `docs/guides/load-configuration-files.md` (backlog H-34), on `main` at `7ebf5bca`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** A field typed `Secret` prints as `<secret>` in the decoded case class, but the provenance of the same load shows its value in clear: `ProvenanceMap#provenanceOf` returns a `Provenance.Resolved` whose `toString` includes the raw text, and `ProvenanceMap#dump` hides a value only when its key name looks sensitive, whatever the field's type. `docs/reference/config/config-source.md` ("Redaction is name-based only") tells readers to "use `Secret`" for "values that must never be printed regardless of key", which `dump` does not honour.

**Reproduction** (save as `Repro.sc` and run `scala-cli run Repro.sc`; the script has top-level statements):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-config:0.0.56
import zio.blocks.config._
import zio.blocks.schema.Schema

final case class DbConfig(url: String, signingKeyBlob: Secret)
object DbConfig {
  implicit val schema: Schema[DbConfig] = Schema.derived
}

val source = ConfigSource.fromMap(Map("url" -> "jdbc:postgresql://localhost:5432/bookstore", "signingKeyBlob" -> "hunter2"), "file")
val loaded = Config.loadWithProvenance[DbConfig](source).toOption.get

println(loaded.value)
println(loaded.provenanceOf("signingKeyBlob"))
println(loaded.dump())
// DbConfig(jdbc:postgresql://localhost:5432/bookstore,<secret>)
// Resolved(file,signingKeyBlob,hunter2)
// ┌────────────────┬────────────────────────────────────────────┬────────┐
// │ Key            │ Value                                      │ Source │
// ├────────────────┼────────────────────────────────────────────┼────────┤
// │ signingKeyBlob │ hunter2                                    │ file   │
// │ url            │ jdbc:postgresql://localhost:5432/bookstore │ file   │
// └────────────────┴────────────────────────────────────────────┴────────┘
```

The same happens with a sensitive name: `provenanceOf("db.password")` on a layered `EnvSource` prints `Resolved(env,BOOKSTORE_DB_PASSWORD,<the password>)`, although `dump()` hides that row.

**Expected.** Every value read for a `Secret` field is shown as `<secret>`, in `dump()` and in the `Provenance` returned by `provenanceOf`, as `config-source.md` promises.

**Root cause.** `ProvenanceMap` holds only the source, not the schema, so `dump` can redact by key name alone (`config/shared/src/main/scala/zio/blocks/config/ProvenanceMap.scala:27-30`, used at `:48`). `provenanceOf` returns the source's `Provenance` unchanged (`ProvenanceMap.scala:35-36`), and `Provenance.Resolved` is a case class with the raw value as a field (`config/shared/src/main/scala/zio/blocks/config/Provenance.scala:40`), so its default `toString` prints it.

**Impact.** A startup log line that prints provenance, the module's recommended way to debug a deployment, leaks secrets that the user had protected with the `Secret` type.

**Test gap.** `ConfigSpec` "dump redacts sensitive values by key name" (`config/shared/src/test/scala/zio/blocks/config/ConfigSpec.scala:177`) checks a sensitive key name only; no test loads a `Secret` field under a neutral name, and none checks `provenanceOf` output.

**Suggested fix.** Have `Config.loadWithProvenance` record the paths of `Secret` fields (it has the schema) and pass them to `ProvenanceMap`, so `dump` and `provenanceOf` redact them. Override `Provenance.Resolved#toString` to omit `rawValue`, or redact it when the key is sensitive. Until then, correct the advice in `config-source.md`.

### schema-yaml: `YamlReader` silently misreads or drops valid YAML that it does not support

Found 2026-10-21 while writing the how-to guide `docs/guides/read-and-write-yaml.md` (backlog H-15), on `main` at `4ec6dd90`. `schema-yaml` has no changes since tag `v0.0.56`, so the behaviour is the same in the 0.0.56 release. Verified by running.

**Summary.** `YamlReader.read` never fails, so YAML forms it does not implement are not rejected: they are misread, or they end the document early, and the rest of the file is dropped without an error. This is separate from the "stops at the first line it cannot read" entry (typos such as `pageSize:20`): every input below is valid YAML that other readers accept. The affected forms are common in real files: a block sequence at the same indentation as its key is the usual Kubernetes and docker-compose style, anchors and merge keys are how CI files share defaults, and multi-document streams are how `kubectl` files bundle several objects.

**Reproduction** (scala-cli, run exactly as written; the output is pasted at the end):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema-yaml:0.0.56
import zio.blocks.schema.yaml.YamlReader

@main def unsupportedYaml(): Unit = {
  // 1. A sequence at the same indentation as its key (the usual Kubernetes and docker-compose style)
  println(YamlReader.read("steps:\n- name: checkout\n- name: test\ntimeout: 30\n"))
  // 2. A sequence nested directly in a sequence
  println(YamlReader.read("matrix:\n  - - '2.13'\n    - '3.3'\n  - - jdk17\nfail-fast: true\n"))
  // 3. An anchor on a block mapping, then a merge key
  println(YamlReader.read("defaults: &defaults\n  image: sbt:1.10\n  timeout: 30\ntest:\n  <<: *defaults\n  script: sbt test\n"))
  // 4. A stream of two documents
  println(YamlReader.read("title: Zionomicon\n---\ntitle: Programming in Scala\n"))
  // 5. Anchors, aliases and tags on scalars
  println(YamlReader.read("author: &a John A. De Goes\nreviewer: *a\nisbn: !!str 9780986200406\n"))
  // 6. A tab used for indentation
  println(YamlReader.read("db:\n\turl: jdbc:postgresql://localhost/books\nport: 5432\n"))
}
// Output:
// Mapping(Chunk((Scalar(steps,None),NullValue),(Scalar(- name,None),Scalar(checkout,None)),(Scalar(- name,None),Scalar(test,None)),(Scalar(timeout,None),Scalar(30,None))))
// Mapping(Chunk((Scalar(matrix,None),Sequence(Chunk(Scalar(- '2.13',None))))))
// Mapping(Chunk((Scalar(defaults,None),Scalar(&defaults,None))))
// Mapping(Chunk((Scalar(title,None),Scalar(Zionomicon,None))))
// Mapping(Chunk((Scalar(author,None),Scalar(&a John A. De Goes,None)),(Scalar(reviewer,None),Scalar(*a,None)),(Scalar(isbn,None),Scalar(!!str 9780986200406,None))))
// Mapping(Chunk((Scalar(db,None),NullValue),(Scalar(url,None),Scalar(jdbc:postgresql://localhost/books,None)),(Scalar(port,None),Scalar(5432,None))))
```

**Expected.** Either the YAML 1.2 meaning, or a parse error that names the unsupported construct and its line:

1. `steps: [{name: checkout}, {name: test}]` plus `timeout: 30`, not `steps: null` followed by two keys named `- name`.
2. `matrix: [['2.13', '3.3'], [jdk17]]` plus `fail-fast: true`.
3. `test` with `image`, `timeout` and `script` (the merge key), and `defaults` as a mapping.
4. Both documents (for example a `readAll` that returns a `Chunk[Yaml]`), or an error when `read` meets a second document.
5. `reviewer: John A. De Goes` and `isbn: "9780986200406"` (a string), not the anchor, alias and tag kept as text.
6. A parse error: YAML forbids tabs for indentation. Today `url` silently moves up to the root mapping.

**Root cause.** In `schema-yaml/shared/src/main/scala/zio/blocks/schema/yaml/YamlReader.scala`:
- Indentless sequence: `parseBlockMapping` parses a value on the following lines only when they are indented further than the key (`:120-123`, `currentIndent > indent` at `:122`); otherwise the value is `NullValue`. The next line, `- name: checkout`, is then at the mapping's own indentation, and `findMappingColon` (`:85-101`) finds the colon after `- name`, so it becomes a key.
- Anchor on a block value: `&defaults` is not empty, so it is read as an inline scalar (`:128`, `parseInlineValue` at `:445-453`), and the indented block that follows makes `parseBlockMapping` return (`:107-108`).
- Nested sequence: the output shows the first item read as the plain scalar `- '2.13'` and the sequence ending at the next line; the branch inside `parseBlockSequence` (`:136-212`) was not traced.
- Multi-document: `parse()` (`:36-48`) skips one leading `---`, returns the value of `parseValue(0)`, and never looks at the remaining lines.
- Anchors, aliases and tags: `parseInlineValue` (`:445-453`) has no case for `&`, `*` or `!`. `YamlTag.fromString` (`YamlTag.scala`) exists but no reader code calls it.
- Tabs: `countLeadingSpaces` (`:62-66`) counts only spaces, so a tab-indented line has indentation 0.

**Impact.** Silent data loss or silently wrong structure for real-world YAML. A Kubernetes manifest with `containers:` followed by `- name: ...` at the same indentation loses its containers, and every key after them. `config-yaml` uses this reader, so the same files load as smaller configurations. A typed decode reports the problem only when a required field happens to be in the lost part, and then names the field, not the line.

**Test gap.** `YamlReaderSpec` has no test for an indentless sequence, a nested sequence, an anchor or alias, a merge key, a tag, a second document or a tab, and no test that the whole input was consumed.

**Suggested fix.** Treat a `- ` line at the key's own indentation as the key's sequence value (as YAML allows). Parse `- - ` items recursively. Either implement anchors, aliases, merge keys and tags, or reject them with a parse error naming the line. After `parse()`, fail if any non-blank, non-comment line is left (or add a multi-document `readAll`). Reject tab indentation.

### schema-yaml: `YamlReader` drops the final line break of `|` and `>` blocks and ignores chomping indicators

Found 2026-10-21 while writing the how-to guide `docs/guides/read-and-write-yaml.md` (backlog H-15), on `main` at `4ec6dd90`. `schema-yaml` has no changes since tag `v0.0.56`, so the behaviour is the same in the 0.0.56 release. Verified by running.

**Summary.** YAML's default "clip" chomping keeps one final line break in a literal (`|`) or folded (`>`) block, `-` (strip) removes it, and `+` (keep) keeps all trailing line breaks. `YamlReader` removes the last line break in every case, so `|` reads like `|-`, and `|+` loses one of its line breaks. A shell script in a CI `run: |` block, a PEM certificate or a Markdown description read through `YamlReader` therefore differs from what every other YAML reader returns.

**Reproduction** (scala-cli, run exactly as written; the output is pasted at the end):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema-yaml:0.0.56
import zio.blocks.schema.yaml.{Yaml, YamlReader}

@main def blockScalars(): Unit = {
  def value(text: String): String = YamlReader.read(text) match {
    case Yaml.Mapping(entries) => entries.head._2 match {
      case Yaml.Scalar(v, _) => v.replace("\n", "\\n")
      case other             => other.toString
    }
    case other => other.toString
  }
  println(value("run: |\n  sbt compile\n  sbt test\n"))
  println(value("run: |-\n  sbt compile\n  sbt test\n"))
  println(value("run: |+\n  sbt compile\n  sbt test\n\n"))
  println(value("run: >\n  sbt compile\n  sbt test\n"))
}
// Output:
// sbt compile\nsbt test
// sbt compile\nsbt test
// sbt compile\nsbt test\n
// sbt compile sbt test
```

**Expected.** `sbt compile\nsbt test\n` for `|`, `sbt compile\nsbt test` for `|-`, `sbt compile\nsbt test\n\n` for `|+`, and `sbt compile sbt test\n` for `>`.

**Root cause.** `schema-yaml/shared/src/main/scala/zio/blocks/schema/yaml/YamlReader.scala`: the block header is only checked for its first character (`:124-127`, `afterColon.charAt(0) == '|'`), so the chomping and indentation indicators are never read. `parseLiteralBlockContent` (`:287-322`) always strips one trailing `'\n'` (`:304-305` and `:319-320`).

**Impact.** Small but silent changes to multi-line strings: a value that must end in a newline (a certificate, a file body, a script that is concatenated with another) no longer does. Round trips through other tools change the text.

**Test gap.** `YamlReaderSpec` asserts the stripped form for a plain `|` block (`"line1\nline2"`), which is the bug, and tests `|+` only with `isRight`.

**Suggested fix.** Parse the block header's chomping (`-`, `+`) and indentation indicators, keep one final line break by default, all of them for `+`, none for `-`. Update the existing test to the YAML result.

### schema-yaml: `YamlReader` forgets which scalars were quoted, so `Yaml#print` quotes every number it read and `Yaml#toJson` turns quoted strings into numbers and booleans

Found 2026-10-21 while writing the how-to guide `docs/guides/read-and-write-yaml.md` (backlog H-15), on `main` at `4ec6dd90`. `schema-yaml` has no changes since tag `v0.0.56`, so the behaviour is the same in the 0.0.56 release. Verified by running.

**Summary.** `YamlReader` keeps the text of each scalar but not whether it was quoted, and it never sets a `YamlTag`. Two things go wrong as a result. Printing a tree that was read from text quotes every number and boolean (`replicas: 3` comes back as `replicas: "3"`), so a read-modify-write of a Kubernetes manifest changes the types of its fields. `Yaml#toJson` guesses the types from the text alone, so a quoted `"9780986200406"`, `"1.10"` or `"true"` becomes a JSON number or boolean.

**Reproduction** (scala-cli, run exactly as written; the output is pasted at the end):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema-yaml:0.0.56
import zio.blocks.schema.yaml.YamlReader

@main def scalarTypes(): Unit = {
  val manifest = "replicas: 3\npaused: false\nversion: \"1.10\"\nisbn: \"9780986200406\"\nlabel: \"true\"\n"
  val yaml     = YamlReader.read(manifest)
  println(yaml)
  println(yaml.print)
  println(yaml.toJson.print)
}
// Output:
// Mapping(Chunk((Scalar(replicas,None),Scalar(3,None)),(Scalar(paused,None),Scalar(false,None)),(Scalar(version,None),Scalar(1.10,None)),(Scalar(isbn,None),Scalar(9780986200406,None)),(Scalar(label,None),Scalar(true,None))))
// replicas: "3"
// paused: "false"
// version: "1.10"
// isbn: "9780986200406"
// label: "true"
// {"replicas":3,"paused":false,"version":1.10,"isbn":9780986200406,"label":true}
```

**Expected.** `print` writes `replicas: 3` and `paused: false` unquoted and keeps the quotes on `version`, `isbn` and `label`. `toJson` gives `{"replicas":3,"paused":false,"version":"1.10","isbn":"9780986200406","label":"true"}`.

**Root cause.** `schema-yaml/shared/src/main/scala/zio/blocks/schema/yaml/YamlReader.scala:445-453` (`parseInlineValue`) builds `new Yaml.Scalar(...)` with no tag for plain and quoted scalars alike. `YamlWriter.needsQuoting` (`schema-yaml/shared/src/main/scala/zio/blocks/schema/yaml/YamlWriter.scala:203-227`) leaves numbers and booleans unquoted only when they carry a `Bool`, `Int`, `Float` or `Null` tag (`:205-208`). `YamlJsonInterop.yamlToJson` (`schema-yaml/shared/src/main/scala/zio/blocks/schema/yaml/YamlJsonInterop.scala:24-44`) ignores tags and quoting and converts any scalar that starts with a digit or `-` and parses as a number to `Json.Number`, and `true`/`false`/`null` in three spellings to JSON literals.

**Impact.** A program that reads YAML, changes a value and writes it back turns every number into a string, which tools such as the Kubernetes API reject (`replicas: "3"`). YAML converted to JSON changes the type of quoted values; decoding that JSON into a `String` field then fails (`expected Json.String`), and JSON written out silently has a number where the file had a string, for example for an ISBN.

**Test gap.** `YamlJsonInteropSpec` converts only hand-built `Yaml` values and JSON, never text read by `YamlReader` with quoted numbers or booleans; no test prints a tree that came from `YamlReader`.

**Suggested fix.** In `parseInlineValue`, tag quoted scalars `YamlTag.Str` and resolve plain scalars with the YAML 1.2 core schema (`Int`, `Float`, `Bool`, `Null`). In `yamlToJson`, keep `Str`-tagged scalars as JSON strings. In the writer, quote a `Str`-tagged scalar whenever it would otherwise resolve to another type.

### schema-yaml: flow style leaves commas and closing brackets unquoted, and `YamlReader` splits the value

Found 2026-10-21 while writing the how-to guide `docs/guides/read-and-write-yaml.md` (backlog H-15), on `main` at `4ec6dd90`. `schema-yaml` has no changes since tag `v0.0.56`, so the behaviour is the same in the 0.0.56 release. Verified by running.

**Summary.** With `YamlOptions.flow` (or `flowStyle = true`), `YamlWriter` writes strings that contain `,`, `]` or `}` without quotes. Inside a flow collection these characters end the item, so the output is invalid YAML for spec-following readers, and `YamlReader` reads it back as different data without an error: a book title `Scala, the Language` comes back as `Scala`.

**Reproduction** (scala-cli, run exactly as written; SnakeYAML 2.3 only to read the output with a spec-following reader; the output is pasted at the end):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema-yaml:0.0.56
//> using dep org.yaml:snakeyaml:2.3
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Book(title: String, tags: List[String])
object Book { implicit val schema: Schema[Book] = Schema.derived }

@main def flowCommas(): Unit = {
  val codec = Schema[Book].derive(YamlFormat)
  val book  = Book("Scala, the Language", List("fp, effects", "zio]", "a: b"))
  val text  = YamlWriter.write(codec.encodeValue(book), YamlOptions.flow)
  println(text)
  println(codec.decode(text))
  println(scala.util.Try(new org.yaml.snakeyaml.Yaml().load[Object](text)).fold(_.getMessage.linesIterator.next(), _.toString))
}
// Output:
// {title: Scala, the Language, tags: [fp, effects, zio], "a: b"]}
// Right(Book(Scala,List(fp, effects, zio)))
// while parsing a flow mapping
```

**Expected.** `{title: "Scala, the Language", tags: ["fp, effects", "zio]", "a: b"]}`, which every reader, including `YamlReader`, reads back as the original value.

**Root cause.** `schema-yaml/shared/src/main/scala/zio/blocks/schema/yaml/YamlWriter.scala`: the flow writers (`:141-174`) call `writeScalar` with the same `needsQuoting` as block style (`:203-227`), which does not know about the flow indicators `,`, `[`, `]`, `{`, `}` inside a value. `YamlReader.splitFlowItems` (`YamlReader.scala:409`) splits at every unquoted comma, so the reader cannot recover.

**Impact.** Silent data loss whenever flow style is used for free text: titles, author lists ("Odersky, Spoon"), descriptions.

**Test gap.** `YamlWriterSpec` tests flow style only with values that contain no flow indicators, and has no flow-style round trip.

**Suggested fix.** Pass a flow flag to `needsQuoting` and quote any value that contains `,`, `[`, `]`, `{` or `}` in flow context (or always quote strings in flow style). Add a flow round-trip property test.

### schema-yaml: `BigDecimal` and `BigInt` fields are written as quoted strings

Found 2026-10-21 while writing the how-to guide `docs/guides/read-and-write-yaml.md` (backlog H-15), on `main` at `4ec6dd90`. `schema-yaml` has no changes since tag `v0.0.56`, so the behaviour is the same in the 0.0.56 release. Verified by running.

**Summary.** The YAML codec writes `BigDecimal` and `BigInt` values without a numeric tag, so `YamlWriter` quotes them because they look like numbers: `amount: "49.99"`. Every other YAML reader then sees a string. The JSON codec writes the same values as numbers, and the YAML codec writes `Double`, `Int` and `Long` as plain numbers.

**Reproduction** (scala-cli, run exactly as written; SnakeYAML 2.3 only to show the types another reader sees; the output is pasted at the end):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema-yaml:0.0.56
//> using dep org.yaml:snakeyaml:2.3
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Price(amount: BigDecimal, copiesSold: BigInt, rating: Double)
object Price { implicit val schema: Schema[Price] = Schema.derived }

@main def bigNumbers(): Unit = {
  val text = Schema[Price].derive(YamlFormat).encodeToString(Price(BigDecimal("49.99"), BigInt(120000), 4.8))
  println(text)
  val loaded = new org.yaml.snakeyaml.Yaml().load[java.util.Map[String, Any]](text)
  loaded.forEach((k, v) => println(s"$k -> ${v.getClass.getSimpleName}"))
  println(Schema[Price].jsonCodec.encodeToString(Price(BigDecimal("49.99"), BigInt(120000), 4.8)))
}
// Output:
// amount: "49.99"
// copies-sold: "120000"
// rating: 4.8
// amount -> String
// copies-sold -> String
// rating -> Double
// {"amount":49.99,"copiesSold":120000,"rating":4.8}
```

**Expected.** `amount: 49.99` and `copies-sold: 120000`, read by SnakeYAML as numbers.

**Root cause.** `schema-yaml/shared/src/main/scala/zio/blocks/schema/yaml/YamlCodec.scala:267` (`bigIntCodec.encodeValue`) and `:278` (`bigDecimalCodec.encodeValue`) build `Yaml.Scalar(...)` with no tag, while the other numeric codecs pass `tag = Some(YamlTag.Int)` or `Some(YamlTag.Float)` (for example `:205`, `:238-239`). `YamlWriter.needsQuoting` quotes any untagged value that starts with a digit (`YamlWriter.scala:239-252`).

**Impact.** Money and other exact decimals, the usual reason to pick `BigDecimal`, are exported as strings to tools that read the YAML (Kubernetes, Ansible, Python services). The values still round-trip inside ZIO Blocks, so tests don't notice.

**Test gap.** No `YamlCodecSpec` or `YamlWriterSpec` test asserts the written text of a `BigDecimal` or `BigInt`.

**Suggested fix.** Tag `BigInt` scalars `YamlTag.Int`, and `BigDecimal` scalars `YamlTag.Float` (or `Int` when whole).

### schema-yaml: derived codecs always use kebab-case keys and ignore `@Modifier.rename`, `@Modifier.transient` and the other field modifiers

Found 2026-10-21 while writing the how-to guide `docs/guides/read-and-write-yaml.md` (backlog H-15), on `main` at `4ec6dd90`. `schema-yaml` has no changes since tag `v0.0.56`, so the behaviour is the same in the 0.0.56 release. Verified by running.

**Summary.** `YamlCodecDeriver` converts every field name to kebab-case and never reads the `modifiers` it is given. `@Modifier.rename("name")` is ignored, `@Modifier.transient` fields are written and read, and there is no way to choose another naming: a field `imagePullPolicy` is always the key `image-pull-policy`. YAML formats that use camelCase keys (Kubernetes, OpenAPI, docker-compose's `snake_case`) cannot be decoded into case classes with the YAML codec at all. The JSON codec honours all of these modifiers, so the same schema behaves differently in the two formats. GitHub issue #1327 asks for configurable field names, and PR #1747 (open) adds a `fieldNameMapper` and support for `@Modifier.rename` and `@Modifier.fieldNaming`; it does not mention `@Modifier.transient`.

**Reproduction** (scala-cli, run exactly as written; the output is pasted at the end):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema-yaml:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Container(
  @Modifier.rename("name") containerName: String,
  imagePullPolicy: String,
  @Modifier.transient cachedDigest: String = ""
)
object Container { implicit val schema: Schema[Container] = Schema.derived }

@main def fieldNames(): Unit = {
  val codec = Schema[Container].derive(YamlFormat)
  val c     = Container("bookstore", "IfNotPresent", "sha256:abc")
  println(codec.encodeToString(c))
  println(Schema[Container].jsonCodec.encodeToString(c))
  println(codec.decode("name: bookstore\nimagePullPolicy: IfNotPresent\n"))
  println(codec.decode("container-name: bookstore\nimage-pull-policy: IfNotPresent\n"))
}
// Output:
// container-name: bookstore
// image-pull-policy: IfNotPresent
// cached-digest: sha256:abc
// {"name":"bookstore","imagePullPolicy":"IfNotPresent"}
// Left(zio.blocks.schema.SchemaError: Missing required field: container-name at: .)
// Right(Container(bookstore,IfNotPresent,))
```

**Expected.** The same keys as the JSON codec (`name`, `imagePullPolicy`, no `cachedDigest`), or at least a way to choose the naming and honour `rename` and `transient`.

**Root cause.** `schema-yaml/shared/src/main/scala/zio/blocks/schema/yaml/YamlCodecDeriver.scala:121` (`name = NameMapper.KebabCase.apply(name)`); none of the seven `derive*` methods reads its `modifiers: Seq[Modifier.Reflect]` parameter (for example `:99`, `:257`). Variant case names are used as written (`:299`).

**Impact.** Kubernetes manifests, OpenAPI documents and any camelCase YAML can't be read into case classes; JSON customizations don't carry over; a `@Modifier.transient` secret is written to YAML files. The reference page `docs/reference/schema/built-in-codecs/yaml.md` never mentions the kebab-case conversion (see the documentation entry).

**Test gap.** `YamlCodecDeriverExtendedSpec` pins the kebab-case output for `firstName`; no test uses a field modifier.

**Suggested fix.** Merge PR #1747, and extend it to `@Modifier.transient` (and `@Modifier.alias` on decode) so YAML follows the JSON codec's modifier semantics.

### schema-yaml: `yaml"..."` does not compile on Scala 2.13 outside the `zio.blocks.schema` package

Found 2026-10-21 while writing the how-to guide `docs/guides/read-and-write-yaml.md` (backlog H-15), on `main` at `4ec6dd90`. `schema-yaml` has no changes since tag `v0.0.56`, so the behaviour is the same in the 0.0.56 release. Verified by running.

**Summary.** Same bug as the `json"..."` entry above, in the YAML module. On Scala 2.13, every use of the `yaml"..."` interpolator in user code fails to compile, with or without spliced values, because the macro expansion calls `YamlInterpolatorRuntime`, which is `private[schema]`. On Scala 3 the interpolator compiles (see the next entry for what it does).

**Reproduction** (scala-cli, run exactly as written; the compiler output is pasted at the end):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema-yaml:0.0.56
import zio.blocks.schema.yaml._

object YamlInterpolatorScala2 {
  def main(args: Array[String]): Unit = {
    val title = "Zionomicon"
    println(yaml"title: $title".print)
    println(yaml"title: Zionomicon".print)
  }
}
// Output:
// ./g.scala:8: error: object YamlInterpolatorRuntime in package yaml cannot be accessed as a member of package zio.blocks.schema.yaml from object YamlInterpolatorScala2
//     println(yaml"title: $title".print)
//             ^
// ./g.scala:9: error: object YamlInterpolatorRuntime in package yaml cannot be accessed as a member of package zio.blocks.schema.yaml from object YamlInterpolatorScala2
//     println(yaml"title: Zionomicon".print)
//             ^
// 2 errors
```

**Expected.** Both lines compile and print `title: Zionomicon`.

**Root cause.** `schema-yaml/shared/src/main/scala-2/zio/blocks/schema/yaml/package.scala:92` emits `reify(YamlInterpolatorRuntime.yamlWithContexts(...))` into the caller's code, and `schema-yaml/shared/src/main/scala/zio/blocks/schema/yaml/YamlInterpolatorRuntime.scala:22` declares `private[schema] object YamlInterpolatorRuntime`. Scala 2 checks the expansion with the call site's access rights.

**Impact.** The interpolator is unusable on Scala 2.13; users have to build trees with the `Yaml` constructors or `YamlReader.read`.

**Test gap.** `YamlInterpolatorSpec` is in `package zio.blocks.schema.yaml`, so the private reference resolves there; no test calls `yaml"..."` from another package.

**Suggested fix.** Make `YamlInterpolatorRuntime` public (it can be marked internal in Scaladoc), as for `JsonInterpolatorRuntime`, and add a test in a package outside `zio.blocks.schema`.

### schema-yaml: on Scala 3, `yaml"..."` loses spliced collections and records, ignores escape sequences, and accepts malformed literals

Found 2026-10-21 while writing the how-to guide `docs/guides/read-and-write-yaml.md` (backlog H-15), on `main` at `4ec6dd90`. `schema-yaml` has no changes since tag `v0.0.56`, so the behaviour is the same in the 0.0.56 release. Verified by running.

**Summary.** The Scala 3 `yaml"..."` interpolator builds YAML text from the literal parts and the spliced values, then parses it with `YamlReader`. That design has four visible problems:
1. A spliced `List`, case class or `Yaml` tree is rendered in block style on the line of its key, so the text is invalid and the reader misreads it without an error: a list keeps only its first item and the rest of the literal is dropped, a record's fields move up into the parent mapping, and a `Yaml` mapping becomes a string.
2. A splice inside a quoted string is inserted in its quoted form, so the quotes become part of the text.
3. Escape sequences in the literal are not processed, unlike `s"..."`: `yaml"a: 1\nb: 2"` is one key whose value contains a backslash and `n`.
4. The compile-time check only validates the spliced types: the literal is parsed with the lenient `YamlReader`, so unclosed brackets and `price:49.99` compile and load as different data.

**Reproduction** (scala-cli, run exactly as written; the output is pasted at the end):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema-yaml:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Author(name: String, born: Int)
object Author { implicit val schema: Schema[Author] = Schema.derived }

@main def yamlInterpolatorScala3(): Unit = {
  val tags   = List("zio", "effects")
  val author = Author("John A. De Goes", 1978)
  val inner  = Yaml.Mapping.fromStringKeys("city" -> Yaml.Scalar("Berlin"))
  val note   = "Fraser: co-author"
  // 1. A spliced List: the list and every later line are lost
  println(yaml"""title: Zionomicon
tags: $tags
price: 49.99""")
  // 2. A spliced record
  println(yaml"""title: Zionomicon
author: $author
price: 49.99""")
  // 3. A spliced Yaml mapping becomes a string
  println(yaml"store: $inner")
  // 4. A string-position splice of a value that needs quoting keeps the quotes in the text
  println(yaml"""note: "Written with $note"""")
  // 5. Escape sequences in the literal are not processed: one key, not two
  println(yaml"title: Zionomicon\nprice: 49.99")
  // 6. Malformed literals compile and load
  println(yaml"tags: [zio, effects")
  println(yaml"""title: Zionomicon
price:49.99
pages: 744""")
}
// Output:
// Mapping(Chunk((Scalar(title,None),Scalar(Zionomicon,None)),(Scalar(tags,None),Scalar(- zio,None))))
// Mapping(Chunk((Scalar(title,None),Scalar(Zionomicon,None)),(Scalar(author,None),Scalar(name: John A. De Goes,None)),(Scalar(born,None),Scalar(1978,None)),(Scalar(price,None),Scalar(49.99,None))))
// Mapping(Chunk((Scalar(store,None),Scalar(city: Berlin,None))))
// Mapping(Chunk((Scalar(note,None),Scalar(Written with "Fraser: co-author",None))))
// Mapping(Chunk((Scalar(title,None),Scalar(Zionomicon\nprice: 49.99,None))))
// Mapping(Chunk((Scalar(tags,None),Sequence(Chunk(Scalar(zio,None),Scalar(effect,None))))))
// Mapping(Chunk((Scalar(title,None),Scalar(Zionomicon,None))))
```

**Expected.** 1: `tags: [zio, effects]` and `price: 49.99` both present; `author` a nested mapping; `store` a nested mapping. 2: `note: Written with Fraser: co-author`. 3: two keys, as with `s"..."`. 4: compile errors for the unclosed `[` and for `price:49.99` (or at least for the first).

**Root cause.** `schema-yaml/shared/src/main/scala/zio/blocks/schema/yaml/YamlInterpolatorRuntime.scala`: `writeValue` renders a `Yaml` (which every Schema-typed splice becomes in the Scala 3 macro) with `YamlWriter.write(y, YamlOptions.default)`, which is block style (`:98`), and the text is parsed again (`:57`). `writeInString` inserts the value raw (`:156`), and the macro passes it `encodeKey(...)`, which is already quoted (`schema-yaml/shared/src/main/scala-3/zio/blocks/schema/yaml/package.scala`). The macro takes the literal parts as written (`rawParts.map(... valueOrAbort)`, scala-3 `package.scala:31-34`) and never calls `StringContext.processEscapes`. `validateYamlLiteral` (`YamlInterpolatorRuntime.scala:24-39`) relies on `YamlReader.read`, which does not throw on malformed input.

**Impact.** Values silently disappear from documents built with the interpolator, which is meant to be the convenient way to build YAML in code. Only literals of scalar splices are safe.

**Test gap.** `YamlInterpolatorSpec` splices collections only in flow or single-line positions where the rendered block happens to parse, has no multi-line literal followed by more keys after a collection splice, and no test with `\n` in a literal or a malformed literal.

**Suggested fix.** Splice values into the parsed tree instead of re-parsing text (parse the literal with placeholders, then replace the placeholder nodes with the encoded `Yaml`), or render spliced values in flow style. Process escapes in the parts. Make `validateYamlLiteral` use a strict parse once the reader can fail.

### sql: `Table.derived` and `Repo.derived` flatten a nested case class field that the codec stores as one JSON column, so every insert fails, and they throw for a `List` field

Found 2026-10-21 while writing the how-to guide `docs/guides/map-custom-column-types.md` (backlog H-22), on `main` at `7d8e0b44`. The code is the same at tag `v0.0.56`. Verified. Not filed.

**Summary.** The row codec stores a field whose type is a case class (without `@Modifier.config("sql.inline", "true")`) as one JSON column, and a `List`, `Seq`, `Set` or `Map` field the same way. `Table.derived` and `Repo.derived` don't ask the codec for its columns: they compute them separately with `TableMetadata.columnsFor`, which flattens every nested record into `<field>_<subfield>` columns and throws for a collection. So for `case class Book(id: Long, title: String, metadata: Metadata)` the table lists five columns while the codec writes three values, and `Repo#insert` fails its own `require`. For a `List[String]` field, `Table.derived` and `Repo.derived` throw before any SQL runs. `Table#createTable` emits the flattened columns too, so a table it creates doesn't match the codec either. A one-field case class used as a newtype with a derived `Schema` hits the same path: the table expects `isbn_value`, while the codec writes `{"value":"..."}` into a column named `isbn`.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-sql` 0.0.56, `sqlite-jdbc` 3.53.4.0, run with `scala-cli run TableColumns.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0

import scala.util.Try
import zio.blocks.schema.Schema
import zio.blocks.sql.*

case class Metadata(publisher: String, edition: Int, pages: Int)
object Metadata { given Schema[Metadata] = Schema.derived }

case class Book(id: Long, title: String, metadata: Metadata)
object Book { given Schema[Book] = Schema.derived }

case class TaggedBook(id: Long, title: String, tags: List[String])
object TaggedBook { given Schema[TaggedBook] = Schema.derived }

@main def run(): Unit = {
  println(Table.derived[Book].columns)
  // Vector(id, title, metadata_publisher, metadata_edition, metadata_pages)
  println(DbCodec.derived[Book].columns)
  // Vector(id, title, metadata)

  val file = java.nio.file.Files.createTempFile("repro", ".db")
  val xa   = JdbcTransactor.fromUrl(s"jdbc:sqlite:$file", SqlDialect.SQLite)
  xa.connect {
    Table.derived[Book].createTable(SqlDialect.SQLite).update
    println(Try(Repo.derived[Book, Long].insert(Book(1L, "Zionomicon", Metadata("Ziverge", 1, 700)))))
    // Failure(java.lang.IllegalArgumentException: requirement failed: Repo.buildInsertFrag: columns/value count mismatch)
  }

  println(Try(Table.derived[TaggedBook]))
  // Failure(java.lang.UnsupportedOperationException: Typed DDL does not support reflect node List[String])
  println(Try(Repo.derived[TaggedBook, Long]))
  // Failure(java.lang.UnsupportedOperationException: Typed DDL does not support reflect node List[String])
}
```

**Expected.** `Table.derived[Book].columns` equals `DbCodec.derived[Book].columns` (`id, title, metadata`), `Repo#insert` writes the JSON column, `Table.derived[TaggedBook]` succeeds with a `tags` column, and `createTable` declares one `TEXT` column (SQLite) or `JSONB` column (PostgreSQL) for each JSON field.

**Root cause.** `DbCodecDeriver#deriveRecord` falls back to `jsonbCodec` for every non-inline record field (`sql/shared/src/main/scala/zio/blocks/sql/DbCodecDeriver.scala:134-136`), and `deriveSequence` / `deriveMap` do the same for collections. `TableMetadata.fromReflect` (`sql/shared/src/main/scala/zio/blocks/sql/TableMetadata.scala:60-80`) recurses into every record field without checking `sql.inline`, and `dbValueFor` throws for any node that isn't a primitive, an enum or a wrapper (`TableMetadata.scala:126-128`). `Table.derived` (`Table.scala:93,105`) and every `Repo.derived` overload (`Repo.scala:432,446,499`) pair the codec with these columns, and `Repo#insert` / `Repo#insertReturning` / `Repo#insertBatch` name `table.columns` in the `INSERT` (`Repo.scala:189,201,222`).

**Impact.** A model with a nested record or a collection field can't use `Repo.derived` at all, although the codec handles it. The error (`columns/value count mismatch` or `Typed DDL does not support reflect node List[String]`) doesn't say why. The H-22 guide works around it by building the `Table` from `codec.columns` and passing it to `Repo.apply`.

**Test gap.** `DbCodecSpec.scala:881,1062` check that the codec produces one JSON column for a `List[Int]` and for a non-inline record field, but no test derives a `Table` or a `Repo` for such a type or compares `Table#columns` with `codec.columns`.

**Suggested fix.** Derive the column list from the same decisions as the codec: in `TableMetadata.fromReflect`, recurse into a record only when the field (or the parent) has `sql.inline` / `sql.inline_fields`, and map non-inline records, sequences, maps and non-simple variants to one `DbValue.DbString` column (or a new JSON marker so PostgreSQL's dialect can emit `JSONB`). Add a property test that `Table.derived[A].columns == DbCodec.derived[A].columns` for every type in `DbCodecSpec`.

### sql: an `Option` field of a `Schema#transform` newtype or of an enum fails on SQL `NULL`

Found 2026-10-21 while writing the how-to guide `docs/guides/map-custom-column-types.md` (backlog H-22), on `main` at `7d8e0b44`. The code is the same at tag `v0.0.56`. Verified. Not filed.

**Summary.** The derived codec for an `Option` field checks `DbResultReader#isNull` before it reads the column. `JdbcResultReader#isNull` only knows about columns that have already been read in the current row, so the check is always `false` and the inner codec reads the `NULL`. For `Option[String]` or `Option[Int]` that is harmless, because the inner value is checked with `wasNull` afterwards. For a newtype whose `Schema` is `Schema[String].transform(...)`, the inner codec first calls the user's function with `null`, which throws (an NPE, or the smart constructor's error). For an enum, the inner codec looks up the case named `null` and throws `Unknown enum variant 'null'`. So a nullable column of either type can't be read when it holds `NULL`. Writing `None` works.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-sql` 0.0.56, `sqlite-jdbc` 3.53.4.0, run with `scala-cli run OptionNewtypeNull.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0

import scala.util.Try
import zio.blocks.schema.Schema
import zio.blocks.sql.*

final case class Isbn(value: String) {
  require(value.length == 13, s"ISBN must have 13 characters, got $value")
}
object Isbn { given Schema[Isbn] = Schema[String].transform(Isbn(_), _.value) }

enum Format { case Hardcover, Paperback, Ebook }
object Format { given Schema[Format] = Schema.derived }

case class Book(id: Long, isbn: Option[Isbn], format: Option[Format])
object Book { given Schema[Book] = Schema.derived }

@main def run(): Unit = {
  val file = java.nio.file.Files.createTempFile("repro", ".db")
  val xa   = JdbcTransactor.fromUrl(s"jdbc:sqlite:$file", SqlDialect.SQLite)
  xa.connect {
    sql"CREATE TABLE book (id INTEGER PRIMARY KEY, isbn TEXT, format TEXT)".update
    sql"INSERT INTO book VALUES (1, '9780981531601', 'Paperback')".update
    sql"INSERT INTO book VALUES (2, NULL, 'Ebook')".update
    sql"INSERT INTO book VALUES (3, '9781617290657', NULL)".update
    println(sql"SELECT id, isbn, format FROM book WHERE id = 1".query[Book])
    // List(Book(1,Some(Isbn(9780981531601)),Some(Paperback)))
    println(Try(sql"SELECT id, isbn, format FROM book WHERE id = 2".query[Book]))
    // Failure(java.lang.NullPointerException: Cannot invoke "String.length()" because "value" is null)
    println(Try(sql"SELECT id, isbn, format FROM book WHERE id = 3".query[Book]))
    // Failure(java.lang.IllegalArgumentException: Unknown enum variant 'null'. Expected one of: Hardcover, Paperback, Ebook)
    println(sql"SELECT isbn FROM book WHERE id = 2".query[Option[String]])
    // List(None)
  }
}
```

**Expected.** Rows 2 and 3 read back as `Book(2,None,Some(Ebook))` and `Book(3,Some(Isbn(9781617290657)),None)`.

**Root cause.** The `Option` branch of `DbCodecDeriver#deriveVariant` returns `None` only if `reader.isNull(...)` is `true` before reading (`sql/shared/src/main/scala/zio/blocks/sql/DbCodecDeriver.scala:367,379`), and only then falls back to `optionalValue(typeId, innerValue, reader.wasNull)`. `JdbcResultReader` fills its null bitmap inside each getter (`sql/jvm/src/main/scala/zio/blocks/sql/JdbcResultReader.scala:41-62`), so `isNull` (`JdbcResultReader.scala:283-285`) is `false` for a column not yet read. The inner codec runs first: the wrapper codec calls `binding.wrap` on the `null` it read (`DbCodecDeriver.scala:328,331`), and the enum codec throws for the unknown name (`DbCodecDeriver.scala:425,436`). `DbCodec.optionCodec` has the same pre-read check but no user code between the read and the `wasNull` fallback, which is why `Option[String]` works.

**Impact.** A nullable column of a validated newtype or an enum, which is common (an optional ISBN, an optional status), fails every query that returns a `NULL` in it, with an error that points at the user's own constructor. The H-22 guide works around it by overriding the field with `DbCodec[Option[String]].transform(_.map(Isbn.unsafe))(_.map(_.value))` through `DbCodec.derivedWith`.

**Test gap.** `DbCodecOptionSpec` tests `Option[String]`, `Option[Int]` and `Maybe[String]` only; no test reads `NULL` into an `Option` of a wrapper or of an enum.

**Suggested fix.** In the `Option` branch, read the inner column through a null-aware step: probe the column with a cheap getter (for example `getString`) and `wasNull` before calling the inner codec, or add a `DbResultReader#isNullAt` that calls `getObject` + `wasNull` for an unread column. Add `DbCodecOptionSpec` cases for an `Option` of a `Schema#transform` newtype and of an enum.

### sql: on SQLite, an `Instant` column that `Table#createTable` declares can't be read back

Found 2026-10-21 while writing the how-to guide `docs/guides/map-custom-column-types.md` (backlog H-22), on `main` at `7d8e0b44`. The code is the same at tag `v0.0.56`. Verified. Not filed.

**Summary.** On SQLite, `Table#createTable` declares an `Instant` field as `TEXT`. The codec writes the value with `setTimestamp`, and the SQLite JDBC driver stores a timestamp as epoch milliseconds, so the `TEXT` column holds the text `1626341400123`. Reading it back with `getTimestamp` fails with `Error parsing time stamp`, so every query on a table that `createTable` made with an `Instant` field fails. An `INTEGER` column works, but drops everything below the millisecond.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-sql` 0.0.56, `sqlite-jdbc` 3.53.4.0, run with `scala-cli run SqliteInstant.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0

import java.time.Instant
import scala.util.Try
import zio.blocks.schema.Schema
import zio.blocks.sql.*

case class Book(id: Long, title: String, publishedAt: Instant)
object Book { given Schema[Book] = Schema.derived }

@main def run(): Unit = {
  val books = Repo.derived[Book, Long]
  val file  = java.nio.file.Files.createTempFile("repro", ".db")
  val xa    = JdbcTransactor.fromUrl(s"jdbc:sqlite:$file", SqlDialect.SQLite)
  xa.connect {
    books.table.createTable(SqlDialect.SQLite).update
    println(books.table.createTable(SqlDialect.SQLite).sql(SqlDialect.SQLite))
    // CREATE TABLE IF NOT EXISTS book (
    //   id INTEGER NOT NULL,
    //   title TEXT NOT NULL,
    //   published_at TEXT NOT NULL
    // )
    books.insert(Book(1L, "Zionomicon", Instant.parse("2021-07-15T09:30:00.123456Z")))
    println(sql"SELECT published_at, typeof(published_at) FROM book".query[(String, String)])
    // List((1626341400123,text))
    println(Try(books.all))
    // Failure(java.sql.SQLException: Error parsing time stamp)

    sql"DROP TABLE book".update
    sql"CREATE TABLE book (id INTEGER PRIMARY KEY, title TEXT NOT NULL, published_at INTEGER NOT NULL)".update
    books.insert(Book(1L, "Zionomicon", Instant.parse("2021-07-15T09:30:00.123456Z")))
    println(books.all)
    // List(Book(1,Zionomicon,2021-07-15T09:30:00.123Z))
  }
}
```

**Expected.** A table created by `createTable` round-trips the `Instant`. Microseconds survive, or the docs say SQLite keeps milliseconds.

**Root cause.** `SqlDialect.SQLite#typeName` maps `DbValue.DbInstant` to `TEXT` (`sql/shared/src/main/scala/zio/blocks/sql/SqlDialect.scala:73`), but `JdbcParamWriter#setInstant` writes with `setTimestamp(index, Timestamp.from(value), utcCal)` (`sql/jvm/src/main/scala/zio/blocks/sql/JdbcParamWriter.scala:49-52`), which sqlite-jdbc stores as milliseconds (its default `date_class` is `INTEGER`), and `JdbcResultReader#getInstant` reads with `getTimestamp(index, utcCalendar)` (`JdbcResultReader.scala:223-233`), which can't parse a number stored as text.

**Impact.** The first `Instant` field in a SQLite table built with `createTable` breaks every read of that table. `docs/reference/sql/sql-dialect.md:65` documents the `TEXT` mapping as correct.

**Test gap.** No SQLite test round-trips an `Instant` through a `createTable` table; `JdbcInstantTimezoneSpec` runs on PostgreSQL only.

**Suggested fix.** Map `DbInstant` to `INTEGER` in the SQLite dialect, or write SQLite `Instant`s as ISO-8601 text (`setString(value.toString)`) and parse them on read, which also keeps the nanoseconds. Add a SQLite round-trip test.

### schema: a `DynamicSchema` written as JSON, YAML, TOON or MessagePack reads back as an accept-anything schema

**Severity:** high (silent accept-anything after a round trip).

Found 2026-10-21 while writing the how-to guide `docs/guides/build-a-schema-registry.md` (backlog H-09), on `main` at `d1b4fe52`. The code is the same at tag `v0.0.56` (only Scaladoc changed in these files since). Verified on Scala 2.13.18 and 3.9.0 against published 0.0.56. Not filed.

**Summary.** `Schema[DynamicSchema]` exists so a schema can be stored and sent, but only its Avro codec round-trips it. Through the JSON, YAML, TOON and MessagePack codecs, and through `Json.fromDynamicValue(DynamicSchema.toDynamicValue(ds))` and back, the decoded value is `DynamicSchema(Reflect.Dynamic)`: decoding returns `Right`, no error is reported, and `check` then accepts every value. A registry that stores schemas as JSON validates nothing, silently.

**Reproduction** (Verified; Scala 2.13.18, `zio-blocks-schema`, `-schema-avro`, `-schema-messagepack`, `-schema-toon`, `-schema-yaml` 0.0.56, run with `scala-cli run LossyRoundTrip213.scala`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-schema-avro:0.0.56
//> using dep dev.zio::zio-blocks-schema-messagepack:0.0.56
//> using dep dev.zio::zio-blocks-schema-toon:0.0.56
//> using dep dev.zio::zio-blocks-schema-yaml:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.avro.AvroFormat
import zio.blocks.schema.json.{Json, JsonFormat}
import zio.blocks.schema.msgpack.MessagePackFormat
import zio.blocks.schema.toon.ToonFormat
import zio.blocks.schema.yaml.YamlFormat

final case class BookPublished(isbn: String, title: String, price: BigDecimal)
object BookPublished { implicit val schema: Schema[BookPublished] = Schema.derived }

object LossyRoundTrip213 {
  def main(args: Array[String]): Unit = {
    val stored = Schema[BookPublished].toDynamicSchema
    val bad    = Json.parseUnsafe("""{"isbn":7,"price":"free","extra":true}""").toDynamicValue

    println(stored.check(bad))
    // Some(zio.blocks.schema.SchemaError: Expected String, got Int at: .isbn)

    val json     = Schema[DynamicSchema].derive(JsonFormat)
    val fromJson = json.decode(json.encode(stored))
    println(fromJson.map(_ == stored))
    // Right(false)
    println(fromJson.map(_.reflect.isDynamic))
    // Right(true)
    println(fromJson.map(_.check(bad)))
    // Right(None)

    // The same through Json values: DynamicSchema.toDynamicValue -> Json -> DynamicSchema.fromDynamicValue
    val viaJson = DynamicSchema.fromDynamicValue(Json.fromDynamicValue(DynamicSchema.toDynamicValue(stored)).toDynamicValue)
    println(viaJson.reflect.isDynamic)
    // true

    // MessagePack, TOON and YAML lose it the same way
    val msgpack = Schema[DynamicSchema].derive(MessagePackFormat)
    println(msgpack.decode(msgpack.encode(stored)).map(_.reflect.isDynamic))
    // Right(true)
    val toon = Schema[DynamicSchema].derive(ToonFormat)
    println(toon.decode(toon.encode(stored)).map(_.reflect.isDynamic))
    // Right(true)
    val yaml = Schema[DynamicSchema].derive(YamlFormat)
    println(yaml.decode(yaml.encode(stored)).map(_.reflect.isDynamic))
    // Right(true)

    // Avro keeps the variant tags and primitive types, so it round-trips
    val avro = Schema[DynamicSchema].derive(AvroFormat)
    println(avro.decode(avro.encode(stored)).map(_ == stored))
    // Right(true)
  }
}
```

The Scala 3 run uses the same file with the header changed to
`//> using scala 3.9.0`, `//> using options -experimental` and the `zio-blocks-next-*` artifacts (same version), and prints exactly the same lines.

**Expected.** Every format that `Schema[DynamicSchema]` can be derived for round-trips it (`decode(encode(ds)) == Right(ds)`), or decoding fails with an error. It never succeeds with a different, accept-anything schema.

**Root cause.** Three things combine.
1. `Schema[DynamicSchema]` (`schema/shared/src/main/scala/zio/blocks/schema/DynamicSchema.scala:1386-1404`; `:1379` at v0.0.56) is a record with one `Schema[DynamicValue]` field, so every format writes the schema through its generic `DynamicValue` codec.
2. That codec is lossy for JSON. `JsonCodec.dynamicValueCodec` (`schema/shared/src/main/scala/zio/blocks/schema/json/JsonCodec.scala:1423`, same at v0.0.56) writes a `DynamicValue.Variant` as a one-key object `{"Case": value}` (`:1522-1526`) but reads every object back as a `DynamicValue.Record` (`:1461-1471`), and it narrows every number to `Int`, `Long` or `BigDecimal` (`:1439-1448`). `Json#toDynamicValue` does the same (`Json.scala:1026-1043`). The serialized schema is made of variants (`{"Record": ...}`, `{"Primitive": ...}`, `{"None": {}}`), so all its tags are lost. MessagePack, TOON and YAML give the same result (verified by run; their codecs not traced). Avro writes a tag for each `DynamicValue` case (`schema-avro/src/main/scala/zio/blocks/schema/avro/AvroCodecDeriver.scala:996-1007`, case 2 builds `DynamicValue.Variant`), so it round-trips.
3. `DynamicSchema.dynamicValueToReflect` falls back instead of failing: an unrecognised node becomes `new Reflect.Dynamic[NoBinding](NoBinding())` (`DynamicSchema.scala:1926-1927`; `:1919-1920` at v0.0.56), an unreadable term becomes a `Dynamic` term named `unknown` (`:1749`), and an unreadable `TypeId` becomes `TypeId.nominal[Any]("Unknown", Owner.Root)` (`:1664-1668`). `DynamicSchema.fromDynamicValue` returns `DynamicSchema`, not `Either`, so it has no way to report the problem (`:1415`).

**Impact.** High. The reference page and two examples present the serialized form as storable (see the Documentation errors entry), and JSON is the obvious format for a registry. The result validates nothing and nothing warns. The H-09 guide stores Avro bytes, writes the JSON only as a human-readable view, and guards against a root `Reflect.Dynamic` after loading.

**Test gap.** `DynamicSchemaSpec` "Schema[DynamicSchema] round-trip" (`schema/shared/src/test/scala-3/zio/blocks/schema/DynamicSchemaSpec.scala:1795-1902`) round-trips only through `DynamicSchema.toDynamicValue`/`fromDynamicValue` in memory and asserts only `typeId.name` and field presence. No test encodes a `DynamicSchema` with any format codec, and no test checks that a malformed blob is rejected.

**Suggested fix.** Give `DynamicSchema` a format-independent schema instead of a `Schema[DynamicValue]` field: a derived structural schema for the serialized `Reflect` tree (records and variants with real tags), so every codec writes tags its own way. Separately, make `DynamicSchema.fromDynamicValue` (or a new `DynamicSchema.decode`) return `Either[SchemaError, DynamicSchema]` and fail on unknown nodes instead of producing `Reflect.Dynamic`/`Unknown`. Add a round-trip test per format comparing with `==`.

**Related.** The Scaladoc wave 5 entry "schema: `DynamicSchema.fromDynamicValue` never fails, so a damaged stored schema accepts values it should reject" (below) covers the same defect from the API side; file them as one GitHub issue.

### schema: storing the `DynamicSchema` of a recursive type overflows the stack

Found 2026-10-21 while writing the how-to guide `docs/guides/build-a-schema-registry.md` (backlog H-09), on `main` at `d1b4fe52`. The code is the same at tag `v0.0.56` (only Scaladoc changed in these files since). Verified on Scala 2.13.18 and 3.9.0 against published 0.0.56. Not filed.

**Summary.** `DynamicSchema.toDynamicValue`, and with it every `Schema[DynamicSchema]` codec, recurses forever on a recursive type such as `Chapter(title: String, sections: List[Chapter])` and throws `StackOverflowError`. `DynamicSchema#check` works on the same schema, so the type is supported everywhere except storage.

**Reproduction** (Verified; Scala 2.13.18, `zio-blocks-schema` 0.0.56, run with `scala-cli run RecursiveOverflow.scala`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

final case class Chapter(title: String, sections: List[Chapter])
object Chapter { implicit val schema: Schema[Chapter] = Schema.derived }

object RecursiveOverflow {
  def main(args: Array[String]): Unit = {
    val stored = Schema[Chapter].toDynamicSchema
    val value  = Schema[Chapter].toDynamicValue(Chapter("Fibers", List(Chapter("Forking", Nil))))

    println(stored.check(value))
    // None

    try println(DynamicSchema.toDynamicValue(stored))
    catch { case e: StackOverflowError => println(e) }
    // java.lang.StackOverflowError

    try println(Schema[DynamicSchema].jsonCodec.encodeToString(stored))
    catch { case e: StackOverflowError => println(e) }
    // java.lang.StackOverflowError
  }
}
```

The Scala 3 run uses the same file with the header changed to
`//> using scala 3.9.0`, `//> using options -experimental` and the `zio-blocks-next-*` artifacts (same version), and prints exactly the same lines.

**Expected.** A recursive schema serializes, with a back-reference node for the recursive occurrence, and reads back to an equal schema.

**Root cause.** `reflectToDynamicValue` unwraps `Reflect.Deferred` by recursing into it with no cycle guard: `case d: Reflect.Deferred[NoBinding, _] => reflectToDynamicValue(d.value)` (`schema/shared/src/main/scala/zio/blocks/schema/DynamicSchema.scala:1659`; `:1652` at v0.0.56). The serialized format has no node for "the type `X` seen above", so there is nothing to emit instead.

**Impact.** Any event with a tree-shaped field (a table of contents, a category tree, a comment thread) can't go in a `DynamicSchema` registry. PR #888 lists "Recursive schema support via `Deferred` handling" as a feature. The H-09 guide warns about it.

**Test gap.** `DynamicSchemaSpec` tests recursive schemas only for `check` and defaults (`:727-790`, `:2437`); "coverage - Deferred serialization" (`:2436`) does not use a self-referencing type.

**Suggested fix.** Track the `TypeId`s on the current path in `reflectToDynamicValue`; when a `Deferred` resolves to one of them, emit a `Ref(typeId)` node, and have `dynamicValueToReflect` build a `Reflect.Deferred` for it from a table of the records it has read. Add a round-trip test for a recursive record and a recursive sealed trait.

**Related.** The Scaladoc wave 5 entry "schema: `DynamicSchema.toDynamicValue` overflows the stack for a recursive type, so its schema cannot be stored" (below) covers the same defect from the API side; file them as one GitHub issue.

### schema: `Into.derived` silently drops a value when a same-name field changes from `A` to `Option[A]`

Found 2026-10-22 while writing the tutorial `docs/guides/evolve-your-data.md` (backlog T-02), on `main` at `2f73284b`; same at tag `v0.0.56`. Verified by running (re-run by the coordinator with the same output).

**Severity:** high (silent data loss in the "make a field optional" evolution step).

**Summary.** Deriving `Into[Src, Dst]` where `Src.isbn: String` and `Dst.isbn: Option[String]` compiles, but the conversion returns `isbn = None`: the source value is dropped without an error. Users expect `Some(value)`, or a compile error.

**Reproduction** (save as `Repro.scala` and run `scala-cli run Repro.scala`; line `B` is this bug, lines `A` and `C` belong to the two entries that refer to this script):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
import zio.blocks.schema.{As, Into}
object Repro {
  case class TypeA(name: String, nickname: Option[String]); case class TypeB(name: String)
  case class Src(name: String, isbn: String); case class Dst(name: String, isbn: Option[String])
  case class Raw(a: Double, b: Double, c: Double); case class Clean(a: Int, b: Int, c: Int)
  def main(args: Array[String]): Unit = {
    val ab = As.derived[TypeA, TypeB]
    println("A: " + ab.into(TypeA("x", Some("n"))).flatMap(ab.from))
    println("B: " + Into.derived[Src, Dst].into(Src("Zionomicon", "978-0000000000")))
    Into.derived[Raw, Clean].into(Raw(1.5, 2.5, 3.5)) match {
      case Left(e) => println("C:\n" + e.message)
      case Right(v) => println("C: " + v)
    }
  }
}
// A: Right(TypeA(x,None))
// B: Right(Dst(Zionomicon,None))
// C:
// converting field Raw.c to Clean.c failed
//   Caused by: Value 3.5 cannot be precisely converted to Int
// converting field Raw.b to Clean.b failed
//   Caused by: Value 2.5 cannot be precisely converted to Int
// converting field Raw.a to Clean.a failed
//   Caused by: Value 1.5 cannot be precisely converted to Int
```

**Expected.** `B: Right(Dst(Zionomicon,Some(978-0000000000)))`, or a compile error.

**Root cause.** There is no `Into[A, Option[A]]` instance; `Into.scala:132` has only `optionInto: Into[Option[A], Option[B]]`. So `matchFields` in `schema/shared/src/main/scala-3/zio/blocks/schema/IntoVersionSpecific.scala` finds no match at any priority and falls through to the "target is `Option` → use `None`" branch (`:1286-1288`), which is meant for target fields that have no source field at all.

**Impact.** Silent data loss exactly in the "add `Option` to a field" evolution step that `Into` is meant for.

**Test gap.** `IntoSpec.scala:678-691` ("succeeds when non-Option maps to Option with compatible type") says in a comment that a predefined `Into[A, Option[A]]` wraps values in `Some`, but only asserts that `typeCheck` is `Right`; it never checks the value.

**Suggested fix.** Add `implicit def someInto[A, B](implicit into: Into[A, B]): Into[A, Option[B]]`, or special-case `A → Option[A]` in `matchFields`, and make the test assert the value. Check the Scala 2 macro too.

### schema: `DynamicValue#partition` puts a variant in both halves, because `retain` keeps every variant

Found 2026-10-22 while writing the Scaladoc of `DynamicValue#retain` and `partition` (wave 5 of the schema Scaladoc, branch `scaladoc-wave-5`, on top of `6cd3c787`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`; the same file with `//> using scala 3.9.0` prints the same lines. Not filed on GitHub yet.

**Summary.** `retain(p)` is documented as keeping only the parts that satisfy `p`, and `partition(p)` as splitting a value into `(retain(p), prune(p))`. `retain` never drops a variant: when nothing inside a variant is kept, it keeps the variant whole, payload included. `prune` keeps it too, so `partition` returns the same variant in both halves, and `retain` returns values that do not satisfy `p`.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

object Repro {
  def main(args: Array[String]): Unit = {
    val book = DynamicValue.Record(
      "title"  -> DynamicValue.string("Zionomicon"),
      "format" -> DynamicValue.Variant("Paperback", DynamicValue.Record("pages" -> DynamicValue.int(700)))
    )
    val isString: DynamicValue => Boolean = _.primitiveValue.exists(_.isInstanceOf[PrimitiveValue.String])
    println(book.retain(isString).toJson.print)
    println(book.prune(isString).toJson.print)
    val (matching, rest) = book.partition(isString)
    println(matching.toJson.print)
    println(rest.toJson.print)
  }
}
// {"title":"Zionomicon","format":{"Paperback":{"pages":700}}}
// {"format":{"Paperback":{"pages":700}}}
// {"title":"Zionomicon","format":{"Paperback":{"pages":700}}}
// {"format":{"Paperback":{"pages":700}}}
```

**Expected.** `{"title":"Zionomicon"}` from `retain` and from the first half of `partition`; the variant only in the second half.

**Root cause.** In `retainRec`, the `Variant` branch returns `current`, the original variant, when the predicate fails for the payload and nothing in it was kept (`schema/shared/src/main/scala/zio/blocks/schema/DynamicValue.scala:2321-2324`). The parent then keeps it, because `hasContent` is `true` for every variant (`:2333-2339`); a primitive in the same position becomes `Null` and is dropped (`:2325-2327`). `partition` is `(retain(dv, path, p), prune(dv, path, p))` (`:2352`).

**Impact.** Code that splits a value with `partition`, or projects it with `retain`, `retainPath` or `project`, gets every sealed-trait value (including every `Option`, encoded as a `Some`/`None` variant) in the "kept" part, whatever the predicate says. The Scaladoc of `retain` and `partition` now describes this.

**Test gap.** `DynamicValueSpec` "retain on Variant where predicate fails and child empty" (`schema/shared/src/test/scala/zio/blocks/schema/DynamicValueSpec.scala:2015-2024`) asserts the current behaviour, the whole variant, for a variant at the root. No test partitions a value that contains a variant.

**Suggested fix.** In the `Variant` branch, return `Null` (so the parent drops it) when neither the predicate nor the payload keeps anything, and make `hasContent` look at the payload of a variant. A variant at the root that keeps nothing then becomes `Null`, as a primitive root does. Change the test above and add a `partition` test with a variant field.

### schema: `DynamicValue#modifyOrFail` ignores a partial function that is not defined, except at the root

Found 2026-10-22 while writing the Scaladoc of `DynamicValue#modifyOrFail` (wave 5 of the schema Scaladoc, branch `scaladoc-wave-5`, on top of `6cd3c787`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`; the same file with `//> using scala 3.9.0` prints the same lines. Not filed on GitHub yet.

**Summary.** The Scaladoc of `DynamicValue#modifyOrFail` and `DynamicValueSelection#modifyOrFail` said that they return `Left` when "the partial function is not defined". That holds only for the root path. For any other path, a value at which the partial function is not defined is left unchanged and the result is `Right`.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

object Repro {
  def main(args: Array[String]): Unit = {
    val book  = DynamicValue.Record("title" -> DynamicValue.string("Zionomicon"), "year" -> DynamicValue.int(2021))
    val onlyStrings: PartialFunction[DynamicValue, DynamicValue] = {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) => DynamicValue.string(s.toUpperCase)
    }
    println(book.modifyOrFail(DynamicOptic.root.field("year"))(onlyStrings))
    println(book.modifyOrFail(DynamicOptic.root)(onlyStrings))
    println(book.select.modifyOrFail(DynamicOptic.root.field("year"))(onlyStrings).either)
  }
}
// Right({
//   title: "Zionomicon",
//   year: 2021
// })
// Left(zio.blocks.schema.SchemaError: Partial function not defined at root)
// Right(Chunk({
//   title: "Zionomicon",
//   year: 2021
// }))
```

**Expected.** `Left` for the first and third calls, as for the root path, or the same `Right` for all three if leaving such values unchanged is intended.

**Root cause.** `modifyAtPathOrFail` checks `pf.isDefinedAt` only when the path is empty (`schema/shared/src/main/scala/zio/blocks/schema/DynamicValue.scala:1318-1320`). For a non-empty path it calls `modifyAtPath` with `v => if (pf.isDefinedAt(v)) pf(v) else v` (`:1322`), so an undefined value stays as it is and only "path not found" can fail.

**Impact.** Callers who use the partial function as a guard (for example "only rewrite strings") and expect `Left` when the guard fails get a silent no-op. The Scaladoc now describes the current behaviour.

**Test gap.** `DynamicValueSpec` tests an undefined partial function only at the root (`schema/shared/src/test/scala/zio/blocks/schema/DynamicValueSpec.scala:389-392`).

**Suggested fix.** Decide on one rule. To fail, track in the function passed to `modifyAtPath` whether `pf` was undefined at any reached value and return `Left` with the path; otherwise drop the root special case so the root behaves like every other path. Add a test for a non-root path.

### schema: `DynamicValueType` and `DynamicValueMergeStrategy` print as `<function1>` and `<function3>`, so error messages say "Expected <function1> but got <function1>"

Found 2026-10-22 while writing the Scaladoc of `DynamicValueType`, `DynamicValueMergeStrategy` and `DynamicValueSelection` (wave 5 of the schema Scaladoc, branch `scaladoc-wave-5`, on top of `6cd3c787`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`; the same file with `//> using scala 3.9.0` prints the same lines. Not filed on GitHub yet.

**Summary.** `DynamicValueType` extends `DynamicValue => Boolean` and `DynamicValueMergeStrategy` extends a three-argument function. Their case objects do not get the usual case-object `toString`, so they print as `<function1>` and `<function3>`. Error messages that name a value type with string interpolation are unreadable: `DynamicValueSelection#as`, `unwrap` and `asPrimitive`, and the kind mismatches of `DynamicSchema#check`.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

object Repro {
  def main(args: Array[String]): Unit = {
    val title = DynamicValue.string("Zionomicon")
    println(DynamicValueType.Record)
    println(DynamicValueMergeStrategy.Auto)
    println(title.select.as(DynamicValueType.Record))
    println(title.select.unwrap(DynamicValueType.Sequence))
    println(title.select.asPrimitive(PrimitiveType.Int(Validation.None)))
    println(Schema[Int].toDynamicSchema.check(DynamicValue.Null))
  }
}
// <function1>
// <function3>
// Left(zio.blocks.schema.SchemaError: Expected <function1> but got <function1>)
// Left(zio.blocks.schema.SchemaError: Cannot unwrap <function1> as <function1>)
// Left(zio.blocks.schema.SchemaError: Expected primitive Int(None) but got <function1>)
// Some(zio.blocks.schema.SchemaError: Expected Primitive(Int), got <function1> at: .)
```

**Expected.** `Record`, `Auto`, `Expected Record but got Primitive`, `Cannot unwrap Primitive as Sequence`, `Expected primitive Int(None) but got Primitive`, and `Expected Primitive(Int), got Null at: .`.

**Root cause.** `scala.Function1` and `scala.Function3` override `toString` (`"<function1>"`, `"<function3>"`), and a case object only generates `toString` when it does not inherit a concrete one from a class other than `AnyRef`. The traits are declared at `schema/shared/src/main/scala/zio/blocks/schema/DynamicValueType.scala:36` and `DynamicValueMergeStrategy.scala:38`. The messages are built at `DynamicValueSelection.scala:475`, `:491` and `:506`, and at `DynamicSchema.scala:307`, `:312`, `:317`, `:322` and `:423`.

**Impact.** Every failed `as`, `unwrap` or `asPrimitive` on a selection, and every `DynamicSchema#check` failure for a value of the wrong kind, gives a message that does not say what was expected or found.

**Test gap.** No test asserts the text of these messages or the `toString` of the case objects.

**Suggested fix.** Override `toString` in each case object (or once in each trait, with a `name` member), and add tests for the messages above.

### schema: `DynamicSchema.toDynamicValue` overflows the stack for a recursive type, so its schema cannot be stored

Found 2026-10-22 while writing the Scaladoc of `DynamicSchema` (wave 5 of the schema Scaladoc, branch `scaladoc-wave-5`, on top of `6cd3c787`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`; the same file with `//> using scala 3.9.0` prints the same lines. Not filed on GitHub yet.

**Summary.** `DynamicSchema.toDynamicValue`, and so `DynamicSchema.schema` and every codec derived from it, follow every `Reflect.Deferred` node. A schema of a recursive type, such as a tree, is cyclic through a `Deferred`, so converting it never ends and throws `StackOverflowError`. Checking values with the same `DynamicSchema` works.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

case class Chapter(title: String, sections: List[Chapter])
object Chapter {
  implicit val schema: Schema[Chapter] = Schema.derived
}

object Repro {
  def attempt(label: String)(f: => Any): Unit =
    try { f; println(s"$label: ok") }
    catch { case e: Throwable => println(s"$label: ${e.getClass.getName}") }

  def main(args: Array[String]): Unit = {
    val dynamicSchema = Schema[Chapter].toDynamicSchema
    println(dynamicSchema.check(Schema[Chapter].toDynamicValue(Chapter("Fibers", Nil))))
    attempt("toDynamicValue")(DynamicSchema.toDynamicValue(dynamicSchema))
    attempt("JSON encode")(DynamicSchema.schema.jsonCodec.encodeToString(dynamicSchema))
  }
}
// None
// toDynamicValue: java.lang.StackOverflowError
// JSON encode: java.lang.StackOverflowError
```

**Expected.** A finite `DynamicValue` that refers back to the recursive type (for example by its type id), and that `DynamicSchema.fromDynamicValue` turns back into an equivalent schema.

**Root cause.** `reflectToDynamicValue` handles a `Deferred` node by converting its value (`schema/shared/src/main/scala/zio/blocks/schema/DynamicSchema.scala:1671`), with no record of the nodes already being converted, so the stack trace repeats `reflectToDynamicValue` and `termToDV`. The serialized format (`:1584-1672`) has no case for a reference to an enclosing type, so `dynamicValueToReflect` could not read one back either.

**Impact.** The schema-registry use that `dynamic-schema.md` describes (store a `DynamicSchema`, load it elsewhere, rebind it) fails for any recursive type, with an error that is not catchable as a non-fatal exception (`Try` lets it through). The Scaladoc of `DynamicSchema` and `DynamicSchema.toDynamicValue` now says so.

**Test gap.** `DynamicSchemaSpec` "Schema[DynamicSchema] round-trip" (`schema/shared/src/test/scala-3/zio/blocks/schema/DynamicSchemaSpec.scala:1795`) uses only non-recursive schemas; the recursive-schema tests (`:774`) check values but never convert the schema.

**Suggested fix.** Keep a set of the type ids being converted; on a `Deferred` whose type id is in the set, emit a reference case (for example `Variant("Ref", Record("typeId" -> ...))`), and in `dynamicValueToReflect` resolve such references to a `Reflect.Deferred` of the enclosing node. Add a round-trip test for a recursive type.

### schema: `SchemaExpr.schemaDefault` returns a `SchemaExpr[Any, A]` that throws `ClassCastException` on input that is not an `A`

Found 2026-10-22 while writing the Scaladoc of `SchemaExpr` (wave 5 of the schema Scaladoc, branch `scaladoc-wave-5`, on top of `6cd3c787`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`; the same file with `//> using scala 3.9.0` prints the same lines. Not filed on GitHub yet.

**Summary.** `SchemaExpr.schemaDefault[A]` returns a literal expression of the schema's default value, typed `SchemaExpr[Any, A]`, so the compiler accepts it next to an expression on any input type. Unlike `SchemaExpr.literal`, its input schema is the schema of `A`, so evaluating it on anything that is not an `A` throws.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._
import scala.util.Try

object Repro {
  def main(args: Array[String]): Unit = {
    implicit val pages: Schema[Int]   = Schema.int.defaultValue(700)
    val default: SchemaExpr[Any, Int] = SchemaExpr.schemaDefault[Int].get
    println(default.evalDynamic(350))
    println(Try(default.evalDynamic("Zionomicon")))
  }
}
// Right(List(700))
// Failure(java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Integer (java.lang.String and java.lang.Integer are in module java.base of loader 'bootstrap'))
```

**Expected.** `Right(List(700))` for both inputs, as `SchemaExpr.literal` gives: a literal does not read its input.

**Root cause.** `schemaDefault` passes `schema.asInstanceOf[Schema[Any]]` as the input schema (`schema/shared/src/main/scala/zio/blocks/schema/SchemaExpr.scala:348`), and `eval`/`evalDynamic` always convert the input with the input schema first (`:100`, `:122`). `literal` uses a `Schema[Unit]` transform that ignores its input instead (`:358-363`).

**Impact.** An expression built with `schemaDefault` and combined with one on another type (for example in a migration) compiles and then fails at run time with an exception rather than a `Left`. The Scaladoc of `schemaDefault` now says so.

**Test gap.** No test calls `SchemaExpr.schemaDefault`.

**Suggested fix.** Build it with `literal(defaultValue)` (or the same `Schema[Unit]` transform), and add a test that evaluates it on an input of another type.

### schema: `DynamicValue#toString` and `toJson` throw for a `Float` or `Double` that is NaN or infinite

Found 2026-10-22 while writing the Scaladoc of `DynamicValue#toString` and `toEjson` (wave 5 of the schema Scaladoc, branch `scaladoc-wave-5`, on top of `6cd3c787`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`; the same file with `//> using scala 3.9.0` prints the same lines. Not filed on GitHub yet.

**Summary.** `DynamicValue#toString` (and `toEjson`) render `Float` and `Double` primitives with the JSON number codec, which rejects NaN and the infinities. Printing any value that contains one throws `JsonCodecError`. Error messages that include a value are built with `toString`, so an operation that should return `Left` throws instead.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._
import scala.util.Try

object Repro {
  def main(args: Array[String]): Unit = {
    val rating = DynamicValue.Record("title" -> DynamicValue.string("Zionomicon"), "rating" -> DynamicValue.double(Double.NaN))
    println(Try(rating.toString))
    println(Try(DynamicValue.float(Float.PositiveInfinity).toString))
    // An error message that shows the value throws instead of being returned
    val notNaN = DynamicSchemaExpr.Not(DynamicSchemaExpr.Literal(DynamicValue.double(Double.NaN), Schema[Double]))
    println(Try(notNaN.eval(DynamicValue.Null)))
  }
}
// Failure(zio.blocks.schema.json.JsonCodecError: illegal number: NaN)
// Failure(zio.blocks.schema.json.JsonCodecError: illegal number: Infinity)
// Failure(zio.blocks.schema.json.JsonCodecError: illegal number: NaN)
```

`DynamicValue#toJson` fails on the same values with another exception (found in review 1 of wave 5; same versions, the same file with `//> using scala 3.9.0` prints the same lines):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._
import scala.util.Try

object Repro {
  def main(args: Array[String]): Unit = {
    val rating = DynamicValue.Record("title" -> DynamicValue.string("Zionomicon"), "rating" -> DynamicValue.double(Double.NaN))
    println(Try(rating.toJson))
    println(Try(DynamicValue.float(Float.NegativeInfinity).toJson))
  }
}
// Failure(java.lang.IllegalArgumentException: Infinity or NaN)
// Failure(java.lang.IllegalArgumentException: Infinity or NaN)
```

**Expected.** `NaN`, `Infinity` and `-Infinity` in the output (or quoted strings), and `Left(... Expected Boolean, got: NaN)` from the expression. `toJson` either rejects such a value with a clear error or writes it as JSON can hold it (for example as a string); today it throws from `Json.Number`, which converts the number with `JsonWriter.toBigDecimal` (`schema/shared/src/main/scala/zio/blocks/schema/json/Json.scala:829`, `:837`, called from `:997-998`).

**Root cause.** `primitiveToEjson` calls `JsonCodec.floatCodec.encodeToString` and `JsonCodec.doubleCodec.encodeToString` (`schema/shared/src/main/scala/zio/blocks/schema/DynamicValue.scala:2872-2873`). The expression's message is built in `extractBoolean` from `$dv` (`DynamicSchemaExpr.scala:52`); other messages built the same way include `DynamicValue#get(key)` (`DynamicValue.scala:209`) and the conversions of `SchemaExpr.ConversionType`.

**Impact.** Logging, debugging or reporting an error about a value with a NaN rating, price or measurement crashes the caller. `DynamicSchemaExpr` and `SchemaExpr` evaluation can throw instead of returning `Left`. The Scaladoc of `toString` now says so.

**Test gap.** The `toEjson` tests (`schema/shared/src/test/scala/zio/blocks/schema/DynamicValueSpec.scala:2824-2840`) use no NaN or infinite value.

**Suggested fix.** In `primitiveToEjson`, write non-finite values directly (`sb.append(v.value)` gives `NaN`, `Infinity`, `-Infinity`), and add tests for both types.

### schema: `Schema#toDynamicSchema` drops the default values of case class fields

Found 2026-10-22 while writing the Scaladoc of `DynamicSchema#getDefaultValue` (wave 5 of the schema Scaladoc, branch `scaladoc-wave-5`, on top of `6cd3c787`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`; the same file with `//> using scala 3.9.0` prints the same lines. Not filed on GitHub yet.

**Summary.** `dynamic-schema.md` said that `Schema#toDynamicSchema` preserves default values. The default value of a case class field (`edition: Int = 1`) is available from the typed schema, but `DynamicSchema#getDefaultValue` on that field returns `None`, and `DynamicSchema.toDynamicValue` does not write it, so a stored schema loses it.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

case class Book(title: String, edition: Int = 1)
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

object Repro {
  def main(args: Array[String]): Unit = {
    val edition = DynamicOptic.root.field("edition")
    println(Schema[Book].get(edition).flatMap(_.getDefaultValue))
    val dynamicSchema = Schema[Book].toDynamicSchema
    println(dynamicSchema.get(edition).flatMap(r => DynamicSchema(r).getDefaultValue))
    println(DynamicSchema.toDynamicValue(dynamicSchema).toString.contains("defaultValue: 1"))
  }
}
// Some(1)
// None
// false
```

**Expected.** `Some(1)`, `Some(1)` and `true`.

**Root cause.** Derivation records a field default as a function on the field's `Reflect.Deferred` node (`deferredDefaultValue`, `schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala:1822`, set by `Deferred#defaultValue` at `:1851`), not as the `storedDefaultValue` `DynamicValue` of the node. `DynamicSchema#getDefaultValue` reads only the `storedDefaultValue` of the node behind a `Deferred` (`DynamicSchema.scala:164`), and `reflectToDynamicValue` converts only that node (`:1671`), so the function is never evaluated or written.

**Impact.** A consumer of a stored or rebound schema cannot fill in missing fields from their defaults, and `DynamicSchema#check` (which treats every field as required) cannot be relaxed with them. The reference page and the Scaladoc of `DynamicSchema` and `getDefaultValue` now say so.

**Test gap.** The `DynamicSchemaSpec` default-value tests set defaults with `DynamicSchema#defaultValue` or on the `Reflect` (`schema/shared/src/test/scala-3/zio/blocks/schema/DynamicSchemaSpec.scala:727`, `:736`, `:2000`); none uses a case class parameter default.

**Suggested fix.** When dropping bindings, evaluate a `Deferred`'s default value with the bound schema and store it as `storedDefaultValue` (a `DynamicValue`), so both `getDefaultValue` and `toDynamicValue` see it. Add a test with a case class parameter default.

### schema: `DynamicValue#setOrFail` and `modifyOrFail` return `Right` unchanged when the path stops after a matching field or map key

Found 2026-10-22 in review 1 of the wave 5 Scaladoc of `DynamicValue#setOrFail` and `modifyOrFail` (branch `scaladoc-wave-5`, on top of `6cd3c787`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`; the same file with `//> using scala 3.9.0` prints the same lines. Not filed on GitHub yet.

**Summary.** `setOrFail` and `modifyOrFail` return `Left` with `Path not found` when the path reaches nothing, but only when the step that fails is the first one, or comes after a step other than a field or a map key. When a field or map-key step matches and a later step reaches nothing, they return `Right` with the value unchanged, although `get` on the same path fails. `DynamicValueSelection#setOrFail` and `modifyOrFail` inherit this.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

object Repro {
  def main(args: Array[String]): Unit = {
    val book = DynamicValue.Record(
      "title"   -> DynamicValue.string("Zionomicon"),
      "details" -> DynamicValue.Record("year" -> DynamicValue.int(2021))
    )
    val missing = DynamicOptic.root.field("details").field("pages")
    println(book.get(missing).either)
    println(book.setOrFail(missing, DynamicValue.int(700)) == Right(book))
    println(book.modifyOrFail(missing) { case v => v } == Right(book))
    println(book.select.setOrFail(missing, DynamicValue.int(700)).either.isRight)
    val editions = DynamicValue.Map(DynamicValue.string("first") -> DynamicValue.Record.empty)
    println(editions.setOrFail(DynamicOptic.root.atKey("first").field("year"), DynamicValue.int(2021)) == Right(editions))
    println(book.setOrFail(DynamicOptic.root.field("pages"), DynamicValue.int(700)))
  }
}
// Left(zio.blocks.schema.SchemaError: Path not found at: .details.pages)
// true
// true
// true
// true
// Left(zio.blocks.schema.SchemaError: Path not found at: .pages)
```

**Expected.** `Left(... Path not found at: .details.pages)` from every `...OrFail` call above, as `get` reports.

**Root cause.** In `modifyAtPath`, the `Field` and `AtMapKey` branches set `found = true` as soon as the name or key matches, before the recursive call (`schema/shared/src/main/scala/zio/blocks/schema/DynamicValue.scala:1173` and `:1208`). When the recursion returns `None`, the entry is kept and the branch still returns `Some` of a rebuilt, unchanged container. The other branches (`AtIndices`, `AtMapKeys`, `Elements`, `MapKeys`, `MapValues`) set `found` only when the recursion returns `Some`, and `deleteAtPath` and `insertAtPath` do the same in their `Field` and `AtMapKey` branches.

**Impact.** Code that uses `setOrFail` to detect a missing nested field, for example to fall back to `insert`, silently loses the write. The Scaladoc of both methods now describes the current behaviour.

**Test gap.** `DynamicValueSpec` tests `setOrFail` and `modifyOrFail` only with a one-step missing path (`schema/shared/src/test/scala/zio/blocks/schema/DynamicValueSpec.scala:384`, `:409`).

**Suggested fix.** Set `found = true` only when the recursive call returns `Some`, as the other branches do, and add tests with a two-step path through a record and through a map key.

### schema: `DynamicValue#diff` never removes a record field, so applying the patch does not give `that`

Found 2026-10-22 in review 1 of the wave 5 Scaladoc of `DynamicValue#diff` (branch `scaladoc-wave-5`, on top of `6cd3c787`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`; the same file with `//> using scala 3.9.0` prints the same lines. Not filed on GitHub yet.

**Summary.** `diff(that)` is meant to return a patch that turns a value into `that`. For records it only sets the fields of `that`: a field that the old record has and `that` lacks is ignored. The patch can even be empty for two different records, and applying it leaves the field in place. Maps do get `Remove` operations for missing keys.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.patch.PatchMode

object Repro {
  def main(args: Array[String]): Unit = {
    val before = DynamicValue.Record("title" -> DynamicValue.string("Zionomicon"), "subtitle" -> DynamicValue.string("Draft"))
    val after  = DynamicValue.Record("title" -> DynamicValue.string("Zionomicon"))
    val patch  = before.diff(after)
    println(patch.isEmpty)
    println(patch.apply(before, PatchMode.Strict).map(_ == after))
    val editionsBefore = DynamicValue.Map(DynamicValue.string("first") -> DynamicValue.int(2021), DynamicValue.string("draft") -> DynamicValue.int(2020))
    val editionsAfter  = DynamicValue.Map(DynamicValue.string("first") -> DynamicValue.int(2021))
    println(editionsBefore.diff(editionsAfter).apply(editionsBefore, PatchMode.Strict).map(_ == editionsAfter))
  }
}
// true
// Right(false)
// Right(true)
```

**Expected.** A patch that removes `subtitle`, so the second line prints `Right(true)`, as it does for the map.

**Root cause.** `Differ.diffRecord` walks only the new fields (`schema/shared/src/main/scala/zio/blocks/schema/patch/Differ.scala:203-245`); the comment at `:242-243` says "Fields that exist in old but not in new are ignored (Records are immutable, we can't delete fields)". A `DynamicValue.Record` can lose fields, and `DynamicValue#delete` removes them, so nothing prevents a removal operation.

**Impact.** Syncing or auditing data with `diff` and `apply` keeps stale fields, and `diff` reports no change between records that differ by a removed field. The Scaladoc of `DynamicValue#diff` now says so.

**Test gap.** `DiffSpec` "Record diffing" (`schema/shared/src/test/scala/zio/blocks/schema/patch/DiffSpec.scala:377`) and the roundtrip law tests (`:704`, `:713`) use records with the same field names on both sides.

**Suggested fix.** Emit a removal for each old field that `that` lacks (a field-removal operation, or `Set` of the whole record when no such operation exists), and add a roundtrip test where `that` has fewer fields.

### schema: a `SchemaExpr` with a `literal` on the left yields no results

Found 2026-10-22 in review 1 of the wave 5 Scaladoc of `SchemaExpr` (branch `scaladoc-wave-5`, on top of `6cd3c787`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`; the same file with `//> using scala 3.9.0` prints the same lines. Not filed on GitHub yet.

**Summary.** Every binary `SchemaExpr` constructor converts the input with the input schema of its left (or first) operand. `SchemaExpr.literal`'s input schema discards the input, so with a literal on the left the right operand sees a `Unit` value instead of the input, its optic reaches nothing, and the expression evaluates to `Right` of an empty sequence instead of a result. The companion's Scaladoc suggested exactly this pattern ("a literal on the left of a comparison").

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

case class Book(title: String, year: Int)
object Book extends CompanionOptics[Book] {
  implicit val schema: Schema[Book] = Schema.derived
  val title: Lens[Book, String] = $(_.title)
  val year: Lens[Book, Int]     = $(_.year)
}

object Repro {
  def main(args: Array[String]): Unit = {
    val zionomicon = Book("Zionomicon", 2021)
    val year       = SchemaExpr.optic[Book, Int](Book.year.toDynamic, Book.schema)
    val title      = SchemaExpr.optic[Book, String](Book.title.toDynamic, Book.schema)
    println(SchemaExpr.relational(year, SchemaExpr.literal[Book, Int](2020), SchemaExpr.RelationalOperator.GreaterThan).eval(zionomicon))
    println(SchemaExpr.relational(SchemaExpr.literal[Book, Int](2020), year, SchemaExpr.RelationalOperator.LessThan).eval(zionomicon))
    println(SchemaExpr.stringConcat(SchemaExpr.literal[Book, String]("Title: "), title).eval(zionomicon))
    println(SchemaExpr.stringConcat(title, SchemaExpr.literal[Book, String]("!")).eval(zionomicon))
  }
}
// Right(Chunk(true))
// Right(List())
// Right(List())
// Right(Chunk(Zionomicon!))
```

**Expected.** `Right(List(true))` for `2020 < year` and `Right(List(Title: Zionomicon))` for the concatenation, as with the literal on the right.

**Root cause.** `relational`, `logical`, `arithmetic`, `bitwise` and `stringConcat` pass `left.inputSchema` (`schema/shared/src/main/scala/zio/blocks/schema/SchemaExpr.scala:403`, `:429`, `:472`, `:498`, `:521`); the other string constructors pass the input schema of their `string` operand. `literal`'s input schema is `Schema[Unit].transform[S](_ => null, _ => ())` (`:369`), so `eval` and `evalDynamic` (`:100`, `:122`) turn the input into a `Unit` value before evaluating the whole tree, and the `Select` on the right finds no field in it.

**Impact.** A filter written as `literal op optic` silently matches nothing: `eval` returns `Right(Seq())` rather than an error, which a caller that checks for `true` reads as `false`. The Scaladoc of the companion and of `literal` now say to put literals on the right.

**Test gap.** `SchemaExprSpec` builds every expression from literals of input type `Any` (`schema/shared/src/test/scala/zio/blocks/schema/SchemaExprSpec.scala:24-36`), so no test mixes a literal with an optic.

**Suggested fix.** Take the input schema from an operand that is not a literal (for example, from whichever operand's tree contains a `Select`), or give `SchemaExpr` a way to carry "no input needed", and add tests with a literal on each side.

## Usability traps

These behave as designed but surprise users. Each should at least be documented where users meet it.

### schema: `Lens.apply` and `Prism.apply` accept a term of another type and fail later with `Index -1`

Found 2026-10-22 in review of the wave 4 Scaladoc (`docs/dev/backlog.md` section 11, branch `scaladoc-wave-4`, on top of `bffadae7`; line numbers are those of the commit that last edits this entry). The code is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`.

**Summary.** `Lens.apply(source, focusTerm)` and `Prism.apply(source, focusTerm)` look the term up in `source` by name and do not check that it is there. A term from another record or variant gives index -1. A lens builds without complaint and throws `ArrayIndexOutOfBoundsException: Index -1` the first time it is used; a prism throws the same exception at construction. Neither message names the term.

**Reproduction** (Scala 3.9.0, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._
import scala.util.Try

case class Author(name: String)
object Author { implicit val schema: Schema[Author] = Schema.derived }
case class Book(title: String, year: Int)
object Book { implicit val schema: Schema[Book] = Schema.derived }

sealed trait Format
case class Paperback(pages: Int) extends Format
case class Ebook(sizeMb: Double) extends Format
object Format { implicit val schema: Schema[Format] = Schema.derived }

sealed trait Genre
case class Fiction(rating: Int) extends Genre
object Genre { implicit val schema: Schema[Genre] = Schema.derived }

object Main {
  def main(args: Array[String]): Unit = {
    val book       = Book.schema.reflect.asRecord.get
    val authorName = Author.schema.reflect.asRecord.get.fields(0).asInstanceOf[Term.Bound[Book, String]]
    val lens       = Lens(book, authorName)
    println(lens)
    println(Try(lens.get(Book("Zionomicon", 2021))))

    val format  = Format.schema.reflect.asVariant.get
    val fiction = Genre.schema.reflect.asVariant.get.cases(0).asInstanceOf[Term.Bound[Format, Paperback]]
    println(Try(Prism(format, fiction)))
  }
}
// Lens(_.name)
// Failure(java.lang.ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 2)
// Failure(java.lang.ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 2)
```

**Expected.** Both constructors fail at once with an `IllegalArgumentException` that names the term and the type: "`name` is not a field of `Book`".

**Root cause.** `Lens.apply` and `Prism.apply` check only for null (`schema/shared/src/main/scala/zio/blocks/schema/Optic.scala:685-686`, `:959-960`). `LensImpl.init`, which runs on first use, indexes `source.registers` with `fieldIndexByName(name)` (`:721`), and the `PrismImpl` constructor indexes `source.matchers` with `caseIndexByName(name)` (`:992`). Both return -1 for an unknown name (`schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala:405`, `:619`, `:2112`).

**Impact.** A hand-built optic with a wrong term fails late (the lens) or with an index error that does not say what is wrong. `Reflect.Record#lensByName`, `Reflect.Variant#prismByName` and the `$` macro avoid the problem.

**Test gap.** No test builds a lens or prism from a term that does not belong to the source.

**Suggested fix.** In both constructors, `require(source.fieldIndexByName(focusTerm.name) >= 0, ...)` (or `caseIndexByName`) with a message that names the term and `source.typeId`.

### http-model-schema: `query[T]` and `header[T]` compile for any schema but decode only primitives

Found 2026-10-21 while writing H-36 (`docs/guides/validate-query-parameters-and-headers.md`). Verified against the published 0.0.56.

`request.query[Option[Int]]("limit")`, `query[List[Int]]` and `query[Isbn]` for a wrapper type compile, because any `Schema[T]` is accepted, but always return `Malformed(key, value, "Unsupported schema type for string decoding")`, even for `?limit=5`. `StringDecoder.decode` handles `Reflect.Primitive` only (`http-model-schema/shared/src/main/scala/zio/http/schema/StringDecoder.scala:26-29`). The error blames the client's value for a programming mistake, and a 400 handler sends it back to the client. The reference page says the module "supports decoding to any type with a `Schema[T]` instance". Reproduction (`scala-cli run`, Scala 3.9.0, `zio-blocks-next-http-model-schema` 0.0.56):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-http-model-schema:0.0.56

import zio.blocks.schema.Schema
import zio.http._
import zio.http.schema._

final case class Isbn(value: String)
object Isbn { implicit val schema: Schema[Isbn] = Schema[String].transform(Isbn(_), _.value) }

object OptionQuery {
  def main(args: Array[String]): Unit = {
    val request = Request.get(URL.parse("/books?limit=5&isbn=978-0981531687").toOption.get)
    println(request.query[Option[Int]]("limit"))
    println(request.query[List[Int]]("limit"))
    println(request.query[Isbn]("isbn"))
    println(request.query[Int]("limit"))
  }
}
// Left(Malformed(limit,5,Unsupported schema type for string decoding))
// Left(Malformed(limit,5,Unsupported schema type for string decoding))
// Left(Malformed(isbn,978-0981531687,Unsupported schema type for string decoding))
// Right(5)
```

Suggested fix: support wrappers (the codec derivers already do, through `ParamCodecSupport.wrapperFieldCodec`) and map `Option[T]` to "missing is `None`". At least document the primitive-only limit on the reference page, and consider reporting it as a distinct error rather than `Malformed`.

### http-model-schema: `queryOrElse` and `headerOrElse` also swallow malformed values, and no decoder applies `Validation`

Found 2026-10-21 while writing H-36. Verified against the published 0.0.56.

`queryOrElse[Int]("limit", 20)` returns `20` for `?limit=abc`, not only for a missing `limit` (`QueryParamsSchemaOps.scala`, `query[T](key).getOrElse(default)`), so a client's typo is silently ignored. The reference page says so ("errors are silently ignored"), but the name suggests "or else, when missing". Separately, none of the decoders checks the `Validation` a `Schema` can carry: a `Schema[Int]` whose primitive type has `Validation.Numeric.Range(Some(1), Some(100))` decodes `?limit=500` to `Right(500)` (`StringDecoder.decodePrimitive` and `ParamCodecSupport.decodePrimitiveString` only parse). The H-36 guide shows a `Missing`-only fallback and range checks written after decoding. Suggested fix: document both on `docs/reference/http-model/schema.md`; consider a `queryOption[T]` that returns `Either[QueryParamError, Option[T]]`.

### telemetry: every call to `metric.counter(name)` registers a new instrument

Found 2026-10-21 while writing T-12, on `main` at `a44dc6db` (same at `v0.0.56`). Verified against the published 0.0.56. Not filed on GitHub yet.

**Summary.** `metric.counter("x")`, `metric.histogram("x")`, `metric.upDownCounter("x")` and `metric.gauge("x")` build and register a new instrument on every call, even when one with the same name exists. The reference pages show the inline form as the normal use (`metric.counter("http.requests").add(1)`, `docs/reference/telemetry/metrics/index.md:15`; `instruments.md:37` and `:172` use it inline too). Code that calls the factory inside a method gets one instrument per call: the reader returns many separate series with the same attributes instead of one total, and the registry grows without bound.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-telemetry` 0.0.56, JDK 21, `scala-cli run`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-telemetry:0.0.56
package bookstore

import zio.blocks.telemetry._

object CounterRepro {
  def restock(title: String): Unit =
    metric.counter("bookstore.books.restocked").add(1L, "title" -> title)

  def main(args: Array[String]): Unit = {
    (1 to 3).foreach(_ => restock("Zionomicon"))
    val sums = metric.reader.collectAllMetrics()
    println(s"instruments: ${sums.size}")
    sums.foreach(println)
  }
}
// instruments: 3
// SumData(List(SumDataPoint(zio.blocks.telemetry.Attributes@cb9700ed,0,1792573831692001433,1)))
// SumData(List(SumDataPoint(zio.blocks.telemetry.Attributes@cb9700ed,0,1792573831694297386,1)))
// SumData(List(SumDataPoint(zio.blocks.telemetry.Attributes@cb9700ed,0,1792573831694308297,1)))
```

**Expected.** Either one instrument with the value 3 (the factory returns the existing instrument for a name, as OpenTelemetry SDKs do), or documentation that tells users to create each instrument once and keep it in a `val`.

**Root cause.** `metric.counter` calls `counterBuilder(name).build()` (`telemetry/shared/src/main/scala/zio/blocks/telemetry/metric.scala:38-39`), and `build()` always creates a new `Counter` and registers it (`Meter.scala:164-168`, `registerCounter` at `:91-93`). The registry has no lookup by name.

**Suggested fix.** Cache instruments by name, kind and unit in `Meter`, or document the "create once" rule on the `metric` reference pages and change the inline examples to `val`s. The T-12 tutorial creates each instrument once and says why.

### schema: `Validation` constraints are checked only by `DynamicSchema#check`, and `DynamicSchema#check` skips `Schema#transform` checks

Found 2026-10-21 while writing the how-to guide `docs/guides/validated-newtypes.md` (backlog H-04), on `main` at `f87e6beb`. The code is the same at tag `v0.0.56`. Verified. Not filed.

**Summary.** The schema module has two ways to state a rule, and each is enforced by a different, non-overlapping path. Built-in `Validation` constraints (`Validation.Numeric.Positive`, `Validation.String.NonEmpty`, ...) live on `PrimitiveType` and are read only by `DynamicSchema#check`/`conforms`: the JSON, TOON and CSV codecs and `Schema#fromDynamicValue` accept values that break them, and `Schema#toJsonSchema` does not emit them. `Schema#transform` checks run in the codecs and `Schema#fromDynamicValue`, but `DynamicSchema#check` never calls them. There is also no public API to attach a `Validation` to a schema: the only way found is to copy the `Reflect.Primitive`.

**Reproduction** (Verified; Scala 2.13.18, `zio-blocks-schema` 0.0.56, run with `scala-cli run ValidationNotEnforced.scala`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56

import zio.blocks.schema._
import zio.blocks.schema.binding.Binding

object Repro extends App {
  // No public API attaches a Validation to a schema; copying the primitive Reflect is the only way found.
  val positiveInt: Schema[Int] = Schema[Int].reflect match {
    case p: Reflect.Primitive[Binding, Int] @unchecked =>
      new Schema(p.copy(primitiveType = PrimitiveType.Int(Validation.Numeric.Positive)))
    case _ => sys.error("unexpected")
  }
  val minusFive = DynamicValue.Primitive(PrimitiveValue.Int(-5))

  println(positiveInt.jsonCodec.decode("-5"))
  // Right(-5)
  println(positiveInt.fromDynamicValue(minusFive))
  // Right(-5)
  println(positiveInt.toJsonSchema)
  // {
  //   "type": "integer",
  //   "maximum": 2147483647,
  //   "minimum": -2147483648
  // }
  println(positiveInt.toDynamicSchema.check(minusFive))
  // Some(zio.blocks.schema.SchemaError: Validation failed: value must be positive)

  // The opposite gap: DynamicSchema#check does not run the check of a Schema#transform wrapper.
  final case class PageCount private (value: Int)
  object PageCount {
    implicit val schema: Schema[PageCount] = Schema[Int].transform(
      n => if (n > 0) new PageCount(n) else throw SchemaError.validationFailed(s"page count must be positive, got $n"),
      _.value
    )
  }
  println(PageCount.schema.fromDynamicValue(minusFive))
  // Left(zio.blocks.schema.SchemaError: page count must be positive, got -5 at: .wrapped)
  println(PageCount.schema.toDynamicSchema.check(minusFive))
  // None
}
```

**Expected.** Either the codecs, `Schema#fromDynamicValue` and `Schema#toJsonSchema` honour `Validation` (with a public way to attach one, such as a modifier), or the docs say plainly that `Validation` is metadata read only by `DynamicSchema`. `DynamicSchema#check` either runs wrapper checks or says it checks structure and `Validation` only.

**Root cause.** In `schema/shared/src/main`, only `DynamicSchema.scala` (`checkPrimitive` at `:394-412`, `checkValidation` at `:414-516`) reads `PrimitiveType#validation`; `JsonSchemaToReflect.scala:261-285` creates `Validation` values when parsing a JSON Schema, and nothing in the codec derivers or `Reflect#fromDynamicValue` reads them. `DynamicSchema#check` (`DynamicSchema.scala:93`) walks the `Reflect` tree without the `Binding`, so it has no `wrap` function to call for a wrapper.

**Impact.** `validation.md` presents `Validation` as "automatic enforcement during schema-based decoding" (see Documentation errors), so a user who relies on it accepts invalid data. A user who validates incoming `DynamicValue`s with `DynamicSchema#check` misses every newtype rule. The guide recommends smart constructors with `Schema#transform` and says that `Validation` is enforced only by `DynamicSchema#check`.

**Test gap.** No codec spec decodes a value that breaks a `Validation`, and no `DynamicSchema` spec checks a wrapper schema built with `Schema#transform`.

**Suggested fix.** Decide what `Validation` is for. If it is meant to be enforced, check it in `Reflect.Primitive`'s decode paths (or in each deriver's primitive codec), emit it from `toJsonSchema` (`minimum`/`exclusiveMinimum`, `minLength`, `pattern`), and add a public way to attach it. If it is metadata, document that in `validation.md` and in the `Validation` Scaladoc. Document that `DynamicSchema#check` does not run wrapper checks.

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
- **A nested `connect` inside `transact` takes a second pooled connection.** With a small pool it times out: `SQLTransientConnectionException: bookstore - Connection is not available, request timed out after 1001ms (total=1, active=1, idle=0, waiting=0)`. Pass the `DbCon`/`DbTx` down instead of calling `connect` again. Verified against PostgreSQL 16 while writing H-17; the SQLite counterpart (`SQLITE_BUSY`) is above.
- **`$` only accepts a method call on its receiver.** `$(transactor)(t => restockShelf(t))` fails with "cannot be passed as an argument to a function or method", so helpers must take `using DbCon` and be called as `$(transactor)(_.transact(work()))`. Verified while writing H-17.
- **Scala 3 `object X extends App` deadlocks when worker threads touch the object.** Code in the `App` body runs inside the object's static initializer, so any other thread that reads a member of the object waits on the class-init lock the main thread holds. Hit in H-17's concurrency example and in T-06 (the streams deadlock above); both use `def main` instead. Worth a general note in AGENTS.md and the examples templates.
- **Scope and Wire error codes never appear in compiler output.** `ErrorMessages.scala` numbers them E1-E17, RE1-RE3 and W1, but the messages users see carry no code, so docs must index errors by message text. Found while writing H-31.
- **`defer` on a closed scope is a silent no-op** (`Scope.scala:202`), while `allocate` and `$` throw. Found while writing H-31.
- **`jdbc:sqlite::memory:` gives every connection its own empty database.** Code that opens more than one connection loses its tables. Found while writing T-07; now noted in `transactor.md`.
- **On Scala 2.13, `case class Isbn private (value: String)` still has a public `apply` and `copy`.** `Isbn("junk")` and `isbn.copy(value = "junk")` compile outside the companion and skip the smart constructor, unless the build adds `-Xsource-features:case-apply-copy-access` (or `-Xsource:3-cross`); Scala 3 makes both private. Every newtype example in `validation.md` relies on the private constructor alone. Verified with scala-cli on 2.13.18 and 3.9.0 while writing H-04; the guide adds the flag to its prerequisites.

### sql: `SqlQuery#orderBy` and `groupBy` can only name columns of the `FROM` table

Found 2026-10-21 while writing the how-to guide `docs/guides/write-joins-and-inspect-generated-sql.md` (backlog H-19), on `main` at `9447794b`; the same at tag `v0.0.56`. Verified with `zio-blocks-next-sql` 0.0.56.

**Summary.** `orderBy(col)` and `groupBy(cols*)` take a bare column name and always render it as `t0."<col>"` (`sql/shared/src/main/scala/zio/blocks/sql/query/QueryRenderer.scala:70-78` for `GROUP BY` and `:85-97` for `ORDER BY`). There is no way to sort or group a join by a column of a joined table: `SqlQuery.from(books).join(bookAuthor).orderBy("t1.name")` throws `java.lang.IllegalArgumentException: Invalid SQL column identifier 't1.name'. Only ASCII letters, digits, and underscores are supported, and the first character must be a letter or underscore.` (`SqlQuery.scala:145-148` validates it as a plain identifier, as `groupBy` does at `:137-140`). A name that exists in the joined table but not in `t0` renders as `t0."<col>"` and fails only when the database runs it. Meanwhile `where` takes a free-form `Frag` that may use any alias, so the API is uneven. Users have to start the query from the table they sort by, or sort in Scala. The H-19 guide documents this in a caution box. A fix would be an `orderBy(alias, col, dir)` overload, or a column reference type returned by the joined table.

### telemetry and scope: wildcard imports of both packages make `Resource` ambiguous

Found 2026-10-21 while writing T-15 (`docs/guides/small-json-backend-from-blocks.md`), on `main` at `1958367d`. The code is the same at tag `v0.0.56`. Verified by running on Scala 3.9.0 and 2.13.18.

**Summary.** `zio.blocks.telemetry` and `zio.blocks.scope` both define a top-level type named `Resource`: telemetry's is the OpenTelemetry resource (a set of attributes that names the service), scope's is the managed resource that `Resource.from` returns. The telemetry guides teach `import zio.blocks.telemetry._` ("brings every type this tutorial uses"), and the scope guides teach `import zio.blocks.scope._`. A program that uses both, which is any service that logs and manages its resources, fails to compile as soon as it mentions `Resource`, and the follow-on error at `allocate` names telemetry's `Resource` as the argument type.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-scope` and `zio-blocks-next-telemetry` 0.0.56, `scala-cli compile Clash.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-scope:0.0.56
//> using dep dev.zio::zio-blocks-next-telemetry:0.0.56
import zio.blocks.scope._
import zio.blocks.telemetry._

class Db extends AutoCloseable {
  def close(): Unit = println("Db: closed")
}

object Clash {
  def db: Resource[Db] = Resource.from[Db]

  def main(args: Array[String]): Unit =
    Scope.global.scoped { scope =>
      import scope._
      allocate(db)
      log.info("started")
    }
}

// [error] ./Clash.scala:13:11
// [error] Reference to Resource is ambiguous.
// [error] It is both imported by import zio.blocks.scope._
// [error] and imported subsequently by import zio.blocks.telemetry._
// [error]   def db: Resource[Db] = Resource.from[Db]
// [error] ./Clash.scala:13:26
// [error] Reference to Resource is ambiguous.
// [error] It is both imported by import zio.blocks.scope._
// [error] and imported subsequently by import zio.blocks.telemetry._
// [error]   def db: Resource[Db] = Resource.from[Db]
// [error] ./Clash.scala:18:7
// [error] None of the overloaded alternatives of method allocate in class Scope with types
// [error]  [A <: AutoCloseable](value: => A): scope.$[A]
// [error]  [A](resource: zio.blocks.scope².Resource[A]): scope.$[A]
// [error] match arguments ((Clash.db : => zio.blocks.telemetry.Resource²))
```

On Scala 2.13.18 (`zio-blocks-scope` and `zio-blocks-telemetry` 0.0.56, same imports, `def db: Resource[Db] = Resource.from[Db]`), the error is `reference to Resource is ambiguous; it is imported twice in the same scope by import zio.blocks.telemetry._ and import zio.blocks.scope._`.

**Expected.** The two wildcard imports the guides teach work together, or the guides warn about the clash and show the fix.

**Root cause.** `final case class Resource(attributes: Attributes)` at `telemetry/shared/src/main/scala/zio/blocks/telemetry/Resource.scala:27` and `sealed trait Resource[+A]` at `scope/shared/src/main/scala/zio/blocks/scope/Resource.scala:58` are both top-level members of packages that users import with a wildcard.

**Workaround.** Import telemetry's names one by one, as T-15 does: `import zio.blocks.telemetry.{log, metric, trace, AttributeKey, Counter, MetricData}`. Or rename on import: `import zio.blocks.telemetry.{Resource => OtelResource, _}`.

**Impact.** Every service that combines the two modules, which is the main use of `scope` (build services) together with `telemetry` (observe them). The compiler message is clear for the first error, but the `allocate` error that follows can mislead.

**Test gap.** No test or example compiles `import zio.blocks.scope._` together with `import zio.blocks.telemetry._`.

**Fix.** Rename telemetry's type (for example `TelemetryResource` or `ServiceResource`), keeping `Resource` as a deprecated alias in a sub-object so wildcard imports no longer bring it in. If the OpenTelemetry name must stay, add a caution to the telemetry reference and guides that recommends importing telemetry names one by one.

### Release: a stray version `0.017` on Maven Central is the "latest" release of eleven modules

Found 2026-10-21 while writing the how-to guide `docs/guides/add-zio-blocks-to-your-build.md` (backlog H-01), on `main` at `b4179489`. The current release is `v0.0.56`.

**Summary.** Besides the real `0.0.17` release, version `0.017` (no second dot) was published to Maven Central on 2026-02-07, the same day as tag `v0.0.17`. There is no `v0.017` tag or GitHub release. Maven orders `0.017` above `0.0.56` (its second segment, 17, beats 0), so the `<latest>` and `<release>` fields of `maven-metadata.xml` still point at the February build for every module that existed then: `chunk`, `context`, `schema`, `schema-avro`, `schema-bson`, `schema-messagepack`, `schema-thrift`, `schema-toon`, `scope`, `streams` and `typeid`, under both `zio-blocks-` and `zio-blocks-next-`, on all published platforms and Scala versions. Modules added later (`maybe`, `config`, `jwt`, ...) are not affected.

**Reproduction** (coursier 2.x and scala-cli 1.10.1, against Maven Central):

```bash
curl -s https://repo1.maven.org/maven2/dev/zio/zio-blocks-schema_3/maven-metadata.xml | grep -E '<latest>|<release>'
#     <latest>0.017</latest>
#     <release>0.017</release>

cs resolve 'dev.zio:zio-blocks-schema_3:latest.release' | grep zio-blocks-schema
# dev.zio:zio-blocks-schema_3:0.017:default
```

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._
```

```
$ scala-cli compile .
[hint] ./Main.scala:2:15
[hint] "zio-blocks-schema is outdated, update to 0.017"
[hint]      zio-blocks-schema 0.0.56 -> dev.zio::zio-blocks-schema:0.017
```

**Expected.** `latest.release` resolves to `0.0.56`, and scala-cli reports `0.0.56` as up to date.

**Root cause.** A release was published with the version string `0.017` instead of `0.0.17`. Maven Central does not allow deleting or overwriting a published version, so the metadata will keep ranking it highest until a version that sorts above `0.017` (such as `0.1.0`) is released.

**Impact.** Every scala-cli user of the affected modules sees an "outdated, update to 0.017" hint, and following it downgrades them to February code. Builds that use `latest.release` or a version range get `0.017`. Dependency bots such as Scala Steward may also propose the downgrade, unless they are told to ignore it.

**Test gap.** The release workflow does not check that the version derived from the tag has the `MAJOR.MINOR.PATCH` shape.

**Suggested fix.** Add a check to the release workflow that fails when the version does not match `^\d+\.\d+\.\d+(-.+)?$`. Add a Scala Steward `updates.ignore` entry for version `0.017` under `dev.zio` so the repo's own bots skip it. The metadata only stops pointing at `0.017` once a release sorts above it, such as `0.1.0`. Until then, the install guide pins `@VERSION@` and never suggests `latest.release`.

### schema: JSON paths that match nothing change nothing and report nothing, and wildcard reads skip elements without the field

Found 2026-10-21 while writing the how-to guide `docs/guides/json-without-case-classes.md` (backlog H-06), on `main` at `0740013c`. The code is the same at tag `v0.0.56` (no file under `schema/` outside the tests changed since the tag). Verified. Not filed.

**Summary.** Three behaviours of `Json` paths make a typo in a path or a missing field hard to notice. A wildcard read such as `get(p".books[*].price")` skips the elements that lack the field and fails only when none has it. `Json#modify`, `set`, `delete` and `insert` return the document unchanged when the path matches nothing, so a misspelled key changes nothing silently; only the `OrFail` variants report it. And the not-found errors name only the last step (`Field 'isbn' not found`), without the path that led there, while `Json#get(index)` adds `at: [5]` and a path read does not.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-schema` 0.0.56, run with `scala-cli run SilentPaths.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
import zio.blocks.schema.*
import zio.blocks.schema.json.*

@main def run(): Unit = {
  // the second book has a misspelled "prise" key
  val doc = Json.parseUnsafe(
    """{"books":[{"title":"Zionomicon","price":49.99},{"title":"Programming in Scala","prise":59.99}]}"""
  )

  // a wildcard read returns one price for two books, with no error
  println(doc.get(p".books[*].price").asAll[BigDecimal])
  // Right(Chunk(49.99))

  // a misspelled path: modify and set return the document unchanged, with no error
  println(doc.modify(p".books[*].prize")(_ => Json.Number(1)) == doc)
  // true
  println(doc.set(p".books[0].prize", Json.Number(1)) == doc)
  // true

  // only the OrFail variants report it
  println(doc.setOrFail(p".books[0].prize", Json.Number(1)))
  // Left(zio.blocks.schema.SchemaError: Field 'prize' not found)

  // not-found errors name the last step only, not the path to it
  println(doc.get(p".books[0].isbn").one)
  // Left(zio.blocks.schema.SchemaError: Field 'isbn' not found)
  println(doc.get(p".books[5].title").one)
  // Left(zio.blocks.schema.SchemaError: Index 5 out of bounds)
  println(doc.get("books")(5).one)
  // Left(zio.blocks.schema.SchemaError: Index 5 out of bounds (size: 2) at: [5])
}
```

**Expected.** Not necessarily a different default, but a consistent and documented one: errors that carry the full path (`Field 'isbn' not found at: .books[0]`), and reference docs that say the plain change methods ignore missing paths and that wildcard reads skip elements.

**Root cause.** `schema/shared/src/main/scala/zio/blocks/schema/json/Json.scala:1556` (`getAtPath`) fails only when `results.knownSize == 0 && jsons.nonEmpty`, so a partial match succeeds, and it builds `SchemaError(s"Field '$name' not found")` with no path. `Json.scala:132-135`, `:149`, `:162` and `:181` return `this` when the path helper returns `None`.

**Impact.** Code that reads or cleans third-party JSON gets fewer values than it expects or leaves fields in place, with no error. The H-06 guide warns about both and teaches the `OrFail` variants.

**Test gap.** No test reads `[*].field` where only some elements have the field.

**Suggested fix.** Add the path so far to not-found errors (`.atField`/`.atIndex`, as `Json.Array#get` already does with `atIndex`), and document the silent behaviour of the plain change methods in `json.md`.

### schema: `Schema#fromDynamicValue` rejects missing collection, `Option` and defaulted fields that the JSON codec accepts

Found 2026-10-21 while writing the how-to guide `docs/guides/json-without-case-classes.md` (backlog H-06), on `main` at `0740013c`; widened the same day with a case from the H-04 session (`docs/guides/validated-newtypes.md`, PR #1811). The code is the same at tag `v0.0.56`. Verified on Scala 2.13.18 and 3.9.0. Not filed.

**Summary.** There are two ways to decode a `Json` value into a case class: `json.as[A]` (the JSON codec) and `Schema[A].fromDynamicValue(json.toDynamicValue)`, which collects every error instead of stopping at the first. They accept different documents. When a field is absent, the JSON codec uses the field's default value, `None` for an `Option`, or an empty collection. `fromDynamicValue` reports `Missing field '<name>'` for each of them. Their messages for the same type mismatch also differ (`expected Json.Number` vs `Expected BigDecimal`).

**Reproduction** (Verified; Scala 2.13.18, `zio-blocks-schema` 0.0.56, run with `scala-cli run MissingFieldsScala2.scala`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.json._

final case class Entry(title: String, tags: List[String], subtitle: Option[String], edition: Int = 1)
object Entry { implicit val schema: Schema[Entry] = Schema.derived }

object MissingFieldsScala2 {
  def main(args: Array[String]): Unit = {
    val json = Json.parseUnsafe("""{"title": "Zionomicon"}""")
    println(json.as[Entry])
    // Right(Entry(Zionomicon,List(),None,1))
    println(Schema[Entry].fromDynamicValue(json.toDynamicValue))
    // Left(zio.blocks.schema.SchemaError: Missing field 'tags' at: .
    // Missing field 'subtitle' at: .
    // Missing field 'edition' at: .)
  }
}
```

The same program on Scala 3.9.0 with `zio-blocks-next-schema` 0.0.56 (`given Schema[Entry] = Schema.derived`, `@main`) prints the same two lines. A second reproduction shows the differing mismatch messages (Verified; Scala 3.9.0, `zio-blocks-next-schema` 0.0.56, run with `scala-cli run AsVersusDynamic.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
import zio.blocks.schema.*
import zio.blocks.schema.json.*

final case class Book(title: String, authors: List[String], price: BigDecimal, tags: List[String])
object Book { given Schema[Book] = Schema.derived }

@main def run(): Unit = {
  // no "tags" field
  val noTags = Json.parseUnsafe("""{"title":"Zionomicon","authors":["John A. De Goes","Adam Fraser"],"price":49.99}""")
  println(noTags.as[Book])
  // Right(Book(Zionomicon,List(John A. De Goes, Adam Fraser),49.99,List()))
  println(Schema[Book].fromDynamicValue(noTags.toDynamicValue))
  // Left(zio.blocks.schema.SchemaError: Missing field 'tags' at: .)

  // a string where a number belongs: the two messages differ
  val stringPrice = Json.parseUnsafe("""{"title":"Zionomicon","authors":[],"price":"49.99","tags":[]}""")
  println(stringPrice.as[Book])
  // Left(zio.blocks.schema.SchemaError: expected Json.Number at: .price)
  println(Schema[Book].fromDynamicValue(stringPrice.toDynamicValue))
  // Left(zio.blocks.schema.SchemaError: Expected BigDecimal at: .price)
}
```

**Expected.** Both routes accept the same documents, so switching to the error-collecting route (as the H-04 newtype validation guide does) does not start rejecting payloads that `json.as[A]` accepts: `Right(Entry(Zionomicon,List(),None,1))` from both.

**Root cause.** The JSON codec fills absent fields in `JsonFieldInfo#setMissingValueOrError` (`schema/shared/src/main/scala/zio/blocks/schema/json/JsonCodecDeriver.scala:3155-3179` when reading text, `:3182-3204` when decoding a `Json` value): the default value from `Reflect#getDefaultValue` first (`getDefaultValue` at `:3027-3029`), then `None` when `isOptional` (`:616`, unless `withRequireOptionFields`), then `emptyCollectionConstructor()` (`:588-590`, unless `withRequireCollectionFields`), and only then `missing required field`. `Reflect.Record#fromDynamicValue` (`schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala:407-445`) has no such fallback: after reading the record's fields, every field it did not see goes straight to `SchemaError.missingField` (`:437-441`).

**Impact.** A payload that passes `json.as[A]` in one place fails validation in another, for the most common optional shapes (absent `Option`, empty list left out, defaulted field). Users of the error-collecting route have to send every field, which the JSON codec's own encoder does not do: it leaves out `None`, empty collections and default values by default. Verified on 2.13.18: `Json.from(Entry("Zionomicon", Nil, None)).print` is `{"title":"Zionomicon"}`, and `fromDynamicValue` of its `toDynamicValue` fails with the same three `Missing field` errors, while `Schema#toDynamicValue` of the same value round-trips.

**Test gap.** No test decodes the same `Json` through both routes, and no `fromDynamicValue` test covers an absent `Option`, collection or defaulted field.

**Suggested fix.** In the missing-field loop of `Reflect.Record#fromDynamicValue`, apply the same order as the codec before reporting `missingField`: the field's default value, `None` for an `Option`/`Maybe` field, an empty collection for a sequence or map field. Or document the difference in `dynamic-value.md` and `json.md` if it is intended.

### schema: `PatchMode.Strict` does not detect that the target changed, so a diff's list removal deletes whatever sits at the index

Found 2026-10-21 while writing H-07 (`docs/guides/diff-and-patch.md`), on `main` at `7d8e0b44`. Verified on Scala 2.13.18 (`zio-blocks-schema` 0.0.56) and Scala 3.9.0 (`zio-blocks-next-schema` 0.0.56, `-experimental`); the patch code is the same at tag `v0.0.56`.

**Summary.** `Schema#diff` records list removals by index (`- [1]`), numbers as deltas (`-= 5.00`) and string changes as edits at a position. `PatchMode.Strict` only checks that each operation *fits* the value it is applied to (the index is in range, the map key exists or is absent). It never checks that the value still holds what it held when the patch was made. Applied to a value that changed in the meantime, a Strict patch returns `Right` and silently removes the wrong list element or produces a price nobody set. `PatchMode`'s Scaladoc says only "Fail on precondition violations", and `patch.md` says Strict "fails immediately if any operation cannot be applied" and to use it "when you need to know if every operation succeeded", which readers take to mean conflict detection.

**Reproduction** (run with scala-cli; the same output on both versions, the Scala 3 file uses `//> using scala 3.9.0`, `//> using options -experimental` and `zio-blocks-next-schema`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.patch._

final case class Book(id: String, title: String, price: BigDecimal, tags: List[String])
object Book { implicit val schema: Schema[Book] = Schema.derived }

object Main extends App {
  val base = Book("zionomicon", "Zionomicon", BigDecimal("44.99"), List("zio", "scala", "effects"))

  // The editor removes the "scala" tag (index 1) and lowers the price to 39.99.
  val patch = Book.schema.diff(base, base.copy(price = BigDecimal("39.99"), tags = List("zio", "effects")))
  println(patch)
  // DynamicPatch {
  //   .price -= 5.00
  //   .tags:
  //     - [1]
  // }

  // Meanwhile someone else removed "scala" and set the price to 42.00.
  val current = base.copy(price = BigDecimal("42.00"), tags = List("zio", "effects"))
  println(patch(current, PatchMode.Strict))
  // Right(Book(zionomicon,Zionomicon,37.00,List(zio)))
}
```

The `effects` tag is gone although nobody removed it, and the price is 37.00 although nobody set it.

**Expected.** Either Strict detects the conflict (a removal that carries the removed value, like RFC 6902's `test`, or a check of the old value before a delta), or the documentation says plainly that no mode detects a changed target.

**Root cause.** `SeqOp.Delete` carries only `index` and `count` (`schema/shared/src/main/scala/zio/blocks/schema/patch/DynamicPatch.scala:877`), and `applySeqOp` checks only the bounds (`DynamicPatch.scala:705-712`). Numeric deltas and string edits likewise check only types and bounds (`DynamicPatch.scala:619-657`). `Differ` emits these operations for every changed list, number and string (`schema/shared/src/main/scala/zio/blocks/schema/patch/Differ.scala`).

**Impact.** Silent data corruption in exactly the cases patches are used for: syncing an offline client, merging two editors' changes, replaying a stored patch.

**Test gap.** `PatchLawsSpec` checks `diff(a, b)(a) == b` only on the value the patch was computed from; no test applies a diff to a changed value.

**Suggested fix.** Document the limitation on `PatchMode` and in `patch.md`, and show the safe pattern (check that the value is unchanged, e.g. a version number, before applying; the guide does this). Optionally, add a value-checking operation (a `Test(path, expected)` op) and have `Differ` emit it before index-based removals.

### schema: a stored `DynamicPatch` cannot be turned back into a `Patch[A]` on Scala 3, but can on Scala 2.13

Found 2026-10-21 while writing H-07 (`docs/guides/diff-and-patch.md`), on `main` at `7d8e0b44`. Verified on Scala 2.13.18 (`zio-blocks-schema` 0.0.56) and Scala 3.9.0 (`zio-blocks-next-schema` 0.0.56, `-experimental`).

**Summary.** `Patch[A]` has no `Schema`, so the way to store a patch is its `DynamicPatch` (which has one). After decoding, there is no public way to wrap the `DynamicPatch` in a `Patch[A]` again: the constructor is `private[schema]`. On Scala 2.13 the case class's synthetic `Patch.apply` stays public, so `Patch(stored, schema)` compiles there and fails on Scala 3, which makes the synthetic `apply` as private as the constructor.

**Reproduction** (run with scala-cli):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.patch._

final case class Book(id: String, title: String, price: BigDecimal)
object Book { implicit val schema: Schema[Book] = Schema.derived }

object Main extends App {
  val book   = Book("zionomicon", "Zionomicon", BigDecimal("44.99"))
  val stored = Book.schema.diff(book, book.copy(price = BigDecimal("39.99"))).dynamicPatch
  val typed: Patch[Book] = Patch(stored, Book.schema)
  println(typed(book, PatchMode.Strict))
  // Right(Book(zionomicon,Zionomicon,39.99))
}
```

The same file with `//> using scala 3.9.0`, `//> using options -experimental` and `zio-blocks-next-schema` fails to compile:

```
-- [E050] Type Error: B.scala:13:27
13 |  val typed: Patch[Book] = Patch(stored, Book.schema)
   |                           ^^^^^
   |object Patch in package zio.blocks.schema.patch does not take parameters
```

**Expected.** A public, cross-version way to get a `Patch[A]` from a `DynamicPatch`, such as `Patch.fromDynamic(patch: DynamicPatch)(implicit schema: Schema[A])` or `Schema#patch(value, patch: DynamicPatch)`.

**Root cause.** `final case class Patch[S] private[schema] (dynamicPatch: DynamicPatch, schema: Schema[S])` (`schema/shared/src/main/scala/zio/blocks/schema/patch/Patch.scala:49`) and no factory in the companion takes a `DynamicPatch`.

**Impact.** Code that stores patches works on Scala 2.13 and breaks when the project moves to Scala 3. The portable workaround is `dynamicPatch(schema.toDynamicValue(a), mode).flatMap(schema.fromDynamicValue)`, which the guide wraps in an `applyStored` helper; `patch.md`'s serialization section shows neither.

**Test gap.** `SerializationSpec` round-trips `DynamicPatch` but never applies a decoded patch to a typed value.

**Suggested fix.** Add the factory, and compile with `-Xsource-features:case-apply-copy-access` on Scala 2.13 so the synthetic `apply` follows the constructor's access.

### schema: map patch errors don't name the key

Found 2026-10-21 while writing H-07 (`docs/guides/diff-and-patch.md`), on `main` at `7d8e0b44`. Verified on Scala 2.13.18 and 3.9.0 against 0.0.56.

**Summary.** When a map operation fails in `PatchMode.Strict`, the error names the map but not the key: `Key not found in map at: .books` or `Key already exists in map at: .books`. With a catalog of many books, the reader cannot tell which book caused the conflict.

**Reproduction.** Add these lines to the reproducer of the `toString` entry under Library bugs:

```scala
  val removePis = Catalog.schema.diff(after, before)
  println(removePis(before, PatchMode.Strict))
  // Left(zio.blocks.schema.SchemaError: Key not found in map at: .books)
```

**Expected.** `Key "programming-in-scala" not found in map at: .books`, or a path that ends in the key (`.books("programming-in-scala")`).

**Root cause.** The messages are fixed strings (`schema/shared/src/main/scala/zio/blocks/schema/patch/DynamicPatch.scala:329,767,774,779`), and the trace stops at the map.

**Suggested fix.** Include the rendered key in the message, or push a `DynamicOptic.Node.AtMapKey` step onto the trace.

### schema (Scala 3): `Binding.of` of a structural record type fails with `zio-blocks-schema` on every Scala 3 version, and the error says to upgrade to Scala 3.5 (0.0.56; fixed on `main` by #1743)

Found 2026-10-21 while writing the Scaladoc of the `binding` package (wave 3 of the schema Scaladoc, branch `scaladoc-wave-3`, on top of `5fd81ef4`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: both reproductions below were run with `scala-cli run Repro.scala` against the published `0.0.56`.

**Status on `main` (checked 2026-10-22 at `bffadae7` plus the wave 3 commits).** #1743 (`3defefab`) fixes this for the next release. `zio-blocks-schema` is now built with Scala 3.9.0 (`project/BuildHelper.scala:11`; the `zio-blocks-next-*` rename now needs `minor >= 10`, `:176-181`), so it compiles the `scala-3.5+` version of the macro support (`findApplicableScala3MinorDirs`, `:97`). Verified with a local publish of `main` (`set ThisBuild / version := "0.0.0-wave3probe"; ++3.9.0; chunkJVM/publishLocal; maybeJVM/publishLocal; markdownJVM/publishLocal; typeidJVM/publishLocal; schemaJVM/publishLocal`): the reproduction below, with `//> using repository ivy2Local`, `//> using dep dev.zio::zio-blocks-schema:0.0.0-wave3probe` and `//> using option -experimental`, compiles and prints `Record`. The `-experimental` in that probe is needed only because of a separate regression from #1743, recorded in its own entry titled "build (Scala 3): every main-line artifact was built with -experimental after #1743, so every Scala 3 user needed -experimental" and fixed in #1826; once that build fix lands, the reproduction compiles without it. No build compiles the `scala-3.5-` sources, with their misleading message, any more.

**Summary (0.0.56).** The Scaladoc of `Binding.of` listed "Structural types (JVM only)". With the `zio-blocks-schema` artifact, `Binding.of` of a structural record type never compiles on Scala 3, whatever the compiler version: that artifact is built with Scala 3.3.7, so it contains the `scala-3.5-` version of the macro support, which always fails. Its error says "requires Scala 3.5+" and suggests upgrading to Scala 3.5, which does not help. Structural record types work only with the `zio-blocks-next-schema` artifact, which needs Scala 3.9 and `-experimental`; a union of structural types does not use this macro and compiles with `zio-blocks-schema` too.

**Reproduction** (Scala 3.9.0, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema.binding._

object Main {
  def main(args: Array[String]): Unit = {
    val binding = Binding.of[{ def title: String; def year: Int }]
    println(binding)
  }
}
// [error] ./Repro.scala:7:19
// [error] Binding.of for structural types requires Scala 3.5+.
// [error]
// [error] The macro API `Symbol.newClass` used to generate structural type
// [error] constructors is marked @experimental and unavailable in Scala 3.3 LTS.
// [error]
// [error] Alternatives:
// [error]   1. Use a case class instead:
// [error]        case class Person(name: String, age: Int)
// [error]        Binding.of[Person]  // works on all Scala 3 versions
// [error]
// [error]   2. Upgrade to Scala 3.5+ to use structural types:
// [error]        Binding.of[{ def name: String; def age: Int }]
// [error]
// [error]   3. Use Schema[A].structural for structural schema without Binding.of:
// [error]        given schema: Schema[Person] = Schema.derived
// [error]        val structuralSchema = schema.structural  // works on all Scala 3 versions
// [error]     val binding = Binding.of[{ def title: String; def year: Int }]
// [error]                   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// Error compiling project (Scala 3.9.0, JVM (21))
// Compilation failed
```

With `//> using dep dev.zio::zio-blocks-next-schema:0.0.56` and `//> using option -experimental`, the same file compiles and prints `Record(Main$$anon$1@2c8d66b2,Main$$anon$2@5a39699c)`.

**Expected.** Either the error names what works (the `zio-blocks-next-schema` artifact with Scala 3.9 and `-experimental`), or the `zio-blocks-schema` artifact supports structural record types.

**Root cause (0.0.56; historical since #1743).** At `v0.0.56`, `project/BuildHelper.scala` (`stdSettings`, the `moduleName` rule `minor >= 7`) published the Scala 3.3 build as `zio-blocks-schema` and the Scala 3.7+ build (in practice the 3.9.0 build, so consumers needed Scala 3.9+) as `zio-blocks-next-schema` (the `zio-blocks-schema_3` 0.0.56 POM depends on `scala3-library_3` 3.3.7). The 3.3 build compiled `schema/shared/src/main/scala-3.5-/zio/blocks/schema/binding/StructuralBindingMacros.scala`, whose `generateAnonymousClassFactory` (`:47` at HEAD) always fails with the message at `:52`-`:67`. It is called only for structural record types (`schema/shared/src/main/scala-3/zio/blocks/schema/binding/BindingCompanionVersionSpecific.scala:1377` at HEAD, through the helper at `:1444-1447`); unions of structural types use `deriveStructuralVariantBindingSimple` (`:1529`), which does not call it.

**Impact.** A Scala 3 user who reads the Scaladoc or the error and upgrades the compiler still gets the same error. The Scaladoc of `Binding.of` and of the `scala-3.5-` `StructuralBindingMacros` now describe the `main` build and mention the 0.0.56 behaviour.

**Test gap.** The structural `Binding.of` tests are in `schema/jvm/src/test/scala-3.5+/`, so at 0.0.56 they ran only in the 3.9 build, and nothing checked what the published 3.3 build did with a newer compiler. On `main` every Scala 3 build uses `scala-3.5+`.

**Suggested fix.** Fixed for the next release by #1743. What remains: the `scala-3.5-` `StructuralBindingMacros` (and its "Upgrade to Scala 3.5+" error) is now unreachable, so delete it, or, if a pre-3.5 build is ever brought back, reword the error to name an artifact that works.

### schema: `toJsonString`, `toJsonBytes` and `fromJson` on a `List`, `Option` or `Map` derive a new JSON codec on every call

Found 2026-10-21 while writing the how-to guide `docs/guides/tune-json-throughput.md` (backlog H-50), on `main` at `7e8ac787`. `JsonCodec`, `JsonReader`, `JsonWriter`, `ReaderConfig`, `WriterConfig` and `Schema` are the same at tag `v0.0.56`. Verified by running against the published 0.0.56. Not filed on GitHub yet.

**Summary.** `Schema#jsonCodec` is a `lazy val`, so a codec is derived once per `Schema` instance. That works for a case class whose companion has `implicit val schema = Schema.derived`. But the schemas for collections and options are `implicit def`s that build a new `Schema` each time the compiler summons one, so the cache is always empty. Every `books.toJsonString`, `books.toJsonBytes` or `json.fromJson[List[Book]]` therefore derives a new `List[Book]` codec, including the codec for `Book` inside it, and throws it away. For small payloads the derivation costs far more than the encoding.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56, `scala-cli run`; timings from one run on scala-cli's default JVM, and they vary):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56

import zio.blocks.schema._

case class Book(title: String, year: Int)
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

object RederiveRepro {
  def main(args: Array[String]): Unit = {
    println(Schema[Book].jsonCodec eq Schema[Book].jsonCodec)
    println(Schema[List[Book]] eq Schema[List[Book]])
    println(Schema[List[Book]].jsonCodec eq Schema[List[Book]].jsonCodec)
    println(Schema[Option[Book]].jsonCodec eq Schema[Option[Book]].jsonCodec)
    println(Schema[Map[String, Book]].jsonCodec eq Schema[Map[String, Book]].jsonCodec)

    // So every call of these extension methods derives a new List[Book] codec
    val books = List(Book("Zionomicon", 2024), Book("Programming in Scala", 2008), Book("Scala with Cats", 2017))
    val json  = books.toJsonString
    def time(label: String)(f: => Any): Unit = {
      (1 to 2000).foreach(_ => f) // warm-up
      val start = System.nanoTime()
      (1 to 2000).foreach(_ => f)
      println(f"$label: ${(System.nanoTime() - start) / 2000 / 1000.0}%.0f us per call")
    }
    val held = Schema[List[Book]].jsonCodec
    time("books.toJsonString       ")(books.toJsonString)
    time("held codec encodeToString")(held.encodeToString(books))
    time("json.fromJson[List[Book]]")(json.fromJson[List[Book]])
    time("held codec decode        ")(held.decode(json))
  }
}
// true
// false
// false
// false
// false
// books.toJsonString       : 34 us per call
// held codec encodeToString: 1 us per call
// json.fromJson[List[Book]]: 20 us per call
// held codec decode        : 1 us per call
```

**Expected.** Either the extension methods reuse one codec per element type, or the docs say plainly that `toJsonString` and `fromJson` on a collection type re-derive the codec on each call and should not be used on a hot path.

**Root cause.** `Schema.list`, `Schema.option` and the other collection schemas are `implicit def`s returning `new Schema(...)` (`schema/shared/src/main/scala/zio/blocks/schema/Schema.scala:261`, `:314`, `:321` and the following definitions). The codec cache is a per-instance `ConcurrentHashMap` behind `lazy val jsonCodec` (`Schema.scala:36-43`). The extension methods call `schema.jsonCodec` on whatever instance the compiler supplies (`schema/shared/src/main/scala-2/zio/blocks/schema/syntax.scala:32-44`, and the same in `scala-3/`). `Schema#derive` (`Schema.scala:54`) never caches, by design.

**Impact.** The shortest JSON code in the docs (`toJsonString`, `fromJson`) is the slowest on collection types, and nothing warns about it. A service that answers with `books.toJsonString` pays for a derivation on every request. The H-50 guide shows the check and tells readers to keep codecs in `val`s.

**Test gap.** No test checks that summoning the same collection schema twice reuses its codec.

**Suggested fix.** Cache derived codecs by the `Reflect` structure rather than by `Schema` instance (for example, memoize `getInstance` per element schema for the built-in collection schemas), or make the common collection schemas stable per element schema. If neither is wanted, document the cost on `syntax.md` and on the JSON codec reference page, and recommend `val listCodec = Schema[List[A]].jsonCodec`.

### schema: decoding from an `InputStream` discards the bytes it read past the first value, so one stream can't hold several values

Found 2026-10-21 while writing the how-to guide `docs/guides/tune-json-throughput.md` (backlog H-50), on `main` at `7e8ac787`. `JsonCodec`, `JsonReader`, `JsonWriter`, `ReaderConfig`, `WriterConfig` and `Schema` are the same at tag `v0.0.56`. Verified by running against the published 0.0.56. Not filed on GitHub yet.

**Summary.** `JsonCodec#decode(input: InputStream)` fills its internal buffer with as much as the stream offers, decodes one value, and returns. Bytes that it read past the end of that value are dropped. With `ReaderConfig#withCheckForEndOfInput(false)`, decoding the same `ByteBuffer` twice returns two values, because the reader moves `position` back to the end of the first value. Decoding the same `InputStream` twice returns the first value and then fails, because the stream is already exhausted. Newline-delimited JSON or a sequence of concatenated documents can't be read from a stream one value at a time.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56, `scala-cli run`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import zio.blocks.schema._
import zio.blocks.schema.json._

case class Book(title: String, year: Int)
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

object StreamReadAheadRepro {
  def main(args: Array[String]): Unit = {
    val codec  = Book.schema.jsonCodec
    val bytes  = codec.encode(Book("Zionomicon", 2024)) ++ codec.encode(Book("Programming in Scala", 2008))
    val config = ReaderConfig.withCheckForEndOfInput(false)

    val buffer = ByteBuffer.wrap(bytes)
    println(s"ByteBuffer: ${codec.decode(buffer, config)}, ${codec.decode(buffer, config)}")

    val stream = new ByteArrayInputStream(bytes)
    println(s"InputStream: ${codec.decode(stream, config)}, ${codec.decode(stream, config)}")
    println(s"bytes left in the stream: ${stream.available()} of ${bytes.length}")
  }
}
// ByteBuffer: Right(Book(Zionomicon,2024)), Right(Book(Programming in Scala,2008))
// InputStream: Right(Book(Zionomicon,2024)), Left(zio.blocks.schema.SchemaError: unexpected end of input at: .)
// bytes left in the stream: 0 of 78
```

**Expected.** Either a way to read several values from one stream (jsoniter-scala, which this reader resembles, has `scanJsonValuesFromStream` for this), or a note in the Scaladoc of `JsonCodec#decode(input: InputStream)` that the stream must hold exactly one value and is read past it.

**Root cause.** `loadMore` reads up to `buf.length - tail` bytes per call (`schema/jvm/src/main/scala/zio/blocks/schema/json/JsonReader.scala:5049-5053`), so the reader routinely holds bytes past the current value. The `InputStream` overload of `read` only clears `this.in` when it returns (`JsonReader.scala:1752`); it has no way to push the extra bytes back. The `ByteBuffer` overloads restore the position instead (`JsonReader.scala:1798`, `:1817`).

**Impact.** Users porting jsoniter-scala code that scans a stream of values, or reading NDJSON logs, must read the whole stream into a `ByteBuffer` or split lines themselves. The H-50 guide documents the limitation.

**Test gap.** The stream round-trip helpers in `JsonTestUtils` (`schema/shared/src/test/scala/zio/blocks/schema/json/JsonTestUtils.scala:73`, `:97`, `:134`) always decode exactly one value per stream.

**Suggested fix.** Add a `JsonCodec#decodeAll(input: InputStream, config)` (or a callback-based scan) that keeps one reader over the stream and decodes values until end of input, and document the one-value rule on the existing overload.

### telemetry: after `trace.install` or `trace.removeAll()`, `trace.collectedSpans` stays empty for the rest of the JVM

Found 2026-10-21 while writing the how-to guide `docs/guides/test-telemetry.md` (backlog H-48), on `main` at `7e8ac787`. The telemetry sources are the same at tag `v0.0.56`. Verified by running against the published 0.0.56 on JDK 21. Not filed on GitHub yet.

**Summary.** `trace.collectedSpans` and `trace.clearSpans()` read the in-memory buffer of the default tracer provider. `trace.install(provider)` and `trace.removeAll()` replace that provider, and no public call ever puts it back, so from then on `trace.collectedSpans` returns an empty list in every part of the program. In a test suite, sbt runs every spec in one JVM, so a single test that installs its own provider, or "cleans up" with `trace.removeAll()` (whose Scaladoc says spans are no-ops "until you install a provider"), makes every later test that reads `trace.collectedSpans` fail, and which tests fail depends on the order the specs run in.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-telemetry` 0.0.56, JDK 21, run with `scala-cli run --server=false --jvm temurin:21 NoWayBack.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-telemetry:0.0.56
package bookstore

import zio.blocks.telemetry._

object NoWayBack {
  def main(args: Array[String]): Unit = {
    trace.span("before-install")(_ => ())
    println(s"1: ${trace.collectedSpans.map(_.name)}")

    // A test that collects spans through its own provider
    trace.install(TracerProvider.builder.build())
    trace.span("during-test")(_ => ())

    // Its teardown: the closest public calls to "put the default back"
    trace.removeAll()
    trace.clearSpans()
    trace.span("later-test")(_ => ())
    println(s"2: ${trace.collectedSpans.map(_.name)}")
  }
}
// 1: List(before-install)
// 2: List()
```

**Expected.** Some public way to return `trace` to its default, in-memory state, such as a `trace.reset()`, so that a test can undo an `install`. Failing that, the Scaladoc of `trace.removeAll()`, `trace.install` and `trace.collectedSpans` should say that the in-memory buffer is disconnected for good.

**Root cause.** The default provider and its buffer are private vals (`telemetry/shared/src/main/scala/zio/blocks/telemetry/trace.scala:28-35`). `install` (`:53`) and `removeAll` (`:59`) replace the provider in `ref`, while `collectedSpans` (`:62`) and `clearSpans` (`:65`) keep reading `spanStore`, which no other provider feeds. `InMemorySpanProcessor` is `private[telemetry]`, so users can't add it to a provider of their own either.

**Impact.** Order-dependent test failures in any suite that mixes tests using `trace.collectedSpans` with a test that installs a provider. `docs/reference/telemetry/tracing/index.md:120` warns that the methods "stop seeing new spans" after `install`, but not that there is no way back. The same holds for logging: after `log.install` no public call restores the default console output. The H-48 guide tells readers to test through injected tracers, and to never call `trace.install` or `trace.removeAll()` in tests that share the globals.

**Test gap.** The library's own tests call `trace.removeAll()` in `finally` blocks (`telemetry/jvm/src/test/scala/zio/blocks/telemetry/AdditionalCoverageSpec.scala:203-240`); no test reads `trace.collectedSpans` after that.

**Suggested fix.** Add `trace.reset()` (and `log.reset()`, `metric.reset()`) that reinstall the default provider, or make `collectedSpans` read from whatever `InMemorySpanProcessor` the current provider has. Making `InMemorySpanProcessor` public would also let tests build an isolated in-memory provider without writing their own processor.

### async: driving one `Async` from two consumers (`toFuture` and `block`) can hang forever with no error

Found 2026-10-21 by the M-04 session (`docs/guides/streaming-library-migration.md`, PR #1801): Build Docs hit its 30-minute timeout because mdoc hung. Reproduced again on main `e79a7617` against the published 0.0.56 (scala-cli, Scala 3.9.0, JDK 21).

**Summary.** An `Async` value is a description that its consumer drives. If two consumers drive the same value, for example `toFuture` and then `block`, one of them can wait forever. Nothing is thrown and nothing is logged; the program just stops. It happens only some of the time, so a test can pass locally and hang in CI. `docs/reference/async.md` ("Concurrent Fan-Out via Running") calls this undefined behaviour, but neither `toFuture` nor `block` warns about it, and a `val` holding an `Async` looks reusable like any other value.

**Reproduction** (`scala-cli run AsyncTwice.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-streams:0.0.56

import zio.blocks.streams.*
import zio.blocks.async.*
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import java.util.concurrent.*

object AsyncTwice {
  def main(args: Array[String]): Unit = {
    val books = Stream("Zionomicon", "Programming in Scala", "Functional Programming in Scala")
    var hangs = 0
    val runs = 25
    (1 to runs).foreach { _ =>
      val total: Async[Either[Nothing, Long]] = books.runAsync(Sink.count)
      val fut = total.toFuture
      val ex  = Executors.newSingleThreadExecutor()
      val blocked = ex.submit(new Callable[Any] { def call(): Any = total.block })
      val ok =
        try { blocked.get(2, TimeUnit.SECONDS); Await.result(fut, 2.seconds); true }
        catch { case _: TimeoutException => false }
      if (!ok) hangs += 1
      ex.shutdownNow()
    }
    println(s"same Async consumed twice: $hangs of $runs runs hung")
    var hangs2 = 0
    (1 to runs).foreach { _ =>
      def total: Async[Either[Nothing, Long]] = books.runAsync(Sink.count)
      val fut = total.toFuture
      val r1 = total.block
      val ok = try { Await.result(fut, 2.seconds); true } catch { case _: TimeoutException => false }
      if (!ok) hangs2 += 1
    }
    println(s"new Async per consumer: $hangs2 of $runs runs hung")
    // same Async consumed twice: 2 of 25 runs hung
    // new Async per consumer: 0 of 25 runs hung
  }
}
```

The M-04 session saw the same thing at 2 CPUs: `.block` hung in 6 of 15 mdoc runs, and a standalone `toFuture` + `block` repro hung 25 of 25 times, while each call alone passed 25 of 25.

**Expected.** Either both consumers see the result, or the second consumer fails fast with a clear error ("this Async is already being driven; use `start` to share it").

**Root cause.** `toFuture` starts a non-blocking driver on the value (`async/jvm/src/main/scala/zio/blocks/async/AsyncInterop.scala:88-94`), and `block` starts a second one that polls the same `Pollable` with its own parker (`async/shared/src/main/scala/zio/blocks/async/Async.scala:2347` and `awaitSuspended` below it). The two drivers race on one pollable; when the completion wakes only one of them, the other never resumes. (Inferred from the code and the documented contract, not traced line by line.)

**Impact.** A silent, intermittent hang. In the docs it cost a 30-minute CI timeout. In an application it is a stuck request or a frozen `main`.

**Test gap.** No test drives one `Async` from two consumers.

**Suggested fix.** Cheapest: Scaladoc on `toFuture`, `toCompletableFuture`, `toJsPromise` and `block` that says "consume once; use `start` to share". Better: mark a suspended `Async` as claimed when a driver starts and make a second driver fail with an exception that names `start`.

**Workaround.** Use a `def` so each consumer builds its own `Async`, or call `start` once and share the `Async.Running` it returns.

### config-hocon: a misspelled include loads without an error, and no include form can make it required

Found 2026-10-21 while writing the how-to guide `docs/guides/load-configuration-files.md` (backlog H-34), on `main` at `7ebf5bca`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `ConfigSource.fromFile` skips an `include` whose file doesn't exist, as Typesafe Config does for a plain `include`. Typesafe Config users make an include mandatory with `include required("db.conf")`, but `config-hocon` parses only the plain `include "file"` form: `required(...)`, `file(...)`, `classpath(...)` and `url(...)` are all parse errors. So there is no way to make a misspelled or missing include fail the load.

**Reproduction** (save as `Repro.sc` and run `scala-cli run Repro.sc`; the script has top-level statements):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-config-hocon:0.0.56
import java.nio.file.Files
import zio.blocks.config._
import zio.blocks.config.hocon._

val dir = Files.createTempDirectory("bookstore")
Files.write(dir.resolve("db.conf"), "url = \"jdbc:postgresql://localhost:5432/bookstore\"".getBytes("UTF-8"))

def load(doc: String): Unit = {
  val file = Files.write(dir.resolve("application.conf"), doc.getBytes("UTF-8"))
  ConfigSource.fromFile(file.toString) match {
    case Right(source)                                     => println(source.all("").keys.toList.sorted)
    case Left(ConfigError.ParseError(_, _, _, Some(cause))) => println(cause.getMessage)
    case Left(error)                                       => println(error.getMessage)
  }
}

load("db { include \"db.conf\" }")
load("db { include \"dbb.conf\" }")
load("db { include required(\"db.conf\") }")
load("db { include file(\"db.conf\") }")
// List(db.url)
// List()
// Expected quoted string after 'include' (line 1, column 14)
// Expected quoted string after 'include' (line 1, column 14)
```

**Why it's a trap.** The typo `dbb.conf` loads as an empty `db` block. The error appears only when a required key is decoded, as `Missing required key 'db.url'`, which names the key, not the include; a key with a default takes the default silently.

**Where it comes from.** `handleInclude` accepts only a quoted string after `include` (`config-hocon/shared/src/main/scala/zio/blocks/config/hocon/HoconParser.scala:442`) and ignores an unresolved include on purpose (`HoconParser.scala:452`).

**Suggested fix.** Parse `include required("...")` and fail the load when its file is missing; at least parse `file("...")` as a plain include. Document in `formats.md` that a missing plain include is skipped. The guide `load-configuration-files.md` warns about both.

### config-yaml: a YAML document that is not a mapping loads as an empty source

Found 2026-10-21 while writing the how-to guide `docs/guides/load-configuration-files.md` (backlog H-34), on `main` at `7ebf5bca`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `ConfigSource.fromYaml` keeps a scalar only when it sits under a key. A document whose top level is a single scalar, such as a file whose only line is `featuredBook:Zionomicon` (no space after the colon, so it is one plain scalar) or a stray sentence, returns `Right` with no keys. A top-level sequence loads under the keys `0`, `1`, ...

**Reproduction** (save as `Repro.sc` and run `scala-cli run Repro.sc`; the script has top-level statements):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-config-yaml:0.0.56
import zio.blocks.config._
import zio.blocks.config.yaml._

println(ConfigSource.fromYaml("featuredBook:Zionomicon", "config.yaml").map(_.all("")))
println(ConfigSource.fromYaml("Zionomicon is the featured book", "config.yaml").map(_.all("")))
println(ConfigSource.fromYaml("- Zionomicon\n- Programming in Scala", "config.yaml").map(_.all("").map { case (k, v) => k -> v.value }))
// Right(Map())
// Right(Map())
// Right(Map(0 -> Zionomicon, 1 -> Programming in Scala))
```

**Why it's a trap.** The file "loads", and every setting then fails as `Missing required key`, or takes its default, with nothing pointing at the file's shape.

**Where it comes from.** `flattenRec` records a `Yaml.Scalar` only when the prefix is non-empty (`config-yaml/shared/src/main/scala/zio/blocks/config/yaml/YamlConfigSource.scala:84-87`), and the top-level call starts with an empty prefix.

**Suggested fix.** Return `Left(ConfigError.ParseError("", sourceId, "a YAML mapping"))` when the document root is not a mapping (or is a non-null scalar).

### schema-yaml: a derived sealed trait or enum is written as `Ebook: {}`, and a plain `Ebook` fails to decode

Found 2026-10-21 while writing the how-to guide `docs/guides/read-and-write-yaml.md` (backlog H-15), on `main` at `4ec6dd90`. `schema-yaml` has no changes since tag `v0.0.56`, so the behaviour is the same in the 0.0.56 release. Verified by running.

**Summary.** The YAML codec encodes every variant as a mapping with one key, the case name, so an enumeration value such as `Format.Ebook` is written as `- Ebook: {}`. YAML files list such values as plain words (`formats: [Ebook, Paperback]`), and decoding those fails with `Expected mapping with single key for variant`. The JSON codec has the same shape by default (`{"Ebook":{}}`) but offers configuration; the YAML deriver has none. The workaround, used in the H-15 guide, is a `Schema[String].transform` schema for the enum, which then adds a `.wrapped` step to error paths (see the `.wrapped` entry).

**Reproduction** (scala-cli, run exactly as written; the output is pasted at the end):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema-yaml:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.yaml._

enum Format { case Paperback, Ebook }
object Format { implicit val schema: Schema[Format] = Schema.derived }
case class Book(title: String, formats: List[Format])
object Book { implicit val schema: Schema[Book] = Schema.derived }

@main def enumCases(): Unit = {
  val codec = Schema[Book].derive(YamlFormat)
  println(codec.encodeToString(Book("Zionomicon", List(Format.Ebook, Format.Paperback))))
  println(codec.decode("title: Zionomicon\nformats: [Ebook, Paperback]\n"))
  println(codec.decode("title: Zionomicon\nformats:\n  - Ebook: {}\n  - Paperback: {}\n"))
}
// Output:
// title: Zionomicon
// formats:
//   - Ebook: {}
//   - Paperback: {}
// Left(zio.blocks.schema.SchemaError: Expected mapping with single key for variant at: .formats.at(0))
// Right(Book(Zionomicon,List(Ebook, Paperback)))
```

**Expected.** At least an option to encode case objects (enum cases without fields) as their name, as YAML users expect; ideally that as the default for case-object-only enums.

**Root cause.** `schema-yaml/shared/src/main/scala/zio/blocks/schema/yaml/YamlCodecDeriver.scala:304-331`: `decodeValue` accepts only a one-entry mapping (`:308`, error at `:322`), and `encodeValue` always writes `Yaml.Mapping.fromStringKeys((caseName, inner))` (`:328`).

**Impact.** Every model with an enum field needs a hand-written `transform` schema before it can read ordinary YAML, and the written files look odd to anyone else (`Ebook: {}`).

**Test gap.** Not a gap; the shape is tested and intended. The trap is that nothing documents it and there is no option.

**Suggested fix.** Encode a case with no fields as a plain scalar of its name (and accept both forms on decode), or add a `caseObjectsAsStrings` style option to `YamlCodecDeriver`. Document the shape on the YAML reference page.

### schema-yaml: a `String` field decodes an empty value, `null` or `~` as `""`, and a `Boolean` field rejects `True`

Found 2026-10-21 while writing the how-to guide `docs/guides/read-and-write-yaml.md` (backlog H-15), on `main` at `4ec6dd90`. `schema-yaml` has no changes since tag `v0.0.56`, so the behaviour is the same in the 0.0.56 release. Verified by running.

**Summary.** A required `String` field whose key is present with an empty value, `null` or `~` decodes as the empty string instead of failing, so a missing title in a file is not reported. A `Boolean` field accepts only lowercase `true` and `false`; `True`, which the YAML 1.2 core schema allows and which `Yaml#toJson` converts to `true`, is an error. (`yes`, `no`, `on`, `off` being rejected is fine: it avoids the YAML 1.1 "Norway problem".)

**Reproduction** (scala-cli, run exactly as written; the output is pasted at the end):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema-yaml:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Book(title: String, inStock: Boolean)
object Book { implicit val schema: Schema[Book] = Schema.derived }

@main def scalarDecoding(): Unit = {
  val codec = Schema[Book].derive(YamlFormat)
  println(codec.decode("title:\nin-stock: true\n"))
  println(codec.decode("title: null\nin-stock: true\n"))
  println(codec.decode("title: ~\nin-stock: true\n"))
  println(codec.decode("title: Zionomicon\nin-stock: True\n"))
  println(YamlReader.read("in-stock: True").toJson.print)
}
// Output:
// Right(Book(,true))
// Right(Book(,true))
// Right(Book(,true))
// Left(zio.blocks.schema.SchemaError: Invalid boolean value: True at: .inStock)
// {"in-stock":true}
```

**Expected.** `Left(... title ...)` for the first three (a `String` field can't be null; `Option[String]` is how YAML files mark an absent value), and `Right(Book(Zionomicon,true))` for `True`, consistent with `toJson`.

**Root cause.** `schema-yaml/shared/src/main/scala/zio/blocks/schema/yaml/YamlCodec.scala:251-256` (`stringCodec` maps `Yaml.NullValue` to `""`, `:253`); `YamlReader.parseInlineValue` turns an empty value, `null` and `~` into `NullValue` (`YamlReader.scala:447`). `booleanCodec` (`YamlCodec.scala:162-172`) matches only `"true"` and `"false"`, while `YamlJsonInterop.yamlToJson` accepts `True`/`TRUE` and `False`/`FALSE` (`YamlJsonInterop.scala:33-34`).

**Impact.** A typo or an unfinished line in a file (`title:`) loads as a record with an empty string, which passes the codec and fails later, if at all. The `Boolean` mismatch makes files from tools that write `True` (Python's `str(True)`) fail.

**Test gap.** No test decodes `NullValue` into a required `String` field or `True` into a `Boolean`.

**Suggested fix.** Make `stringCodec` fail on `NullValue` (an `Option[String]` field already maps it to `None`), and accept the YAML 1.2 core spellings `True`/`TRUE`/`False`/`FALSE` in `booleanCodec`.

### sql (PostgreSQL): `Repo#insert` into a `jsonb` column fails unless the URL has `stringtype=unspecified`, and `PgCodec` string arrays bind as `varchar[]` and read `NULL` as `null`

Found 2026-10-21 while writing the how-to guide `docs/guides/map-custom-column-types.md` (backlog H-22), on `main` at `7d8e0b44`. The code is the same at tag `v0.0.56`. Verified. Not filed.

**Summary.** The JSON codecs (`DbCodec.jsonb` and the deriver's fallback for nested records and collections) bind the JSON with `setString`. The PostgreSQL JDBC driver sends that as `character varying`, which PostgreSQL won't assign to a `jsonb` column, so `Repo#insert` fails with `column "metadata" is of type jsonb but expression is of type character varying`. It works once the JDBC URL has `stringtype=unspecified`. `PgCodec.listStringCodec` and the `PgCodec` string-list parameters bind a `varchar[]`: assignment to a `text[]` column works, but an operator such as `&&` against a `text[]` column needs a `::text[]` cast. `PgCodec.listStringCodec` reads a SQL `NULL` array as a `null` `List`.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-sql` 0.0.56, `postgresql` 42.7.14, PostgreSQL 16 in Docker, run with `scala-cli run PgJsonbArrays.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.postgresql:postgresql:42.7.14

// docker run --rm -d -p 5432:5432 -e POSTGRES_USER=bookstore -e POSTGRES_PASSWORD=bookstore -e POSTGRES_DB=bookstore postgres:16

import scala.util.Try
import zio.blocks.schema.Schema
import zio.blocks.typeid.TypeId
import zio.blocks.sql.*

case class Metadata(publisher: String, edition: Int, pages: Int)
object Metadata { given Schema[Metadata] = Schema.derived }

case class Book(id: Long, metadata: Metadata, tags: List[String])
object Book { given Schema[Book] = Schema.derived }

@main def run(): Unit = {
  val url   = sys.env.getOrElse("DB_URL", "jdbc:postgresql://localhost:5432/bookstore")
  val codec = DbCodec.derivedWith[Book](_.instance(TypeId.of[Book], "tags", PgCodec.listStringCodec))
  // Columns from the codec, to avoid the Table.derived column mismatch
  val books = Repo(Table("book", codec, codec.columns.map(ColumnMeta(_, DbValue.DbString(""), false))), "id", DbCodec[Long], (b: Book) => b.id)
  val zionomicon = Book(1L, Metadata("Ziverge", 1, 700), List("zio", "effects"))

  JdbcTransactor.fromUrl(url, "bookstore", "bookstore", SqlDialect.PostgreSQL).connect {
    sql"DROP TABLE IF EXISTS book".update
    sql"CREATE TABLE book (id BIGINT PRIMARY KEY, metadata JSONB NOT NULL, tags TEXT[])".update
    println(Try(books.insert(zionomicon)).fold(_.getMessage.linesIterator.next(), _.toString))
    // ERROR: column "metadata" is of type jsonb but expression is of type character varying
  }

  JdbcTransactor.fromUrl(url + "?stringtype=unspecified", "bookstore", "bookstore", SqlDialect.PostgreSQL).connect {
    println(books.insert(zionomicon))
    // 1
    import PgCodec.given
    val wanted = List("effects")
    println(Try(sql"SELECT id FROM book WHERE tags && $wanted".query[Long]).fold(_.getMessage.linesIterator.next(), _.toString))
    // ERROR: operator does not exist: text[] && character varying[]
    println(sql"SELECT id FROM book WHERE tags && $wanted::text[]".query[Long])
    // List(1)
    sql"""INSERT INTO book VALUES (2, '{"publisher":"Artima","edition":1,"pages":776}', NULL)""".update
    println(books.find(2L))
    // Book(2,Metadata(Artima,1,776),null)
    sql"DROP TABLE book".update
  }
}
```

**Expected.** Either the JSON codecs bind a value PostgreSQL accepts for `jsonb` (a `PGobject` of type `jsonb`, or `setObject(..., Types.OTHER)`), or the docs say to add `stringtype=unspecified`. The `NULL` array either fails clearly or the docs say to use `NOT NULL`.

**Root cause.** `DbCodec.jsonb` is `DbCodec[String].transform(...)` (`sql/shared/src/main/scala/zio/blocks/sql/DbCodec.scala:164-165`), and `JdbcParamWriter#setString` calls `PreparedStatement#setString` (`sql/jvm/src/main/scala/zio/blocks/sql/JdbcParamWriter.scala:33`). `PgCodec` writes string arrays as `"varchar"` (`sql/jvm/src/main/scala/zio/blocks/sql/PgCodec.scala:69,72`) and returns `null` for a `NULL` array (`PgCodec.scala:53,60,65`).

**Impact.** Every PostgreSQL user who stores JSON in a `jsonb` column hits the insert error first. No page mentions `stringtype=unspecified`; the H-22 guide does.

**Suggested fix.** Add a PostgreSQL `jsonb` write path (a `DbValue` variant or a dialect hook that binds `Types.OTHER`), bind string arrays as `text`, and give `PgCodec` an `Option[List[String]]` codec. At minimum, document `stringtype=unspecified` in `db-codec.md`.

### sql: a newtype with a private constructor can't be an `sql"..."` parameter, although its `Schema` is in scope

Found 2026-10-21 while writing the how-to guide `docs/guides/map-custom-column-types.md` (backlog H-22), on `main` at `7d8e0b44`. The code is the same at tag `v0.0.56`. Verified. Not filed.

**Summary.** `sql"... = $isbn"` with `isbn: Isbn`, where `Isbn` is the validated newtype from the H-04 guide (private constructor, `given Schema[Isbn] = Schema[String].transform(...)`), fails to compile with `No DbParam/Frag/SqlLiteral/DbValue instance found for type Isbn`. The interpolator looks for `DbParam.fromDbCodec`, which needs a `DbCodec[Isbn]`, and `DbCodec.derived` derives a new `Schema` for `Isbn` instead of using the given one. That new `Schema` needs the private constructor, so no `DbCodec[Isbn]` exists. The same `Isbn` works as a `Repo` field, because `Repo.derived` uses the given `Schema`.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-sql` 0.0.56, run with `scala-cli compile PrivateNewtypeParam.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56

import zio.blocks.schema.Schema
import zio.blocks.sql.*

final case class Isbn private (value: String)
object Isbn {
  def unsafe(s: String): Isbn = new Isbn(s)
  given Schema[Isbn] = Schema[String].transform(unsafe, _.value)
}

@main def run(): Unit = {
  val isbn = Isbn.unsafe("9780981531601")
  println(sql"SELECT title FROM book WHERE isbn = $isbn".sql(SqlDialect.SQLite))
  // [error] No DbParam/Frag/SqlLiteral/DbValue instance found for type Isbn. Supported: DbParam[T], Frag (verbatim), SqlLiteral (verbatim), DbValue. Use sql"..." or StringContext(...).sql(table)(...) accordingly.
  summon[DbCodec[Isbn]]
  // [error] constructor Isbn cannot be accessed as a member of Isbn from anonymous class zio.blocks.schema.binding.Constructor[Isbn] {...}.
}
```

**Expected.** The interpolator binds `isbn` as its underlying `String`, using the `Schema[Isbn]` in scope.

**Root cause.** `DbCodec.derived` is `Schema.derived[A].deriving(DbCodecDeriver).derive` (`sql/shared/src/main/scala/zio/blocks/sql/DbCodec.scala:190-191`), which ignores a `Schema[A]` in scope. `DbParam.fromDbCodec` (`sql/shared/src/main/scala/zio/blocks/sql/SqlInterpolator.scala:112`) is the only parameter instance for user types, and the macro reports the summon failure without the underlying cause (`sql/shared/src/main/scala/zio/blocks/sql/SqlMacros.scala:247-256`).

**Impact.** Users pass `isbn.value` instead, which works but drops the type safety the newtype exists for. The error message doesn't hint at the cause.

**Suggested fix.** Make `DbCodec.derived` use a `Schema[A]` in scope when there is one (`summonFrom`), or add a `DbParam` derived from `Schema[A]`.

### sql: an `Option` field of a nested case class is rejected, although the same field without `Option` is stored as JSON

Found 2026-10-21 while writing the how-to guide `docs/guides/map-custom-column-types.md` (backlog H-22), on `main` at `7d8e0b44`. The code is the same at tag `v0.0.56`. Verified. Not filed.

**Summary.** A `metadata: Metadata` field is stored as one JSON column, but `metadata: Option[Metadata]` makes derivation throw `Option[A] where A has 3 columns is not supported`. The `Option` branch derives the record's own multi-column codec instead of the JSON fallback the non-optional field gets.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-sql` 0.0.56, run with `scala-cli run OptionRecordField.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56

import scala.util.Try
import zio.blocks.schema.Schema
import zio.blocks.sql.*

case class Metadata(publisher: String, edition: Int, pages: Int)
object Metadata { given Schema[Metadata] = Schema.derived }

case class Book(id: Long, title: String, metadata: Metadata)
object Book { given Schema[Book] = Schema.derived }

case class DraftBook(id: Long, title: String, metadata: Option[Metadata])
object DraftBook { given Schema[DraftBook] = Schema.derived }

@main def run(): Unit = {
  println(DbCodec.derived[Book].columns)
  // Vector(id, title, metadata)
  println(Try(DbCodec.derived[DraftBook]))
  // Failure(java.lang.UnsupportedOperationException: Option[A] where A has 3 columns is not supported. Option is only supported for single-column types.)
}
```

**Expected.** `Option[Metadata]` is one nullable JSON column, as `DbCodec.jsonbOption[Metadata]` provides.

**Root cause.** The `Option` branch of `DbCodecDeriver#deriveVariant` takes the inner field's codec from `D.instance(innerField.value.metadata)` and rejects multi-column codecs (`sql/shared/src/main/scala/zio/blocks/sql/DbCodecDeriver.scala:352-360`), while `deriveRecord` picks `jsonbCodec` for a record field only when the field's own type is a record (`DbCodecDeriver.scala:134-136`).

**Impact.** Users must override the field with `DbCodec.jsonbOption` through `DbCodec.derivedWith`, which needs a `JsonCodec` for the record, and then build the `Table` by hand (see the `Table.derived` entry above).

**Suggested fix.** In the `Option` branch, use `jsonbOption`-style wrapping when the inner type is a non-inline record, a collection or a non-simple variant.

### schema: `DynamicSchema#check` rejects JSON numbers and `Option` values that the JSON codec accepts

Found 2026-10-21 while writing the how-to guide `docs/guides/build-a-schema-registry.md` (backlog H-09), on `main` at `d1b4fe52`. The code is the same at tag `v0.0.56` (only Scaladoc changed in these files since). Verified on Scala 2.13.18 and 3.9.0 against published 0.0.56. Not filed.

**Summary.** `DynamicSchema#check` compares primitive types exactly, and `Json#toDynamicValue` reads a whole number as `Int` (or `Long`), every other number as `BigDecimal`, and an `Option` as a bare value or `null`. So `check` rejects JSON that the typed JSON codec decodes: a whole-number `BigDecimal` price, any `Double`/`Float` field, a small `Long`, and every `Option` field. `Schema#fromDynamicValue` sits in between: it widens numbers but also rejects the bare `Option`. A JSON document written by the typed codec itself fails `check`. Separately, `check`'s messages print `<function1>` where they mean the value's kind.

**Reproduction** (Verified; Scala 2.13.18, `zio-blocks-schema` 0.0.56, run with `scala-cli run CheckVsDecode.scala`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.json._

final case class BookPublished(isbn: String, price: BigDecimal, rating: Double, edition: Option[Int])
object BookPublished { implicit val schema: Schema[BookPublished] = Schema.derived }

object CheckVsDecode {
  def main(args: Array[String]): Unit = {
    val stored = Schema[BookPublished].toDynamicSchema
    val text   = """{"isbn":"978-0","price":50,"rating":4.5,"edition":2}"""
    val dv     = Json.parseUnsafe(text).toDynamicValue

    println(Schema[BookPublished].jsonCodec.decode(text))
    // Right(BookPublished(978-0,50,4.5,Some(2)))
    println(Schema[BookPublished].fromDynamicValue(dv))
    // Left(zio.blocks.schema.SchemaError: Expected a variant at: .edition)
    println(stored.check(dv))
    // Some(zio.blocks.schema.SchemaError: Expected BigDecimal, got Int at: .price)

    val fixedPrice = Json.parseUnsafe("""{"isbn":"978-0","price":50.5,"rating":4.5,"edition":2}""").toDynamicValue
    println(stored.check(fixedPrice))
    // Some(zio.blocks.schema.SchemaError: Expected Double, got BigDecimal at: .rating)

    val fixedRating = Json.parseUnsafe("""{"isbn":"978-0","price":50.5,"rating":4,"edition":2}""").toDynamicValue
    println(stored.check(fixedRating))
    // Some(zio.blocks.schema.SchemaError: Expected Double, got Int at: .rating)

    // Even the typed codec's own output fails: an Option is written as a bare value
    val own = Json.parseUnsafe(Schema[BookPublished].jsonCodec.encodeToString(BookPublished("978-0", BigDecimal("50.5"), 4.5, Some(2))))
    println(own.print)
    // {"isbn":"978-0","price":50.5,"rating":4.5,"edition":2}
    println(stored.check(own.toDynamicValue))
    // Some(zio.blocks.schema.SchemaError: Expected Double, got BigDecimal at: .rating)

    // DynamicValueType has no readable toString, so messages say <function1>
    val optOnly = DynamicSchema(Schema[Option[Int]].reflect.noBinding)
    println(optOnly.check(DynamicValue.int(2)))
    // Some(zio.blocks.schema.SchemaError: Expected Variant, got <function1> at: .)
    println(DynamicValueType.Variant)
    // <function1>
  }
}
```

The Scala 3 run uses the same file with the header changed to
`//> using scala 3.9.0`, `//> using options -experimental` and the `zio-blocks-next-*` artifacts (same version), and prints exactly the same lines.

**Expected.** Either `check` accepts what the codecs accept (widen numbers the way `PrimitiveType#fromDynamicValue` does, and accept a bare value or `null` for an `Option`), or the docs say plainly that `check` is for `DynamicValue`s made by `Schema#toDynamicValue`, not for JSON. Error messages name the kind: `Expected Variant, got Primitive`.

**Root cause.** `checkPrimitive` requires `pt.typeId == pv.primitiveType.typeId` (`schema/shared/src/main/scala/zio/blocks/schema/DynamicSchema.scala:401-419`; `:394-412` at v0.0.56), while decoding widens: `PrimitiveType.BigDecimal#fromDynamicValue` accepts `Byte`, `Char`, `Short`, `Int`, `Long`, `Float`, `Double` and `BigInt` (`schema/shared/src/main/scala/zio/blocks/schema/PrimitiveType.scala:424-445`; `:427` at v0.0.56). `Json#toDynamicValue` narrows numbers (`json/Json.scala:1026-1043`). `Option` is a `Variant` of `None`/`Some(value)` (`Reflect.scala:1654-1663`), and the JSON codec writes it bare. The `<function1>`: `sealed trait DynamicValueType extends (DynamicValue => Boolean)` (`DynamicValueType.scala:33`) inherits `Function1#toString`, and Scala does not generate a `toString` for a case object whose parent already overrides it; the messages interpolate `${value.valueType}` (`DynamicSchema.scala:300, 305, 310, 315, 416`).

**Impact.** A gateway that validates incoming JSON events with a stored `DynamicSchema` — the use the `toSchema` section of `dynamic-schema.md` recommends — rejects valid events for the most common field types. The H-09 guide warns readers to use `check` on JSON only for string, boolean, `Int`, list and record fields, and to rebind and decode otherwise.

**Test gap.** `DynamicSchemaSpec` builds every checked value by hand with exact primitive types; no test checks a value produced by `Json#toDynamicValue` or the JSON codec. No test asserts the text of an "Expected Variant/Record/Sequence/Map" message.

**Suggested fix.** In `checkPrimitive`, accept a value when `pt.fromDynamicValue` accepts it (or an explicit lossless-widening table). In `checkValue`, when the schema node is an `Option` variant, accept `Null` as `None` and any other value as `Some`. Add `override def toString` to `DynamicValueType`'s case objects (or interpolate a name).

### schema: `BindingResolver.defaults` has no binding for `Option`, so rebinding any schema with an `Option` field fails

Found 2026-10-21 while writing the how-to guide `docs/guides/build-a-schema-registry.md` (backlog H-09), on `main` at `d1b4fe52`. The code is the same at tag `v0.0.56` (only Scaladoc changed in these files since). Verified on Scala 2.13.18 and 3.9.0 against published 0.0.56. Not filed.

**Summary.** `BindingResolver.defaults` covers primitives, `java.time`, collections and `Map`, but not `Option`. Rebinding a schema with an `Option[A]` field therefore throws `RebindException: Missing Record binding for type scala.Some at path: .<field><Some>`, which names `Some` rather than `Option`. The fix is three extra bindings per element type (`Binding.of[Option[A]]`, `Binding.Record.someInt` / `Binding.Record.some[A]`, `Binding.Record.none`), which only one test shows.

**Reproduction** (Verified; Scala 2.13.18, `zio-blocks-schema` 0.0.56, run with `scala-cli run OptionRebind.scala`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.binding._

final case class BookPublished(isbn: String, edition: Option[Int], subtitle: Option[String])
object BookPublished { implicit val schema: Schema[BookPublished] = Schema.derived }

object OptionRebind {
  def main(args: Array[String]): Unit = {
    val stored = Schema[BookPublished].toDynamicSchema

    try stored.rebind[BookPublished](BindingResolver.empty.bind(Binding.of[BookPublished]) ++ BindingResolver.defaults)
    catch { case e: RebindException => println(e.getMessage) }
    // Missing Record binding for type scala.Some at path: .subtitle<Some>

    val resolver = BindingResolver.empty
      .bind(Binding.of[BookPublished])
      .bind(Binding.of[Option[Int]])
      .bind(Binding.Record.someInt)
      .bind(Binding.of[Option[String]])
      .bind(Binding.Record.some[String])
      .bind(Binding.Record.none) ++ BindingResolver.defaults
    val rebound = stored.rebind[BookPublished](resolver)
    println(rebound.jsonCodec.decode("""{"isbn":"978-0","edition":2,"subtitle":"Functional Effects"}"""))
    // Right(BookPublished(978-0,Some(2),Some(Functional Effects)))
  }
}
```

The Scala 3 run uses the same file with the header changed to
`//> using scala 3.9.0`, `//> using options -experimental` and the `zio-blocks-next-*` artifacts (same version), and prints exactly the same lines.

**Expected.** `BindingResolver.defaults` rebinds `Option[A]` for any `A`, as it does `List[A]`: an `Option` entry keyed by the type constructor plus `Some`/`None`, so `BindingResolver.empty.bind(Binding.of[BookPublished]) ++ BindingResolver.defaults` is enough.

**Root cause.** `defaults` (`schema/shared/src/main/scala/zio/blocks/schema/binding/BindingResolver.scala:225-271`; `:215-261` at v0.0.56) binds no `Option`, `Some` or `None`. `RebindTransformer#transformRecord` reaches the `Some` case record first and throws with its `TypeId` (`RebindTransformer.scala:47-50`).

**Impact.** Every real event schema has an optional field, so every registry consumer hits this on its first rebind. `dynamic-schema.md` and an example say the opposite (see the Documentation errors entry). The H-09 guide shows the three bindings.

**Test gap.** Only `DynamicSchemaRebindSpec` "rebinds option schema" (`schema/shared/src/test/scala/zio/blocks/schema/DynamicSchemaRebindSpec.scala:243-262`) covers it, with the manual bindings; `BindingResolverSpec`'s "contains ... in defaults" suites (`:345-401`) don't list `Option`.

**Suggested fix.** Add `Option` (variant), `Some` (record, generic in its element) and `None` to `defaults`, keyed by type constructor like the sequence bindings; or document the three bindings in `binding-resolver.md` and make the `RebindException` message mention `Option` when the missing type is `scala.Some`.

### schema: case-class default values are not part of a `DynamicSchema`

**Severity:** low.

Found 2026-10-21 while writing the how-to guide `docs/guides/build-a-schema-registry.md` (backlog H-09), on `main` at `d1b4fe52`. The code is the same at tag `v0.0.56` (only Scaladoc changed in these files since). Verified on Scala 2.13.18 and 3.9.0 against published 0.0.56. Not filed.

**Summary.** For `BookPublished(isbn: String, edition: Int = 1)`, the field `edition` in `Schema[BookPublished].toDynamicSchema` has no default: `DynamicSchema(field.value).getDefaultValue` is `None`. A consumer that only has the stored schema can't tell that the field is safe to add, so a user-written compatibility check (as in the H-09 guide) must treat it as breaking. Rebinding with `Binding.of` brings the default back, so decoding is not affected.

**Reproduction** (Verified; Scala 2.13.18, `zio-blocks-schema` 0.0.56, run with `scala-cli run DefaultNotStored.scala`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

final case class BookPublished(isbn: String, edition: Int = 1)
object BookPublished { implicit val schema: Schema[BookPublished] = Schema.derived }

object DefaultNotStored {
  def main(args: Array[String]): Unit = {
    println(Schema[BookPublished].jsonCodec.decode("""{"isbn":"978-0"}"""))
    // Right(BookPublished(978-0,1))

    val edition = Schema[BookPublished].toDynamicSchema.reflect.asRecord.get.fields.find(_.name == "edition").get
    println(DynamicSchema(edition.value).getDefaultValue)
    // None
  }
}
```

The Scala 3 run uses the same file with the header changed to
`//> using scala 3.9.0`, `//> using options -experimental` and the `zio-blocks-next-*` artifacts (same version), and prints exactly the same lines.

**Expected.** `Schema#toDynamicSchema` turns each field's binding default into the stored default (`storedDefaultValue`, as `Reflect#defaultValue` does), so `getDefaultValue` returns `Some(1)` as a `DynamicValue`, and it survives storage.

**Root cause.** The derived default lives in the field's `Binding` (the field's reflect is a `Reflect.Deferred`), and `Reflect#noBinding` (`schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala:202-203`) drops bindings without copying the default into `storedDefaultValue`. Not traced further.

**Impact.** Low; affects schema comparison and documentation tools that read a stored schema. The H-09 guide says its `compare` reports a defaulted field as breaking for this reason.

**Test gap.** `DynamicSchemaSpec` "defaultValue and examples" (`:705-760`) sets defaults with `DynamicSchema#defaultValue`; no test derives a case class with a default parameter and reads it from `toDynamicSchema`.

**Suggested fix.** In `noBinding`, convert a binding's default value to a `DynamicValue` and keep it as the stored default.

**Related.** The Scaladoc wave 5 entry "schema: `Schema#toDynamicSchema` drops the default values of case class fields" (below) covers the same defect from the API side; file them as one GitHub issue.

### sql: the `sql` identifier check suggests an SQL keyword instead of the misspelled column

Found 2026-10-20 while writing the migration guide `docs/guides/migrate-from-magnum-or-doobie.md` (backlog M-03), on `main` at `78694917`. `SqlIdentifierChecker.scala` is unchanged at tag `v0.0.56` and at `main` `2f73284b`. Verified by running mdoc (Scala 3.9.0, docs project, `sql.jvm`).

**Summary.** When a misspelled identifier is the same edit distance from a column of the table and from an allowlisted SQL keyword, the compile error suggests the keyword. For `titel` it suggests `time`, not the column `title`. A misspelled `auther` correctly suggests `author`, because no keyword is equally close.

**Reproduction** (an mdoc block on the docs project, Scala 3.9.0; the error is the exact output mdoc rendered):

```scala
import zio.blocks.schema.Schema
import zio.blocks.sql.*

case class Book(id: Long, title: String, author: String, year: Int, isbn: Option[String])
object Book { given Schema[Book] = Schema.derived }
val bookTable = Table.derived[Book]

StringContext("SELECT titel FROM book WHERE year < ", "").sql(bookTable)(2010)
// error:
// Unknown identifier 'titel' at position 7; did you mean 'time'?
```

**Expected.** "did you mean 'title'?": the caller passed `bookTable`, so its columns are the likely targets.

**Root cause.** `sql/shared/src/main/scala/zio/blocks/sql/SqlIdentifierChecker.scala:278-288` puts known tables, known columns and the ~160-entry keyword allowlist into one `suggestionPool`. `findSuggestion` (`:451-466`) breaks a tie at equal Levenshtein distance by taking the lexicographically smaller candidate. levenshtein("titel", "title") = 2 = levenshtein("titel", "time"), and "time" < "title".

**Impact.** The suggestion points the user at the wrong fix.

**Test gap.** No checker test covers a tie between a column and a keyword.

**Suggested fix.** On a tie, prefer known tables and columns over allowlist entries: search tables and columns first, and fall back to the allowlist only when they give no candidate within distance 2.

### schema: `Into` lists accumulated field errors in reverse field order

Found 2026-10-22 while writing the tutorial `docs/guides/evolve-your-data.md` (backlog T-02, section 4), on `main` at `2f73284b`; same at tag `v0.0.56`. Verified by running.

**Summary.** When several fields fail, `SchemaError#message` lists them last field first: with bad fields `a`, `b`, `c` it prints `c`, `b`, `a`. Readers who compare the message with their case class see the fields "upside down". The T-02 page shows `edition` before `year` for this reason, and its prose doesn't promise an order.

**Reproduction.** Line `C` of the script in "schema: `Into.derived` silently drops a value when a same-name field changes from `A` to `Option[A]`" above (`scala-cli run Repro.scala`, Scala 3.9.0, `zio-blocks-next-schema` 0.0.56).

**Expected.** Errors in field declaration order: `a`, `b`, `c`.

**Root cause.** `sequenceEithers` in `schema/shared/src/main/scala-3/zio/blocks/schema/IntoVersionSpecific.scala:1615-1633` folds with `foldRight` and builds `Left(err ++ err2)`, where `err` holds the errors of the later fields.

**Suggested fix.** Build `Left(err2 ++ err)`. The exact-message test at `IntoVersionSpecificSpec.scala:213-220` expects the reversed order and needs updating with it.

### async: cancelling an `Async` never reaches the underlying call for `fromFuture`, `fromCompletionStage`, `fromJsPromise` or `Async.promise`

Found 2026-10-22 while writing the how-to guide `docs/guides/wrap-callback-and-future-apis.md` (backlog H-28), on branch `async-interop` at `94080050`. Same at tag `v0.0.56`: `async/` differs from the tag only by an import cleanup in `scala-3/AsyncSyntaxVersionSpecific.scala`. Verified by running.

**Summary.** Cancelling a running `Async` (`Running#cancel`) stops the driver and calls `Pollable#cancel` on the pending leaf. Every built-in bridge's leaf is a `Completer`, which is `final` and inherits the no-op `Pollable#cancel`, so the source keeps running: a `CompletableFuture` is not cancelled, and a callback API's cancel handle can't be passed anywhere, because `Async.promise` only hands the body a `Completer`. `docs/reference/async.md` doesn't say so.

**Reproduction** (save as `CancelRepro.scala` and run `scala-cli run CancelRepro.scala`):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-async:0.0.56
//> using option -experimental

import zio.blocks.async._

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

object CancelRepro {
  def main(args: Array[String]): Unit = {
    val stage = new CompletableFuture[Int]()
    Async.fromCompletionStage(stage).start.cancel()
    println(s"CompletableFuture cancelled after Async cancel: ${stage.isCancelled}")
    // CompletableFuture cancelled after Async cancel: false

    val open = new AtomicInteger(0)
    def callbackApi(onDone: Int => Unit): () => Unit = {
      open.incrementAndGet()
      () => open.decrementAndGet() // the API's cancel handle
    }
    Async.promise[Int] { c ?=>
      callbackApi(n => c.succeed(n))
      ()
    }.start.cancel()
    println(s"Open callback requests after Async cancel: ${open.get()}")
    // Open callback requests after Async cancel: 1
  }
}
```

**Expected.** Either the bridges forward cancellation to their source (`CompletableFuture#cancel`, and an `Async.promise` overload that accepts a cancel handle), or the reference page states plainly that they don't.

**Root cause.** `async/shared/src/main/scala/zio/blocks/async/Completer.scala:31` (`final class Completer[A] extends Pollable[A]`, with no `cancel` override); `Pollable.scala:72` (`def cancel(): Unit = ()`); `async/jvm/src/main/scala/zio/blocks/async/AsyncInterop.scala:47-80` (`fromFuture`/`fromCompletionStage` build on `promiseInternal`, and nothing calls `cf.cancel`); `Async.scala:1705` (`promiseInternal` returns `c.peek`, the `Completer`).

**Impact.** Users who rely on cancel to free resources leak in-flight requests (sockets, timers, server work). The workaround is not obvious: a custom `Pollable` that keeps the `Completer` for the result, answers `this` from `poll` while pending, and overrides `cancel`. The H-28 guide documents it as a `cancellable` helper.

**Test gap.** No test checks what happens to the source when a bridged `Async` is cancelled. `AsyncInteropSpec` covers the reverse direction only: a cancelled source gives a `CancellationException`.

**Suggested fix.** Add an `Async.promise` variant whose body returns a cancel callback, e.g. `def cancellable[A](register: Completer[A] => (() => Unit)): Async[A]` (essentially the guide's helper, run-verified through `map` and `Async.async`). Make `fromCompletionStage` call `toCompletableFuture.cancel(false)` on cancel, or make that opt-in. Then state the semantics in `docs/reference/async.md` under "Integration Points" and "Callback Bridge".

### schema: `DynamicSchema.fromDynamicValue` never fails, so a damaged stored schema accepts values it should reject

Found 2026-10-22 while writing the Scaladoc of `DynamicSchema.fromDynamicValue` (wave 5 of the schema Scaladoc, branch `scaladoc-wave-5`, on top of `6cd3c787`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`; the same file with `//> using scala 3.9.0` prints the same lines. Not filed on GitHub yet.

**Summary.** `DynamicSchema.fromDynamicValue` (and so `DynamicSchema.schema` and its codecs) turns any input into a schema. A part it does not recognize becomes a `Reflect.Dynamic` node, which `check` treats as "any value", an unreadable type id becomes `Unknown`, and an unknown primitive type name becomes `String`. A stored schema that is damaged, or written by a different version, loads without an error and then accepts values it was meant to reject.

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

case class Book(title: String, year: Int)
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

object Repro {
  def main(args: Array[String]): Unit = {
    val stored = DynamicSchema.toDynamicValue(Schema[Book].toDynamicSchema)
    // The stored schema of the `title` field is damaged, or written by another version
    val titleSchema = DynamicOptic.root.caseOf("Record").field("fields").at(0).field("value")
    val damaged     = stored.set(titleSchema, DynamicValue.string("garbled"))
    val loaded      = DynamicSchema.fromDynamicValue(damaged)
    println(loaded)
    println(loaded.check(DynamicValue.Record("title" -> DynamicValue.int(42), "year" -> DynamicValue.int(2021))))
    println(DynamicSchema.fromDynamicValue(DynamicValue.string("junk")).check(DynamicValue.int(42)))
  }
}
// DynamicSchema(record Book {
//   title: DynamicValue
//   year: Int
// })
// None
// None
```

**Expected.** `Left` (or an exception) from loading a schema that is not in the format `toDynamicValue` writes, so a schema registry can reject it.

**Root cause.** `dynamicValueToReflect` falls back instead of failing at every step: `Reflect.Dynamic` for an unknown or malformed node (`schema/shared/src/main/scala/zio/blocks/schema/DynamicSchema.scala:1938-1939`), `TypeId.nominal("Unknown", Owner.Root)` for a type id it cannot read (`:1679`), `PrimitiveType.String` for an unknown primitive (`:1839`), and defaults for missing fields; the result type, `DynamicSchema`, has no room for an error.

**Impact.** A registry that validates incoming data with stored schemas fails open: the damaged part, or the whole value, is no longer checked. The Scaladoc of `DynamicSchema.fromDynamicValue` now says so.

**Test gap.** The `DynamicSchemaSpec` round-trip tests (`schema/shared/src/test/scala-3/zio/blocks/schema/DynamicSchemaSpec.scala:1795`, `:1903`) read only values written by `toDynamicValue`.

**Suggested fix.** Add `DynamicSchema.fromDynamicValueEither` (or make the companion schema's constructor fail) returning `Left(SchemaError)` for an unknown node, type id or primitive name, and use it in `DynamicSchema.schema`; keep the lenient reader only where it is wanted.

### schema: an `#option(...)` search never matches an `Option` value from a `Schema`, and matches every value of the inner type instead

Found 2026-10-22 while writing the Scaladoc of `SchemaMatch` and `SchemaRepr` (wave 5 of the schema Scaladoc, branch `scaladoc-wave-5`, on top of `6cd3c787`; line numbers are those of the commit that adds this entry). The code, apart from Scaladoc comments, is the same at tag `v0.0.56`. Verified: the reproduction below was run with `scala-cli run --server=false Repro.scala` against the published `0.0.56`; the same file with `//> using scala 3.9.0` prints the same lines. Not filed on GitHub yet.

**Summary.** `SchemaRepr.Optional(inner)` (`#option(...)` in `p"..."`) matches `DynamicValue.Null` or a value that matches `inner`. A `Schema` encodes `Option` as a variant (`Some` with a `value` field, or `None` with an empty record), never as `Null`, so the pattern matches no option, and because the inner type alone is enough, `#option(string)` finds the same values as `#string`. `dynamic-optic.md` described the row as "Optional values with inner type".

**Reproduction** (Scala 2.13.18, `zio-blocks-schema` 0.0.56):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
import zio.blocks.schema._

case class Book(title: String, subtitle: Option[String])
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

object Repro {
  def main(args: Array[String]): Unit = {
    val dv = Schema[Book].toDynamicValue(Book("Zionomicon", Some("Functional Effects")))
    println(dv.get(DynamicOptic.root.field("subtitle")).one)
    println(dv.get(p"#option(string)").toChunk)
    println(dv.get(p"#string").toChunk)
  }
}
// Right({
//   value: "Functional Effects"
// } @ {tag: "Some"})
// Chunk("Zionomicon","Functional Effects")
// Chunk("Zionomicon","Functional Effects")
```

**Expected.** `#option(string)` finds `Some("Functional Effects")` (the variant, or its payload), and not the non-optional `title`.

**Root cause.** `SchemaMatch.matches` treats `Optional` as `value eq Null || matches(inner, value)` (`schema/shared/src/main/scala/zio/blocks/schema/SchemaMatch.scala:95-97`); nothing looks for the `Some`/`None` variant that `Schema[Option[A]]` produces.

**Impact.** Searches for optional fields silently return the wrong set of values. The `SchemaMatch` Scaladoc and the `dynamic-optic.md` pattern table now describe the current behaviour.

**Test gap.** The `Optional` suite of `SchemaMatchSpec` (`schema/shared/src/test/scala/zio/blocks/schema/SchemaMatchSpec.scala:508-540`) matches only `Null` and bare values, never a value produced by `Schema[Option[A]]`.

**Suggested fix.** Also match `Variant("None", _)` and `Variant("Some", Record(value -> v))` where `v` matches `inner`, and consider not matching a bare inner value. Add tests with values from `Schema[Option[String]]`.

### htmx: a `String` URL in `hx-get` is not percent-encoded, so user input in a query can add parameters

Found 2026-10-23 while writing T-10 (`docs/guides/add-interactivity-with-htmx.md`) on branch `docs/t10-htmx-interactivity`, from `origin/main` `a643b7cb0`. Not checked against tag `v0.0.56`.

**Summary.** `ToHtmxValue.urlLikeValue` returns a `String` unchanged. HTML escaping protects the attribute, but not the URL inside it, so a reader's `&` or `=` becomes a real query separator.

**Reproduction** (`Repro.scala`, run with `scala-cli run --server=false Repro.scala`):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-http-htmx:0.0.56

import zio.blocks.html._
import zio.http.htmx._

object Repro {
  def main(args: Array[String]): Unit = {
    val userInput = "\"><script>alert(1)</script>&x=y z"
    println(button(hxGet := ("/books?q=" + userInput)).render)
    println(button(hxGet := "/books?q=a&b=c").render)
  }
}
// <button hx-get="/books?q=&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;&amp;x=y z"></button>
// <button hx-get="/books?q=a&amp;b=c"></button>
```

**Expected.** The user text is percent-encoded in the query part, so `&` becomes `%26`, `=` becomes `%3D` and the space becomes `%20`. The request then carries one parameter `q`.

**Root cause.** `htmx/shared/src/main/scala/zio/http/htmx/ToHtmxValue.scala:47` (`case value: String => value`).

**Impact.** A search or filter value containing `&` or `=` adds or changes request parameters on `hx-get`. This is not script injection, because the HTML escaping holds.

**Test gap.** No test covers reserved characters in a `UrlLike` string.

**Suggested fix.** Encode query values when building the URL from user input (for example through `QueryParams`), or document that a `String` must already be percent-encoded.

## Test and CI

### Flaky streams test: `unfoldAsync - reset invalidates a stale completion and restarts from initial state`

Seen 2026-10-20 on PR #1773 (docs only, touches nothing in `streams/` or `async/`). In `testJVM (25, JVM, 3.3.x)`, this test hit its 30 s timeout while the other 12,760 tests passed. Rerunning is the current workaround. Needs investigation: a timing dependency in the test, or a real race in `unfoldAsync` reset.

Seen again 2026-10-20 on PR #1774 (H-12, docs only): an async-reader test hit the same 30 s timeout in `testJVM (17, JVM, 3.9.x)`. Later pushes replaced that run. Two sightings on unrelated docs PRs in one day suggest the timeouts are too tight for loaded CI runners.

Seen 2026-10-21 on four more docs-only PRs, each with exactly 1 of 12,761 tests failing and a different test each time:

- #1814 (H-22), `testJVM (25, JVM, 3.9.x)`: `unfoldAsync - reset invalidates a stale completion and restarts from initial state` (job 119294098388). Also hit once on #1807 (H-39), passed on rerun.
- #1802 (H-19), `testJVM (17, JVM, 3.3.x)`: `MergeInnerErrorSpec - error in inner stream does not hang (InnerDone-in-finally regression) - Long mergeAll: error in inner terminates without hang` (job 119256732852).
- #1808 (H-36), `testJVM (25, JVM, 3.9.x)`: `async reader reachable coverage - nested concat rejected reset closes its nested head` (job 119164951462).

All three are hang-detection tests in the streams async paths. Docs PRs now fail CI about once per run on these, so either the timeouts need headroom on shared runners or one of these paths has a real race. Worth one investigation pass with the tests looped locally under CPU load (`taskset -c 0,1`).

### mdoc: `mdoc:fail` on Scala 3 ignores earlier `:reset` sections

Seen 2026-10-20 while writing H-02. A `mdoc:fail` block after a `mdoc:silent:reset` reports bogus "already defined" errors for names from before the reset, so the expected compile error cannot be shown. H-02 described that error in prose instead. Pages that rely on `mdoc:fail` (H-31) should check their rendered output.


### mdoc: `mdoc:fail` and `mdoc:warn` also compile earlier `compile-only` blocks

Found 2026-10-20 while writing the how-to guide `docs/guides/fix-scope-and-wire-compile-errors.md` (backlog H-31). This is docs tooling (mdoc), not a library bug. Reproduced with this repo's `docs/mdoc`.

**Summary.** A `mdoc:fail` or `mdoc:warn` block is checked by compiling a program made of every earlier evaluated block *plus every earlier `compile-only` block*, pasted in without their own scope. A `compile-only` fix that redefines a name from the same section compiles fine on its own, but a later `mdoc:warn` then fails with no reason given. Later `mdoc:fail` blocks can fail for the clash instead of their intended error.

**Reproduction** (`sbt "++3.9.0; docs/mdoc --in docs/guides/zz-trap.md --out /tmp/zz-trap.md"`):

````markdown
```scala mdoc:silent:reset
class Book(val title: String)
```

```scala mdoc:compile-only
final case class Book(title: String)
```

```scala mdoc:warn
val two = { 1; 2 }
```
````

```
error: zz-trap.md:15:1: Expected compile warnings but program failed to compile
val two = { 1; 2 }
^^^^^^^^^^^^^^^^^^
info: Compiled in 17.99s (1 error)
```

Changing the second fence to `scala mdoc:silent:reset` makes the run pass.

**Root cause.** mdoc's fail/warn instrumentation builds one program from the earlier sections and inlines `compile-only` code into its section's class, so the two `Book` definitions clash. Run `docs/mdoc --verbose` to see it: the hidden program is the `FailSection("...")` string in the instrumented output.

**Workaround.** Start any fix block that redefines a name with `mdoc:silent:reset`, not `compile-only`. Always read the rendered `// error:` lines of every `mdoc:fail` block.

### `DeriverWithInstanceCsvSpec` asserts with `.contains`

Found 2026-10-20 while writing H-03. The schema-csv spec checks rendered output with `.contains`, against the AGENTS.md rule to assert the full rendered string with `==`. A regression in field order or separators would pass.

### `schema-examples` has no scalafmt setup

Found 2026-10-20 while writing H-03. The standalone `schema-examples/` build has no scalafmt plugin or config, so `sbt scalafmtAll` in the root build does not format it. Sessions format example files by hand or with `cs launch scalafmt -- --config .scalafmt.conf`. Add the plugin (or a `.scalafmt.conf` symlink) to the example builds, or document the command in AGENTS.md.

### Example projects' tests never run in CI

Found 2026-10-20 while writing H-47. CI's "Compile example projects" step (`project/CiWorkflow.scala:360`, generated into `.github/workflows/ci.yml`) runs `sbt compile` in each `*-examples/` build, so `src/test` sources are never compiled or run. `sql-examples` is the first examples build with tests (H-47's `BookshopSpec`, 4 tests, passing locally against 0.0.56). Switching the step to `sbt test` (and regenerating `ci.yml`) would cover them. This is a CI change for the maintainer to decide; H-47's attempt to make it was blocked by a permission check, so it was left alone.

### Three example builds are not registered in the root build

Found 2026-10-20 while recording H-37. `config-examples` (T-05), `sql-examples` (T-07) and `html-examples` (T-09) are standalone builds with their own `build.sbt` but no `lazy val ... = RootProject(file("..."))` line or root `aggregate(...)` entry in `build.sbt`, unlike the other standalone `*-examples/` builds. (`projection-examples` is deliberately a root subproject, `lazy val \`projection-examples\` = project` at `build.sbt:560`, with no `build.sbt` of its own, so it is not affected.) CI's examples loop still compiles them (it looks for a `build.sbt`), so the gap shows up in the root aggregate and IDE import. AGENTS.md (updated on the `jwt` branch, H-37) now says a new examples build needs both. Add the three missing entries.

### Benchmark manifest provider versions lag `build.sbt`

Found 2026-10-21 while fixing `docs/reference/streams/index.md:78` for M-04. `build.sbt:1484-1487` pins Kyo `1.0.0-RC7` (bumped in #1700) and Ox `1.1.0` (bumped in #1751) for `streams-benchmark`, but `streams-benchmark/benchmark-manifest.json:16-21` still records Kyo `1.0.0-RC6` and Ox `1.0.6`, and `streams-benchmark/tools/validate_matrix.py:14` asserts those old values. A provider run today therefore measures RC7 and 1.1.0 while its manifest claims RC6 and 1.0.6. Scala Steward updates `build.sbt` only. Either bump the manifest and the validator together (and rerun the provider baselines), or have `validate_matrix.py` read the versions from `build.sbt` so the two cannot drift. The reference page now names both sets of versions.

### Docs build memory with many sbt servers

Seen 2026-10-20: a full `sbt check` was OOM-killed while several worktrees each ran an sbt server (about 4 GB free). Shut down idle servers in finished worktrees with `sbt --client shutdown`.

### `schema-examples` and `streams-examples` have no scalafmt setup

Found 2026-10-20 while writing H-03. The standalone `schema-examples/` build has no scalafmt plugin or config, so `sbt scalafmtAll` in the root build does not format it. Sessions format example files by hand or with `cs launch scalafmt -- --config .scalafmt.conf`. The same gap exists in `streams-examples` (seen while writing M-04). Add the plugin (or a `.scalafmt.conf` symlink) to the example builds, or document the command in AGENTS.md.

### A subagent killed every sbt server on the machine

Seen 2026-10-20 around 23:40 (+0330) while H-30 was written: its examples-builder subagent ran `pkill -f sbt-launch`, which stops the sbt servers of every session on the machine, not only its own. Any session that lost its server around then should rerun its last sbt step. H-30 added a "never kill sbt by pattern" rule to the docs-examples skill; stop a server with `sbt --client shutdown` from its own worktree instead.

### async tests: 64 test names still refer to the removed `unsafeRunAsync`

Found 2026-10-22 while writing the how-to guide `docs/guides/wrap-callback-and-future-apis.md` (backlog H-28), on branch `async-interop` at `94080050`; same at tag `v0.0.56`.

**Summary.** 64 test names under `async/*/src/test` start with "unsafeRunAsync: ..." (e.g. `async/js/src/test/scala/zio/blocks/async/AsyncJsSpec.scala:122`, `:139`, `:149`), but `unsafeRunAsync` no longer exists in `async/*/src/main`; commit `7c49fb91a` removed it. Readers who grep the tests for cancel semantics find an API that doesn't exist.

**Reproduction** (from the repository root):

```bash
grep -rn "unsafeRunAsync" async/*/src/test | wc -l   # 64
grep -rn "unsafeRunAsync" async/*/src/main | wc -l   # 0
```

**Suggested fix.** Rename the test labels to the runner they exercise (`start`/`Running`).

## Documentation errors

Pages that contradict the source. None of these were copied into the new guides.

**schema / JSON**

- `json-config.md` (around lines 146-175) says `implicit val nameMapper: NameMapper = NameMapper.SnakeCase` in the companion makes `toJson` emit snake_case (confirmed wrong by mdoc while writing M-01: the field stays `firstAuthor`). Nothing reads an implicit `NameMapper`. The real ways are `JsonCodecDeriver.withFieldNameMapper` with `Schema#derive`, or `@Modifier.fieldNaming`. The output comments hide the problem because the blocks are compile-only. Severity: high, because it teaches code that silently does nothing.
- `customize-json-encoding.md` (H-02; the table around lines 357-361 and the prose around line 431) presents `withRequireCollectionFields` and `withRequireDefaultValueFields` as decoder-only settings. Setting either to true also makes the encoder write empty collections and default values (see Usability traps). Found while writing M-01.
- `json-config.md` "Custom Mappers" lists only `Identity`, `SnakeCase` and `KebabCase`; `CamelCase`, `PascalCase` and `Custom(f)` also exist.
- `modifier.md`, `transient`: does not say that a transient field's JSON value is ignored on decode and the default is used.
- `schema-error.md` (lines 56-57, 171-172, 283-289, 313-316) shows `Missing field 'x' at: .path` as the decode result. JSON decoding reports `missing required field "x" at: .path` (`JsonReader.scala:89`).
- `syntax.md` shows `show` output as `Record { name = Alice, age = 30 }`; the real output is the EJSON form (`DynamicValue.toString`).
- `Schema.fromJsonSchema` Scaladoc (`Schema.scala:357-358`) says values are validated "during construction"; validation happens at decode time (`:366-370`). Fixed in the wave 1 Scaladoc (branch `scaladoc-wave-1`).
- `validation.md:356-373` says `Validation` constraints become `minimum`, `maxLength`, `pattern` and so on in `toJsonSchema`. They do not; only parsing a JSON Schema maps them (`JsonSchemaToReflect.scala:261-285`).
- `validation.md:304` uses `JsonDecoder[Order].decodeString`; `JsonDecoder` was removed in #1240.
- `validation.md` code blocks that do not compile at 0.0.56 (verified with scala-cli on Scala 2.13.18 while writing H-04; the page has no mdoc blocks, so nothing caught them): `.withTypeName[...]` at lines 218, 235, 252, 401 and 420 (`value withTypeName is not a member of zio.blocks.schema.Schema[...]`; the type name comes from the implicit `TypeId` that `Schema#transform` takes, `Schema.scala:185`); `transform(wrap = ..., unwrap = ...)` at lines 266-273 (`unknown parameter name: wrap`; the parameters are `to` and `from`); `DynamicValue.Record(Vector(...))` at lines 324 and 348 (`overloaded method apply ... cannot be applied`; it takes a `Chunk` or `(String, DynamicValue)*`, `DynamicValue.scala:595`).
- `validation.md` output comments do not match the real messages (verified while writing H-04): line 177 shows `at: $.user.age`, the real text is `at: .user.age`; line 305 shows `must be positive at $.quantity`, the real text is `must be positive at: .quantity.wrapped`; line 353 shows `Expected String, got Int at $.name`, the real text is `Expected String, got Int at: .name at: .wrapped`.
- `validation.md:13` ("Automatic enforcement during schema-based decoding") and `:281` apply only to `Schema#transform` wrappers. `Validation` constraints are enforced only by `DynamicSchema#check` (see the Usability traps entry on `Validation`). Its newtype examples (`case class Email private (value: String)` and so on) also keep a public `apply` and `copy` on Scala 2.13, so `Email("junk")` compiles and skips the check (see Usability traps).
- `schema-error.md:41-43` says multiple independent problems are surfaced all at once and that every `Codec#decode` returns them in one `SchemaError`. The JSON and TOON codecs stop at the first failure; only `Schema#fromDynamicValue` (and `Into.derived`) collect every error. Found while writing H-04, which shows both.
- `json-schema.md:366` shows an error message without the ` at: <path>` suffix that `SchemaError.message` adds. The page also does not mention the `pattern` anchoring behaviour above.
- `ValidationOptions` Scaladoc (`JsonSchema.scala:376-377`) says format checks are annotation-only by default; the code defaults to `validateFormats = true`.
- `json-config.md:89-92`: the `ReaderConfig` table gives `preferredCharBufSize` a default of `8192`; the default is `4096` (`ReaderConfig.scala:108`). The table also leaves out `preferredBufSize` (32768), `maxBufSize` (33554432) and `maxCharBufSize` (4194304), so the two safety limits are undocumented on the reference page. Found while writing H-50.
- `json-config.md:208` says "`checkForEndOfInput` adds one comparison". After the value, the reader skips all trailing whitespace and, for an `InputStream` or `ByteBuffer`, keeps reading input to do it (`JsonReader.scala:1831-1850`). `:207` ("minimal performance impact" of indentation) and `:214` ("compact output is faster") are not backed by any benchmark in the repo. Found while writing H-50.
- `json/index.md:66-67` lists "order" as a `WriterConfig` option and "number handling" as a `ReaderConfig` option. Neither exists: `WriterConfig` has `indentionStep`, `preferredBufSize` and `escapeUnicode` (`WriterConfig.scala:42-44`), and `ReaderConfig` has only buffer sizes and `checkForEndOfInput` (`ReaderConfig.scala:50-54`). Found while writing H-50.

- `json.md:25` says "Objects use `Vector[(String, Json)]`". `Json.Object` holds a `Chunk[(String, Json)]` (`Json.scala:493`). Found while writing H-06; verified.
- `json.md:66-86` ("String Interpolators") says the `json"..."` interpolator is compile-time validated, without saying it does not compile on Scala 2.13 in user code (see Library bugs).
- `json.md:471` says the `Auto` merge strategy concatenates arrays, and the table at `json.md:494-501` lists a `Deep` strategy and says `Auto` and `Shallow` concatenate arrays. There is no `Deep` (`MergeStrategy.scala:37-115` has `Auto`, `Replace`, `Shallow`, `Concat`, `Custom`); `Auto` merges arrays by index (`{"arr":[1,2]}` merged with `{"arr":[9]}` gives `[9,2]`), and `Shallow` takes the right array (`[9]`). Verified with 0.0.56.
- `json.md:732-736` shows `toKV` output as `($.a.b, Json.Number(1))`. It prints `Chunk((.a.b,1),(.a.c,2))`; `DynamicOptic` has no `$` prefix. Verified.
- `json.md:904-905` shows `Left(SchemaError: Index 5 out of bounds at path $.users[5])`. The real message is `Index 5 out of bounds (size: 1) at: [5]` (`Json.scala:671-672`): no `$`, and the path holds only the index. Verified.
- `json-selection.md:92` says to access elements "using `JsonSelection#apply` or `JsonSelection#get`". `JsonSelection` has `apply(index: Int)` (`JsonSelection.scala:182`) but no `get(Int)`; the example compiles only because `data` is a `Json`, which has `get(index)`.
- `json-selection.md:284` shows `data.get("age").error  // Some(SchemaError("field not found"))`. The message is `Key 'age' not found` (`Json.scala:508-516`). Verified.
- `JsonSelection` Scaladoc (`JsonSelection.scala:29`) shows `json.get("users").asArrays.apply(0).get("name").asStrings`; there are no `asArrays`/`asStrings` (they became the `arrays`/`strings` filters in #830). `Json` Scaladoc (`Json.scala:40`) says "Numbers use `String`"; `Json.Number` holds a `BigDecimal` (`Json.scala:796`).

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

**openapi (`openapi.md`)**

Found while writing H-39 (`docs/guides/write-and-export-openapi.md`, 2026-10-21) and fixed in the same PR:

- The signature at the top of the page showed `servers` and `security` as `Option[Chunk[...]] = None`; both are `Chunk[...] = Chunk.empty`, and the signature left out `jsonSchemaDialect`, `webhooks`, `tags`, `externalDocs` and `extensions` (`OpenAPI.scala:58-70`). The type diagram had the same `Option` types, `requestBody` without its `Option`, and `Map` where the source uses `ChunkMap`.
- Installation showed only `zio-blocks-openapi`, without the `zio-blocks-next-openapi` artifact for Scala 3.9 and newer or the `schema-yaml` module for YAML.
- "Integration Points" said Markdown descriptions round-trip correctly; a document with `md"..."` descriptions is not `==` to itself after a round trip (see Library bugs), and the page never said that `encodeToString` throws.
- "Parameter" listed `schema` as required; it is `Option[ReferenceOr[SchemaObject]] = None` (`OpenAPI.scala:589`).
- "Operation" called `responses` required; it defaults to `Responses()` (`OpenAPI.scala:311`).
- "Common Extension Fields" said all types have `x-*` extensions; `Reference`, `XML`, `Discriminator` and `SecurityRequirement` have no `extensions` field.

**config (`formats.md`, HOCON section)**

- Line 191 says `include` directives are resolved through a callback, so includes "come from a classpath, an archive, or a test fixture". Through the public `ConfigSource` API there is no callback (see the dropped-includes bug) and no classpath loader.
- Line 247 says `HoconError` carries the line and column. That holds only for lexer errors; resolution errors report 0:0. From source.
- The HOCON section has no warnings for the dropped includes, environment substitutions, array concatenation and duration/size syntax above.

**http-model (`schema.md`)**

- "RequestSchemaOps" says it "exposes all methods from `QueryParamsSchemaOps` and `HeadersSchemaOps` directly on `Request`", "ResponseSchemaOps" says the same for `Response`, and the "How They Work Together" diagram lists `RequestSchemaOps.header[T](name)` and `ResponseSchemaOps.header[T](name)`. `request.header[T](name)` and `response.header[T](name)` do not compile (see Library bugs). The page's own examples quietly use `request.headers.header[T]`. Found while writing H-36.
- "Supported Types" says the module "supports decoding to any type with a `Schema[T]` instance", and "Custom Types" says to "provide a `Schema[T]` instance". Only primitive schemas decode; `Option`, collections and wrapper types return `Malformed(..., "Unsupported schema type for string decoding")` (see Usability traps). Found while writing H-36.

**schema / binding** (found and fixed while writing the `binding` Scaladoc, wave 3)

- `binding.md` showed `defaultValue` and `examples` fields on every `Binding` case class, a `Wrapper` with `wrap: B => Either[SchemaError, A]`, `Binding[T <: BindingType, A]`, a `SeqConstructor.Boxed` class with `ObjectBuilder`/`newObjectBuilder`/`addObject`, field names `seqConstructor`/`mapConstructor`, an `ArraySeq` binding in `Binding.Seq`'s companion and "stack-allocated" arrays. None of these match `Binding.scala`, `SeqConstructor.scala` or `MapConstructor.scala`; the page now shows the real signatures.
- `registers.md` said a `RegisterOffset` is an `Int` with 16-bit halves; it is a `Long` with 32-bit halves (`schema/shared/src/main/scala-2/zio/blocks/schema/binding/RegisterOffset.scala:44`, `:73`, the same lines in the `scala-3` copy). Its list said `RegisterOffset(bytes = 4, ints = 2, objects = 3)` points at byte 8; it is byte 12, as the page's own `getBytes` example shows.
- `binding-resolver.md` bound `Binding.of[Email]` and then called `resolveWrapper[Email]`, which returns `None`: for a plain single-field case class, `Binding.of` derives a `Binding.Record` (verified with 0.0.56; rebinding the `Schema#transform` schema with it throws `RebindException: Missing Wrapper binding for type Email`). The `UserId` example in "Combining Resolvers" had the same mistake. Both now bind a `Binding.Wrapper` explicitly.
- Scaladoc: `Registry#bind` said `Binding.of[A]` returns `Any` on Scala 2 (its static type is `Binding[_, A]`); `StructuralReflection` said the `Binding.of` macro uses it (only tests do; the macros generate their own `getMethod` calls).

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

**config (flags and rollouts)**

- `rollout.md` says "a percentage behaves as the literal share of keys it names". That holds only for the first choice on a pattern; later choices are thresholds (see the `Rollout.validate` entry).
- `rollout.md` says a sum above 100 "usually means someone added a rule instead of adjusting one". This follows from the same slice reading, which evaluation does not use.
- `flags.md` says the two parse exceptions are "wrapped in ExceptionInInitializerError by the JVM". The library constructs the `ExceptionInInitializerError` itself (`StaticFlag.scala:109`, `DynamicFlag` `initSnapshot`); the effect for callers is the same.
- The `DynamicFlag` Scaladoc example `"true@beta/50%; false"` (`DynamicFlag.scala:32`) matches only the key `beta`, not 50% of beta users.

**scope**

- `scope.md:1065` lists a non-existent `andThen`; `scope.md:19-22` have wrong `scoped`, `allocate` and `open` signatures; `scope.md:943` quotes `Cannot acquire resource: scope has already been closed.`; the real text is `Cannot allocate resource: scope is already closed.`
- `wire.md:66` (`new UserService($(db)(identity))`) does not compile; `wire.md:28` calls Wire a "lazy recipe" (see the eager `Wire(value)` bug).
- `finalization.md` says `abstract class` in five places (the source is `final class`); line 173 has wrong LIFO comments; line 280 names the private `runFinalizers`.
- Thread ownership (`scope.md:952`, Scope tutorial lines 690, 706, 749): only `scoped` checks the owner thread; `allocate`, `defer` and `$` do not.
- `unscoped.md:114` and the Scope tutorial (lines 316, 332) do not mention Scala 3 automatic derivation (see the `AutoCloseable` escape bug).
- Several pages teach `val appResource = Resource.from(...)` (see the single-use bug).

### docs: `context.md` shows `Wire.make[Config & Logger, Service]`, which does not exist

Found 2026-10-20 while writing the how-to guide `docs/guides/feed-config-into-a-wire-graph.md` (backlog H-30), on `main` at `b4179489`. The same at tag `v0.0.56`. Verified by compiling.

**Summary.** The section "Integration with Wire and Scope" of `docs/reference/context.md:470-493` builds a service with `val buildService = Wire.make[Config & Logger, Service]`. The block is a plain fence labelled pseudocode, so mdoc never compiles it, but it reads as the API. `Wire` has no `make` method on its companion; `make(scope, ctx)` is an instance method of `Wire.Shared` and `Wire.Unique`. The macro that derives a wire from `Service`'s constructor is `Wire.shared[Service]` (or `Wire.unique[Service]`), and it infers the input type `Config & Logger` itself.

**Reproduction** (`scala-cli run .`; the page's code with `Wire.shared`, then the page's line):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-scope:0.0.56

import zio.blocks.context.*
import zio.blocks.scope.*

case class Config(debug: Boolean)
case class Logger(name: String)
case class Service(config: Config, logger: Logger)

@main def contextWire(): Unit = {
  val buildService = Wire.shared[Service]
  val deps         = Context(Config(debug = true), Logger("app"))
  Scope.global.scoped { scope =>
    val service: Service = buildService.make(scope, deps)
    println(service)
  }
}
// Service(Config(true),Logger(app))
```

With the page's line instead, `val buildService = Wire.make[Config & Logger, Service]`, compilation fails:

```
[error] ./ContextWireMake.scala:15:22
[error] value make is not a member of object zio.blocks.scope.Wire
[error]   val buildService = Wire.make[Config & Logger, Service]
[error]                      ^^^^^^^^^
```

**Expected.** The page shows code that compiles.

**Root cause.** The page was written against an API that doesn't exist: `Wire`'s companion offers `apply`, `shared`, `unique` and `Shared.fromFunction`/`Unique.fromFunction` (`scope/shared/src/main/scala/zio/blocks/scope/Wire.scala:282`, `scope/shared/src/main/scala-3/zio/blocks/scope/WireVersionSpecific.scala:59`, `:72`, `:93`, `:114`), and no `make`.

**Impact.** A reader who copies the snippet gets a compile error with no hint of the right method. The backlog already warns writers not to copy it (`docs/dev/backlog.md`, rows H-30 and the context entry).

**Test gap.** The block is a plain ` ```scala ` fence labelled pseudocode, so mdoc doesn't compile it.

**Suggested fix.** Replace the line with `val buildService = Wire.shared[Service]`, drop the "pseudocode" label, and make the block `mdoc:compile-only` so it stays compiled.

### Scope tutorial, reference pages and example contradict the source

Found 2026-10-20 while writing the how-to guide `docs/guides/fix-scope-and-wire-compile-errors.md` (backlog H-31), on `main` at `64b2f666`. Reproduced against the published `0.0.56`. Listed here and not fixed in that PR.

**Wrong statements:**

- `docs/guides/compile-time-resource-safety-with-scope.md:726` says `defer` throws `IllegalStateException` on a closed scope. `defer` returns a no-op `DeferHandle` and ignores the finalizer (`Scope.scala:202-211`).
- The same line quotes the exception as `Scope is closed`. The real messages are `Cannot allocate resource: scope is already closed.` (allocate), `Cannot open child scope: scope is already closed.` (open) and `Cannot access scoped value: scope is already closed.` (`$`).
- The same file at `:820` says `Wire.shared[Database]`, where `Database(url: String)`, fails with "no Wire[String] for url". It compiles. The error only appears later, from `Resource.from`, as E7 `Cannot auto-create java.lang.String`.
- The same file uses the fence `scala mdoc:compile-only mdoc:silent:reset` at `:27` and `:728`. That fence mixes two modifier lists, `compile-only` and `silent:reset`, so it isn't clear which one the author meant mdoc to apply.
- `docs/reference/resource-management/scope.md:943` quotes `Cannot acquire resource: scope has already been closed.` The real text is `Cannot allocate resource: scope is already closed.`
- `docs/reference/resource-management/wire.md:85` and `:506`, and `index.md:18`, promise compile-time cycle detection and "missing dependency" errors. Cycles crash the macro (#1767), and `renderMissingDependency` is never called (see the entry above).
- `scope-examples/src/main/scala/scope/examples/CircularDependencyDemoExample.scala:28` and `:41` say that uncommenting the cycle "would produce a compile error" with a cycle diagram. It crashes the macro instead. `scope.md:1258` presents that example as cycle detection.

**Reproduction** (`scala-cli run .` per file; same header as above):

```scala
// Closed.scala: defer is a no-op, and the allocate message
import zio.blocks.scope.*

class Db extends AutoCloseable { def close(): Unit = () }

@main def closed(): Unit = {
  val shop  = Scope.global.open()
  val scope = shop.scope
  shop.close()
  val handle = scope.defer(println("never runs"))
  println(s"defer on a closed scope returned: $handle")
  try scope.allocate(Resource(new Db))
  catch { case e: IllegalStateException => println(e.getMessage.linesIterator.take(3).mkString("\n")) }
}
// defer on a closed scope returned: zio.blocks.scope.DeferHandle$Noop$@2f7a2457
// ── Scope Error ─────────────────────────────────────────────────────────────────
//
//   Cannot allocate resource: scope is already closed.
```

```scala
// WireDb.scala: Wire.shared[Database] compiles
import zio.blocks.scope.*

case class Database(url: String) extends AutoCloseable {
  override def close(): Unit = ()
}

@main def wireDb(): Unit = {
  val dbWire = Wire.shared[Database]
  println(s"Wire.shared[Database] compiled: $dbWire")
}
// Wire.shared[Database] compiled: Shared(WireDb$package$$$Lambda/0x000070e180002900@45c8e616)
```

**Suggested fix.** Correct each statement to match the source, and point section 12 of the tutorial and the error sections of `scope.md` at `docs/guides/fix-scope-and-wire-compile-errors.md` instead of keeping a second list of messages. Fix the example's comment, and the cycle claims, once #1767 is fixed.

### docs: `db-codec.md` says non-optional types throw on SQL `NULL`, but `String` decodes to `null` and numeric types to `0`

Found 2026-10-20 while writing the how-to guide `docs/guides/stream-large-query-results.md` (backlog H-20), on `main` at `b4179489`. Verified with `zio-blocks-next-sql` 0.0.56 and sqlite-jdbc 3.53.4.0; the code is the same at tag `v0.0.56`.

**Summary.** `docs/reference/sql/db-codec.md:22` says "non-optional types throw `IllegalStateException` on unexpected `NULL`, surfacing schema mismatches immediately rather than silently coercing." Only `BigDecimal` and the deriver's `Char` do that. A `NULL` read into a `String` field becomes `null`, and a `NULL` read into an `Int` field becomes `0`. `Frag#query` and `Frag#queryStream` behave the same way.

**Reproduction.** With `case class Sale(id: Long, title: String, quantity: Int, soldOn: LocalDate)` and one row in a SQLite `sale` table:

- `sql"SELECT id, NULL AS title, quantity, sold_on FROM sale".queryStream[Sale].runCollect` returns `Right(Chunk(Chunk(Sale(1,null,3,2026-10-01))))`.
- The same query with `.query[Sale]` returns `List(Sale(1,null,3,2026-10-01))`; `title` is a real `null`.
- `SELECT id, title, NULL AS quantity, sold_on` with `.query[Sale]` returns `List(Sale(1,Zionomicon,0,2026-10-01))`.

**Root cause.** Only `BigDecimal` (`sql/shared/src/main/scala/zio/blocks/sql/DbCodec.scala:336,340`) and the deriver's `Char` (`sql/shared/src/main/scala/zio/blocks/sql/DbCodecDeriver.scala:661-662`) call `unexpectedNull`. `stringCodec` (`DbCodec.scala:278-285`), `intCodec`, `longCodec` and the other primitive codecs return whatever the JDBC getter returns (`null`, `0`, `false`). The type table on the same page (`db-codec.md:360`) says "Throws on SQL NULL" only for `BigDecimal`, which matches the code, so the page contradicts itself.

**Impact.** A schema mismatch (a nullable column mapped to a non-`Option` field) passes silently: a `String` field holds `null` and can throw a `NullPointerException` far from the query, and a numeric field holds `0`, which looks like real data.

**Test gap.** Only `DbCodecSpec.scala:612-616` tests the `NULL` failure, for `BigDecimal`. No test reads `NULL` into a `String` or a numeric field.

**Suggested fix.** Either change `db-codec.md:22` to say that only `BigDecimal` and `Char` fail fast, `String` gives `null` and the numeric and `Boolean` types give the JDBC default, or make the primitive codecs check `wasNull` and throw. The second is a behaviour change with a per-column cost.

### docs: `sql-zio.md` and `transactor-zio.md` show code that doesn't compile or blocks ZIO's main threads

Found 2026-10-21 while writing the how-to guide `docs/guides/sql-from-zio.md` (backlog H-21), on `main` at `a44dc6db`. The import order was verified by compiling (see the shadowing entry under Library bugs); the rest is from source.

**Summary.** Both pages are all plain `scala` fences, so nothing on them is compiled. The H-21 branch fixes the import order in every snippet (it was `import zio.blocks.sql.*` before `import zio.blocks.sql.zio.*`, which never compiles), says that `postgresLayer`/`sqliteLayer` need `import zio.blocks.sql.zio.*`, and corrects the "interrupt-safe" bullet in `transactor-zio.md` (interrupting `transactZIO` commits; see Library bugs). These remain:

- `sql-zio.md` "Quick Start" runs `transactor.connect { sql"SELECT 1"... }` inside `ZIO.serviceWith[Transactor]`, which executes the JDBC call on ZIO's default thread pool. It should be `ZIO.serviceWithZIO[Transactor](t => ZIO.attemptBlocking(t.connect(...)))`, or use `TransactorZIO`.
- `sql-zio.md` uses `userRepo`, `newUser` and `User` without defining them, and calls itself a "guide" although it is a reference page.
- `transactor-zio.md` builds its examples on `TransactorZIO.fromUrl("jdbc:sqlite::memory:", ...)` and then uses the table in a later call, which its own `fromUrl` section (`:149`) says can't work: every call gets a fresh, empty database.
- `transactor-zio.md` shows the class shape without the two-argument `transact(isolation, readOnly)` and `transactZIO(isolation, readOnly)` overloads (`TransactorZIO.scala:80`, `:132`).
- Neither page says that inside `connectZIO`/`transactZIO` the body's statements must be wrapped in `ZIO.attemptBlocking` to leave the default pool, which `TransactorZIO.scala:200` makes necessary.

**Suggested fix.** Turn both pages into `mdoc` pages now that `sql-zio` is on the docs classpath, on a temporary SQLite file, and link `docs/guides/sql-from-zio.md` for the recipes.

### docs: `csv.md` promises error locations the codec doesn't give, and documents unused API

Found 2026-10-20 while writing the how-to guide `docs/guides/read-and-write-csv-and-tsv-files.md` (backlog H-11), on `main` at `b4179489`. Verified by running. PR #1773 (merged) fixed other parts of this page (TSV "Pattern 2", run-time rejection, `Option` fields); the statements below are still on `main` after it.

**Summary.** `docs/reference/schema/built-in-codecs/csv.md` makes these claims that the source contradicts:

- `:31` "Precise error reporting with row/column locations" and `:153` "CSV errors report the exact row and column where parsing or type conversion failed". Decode errors carry no row, column or field (see "decode errors never name the column" above), and parse errors count rows from the failing row (see "`CsvError` row and column count from the start of the failing row" above).
- `:151-178` "Pattern 3" decodes a whole document, header included, with `CsvCodec#decode`. The codec reads only the first row, so the example reports the header, not the bad `salary` cell.
- `:357-366` presents `CsvError.TypeError` as the error for a failed conversion; nothing produces it.
- `:321` and `:335` document `nullValue`, which nothing reads.
- `:522` "Flat case classes (records with no variant or sequence fields) are fully supported" leaves out that a nested record field is accepted and written as one cell with a line break inside.

**Reproduction of Pattern 3** (Scala 3.9.0, `zio-blocks-next-schema-csv` 0.0.56, run with scala-cli):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-schema:0.0.56
//> using dep dev.zio::zio-blocks-next-schema-csv:0.0.56

import java.nio.CharBuffer
import zio.blocks.schema._
import zio.blocks.schema.csv._

case class Employee(id: Int, name: String, salary: BigDecimal)
object Employee { implicit val schema: Schema[Employee] = Schema.derived }

@main def repro(): Unit = {
  // csv.md "Pattern 3: Handle CSV Errors with Location Information", verbatim input
  val codec   = Employee.schema.derive(CsvFormat)
  val csvData = "id,name,salary\n1,Alice,invalid\n"
  println(codec.decode(CharBuffer.wrap(csvData)))
  // Left(zio.blocks.schema.SchemaError: Cannot parse 'id' as Int: For input string: "id" at: .)
}
```

**Suggested fix.** Say that `CsvCodec#decode` decodes one row and its errors name the bad value but not the column, and rewrite Pattern 3 to read the document with `CsvReader.readAll` and decode each row, counting rows itself (as the H-11 guide does). Mark `TypeError` and `nullValue` as unused, and add nested records to the "Record Type Support" section.

**Other modules**

- `html.md`: artifact name for Scala 3.7+, `Css.Declaration`, `hx-*` URL attributes, `selector""`. Filed as #1766.
- `streams/index.md:78` lists Kyo RC6 and Ox 1.0.6; the build pins RC7 and 1.1.0.
- `docs/index.md` and `built-in-codecs/json/index.md:39,45` give `zio-blocks-schema` for every Scala version; Scala 3.7+ uses `zio-blocks-next-schema`. H-01 (stalled) includes this fix.
- `combinators.md` documents a `Choices` module that does not exist (research §9.6).
- `query-dsl-sql.md` "Compile-time SQL Dumps" (around lines 1080-1095) and the `Dump` Scaladoc (`Dump.scala:25-30`, `:547-549`) say query dumps are named `<owner>-<dialect>.sql` and fall back to `query`. `Dump.dumpQuery` writes `<name>_query-<dialect>.sql` (the `-query` suffix is sanitized to `_query`), named after the `val` it is assigned to, else after the owner, else `<type>_query`. Found while writing H-19; see the `Dump.dumpQuery` library bug.
- `docs/dev/research.md` cites stale line numbers (for example `Transactor.scala:65` is now `:261`/`:275`, `Table.scala:32` is now `:262`).

### docs: telemetry pages give the wrong JDK requirement, no Scala 3.9 artifact, and a double-printing example

Found 2026-10-21 while writing T-12, on `main` at `a44dc6db` (same at `v0.0.56`). Verified against the published 0.0.56 on JDK 17, 21 and 25.

- **JDK.** The JVM module is compiled with `-release 21` (`build.sbt:746-755`). On JDK 17 even compiling a `log.*` call fails, because the log macros run inside the compiler: `java.lang.UnsupportedClassVersionError: zio/blocks/telemetry/LogMacros$ has been compiled by a more recent version of the Java Runtime (class file version 65.0), this version of the Java Runtime only recognizes class file versions up to 61.0` (verified with `scala-cli run --server=false --jvm 17`; without `--server=false`, scala-cli compiles on its own newer JVM and hides this). Code that only uses spans or metrics compiles on JDK 17 and fails the same way at run time (`zio/blocks/telemetry/trace$`). On JDK 21 it works without `--enable-preview`, including span context propagation through `ScopedValue` (a preview API in JDK 21 to 24, final in 25): a log line inside a nested span carries the span's ids, and the child's parent is the outer span. `telemetry-guide.md:1032` and `:1113` call `ScopedValue` storage "JDK 25+", and the `ContextStorage` Scaladoc says "Pure ScopedValue storage for JDK 25+" (`telemetry/jvm/src/main/scala/zio/blocks/telemetry/ContextStorage.scala:49`); the installation sections of `reference/telemetry/index.md` and `telemetry-guide.md` gave no JDK requirement. Fixed in the T-12 PR: both installation sections now say JDK 21 or newer, and the guide's JDK 25 wording is corrected.
- **Test and CI gap.** The telemetry and otel tests run only on JDK 25 (`whenJdkAtLeast(25, "telemetryJVM/test; otel/test")`, `build.sbt:149` and `:155`), although the published jar targets Java 21. Nothing in CI runs the module on JDK 21.
- **Scala 3.9.** Neither installation section said that Scala 3.9 and newer use `zio-blocks-next-telemetry` with `-experimental` (without the flag, 0.0.56 fails with `LogVersionSpecific is marked @experimental`). Fixed in the T-12 PR in `reference/telemetry/index.md`.
- **Double printing.** `reference/telemetry/logging/index.md:178-183` adds `log.writer(TextLogFormatter, StdoutWriter)` and `log.writer(JsonLogFormatter, StderrWriter)` as "human-readable text to stdout, JSON to stderr". `log.writer` is additive and the default console output stays, so every line is printed twice on standard output, once by the default output and once by the new writer, plus once as JSON. The example needs `log.install(LoggerProvider.builder.build().get(...))` first. Not fixed here.
- **`removeAll`.** `reference/telemetry/logging/index.md:176` says `log.*` calls become no-ops after `log.removeAll()` "until you add an output again"; adding an output with `writer` or `addProcessor` doesn't restore them (see the library bug above). Not fixed here.
- **Histogram boundaries.** The `HistogramDataPoint` Scaladoc calls `boundaries` "upper exclusive boundaries for each bucket" (`telemetry/shared/src/main/scala/zio/blocks/telemetry/MetricData.scala:68`), but `Histogram.findBucketIndex` puts a value in the first bucket whose boundary is `>=` the value (`value <= boundaries(i)`, `Histogram.scala:98-105`), so the boundaries are inclusive upper bounds, as in OpenTelemetry. From source; not fixed here.
- **Missing ADR.** `ContextStorage.scala:28` links `docs/adr/0001-scoped-context-no-set.md`, which never existed (already noted in `backlog.md`).

### `docs/reference/schema/built-in-codecs/yaml.md` shows output the codec does not produce and omits kebab-case keys

Found 2026-10-21 while writing the how-to guide `docs/guides/read-and-write-yaml.md` (backlog H-15), on `main` at `4ec6dd90`. `schema-yaml` has no changes since tag `v0.0.56`, so the behaviour is the same in the 0.0.56 release. Verified by running.

**Summary.** The YAML reference page has no mdoc blocks, and several of its plain-fence examples and claims contradict 0.0.56:
1. "Pattern 3: Pretty-Print with Configuration" (`yaml.md:176-202`) says `YamlOptions` controls the output, but its code calls `codec.encodeToString(server)`, which takes no options and never writes `---`; the comment shows a `---` line that is not printed. `YamlCodec` has no method that takes `YamlOptions`; options apply only to `YamlWriter.write` and `Yaml#print`/`printPretty`.
2. The `Yaml.print` example (`yaml.md:285-304`) shows `active: true` for `Yaml.Scalar("true")`; the writer prints `active: "true"` because the scalar has no `Bool` tag.
3. "Each field becomes a named key" (`yaml.md:446`) doesn't say the key is the field name in kebab-case: `zipCode` is written as `zip-code`. The page never mentions this, nor that field modifiers are ignored.
4. "Sealed traits and sum types are encoded as YAML mappings with discriminator information" (`yaml.md:463`, also `:65`) — there is no discriminator: a case is a one-key mapping named after the case (`Stopped:` / `  exit-code: 1`).
5. "27 primitive types" (`yaml.md:6`, `:51`, `:417`); the deriver handles 30 (by reading `YamlCodecDeriver.scala`, not run).
6. The page never mentions `YamlReader`, `YamlWriter`, `YamlSyntax`, the `yaml"..."` interpolator, `YamlTag`, or that the reader is lenient and never reports syntax errors.

**Reproduction** (scala-cli, run exactly as written; the output is pasted at the end):

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema-yaml:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Server(name: String, endpoints: List[String], timeout: Int)
object Server { implicit val schema: Schema[Server] = Schema.derived }

case class Address(street: String, city: String, zipCode: String)
object Address { implicit val schema: Schema[Address] = Schema.derived }

sealed trait Status
case class Running(pid: Int) extends Status
case class Stopped(exitCode: Int) extends Status
object Status { implicit val schema: Schema[Status] = Schema.derived }

@main def referencePage(): Unit = {
  // Pattern 3: "Pretty-Print with Configuration"
  println(Server.schema.derive(YamlFormat).encodeToString(Server("api-server", List("GET /health", "POST /data"), 30)))
  // "Format YAML nodes using compact or pretty-printed output"
  val yaml = Yaml.Mapping.fromStringKeys("name" -> Yaml.Scalar("Bob"), "active" -> Yaml.Scalar("true"))
  println(yaml.print)
  // Record support: "Each field becomes a named key"
  println(Address.schema.derive(YamlFormat).encodeToString(Address("Unter den Linden 1", "Berlin", "10117")))
  // Variant support: "encoded as YAML mappings with discriminator information"
  println(Status.schema.derive(YamlFormat).encodeToString(Stopped(1)))
}
// Output:
// name: api-server
// endpoints:
//   - GET /health
//   - POST /data
// timeout: 30
// name: Bob
// active: "true"
// street: Unter den Linden 1
// city: Berlin
// zip-code: "10117"
// Stopped:
//   exit-code: 1
```

**Expected.** The page's comments match this output; Pattern 3 uses `YamlWriter.write(codec.encodeValue(server), YamlOptions.pretty)` (or `codec.encodeValue(server).printPretty`); the records section states the kebab-case rule; the variants section shows the wrapper shape.

**Root cause.** Hand-written, never-compiled examples (`yaml.md` has 0 mdoc blocks).

**Impact.** Readers copy Pattern 3 and get no document marker; expect camelCase keys and fail to read their files; expect a discriminator field.

**Test gap.** The page isn't checked by mdoc.

**Suggested fix.** Convert the page's examples to `mdoc` blocks so the output is generated, fix the five statements above, and add sections on `YamlReader`/`YamlWriter`, `YamlSyntax`, the interpolator (with its Scala 2.13 limitation) and the unsupported YAML features. The H-15 guide (`docs/guides/read-and-write-yaml.md`) has a verified table of unsupported features that can be linked.

### docs: `table.md` and `db-codec-deriver.md` say `createTable` handles collection and nested-record fields

Found 2026-10-21 while writing the how-to guide `docs/guides/map-custom-column-types.md` (backlog H-22), on `main` at `7d8e0b44`. The code is the same at tag `v0.0.56`. Verified. Not filed.

**Summary.** `docs/reference/sql/table.md:238` says fields that fall back to JSONB "such as `List[A]`" produce "a `TEXT` or `JSONB` column", and that "nested records that are flattened into multiple columns are fully supported". `Table.derived` throws for a `List[A]` field, and it flattens every nested record even though the codec stores a non-inline record as one JSON column (see the `Table.derived` entry under Library bugs). `table.md:273` repeats "nested records are flattened". `docs/reference/sql/db-codec-deriver.md:23` says the fallback is "a single `TEXT` / `JSONB` column"; no code path emits `JSONB`, since `SqlDialect` maps `DbString` to `TEXT` on both dialects (`sql/shared/src/main/scala/zio/blocks/sql/SqlDialect.scala:36,64`).

**Suggested fix.** Until the library bug is fixed, say that `Table.derived`, `Repo.derived` and `createTable` work only for records whose nested record fields carry `sql.inline`, and that a JSON column on PostgreSQL must be declared `JSONB` by hand.

### schema: `dynamic-schema.md` and three examples say a `DynamicSchema` can be stored as JSON and that `BindingResolver.defaults` covers `Option`

Found 2026-10-21 while writing the how-to guide `docs/guides/build-a-schema-registry.md` (backlog H-09), on `main` at `d1b4fe52`. The code is the same at tag `v0.0.56` (only Scaladoc changed in these files since). Verified on Scala 2.13.18 and 3.9.0 against published 0.0.56. Not filed.

**Summary.** Five places contradict the code at 0.0.56:
- `docs/reference/schema/dynamic-schema.md:16`: "Can be serialized to a `DynamicValue` and deserialized back, making it storable in databases or transmittable over the network". True in memory, but no format except Avro stores that `DynamicValue` losslessly (see the Library bugs entry on JSON round trips).
- `schema-examples/src/main/scala/dynamicschema/DynamicSchemaRegistryExample.scala:30-32`: "the blob can be stored as JSON or bytes without any issue". Stored as JSON, it reads back as an accept-anything schema.
- `schema-examples/src/main/scala/bindingresolvers/BindingResolverSchemaRegistryExample.scala:12-14`: "DynamicSchema carries only field names, type names, and annotations, so it can be stored as JSON or bytes". Same problem. (This example is linked from no page.)
- `docs/reference/schema/dynamic-schema.md:267`: "`BindingResolver.defaults` covers all standard primitives, `java.time` types, `List`, `Map`, `Option`, and other standard collections." It has no `Option` binding (see the Usability traps entry), so a reader who follows it gets `Missing Record binding for type scala.Some`. `binding-resolver.md:88-96` lists the defaults correctly, without `Option`.
- `schema-examples/src/main/scala/dynamicschema/DynamicSchemaRebindExample.scala:39`: "BindingResolver.defaults covers all primitives, java.time, List, Map, Option, etc." Same error; the example works only because its types have no `Option` field.

Also misleading: `dynamic-schema.md:410` recommends `DynamicSchema#toSchema` for "validation gateway[s] or format converter[s]". Used with JSON input, its `check` rejects valid events with `Double`, `Float`, small `Long`, whole-number `BigDecimal` or `Option` fields (see the Usability traps entry on `check`).

**Reproduction.** The `LossyRoundTrip213.scala`, `OptionRebind.scala` and `CheckVsDecode.scala` reproductions in the entries above (Verified on Scala 2.13.18 and 3.9.0 against 0.0.56).

**Expected.** The page and the examples say that only `Schema[DynamicSchema].derive(AvroFormat)` round-trips a `DynamicSchema` in 0.0.56, that `defaults` has no `Option`, how to bind one, and that `check`/`toSchema` expect `DynamicValue`s built by `Schema#toDynamicValue`.

**Root cause.** The page and examples were written from the API's intent; no test round-trips through a format or rebinds `Option` with `defaults` alone.

**Impact.** Readers build registries that validate nothing, or hit a `RebindException` they were told can't happen.

**Test gap.** The examples only compile in CI; none checks its claims.

**Suggested fix.** Correct the five sentences, add a "Storing a DynamicSchema" subsection to `dynamic-schema.md` that uses Avro, and link the H-09 guide (it now has a See-also line there). Update the three example comments.

### docs: `telemetry-guide.md` tells tests to "restore state" with `log.removeAll()`, which silences later tests

Found 2026-10-21 while writing the how-to guide `docs/guides/test-telemetry.md` (backlog H-48), on `main` at `7e8ac787` (same at `v0.0.56`). Verified by running against the published 0.0.56 on JDK 21. Not fixed here.

**Summary.** The "Testing with telemetry" section of `docs/guides/telemetry-guide.md:334-376` captures logs with a `CapturingLogProcessor` installed through `log.install`, then says "Don't forget to restore state between tests" and shows `log.removeAll()`. `removeAll` restores nothing. It calls `shutdown()` on the test's processor, which in the guide's own processor clears the captured records; it leaves no output at all instead of the default console output; and, because of the `log.removeAll()` bug in the Library bugs section, a later test that adds its processor with `log.addProcessor` (or an output with `log.writer`) captures nothing. Line 1099 repeats the pattern.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-telemetry` 0.0.56, JDK 21, run with `scala-cli run --server=false --jvm temurin:21 RestoreRepro.scala`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-telemetry:0.0.56
package bookstore

import java.util.concurrent.CopyOnWriteArrayList
import zio.blocks.telemetry._

// The processor from telemetry-guide.md, "Testing with telemetry"
class CapturingLogProcessor extends LogRecordProcessor {
  val records = new CopyOnWriteArrayList[LogRecord]()

  def onEmit(record: LogRecord): Unit = records.add(record)
  def shutdown(): Unit = records.clear()
  def forceFlush(): Unit = ()
}

object RestoreRepro {
  def main(args: Array[String]): Unit = {
    // Test 1, as the guide shows it
    val first = new CapturingLogProcessor
    log.install(LoggerProvider.builder.addLogRecordProcessor(first).build().get("test"))
    log.warn("payment retried")
    println(s"test 1 captured: ${first.records.size}")

    // "Don't forget to restore state between tests"
    log.removeAll()

    // Test 2 adds its processor to the global logger
    val second = new CapturingLogProcessor
    log.addProcessor(second)
    log.warn("payment retried")
    println(s"test 2 captured: ${second.records.size}")

    // Code that expects the default console output
    log.info("printed by the default output?")
    println("end")
  }
}
// test 1 captured: 1
// test 2 captured: 0
// end
```

**Expected.** The guide's teardown leaves logging working: test 2 captures one record, and the last `log.info` prints a line.

**Root cause.** Documentation. `log.removeAll()` shuts down every processor and installs the silent state (`telemetry/shared/src/main/scala/zio/blocks/telemetry/log.scala:65-73`, `GlobalLogState.scala:130-132`); there is no public call that restores the default console logger.

**Impact.** Readers who follow the guide get tests that pass alone and fail, capturing nothing, when another test ran first.

**Suggested fix.** Replace the teardown with "install a fresh capturing logger with `log.install` at the start of each test, and run such tests sequentially", or recommend passing a `Logger` to the code under test, as `docs/guides/test-telemetry.md` does. Drop the `records.clear()` from `shutdown()` in the example processor.

### docs: the `Frag#queryChunked` Scaladoc example calls `Transactor.connect(transactor)`, which does not exist

Found 2026-10-22 by the M-03 session, checked against source on `main` (same at `v0.0.56`). `sql/shared/src/main/scala/zio/blocks/sql/Frag.scala:375` shows `Transactor.connect(transactor) { ... }`, and `:370` mentions `Transactor.connect`. There is no `object Transactor`: `Transactor` is a trait (`Transactor.scala:65`) whose `connect` is an instance method (`:68`). The example should read `transactor.connect { ... }`.

### docs: `as.md` calls dropping an `Option` field "round-trips cleanly"

Found 2026-10-22 while writing the tutorial `docs/guides/evolve-your-data.md` (backlog T-02), on `main` at `2f73284b`.

**Summary.** `docs/reference/schema/schema-evolution/as.md:359` says an `Option` field that is absent from the other type "round-trips cleanly: `Some(v)` becomes `None` after a round-trip". That is data loss, not a clean round trip: line `A` of the script in "schema: `Into.derived` silently drops a value when a same-name field changes from `A` to `Option[A]`" shows `TypeA(x,Some(n))` coming back as `TypeA(x,None)`.

**Suggested text.** "**`Option` fields on one side are allowed.** An `Option` field absent from the other type is dropped on the way out and comes back as `None`, so `Some(v)` does not survive a round trip:"

### docs: the htmx reference never says which htmx version its attribute syntax targets

Found 2026-10-23 while writing T-10 (`docs/guides/add-interactivity-with-htmx.md`), from `origin/main` `a643b7cb0`.

**Summary.** `docs/reference/htmx/index.md` documents attributes such as `hx-trigger` and `hx-swap` without saying which htmx release they target. The syntax targets htmx 2.x; htmx 4.x parses these attributes differently (HCON), so readers pasting the attributes into a 4.x page get different behaviour.

**Suggested fix.** One sentence on the reference index: "The attribute syntax targets htmx 2.x" and the pinned script version (for example `htmx@2.0.11`).

## Resolved

- streams overview (`streams/index.md`) marked `fromInputStream` and `fromJavaReader` as JVM-only, but both run on Scala.js. Fixed in #1796 (H-24).
- docs: `zio-blocks-next-*` needs Scala 3.9+ (plus `-experimental`), not 3.7+. Fixed across 20 guides and 14 example builds in #1810 (H-01). Leftovers on `main`: `guides/stream-files-and-sockets.md:80` still says "Scala 3.7 and newer", and 14 example builds still turn on `-experimental` at `minor >= 8`.
- Codecs: `format.md` (`sealed trait`, real `BinaryFormat`/`TextFormat` signatures, which formats are binary); TOON `ArrayFormat.Auto` Scaladoc and `toon.md:706`; `csv.md` (`CsvConfig` ignored by derived codecs, runtime rejection, `Option` rejected); `xml.md` stale version; `built-in-codecs/index.md` artifact prefix. Fixed in #1773.
- schema: on Scala 3.10, direct optic access (`Person.name`) on a `DerivedOptics` companion stopped compiling outside `zio.blocks.schema`, because the `given Conversion` behind it needed the `private[schema]` `OpticsFor`; Scala 3.9 only warned. `OpticsFor` is now public. Fixed in #1743. The `DerivedOptics` Scaladoc no longer warns about it (wave 4 of the schema Scaladoc).
- `json-patch.md:608` said that converting a `JsonPatch` to RFC 6902 is a matter of encoding it with its `Schema` and reformatting. The encoded patch holds number deltas and string edits that RFC 6902 cannot express, so the conversion needs the source document to compute `replace` values and must track list indices as earlier operations shift them. No other page or Scaladoc claims RFC 6902 compatibility. The sentence now says this and links the H-07 guide's converter. Fixed in #1815 (H-07).
- `transactor-zio.md` mentioned `JdbcTransactor.postgresLayer` without saying it is an extension that needs `import zio.blocks.sql.zio.*` (found while writing H-17). Fixed on the H-21 branch `docs/h21-sql-from-zio`.
- `type-class-derivation.md` "Customizing Derivation with Instance and Modifier Overrides": all four examples were uncompiled plain blocks, one with a `// ... AST overrides ...` placeholder and undefined types. Each is now a self-contained `mdoc:compile-only` block with the real JSON AST overrides. Fixed in #1787.
- `jwt.md`: audience mismatch documented as `InvalidToken` (it is `InvalidClaim("aud", ...)`), missing `InvalidClaim`/`InvalidKey` rows, no key-format guidance, no frontmatter. Fixed in #1789 (H-37). The `JwtDecodeOptions` Scaladoc (`Jwt.scala:131-132`) still says `InvalidToken`; see the jwt entry above.
- `sql-transactions.md`: the Hikari recipe, "JDK 25+" virtual-thread claim and "pool size equals database max connections" advice were replaced by a link to H-17's guide (virtual threads are final in JDK 21, JEP 444; JEP 491 shipped in JDK 24; pool sizing must keep the sum of all pools under `max_connections`). Also fixed: `.query[Int].toOne` (no such method; `queryOne[Long]`) and the unsupported "read-only avoids WAL writes" claim. Fixed in #1790 (H-17).
- `codec.md` never mentioned `Deriver#withInstance`/`withModifier`, and its BinaryCodec/TextCodec paragraph was wrong (`CsvCodec` extends `TextCodec`; XML and YAML extend `BinaryCodec`; `BsonCodec` extends neither). New "Overriding a Type on a Deriver" subsection and fix in #1784 (merged).
- Stale example: `schema-examples/src/main/scala/deriveroverrides/CompleteExample.scala` predated #1348 and showed no codec override. Rewritten in #1781 to demonstrate type, term-name and optic overrides with a round trip and the current run command.
- Docs build: a broken relative link in `docs/reference/schema/modifier.md:395` (`../guides/` instead of `../../guides/`), added by #1775, failed Build Docs on main. Fixed in #1772.
- Config: `formats.md:97` (YAML parse error type) and `config-source.md` (`EnvSource` and `keyFormat`). Fixed in #1771.
- sql: `sql-transactions.md` default isolation and PostgreSQL facts; `repo.md`/`table.md`/`ddl.md` upserts, `pageAfter` and the missing `PRIMARY KEY` caveat; `:memory:` warnings in `transactor.md` and `transactor-zio.md`; `db-con.md` `DbTx` members; the backlog's "sql has no JS backend" note. Fixed in #1760.
- Codecs: `format.md` (`sealed trait`, real `BinaryFormat`/`TextFormat` signatures, which formats are binary); TOON `ArrayFormat.Auto` Scaladoc and `toon.md:706`; `csv.md` (`CsvConfig` ignored by derived codecs, runtime rejection, `Option` rejected); `xml.md` stale version; `built-in-codecs/index.md` artifact prefix. In PR #1773 (open).
- `patch.md`: every snippet was a plain uncompiled fence; five sections did not compile (`editString` with `Vector`, `modifyKey` with a forward-referenced implicit, `Patch[Data] ++ Patch[Counter]`, `val new`, a non-existent `JsonEncoder`), and the Strict, Clobber, `Patch#apply`, Prism-`set`, LazyList and string-delta claims contradicted the source. The snippets are now mdoc blocks and the claims are corrected; see the H-07 entry "docs: `patch.md` has six snippets that don't compile and several claims the source contradicts". Fixed in #1828.

### build (Scala 3): every main-line artifact was built with -experimental after #1743, so every Scala 3 user needed -experimental

Found 2026-10-22 while reviewing the Scaladoc wave 3 branch, on `main` at `bffadae7` and still at `4a142547`. Introduced by #1743 (`3defefab`). Not in the release: `v0.0.56` builds its main line with Scala 3.3, and its artifacts need no flag on Scala 3.3.7 or 3.9.0. Verified by running against local publishes of `main` and of the fix.

**Summary.** #1743 moved the main line (`zio-blocks-*`) to Scala 3.9 and the next line (`zio-blocks-next-*`) to Scala 3.10, but kept passing `-experimental` to every Scala 3 compile from 3.8 up. When a module is compiled with `-experimental`, scalac marks each of its top-level definitions `@experimental`. So every main-line Scala 3 artifact on `main` was experimental for its users: `Chunk(1, 2, 3)`, `Schema[Int]`, `derives Schema` and `Schema.derived` did not compile without `-experimental`.

**Reproduction** (Scala 3.9.0 and scala-cli 1.10.1, against a local publish of `main` at `4a142547`: `sbt 'set ThisBuild / version := "0.0.0-exp1743"; ++3.9.0; chunkJVM/publishLocal; typeidJVM/publishLocal; markdownJVM/publishLocal; maybeJVM/publishLocal; schemaJVM/publishLocal'`, then `scala-cli run --server=false <file>`):

```scala
//> using scala 3.9.0
//> using repository ivy2Local
//> using dep dev.zio::zio-blocks-schema:0.0.0-exp1743
import zio.blocks.schema._

case class Book(title: String, year: Int) derives Schema

object Main {
  def main(args: Array[String]): Unit =
    println(Schema[Book].jsonCodec.encodeToString(Book("Zionomicon", 2021)))
}
// -- Error: CaseClass.scala:6:50
// 6 |case class Book(title: String, year: Int) derives Schema
//   |                                                  ^
//   |trait SchemaCompanionVersionSpecific is marked @experimental: Added by -experimental
//   |
//   |Experimental definition may only be used under experimental mode:
//   |  1. in a definition marked as @experimental, or
//   |  2. an experimental feature is imported at the package level, or
//   |  3. compiling with the -experimental compiler flag.
// -- Error: CaseClass.scala:6:50
//    6 |case class Book(title: String, year: Int) derives Schema
//      |                                                  ^
//      |   object TypeId is marked @experimental: Added by -experimental
//      ...
//      |This location contains code that was inlined from SchemaCompanionVersionSpecific.scala:1186
// 2 errors found
// Compilation failed
```

Code that does not derive fails the same way, on every main-line object it touches:

```scala
//> using scala 3.9.0
//> using repository ivy2Local
//> using dep dev.zio::zio-blocks-schema:0.0.0-exp1743
import zio.blocks.schema._
import zio.blocks.chunk.Chunk

object Main {
  def main(args: Array[String]): Unit = {
    println(Chunk(1, 2, 3))
    println(Schema[Int].jsonCodec.encodeToString(42))
    println(DynamicValue.Primitive(PrimitiveValue.Int(1)))
  }
}
// -- Error: Plain.scala:9:12
// 9 |    println(Chunk(1, 2, 3))
//   |            ^^^^^
//   |         object Chunk is marked @experimental: Added by -experimental
//   ...
// -- Error: Plain.scala:10:12
// 10 |    println(Schema[Int].jsonCodec.encodeToString(42))
//    |            ^^^^^^
//    |       object Schema is marked @experimental: Added by -experimental
//    ...
// -- Error: Plain.scala:10:23
// 10 |    println(Schema[Int].jsonCodec.encodeToString(42))
//    |                       ^
//    |       object Schema is marked @experimental: Added by -experimental
//    ...
// -- Error: Plain.scala:11:12
// 11 |    println(DynamicValue.Primitive(PrimitiveValue.Int(1)))
//    |            ^^^^^^^^^^^^
//    |     object DynamicValue is marked @experimental: Added by -experimental
//    ...
// -- Error: Plain.scala:11:35
//    |   object PrimitiveValue is marked @experimental: Added by -experimental
//    ...
// 5 errors found
// Compilation failed
```

With `-O -experimental` the second file prints `Chunk(1,2,3)`, `42` and `1`. The same two files against the published `0.0.56` on Scala 3.9.0 compile without the flag and print `{"title":"Zionomicon","year":2021}`, then `Chunk(1,2,3)`, `42` and `1`.

**Expected.** A Scala 3.9 user of `zio-blocks-*` compiles without `-experimental`, as with 0.0.56. Only the next line (`zio-blocks-next-*`, Scala 3.10+) needs the flag.

**Root cause.** `stdSettings` passed `-experimental` for every Scala 3 minor from 8 up (`project/BuildHelper.scala:232-234` at `4a142547`: `if (minor >= 8) { Seq("-experimental") ++ (if (minor >= 10) Seq.empty else Seq("-opt")) }`). That threshold dates from when 3.8+ was only the next line. #1743 moved the `zio-blocks-next-` rename to `minor >= 10` (`BuildHelper.scala:178`) but left `-experimental` at `minor >= 8`, so the 3.9 main line (and the Golem 3.8.3 build of streams and async) got it too. scalac 3.9.0 then adds `@experimental("Added by -experimental")` to every top-level definition of each compiled file (`Checking.checkAndAdaptExperimentalImports`, `dotty/tools/dotc/typer/Checking.scala:956-965` in `scala3-compiler_3-3.9.0`). No main-line source needs the flag: the whole build compiles on 3.9.0 without it, including the structural `Binding.of` macro, whose `Symbol.newClass` is no longer `@experimental` in scala-library 3.9.0.

**Impact.** Release blocker. The next release would break every Scala 3 user of the main line: every call into chunk, schema, typeid and the other main-line modules would fail to compile until the user added `-experimental`, which also opts their own code into experimental mode.

**Test gap.** Nothing compiled user code against the main line without `-experimental`. The in-build consumers (tests, `schema-scaladoc-check`) inherit the library's scalac options, `docs` adds `-experimental` itself (`build.sbt:1590`), and the examples builds use the published 0.0.56 and pass `-experimental` themselves.

**Fix.** Pass `-experimental` only for Scala 3.10+ (`-opt` stays on 3.8 and 3.9), and compile `schema-scaladoc-check`'s Scaladoc examples without `-experimental` or `-language:experimental.*` below 3.10 (`BuildHelper.Scala3NextMinor` holds the one threshold for the `zio-blocks-next-` rename, the flag and this check), so the build fails if a main-line module ever gets the flag again. On the unfixed rule, `++3.9.0; schema-scaladoc-check/compile` fails with 23 `... is marked @experimental: Added by -experimental` errors; with the fix it passes, and both files above compile without the flag. Fixed in #1826.

### typeid (Scala 3): `IsNominalType` and `IsNominalIntersection` accept an abstract type parameter, and the negative tests passed only because of `-experimental`

Found 2026-10-22 by CI on #1826 (the `-experimental` fix above), on `main` at `4a142547`. The code is the same at tag `v0.0.56`. Verified by running against the published 0.0.56 on Scala 3.3.7 and 3.9.0, and against local publishes of #1826 before and after the fix.

**Summary.** On Scala 3, `IsNominalType[A]` can be summoned for an unresolved type parameter `A`, which issue #1172 says must not compile. The derived `TypeId` then describes the parameter (`A`), not the type the caller passes. `IsNominalIntersection` accepts a type-parameter member the same way. Scala 2 rejects both. `IsNominalTypeNegativeSpec` checks exactly this, but passed only because the build compiled with `-experimental` (see Root cause); #1826 removed the flag and the spec failed.

**Reproduction** (Scala 3.9.0, scala-cli 1.10.1, `scala-cli run --server=false Probe.scala`):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-typeid:0.0.56
import zio.blocks.typeid.IsNominalType

def foo[A]: IsNominalType[A] = summon[IsNominalType[A]]

@main def run() = {
  println(foo[String].typeId)
  println(foo[String].typeId.fullName)
}
// A
// Probe$package.A
```

`//> using scala 3.3.7` prints the same.

**Expected.** `foo` does not compile. With the fix (a local publish of #1826 as `0.0.0-nominalfix`, plus `//> using repository ivy2Local`):

```
-- Error: Probe.scala:6:55
6 |def foo[A]: IsNominalType[A] = summon[IsNominalType[A]]
  |                                                       ^
  |Cannot derive IsNominalType for non-nominal type: A. Only classes, traits, objects, enums, and applied types are supported. Intersection types (A & B), union types (A | B), structural types, and type lambdas are not allowed.
1 error found
Compilation failed
```

**Root cause.** The Scala 3 macro's `isNominal` returns `true` for every `TypeRef` and every `TermRef` (`typeid/shared/src/main/scala-3/zio/blocks/typeid/IsNominalTypeVersionSpecific.scala:41-42`), and the `TypeRef` of a type parameter or an abstract type member is one of them; `F[Int]` with `F` a type parameter passes through `AppliedType` the same way. `IsNominalIntersectionVersionSpecific.scala:46-47` has a copy of the same function. The Scala 2 macro accepts a type only when `typeSymbol.isClass` or `isModuleClass` (`typeid/shared/src/main/scala-2/zio/blocks/typeid/IsNominalTypeVersionSpecific.scala:39-40`), so it rejects them.

The negative tests hid it. Each did `val compiles = typeChecks("""...""")` and `assertTrue(!compiles)` (`typeid/shared/src/test/scala-3.4+/zio/blocks/typeid/IsNominalTypeNegativeSpec.scala:39-56` at `4a142547`). With `-experimental`, the snippet failed for an unrelated reason: `typeCheckErrors` on the same snippet returns only `Recursive value errs needs type ... while trying to compute the signature of value errs` (a cyclic reference on the untyped `val` that holds the result). So `typeChecks` returned `false` and the tests passed while the macro accepted the type. The third test, whose snippet expands no `IsNominalType` macro, passed either way.

**Impact.** The #1172 guarantee does not hold on Scala 3: a DSL method `def contains[A: IsNominalType](a: A)` can be called from unconstrained generic code, and gets a `TypeId` for the type parameter instead of the real type. Any code that relies on `IsNominalType` to rule out abstract types, as the #1172 DSL pattern does, is affected. An applied type with an abstract argument is still accepted on both Scala 2 and 3, before and after this fix: `IsNominalType[List[A]]` derives, so `def f[A](xs: List[A]) = contains(xs)` still compiles.

**Test gap.** The negative tests asserted only `!typeChecks(...)`, which passes for any compile error, including ones unrelated to the macro. No test covered abstract type members, applied type parameters or `IsNominalIntersection` with a type-parameter member.

**Fix.** In #1826: `isNominal` accepts a `TypeRef` only when its symbol is a class, trait, object or enum (`isClassDef`) or an opaque type, follows aliases, and widens and dealiases a `TermRef` (`x.type` is nominal when `x`'s type is, so `pair.type` for `val pair: Pair[Int]` with `type Pair[X] = (X, X)` still works). Both macros share it. Two differences from Scala 2 remain: `x.type` for a value of an intersection type (`x: A & B`) is rejected on Scala 3 and accepted on Scala 2, and `lit.type` for `final val lit = 1` is accepted on Scala 3 and rejected on Scala 2 (as before this fix). The negative tests now assert the macro's own error message with `typeCheckErrors` (with an explicit `List[Error]` type, which also avoids the cyclic reference on 3.10, where `-experimental` is still on), and cover abstract type members, applied type parameters, the singleton type of a parameter's value and `IsNominalIntersection`, plus Scala 3 positives for an opaque type, an alias, an object singleton and the singleton types of a `Pair[Int]` and a `String` value. A shared `IsNominalTypeSpec` test checks the bare type parameter on Scala 2 and 3.
