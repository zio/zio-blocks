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

It also carries trace context *between* processes. A trace that stops at your service boundary isn't much use, so a [propagator](./propagators.md) reads and writes the standard headers that let two services contribute spans to the same trace.

This is a separate artifact, JVM-only:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-telemetry-otel" % "@VERSION@"
```

## Where the Endpoint and Batching Are Configured

One [`ExporterConfig`](./exporter-config.md) holds everything about talking to a collector: its address, any authentication headers, and how much data to accumulate before sending. The defaults point at a collector on localhost, which is what a local development stack usually gives you:

```scala mdoc:compile-only
import zio.blocks.telemetry.otel._
import java.time.Duration

val config = ExporterConfig(
  endpoint = "https://otlp.example.com:4318",
  headers = Map("Authorization" -> "Bearer <token>"),
  timeout = Duration.ofSeconds(10)
)
```

## Where the Bytes Go

Sending is a seam you can replace. An [`HttpSender`](./http-sender.md) takes a URL, headers, and a body, and returns the response — the built-in one uses the JDK HTTP client:

```scala mdoc:compile-only
import zio.blocks.telemetry.otel._
import java.time.Duration

val sender = HttpSender.jdk(Duration.ofSeconds(10))
```

Implement the trait yourself to route through a proxy, add request signing, or capture requests in a test.

## Propagating Context Across Services

One request usually touches several services. Your API calls an inventory service, which calls a database proxy — and you want that whole journey to read as *one* trace, not three unrelated ones.

That doesn't happen by itself. Each service starts a fresh trace unless the caller tells it which trace it is already part of, and an HTTP call carries nothing but headers. So the caller writes the current trace's identity into a header, and the receiver reads it back out and continues that trace instead of beginning its own.

A propagator does that writing and reading. The identity travels as text in a standard header, which is what lets it cross between services written in different languages: `W3CTraceContextPropagator` speaks the `traceparent` header most tools expect, and `B3Propagator` speaks Zipkin's older format. See [Propagators](./propagators.md) for both directions of the round trip.

## Exporting Traces, Logs, and Metrics

The exporters that carry each signal to a collector are currently internal to this module: `OtlpJsonTraceExporter`, `OtlpJsonLogExporter`, and `OtlpJsonMetricExporter` are `private[otel]`, as is the `BatchProcessor` that batches records before a send. There is no public way to construct one yet, so this page documents the pieces you *can* reach — configuration, transport, and propagation — and leaves the wiring recipes for when the API exposes an entry point.

Until then, the [Telemetry Guide](../../../guides/telemetry-guide.md) sketches the intended shape of that wiring, and the core module's in-process destinations cover development and testing: [`log.writer`](../logging/index.md) for formatted output, [`trace.collectedSpans`](../tracing/index.md) for recorded spans, and [`metric.reader`](../metrics/index.md) for measurement snapshots.

## See Also

- [ExporterConfig](./exporter-config.md) — endpoint, headers, timeout, and batching limits
- [HttpSender](./http-sender.md) — the transport seam and its JDK implementation
- [Propagators](./propagators.md) — W3C and B3 context propagation
- [Telemetry Reference](../index.md) — the core module that records what this one exports
