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

package zio.http

import zio.blocks.chunk.Chunk

sealed trait FormField {
  def name: String
}

object FormField {

  final case class Simple(name: String, value: String) extends FormField

  final case class Text(
    name: String,
    value: String,
    contentType: Option[ContentType] = None,
    filename: Option[String] = None
  ) extends FormField

  final case class Binary(
    name: String,
    data: Chunk[Byte],
    contentType: ContentType,
    filename: Option[String] = None
  ) extends FormField
}
