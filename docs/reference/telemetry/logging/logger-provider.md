---
id: logger-provider
title: "LoggerProvider"
description: "Root factory for Logger instances in the telemetry module's logging area: binds Resource, LogRecordProcessors, and ContextStorage together."
keywords:
  - "Structured Logging"
  - "Logging Configuration"
  - "Logger Factory"
  - "LoggerProvider"
---

`LoggerProvider` is the root factory for `Logger` instances in the logging area of the telemetry module. It binds a `Resource` (the entity-level service descriptor), an ordered pipeline of `LogRecordProcessor`s, and a `ContextStorage[Option[SpanContext]]` together, then mints named `Logger` objects that all inherit that shared configuration. Its constructor is package-private; use `LoggerProvider.builder` and the returned `LoggerProviderBuilder` to configure and create a provider.

- **AutoCloseable lifecycle** — `close()` delegates to `shutdown()`, so the provider can be used in `scala.util.Using.resource` blocks or Java `try`-with-resources.
- **Multi-processor fan-out** — Any number of `LogRecordProcessor`s can be registered; all are called in order on every emitted `LogRecord`.
- **Trace-context-aware** — Sharing the same `ContextStorage[Option[SpanContext]]` instance with a `TracerProvider` enables automatic trace–log correlation: every `LogRecord` emitted inside an active span is stamped with the span's trace and span identifiers as unboxed primitive fields.
- **Builder pattern** — `LoggerProvider.builder` returns a `LoggerProviderBuilder`; configure in any order and finish with `build()`.

The two cooperating classes share the following public surface:

```scala
final class LoggerProvider private[telemetry] (
  resource: Resource,
  private val processors: Array[LogRecordProcessor],
  contextStorage: ContextStorage[Option[SpanContext]]
) extends AutoCloseable {

  // Logger Acquisition
  def get(name: String, version: String = ""): Logger

  // Lifecycle
  def shutdown(): Unit
  override def close(): Unit
}

object LoggerProvider {
  def builder: LoggerProviderBuilder
}

final class LoggerProviderBuilder private[telemetry] (...) {
  // Builder — Configuration
  def setResource(resource: Resource): LoggerProviderBuilder
  def addLogRecordProcessor(processor: LogRecordProcessor): LoggerProviderBuilder
  def setContextStorage(contextStorage: ContextStorage[Option[SpanContext]]): LoggerProviderBuilder

  def build(): LoggerProvider
}
```

## Usage

The following block shows the full lifecycle of a `LoggerProvider` — building it, obtaining a `Logger`, emitting a record, and shutting down:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .addLogRecordProcessor(LogRecordProcessor.noop)
  .build()

val logger = provider.get("com.example.OrderService", "1.0.0")
logger.info("order placed", "orderId" -> AttributeValue.StringValue("ord-001"))

provider.shutdown()
```

The global `log` singleton wraps this pattern behind a zero-setup, macro-enriched API. Call `log.install(provider.get("com.example"))` once at application startup to replace its built-in no-op with a production-configured logger.

## Creating Values

We build a `LoggerProvider` in two steps: obtain a `LoggerProviderBuilder` from `LoggerProvider.builder`, configure it, then call `build()`.

### Starting with `LoggerProvider.builder`

`LoggerProvider.builder` is the sole public entry point for construction. It returns a fresh `LoggerProviderBuilder` pre-populated with `Resource.default` (service name `"unknown_service"` plus SDK attributes), an empty processor list, and no explicit `ContextStorage`:

```scala
object LoggerProvider {
  def builder: LoggerProviderBuilder
}
```

We obtain a builder and verify its defaults are acceptable before adding custom configuration:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val builder         = LoggerProvider.builder
val minimalProvider = builder.build()
minimalProvider.shutdown()
```

### Building with `LoggerProviderBuilder.build`

