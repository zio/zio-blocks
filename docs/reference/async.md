---
id: async
title: "Async"
description: "Reference for the ZIO Blocks async module: Async[A], Pollable, Completer, Async.Running, and Cancelable."
keywords:
  - "Asynchronous Effects"
  - "Lazy Computation"
  - "Callback Bridge"
  - "Structured Cancellation"
  - "Async"
  - "Pollable"
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

The `async` module provides `Async[A]` — a lazy, zero-dependency asynchronous effect type for Scala 2.13 and Scala 3, targeting both JVM and Scala.js. 

An `Async[A]` value is a *description* of a computation that either yields an `A` or fails with a `Throwable`; nothing executes until you drive it with `Async#block`, `Async#start`, or a platform interop converter. 

Conceptually, an `Async[A]` is one of three things — a value that is already available, a failure that has already happened, or a computation that will complete later:

```scala
// The mental model, not the real encoding.
enum Async[+A]:
  case Ready(value: A)                 // already available
  case Failed(cause: Throwable)        // already failed
  case Suspended(source: Pollable[A])  // completes later, via a callback
```

The real definition is a type alias whose runtime representation is `Any`. That is a performance decision, not a modelling one: a ready value *is* its own `Async`, so the common path allocates nothing and boxes nothing. Reach for the three cases above when reasoning about behaviour, and let the encoding stay invisible — no combinator in this module requires you to know it.

