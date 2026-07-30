---
id: span-processor
title: "SpanProcessor"
description: "Hook for span lifecycle events (onStart, onEnd) in the telemetry module's tracing area. Implement to export or collect spans."
keywords:
  - "Distributed Tracing"
  - "Span Export"
  - "Lifecycle Hook"
  - "SpanProcessor"
sidebar_label: "SpanProcessor"
---

`SpanProcessor` is the extension point exporter authors implement to do something with finished spans — ship them to a backend, buffer them, or collect them for tests. A [`TracerProvider`](./tracer-provider.md) calls `onStart` when a sampled span begins and `onEnd` with the immutable [`SpanData`](./span-data.md) snapshot when it finishes. You rarely implement this directly: `SpanProcessor.noop` and the built-in in-memory collector cover development and testing, so you write one only to bridge spans to an export target such as OTLP.

```scala
trait SpanProcessor extends AutoCloseable {
  def onStart(span: Span): Unit         // span still mutable; RecordOnly + RecordAndSample
  def onEnd(spanData: SpanData): Unit   // immutable snapshot — export from here
  def shutdown(): Unit                  // flush and release; called by TracerProvider.shutdown()
  def forceFlush(): Unit                // flush buffered data now
  override def close(): Unit = shutdown()
}

object SpanProcessor {
  val noop: SpanProcessor  // ignores every event
}
```

## Implementing a Processor

A custom processor forwards each `SpanData` to an export target. Register it on the provider with `addSpanProcessor`; when several are registered, they receive callbacks in insertion order:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val exporter = new SpanProcessor {
  def onStart(span: Span): Unit       = ()
  def onEnd(spanData: SpanData): Unit = println(s"[EXPORT] ${spanData.name}")
  def shutdown(): Unit                = ()
  def forceFlush(): Unit              = ()
}

val provider = TracerProvider.builder.addSpanProcessor(exporter).build()
trace.install(provider)
trace.span("checkout") { _ => () } // prints: [EXPORT] checkout
provider.shutdown()
```

## Integration

The global `trace` object's default provider already registers an `InMemorySpanProcessor`, so `trace.collectedSpans` and `trace.clearSpans` are available for test assertions without any additional setup.
