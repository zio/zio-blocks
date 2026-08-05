---
id: index
title: "OTLP Export"
description: "Send recorded telemetry out of your process to an OpenTelemetry collector, and carry trace context across service boundaries."
keywords:
  - "OpenTelemetry Protocol"
  - "Telemetry Export"
  - "Trace Propagation"
  - "OTLP"
---

The core telemetry module records signals inside your process. This module gets them out: it speaks OTLP over HTTP, so anything that accepts OpenTelemetry data — a collector, Jaeger, Tempo, a vendor's endpoint — can receive your traces, logs, and metrics.

It also carries trace context *between* processes. A trace that stops at your service boundary isn't much use, so a propagator reads and writes the standard headers that let two services contribute spans to the same trace.

## Installation

Add the module to your build file:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-telemetry-otel" % "@VERSION@"
```

## Configuring the Endpoint and Batching

One `ExporterConfig` answers two questions for an exporter: where to send data, and how much to hold before sending it. Both matter for the same reason — a collector is a network hop away, so sending one span per request would add a round trip to every request you were trying to measure. Records accumulate and go out in batches instead.

```scala
final case class ExporterConfig(
  endpoint: String             = "http://localhost:4318",
  headers: Map[String, String] = Map.empty,
  timeout: Duration            = Duration.ofSeconds(30),
  maxQueueSize: Int            = 2048,
  maxBatchSize: Int            = 512,
  flushIntervalMillis: Long    = 5000
)
```

Every field has a default, so `ExporterConfig()` is valid and points at a collector on localhost — the usual local development setup. `endpoint` is the collector's base URL, `headers` carries whatever it needs to accept you (typically an API key or bearer token), and `timeout` bounds a single send attempt:

```scala mdoc:compile-only
import zio.blocks.telemetry.otel._
import java.time.Duration

val config = ExporterConfig(
  endpoint = "https://otlp.example.com:4318",
  headers = Map("Authorization" -> "Bearer <token>"),
  timeout = Duration.ofSeconds(10)
)
```

Headers are treated as sensitive: `toString` prints their count, never their contents, so a config logged at startup can't leak a token.

The three batching fields trade latency against overhead. `flushIntervalMillis` is how long a partial batch waits before going out anyway — lower it to see data sooner, raise it to send fewer, larger requests. `maxBatchSize` is how many records go in one request; a batch is sent as soon as it fills, without waiting for the interval. `maxQueueSize` is how many records may be waiting at once, which is your backpressure limit: once the queue is full, further records are dropped rather than allowed to grow without bound. The defaults suit a service with steady moderate traffic; a high-throughput one should raise `maxQueueSize` before it starts dropping.

## Sending the Bytes

`HttpSender` is the one piece of the export path that actually touches the network. An exporter hands it a URL, headers, and a body of OTLP bytes; it performs the request and returns the response.

It's a separate trait so the network is replaceable. Real deployments need things a fixed HTTP client can't know about — an outbound proxy, request signing, a corporate TLS setup — and tests need the opposite: no network at all.

```scala
trait HttpSender {
  def send(url: String, headers: Map[String, String], body: Array[Byte]): HttpResponse
  def shutdown(): Unit
}

object HttpSender {
  def jdk(timeout: Duration = Duration.ofSeconds(30)): HttpSender
}
```

`HttpSender.jdk` wraps `java.net.http.HttpClient`, which ships with the JVM, so nothing is added to your dependencies. The timeout applies to both connecting and completing a request, and `shutdown()` releases the client's resources at exit:

```scala mdoc:compile-only
import zio.blocks.telemetry.otel._
import java.time.Duration

