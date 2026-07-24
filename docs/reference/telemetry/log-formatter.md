---
id: log-formatter
title: "LogFormatter"
description: "Stateless log formatter trait in the ZIO Blocks Telemetry logging pillar — two built-ins: TextLogFormatter for human-readable output and JsonLogFormatter for OTLP JSON."
keywords:
  - "LogFormatter"
  - "TextLogFormatter"
  - "JsonLogFormatter"
  - "OTLP JSON"
  - "Structured Logging"
  - "StringBuilder"
---

`LogFormatter` is a stateless singleton that converts a log emission into text by appending to a caller-supplied `StringBuilder`. Formatters hold no per-instance mutable state — the `StringBuilder` is owned and pooled by the emitter, so formatters impose zero per-record allocation. Two built-in implementations cover the two most common deployment patterns: `TextLogFormatter` for human-readable console output and `JsonLogFormatter` for OTLP-compatible JSON ingest.

```scala
trait LogFormatter {
  def format(
    sb: StringBuilder,
    timestampNanos: Long,
    severity: Severity,
    severityText: String,
    body: String,
    builder: Attributes.AttributesBuilder,
    traceIdHi: Long,
    traceIdLo: Long,
    spanId: Long,
    traceFlags: Byte,
    throwable: Option[Throwable]
  ): Unit

  def formatRecord(sb: StringBuilder, record: LogRecord): Unit
}
```

## Usage

The following example routes `log` output to two sinks simultaneously — human-readable text to stdout and OTLP JSON to stderr:

```scala
import zio.blocks.telemetry._

log.writer(TextLogFormatter, StdoutWriter)
log.writer(JsonLogFormatter, StderrWriter)

log.info("order shipped", "orderId" -> "ORD-7")
```

`TextLogFormatter` caches the per-second UTC timestamp prefix to minimize repeated string construction on the hot path. `JsonLogFormatter` produces output matching the OTLP log data model (`timeUnixNano`, `severityNumber`, `body.stringValue`, `attributes`) and includes trace correlation fields when a `SpanContext` is active.

## Built-in Formatters

| Formatter | Output style | Best for |
|---|---|---|
| `TextLogFormatter` | `2026-07-22T10:30:00.123Z INFO  [MyClass.method:42] message {key="val"}` | Local development, human tailing |
| `JsonLogFormatter` | `{"timeUnixNano":"...","severityNumber":9,"body":{"stringValue":"..."},"attributes":[...]}` | Log aggregators, OTLP collectors |

## Key Operations

| Member | Description |
|---|---|
| `format(sb, timestampNanos, severity, severityText, body, builder, ...)` | Low-level format path invoked by the macro-generated emitter. Reads attributes directly from the in-progress `AttributesBuilder` to avoid constructing an `Attributes` object on the hot path. |
| `formatRecord(sb, record: LogRecord)` | Higher-level format path invoked by `FormattedLogRecordProcessor`. Reads attributes from the completed `LogRecord`. |

:::tip
To add a custom formatter — for example, to emit structured logs in a vendor-specific format — implement `LogFormatter` as a singleton `object` and pass it to `log.writer(MyFormatter, StdoutWriter)`.
:::
