---
id: tracer-provider
title: "TracerProvider"
description: "Root factory for distributed tracing: produces Tracer instances sharing a Resource, Sampler, SpanProcessors, and ContextStorage."
keywords:
  - "Distributed Tracing"
  - "Tracing Configuration"
  - "Tracer Factory"
  - "TracerProvider"
---

`TracerProvider` exists so tracing is configured once, in a single place. You set the service identity ([`Resource`](../shared/resource.md)), the sampling policy ([`Sampler`](./sampler.md)), and where finished spans go ([`SpanProcessor`](./span-processor.md)s) when you build it at startup; from then on every [`Tracer`](./tracer.md) it hands out — and every span they create — shares that one configuration, so all your telemetry carries a consistent identity and policy. You build it once, install it, and rarely touch it again.

```scala
final class TracerProvider private[telemetry] (...) extends AutoCloseable {
  def get(name: String, version: String = ""): Tracer  // a Tracer for a named scope

  def shutdown(): Unit        // shut down every processor; call once at exit
  def forceFlush(): Unit      // ask processors to export buffered spans now
  override def close(): Unit  // alias for shutdown() — satisfies AutoCloseable
}

object TracerProvider {
  def builder: TracerProviderBuilder
}

final class TracerProviderBuilder private[telemetry] (...) {
  def setResource(resource: Resource): TracerProviderBuilder
  def setSampler(sampler: Sampler): TracerProviderBuilder
  def addSpanProcessor(processor: SpanProcessor): TracerProviderBuilder
  def setContextStorage(contextStorage: ContextStorage[Option[SpanContext]]): TracerProviderBuilder
  def build(): TracerProvider
}
```

## Usage

Build a provider once at startup, set only what you need — a `Resource` for your service identity, a `Sampler`, and the `SpanProcessor`s that export your spans — then `get` a `Tracer` from it. Anything you leave unset takes a sensible default (record every span; keep spans in memory until you add a processor):

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "payments")))
  .setSampler(ParentBasedSampler(AlwaysOnSampler))
  .addSpanProcessor(SpanProcessor.noop) // replace with an OTLP exporter
  .build()

val tracer = provider.get("com.example.payments")
tracer.span("process-payment") { span =>
  span.setAttribute("payment.id", "pay-001")
}

provider.shutdown()
```

Most applications never hold the provider directly. Install it once on the global `trace` object at startup and use [`trace.span`](./index.md) everywhere, removing the setup burden from individual call sites:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = TracerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "catalog")))
  .build()

trace.install(provider)
```

## Lifecycle

Call `shutdown()` once when the app exits — it flushes and releases every processor. (`close()` is an `AutoCloseable` alias for it; `forceFlush()` exports buffered spans on demand.)

## Trace–Log Correlation

Logs emitted inside a span are correlated with it automatically: a `TracerProvider` and a `LoggerProvider` share the same default context storage, so every log record carries that span's trace and span IDs with no wiring. (Override `setContextStorage` on both only to isolate correlation — e.g. in tests.) See the [Telemetry module index](./index.md).
