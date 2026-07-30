---
id: severity
title: "Severity"
description: "24-level severity scale following the OpenTelemetry log data model: Trace(1-4), Debug(5-8), Info(9-12), Warn(13-16), Error(17-20), Fatal(21-24)."
keywords:
  - "Structured Logging"
  - "Severity Levels"
  - "Log Filtering"
  - "Severity"
sidebar_label: "Severity"
---

`Severity` is a sealed trait with 24 case objects following the OpenTelemetry log data model. Six named categories map to four numeric levels each, allowing fine-grained severity distinctions within a category while remaining compatible with the canonical SLF4J / Java-util-logging five-level scale.

```scala
sealed trait Severity {
  def number: Int    // 1 to 24
  def text:   String // "TRACE", "TRACE2", ..., "DEBUG", ..., "FATAL4"
}

object Severity {
  // Trace   1-4
  case object Trace  extends Severity   // number = 1
  case object Trace2 extends Severity   // number = 2
  case object Trace3 extends Severity   // number = 3
  case object Trace4 extends Severity   // number = 4

  // Debug   5-8
  case object Debug  extends Severity   // number = 5
  // ... Debug2, Debug3, Debug4 ...

  // Info   9-12
  case object Info   extends Severity   // number = 9
  // ... Info2, Info3, Info4 ...

  // Warn  13-16
  case object Warn   extends Severity   // number = 13
  // ...

  // Error 17-20
  case object Error  extends Severity   // number = 17
  // ...

  // Fatal 21-24
  case object Fatal  extends Severity   // number = 21
  // ...

  def fromNumber(n: Int): Option[Severity]   // Some if 1 <= n <= 24
  def fromText(text: String): Option[Severity]  // case-insensitive match on text field
}
```

## Usage

The six named category case objects (`Trace`, `Debug`, `Info`, `Warn`, `Error`, `Fatal`) are the most common:

```scala mdoc:compile-only
import zio.blocks.telemetry._

// Set the global minimum level to Warn — drops Trace, Debug, and Info records
log.setMinSeverity(Severity.Warn)

log.debug("this is suppressed")  // dropped
log.warn("this is emitted")      // passes

// Parse from an external string (e.g. an environment variable)
val level = Severity.fromText("ERROR").getOrElse(Severity.Info)
assert(level == Severity.Error)
assert(level.number == 17)

// Parse from a number (e.g. from an OTLP protobuf field)
val numeric = Severity.fromNumber(9)
assert(numeric == Some(Severity.Info))
```

## Severity Filtering

`log.setMinSeverity(severity)` sets the global floor for the global `log` singleton. Records whose `severity.number` is below this floor are dropped before any `LogRecord` is constructed. Per-namespace overrides use `log.setMinSeverity("com.example.noisy", Severity.Warn)`, which takes precedence over the global floor for matching namespaces.

```scala mdoc:compile-only
import zio.blocks.telemetry._

// Global floor: only Warn and above
log.setMinSeverity(Severity.Warn)

// Namespace override: Debug and above for the query layer
log.setMinSeverity("com.example.db", Severity.Debug)

// Remove namespace override
log.clearMinSeverity("com.example.db")

// Restore uniform global filtering
log.clearAllOverrides()
```

## Integration

`Severity` appears in `LogRecord.severity`, `LogRecordProcessor.minimumLevel` (as an `Int` for comparison efficiency), and `log.setMinSeverity`. The `log.warn(...)` global method emits at `Severity.Warn` (number 13), and `Logger.warn(...)` does the same; numeric comparison is used on the hot path to avoid pattern-matching overhead.
