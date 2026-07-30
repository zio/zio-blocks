---
id: log-record
title: "LogRecord"
description: "Immutable snapshot of a single log emission with typed attributes and native trace-correlation fields."
keywords:
  - "Structured Logging"
  - "Trace Correlation"
  - "Immutable Log Snapshot"
  - "LogRecord"
sidebar_label: "LogRecord"
---

`LogRecord` is the immutable, fully-populated snapshot of a single log emission. It is the value passed to `LogRecordProcessor.onEmit` and the unit that log formatters and exporters receive. Trace-correlation fields (`traceIdHi`, `traceIdLo`, `spanId`, `traceFlags`) are stored as unboxed primitives; the sentinel value `0` means "no active span".

```scala
final case class LogRecord(
  timestampNanos:         Long,
  observedTimestampNanos: Long,
  severity:               Severity,
  severityText:           String,
  body:                   LogMessage,       // typically LogMessage.StringMessage(text)
  attributes:             Attributes,       // code.* source location + caller-supplied attrs
  traceIdHi:              Long,             // 0L when no span is active
  traceIdLo:              Long,             // 0L when no span is active
  spanId:                 Long,             // 0L when no span is active
  traceFlags:             Byte,             // 0x00 when not sampled
  resource:               Resource,
  instrumentationScope:   InstrumentationScope,
  throwable:              Option[Throwable] = None
) {
  def hasTraceId: Boolean  // true when traceIdHi != 0L || traceIdLo != 0L
  def hasSpanId:  Boolean  // true when spanId != 0L
}

object LogRecord {
  def builder: LogRecordBuilder
}
```

## Creating Values

`LogRecord` is normally produced by `Logger` or the global `log` singleton on every `log.info(...)` call. The `LogRecord.builder` allows constructing a record manually — for instance to bridge from another logging framework:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val record = LogRecord.builder
  .setSeverity(Severity.Error)
  .setBody("disk full")
  .build

assert(record.severity == Severity.Error)
assert(!record.hasTraceId)  // no active span; traceIdHi and traceIdLo are 0L
```

## Key Fields

| Field | Description |
|-------|-------------|
| `timestampNanos` | Epoch nanoseconds when the log call was made. |
| `severity` | One of the 24 severity levels from the OTel log data model. |
| `body` | The log message string (or a key-value body for structured backends). |
| `attributes` | Typed attributes including `code.filepath`, `code.namespace`, `code.function`, `code.lineno` set by the macro, plus any caller-supplied enrichments. |
| `traceIdHi` / `traceIdLo` | The 128-bit trace ID split into two unboxed `Long`s. `0L/0L` when no span is active. |
| `spanId` | The 64-bit span ID as an unboxed `Long`. `0L` when no span is active. |
| `traceFlags` | W3C trace flags byte. `0x00` when not sampled. |
| `resource` | Entity-level descriptor inherited from `LoggerProvider`. |
| `instrumentationScope` | Library scope inherited from the `Logger`. |

## Integration

`LogRecord` flows from `Logger.emit` through every `LogRecordProcessor.onEmit` in the pipeline. `LogFormatter.formatRecord` converts one into a rendered string. `LogRecordProcessor` implementations can filter, enrich, or export records to any backend.
