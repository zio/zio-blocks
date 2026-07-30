---
id: logger
title: "Logger"
description: "Instance-level structured logger that routes log records through a LogRecordProcessor pipeline with automatic span correlation."
keywords:
  - "Structured Logging"
  - "Trace Correlation"
  - "Log Emission"
  - "Logger"
---

`Logger` is the instance-level structured log emitter in the logging area of the telemetry module. Each `Logger` is bound to one named `InstrumentationScope`, carries the `Resource` and `LogRecordProcessor` pipeline configured on its parent `LoggerProvider`, and automatically stamps every emitted `LogRecord` with the active `SpanContext` it reads from `ContextStorage`. Instances are obtained by calling `LoggerProvider#get`; the global `log` singleton delegates to one internally.

- **Instance-scoped** — bound to one `InstrumentationScope` (library name and optional version), so records carry a precise origin label.
- **Processor pipeline** — records flow through every registered `LogRecordProcessor` in insertion order; processor exceptions are caught and printed to stderr so later processors still receive the record.
- **Active-span correlation** — `ContextStorage` is consulted on every emit; the active `SpanContext`'s trace ID, span ID, and trace flags are injected into the `LogRecord` as unboxed primitive fields with zero allocation.
- **Severity gate** — each processor's `minimumLevel` is cached at construction; records below the lowest gate are dropped before any allocation occurs.
- **Thread-safe emit** — the emit path contains no shared mutable state.

The full public surface of `Logger` is:

```scala
final class Logger private[telemetry] (
  private[telemetry] val instrumentationScope: InstrumentationScope,
  private[telemetry] val resource: Resource,
  private[telemetry] val processors: Array[LogRecordProcessor],
  private[telemetry] val contextStorage: ContextStorage[Option[SpanContext]]
) {

  // Trace Correlation
  def currentSpanContext(): Option[SpanContext]

  // Severity-level logging — (String, AttributeValue)* attribute pairs
  def trace(body: String, attrs: (String, AttributeValue)*): Unit
  def debug(body: String, attrs: (String, AttributeValue)*): Unit
  def info(body: String, attrs: (String, AttributeValue)*): Unit
  def warn(body: String, attrs: (String, AttributeValue)*): Unit
  def error(body: String, attrs: (String, AttributeValue)*): Unit
  def fatal(body: String, attrs: (String, AttributeValue)*): Unit

  // Low-level pre-built record emit
  def emit(logRecord: LogRecord): Unit
}
```

## Usage

The following example shows the complete lifecycle — building a `LoggerProvider`, obtaining a `Logger`, and emitting records at several severity levels:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .addLogRecordProcessor(LogRecordProcessor.noop)
  .build()

val logger = provider.get("com.example.OrderService", "1.0.0")

logger.info("order placed", "orderId" -> AttributeValue.StringValue("ord-001"))
logger.warn("payment delayed", "durationMs" -> AttributeValue.LongValue(250L))
logger.error("checkout failed", "code" -> AttributeValue.LongValue(503L))

provider.shutdown()
```

## Creating Values

A `Logger` cannot be constructed directly — its constructor is package-private. We obtain one from a `LoggerProvider`, which itself is built through `LoggerProvider.builder`.

### Obtaining a Logger from a provider

`LoggerProvider#get` produces a `Logger` bound to a named instrumentation scope. Pass a scope name — typically a fully qualified package or library identifier — and an optional version string. The returned instance inherits the provider's `Resource`, processor list, and `ContextStorage`:

```scala
final class LoggerProvider private[telemetry] (...) {
  def get(name: String, version: String = ""): Logger
}
```

We retrieve both a versioned logger for a payment component and an unversioned one for order processing from the same provider:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .addLogRecordProcessor(LogRecordProcessor.noop)
  .build()

val orderLogger   = provider.get("com.example.OrderService")
val paymentLogger = provider.get("com.example.PaymentService", "2.1.0")

orderLogger.info("order received")
paymentLogger.warn("payment gateway slow")

provider.shutdown()
```

:::note
`LoggerProvider#get` allocates a new `Logger` on every call. Assign the result to a `val` at startup and reuse it throughout the component's lifetime rather than calling `get` per request.
:::

### Building the provider

