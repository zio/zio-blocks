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
import zio.blocks.streams.Stream

/**
 * HTTP message body backed by a `Stream[Nothing, Byte]`.
 *
 * Body is a thin wrapper around a pull-based byte stream and a content type.
 * All optimization intelligence (known chunk, known length) lives in the
 * underlying `Stream` via its `knownChunk` and `knownLength` metadata APIs.
 *
 * @param stream
 *   the underlying byte stream
 * @param contentType
 *   the MIME content type of this body
 */
final class Body private (val stream: Stream[Nothing, Byte], val contentType: ContentType) {

  override def equals(that: Any): Boolean = that match {
    case b: Body =>
      (this eq b) || (contentType == b.contentType && {
        (stream.knownChunk, b.stream.knownChunk) match {
          case (Some(a), Some(b)) => a == b
          case _                  => false
        }
      })
    case _ => false
  }

  override def hashCode: Int = {
    val dataHash = stream.knownChunk.map(_.hashCode).getOrElse(System.identityHashCode(stream))
    dataHash * 31 + contentType.hashCode
  }

  /** Returns the body content as a `Stream[Nothing, Byte]`. */
  def toStream: Stream[Nothing, Byte] = stream

  /**
   * Materializes the entire stream into a `Chunk[Byte]`.
   *
   * If the stream has a known chunk (e.g. created via `Stream.fromChunk`), this
   * returns it directly without running the stream. Otherwise, the stream is
   * collected.
   */
  def toChunk: Chunk[Byte] =
    stream.knownChunk.getOrElse(stream.runCollect.getOrElse(Chunk.empty))

  /** Materializes the entire stream into an `Array[Byte]`. */
  def toArray: Array[Byte] = toChunk.toArray

  /**
   * Returns the known length of this body in bytes, or `None` if the length is
   * not known without consuming the stream.
   */
  def length: Option[Long] = stream.knownLength

  /** Returns `true` if this body is known to be empty (zero bytes). */
  def isEmpty: Boolean = stream.knownLength.contains(0L)

  /** Returns `true` if this body is known to be non-empty. */
  def nonEmpty: Boolean = stream.knownLength.exists(_ > 0L)

  /**
   * Decodes the body bytes into a `String` using the given charset.
   *
   * @param charset
   *   the charset to use for decoding (defaults to UTF-8)
   */
  def asString(charset: Charset = Charset.UTF8): String =
    new String(toArray, charset.name)

  override def toString: String =
    s"Body(length=${length.map(_.toString).getOrElse("unknown")}, contentType=$contentType)"
}

object Body {

  /**
   * An empty body with no bytes and `application/octet-stream` content type.
   */
  val empty: Body = new Body(Stream.fromChunk(Chunk.empty[Byte]), ContentType.`application/octet-stream`)

  /**
   * Creates a body from a `Chunk[Byte]`.
   *
   * The resulting body has a known chunk and known length, enabling structural
   * equality and O(1) length queries.
   */
  def fromChunk(chunk: Chunk[Byte], contentType: ContentType = ContentType.`application/octet-stream`): Body =
    new Body(Stream.fromChunk(chunk), contentType)

  /**
   * Creates a body from a byte array.
   *
   * The array is wrapped via `Chunk.fromArray` without a defensive copy. The
   * resulting body has a known chunk and known length.
   */
  def fromArray(bytes: Array[Byte], contentType: ContentType = ContentType.`application/octet-stream`): Body =
    new Body(Stream.fromChunk(Chunk.fromArray(bytes)), contentType)

  /**
   * Creates a body from a string, encoding with the given charset.
   *
   * Sets the content type to `text/plain` with the specified charset.
   */
  def fromString(s: String, charset: Charset = Charset.UTF8): Body = {
    val bytes = s.getBytes(charset.name)
    val ct    = ContentType(MediaTypes.text.`plain`, charset = Some(charset))
    new Body(Stream.fromChunk(Chunk.fromArray(bytes)), ct)
  }

  /**
   * Creates a body from an arbitrary byte stream.
   *
   * The stream's `knownChunk` and `knownLength` metadata determine whether
   * structural equality and O(1) length queries are available.
   */
  def fromStream(
    stream: Stream[Nothing, Byte],
    contentType: ContentType = ContentType.`application/octet-stream`
  ): Body =
    new Body(stream, contentType)
}
