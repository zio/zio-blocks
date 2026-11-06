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

package zio.http.htmx

import scala.concurrent.duration.FiniteDuration

/** Validation, duration and JSON helpers shared by the htmx value types. */
private[htmx] object HtmxSupport {

  /**
   * Returns `value` trimmed, or throws
   * `IllegalArgumentException(label + " must be non-empty")` if it is blank.
   */
  def requireNonBlank(value: String, label: String): String = {
    val trimmed = value.trim
    if (trimmed.isEmpty) throw new IllegalArgumentException(label + " must be non-empty")
    else trimmed
  }

  /**
   * Renders whole seconds as `<n>s` and anything else as `<n>ms`, after
   * truncating to milliseconds.
   */
  def renderDuration(duration: FiniteDuration): String = {
    val millis = duration.toMillis
    if (millis % 1000 == 0) (millis / 1000).toString + "s"
    else millis.toString + "ms"
  }

  /**
   * Parses `<n>ms`, `<n>s` or a bare number of milliseconds, where `n` is a
   * non-negative whole number.
   */
  def parseDuration(value: String): Either[String, FiniteDuration] =
    if (value.endsWith("ms")) {
      val raw = value.substring(0, value.length - 2)
      parseNonNegativeLong(raw).map(FiniteDuration(_, scala.concurrent.duration.MILLISECONDS))
    } else if (value.endsWith("s")) {
      val raw = value.substring(0, value.length - 1)
      parseNonNegativeLong(raw).map(FiniteDuration(_, scala.concurrent.duration.SECONDS))
    } else if (
      value.forall(_.isDigit) || (value.startsWith("-") && value.drop(1).nonEmpty && value.drop(1).forall(_.isDigit))
    ) {
      parseNonNegativeLong(value).map(FiniteDuration(_, scala.concurrent.duration.MILLISECONDS))
    } else Left("Unsupported HTMX duration: " + value)

  /**
   * Returns `duration`, or throws
   * `IllegalArgumentException(label + " must be non-negative")` if it is
   * negative.
   */
  def requireNonNegativeDuration(duration: FiniteDuration, label: String): FiniteDuration =
    if (duration < DurationZero) throw new IllegalArgumentException(label + " must be non-negative")
    else duration

  private val DurationZero = FiniteDuration(0, scala.concurrent.duration.MILLISECONDS)

  private def parseNonNegativeLong(value: String): Either[String, Long] =
    try {
      val parsed = value.toLong
      if (parsed < 0) Left("HTMX duration must be non-negative: " + value)
      else Right(parsed)
    } catch {
      case _: NumberFormatException => Left("Invalid number: " + value)
    }

  /** Returns `value` as a quoted, escaped JSON string literal. */
  def quoteJson(value: String): String =
    _root_.zio.blocks.schema.json.Json.String(value).print
}
