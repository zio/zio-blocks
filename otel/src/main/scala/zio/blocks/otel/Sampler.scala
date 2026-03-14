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

package zio.blocks.otel

/**
 * Represents a sampling decision made by a `Sampler`.
 */
sealed trait SamplingDecision

object SamplingDecision {

  /**
   * The span will not be recorded and all events and attributes will be
   * dropped.
   */
  case object Drop extends SamplingDecision

  /**
   * The span will be recorded but the sampled flag will not be set, so it will
   * not be exported.
   */
  case object RecordOnly extends SamplingDecision

  /**
   * The span will be recorded and the sampled flag will be set, so it will be
   * exported.
   */
  case object RecordAndSample extends SamplingDecision
}

/**
 * The result of a sampling decision, including the decision itself, any
 * additional attributes to add to the span, and the trace state.
 *
 * @param decision
 *   the sampling decision
 * @param attributes
 *   additional attributes to add to the span
 * @param traceState
 *   the trace state string to propagate
 */
final case class SamplingResult(
  decision: SamplingDecision,
  attributes: Attributes,
  traceState: String
)

/**
 * A sampler decides whether a span should be recorded and/or sampled.
 *
 * Samplers are invoked during span creation to determine the sampling decision
 * for the new span.
 */
trait Sampler {

  /**
   * Determines whether a span should be sampled.
   *
   * @param parentContext
   *   the parent span context, if any
   * @param traceId
   *   the trace ID of the span being created
   * @param name
   *   the name of the span being created
   * @param kind
   *   the kind of the span being created
   * @param attributes
   *   the initial attributes of the span being created
   * @param links
   *   the links associated with the span being created
   * @return
   *   the sampling result
   */
  def shouldSample(
    parentContext: Option[SpanContext],
    traceId: TraceId,
    name: String,
    kind: SpanKind,
    attributes: Attributes,
    links: Seq[SpanLink]
  ): SamplingResult

  /**
   * Returns a human-readable description of this sampler.
   */
  def description: String
}

/**
 * A sampler that always records and samples every span.
 */
object AlwaysOnSampler extends Sampler {

  private val result: SamplingResult =
    SamplingResult(SamplingDecision.RecordAndSample, Attributes.empty, "")

  def shouldSample(
    parentContext: Option[SpanContext],
    traceId: TraceId,
    name: String,
    kind: SpanKind,
    attributes: Attributes,
    links: Seq[SpanLink]
  ): SamplingResult = result

  val description: String = "AlwaysOnSampler"
}

/**
 * A sampler that always drops every span.
 */
object AlwaysOffSampler extends Sampler {

  private val result: SamplingResult =
    SamplingResult(SamplingDecision.Drop, Attributes.empty, "")

  def shouldSample(
    parentContext: Option[SpanContext],
    traceId: TraceId,
    name: String,
    kind: SpanKind,
    attributes: Attributes,
    links: Seq[SpanLink]
  ): SamplingResult = result

  val description: String = "AlwaysOffSampler"
}

/**
 * A sampler that delegates to the parent span's sampling decision.
 *
 * If no parent exists, delegates to the provided root sampler. If a parent
 * exists, the decision follows the parent's sampled flag.
 *
 * @param root
 *   the sampler to use when there is no parent span
 */
final case class ParentBasedSampler(root: Sampler) extends Sampler {

  private val sampledResult: SamplingResult =
    SamplingResult(SamplingDecision.RecordAndSample, Attributes.empty, "")

  private val droppedResult: SamplingResult =
    SamplingResult(SamplingDecision.Drop, Attributes.empty, "")

  def shouldSample(
    parentContext: Option[SpanContext],
    traceId: TraceId,
    name: String,
    kind: SpanKind,
    attributes: Attributes,
    links: Seq[SpanLink]
  ): SamplingResult =
    parentContext match {
      case None         => root.shouldSample(parentContext, traceId, name, kind, attributes, links)
      case Some(parent) => if (parent.isSampled) sampledResult else droppedResult
    }

  val description: String = s"ParentBasedSampler(root=${root.description})"
}
