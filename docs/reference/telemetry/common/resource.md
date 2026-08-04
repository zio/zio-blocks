---
id: resource
title: "Resource"
description: "Describes the entity producing telemetry (service, container, host). An immutable wrapper around Attributes shared by TracerProvider, LoggerProvider, and MeterProvider."
keywords:
  - "Telemetry Metadata"
  - "Resource Attributes"
  - "Semantic Conventions"
  - "Resource"
---

`Resource` is an immutable wrapper around `Attributes` that describes the entity producing telemetry — the service, container, host, or runtime. It is stamped on every span, log record, and metric data point so that a backend can group signals by their origin. All three provider types (`TracerProvider`, `LoggerProvider`, `MeterProvider`) accept a `Resource` at configuration time and propagate it through their scope instances.

```scala
final case class Resource(attributes: Attributes) {
  def merge(other: Resource): Resource  // other's attributes take precedence on duplicate keys
}

object Resource {
  val empty:   Resource                             // no attributes
  val default: Resource                             // service.name + telemetry.sdk.* attributes
  def create(attributes: Attributes): Resource
}
```

## Predefined Instances

| Instance           | Description                                                                                                                                                          |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Resource.empty`   | A `Resource` with no attributes. Use as a neutral base.                                                                                                              |
| `Resource.default` | Pre-populated with `service.name = "unknown_service"`, `telemetry.sdk.name = "zio-blocks"`, `telemetry.sdk.language = "scala"`, and the SDK version from build info. |
