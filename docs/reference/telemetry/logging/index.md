---
id: index
title: "Logging"
description: "Logging index: the log entry point, LoggerProvider, Logger, LogRecord, and supporting types for structured logging in the telemetry module."
keywords:
  - "Structured Logging"
  - "Trace Correlation"
  - "Logging Overview"
  - "LoggerProvider"
sidebar_label: "Logging"
---

Logging covers structured, severity-leveled log emission. The `log` object is the entry point; behind it, [`LoggerProvider`](./logger-provider.md) is configured once at startup and produces [`Logger`](./logger.md) instances, and each `Logger` emits [`LogRecord`](./log-record.md) snapshots through an ordered pipeline of [`LogRecordProcessor`](./log-record-processor.md) instances.

`log` is the zero-setup entry point and the primary logging API. Its emission methods are macros that capture the call site's source file, enclosing class, method name, and line number at compile time, and they stamp the active span's trace and span IDs into every record automatically. Register a [`LogWriter`](./log-formatter.md) via `log.writer` for human-readable console output, or install a `Logger` / add a `LogRecordProcessor` via `log.install` / `log.addProcessor` for a full export pipeline.

Each emission method takes a message plus a varargs list of context values (`ctx: Any*`). Every argument is classified at compile time: a `(String, String)`, `(String, Long)`, `(String, Int)`, `(String, Double)`, or `(String, Boolean)` pair becomes a typed attribute; a `Throwable` attaches its type, message, and stack trace; a [`Severity`](./severity.md) overrides the record's level; an [`Attributes`](../shared/attributes.md) set is merged in; a bare `String` replaces the body; and any other type resolves through an implicit [`LogEnrichment[A]`](./log-enrichment.md) instance (define your own to log domain values directly).

## Emit structured, severity-leveled records

Six severities — `trace`, `debug`, `info`, `warn`, `error`, `fatal` — cover the whole scale.

```scala
object log {
  def trace(msg: String, ctx: Any*): Unit
  def debug(msg: String, ctx: Any*): Unit
  def info(msg: String, ctx: Any*): Unit
  def warn(msg: String, ctx: Any*): Unit
  def error(msg: String, ctx: Any*): Unit
  def fatal(msg: String, ctx: Any*): Unit
}
```

Pass typed key/value pairs for structured attributes, a `Throwable` to capture an exception, or an `Attributes` set to merge many values at once.

```scala mdoc:compile-only
import zio.blocks.telemetry._

log.info("order placed", "orderId" -> "ord-123", "amount" -> 99L, "express" -> true)
log.debug("cache lookup", "hit" -> false)

try throw new RuntimeException("payment declined")
catch { case e: Throwable => log.error("charge failed", "orderId" -> "ord-123", e) }
```

## Limit log volume at hot call sites

Two rate-limiting families, each spanning all six severities, keep high-frequency sites quiet.

```scala
object log {
  def <level>Every(every: Int, msg: String, ctx: Any*): Unit
  def <level>AtMost(intervalMillis: Long, msg: String, ctx: Any*): Unit
}
```

The `Every` family is count-based — `<level>Every(every, msg, ctx*)` emits on every Nth call at that site. The `AtMost` family is time-based — `<level>AtMost(intervalMillis, msg, ctx*)` emits at most once per interval at that site. Each call site tracks its own counter/clock independently.

```scala mdoc:compile-only
import zio.blocks.telemetry._

// Count-based: one line for every 100th retry
log.warnEvery(100, "retrying upstream call", "endpoint" -> "/inventory")

// Time-based: at most one line per 5 seconds, whatever the call rate
log.infoAtMost(5000L, "processing batch", "size" -> 512L)
log.errorAtMost(1000L, "connection pool exhausted")
```

## Attach scoped annotations

Attach key/value pairs to every record emitted inside a block with `annotated`.

```scala
object log {
  def annotated[A](annotations: (String, String)*)(f: => A): A
}
```

The pairs reach every record from the block, including calls in nested methods, without threading them through each `log.*` call.

```scala mdoc:compile-only
import zio.blocks.telemetry._

log.annotated("requestId" -> "req-42", "tenant" -> "acme") {
  log.info("started")   // both annotations attached
  log.info("finished")  // both annotations attached
}
```

## Correlate logs with the active span

When a `log.*` call runs inside a `trace.span`, the record is stamped with the enclosing span's trace and span IDs automatically, because logging and tracing share the same `ContextStorage`. No extra wiring is required.

```scala mdoc:compile-only
import zio.blocks.telemetry._

trace.span("checkout") { _ =>
  log.info("order validated", "orderId" -> "ord-123") // carries the checkout span's IDs
}
```

## Filter by severity

Drop records below a threshold, globally or per package prefix.

```scala
object log {
  def setMinSeverity(severity: Severity): Unit
  def setMinSeverity(prefix: String, severity: Severity): Unit
  def clearMinSeverity(prefix: String): Unit
  def clearAllOverrides(): Unit
  def withMinSeverity[A](severity: Severity)(f: => A): A
}
```

