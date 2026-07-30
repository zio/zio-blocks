---
id: log-record-processor
title: "LogRecordProcessor"
description: "Hook for log record lifecycle (onEmit) in the telemetry module's logging area. Implement to export, filter, or format log records."
keywords:
  - "Structured Logging"
  - "Log Export"
  - "Lifecycle Hook"
  - "LogRecordProcessor"
sidebar_label: "LogRecordProcessor"
---

`LogRecordProcessor` is the extension point for the logging emit pipeline. Every `LogRecord` produced by a `Logger` is dispatched to all registered processors in insertion order; each processor's `onEmit` callback fires synchronously on the emitting thread.

```scala
trait LogRecordProcessor extends AutoCloseable {
  def onEmit(logRecord: LogRecord): Unit
  def shutdown(): Unit
  def forceFlush(): Unit
  override def close(): Unit = shutdown()

  // Optional severity gate — Logger caches the minimum across all processors
  // and drops records below the threshold before constructing a LogRecord.
  def minimumLevel: Int = 1   // default: accept everything (Severity.Trace = 1)
}

object LogRecordProcessor {
  val noop: LogRecordProcessor  // no-op singleton; all methods are no-ops
}
```

## Predefined Implementations

| Type | Description |
|------|-------------|
| `LogRecordProcessor.noop` | No-op singleton; use as a placeholder. |
| `ConsoleLogRecordProcessor` | Writes formatted text to stdout. When it is the sole registered processor, `Logger` uses a fast formatted path that skips `LogRecord` allocation entirely. |
| `FormattedLogRecordProcessor(formatter, writer)` | Formats each record with a `LogFormatter` and writes the result via a `LogWriter`. Added when `log.writer(formatter, writer)` is called. |

## Creating Values

Implement `LogRecordProcessor` to plug in a custom export or filtering layer:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .addLogRecordProcessor(new LogRecordProcessor {
    def onEmit(r: LogRecord): Unit = {
      // route to an OTLP exporter, Kafka, or any sink
      println(s"[${r.severity}] ${r.body}")
    }
    def shutdown(): Unit   = ()
    def forceFlush(): Unit = ()
  })
  .build()

val logger = provider.get("com.example")
logger.info("hello from processor")

provider.shutdown()
```

## Core Operations

| Method | Description |
|--------|-------------|
| `onEmit(logRecord: LogRecord)` | Called for every record whose severity number meets `minimumLevel`. Called synchronously on the emitting thread. |
| `minimumLevel: Int` | The lowest severity number this processor accepts. `Logger` caches the minimum across all processors and skips `LogRecord` construction entirely for records below the threshold. |
| `shutdown()` | Flush and release resources. Called by `LoggerProvider.shutdown()`. |
| `forceFlush()` | Flush any buffered records immediately. |
| `close()` | Delegates to `shutdown()`; satisfies `AutoCloseable`. |

## Integration

Processors are registered via `LoggerProvider.builder.addLogRecordProcessor(processor)`. Multiple processors can be added; they receive callbacks in insertion order. Processor exceptions are caught and printed to stderr, so one failing processor does not stop the rest. The global `log` object's `addProcessor` and `writer` methods add processors to the currently installed `Logger`'s pipeline without requiring a full provider rebuild.
