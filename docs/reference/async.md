---
id: async
title: "Async"
---

The `async` module provides `Async[A]`, a lightweight, zero-dependency
asynchronous effect type for modern Scala. It is designed around a single idea:
**a ready `Async[A]` is just an `A`**. The happy path allocates nothing — no
effect tree, no wrapper, no boxing beyond what a generic JVM method already
requires — so synchronous code composed with `map` / `flatMap` runs at
hand-written speed while still being able to suspend on genuinely asynchronous
work.

## Overview

`Async[A]` is an opaque type whose ready representation is the value itself and
whose pending representation is a `Pollable[A]`. You never construct it
directly; you enter the type through constructors and transform it through
inline extension methods:

- **Constructors** — `Async.succeed`, `Async.fail`, `Async.attempt`,
  `Async.never`, `Async.collectAll`, and the callback bridge `Async.promise`.
- **Transformers** — `map`, `flatMap`, `zip`, `zipWith`, `catchAll`,
  `mapError`, `orElse`, `foldCause`, `either`, `tap`, `ensuring`, `as`, `unit`,
  `*>`, `<*`, `flatten`.
- **Direct style** — `Async.async { ... .await ... }` lets you write
  straight-line code with `.await`, rewritten at compile time into a
  non-blocking `flatMap` chain.
- **Running** — `.block` drives an `Async` to its value (blocking on the JVM,
  throwing on a genuinely pending value on JS).
- **Interop** — conversions to and from `scala.concurrent.Future` and Java's
  `CompletionStage` / `CompletableFuture` (JVM only).

## Installation

Add the following to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-blocks-async" % "@VERSION@"
```

For cross-platform projects (Scala.js):

```sbt
libraryDependencies += "dev.zio" %%% "zio-blocks-async" % "@VERSION@"
```

Supported platforms: JVM and Scala.js, Scala 2.13 and Scala 3.x. The
direct-style `Async.async` block is rewritten by dotty-cps-async on Scala 3
(JVM and older Scala 3 JS), by native `js.async` / `js.await` on Scala 3.8+ JS,
and by a hand-written macro on Scala 2.

## Constructors

`Async.succeed` lifts a pure value; `Async.fail` lifts an error; `Async.attempt`
catches a thrown exception and turns it into a failure.

```scala mdoc
import zio.blocks.async._

val ready: Async[Int] = Async.succeed(42)

val failed: Async[Nothing] = Async.fail(new RuntimeException("boom"))

val caught: Async[Int] = Async.attempt(Integer.parseInt("123"))
```

`Async.collectAll` sequences a collection of `Async` values, short-circuiting on
the first failure:

```scala mdoc
val all: Async[List[Int]] =
  Async.collectAll(List(Async.succeed(1), Async.succeed(2), Async.succeed(3)))
```

## Transforming values

The transformer methods are `inline` extension methods. On the ready path they
apply your function directly to the underlying value with **no `Function1`
allocation**; only a genuinely pending `Async` takes the suspended slow path.

```scala mdoc
val mapped: Async[Int] = Async.succeed(20).map(_ + 1)

val chained: Async[Int] = Async.succeed(20).flatMap(n => Async.succeed(n * 2))

val recovered: Async[Int] =
  Async.fail(new RuntimeException("nope")).catchAll(_ => Async.succeed(-1))
```

### Combining with `zip`

`zip` fuses two `Async` values into a tuple using the `combinators` module's
`Tuples` combiner, so chained zips flatten automatically (`a zip b zip c`
yields `Async[(A, B, C)]`, not `Async[((A, B), C)]`). Use `zipWith` to combine
with an explicit function:

```scala mdoc
val zipped: Async[(Int, String)] =
  Async.succeed(1).zip(Async.succeed("two"))

val summed: Async[Int] =
  Async.succeed(3).zipWith(Async.succeed(4))(_ + _)
```

### Error handling

`catchAll` recovers a failure, `mapError` transforms the cause, `orElse`
falls back to another `Async`, and `either` reifies the outcome:

```scala mdoc
val asEither: Async[Either[Throwable, Int]] =
  Async.fail(new RuntimeException("x")).either

