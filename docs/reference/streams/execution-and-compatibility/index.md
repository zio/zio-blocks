---
id: index
title: "Execution and Compatibility"
description: "Execution and Compatibility index: async execution, primitive lanes, platform availability, and Scala 2 support for streams."
keywords:
  - "Asynchronous Streams"
  - "Primitive Specialization"
  - "Cross-Platform Streams"
  - "Scala 2 Compatibility"
sidebar_label: "Execution and Compatibility"
---

[Core Types](../core/index.md) documents the descriptions you write — `Stream`, `Pipeline`, `Sink` — and [Low-Level Primitives](../primitives/index.md) documents the `Reader` and `Writer` machinery those descriptions compile into. This section is about neither: it's the four cross-cutting concerns that apply once a description starts running — how a stream actually executes on a callback or by blocking, how it represents its elements at runtime, and what differs depending on which platform or Scala version compiles it. None of the four adds a new type to compose with; each explains a dimension that cuts across every `Stream`, `Pipeline`, and `Sink` you already have.

## Asynchronous Stream Execution

[Asynchronous Stream Execution](./async-execution.md) answers how one `Stream[E, A]` type describes both synchronous and asynchronous pipelines, with no second stream type, mode parameter, or annotation to track which one you're building. Read it when you need the async source constructors, the element-level async operators, the `*Async` terminal family, or the rules around cancellation and resource ownership for an asynchronously-driven stream.

## Zero-Boxing Optimization

[Zero-Boxing Optimization](./zero-boxing.md) answers how the library dispatches on a primitive element type to select a physical lane for synchronous interpretation, and how each lane signals end of stream. Read it when you're working with streams of `Int`, `Long`, `Double`, or the other primitive types and want to understand how lane selection works and where it does and doesn't apply — not as a benchmark, but as a structural explanation of the dispatch mechanism.

## Platform Differences: JVM and Scala.js

[Platform Differences](./platform-differences.md) answers which members exist only on the JVM, which are cross-platform, and what to write instead when a blocking terminal isn't available on Scala.js. Read it before cross-building code against streams, or when a Scala.js compile fails on a member that works fine on the JVM.

## Scala 2 Compatibility Design Note

[Scala 2 Compatibility Design Note](./scala-2-compatibility.md) answers why streams supports Scala 2.13 at all, the hot-path constraint that ruled out an obvious compatibility approach, and why the Scala 2 and Scala 3 sources are shared rather than split. Read it if you're touching version-specific code in `Stream` or `Sink`, or want to understand the trade-offs behind the current source layout.

## See Also

- [Streams Reference](../index.md) — module overview and how all three sections fit together
- [Core Types](../core/index.md) — `Stream`, `Pipeline`, and `Sink`, the descriptions this section explains the execution of
- [Low-Level Primitives](../primitives/index.md) — `Reader` and `Writer`, the cursors those descriptions compile into
