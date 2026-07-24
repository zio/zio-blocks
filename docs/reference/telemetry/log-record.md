---
id: log-record
title: "LogRecord"
description: "Immutable snapshot of a single log emission in the ZIO Blocks Telemetry logging pillar — carries severity, body, attributes, and trace correlation fields."
keywords:
  - "LogRecord"
  - "Log Emission"
  - "Severity"
  - "Trace Correlation"
  - "LogRecordBuilder"
  - "hasTraceId"
  - "hasSpanId"
---

`LogRecord` is the immutable data object passed to every `LogRecordProcessor#onEmit` call. It captures the full state of a single log emission: timestamps, severity, message body, typed attributes, trace correlation fields, the producing resource, and an optional throwable whose stack trace is deferred to export time. Trace fields (`traceIdHi`, `traceIdLo`, `spanId`) use primitive `0` as the absent sentinel rather than `Option` wrappers, preserving zero allocation on the logging hot path.

```scala
final case class LogRecord(
  timestampNanos: Long,
  observedTimestampNanos: Long,
  severity: Severity,
  severityText: String,
  body: LogMessage,
  attributes: Attributes,
  traceIdHi: Long,
  traceIdLo: Long,
  spanId: Long,
  traceFlags: Byte,
  resource: Resource,
  instrumentationScope: InstrumentationScope,
  throwable: Option[Throwable] = None
)
```

## Usage

The following example builds a `LogRecord` manually using `LogRecord.builder`, overriding severity and body:

```scala
import zio.blocks.telemetry._

val r: LogRecord = LogRecord.builder
  .setSeverity(Severity.Error)
  .setBody("disk full")
  .setAttribute(AttributeKey.string("host"), "node-7")
  .build

println(r.severity.text)   // ERROR
println(r.body.value)      // disk full
println(r.hasTraceId)      // false — no trace context was set
```

In normal application code, `LogRecord` instances are created internally by the `log` macro and passed through the `LogRecordProcessor` pipeline. Use `LogRecord.builder` directly when constructing test fixtures or forwarding records from an external log source.

## Key Members

| Member | Description |
|---|---|
| `timestampNanos: Long` | Epoch nanoseconds when the event occurred. |
| `severity: Severity` | One of the 24 severity levels. |
| `body: LogMessage` | The log message — a `LogMessage.Simple` or deferred-formatted `Templated`. |
| `attributes: Attributes` | Typed key-value annotations, including `code.*` source location attributes. |
| `traceIdHi / traceIdLo: Long` | 128-bit trace ID stored as two primitive longs. Both are `0` when absent. |
| `spanId: Long` | 64-bit span ID. `0` when absent. |
| `hasTraceId: Boolean` | Returns `true` when `traceIdHi != 0 || traceIdLo != 0`. |
| `hasSpanId: Boolean` | Returns `true` when `spanId != 0`. |
| `throwable: Option[Throwable]` | Optional throwable whose stack trace is formatted lazily by the exporter. |
| `LogRecord.builder` | Returns a `LogRecordBuilder` with sensible defaults (`Severity.Info`, current timestamps, empty attributes). |