`LoggerProvider.builder` is the sole public entry point for creating the `LoggerProvider` that vends `Logger` instances. It returns a `LoggerProviderBuilder` pre-populated with `Resource.default`, an empty processor list, and no explicit `ContextStorage`. Call configuration methods in any order and finish with `build()`:

```scala
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

The following example assembles a fully specified provider for an `"inventory-service"` and installs it into the global `log` singleton so that call sites need no provider reference:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "inventory-service")))
  .addLogRecordProcessor(LogRecordProcessor.noop)
  .build()

log.install(provider.get("com.example.inventory"))

log.info("server started")

provider.shutdown()
```

For a complete walkthrough of every `LoggerProviderBuilder` option — including `setContextStorage` for explicit trace–log correlation — see the [LoggerProvider](./logger-provider.md) reference page.

## Core Operations

`Logger` exposes two groups of operations: the severity-leveled emit methods that cover the common logging path, and a lower-level `emit` that accepts a fully constructed `LogRecord`. A third category provides access to the active `SpanContext`.

### Logging

The logging methods cover both the common severity-leveled path and the rare case where a caller builds a `LogRecord` by hand.

#### Emitting at a fixed severity

`Logger#trace`, `Logger#debug`, `Logger#info`, `Logger#warn`, `Logger#error`, and `Logger#fatal` each emit a log record at a specific severity level. Every method accepts a message body string and an optional variadic sequence of `(String, AttributeValue)` pairs that attach typed structured metadata to the record. The active `SpanContext` is read from `ContextStorage` and injected automatically:

```scala
final class Logger private[telemetry] (...) {
  def trace(body: String, attrs: (String, AttributeValue)*): Unit  // Severity 1
  def debug(body: String, attrs: (String, AttributeValue)*): Unit  // Severity 5
  def info(body: String, attrs: (String, AttributeValue)*): Unit   // Severity 9
  def warn(body: String, attrs: (String, AttributeValue)*): Unit   // Severity 13
  def error(body: String, attrs: (String, AttributeValue)*): Unit  // Severity 17
  def fatal(body: String, attrs: (String, AttributeValue)*): Unit  // Severity 21
}
```

The following example shows each method alongside representative `AttributeValue` variants:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .addLogRecordProcessor(LogRecordProcessor.noop)
  .build()

val logger = provider.get("com.example.PaymentService")

logger.trace("entering loop", "iter" -> AttributeValue.LongValue(0L))
logger.debug("cache miss", "key" -> AttributeValue.StringValue("abc"))
logger.info(
  "request processed",
  "userId" -> AttributeValue.StringValue("u42"),
  "ms"     -> AttributeValue.LongValue(37L)
)
logger.warn("slow query", "durationMs" -> AttributeValue.LongValue(500L))
logger.error("payment failed", "code" -> AttributeValue.LongValue(503L))
logger.fatal("unrecoverable state")

provider.shutdown()
```

`AttributeValue` supports eight variants covering all OpenTelemetry primitive and array types: `StringValue`, `BooleanValue`, `LongValue`, `DoubleValue`, `StringSeqValue`, `LongSeqValue`, `DoubleSeqValue`, and `BooleanSeqValue`. Each named attribute key must be a `String`.

:::caution
These six methods follow the full `LogRecord` allocation path: they capture a nanosecond timestamp, read `ContextStorage`, build an `Attributes` map, and construct a `LogRecord` before passing it to the processor pipeline. The global `log` singleton's macro-generated equivalents additionally inject compile-time source location and can take a fast formatted path that skips `LogRecord` construction entirely when the sole processor is a console writer. For maximum throughput in hot paths, prefer `log` over a bare `Logger`.
:::

#### Emitting a pre-built record

`Logger#emit` accepts a fully constructed `LogRecord` and routes it through the processor pipeline directly. Records whose severity number is below every processor's `minimumLevel` are dropped immediately. Otherwise each processor's `onEmit` callback is called in insertion order; a `NonFatal` exception from any processor is caught, written to stderr, and processing continues with the next processor:

```scala
final class Logger private[telemetry] (...) {
  def emit(logRecord: LogRecord): Unit
}
```

We build a `LogRecord` with `LogRecord.builder`, set its severity and body, and emit it:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .addLogRecordProcessor(LogRecordProcessor.noop)
  .build()

