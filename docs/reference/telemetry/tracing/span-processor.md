---
id: span-processor
title: "SpanProcessor"
description: "Hook for span lifecycle events (onStart, onEnd) in the telemetry Tracing sub-domain. Implement to export or collect spans."
keywords:
  - "SpanProcessor onStart onEnd"
  - "InMemorySpanProcessor testing"
  - "SpanProcessor.noop no-op"
  - "span export pipeline"
sidebar_label: "SpanProcessor"
---

`SpanProcessor` is the extension point for the Tracing sub-domain's lifecycle hooks. A `TracerProvider` calls `onStart` when a new sampled span begins and `onEnd` when it finishes, passing the completed `SpanData` snapshot to exporters, in-memory buffers, or testing collectors.

```scala
trait SpanProcessor extends AutoCloseable {
  def onStart(span: Span): Unit           // called synchronously on the span-creation thread
  def onEnd(spanData: SpanData): Unit     // called synchronously on the span-end thread
  def shutdown(): Unit
  def forceFlush(): Unit
  override def close(): Unit = shutdown()
}

object SpanProcessor {
  val noop: SpanProcessor  // no-op singleton; all methods are no-ops
}
```

## Creating Values

Three paths produce a `SpanProcessor`:

- **`SpanProcessor.noop`** — the zero-overhead no-op singleton; use as a placeholder.
- **`new InMemorySpanProcessor(capacity)`** — built-in collector that stores `SpanData` in a ring buffer. The default `trace` global uses one internally.
- **Custom implementation** — implement the trait to forward `SpanData` to an OTLP gRPC endpoint, a file writer, or any other export target.

```scala mdoc:compile-only
import zio.blocks.telemetry._

// Built-in no-op
val noop = SpanProcessor.noop

// Custom exporter
val custom = new SpanProcessor {
  def onStart(span: Span): Unit       = ()
  def onEnd(spanData: SpanData): Unit = println(s"[EXPORT] ${spanData.name}")
  def shutdown(): Unit                = ()
  def forceFlush(): Unit              = ()
}

val provider = TracerProvider.builder.addSpanProcessor(custom).build()
trace.install(provider)
trace.span("checkout") { _ => () }
// prints: [EXPORT] checkout
provider.shutdown()
```

## Core Operations

| Method | Description |
|--------|-------------|
| `onStart(span: Span)` | Called on span creation for `RecordOnly` and `RecordAndSample` decisions. The span is still mutable at this point. |
| `onEnd(spanData: SpanData)` | Called after `Span#end()` with the immutable `SpanData` snapshot. Use this for export. |
| `shutdown(): Unit` | Flush and release resources. Called by `TracerProvider.shutdown()`. |
| `forceFlush(): Unit` | Flush buffered data immediately. Called by `TracerProvider.forceFlush()`. |
| `close(): Unit` | Delegates to `shutdown()` — satisfies `AutoCloseable`. |

## Integration

Processors are registered via `TracerProvider.builder.addSpanProcessor(processor)`. Multiple processors can be added; they receive callbacks in insertion order. The global `trace` object's default provider uses an `InMemorySpanProcessor` internally, making `trace.collectedSpans` and `trace.clearSpans` available for test assertions without any additional setup.
