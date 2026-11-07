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

package zio.blocks.telemetry.otel

import zio.blocks.telemetry._
import zio.blocks.typeid.IsNominalType

/**
 * A snapshot of the active span's context, as a value that can travel in a
 * `zio.blocks.context.Context`. Code that passes its dependencies through
 * `Context[R]` instead of reading a `ContextStorage` carries the trace this
 * way: take a snapshot with `OtelContext.current` where the span is active, add
 * it to the context, and read it with `get[OtelContext]` further down.
 *
 * {{{
 * import zio.blocks.telemetry._
 * import zio.blocks.context.Context
 *
 * val storage = ContextStorage.create[Option[SpanContext]](None)
 * val tracer  = TracerProvider.builder.setContextStorage(storage).build().get("checkout")
 *
 * val (spanId, carried) = tracer.span("place-order") { span =>
 *   (span.spanContext.spanId, Context(OtelContext.current(storage)))
 * }
 * carried.get[OtelContext].spanContext.map(_.spanId == spanId) // Some(true)
 * OtelContext.current(storage) // OtelContext(None)
 * }}}
 *
 * @param spanContext
 *   the context of the span that was active, or `None` when none was
 * @see
 *   [[https://zioblocks.com/reference/telemetry/otel/ OTLP Export]]
 */
final case class OtelContext(spanContext: Option[SpanContext])

/**
 * Holds `current` and `withSpan`, and the `IsNominalType` instance that lets an
 * `OtelContext` be a `Context` key.
 */
object OtelContext {

  /** Lets an `OtelContext` be stored in, and read from, a `Context`. */
  implicit val isNominalType: IsNominalType[OtelContext] =
    IsNominalType.derived[OtelContext]

  /**
   * Returns a snapshot of the span context in `storage`. To see the spans a
   * tracer opens, give the same storage to
   * `TracerProviderBuilder#setContextStorage`.
   */
  def current(storage: ContextStorage[Option[SpanContext]]): OtelContext =
    OtelContext(storage.get())

  /**
   * Runs `f` with `span`'s context as the value of `storage`, and restores the
   * previous value afterwards, also when `f` throws. It sets only `storage`: a
   * tracer that uses another storage does not see `span` as its current span.
   *
   * {{{
   * import zio.blocks.telemetry._
   *
   * val storage = ContextStorage.create[Option[SpanContext]](None)
   * val tracer  = trace.get("checkout")
   *
   * val inside = tracer.span("charge-card") { span =>
   *   OtelContext.withSpan(span, storage) {
   *     OtelContext.current(storage).spanContext.exists(_.spanId == span.spanContext.spanId)
   *   }
   * }
   * // inside is true
   * storage.get() // None
   * }}}
   */
  def withSpan[A](span: Span, storage: ContextStorage[Option[SpanContext]])(f: => A): A =
    storage.scoped(Some(span.spanContext))(f)
}
