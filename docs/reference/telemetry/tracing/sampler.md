---
id: sampler
title: "Sampler"
description: "Sampling decision interface in the ZIO Blocks Telemetry tracing pillar — determines whether each new span should be dropped, recorded, or recorded and exported."
keywords:
  - "Sampler"
  - "Sampling Decision"
  - "AlwaysOnSampler"
  - "AlwaysOffSampler"
  - "ParentBasedSampler"
  - "SamplingResult"
  - "SamplingDecision"
---

`Sampler` is a trait that decides the fate of every new span before any recording work occurs. A `Tracer` consults its configured `Sampler` at the moment a span is started, passing the parent context, trace ID, span name, kind, initial attributes, and links. The `Sampler` returns a `SamplingResult` carrying a `SamplingDecision` (`Drop`, `RecordOnly`, or `RecordAndSample`), optional additional attributes, and a trace state string.

Three built-in implementations cover the most common production and testing needs:

```scala
trait Sampler {
  def shouldSample(
    parentContext: Option[SpanContext],
    traceIdHi: Long,
    traceIdLo: Long,
    name: String,
    kind: SpanKind,
    attributes: Attributes,
    links: Seq[SpanLink]
  ): SamplingResult

  def description: String
}

object AlwaysOnSampler  extends Sampler  // always RecordAndSample
object AlwaysOffSampler extends Sampler  // always Drop
final case class ParentBasedSampler(root: Sampler) extends Sampler
```

## Usage

The following example configures a `TracerProvider` with `ParentBasedSampler`, which records all root spans by delegating to `AlwaysOnSampler` and follows the parent decision for child spans:

```scala
import zio.blocks.telemetry._

val provider = TracerProvider.builder
  .setSampler(ParentBasedSampler(AlwaysOnSampler))
  .build()

trace.install(provider)

trace.span("root") { _ =>
  // This child span is sampled because the parent is sampled
  trace.span("child") { _ => () }
}

provider.shutdown()
```

For load-shedding in production, supply a custom `Sampler` that examines the span name or attributes and returns `SamplingDecision.Drop` for low-value spans.

## Built-in Samplers

| Sampler | Decision | Use case |
|---|---|---|
| `AlwaysOnSampler` | `RecordAndSample` for every span | Development, integration tests, low-traffic services |
| `AlwaysOffSampler` | `Drop` for every span | Disable tracing entirely; useful in unit tests |
| `ParentBasedSampler(root)` | Follows parent's sampled flag; delegates to `root` for root spans | Production — respects upstream sampling decisions |

:::tip
`ParentBasedSampler(AlwaysOnSampler)` is the standard production default: it records all root spans and propagates the sampling decision to all child spans so the trace is either complete or fully absent.
:::
