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

/**
 * Receives the spans of a [[TracerProvider]]: `onStart` when a recorded span
 * begins, and `onEnd` with its snapshot when it ends. Implement it to export
 * spans, buffer them, or keep them for a test, and add it with
 * `TracerProviderBuilder#addSpanProcessor`.
 *
 * The provider calls its processors on the thread that runs the span, in the
 * order they were added, so `onEnd` adds to that span's latency: hand the data
 * to a queue and export elsewhere. Spans the sampler drops, and spans started
 * with a [[SpanBuilder]], never reach a processor.
 *
 * {{{
 * import scala.collection.mutable.ListBuffer
 *
 * val finished = ListBuffer.empty[SpanData]
 * val inMemory = new SpanProcessor {
 *   def onStart(span: Span): Unit = ()
 *   def onEnd(spanData: SpanData): Unit = finished += spanData
 *   def shutdown(): Unit = ()
 *   def forceFlush(): Unit = ()
 * }
 * val tracer = TracerProvider.builder.addSpanProcessor(inMemory).build().get("checkout")
 *
 * tracer.span("reserve-stock")(_ => ())
 *
 * finished.map(_.name) // ListBuffer(reserve-stock)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/tracing/span-processor/ SpanProcessor]]
 */
trait SpanProcessor extends AutoCloseable {

  /**
   * Called with the live span before the block of `Tracer#span` runs, for every
   * span the sampler records, with or without the sampled flag.
   */
  def onStart(span: Span): Unit

  /**
   * Called with the immutable snapshot of a span after it ends, on the thread
   * that ran it.
   */
  def onEnd(spanData: SpanData): Unit

  /**
   * Called by `TracerProvider#shutdown`: export what is buffered and release
   * resources. The provider still calls `onStart` and `onEnd` afterwards.
   */
  def shutdown(): Unit

  /** Called by `TracerProvider#forceFlush`: export what is buffered now. */
  def forceFlush(): Unit

  override def close(): Unit = shutdown()
}

/** Holds [[SpanProcessor.noop]]. */
object SpanProcessor {

  /** A processor whose four methods do nothing. */
  val noop: SpanProcessor = new SpanProcessor {
    def onStart(span: Span): Unit       = ()
    def onEnd(spanData: SpanData): Unit = ()
    def shutdown(): Unit                = ()
    def forceFlush(): Unit              = ()
  }
}