val logger = provider.get("com.example.AuditService")

val record = LogRecord.builder
  .setSeverity(Severity.Warn)
  .setBody("audit event: config reloaded")
  .build

logger.emit(record)

provider.shutdown()
```

:::caution
`Logger#emit` bypasses the automatic `SpanContext` injection and the fast-path `FormattedLogEmitter` that the severity-level methods use. Use it only when the record has been pre-populated with all required fields, such as when bridging from another logging framework or replaying records from a buffer.
:::

### Trace Correlation

`Logger#currentSpanContext` returns the `SpanContext` currently stored in the logger's `ContextStorage`, or `None` if no span is active. The same value is injected automatically into every record emitted through the severity-level methods, but this accessor is useful when code needs to read the active trace and span identifiers explicitly — for instance to attach them to an outgoing HTTP header or to log them alongside a manual record:

```scala
final class Logger private[telemetry] (...) {
  def currentSpanContext(): Option[SpanContext]
}
```

The following example reads the active span context before emitting a record:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .addLogRecordProcessor(LogRecordProcessor.noop)
  .build()

val logger = provider.get("com.example.TraceAwareService")

val ctx: Option[SpanContext] = logger.currentSpanContext()
logger.info("span context present", "active" -> AttributeValue.BooleanValue(ctx.isDefined))

provider.shutdown()
```

When a `TracerProvider` and a `LoggerProvider` share the same `ContextStorage[Option[SpanContext]]` instance, every log record emitted while a span is active is automatically stamped with the span's `traceIdHi`, `traceIdLo`, `spanId`, and `traceFlags` fields. Both providers default to `ContextStorage.defaultSpanContextStorage`, so correlation is active with no explicit setup. Pass a shared instance via `LoggerProviderBuilder#setContextStorage` only when isolating correlation in tests or integrating with a custom runtime.

## Comparisons

### Logger vs SLF4J Logger (Java)

Both types represent a named logger, but their designs diverge sharply.

| Dimension               | ZIO-Blocks `Logger`                                                                                 | SLF4J `Logger`                                                                          |
|-------------------------|-----------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| **Acquisition**         | Obtained from `LoggerProvider#get`; provider carries the full configuration.                       | Obtained from the static `LoggerFactory`; backend is classpath-global.                  |
| **Configuration scope** | Each `Logger` carries its own processor pipeline, `Resource`, and `ContextStorage` at construction. | `Logger` holds no configuration; all routing is delegated to the classpath-bound backend.|
| **Trace correlation**   | `SpanContext` is injected automatically on every emit via `ContextStorage`.                         | No native OpenTelemetry correlation; requires a separate MDC bridge library.            |
| **Testability**         | Injectable: pass a `Logger` constructed with a test processor to verify emitted records in isolation.| Backend is global; tests must configure a shared appender or use a test-specific factory.|
| **Attribute types**     | Typed `AttributeValue` variants (String, Long, Double, Boolean, and array forms).                  | Untyped string key-value pairs via MDC; structured backends vary.                       |

### Logger vs `log` (global singleton)

`log` is the recommended API for application code; `Logger` is what `log` delegates to internally.

| Dimension                | `Logger` instance method                                          | `log` global singleton                                                         |
|--------------------------|-------------------------------------------------------------------|--------------------------------------------------------------------------------|
| **Source location**      | Not captured — the method call site is not recorded.              | Captured at compile time by macro-generated code; available to formatters.     |
| **API surface**          | Plain Scala methods; identical across Scala 2 and 3.              | Macro-generated: `inline def` on Scala 3, `def ... = macro` on Scala 2.       |
| **Rate limiting**        | Not available.                                                    | Per-call-site rate-limited variants are available.                             |
| **Scoped annotations**   | Not available.                                                    | `log.annotated(...)` attaches key/value pairs for the duration of a block.    |
| **Recommended for**      | Library authors who accept a `Logger` parameter for isolation.    | Application code; single global reference, no provider wiring required.       |

When writing a reusable library component that must be independently testable, accept a `Logger` parameter and call its methods directly. Application code that does not need that isolation level should use `log` instead and call `log.install(provider.get("com.example"))` once at startup to wire a production-configured `Logger` into the global singleton.
