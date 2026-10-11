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

final case class SourceLocation(
  filePath: String,
  namespace: String,
  methodName: String,
  lineNumber: Int
)

object SourceLocation {

  /**
   * Stable OpenTelemetry `code.function.name`: the fully-qualified function
   * name without arguments (`namespace.methodName`). Single source of truth,
   * evaluated at macro-expansion time (spliced as a literal, so hot log calls
   * pay no concatenation) and reused by the programmatic `log` helpers.
   */
  private[telemetry] def qualifiedFunctionName(namespace: String, methodName: String): String =
    if (namespace == null || namespace.isEmpty) methodName
    else if (methodName == null || methodName.isEmpty) namespace
    else namespace + "." + methodName
}
