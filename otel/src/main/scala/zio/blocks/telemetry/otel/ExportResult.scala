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

/**
 * The outcome of one export request. [[ExportResult.Success]] means the
 * collector accepted the payload. [[ExportResult.Failure]] carries a short
 * message and whether sending the same payload again can succeed: yes for a
 * throttled or overloaded collector (`429`, `502`, `503`, `504`), no for one
 * that rejected it (any other status, such as `400`). `fromHttpResponse` makes
 * this choice from a status code.
 *
 * {{{
 * def classify(status: Int): ExportResult =
 *   ExportResult.fromHttpResponse(HttpResponse(status, Array.emptyByteArray, Map.empty))
 *
 * classify(200) // Success
 * classify(503) // Failure(true,HTTP 503)
 * classify(500) // Failure(false,HTTP 500)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/otel/custom-exporter/ Building an Exporter]]
 */
sealed trait ExportResult

/** Holds the two outcomes and `fromHttpResponse`. */
object ExportResult {
  private val retryableStatusCodes: Set[Int] = Set(429, 502, 503, 504)

  /**
   * Classifies an HTTP response: `Success` for any `2xx` status, a retryable
   * `Failure` for `429`, `502`, `503` and `504`, and a non-retryable one for
   * every other status, including `500` and redirects. The message is
   * `HTTP <status>`; the body and a `Retry-After` header are not read.
   */
  def fromHttpResponse(response: HttpResponse): ExportResult =
    if (response.statusCode >= 200 && response.statusCode < 300) ExportResult.Success
    else if (retryableStatusCodes.contains(response.statusCode))
      ExportResult.Failure(retryable = true, message = "HTTP " + response.statusCode)
    else ExportResult.Failure(retryable = false, message = "HTTP " + response.statusCode)

  /** The collector accepted the payload. */
  case object Success extends ExportResult

  /**
   * The export failed.
   *
   * @param retryable
   *   whether sending the same payload again can succeed
   * @param message
   *   a short description, such as `HTTP 503`
   */
  final case class Failure(retryable: Boolean, message: String) extends ExportResult
}
