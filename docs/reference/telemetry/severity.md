---
id: severity
title: "Severity"
description: "24-level severity scale in the ZIO Blocks Telemetry logging pillar — follows the OpenTelemetry log data model with six named categories from Trace to Fatal."
keywords:
  - "Severity"
  - "Log Level"
  - "OpenTelemetry"
  - "fromNumber"
  - "fromText"
  - "Severity Scale"
---

`Severity` is a sealed trait with 24 case objects that represent the OpenTelemetry log data model severity scale. The levels are organized into six named categories — `Trace` (1–4), `Debug` (5–8), `Info` (9–12), `Warn` (13–16), `Error` (17–20), and `Fatal` (21–24) — where each category has a primary level and three fine-grained variants (`Trace2`/`Trace3`/`Trace4`, etc.). Every level exposes a numeric value and a canonical text label.

```scala
sealed trait Severity {
  def number: Int
  def text: String
}
```

## Usage

The following example sets a global severity floor, then resolves severity values from text and numeric inputs:

```scala
import zio.blocks.telemetry._

// Suppress Trace, Debug, and Info globally
log.setMinSeverity(Severity.Warn)

// Parse from text (case-insensitive; returns the primary level of each category)
val fromText: Option[Severity] = Severity.fromText("ERROR")  // Some(Severity.Error)
val unknown: Option[Severity]  = Severity.fromText("VERBOSE") // None

// Parse from a numeric level
val fromNum: Option[Severity]  = Severity.fromNumber(9)  // Some(Severity.Info)
val fine: Option[Severity]     = Severity.fromNumber(10) // Some(Severity.Info2)
val outOfRange: Option[Severity] = Severity.fromNumber(0) // None
```

`Severity.fromText` only maps the six canonical text labels (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL`) and returns the primary level of each category. The fine-grained variants (`Info2`–`Info4`, etc.) can only be obtained via `Severity.fromNumber`.

## Severity Table

| Category | Numbers | Primary object | `text` |
|---|---|---|---|
| Trace | 1–4 | `Severity.Trace` | `"TRACE"` |
| Debug | 5–8 | `Severity.Debug` | `"DEBUG"` |
| Info | 9–12 | `Severity.Info` | `"INFO"` |
| Warn | 13–16 | `Severity.Warn` | `"WARN"` |
| Error | 17–20 | `Severity.Error` | `"ERROR"` |
| Fatal | 21–24 | `Severity.Fatal` | `"FATAL"` |

## Key Operations

| Member | Description |
|---|---|
| `number: Int` | Numeric severity level (1–24). Used by `LogRecordProcessor#minimumLevel` comparisons. |
| `text: String` | Canonical text label for the category (`"INFO"`, `"WARN"`, etc.). Fine-grained variants share the same text as their primary level. |
| `Severity.fromNumber(n: Int): Option[Severity]` | Returns the severity for numeric value 1–24, or `None` for out-of-range values. |
| `Severity.fromText(s: String): Option[Severity]` | Case-insensitive parse of the six canonical labels. Returns `None` for unrecognized strings. |
