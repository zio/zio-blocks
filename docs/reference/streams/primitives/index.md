---
id: index
title: "Low-Level Primitives"
description: "Low-Level Primitives index: Reader and Writer, the live cursors a Stream and Sink compile into at execution time."
keywords:
  - "Pull-Based Streaming"
  - "Push-Based Writing"
  - "Primitives Overview"
  - "Reader"
  - "Writer"
sidebar_label: "Low-Level Primitives"
---

`Reader` and `Writer` are the live, stateful objects that a stream pipeline compiles into and runs on. Where [Core Types](../core/index.md) — `Stream`, `Pipeline`, and `Sink` — are lazy, immutable descriptions of what should happen, `Reader` and `Writer` are the machinery that actually happens: a `Reader` is a single-use cursor you pull from one element at a time, and a `Writer` is a single-use sink you push elements into one at a time. Building a `Stream` or a `Sink` costs nothing and produces no side effects; obtaining a `Reader` or driving a `Writer` does real work and holds real resources — an open file, a socket, a position in a buffer — until it is closed.

You compose against `Stream`, `Pipeline`, and `Sink` for the vast majority of pipelines, and a terminal operation such as `stream.runAsync(sink)` handles compiling to and draining a `Reader` for you. This section documents the two types for the cases where you work with one directly: writing a custom source or sink, wrapping a native I/O or NIO API, or understanding what a terminal operation is actually doing when it runs.

## Reader

[`Reader[+Elem]`](./reader.md) is the pull side: the cursor a `Stream` compiles into when it runs. It has two library-provided kinds — `Reader.SyncReader[Elem]`, whose pulls return directly, and `Reader.AsyncReader[Elem]`, whose pulls return `Async` — and an asynchronous reader is single-consumer, with exactly one owner responsible for awaiting its `close()`. Reach for `Reader` directly when you are wrapping a native asynchronous byte source (an `AsynchronousByteChannel`, a `ReadableStream`) into the library's execution model, or when you are driving a compiled stream by hand instead of through a `Sink`.

## Writer

[`Writer[-Elem]`](./writer.md) is the push side: a sink you feed elements into one at a time until it closes or fills, used internally by channel-based implementations and as an adapter over Java I/O. Reach for `Writer` directly when you are adapting an `OutputStream`-shaped or similarly push-based destination, or implementing a custom sink that a producer external to the stream pipeline writes into.

## See Also

- [Core Types](../core/index.md) — `Stream`, `Pipeline`, and `Sink`, the lazy descriptions that compile into these primitives
- [Streams Reference](../index.md) — module overview and how the two sections fit together
- [Asynchronous Stream Execution](../execution-and-compatibility/async-execution.md) — how a stream's graph decides between a `SyncReader` and an `AsyncReader`
