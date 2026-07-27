---
id: logger
title: "Logger"
description: "Scope-bound structured logger that routes records through a LogRecordProcessor pipeline with active-span correlation."
keywords:
  - "Instance-Scoped Logger"
  - "LogRecordProcessor Pipeline"
  - "Structured Log Emission"
  - "Active Span Correlation"
  - "LoggerProvider Factory"
  - "Severity-Level Logging"
  - "Trace Context Injection"
---

`Logger` is an instance-scoped structured logger that routes every log record through an ordered chain of `LogRecordProcessor` instances and automatically correlates records with the active `SpanContext` drawn from a `ContextStorage`. Its constructor is package-private; instances are obtained via `LoggerProvider#get`. The global [`log`](./index.md) singleton delegates to an internal `Logger` — library code that needs isolation from the global backend should accept a `Logger` as a constructor parameter instead.

- **Instance-scoped** — each `Logger` is bound to one `InstrumentationScope` (name + optional version), so `instrumentationScope` appears on every record it emits.
- **Processor pipeline** — records flow through every `LogRecordProcessor` in the order they were registered. Exceptions from individual processors are caught so that later processors always receive the record.
- **Active-span correlation** — `currentSpanContext` reads the `ContextStorage` at emit time and injects `traceId`, `spanId`, and `traceFlags` into every record when a span is active.
- **Fast path** — when the sole registered processor is a `ConsoleLogRecordProcessor`, `Logger` constructs a `FormattedLogEmitter` backed by `TextLogFormatter` and `StdoutWriter`, bypassing the general `StandardLogEmitter` path.
- **Thread-safe emit** — processor iteration uses a fixed array with per-processor `try/catch NonFatal`; no locks are required.

The full public surface, alongside its provider and builder, is:

```scala
final class Logger private[telemetry] (
  private[telemetry] val instrumentationScope: InstrumentationScope,
  private[telemetry] val resource: Resource,
  private[telemetry] val processors: Array[LogRecordProcessor],
  private[telemetry] val contextStorage: ContextStorage[Option[SpanContext]]
) {
  // Trace correlation
  def currentSpanContext(): Option[SpanContext]

  // Structured logging — each method targets one Severity category
  def trace(body: String, attrs: (String, AttributeValue)*): Unit
  def debug(body: String, attrs: (String, AttributeValue)*): Unit
  def info(body: String, attrs: (String, AttributeValue)*): Unit
  def warn(body: String, attrs: (String, AttributeValue)*): Unit
  def error(body: String, attrs: (String, AttributeValue)*): Unit
  def fatal(body: String, attrs: (String, AttributeValue)*): Unit

  // Low-level emit — passes a pre-built LogRecord to all processors
  def emit(logRecord: LogRecord): Unit
}

object LoggerProvider {
  def builder: LoggerProviderBuilder
}

final class LoggerProvider private[telemetry] (
  resource: Resource,
  private val processors: Array[LogRecordProcessor],
  contextStorage: ContextStorage[Option[SpanContext]]
) extends AutoCloseable {
  def get(name: String, version: String = ""): Logger
  def shutdown(): Unit
  override def close(): Unit
}

final class LoggerProviderBuilder private[telemetry] (...) {
  def setResource(resource: Resource): LoggerProviderBuilder
  def addLogRecordProcessor(processor: LogRecordProcessor): LoggerProviderBuilder
  def setContextStorage(contextStorage: ContextStorage[Option[SpanContext]]): LoggerProviderBuilder
  def build(): LoggerProvider
}
```

## Usage

The following example configures a `LoggerProvider` with a console processor, obtains a scoped `Logger`, and emits records at several severity levels with typed attributes:

```scala mdoc:compile-only
import zio.blocks.telemetry._

// Build a provider backed by a console processor
val provider: LoggerProvider = LoggerProvider.builder
  .addLogRecordProcessor(new ConsoleLogRecordProcessor())
  .build()

// Obtain a Logger scoped to "payment-service" version "1.0"
val logger: Logger = provider.get("payment-service", "1.0")

// Emit at different severity levels with key/value attributes
logger.info("request received",
  "userId"  -> AttributeValue.StringValue("u42"),
  "orderId" -> AttributeValue.LongValue(7001L))

logger.warn("slow downstream",
  "serviceMs" -> AttributeValue.LongValue(820L))

logger.error("payment declined",
  "code" -> AttributeValue.LongValue(402L))

// Inspect active span context (None if no span is active)
val ctx: Option[SpanContext] = logger.currentSpanContext()
```

## Construction / Creating Instances

`Logger` instances are always obtained through a `LoggerProvider`. The provider binds the processor pipeline and context storage; each call to `LoggerProvider#get` produces a new `Logger` scoped to a distinct `InstrumentationScope`.

### `LoggerProvider.get` — Obtain a Logger by instrumentation scope

`LoggerProvider#get` creates a `Logger` whose `InstrumentationScope` carries the given name and optional version. All records emitted by the returned logger carry this scope as metadata, which allows log processors and exporters to distinguish records from different libraries or components in the same process.

