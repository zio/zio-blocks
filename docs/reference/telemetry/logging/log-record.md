---
id: log-record
title: "LogRecord"
description: "Immutable snapshot of a single log emission — the read-only value a LogRecordProcessor receives and a LogFormatter renders."
keywords:
  - "Structured Logging"
  - "Trace Correlation"
  - "Immutable Log Snapshot"
  - "LogRecord"
sidebar_label: "LogRecord"
---

A `LogRecord` is one log entry as a value — everything a single `log.info(...)` call captured, bundled together: the message, its severity, when it happened, the key/value fields attached to it, and which request it belongs to.

The type exists because a log entry has to travel. From the call site it may go to your console, to a file, and to a backend that indexes it, and each of those destinations needs the whole story in one self-contained package — a bare string wouldn't carry the severity or the trace it came from. Because a record is immutable, every destination can read the same one safely.

You never build a `LogRecord` in normal use; the global [`log`](./index.md) (or a [`Logger`](./logger.md)) builds one for you on every call. You meet it when you extend the pipeline: it's the value handed to a [`LogRecordProcessor`](./log-record-processor.md)'s `onEmit` and to a [`LogFormatter`](./log-formatter.md), so writing either means reading fields off a record.

```scala
final class LogRecord private (
  timestampNanos:         Long,
  observedTimestampNanos: Long,
  severity:               Severity,
  severityText:           String,
  body:                   LogMessage,           // LogMessage.Simple(text), or a lazy template
  traceIdHi:              Long,                 // 0L when no span is active
  traceIdLo:              Long,                 // 0L when no span is active
  spanId:                 Long,                 // 0L when no span is active
  traceFlags:             Byte,                 // 0x00 when not sampled
  resource:               Resource,
  instrumentationScope:   InstrumentationScope,
  throwable:              Option[Throwable] = None
  // ...plus private source fields and private user attributes (below)
) {
  def hasTraceId:  Boolean  // traceIdHi != 0L || traceIdLo != 0L
  def hasSpanId:   Boolean  // spanId != 0L

  def sourceFile:     String  // "" when no source was captured
  def sourceFunction: String  // fully qualified, "" when absent
  def sourceLine:     Int     // 0 when absent (non-positive lines normalize to 0)

  def attribute[A](key: AttributeKey[A]): Option[A]  // None when absent; kind must match how it was written
  def attribute(key: String): Option[AttributeValue]
  def foreachAttribute(f: (String, AttributeValue) => Unit): Unit  // user attrs only; boxes per attribute

  // Operation methods — the extension points for custom LogEnrichment instances:
  def withAttribute[A](key: AttributeKey[A], value: A): LogRecord
  def withBody(body: LogMessage): LogRecord
  def withSeverity(severity: Severity): LogRecord
  def withThrowable(throwable: Option[Throwable]): LogRecord
}

object LogRecord {
  def builder: LogRecordBuilder  // the only way to obtain a builder; its state is fully encapsulated
}
```

## Source metadata lives apart from user attributes

The source location the macro captured — file path, fully-qualified function name, line number — is stored in dedicated private fields, not in the attribute set. There is no `record.attributes` accessor: `attribute("code.file.path")` reads back only a *user* attribute with that name, never the captured source. `foreachAttribute` likewise enumerates user attributes only. Use `sourceFile`, `sourceFunction`, and `sourceLine` for the captured source.

When no source was captured (direct `Logger` calls, or a builder that never set one), the accessors report `""`, `""`, and `0`.

## Reading attributes has a boxing cost

`attribute` and `foreachAttribute` are the external convenience API: every value crosses as an [`AttributeValue`](../common/any-value.md) wrapper, so primitives box on the way out, and `foreachAttribute` allocates one wrapper per attribute. The typed `attribute[A]` lookup stays cheap for single reads. Hot exporters (text, JSON, OTLP) bypass both and iterate the private typed store directly — don't claim zero-attribute-allocation for `foreachAttribute`; use it for inspection, tests, and custom sinks where clarity beats the last nanosecond.

The key kind must match the stored kind: mismatched-kind lookup is unsupported and can fail when the value is extracted. Prefer the `AttributeKey` matching how the value was written; for input whose kind is unknown, use the untyped `String` overload, which reports the stored kind as an `AttributeValue`.

## The builder exposes no storage

`LogRecordBuilder` is obtained only from `LogRecord.builder`. Its constructor, fields, and any `copy` are gone — the typed setters are the only way in and `build` is the only way out, so the attribute container never crosses the public API. Custom [`LogEnrichment`](./log-enrichment.md) instances add user attributes with `withAttribute` and replace body, severity, or throwable with the `with*` methods; enrichment never touches source fields.

Exporters combine both sides at the boundary using the stable OpenTelemetry keys (`code.file.path`, `code.function.name`, `code.line.number`): the captured source is written first, then user attributes. A user attribute that shares a canonical key is suppressed only when the matching source field is present — source wins per field, never duplicated. When the source field is absent, the user value passes through unchanged, so externally supplied `code.*` values are never lost silently.

Each field mirrors what the emission recorded. The trace and span IDs are what tie this entry back to the request that produced it: they're filled in when the call ran inside an active span, and left as `0` when it didn't — so check `hasTraceId`/`hasSpanId` rather than comparing to zero yourself.
