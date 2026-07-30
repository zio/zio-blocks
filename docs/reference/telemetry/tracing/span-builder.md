---
id: span-builder
title: "SpanBuilder"
description: "Mutable builder for creating spans with explicit kind, start timestamp, links, or parent context outside the normal Tracer.span lifecycle."
keywords:
  - "SpanBuilder startSpan"
  - "explicit span timestamp links"
  - "setKind setParent addLink"
  - "manual span management"
sidebar_label: "SpanBuilder"
---

`SpanBuilder` is a mutable builder for creating `Span` instances outside the normal `Tracer.span` callback lifecycle. Use it when you need fine-grained control over span metadata — explicit start timestamps, links to spans in other traces, or a non-default parent context — that the `span(name, kind, attrs)` overloads do not expose.

```scala
object SpanBuilder {
  def apply(name: String): SpanBuilder
}

final class SpanBuilder {
  // Configuration
  def setKind(kind: SpanKind): SpanBuilder
  def setParent(parentContext: SpanContext): SpanBuilder
  def setAttribute[A](key: AttributeKey[A], value: A): SpanBuilder
  def addLink(link: SpanLink): SpanBuilder
  def setStartTimestamp(nanos: Long): SpanBuilder
  def setResource(resource: Resource): SpanBuilder
  def setInstrumentationScope(scope: InstrumentationScope): SpanBuilder

  // Finalization
  def startSpan(): Span                                    // random trace ID
  def startSpan(traceIdHi: Long, traceIdLo: Long): Span   // explicit trace ID
}
```

:::caution
Spans created through `SpanBuilder#startSpan` bypass the `Tracer`'s `Sampler` and `SpanProcessor` list — no `onStart` or `onEnd` callbacks fire. For normal traced work, use `Tracer.span(...) { ... }`.
:::

## Usage

The following example creates a `Producer` span with an explicit start timestamp, a causal link to an upstream context, and explicit trace IDs — all capabilities unavailable through `Tracer.span`:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val upstreamCtx = SpanContext.invalid   // in practice, parsed from a queue message header

val span = SpanBuilder("enqueue-message")
  .setKind(SpanKind.Producer)
  .setParent(upstreamCtx)
  .setAttribute(AttributeKey.string("queue"), "orders")
  .addLink(SpanLink(upstreamCtx, Attributes.empty))
  .setStartTimestamp(1_000_000_000L)
  .startSpan()

try {
  span.setAttribute("message.id", "msg-456")
  span.setStatus(SpanStatus.Ok)
} finally {
  span.end(1_000_500_000L)
}

val data = span.toSpanData
assert(data.kind == SpanKind.Producer)
assert(data.startTimeNanos == 1_000_000_000L)
assert(data.endTimeNanos   == 1_000_500_000L)
```

## Integration

`Tracer.spanBuilder` calls `SpanBuilder.apply` internally and pre-configures it with the tracer's `Resource` and `InstrumentationScope`. When you call `Tracer.spanBuilder(name).startSpan()` via a `Tracer` obtained from a `TracerProvider`, the resource and scope are already set. When you call `SpanBuilder("name")` directly, set `setResource` and `setInstrumentationScope` yourself to ensure the exported `SpanData` carries the correct metadata.
