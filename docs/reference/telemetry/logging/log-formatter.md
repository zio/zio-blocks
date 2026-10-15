---
id: log-formatter
title: "LogFormatter"
description: "How a LogRecord is rendered to text: the built-in human-readable and OTLP-JSON formatters behind log.writer."
keywords:
  - "Structured Logging"
  - "Log Formatting"
  - "Text and JSON Output"
  - "LogFormatter"
sidebar_label: "LogFormatter"
---

`LogFormatter` renders a [`LogRecord`](./log-record.md) into text; its partner, the [`LogWriter`](./log-writer.md), routes that text to a destination. You rarely name a formatter directly — you hand a formatter/writer pair to [`log.writer`](./index.md), and the two built-ins below cover the common cases. Implement the trait yourself when your pipeline expects a shape neither produces.

## Built-in Formatters

Two singletons cover the usual output formats — both safe to share across threads, with `TextLogFormatter` caching its timestamp prefix for the current second:

| Formatter          | Output                                                                                                                                                      |
|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `TextLogFormatter` | Human-readable — `2026-07-29T10:00:00.000Z INFO  message [Svc.doWork] src/main/scala/com/example/Svc.scala:42 {key=val}`                                      |
| `JsonLogFormatter` | OTLP-compatible JSON — `{"timeUnixNano":"...","severityNumber":9,"severityText":"INFO","body":{"stringValue":"message"},"attributes":[...]}`                |

Both render the record's timestamp, severity, source location (from the `code.*` attributes), message body, and an attached throwable's stack trace. They differ in what else they carry: `TextLogFormatter` prints the body first, then the compact `[Svc.doWork]` function separately, then the contiguous captured `path:line` token for navigation, then the remaining attributes as a compact `{key=value}` list — and drops the trace and span IDs — while `JsonLogFormatter` emits every attribute — including the three `code.*` entries — plus `traceId` and `spanId` when the record was written inside a span.

The text layout used to print the location before the body (`INFO  [Svc.doWork:42] message {key=val}`). It now prints the message first: `INFO  message [Svc.doWork] src/main/scala/com/example/Svc.scala:42 {key=val}`. When no file path was captured, the compact `[Svc.doWork:42]` fallback stays informational; a bare line renders as `[line:42]`; with no location at all nothing renders. The captured path is preserved exactly as the macro emitted it at compile time: it is never absolutized against the runtime working directory, never shortened — backslashes stay literal, so a Windows `C:\work\...` path renders as-is — and the method never appears inside the `path:line` token. Only CR, LF, tab, and other control characters in location text are backslash-escaped, so a hostile path cannot forge extra log lines.

That compile-time path may not exist where you read the log. It is the source file path as seen by the compiler that built the code — for a library published from another checkout, for CI artifacts, or for logs emitted inside a container image without sources, the file may live elsewhere or not at all. Treat `path:line` as an informational pointer, not a guarantee the file opens there.

## Example Usage

Choosing a formatter is choosing who reads your logs. In development that's you, so pick `TextLogFormatter` — one aligned line per record, easy to scan in a terminal. In production it's a log collector, so pick `JsonLogFormatter` — every attribute stays a separate field the collector can index and query. Select it once at startup, alongside a [`LogWriter`](./log-writer.md) for the destination:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val isDevelopment = sys.env.get("ENV").forall(_ != "production")

if (isDevelopment) log.writer(TextLogFormatter, StdoutWriter)  // readable in a terminal
else log.writer(JsonLogFormatter, StdoutWriter)                // queryable by a collector

log.info("server ready", "port" -> 8080L)
```

You can also register both — `log.writer` is additive, so each call adds another channel and every record goes to all of them. That's how you keep readable console output while also emitting machine-readable JSON:

```scala mdoc:compile-only
import zio.blocks.telemetry._

log.writer(TextLogFormatter, StdoutWriter)  // for the developer watching the terminal
log.writer(JsonLogFormatter, StderrWriter)  // for the collector tailing stderr

log.info("server ready", "port" -> 8080L)   // written to both channels
```

## Custom Formatter

Sometimes neither built-in shape fits — an existing ingest pipeline expects logfmt, or a report wants CSV. Implement the trait yourself and pass your formatter to `log.writer` like any built-in. Two methods are abstract, but only one of them is yours to write:

```scala
trait LogFormatter {
  def format(
    sb: StringBuilder, timestampNanos: Long, severity: Severity, severityText: String,
    body: String, builder: Attributes.AttributesBuilder,
    traceIdHi: Long, traceIdLo: Long, spanId: Long, traceFlags: Byte,
    throwable: Option[Throwable]
  ): Unit

  def formatRecord(sb: StringBuilder, record: LogRecord): Unit
}
```

`formatRecord` is the one that runs: a channel registered with `log.writer` renders each finished [`LogRecord`](./log-record.md) through it. Put your format there.

`format` serves a lower-allocation path that skips building a `LogRecord`, and the library uses it only for its own default console output — no formatter you register ever reaches it. You still have to define it to satisfy the trait, so mirror the scalar fields and move on; it cannot render attributes anyway, since the accessors for reading them off the builder are internal.

Both methods receive a fresh, empty `StringBuilder` from the caller, which writes it out and discards it. Append your output and return — the caller handles writing.

A CSV formatter — three comma-separated fields in `formatRecord`, with `format` mirroring them:

```scala mdoc:compile-only
import zio.blocks.telemetry._

object CsvFormatter extends LogFormatter {
  def formatRecord(sb: StringBuilder, record: LogRecord): Unit =
    sb.append(record.timestampNanos).append(',').append(record.severityText).append(',').append(record.body.value)

  def format(
    sb: StringBuilder, timestampNanos: Long, severity: Severity, severityText: String,
    body: String, builder: Attributes.AttributesBuilder,
    traceIdHi: Long, traceIdLo: Long, spanId: Long, traceFlags: Byte,
    throwable: Option[Throwable]
  ): Unit = sb.append(timestampNanos).append(',').append(severityText).append(',').append(body)
}

log.writer(CsvFormatter, StdoutWriter)
log.info("order placed")
// 2026-07-31T13:23:25.137Z INFO  order placed [Main$.main] src/main/scala/example/Main.scala:20  <- default console output
// 1785504205137002324,INFO,order placed                                                                          <- your channel
```

Two lines, because `log.writer` adds a channel rather than replacing one, and the default console output is still registered.

To render attributes too, pass an `AttributeVisitor` to `record.attributes.accept` — it fires once per attribute with the raw unboxed value, so nothing is boxed on the way out. Only the four scalar methods are abstract; the seq variants default to no-ops. Source location arrives among those attributes as `code.file.path`, `code.function.name` (fully qualified), and `code.line.number`. Decide which convention you want: `TextLogFormatter` lifts them into its `[Svc.doWork] path:line` location (falling back to `[Svc.doWork:42]` when no path was captured, `[line:42]` for a bare line, and nothing at all when there is no location) and then skips those keys, while `JsonLogFormatter` emits them as ordinary attributes. If you render the location separately, skip exactly those three canonical keys — any other `code.*` key is an ordinary user attribute and must stay visible.
