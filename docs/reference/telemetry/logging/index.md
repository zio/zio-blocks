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

Logging records what your application did as it runs, so you can understand its behavior and diagnose problems after the fact. Unlike a plain `println`, these logs are **structured** — each entry carries typed key/value fields (an order id, a duration) you can search and filter on, not just a line of text — and **severity-leveled**, tagged `trace`, `debug`, `info`, `warn`, `error`, or `fatal` so you can keep production quiet and turn up detail only when debugging. Each log is also tied back to the trace that produced it, so from one log line you can pull up the whole request it belongs to. 

You write logs through the `log` object: call `log.info("order placed")` (or `debug`, `warn`, `error`, …) anywhere in your code and it works immediately, with no setup. Behind each call, `log` automatically records where the log came from — the file, class, method, and line — and stamps it with the trace and span of whatever [`trace.span`](../tracing/index.md) you happen to be inside. That stamping is what lets logs and traces line up later: no need to thread a request id through your code by hand.

By default those records just accumulate in memory; to actually see them you point `log` at a destination once, at application startup. For local development, `log.writer(...)` prints human-readable (or JSON) lines to the console through a [`LogWriter`](./log-formatter.md). For production, `log.install(...)` or `log.addProcessor(...)` wires up a full pipeline that ships records to a backend for storage and search. You do this once; every `log.*` call in the app then flows through it.

After the message you attach context, and this is where structured logging pays off. The common case is key/value pairs — `log.info("order placed", "orderId" -> "ord-123", "amount" -> 99L)` — which become searchable fields on that entry instead of being buried in the text, so later you can query "all logs where `orderId = ord-123`". Pass a `Throwable` and its type, message, and stack trace are captured for you. A few values are handled specially: a [`Severity`](./severity.md) overrides the entry's level, an [`Attributes`](../shared/attributes.md) set adds many fields at once, and a plain `String` replaces the message body. When you have a domain type you'd like to log directly, give it a [`LogEnrichment`](./log-enrichment.md) instance that tells `log` how to turn it into fields.

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

## See Also

- [Telemetry Guide](../../../guides/telemetry-guide.md) — logging data flow, rate limiting, and production patterns
- [Telemetry Reference](../index.md) — module overview and all three pillars
- [Shared Vocabulary](../shared/index.md) — `Attributes`, `AttributeKey`, `Resource`, and `InstrumentationScope`
