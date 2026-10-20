# ZIO Blocks: library issues found while writing docs

Internal list of library bugs found while writing documentation. Each entry is written so it can be filed on GitHub as is. When an entry is filed, add the issue link under its heading.

## `Schema.fromJsonSchema` treats every property as required, ignoring `"required"`

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

## `Resource.from` never reports a dependency cycle (E10); the macro crashes instead

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

## A dependency on a Java class with constructor parameters crashes the `Resource.from` / `Wire.shared` macro

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

## Duplicate constructor parameter types (E12) are never reported in Scala 3; both parameters get the same instance

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

## The E3 subtype-conflict hint suggests code that isn't valid Scala

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

## Four Scope error codes can't be reached, and `renderMissingDependency` is dead code

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

## A failing `scoped` body adds a confusing "Ambiguous given instances" error

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

## Scope tutorial, reference pages and example contradict the source

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

## mdoc: `mdoc:fail` and `mdoc:warn` also compile earlier `compile-only` blocks

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
