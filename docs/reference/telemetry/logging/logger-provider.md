---
id: logger-provider
title: "LoggerProvider"
description: "Production factory for Logger instances — configures Resource, LogRecordProcessors, and ContextStorage for the logging pillar."
keywords:
  - "LoggerProvider Builder"
  - "Logger Factory"
  - "LogRecordProcessor Configuration"
  - "Trace-Log Correlation"
  - "ContextStorage Propagation"
  - "Telemetry Provider Lifecycle"
  - "Structured Logging Setup"
---

`LoggerProvider` is a factory for [`Logger`](./logger.md) instances that share a common `Resource`, ordered pipeline of `LogRecordProcessor`s, and `ContextStorage` for active-span propagation. It is the production configuration entry point for the logging pillar of the ZIO Blocks Telemetry module — positioned between the global `log` singleton (which holds a reference to the installed `Logger`) and the per-scope `Logger` instances that library code receives via `LoggerProvider#get`.

- **Immutable after construction** — all settings are fixed when `build()` is called; no field can be mutated at runtime.
- **Builder pattern** — the primary constructor is package-private; `LoggerProvider.builder` is the only public entry point.
- **`AutoCloseable`** — `close()` delegates to `shutdown()`, so a provider can be managed with `scala.util.Using.resource` or a JVM shutdown hook.
- **Trace-context-aware** — the same `ContextStorage[Option[SpanContext]]` can be shared with [`TracerProvider`](../tracing/tracer-provider.md) so every log record carries the active span's trace and span IDs automatically.

The public surface of `LoggerProvider` and its companion builder is:

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
  // Builder Configuration — call before build()
  def setResource(resource: Resource): LoggerProviderBuilder
  def addLogRecordProcessor(processor: LogRecordProcessor): LoggerProviderBuilder
  def setContextStorage(contextStorage: ContextStorage[Option[SpanContext]]): LoggerProviderBuilder

  def build(): LoggerProvider
}
```

`LoggerProvider` sits in the Logging group alongside `Logger`. Its relationship to the rest of the telemetry module looks like this:

```
log (global singleton — holds a reference to a Logger)
  └── LoggerProvider   ← this type
        └── Logger     (obtained via LoggerProvider#get → log.install)
              └── log records → LogRecordProcessor pipeline
                                  └── ConsoleLogRecordProcessor
                                  └── custom exporters / batch processors
```

## Usage

The following example shows the complete lifecycle: configure a provider with a custom resource and processor, obtain a `Logger`, emit a record, then shut down:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import scala.util.Using

Using.resource(
  LoggerProvider.builder
    .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "payments")))
    .addLogRecordProcessor(new ConsoleLogRecordProcessor)
    .build()
) { provider =>
  val logger: Logger = provider.get("payments-service", "1.0.0")
  logger.info("server started")
}
```

`Using.resource` calls `provider.close()` — which delegates to `shutdown()` — automatically when the block exits, even on exception.

## Construction / Creating Instances

Because `LoggerProvider`'s primary constructor is package-private, the builder is the only supported construction path. We start with `LoggerProvider.builder`, call zero or more configuration methods on the returned `LoggerProviderBuilder`, and finish with `build()`. Every configuration method returns `this`, so calls chain fluently.

### `LoggerProvider.builder` — Start a new builder

`LoggerProvider.builder` returns a `LoggerProviderBuilder` pre-populated with the following defaults: `Resource.default` (SDK attributes plus `service.name = "unknown_service"`), an empty processor list, and `ContextStorage.defaultSpanContextStorage` (a `ScopedValue`-backed store on JVM).

```scala
object LoggerProvider {
  def builder: LoggerProviderBuilder
}
```

We always begin provider construction with this call:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val builder: LoggerProviderBuilder = LoggerProvider.builder
```

The builder is mutable internally but its mutations are not visible outside the builder: once `build()` is called the resulting `LoggerProvider` is immutable.

### `LoggerProviderBuilder.build` — Finalise configuration

`LoggerProviderBuilder.build` constructs the `LoggerProvider` from the accumulated builder state. If `setContextStorage` was not called, `build()` supplies `ContextStorage.defaultSpanContextStorage` automatically. The resulting provider is immutable.

```scala
final class LoggerProviderBuilder private[telemetry] (...) {
  def build(): LoggerProvider
}
```

We call `build()` once at the end of the chain after all configuration methods:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider: LoggerProvider = LoggerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "my-service")))
  .addLogRecordProcessor(new ConsoleLogRecordProcessor)
  .build()

provider.shutdown()
```

After calling `build()`, the builder instance should be discarded. Re-using or further mutating the builder after `build()` is not supported.

## Core Operations

Once a `LoggerProvider` is built, its API groups into three categories: obtaining `Logger` instances, configuring the builder before construction, and managing the provider's lifecycle.

### Logger Acquisition

The `get` method is the bridge between `LoggerProvider` configuration and the `Logger` instances that application code uses to emit records.

#### `get` — Obtain a named logger

`LoggerProvider#get` returns a `Logger` for the given instrumentation scope `name` and optional `version`. The returned `Logger` carries the provider's shared `Resource`, processors, and `ContextStorage`, scoped under an `InstrumentationScope` identified by the provided name and version. A new `Logger` instance is constructed on every call.

```scala
final class LoggerProvider private[telemetry] (...) {
  def get(name: String, version: String = ""): Logger
}
```

We call `LoggerProvider#get` once at module or library initialization and reuse the resulting `Logger`, since each call allocates a new instance:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .addLogRecordProcessor(new ConsoleLogRecordProcessor)
  .build()

// Acquire once — both name and version are recorded in every record's InstrumentationScope
val logger: Logger = provider.get("com.example.MyService", "1.0.0")

// Use the Logger to emit records at different severity levels
logger.info("server started")
logger.warn("high memory", "heapMb" -> AttributeValue.LongValue(1024L))

provider.shutdown()
```

When `version` is an empty string (the default), the `InstrumentationScope` records no version. Supply a semantic version string whenever the instrumentation scope is a versioned library component so that backends can correlate log records with library releases.

After obtaining a `Logger` from `LoggerProvider#get`, we can install it into the global `log` singleton by calling `log.install(logger)` so that macro-based call-site logging picks up the configured pipeline.

:::caution
`get` constructs a new `Logger` on every invocation. Cache the result at module initialization rather than calling `get` on the hot path.
:::

### Builder

The builder methods configure the three dimensions of a `LoggerProvider`: the `Resource` describing the service, the ordered list of `LogRecordProcessor`s that receive each emitted record, and the `ContextStorage` that carries the active `SpanContext` for trace-log correlation. We call these methods on the `LoggerProviderBuilder` returned by `LoggerProvider.builder`, before calling `build()`.

#### `setResource` — Identify the producing service

`LoggerProviderBuilder#setResource` sets the `Resource` that is attached to every log record this provider emits. A `Resource` wraps `Attributes` that describe the service, container, or host producing the telemetry. If not called, the builder uses `Resource.default`, which includes SDK identification attributes and `service.name = "unknown_service"`.

```scala
final class LoggerProviderBuilder private[telemetry] (...) {
  def setResource(resource: Resource): LoggerProviderBuilder
}
```

We build a `Resource` from a single `Attributes.of` call, using the predefined `Attributes.ServiceName` key:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "payments")))
  .addLogRecordProcessor(new ConsoleLogRecordProcessor)
  .build()

