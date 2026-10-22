---
id: wrap-callback-and-future-apis
title: "How to Wrap Callback and Future-Based APIs in Async"
description: "Bring Future, CompletionStage, JS Promise and callback APIs into Async, and cancel the underlying call when the Async is cancelled."
keywords:
  - "Future Interop"
  - "Callback Bridge"
  - "Cancellation"
  - "CompletionStage"
  - "Async"
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

In this guide, we wrap the clients of a small bookstore in `Async`. One client returns a Scala `Future`, one returns a Java `CompletableFuture`, and one reports through a callback and hands back a cancel handle. By the end, you will have a quote pipeline that combines all three in direct style, hands its result back to code that still expects a `Future`, and withdraws the in-flight warehouse request when the `Async` is cancelled.

We take one API shape at a time: a `Future` in and out, then a Java `CompletionStage`, then a JavaScript `Promise`, then callbacks, first with errors and then with cancellation. The guide ends with a table that maps each API shape to its bridge.

## The Problem

Most of the asynchronous code you depend on was written before `Async` existed. Each library picks its own shape: Scala `Future`, Java `CompletionStage`, JavaScript `Promise`, or a plain callback. Gluing these together by hand means converting every result to one shape, and the conversion code is where errors and cancel handles get lost.

For example, here is a stock check that combines a `Future`-based price lookup with a callback-based inventory call, written with nothing but the standard library:

```scala mdoc:compile-only
import scala.concurrent.{ExecutionContext, Future, Promise}

trait Request { def cancel(): Unit }

def priceAndStock(
  lookupPrice: String => Future[BigDecimal],
  stock: (String, Either[Throwable, Int] => Unit) => Request,
  title: String
)(implicit ec: ExecutionContext): Future[(BigDecimal, Int)] = {
  val stockLevel = Promise[Int]()
  val request    = stock(title, result => stockLevel.complete(result.toTry))
  // `request` goes out of scope here: no caller can ever cancel the call.
  for {
    price <- lookupPrice(title)
    level <- stockLevel.future
  } yield (price, level)
}
```

This works until a caller loses interest in the answer. A `Future` cannot be cancelled, so the `Request` handle has nowhere to go, and the inventory call keeps running after nobody needs it. Every new client adds another hand-written `Promise` adapter.

In this guide, we bring each of these shapes into `Async` with one bridge per shape, and we carry the cancel handle all the way through, so cancelling the `Async` cancels the call.

## Prerequisites

To add the async module to your project, include this dependency in your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %%% "zio-blocks-async" % "@VERSION@"

// Scala 3.9 and newer (also needs scalacOptions += "-experimental"): the same module with the zio-blocks-next- prefix
libraryDependencies += "dev.zio" %%% "zio-blocks-next-async" % "@VERSION@"
```

[How to Add ZIO Blocks to Your Build](./add-zio-blocks-to-your-build.md) explains the two artifact names and how to choose one per Scala version. Use `%%%` for a cross-built JVM and Scala.js project, and `%%` for a JVM-only one.

This guide assumes you have worked through [Getting Started with Async](./async-getting-started.md), so `Async#map`, `Async#block`, `Async#either`, `Async.async` with `await`, and `Async#start` are familiar. The [Async reference](../reference/async.md) lists every operation with its full signature.

## The Bookstore Clients

We need three clients that know nothing about `Async`. `PriceService` returns a `Future`, `ShippingQuotes` returns a `CompletableFuture` the way a Java SDK would, and `InventoryClient` takes a callback and returns a `Request` that can be cancelled. `InventoryClient` takes its reply delay as a parameter, so we can build a fast warehouse and a slow one. Prices, stock levels and shipping times are fictional sample data.

The clients, together with the imports the rest of the guide uses:

