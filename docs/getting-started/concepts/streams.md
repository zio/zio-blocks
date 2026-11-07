---
id: streams
title: "Streams in a Nutshell"
sidebar_label: "Streams"
description: "The core idea of the Streams block: describe a pipeline over data that arrives piece by piece, with typed errors and safe resources, then run it."
keywords:
  - "Getting Started"
  - "Streams"
  - "Pipeline"
  - "Typed Errors"
---

A `Stream[E, A]` describes a sequence of `A` values that arrives piece by piece, and that may fail with an error of type `E`. You build a pipeline by transforming the stream, then run it to get a result. Because a stream pulls one element at a time, it processes files, sockets and query results that do not fit in memory.

## The Problem It Solves

Reading a large file into a `List` works until the file is larger than the heap. Iterators avoid that, but they have no way to report a typed error, no way to close the file when processing stops early, and no clear point where the work happens.

A stream fixes all three. Its error type is part of its type, it can own the resource it reads from, and nothing happens until you run it.

## Describe, Then Run

We describe a stream of orders and a pipeline over it. These lines do no work yet; they only describe what should happen:

```scala mdoc:silent
import zio.blocks.streams._

final case class Order(id: Int, amountCents: Long, paid: Boolean)

val orders: Stream[Nothing, Order] = Stream(
  Order(1, 2500L, paid = true),
  Order(2, 1200L, paid = false),
  Order(3, 4800L, paid = true),
  Order(4, 900L, paid = true)
)

val paidAmounts: Stream[Nothing, Long] = orders.filter(_.paid).map(_.amountCents)
```

The type `Stream[Nothing, Order]` says that this stream cannot fail. Running it does the work and returns a plain `Either`, with the error on the left and the result on the right:

```scala mdoc
paidAmounts.runCollect

paidAmounts.runFold(0L)((total, cents) => total + cents)
```

The same `paidAmounts` value can run again, and each run starts from the beginning.

## Errors Are Part of the Type

A step that can fail turns a `Stream[Nothing, A]` into a stream with a real error type. Here each text line becomes an order, or fails with a `BadLine` error:

```scala mdoc:silent
final case class BadLine(line: String)

def parse(line: String): Stream[BadLine, Order] =
  line.split(",") match {
    case Array(id, cents, paid) if id.toIntOption.isDefined && cents.toLongOption.isDefined =>
      Stream.succeed(Order(id.toInt, cents.toLong, paid == "paid"))
    case _ =>
      Stream.fail(BadLine(line))
  }

val parsed: Stream[BadLine, Order] =
  Stream("1,2500,paid", "2,oops,unpaid", "3,4800,paid").flatMap(parse)
```

Running the stream stops at the first error and returns it as a value:

```scala mdoc
parsed.runCollect
```

`Stream#catchAll` replaces a failure with another stream. Applied to the parsing step of each line, it turns a bad line into an empty stream, so the pipeline skips that line and keeps going, and the error type becomes `Nothing` again:

```scala mdoc
Stream("1,2500,paid", "2,oops,unpaid", "3,4800,paid")
  .flatMap(line => parse(line).catchAll(_ => Stream.empty))
  .map(_.id)
  .runCollect
```

Applied to the whole `parsed` stream instead, `catchAll` would take over at the first error and end the run there, without order 3.

## What Else Streams Give You

The block is built for real input and output:

- `Stream.fromAcquireRelease` opens a file or a connection when the stream starts and closes it when the stream ends, even if it fails or stops early.
- A `Sink` describes how to consume a stream, such as counting, folding or writing to a file, and `Stream#run` connects the two.
- Elements of type `Int`, `Long`, `Double` and the other primitives stay unboxed as they flow through a pipeline.
- The run methods on this page block the calling thread and exist on the JVM. On Scala.js, or whenever a thread must not block, use the `Async` variants such as `Stream#runCollectAsync`.

## Learn More

The [Your First Streaming Pipeline](../../guides/your-first-streaming-pipeline.md) tutorial builds a complete pipeline over a log file, and the [Streams reference](../../reference/streams/index.md) documents every operator. The next page shows how to [use ZIO Blocks with ZIO, cats-effect or plain Scala](../use-with-effect-systems.md).
