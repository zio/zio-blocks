---
id: fix-scope-and-wire-compile-errors
title: "How to Fix Common Scope and Wire Compile Errors"
description: "Look up a Scope or Wire compile error by its message, see why the macro rejected your code, and apply the fix."
keywords:
  - "Compile Errors"
  - "Macro Error Messages"
  - "Dependency Injection"
  - "Resource Safety"
  - "Wire"
  - "Unscoped"
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This guide is a lookup table for the errors that `Scope`, `Resource.from` and `Wire` report. Find your error message in the [Quick Index](#quick-index), jump to its entry, and leave with a fix. Each entry shows the smallest code that triggers the error, the real compiler output (rendered by mdoc, not pasted), one or two sentences on why it happens, and the corrected code.

The entries cover:

- the errors you will most likely hit first: escaping `$[A]` values, the `$` receiver rule, dependency cycles, duplicate providers, and dependencies the macro cannot build;
- every remaining code from E1 to E17, in code order;
- the leak warning (W1) and the closed-scope runtime errors (RE1 to RE3);
- the differences between the Scala 2 and Scala 3 macros.

## The Problem

`Scope` checks resource safety at compile time, and `Resource.from` resolves a whole dependency graph at compile time. That moves mistakes from production into the compiler, but it also means the first thing you see is a macro error. A message such as `No given instance of type Unscoped[Scope.Child[Scope.global.type]#$[Db]]` names the problem precisely, yet it doesn't tell you what to change. A large, unfamiliar error is the point where many people give up on a library.

Every Scope error has a known cause and a short fix. This page maps each message to both.

## Prerequisites

Add the module to your build:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-scope" % "@VERSION@"
```

Every example on this page starts with this import:

```scala
import zio.blocks.scope.*
```

You should know what `scoped`, `allocate`, `$` and `Resource.from` do. If you don't, read the [Compile-Time Resource Safety with Scope](./compile-time-resource-safety-with-scope.md) tutorial first; this page doesn't re-explain those concepts.

## Reading a Scope Error

Most macro errors come in a box that starts with `── Scope Error ──` and ends with a rule line. The first line inside the box says what went wrong, and the `Hint:` or `Fix:` line says what to do.

The codes on this page (E1, E10, RE2 and so on) come from the library source, `ErrorMessages.scala`. **The compiler output doesn't print them**, so match on the first line of the message, not on a code.

Three more things to know before you search:

- **Colors.** In a terminal the boxes are colored. Set the `NO_COLOR` environment variable to any non-empty value, or set the system property `-Dsbt.log.noformat=true`, to get plain text. The check runs inside the compiler's JVM.
- **Type names in this page.** mdoc compiles each snippet inside a generated class, so types appear as `MdocApp3.this.Catalog`. In your project you will see your own package, such as `com.example.Catalog`.
- **Some errors have no code.** Escaping a scope and breaking the `$` receiver rule come from the compiler's implicit search and from the `$` macro, not from `ErrorMessages`. They are listed first because they are the most common.

## Quick Index

| Code | The message starts with | Usual fix |
|------|-------------------------|-----------|
| none | `No given instance of type zio.blocks.scope.Unscoped[...]` | [Return plain data, not the scoped value](#escaping-scoped-value) |
| none | `Parameter 1 ('d') must only be used as a method receiver` | [Call a method on the parameter](#dollar-receiver-rule) |
| none | `value ... is not a member of scope.$[...]` | [Go through `$`](#method-on-scoped-value) |
| E10 | *(today: `Exception occurred while executing macro expansion` with `key not found`)* | [Break the cycle](#e10-dependency-cycle) |
| E11 | `Multiple providers for ...` | [Pass exactly one wire per type](#e11-multiple-providers) |
| E8 | `Cannot auto-create ...` / `This type is abstract` | [Wire a concrete implementation](#e8-abstract-type) |
| E7 | `Cannot auto-create ...` / `This type (primitive, collection, or function)` | [Provide the value with `Wire(value)`](#e7-unmakeable-type) |
| E9 | `Cannot auto-create ...` / `This type has no primary constructor` | [Provide an explicit wire](#e9-no-constructor) |
| E4 | `Resource.from[...] cannot be derived.` | [Pass wires, or call `Resource.from[T]()`](#e4-has-dependencies) |
| E1 | `Cannot derive Wire for ...: not a class.` | [Derive the concrete class](#e1-not-a-class) |
| E2 | `... has no primary constructor.` | [Write the wire by hand](#e2-no-primary-constructor) |
| E3 | `Dependency type conflict in ...` | [Wrap one of the types](#e3-subtype-conflict) |
| E5 | `Unsupported implicit parameter in ...` | [Make the parameter explicit](#e5-unsupported-implicit) |
| E6 | `Cannot extract types from wire expression.` | [Pass a real wire](#e6-cannot-extract-wire-types) |
| E12 | `Constructor of ... has multiple parameters of type ...` | [Wrap one of the parameters](#e12-duplicate-parameter-type) |
| E13 | `Expected varargs of Wire[?, ?] expressions.` | [List the wires inline](#e13-invalid-varargs) |
| E14-E17 | `Cannot derive Unscoped for ...` (Scala 2 only) | [Fix the type or write the instance](#e14-e17-unscoped-derivation) |
| W1 | `── Scope Warning ──` / `... is being leaked from scope` | [Stay inside `$`, or add `Unscoped`](#w1-leak-warning) |
| RE1 | `IllegalStateException`: `Cannot allocate resource: scope is already closed.` | [Allocate while the scope is open](#re1-allocate-on-closed-scope) |
| RE2 | `IllegalStateException`: `Cannot open child scope: scope is already closed.` | [Open while the parent is open](#re2-open-on-closed-scope) |
| RE3 | `IllegalStateException`: `Cannot access scoped value: scope is already closed.` | [Use `$` inside the owning scope](#re3-use-on-closed-scope) |

## The Most Common Errors

### Returning a Scoped Value from `scoped` {#escaping-scoped-value}

A `scoped` block may return a value only if its type has an `Unscoped` instance. A resource you allocated has type `$[Db]`, which has none:

```scala mdoc:silent:reset
import zio.blocks.scope.*

class Db extends AutoCloseable {
  def countBooks(): Int = 42
  def close(): Unit     = ()
}
```

```scala mdoc:fail
val db = Scope.global.scoped { scope =>
  import scope.*
  allocate(Resource(new Db))
}
```

The block's resources are closed when it exits, so returning `db` would hand out a closed connection. The `Mirror` lines in the message are the compiler explaining that it also tried, and failed, to derive an instance.

Return plain data computed from the resource instead:

```scala mdoc
val count: Int = Scope.global.scoped { scope =>
  import scope.*
  val db = allocate(Resource(new Db))
  $(db)(_.countBooks())
}
println(count)
```

The same error appears when you return your own type and it has no `Unscoped` instance:

```scala mdoc:silent:reset
import zio.blocks.scope.*

class Book(val title: String)
```

```scala mdoc:fail
val book = Scope.global.scoped { _ => new Book("Zionomicon") }
```

`Unscoped` instances exist for primitives, `String`, collections, `Option`, `Either` and tuples of up to four elements, as long as every element type is itself `Unscoped`. For your own data, declare an instance. In Scala 3, case classes, sealed traits and enums whose fields are all `Unscoped` get one automatically. In Scala 2 you derive it explicitly:

<Tabs groupId="scala-version" defaultValue="scala2">
<TabItem value="scala2" label="Scala 2">

```scala
import zio.blocks.scope._

final case class Book(title: String)
object Book {
  implicit val unscoped: Unscoped[Book] = Unscoped.derived[Book]
}

val book: Book = Scope.global.scoped { _ => Book("Zionomicon") }
```

</TabItem>
<TabItem value="scala3" label="Scala 3">

```scala mdoc:silent:reset
import zio.blocks.scope.*

final case class Book(title: String) // Unscoped is derived automatically

val book: Book = Scope.global.scoped { _ => Book("Zionomicon") }
```

</TabItem>
</Tabs>

For a class that isn't a case class, write the instance by hand: `given Unscoped[Book] = new Unscoped[Book] {}` in Scala 3, or `implicit val unscopedBook: Unscoped[Book] = new Unscoped[Book] {}` in Scala 2. Only do this for pure data, never for a type that holds a resource.

If you return a case class that has a resource field, the error names the field's type, not the case class:

```scala mdoc:silent:reset
import zio.blocks.scope.*

class Db extends AutoCloseable { def close(): Unit = () }
case class Receipt(db: Db)
```

```scala mdoc:fail
val receipt = Scope.global.scoped { _ => Receipt(new Db) }
```

Derivation needs an `Unscoped` instance for every field, and `Db` has none. Keep the resource out of the returned type: put only plain data in `Receipt`.

### Using a `$` Parameter as a Value {#dollar-receiver-rule}

Inside `$(db)(d => ...)`, the parameter `d` may only be the receiver of a method call. Returning it, storing it, passing it to a function or capturing it in a closure is rejected:

```scala mdoc:silent:reset
import zio.blocks.scope.*

class Db extends AutoCloseable {
  def countBooks(): Int = 42
  def close(): Unit     = ()
}

def describe(db: Db): String = s"${db.countBooks()} books"
```

```scala mdoc:fail
Scope.global.scoped { scope =>
  import scope.*
  val db = allocate(Resource(new Db))
  $(db)(d => d)
  ()
}
```

```scala mdoc:fail
Scope.global.scoped { scope =>
  import scope.*
  val db = allocate(Resource(new Db))
  $(db)(describe)
}
```

Any of those uses would let the raw resource outlive the `$` call, and with it the scope. Passing a method reference such as `describe` counts as passing the parameter itself as an argument.

Call methods on the parameter, and pass their results along instead:

```scala mdoc:compile-only
def describeCount(count: Int): String = s"$count books"

Scope.global.scoped { scope =>
  import scope.*
  val db = allocate(Resource(new Db))
  $(db)(d => describeCount(d.countBooks()))
}
```

The [Scope reference](../reference/resource-management/scope.md#compile-errors) lists every form the `$` macro rejects.

### Calling a Method Directly on `$[A]` {#method-on-scoped-value}

A value of type `$[Db]` has none of `Db`'s methods:

```scala mdoc:fail
Scope.global.scoped { scope =>
  import scope.*
  val db = allocate(Resource(new Db))
  db.countBooks()
  ()
}
```

`$[A]` is an opaque wrapper, so the compiler can see when you use the resource. Go through `$`:

```scala mdoc:compile-only
Scope.global.scoped { scope =>
  import scope.*
  val db = allocate(Resource(new Db))
  $(db)(_.countBooks())
}
```

### E10: Dependency Cycle Detected {#e10-dependency-cycle}

Two services that need each other can't both be built first:

```scala mdoc:silent:reset
import zio.blocks.scope.*

class InventoryRepo(prices: PriceService)
class PriceService(inventory: InventoryRepo)
class Catalog(inventory: InventoryRepo)
```

```scala
Resource.from[Catalog]()
```

`ErrorMessages` defines an E10 message with a cycle diagram, but in the current release `Resource.from` never reaches it. A cycle crashes the macro with `Exception occurred while executing macro expansion.` followed by `java.util.NoSuchElementException: key not found:` and the name of a type in the cycle, plus a long stack trace ([zio-blocks#1767](https://github.com/zio/zio-blocks/issues/1767)). If you see that exception from `Resource.from`, look for a cycle.

Break the cycle by restructuring the dependencies. Here both services only needed shared data, so they now depend on a `Db` instead of on each other:

```scala mdoc:silent:reset
import zio.blocks.scope.*

class Db
class PriceService(db: Db)
class InventoryRepo(db: Db, prices: PriceService)
class Catalog(inventory: InventoryRepo)
```

```scala mdoc:compile-only
Resource.from[Catalog]()
```

You can also break a cycle by putting a trait between the two classes and wiring an implementation that doesn't depend back on the first one.

### E11: Multiple Providers for a Type {#e11-multiple-providers}

`Resource.from` matches each dependency against the wires you pass, subtypes included. Two wires that both provide `PriceService` are ambiguous:

```scala mdoc:silent:reset
import zio.blocks.scope.*

trait PriceService
class FixedPrices    extends PriceService
class DiscountPrices extends PriceService
class Catalog(prices: PriceService)
```

```scala mdoc:fail
Resource.from[Catalog](Wire.shared[FixedPrices], Wire.shared[DiscountPrices])
```

The macro can't guess which implementation `Catalog` should get. Pass exactly one wire for each dependency type:

```scala mdoc:compile-only
Resource.from[Catalog](Wire.shared[FixedPrices])
```

If you really need both implementations in one graph, give them distinct types, for example wrapper case classes, and make the constructors ask for those types.

### E8: Abstract Type Cannot Be Auto-Created {#e8-abstract-type}

`Resource.from` builds every dependency you don't provide a wire for, but it can't instantiate a trait or an abstract class:

```scala mdoc:silent:reset
import zio.blocks.scope.*

trait PriceService
class FixedPrices extends PriceService
class Catalog(prices: PriceService)
```

```scala mdoc:fail
Resource.from[Catalog]()
```

The `Required by:` tree shows which constructor asked for the abstract type. Wire a concrete implementation:

```scala mdoc:compile-only
Resource.from[Catalog](Wire.shared[FixedPrices])
```

### E7: Primitive, Collection, or Function Cannot Be Auto-Created {#e7-unmakeable-type}

The macro never invents values for `String`, the numeric types, `Boolean`, `Unit`, `List`, `Seq`, `Set`, `Map`, `Option` or function types:

```scala mdoc:silent:reset
import zio.blocks.scope.*

class Db(url: String)
class Catalog(db: Db)
```

```scala mdoc:fail
Resource.from[Catalog]()
```

There is no sensible default for a database URL. Provide the value with `Wire(value)`:

```scala mdoc:compile-only
Resource.from[Catalog](Wire("jdbc:h2:mem:bookstore"))
```

If a graph needs two values of the same primitive type, wrap each in its own case class (for example `final case class DbUrl(value: String)`), because the graph has one slot per type.

### E9: No Constructor for Auto-Create {#e9-no-constructor}

This message means a dependency is a concrete class with no primary constructor. Every Scala class has one, so this guide found no way to trigger E9 from Scala code. If you do see it, provide an explicit wire for that type, as the message's `Fix:` line shows.

### E4: `Resource.from[T]` Has Dependencies {#e4-has-dependencies}

`Resource.from[T]` without an argument list only works for types whose constructor takes nothing, or takes only a `Finalizer` or `Scope`:

```scala mdoc:silent:reset
import zio.blocks.scope.*

class Db extends AutoCloseable { def close(): Unit = () }
class Catalog(db: Db)
```

```scala mdoc:fail
Resource.from[Catalog]
```

That form doesn't resolve a dependency graph. Use the wires form: pass wires for the dependencies, or an empty argument list to let the macro build every dependency it can:

```scala mdoc:compile-only
val explicitDb = Resource.from[Catalog](Wire.shared[Db])
val autoDb     = Resource.from[Catalog]()
```

Note the difference between `Resource.from[Catalog]` and `Resource.from[Catalog]()`: only the second one resolves dependencies.

## The Remaining Compile Errors

### E1: Not a Class {#e1-not-a-class}

`Wire.shared[T]`, `Wire.unique[T]` and `Resource.from[T]` call `T`'s constructor, so `T` must be a concrete class:

```scala mdoc:silent:reset
import zio.blocks.scope.*

trait PriceService
class FixedPrices extends PriceService
```

```scala mdoc:fail
Wire.shared[PriceService]
```

A trait or an abstract class has no constructor to call. Derive the concrete class; the wire still provides `PriceService` to anything that needs one:

```scala mdoc:compile-only
val prices = Wire.shared[FixedPrices]
```

If you need construction logic the macro can't derive, build the wire by hand with `Wire.Shared` or `Wire.Unique`, as the hint says.

### E2: No Primary Constructor {#e2-no-primary-constructor}

Like E9, this message needs a class without a primary constructor, and every Scala class has one, so this guide found no Scala trigger for it. The fix is to write the wire by hand with `Wire.Shared` or `Wire.Unique`.

### E3: Subtype Conflict Between Dependencies {#e3-subtype-conflict}

A constructor can't take two dependencies where one type is a subtype of the other:

```scala mdoc:silent:reset
import zio.blocks.scope.*

class InventoryRepo
class SqlInventoryRepo extends InventoryRepo
class Catalog(inventory: InventoryRepo, sql: SqlInventoryRepo)
```

```scala mdoc:fail
Wire.shared[Catalog]
```

The dependency graph is indexed by type, so a request for `InventoryRepo` could be answered with the `SqlInventoryRepo`. Wrap one of the two types so they're unrelated. The code in the message's hint isn't valid Scala when the type has a package prefix, so write the wrapper yourself:

```scala mdoc:silent:reset
import zio.blocks.scope.*

class InventoryRepo
class SqlInventoryRepo extends InventoryRepo
final case class ReadReplica(repo: SqlInventoryRepo)
class Catalog(inventory: InventoryRepo, replica: ReadReplica)
```

```scala mdoc:compile-only
val catalog = Wire.shared[Catalog]
```

### E5: Unsupported Implicit Parameter {#e5-unsupported-implicit}

This message targets a `Resource.from[T]` constructor with an implicit parameter other than `Finalizer` or `Scope`. Both macros check for dependencies (E4) first, and an implicit `Clock` parameter is reported as an E4 dependency, so E5 isn't reachable from user code. Follow the E4 fix: make the parameter explicit and provide it with a wire.

### E6: Cannot Extract Wire Types {#e6-cannot-extract-wire-types}

Every real wire has the type `Wire[In, Out]`, so `Resource.from` reports E6 only when an argument has some other static type, such as `???` (type `Nothing`). Pass real `Wire` values.

### E12: Duplicate Parameter Type {#e12-duplicate-parameter-type}

This message is for a constructor with two parameters of the same type. In Scala 3 it isn't reported: the code compiles, and both parameters receive the same instance:

```scala mdoc:silent:reset
import zio.blocks.scope.*

class Db
class Catalog(val primary: Db, val replica: Db)
```

```scala mdoc
val sameDb: Boolean = Scope.global.scoped { scope =>
  import scope.*
  val catalog = allocate(Resource.from[Catalog]())
  $(catalog)(c => c.primary.eq(c.replica))
}
println(sameDb)
```

The graph holds one value per type, so it can't supply two different `Db` values. If the two parameters must differ, give each a distinct type:

```scala mdoc:silent:reset
import zio.blocks.scope.*

class Db
final case class PrimaryDb(db: Db)
final case class ReplicaDb(db: Db)
class Catalog(primary: PrimaryDb, replica: ReplicaDb)
```

```scala mdoc:compile-only
Resource.from[Catalog](
  Wire(PrimaryDb(new Db)),
  Wire(ReplicaDb(new Db))
)
```

### E13: Invalid Varargs {#e13-invalid-varargs}

`Resource.from[T](wires*)` reads its arguments at compile time, so it needs them written out at the call site:

```scala mdoc:silent:reset
import zio.blocks.scope.*

class Db
class Catalog(db: Db)
```

```scala mdoc:fail
val wires = Seq(Wire.shared[Db])
Resource.from[Catalog](wires*)
```

A `Seq` only exists at run time, so the macro can't see which wires it holds. List the wires inline:

```scala mdoc:compile-only
Resource.from[Catalog](Wire.shared[Db])
```

E13 is Scala 3 only.

### E14 to E17: `Unscoped` Derivation Errors (Scala 2 Only) {#e14-e17-unscoped-derivation}

These four errors come from Scala 2's `Unscoped.derived[A]` macro, which you call yourself (see the Scala 2 tab in [Returning a Scoped Value](#escaping-scoped-value)). All of them start with `Cannot derive Unscoped for`:

- **E14, type parameter not deducible.** A subtype of a sealed generic type has a type parameter the macro can't work out from the parent's type arguments. Give the subtype concrete type arguments, or simplify the hierarchy.
- **E15, sealed type with no known subclasses.** Define at least one subclass in the same file as the sealed type.
- **E16, no `Unscoped` instance for a field or subtype.** For example, `Unscoped.derived[Receipt]` where `Receipt` has a `Db` field. Remove the resource from the type, or give the field's type its own instance if it's pure data.
- **E17, no primary constructor.** Write the instance by hand: `implicit val unscoped: Unscoped[A] = new Unscoped[A] {}`.

Scala 3 doesn't use these codes. Its `Unscoped` derivation is a `given` built on `Mirror`, so the same mistakes produce the compiler's standard `No given instance of type ...Unscoped[...]` message, as in [Returning a Scoped Value](#escaping-scoped-value).

## Warnings

### W1: Value Leaked from Its Scope {#w1-leak-warning}

`leak` unwraps a `$[A]` into a plain `A`, which turns off the compile-time checks for that value. The macro warns every time:

```scala mdoc:silent:reset
import zio.blocks.scope.*

class Db extends AutoCloseable {
  def countBooks(): Int = 42
  def close(): Unit     = ()
}
```

```scala mdoc:warn
Scope.global.scoped { scope =>
  import scope.*
  val db      = allocate(Resource(new Db))
  val raw: Db = leak(db)
  ()
}
```

After the block exits, `raw` points at a closed resource. Keep the work inside `$`:

```scala mdoc:compile-only
Scope.global.scoped { scope =>
  import scope.*
  val db = allocate(Resource(new Db))
  $(db)(_.countBooks())
}
```

Use `leak` only to hand a resource to a library that can't accept `$[A]`, and keep that library call inside the block. If the leaked type is pure data, give it an `Unscoped` instance instead: `$` then returns it unwrapped and nothing has to leak.

## Runtime Errors

These errors are `IllegalStateException`s, thrown when code uses a scope after it has closed. The compiler can't catch them when a scope reference outlives its block, for example through `open()` and `close()`, a field, or another thread.

### RE1: Allocate on a Closed Scope {#re1-allocate-on-closed-scope}

```scala mdoc:crash
val shop1   = Scope.global.open()
val closed1 = shop1.scope
shop1.close()
closed1.allocate(Resource(new Db))
```

`close()` already ran the scope's finalizers, so a new resource would never be released. Allocate before you close the scope.

### RE2: Open a Child of a Closed Scope {#re2-open-on-closed-scope}

```scala mdoc:crash
val shop2   = Scope.global.open()
val closed2 = shop2.scope
shop2.close()
closed2.open()
```

A child scope is linked to its parent's finalizers, and a closed parent has none left to link to. Open children while the parent is still open.

### RE3: Use `$` on a Closed Scope {#re3-use-on-closed-scope}

```scala mdoc:crash
val shop3   = Scope.global.open()
val closed3 = shop3.scope
val db3     = closed3.allocate(Resource(new Db))
shop3.close()
(closed3 $ db3)(_.countBooks())
```

The value behind `db` has already been released. Make every `$` call before the owning scope closes.

`defer` on a closed scope is not an error: the finalizer is ignored and you get a no-op handle back. Calling `scoped` from a thread that doesn't own the scope throws a different `IllegalStateException`, `Cannot create child scope: current thread '...' does not own this scope`. The tutorial's [Thread Ownership](./compile-time-resource-safety-with-scope.md#11-thread-ownership) section covers that case.

## Scala 2 and Scala 3 Differences

Both macro implementations render their errors with the same `ErrorMessages` functions, so the boxes read the same in both versions. The differences:

- **E13** is reported only by Scala 3.
- **E14 to E17** are reported only by Scala 2, by `Unscoped.derived`. Scala 3 reports the same mistakes with the compiler's standard `No given instance` message.
- **Escaping a scope** is a missing implicit in both versions, but Scala 2 uses its own wording for a missing implicit.
- **Type names** are printed with `toString` in Scala 2 and with `show` in Scala 3, so some types are formatted slightly differently.

mdoc compiles this page with Scala 3, so every rendered message above is Scala 3 output.

## Putting It Together

This bookstore graph compiles cleanly. It provides the database URL with `Wire(value)` (E7), wires a concrete `PriceService` (E8), avoids a cycle between `InventoryRepo` and `PriceService` (E10), and returns a case class instead of a scoped value:

```scala mdoc:embed:scope-examples/src/main/scala/scope/examples/compileerrors/BookstoreWiringExample.scala
```

## Running the Examples

Clone the repository and move into the examples module:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/scope-examples
```

<details>
<summary><strong>A bookstore graph with every fix applied</strong></summary>

```scala mdoc:embed:scope-examples/src/main/scala/scope/examples/compileerrors/BookstoreWiringExample.scala:show-line-numbers
```

Run it with:

```bash
sbt "runMain scope.examples.compileerrors.bookstoreWiringExample"
```

</details>

To compile the example without running it:

```bash
sbt compile
```

## See Also

- [Compile-Time Resource Safety with Scope](./compile-time-resource-safety-with-scope.md): the tutorial that introduces every concept this page assumes.
- [Scope reference](../reference/resource-management/scope.md): `scoped`, `allocate`, `$`, `open` and `leak` in detail.
- [Wire reference](../reference/resource-management/wire.md): `Wire.shared`, `Wire.unique`, `Wire(value)` and macro derivation.
- [Resource reference](../reference/resource-management/resource.md): `Resource.from[T]` and `Resource.from[T](wires*)`.
- [Unscoped reference](../reference/resource-management/unscoped.md): the built-in instances and how to declare your own.