```scala mdoc:silent
import zio.blocks.async._

import java.util.concurrent.{CompletableFuture, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object PriceService {
  private val prices = Map(
    "Zionomicon"                      -> BigDecimal("49.99"),
    "Programming in Scala"            -> BigDecimal("59.95"),
    "Functional Programming in Scala" -> BigDecimal("44.99")
  )

  def lookup(title: String)(implicit ec: ExecutionContext): Future[BigDecimal] =
    Future(prices.getOrElse(title, throw new NoSuchElementException(s"No price for $title")))
}

object ShippingQuotes {
  def estimateDays(title: String): CompletableFuture[Int] =
    CompletableFuture.supplyAsync { () =>
      if (title.isEmpty) throw new IllegalArgumentException("Title must not be empty")
      if (title.length > 20) 5 else 2
    }
}

final class InventoryClient(replyAfterMillis: Long) {
  private val stockLevels = Map(
    "Zionomicon"                      -> 12,
    "Programming in Scala"            -> 3,
    "Functional Programming in Scala" -> 0
  )
  private val open      = new AtomicInteger(0)
  private val warehouse = Executors.newSingleThreadScheduledExecutor { (r: Runnable) =>
    val t = new Thread(r, "warehouse")
    t.setDaemon(true)
    t
  }

  def openRequests: Int = open.get()

  def stock(title: String)(onDone: Either[Throwable, Int] => Unit): InventoryClient.Request = {
    open.incrementAndGet()
    val reply = warehouse.schedule(
      new Runnable {
        def run(): Unit = {
          open.decrementAndGet()
          onDone(stockLevels.get(title).toRight(new NoSuchElementException(s"Unknown title: $title")))
        }
      },
      replyAfterMillis,
      TimeUnit.MILLISECONDS
    )
    new InventoryClient.Request {
      def cancel(): Unit = if (reply.cancel(false)) open.decrementAndGet()
    }
  }
}

object InventoryClient {
  trait Request {
    def cancel(): Unit
  }
}
```

`InventoryClient#openRequests` counts the questions still waiting for the warehouse. We use it later to check whether a cancelled `Async` left a request behind.

## Bring a Scala `Future` Into `Async`

`Async.fromFuture` turns a `Future[A]` into an `Async[A]`. It needs no `ExecutionContext`: if the future has already completed, the result is a ready value; otherwise the `Async` suspends until the future completes.

To look up a price and use it as an `Async`:

```scala mdoc
val price: BigDecimal = Async.fromFuture(PriceService.lookup("Zionomicon")).block
```

A failed `Future` becomes a failed `Async` carrying the same exception, so the usual error operators apply. To observe the failure as data with `Async#either`, and to recover from it with `Async#catchAll`:

```scala mdoc
println(Async.fromFuture(PriceService.lookup("Scala with Cats")).either.block)

val fallbackPrice: BigDecimal =
  Async
    .fromFuture(PriceService.lookup("Scala with Cats"))
    .catchAll(_ => Async.succeed(BigDecimal(0)))
    .block
```

## Hand an `Async` Back to Future-Based Code

On the JVM, `Async#toFuture` and `Async#toCompletableFuture` go the other way, for callers that still expect a `Future` or a `CompletableFuture`. Both take an implicit `ExecutionContext` that completes the result; neither blocks a thread while the `Async` is pending.

To hand a formatted price to a Scala caller and a raw price to a Java caller:

```scala mdoc:silent
val forScala: Future[String] =
  Async
    .fromFuture(PriceService.lookup("Programming in Scala"))
    .map(p => s"Programming in Scala costs $p")
    .toFuture

val forJava: CompletableFuture[BigDecimal] =
  Async.fromFuture(PriceService.lookup("Functional Programming in Scala")).toCompletableFuture
```

Each caller waits for its result the way it normally would, with `Await.result` for the `Future` and `CompletableFuture#get` for the `CompletableFuture`:

```scala mdoc
println(Await.result(forScala, 5.seconds))
println(forJava.get())
```

A failed `Async` produces a failed `Future` (or a `CompletableFuture` completed exceptionally) with the same exception. On Scala.js, `Async#toFuture` works the same way, and `Async#toJsPromise` produces a `js.Promise` without needing an `ExecutionContext`.

## Bring a Java `CompletionStage` Into `Async`

On the JVM, `Async.fromCompletionStage` accepts any `CompletionStage[A]`, which includes every `CompletableFuture[A]`. Java wraps a failure from a stage in a `CompletionException`. `Async.fromCompletionStage` removes that wrapper, so the `Async` fails with the exception the producer threw.

