---
id: span-processor
title: "SpanProcessor"
description: "Lifecycle hook trait in the ZIO Blocks Telemetry tracing pillar — receives onStart and onEnd callbacks from a Tracer and drives span export."
keywords:
  - "SpanProcessor"
  - "Span Lifecycle"
  - "onStart"
  - "onEnd"
  - "InMemorySpanProcessor"
  - "AutoCloseable"
  - "Span Export"
---

`SpanProcessor` is an open trait that receives lifecycle notifications from a `Tracer`: `onStart` is called when a span begins and `onEnd` is called with the immutable `SpanData` snapshot immediately after the span ends. Processors chain in insertion order — `TracerProvider` invokes each registered processor in sequence. Use `SpanProcessor.noop` to satisfy the interface without side effects, and `InMemorySpanProcessor` in tests to capture and inspect completed spans.

```scala
trait SpanProcessor extends AutoCloseable {
  def onStart(span: Span): Unit
  def onEnd(spanData: SpanData): Unit
  def shutdown(): Unit
  def forceFlush(): Unit
  override def close(): Unit  // delegates to shutdown()
}
```

## Usage

The following example registers a custom `InMemorySpanProcessor` with a fresh `TracerProvider`, records a span, and reads the collected data:

```scala
import zio.blocks.telemetry._

// InMemorySpanProcessor is the built-in test implementation
val mem = new InMemorySpanProcessor()

val provider = TracerProvider.builder
  .addSpanProcessor(mem)
  .build()

trace.install(provider)

trace.span("x") { _ => () }

val spans: List[SpanData] = mem.collectedSpans
println(spans.head.name)  // "x"

provider.shutdown()
```

Production code typically implements `SpanProcessor` to batch spans and forward them to an OTLP exporter or a telemetry backend. `onEnd` is called on the thread that ended the span, so blocking implementations should hand off to a background queue.

## Key Operations

| Member | Description |
|---|---|
| `onStart(span: Span): Unit` | Called when a span starts recording. The span is still mutable at this point. |
| `onEnd(spanData: SpanData): Unit` | Called with the immutable snapshot after `span.end()` completes. |
| `shutdown(): Unit` | Releases processor resources. Called by `TracerProvider#shutdown`. |
| `forceFlush(): Unit` | Exports any buffered data immediately. |
| `SpanProcessor.noop` | Singleton no-op implementation — all methods are empty. |

:::caution
`onEnd` is invoked synchronously on the thread that ended the span. A slow or blocking processor will add latency to every span's end. Prefer an asynchronous, batching processor for production exporters.
:::
