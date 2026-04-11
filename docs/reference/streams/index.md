---
id: index
title: "Streams"
---

ZIO Blocks Streams is a **synchronous, pull-based, resource-safe streaming library** built on three composable primitives: [Stream](./stream.md), [Pipeline](./pipeline.md), and [Sink](./sink.md). Streams are lazy descriptions — nothing executes until a terminal operation is called.

**Related Types:**
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
