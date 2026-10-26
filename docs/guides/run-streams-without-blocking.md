---
id: run-streams-without-blocking
title: "How to Run Streams Without Blocking"
description: "Swap blocking stream terminals for their Async twins, do async work per element, and run the same pipeline on Scala.js, including a ReadableStream source."
keywords:
  - "Async Terminals"
  - "Non-Blocking Streams"
  - "Scala.js"
  - "ReadableStream"
  - "runFoldAsync"
  - "Stream"
---

In this guide, we total the price of a shelf of Scala books by asking a price service for each title, without parking a thread while we wait. By the end, you will have a pipeline written once against `Stream` and `Async` that runs on the JVM and on Scala.js, and you will know where it is safe to block, how its errors and cancellation behave, and how to read a browser or Node `ReadableStream` such as a `fetch` response body as a stream.

We start from the blocking terminal that causes the trouble, swap it for its `Async` twin, add async work per element with `runForeachAsync` and `runFoldAsync`, and compose the result. Then we look at errors and cancellation, run the same pipeline on Scala.js, and read a `ReadableStream`.

## The Problem

A blocking terminal such as `Stream#runCollect` or `Stream#runFold` makes one promise: when it returns, the stream is finished and the answer is in your hand. On the JVM, the promise is kept by parking the calling thread until the stream completes. If the stream does a remote call per element, the thread sits idle for every call. On Scala.js the promise cannot be kept at all, because the single thread that would wait is the one that has to run the callbacks that deliver the answer.

For example, here is a price total over a list of titles, where each price comes from a call that returns a `Future`. A caller that wants one number back has nothing to do but wait for every price in turn:

```scala mdoc:compile-only
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

def totalBlocking(titles: List[String], price: String => Future[Long]): Long =
  titles.foldLeft(0L)((sum, title) => sum + Await.result(price(title), 5.seconds))
```

This parks the calling thread once per title, one title at a time, so a hundred 100 ms lookups hold a thread for ten seconds doing nothing. A web server that does this per request runs out of threads long before it runs out of CPU. A blocking stream terminal such as `Stream#runCollect` or `Stream#runFold` has the same cost, and on Scala.js it does not even compile, because those terminals do not exist there.

In this guide, we replace each blocking call with an asynchronous one that returns immediately with an `Async` and let the caller decide when, and whether, to wait.

## Prerequisites

To add the streams module to your project, include one of these dependencies in your `build.sbt`. The async module comes with it:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-streams" % "@VERSION@"

// Scala 3.9 and newer (also needs scalacOptions += "-experimental"): the same module with the zio-blocks-next- prefix
libraryDependencies += "dev.zio" %% "zio-blocks-next-streams" % "@VERSION@"
```

For a Scala.js or cross-built project, use `%%%` instead of `%%`. [How to Add ZIO Blocks to Your Build](./add-zio-blocks-to-your-build.md) explains the two artifact names. Streams need JDK 21 at runtime on the JVM.

This guide assumes you have worked through [Your First Streaming Pipeline](./your-first-streaming-pipeline.md) and [Getting Started with Async](./async-getting-started.md). To follow along, we need a stream of books and a price service. `BookPrices.priceInCents` answers after a 100 ms delay on another thread, the way a remote call would, and fails for a title the shop does not stock. The prices are sample data:

```scala mdoc:silent
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams._

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.MILLISECONDS

final case class Book(title: String, authors: String)

object BookPrices {
  val shelf: List[Book] = List(
    Book("Zionomicon", "John A. De Goes, Adam Fraser and Milad Khajavi"),
    Book("Programming in Scala", "Martin Odersky, Lex Spoon and Bill Venners"),
    Book("Functional Programming in Scala", "Paul Chiusano and Runar Bjarnason")
  )

  private val cents: Map[String, Long] = Map(
    "Zionomicon"                      -> 4999L,
    "Programming in Scala"            -> 5995L,
    "Functional Programming in Scala" -> 4499L
  )

