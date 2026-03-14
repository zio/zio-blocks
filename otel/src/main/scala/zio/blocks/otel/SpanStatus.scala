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
 * Represents the completion status of a span.
 *
 * Follows OpenTelemetry span status specification.
 */
sealed trait SpanStatus

object SpanStatus {

  /**
   * Unset status - the default status, meaning the span status was not set.
   *
   * The span status can be updated by the instrumentation.
   */
  case object Unset extends SpanStatus

  /**
   * Ok status - the span completed successfully.
   *
   * The operation associated with the span completed without unhandled errors.
   */
  case object Ok extends SpanStatus

  /**
   * Error status - the span ended due to an error.
   *
   * @param description
   *   human-readable error message describing what went wrong
   */
  final case class Error(description: String) extends SpanStatus
}