`LoggerProviderBuilder.build()` resolves the accumulated configuration and returns an immutable `LoggerProvider`. If `setContextStorage` was never called, it substitutes `ContextStorage.defaultSpanContextStorage` — the same shared singleton that `TracerProvider` defaults to, making trace–log correlation automatic when both are installed with their default settings:

```scala
final class LoggerProviderBuilder private[telemetry] (...) {
  def build(): LoggerProvider
}
```

The following example assembles a fully specified provider for a `"payment-service"`:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "payment-service")))
  .addLogRecordProcessor(LogRecordProcessor.noop)
  .build()

provider.shutdown()
```

## Core Operations

The full API of `LoggerProvider` and `LoggerProviderBuilder` falls into three categories: the builder's configuration methods, the single method that produces a `Logger`, and the lifecycle methods that release processor resources.

### Builder — Configuration

The builder methods `setResource`, `addLogRecordProcessor`, and `setContextStorage` control every aspect of a provider before it is built. Each method mutates the builder in place and returns `this`, enabling a fluent chain.

#### Describing the service entity

`LoggerProviderBuilder.setResource` replaces the `Resource` that the provider stamps on every `LogRecord`. A `Resource` wraps a typed `Attributes` collection; the most common attribute is `service.name`, accessed via the predefined `Attributes.ServiceName` key. If `setResource` is never called, the builder uses `Resource.default`:

```scala
final class LoggerProviderBuilder private[telemetry] (...) {
  def setResource(resource: Resource): LoggerProviderBuilder
}
```

We override the resource to give the provider a meaningful service identity:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "inventory-service")))
  .build()

provider.shutdown()
```

#### Adding log record processors

`LoggerProviderBuilder.addLogRecordProcessor` appends a `LogRecordProcessor` to the pipeline. Multiple calls accumulate processors; all are called in order on every `LogRecord` the `Logger` emits. Each processor's `onEmit` callback receives the full record including severity, body, typed attributes, and trace-correlation fields:

```scala
final class LoggerProviderBuilder private[telemetry] (...) {
  def addLogRecordProcessor(processor: LogRecordProcessor): LoggerProviderBuilder
}
```

We register two processors in insertion order — a console writer alongside an exporter:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .addLogRecordProcessor(LogRecordProcessor.noop) // replace with a console writer
  .addLogRecordProcessor(LogRecordProcessor.noop) // replace with an OTLP exporter
  .build()

provider.shutdown()
```

:::caution
Processors are called sequentially on the calling thread. Long-running processors delay emission for all subsequent processors; consider wrapping I/O-bound exporters in an async-buffering adapter.
:::

#### Sharing the context store

`LoggerProviderBuilder.setContextStorage` overrides the `ContextStorage[Option[SpanContext]]` used to propagate the active span context into each emitted `LogRecord`. When not set, the builder uses `ContextStorage.defaultSpanContextStorage`, the same singleton that `TracerProvider` defaults to, giving automatic trace–log correlation with no additional setup. Pass an explicit shared instance when isolating correlation in tests or integrating with a custom async runtime:

```scala
final class LoggerProviderBuilder private[telemetry] (...) {
  def setContextStorage(contextStorage: ContextStorage[Option[SpanContext]]): LoggerProviderBuilder
}
```

The following example creates one `ContextStorage` instance and hands it to both a `TracerProvider` and a `LoggerProvider`, ensuring that every log record emitted inside a span carries that span's identifiers:

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

:::note
Full `ScopedValue` support requires JDK 25 or later.
:::

### Logger Acquisition

`LoggerProvider.get` produces a `Logger` bound to a named instrumentation scope. Pass a scope name — typically a package or library name — and an optional version string. The returned `Logger` inherits the provider's `Resource`, processor list, and `ContextStorage`; every `LogRecord` it emits is stamped with the given `InstrumentationScope`:

```scala
final class LoggerProvider private[telemetry] (...) {
  def get(name: String, version: String = ""): Logger
}
```

We retrieve a versioned logger for one component alongside an unversioned one for another:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .addLogRecordProcessor(LogRecordProcessor.noop)
  .build()

val logger  = provider.get("com.example.OrderService")
val loggerV = provider.get("com.example.PaymentService", "2.1.0")

logger.info("order received")
loggerV.warn("payment gateway slow")

provider.shutdown()
```