To bring in a shipping estimate, and to see that a failed estimate arrives unwrapped:

```scala mdoc
val days: Int = Async.fromCompletionStage(ShippingQuotes.estimateDays("Zionomicon")).block

println(Async.fromCompletionStage(ShippingQuotes.estimateDays("")).either.block)
```

## Bring a JavaScript `Promise` Into `Async`

On Scala.js, `Async.fromJsPromise` turns a `js.Promise[A]` into an `Async[A]`. The snippet below is for **Scala.js only**: it is not compiled with this page, because the documentation build runs on the JVM.

To bring in a review count from a JavaScript API (`fetchReviewCount` stands for whatever facade you already have):

```scala
import zio.blocks.async._
import scala.scalajs.js

def fetchReviewCount(title: String): js.Promise[Int] = ???

val reviews: Async[Int] = Async.fromJsPromise(fetchReviewCount("Zionomicon"))
```

A rejected promise becomes a failed `Async`, and `Async#toJsPromise` hands an `Async` back to JavaScript code.

## Wrap a Callback That Reports Errors

The [callback bridging section of the tutorial](./async-getting-started.md#7-callback-bridging-asyncpromise-and-completer) completes a `Completer` with `Completer#succeed`. Real callbacks also report failures. Call `Completer#fail` on the error branch, and the failure travels through `Async` like any other: `Async#either`, `Async#catchAll`, and a rethrow from `Async#block` all see it.

To wrap `InventoryClient#stock`, whose callback receives an `Either`, the body takes the `Completer` as `c =>` on Scala 2 and `c ?=>` on Scala 3:

<Tabs groupId="scala-version" defaultValue="scala2">
<TabItem value="scala2" label="Scala 2">

```scala
def stockOf(client: InventoryClient, title: String): Async[Int] =
  Async.promise[Int] { c =>
    client.stock(title) {
      case Right(level) => c.succeed(level)
      case Left(error)  => c.fail(error)
    }
    () // the Request handle is dropped here
  }
```

</TabItem>
<TabItem value="scala3" label="Scala 3">

```scala mdoc:silent
def stockOf(client: InventoryClient, title: String): Async[Int] =
  Async.promise[Int] { c ?=>
    client.stock(title) {
      case Right(level) => c.succeed(level)
      case Left(error)  => c.fail(error)
    }
    () // the Request handle is dropped here
  }
```

</TabItem>
</Tabs>

To check a title the warehouse knows, and one it does not, we first build a fast warehouse:

```scala mdoc:silent
val inventory = new InventoryClient(replyAfterMillis = 10)
```

With a warehouse that replies after 10 milliseconds, one lookup succeeds and the other fails:

```scala mdoc
println(stockOf(inventory, "Zionomicon").block)

println(stockOf(inventory, "Scala with Cats").either.block)
```

The `Completer` is one-shot: the first `Completer#succeed` or `Completer#fail` wins, and a callback that fires again is ignored.

## Cancel the Underlying Call When the `Async` Is Cancelled

`stockOf` throws away the `Request` that `InventoryClient#stock` returns, and `Async.promise` has no place to put it. So cancelling the `Async` stops the `Async` from waiting, but the warehouse request stays open.

To see the leftover request, we build a warehouse that takes a minute to reply:

```scala mdoc:silent
val slowWarehouse = new InventoryClient(replyAfterMillis = 60000)
```

Starting the lookup and cancelling it straight away leaves the warehouse request open:

```scala mdoc
stockOf(slowWarehouse, "Zionomicon").start.cancel()
println(s"Open warehouse requests after cancel: ${slowWarehouse.openRequests}")
```

You rarely need to extend `Pollable` directly. It is the low-level extension point for custom suspended computations, and `Async.promise` covers callbacks that cannot be cancelled. A callback with a cancel handle is the case for a custom `Pollable`: when a running `Async` is cancelled, the driver calls `Pollable#cancel` on the pending `Pollable` it is waiting on. The `Completer` that `Async.promise` waits on inherits the default `Pollable#cancel`, which does nothing.

`cancellable` is not part of ZIO Blocks; it is a helper you add to your own code. To build it, keep a `Completer` for the result and forward cancellation to the handle:

```scala mdoc:silent
def cancellable[A](register: Completer[A] => (() => Unit)): Async[A] =
  new Pollable[A] {
    private val result     = new Completer[A]
    private val cancelCall = register(result)

    def poll(onComplete: Runnable): Async[A] = {
      val polled = result.poll(onComplete)
      // While pending, answer with this Pollable rather than the Completer, so
      // the driver keeps waiting on us and our cancel() is the one it calls.
      if (polled.asInstanceOf[AnyRef] eq result) this else polled
    }

    override def cancel(): Unit = cancelCall()
  }
```

`register` runs when `cancellable` is called, just as the body of `Async.promise` does. It starts the call, wires the callback to the `Completer`, and returns a function that cancels the call. `Pollable#poll` delegates to `Completer#poll` but answers with `this` while the result is pending. If it answers with the `Completer` instead, the driver waits on the `Completer` from then on, and cancellation reaches the no-op `Completer#cancel` rather than ours.

With the helper in place, a cancellable `stockOf` returns `InventoryClient.Request#cancel` instead of dropping the handle:

```scala mdoc:silent
def cancellableStockOf(client: InventoryClient, title: String): Async[Int] =
  cancellable[Int] { c =>
    val request = client.stock(title) {
      case Right(level) => c.succeed(level)
      case Left(error)  => c.fail(error)
    }
    () => request.cancel()
  }

val anotherSlowWarehouse = new InventoryClient(replyAfterMillis = 60000)
```

To repeat the experiment, and to check that the happy path is unchanged:

```scala mdoc
cancellableStockOf(anotherSlowWarehouse, "Zionomicon").start.cancel()
println(s"Open warehouse requests after cancel: ${anotherSlowWarehouse.openRequests}")

println(cancellableStockOf(inventory, "Programming in Scala").block)
```

Cancellation reaches the helper through `Async#map` and through `Async.async` blocks too, because each combinator forwards the cancel signal to the computation it is waiting on.

:::warning
`Async.fromFuture`, `Async.fromCompletionStage` and `Async.fromJsPromise` do not cancel their source. Cancelling the `Async` stops it from waiting, but the `Future`, stage or promise keeps running. A `Future` and a `js.Promise` cannot be cancelled at all. For a `CompletableFuture` you want to cancel, use `cancellable`: register `CompletableFuture#whenComplete` to complete the `Completer`, and return `() => future.cancel(true)`. Unlike `Async.fromCompletionStage`, this bridge does not remove a `CompletionException` wrapper for you.
:::

## Putting It Together

The complete example uses the three clients from `Bookstore.scala`, which the Running the Examples section embeds, and quotes a book by combining them in an `Async.async` block: the price from the `Future`-based service, the stock level through the `cancellable` bridge, and the shipping estimate from the `CompletableFuture`. It hands each quote back as a `Future` to a storefront that has not moved to `Async`, recovers from unknown titles, and cancels one quote to show that its warehouse request is withdrawn:

```scala mdoc:embed:async-examples/src/main/scala/zio/blocks/async/wrapcallbackandfutureapis/CompleteExample.scala
```

## Choosing a Bridge

Each API shape has one bridge into `Async`. The last column says what cancelling the resulting `Async` does to the underlying work:

| API shape                             | Bridge into `Async`                                               | Cancelling the `Async`                       |
|---------------------------------------|-------------------------------------------------------------------|----------------------------------------------|
| Scala `Future[A]`                     | `Async.fromFuture(future)`                                        | Stops waiting; the `Future` runs on          |
| Java `CompletionStage[A]` (JVM)       | `Async.fromCompletionStage(stage)`                                | Stops waiting; the stage runs on             |
| JavaScript `js.Promise[A]` (Scala.js) | `Async.fromJsPromise(promise)`                                    | Stops waiting; the promise runs on           |
| Callback with no cancel handle        | `Async.promise` and `Completer`                                   | Stops waiting; the call runs on              |
| Callback that returns a cancel handle | A `Pollable` that overrides `Pollable#cancel`, like `cancellable` | Cancels the call                             |
| Status you check repeatedly (polling) | A `Pollable` whose `Pollable#poll` checks the status              | Whatever its `Pollable#cancel` override does |

To go back the other way, use `Async#toFuture` and, on the JVM, `Async#toCompletableFuture`, or `Async#toJsPromise` on Scala.js. For the polling shape, the [`Pollable` section of the reference](../reference/async.md#pollable) walks through a job-status example.

## Running the Examples

Every snippet in this guide has a standalone companion file in the `async-examples` build. Clone the repository and move into that build:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/async-examples
```

To check that every example compiles without running any of them:

```bash
sbt compile
```

Each example uses the sample clients from one shared file:

<details>
<summary><strong>Sample Clients</strong></summary>

```scala mdoc:embed:async-examples/src/main/scala/zio/blocks/async/wrapcallbackandfutureapis/Bookstore.scala:show-line-numbers
```

</details>

<details>
<summary><strong>Bring a Scala Future Into Async</strong></summary>

```scala mdoc:embed:async-examples/src/main/scala/zio/blocks/async/wrapcallbackandfutureapis/Step1FromFutureExample.scala:show-line-numbers
```

Run it with:

```bash
sbt "runMain zio.blocks.async.wrapcallbackandfutureapis.Step1FromFutureExample"
```

</details>

<details>
<summary><strong>Hand an Async Back to Future-Based Code</strong></summary>

```scala mdoc:embed:async-examples/src/main/scala/zio/blocks/async/wrapcallbackandfutureapis/Step2ToFutureExample.scala:show-line-numbers
```

Run it with:

```bash
sbt "runMain zio.blocks.async.wrapcallbackandfutureapis.Step2ToFutureExample"
```

</details>

<details>
<summary><strong>Bring a Java CompletionStage Into Async</strong></summary>

```scala mdoc:embed:async-examples/src/main/scala/zio/blocks/async/wrapcallbackandfutureapis/Step3CompletionStageExample.scala:show-line-numbers
```

Run it with:

```bash
sbt "runMain zio.blocks.async.wrapcallbackandfutureapis.Step3CompletionStageExample"
```

</details>

<details>
<summary><strong>Wrap a Callback That Reports Errors</strong></summary>

```scala mdoc:embed:async-examples/src/main/scala/zio/blocks/async/wrapcallbackandfutureapis/Step4CallbackErrorsExample.scala:show-line-numbers
```

Run it with:

```bash
sbt "runMain zio.blocks.async.wrapcallbackandfutureapis.Step4CallbackErrorsExample"
```

</details>

<details>
<summary><strong>Cancel the Underlying Call</strong></summary>

```scala mdoc:embed:async-examples/src/main/scala/zio/blocks/async/wrapcallbackandfutureapis/Step5CancellableCallbackExample.scala:show-line-numbers
```

Run it with:

```bash
sbt "runMain zio.blocks.async.wrapcallbackandfutureapis.Step5CancellableCallbackExample"
```

</details>

<details>
<summary><strong>Complete Example: Book Quotes</strong></summary>

```scala mdoc:embed:async-examples/src/main/scala/zio/blocks/async/wrapcallbackandfutureapis/CompleteExample.scala:show-line-numbers
```

Run it with:

```bash
sbt "runMain zio.blocks.async.wrapcallbackandfutureapis.CompleteExample"
```

</details>

## Going Further

- The [Async reference](../reference/async.md) covers the [`Pollable` protocol](../reference/async.md#the-pollable-protocol), [`Completer`](../reference/async.md#completer), and [`Running#cancel`](../reference/async.md#runningcancel) in full.
- [Asynchronous Stream Execution](../reference/streams/execution-and-compatibility/async-execution.md) shows how `Async` sources feed streams.
- [Compile-Time Resource Safety with Scope](./compile-time-resource-safety-with-scope.md) ties `Async.Running` handles, which are `AutoCloseable`, to lexical scopes so they are cancelled on exit.
