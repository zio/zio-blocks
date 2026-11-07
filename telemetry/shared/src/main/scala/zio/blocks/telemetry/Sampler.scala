/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.telemetry

/** What a [[Sampler]] decides about a new span. */
sealed trait SamplingDecision

/** The three decisions. */
object SamplingDecision {

  /**
   * Do not record the span: `Tracer#span` runs the block with [[Span.NoOp]],
   * and no processor hears of it. Inside the block, the current span is a
   * context with the sampled flag cleared, so child spans stay in the same
   * trace.
   */
  case object Drop extends SamplingDecision

  /**
   * Record the span with the sampled flag cleared. It reaches every processor
   * like a sampled span, and the in-memory buffer behind `trace.collectedSpans`
   * keeps it. Nothing in the module drops it: a processor that exports only
   * sampled spans must check `spanData.spanContext.isSampled` itself. A
   * propagator's `inject` writes the cleared flag, so downstream services see
   * it.
   */
  case object RecordOnly extends SamplingDecision

  /** Record the span with the sampled flag set. */
  case object RecordAndSample extends SamplingDecision
}

/**
 * What a [[Sampler]] returns: the decision, attributes to add to the span, and
 * its trace state. A sampler that returns the same result often can build it
 * once and reuse it.
 *
 * @param decision
 *   whether to record the span and mark it sampled
 * @param attributes
 *   attributes added to a recorded span before the ones passed to
 *   `Tracer#span`, which win on a name clash
 * @param traceState
 *   the W3C trace state of the new span; when empty, the span keeps its
 *   parent's
 */
final case class SamplingResult(
  decision: SamplingDecision,
  attributes: Attributes,
  traceState: String
)

/**
 * Decides, for each span that `Tracer#span` opens, whether to record it and
 * whether to mark it sampled. Set one with `TracerProviderBuilder#setSampler`;
 * the built-ins are [[AlwaysOnSampler]] (the default), [[AlwaysOffSampler]] and
 * [[ParentBasedSampler]].
 *
 * The sampler runs before the block, so it sees only the attributes passed when
 * the span is opened. Spans started with a [[SpanBuilder]] skip it.
 *
 * {{{
 * object HealthCheckSampler extends Sampler {
 *   private val drop   = SamplingResult(SamplingDecision.Drop, Attributes.empty, "")
 *   private val record = SamplingResult(SamplingDecision.RecordAndSample, Attributes.empty, "")
 *
 *   def shouldSample(
 *     parentContext: Option[SpanContext],
 *     traceIdHi: Long,
 *     traceIdLo: Long,
 *     name: String,
 *     kind: SpanKind,
 *     attributes: Attributes,
 *     links: Seq[SpanLink]
 *   ): SamplingResult = if (name == "GET /health") drop else record
 *
 *   def description: String = "HealthCheckSampler"
 * }
 *
 * val tracer = TracerProvider.builder.setSampler(HealthCheckSampler).build().get("api")
 * tracer.span("GET /health")(_.isRecording) // false
 * tracer.span("GET /orders")(_.isRecording) // true
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/tracing/sampler/ Sampler]]
 */
trait Sampler {

  /**
   * Decides about a new span.
   *
   * @param parentContext
   *   the context of the current span, or `None` outside every span
   * @param traceIdHi
   *   the high 64 bits of the trace ID
   * @param traceIdLo
   *   the low 64 bits of the trace ID
   * @param name
   *   the name of the span being created
   * @param kind
   *   the kind of the span being created
   * @param attributes
   *   the attributes passed when the span is opened
   * @param links
   *   the links of the span being created; always empty when `Tracer#span`
   *   calls it
   * @return
   *   the decision, extra attributes and trace state
   */
  def shouldSample(
    parentContext: Option[SpanContext],
    traceIdHi: Long,
    traceIdLo: Long,
    name: String,
    kind: SpanKind,
    attributes: Attributes,
    links: Seq[SpanLink]
  ): SamplingResult

  /** Returns a short name for the sampler, for logs and debugging. */
  def description: String
}

/**
 * Records and samples every span: the default of `TracerProvider.builder`.
 */
object AlwaysOnSampler extends Sampler {

  private val result: SamplingResult =
    SamplingResult(SamplingDecision.RecordAndSample, Attributes.empty, "")

  /** Returns `RecordAndSample`, with no attributes and an empty trace state. */
  def shouldSample(
    parentContext: Option[SpanContext],
    traceIdHi: Long,
    traceIdLo: Long,
    name: String,
    kind: SpanKind,
    attributes: Attributes,
    links: Seq[SpanLink]
  ): SamplingResult = result

  /** Returns `"AlwaysOnSampler"`. */
  val description: String = "AlwaysOnSampler"
}

/**
 * Drops every span; `trace.removeAll()` installs a provider that uses it.
 */
object AlwaysOffSampler extends Sampler {

  private val result: SamplingResult =
    SamplingResult(SamplingDecision.Drop, Attributes.empty, "")

  /** Returns `Drop`. */
  def shouldSample(
    parentContext: Option[SpanContext],
    traceIdHi: Long,
    traceIdLo: Long,
    name: String,
    kind: SpanKind,
    attributes: Attributes,
    links: Seq[SpanLink]
  ): SamplingResult = result

  /** Returns `"AlwaysOffSampler"`. */
  val description: String = "AlwaysOffSampler"
}

/**
 * Follows the decision of the parent span. With a valid parent, it returns
 * `RecordAndSample` when the parent is sampled and `Drop` when it is not, with
 * the parent's trace state and no attributes. With no parent, or an invalid
 * one, it asks `root`. `ParentBasedSampler(AlwaysOnSampler)` samples new traces
 * and honours the decision of an upstream service.
 *
 * @param root
 *   the sampler for spans without a valid parent
 */
final case class ParentBasedSampler(root: Sampler) extends Sampler {

  /**
   * Follows the parent's sampled flag, or asks `root` without a valid parent.
   */
  def shouldSample(
    parentContext: Option[SpanContext],
    traceIdHi: Long,
    traceIdLo: Long,
    name: String,
    kind: SpanKind,
    attributes: Attributes,
    links: Seq[SpanLink]
  ): SamplingResult =
    parentContext match {
      case None =>
        root.shouldSample(parentContext, traceIdHi, traceIdLo, name, kind, attributes, links)
      case Some(parent) if parent.isValid =>
        val ts = parent.traceState
        if (parent.isSampled)
          SamplingResult(SamplingDecision.RecordAndSample, Attributes.empty, ts)
        else
          SamplingResult(SamplingDecision.Drop, Attributes.empty, ts)
      case Some(_) =>
        root.shouldSample(parentContext, traceIdHi, traceIdLo, name, kind, attributes, links)
    }

  /**
   * Returns `ParentBasedSampler(root=...)` with the root's description, such as
   * `ParentBasedSampler(root=AlwaysOnSampler)`.
   */
  val description: String = s"ParentBasedSampler(root=${root.description})"
}
