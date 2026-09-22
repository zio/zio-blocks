---
id: index
title: "Core Types"
description: "Core Types index: Stream, Pipeline, and Sink, the declarative descriptions you compose into a stream processing pipeline."
keywords:
  - "Pull-Based Streams"
  - "Stream Composition"
  - "Core Types Overview"
  - "Sink"
sidebar_label: "Core Types"
---

`Stream`, `Pipeline`, and `Sink` are the three types you compose when you build a stream processing pipeline. Each is a lazy, immutable description of what should happen, not a live process that is already running: a `Stream` describes a source of elements, a `Pipeline` describes a transformation from one element type to another, and a `Sink` describes how to consume elements into a result. You build these descriptions up with ordinary combinators — `map`, `filter`, `andThen`, `via` — before anything executes, then hand the finished description to a terminal operation such as `stream.runAsync(sink)` — or `stream.run(sink)`, which is JVM-only — which is the point where the description is compiled and actually driven.

That compilation step is what connects this page to its sibling section. Running a stream materializes it into a [`Reader`](../primitives/reader.md), a stateful, single-use cursor that a sink pulls from one element at a time. Readers (and the corresponding [`Writer`](../primitives/writer.md)) are the low-level primitives the library uses internally to execute a pipeline; you rarely construct or call one directly. They're documented separately under Low-Level Primitives — this section is about the descriptions you write, that section is about the machinery your descriptions compile into.

## Stream

[`Stream[+E, +A]`](./stream.md) describes a source of elements of type `A` that may fail with an error `E`. Reach for it whenever you need to represent "a sequence of values, produced lazily, possibly from an external resource" — reading lines from a file, paginating through an API, or simply transforming an in-memory collection without forcing it all into memory at once. Nothing runs until a terminal operation is driven, so a `Stream` value is cheap to build, store, and pass around.

## Pipeline

[`Pipeline[-In, +Out]`](./pipeline.md) describes a reusable transformation from elements of type `In` to elements of type `Out`. Reach for it when the same sequence of `map`/`filter`/`take`-style steps needs to be applied to more than one stream, or when you want to name and share a transformation independently of any particular source or consumer. A `Pipeline` composes with `andThen` and applies to a `Stream` with `stream.via(pipe)`, or to a `Sink` with `pipe.andThenSink(sink)`.

## Sink

[`Sink[+E, -A, +Z]`](./sink.md) describes how to consume elements of type `A` into a final result of type `Z`, possibly failing with `E`. Reach for it whenever you need to describe the endpoint of a pipeline — collecting elements into a `Chunk`, folding them into an aggregate, or writing them out to some destination — as a value you can build once and reuse across different streams.

## See Also

- [Streams Reference](../index.md) — module overview and how the three types fit together
- [Reader](../primitives/reader.md) and [Writer](../primitives/writer.md) — the low-level cursors a pipeline compiles into