```scala
final class LoggerProvider private[telemetry] (...) extends AutoCloseable {
  def get(name: String, version: String = ""): Logger
}
```

We call `LoggerProvider#get` once per instrumentation scope at startup, typically storing the result as a constructor parameter or a module-level value:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider: LoggerProvider = LoggerProvider.builder.build()

// Scope to a library name; version is optional
val logger: Logger = provider.get("com.example.payments")

// With an explicit version string
val versionedLogger: Logger = provider.get("com.example.auth", "2.3.1")
```

### `LoggerProvider.builder` — Build a custom provider

`LoggerProvider.builder` is the sole public entry point for constructing a `LoggerProvider`. It returns a `LoggerProviderBuilder` whose fluent API lets us attach processors, override the resource, and supply a custom `ContextStorage` before calling `build()`.

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

The builder produces a `LoggerProvider` configured with a `ConsoleLogRecordProcessor` that writes human-readable text to stdout, combined with a custom capturing processor for tests:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val records = scala.collection.mutable.ArrayBuffer.empty[LogRecord]

val capturingProcessor: LogRecordProcessor = new LogRecordProcessor {
  def onEmit(record: LogRecord): Unit = records += record
  def shutdown(): Unit                = ()
  def forceFlush(): Unit              = ()
  override def minimumLevel: Int      = 1
}

val provider: LoggerProvider = LoggerProvider.builder
  .setResource(Resource.default)
  .addLogRecordProcessor(new ConsoleLogRecordProcessor())
  .addLogRecordProcessor(capturingProcessor)
  .build()
```

:::note
Calling `build()` with no processors configured results in a `Logger` that silently discards all records — no output is produced. Add at least one processor before calling `build()`.
:::

## Core Operations

### Logging

The logging category contains seven methods: the six severity-level methods (`trace`, `debug`, `info`, `warn`, `error`, `fatal`) that each accept a body string and variadic key/value attribute pairs, and `emit` for submitting a fully pre-built `LogRecord`.

#### `trace` / `debug` / `info` / `warn` / `error` / `fatal` — Emit a record at a named severity

Each method builds an `AttributesBuilder` from the supplied key/value pairs, reads the active `SpanContext` from `ContextStorage`, constructs a `LogRecord` with a nanosecond-precision timestamp, and forwards the record to `Logger#emit`. The severity numbers follow the OpenTelemetry log data model:

| Method  | `Severity`       | Number |
|---------|------------------|--------|
| `trace` | `Severity.Trace` |      1 |
| `debug` | `Severity.Debug` |      5 |
| `info`  | `Severity.Info`  |      9 |
| `warn`  | `Severity.Warn`  |     13 |
| `error` | `Severity.Error` |     17 |
| `fatal` | `Severity.Fatal` |     21 |

All six share the same signature shape:

```scala
final class Logger private[telemetry] (...) {
  def trace(body: String, attrs: (String, AttributeValue)*): Unit
  def debug(body: String, attrs: (String, AttributeValue)*): Unit
  def info(body: String, attrs: (String, AttributeValue)*): Unit
  def warn(body: String, attrs: (String, AttributeValue)*): Unit
  def error(body: String, attrs: (String, AttributeValue)*): Unit
  def fatal(body: String, attrs: (String, AttributeValue)*): Unit
}
```

The attribute values use `AttributeValue`, a sealed ADT whose variants cover strings, booleans, longs, doubles, and sequences of each. The following example shows logging at each level with representative attributes:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val logger: Logger = LoggerProvider.builder.build().get("example")

logger.trace("entering loop",  "iter"       -> AttributeValue.LongValue(0L))
logger.debug("cache miss",     "key"        -> AttributeValue.StringValue("products:featured"))
logger.info("user signed in",  "userId"     -> AttributeValue.StringValue("u42"),
                                "region"    -> AttributeValue.StringValue("eu-west"))
logger.warn("slow query",      "durationMs" -> AttributeValue.LongValue(620L))
logger.error("payment failed", "code"       -> AttributeValue.LongValue(402L))
logger.fatal("disk full",      "freeBytes"  -> AttributeValue.LongValue(0L))
```

:::caution
Unlike the global [`log`](./index.md) macros, these instance methods do not inject compile-time source location. No `code.filepath`, `code.namespace`, or `code.lineno` attributes are added to the record. If source location is important, use the global `log` at the application layer and reserve `Logger` for library internals.
:::

#### `emit` — Emit a pre-built LogRecord

`Logger#emit` accepts a fully constructed `LogRecord` and forwards it directly to every registered `LogRecordProcessor`. Per-processor exceptions are caught via `NonFatal`, ensuring that a misbehaving processor cannot prevent later processors from receiving the record. Records whose `severity.number` is below the computed minimum across all processors are discarded before iteration begins.

```scala
final class Logger private[telemetry] (...) {
  def emit(logRecord: LogRecord): Unit
}
```

We use `Logger#emit` when we need full control over all `LogRecord` fields — for example, when replaying records from a buffer with their original timestamps, or when a `LogRecord` arrives from an external source:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val logger: Logger = LoggerProvider.builder.build().get("replay-agent")

