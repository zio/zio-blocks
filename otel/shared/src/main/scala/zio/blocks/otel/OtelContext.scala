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

import zio.blocks.typeid.IsNominalType

/**
 * Bridges the otel module's ContextStorage with zio-blocks Context[R].
 *
 * OtelContext captures the current span context from a ContextStorage, enabling
 * type-safe context threading via `Context[R & OtelContext]`.
 *
 * @param spanContext
 *   the active span context, if any
 */
final case class OtelContext(spanContext: Option[SpanContext])

object OtelContext {

  implicit val isNominalType: IsNominalType[OtelContext] =
    IsNominalType.derived[OtelContext]

  /**
   * Snapshots the current span context from the given storage.
   */
  def current(storage: ContextStorage[Option[SpanContext]]): OtelContext =
    OtelContext(storage.get())

  /**
   * Executes `f` with the given span's context set as current in storage.
   * Restores the previous context afterward (even if `f` throws).
   */
  def withSpan[A](span: Span, storage: ContextStorage[Option[SpanContext]])(f: => A): A =
    storage.scoped(Some(span.spanContext))(f)
}
