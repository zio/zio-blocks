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

package zio.blocks.schema.yaml

import zio.blocks.schema.DynamicOptic

class YamlError(
  val spans: List[DynamicOptic.Node],
  message: String,
  val line: Option[Int] = None,
  val column: Option[Int] = None
) extends Throwable(message, null, false, false) {
  override def getMessage: String = (line, column) match {
    case (Some(l), Some(c)) => s"$message (at line $l, column $c)"
    case (Some(l), None)    => s"$message (at line $l)"
    case _                  => message
  }

  def path: DynamicOptic = DynamicOptic(spans.reverse.toIndexedSeq)

  def atSpan(span: DynamicOptic.Node): YamlError =
    new YamlError(span :: spans, message, line, column)
}

object YamlError {

  def apply(message: String): YamlError = new YamlError(Nil, message)

  def apply(message: String, spans: List[DynamicOptic.Node]): YamlError =
    new YamlError(spans, message)

  def apply(message: String, line: Int, column: Int): YamlError =
    new YamlError(Nil, message, Some(line), Some(column))

  def parseError(message: String, line: Int, column: Int): YamlError =
    new YamlError(Nil, s"Parse error: $message", Some(line), Some(column))

  def validationError(message: String): YamlError =
    new YamlError(Nil, s"Validation error: $message")

  def encodingError(message: String): YamlError =
    new YamlError(Nil, s"Encoding error: $message")
}
