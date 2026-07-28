---
id: tracer
title: "Tracer"
description: "Scope-bound span factory in the ZIO Blocks tracing pillar — creates, samples, and propagates spans automatically."
keywords:
  - "Span Creation"
  - "InstrumentationScope"
  - "Automatic Parent Propagation"
  - "Sampler Consultation"
  - "Zero-Alloc No-Op Span"
---

A `Tracer` creates [`Span`](./span.md)s for one [instrumentation scope](../instrumentation-scope.md) — a name/version identifying the library or component doing the work. It inherits its `Resource`, `Sampler`, `SpanProcessor`s, and `ContextStorage` from the [`TracerProvider`](./tracer-provider.md) that created it, so every span it opens shares that pipeline.

- **Automatic parent propagation** — the active span is read from `ContextStorage` and restored for the block's duration, so nested `span` calls form a parent/child hierarchy with no explicit wiring.
- **Zero-allocation when dropped** — if the `Sampler` drops a span, the block runs with the `Span.NoOp` singleton; nothing is allocated.
- **Purely synchronous** — no effect-system dependency; usable in any Scala code.

```scala
final class Tracer {
  // Create a span around a block; sampled, context-propagated, ended automatically
  def span[A](name: String)(f: Span => A): A
  def span[A](name: String, kind: SpanKind)(f: Span => A): A
  def span[A](name: String, kind: SpanKind, attributes: Attributes)(f: Span => A): A

  def spanBuilder(name: String): SpanBuilder   // manual lifecycle; bypasses sampler/processors
  def currentSpan: Option[SpanContext]         // the active span context, if any
}
```

## Usage

A root span wrapping a child span — the nested call becomes a child automatically:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider: TracerProvider = TracerProvider.builder.build()
val tracer: Tracer            = provider.get("checkout-service", "2.1.0")

val total: Double = tracer.span("calculate-total") { rootSpan =>
  rootSpan.setAttribute("cart.size", 3L)

  // child of "calculate-total" automatically
  tracer.span("fetch-prices", SpanKind.Client) { childSpan =>
    childSpan.setAttribute("db.system", "postgresql")
    99.95
  }
}

provider.shutdown()
```

`span` consults the `Sampler`, sets the active context, notifies each `SpanProcessor` on start, runs the block, and always ends the span in a `finally` — so exceptions never leak open spans.

## Obtaining a Tracer

`Tracer`'s constructor is package-private — you never build one directly. Get a named tracer from [`TracerProvider#get`](./tracer-provider.md) (production, with your configured provider) or [`trace.get`](./index.md) (a quick tracer off the global provider). A new instance is built per call, so cache it at initialization rather than calling on a hot path.

:::note
Library code should accept `Tracer` as a parameter rather than calling `trace.get` inside the library. Depending on the global singleton makes a library non-portable and forces callers to install a global provider to control sampling.
:::

## Operations

### Creating spans

The three `span` overloads add arguments in turn — a default `Internal` span, an explicit [`SpanKind`](./span-kind.md), and initial `Attributes`. Attributes passed here are visible to the `Sampler` before it decides; attributes set with `Span#setAttribute` inside the block are recorded after, so the sampler never sees them.

```scala mdoc:compile-only
import zio.blocks.telemetry._

val tracer: Tracer = trace.get("db-layer")

val attrs: Attributes = Attributes.builder.put("db.system", "postgresql").build

tracer.span("db-query", SpanKind.Client, attrs) { span =>
  span.setAttribute("db.statement", "SELECT * FROM orders WHERE id = $1")
}
```

### Manual spans

`spanBuilder` returns a [`SpanBuilder`](./span.md) pre-configured with this tracer's scope, for cases the higher-order `span` can't wrap — a custom start timestamp, links, or a span that crosses an async boundary. You must `end()` it yourself.

```scala mdoc:compile-only
import zio.blocks.telemetry._

val tracer: Tracer = trace.get("async-worker")

val span: Span = tracer.spanBuilder("async-work")
  .setKind(SpanKind.Producer)
  .setStartTimestamp(System.nanoTime())
  .startSpan()

try span.setAttribute("queue.name", "orders")
finally span.end()
```

:::caution
Spans from `spanBuilder` bypass the sampler and processors — the span is purely local. Prefer `span` whenever the higher-order API fits.
:::

### Reading the active context

`currentSpan` returns the active [`SpanContext`](./span-context.md) (or `None`), for attaching trace and span IDs to log records or outbound headers without a reference to the `Span`.

```scala mdoc:compile-only
import zio.blocks.telemetry._

val tracer: Tracer = trace.get("correlation")

tracer.span("outer") { _ =>
  val ctx: Option[SpanContext] = tracer.currentSpan
  val traceId: String          = ctx.map(_.traceIdHex).getOrElse("")
  val spanId: String           = ctx.map(_.spanId.toHex).getOrElse("")
  // traceId / spanId can now be injected into outbound headers or a log MDC
}
```