provider.shutdown()
```

Passing descriptive resource attributes is important for log aggregation backends, which use the `service.name` attribute to group records under the correct service.

#### `addLogRecordProcessor` — Wire processors and exporters

`LoggerProviderBuilder#addLogRecordProcessor` appends a `LogRecordProcessor` to the provider's ordered pipeline. Each processor's `onEmit` method is called sequentially on the thread that emits each record. Calling `addLogRecordProcessor` multiple times attaches multiple processors; they run in insertion order.

```scala
final class LoggerProviderBuilder private[telemetry] (...) {
  def addLogRecordProcessor(processor: LogRecordProcessor): LoggerProviderBuilder
}
```

The following example wires a console processor for human-readable local output alongside a second processor for exporting records to a remote backend:

```scala mdoc:compile-only
import zio.blocks.telemetry._

// Implement a minimal no-op processor as a stand-in for a real exporter
val exportingProcessor: LogRecordProcessor = new LogRecordProcessor {
  def onEmit(logRecord: LogRecord): Unit = ()
  def shutdown(): Unit                   = ()
  def forceFlush(): Unit                 = ()
}

val provider = LoggerProvider.builder
  .addLogRecordProcessor(new ConsoleLogRecordProcessor)
  .addLogRecordProcessor(exportingProcessor)
  .build()

provider.shutdown()
```

:::caution
Processors are called sequentially on the calling thread. A slow or blocking processor — for example, a synchronous HTTP exporter — will slow every log emission. Prefer batch processors that hand off to a background thread.
:::

#### `setContextStorage` — Enable trace-log correlation

`LoggerProviderBuilder#setContextStorage` overrides the `ContextStorage[Option[SpanContext]]` used to propagate the currently active span through the call stack. When not called, the builder resolves to `ContextStorage.defaultSpanContextStorage`, which on JVM is backed by a `ScopedValue`.

```scala
final class LoggerProviderBuilder private[telemetry] (...) {
  def setContextStorage(contextStorage: ContextStorage[Option[SpanContext]]): LoggerProviderBuilder
}
```

Pass the same `ContextStorage` instance to both `LoggerProvider` and `TracerProvider` to enable automatic trace-log correlation — log records emitted inside a `tracer.span` block will carry the active `SpanContext` without any additional instrumentation:

```scala mdoc:compile-only
import zio.blocks.telemetry._

// Create one shared storage instance and pass it to both providers
val sharedStorage: ContextStorage[Option[SpanContext]] =
  ContextStorage.create[Option[SpanContext]](None)

val loggerProvider = LoggerProvider.builder
  .setContextStorage(sharedStorage)
  .addLogRecordProcessor(new ConsoleLogRecordProcessor)
  .build()

val tracerProvider = TracerProvider.builder
  .setContextStorage(sharedStorage)
  .build()

loggerProvider.shutdown()
tracerProvider.shutdown()
```