val sender = HttpSender.jdk(Duration.ofSeconds(10))
```

`send` returns an `HttpResponse` rather than throwing, so an exporter can decide whether a failure is worth retrying. Its `firstHeader(name)` looks a header up case-insensitively, which is what you need for a `Retry-After` on a `429` or `503`.

To write your own, implement the two methods: return a `2xx` status for a send the exporter should treat as delivered, anything else to mark it failed. Most custom senders are wrappers rather than replacements — sign the request, pick a proxy, count attempts, then delegate to `HttpSender.jdk(...)` and pass its response back.

## Propagating Context Across Services

One request usually touches several services. Your API calls an inventory service, which calls a database proxy — and you want that whole journey to read as *one* trace, not three unrelated ones.

That doesn't happen by itself. Each service starts a fresh trace unless the caller tells it which trace it is already part of, and an HTTP call carries nothing but headers. So the caller writes the current trace's identity into a header, and the receiver reads it back out and continues that trace instead of beginning its own.

A propagator does that writing and reading. The identity travels as text in a standard header, which is what lets it cross between services written in different languages:

```scala
trait Propagator {
  def extract[C](carrier: C, getter: (C, String) => Option[String]): Option[SpanContext]
  def inject[C](spanContext: SpanContext, carrier: C, setter: (C, String, String) => C): C
  def fields: Seq[String]
}
```

The carrier is whatever holds your headers — a `Map`, your framework's request type — and you supply the accessor, so no HTTP library is assumed. `fields` names the headers a propagator touches, which is what you pass to a CORS or header-allowlist configuration.

Use `W3CTraceContextPropagator` unless something upstream requires otherwise: it implements the W3C `traceparent` standard, which is what OpenTelemetry emits by default and what most backends expect. `B3Propagator` covers Zipkin's format in two shapes — `B3Propagator.single` puts everything in one `b3` header, `B3Propagator.multi` splits it across `X-B3-TraceId`, `X-B3-SpanId`, and `X-B3-Sampled`.

On the way out, take the active span's context and write it into a header map:

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

On the way in, `extract` returns `None` when the header is absent or malformed, so a request without trace context simply starts its own trace. An extracted context is marked `isRemote = true`, which is how you tell a continued trace from one that started locally.

Extracting isn't enough on its own, though — the context has to be *active* while your handler runs, or the span you open won't attach to it. Hold the `ContextStorage` you gave the provider and scope the remote context around the work:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

val storage = ContextStorage.create[Option[SpanContext]](None)
trace.install(TracerProvider.builder.setContextStorage(storage).build())

val tracer = trace.get("api-service")

val parent: Option[SpanContext] =
  W3CTraceContextPropagator.extract(
    Map("traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"),
    (m: Map[String, String], k: String) => m.get(k)
  )

storage.scoped(parent) {
  tracer.span("handle-checkout", SpanKind.Server) { span =>
    span.setAttribute("http.route", "/checkout")
  }
}
```

Create the storage yourself: a provider's own is internal, so there's no reaching it after the fact. Note also that `trace.get` binds to whichever provider is installed when it runs, so take your tracers after `trace.install`.

## Exporting Traces, Logs, and Metrics

The exporters that carry each signal to a collector are currently internal to this module: `OtlpJsonTraceExporter`, `OtlpJsonLogExporter`, and `OtlpJsonMetricExporter` are `private[otel]`, as is the `BatchProcessor` that batches records before a send. There is no public way to construct one yet, so this page documents the pieces you *can* reach — configuration, transport, and propagation — and leaves the wiring recipes for when the API exposes an entry point.

Until then, the [Telemetry Guide](../../../guides/telemetry-guide.md) sketches the intended shape of that wiring, and the core module's in-process destinations cover development and testing: [`log.writer`](../logging/index.md) for formatted output, [`trace.collectedSpans`](../tracing/index.md) for recorded spans, and [`metric.reader`](../metrics/index.md) for measurement snapshots.

## See Also

- [Tracing](../tracing/index.md) — opening the spans this module exports, and [`SpanContext`](../tracing/span-context.md), the identity a propagator moves
- [Telemetry Reference](../index.md) — the core module that records what this one exports
