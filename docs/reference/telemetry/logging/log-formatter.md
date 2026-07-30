---
id: log-formatter
title: "LogFormatter and LogWriter"
description: "Stateless log formatters (TextLogFormatter, JsonLogFormatter) and output sinks (StdoutWriter, StderrWriter) for the telemetry module's logging area."
keywords:
  - "Structured Logging"
  - "Log Formatting"
  - "Output Writer"
  - "LogFormatter"
  - "LogWriter"
sidebar_label: "LogFormatter / LogWriter"
---

`LogFormatter` and `LogWriter` are the two halves of the log output pipeline. A `LogFormatter` renders a `LogRecord` (or raw builder arrays on the fast path) into a `StringBuilder`. A `LogWriter` routes the rendered output to a destination — stdout, stderr, a file, or a custom sink.

## LogFormatter

```scala
trait LogFormatter {
  // Fast path — called by FormattedLogEmitter when the sole processor is a console writer
  def format(sb: StringBuilder, timestamp: Long, severity: Severity, source: SourceLocation,
             message: String, attrs: Attributes): Unit

  // Standard path — called by FormattedLogRecordProcessor
  def formatRecord(sb: StringBuilder, record: LogRecord): Unit
}
```

Two built-in singletons cover the common output formats:

| Singleton | Output format |
|-----------|--------------|
| `TextLogFormatter` | Human-readable: `2026-07-29T10:00:00.000Z INFO  [com.example.Svc.doWork:42] message {key=val}` |
| `JsonLogFormatter` | OTLP-compatible JSON: `{"timestamp":..., "severity":"INFO", "body":"message", "attributes":{...}}` |

## LogWriter

```scala
trait LogWriter {
  def write(content: CharSequence): Unit
  def flush(): Unit = ()
  def close(): Unit = ()
}
```

Predefined singletons:

| Singleton | Destination |
|-----------|-------------|
| `StdoutWriter` | Writes to `System.out` |
| `StderrWriter` | Writes to `System.err` |
| `NoopWriter` | Discards output (useful for benchmarks) |
| `FileLogWriter(path)` | JVM only: appends to a file |

## Usage

Use `log.writer(formatter, writer)` to add a formatted output channel to the global logger. Multiple calls add multiple channels; each receives every record independently:

```scala mdoc:compile-only
import zio.blocks.telemetry._

// Human-readable text to stdout
log.writer(TextLogFormatter, StdoutWriter)

// OTLP JSON to stderr
log.writer(JsonLogFormatter, StderrWriter)

log.info("server ready", "port" -> 8080L)
// Writes to both channels
```

When building a `LoggerProvider` directly, use `ConsoleLogRecordProcessor` (which formats records as human-readable text and writes to stdout) or implement a custom `LogRecordProcessor` that calls the formatter explicitly:

```scala mdoc:compile-only
import zio.blocks.telemetry._

// Option 1: built-in ConsoleLogRecordProcessor (text format to stdout)
val provider1 = LoggerProvider.builder
  .addLogRecordProcessor(new ConsoleLogRecordProcessor())
  .build()
provider1.get("com.example").info("started")
provider1.shutdown()

// Option 2: custom processor using LogFormatter and LogWriter directly
val provider2 = LoggerProvider.builder
  .addLogRecordProcessor(new LogRecordProcessor {
    private val sb = new StringBuilder
    def onEmit(r: LogRecord): Unit = {
      sb.clear()
      JsonLogFormatter.formatRecord(sb, r)
      StderrWriter.write(sb)
    }
    def shutdown(): Unit   = ()
    def forceFlush(): Unit = ()
  })
  .build()
provider2.get("com.example").info("started in JSON")
provider2.shutdown()
```

## Custom Processors

When neither `TextLogFormatter` nor `JsonLogFormatter` fits, implement a custom `LogRecordProcessor` that formats `LogRecord` fields directly:

```scala mdoc:compile-only
import zio.blocks.telemetry._

// Custom processor that emits records in CSV format
val csvProcessor = new LogRecordProcessor {
  def onEmit(r: LogRecord): Unit =
    println(s"${r.timestampNanos},${r.severity.text},${r.body}")
  def shutdown(): Unit   = ()
  def forceFlush(): Unit = ()
}

val provider = LoggerProvider.builder.addLogRecordProcessor(csvProcessor).build()
provider.get("com.example").info("csv test")
provider.shutdown()
```

## Integration

`LogFormatter.formatRecord(sb, record)` is called by processor implementations that write formatted output via a `LogWriter`. The built-in `ConsoleLogRecordProcessor` triggers a fast-path emitter inside `Logger` that formats directly from raw builder arrays — bypassing `LogRecord` allocation — when it is the sole registered processor. Custom processors receive a fully-populated `LogRecord` and can apply any formatting or routing logic.
