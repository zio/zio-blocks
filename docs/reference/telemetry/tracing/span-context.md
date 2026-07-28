---
id: span-context
title: "SpanContext"
description: "Propagatable identity of a span in the ZIO Blocks Telemetry tracing pillar — stores trace and span IDs as primitives for zero-allocation propagation."
keywords:
  - "SpanContext"
  - "Trace ID"
  - "Span ID"
  - "TraceFlags"
  - "Distributed Tracing"
  - "W3C Trace Context"
  - "isRemote"
---

`SpanContext` is the propagatable identity of a span. It carries the 128-bit trace ID inlined as two `Long` fields (`traceIdHi`, `traceIdLo`), a 64-bit span ID stored as the `SpanId` AnyVal, trace flags as the `TraceFlags` AnyVal, a vendor-specific trace state string, and a boolean indicating whether the context originated from a remote parent. All fields are stored as primitives to avoid boxing on the hot propagation path.

`SpanContext.invalid` is the sentinel value used when no span is active — all numeric fields are zero and both `isValid` and `isSampled` return `false`.

```scala
final case class SpanContext(
  traceIdHi: Long,
  traceIdLo: Long,
  spanId: SpanId,
  traceFlags: TraceFlags,
  traceState: String,
  isRemote: Boolean
)
```

## Usage

The following example reads the active span context from a `Tracer` and uses the trace ID hex string for log correlation:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val tracer: Tracer = trace.get("com.example")

tracer.span("handle-request") { _ =>
  val ctx: Option[SpanContext] = tracer.currentSpan

  ctx.foreach { c =>
    if (c.isValid) {
      // 32-character lowercase hex string suitable for injection into log MDC
      val traceId: String = c.traceIdHex
      println(s"trace=$traceId sampled=${c.isSampled}")
    }
  }
}

// Outside any active span — falls back to the invalid sentinel
val fallback: SpanContext = tracer.currentSpan.getOrElse(SpanContext.invalid)
assert(!fallback.isValid)
```

`SpanContext` values flow automatically through `ContextStorage` when spans are created via `Tracer#span`. Inject a `SpanContext` into outbound HTTP headers to propagate the W3C `traceparent` header to downstream services.

## Key Operations

| Member | Description |
|---|---|
| `isValid: Boolean` | Returns `true` if both the trace ID (`traceIdHi | traceIdLo != 0`) and the span ID are non-zero. |
| `isSampled: Boolean` | Returns `true` if the sampled flag in `traceFlags` is set. Use to decide whether to forward the trace context. |
| `traceIdHex: String` | Returns the 128-bit trace ID as a 32-character lowercase hex string. Suitable for log MDC and outbound header values. |
| `SpanContext.invalid` | The zero-valued sentinel representing "no active span". |