Suspension is where the other types enter. A `Pollable[A]` is the extension point that produces a not-yet-available result, [`Completer`](#completer) is the ready-made `Pollable` for bridging callbacks, [`Async.Running`](#asyncrunning) is a `Pollable` you can also cancel, and [`Cancelable`](#cancelable) is that cancellation interface on its own.

## Motivation

`Async[A]` targets infrastructure-level code that needs a first-class, lazy async description with zero external dependencies. Several properties distinguish it from heavier alternatives:

- It is **lazy** — constructing an `Async[A]` produces only a description; execution begins only when you explicitly drive it.
- It **stays cheap when there is nothing to wait for** — chaining operations onto an already-available value runs them directly, with none of the wrapper objects most effect types allocate. Using `Async[A]` on a mostly-synchronous path costs close to nothing.
- It provides **structured cancellation** — `Async.Running` carries a synchronous, idempotent `Cancelable` handle.
- It runs on both **JVM and Scala.js** with platform-appropriate interop for `Future`, `CompletionStage`, and `js.Promise`.

## Installation

To add the module to your build:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-async" % "@VERSION@"
```

## Overview

`Async[A]` is the type you will spend nearly all your time with. Every way of creating a value, every way of transforming one, and every way of running one produces or consumes an `Async[A]`. If you only learn this type, you can already write complete programs — the rest of the module exists to feed values into it or to control one that is already running.

`Completer[A]` is the type you reach for next, and the reason is a problem you have almost certainly hit: some library hands you a result through a callback rather than returning it. `Async.promise` gives you a `Completer`, you pass that to the callback, and you get back an `Async[A]` that completes when the callback fires. From that point on it behaves like any other `Async[A]`, so the callback-based API disappears into ordinary code.

`Async.Running[A]` appears when you start work without waiting for it. Calling `.start` on an `Async[A]` begins the computation immediately and hands you a `Running` as a receipt. Keep it, and you can wait for the result later, run several pieces of work at once and collect them all, or stop the work early.

`Cancelable` is that last ability on its own — a single way to say "stop this." `Async.Running` provides it, and so can anything else you write that needs to be stoppable.

`Pollable[A]` is the one type most programs never touch. It is the extension point for teaching the module about a brand-new source of delayed results — a timer, a socket read, a platform-specific callback. Implementing one makes your source usable anywhere an `Async[A]` is expected. Reach for it only when you are wiring up something genuinely new; for ordinary callback bridging, `Async.promise` and a `Completer` are the right tools.

## How They Work Together

A computation moves through four phases: construct leaf values, compose them, drive the result, then observe or cancel, as shown in the following flow diagram:

```
┌─ 1. CONSTRUCT — describe the work ─────────────────────────────┐
│   Async.succeed(a)       a value you already have              │
│   Async.fail(t)          a failure you already have            │
│   Async.attempt { … }    a block that might throw              │
│   Async.promise { … }    gives you a Completer to call later   │
│   new Pollable[A] { … }  your own source of results (rare)     │
└────────────────────────────────────────────────────────────────┘
                          │
                          ▼
            Async[A]  ──  a description; nothing has run yet
                          │
                          ▼
┌─ 2. COMPOSE — build a bigger description ──────────────────────┐
│   map    flatMap    zipWith    tap                             │
│   catchAll    ensuring    collectAll                           │
│                                                                │
│   each returns a new Async[A] — still nothing has run          │
└────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─ 3. DRIVE — now it runs ───────────────────────────────────────┐
│   .block             .start            .toFuture               │
│   wait right here    run in the        .toJsPromise            │
│                      background        hand it to the          │
│        │                  │            platform                │
│        ▼                  ▼                                    │
│   the A, or          Async.Running[A]                          │
│   the Throwable      — your receipt                            │
└────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─ 4. OBSERVE or CANCEL — using the receipt ─────────────────────┐
│   .block  .flatMap  .zipWith    wait for it, or compose more   │
│   .cancel()                     stop it (via Cancelable)       │
└────────────────────────────────────────────────────────────────┘
```

Three of these types are closely related, and seeing why makes the module much smaller than it first looks. `Pollable[A]` answers one question — *is the result ready yet?* — and anything that can answer it is a `Pollable`:

```
Pollable[A]   —  "a result that is not here yet"
   │
   ├── Completer[A]       you complete it yourself, once, from a callback
   ├── Async.Running[A]   work that is already running; can also be stopped
   └── Failure            a computation that has already failed
```

That shared parent is what lets all three be used interchangeably. Wherever an `Async[A]` is expected, you can supply any of them, and every combinator — `map`, `flatMap`, `zipWith`, and the rest — works on the result without knowing or caring which one it is.

`Failure` is on the list because failing is just another way of being finished. Once a computation has failed, `map` and `flatMap` stop doing work and pass the error straight through, so the failure travels to the end of your chain untouched.

You will use `Completer` and `Async.Running` constantly, and `Failure` mostly without naming it. Writing your own `Pollable` is the rare case, reserved for teaching the module about a new source of delayed results.

The four phases in detail:

1. **Construct** — create leaf values with `Async.succeed`, `Async.fail`, `Async.attempt`, or `Async.promise` (which supplies a `Completer[A]` to a callback body). Custom `Pollable` subclasses handle timers and socket reads.
2. **Compose** — chain with `map`, `flatMap`, `catchAll`, `zipWith`, `tap`, `ensuring`, and `collectAll`. On the ready path these are allocation-free; on the suspended path they allocate a `Pollable` continuation that the driver traverses poll by poll.
3. **Drive** — consume with `block` (park the calling thread), `start` (dispatch to a background worker; returns `Async.Running[A]`), or an interop converter (`toFuture`, `toJsPromise`).
4. **Observe or cancel** — the `Async.Running[A]` returned by `start` can be polled for fan-out, cancelled with `Cancelable#cancel`, or composed further with `flatMap` and `zipWith`.

The following snippet grounds all four phases in a real test: two off-thread `Completer` completions are composed with `zipWith` and driven by `block`:

```scala mdoc:compile-only
import zio.blocks.async._

def realAsync[A](value: A, ms: Long): Async[A] = {
  val c = new Completer[A]
  val t = new Thread(new Runnable {
    def run(): Unit = { Thread.sleep(ms); c.succeed(value) }
  })
  t.setDaemon(true)
  t.start()
  c.peek  // the Completer is itself an Async[A]
}

// Phase 1 and 2: construct two off-thread leaves and compose with zipWith
val r: Async[Int] = (realAsync(3, 30): Async[Int]).zipWith(realAsync(4, 5): Async[Int])(_ + _)

// Phase 3: drive — parks the calling thread until both off-thread wakers fire
val result: Int = r.block  // => 7
```

A second example shows what `Async.collectAll` guarantees: the results come back in the order you listed the computations, not the order they happened to finish. To make that visible, the delays below are deliberately reversed — the first element takes the longest, the last finishes almost immediately:

```scala mdoc:compile-only
import zio.blocks.async._

// Completes with `value` after `ms`, on another thread.
def delayed[A](value: A, ms: Long): Async[A] = {
  val c = new Completer[A]
  val t = new Thread(new Runnable {
    def run(): Unit = { Thread.sleep(ms); c.succeed(value) }
  })
  t.setDaemon(true)
  t.start()
  c.peek
}

val ordered: Async[List[Int]] = Async.collectAll(List[Async[Int]](
  delayed(1, 90),  // finishes third
  delayed(2, 45),  // finishes second
  delayed(3, 5)    // finishes first
))

// Completion order is 3, 2, 1 — the list is still 1, 2, 3.
val results: List[Int] = ordered.block  // => List(1, 2, 3)
```

Without that guarantee you would have to tag each computation and re-sort the results yourself. Because `collectAll` keeps the positions, you can zip the output against the input list — or pattern-match on it positionally — and trust that element *n* belongs to computation *n*.

## Common Patterns

The five patterns below address the most frequent tasks: bridging callbacks, writing sequential-looking code, guaranteeing cleanup, sharing in-flight computations, and collecting parallel results.

### Callback Bridge

Callback-based APIs all share one shape. Instead of returning the result, they return `Unit` immediately and call one of two functions you hand them once the work is done:

```scala mdoc:compile-only
// The API you are stuck with. A real one calls back later, from
// another thread; the shape is what matters here.
def legacyApi(onSuccess: String => Unit, onError: Throwable => Unit): Unit =
  onSuccess("done")
```

That signature is the problem. Because `legacyApi` returns `Unit`, there is no value to return from your own function, nothing to pass to another function, and no way to say "do this, then that" — the result only ever appears inside a callback body, so the rest of your program has to be written in there too.

`Async.promise` inverts it. It gives you a `Completer[A]`, which is a value that can be completed later, and hands you back an `Async[A]` representing the eventual result. You pass the completer's two methods where `legacyApi` expects its two functions. The body is written slightly differently on each Scala version — `c =>` on Scala 2, `c ?=>` on Scala 3, for the reason explained under [`Completer`](#completer):

<Tabs groupId="scala-version" defaultValue="scala2">
<TabItem value="scala2" label="Scala 2">

```scala
import zio.blocks.async._

def legacyApi(onSuccess: String => Unit, onError: Throwable => Unit): Unit =
  onSuccess("done")

val async: Async[String] = Async.promise[String] { c =>
  legacyApi(
    result => c.succeed(result),
    err    => c.fail(err)
  )
}
val result: String = async.block  // blocks until the callback fires
```

</TabItem>
<TabItem value="scala3" label="Scala 3">

```scala mdoc:compile-only
import zio.blocks.async._

def legacyApi(onSuccess: String => Unit, onError: Throwable => Unit): Unit =
  onSuccess("done")

val async: Async[String] = Async.promise[String] { c ?=>
  legacyApi(
    result => c.succeed(result),
    err    => c.fail(err)
  )
}
val result: String = async.block  // blocks until the callback fires
```

</TabItem>
</Tabs>

The two lines inside `legacyApi` are the whole bridge: whichever callback fires, it completes `c`, and completing `c` completes the `Async[String]`. Note what has been gained — `async` is an ordinary value. You can return it, store it, or chain `map` and `flatMap` onto it, and the callback API is no longer visible to anything downstream.

The first call to `Completer#succeed` or `Completer#fail` wins; later calls do nothing. That makes the bridge safe against callbacks that fire twice or APIs that retry internally — a common hazard when wrapping code you do not control.

### Direct Style

Inside `Async.async { ... }`, use `await` to extract values from `Async` computations in sequential-looking code without explicit `flatMap` chains:

```scala mdoc:compile-only
import zio.blocks.async._

case class Order(id: Int, userId: Int)
case class User(id: Int, name: String, tier: String)
case class Shipment(orderId: Int, carrier: String)

def fetchOrder(id: Int): Async[Order]      = Async.succeed(Order(id, 1))
def fetchUser(id: Int): Async[User]        = Async.succeed(User(id, "sam", "gold"))
def fulfill(orderId: Int): Async[Shipment] = Async.succeed(Shipment(orderId, "express"))

def fulfillOrGuest(orderId: Int): Async[String] = Async.async {
  val order    = fetchOrder(orderId).catchAll(_ => fetchOrder(9001)).await
  val user     = fetchUser(order.userId)
                   .catchAll(_ => Async.succeed(User(0, "guest", "bronze"))).await
  val shipment = fulfill(order.id).await
  s"shipped ${shipment.orderId} for ${user.name} via ${shipment.carrier}"
}
val result: String = fulfillOrGuest(9001).block
```

Awaits run in source order; a failed `Async[A]` under `await` propagates as `Async.fail`.

Nothing new happens at runtime here. `Async.async` rewrites its body at compile time: the block is split at each `await` and reassembled into the `flatMap` chain you would have written by hand. The example above compiles to roughly this:

```scala
fetchOrder(orderId).catchAll(_ => fetchOrder(9001)).flatMap { order =>
  fetchUser(order.userId)
    .catchAll(_ => Async.succeed(User(0, "guest", "bronze")))
    .flatMap { user =>
      fulfill(order.id).map { shipment =>
        s"shipped ${shipment.orderId} for ${user.name} via ${shipment.carrier}"
      }
    }
}
```

So `await` is not a method that blocks or waits. It is a marker the rewrite removes, and everything after it becomes the continuation that runs once the value arrives. Direct style therefore costs nothing over writing the chain yourself — by the time the code runs, it *is* that chain. Choose whichever reads better.

One consequence is worth remembering: `await` only means something inside an `Async.async` block. Elsewhere there is no rewrite to remove it, so the call survives to runtime and throws.

The rewrite is performed by `dotty-cps-async` on Scala 3 — or by native `js.async` and `js.await` on Scala.js 3.8 and later — and by a built-in `scala-reflect` macro on Scala 2.13. Both Scala versions support direct style, and neither needs an extra dependency.

### Bracket / Ensuring

Some work has to happen no matter what: closing a file, releasing a connection, deleting a temporary directory. In ordinary code you write that in a `finally` block. `ensuring` is the same idea for `Async` — you attach a cleanup step, and it runs once the computation settles, whether it produced a value or failed:

```scala
import zio.blocks.async._

val result: Async[String] =
  Async.attempt(openResource()).flatMap { res =>
    Async.attempt(res.read()).ensuring(Async.attempt(res.close()))
  }
```

Here `res.close()` runs after `res.read()` finishes, and the value of `result` is whatever the read produced. That is the rule worth remembering: **the cleanup never changes the answer.** It cannot turn a failure into a success, and it cannot turn a success into a failure.

That last part raises an obvious question — what if the cleanup itself fails? Closing a file can throw too. The answer depends on how the main computation ended, so it is worth seeing both cases:

```scala mdoc:compile-only
import zio.blocks.async._

// Both fail: reading the resource, and then closing it.
val bothFail: Async[String] =
  Async
    .attempt[String](throw new RuntimeException("read failed"))
    .ensuring(Async.attempt(throw new IllegalStateException("close failed")))

bothFail.either.block match {
  case Left(e) =>
    println(e.getMessage)                     // read failed   <- the original failure
    println(e.getSuppressed()(0).getMessage)  // close failed  <- attached to it
  case Right(_) => ()
}

// Only the cleanup fails.
val readOk: Async[String] =
  Async
    .succeed("contents")
    .ensuring(Async.attempt(throw new IllegalStateException("close failed")))

val value: String = readOk.block  // "contents" — the close failure is gone
```

In the first case the read had already failed, so you get the read's exception — the one that explains what actually went wrong. The close error is not thrown away, though: it is carried along inside that exception, in a list the JVM keeps for exactly this purpose. `getSuppressed` returns that list. Your logging framework almost certainly prints it, usually under a line beginning `Suppressed:`, so both problems end up on the page.

The second case is the one to watch. The read succeeded, so there is no exception to carry the close error, and it is simply dropped — `readOk.block` returns `"contents"` and you never hear that closing failed. If a cleanup error matters to you on the success path, catch it inside the cleanup step itself and log it there:

```scala
Async
  .attempt(res.read())
  .ensuring(Async.attempt(res.close()).catchAll { t =>
    Async.succeed(logger.warn("close failed", t))
  })
```

### Concurrent Fan-Out via Running

Suppose one expensive computation feeds several parts of your program — a report that is both summarised and emailed, say. The obvious approach is to describe the work once and use that description in both places, but a description is a recipe, not a result: each place that drives it cooks the meal again. You want the work to happen once, in the background, with everyone reading the same outcome.

`Async.start` does that. It hands the body to a background worker and returns immediately with an `Async.Running[A]` — a handle to work already in flight:

```scala mdoc:compile-only
import zio.blocks.async._

def heavyComputation(): Int = { Thread.sleep(50); 42 }

// Returns straight away; the work proceeds on a background worker.
val running: Async.Running[Int] = Async.start(heavyComputation())

// The handle is itself an Async[Int], so it composes like anything else.
val doubled: Async[Int]    = running.map(_ * 2)
val labelled: Async[String] = running.map(n => s"got $n")

val a: Int    = doubled.block   // 84
val b: String = labelled.block  // "got 42" — heavyComputation ran once, not twice
```

Both consumers see the same settled outcome, because they share one running computation rather than one recipe. `Async.Running[A]` is a subtype of `Async[A]`, so it works with `map`, `flatMap`, and `zipWith` without conversion, and `running.cancel()` stops the work early — a no-op if it has already finished.

Take care to start the work the right way round, because the wrong version looks almost identical:

```scala
Async.start(heavyComputation())            // ✅ the worker evaluates it
Async.attempt(heavyComputation()).start    // ❌ already evaluated, on this thread
```

Both lines compile, and both hand you an `Async.Running[Int]`. Only the first one runs anything in the background.

The difference is *when the argument gets evaluated*. Scala normally evaluates an argument before passing it, so in `Async.attempt(heavyComputation())` the computation runs first — on your own thread, right at that line — and `attempt` merely wraps the answer it produced. Tacking `.start` on afterwards cannot un-run it; there is nothing left to move to a worker.

`Async.start` is declared differently. Its parameter is `body: => A`, and that `=>` means "don't evaluate this yet — hand me the code and I will run it when I am ready." It passes the code to a worker thread, which is why the call returns immediately.

The clock shows it plainly:

```scala
Async.start(heavyComputation())            // returns in about 0 ms
Async.attempt(heavyComputation()).start    // returns in about 50 ms — you waited for it
```

So: use `Async.start` for work you want moved off the calling thread. Use `fa.start` when `fa` is an `Async` you have already built and composed and now want driven.

### Batch Collection

Use `Async.collectAll` to sequence a list of `Async` values and gather results into a `List[A]` in input order:

```scala
import zio.blocks.async._

val batch: Async[List[Int]] = Async.collectAll(List(
  Async.attempt(compute(1)),
  Async.attempt(compute(2)),
  Async.attempt(compute(3))
))
val results: List[Int] = batch.block  // => List(r1, r2, r3) in input order
```

The first failure short-circuits and remaining elements are not driven. Already-ready lists take an optimized path that skips allocating a sequencing continuation.

## Integration Points

You are unlikely to be starting from scratch. Your codebase probably already returns `Future`s, calls a Java library that returns a `CompletionStage`, or talks to a JavaScript API that returns a `Promise`. `Async` is built to sit next to those, so you can adopt it in one part of a program without rewriting everything around it.

Conversions go in both directions, and none of them blocks a thread:

| You have                   | Bring it in with                | You need                     | Hand it out with         |
|:---------------------------|:--------------------------------|:-----------------------------|:-------------------------|
| `Future[A]`                | `Async.fromFuture(f)`           | `Future[A]`                  | `fa.toFuture`            |
| `CompletionStage[A]` (JVM) | `Async.fromCompletionStage(cs)` | `CompletableFuture[A]` (JVM) | `fa.toCompletableFuture` |
| `js.Promise[A]` (Scala.js) | `Async.fromJsPromise(p)`        | `js.Promise[A]` (Scala.js)   | `fa.toJsPromise`         |

A round trip through `Future` looks like this — take what an existing service hands you, work with it as an `Async`, and give a `Future` back to a caller who still expects one:

```scala mdoc:compile-only
import zio.blocks.async._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

// The service you already have.
def loadUserName(id: Int): Future[String] = Future.successful("sam")

// Bring it in, work with it as an Async, hand a Future back out.
def greet(id: Int): Future[String] =
  Async
    .fromFuture(loadUserName(id))
    .map(name => s"hello, $name")
    .catchAll(_ => Async.succeed("hello, guest"))
    .toFuture
```

Java's `CompletionStage` works the same way:

```scala mdoc:compile-only
import zio.blocks.async._
import java.util.concurrent.{CompletableFuture, CompletionStage}
import scala.concurrent.ExecutionContext.Implicits.global

def fetchToken(): CompletionStage[String] = CompletableFuture.completedFuture("t-123")

val token: Async[String]                 = Async.fromCompletionStage(fetchToken())
val backToJava: CompletableFuture[String] = token.map(_.toUpperCase).toCompletableFuture
```

On Scala.js the pair is `fromJsPromise` and `toJsPromise`:

```scala
import zio.blocks.async._
import scala.scalajs.js

def fetchJson(url: String): js.Promise[String] = js.native

val parsed: Async[String]       = Async.fromJsPromise(fetchJson("/api/config"))
val handedBack: js.Promise[String] = parsed.map(_.trim).toJsPromise
```

Two details are worth knowing before you use them.

Handing a value out to `Future` or `CompletableFuture` needs an `ExecutionContext` in scope, exactly as ordinary `Future` code does — usually `import scala.concurrent.ExecutionContext.Implicits.global`, as above, or whichever one your application already provides. `toJsPromise` needs nothing, because JavaScript has a single built-in event loop to run the callback on.

Failures survive the trip. That takes some care on the Java side: when a `CompletionStage` fails, Java wraps your exception in a `CompletionException` before handing it over. `fromCompletionStage` unwraps it, so the `Async` fails with the exception you actually threw rather than with Java's wrapper — which means `catchAll` sees what you expect.

Two further integration points are worth knowing about:

**Cancelling with `Using`.** `Async.Running` is an `AutoCloseable` — `Cancelable` extends it — so `scala.util.Using` (or Java's try-with-resources) cancels the work automatically when the block ends, the same way it closes a file handle:

```scala mdoc:compile-only
import zio.blocks.async._
import scala.util.Using

def pollForUpdates(): Nothing = { while (true) Thread.sleep(100); ??? }

Using(Async.start(pollForUpdates())) { running =>
  // Do other work while the poller runs.
  Thread.sleep(500)
} // leaving the block cancels the poller, whether or not the body threw
```

The [Scope reference](resource-management/scope.md) covers the wider resource-management model.

**Feeding [streams](streams/stream.md).** A callback-based source can be turned into a stream with `Async.promise` and a `Completer`. Because a stream pulls values as it is ready for them, a source that produces faster than the consumer can handle will not overwhelm it.

## The Async[A] Type

`Async[A]` is the module's central type — a lazy description of a possibly-suspended computation that either yields an `A` or fails with a `Throwable`. Its runtime encoding is `Any`, so on the ready-value fast path every combinator is a plain function application with zero boxing. The type is covariant: `Async[Nothing]` is a valid `Async[A]` for any `A`, letting `Async.fail` and `Async.never` fit anywhere without a cast.

### Creating Values

The companion object provides factories for constructing leaf `Async[A]` values:

```scala
object Async {
  def succeed[A](a: A): Async[A]
  def fail(cause: Throwable): Async[Nothing]
  def attempt[A](body: => A): Async[A]
  def promise[A](body: Completer[A] => Unit): Async[A]  // Scala 2; Completer[A] ?=> Unit on Scala 3
  def start[A](body: => A): Async.Running[A]
  val never: Async[Nothing]
  def collectAll[A](as: IterableOnce[Async[A]]): Async[List[A]]
  def async[A](body: => A): Async[A]                    // Scala 3 macro; see the Direct Style pattern

  // JVM only
  def fromFuture[A](future: scala.concurrent.Future[A]): Async[A]
  def fromCompletionStage[A](cs: java.util.concurrent.CompletionStage[A]): Async[A]
}
```

`Async.succeed` lifts a pure, immediately-available value into an `Async[A]`:

```scala mdoc:compile-only
import zio.blocks.async._

val ready: Async[Int] = Async.succeed(42)
val result: Int = ready.block  // => 42
```

`Async.fail` creates a terminal failure that short-circuits every downstream `map` and `flatMap` without invoking continuations:

```scala mdoc:compile-only
import zio.blocks.async._

val boom: Async[Int] = Async.fail(new RuntimeException("boom"))
val result: Int = boom.catchAll(_ => Async.succeed(-1)).block  // => -1
```

`Async.attempt` captures a by-name expression and converts any thrown `Throwable` into a failure:

```scala mdoc:compile-only
import zio.blocks.async._

val parsed: Async[Int] = Async.attempt("42".toInt)
val bad: Async[Int]    = Async.attempt("nope".toInt)  // => Async.fail(NumberFormatException)
val result: Int        = bad.catchAll(_ => Async.succeed(0)).block  // => 0
```

`Async.never` is a permanently-suspended `Async[Nothing]` useful for testing cancellation or as a placeholder where an `Async[A]` is required but no value should arrive:

```scala mdoc:compile-only
import zio.blocks.async._

val running: Async.Running[Nothing] = Async.never.start
running.cancel()  // stop the driver immediately; nothing will be delivered
```

`Async.start(body: => A)` runs a synchronous body in the background and returns an `Async.Running[A]` without requiring an existing `Async[A]` description:

```scala mdoc:compile-only
import zio.blocks.async._

val running: Async.Running[Int] = Async.start { 42 }
val result: Int = running.block  // => 42
```

### Transformation

Pure transformations apply a function to the success value and return a new `Async`:

```scala
implicit class AsyncOps[A](fa: Async[A]) {
  def map[B](f: A => B): Async[B]
  def flatMap[B](f: A => Async[B]): Async[B]
  def as[B](b: B): Async[B]
  def unit: Async[Unit]
  def flatten: Async[A]  // available when fa: Async[Async[A]]; collapses one nesting level
}
```

`map` applies a pure function and `flatMap` sequences a dependent second computation:

```scala mdoc:compile-only
import zio.blocks.async._

val result: Async[String] =
  Async.succeed(21)
    .map(_ * 2)
    .flatMap(n => Async.succeed(s"value: $n"))
val out: String = result.block  // => "value: 42"
```

### Composition

Compositional operators combine independent or dependent `Async` values:

```scala
implicit class AsyncOps[A](fa: Async[A]) {
  def zipWith[B, C](that: Async[B])(f: (A, B) => C): Async[C]
  def zip[B](that: Async[B]): Async[(A, B)]
  def tap(f: A => Async[Any]): Async[A]
  def ensuring(finalizer: Async[Any]): Async[A]
  def *>[B](that: Async[B]): Async[B]
  def <*[B](that: Async[B]): Async[A]
  def orElse[B](that: => Async[B]): Async[_]  // result type merges A and B via Concat typeclass
}
```

`zipWith` waits for both sides and combines their results; `tap` runs a side-effecting action while passing the original value through:

```scala mdoc:compile-only
import zio.blocks.async._

val combined: Async[Int] =
  Async.succeed(3).zipWith(Async.succeed(4))(_ + _)
val tapped: Async[Int] =
  combined.tap(v => Async.attempt(println(s"sum is $v")))
val result: Int = tapped.block  // => 7, prints "sum is 7"
```

`*>` and `<*` sequence two effects and discard the left or right result respectively:

```scala mdoc:compile-only
import zio.blocks.async._

val logged: Async[Int] =
  Async.attempt(println("starting")).*>(Async.succeed(42))
val result: Int = logged.block  // => 42
```

### Error Handling

`Async` represents failure as a `Throwable` and provides dedicated recovery operators:

```scala
implicit class AsyncOps[A](fa: Async[A]) {
  def catchAll[A1 >: A](f: Throwable => Async[A1]): Async[A1]
  def mapError(f: Throwable => Throwable): Async[A]
  def foldCause[B](onFailure: Throwable => B)(onSuccess: A => B): Async[B]
  def either: Async[Either[Throwable, A]]
}
```

`catchAll` recovers from any failure by supplying a replacement `Async[A]`; `either` converts the outcome to an `Either` so the failure surface is visible in the return type:

```scala mdoc:compile-only
import zio.blocks.async._

val safe: Async[Either[Throwable, Int]] =
  Async.fail(new Exception("oops")).either
val result: Either[Throwable, Int] = safe.block  // => Left(Exception("oops"))
```

`foldCause` handles both the success and failure branches in a single call without allocating a recovery `Async`:

```scala mdoc:compile-only
import zio.blocks.async._

val message: Async[String] =
  Async.attempt("42".toInt).foldCause(
    (err: Throwable) => s"failed: ${err.getMessage}"
  )(
    (n: Int) => s"parsed: $n"
  )
val result: String = message.block  // => "parsed: 42"
```

### Driving

Driving executes the `Async[A]` description and delivers the result through one of three mechanisms:

```scala
implicit class AsyncOps[A](fa: Async[A]) {
  def block: A                                        // parks calling thread; re-throws on failure
  def await: A                                        // Scala 3 macro; inside Async.async { } only
  def start: Async.Running[A]
  def toFuture(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[A]
  def toCompletableFuture(implicit ec: scala.concurrent.ExecutionContext)
    : java.util.concurrent.CompletableFuture[A]       // JVM only
}
```

`block` parks the calling thread until the computation settles, then returns the value or re-throws the underlying `Throwable`:

```scala mdoc:compile-only
import zio.blocks.async._

val result: Int = Async.succeed(42).map(_ + 1).block  // => 43
```

`start` dispatches the description to a background worker and returns an `Async.Running[A]` immediately, without blocking:

```scala mdoc:compile-only
import zio.blocks.async._

val running: Async.Running[Int] = Async.attempt(42).start
val result: Int = running.block  // join when ready
```

`toFuture` hands off to a `scala.concurrent.Future`, bridging into any code that already expects the standard-library async type:

```scala mdoc:compile-only
import zio.blocks.async._
import scala.concurrent.ExecutionContext.Implicits.global

val future: scala.concurrent.Future[Int] =
  Async.succeed(99).toFuture
```

### Conditional Execution

`when` and `unless` are package-level functions (brought in by `import zio.blocks.async._`) that conditionally evaluate an `Async[Any]` based on a `Boolean` condition. The unevaluated branch is passed by name so no `Async` is constructed when the condition is false:

```scala mdoc:compile-only
import zio.blocks.async._

val flag = true
val logged:  Async[Unit] = when(flag)(Async.attempt(println("running")))
val skipped: Async[Unit] = unless(flag)(Async.attempt(println("skipped")))
```

## Custom Suspension

`Pollable[A]` and `Completer[A]` are the two building blocks for custom suspended leaves. `Pollable` defines the polling contract the driver uses to re-visit a suspended computation; `Completer` implements that contract specifically for one-shot callback bridging.

### Pollable

You rarely subclass `Pollable` directly — prefer `Async.promise` with a `Completer` for callback APIs or `Async.attempt` for blocking I/O. `Pollable` is the right choice when you need fine-grained polling semantics: for example, a timer that decrements a tick counter each time the driver visits it.

The structural declaration is:

```scala
abstract class Pollable[+A] {
  def poll(onComplete: Runnable): Async[A]
}
```

When the driver calls `poll`, the `Pollable` either returns `Async.succeed(a)` to signal readiness or re-registers `onComplete` and returns `this` (or another `Async[A]`) to indicate it should be revisited. The following example implements a countdown before delivery:

```scala mdoc:compile-only
import zio.blocks.async._

class Delayed[A](v: A, var ticks: Int) extends Pollable[A] {
  def poll(onComplete: Runnable): Async[A] =
    if (ticks <= 0) Async.succeed(v)
    else { ticks -= 1; onComplete.run(); this }
}

val result: String = new Delayed("done", ticks = 2).block  // => "done"
```

Because `Pollable[A]` is a subtype of `Async[A]` in the module's encoding, this value flows directly into any `Async[A]` position and composes with all combinators.

### Completer

`Completer[A]` extends `Pollable[A]` to bridge a single callback completion into the `Async` world. It is thread-safe and one-shot: the first call to `Completer#succeed` or `Completer#fail` wins and all subsequent calls are no-ops.

The structural declaration is:

```scala
final class Completer[A] extends Pollable[A] {
  def succeed(a: A): Unit
  def fail(cause: Throwable): Unit
  def peek: Async[A]
  def poll(onComplete: Runnable): Async[A]
}
```

`Async.promise` creates a new `Completer[A]`, passes it to the body, and returns the `Completer` as an `Async[A]` that the driver polls until the callback fires. The body syntax differs between Scala 2 and Scala 3: on Scala 3 the parameter type is `Completer[A] ?=> Unit` (a context function), while on Scala 2 it is `Completer[A] => Unit`:

<Tabs groupId="scala-version" defaultValue="scala2">
<TabItem value="scala2" label="Scala 2">

```scala
import zio.blocks.async._

val async: Async[Int] = Async.promise[Int] { c =>
  new Thread(() => { Thread.sleep(20); c.succeed(42) }).start()
}
val result: Int = async.block  // => 42
```

</TabItem>
<TabItem value="scala3" label="Scala 3">

```scala mdoc:compile-only
import zio.blocks.async._

val async: Async[Int] = Async.promise[Int] { c ?=>
  new Thread(() => { Thread.sleep(20); c.succeed(42) }).start()
}
val result: Int = async.block  // => 42
```

</TabItem>
</Tabs>

`Completer#peek` returns the `Completer` itself as an `Async[A]`, bypassing the `Async.promise` body — useful when managing scheduling manually, as shown in the `realAsync` helper in "How They Work Together."

## Concurrency and Cancellation

`Async.Running[A]` and `Cancelable` work together to give you control over in-flight computations. `Async.Running` is the concrete handle returned by `start`; `Cancelable` is the interface that makes it possible to stop the driver loop.

### Async.Running

`Async.Running[A]` is the handle returned by `start`. It extends both `Pollable[A]` — making it a first-class `Async[A]` that composes with every combinator — and `Cancelable`, giving you synchronous, idempotent cancellation.

The structural declaration is:

```scala
abstract class Running[+A] extends Pollable[A] with Cancelable
```

Because `Async.Running[A]` is itself an `Async[A]`, you can join the result, compose it further, or pass it anywhere an `Async[A]` is expected:

```scala mdoc:compile-only
import zio.blocks.async._

val running: Async.Running[Int] = Async.attempt(42).start
// Compose the running handle as an Async[Int]:
val doubled: Async[Int] = running.flatMap(n => Async.succeed(n * 2))
val result: Int = doubled.block  // joins and transforms
```

Calling `cancel` stops the driver loop. If the computation has already settled, `cancel` is a no-op:

```scala mdoc:compile-only
import zio.blocks.async._

val running: Async.Running[Nothing] = Async.never.start
running.cancel()  // stops immediately; the driver never delivers a value
```

Because `Async.Running` extends `AutoCloseable`, it works as a managed resource in `scala.util.Using`:

```scala mdoc:compile-only
import zio.blocks.async._
import scala.util.Using

// Using calls close() = cancel() on any exit from the block:
val _: scala.util.Try[String] = Using(Async.never.start) { _ => "done" }
```

### Cancelable

`Cancelable` is the minimal cancellation interface: a single `cancel()` method that stops a driver loop synchronously and idempotently. `close()` delegates to `cancel()`, satisfying the `AutoCloseable` contract.

The structural declaration is:

```scala
trait Cancelable extends AutoCloseable {
  def cancel(): Unit
  final def close(): Unit = cancel()
}
```

`Cancelable.noop` is the predefined no-op instance, useful as a placeholder when no real cancellation is needed:

```scala mdoc:compile-only
import zio.blocks.async._

val c: Cancelable = Cancelable.noop
c.cancel()  // no-op
c.close()   // no-op; delegates to cancel()
```

## Internal Encoding

The two types below are implementation details of the `Async[A]` encoding. You never construct or inspect them directly; understanding them explains how the runtime distinguishes user data from suspended computations.

### Failure

`Failure` is the terminal-failure representation inside the encoding. It extends `Pollable[Nothing]` and short-circuits `map` and `flatMap` by returning itself without invoking continuations. `Async.fail` and `Completer#fail` produce `Failure` values; `catchAll` and `either` recover from them.

The structural declaration is:

```scala
final class Failure(val cause: Throwable) extends Pollable[Nothing]
```

To observe a failure without re-throwing, use `either`:

```scala mdoc:compile-only
import zio.blocks.async._

val failed: Async[Int]             = Async.fail(new RuntimeException("boom"))
val result: Either[Throwable, Int] = failed.either.block  // => Left(RuntimeException("boom"))
```

When `block` encounters a `Failure`, it re-throws `cause` on the calling thread. When `catchAll` matches one, it passes the original `Throwable` to the recovery function without any unwrapping.

### AsyncEncoding.WrappedPollable

`AsyncEncoding.WrappedPollable` is an internal carrier that disambiguates a `Pollable` stored as a *success value* from one stored as a *suspended computation*. It does **not** extend `Pollable`; it instead tracks a nesting `depth` that the driver increments on each `Async.succeed` wrapping layer and decrements during result delivery, preserving the user's `Pollable` as data rather than treating it as a suspension point.

The structural declaration is:

```scala
final case class WrappedPollable(value: Pollable[?], depth: Int)
```

This type is completely transparent to users. When you write `Async.succeed(somePollable)`, the runtime wraps it in a `WrappedPollable` so the driver does not mistake it for a pending computation. To observe this, settle the `Completer` first so `block` can deliver it as data:

```scala mdoc:compile-only
import zio.blocks.async._

// Settle the Completer first so block delivers it as data rather than waiting on it:
val c: Completer[Int] = new Completer[Int]
c.succeed(42)

val asData: Async[Completer[Int]] = Async.succeed(c)  // c is wrapped as data, not driven
val extracted: Completer[Int]     = asData.block       // => the same Completer instance
```

## Comparisons

The table below situates `Async[A]` among common alternatives:

| Aspect                   | `Async[A]`                                      | `scala.concurrent.Future`         | ZIO effect type                    | cats-effect `IO`                    |
|--------------------------|-------------------------------------------------|-----------------------------------|------------------------------------|-------------------------------------|
| Evaluation               | Lazy — description only until driven            | Eager — starts at construction    | Lazy                               | Lazy                                |
| Dependencies             | Zero (single module)                            | Scala stdlib only                 | Full ZIO ecosystem                 | Cats Effect typeclass tower         |
| Error type               | `Throwable` (untyped)                           | `Throwable` (untyped)             | Typed (`E`)                        | `Throwable` (untyped)               |
| Environment              | None                                            | Implicit `ExecutionContext`        | Typed (`R`)                        | Implicit runtime                    |
| Cancellation             | Synchronous, concrete `Cancelable`              | Not supported                     | Fiber-based, structured            | Fiber-based (`IO.canceled`)         |
| Ready-value overhead     | Zero — `Any` encoding, inline fast path         | `Promise` + callback allocation   | Varies by operator                 | Allocates `IOFiber` on each run     |
| Custom suspension        | `Pollable` subclass                             | Not supported                     | `ZIO.async` callback DSL           | `IO.async` callback DSL             |
| Scala 2.13 support       | Yes                                             | Yes                               | Yes                                | Yes                                 |
| Direct style             | `Async.async { … }` (Scala 3 macro)             | Not applicable                    | Yes (ZIO direct)                   | Yes (CE direct)                     |

`Async[A]` is intentionally narrower than ZIO or cats-effect `IO`: no environment type, no typed error channel, no fiber abstraction, no typeclass tower. It is the right choice for infrastructure-level code where full ZIO or cats-effect would introduce more machinery than the problem requires.

## Running the Examples

The `async-examples` module ships `AsyncShowcaseExample`, which exercises the full cross-type workflow — `Completer`-backed callback bridges, the `Async.async` direct-style DSL, `catchAll` recovery, and a final `block` drive — in a single runnable pipeline. The `fulfillOrGuest` function from that file, shown below, uses helper functions defined in the same example to demonstrate composition across all four phases:

```scala
import zio.blocks.async._

def fulfillOrGuest(orderId: Int): Async[String] = Async.async {
  val order    = fetchOrder(orderId).catchAll(_ => fetchOrder(9001)).await
  val user     = fetchUser(order.userId)
                   .catchAll(_ => Async.succeed(User(0, "guest", "bronze"))).await
  val shipment = fulfill(order.id).await
  s"shipped ${shipment.orderId} for ${user.name} via ${shipment.carrier}"
}

println(fulfillOrGuest(9001).block)
```

To run the full example, clone the repository and execute:

```
sbt "async-examples/run"
```

## See Also

- [Stream Reference](streams/stream.md) — pull-based streaming with resource safety; use `Async.promise` and `Completer` to bridge callback-based push sources into the pull-based stream model
- [Scope Reference](resource-management/scope.md) — compile-time resource safety; `Async.Running` extends `AutoCloseable` and can be used inside `scala.util.Using` or any Scope-managed context for structured cancellation
- [Compile-Time Resource Safety with Scope](../guides/compile-time-resource-safety-with-scope.md) — step-by-step tutorial on resource ownership that applies equally to `Async.Running` handles