  def priceInCents(title: String): Async[Long] =
    Async.fromCompletionStage(
      CompletableFuture.supplyAsync(
        () => cents.getOrElse(title, throw new NoSuchElementException(s"No price for $title")),
        CompletableFuture.delayedExecutor(100, MILLISECONDS)
      )
    )
}
```

`priceInCents` wraps a `CompletableFuture` with `Async.fromCompletionStage`. [How to Wrap Callback and Future-Based APIs in Async](./wrap-callback-and-future-apis.md) covers that bridge and its siblings for `Future`, JavaScript promises and callbacks.

## Swapping a Blocking Terminal for Its Async Twin

Every blocking terminal has a twin whose name ends in `Async`: `Stream#runCollect` becomes `Stream#runCollectAsync`, `Stream#runDrain` becomes `Stream#runDrainAsync`, and `Stream#runFold` becomes `Stream#runFoldAsync`. The twin returns `Async[Either[E, Z]]`: the same `Either` the blocking terminal returns, wrapped in an `Async` that completes later.

To collect the price of every book on the shelf:

```scala mdoc:silent
val prices: Stream[Nothing, Long] =
  Stream.fromIterator(BookPrices.shelf.iterator).mapAsync(book => BookPrices.priceInCents(book.title))

val pending: Async[Either[Nothing, Chunk[Long]]] = prices.runCollectAsync
```

Creating `pending` takes no time, because the terminal returns at once and the work happens when something drives the `Async`. To get the answer, we drive it with `Async#block`:

```scala mdoc
println(pending.block)
```

Running the same stream with the blocking `runCollect` and with `runCollectAsync` and timing the two calls shows the difference:

```text
runCollect returned after 449 ms with Right(Chunk(4999,5995,4499))
runCollectAsync returned after 1 ms, nothing has run yet
the result is Right(Chunk(4999,5995,4499))
```

The blocking call holds its thread for the three lookups; the async call hands back an `Async` immediately. The availability table in [Platform Differences](../reference/streams/execution-and-compatibility/platform-differences.md) lists the replacement for every blocking member.

:::warning
An async terminal does not make a blocking source non-blocking. The `Async` runs on whatever thread drives it, so a source whose pull blocks, such as an iterator that sleeps, a file read or `Stream.fromInputStream`, still blocks that thread. In a probe with an iterator that sleeps 200 ms per element, `runCollectAsync` returns after about 21 ms, and `Async#block` then takes about 654 ms because the sleeping runs inside it. Only sources and callbacks that report completion through an `Async`, like `mapAsync` over `BookPrices.priceInCents`, wait without holding a thread.
:::

## Doing Async Work for Each Element

`mapAsync` lets a stage return an `Async`, but often we only want the final answer. `runForeachAsync` and `runFoldAsync` take a step function that returns an `Async` and run it one element at a time, so no thread waits for the price of a book.

`runForeachAsync` runs an effect for each element and returns `Async[Either[E, Unit]]`. To print each book with its price:

```scala mdoc:silent
val shelf: Stream[Nothing, Book] = Stream.fromIterator(BookPrices.shelf.iterator)

val printed: Async[Either[Nothing, Unit]] =
  shelf.runForeachAsync { book =>
    BookPrices.priceInCents(book.title).map(cents => println(s"${book.title} by ${book.authors}: $cents cents"))
  }
```

To run `printed` and print one line per book:

```scala mdoc
printed.block
```

`runFoldAsync` threads an accumulator through the stream. To total the shelf:

```scala mdoc:silent
val total: Async[Either[Nothing, Long]] =
  shelf.runFoldAsync(0L)((sum, book) => BookPrices.priceInCents(book.title).map(sum + _))
```

To print the total:

```scala mdoc
println(total.block)
```

`runFoldAsync` has an overload for each primitive accumulator, `Double`, `Float`, `Int` and `Long`, and a generic one. The compiler picks the overload from the static type of the zero, which is why we wrote `0L` and not `0`. The primitive overloads keep the accumulator unboxed, so a fold over a primitive total does not allocate a box per step. The `Float` overload has no blocking twin.

The step function runs for one element at a time. Each lookup finishes before the next one starts, so this total takes about 300 ms for three books. To overlap the lookups, use `mapParAsync` on the stream; [Asynchronous Stream Execution](../reference/streams/execution-and-compatibility/async-execution.md) covers the bounded-concurrency operator.

