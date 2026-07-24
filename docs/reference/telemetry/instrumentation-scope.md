---
id: instrumentation-scope
title: "InstrumentationScope"
description: "Library or component identity in the ZIO Blocks Telemetry shared vocabulary — stamped into every Span, SpanData, and LogRecord to identify the producing instrumentation."
keywords:
  - "InstrumentationScope"
  - "Library Identity"
  - "Instrumentation"
  - "Span Scope"
  - "LogRecord Scope"
  - "version"
---

`InstrumentationScope` identifies the library or component that produced a telemetry signal. It is stamped into every `Span`, `SpanData`, and `LogRecord` and carried through to the exporter, where telemetry backends use it to group signals by the library version that produced them. A scope has a required `name` (typically a reverse-DNS package name), an optional `version`, and an optional set of additional `Attributes`.

```scala
final case class InstrumentationScope(
  name: String,
  version: Option[String] = None,
  attributes: Attributes = Attributes.empty
)
```

## Usage

The following example constructs a scope with a name and version, then passes it to a `TracerProvider` via `TracerProvider#get`:

```scala
import zio.blocks.telemetry._

// Explicitly construct a scope value
val scope: InstrumentationScope =
  InstrumentationScope("com.example.mylib", Some("2.1.0"))

// Or obtain a scoped Tracer directly — the scope is created internally
val tracer: Tracer = TracerProvider.builder.build().get("com.example.mylib", "2.1.0")

tracer.span("operation") { _ => () }
```

`TracerProvider#get(name, version)` constructs an `InstrumentationScope(name, Some(version))` internally when `version` is non-empty, and `InstrumentationScope(name, None)` when `version` is the empty string default. Both forms stamp the scope into every `SpanData` the tracer produces.

## Key Fields

| Field | Type | Description |
|---|---|---|
| `name: String` | Required | Reverse-DNS name of the instrumented library or component, e.g. `"com.example.payments"`. |
| `version: Option[String]` | Optional | Semantic version string of the instrumentation, e.g. `Some("1.2.3")`. `None` when no version is declared. |
| `attributes: Attributes` | Optional | Additional metadata about the scope. Defaults to `Attributes.empty`. |

:::tip
Use a reverse-DNS name (`com.example.mylib`) for the scope `name` rather than a human-readable label. Telemetry backends index by scope name and version, so a consistent naming convention makes it easy to correlate signals across service deployments and library upgrades.
:::
