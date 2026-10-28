---
id: async-getting-started
title: "Getting Started with Async"
description: "Learn to create, compose, and run async effects in Scala with the zero-allocation Async[A] type."
keywords:
  - "Asynchronous Effects"
  - "Zero Allocation"
  - "Direct Style"
  - "Error Handling"
  - "Async"
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

Welcome! This tutorial introduces `Async[A]`, a zero-allocation effect type from ZIO Blocks that unifies ready values, failures, and genuinely suspended computations under a single type and combinator set. If you know basic Scala syntax and have a sense of what an effect type is, you have everything you need to follow along.

## 1. Introduction

By the end of this tutorial, you will be able to:

- Create ready-value effects with `Async.succeed` and transform them with `Async#map` and `Async#block`.
- Handle errors with `Async.fail`, `Async#catchAll`, and `Async#either`.
- Write sequential async code in direct style using `Async.async { … .await … }`.
- Bridge throw-based code and callback-based APIs with `Async.attempt` and `Async.promise` / `Completer`.
- Fork background computations with `Async.start` and cancel them with `Async.Running#cancel`.

To add the async module to your project, include this dependency in your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-async" % "@VERSION@"
```

Then bring the full DSL into scope at the top of each file you use it from:

```scala
import zio.blocks.async._
```

We recommend reading from top to bottom — each section builds directly on the one before it.

## 2. Background: What Is `Async[A]`?

`Async[A]` was designed to solve a specific problem: code often juggles three different kinds of values — results that are already available, computations that need to wait for I/O or a callback, and failures. Treating these differently in different places creates friction. `Async[A]` is one abstraction that covers all three.

The design's most important property is its **happy-path allocation budget: zero**. When you chain `Async.succeed`, `Async#map`, and another `map` call together, every step is a plain function call. No wrapper objects accumulate on the heap. Only a computation that truly suspends — waiting for a callback, a timer, or a thread — leaves a pending object behind. This is the zero-allocation promise: you pay for suspension only when you actually suspend.

We will explore `Async[A]` through an order-processing scenario: looking up a user via a callback API, parsing an order ID, checking stock availability in the background, and recovering gracefully from any failure — one concept at a time.

## 3. Ready Values: `Async.succeed`, `map`, and `block`

The simplest async computation is one that already has its answer. `Async.succeed(value)` wraps an available value into an `Async[A]` so it can take part in async chains without allocating anything. Once you have an `Async`, you transform it with `Async#map` and drive it to its final result with `Async#block`.

Here we wrap the integer `42`, double it with `map`, then extract the result:

```scala mdoc:compile-only
import zio.blocks.async._

val result: Int = Async.succeed(42).map(_ * 2).block
println(s"Ready mapped: $result")
```

Expected output:

```text
Ready mapped: 84
```

- `Async.succeed(42)` wraps `42` as a ready-value `Async[Int]` with zero allocation.
- Calling `map` with `_ * 2` applies the function; because the input is already ready, the entire chain is just a function call — no suspension object is created.
- The `block` call is the eager driver — it polls the async until it completes and returns the final value. It is safe to call on the JVM; on Scala.js it throws if the computation is still suspended.

Try changing `42` to a different number and watch the output change accordingly.

<details>
  <summary>Runnable version: <code>Concept1ReadyValuesExample.scala</code></summary>

```scala mdoc:embed:async-examples/src/main/scala/zio/blocks/async/gettingstarted/Concept1ReadyValuesExample.scala:show-line-numbers
```

</details>

## 4. Error Handling: `Async.fail`, `catchAll`, and `either`

Not every computation succeeds. `Async.fail(throwable)` creates a failed async that short-circuits all downstream `map` calls without invoking their functions. `Async#catchAll` recovers by applying a function that returns a new `Async`.

Here we create a failed computation and recover from it with `catchAll`:

```scala mdoc:compile-only
import zio.blocks.async._

val recovered: String = Async
  .fail(new Exception("oops"))
  .catchAll(_ => Async.succeed("default"))
  .block
println(s"Recovered: $recovered")
```

Expected output:

```text
Recovered: default
```

- `Async.fail(new Exception("oops"))` creates a computation that carries the exception as its failure.
- Calling `catchAll` with a recovery function intercepts the failure; the lambda ignores the specific error and returns a ready-value replacement.
- The `block` call drives the recovered chain to its result, `"default"`.