## Using the Result Without Waiting

An `Async` is a value you pass around, so most of the time we do not block at all. We write the pipeline as a function that returns an `Async`, and let the caller compose it:

```scala mdoc:silent
def totalInCents(titles: Stream[Nothing, String]): Async[Either[Nothing, Long]] =
  titles.runFoldAsync(0L)((sum, title) => BookPrices.priceInCents(title).map(sum + _))
```

`Async#map` transforms the result when it arrives, with no thread waiting in between:

```scala mdoc:silent
val titles: Stream[Nothing, String] = Stream.fromIterator(BookPrices.shelf.iterator.map(_.title))

val label: Async[Either[Nothing, String]] =
  totalInCents(titles).map(_.map(cents => f"The shelf costs ${cents / 100.0}%.2f"))
```

To print the label:

```scala mdoc
println(label.block)
```

Code that has not moved to `Async` usually wants a `Future`. On the JVM, `Async#toFuture` takes an implicit `ExecutionContext` and returns one. On Scala.js, `Async#toJsPromise` returns a `js.Promise`. To hand the label to a `Future`-based caller:

```scala mdoc:compile-only
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

val future: Future[Either[Nothing, String]] = label.toFuture
println(Await.result(future, 5.seconds))
```

To run the `Async` in the background and keep a handle that you can join or cancel, use `Async#start`, which returns an `Async.Running`. [Getting Started with Async](./async-getting-started.md#8-forking-and-cancellation-start-and-asyncrunning) covers it.

:::tip
Block only at the outermost edge of a JVM program, such as the end of `main`, where there is nothing left to do but wait. A call to `Async#block` anywhere else holds a thread, which is what we set out to avoid. On Scala.js, `Async#block` returns a value that is already complete and throws `IllegalStateException` when it would have to wait, so there is no edge to block at. The host drives the `Async`, as the next sections show.
:::

## Errors and Cancellation

A stream has two kinds of failure, and the `Async` keeps them apart. A typed error, the `E` of `Stream[E, A]`, arrives as `Left` inside the result. A defect, an exception thrown by user code or a failed `Async` inside a step, fails the `Async` itself.

A typed error lands in the `Either`:

```scala mdoc
val typed: Stream[String, Long] = Stream.fail("the price service is down")
println(typed.runFoldAsync(0L)((sum, cents) => Async.succeed(sum + cents)).block)
```

A defect fails the `Async`. We total the shelf with a title the shop does not stock, and look at it through `Async#either`, then through `Async#block`:

```scala mdoc:silent
val unstocked: Async[Either[Nothing, Long]] =
  shelf.runFoldAsync(0L) { (sum, book) =>
    val title = if (book.title == "Zionomicon") "Zionomicon 2" else book.title
    BookPrices.priceInCents(title).map(sum + _)
  }
```

To see the defect as an `Either` and as a thrown exception:

```scala mdoc
println(unstocked.either.block)
println(scala.util.Try(unstocked.block))
```

`Async#either` moves the failure into an `Either[Throwable, ...]` value, and `Async#block` rethrows it, so one `try` or `Try` at the edge covers every defect.

Cancelling a running terminal stops the pulls and runs the stream's finalizers. To see it, we drain an endless stream of lookups with a finalizer, start it, and cancel it after 350 ms:

```scala mdoc:compile-only
import java.util.concurrent.atomic.AtomicInteger

val pulls     = new AtomicInteger
val finalized = new AtomicInteger

val endless: Stream[Nothing, Long] =
  Stream
    .fromIterator(Iterator.continually(BookPrices.shelf.head))
    .mapAsync { book =>
      pulls.incrementAndGet()
      BookPrices.priceInCents(book.title)
    }
    .ensuring { finalized.incrementAndGet(); () }

val running = endless.runDrainAsync.start
Thread.sleep(350)
running.cancel()
val pullsAtCancel = pulls.get()
Thread.sleep(300)
println(s"pulls at cancel: $pullsAtCancel, pulls 300 ms later: ${pulls.get()}, finalizer ran: ${finalized.get()}")
```

Running it prints:

```text
pulls at cancel: 4, pulls 300 ms later: 4, finalizer ran: 1
```

No pull starts after the cancel, and the finalizer runs exactly once. One thing the cancel does not do is reach inside the lookup that was in flight: a lookup built from a `Future`, `CompletionStage` or JavaScript promise keeps running after you stop waiting for it. [How to Wrap Callback and Future-Based APIs in Async](./wrap-callback-and-future-apis.md#cancel-the-underlying-call-when-the-async-is-cancelled) explains why and how to carry a cancel handle through.

## Running the Same Pipeline on Scala.js

Everything above used only the `*Async` terminals, `Async#map` and `Async.fromCompletionStage`, so only one piece is JVM-specific: the price lookup. The pipeline itself, `ShelfTotalJs.totalInCents`, does not change if we pass the lookup in as a parameter. The Scala.js code in the rest of this guide is not compiled by the docs build; each snippet runs on Node 24 with Scala.js 1.22 on Scala 2.13.18 and 3.9.0.

On Scala.js, the lookup wraps a `js.Promise` with `Async.fromJsPromise`:

```scala
// Scala.js code
import zio.blocks.async._
import zio.blocks.streams._

import scala.scalajs.js

object ShelfTotalJs {
  private val cents: Map[String, Long] = Map(
    "Zionomicon"                      -> 4999L,
    "Programming in Scala"            -> 5995L,
    "Functional Programming in Scala" -> 4499L
  )

  def priceInCents(title: String): Async[Long] =
    Async.fromJsPromise(new js.Promise[Long]((resolve, reject) => {
      js.timers.setTimeout(100) {
        cents.get(title) match {
          case Some(price) => resolve(price)
          case None        => reject(new NoSuchElementException(s"No price for $title"))
        }
      }
      ()
    }))

  def totalInCents(titles: Stream[Nothing, String], price: String => Async[Long]): Async[Either[Nothing, Long]] =
    titles.runFoldAsync(0L)((sum, title) => price(title).map(sum + _))

  def main(args: Array[String]): Unit = {
    val titles = Stream("Zionomicon", "Programming in Scala", "Functional Programming in Scala")

    totalInCents(titles, priceInCents).map(total => println(s"The total is $total")).toJsPromise

    println("main has returned; the total prints when the timers fire")
  }
}
```

Running it under Node prints:

```text
main has returned; the total prints when the timers fire
The total is Right(15493)
```

`main` returns before the first timer fires, which is the point: nothing waits. `Async#toJsPromise` hands the `Async` to the JavaScript event loop, so Node stays alive until the promise settles. In a browser, you would keep the `Async` or the `js.Promise` and update the page in the callback.

Two things differ from the JVM, and the compiler or the runtime tells you at once. A blocking terminal does not exist on Scala.js, so the call fails to compile:

```text
value runCollect is not a member of zio.blocks.streams.Stream[Nothing, Int]
```

`Async#block` does exist in shared source, so it cannot be removed. It returns a value that is already complete and throws when it would have to wait:

```text
A ready value: 1
A pending value: Async.block: suspension did not complete synchronously and JavaScript cannot block. Drive the Pollable from a non-blocking entry point instead.
```

## Reading a ReadableStream as a Source

In a browser or Node 18 and later, a `fetch` response body is a `ReadableStream` of bytes. `ReadableStreamReaders.fromReadableStream` wraps one as a `Reader.AsyncReader[Byte]`, and `Stream.fromReader` turns the reader into a `Stream[E, Byte]`. The adapter is Scala.js-only and byte-only: every chunk the JavaScript stream delivers is a `Uint8Array`, and the stream you get has element type `Byte`. It calls `read()` on the JavaScript stream only when the zio-blocks stream pulls for more bytes, and has at most one read in flight, so a slow consumer slows the reads rather than piling up chunks.

To fetch a catalogue of titles, one per line, and total their prices with the same `ShelfTotalJs.totalInCents`, we first read the bytes and then decode them. The URL is a `data:` URL, so the example needs no network. Node and the browsers fetch it like any other:

```scala
// Scala.js code
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams._

import scala.scalajs.js

object CatalogueJs {
  private val catalogueUrl =
    "data:text/plain,Zionomicon%0AProgramming%20in%20Scala%0AFunctional%20Programming%20in%20Scala%0A"

  def titlesIn(bytes: Chunk[Byte]): Stream[Nothing, String] =
    Stream.fromIterator(new String(bytes.toArray, "UTF-8").split("\n").iterator.filter(_.nonEmpty))

  def main(args: Array[String]): Unit = {
    val response = js.Dynamic.global.fetch(catalogueUrl).asInstanceOf[js.Promise[js.Dynamic]]

    val total: Async[Either[Nothing, Long]] =
      Async.fromJsPromise(response).flatMap { resp =>
        val body = resp.body.asInstanceOf[ReadableStreamReaders.ReadableStream]
        val bytes: Stream[Nothing, Byte] =
          Stream.fromReader[Nothing, Byte](ReadableStreamReaders.fromReadableStream(body))

        bytes.runCollectAsync.flatMap {
          case Right(all) => ShelfTotalJs.totalInCents(titlesIn(all), ShelfTotalJs.priceInCents)
          case Left(e)    => Async.succeed(Left(e))
        }
      }

    total.map(result => println(s"The catalogue costs $result")).toJsPromise
    ()
  }
}
```

```text
The catalogue costs Right(15493)
```

`ReadableStreamReaders` declares its own small `@js.native` facades, `ReadableStreamReaders.ReadableStream` among them, so we cast the `body` of the untyped `fetch` result to that facade. The factory takes the stream and calls `getReader()` itself.

`ReadableStreamReaders.fromReadableStream` owns the stream. When the zio-blocks stream closes early, for example under `take(3)`, the adapter cancels the JavaScript stream and releases its lock. To see it, we give the stream a `cancel` hook and take three of its five bytes:

```scala
// Scala.js code
import java.util.concurrent.atomic.AtomicBoolean

import zio.blocks.async._
import zio.blocks.streams._

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

object EarlyCloseJs {
  def main(args: Array[String]): Unit = {
    val cancelled = new AtomicBoolean(false)

    val source = js.Dynamic.literal(
      start = { (controller: js.Dynamic) =>
        controller.enqueue(new Uint8Array(js.Array[Short](1, 2, 3, 4, 5)))
      }: js.Function1[js.Dynamic, Unit],
      cancel = { (reason: js.Any) =>
        cancelled.set(true)
      }: js.Function1[js.Any, Unit]
    )
    val stream = js.Dynamic
      .newInstance(js.Dynamic.global.ReadableStream)(source)
      .asInstanceOf[ReadableStreamReaders.ReadableStream]

    Stream
      .fromReader[Nothing, Byte](ReadableStreamReaders.fromReadableStream(stream))
      .take(3)
      .runCollectAsync
      .map(result => println(s"take(3) gave $result, stream cancelled: ${cancelled.get()}"))
      .toJsPromise
    ()
  }
}
```

Running it prints:

```text
take(3) gave Right(Chunk(1,2,3)), stream cancelled: true
```

If the stream is not yours to cancel, use `ReadableStreamReaders.fromReadableStreamUnmanaged`, which releases the lock and never cancels, so the code that creates the stream can keep using it. [Reader](../reference/streams/primitives/reader.md#from-native-asynchronous-sources) documents both factories.

:::warning
In 0.0.56, a read that the JavaScript stream rejects, for example a dropped connection, does not arrive as `Left` in the `Either` the way the JVM channel adapter's failures do. It fails the `Async` itself with an internal `StreamError`, whose message is empty, so handle it with `Async#either` or a `catch` at the edge. This is a known bug in 0.0.56:

```text
The read failed: Left(zio.blocks.streams.internal.StreamError)
```

That line is the result of `runCollectAsync.either` on a stream whose second read is rejected with `new js.Error("network reset")`.
:::

## Putting It Together

The complete example puts the pipeline behind a `PriceLookup` function, so the same `receipt` could take the Scala.js lookup. It uses the `Book` case class and the `BookPrices` price service from the support file `BookPrices.scala`, which Running the Examples embeds first. It prints a line per book with `runForeachAsync`, totals the shelf with `runFoldAsync`, joins the two with `Async#flatMap`, and blocks once, at the end of `main`. A second run adds a title the shop does not stock and looks at the failure through `Async#either`. The complete example, embedded from its companion file, follows:

```scala mdoc:embed:streams-examples/src/main/scala/runstreamswithoutblocking/CompleteExample.scala
```

## Running the Examples

Every JVM snippet in this guide has a standalone companion file in the `streams-examples` build. Clone the repository and move into that build:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/streams-examples
```

Every example uses the `Book` case class and the `BookPrices` price service:

<details>
<summary><strong>The Price Service</strong></summary>

The price service and the `Book` case class:

```scala mdoc:embed:streams-examples/src/main/scala/runstreamswithoutblocking/BookPrices.scala:show-line-numbers
```

</details>

<details>
<summary><strong>Swap a Blocking Terminal for Its Async Twin</strong></summary>

The terminal swap, timed against the blocking call:

```scala mdoc:embed:streams-examples/src/main/scala/runstreamswithoutblocking/Concept1SwapTerminalExample.scala:show-line-numbers
```

Run it with:

```bash
sbt "runMain runstreamswithoutblocking.Concept1SwapTerminalExample"
```

</details>

<details>
<summary><strong>Do Async Work for Each Element</strong></summary>

Per-element async work with `runForeachAsync` and `runFoldAsync`:

```scala mdoc:embed:streams-examples/src/main/scala/runstreamswithoutblocking/Concept2PerElementExample.scala:show-line-numbers
```

Run it with:

```bash
sbt "runMain runstreamswithoutblocking.Concept2PerElementExample"
```

</details>

<details>
<summary><strong>Use the Result Without Waiting</strong></summary>

Composing the result and handing it to a `Future`:

```scala mdoc:embed:streams-examples/src/main/scala/runstreamswithoutblocking/Concept3UsingResultExample.scala:show-line-numbers
```

Run it with:

```bash
sbt "runMain runstreamswithoutblocking.Concept3UsingResultExample"
```

</details>

<details>
<summary><strong>Errors and Cancellation</strong></summary>

Typed errors, defects and cancellation:

```scala mdoc:embed:streams-examples/src/main/scala/runstreamswithoutblocking/Concept4ErrorsAndCancellationExample.scala:show-line-numbers
```

Run it with:

```bash
sbt "runMain runstreamswithoutblocking.Concept4ErrorsAndCancellationExample"
```

</details>

<details>
<summary><strong>Complete Example: A Shelf Receipt</strong></summary>

The complete shelf receipt:

```scala mdoc:embed:streams-examples/src/main/scala/runstreamswithoutblocking/CompleteExample.scala:show-line-numbers
```

Run it with:

```bash
sbt "runMain runstreamswithoutblocking.CompleteExample"
```

</details>

To check that every example compiles without running any of them, run:

```bash
sbt compile
```

The `streams-examples` build is JVM-only, so the Scala.js snippets have no companion files. To run one, make a scratch sbt project with `addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")` in `project/plugins.sbt` and this `build.sbt`, copy a snippet into `src/main/scala`, and run it with `sbt run`, which needs Node on your path:

```scala
scalaVersion := "3.9.0"
enablePlugins(ScalaJSPlugin)
scalaJSUseMainModuleInitializer := true
scalacOptions += "-experimental"
libraryDependencies += "dev.zio" %%% "zio-blocks-streams" % "@VERSION@"
```

## Going Further

- [Asynchronous Stream Execution](../reference/streams/execution-and-compatibility/async-execution.md) lists every async constructor, operator and terminal, and [Platform Differences](../reference/streams/execution-and-compatibility/platform-differences.md) lists what exists on each platform.
- [How to Wrap Callback and Future-Based APIs in Async](./wrap-callback-and-future-apis.md) brings `Future`, `CompletionStage`, promise and callback clients into `Async`, with cancellation.
- [How to Stream Files and Sockets Without Leaking Resources](./stream-files-and-sockets.md) covers the JVM sources that do block, and how they close their handles.
- [Reader](../reference/streams/primitives/reader.md#from-native-asynchronous-sources) documents the JVM NIO and Scala.js `ReadableStream` adapters.
