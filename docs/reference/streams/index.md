---
id: index
title: "Streams"
---

`zio.blocks.streams` is a **synchronous, pull-based** streaming library for **Scala 3** (and Scala 2.13) with typed errors, resource safety, and primitive specialization. Streams are lazy descriptions -- nothing executes until a terminal operation is called. All results are returned as `Either[E, Z]`, keeping error handling explicit and typed. The library has zero runtime dependencies beyond `zio.blocks.chunk` and `zio.blocks.scope`, and achieves zero-boxing on primitive element types (`Int`, `Long`, `Float`, `Double`) through JVM-type-specialized internal readers.

ZIO Blocks Streams is built on three composable primitives: [Stream](./stream.md), [Pipeline](./pipeline.md), and [Sink](./sink.md):

- [`Stream[+E, +A]`](./stream.md) — a lazy, pull-based sequence of elements that may fail with error `E`
- [`Pipeline[-In, +Out]`](./pipeline.md) — a reusable, composable stream-to-stream transformation
- [`Sink[+E, -A, +Z]`](./sink.md) — a stream consumer that produces a typed result `Z`

## Overview

ZIO Blocks Streams is designed around three core principles:

**Synchronous execution.** All terminal operations (`run`, `runCollect`, `head`, etc.) return `Either[E, Z]` directly — no async effects, no ZIO runtime required. This makes streams easy to embed in any Scala or Java code.

**Pull-based evaluation.** Execution is driven from the consumer (Sink) backward through the pipeline to the source (Stream). This enables natural short-circuiting: if a sink only needs the first three elements, the stream stops producing after three elements — no work is wasted.

**Resource safety via RAII.** Resources acquired during stream construction (file handles, database connections, etc.) are always released in `finally` blocks, whether the stream succeeds, fails, or is short-circuited.

### The Three Primitives

These types form a clean pipeline:

```
┌──────────────┐     ┌──────────────────┐     ┌───────────────┐
│ Stream[E, A] │ ──→ │ Pipeline[A, B]   │ ──→ │ Sink[E, B, Z] │
└──────────────┘     └──────────────────┘     └───────────────┘
                                                       │
                                               ┌───────▼──────┐
                                               │ Either[E, Z] │
                                               └──────────────┘
```

| Type                    | Role                    | Key operation           |
| ----------------------- | ----------------------- | ----------------------- |
| `Stream[+E, +A]`        | Source of elements      | `stream.via(pipe)`      |
| `Pipeline[-In, +Out]`   | Element transformer     | `pipe.andThen(other)`   |
| `Sink[+E, -A, +Z]`      | Consumer → result       | `stream.run(sink)`      |

### Quick Start

```scala
import zio.blocks.streams.*

// Build a lazy stream description
val stream = Stream.range(1, 100)
  .filter(_ % 2 == 0)
  .map(_ * 3)

// Run it — nothing executes until here
val result: Either[Nothing, Chunk[Int]] = stream.take(5).runCollect
// Right(Chunk(6, 12, 18, 24, 30))
```

### Typed Errors

ZIO Blocks Streams distinguishes two error channels:

- **Typed errors (`E`)**: recoverable business logic errors, returned as `Left(e)` from terminal operations. Use `catchAll`, `orElse`, or `catchDefect` to handle them.
- **Untyped defects (`Throwable`)**: unexpected exceptions (bugs, system failures). Propagate as thrown exceptions.

```scala
import zio.blocks.streams.*

sealed trait ApiError
case object NotFound extends ApiError

val stream: Stream[ApiError, String] =
  Stream.fail(NotFound)
    .catchAll(_ => Stream.succeed("default"))

stream.runCollect
// Right(Chunk("default"))
```

### Resource Management

Use `fromAcquireRelease` to safely manage any resource:

```scala
import zio.blocks.streams.*

val managed = Stream.fromAcquireRelease(
  acquire = scala.io.Source.fromFile("data.txt"),
  release = _.close()
)(source => Stream.fromIterator(source.getLines()))

managed.take(10).runCollect
// Source is closed in finally block regardless of outcome
```

## Why Streams?

Streaming libraries in the Scala ecosystem typically require an effect system. fs2 needs `cats.effect.IO`, Kyo Streams needs the Kyo runtime, and Pekko (formerly Akka) Streams needs the actor runtime. When your code is synchronous and you want streaming without pulling in an effect monad, the options narrow considerably.

`zio.blocks.streams` fills that gap:

| Feature                    | ZB Streams               | fs2                 | Kyo              | Ox                       | Pekko            |
| -------------------------- | ------------------------ | ------------------- | ---------------- | ------------------------ | ---------------- |
| Effect system required     | No                       | Yes (cats-effect)   | Yes (Kyo)        | No (virtual threads)     | Yes (Akka)       |
| Execution model            | Synchronous, pull-based  | Async, pull-based   | Async, chunk     | Synchronous, pull-based  | Async, push      |
| Typed errors               | `Either[E, Z]`           | ApplicativeError    | Kyo effects      | Exceptions               | No               |
| Primitive specialization   | Yes (zero boxing)        | No                  | No               | No                       | No               |
| Stack-safe deep pipelines  | Yes (trampolined)        | Yes (Pull)          | Yes              | No (SO on deep flatMap)  | N/A              |
| Resource safety            | Scope integration        | Resource/bracket    | Kyo resources    | try/finally              | Graph lifecycle  |
| Dependencies               | chunk + scope            | cats-effect + scodec | Kyo core         | Ox core                  | Akka actor       |

