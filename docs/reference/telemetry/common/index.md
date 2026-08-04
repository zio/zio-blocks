---
id: index
title: "Common Types"
description: "Shared telemetry types: Attributes, AttributeKey, Resource, and InstrumentationScope — the common building blocks used across tracing, logging, and metrics."
keywords:
  - "Telemetry Metadata"
  - "Common Types"
  - "Common Building Blocks"
  - "Attributes"
sidebar_label: "Common Types"
---

These four types describe every signal, across all three telemetry pillars — tracing, logging, and metrics. Every span, log record, and metric data point is annotated with `Attributes`; every provider accepts a `Resource`; every scope instance is tagged with an `InstrumentationScope`. Understanding these four types unlocks the rest of the module.

## Types

| Type | Description |
|------|-------------|
| [Attributes](./attributes.md) | Immutable, unboxed parallel-array collection of typed key-value pairs. Carried by every signal: `Span`, `LogRecord`, `Counter`, `Resource`, and `InstrumentationScope`. |
| [AttributeKey](./attribute-key.md) | Typed, interned key for an `Attributes` entry. Binds a string name to one of eight value types (`String`, `Long`, `Double`, `Boolean`, and their `Seq` variants). |
| [Resource](./resource.md) | Immutable `Attributes` wrapper that describes the entity producing telemetry (service name, SDK version, deployment environment). Shared across all three providers. |
| [InstrumentationScope](./instrumentation-scope.md) | Named and versioned identity for the library or component that created a signal. Set when calling `TracerProvider.get`, `LoggerProvider.get`, or `MeterProvider.get`. |

## Design

All four types are designed for hot-path telemetry code:

- **`Attributes`** stores `Long`, `Double`, and `Boolean` values unboxed in a parallel primitive array via bit-casting. Boxing only occurs when iterating with `foreach` or converting to `Map`.
- **`AttributeKey`** is interned by name and value type, so equality checks are reference-equality fast and there is no per-call string allocation.
- **`Resource`** is immutable and shared across all scope instances created by a provider; it is stamped into each `SpanData`, `LogRecord`, and `MetricData` snapshot at export time, not at signal creation.
- **`InstrumentationScope`** is constructed once per scope by the provider and referenced from all signals that scope creates.

## See Also

- [Telemetry](../index.md) — module overview and the three-pillar architecture
- [Tracing](../tracing/index.md) — span lifecycle types that use `Attributes` and `Resource`
- [Logging](../logging/index.md) — structured log record types that carry `Attributes`
- [Metrics](../metrics/index.md) — dimensional measurement types that use `Attributes` as labels
