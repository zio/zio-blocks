---
id: span-context
title: "SpanContext"
description: "Propagatable span identity: 128-bit trace ID (two Longs), SpanId, TraceFlags, trace state, and isRemote."
keywords:
  - "SpanContext tracing identity"
  - "traceIdHi traceIdLo SpanId TraceFlags"
  - "W3C TraceContext propagation"
  - "isValid isSampled traceIdHex"
sidebar_label: "SpanContext"
---

`SpanContext` is the propagatable portion of a span. It carries the 128-bit trace ID (split into two `Long` fields to avoid boxing), the `SpanId` (an `AnyVal` wrapping a `Long`), the `TraceFlags` (an `AnyVal` wrapping a `Byte`), a trace-state string, and an `isRemote` flag that marks contexts extracted from upstream HTTP headers. `SpanContext.invalid` is the sentinel used when no span is active.

```scala
final case class SpanContext(
  traceIdHi:   Long,        // high 64 bits of the 128-bit trace ID
  traceIdLo:   Long,        // low  64 bits of the 128-bit trace ID
  spanId:      SpanId,      // AnyVal wrapping a Long
  traceFlags:  TraceFlags,  // AnyVal wrapping a Byte
  traceState:  String,      // W3C tracestate header value
  isRemote:    Boolean      // true when extracted from an incoming request
) {
  def isValid:      Boolean // true when traceId or spanId is non-zero
  def isSampled:    Boolean // true when the sampled bit of traceFlags is set
  def traceIdHex:   String  // 32-char lowercase hex trace ID
}

object SpanContext {
  val invalid: SpanContext   // sentinel: all-zero IDs, not sampled, not remote
  def create(traceIdHi: Long, traceIdLo: Long, spanId: SpanId, traceFlags: TraceFlags,
             traceState: String, isRemote: Boolean): SpanContext
}
```

## Creating Values

Most code never constructs a `SpanContext` directly — `Tracer.span` generates one per new span. The two named constructors are for advanced use cases:

- **`SpanContext.invalid`** — the zero-value sentinel; `isValid` returns `false`.
- **`SpanContext.create`** — builds an explicit context for distributed propagation (header extraction) or testing.

```scala mdoc:compile-only
import zio.blocks.telemetry._

// Sentinel for "no active span"
val noSpan = SpanContext.invalid
assert(!noSpan.isValid)
assert(!noSpan.isSampled)

// Extract from an incoming W3C traceparent header (simplified)
val extracted = SpanContext.create(
  traceIdHi  = 0x4bf92f3577b34da6L,
  traceIdLo  = 0xa3ce929d0e0e4736L,
  spanId     = SpanId(0x00f067aa0ba902b7L),
  traceFlags = TraceFlags.sampled,
  traceState = "",
  isRemote   = true
)
assert(extracted.isValid)
assert(extracted.isSampled)
println(extracted.traceIdHex) // "4bf92f3577b34da6a3ce929d0e0e4736"
```

## Core Operations

| Method | Description |
|--------|-------------|
| `isValid: Boolean` | `true` when either `traceIdHi`, `traceIdLo`, or `spanId.value` is non-zero. |
| `isSampled: Boolean` | `true` when the sampled bit (`0x01`) of `traceFlags` is set. |
| `traceIdHex: String` | Returns the 128-bit trace ID as a 32-character lowercase hexadecimal string. |

## Integration

`SpanContext` is read from `ContextStorage` by `Tracer.currentSpan` and by `Logger` on every emit for automatic trace–log correlation. `SpanData` carries two `SpanContext` values: the span's own context and its parent's. `SpanContext.invalid` is the parent context for root spans (those with no parent).