### Benchmarks

All benchmarks use 10,000 elements, measured in operations per second (higher is better). Run on Apple M-series, JDK 25, Scala 3.7.4.

| Benchmark              | ZB Streams | Ox     | Kyo    | fs2    | Pekko |
| ---------------------- | ---------- | ------ | ------ | ------ | ----- |
| drain                  | 179,872    | 54,512 | 31,777 | 20,795 | 4,381 |
| map                    | 161,920    | 42,007 | 12,012 | 13,295 | 2,259 |
| filter                 | 168,541    | 47,933 | 19,962 | 14,977 | 2,901 |
| flatMap                | 49,165     | 30,506 | 28,303 | 748    | 742   |
| take/drop              | 322,470    | 28,708 | 64,640 | 28,836 | 2,379 |
| map+filter+flatMap     | 980        | 508    | 602    | 19     | 16    |
| mixed depth 1          | 47,459     | 19,449 | 13,427 | 257    | 639   |
| mixed depth 2          | 33,859     | 15,336 | 7,328  | 208    | 459   |
| mixed depth 3          | 23,610     | 11,878 | 3,174  | 139    | 256   |
| nested flatMap (10K)   | 8,161      | --     | --     | 937    | --    |
| nested concat (10K)    | 6,140      | --     | 3      | 1,065  | 1     |

"--" indicates the benchmark was not run or the library crashed.

ZB Streams leads in every single-operator benchmark and maintains its advantage as pipeline depth increases. The "mixed depth" rows show cascading `map`/`filter`/`flatMap` stages — ZB Streams degrades gracefully thanks to its trampolined execution model, while libraries without stack-safety (Ox) or with high per-element overhead (fs2, Pekko) fall off sharply.

## Primitive Specialization

ZB Streams eliminates boxing for `Int`, `Long`, `Float`, and `Double` elements throughout the entire pipeline. Every intermediate step uses specialized `readInt`/`readLong`/`readFloat`/`readDouble` methods, so no `java.lang.Integer` wrappers are allocated.

```scala
import zio.blocks.streams.*

// This entire pipeline runs with ZERO boxing of the Int elements.
// Every step uses specialized readInt/writeInt internally.
val sum: Either[Nothing, Long] =
  Stream.range(0, 1_000_000)   // Int-specialized source
    .filter(_ % 2 == 0)        // Int-specialized filter
    .map(_ * 3)                 // Int->Int specialized map
    .runFold(0L)(_ + _)         // Long-specialized accumulator
```

This matters most for numeric workloads — data processing, statistics, encoding/decoding — where millions of elements flow through multi-stage pipelines.

## Installation

Add the Streams module to your SBT build:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-streams" % "@VERSION@"
```

For Scala.js (JavaScript/Node.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-streams" % "@VERSION@"
```

Supported Scala versions: 2.13.x and 3.x.

## JVM Platform Availability

| Feature                               | JVM | Scala.js |
| ------------------------------------- | --- | -------- |
| `Stream`, `Pipeline`, `Sink` core API | ✅  | ✅       |
| `Stream.fromInputStream`              | ✅  | ❌       |
| `Stream.fromJavaReader`               | ✅  | ❌       |
| `Sink.fromOutputStream`               | ✅  | ❌       |
| `Sink.fromJavaWriter`                 | ✅  | ❌       |
| `NioStreams` (ByteBuffer / Channel)   | ✅  | ❌       |
| `NioSinks` (WritableByteChannel)      | ✅  | ❌       |

## Practical Guidance

- **Start with `Stream` constructors and terminal operations.** You can get very far with `Stream.range`, `Stream.fromIterable`, `.map`, `.filter`, and `.runCollect`.
- **Use `Either` pattern matching** to handle the result: `Right(value)` for success, `Left(error)` for typed failures.
- **Prefer `Stream.fromAcquireRelease`** when wrapping resources (files, connections, etc.) over manual try/finally. It guarantees cleanup even on early termination via `take`, `head`, or error.
- **Use the auto-closing I/O constructors** (`fromInputStream`, `fromJavaReader`, `NioStreams.fromChannel`) by default. Only use the `Unmanaged` variants when you need to borrow a resource whose lifetime is managed elsewhere.
- **Use `Pipeline`** when you have a transformation you want to reuse across multiple streams or apply to sinks.
- **Use `&&` for zipping** instead of manual zip calls. Tuples flatten automatically: `a && b && c` produces `(A, B, C)` not `((A, B), C)`.
- **Leverage primitive specialization** for numeric workloads. Streams of `Int`, `Long`, `Float`, and `Double` avoid boxing automatically; use `Sink.sumInt`, `runFold(0)(_ + _)`, etc. for zero-allocation folds.
- **Use `scan` for running accumulators**, `grouped` for batching, and `sliding` for windowed computations.
- **Use `render`/`toString`** to inspect pipeline structure during debugging — it shows each transformation stage without executing the stream.
- **Use `Sink.create`** as an escape hatch when none of the built-in sinks fit.
- **`suspend`** is your friend for recursive or self-referential stream definitions, preventing stack overflow during construction.
- **Typed errors vs. defects**: use `Stream.fail` for expected domain errors and `Stream.die` for programmer errors. Use `catchAll` for the former, `catchDefect` for the latter.
