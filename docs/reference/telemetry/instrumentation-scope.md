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

An `InstrumentationScope` is a label that says which library or component produced a piece of telemetry. It travels with every `Span`, `SpanData`, and `LogRecord` all the way to the exporter, so a backend can tell where each signal came from. A scope has a required `name` (usually a reverse-DNS package name like `com.example.mylib`), an optional `version`, and optional extra `Attributes`.

```scala
final case class InstrumentationScope(
  name: String,
  version: Option[String] = None,
  attributes: Attributes = Attributes.empty
)
```

## Motivation

A running application emits telemetry from many sources at once — your own code, framework internals, and third-party libraries all produce spans and logs into the same stream. Without a marker of origin those signals are indistinguishable: you can't filter to one library, attribute a slow span to the component that created it, or tell whether a regression arrived with a particular library upgrade. `InstrumentationScope` solves this by stamping each signal with the name and version of the code that produced it, so a backend can group, filter, and compare telemetry by its source.

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


:::tip
Use a reverse-DNS name (`com.example.mylib`) for the scope `name` rather than a human-readable label. Telemetry backends index by scope name and version, so a consistent naming convention makes it easy to correlate signals across service deployments and library upgrades.
:::