Sometimes you want to observe both the success and failure branches as plain data rather than handle them immediately. `Async#either` reifies both outcomes as `Either[Throwable, A]` so you can pattern-match on them:

```scala mdoc:compile-only
import zio.blocks.async._

val observed: Either[Throwable, Int] = Async
  .fail(new Exception("error"))
  .either
  .block
println(s"Observed: $observed")
```

Expected output:

```text
Observed: Left(java.lang.Exception: error)
```

- Calling `either` wraps the failure in `Left`; a successful result would appear in `Right`, turning the async's outcome into an ordinary Scala value.
- The `block` call materialises that `Either` so the learner can inspect it.

<details>
  <summary>Runnable version, with both blocks of this section: <code>Concept2ErrorHandlingExample.scala</code></summary>

```scala mdoc:embed:async-examples/src/main/scala/zio/blocks/async/gettingstarted/Concept2ErrorHandlingExample.scala:show-line-numbers
```

</details>

## 5. Direct Style: `Async.async` and `await`

Writing nested `Async#flatMap` chains is precise but becomes hard to read when many steps depend on each other. `Async.async { … }` lets you write that same sequencing in direct style: inside the block, call `Async#await` on any `Async` to extract its value and bind it to a local variable, as if you were writing straight-line code. The compiler rewrites every `await` call into a `flatMap` chain at compile time, so the runtime behaviour is identical.

Here we compose a user name and an order ID without a single explicit `flatMap`:

```scala mdoc:compile-only
import zio.blocks.async._

val summary: String = Async.async {
  val user  = Async.succeed("Ada").await
  val order = Async.succeed(9001).await
  s"${user}'s order ${order}"
}.block
println(s"Summary: $summary")
```

Expected output:

```text
Summary: Ada's order 9001
```

- `Async.async { … }` opens a macro-powered block; the entire expression produces an `Async[String]`.
- Calling `await` on `Async.succeed("Ada")` extracts `"Ada"` and binds it to `user`; this is not a blocking call — the macro rewrites it into a `flatMap` continuation.
- Calling `await` on `Async.succeed(9001)` similarly binds the order number to `order`.
- The final string expression becomes the block's result value.
- The `block` call drives the whole composed async to completion.

:::caution[`await` Is Only Valid Inside `Async.async { … }`]
The `await` method is enforced by the compiler to be used only inside an `Async.async { … }` block. On Scala 3 the inline expansion fails with:

```
".await may only be used directly inside an Async.async { ... } block."
```

On Scala 2, the `@compileTimeOnly` annotation fires at the same point. Try deleting the `Async.async { … }` wrapper — the compiler will tell you immediately.
:::

<details>
  <summary>Runnable version: <code>Concept3DirectStyleExample.scala</code></summary>

```scala mdoc:embed:async-examples/src/main/scala/zio/blocks/async/gettingstarted/Concept3DirectStyleExample.scala:show-line-numbers
```

</details>

## 6. Bridging Exceptions: `Async.attempt`

Scala code often signals failure by throwing exceptions rather than returning error values. `Async.attempt(body)` evaluates a block that may throw and captures any exception as an async failure, turning it into a value that `catchAll` can recover. A block that succeeds produces a ready-value `Async`; one that throws produces a failed `Async`.

The example parses a well-formed string and then a malformed one:

```scala mdoc:compile-only
import zio.blocks.async._

val good: Int = Async.attempt("42".toInt).block
println(s"Parsed: $good")

val bad: Either[Throwable, Int] = Async.attempt("oops".toInt).either.block
println(s"Failed parse: $bad")
```

Expected output:

```text
Parsed: 42
Failed parse: Left(java.lang.NumberFormatException: For input string: "oops")
```

- `Async.attempt("42".toInt)` evaluates `"42".toInt`; because it succeeds, the result `42` becomes a ready-value `Async[Int]`.
- `Async.attempt("oops".toInt)` evaluates `"oops".toInt`; the `NumberFormatException` is caught and becomes a failed `Async`.
- Chaining `either` and `block` drives the failed async and reifies its outcome as `Left(…)`.

<details>
  <summary>Runnable version: <code>Concept4BridgingExceptionsExample.scala</code></summary>

