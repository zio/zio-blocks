---
id: log-enrichment
title: "LogEnrichment"
description: "Typeclass resolved at compile time by macro-generated log.* calls to attach typed values (Throwable, attributes, key-value pairs) to LogRecords."
keywords:
  - "Structured Logging"
  - "Log Enrichment"
  - "Compile-Time Typeclass"
  - "LogEnrichment"
sidebar_label: "LogEnrichment"
---

`LogEnrichment[A]` is a typeclass that macro-generated `log.*` calls resolve at compile time. When you write `log.info("msg", ex, "key" -> value, ...)`, the Scala macro expands each enrichment argument by finding an implicit `LogEnrichment[A]` for its type and calling `enrich` to attach the value to the emitted `LogRecord`.

```scala
trait LogEnrichment[A] {
  def enrich(builder: AttributesBuilder, record: LogRecordBuilder, value: A): Unit
}
```

## Built-in Instances

| Enrichment type | Effect on `LogRecord` |
|-----------------|----------------------|
| `Throwable` | Adds `exception.type` and `exception.message` string attributes; stores the throwable in `LogRecord.throwable`. |
| `Attributes` | Merges all attributes into the record's attribute set. |
| `Severity` | Overrides the record's severity. |
| `(String, String)` | Adds a `String`-typed attribute. |
| `(String, Long)` | Adds a `Long`-typed attribute. |
| `(String, Int)` | Adds a `Long`-typed attribute (Int widened to Long). |
| `(String, Double)` | Adds a `Double`-typed attribute. |
| `(String, Boolean)` | Adds a `Boolean`-typed attribute. |

## Usage

The enrichment typeclass is transparent to callers — all types with built-in instances are accepted directly as vararg arguments to `log.*`:

```scala mdoc:compile-only
import zio.blocks.telemetry._

val ex: Throwable = new RuntimeException("connection refused")

log.writer(TextLogFormatter, StdoutWriter)

log.error(
  "payment failed",
  ex,                          // Throwable enrichment
  "orderId"   -> "ord-123",   // (String, String) enrichment
  "amount"    -> 99L,         // (String, Long) enrichment
  "retryable" -> false        // (String, Boolean) enrichment
)
```

## Custom Instances

Add a custom `LogEnrichment[MyType]` implicit to attach domain objects to log records:

```scala mdoc:compile-only
import zio.blocks.telemetry._

final case class RequestId(value: String)

implicit val requestIdEnrichment: LogEnrichment[RequestId] = new LogEnrichment[RequestId] {
  def enrich(record: LogRecord, value: RequestId): LogRecord =
    record.copy(attributes = record.attributes ++ Attributes.of(AttributeKey.string("request.id"), value.value))
}

log.writer(TextLogFormatter, StdoutWriter)
log.info("handling request", RequestId("req-001"))
// emits: ... request.id=req-001
```

## Integration

`LogEnrichment` is resolved at compile time by the macro — there is zero runtime overhead for type dispatch. The macro-generated code calls `enrich` for each vararg argument in sequence, accumulating attributes into an `AttributesBuilder` and optionally setting fields on a `LogRecordBuilder`, before the final `LogRecord` is constructed and passed to the processor pipeline. Custom instances must be in implicit scope at the `log.*` call site.
