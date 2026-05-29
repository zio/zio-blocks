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
 * Receives span lifecycle events from a [[Tracer]].
 *
 * Processors can export spans, buffer them, or perform in-memory inspection.
 */
trait SpanProcessor extends AutoCloseable {
  /**
   * Called when a span is started.
   */
  def onStart(span: Span): Unit

  /**
   * Called when a span has ended and immutable span data is available.
   */
  def onEnd(spanData: SpanData): Unit

  /**
   * Shuts the processor down and releases any owned resources.
   */
  def shutdown(): Unit

  /**
   * Flushes any buffered span data.
   */
  def forceFlush(): Unit

  override def close(): Unit = shutdown()
}

object SpanProcessor {

  /**
   * A processor that ignores all lifecycle events.
   */
  val noop: SpanProcessor = new SpanProcessor {
    def onStart(span: Span): Unit       = ()
    def onEnd(spanData: SpanData): Unit = ()
    def shutdown(): Unit                = ()
    def forceFlush(): Unit              = ()
  }
}