:::note
Override `ContextStorage` only when sharing context explicitly with `TracerProvider`, or when integrating with a custom async runtime whose threading model makes `ScopedValue` propagation unreliable.
:::

### Lifecycle

The lifecycle operations release resources held by the registered `LogRecordProcessor`s. We call them once when the application is shutting down or when the provider is no longer needed.

#### `shutdown` — Release processor resources

`LoggerProvider#shutdown` calls `shutdown()` on every registered `LogRecordProcessor` in insertion order, releasing any background threads, buffers, or network connections they hold. After `shutdown()` returns, the provider is still structurally valid but processors will no longer emit data.

```scala
final class LoggerProvider private[telemetry] (...) {
  def shutdown(): Unit
}
```

We call `LoggerProvider#shutdown` once during application shutdown:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .addLogRecordProcessor(new ConsoleLogRecordProcessor)
  .build()

val logger = provider.get("com.example.App")
logger.info("application stopping")

provider.shutdown()
```

#### `close` — `AutoCloseable` delegation

`LoggerProvider#close` implements the `AutoCloseable` contract by delegating to `shutdown()`. This allows `LoggerProvider` to be used directly with `scala.util.Using.resource` or Java's try-with-resources construct.

```scala
final class LoggerProvider private[telemetry] (...) {
  override def close(): Unit
}
```

Using `scala.util.Using.resource` guarantees shutdown even when an exception propagates out of the block:

```scala mdoc:compile-only
import zio.blocks.telemetry._
import scala.util.Using

Using.resource(
  LoggerProvider.builder
    .addLogRecordProcessor(new ConsoleLogRecordProcessor)
    .build()
) { provider =>
  val logger = provider.get("com.example.App")
  logger.info("hello from managed provider")
}
// provider.close() — and therefore shutdown() — is called automatically here
```

## Comparisons

`LoggerProvider` compares to two reference points: SLF4J's static factory singleton on the Java side, and `TracerProvider` within the same module.

### `LoggerProvider` vs. SLF4J `LoggerFactory`

SLF4J's `LoggerFactory` is the most widely used Java logger-acquisition API. The table below contrasts it with `LoggerProvider` on the dimensions most relevant to production Scala applications:

| Aspect                       | SLF4J `LoggerFactory`                                        | ZIO Blocks `LoggerProvider`                                         |
|------------------------------|--------------------------------------------------------------|---------------------------------------------------------------------|
| Instantiation                | Static service-loader singleton; configured via `logback.xml` | Explicit value you construct and pass around                        |
| Configuration                | XML or Groovy at startup; limited programmatic control        | Fully programmatic; hot-reloadable via `log.install` / `log.writer` |
| Global convenience facade    | `LoggerFactory.getLogger(...)` everywhere                    | `log` singleton — install via `log.install(provider.get(...))`      |
| Trace-log correlation        | Requires MDC bridge + OTel agent or SDK integration          | Native — share `ContextStorage` with `TracerProvider`               |
| Structured attributes        | MDC (`String` → `String` only)                               | Typed attributes (`Long`, `Double`, `Boolean`, `String`) per record |
| `AutoCloseable`              | No; Logback has `stop()` on `LoggerContext`                  | Yes — `close()` delegates to `shutdown()`                           |
| Dependencies                 | SLF4J API + Logback Classic (~500 KB)                        | `zio-blocks-telemetry` only, zero transitive dependencies           |

The global `log` facade fills the same convenience role as `LoggerFactory.getLogger(...)` for code that should not depend on a specific logger instance. We install a provider-backed `Logger` into that facade with `log.install(provider.get("scope"))`, which makes it hot-swappable at runtime rather than locked to a static classloader binding.

### `LoggerProvider` vs. `TracerProvider`

`TracerProvider` and `LoggerProvider` are sibling provider types in the same `zio.blocks.telemetry` package, serving the tracing and logging pillars respectively. Both follow the identical builder pattern and `AutoCloseable` lifecycle. The key difference is that `TracerProvider` adds a `Sampler` configuration dimension that `LoggerProvider` does not need, while both share `ContextStorage` for cross-pillar correlation:

| Aspect                     | `LoggerProvider`                                     | `TracerProvider`                                       |
|----------------------------|------------------------------------------------------|--------------------------------------------------------|
| Produces                   | `Logger` → log records                               | `Tracer` → `Span`                                      |
| Builder method             | `LoggerProvider.builder`                             | `TracerProvider.builder`                               |
| Sampler support            | No                                                   | Yes — `setSampler`                                     |
| Processor type             | `LogRecordProcessor`                                 | `SpanProcessor`                                        |
| `ContextStorage` support   | Yes — `setContextStorage`                            | Yes — `setContextStorage`                              |
| `AutoCloseable`            | Yes                                                  | Yes                                                    |

Passing the same `ContextStorage[Option[SpanContext]]` instance to both providers is the recommended approach for trace-log correlation: log records emitted inside a `tracer.span` block automatically carry the active `SpanContext`, with no additional instrumentation required.