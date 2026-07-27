---
id: log-record-processor
title: "LogRecordProcessor"
description: "Open lifecycle trait in the ZIO Blocks Telemetry logging pillar — receives onEmit callbacks from a Logger and gates below-threshold records via minimumLevel."
keywords:
  - "LogRecordProcessor"
  - "onEmit"
  - "minimumLevel"
  - "AutoCloseable"
  - "Log Export"
  - "OTLP"
---

`LogRecordProcessor` is an open trait that receives log records from a `Logger`. Every time the `log` macro produces a record that passes the global severity floor, each registered `LogRecordProcessor#onEmit` is called in sequence. The `minimumLevel` property lets individual processors declare their own floor: the `Logger` skips processors whose `minimumLevel` is higher than the record's `severity.number`. `LogRecordProcessor.noop` is the no-op singleton.

```scala
trait LogRecordProcessor extends AutoCloseable {
  def onEmit(logRecord: LogRecord): Unit
  def shutdown(): Unit
  def forceFlush(): Unit
  override def close(): Unit  // delegates to shutdown()
  def minimumLevel: Int       // default 1 — accepts all severities
}
```

## Usage

The following example implements a `LogRecordProcessor` inline and attaches it to the `log` global via `log.addProcessor`:

```scala
import zio.blocks.telemetry._

def exportToOtlp(r: LogRecord): Unit = () // placeholder

log.addProcessor(new LogRecordProcessor {
  def onEmit(r: LogRecord): Unit = exportToOtlp(r)
  def shutdown(): Unit           = ()
  def forceFlush(): Unit         = ()
})

log.error("payment failed", "orderId" -> "ORD-42")
```

A processor that should gate on a higher severity level overrides `minimumLevel`. For example, a noisy debug processor can declare `override def minimumLevel: Int = 5` to receive only `Debug` and above (numeric value ≥ 5) without requiring a global severity change.

## Key Operations

| Member | Description |
|---|---|
| `onEmit(logRecord: LogRecord): Unit` | Called for every record whose `severity.number >= minimumLevel`. Runs synchronously on the logging thread. |
| `shutdown(): Unit` | Releases resources held by this processor (buffers, connections). Called by `log.removeAll()`. |
| `forceFlush(): Unit` | Exports any buffered records immediately. |
| `minimumLevel: Int` | Lowest severity number this processor accepts. Default `1` accepts everything. |
| `LogRecordProcessor.noop` | Singleton no-op implementation — all methods are empty. |

:::caution
`onEmit` is called synchronously on the thread emitting the log record. A slow or blocking implementation will add latency to every log call. Prefer a batching, asynchronous processor for production exporters.
:::
