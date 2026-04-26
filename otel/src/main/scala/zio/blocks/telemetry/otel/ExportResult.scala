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

sealed trait ExportResult

object ExportResult {
  private val retryableStatusCodes: Set[Int] = Set(429, 502, 503, 504)

  def fromHttpResponse(response: HttpResponse): ExportResult =
    if (response.statusCode >= 200 && response.statusCode < 300) ExportResult.Success
    else if (retryableStatusCodes.contains(response.statusCode))
      ExportResult.Failure(retryable = true, message = "HTTP " + response.statusCode)
    else ExportResult.Failure(retryable = false, message = "HTTP " + response.statusCode)

  case object Success                                           extends ExportResult
  final case class Failure(retryable: Boolean, message: String) extends ExportResult
}
