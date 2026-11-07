---
id: resource-and-di-migration
title: "Coming from cats-effect Resource, ZIO Scope/ZLayer, macwire or Using"
description: "Move resource handling and dependency wiring from cats-effect, ZIO, macwire or scala.util.Using to ZIO Blocks Scope, Resource and Wire."
keywords:
  - "Resource Management"
  - "Dependency Injection"
  - "Migration"
  - "Scope"
  - "Resource"
  - "Wire"
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This guide moves a small bookstore service from cats-effect `Resource`, ZIO `Scope` and `ZLayer`, macwire, or `scala.util.Using` to the ZIO Blocks [`scope` module](../reference/resource-management/index.md). By the end, the service's database connection and export file are opened, shared and closed by `Scope`, `Resource` and `Wire`, and the compiler rejects any attempt to use them after they close. Each source library gets its own section with the same three parts: the code you wrote, the code you write now, and what changes. Read the concept map, then jump to the section for the library you are leaving.

- [Concept Map](#concept-map)
- [Coming from cats-effect `Resource`](#coming-from-cats-effect-resource)
- [Coming from ZIO `Scope` and `ZLayer`](#coming-from-zio-scope-and-zlayer)
- [Coming from macwire](#coming-from-macwire)
- [Coming from `scala.util.Using`](#coming-from-scalautilusing)
- [What You Gain and What Is Different](#what-you-gain-and-what-is-different)
- [Migration Checklist](#migration-checklist)

## The Problem

All four libraries close resources for you, but none of them stops you from keeping a resource after it closes. A value returned from `use`, `ZIO.scoped` or `Using.resource` compiles fine and fails only when someone calls it. On top of that, lifecycle and wiring live in different places: macwire builds the object graph but closes nothing, `Using` closes things but builds no graph, and the effect systems need an effect type around every constructor.

Here is the leak with `scala.util.Using`, which needs nothing outside the standard library:

```scala mdoc:compile-only
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.Using

final class Connection extends AutoCloseable {
  private val open = new AtomicBoolean(true)
  def query(sql: String): String = {
    require(open.get, "connection is closed")
    s"rows for $sql"
  }
  def close(): Unit = open.set(false)
}

// Compiles: hands back a connection that is already closed
val leaked: Connection = Using.resource(new Connection)(identity)
```

The same shape compiles with `db.use(IO.pure)` in cats-effect and with `ZIO.scoped(ZIO.fromAutoCloseable(...))` returning the resource in ZIO. Every one of them turns into a runtime failure later.

ZIO Blocks Scope ties each resource to a scope with a type, `$[A]`, and lets a value leave the scope only when it has an `Unscoped` instance, which resources do not. `Resource.from` wires a graph from constructors and closes every `AutoCloseable` in it, in reverse order, when the scope closes.

## Prerequisites

Add the `scope` module:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-scope" % "@VERSION@"
```

For Scala.js, use `%%%`. The module supports Scala 2.13 and 3.x. It has no third-party dependencies (it pulls in only the ZIO Blocks `context` and `chunk` modules) and does not depend on cats-effect, ZIO or any other effect library.

Every ZIO Blocks snippet below starts from this import:

```scala mdoc:silent
import zio.blocks.scope._
```

This guide assumes you already know one of the four source libraries. It maps their concepts onto ZIO Blocks rather than teaching `Scope` from scratch, so if `Scope#scoped`, `Scope#allocate` or `Scope#$` are new to you, work through [Compile-Time Resource Safety with Scope](./compile-time-resource-safety-with-scope.md) first.

## The Bookstore Model

The running example is a bookstore service with this graph:

```text
DbConfig ──► Db ──┬──► InventoryRepo ──┐
                  └──► PriceService ───┴──► Catalog

ExportFile (a second, independent resource)
```

`Db` is an `AutoCloseable` connection that both `InventoryRepo` and `PriceService` share. `Catalog` sits on top. `ExportFile` is a temporary CSV file that the service writes to and deletes when it is done. Each resource prints a line when it opens and closes, so the order is visible:

```scala mdoc:silent
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

final case class DbConfig(url: String)

final class Db(config: DbConfig) extends AutoCloseable {
  println(s"Db: connected to ${config.url}")

  private val stock: Map[String, Int] = Map(
    "Zionomicon"                      -> 3,
    "Programming in Scala"            -> 7,
    "Functional Programming in Scala" -> 0
  )

  private val prices: Map[String, BigDecimal] = Map(
    "Zionomicon"                      -> BigDecimal("49.99"),
    "Programming in Scala"            -> BigDecimal("59.95"),
    "Functional Programming in Scala" -> BigDecimal("44.99")
  )

  def stockOf(title: String): Int        = stock.getOrElse(title, 0)
  def priceOf(title: String): BigDecimal = prices.getOrElse(title, BigDecimal(0))
  def close(): Unit                      = println("Db: connection closed")
}

final class InventoryRepo(db: Db) {
  def inStock(title: String): Int = db.stockOf(title)
}

final class PriceService(db: Db) {
  def price(title: String): BigDecimal = db.priceOf(title)
}

final class Catalog(inventory: InventoryRepo, prices: PriceService) {
  def describe(title: String): String =
    s"$title: ${inventory.inStock(title)} in stock at $$${prices.price(title)}"
}

final class ExportFile(prefix: String) extends AutoCloseable {
  private val path: Path = Files.createTempFile(prefix, ".csv")
  println("ExportFile: created")

  def write(line: String): Unit = {
    Files.write(path, (line + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND)
    ()
  }

  def lineCount: Int = Files.readAllLines(path).size

  def close(): Unit = {
    Files.deleteIfExists(path)
    println("ExportFile: deleted")
  }
}
```

`Db` and `ExportFile` are plain classes, not case classes. That matters on Scala 3, where every case class whose fields have `Unscoped` instances gets one too, so a case class resource could leave its scope. Keep resources as plain classes.

## Concept Map

| Task                          | cats-effect 3                          | ZIO 2                                    | macwire 2                     | `scala.util.Using`                  | ZIO Blocks                                         |
|-------------------------------|----------------------------------------|------------------------------------------|-------------------------------|-------------------------------------|----------------------------------------------------|
| Describe acquire and release  | `Resource.make(acq)(rel)`              | `ZIO.acquireRelease(acq)(rel)`           | none                          | a `Releasable[R]` instance          | `Resource.acquireRelease(acq)(rel)`                |
| Wrap an `AutoCloseable`       | `Resource.fromAutoCloseable(IO(r))`    | `ZIO.fromAutoCloseable(ZIO.attempt(r))`  | none                          | built in                            | `Resource.fromAutoCloseable(r)` or `allocate(r)`   |
| Use one resource              | `r.use(f)`                             | `ZIO.scoped(effect)`                     | none                          | `Using.resource(r)(f)`              | `r.use(f)`                                         |
| Several resources, one block  | `for` over resources, then `use`       | `ZIO.scoped` around a `for`              | none                          | `Using.Manager { use => ... }`      | `Scope.global.scoped { scope => ... }`             |
| Compose descriptions          | `map`, `flatMap`                       | `map`, `flatMap`                         | none                          | none                                | `map`, `flatMap`, `zip`                            |
| Acquire in parallel           | `both`, `parTupled`                    | `zipPar`                                 | none                          | none                                | not supported; `zip` is sequential                 |
| Add a finalizer               | `Resource.onFinalize(fin)`             | `ZIO.addFinalizer(fin)`                  | none                          | none                                | `defer(fin)`, cancel with `DeferHandle.cancel()`   |
| Manage the lifetime by hand   | `allocated`                            | `Scope.make`, then `close(exit)`         | none                          | none                                | `open()`, then `OpenScope.close()`                 |
| Several finalizers fail       | first error wins, rest reported        | all kept in the `Cause`                  | none                          | one thrown, rest suppressed         | first failure thrown, rest suppressed              |
| Stop a resource escaping      | no                                     | no                                       | no                            | no                                  | compile error unless the type is `Unscoped`        |
| Build from a constructor      | none                                   | `ZLayer.fromFunction`, `ZLayer.derive`   | `wire[A]`                     | none                                | `Wire.shared[A]`, `Wire.unique[A]`                 |
| Wire a whole graph            | none                                   | `ZLayer.make[A](layers*)`                | `autowire[A](deps*)` (Scala 3) | none                                | `Resource.from[A](wires*)`                         |
| Shared or fresh instances     | none                                   | shared by default, `ZLayer#fresh`        | one `lazy val` per instance   | none                                | `Wire.shared` (default), `Wire.unique`             |
| Close services in the graph   | through `Resource`                     | through `ZLayer.scoped`                  | by hand                       | by hand                             | automatic for `AutoCloseable`                      |

## Coming from cats-effect `Resource`

cats-effect `Resource[F, A]` and ZIO Blocks `Resource[A]` are both lazy descriptions of acquire and release that compose with `flatMap` and run with `use`. The ZIO Blocks one has no `F[_]`: acquisition and release are plain, synchronous Scala.

### You Wrote This

This is the old code, cats-effect 3 (not compiled here):

```scala
import cats.effect.{IO, IOApp, Resource}
import java.nio.file.{Files, Path}

object Bookstore extends IOApp.Simple {
  val db: Resource[IO, Db] =
    Resource.fromAutoCloseable(IO(new Db(DbConfig("jdbc:h2:mem:bookstore"))))

  val exportFile: Resource[IO, Path] =
    Resource.make(IO.blocking(Files.createTempFile("catalog", ".csv")))(p =>
      IO.blocking(Files.deleteIfExists(p)).void
    )

  // released in reverse order: exportFile first, then db
  val resources: Resource[IO, (Db, Path)] =
    for {
      d <- db
      f <- exportFile
    } yield (d, f)

  val run: IO[Unit] =
    resources.use { case (d, file) =>
      IO.blocking(Files.writeString(file, s"Zionomicon,${d.stockOf("Zionomicon")}")) *>
        IO.println(s"exported to $file")
    }
}
```

### You Write This

Drop the `IO` wrappers. `Resource.make` becomes `Resource.acquireRelease`, `Resource.fromAutoCloseable` keeps its name, and the `for` comprehension and `use` work the same way:

```scala mdoc:silent
val db: Resource[Db] =
  Resource.fromAutoCloseable(new Db(DbConfig("jdbc:h2:mem:bookstore")))

val exportPath: Resource[Path] =
  Resource.acquireRelease(Files.createTempFile("catalog", ".csv")) { p =>
    Files.deleteIfExists(p)
    println("export path: deleted")
  }

val resources: Resource[(Db, Path)] =
  for {
    d <- db
    f <- exportPath
  } yield (d, f)
```

Nothing has run yet. `use` acquires both, runs the function, and releases them in reverse order:

```scala mdoc
val linesWritten: Int = resources.use { case (d, file) =>
  Files.write(file, s"Zionomicon,${d.stockOf("Zionomicon")}".getBytes(StandardCharsets.UTF_8))
  Files.readAllLines(file).size
}
```

`linesWritten` is an `Int`, so it may leave `use`. The resource itself may not. This is the cats-effect `db.use(IO.pure)` leak, and it does not compile:

```scala mdoc:fail
val escaped: Db = db.use(d => d)
```

### What Changes

- **No effect type.** `Resource[A]` has one type parameter. Acquire and release are by-name Scala expressions, so `IO(...)`, `IO.blocking(...)` and `Resource.eval` go away: call the code directly.
- **The result of `use` must be `Unscoped`.** `use[B](f: A => B)` requires an `Unscoped[B]` instance. `Int`, `String`, collections of them and Scala 3 case classes built from them have one. A plain class such as `Db` does not, so it cannot escape.
- **No parallel acquisition.** `both` and `parTupled` have no equivalent. `zip` acquires left then right and releases right then left.
- **No cancellation or async.** A ZIO Blocks scope is synchronous and runs on the thread that opened it. If you rely on fibers, interruption or `Resource[IO, A]` inside a concurrent program, keep cats-effect for that part.
- **Finalizer errors are kept.** In cats-effect, when several releases fail, the first error wins and the rest go to the runtime's `reportFailure`. A ZIO Blocks scope throws the first failure and attaches the others as suppressed exceptions. The [`scala.util.Using` section](#finalizer-failures) shows this.
- **`allocated` becomes `open()`.** `Scope.global.open()` returns an `OpenScope` whose `close()` runs its finalizers and returns a `Finalization`, with no `F[Unit]` to forget.

## Coming from ZIO `Scope` and `ZLayer`

ZIO splits resource handling into two pieces: `ZIO.acquireRelease` plus `ZIO.scoped` for local resources, and `ZLayer` for the service graph. ZIO Blocks has the same split: `Scope#scoped` plus `Scope#allocate` for local resources, and `Wire` plus `Resource.from` for the graph.

### Scoped Resources

A local resource in ZIO lives inside `ZIO.scoped`; in ZIO Blocks it lives inside `Scope.global.scoped`.

#### You Wrote This

This is the old code, ZIO 2 (not compiled here):

```scala
import zio._

val program: Task[Int] =
  ZIO.scoped {
    for {
      db   <- ZIO.fromAutoCloseable(ZIO.attempt(new Db(DbConfig("jdbc:h2:mem:bookstore"))))
      file <- ZIO.fromAutoCloseable(ZIO.attempt(new ExportFile("catalog")))
      _    <- ZIO.addFinalizer(ZIO.succeed(println("export finished")))
      _    <- ZIO.attempt(file.write(s"Zionomicon,${db.stockOf("Zionomicon")}"))
    } yield file.lineCount
  }
```

#### You Write This

`ZIO.scoped` becomes `Scope.global.scoped`, `ZIO.fromAutoCloseable` becomes `Scope#allocate`, and `ZIO.addFinalizer` becomes `defer`. Values come back as `$[A]`, and `$` opens them for one call:

```scala mdoc
val exportedLines: Int = Scope.global.scoped { scope =>
  import scope._

  val db   = allocate(new Db(DbConfig("jdbc:h2:mem:bookstore")))
  val file = allocate(new ExportFile("catalog"))
  defer(println("export finished"))

  $(db, file)((d, f) => f.write(s"Zionomicon,${d.stockOf("Zionomicon")}"))
  $(file)(_.lineCount)
}
```

`defer` registered last, so it ran first. Then the export file closed, then the database, in reverse order of acquisition, as in ZIO.

### The Service Graph

A ZIO service graph is a set of `ZLayer`s assembled by `ZLayer.make`; in ZIO Blocks it is one `Resource.from` call.

#### You Wrote This

This is the old code, ZIO 2 (not compiled here):

```scala
import zio._

val dbLayer: ZLayer[DbConfig, Throwable, Db] =
  ZLayer.scoped[DbConfig](ZIO.serviceWithZIO[DbConfig](c => ZIO.fromAutoCloseable(ZIO.attempt(new Db(c)))))

val catalogLayer: ZLayer[Any, Throwable, Catalog] =
  ZLayer.make[Catalog](
    ZLayer.succeed(DbConfig("jdbc:h2:mem:bookstore")),
    dbLayer,
    ZLayer.fromFunction(new InventoryRepo(_)),
    ZLayer.fromFunction(new PriceService(_)),
    ZLayer.fromFunction(new Catalog(_, _))
  )

val run =
  ZIO.serviceWith[Catalog](_.describe("Zionomicon")).debug.provide(catalogLayer)
```

#### You Write This

`Resource.from[Catalog]` reads the constructors itself, so you list only what it cannot build: values such as `DbConfig` (it never builds a `String`), and services you want to construct differently. `Wire(value)` is the `ZLayer.succeed` counterpart:

```scala mdoc:silent
def catalogResource: Resource[Catalog] =
  Resource.from[Catalog](Wire(DbConfig("jdbc:h2:mem:bookstore")))
```

Allocate it in a scope like any other resource. One `Db` serves both repositories, and it closes when the scope does:

```scala mdoc
val description: String = Scope.global.scoped { scope =>
  import scope._
  val catalog = allocate(catalogResource)
  $(catalog)(_.describe("Zionomicon"))
}
```

`Db` extends `AutoCloseable`, so the macro registered its `close()`. There was no `ZLayer.scoped` step to remember.

When `Resource.from` cannot build the graph, the error names the type and the path that needed it. Leaving out the `DbConfig` wire fails because nothing can build its `String` field:

```scala mdoc:fail
Resource.from[Catalog]()
```

Both libraries check the graph at compile time: ZIO prints a `ZLAYER ERROR` box that lists the missing or ambiguous layers by number, while ZIO Blocks prints a `Scope Error` box with the tree of types that needed the missing one and a suggested `Wire(...)` fix.

### Shared and Fresh Services

ZIO shares a layer across the graph by default and gives you `ZLayer#fresh` for a separate copy. ZIO Blocks works the same way: auto-built services and `Wire.shared[A]` are shared, and `Wire.unique[A]` builds a new instance for each dependent:

```scala mdoc:silent
def catalogWithFreshPrices: Resource[Catalog] =
  Resource.from[Catalog](
    Wire(DbConfig("jdbc:h2:mem:bookstore")),
    Wire.unique[PriceService]
  )
```

`ZLayer.derive[A]` maps to `Wire.shared[A]`. One difference: `ZLayer.derive` does not close an `AutoCloseable` unless the class extends `ZLayer.Derive.Scoped`, while `Wire.shared` always registers `close()`.

### Manual Wires

`ZLayer.fromFunction` maps to `Wire.Shared.fromFunction`, for construction the macro cannot express. The function receives the `Scope` and a `Context` holding its inputs. A hand-written wire does not register `close()` for you, so defer it yourself:

```scala mdoc:silent
val dbWire: Wire.Shared[DbConfig, Db] =
  Wire.Shared.fromFunction[DbConfig, Db] { (scope, ctx) =>
    val db = new Db(ctx.get[DbConfig])
    scope.defer(db.close())
    db
  }

def catalogFromManualWire: Resource[Catalog] =
  Resource.from[Catalog](Wire(DbConfig("jdbc:h2:mem:bookstore")), dbWire)
```

Allocating the resource builds the graph with the manual wire and prints the description:

```scala mdoc
catalogFromManualWire.use(_.describe("Programming in Scala"))
```

:::warning
`Wire(value)` takes its argument by value. `Wire(new Db(...))` opens the connection when the wire is created, not when a scope allocates it. Use `Wire(...)` for configuration values and let `Resource.from`, `Wire.shared` or `Wire.Shared.fromFunction` construct resources.
:::

### What Changes

- **No environment type.** There is no `R` in `Resource[A]`. The graph is resolved once, inside `Resource.from`, and the result is an ordinary `Resource[Catalog]`.
- **Fewer layers to write.** `Resource.from` builds every concrete class it can reach from constructors. You list only leaf values and overrides, much like `ZLayer.derive` for the whole graph.
- **Errors are exceptions.** `Resource[A]` has no error channel. A constructor that throws fails `allocate` with that exception, and the scope still closes everything acquired so far.
- **Build a fresh graph for each run.** Write `def catalogResource`, not `val`. A `Resource` built from shared wires counts its users and is used up once the last one closes; allocating the same value again throws `IllegalStateException: Cannot allocate from a destroyed shared resource`. A `ZLayer` value can be provided again; a `Resource.from` value cannot.
- **Concurrency stays in ZIO.** If services in the graph run fibers, use `ZLayer` and ZIO's `Scope` for them.

## Coming from macwire

macwire is the closest match: `wire[A]` and `Wire.shared[A]` both read a constructor at compile time, with no runtime reflection. What macwire leaves out is lifecycle, and that is what ZIO Blocks adds.

### You Wrote This

This is the old code, macwire 2 (not compiled here):

```scala
import com.softwaremill.macwire._

class BookstoreModule {
  lazy val config: DbConfig         = DbConfig("jdbc:h2:mem:bookstore")
  lazy val db: Db                   = wire[Db]
  lazy val inventory: InventoryRepo = wire[InventoryRepo]
  lazy val prices: PriceService     = wire[PriceService]
  lazy val catalog: Catalog         = wire[Catalog]
}

val module = new BookstoreModule
try println(module.catalog.describe("Zionomicon"))
finally module.db.close() // macwire has no lifecycle: closing is on you
```

On Scala 3, macwire 2 also has `autowire`, which builds the whole graph from the dependencies you pass it:

```scala
import com.softwaremill.macwire._

val catalog = autowire[Catalog](DbConfig("jdbc:h2:mem:bookstore"))
```

### You Write This

`autowire[Catalog](deps*)` maps directly to `Resource.from[Catalog](wires*)`, and it works on Scala 2.13 as well as Scala 3. The module of `lazy val`s is no longer needed, and neither is the `try`/`finally`:

```scala mdoc
Resource
  .from[Catalog](Wire(DbConfig("jdbc:h2:mem:bookstore")))
  .use(_.describe("Functional Programming in Scala"))
```

To keep one named wire per service, as with `wire[A]`, write `Wire.shared[A]` (or `Wire.unique[A]`) and pass the wires to `Resource.from`. Both libraries resolve dependencies by type, and `Resource.from`, like `autowire`, refuses to build a `String` or a primitive on its own.

### What Lifecycle Adds

A macwire module closes nothing, so every `AutoCloseable` needs its own `try`/`finally`, written in reverse order by hand. With ZIO Blocks:

- **`AutoCloseable` services close automatically**, in reverse order of construction, when the scope closes, including when the body throws. The exception is `Resource.from[T]` without parentheses: when `T`'s constructor takes a `Finalizer` or `Scope`, it does not register `close()` (a known bug in 0.0.56), so write `Resource.from[T]()` instead.
- **Services can register their own cleanup.** A constructor that takes an implicit `Finalizer` (or `Scope`) gets one injected, and can call `defer`:

```scala mdoc:silent
final class CachedPriceService(db: Db)(implicit finalizer: Finalizer) {
  defer(println("CachedPriceService: price cache flushed"))

  def price(title: String): BigDecimal = db.priceOf(title)
}

final class CachedCatalog(inventory: InventoryRepo, prices: CachedPriceService) {
  def describe(title: String): String =
    s"$title: ${inventory.inStock(title)} in stock at $$${prices.price(title)}"
}
```

Wiring `CachedCatalog` flushes the price cache before the connection closes:

```scala mdoc
Resource
  .from[CachedCatalog](Wire(DbConfig("jdbc:h2:mem:bookstore")))
  .use(_.describe("Zionomicon"))
```

The price cache was flushed before the connection closed, because `CachedPriceService` was built after `Db`.

- **Services cannot outlive the graph.** A macwire module hands out plain references that stay usable after you close `db`. A ZIO Blocks scope hands out `$[Catalog]`, which cannot leave the scope.
- **Sharing is explicit.** In macwire, sharing follows from how many `lazy val`s you write. In ZIO Blocks, shared is the default and `Wire.unique[A]` asks for a new instance per dependent.

## Coming from `scala.util.Using`

`Using` is in the standard library, so the old code on this page compiles too. `Using.resource` maps to `Resource#use`, and `Using.Manager` maps to `Scope#scoped` plus `Scope#allocate`.

### You Wrote This

`Using.Manager` closes every resource passed to `use` when the block ends:

```scala mdoc
import scala.util.{Try, Using}

val managed: Try[Int] = Using.Manager { use =>
  val db   = use(new Db(DbConfig("jdbc:h2:mem:bookstore")))
  val file = use(new ExportFile("catalog"))
  file.write(s"Zionomicon,${db.stockOf("Zionomicon")}")
  file.lineCount
}
```

### You Write This

The same export written with a scope:

```scala mdoc
val scopedLines: Int = Scope.global.scoped { scope =>
  import scope._
  val db   = allocate(new Db(DbConfig("jdbc:h2:mem:bookstore")))
  val file = allocate(new ExportFile("catalog"))
  $(db, file)((d, f) => f.write(s"Zionomicon,${d.stockOf("Zionomicon")}"))
  $(file)(_.lineCount)
}
```

Both close in reverse order. `Using.Manager` returns a `Try`; `Scope#scoped` returns the value and throws if the body or a finalizer fails. Wrap the call in `Try` if you want the old shape back.

### Finalizer Failures

`Using` and ZIO Blocks handle failing finalizers in nearly the same way. `Using` rethrows one exception and adds the others with `addSuppressed`, ranking fatal errors above ordinary ones. A ZIO Blocks scope throws the first failure in run order and adds the rest as suppressed; when the body itself throws, the body's exception wins and every finalizer failure is suppressed on it. Here are two resources whose `close()` fails:

```scala mdoc:silent
final class FlakyResource(name: String) extends AutoCloseable {
  def close(): Unit = throw new IllegalStateException(s"$name failed to close")
}

def describeFailure(t: Throwable): String =
  s"${t.getMessage}; suppressed: ${t.getSuppressed.map(_.getMessage).mkString(", ")}"
```

With `Using.Manager`, the file closes first, so its failure is the one you get:

```scala mdoc
Using.Manager { use =>
  use(new FlakyResource("db"))
  use(new FlakyResource("export file"))
  ()
}.failed.foreach(t => println(describeFailure(t)))
```

A scope gives the same answer:

```scala mdoc
Try {
  Scope.global.scoped { scope =>
    import scope._
    allocate(new FlakyResource("db"))
    allocate(new FlakyResource("export file"))
    ()
  }
}.failed.foreach(t => println(describeFailure(t)))
```

What `Using` has no counterpart for is collecting the failures without throwing. A scope from `open()` returns them as a `Finalization` when you close it:

```scala mdoc:silent
val openScope = Scope.global.open()
openScope.scope.allocate(new FlakyResource("db"))
openScope.scope.allocate(new FlakyResource("export file"))

val finalization: Finalization = openScope.close()
```

`close()` threw nothing. Both failures are in `finalization.errors`, in the order the finalizers ran:

```scala mdoc
println(finalization.errors.map(_.getMessage).mkString(" | "))
```

From there, `finalization.orThrow()` throws the first error with the rest suppressed, and `finalization.suppress(primary)` attaches them all to an exception of your own.

### What Changes

- **Resources cannot escape.** `Using.resource(r)(identity)` compiles; `Scope#allocate` returns a `$[A]`, and `Scope#scoped` rejects a result without an `Unscoped` instance.
- **Use goes through `$`.** Inside the scope, call methods on a resource with `$(db)(_.stockOf(title))`. The lambda's parameter may appear only as the receiver of a method call, so you cannot pass the resource itself to other code. See [`$` and the scoped value type](../reference/resource-management/scope.md) for the rules.
- **More than resources.** `defer` adds any cleanup action, `DeferHandle.cancel()` removes it again, and nested `Scope#scoped` blocks plus `Scope#lower` let a child scope use its parent's resources.

## What You Gain and What Is Different

You gain:

- **A compile-time check on escapes.** `$[A]` and `Unscoped` turn use-after-close into a compile error. None of the four source libraries offers this.
- **Lifecycle and wiring in one model.** The constructor graph from macwire and the reverse-order cleanup from `Using`, cats-effect and ZIO come from the same `Resource`.
- **A compile-time checked graph.** `Resource.from` reports missing dependencies and ambiguous providers at compile time, like `ZLayer.make`. In 0.0.56 a dependency cycle crashes the macro instead of producing an error (a known bug, #1767).
- **No third-party dependencies and no effect type.** The module runs on Scala 2.13 and 3, on the JVM and Scala.js, in direct-style code.

What is different:

- **Synchronous only.** No fibers, async acquisition, interruption or parallel `both`. For those, stay on cats-effect or ZIO.
- **Child scopes belong to a thread.** Only the thread that entered a `Scope#scoped` block can open child scopes on it; a scope from `open()` has no owner.
- **Results need `Unscoped`.** To return your own type from `Scope#scoped` or `Resource#use`, it needs an `Unscoped` instance (see below).
- **`Resource.from` values are single-use.** Build them with `def`.
- **Case classes are `Unscoped` on Scala 3.** Model resources as plain classes so the escape check covers them.

To return your own type from a scope, give it an `Unscoped` instance. On Scala 3, case classes whose fields are `Unscoped` get one automatically, and `derives Unscoped` makes it explicit; on Scala 2, derive it in the companion:

<Tabs groupId="scala-version" defaultValue="scala2">
<TabItem value="scala2" label="Scala 2">

```scala mdoc:compile-only
final case class StockReport(title: String, inStock: Int)

object StockReport {
  implicit val unscoped: Unscoped[StockReport] = Unscoped.derived[StockReport]
}
```

</TabItem>
<TabItem value="scala3" label="Scala 3">

```scala mdoc:silent
final case class StockReport(title: String, inStock: Int) derives Unscoped
```

</TabItem>
</Tabs>

Returning `StockReport` from `Scope#scoped` now compiles because it has an `Unscoped` instance:

```scala mdoc
val report: StockReport = Scope.global.scoped { scope =>
  import scope._
  val catalog = allocate(Resource.from[InventoryRepo](Wire(DbConfig("jdbc:h2:mem:bookstore"))))
  StockReport("Zionomicon", $(catalog)(_.inStock("Zionomicon")))
}
println(report)
```

## Migration Checklist

1. Add `zio-blocks-scope` and `import zio.blocks.scope._`.
2. Replace `Resource.make` / `ZIO.acquireRelease` with `Resource.acquireRelease`, and cats-effect or ZIO `fromAutoCloseable` with ZIO Blocks `Resource.fromAutoCloseable` or `allocate(new ...)` (`Scope#allocate`). Remove the effect wrappers around acquire and release.
3. Replace `Resource#use` / `ZIO.scoped` / `Using.Manager` blocks with `Scope.global.scoped { scope => import scope._; ... }` and call resources through `$`.
4. Replace `Resource.onFinalize` / `ZIO.addFinalizer` with `defer`.
5. Replace `allocated` / `Scope.make` with `open()` and `OpenScope.close()`, and handle the returned `Finalization`.
6. Replace `ZLayer.make` or a macwire module with one `Resource.from[Root](wires*)`. Pass `Wire(value)` for configuration values; let the macro build the rest.
7. Replace `ZLayer#fresh` layers with `Wire.unique[A]`, and `ZLayer.fromFunction` with `Wire.Shared.fromFunction`, deferring `close()` yourself.
8. Turn `val` resources built by `Resource.from` into `def`s.
9. Make resource types plain classes, and give returned result types an `Unscoped` instance.
10. Delete the manual `try`/`finally` blocks that closed macwire-built services.
11. Keep cats-effect or ZIO for parts that need fibers, async resources or parallel acquisition.

## Putting It Together

This is the bookstore fully migrated: `Resource.from` wires the graph with one shared `Db`, a service registers its own cleanup through `Finalizer`, the scope opens the export file as a second resource, and everything closes in reverse order:

```scala mdoc:embed:scope-examples/src/main/scala/resourcemigration/BookstoreMigratedExample.scala
```

## Running the Examples

The complete example is a runnable file in the `scope-examples` project. Clone the repository and change into the project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/scope-examples
```

<details>
  <summary>scope-examples/src/main/scala/resourcemigration/BookstoreMigratedExample.scala</summary>

```scala mdoc:embed:scope-examples/src/main/scala/resourcemigration/BookstoreMigratedExample.scala:show-line-numbers
```

</details>

Run it from the `scope-examples` directory:

```bash
sbt "runMain resourcemigration.BookstoreMigratedExample"
```

To check that the example compiles without running it:

```bash
sbt compile
```

## See Also

- [Compile-Time Resource Safety with Scope](./compile-time-resource-safety-with-scope.md): the tutorial behind every concept on this page.
- [Resource Management](../reference/resource-management/index.md): how `Scope`, `Resource` and `Wire` fit together.
- [Scope](../reference/resource-management/scope.md), [Resource](../reference/resource-management/resource.md) and [Wire](../reference/resource-management/wire.md): the full API.
- [Unscoped](../reference/resource-management/unscoped.md) and [Finalization](../reference/resource-management/finalization.md): escape rules and finalizer errors.
- The how-to guide "How to fix common Scope and Wire compile errors", in progress, will explain each `Resource.from` and `$` compile error.
- [How to Use ZIO Blocks in a ZIO or cats-effect Application](./use-zio-blocks-in-zio-or-cats-effect.md): using ZIO Blocks resources from a ZIO or cats-effect application, with cleanup carried across.