:::note
`LoggerProvider.get` allocates a new `Logger` on every call. Assign the result to a `val` at startup and reuse it throughout the service lifetime rather than calling `get` per request.
:::

### Lifecycle

`LoggerProvider.shutdown` and `LoggerProvider.close` manage the provider's operational lifetime. `LoggerProvider.shutdown` calls `shutdown()` on every registered processor in insertion order, flushing and releasing their resources. `LoggerProvider.close` is an alias for `shutdown()` that satisfies `AutoCloseable`, enabling use in `scala.util.Using.resource` blocks:

```scala
final class LoggerProvider private[telemetry] (...) extends AutoCloseable {
  def shutdown(): Unit
  override def close(): Unit
}
```

We use `scala.util.Using.resource` to guarantee cleanup even if a processor throws:

```scala mdoc:compile-only
import scala.util.Using
import zio.blocks.telemetry._

Using.resource(LoggerProvider.builder.addLogRecordProcessor(LogRecordProcessor.noop).build()) { provider =>
  val logger = provider.get("com.example")
  logger.info("startup complete")
}
```

:::caution
`shutdown()` does not catch exceptions thrown by individual processors. Wrap the call in a `try/finally` block when processors may throw during cleanup.
:::

## Subtypes and Related Types

Three closely associated types complete the `LoggerProvider` story: the mutable builder that produces it, the `Logger` scope instance it vends, and the `log` global singleton that holds a reference to one.

| Type                    | Relationship                                                                                                                                                                                        |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `LoggerProviderBuilder` | Mutable builder returned by `LoggerProvider.builder`. Accumulates resource, processor, and context-storage settings; call `build()` to produce an immutable `LoggerProvider`.                       |
| `Logger`                | Produced by `LoggerProvider.get(name, version)`. Emits `LogRecord` snapshots through the provider's shared processor pipeline and reads `SpanContext` from the shared `ContextStorage`.             |
| `log` (object)          | Application-level global singleton backed by a shared `Logger`. Call `log.install(logger)` to replace its built-in no-op; macro-generated methods capture call-site source location at compile time. |

We install a configured `Logger` into the global `log` singleton at application startup so that call sites need no provider reference:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "catalog")))
  .addLogRecordProcessor(LogRecordProcessor.noop)
  .build()

log.install(provider.get("com.example.catalog"))

log.info("server started")
```

## Integration

`LoggerProvider` sits at the head of the Logging type chain. It produces `Logger` instances; each `Logger` emits `LogRecord` snapshots that carry typed `Attributes`, a nanosecond timestamp, a `Severity` level, an `InstrumentationScope`, and — when a span is active — the trace and span identifiers read from the shared `ContextStorage`. Those records flow through the ordered `LogRecordProcessor` pipeline, where formatters, filters, and exporters transform or route them.

The most important cross-pillar integration point is trace–log correlation. When a `TracerProvider` and a `LoggerProvider` share the same `ContextStorage[Option[SpanContext]]` instance, every log record emitted while a span is active is automatically stamped with `traceIdHi`, `traceIdLo`, `spanId`, and `traceFlags` as unboxed primitive fields — no boxing occurs until a `LogFormatter` runs. Because both providers default to the same `ContextStorage.defaultSpanContextStorage` singleton, correlation is active with no explicit configuration. For explicit control, pass a shared instance via `setContextStorage` to both providers — the same pattern shown in [TracerProvider](../tracing/tracer-provider.md).

For a complete picture of how `LoggerProvider`, `TracerProvider`, and `MeterProvider` interconnect across all three observability pillars, see the [Telemetry module index](../index.md).
