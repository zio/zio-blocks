---
id: propagators
title: "Propagators"
description: "Carry a trace across a service boundary by reading and writing standard headers — W3C traceparent or Zipkin B3."
keywords:
  - "Trace Propagation"
  - "Distributed Tracing"
  - "Traceparent Header"
  - "Propagator"
sidebar_label: "Propagators"
---

Inside one process, a span opened inside another becomes its child automatically. Across a network call, nothing is automatic: the receiving service starts a fresh trace, and you end up with two disconnected halves of one request.

A propagator closes that gap. It writes the active trace's identity into outgoing headers, and reads it back out on the other side, so both services record spans under the same trace id.

```scala
trait Propagator {
  def extract[C](carrier: C, getter: (C, String) => Option[String]): Option[SpanContext]
  def inject[C](spanContext: SpanContext, carrier: C, setter: (C, String, String) => C): C
  def fields: Seq[String]
}
```

The carrier is whatever holds your headers — a `Map`, your framework's request type — and you supply the accessor, so no HTTP library is assumed. `fields` names the headers a propagator touches, which is what you pass to a CORS or header-allowlist configuration.

## Choosing One

`W3CTraceContextPropagator` implements the W3C `traceparent` standard. Use it unless something upstream requires otherwise — it's what OpenTelemetry emits by default and what most backends expect.

`B3Propagator` covers Zipkin's format, in two shapes: `B3Propagator.single` puts everything in one `b3` header, and `B3Propagator.multi` splits it across `X-B3-TraceId`, `X-B3-SpanId`, and `X-B3-Sampled`. Reach for these when talking to an existing Zipkin-instrumented system.

## Injecting Into an Outgoing Call

Take the active span's context and write it into a header map:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

val tracer = trace.get("api-service")

tracer.span("call-inventory", SpanKind.Client) { span =>
  val headers = W3CTraceContextPropagator.inject(
    span.spanContext,
    Map.empty[String, String],
    (carrier: Map[String, String], k: String, v: String) => carrier + (k -> v)
  )
  // headers now holds "traceparent" -> "00-<traceId>-<spanId>-01"
  headers
}
```

## Extracting From an Incoming Request

`extract` returns `None` when the header is absent or malformed, so a request without trace context simply starts its own trace:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

val incoming = Map(
  "traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
)

val parent: Option[SpanContext] =
  W3CTraceContextPropagator.extract(incoming, (m: Map[String, String], k: String) => m.get(k))
```

An extracted context is marked `isRemote = true`, which is how you tell a continued trace from one that started locally.

## Continuing the Trace

Extracting a context isn't enough — it has to be *active* while your handler runs, or the span you open won't attach to it. Hold the `ContextStorage` you gave the provider and scope the remote context around the work:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

val storage = ContextStorage.create[Option[SpanContext]](None)
trace.install(TracerProvider.builder.setContextStorage(storage).build())

val tracer = trace.get("api-service")
val parent: Option[SpanContext] =
  W3CTraceContextPropagator.extract(Map("traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"),
                                    (m: Map[String, String], k: String) => m.get(k))

storage.scoped(parent) {
  tracer.span("handle-checkout", SpanKind.Server) { span =>
    span.setAttribute("http.route", "/checkout")
  }
}
```

Create the storage yourself: a provider's own is internal, so there's no way to reach it after the fact. Note also that `trace.get` binds to whichever provider is installed when it runs, so take your tracers after `trace.install`.

## See Also

- [SpanContext](../tracing/span-context.md) — the identity a propagator moves
- [Tracing](../tracing/index.md) — opening spans and scoping them
- [OTLP Export](./index.md) — sending the resulting spans to a collector
