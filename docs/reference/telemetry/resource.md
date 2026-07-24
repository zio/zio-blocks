---
id: resource
title: "Resource"
description: "Entity description in the ZIO Blocks Telemetry shared vocabulary — immutable Attributes wrapper that identifies the service, container, or host producing telemetry."
keywords:
  - "Resource"
  - "service.name"
  - "telemetry.sdk"
  - "Resource.default"
  - "Resource.create"
  - "Attributes Merge"
---

`Resource` describes the entity producing telemetry signals — typically a service, container, or host. It is an immutable wrapper around `Attributes` and is stamped into every `Span`, `SpanData`, `LogRecord`, and metric data point emitted by an instrument. `Resource.default` auto-populates four standard attributes: `service.name = "unknown_service"`, `telemetry.sdk.name = "zio-blocks"`, `telemetry.sdk.language = "scala"`, and `telemetry.sdk.version` from the build metadata.

```scala
final case class Resource(attributes: Attributes)
```

## Usage

The following example merges `Resource.default` with a service-specific resource so that the SDK attributes are retained and `service.name` is overridden:

```scala
import zio.blocks.telemetry._

val serviceResource: Resource =
  Resource.create(Attributes.of(Attributes.ServiceName, "order-service"))

// Merge: other's attributes take precedence on key conflict
val combined: Resource = Resource.default.merge(serviceResource)

// "order-service" wins over the default "unknown_service"
val name: Option[String] = combined.attributes.get(Attributes.ServiceName)
```

Supply the merged resource to `TracerProvider.builder.setResource(combined)` and `LoggerProvider.builder.setResource(combined)` to stamp the service name into all emitted signals.

## Constructors

| Constructor | Description |
|---|---|
| `Resource.empty` | A `Resource` with no attributes. |
| `Resource.default` | A `Resource` with `service.name`, `telemetry.sdk.name`, `telemetry.sdk.language`, and `telemetry.sdk.version` pre-populated. |
| `Resource.create(attributes: Attributes)` | Wraps the given `Attributes` in a new `Resource`. |

## Key Operations

| Method | Description |
|---|---|
| `merge(other: Resource): Resource` | Returns a new `Resource` whose attributes are the union of `this.attributes` and `other.attributes`. On key conflict, `other`'s value takes precedence (follows `Attributes.++` semantics). |
| `attributes: Attributes` | The underlying attribute collection. Read to inspect or forward individual attributes. |

:::note
`Attributes.ServiceName` is a predefined `AttributeKey[String]` for the `"service.name"` key. Use it rather than `AttributeKey.string("service.name")` to avoid key name typos and ensure consistent lookup.
:::
