---
id: log-enrichment
title: "LogEnrichment"
description: "Typeclass consumed by macro-generated log calls in the ZIO Blocks Telemetry logging pillar — defines how each enrichment argument modifies a LogRecord."
keywords:
  - "LogEnrichment"
  - "Log Typeclass"
  - "Structured Logging"
  - "Enrichment Arguments"
  - "Throwable Enrichment"
  - "Attributes Enrichment"
---

`LogEnrichment[A]` is the typeclass that the `log` macro uses to process each enrichment argument passed to `log.info`, `log.error`, and the other severity methods. When the macro expands a call like `log.error("payment failed", ex, "orderId" -> orderId)`, it consults an implicit `LogEnrichment` instance for each argument and applies it to the in-progress `LogRecord`. Custom instances can extend enrichment to application-specific types.

```scala
trait LogEnrichment[A] {
  def enrich(record: LogRecord, value: A): LogRecord
}
```

## Usage

The following example uses the built-in enrichment instances to attach a throwable, a typed numeric attribute, and a `Severity` override to a single log call:

```scala
import zio.blocks.telemetry._

val ex: Throwable = new RuntimeException("gateway timeout")
val orderId: Long = 42L

log.error("payment failed", ex, "orderId" -> orderId)
```

The macro applies `LogEnrichment[Throwable]` (which sets `exception.type`, `exception.message`, and defers the stack trace), then `LogEnrichment[(String, Long)]` (which adds a `long` attribute), all without any runtime reflection.

## Built-in Instances

| Instance | Applied to | Effect on `LogRecord` |
|---|---|---|
| `LogEnrichment[Throwable]` | Bare exception | Sets `exception.type` and `exception.message` attributes; stores throwable for lazy stack-trace formatting. |
| `LogEnrichment[String]` | Bare string | Overrides the record body. Rarely used; the first positional argument to `log.info(msg, ...)` is always the body. |
| `LogEnrichment[Attributes]` | Pre-built `Attributes` value | Merges all attributes into the record's attribute set. |
| `LogEnrichment[Severity]` | `Severity` value | Overrides the record's severity and `severityText`. |
| `LogEnrichment[(String, String)]` | `"key" -> "value"` | Adds a `string` attribute. |
| `LogEnrichment[(String, Long)]` | `"key" -> longValue` | Adds a `long` attribute. |
| `LogEnrichment[(String, Double)]` | `"key" -> doubleValue` | Adds a `double` attribute. |
| `LogEnrichment[(String, Boolean)]` | `"key" -> boolValue` | Adds a `boolean` attribute. |
| `LogEnrichment[(String, Int)]` | `"key" -> intValue` | Converts `Int` to `Long` and adds a `long` attribute. |

:::caution
The macro expands enrichment arguments at the call site. Passing a pre-collected `Seq[Any]` with the spread syntax (`enrichments: _*`) causes a compile error. Each enrichment must be a literal argument in the call.
:::