val fallback: Async[Int] =
  Async.fail(new RuntimeException("x")).orElse(Async.succeed(0))
```

## Direct style: `Async.async` and `.await`

Inside an `Async.async { ... }` block you can write straight-line code and use
`.await` to extract the value of any `Async`. The block is rewritten at compile
time into a non-blocking `flatMap` / `map` chain — there is no thread blocking
on the happy path. `.await` is **lexically restricted** to `Async.async`
blocks; using it elsewhere is a compile error.

```scala mdoc:compile-only
def loadUser(id: Int): Async[String] = Async.succeed(s"user-$id")
def loadOrders(user: String): Async[List[String]] = Async.succeed(List(s"$user-order"))

val program: Async[Int] =
  Async.async {
    val user   = loadUser(1).await
    val orders = loadOrders(user).await
    orders.size
  }
```

A failure encountered by `.await` short-circuits the block and surfaces as a
failed `Async`, exactly as if you had thrown — `Async.async { Async.fail(t).await }`
is equivalent to `Async.fail(t)`.

## The callback bridge: `Async.promise`

`Async.promise` builds an `Async` from a callback-style API. You receive a
`Completer` and call `succeed` / `fail` when the result arrives. If the body
completes the completer synchronously, the result collapses to a bare value
with no `Pollable` allocation. On Scala 3 the completer is supplied via a
context function, so you can call the top-level `succeed` / `fail` helpers
directly:

```scala mdoc:compile-only
import zio.blocks.async._

val fromCallback: Async[Int] =
  Async.promise[Int] {
    // register a callback with some external system, then:
    succeed(42)
  }
```

## Running an `Async`

`.block` drives an `Async` to its value. A ready value returns immediately. A
pending value blocks the calling thread on the JVM (Loom-friendly) and throws
on JS, where the platform cannot block. Use `.block` only at the edge of your
program, never on a scheduler/reactor thread.

```scala mdoc
val result: Int = Async.succeed(20).map(_ + 1).block
```

## Interop (JVM)

On the JVM, `AsyncInterop` converts to and from `scala.concurrent.Future` and
Java's `CompletionStage` / `CompletableFuture`, preserving both success and
failure:

```scala mdoc:compile-only
import zio.blocks.async._
import scala.concurrent.{ExecutionContext, Future}
import java.util.concurrent.CompletableFuture

implicit val ec: ExecutionContext = ExecutionContext.global

val fromFut: Async[Int]          = AsyncInterop.fromFuture(Future.successful(1))
val toFut: Future[Int]           = AsyncInterop.toFuture(Async.succeed(1))
val fromStage: Async[Int]        = AsyncInterop.fromCompletionStage(CompletableFuture.completedFuture(1))
val toStage: CompletableFuture[Int] = AsyncInterop.toCompletableFuture(Async.succeed(1))
```

## Cross-platform and cross-version notes

| Feature                          | JVM | JS | Scala 2.13 | Scala 3.x | Notes                                                   |
|----------------------------------|-----|----|------------|-----------|---------------------------------------------------------|
| Constructors & transformers      | ✅  | ✅ | ✅         | ✅        | Identical behavior everywhere                           |
| `Async.async` / `.await`         | ✅  | ✅ | ✅         | ✅        | DCA (Scala 3), `js.async`/`js.await` (3.8+ JS), macro (Scala 2) |
| `.block` on a pending value      | ✅  | ❌ | ✅         | ✅        | Blocks on JVM; throws on JS (cannot block)              |
| `Future` / `CompletionStage` interop | ✅ | ❌ | ✅      | ✅        | `AsyncInterop` is JVM-only                              |

The public API is identical across all platforms and Scala versions by design;
the cross-platform test suite fails if any user-visible behavior diverges.

## See Also

- [Combinators](./combinators.md) — `Async#zip` uses the `Tuples` combiner for
  automatic tuple flattening.
</content>
</invoke>
