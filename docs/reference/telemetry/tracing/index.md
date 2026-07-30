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

Tracing covers the complete span lifecycle for distributed tracing. The `trace` object is the entry point; behind it, [`TracerProvider`](./tracer-provider.md) is configured once at startup and produces [`Tracer`](./tracer.md) instances, and each `Tracer` opens and closes [`Span`](./span.md) scopes, consulting a [`Sampler`](./sampler.md) and notifying [`SpanProcessor`](./span-processor.md) listeners as spans begin and end.

`trace` is the zero-setup entry point and the primary tracing API. On import it delegates to an internally-managed `TracerProvider` that samples every span and buffers completed spans in memory, so `trace.span(...)` records immediately with no configuration — the default for development and tests, where `trace.collectedSpans` returns exactly what was recorded. Call `trace.install(provider)` once at application startup to route spans to real exporters.

## Open a span and record work

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

## Classify a span and pre-set attributes

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

## Nest spans into a trace tree

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

## Scope spans to an instrumentation library

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

## Inspect recorded spans in tests

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

## Install a provider and reset

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
