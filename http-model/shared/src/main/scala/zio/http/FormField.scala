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

/**
 * One field of a multipart form: a [[FormField.Simple]] name and value, a
 * [[FormField.Text]] with optional content type and file name, or a
 * [[FormField.Binary]] with raw bytes.
 */
sealed trait FormField {

  /** The field name. */
  def name: String
}

/** The [[FormField]] variants. */
object FormField {

  /** A plain name and text value, without a content type or file name. */
  final case class Simple(name: String, value: String) extends FormField

  /**
   * A text field, with an optional content type and an optional file name.
   */
  final case class Text(
    name: String,
    value: String,
    contentType: Option[ContentType] = None,
    filename: Option[String] = None
  ) extends FormField

  /**
   * A field holding raw bytes, with a required content type and an optional
   * file name.
   */
  final case class Binary(
    name: String,
    data: Chunk[Byte],
    contentType: ContentType,
    filename: Option[String] = None
  ) extends FormField
}
