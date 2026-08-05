---
id: exporter-config
title: "ExporterConfig"
description: "Where a collector lives and how much telemetry to accumulate before sending — endpoint, auth headers, timeout, and batching limits."
keywords:
  - "Telemetry Export"
  - "Collector Endpoint"
  - "Export Batching"
  - "ExporterConfig"
sidebar_label: "ExporterConfig"
---

`ExporterConfig` answers two questions for an exporter: where to send data, and how much to hold before sending it.

Both matter for the same reason. A collector is a network hop away, so sending one span per request would add a round trip to every request you were trying to measure. Instead, records accumulate and go out in batches — which means choosing how long to wait, how many to hold, and what to do when they arrive faster than they leave.

```scala
final case class ExporterConfig(
  endpoint: String                  = "http://localhost:4318",
  headers: Map[String, String]      = Map.empty,
  timeout: Duration                 = Duration.ofSeconds(30),
  maxQueueSize: Int                 = 2048,
  maxBatchSize: Int                 = 512,
  flushIntervalMillis: Long         = 5000
)
```

Every field has a default, so `ExporterConfig()` is valid and points at a collector on localhost — the usual local development setup.

## Reaching a Collector

`endpoint` is the collector's base URL; `headers` carries whatever it needs to accept you, typically an API key or bearer token. `timeout` bounds a single send attempt:

```scala mdoc:compile-only
import zio.blocks.telemetry.otel._
import java.time.Duration

val config = ExporterConfig(
  endpoint = "https://otlp.example.com:4318",
  headers = Map("Authorization" -> "Bearer <token>"),
  timeout = Duration.ofSeconds(10)
)
```

Headers are treated as sensitive: `toString` prints their count, never their contents, so a config logged at startup can't leak a token.

## Tuning the Batch

The three batching fields trade latency against overhead:

- **`flushIntervalMillis`** — how long a partial batch waits before going out anyway. Lower it to see data sooner; raise it to send fewer, larger requests.
- **`maxBatchSize`** — how many records go in one request. A batch is sent as soon as it fills, without waiting for the interval.
- **`maxQueueSize`** — how many records may be waiting at once. This is your backpressure limit: once the queue is full, further records are dropped rather than allowed to grow without bound.

The defaults (a 5-second interval, 512 per batch, 2048 queued) suit a service with steady moderate traffic. A low-traffic service can afford a shorter interval; a high-throughput one should raise `maxQueueSize` before it starts dropping.

## See Also

- [HttpSender](./http-sender.md) — what performs the send this config describes
- [OTLP Export](./index.md) — how configuration, transport, and propagation fit together
