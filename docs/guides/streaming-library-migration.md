---
id: streaming-library-migration
title: "Coming from fs2, ZIO Streams or Ox"
description: "Move streaming pipelines from fs2, ZIO Streams or Ox to ZIO Blocks Stream, Sink and Pipeline, without an effect system."
keywords:
  - "Streaming"
  - "Migration"
  - "fs2"
  - "ZIO Streams"
  - "Ox"
  - "Stream"
---

This guide moves a bookstore's order pipeline from fs2, ZIO Streams or Ox to the ZIO Blocks [`streams` module](../reference/streams/index.md). By the end, the pipeline reads an order log, parses it with typed errors, prices the orders in parallel and totals the sales per title, using `Stream`, `Sink` and `Pipeline` in plain Scala, with no effect type and no runtime to start. Each source library gets its own section with the same three parts: the code you wrote, the code you write now, and what changes. Read the concept map, then jump to the section for the library you are leaving. Every migration should also read [Traps When Migrating](#traps-when-migrating): those are the places where code that looks like a direct translation behaves differently.

- [Concept Map](#concept-map)
- [Coming from ZIO Streams](#coming-from-zio-streams)
- [Coming from fs2](#coming-from-fs2)
- [Coming from Ox](#coming-from-ox)
- [Traps When Migrating](#traps-when-migrating)
- [What You Gain and What Is Different](#what-you-gain-and-what-is-different)
- [Migration Checklist](#migration-checklist)

## The Problem

fs2 and ZIO Streams tie every stream to an effect system. Each stage that calls ordinary code is wrapped in `IO` or `ZIO`, and nothing runs until an `IOApp`, a `ZIOAppDefault` or an unsafe runtime call interprets it. That is the right trade when the program is already built on cats-effect or ZIO. When the code around the stream is plain, synchronous Scala, the effect type is overhead: one more dependency, one more runtime, and wrappers around functions that never needed them.

Here is a small fs2 pipeline that totals copies sold per title. `parseOrder` and `addOrder` are pure functions from [the bookstore model](#the-bookstore-order-log) below, yet the stream still needs `IO`, `evalMap`, `compile` and `IOApp` to produce a `Map`.

This is the old code, fs2 3.14.0 (not compiled here):

```scala
import cats.effect.{IO, IOApp}
import fs2.Stream

object CopiesPerTitle extends IOApp.Simple {
  val run: IO[Unit] =
    Stream
      .emits(orderLines)
      .evalMap(line => IO(parseOrder(line)))
      .collect { case Right(order) => order }
      .compile
      .fold(Map.empty[String, Int])(addOrder)
      .flatMap(totals => IO.println(s"copies per title: $totals"))
}
```

The ZIO Streams version has the same shape with `ZStream`, `runFold` and `ZIOAppDefault`. Ox removes the effect type, but it is Scala 3.9+ and JDK 21+ only, and it reports failures as exceptions rather than typed errors.

ZIO Blocks Streams runs the same pipeline as a direct call: `runFold` blocks the calling thread and returns `Either[E, Z]`, with the stream's typed error on the left. The same `Stream` compiles on Scala 2.13 and Scala 3, and on Scala.js it runs through the asynchronous terminals.

## Prerequisites

The module you add depends on your Scala version.

- **Scala 2.13 and Scala 3.3 to 3.8:**

  ```scala
  libraryDependencies += "dev.zio" %% "zio-blocks-streams" % "@VERSION@"
  ```

- **Scala 3.9 and later:** use the `next` artifact and turn on experimental features:

  ```scala
  libraryDependencies += "dev.zio" %% "zio-blocks-next-streams" % "@VERSION@"
  scalacOptions += "-experimental"
  ```

On the JVM, the module is compiled to Java 21 bytecode, so it needs JDK 21 or later at runtime. For Scala.js, use `%%%` and Scala.js 1.22 or later. The module depends only on other ZIO Blocks modules (`scope`, `chunk`, `combinators`, `ringbuffer` and `async`), not on cats-effect, ZIO or Ox.

Every ZIO Blocks snippet below starts from this import:

```scala mdoc:silent
import zio.blocks.chunk.Chunk
import zio.blocks.streams._
```

This guide assumes you know one of the three source libraries. It maps their concepts onto ZIO Blocks rather than teaching streams from scratch. The [Streams reference](../reference/streams/index.md) covers `Stream`, `Sink` and `Pipeline` in depth. The tutorial "Build your first streaming pipeline" and the how-to guides "How to stream large query results" and "How to stream files and sockets without leaking resources" are in progress.

## The Bookstore Order Log

The running example is an order log, one CSV line per order: an order ID, a book title and the number of copies. The sales data is fictional. One line is malformed on purpose, so every library has to decide what to do with it. `priceOf` stands in for a slow call to a pricing service, and `ordersPath` is a temporary file holding the same log:

```scala mdoc:silent
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

final case class OrderEvent(orderId: Long, title: String, copies: Int)

// Fictional order log: one CSV line per order, and one malformed line
val orderLines: List[String] = List(
  "1,Zionomicon,2",
  "2,Programming in Scala,1",
  "3,Functional Programming in Scala,3",
  "not-an-order",
  "4,Zionomicon,1",
  "5,Programming in Scala,4"
)

def parseOrder(line: String): Either[String, OrderEvent] =
  line.split(',') match {
    case Array(id, title, copies) =>
      (id.toLongOption, copies.toIntOption) match {
        case (Some(i), Some(c)) => Right(OrderEvent(i, title, c))
        case _                  => Left(s"malformed order: $line")
      }
    case _ => Left(s"malformed order: $line")
  }

def addOrder(totals: Map[String, Int], order: OrderEvent): Map[String, Int] =
  totals.updated(order.title, totals.getOrElse(order.title, 0) + order.copies)

private val listPrices: Map[String, BigDecimal] = Map(
  "Zionomicon"                      -> BigDecimal("49.99"),
  "Programming in Scala"            -> BigDecimal("59.95"),
  "Functional Programming in Scala" -> BigDecimal("44.99")
)

// A slow lookup, standing in for a call to a pricing service
def priceOf(title: String): BigDecimal = {
  Thread.sleep(20)
  listPrices(title)
}

def addRevenue(totals: Map[String, BigDecimal], sale: (String, BigDecimal)): Map[String, BigDecimal] =
  totals.updated(sale._1, totals.getOrElse(sale._1, BigDecimal(0)) + sale._2)

val ordersPath: Path = Files.write(Files.createTempFile("orders", ".csv"), orderLines.asJava)
```

None of this code uses a streaming library, so the "before" snippets for fs2, ZIO Streams and Ox below use it unchanged.

## Concept Map

| Task                        | fs2 3.14                                | ZIO Streams 2.1                                | Ox 1.1                             | ZIO Blocks                                                                     |
|-----------------------------|-----------------------------------------|------------------------------------------------|------------------------------------|--------------------------------------------------------------------------------|
| Stream type                 | `Stream[F, O]`                          | `ZStream[R, E, A]`                             | `Flow[T]`                          | `Stream[E, A]`                                                                 |
| Reusable transformation     | `Pipe[F, I, O]`, `through`              | `ZPipeline[R, E, In, Out]`, `via`              | a function `Flow[A] => Flow[B]`    | `Pipeline[In, Out]`, `via`                                                     |
| Consumer                    | `compile.*`                             | `ZSink[R, E, In, L, Z]`, `run(sink)`           | `run*` methods                     | `Sink[E, A, Z]`, `run(sink)`                                                   |
| From a collection           | `Stream.emits(xs)`                      | `ZStream.fromIterable(xs)`                     | `Flow.fromIterable(xs)`            | `Stream.fromIterable(xs)`, `Stream(a, b, c)`                                   |
| From an iterator            | `Stream.fromIterator[F](it, chunkSize)` | `ZStream.fromIterator(it)`                     | `Flow.fromIterator(it)`            | `Stream.fromIterator(it)`                                                      |
| Unfold a state              | `Stream.unfold(s)(f)`                   | `ZStream.unfold(s)(f)`                         | `Flow.unfold(s)(f)`                | `Stream.unfold(s)(f)`                                                          |
| Push elements from a loop   | none covered here                       | none covered here                              | `Flow.usingEmit(emit => ...)`      | `Stream.unfold`, `Stream.fromIterator`, or a `Reader` with `Stream.fromReader` |
| Call code for each element  | `evalMap(a => IO(f(a)))`                | `mapZIO(a => ZIO.attempt(f(a)))`               | `map(f)`                           | `map(f)`; `mapAsync` for code that returns `Async`                             |
| Parallel map                | `parEvalMap(n)` (ordered)               | `mapZIOPar(n)` (ordered)                       | `mapPar(n)` (ordered)              | `mapPar(n)`, `mapParAsync(n)` (unordered only)                                 |
| Parallel map, unordered     | `parEvalMapUnordered(n)`                | `mapZIOParUnordered(n)`                        | `mapParUnordered(n)`               | `mapPar(n)`, `mapParAsync(n)`                                                  |
| Parallel flatMap            | `map(f).parJoin(n)`                     | `flatMapPar(n)(f)`                             | `map(f).flattenPar(n)`             | `flatMapPar(n)(f)`                                                             |
| Merge two streams           | `a.merge(b)`                            | `a.merge(b)`                                   | `a.merge(b)`                       | `Stream.mergeAll(2)(Stream(a, b))`                                             |
| Acquire and release         | `Stream.bracket`, `Stream.resource`     | `ZStream.acquireReleaseWith`, `ZStream.scoped` | `usingEmit` with `try`/`finally`   | `Stream.fromAcquireRelease`, `Stream.fromResource`                             |
| Fixed-size batches          | `chunkN(n)`                             | `grouped(n)`                                   | `grouped(n)` (a `Seq`)             | `grouped(n)` or `chunked(n)` (a `Chunk`)                                       |
| Running state               | `scan`, `mapAccumulate`                 | `scan`, `mapAccum`                             | `scan`, `mapStateful`              | `scan`, `mapAccum`                                                             |
| Custom pull loop            | `Pull`, `stream.pull.uncons1`           | none covered here                              | none                               | `start` plus `Reader#read`, or `mapAccum`                                      |
| Typed error                 | none (`Throwable` in `F`)               | the `E` channel                                | none (exceptions; `either` blocks) | the `E` channel, `Left` from every terminal                                    |
| Recover from an error       | `handleErrorWith`                       | `catchAll`, `orElse`                           | `recover`, `recoverWith`           | `catchAll`, `orElse` (alias `\|\|`), `catchDefect`                             |
| Turn exceptions into errors | `attempt`                               | `ZStream.fromZIO(ZIO.attempt(...))`            | `either.catching`                  | `Stream.attempt(f)`                                                            |
| Collect everything          | `compile.toList`                        | `runCollect`                                   | `runToList()`                      | `runCollect`                                                                   |
| Fold                        | `compile.fold(z)(f)`                    | `runFold(z)(f)`                                | `runFold(z)(f)`                    | `runFold(z)(f)`                                                                |
| Drain                       | `compile.drain`                         | `runDrain`                                     | `runDrain()`                       | `runDrain`                                                                     |
| Count                       | `compile.count`                         | `runCount`                                     | none                               | `count`                                                                        |
| Run the program             | `IOApp`, `unsafeRunSync()`              | `ZIOAppDefault`, `Unsafe.unsafe`               | a direct call                      | a direct call on the JVM; `*Async` terminals on Scala.js                       |

## Coming from ZIO Streams

`ZStream[R, E, A]`, `ZSink` and `ZPipeline` map almost one to one onto `Stream[E, A]`, `Sink[E, A, Z]` and `Pipeline[In, Out]`. The `E` channel works the same way. What goes away is the environment `R` and the `ZIO` effect around every step.

### A First Pipeline

#### You Wrote This

This is the old code, ZIO Streams 2.1.27 (not compiled here):

```scala
import zio._
import zio.stream._

object CopiesPerTitle extends ZIOAppDefault {
  val run =
    ZStream
      .fromIterable(orderLines)
      .map(parseOrder)
      .collect { case Right(order) => order }
      .runFold(Map.empty[String, Int])(addOrder)
      .debug("copies per title")
}
```

#### You Write This

The operators keep their names. `runFold` runs the stream on the spot instead of returning an effect for `ZIOAppDefault` to run:

```scala mdoc:silent
val copiesPerTitle: Either[Nothing, Map[String, Int]] =
  Stream
    .fromIterable(orderLines)
    .map(parseOrder)
    .collect { case Right(order) => order }
    .runFold(Map.empty[String, Int])(addOrder)
```

```scala mdoc
println(copiesPerTitle)
```

The result is an `Either` even though this stream cannot fail: every terminal returns `Either[E, Z]`, and `E` is `Nothing` here.

### Typed Errors

#### You Wrote This

ZIO Streams carries a parse failure in the error channel with `ZIO.fromEither`, and recovers with `catchAll` (ZIO Streams 2.1.27, not compiled here):

```scala
import zio._
import zio.stream._

val orders: ZStream[Any, String, OrderEvent] =
  ZStream.fromIterable(orderLines).mapZIO(line => ZIO.fromEither(parseOrder(line)))

// fails with "malformed order: not-an-order"
val strict: IO[String, Map[String, Int]] =
  orders.runFold(Map.empty[String, Int])(addOrder)

// stops at the malformed line and keeps what it has
val lenient: UIO[Map[String, Int]] =
  orders.catchAll(_ => ZStream.empty).runFold(Map.empty[String, Int])(addOrder)
```

#### You Write This

There is no `mapZIO`, so turn each `Either` into a one-element stream or a failed stream with `flatMap`. The error type is inferred from `Stream.fail`:

```scala mdoc:silent
def orderOrFailure(line: String): Stream[String, OrderEvent] =
  parseOrder(line).fold(error => Stream.fail(error), order => Stream.succeed(order))

val orders: Stream[String, OrderEvent] =
  Stream.fromIterable(orderLines).flatMap(orderOrFailure)
```

The failure comes back as a `Left` from the terminal, and `catchAll` recovers from it on the outer stream, as in ZIO:

```scala mdoc
println(orders.runFold(Map.empty[String, Int])(addOrder))

println(orders.catchAll(_ => Stream.empty).runFold(Map.empty[String, Int])(addOrder))
```

:::warning
Put `catchAll` on the outer stream, as above, not on the inner stream inside `flatMap`. Recovery inside `flatMap` followed by `grouped`, `sliding`, `scan` or `mapAccum` throws an internal error from blocking terminals. See [Recovery inside `flatMap`](#recovery-inside-flatmap).
:::

### Resources and Parallel Work

#### You Wrote This

The order log comes from a file opened with `ZStream.acquireReleaseWith`, and each order is priced in parallel with `mapZIOPar` (ZIO Streams 2.1.27, not compiled here):

```scala
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._
import zio._
import zio.stream._

def orderFile(path: Path): ZStream[Any, Throwable, String] =
  ZStream
    .acquireReleaseWith(ZIO.attempt(Files.newBufferedReader(path)))(reader =>
      ZIO.succeed { reader.close(); println("order file closed") }
    )
    .flatMap(reader => ZStream.fromIterator(reader.lines().iterator().asScala))

val revenue: Task[Map[String, BigDecimal]] =
  orderFile(ordersPath)
    .map(parseOrder)
    .collect { case Right(order) => order }
    .mapZIOPar(4)(order => ZIO.attemptBlocking(order.title -> priceOf(order.title) * order.copies))
    .runFold(Map.empty[String, BigDecimal])(addRevenue)
```

#### You Write This

`ZStream.acquireReleaseWith(acquire)(release).flatMap(use)` becomes `Stream.fromAcquireRelease(acquire, release)(use)`. Acquire and release are plain code, and `mapZIOPar` becomes `mapPar`:

```scala mdoc:silent
import java.io.BufferedReader

def orderFile(path: Path): Stream[Nothing, String] =
  Stream.fromAcquireRelease(
    Files.newBufferedReader(path),
    (reader: BufferedReader) => { reader.close(); println("order file closed") }
  )(reader => Stream.fromIterator(reader.lines().iterator().asScala))

val revenue: Stream[Nothing, (String, BigDecimal)] =
  orderFile(ordersPath)
    .map(parseOrder)
    .collect { case Right(order) => order }
    .mapPar(4)(order => order.title -> priceOf(order.title) * order.copies)
```

Each run opens the file, and the release runs when the run ends. `mapPar` finishes the orders in any order, so the totals are sorted by title before printing:

```scala mdoc
println(revenue.runFold(Map.empty[String, BigDecimal])(addRevenue).map(_.toList.sortBy(_._1)))
```

The release argument is optional. Without it, `fromAcquireRelease` calls `close()` on an `AutoCloseable` resource. A file that cannot be opened throws from the terminal, because exceptions in `acquire` are defects; wrap the open in `Stream.attempt` to get a `Stream[Throwable, A]` instead. For a resource built with the ZIO Blocks [`scope` module](../reference/resource-management/index.md), `Stream.fromResource(resource)(use)` is the `ZStream.scoped` counterpart: it opens a scope for each run and closes it when the run ends.

:::warning
`mapZIOPar` keeps input order; `mapPar` does not. The fold above does not care about order. If yours does, read [`mapPar` emits in completion order](#mappar-emits-in-completion-order).
:::

### ZIO Effects in the Pipeline

A `ZIO` step becomes one of two things:

- **A blocking call.** `ZIO.attempt` and `ZIO.attemptBlocking` around ordinary code disappear: call the code in `map`, or in `mapPar` to run several calls at once. On the JVM, `mapPar` and `flatMapPar` run their workers on virtual threads, so blocking in them is cheap.
- **An `Async` value.** When the code you call is itself asynchronous, for example a client that returns a `Future`, convert it to `zio.blocks.async.Async` and use `mapAsync` or `mapParAsync`. On the JVM, `Async.fromFuture` and `Async.fromCompletionStage` convert from the standard types:

```scala mdoc:silent
import scala.concurrent.{ExecutionContext, Future}
import zio.blocks.async._

// A pricing client with a Future-based API
def priceFromService(title: String)(implicit ec: ExecutionContext): Future[BigDecimal] =
  Future(priceOf(title))
```

```scala mdoc:silent
implicit val ec: ExecutionContext = ExecutionContext.global

val asyncRevenue: Either[Nothing, Map[String, BigDecimal]] =
  Stream
    .fromIterable(orderLines)
    .map(parseOrder)
    .collect { case Right(order) => order }
    .mapParAsync(4)(order =>
      Async.fromFuture(priceFromService(order.title).map(price => order.title -> price * order.copies))
    )
    .runFold(Map.empty[String, BigDecimal])(addRevenue)
```

```scala mdoc
println(asyncRevenue.map(_.toList.sortBy(_._1)))
```

A ZIO program that keeps ZIO elsewhere can still call a ZIO Blocks stream: the blocking terminals are plain calls, so wrap one in `ZIO.attemptBlocking`.

### What Has No Equivalent

These ZIO Streams features have no ZIO Blocks counterpart. Keep ZIO Streams for the parts of a program that need them:

- **The environment.** There is no `R`, so `ZStream.service`, `serviceWith` and `serviceWithZIO` have no counterpart. Pass dependencies as ordinary parameters, or wire them with the `scope` module's `Resource.from`.
- **Schedules and time.** There is no `Schedule`, so `ZStream.repeatWithSchedule`, `repeat(schedule)`, `schedule`, `ZStream.tick`, `throttleShape`, `throttleEnforce` and `groupedWithin` have no counterpart. `Stream#repeated` repeats a whole stream with no delay, and `Stream.repeat(a)` repeats one value forever.
- **Fibers and interruption.** There is no fiber to fork, join or interrupt. Concurrency lives inside `mapPar`, `mapParAsync`, `flatMapPar` and `Stream.mergeAll`.
- **Merge strategies.** `merge` with a `HaltStrategy` has no counterpart. `Stream.mergeAll(n)(streams)` ends when every input ends.
- **Sink leftovers and channels.** A `Sink[E, A, Z]` has no leftover type, and there is no `ZChannel`. For custom stateful operators, use `mapAccum`, `scan` or a manual pull with `start` (see [Custom Operators with `Pull`](#custom-operators-with-pull)).

### What Changes

- **No effect type and no runtime.** `Stream[E, A]` has two type parameters. Stages are plain functions, and a terminal returns its result instead of an effect.
- **Every terminal returns `Either[E, Z]`.** `runCollect`, `runFold`, `runDrain`, `count` and `run(sink)` return `Left` for a typed error. An exception thrown inside the pipeline is a defect: blocking terminals rethrow it, and `catchDefect` recovers from it.
- **`fromInputStream` closes its stream.** ZIO's `ZStream.fromInputStream` never closes the `InputStream`. ZIO Blocks' `Stream.fromInputStream` closes it when the run ends, and `Stream.fromInputStreamUnmanaged` leaves it open. See [Managed handles are opened once](#managed-handles-are-opened-once).
- **`merge` becomes `Stream.mergeAll`.** `a.merge(b)` becomes `Stream.mergeAll(2)(Stream(a, b))`, and `ZStream.mergeAll(n)(streams*)` keeps its name with the streams passed as a stream.

## Coming from fs2

fs2's `Stream[F, O]` and ZIO Blocks' `Stream[E, A]` are both pull-based. The ZIO Blocks one has no `F[_]`: where fs2 puts `IO` in the type and `Throwable` in `F`, ZIO Blocks puts a typed error `E`.

### You Wrote This

The order log is read with `Stream.bracket`, parsed with a `Pipe`, and priced in parallel with `parEvalMap`. This is the old code, fs2 3.14.0 (not compiled here):

```scala
import java.io.BufferedReader
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._
import cats.effect.IO
import fs2.{Pipe, Stream}

def orderFile(path: Path): Stream[IO, String] =
  Stream
    .bracket(IO.blocking(Files.newBufferedReader(path)))(reader =>
      IO.blocking { reader.close(); println("order file closed") }
    )
    .flatMap(reader => Stream.fromIterator[IO](reader.lines().iterator().asScala, 64))

val parseOrders: Pipe[IO, String, OrderEvent] =
  _.map(parseOrder).collect { case Right(order) => order }

val revenue: IO[Map[String, BigDecimal]] =
  orderFile(ordersPath)
    .through(parseOrders)
    .parEvalMap(4)(order => IO.blocking(order.title -> priceOf(order.title) * order.copies))
    .compile
    .fold(Map.empty[String, BigDecimal])(addRevenue)
```

### You Write This

`Stream.bracket(acquire)(release).flatMap(use)` becomes `Stream.fromAcquireRelease(acquire)(use)`; with no release function, the `BufferedReader` is closed as an `AutoCloseable`. A `Pipe` becomes a `Pipeline` built from `Pipeline.map`, `Pipeline.collect` and `andThen`, and `through` becomes `via`. `parEvalMap` becomes `mapPar`, and `compile.fold` becomes `runFold`:

```scala mdoc:silent
def orderLog(path: Path): Stream[Nothing, String] =
  Stream.fromAcquireRelease(Files.newBufferedReader(path))(reader =>
    Stream.fromIterator(reader.lines().iterator().asScala)
  )

val parseOrders: Pipeline[String, OrderEvent] =
  Pipeline
    .map[String, Either[String, OrderEvent]](parseOrder)
    .andThen(Pipeline.collect[Either[String, OrderEvent], OrderEvent] { case Right(order) => order })
```

```scala mdoc:silent
val revenueFromLog: Either[Nothing, Map[String, BigDecimal]] =
  orderLog(ordersPath)
    .via(parseOrders)
    .mapPar(4)(order => order.title -> priceOf(order.title) * order.copies)
    .runFold(Map.empty[String, BigDecimal])(addRevenue)
```

```scala mdoc
println(revenueFromLog.map(_.toList.sortBy(_._1)))
```

`Stream.fromIterator` needs no chunk size: ZIO Blocks pulls one element at a time and specializes primitive elements instead of batching them into chunks. `Stream.resource(r)` maps to `Stream.fromResource(resource)(use)`, with a ZIO Blocks `Resource` in place of a cats-effect one.

### Custom Operators with `Pull`

#### You Wrote This

A `Pull` writes an operator that fs2 does not have, here "emit orders until `limit` copies have been sold". This is the old code, fs2 3.14.0 (not compiled here):

```scala
import cats.effect.IO
import fs2.{Pipe, Pull, Stream}

// Emit orders until `limit` copies have been sold
def untilCopies(limit: Int): Pipe[IO, OrderEvent, OrderEvent] = {
  def go(orders: Stream[IO, OrderEvent], sold: Int): Pull[IO, OrderEvent, Unit] =
    orders.pull.uncons1.flatMap {
      case Some((order, rest)) if sold < limit => Pull.output1(order) >> go(rest, sold + order.copies)
      case _                                   => Pull.done
    }
  orders => go(orders, 0).stream
}

val firstOrders: IO[List[Long]] =
  Stream
    .emits(orderLines)
    .map(parseOrder)
    .collect { case Right(order) => order }
    .through(untilCopies(5))
    .map(_.orderId)
    .compile
    .toList
```

#### You Write This

Most `Pull` loops carry a piece of state from one element to the next. `mapAccum` does that and stays a stream: it pairs each order with the copies sold before it, and `takeWhile` stops at the limit:

```scala mdoc:silent
val parsedOrders: Stream[Nothing, OrderEvent] =
  Stream.fromIterable(orderLines).via(parseOrders)

def untilCopies(orders: Stream[Nothing, OrderEvent], limit: Int): Stream[Nothing, OrderEvent] =
  orders
    .mapAccum(0)((sold, order) => (sold + order.copies, (sold, order)))
    .takeWhile { case (soldBefore, _) => soldBefore < limit }
    .map { case (_, order) => order }
```

```scala mdoc
println(untilCopies(parsedOrders, 5).map(_.orderId).runCollect)
```

When the loop does not fit an operator, pull by hand. On the JVM, `start` opens the stream as a `Reader` inside a [`Scope`](../reference/resource-management/scope.md), and `read(sentinel)` returns the next element, or the sentinel you pass once the stream ends. `OrderEvent` is a reference type, so `null` makes a safe sentinel:

```scala mdoc:silent
import zio.blocks.scope._

val firstOrderIds: List[Long] = Scope.global.scoped { scope =>
  import scope._
  val reader = parsedOrders.start(using scope)

  $(reader) { r =>
    var sold  = 0
    var ids   = List.empty[Long]
    var order = r.read(null)
    while (order != null && sold < 5) {
      ids = order.orderId :: ids
      sold += order.copies
      order = r.read(null)
    }
    ids.reverse
  }
}
```

```scala mdoc
println(firstOrderIds)
```

On Scala 2.13, pass the scope as `parsedOrders.start(scope)`. The reader closes with the scope. For a primitive stream, choose a sentinel your stream never emits; the [Reader reference](../reference/streams/primitives/reader.md) covers the primitive read methods.

### Batches

#### You Wrote This

`chunkN` groups elements into batches for a bulk write (fs2 3.14.0, not compiled here):

```scala
import cats.effect.IO
import fs2.Stream

// Write the order log two lines at a time
val batches: IO[Unit] =
  Stream
    .emits(orderLines)
    .chunkN(2)
    .evalMap(batch => IO.println(s"writing ${batch.size} lines"))
    .compile
    .drain
```

#### You Write This

`chunkN(n)` becomes `grouped(n)` (or its alias `chunked(n)`), which emits a ZIO Blocks `Chunk`. The last batch may be smaller:

```scala mdoc
println(
  Stream
    .fromIterable(orderLines)
    .grouped(2)
    .runForeach(batch => println(s"writing ${batch.size} lines"))
)
```

`chunks` has no counterpart, because a ZIO Blocks stream does not carry chunk boundaries. `groupWithin` has none either, because there are no timers.

### What Changes

- **No `F[_]`.** `Stream[IO, O]` becomes `Stream[E, A]`, and `IO(...)`, `IO.blocking(...)` and `evalMap` turn into plain calls in `map`. `Stream.eval(io)` becomes `Stream.attempt(f)` for code that can throw.
- **A typed error instead of `Throwable`.** fs2 fails the stream with a `Throwable`. ZIO Blocks has a typed `E` returned as `Left`, and treats thrown exceptions as defects. `handleErrorWith` maps to `catchAll` for typed errors and to `catchDefect` for exceptions.
- **`compile.*` becomes a terminal.** `compile.toList` becomes `runCollect` (a `Chunk`, inside `Either`), `compile.fold` becomes `runFold`, `compile.drain` becomes `runDrain`, and `compile.count` becomes `count`.
- **`parJoin` becomes `flatMapPar` or `Stream.mergeAll`.** `stream.map(f).parJoin(n)` becomes `stream.flatMapPar(n)(f)`, and a stream of streams merges with `Stream.mergeAll(n)(streams)`.
- **No `Pull`, `Topic` or `Signal`.** Use `mapAccum`, `scan` or `start` for custom loops, and keep fs2 for pub-sub and signals.

## Coming from Ox

Ox and ZIO Blocks are both direct style: a `Flow` and a `Stream` are lazy descriptions, and a `run*` call runs them on the calling thread. What differs is the error model, the concurrency model and the platforms.

### You Wrote This

The order log arrives from a paged API, pushed into the flow with `Flow.usingEmit`, and priced with `mapPar`. This is the old code, Ox 1.1.0 (not compiled here):

```scala
import ox.flow.Flow

// A paged API: page 0, 1, 2, ... until a page comes back empty
def fetchPage(page: Int): List[String] =
  orderLines.grouped(2).toList.lift(page).getOrElse(Nil)

val pagedOrders: Flow[String] = Flow.usingEmit { emit =>
  var page  = 0
  var lines = fetchPage(page)
  while (lines.nonEmpty) {
    lines.foreach(emit(_))
    page += 1
    lines = fetchPage(page)
  }
}

// mapPar keeps input order: results come back sorted by orderId
val pricedOrders: List[(Long, BigDecimal)] =
  pagedOrders
    .map(parseOrder)
    .collect { case Right(order) => order }
    .mapPar(4)(order => order.orderId -> priceOf(order.title) * order.copies)
    .runToList()
```

### You Write This

A `usingEmit` loop that walks pages is an unfold: `Stream.unfold` produces one page per step, and `flatMap` flattens each page into lines. `runToList()` becomes `runCollect`:

```scala mdoc:silent
def fetchPage(page: Int): List[String] =
  orderLines.grouped(2).toList.lift(page).getOrElse(Nil)

val pagedOrders: Stream[Nothing, String] =
  Stream
    .unfold(0)(page => Option(fetchPage(page)).filter(_.nonEmpty).map(lines => (lines, page + 1)))
    .flatMap(lines => Stream.fromIterable(lines))
```

`mapPar` emits results in completion order, so sort them if order matters:

```scala mdoc:silent
val pricedOrders: Either[Nothing, Chunk[(Long, BigDecimal)]] =
  pagedOrders
    .map(parseOrder)
    .collect { case Right(order) => order }
    .mapPar(4)(order => order.orderId -> priceOf(order.title) * order.copies)
    .runCollect
    .map(_.sortBy(_._1))
```

```scala mdoc
println(pricedOrders)
```

A `usingEmit` loop over an iterator becomes `Stream.fromIterator`. A loop that does not fit either shape can implement a `Reader` and pass it to `Stream.fromReader`; see the [Reader reference](../reference/streams/primitives/reader.md). A `usingEmit` with `try`/`finally` around a resource becomes `Stream.fromAcquireRelease`, as in [the ZIO Streams section](#resources-and-parallel-work).

### Errors

#### You Wrote This

A `Flow` has no error type. A failure is an exception, and the `run*` method rethrows it (Ox 1.1.0, not compiled here):

```scala
import ox.flow.Flow

// An exception thrown inside the flow is rethrown by the run method
def strict: Map[String, Int] =
  Flow
    .fromIterable(orderLines)
    .map(line => parseOrder(line).fold(msg => throw new IllegalArgumentException(msg), identity))
    .runFold(Map.empty[String, Int])(addOrder)
```

#### You Write This

ZIO Blocks keeps the same behavior for exceptions: a throw inside `map` is a defect, and a blocking terminal rethrows it. The better translation is a typed error, so the failure arrives as a value. `orders`, from the [ZIO Streams section](#typed-errors), fails with the parse error:

```scala mdoc
println(orders.runFold(Map.empty[String, Int])(addOrder))
```

`Stream.attempt(f)` turns code that throws into a `Stream[Throwable, A]`, the counterpart of Ox's `either.catching`:

```scala mdoc
println(Stream.attempt("not-a-number".toInt).runCollect)
```

Ox's `recover` and `recoverWith` map to `catchDefect` for exceptions. Ox's `orElse` is a different operator: it substitutes a flow only when the flow is empty. ZIO Blocks' `orElse` (alias `||`) falls back on the first typed error and does not handle defects.

### Merging and Structured Concurrency

#### You Wrote This

`merge` runs two flows at once and interleaves their elements (Ox 1.1.0, not compiled here):

```scala
import ox.flow.Flow

val onlineOrders: Flow[String]  = Flow.fromIterable(orderLines.take(3))
val inStoreOrders: Flow[String] = Flow.fromIterable(orderLines.drop(3))

// merge runs both flows concurrently and interleaves their elements
val allCopies: Map[String, Int] =
  onlineOrders
    .merge(inStoreOrders)
    .map(parseOrder)
    .collect { case Right(order) => order }
    .runFold(Map.empty[String, Int])(addOrder)
```

#### You Write This

`a.merge(b)` becomes `Stream.mergeAll(2)(Stream(a, b))`, which pulls from up to two streams at once and emits in arrival order:

```scala mdoc:silent
val onlineOrders: Stream[Nothing, String]  = Stream.fromIterable(orderLines.take(3))
val inStoreOrders: Stream[Nothing, String] = Stream.fromIterable(orderLines.drop(3))

val allCopies: Either[Nothing, Map[String, Int]] =
  Stream
    .mergeAll(2)(Stream(onlineOrders, inStoreOrders))
    .map(parseOrder)
    .collect { case Right(order) => order }
    .runFold(Map.empty[String, Int])(addOrder)
```

```scala mdoc
println(allCopies.map(_.toList.sortBy(_._1)))
```

Ox builds its operators on structured concurrency: `mapPar`, `merge` and `flattenPar` open a scope when the flow runs, and a failing branch cancels the others before the exception reaches the caller. Ox also gives you `supervised` and `fork` to run your own concurrent code next to a flow. ZIO Blocks has no user-facing concurrency scope and no `fork`. Its concurrency lives inside `mapPar`, `mapParAsync`, `flatMapPar` and `Stream.mergeAll`, which run workers on virtual threads on the JVM. When one worker fails, the operator stops the others and closes upstream before the terminal returns. A ZIO Blocks `Scope` manages resources, not threads. Keep Ox's `supervised` for code that forks work outside a stream.

### What Changes

- **Typed errors.** An Ox failure is an exception. A ZIO Blocks failure can be a typed `E` returned as `Left`, or a defect that the terminal rethrows.
- **`mapPar` is unordered.** Ox's `mapPar` keeps input order, and its `mapParUnordered` does not. ZIO Blocks' `mapPar` behaves like `mapParUnordered`. See [`mapPar` emits in completion order](#mappar-emits-in-completion-order).
- **`Chunk`, not `Seq`.** `grouped(n)` emits a `Chunk`, and `runCollect` returns one.
- **`flattenPar` becomes `flatMapPar` or `Stream.mergeAll`.**
- **More platforms.** Ox 1.1.0 needs Scala 3.9 and JDK 21. ZIO Blocks Streams also runs on Scala 2.13, Scala 3.3 and Scala.js; only the JVM needs JDK 21.
- **No time-based operators.** Ox's `throttle`, `debounce`, `groupedWithin`, `Flow.tick` and `retry` have no counterpart.

## Traps When Migrating

Each trap below is code that looks like a direct translation and behaves differently. The first two are about ordering and resources. The next three are known library bugs, listed in the project's issue log, that affect the blocking terminals; the asynchronous terminals give the right answer in each case.

### `mapPar` Emits in Completion Order

fs2's `parEvalMap`, ZIO's `mapZIOPar` and Ox's `mapPar` all keep input order. ZIO Blocks' `mapPar`, `mapParAsync`, `flatMapPar` and `Stream.mergeAll` emit each result as soon as it is ready. A fold that adds numbers, as in this guide, does not care. Code that writes results in order, or that zips them with the input, does. Carry a key through the parallel step and sort afterwards, as in [the Ox section](#coming-from-ox), or keep the order-sensitive part sequential with `map`.

### Recovery Inside `flatMap`

A blocking terminal throws the internal `Stream$AsyncBoundaryRequired$` when `catchAll` or `||` sits on the inner stream of a `flatMap` and a stateful operator (`grouped`, `chunked`, `sliding`, `scan` or `mapAccum`) follows. This is the shape a ZIO `mapZIO(...).catchAll` often turns into:

```scala mdoc:compile-only
// Throws Stream$AsyncBoundaryRequired$ from runCollect
Stream
  .fromIterable(orderLines)
  .flatMap(line => orderOrFailure(line).catchAll(_ => Stream.empty))
  .grouped(2)
  .runCollect
```

Recover on the outer stream instead, as in [Typed Errors](#typed-errors); that stops at the first malformed line. To skip bad lines and keep going, filter the `Either` values before they become stream errors:

```scala mdoc
println(
  Stream
    .fromIterable(orderLines)
    .map(parseOrder)
    .collect { case Right(order) => order }
    .grouped(2)
    .map(_.size)
    .runCollect
)
```

The asynchronous terminals, such as `runCollectAsync`, handle the inner `catchAll` correctly.

### A Primitive `map` after `flatMap`

After a `flatMap` over reference elements, a `map` to a primitive (`Int`, `Long`, `Double`, `Boolean`, `Char`) followed by `grouped`, `chunked`, `sliding`, `take`, `takeWhile`, `scan` or `mapAccum` throws a `ClassCastException` from blocking terminals. `pagedOrders` from the Ox section is built with `flatMap`, so the obvious "copies per batch" code crashes:

```scala mdoc:compile-only
// Throws java.lang.ClassCastException from runCollect
pagedOrders
  .map(parseOrder)
  .collect { case Right(order) => order }
  .map(_.copies)
  .grouped(2)
  .runCollect
```

Keep the elements as objects until after the stateful operator, and map to the primitive inside each batch:

```scala mdoc
println(
  pagedOrders
    .map(parseOrder)
    .collect { case Right(order) => order }
    .grouped(2)
    .map(_.map(_.copies))
    .runCollect
)
```

A fold such as `runFold(0)(_ + _)` after the primitive `map` is not affected. The asynchronous terminals give the right answer for the original pipeline.

### Blocking Inside an `App` Object

A blocking terminal can deadlock inside the body of a Scala 3 `object Main extends App` once `catchAll` is in the pipeline. The object's body runs inside its class initializer. A worker thread that touches the object waits for the initializer to finish, while the initializer waits for the worker. Put the program in a `main` method:

```scala mdoc:compile-only
object CopiesReport {
  def main(args: Array[String]): Unit =
    println(orders.catchAll(_ => Stream.empty).runFold(Map.empty[String, Int])(addOrder))
}
```

### Managed Handles Are Opened Once

`Stream.fromInputStream(in)` and `NioStreams.fromChannel(channel)` take a handle that is already open. They close it when the first run ends, so the stream leaks the handle if it never runs, and a second run fails:

```scala mdoc:silent
val bytesOnce: Stream[java.io.IOException, Byte] =
  Stream.fromInputStream(Files.newInputStream(ordersPath))
```

The first run reads the file and closes it. The second run reads a closed handle:

```scala mdoc
println(bytesOnce.count)
println(bytesOnce.count)
```

Open the handle in `Stream.fromAcquireRelease` and read it with the unmanaged constructor, so that each run opens a fresh handle and `fromAcquireRelease` alone closes it:

```scala mdoc:silent
val bytesPerRun: Stream[java.io.IOException, Byte] =
  Stream.fromAcquireRelease(Files.newInputStream(ordersPath))(in => Stream.fromInputStreamUnmanaged(in))
```

```scala mdoc
println(bytesPerRun.count)
println(bytesPerRun.count)
```

The same pattern works for a channel with `NioStreams.fromChannelUnmanaged`. In ZIO Streams, `ZStream.fromInputStream` never closes the stream at all.

### Primitive Streams Use End-of-Stream Sentinels

A stream of `Int`, `Long`, `Double`, `Float`, `Byte`, `Short`, `Char` or `Boolean` runs without boxing. Internally, the end of the stream is signalled with an in-band sentinel value, so each element costs one primitive comparison. This design is deliberate, and the library does not add per-element checks to it. It shows up in user code only when you pull by hand with `start` and `Reader#read(sentinel)`: choose a sentinel your stream never produces. The [Reader reference](../reference/streams/primitives/reader.md) describes the primitive read methods.

## What You Gain and What Is Different

You gain:

- **No effect system.** No `IO`, `ZIO`, `IOApp` or runtime. Stages are plain functions and terminals return values, so a stream fits into synchronous code as easily as a collection does.
- **Typed errors without an effect type.** `Stream[E, A]` carries `E`, and every terminal returns `Either[E, Z]`.
- **Primitive lanes with zero boxing.** A `Stream[Nothing, Int]` keeps its elements as `int`s from source to sink. See [zero-boxing](../reference/streams/execution-and-compatibility/zero-boxing.md).
- **Scope integration.** `Stream.fromResource` runs each pass in its own ZIO Blocks `Scope`, and `start` hands out a reader that the scope closes.
- **Wide platform support.** Scala 2.13, Scala 3 and Scala.js, from the same source.

What is different:

- **Blocking and asynchronous terminals.** On the JVM, `runCollect`, `runFold`, `runDrain`, `count`, `run(sink)` and `start` block the calling thread. Every platform has the asynchronous versions (`runCollectAsync`, `runFoldAsync`, `runAsync(sink)`, `countAsync` and so on), which return `Async[Either[E, Z]]`.
- **Scala.js has only the asynchronous terminals.** JavaScript cannot block, so there is no `runCollect` or `start` there, and `mapPar`, `flatMapPar` and `Stream.mergeAll` run sequentially.
- **No time, schedules or environment.** No `Schedule`, timers, `groupedWithin`, throttling or `R` channel.
- **Parallel operators are unordered.** `mapPar` is `mapParUnordered` under another name.
- **No `Pull`, `ZChannel` or fibers.** Custom operators use `mapAccum`, `scan` or `start`, and concurrency lives inside the parallel operators.

On Scala.js, run the stream with an asynchronous terminal and convert the `Async` result to a `Future` with `toFuture` (or to a `js.Promise` with `toJsPromise`). This code compiles on both platforms:

```scala mdoc:silent
val totalsAsync: Async[Either[String, Map[String, Int]]] =
  orders.runAsync(Sink.foldLeft[OrderEvent, Map[String, Int]](Map.empty)(addOrder))

val totalsFuture: Future[Either[String, Map[String, Int]]] = totalsAsync.toFuture
```

On the JVM, `block` waits for the result instead:

```scala mdoc
println(totalsAsync.block)
```

## Migration Checklist

1. Add `zio-blocks-streams` (Scala 2.13, 3.3 to 3.8) or `zio-blocks-next-streams` plus `-experimental` (Scala 3.9+), and `import zio.blocks.streams._`. Run on JDK 21 or later.
2. Replace `Stream[IO, A]`, `ZStream[R, E, A]` or `Flow[A]` with `Stream[E, A]`. Use `Nothing` for `E` when the stream cannot fail.
3. Replace `evalMap(a => IO(f(a)))` and `mapZIO(a => ZIO.attempt(f(a)))` with `map(f)`. Use `Stream.attempt` where the code can throw and you want a typed error.
4. Replace `parEvalMap`, `mapZIOPar` and Ox `mapPar` with `mapPar` (or `mapParAsync` for `Async` code), and sort the output if order matters.
5. Replace `Stream.bracket`, `ZStream.acquireReleaseWith` and `usingEmit` with `try`/`finally` by `Stream.fromAcquireRelease`; replace `Stream.resource` and `ZStream.scoped` with `Stream.fromResource`.
6. Wrap `Stream.fromInputStream` and `NioStreams.fromChannel` in `Stream.fromAcquireRelease` with the `Unmanaged` constructor if the stream may run more than once.
7. Replace `Pipe` and `ZPipeline` with `Pipeline`, `through` with `via`, and `chunkN` with `grouped`.
8. Replace `compile.toList`, `runToList()` and friends with `runCollect`, `runFold`, `runDrain` or `count`, and handle the `Either` they return.
9. Put `catchAll` on the outer stream, not inside `flatMap`, when a stateful operator follows.
10. After a `flatMap`, keep elements as objects through `grouped`, `take`, `scan` and similar operators; map to primitives inside the batches.
11. Run blocking terminals from a `main` method, not from the body of an `object ... extends App`.
12. On Scala.js, use the `*Async` terminals and `toFuture`.
13. Keep fs2, ZIO Streams or Ox for the parts that need schedules, timers, the ZIO environment, fibers or pub-sub.

## Putting It Together

This is the bookstore pipeline fully migrated. `Stream.fromAcquireRelease` opens the order log once per run, so the same stream runs twice: once to report the malformed lines, and once to price the orders in parallel with `mapPar` and fold the revenue per title with `runFold`. The program runs from a `main` method:

```scala mdoc:embed:streams-examples/src/main/scala/streammigration/BookstoreRevenueMigrated.scala
```

## Running the Examples

The complete example is a runnable file in the `streams-examples` project. Clone the repository and change into the project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/streams-examples
```

<details>
  <summary>streams-examples/src/main/scala/streammigration/BookstoreRevenueMigrated.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/streammigration/BookstoreRevenueMigrated.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain streammigration.BookstoreRevenueMigrated"
```

To check that the example compiles without running it:

```bash
sbt compile
```

## See Also

- [Streams](../reference/streams/index.md): the module overview, the mental model and the comparison table.
- [Stream](../reference/streams/core/stream.md), [Sink](../reference/streams/core/sink.md) and [Pipeline](../reference/streams/core/pipeline.md): the full API.
- [Reader](../reference/streams/primitives/reader.md): manual pulls with `start` and the sentinel protocol.
- [Async execution](../reference/streams/execution-and-compatibility/async-execution.md) and [platform differences](../reference/streams/execution-and-compatibility/platform-differences.md): the asynchronous terminals and what changes on Scala.js.
- [Coming from cats-effect Resource, ZIO Scope/ZLayer, macwire or Using](./resource-and-di-migration.md): the resource side of the same migration.
- The tutorial "Build your first streaming pipeline" and the how-to guides "How to stream large query results" and "How to stream files and sockets without leaking resources" are in progress.
