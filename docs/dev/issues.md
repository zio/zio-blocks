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
- **A nested `connect` inside `transact` takes a second pooled connection.** With a small pool it times out: `SQLTransientConnectionException: bookstore - Connection is not available, request timed out after 1001ms (total=1, active=1, idle=0, waiting=0)`. Pass the `DbCon`/`DbTx` down instead of calling `connect` again. Verified against PostgreSQL 16 while writing H-17; the SQLite counterpart (`SQLITE_BUSY`) is above.
- **`$` only accepts a method call on its receiver.** `$(transactor)(t => restockShelf(t))` fails with "cannot be passed as an argument to a function or method", so helpers must take `using DbCon` and be called as `$(transactor)(_.transact(work()))`. Verified while writing H-17.
- **Scala 3 `object X extends App` deadlocks when worker threads touch the object.** Code in the `App` body runs inside the object's static initializer, so any other thread that reads a member of the object waits on the class-init lock the main thread holds. Hit in H-17's concurrency example and in T-06 (the streams deadlock above); both use `def main` instead. Worth a general note in AGENTS.md and the examples templates.
- **Scope and Wire error codes never appear in compiler output.** `ErrorMessages.scala` numbers them E1-E17, RE1-RE3 and W1, but the messages users see carry no code, so docs must index errors by message text. Found while writing H-31.
- **`defer` on a closed scope is a silent no-op** (`Scope.scala:202`), while `allocate` and `$` throw. Found while writing H-31.
- **`jdbc:sqlite::memory:` gives every connection its own empty database.** Code that opens more than one connection loses its tables. Found while writing T-07; now noted in `transactor.md`.

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

## Test and CI

### Flaky streams test: `unfoldAsync - reset invalidates a stale completion and restarts from initial state`

Seen 2026-10-20 on PR #1773 (docs only, touches nothing in `streams/` or `async/`). In `testJVM (25, JVM, 3.3.x)`, this test hit its 30 s timeout while the other 12,760 tests passed. Rerunning is the current workaround. Needs investigation: a timing dependency in the test, or a real race in `unfoldAsync` reset.

Seen again 2026-10-20 on PR #1774 (H-12, docs only): an async-reader test hit the same 30 s timeout in `testJVM (17, JVM, 3.9.x)`. Later pushes replaced that run. Two sightings on unrelated docs PRs in one day suggest the timeouts are too tight for loaded CI runners.

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
- `docs/dev/research.md` cites stale line numbers (for example `Transactor.scala:65` is now `:261`/`:275`, `Table.scala:32` is now `:262`).

### docs: the streams overview marks `fromInputStream` and `fromJavaReader` as JVM-only, but they also run on Scala.js

Found 2026-10-20 while writing the how-to guide `docs/guides/stream-files-and-sockets.md` (backlog H-24), on `main` at `b4179489`. The same text is in tag `v0.0.56`; it dates from #1318.

**Summary.** The "Creating Streams" block of `docs/reference/streams/index.md` labels both groups of I/O constructors "JVM only": `Stream.fromInputStream`, `Stream.fromJavaReader`, `Stream.fromInputStreamUnmanaged` and `Stream.fromJavaReaderUnmanaged`. All four are defined in the shared sources and work on Scala.js; only their blocking terminals (`runCollect`, `run`, …) and the NIO helpers (`NioStreams`, `NioSinks`) are JVM-only.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-streams` 0.0.56 for Scala.js, run with `scala-cli run Repro.scala --js-cli-on-jvm` — the default native Scala.js linker of scala-cli 1.10 only reads IR up to 1.20, and the 0.0.56 artifacts are IR 1.22):

```scala
//> using scala 3.9.0
//> using platform scala-js
//> using jsVersion 1.22.0
//> using option -experimental
//> using dep dev.zio::zio-blocks-next-streams::0.0.56

import zio.blocks.async._
import zio.blocks.streams._

import java.io.{ByteArrayInputStream, StringReader}
import scala.concurrent.ExecutionContext.Implicits.global

object Repro {
  def main(args: Array[String]): Unit = {
    val bytes = new ByteArrayInputStream("Zionomicon".getBytes("UTF-8"))
    Stream.fromInputStream(bytes).map(_.toChar).runCollectAsync.toFuture.foreach(println)

    val chars = new StringReader("Programming in Scala")
    Stream.fromJavaReader(chars).runCollectAsync.toFuture.foreach(r => println(r.map(_.mkString)))
  }
}
// Right(Chunk(Z,i,o,n,o,m,i,c,o,n))
// Right(Programming in Scala)
```

**Expected.** The overview says these constructors are available on both platforms, as `docs/reference/streams/core/stream.md` already implies (it lists them without a platform note).

**Root cause.** Documentation only. `docs/reference/streams/index.md:398` and `:402` say "JVM only", but the four constructors live in `streams/shared/src/main/scala/zio/blocks/streams/Stream.scala:2997`, `:3010`, `:3067` and `:3080`, and the shared spec `streams/shared/src/test/scala/zio/blocks/streams/NewCombinatorsSpec.scala:612-630` runs them on both platforms.

**Impact.** Low. A Scala.js user reading the overview concludes they cannot stream a `java.io.InputStream` or `Reader` and writes the adapter by hand.

**Test gap.** None in the library; the shared spec covers both platforms. Nothing checks platform labels in the docs against the source tree.

**Suggested fix.** Drop "- JVM only" from both comments at `docs/reference/streams/index.md:398` and `:402`.

Fixed in #1796 (H-24).

## Resolved

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
