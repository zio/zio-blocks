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
import zio.blocks.mediatype.MediaTypes

/**
 * Materialized, immutable HTTP message body backed by a `Chunk[Byte]`.
 *
 * The underlying `data` chunk provides structural equality and efficient
 * slicing without defensive copies.
 */
final class Body private (val data: Chunk[Byte], val contentType: ContentType) {

  override def equals(that: Any): Boolean = that match {
    case b: Body => data == b.data && contentType == b.contentType
    case _       => false
  }

  override def hashCode: Int = data.hashCode * 31 + contentType.hashCode

  def asString(charset: Charset = Charset.UTF8): String = new String(data.toArray, charset.name)

  def toArray: Array[Byte] = data.toArray

  def length: Int = data.length

  def isEmpty: Boolean = data.isEmpty

  def nonEmpty: Boolean = data.nonEmpty

  override def toString: String = s"Body(length=${data.length}, contentType=$contentType)"
}

object Body {

  val empty: Body = new Body(Chunk.empty, ContentType.`application/octet-stream`)

  def fromArray(bytes: Array[Byte], contentType: ContentType = ContentType.`application/octet-stream`): Body =
    new Body(Chunk.fromArray(bytes), contentType)

  def fromChunk(chunk: Chunk[Byte], contentType: ContentType = ContentType.`application/octet-stream`): Body =
    new Body(chunk, contentType)

  def fromString(s: String, charset: Charset = Charset.UTF8): Body = {
    val bytes = s.getBytes(charset.name)
    val ct    = ContentType(MediaTypes.text.`plain`, charset = Some(charset))
    new Body(Chunk.fromArray(bytes), ct)
  }
}