```scala mdoc:embed:async-examples/src/main/scala/zio/blocks/async/gettingstarted/Concept4BridgingExceptionsExample.scala:show-line-numbers
```

</details>

## 7. Callback Bridging: `Async.promise` and `Completer`

Many real-world APIs — database drivers, network libraries, timers — signal completion by calling a callback rather than returning a value. `Async.promise` lets you lift these APIs into `Async` without rewriting them. It suspends the computation and provides a `Completer[A]` — a thread-safe, one-shot handle — that you can pass to the callback. Calling `completer.succeed(value)` from any thread resolves the async and wakes up any awaiter.

The example starts a background thread that completes the promise after a short delay:

<Tabs groupId="scala-version" defaultValue="scala2">
<TabItem value="scala2" label="Scala 2">

```scala
import zio.blocks.async._

val result: String = Async
  .promise[String] { c =>
    new Thread {
      override def run(): Unit = {
        Thread.sleep(10)
        c.succeed("hello from callback")
      }
    }.start()
  }
  .block
println(s"Promise resolved: $result")
```

</TabItem>
<TabItem value="scala3" label="Scala 3">

```scala mdoc:compile-only
import zio.blocks.async._

val result: String = Async
  .promise[String] {
    val c = summon[Completer[String]]
    new Thread {
      override def run(): Unit = {
        Thread.sleep(10)
        c.succeed("hello from callback")
      }
    }.start()
  }
  .block
println(s"Promise resolved: $result")
```

</TabItem>
</Tabs>

Expected output:

```text
Promise resolved: hello from callback
```

- `Async.promise[String] { … }` opens the promise body; on Scala 2 the `Completer[String]` arrives as an explicit parameter `c`; on Scala 3 it arrives as a context function argument retrieved with `summon[Completer[String]]`.
- We capture the completer in `c` so it can be referenced from the Thread's `run()` method — implicits and givens do not propagate across thread boundaries, so we capture explicitly.
- Calling `c.succeed("hello from callback")` completes the promise; the first call wins and subsequent calls are no-ops.
- The `block` call waits until the completer fires and returns the resolved value.

:::warning[Scala 3: do not annotate the binder]
On Scala 3, `Async.promise[String] { (c: Completer[String]) => ... }` compiles, but the statements inside the lambda never run, so the completer is never completed and `block` waits forever. Use the Scala 3 form above, `summon[Completer[String]]` or `{ c ?=> ... }`. Compiler warning E190 at the call site is the only hint.
:::

<details>
  <summary>Runnable version, the Scala 3 tab: <code>Concept5CallbackBridgingExample.scala</code></summary>

```scala mdoc:embed:async-examples/src/main/scala/zio/blocks/async/gettingstarted/Concept5CallbackBridgingExample.scala:show-line-numbers
```

</details>

## 8. Forking and Cancellation: `start` and `Async.Running`

By default, async chains run eagerly on the calling thread until the first suspension or completion. To evaluate a block on a **background worker** (a separate JVM thread or a Scala.js microtask) instead, pass it to `Async.start`. This returns an `Async.Running[A]` handle — itself an `Async[A]` — representing the in-flight computation. You can join it by calling `block` on the handle, or stop waiting for it with `cancel`.

The first block forks a computation and joins it, printing the name of the thread each step runs on:

```scala mdoc:compile-only
import zio.blocks.async._

val running: Async.Running[Int] = Async.start {
  println(s"Computing on ${Thread.currentThread.getName}")
  42 * 2
}
val joined: Int = running.block
println(s"Joined on ${Thread.currentThread.getName}: $joined")
```

Expected output:

```text
Computing on zio-blocks-async-eval
Joined on main: 84
```

- `Async.start { … }` hands the block to a background worker and returns the `Running` handle immediately; the first line shows the block running on the worker thread, `zio-blocks-async-eval`.
- `running.block` blocks the calling thread until the background computation finishes and returns the result `84`, back on `main`.

The extension method `Async#start` drives an `Async` you have already built. Notice what happens when that `Async` is a chain of ready values:

```scala mdoc:compile-only
import zio.blocks.async._

val ready: Async.Running[Int] = Async
  .succeed(42)
  .map { x => println(s"map runs on ${Thread.currentThread.getName}"); x * 2 }
  .start
println(s"Joined: ${ready.block}")
```

Expected output:

