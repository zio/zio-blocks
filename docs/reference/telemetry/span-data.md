---
id: span-data
title: "SpanData"
description: "Immutable snapshot of a completed span in the ZIO Blocks Telemetry tracing pillar — passed to SpanProcessors on end and returned by trace.collectedSpans."
keywords:
  - "SpanData"
  - "Span Snapshot"
  - "SpanProcessor"
  - "Tracing Export"
  - "Immutable Record"
  - "InstrumentationScope"
---

`SpanData` is an immutable snapshot of all data accumulated by a `Span` over its lifetime. It is created automatically when a span ends — the `Tracer` calls `Span#toSpanData` and passes the result to each registered `SpanProcessor#onEnd`. In tests, `trace.collectedSpans` returns a `List[SpanData]` buffered by the default in-memory processor.

```scala
final case class SpanData(
  name: String,
  kind: SpanKind,
  spanContext: SpanContext,
  parentSpanContext: SpanContext,
  startTimeNanos: Long,
  endTimeNanos: Long,
  attributes: Attributes,
  events: List[SpanEvent],
  links: List[SpanLink],
  status: SpanStatus,
  resource: Resource,
  instrumentationScope: InstrumentationScope
)
```

`parentSpanContext` is `SpanContext.invalid` for root spans. `startTimeNanos` and `endTimeNanos` are epoch nanoseconds.

## Usage

The following example records a span, then retrieves and inspects its `SpanData` snapshot via `trace.collectedSpans`:

```scala
import zio.blocks.telemetry._

trace.clearSpans()

trace.span("process-order") { span =>
  span.setAttribute(AttributeKey.string("order.id"), "ORD-999")
  span.setStatus(SpanStatus.Ok)
}

val sd: SpanData = trace.collectedSpans.head

println(sd.name)                                    // "process-order"
println(sd.status)                                  // SpanStatus.Ok
println(sd.attributes.get(AttributeKey.string("order.id"))) // Some("ORD-999")
println(sd.endTimeNanos > sd.startTimeNanos)        // true
```

`SpanData` can also be obtained directly from a `Span` at any time via `Span#toSpanData` — for instance, before the span ends, in which case `endTimeNanos` is `0L`.

## Key Fields

| Field | Type | Description |
|---|---|---|
| `name` | `String` | The operation name set at span creation. |
| `kind` | `SpanKind` | `Internal`, `Server`, `Client`, `Producer`, or `Consumer`. |
| `spanContext` | `SpanContext` | The trace ID, span ID, and sampling flags for this span. |
| `parentSpanContext` | `SpanContext` | The parent's context, or `SpanContext.invalid` for a root span. |
| `startTimeNanos` | `Long` | Epoch nanoseconds when the span started. |
| `endTimeNanos` | `Long` | Epoch nanoseconds when the span ended (`0L` if still recording). |
| `attributes` | `Attributes` | All key-value annotations set on the span. |
| `events` | `List[SpanEvent]` | Named point-in-time events recorded during the span. |
| `status` | `SpanStatus` | `Unset`, `Ok`, or `Error(description)`. |
| `resource` | `Resource` | The resource describing the producing service. |
| `instrumentationScope` | `InstrumentationScope` | The library scope that created the span. |
