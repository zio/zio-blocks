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
 * The outcome of a span, as the OpenTelemetry specification defines it. Every
 * span starts as `Unset`; set `Ok` or `Error` with `Span#setStatus` before it
 * ends. Nothing sets it for you, not even when the block of `trace.span`
 * throws.
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/tracing/span-status/ SpanStatus]]
 */
sealed trait SpanStatus

/** The three statuses. */
object SpanStatus {

  /** No status was set: the default of every span. */
  case object Unset extends SpanStatus

  /** The operation succeeded. */
  case object Ok extends SpanStatus

  /**
   * The operation failed.
   *
   * @param description
   *   what went wrong, such as `gateway timeout`
   */
  final case class Error(description: String) extends SpanStatus
}