Set a global floor with `setMinSeverity(severity)`, or a per-package-prefix override with `setMinSeverity(prefix, severity)` (matched against the call site's namespace). Clear overrides with `clearMinSeverity(prefix)` or `clearAllOverrides()`, and lower the threshold for one block with `withMinSeverity`.

```scala mdoc:compile-only
import zio.blocks.telemetry._

log.setMinSeverity(Severity.Info)                     // drop trace/debug globally
log.setMinSeverity("com.example.noisy", Severity.Warn) // stricter for one package

log.withMinSeverity(Severity.Trace) {
  log.trace("visible only inside this block")
}
```

## Route output

Send records to a console writer for simple output, or to a processor pipeline for export.

```scala
object log {
  def writer(formatter: LogFormatter, logWriter: LogWriter): Unit
  def clearWriters(): Unit
  def install(logger: Logger, minSeverity: Severity = Severity.Trace): Unit
  def addProcessor(processor: LogRecordProcessor): Unit
  def removeAll(): Unit
}
```

For simple console output, `log.writer(formatter, writer)` adds a formatted sink; it is additive, so each call adds another output, and `log.clearWriters()` removes them. For a full pipeline, `log.install(logger)` replaces the backend with a configured `Logger`, `log.addProcessor(processor)` appends a `LogRecordProcessor`, and `log.removeAll()` detaches everything (calls become no-ops until an output is added again).

```scala mdoc:compile-only
import zio.blocks.telemetry._

// Human-readable text to stdout, JSON to stderr
log.writer(TextLogFormatter, StdoutWriter)
log.writer(JsonLogFormatter, StderrWriter)

// Or install a processor-based backend
val logger = LoggerProvider.builder
  .addLogRecordProcessor(new ConsoleLogRecordProcessor)
  .build()
  .get("com.example")
log.install(logger, Severity.Info)
```

## How They Work Together

The `log` object delegates to a `LoggerProvider`, which acts as a factory for `Logger` instances that emit `LogRecord` snapshots. Because the provider shares its `ContextStorage` with [`TracerProvider`](../tracing/tracer-provider.md), each record is stamped with the active span's identity — correlation is automatic.

```
  log (object)  ── macros capture file / class / method / line
     │  delegates to
     ▼
  LoggerProvider ──── Resource · LogRecordProcessor[] · ContextStorage
     │  get(scope)
     ▼
  Logger ──emit──▶ LogRecord ──▶ LogRecordProcessor.onEmit ──▶ writer / exporter
     ▲                              (LogFormatter + LogWriter render text / JSON)
     └── reads active SpanContext from shared ContextStorage (trace–log correlation)
```

**Type Relationships:**

- `log` wraps a `Logger`; its methods are macros that capture source location at compile time and add rate-limiting and `annotated` scopes.
- `LoggerProvider` holds the shared [`Resource`](../shared/resource.md) and `LogRecordProcessor` pipeline and creates `Logger` instances via `get(scope)`.
- `Logger` builds an immutable `LogRecord` per emission and dispatches it to every `LogRecordProcessor.onEmit`.
- A `LogRecordProcessor` renders output through a [`LogFormatter`](./log-formatter.md) + `LogWriter`, gates emission by `Severity`, or exports to an external system.
- Because `LoggerProvider` shares its `ContextStorage` with `TracerProvider`, each `LogRecord` is stamped with the active span's trace and span IDs.
- `Severity` classifies each record; `LogEnrichment` attaches the typed values passed at the call site.

## Usage

Logging's core job is to **emit structured, correlated logs**. Point `log` at a writer once, then emit records inside your spans; each record carries its typed key-value context and the active trace and span IDs automatically.

```scala mdoc:compile-only
import zio.blocks.telemetry._

trace.install(TracerProvider.builder.build())
log.writer(TextLogFormatter, StdoutWriter)

trace.span("checkout") { _ =>
  log.info("order placed", "orderId" -> "ord-123", "amount" -> 99L)
  log.warn("inventory low", "sku" -> "sku-42", "remaining" -> 3L)
}
```

## Type Pages

- **[LoggerProvider](./logger-provider.md)** — factory for `Logger` instances; holds `Resource`, the `LogRecordProcessor` pipeline, and `ContextStorage`. Build via `LoggerProvider.builder`.
- **[Logger](./logger.md)** — emits `LogRecord`s through configured processors; auto-correlates with the active span via `ContextStorage`.
- **[LogRecord](./log-record.md)** — immutable snapshot of a single log emission, carrying severity, body, typed attributes, and trace-correlation fields as unboxed primitives.
- **[LogRecordProcessor](./log-record-processor.md)** — hook for the log record lifecycle (`onEmit`); implement to export, filter, or format records.
- **[LogFormatter / LogWriter](./log-formatter.md)** — `LogFormatter` renders a `LogRecord`; `LogWriter` routes the output. Built-in: `TextLogFormatter`, `JsonLogFormatter`, `StdoutWriter`, `StderrWriter`.
- **[Severity](./severity.md)** — 24-level severity scale following the OpenTelemetry log data model, in six categories: Trace, Debug, Info, Warn, Error, Fatal.
- **[LogEnrichment](./log-enrichment.md)** — typeclass resolved at compile time by macro-generated `log.*` calls; attaches typed values to records.

## See Also

- [Telemetry Guide](../../../guides/telemetry-guide.md) — logging data flow, rate limiting, and production patterns
- [Telemetry Reference](../index.md) — module overview and all three pillars
- [Shared Vocabulary](../shared/index.md) — `Attributes`, `AttributeKey`, `Resource`, and `InstrumentationScope`
