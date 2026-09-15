---
id: async-execution
title: "Asynchronous Stream Execution"
sidebar_label: "Async Execution"
description: "How one Stream type describes synchronous and asynchronous pipelines, and the full async constructor, operator, and terminal API."
keywords:
  - "Asynchronous Streams"
  - "Stream Compilation"
  - "Async Terminals"
  - "Close Ownership"
  - "Stream"
---

One `Stream[E, A]` describes both synchronous and asynchronous pipelines. There is no asynchronous stream type to convert to, no mode parameter to thread through your signatures, and no annotation that marks a description as one or the other. The pull request that introduced this API states the goal directly: it supports mixed synchronous and asynchronous stream composition "without a second stream type or mode parameter, including dynamic inner streams and platform-specific materialization."

The terminals ending in `Async` are the cross-platform ones. They compile and run on the JVM and on Scala.js, and they are the family shared code should be written against. The blocking terminals (`run`, `runCollect`, `head`, `start`, and their siblings) still exist, but only on the JVM.

## Overview

The asynchronous surface is five things: constructors that produce a stream from an `Async`, element-level operators that take an `Async` callback, the `*Async` terminal family, one bounded-concurrency operator, and the platform adapters that turn native asynchronous I/O into a stream.

| Addition                   | Size                    | Documented in                                           |
|----------------------------|-------------------------|---------------------------------------------------------|
| Async source constructors  | 10 names / 14 overloads | [Async Source Constructors](#async-source-constructors) |
| Sequential async operators | 10 names                | [Async Operators](#async-operators)                     |
| Async terminals            | 12 names / 16 overloads | [Async Terminals](#async-terminals)                     |
| Manual-pull terminals      | 2 names                 | [Manual Pull and Ownership](#manual-pull-and-ownership) |
| Bounded concurrency        | 1 name (`mapParAsync`)  | [Concurrent Operators](./concurrent-operators.md)       |
| The `Reader` union         | 2 subtypes              | [Reader](./reader.md)                                   |
| Platform I/O adapters      | JVM NIO and JS streams  | [Asynchronous I/O](./async-io.md)                       |

Every asynchronous addition follows one naming convention: the synchronous name with `Async` appended. There is no `fromAsync` and no `asyncPush`.

## Dependency and imports

The streams module carries the asynchronous effect type with it — `zio-blocks-streams` depends on `zio-blocks-async`, so one coordinate is all you add:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-streams" % "@VERSION@"
```

Use `%%%` in a cross-built project so the same line resolves for both JVM and Scala.js; `%%` is enough for a JVM-only build.

Every snippet on this page assumes these imports:

```scala
import zio.blocks.streams._          // Stream, Sink, Pipeline, JvmType
import zio.blocks.streams.io.Reader  // Reader, Reader.SyncReader, Reader.AsyncReader
import zio.blocks.async._            // Async, Pollable, Completer, and the Async extension methods
import zio.blocks.chunk.Chunk
```

Importing `zio.blocks.async._` rather than `zio.blocks.async.Async` matters: `map`, `flatMap`, `block`, `either`, and the rest of the `Async` combinators are extension methods brought into scope by the package import.

## One Stream Type, Two Execution Modes

The central claim of this page is short: the type that decides between synchronous and asynchronous execution is `Reader`, not `Stream`.

`Stream[E, A]` is a description. Nothing in it runs until a terminal is driven, and at that moment the description is compiled into a `Reader[A]`. `Reader` is the union of a synchronous and an asynchronous kind, and that compilation is the only place the two modes part ways.

```
┌───────────────────────────────────────────────────────────────┐
│ Stream[E, A]  -  a description; nothing has run yet           │
└───────────────────────────────────────────────────────────────┘
       │  a terminal is driven
       ▼
┌───────────────────────────────────────────────────────────────┐
│ Stream.compile  -  attempt the synchronous compilation        │
│ once, at materialization; never per element                   │
└───────────────────────────────────────────────────────────────┘
       │                            │
       │ every node compiles        │ any of nine node types
       │ synchronously              │ cannot: recompile the
       │                            │ whole graph async
       ▼                            ▼
┌────────────────────────┐   ┌──────────────────────────────────┐
│ Reader.SyncReader[A]   │   │ Reader.AsyncReader[A]            │
│ read and close         │   │ read and close return Async;     │
│ return directly        │   │ sync stages adapted by toAsync   │
└────────────────────────┘   └──────────────────────────────────┘
```

### Classification happens at compile time

`Stream.compile` first attempts a synchronous compilation of the whole graph. Nine node types cannot be represented synchronously — the asynchronous source boundary and the asynchronous operator nodes among them — and each of them aborts that attempt. When the attempt aborts, the graph is recompiled on the asynchronous path and the result is an `AsyncReader`.

This decision is made **once, at materialization**. It is never made per element, and it is never made per pull. A stream that turns out to be entirely synchronous runs through the synchronous engine with no asynchronous machinery in the loop at all.

The same description can be materialized more than once, and each materialization classifies independently. Classification is a property of the graph, not of the value's type.

### The Reader union

`Reader[+Elem]` is the root over two kinds:

```scala
abstract class Reader[+Elem] {
  def ++[Elem2 >: Elem](next: => Reader[Elem2]): Reader[Elem2]
  def concat[Elem2 >: Elem](next: () => Reader[Elem2]): Reader[Elem2]
  def concatAsync[Elem2 >: Elem](next: () => Async[Reader[Elem2]]): Reader.AsyncReader[Elem2]
  def withReleaseAsync(release: () => Async[Unit]): Reader.AsyncReader[Elem]
  def jvmType: JvmType
}
```

The root carries only kind-independent composition and one piece of metadata. Everything that actually pulls or closes lives on one of the two subtypes: `Reader.SyncReader[Elem]`, whose `read` and `close` return directly, and `Reader.AsyncReader[Elem]`, whose pull and lifecycle operations return `Async`.

A synchronous graph materializes as the former; a graph containing any asynchronous node materializes as the latter. See [Reader](./reader.md) for the full member list of both kinds, for how to implement a custom reader, and for `SyncReader#toAsync` and the JVM-only `AsyncReader#toSync`.

### Mixing synchronous and asynchronous stages

When a synchronous source meets an asynchronous operator, the asynchronous node aborts the synchronous compilation, the whole graph recompiles on the asynchronous path, and the synchronous source is adapted in place by `SyncReader#toAsync`. Nothing in user code needs annotating, and no static type changes.

Composition widens. Two synchronous participants stay synchronous; a single asynchronous participant makes the result asynchronous:

```scala mdoc:compile-only
import zio.blocks.streams.io.Reader
import zio.blocks.async._

val syncReader  = Reader.singleInt(1)
val asyncReader = Reader.singleInt(2).toAsync

val ss: Reader.SyncReader[Int]  = syncReader ++ Reader.singleInt(2)
val sa: Reader.AsyncReader[Int] = Reader.singleInt(1) ++ asyncReader
val as: Reader.AsyncReader[Int] = asyncReader ++ Reader.singleInt(3)
val aa: Reader.AsyncReader[Int] = asyncReader ++ Reader.singleInt(4).toAsync
```

At the stream level the same widening happens with no visible type at all. Adding one asynchronous stage to a synchronous pipeline leaves the annotation exactly as it was:

```scala mdoc:compile-only
import zio.blocks.streams._
import zio.blocks.streams.io.Reader
import zio.blocks.async._

val syncOnly: Stream[Nothing, Int] =
  Stream.fromReader[Nothing, Int](Reader.fromIterable(List(1, 2, 3, 4, 5))).map(_ * 10)

val mixed: Stream[Nothing, Int] =
  syncOnly.filterAsync(i => Async.succeed(i > 20))
```

On the JVM a blocking terminal still accepts `mixed`: the asynchronous reader is converted back at the final boundary. On Scala.js, use an `*Async` terminal.

### Why two engines

The synchronous engine keeps its lane registers as stack locals inside a single loop. Stack locals cannot survive a suspension — the moment a callback returns a value that is not yet ready, the loop's frame has to unwind and there is nowhere for those registers to live. The asynchronous path is therefore a separate, heap-allocated engine that keeps the equivalent state in an object it can park and resume.

That is the whole reason classification exists. It is also the reason a purely synchronous stream pays nothing for the library's asynchronous support: a graph with no asynchronous node never touches the heap-allocated engine.

### There is no mode annotation, and no lane diagnostic

Two things readers look for here, and will not find:

- **No type-level marker.** `Stream[E, A]` carries no phantom parameter, no `Sync`/`Async` tag, and no evidence that says which way a description will compile. You cannot write a signature that only accepts asynchronous streams, and you cannot ask a `Stream` value whether it will materialize asynchronously.
- **No public lane diagnostic.** The primitive representation a stream uses internally — its physical pull lane — is not reported by any public API either. `JvmType.Infer` reports the static type, which is exactly the thing the representation machinery stopped trusting. See [Zero-Boxing Optimization](./zero-boxing.md) for what the lanes are and how one is chosen.

If you need to control the kind rather than observe it, use the union-preserving `Stream.fromReader` overloads below: they let you hand a specific reader kind to the stream.

## Async Source Constructors

These are the companion constructors that turn an `Async` into a stream. Each of them defers its thunk until the first reader operation is driven.

### `Stream.attemptAsync`

```scala
def attemptAsync[A](f: => Async[A])(implicit jtA: JvmType.Infer[A]): Stream[Throwable, A]
```

Lazily evaluates an asynchronous thunk once per materialization and emits its result. Non-fatal synchronous throws and asynchronous failures become typed errors; fatal throwables remain defects.

### `Stream.attemptEvalAsync`

```scala
def attemptEvalAsync(f: => Async[Any]): Stream[Throwable, Nothing]
```

Lazily executes an asynchronous effect once per materialization and emits nothing. Non-fatal synchronous throws and asynchronous failures become typed errors; fatal throwables remain defects. Use it for an effect whose result you do not want in the stream.

### `Stream.deferAsync`

```scala
def deferAsync(finalizer: => Async[Unit]): Stream[Nothing, Nothing]
```

Creates an empty stream that lazily registers an asynchronous release action. It is awaited exactly once when each materialization closes, including after failure, early termination, or cancellation; failure is a defect.

### `Stream.evalAsync`

```scala
def evalAsync(f: => Async[Any]): Stream[Nothing, Nothing]
```

Lazily executes an asynchronous effect once per materialization and emits nothing; synchronous throws and asynchronous failures are defects. This is `attemptEvalAsync` without the typed error channel.

### `Stream.fromAcquireReleaseAsync`

```scala
def fromAcquireReleaseAsync[R, E, A](
  acquire: => Async[R],
  release: R => Async[Unit]
)(use: R => Stream[E, A])(implicit jtA: JvmType.Infer[A]): Stream[E, A]
```

Lazily acquires one resource per materialization, constructs the stream with `use`, and awaits release exactly once after completion, failure, early termination, or cancellation — including cancellation during acquisition, once the resource is obtained. Acquisition, `use`, and release failures are defects.

### `Stream.fromIteratorAsync`

```scala
def fromIteratorAsync[A](it: => Async[Iterator[A]])(implicit jtA: JvmType.Infer[A]): Stream[Nothing, A]
```

Asynchronously obtains one iterator per materialization and consumes it in order. Acquisition and iterator failures are defects.

### `Stream.fromReaderAsync`

```scala
def fromReaderAsync[E, A](mkReader: => Async[Reader[A]])(implicit jtA: JvmType.Infer[A]): Stream[E, A]
```

Lazily runs `mkReader` once per materialization and closes the resulting reader when the stream closes. Effect and thunk failures are defects.

### `Stream.unfoldAsync`

```scala
def unfoldAsync[S, A](s: S)(f: S => Async[Option[(A, S)]])(implicit jtA: JvmType.Infer[A]): Stream[Nothing, A]
```

Lazily unfolds state sequentially, emitting each `A` and continuing with the paired state until `f` returns `None`. At most one callback is active at a time; callback failure is a defect.

### `Stream.unwrap`

```scala
def unwrap[E](stream: => Async[Stream[E, Nothing]])(implicit dummy: DummyImplicit): Stream[E, Nothing]
def unwrap[E, A](stream: => Async[Stream[E, A]])(implicit jtA: JvmType.Infer[A]): Stream[E, A]
```

Flattens an asynchronously produced stream. The effect is evaluated lazily once per materialization; effect failures are defects, while the produced stream retains its typed error channel. The `DummyImplicit` overload exists to preserve inference when the element type is `Nothing`.

`unwrap` is the idiom for feeding an asynchronously produced stream into an operator that has no asynchronous twin. `flatMap`, `catchAll`, `catchDefect`, `orElse`, and `flatMapPar` all compose with it unchanged:

```scala mdoc:compile-only
import zio.blocks.streams._
import zio.blocks.async._

val stream: Stream[Nothing, Int] = Stream(1, 2, 3)

val flatMapped: Stream[Nothing, Long] =
  stream.flatMap(i => Stream.unwrap(Async.succeed(Stream(i.toLong))))

val recovered: Stream[String, Int] =
  Stream.fail[String]("failure").catchAll(_ => Stream.unwrap(Async.succeed(stream)))
```

### `Stream.fromReader`

Four overloads dispatch on the kind of reader you hand them, so the kind you chose is the kind the stream materializes:

```scala
def fromReader[E, A](mkReader: => Reader[A]): Stream[E, A]

def fromReader[E, A](mkReader: => Reader.SyncReader[A])(implicit
  dummy1: DummyImplicit,
  dummy2: DummyImplicit
): Stream[E, A]

def fromReader[E, A](mkReader: => Nothing)(implicit
  dummy1: DummyImplicit,
  dummy2: DummyImplicit,
  dummy3: DummyImplicit
): Stream[E, A]

def fromReader[E, A](mkReader: => Reader.AsyncReader[A])(implicit dummy: DummyImplicit): Stream[E, A]
```

Each overload lazily obtains one reader per materialization and closes it when the stream closes; reader-thunk failures are defects. The `Reader[A]` overload is documented as advanced: it is the union-preserving escape hatch, and it preserves whether the returned reader is synchronous or asynchronous. The `AsyncReader` overload awaits the reader's close.

### Laziness and error conventions

Two rules govern this whole family, and they are worth stating on their own because they are the two things most often assumed backwards.

**Laziness.** Compilation and materialization remain synchronous. Closing a stream before initialization neither invokes the thunk nor acquires a resource — an asynchronous constructor's effect starts only when the stream is first pulled. Do not compensate by eagerly opening a resource before constructing the stream.

**Errors.** Only the two `attempt*` constructors — `attemptAsync` and `attemptEvalAsync` — convert non-fatal callback failures into typed `Throwable` errors. Every other constructor's callback failure remains a defect, which fails the outer `Async` rather than appearing as a `Left`.

:::warning[A defect is not a typed error]
A defect does not surface in the `Either` that a terminal returns. It fails the surrounding `Async`, so a `match` on `Left`/`Right` will never see it. Reach for `attemptAsync` when you want a callback's failure in the `Left` channel.
:::

## Async Operators

These are the sequential, element-level twins of the synchronous operators. Each applies its callback to one element at a time, in order, with at most one invocation active.

### `Stream#mapAsync`

```scala
def mapAsync[B](f: A => Async[B])(implicit jtB: JvmType.Infer[B]): Stream[E, B]
```

Asynchronously transforms each element. Synchronous twin: [`map`](./stream.md#streammapb).

### `Stream#mapErrorAsync`

```scala
def mapErrorAsync[E2](f: E => Async[E2]): Stream[E2, A]
```

Asynchronously transforms the typed error channel. It runs only when the source fails with a typed error; callback failure is a defect. It genuinely changes the error type, so a `Stream[String, A]` can become a `Stream[Long, A]`. Synchronous twin: [`mapError`](./stream.md#streammaperrore2).

### `Stream#filterAsync`

```scala
def filterAsync(pred: A => Async[Boolean]): Stream[E, A]
```

Tests elements sequentially and emits those satisfying the asynchronous predicate, preserving order. Predicate failure is a defect. Synchronous twin: [`filter`](./stream.md#streamfilter).

### `Stream#collectAsync`

```scala
def collectAsync[B](f: A => Async[Option[B]])(implicit jtB: JvmType.Infer[B]): Stream[E, B]
```

Asynchronously transforms defined elements, dropping `None` results. Synchronous twin: [`collect`](./stream.md#streamcollectb).

### `Stream#mapAccumAsync`

```scala
def mapAccumAsync[S, B](init: S)(f: (S, A) => Async[(S, B)])(implicit jtB: JvmType.Infer[B]): Stream[E, B]
```

Asynchronously transforms elements while threading state sequentially. At most one invocation of `f` is active at a time. Synchronous twin: [`mapAccum`](./stream.md#stateful-transformations).

### `Stream#scanAsync`

```scala
def scanAsync[S](init: S)(f: (S, A) => Async[S])(implicit jtS: JvmType.Infer[S]): Stream[E, S]
```

Asynchronously emits the accumulator at each step, starting with `init`. The output stream has one more element than the input. Synchronous twin: [`scan`](./stream.md#stateful-transformations).

### `Stream#takeWhileAsync`

```scala
def takeWhileAsync(pred: A => Async[Boolean]): Stream[E, A]
```

Tests elements sequentially and emits them while the asynchronous predicate holds, then closes upstream at the first `false`. Predicate failure is a defect. Synchronous twin: [`takeWhile`](./stream.md#skipping-and-taking).

### `Stream#distinctByAsync`

```scala
def distinctByAsync[K](f: A => Async[K]): Stream[E, A]
```

Sequentially computes keys and emits the first element for each key, preserving order. Key state is per materialization and may grow without bound; asynchronous failures are defects. Synchronous twin: [`distinctBy`](./stream.md#streamdistinctbyk).

### `Stream#tapEachAsync`

```scala
def tapEachAsync(f: A => Async[Unit]): Stream[E, A]
```

Runs an asynchronous effect for each element and passes it through. Synchronous twin: [`tapEach`](./stream.md#other-operations).

### `Stream#ensuringAsync`

```scala
def ensuringAsync(finalizer: => Async[Unit]): Stream[E, A]
```

Registers an asynchronous finalizer lazily and awaits it exactly once when the materialized stream closes, including normal completion, failure, early termination, and cancellation. Finalizer failure is a defect. Synchronous twin: [`ensuring`](./stream.md#streamensuring).

For the one *concurrent* asynchronous operator, `mapParAsync`, see [Concurrent Operators](./concurrent-operators.md).

## Async Terminals

A terminal is what drives a stream. The cross-platform family all ends in `Async` and all shares one return shape.

### The `Async[Either[E, Z]]` convention

Every cross-platform terminal returns `Async[Either[E, Z]]`, never `Async[Z]`. The two channels are kept apart deliberately:

- The **typed error channel** `E` stays inside the `Either`. A stream that fails with a typed error still succeeds at the `Async` level: the `Async` completes normally, carrying `Left(e)`.
- `Async`'s own **untyped `Throwable` channel** is reserved for defects — a callback that threw, a finalizer that failed, a cleanup failure. These fail the outer `Async` and never appear as a `Left`.

This one rule explains the shape of every signature in this section:

```scala mdoc:compile-only
import zio.blocks.streams._
import zio.blocks.chunk.Chunk
import zio.blocks.async._

val readings: Stream[String, Int] = Stream(12, 7, 30)

val collected: Async[Either[String, Chunk[Int]]] = readings.runCollectAsync

val described: Async[String] = collected.map(result =>
  result match {
    case Right(values) => s"collected ${values.length} readings"
    case Left(error)   => s"typed error: $error"
  }
)
```

### Collecting and running

```scala
def runAsync[ES, E3, Z](sink: Sink[ES, A, Z])(implicit
  errorConcat: Concat.WithOut[E @uncheckedVariance, ES, E3]
): Async[Either[E3, Z]]

def runCollectAsync: Async[Either[E, Chunk[A]]]

def runDrainAsync: Async[Either[E, Unit]]

def runForeachAsync(f: A => Async[Unit]): Async[Either[E, Unit]]
```

`runAsync` is the general form: it runs the stream through the asynchronous drain of a `Sink`, and materialization and cleanup are lazy, cancellation-safe, and performed exactly once. Its error type is the concatenation of the stream's error type and the sink's, which is why the implicit `Concat` evidence appears.

`runCollectAsync` collects all elements in order. It requires memory proportional to the entire output and does not terminate for an infinite stream. `runDrainAsync` discards them. `runForeachAsync` applies an asynchronous callback to each element sequentially.

### Folding

`runFoldAsync` is a five-member family: four primitive accumulator lanes and one generic.

| Accumulator | Signature                                                  | Blocking `runFold` twin |
|-------------|------------------------------------------------------------|-------------------------|
| `Double`    | `runFoldAsync(z: Double)(f: (Double, A) => Async[Double])` | yes                     |
| `Float`     | `runFoldAsync(z: Float)(f: (Float, A) => Async[Float])`    | none                    |
| `Int`       | `runFoldAsync(z: Int)(f: (Int, A) => Async[Int])`          | yes                     |
| `Long`      | `runFoldAsync(z: Long)(f: (Long, A) => Async[Long])`       | yes                     |
| generic `Z` | `runFoldAsync[Z](z: Z)(f: (Z, A) => Async[Z])`             | yes                     |

The generic overload takes an implicit `JvmType.Infer[Z]`; the four primitive ones do not, and the overload is selected by the static type of `z`. Write `0L` rather than `0` when you want the `Long` lane.

The `Float` lane has no counterpart in the blocking `runFold` family, which offers only `Double`, `Int`, `Long`, and generic. It is new with the asynchronous terminals.

Each fold callback is applied sequentially, one element at a time.

### Queries

The query terminals are one-liners over `runAsync`. Knowing which `Sink` each delegates to tells you its semantics exactly:

| Terminal            | Returns                       | Delegates to             |
|---------------------|-------------------------------|--------------------------|
| `countAsync`        | `Async[Either[E, Long]]`      | `Sink.count`             |
| `existsAsync(pred)` | `Async[Either[E, Boolean]]`   | `Sink.existsAsync(pred)` |
| `findAsync(pred)`   | `Async[Either[E, Option[A]]]` | `Sink.findAsync(pred)`   |
| `forallAsync(pred)` | `Async[Either[E, Boolean]]`   | `Sink.forallAsync(pred)` |
| `foreachAsync(f)`   | `Async[Either[E, Unit]]`      | `runForeachAsync(f)`     |
| `headAsync`         | `Async[Either[E, Option[A]]]` | `Sink.head`              |
| `lastAsync`         | `Async[Either[E, Option[A]]]` | `Sink.last`              |

`existsAsync`, `findAsync`, and `forallAsync` take an `A => Async[Boolean]` predicate; `foreachAsync` is an alias for `runForeachAsync`. `countAsync`, `headAsync`, and `lastAsync` take no callback and therefore reuse the ordinary synchronous sinks.

### Blocking twins are JVM-only

Thirteen blocking members — `count`, `exists`, `find`, `forall`, `foreach`, `head`, `last`, `run`, `runCollect`, `runDrain`, `runFold`, `runForeach`, and `start` — live on the JVM only. Shared, cross-compiled sources cannot call them; they must use the `*Async` family, `startAsync`, and `useReaderAsync` instead.

For the full platform matrix, including which reader conversions and sink constructors exist on which platform, see [Platform Differences](./platform-differences.md). For converting an existing blocking codebase, see [Migration](./migration.md).

### Driving an `Async` from a JVM `main`

An `Async[Either[E, Z]]` is a value. Something has to drive it, and on the JVM that something is `.block`:

```scala mdoc:compile-only
import zio.blocks.streams._
import zio.blocks.chunk.Chunk
import zio.blocks.async._

val stream: Stream[String, Int] = Stream(1, 2, 3)

// At the edge of the world, and on the JVM only:
val result: Either[String, Chunk[Int]] = stream.runCollectAsync.block
```

`.block` drives the effect to its value, parking the calling thread until it is ready; a failure is re-thrown as its cause. A ready value returns immediately without parking.

This is the edge-of-the-world idiom, and it is JVM-only. Two rules keep it honest:

1. **`.block` belongs in `main`, or in a test, and nowhere else.** Never call it inside a stream callback or inside a `poll` — blocking the driver from within the loop it is driving deadlocks it.
2. **Scala.js code must not use it at all.** JavaScript cannot block, so `.block` throws an `IllegalStateException` there. Cross-platform code should keep the `Async` and hand it to the host: convert it at the boundary (for example with `toFuture`) and let the runtime drive it.

Inside an `Async.async { ... }` block, use the direct-style `.await` instead, which extracts the value without blocking. See [Async](../async.md) for both.

## Manual Pull and Ownership

Sometimes you want the reader rather than a result — to interleave pulls with other work, or to hand the source to a protocol loop. Two terminals give you one, and they differ in exactly one respect: who is responsible for closing it.

### `Stream#startAsync`

```scala
def startAsync: Async[Reader.AsyncReader[A]]
```

Materializes this stream as a caller-owned asynchronous reader. **Ownership transfers to the caller**, who must drive the reader and await `close()`. This is the one place in the API where the library does not close what it opened — if you forget the `close()`, finalizers registered by `ensuringAsync`, `deferAsync`, and `fromAcquireReleaseAsync` never run.

### `Stream#useReaderAsync`

```scala
def useReaderAsync[Z](f: Reader.AsyncReader[A] => Async[Z]): Async[Z]
```

The scoped alternative. Ownership is **retained** by the library: the reader is passed to `f`, and its close is awaited on every outcome — success, typed failure, defect, and cancellation alike. Prefer it whenever the reader's lifetime is bounded by a single block of code.

Note the return type: `Async[Z]`, not `Async[Either[E, Z]]`. `useReaderAsync` hands you the reader, so whatever `f` produces is what you get back; stream errors surface through the reader's own pulls.

### One active operation per reader

An `AsyncReader` is a single-consumer cursor, not a concurrent work queue. **At most one operation may be in flight at a time**: await the `Async` returned by a `read`, `readAll`, `skip`, or `close` before beginning the next one.

Readers are not thread-safe either. Overlapping pulls, or driving one reader from two threads without external synchronization, is outside the contract — the reader's internal position and lifecycle state are not defended against it, and the result is not specified.

### Cleanup failures

When cleanup fails on a path that has already failed, the cleanup failure is **attached to** the primary failure rather than replacing it. The original cause is what propagates; the cleanup cause is recorded as a suppressed exception on it.

This means a `Throwable` that reaches you from a failed `Async` may carry more than one story. Inspect `getSuppressed` before concluding that a close error was the only thing that went wrong.

## Cancellation

Cancellation in this library is **cooperative, never preemptive**. Cancelling signals the in-flight operation; it does not interrupt a thread, and it never waits for an in-flight `poll` to return.

Two pieces of the API matter here:

- **`Pollable#cancel()`** signals cancellation of the currently pending operation, and now reaches the active leaf operation. Before this change, cancellation was driver-level only and explicitly did not reach the leaf. Implementations that own cancellable work override `cancel()` with an idempotent, non-blocking signal; implementations without such work inherit the no-op. A running driver invokes it only when cancellation wins the race against completion.
- **`Async.Running#cancel(onCleanupFailure: Throwable => Unit)`** cancels a run and reports a failure from its asynchronous cleanup. The reporter is retained only when this cancellation wins completion, and it is invoked at most once. Use it when a cleanup failure during cancellation must not be lost.

Cancellation closes an acquired reader and awaits its finalizer. `startAsync` is the deliberate exception, because it has already transferred that responsibility to its caller.

See [Async](../async.md) for `Pollable`, `Cancelable`, and `Async.Running` themselves.

## Resource Management

Two members carry resources through an asynchronous stream, and they compose with the ownership rules above.

`Stream.fromAcquireReleaseAsync` brackets a resource around a stream: one acquisition per materialization, and release awaited exactly once after completion, failure, early termination, or cancellation — including cancellation that arrives during acquisition, once the resource is obtained.

`Stream#ensuringAsync` registers a finalizer without a resource: awaited exactly once when the materialized stream closes, on every outcome.

Both are driven by the *close* of the materialized reader, which is what ties them to ownership:

- Under `runCollectAsync` and every other terminal, the library closes the reader, so both run without your involvement.
- Under `useReaderAsync`, the library still closes the reader, so both still run — on success and on failure alike.
- Under `startAsync`, **you** close the reader. Until you await `close()`, neither the release action nor the finalizer has run.

A failure inside a finalizer is a defect, and if the stream had already failed, that defect is attached to the primary failure rather than replacing it.

## How This Is Verified

The asynchronous execution path is covered by three-way differential equivalence: for each generated program, the *ready* execution, the *suspended* execution, and an independent reference model must agree. Agreement is compared on result, failure provenance, throwable order, materialized reader kind, demand, callbacks, ownership, outstanding resources, and suppressed exceptions — not merely on the final value. Duplicate, late, and reentrant callbacks are injected deliberately as faults, so the properties above are checked against a source that misbehaves on purpose.

:::note[On allocation figures]
Near-zero allocation numbers observed for this path are profiler noise, not a promise. An allocation profiler is required before claiming that any particular stream program allocates nothing.
:::

## Running the Examples

Every example below is a runnable file in the `streams-examples` module. Clone the repository and run them with sbt:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

### Async terminals and `.block`

Three terminals on one description, the `Either` unwrapped on both branches, and `.block` used exactly once, at the edge of `main`:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/stream/StreamAsyncTerminalsExample.scala")
```

Run it with:

```bash
sbt "streams-examples/runMain stream.StreamAsyncTerminalsExample"
```

### Ownership: `startAsync` versus `useReaderAsync`

A finalizer that counts its own runs, proving that `useReaderAsync` closes on success *and* on failure, while `startAsync` closes only because the caller does it:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/stream/StreamAsyncOwnershipExample.scala")
```

Run it with:

```bash
sbt "streams-examples/runMain stream.StreamAsyncOwnershipExample"
```

### A mixed synchronous and asynchronous pipeline

A synchronous source, one asynchronous operator, and no change to any annotation:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/stream/StreamMixedKindExample.scala")
```

Run it with:

```bash
sbt "streams-examples/runMain stream.StreamMixedKindExample"
```

### A composed asynchronous pipeline

Real suspension through a `Completer`, `Stream.unwrap` feeding `filterAsync`, `mapAsync`, and `ensuringAsync`, 33,000 nested stages to demonstrate that the asynchronous path is stack-safe, and an assertion that the finalizer runs exactly once:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("streams-examples/src/main/scala/stream/StreamAsyncOrderPipelineExample.scala")
```

Run it with:

```bash
sbt "streams-examples/runMain stream.StreamAsyncOrderPipelineExample"
```

## See Also

- [Reader](./reader.md) — the `SyncReader` / `AsyncReader` union, custom reader implementations, and mixed-kind composition
- [Concurrent Operators](./concurrent-operators.md) — `mapPar`, `mapParAsync`, `mergeAll`, and `flatMapPar`
- [Asynchronous I/O](./async-io.md) — JVM NIO channels and Scala.js readable streams as asynchronous sources
- [Platform Differences](./platform-differences.md) — what exists on the JVM, what exists on Scala.js, and what throws
- [Migration](./migration.md) — moving an existing blocking codebase onto the `*Async` family
- [Async](../async.md) — `Async[A]`, `Pollable`, `Completer`, `Async.Running`, and cancellation
- [Zero-Boxing Optimization](./zero-boxing.md) — primitive lanes, and why async is lane-aware rather than end-to-end allocation-free