```text
map runs on main
Joined: 84
```

- The `map` function runs on `main`, while the chain is being built and before `start` is called: its input is already available, so there is nothing to wait for.
- Calling `start` on the result finds a ready value and settles at once, without a background worker. This is the zero-allocation promise from section 2 at work — a chain leaves the calling thread only when it genuinely suspends, such as waiting on a `Completer`.

The last block demonstrates cancellation:

```scala mdoc:compile-only
import zio.blocks.async._

val running2: Async.Running[Int] = Async.start {
  Thread.sleep(100)
  println("Body finished after cancel")
  99
}
running2.cancel()
println("Cancelled running2")
Thread.sleep(200) // give the body time to finish
```

Expected output:

```text
Cancelled running2
Body finished after cancel
```

- `Async.start { … }` forks the block onto a background worker; the call returns the `Running` handle immediately.
- Calling `cancel` stops the driver, so no result is ever published to an awaiter. The call is idempotent.
- The second line shows that `cancel` does not interrupt the block itself: it finishes on its own thread. Cancelling an `Async.start` means you have stopped waiting for the result, not that the work has stopped.

:::caution
A cancelled run never settles: it neither succeeds nor fails. Calling `block` on a cancelled handle waits forever on the JVM and throws `IllegalStateException` on Scala.js, so cancel only a handle whose every consumer you own. The [`Async.Running`](../reference/async.md#asyncrunning) reference covers the details.
:::

<details>
  <summary>Runnable version, with all three blocks of this section: <code>Concept6ForkingAndCancellationExample.scala</code></summary>

```scala mdoc:embed:async-examples/src/main/scala/zio/blocks/async/gettingstarted/Concept6ForkingAndCancellationExample.scala:show-line-numbers
```

</details>

## 9. Putting It Together

Let's combine all six concepts into a single order-processing pipeline. The program bridges a callback-based user-lookup API with `Async.promise`, safely parses an order ID with `Async.attempt`, runs a stock check in the background with `Async.start`, sequences everything in direct style inside `Async.async`, and recovers from any failure with `catchAll`. Each step reuses a section's code with order-processing values: the callback delivers the user name `"Ada"` instead of a greeting, `Async.attempt` parses the order ID `"9001"`, the background block is a stock check that sleeps 5 ms and returns `true`, and the recovery handler reports the failure's message instead of a fixed default:

```scala mdoc:embed:async-examples/src/main/scala/zio/blocks/async/gettingstarted/CompleteExample.scala
```

Expected output:

```text
Report: Order 9001 for Ada: in stock
```

## 10. Running the Examples

All runnable code from this tutorial is in [`async-examples/src/main/scala/zio/blocks/async/gettingstarted/`](https://github.com/zio/zio-blocks/tree/main/async-examples/src/main/scala/zio/blocks/async/gettingstarted).
Run any file from that folder with `scala-cli run <File>.scala`, or from `async-examples` with
`sbt "runMain zio.blocks.async.gettingstarted.<ObjectName>"`.

## 11. What You've Learned

By completing this tutorial, you can now:

- Create ready-value effects with `Async.succeed`, transform them with `map`, and drive them to a result with `block`.
- Handle failures with `Async.fail`, recover them with `catchAll`, and observe both outcomes as data with `either`.
- Write sequential async pipelines in direct style using `Async.async { … .await … }` — the compiler threads the `flatMap` calls for you.
- Lift throw-based Scala code safely into the async error channel with `Async.attempt`.
- Bridge a callback-based API with `Async.promise` and `Completer`, fork background work with `Async.start`, and hold a cancellable `Async.Running` handle.

## 12. Where to Go Next

If your code already calls `Future`-based, Java `CompletionStage`-based, or callback-based APIs, the next step is [How to Wrap Callback and Future-Based APIs in Async](./wrap-callback-and-future-apis.md), which brings each of them into `Async` and shows how to cancel the underlying call.

The [Async reference page](../reference/async.md) documents every method and combinator with full signatures — it is the natural next stop once you are comfortable with the basics in this tutorial.

If your application manages resources that need deterministic cleanup — database connections, file handles, or connection pools — read the [Compile-Time Resource Safety with Scope](./compile-time-resource-safety-with-scope.md) tutorial, which shows how to tie resource lifetimes to lexical scopes and compose them without try/finally boilerplate.