val record: LogRecord = LogRecord.builder
  .setSeverity(Severity.Warn)
  .setBody("upstream degraded")
  .setAttribute(AttributeKey.string("upstream"), "payment-gateway")
  .build

logger.emit(record)
```

:::caution
`Logger#emit` bypasses the automatic `SpanContext` injection that the severity-level methods perform. If you want trace correlation on a manually built record, populate the trace fields on the `LogRecordBuilder` explicitly before calling `emit`.
:::

### Trace Correlation

The trace correlation category exposes the active span context that `Logger` automatically injects into every record emitted by the severity-level methods.

#### `currentSpanContext` — Read the active span context

`Logger#currentSpanContext` delegates to the `ContextStorage[Option[SpanContext]]` the logger was constructed with and returns the currently active `SpanContext`, or `None` when no span is active. This is the same storage read that the severity-level methods use internally to populate `traceId`, `spanId`, and `traceFlags` on each `LogRecord`.

```scala
final class Logger private[telemetry] (...) {
  def currentSpanContext(): Option[SpanContext]
}
```

We call `Logger#currentSpanContext` to inspect trace correlation before emitting a record conditionally, or to extract trace IDs for manual propagation:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val logger: Logger = LoggerProvider.builder.build().get("diagnostics")

val ctx: Option[SpanContext] = logger.currentSpanContext()
ctx match {
  case Some(spanCtx) if spanCtx.isValid =>
    logger.info("in-trace diagnostic", "traceId" -> AttributeValue.StringValue(spanCtx.traceIdHex))
  case _ =>
    logger.info("out-of-trace diagnostic")
}
```

## Comparison

### vs `log` (global singleton)

[`log`](./index.md) and `Logger` occupy different layers of the telemetry module. `log` is the global application-layer entry point; `Logger` is the instance-level engine that `log` delegates to. The distinction governs when you reach for each:

| Dimension                 | `log` (global singleton)                                            | `Logger` (instance)                                             |
|---------------------------|---------------------------------------------------------------------|-----------------------------------------------------------------|
| Access                    | Import and call directly — no injection needed                      | Constructor parameter or `LoggerProvider#get` call              |
| Source location           | Injected at compile time via macro (`code.filepath`, `code.lineno`) | Not injected — no macro expansion                               |
| Severity overrides        | Hierarchical per-prefix overrides; `withMinSeverity` block scope    | Processor `minimumLevel` only; no prefix hierarchy              |
| Rate limiting             | `traceEvery` / `traceAtMost` family per call site                  | No built-in rate limiting                                       |
| Scoped annotations        | `log.annotated` — thread-local key/value block                     | Not available on `Logger` directly                              |
| Backend replacement       | `log.install(logger, ...)` — hot-swappable at runtime              | Fixed at construction by `LoggerProvider#get`                   |
| Primary user              | Application code, frameworks, integration layers                    | Library code; inject for isolation and testability              |

Library code should declare a `Logger` parameter rather than calling `log` directly — this keeps the library free of global state and allows callers to substitute a no-op or capturing implementation in tests. Application code should use `log`, which provides the richer feature set and automatically delegates to the installed `Logger`.

### vs SLF4J Logger (Java)

SLF4J's `Logger` is the dominant Java logging abstraction. The table contrasts it with the ZIO Blocks `Logger` on the dimensions most relevant to Scala library authors:

| Dimension              | SLF4J `Logger`                                                           | ZIO Blocks `Logger`                                                         |
|------------------------|--------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| Acquisition            | `LoggerFactory.getLogger(Class<?>)` — static, classpath-global           | `LoggerProvider#get(name, version)` — explicit, injectable                  |
| Backend coupling       | Classpath binding at startup; single global backend                       | Per-instance processor pipeline; multiple independent `Logger` instances     |
| Structured data        | Fluent API (`atInfo().addKeyValue(...)`) since SLF4J 2.x                 | Variadic `(String, AttributeValue)` pairs on every severity method          |
| Trace correlation      | MDC (`String` → `String`) must be populated manually                     | Automatic from `ContextStorage[Option[SpanContext]]` at emit time            |
| Testability            | `LogbackTestAppender` or SLF4J `ListAppender`; classpath dependency       | Implement `LogRecordProcessor`; supply to `LoggerProvider.builder`          |
| Severity granularity   | 5 levels: `TRACE` / `DEBUG` / `INFO` / `WARN` / `ERROR`                 | 24 levels in 6 categories (e.g., `Info`, `Info2`, `Info3`, `Info4`)         |
| OTLP compatibility     | Requires a bridge handler                                                 | Native `LogRecord` matches the OTLP log data model                          |

The key practical difference is backend coupling. SLF4J binds to exactly one backend per classloader; swapping it in tests requires classloader manipulation or a special test implementation. A ZIO Blocks `Logger` is an ordinary value carrying its own processor list — tests create a `LoggerProvider` with a capturing processor, pass the resulting `Logger` to the unit under test, and inspect the collected `LogRecord` values directly, with no classloader tricks required.