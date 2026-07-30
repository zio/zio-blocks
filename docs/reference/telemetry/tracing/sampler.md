---
id: sampler
title: "Sampler"
description: "Decides per-span whether to drop, record-only, or record-and-sample. Built-ins: AlwaysOnSampler, AlwaysOffSampler, ParentBasedSampler."
keywords:
  - "Sampler head sampling"
  - "AlwaysOnSampler AlwaysOffSampler ParentBasedSampler"
  - "SamplingDecision Drop RecordAndSample"
  - "TracerProvider setSampler"
sidebar_label: "Sampler"
---

`Sampler` is a strategy object that decides, for every new span, whether to record and export it (`RecordAndSample`), record it without exporting (`RecordOnly`), or drop it entirely (`Drop`). When the decision is `Drop`, `Tracer` returns `Span.NoOp` with zero allocation; when `RecordAndSample`, a live `RecordingSpan` is built, and `SpanProcessor` hooks fire.

```scala
trait Sampler {
  def shouldSample(
    parentContext:   Option[SpanContext],
    traceIdHi:       Long,
    traceIdLo:       Long,
    name:            String,
    kind:            SpanKind,
    attributes:      Attributes,
    links:           Seq[SpanLink]
  ): SamplingResult

  def description: String
}

final case class SamplingResult(
  decision:   SamplingDecision,  // Drop | RecordOnly | RecordAndSample
  attributes: Attributes,        // additional attributes injected into the span
  traceState: String
)
```

## Predefined Samplers

| Sampler                             | Behaviour                                                                          |
|-------------------------------------|------------------------------------------------------------------------------------|
| `AlwaysOnSampler`                   | Always returns `RecordAndSample`. Default for `TracerProvider.builder`.            |
| `AlwaysOffSampler`                  | Always returns `Drop`. No allocation on any span path.                             |
| `ParentBasedSampler(root: Sampler)` | Follows the parent span's sampled flag; delegates to `root` when no parent exists. |

```scala mdoc:compile-only
import zio.blocks.telemetry._

// All spans sampled (default)
val allOn = TracerProvider.builder.setSampler(AlwaysOnSampler).build()

// No spans — useful for disabling telemetry in tests
val allOff = TracerProvider.builder.setSampler(AlwaysOffSampler).build()

// OTel-compatible head sampling: follow parent; sample root spans
val parentBased = TracerProvider.builder
  .setSampler(ParentBasedSampler(AlwaysOnSampler))
  .build()

allOn.shutdown()
allOff.shutdown()
parentBased.shutdown()
```

## Custom Samplers

Implement `Sampler` to apply custom logic — for instance, sampling only requests from specific tenants or at a probabilistic rate:

```scala mdoc:compile-only
import zio.blocks.telemetry._

object TenantSampler extends Sampler {
  private val sampledResult = SamplingResult(SamplingDecision.RecordAndSample, Attributes.empty, "")
  private val droppedResult = SamplingResult(SamplingDecision.Drop, Attributes.empty, "")

  def shouldSample(
    parentContext: Option[SpanContext],
    traceIdHi: Long, traceIdLo: Long,
    name: String, kind: SpanKind, attributes: Attributes,
    links: Seq[SpanLink]
  ): SamplingResult =
    if (attributes.get(AttributeKey.string("tenant")).contains("premium"))
      sampledResult
    else
      droppedResult

  def description: String = "TenantSampler"
}

val provider = TracerProvider.builder.setSampler(TenantSampler).build()
provider.shutdown()
```

## Integration

The sampler is configured via `TracerProvider.builder.setSampler(sampler)`. It is called once per new span in the hot path; `AlwaysOnSampler` and `AlwaysOffSampler` cache their `SamplingResult` instances, so they incur zero allocation.
