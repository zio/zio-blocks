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
extension methods:

- **Constructors** — `Async.succeed`, `Async.fail`, `Async.attempt`,
  `Async.never`, `Async.collectAll`, and the callback bridge `Async.promise`.
- **Transformers** — `map`, `flatMap`, `zip`, `zipWith`, `catchAll`,
  `mapError`, `orElse`, `foldCause`, `either`, `tap`, `ensuring`, `as`, `unit`,
  `*>`, `<*`, `flatten`, and the conditional helpers `when` / `unless`.
- **Direct style** — `Async.async { ... .await ... }` lets you write
  straight-line code with `.await`, rewritten at compile time into a
  non-blocking `flatMap` chain.
- **Running** — `.block` drives an `Async` to its value (blocking on the JVM,
  throwing on a genuinely pending value on JS).
- **Interop** — conversions to and from `scala.concurrent.Future` on every
  platform, Java's `CompletionStage` / `CompletableFuture` on the JVM, and
  `js.Promise` on Scala.js.

On Scala 3 the transformers are zero-cost `inline` extension methods (the ready
path applies your function directly to the underlying value with no `Function1`
allocation). On Scala 2 they are methods on an implicit `AsyncOps` class. The
raw-value representation — a ready `Async[A]` *is* an `A` — holds identically on
both.

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

On the ready path the transformers apply your function directly to the
underlying value; only a genuinely pending `Async` takes the suspended slow
path.

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

### Conditional effects

`when` / `unless` run an `Async` only when a condition holds, discarding its
value. `Async.never` is an `Async` that never completes — useful as a sentinel:

```scala mdoc
val maybe: Async[Unit] = when(1 < 2)(Async.succeed(()))

val skipped: Async[Unit] = unless(1 < 2)(Async.succeed(()))

val forever: Async[Nothing] = Async.never
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

`.await` is also supported inside `List`, `Option`, `Vector`, immutable `Set`, and
immutable `Map` HOF closures (`map`, `foreach`, `flatMap`) on every backend, with
semantics that match each method's natural meaning (and the Scala 3 backends
exactly):

- **`List.map`** is **eager**: strict `map` applies the closure to every element
  first — running all construction-time side effects — producing a
  `List[Async[B]]`, and the awaits are then sequenced left-to-right via
  `Async.collectAll` (fail-fast on the first failure). This mirrors how
  `Array.map(async ...)` composes in JavaScript.
- **`List.foreach`** is **lazy / sequential**: the closure for element `n+1` runs
  only after element `n`'s `.await` completes successfully, and a failed await
  short-circuits the remaining elements. The result is `Unit`.
- **`List.flatMap`** is **lazy / sequential** like `foreach`, but accumulates each
  closure's `IterableOnce` into the result `List`.
- **`Option.map` / `Option.flatMap` / `Option.foreach`**: an `Option` holds at
  most one element, so the eager/lazy distinction collapses to a single
  `Some`/`None` branch — `None` short-circuits (the closure never runs), `Some(x)`
  runs the closure and (for `map`/`flatMap`) rewraps the result; a failed await
  propagates.
- **`Vector` / immutable `Set`** (`map` / `flatMap` / `foreach`): **lazy /
  sequential** like `List.foreach` (the closure for element `n+1` runs only after
  element `n`'s await completes; a failure short-circuits the rest). Note that
  `Vector.map` is lazy — only `List.map` is eager (it is the special case backed
  by dotty-cps-async's `ListAsyncShift`). The result **collection type is
  preserved** (`Vector.map` → `Vector`, `Set.map` → `Set`); for `Set`, the
  *awaited* values are deduplicated.
- **immutable `Map`** (`map` / `flatMap` / `foreach`): **lazy / sequential** over
  the map's `(K, V)` entries. A pair-returning `map`/`flatMap` rebuilds a
  `Map[K2, V2]` (later entries with the same key win); a non-pair `map`/`flatMap`
  widens the result to an `Iterable`, matching the standard library's overload
  choice. `foreach` runs the closure for each entry, returning `Unit`.
- **Short-circuiting predicate scans** (`find` / `exists` / `forall`, predicate
  `A => Boolean`): **lazy / sequential** over any whitelisted receiver — the
  predicate for element `n+1` runs only after element `n`'s await completes, and
  the scan stops at the first decisive element (`exists` → first `true`; `forall`
  → first `false`; `find` → first matching element as `Some`, else `None`). Note
  that `Option.find` is *not* covered on Scala 2 (it resolves via the
  `Option`→`Iterable` implicit conversion, whose receiver is not whitelisted);
  use `Option.exists`/`forall`, or `find` over a `List`/`Vector`/`Set`/`Map`.
- **`foldLeft`** (op `(B, A) => B`): **lazy / sequential** over any whitelisted
  receiver via `.iterator` — a left fold is inherently sequential (element
  `n+1`'s op needs `n`'s accumulator), so the op for element `n+1` runs only
  after element `n`'s await completes, and a failed await short-circuits the
  rest. The accumulator is threaded through and `foldLeft[B]` returns `B`
  directly (it may differ from the element type), so awaits in the initial
  accumulator are sequenced before the fold.

These are identical across Scala 2/3 and JVM/JS. Because Scala desugars
for-comprehensions over a `List` / `Option` / `Vector` / `Set` / `Map` into these
methods,
single- and multi-generator `for` comprehensions with `.await` work too
(`for ... yield` → `map`; nested generators → `flatMap`/`map`; `for { ... }`
without `yield` → `foreach`; a guard `if` → `withFilter`):

```scala
val pairs: Async[List[Int]] = Async.async {
  for {
    i <- List(1, 2)
    j <- List(10, 20)
  } yield Async.succeed(i + j).await
} // List(11, 21, 12, 22)
```

> **Scala 2 limitation (current):** the Scala 2 macro supports `.await` in
> sequential statements, `if` / `match` / `while` / `try`-`catch`-`finally`,
> `throw`, assignments, `List` / `Option` / `Vector` / immutable `Set` / immutable
> `Map` `map` / `foreach` / `flatMap` closures, the short-circuiting predicate
> scans `find` / `exists` / `forall` over those receivers, and the
> for-comprehensions that desugar to the former (including guards), but **rejects**
> `.await` inside other function
> literals / higher-order-function arguments (and HOFs over collections other than
> those five), with an actionable compile error. Those positions are supported on
> Scala 3. Support for more of them on Scala 2 is in progress.
>
> Conversely, the Scala 2 macro is a strict superset for some guard shapes that
> dotty-cps-async on Scala 3 currently rejects: *multiple* `List`
> for-comprehension guards (chained `withFilter`), and *any* `Option`
> for-comprehension guard (DCA has no `AsyncShift[Option#WithFilter]`). Single
> `List` guards behave identically on every cell.

## The callback bridge: `Async.promise`

`Async.promise` builds an `Async` from a callback-style API. You receive a
`Completer` and call `succeed` / `fail` when the result arrives. Completion is
one-shot — the first `succeed` or `fail` wins; later calls are silent no-ops. If
the body completes the completer synchronously, the result collapses to a bare
value with no `Pollable` allocation.

On Scala 3 the completer is supplied via a context function, so you can call the
top-level `succeed` / `fail` helpers directly:

```scala mdoc:compile-only
import zio.blocks.async._

val fromCallback: Async[Int] =
  Async.promise[Int] {
    // register a callback with some external system, then:
    succeed(42)
  }
```

On Scala 2 the body receives the `Completer` explicitly; mark it `implicit` to
use the same top-level `succeed` / `fail` helpers, or call its methods directly:

```scala
// Scala 2
Async.promise[Int] { implicit c => succeed(42) }
Async.promise[Int] { c => c.succeed(42) }
```

## Running an `Async`

`.block` drives an `Async` to its value. A ready value returns immediately. A
pending value blocks the calling thread on the JVM (Loom-friendly) and throws
on JS, where the platform cannot block. Use `.block` only at the edge of your
program, never on a scheduler/reactor thread.

```scala mdoc
val result: Int = Async.succeed(20).map(_ + 1).block
```

### Cancellable, callback-based running: `Async.unsafeRunAsync`

`Async.unsafeRunAsync(fa)(cb)` runs an `Async` without blocking and returns a
`Cancelable`. It is the sanctioned, non-blocking way to drive an arbitrary
`Async` (the encoding is otherwise sealed) and is the foundation cancellation
and fiber-style wrappers build on.

```scala mdoc:compile-only
import zio.blocks.async._

val cancelable: Cancelable =
  Async.unsafeRunAsync(Async.succeed(1).map(_ + 1)) {
    case Right(value) => println(s"done: $value")
    case Left(cause)  => println(s"failed: $cause")
  }

cancelable.cancel() // idempotent; no-op once the run has completed
```

The callback fires **at most once**:

- exactly once with `Right(value)` on success or `Left(cause)` on failure
  (including a `Throwable` thrown by a `poll`), **iff** the run reaches a
  terminal state before `cancel()` wins;
- never, if `cancel()` linearizes first.

For an already-ready `Async` the callback runs synchronously on the calling
thread (before `unsafeRunAsync` returns). For a suspended `Async` it runs on a
daemon worker thread on the JVM, or a microtask on Scala.js — never while an
internal driver lock is held, and never re-entrantly from inside a `poll`.
`cancel()` is driver-level only: it stops the poll loop and suppresses the
callback, but does not abort an in-flight leaf (socket, timer, JS promise).

## Interop

`AsyncInterop` converts between `Async` and the platform's standard async types,
preserving both success and failure. `scala.concurrent.Future` conversion is
available on both platforms; the JVM additionally offers Java
`CompletionStage` / `CompletableFuture`, and Scala.js offers `js.Promise`.

On the JVM:

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

On Scala.js, `AsyncInterop` provides `fromFuture` / `toFuture` plus
`fromJsPromise` / `toJsPromise` for native `scala.scalajs.js.Promise` interop.

## Low-level building blocks: `Pollable` and `Waker`

Most code should use the constructors and `Async.promise`. For custom
asynchronous leaves you can implement a `Pollable[A]` directly. `poll(waker)`
returns the ready value (or a `Failure`) when available, or a `Pollable`
(commonly `this`) when still pending; a pending pollable must arrange to call
`waker.wake()` once progress can be made, prompting the scheduler to re-poll.

## Cross-platform and cross-version notes

| Feature                          | JVM | JS | Scala 2.13 | Scala 3.x | Notes                                                   |
|----------------------------------|-----|----|------------|-----------|---------------------------------------------------------|
| Constructors & transformers      | ✅  | ✅ | ✅         | ✅        | Identical behavior everywhere                           |
| `Async.async` / `.await`         | ✅  | ✅ | ✅         | ✅        | DCA (Scala 3), `js.async`/`js.await` (3.8+ JS), macro (Scala 2); `.await` in `List` / `Option` / `Vector` / `Set` / `Map` `map`/`foreach`/`flatMap` closures and their for-comprehensions is supported everywhere; other HOF closures (and other collections) are Scala 3 only for now |
| `.block` on a pending value      | ✅  | ❌ | ✅         | ✅        | Blocks on JVM; throws on JS (cannot block)              |
| `Async.unsafeRunAsync` / `Cancelable` | ✅ | ✅ | ✅        | ✅        | Non-blocking callback runner; worker thread (JVM) / microtask (JS) |
| `Future` interop                 | ✅  | ✅ | ✅         | ✅        | `AsyncInterop.fromFuture` / `toFuture` on both platforms |
| `CompletionStage` interop        | ✅  | ❌ | ✅         | ✅        | JVM-only (`fromCompletionStage` / `toCompletableFuture`) |
| `js.Promise` interop             | ❌  | ✅ | ✅         | ✅        | JS-only (`fromJsPromise` / `toJsPromise`)              |

The public API is identical across all platforms and Scala versions by design;
the cross-platform test suite fails if any user-visible behavior diverges.

## See Also

- [Combinators](./combinators.md) — `Async#zip` uses the `Tuples` combiner for
  automatic tuple flattening.
</content>
</invoke>
