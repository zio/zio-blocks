---
id: index
title: "Tracing"
description: "Tracing index: the trace entry point, TracerProvider, Tracer, Span, and supporting types for distributed tracing in the telemetry module."
keywords:
  - "Distributed Tracing"
  - "Trace Correlation"
  - "Tracing Overview"
  - "TracerProvider"
sidebar_label: "Tracing"
---

Tracing follows a single request as it moves through your application — and across services — recording each step as a **span**: a timed unit of work with a name, attributes, and an outcome. Spans nest into a **trace**, a parent/child tree that shows where a request spent its time and where it failed. Because one trace can stretch across several services, it's how you answer "why was this request slow?" when the answer is three network hops away.

You create spans through the `trace` object: `trace.span("handle-request") { span => … }` wraps a block of work — it opens a span, runs the block, and closes the span automatically when the block returns. A `trace.span` opened inside another automatically becomes its child, so the tree builds itself with no manual wiring. Inside the block you annotate the span with attributes, events, and a status, and classify its role with a [`SpanKind`](./span-kind.md) when it crosses a service boundary.

With no setup, `trace` records every span into an in-memory buffer — the default for development and tests, where `trace.collectedSpans` returns exactly what was recorded. For production, call `trace.install(provider)` once at startup to send spans to a real backend (Jaeger, OTLP, …); a [`Sampler`](./sampler.md) then decides which spans to keep so you aren't exporting everything under load. The types behind `trace` — [`TracerProvider`](./tracer-provider.md), [`Tracer`](./tracer.md), [`Span`](./span.md), and the [`SpanProcessor`](./span-processor.md)s — are configured once at startup; day to day you just call `trace.span`.

## Open a Span and Record Work

Wrap a unit of work in `trace.span(name) { span => … }`.

```scala
object trace {
  def span[A](name: String)(f: Span => A): A
}
```

The block receives a live `Span`; set attributes, add timeline events, and set the completion status on it. The span closes automatically when the block returns.

```scala mdoc:compile-only
import zio.blocks.telemetry._

trace.span("handle-request") { span =>
  span.setAttribute("http.route", "/orders")
  span.setAttribute("http.status_code", 200L)
  span.addEvent("validated")
  span.setStatus(SpanStatus.Ok)
}
```

## Classify a Span and Pre-Set Attributes

Two overloads add a [`SpanKind`](./span-kind.md) and, optionally, initial [`Attributes`](../shared/attributes.md).

```scala
object trace {
  def span[A](name: String, kind: SpanKind)(f: Span => A): A
  def span[A](name: String, kind: SpanKind, attributes: Attributes)(f: Span => A): A
}
```

`SpanKind` marks a span's role in a trace (`Server`, `Client`, `Producer`, `Consumer`, or the default `Internal`), and the `Attributes` set stamps values known at creation.

```scala mdoc:compile-only
import zio.blocks.telemetry._

trace.span("db-query", SpanKind.Client, Attributes.of(AttributeKey.string("db.system"), "postgresql")) { span =>
  span.setAttribute("db.statement", "SELECT * FROM orders")
}
```

## Nest Spans into a Trace Tree

A `trace.span` opened inside another automatically attaches to the enclosing span as its parent through the shared `ContextStorage`, so nested calls build a parent/child tree with no manual context passing.

```scala mdoc:compile-only
import zio.blocks.telemetry._

trace.span("checkout", SpanKind.Server) { _ =>
  trace.span("load-cart") { child =>
    child.setAttribute("cart.items", 3L)
  }
  trace.span("charge-card")(_ => ())
}
```

## Scope Spans to an Instrumentation Library

`trace.get(name)` returns a `Tracer` bound to a named instrumentation scope.

```scala
object trace {
  def get(name: String): Tracer
}
```

Prefer this in library code so spans are attributed to the library that produced them.

```scala mdoc:compile-only
import zio.blocks.telemetry._

val tracer: Tracer = trace.get("com.example.orders")
tracer.span("reserve-inventory")(_ => ())
```

## Inspect Recorded Spans in Tests

Under the default in-memory provider, completed spans are readable back.

```scala
object trace {
  def collectedSpans: List[SpanData]
  def clearSpans(): Unit
}
```

`trace.collectedSpans` returns every completed span as immutable [`SpanData`](./span-data.md), and `trace.clearSpans()` resets the buffer between cases.

```scala mdoc:compile-only
import zio.blocks.telemetry._

trace.clearSpans()
trace.span("unit-of-work")(_ => ())
val recorded: List[SpanData] = trace.collectedSpans
recorded.foreach(sd => println(sd.name))
```

## Install a Provider and Reset

`trace.install(provider)` swaps in a production `TracerProvider` — configured with a [`Resource`](../shared/resource.md), a `Sampler`, and one or more `SpanProcessor` exporters.

```scala
object trace {
  def install(provider: TracerProvider): Unit
  def removeAll(): Unit
}
```

`trace.removeAll()` detaches the provider, turning subsequent spans into no-ops. The Usage example below shows a full provider build and install.

## Usage

Tracing's core job is to **track a request through your application** — install a provider once at startup, then wrap each unit of work in a span:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val tracerProvider = TracerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "order-service")))
  .setSampler(AlwaysOnSampler)
  .build()

trace.install(tracerProvider)

trace.span("handle-request", SpanKind.Server) { span =>
  span.setAttribute("http.route", "/orders")

  trace.span("load-order") { child =>
    child.setAttribute("order.id", "ord-123")
  }

  span.addEvent("request-complete")
}
```

## See Also

- [Telemetry Guide](../../../guides/telemetry-guide.md) — tracing data flow, sampling, and production patterns
- [Telemetry Reference](../index.md) — module overview and all three pillars
- [Shared Vocabulary](../shared/index.md) — `Attributes`, `AttributeKey`, `Resource`, and `InstrumentationScope`
