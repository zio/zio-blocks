---
id: logger-provider
title: "LoggerProvider"
description: "Root factory for structured logging: produces Logger instances sharing a Resource, LogRecordProcessor pipeline, and ContextStorage."
keywords:
  - "Structured Logging"
  - "Logging Configuration"
  - "Logger Factory"
  - "LoggerProvider"
---

A `LoggerProvider` is what makes [`Logger`](./logger.md)s. You build one at startup, and it hands out every logger your application uses.

It exists so that two questions get answered once instead of at every call site: *who is logging* — your service's identity, a [`Resource`](../shared/resource.md) — and *where the logs go* — the [`LogRecordProcessor`](./log-record-processor.md)s that render or ship each record. Every logger the provider creates inherits both, so pointing your whole application at a different destination is a one-line change at startup rather than an edit everywhere you log.

Most applications never hold a provider directly: build one, hand a logger from it to `log.install(...)`, and use the global [`log`](./index.md) from then on. Reach for the provider itself when you need that startup configuration under your own control.

```scala
final class LoggerProvider private[telemetry] (...) extends AutoCloseable {
  def get(name: String, version: String = ""): Logger  // a Logger for a named scope

  def shutdown(): Unit        // shut down every processor; call once at exit
  override def close(): Unit  // alias for shutdown() — satisfies AutoCloseable
}

object LoggerProvider {
  def builder: LoggerProviderBuilder
}

final class LoggerProviderBuilder private[telemetry] (...) {
  def setResource(resource: Resource): LoggerProviderBuilder
  def addLogRecordProcessor(processor: LogRecordProcessor): LoggerProviderBuilder
  def setContextStorage(contextStorage: ContextStorage[Option[SpanContext]]): LoggerProviderBuilder
  def build(): LoggerProvider
}
```

## Usage

Build a provider once at startup, set only what you need — a `Resource` for your service identity and the `LogRecordProcessor`s that handle your records — then `get` a `Logger` from it. Anything left unset takes a sensible default (`Resource.default`, and an empty pipeline until you add a processor):

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "payments")))
  .addLogRecordProcessor(LogRecordProcessor.noop) // replace with a console writer or OTLP exporter
  .build()

val logger = provider.get("com.example.payments")
logger.info("order placed", "orderId" -> AttributeValue.StringValue("ord-001"))

provider.shutdown()
```

Each `get` allocates a fresh `Logger`, so bind one to a `val` per component at startup rather than calling `get` per request. More often, hand a single logger to `log.install` and let the rest of the code use `log.*`, keeping provider wiring out of individual call sites:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "catalog")))
  .addLogRecordProcessor(LogRecordProcessor.noop)
  .build()

log.install(provider.get("com.example.catalog"))

log.info("server started")
```

## Lifecycle

Call `shutdown()` once when the app exits — it shuts down every registered processor in insertion order, flushing and releasing their resources. (`close()` is an `AutoCloseable` alias, so a provider works in `scala.util.Using.resource` and Java try-with-resources.)

## Trace–log correlation

Logs emitted inside a span are correlated with it automatically: a `LoggerProvider` and a [`TracerProvider`](../tracing/tracer-provider.md) share the same default context storage, so every record carries that span's trace and span IDs as unboxed primitive fields, with no wiring. To take explicit control — isolating correlation in tests, or integrating with a custom runtime — create one `ContextStorage` and pass it to `setContextStorage` on both providers:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val sharedStorage = ContextStorage.create[Option[SpanContext]](None)

val tp = TracerProvider.builder.setContextStorage(sharedStorage).build()
val lp = LoggerProvider.builder.setContextStorage(sharedStorage).build()

trace.install(tp)
log.install(lp.get("com.example"))

tp.shutdown()
lp.shutdown()
```

For the full picture of how `LoggerProvider`, `TracerProvider`, and `MeterProvider` interconnect, see the [Telemetry module index](../index.md).
