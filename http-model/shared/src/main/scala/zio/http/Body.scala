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
import zio.blocks.async.Async
import zio.blocks.async._
import zio.blocks.mediatype.MediaTypes
import zio.blocks.streams.Stream

/**
 * The body of an HTTP message: a byte stream and its [[ContentType]]. A body
 * built from bytes or a string ([[Body.fromChunk]], [[Body.fromArray]],
 * [[Body.fromString]]) knows its bytes and length up front; a body built with
 * [[Body.fromStream]] knows them only if the stream does, and reading it
 * consumes the stream.
 *
 * Read the bytes with `toChunkAsync`, `toArrayAsync` or `textAsync`, which work
 * on the JVM and on Scala.js. The synchronous twins (`toChunk`, `toArray`,
 * `text`, ...) block until the stream ends, and on Scala.js they throw
 * `IllegalStateException` for a stream that has to wait for input.
 *
 * {{{
 * val body = Body.fromString("order 42 shipped")
 *
 * body.contentType.render // text/plain; charset=UTF-8
 * body.length             // Some(16)
 * body.text               // order 42 shipped
 * }}}
 *
 * @param stream
 *   the bytes of the body
 * @param contentType
 *   the content type of the bytes, which `Request#body` and `Response#body`
 *   copy into the `content-type` header
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#body Body]]
 */
final class Body private (val stream: Stream[Nothing, Byte], val contentType: ContentType) {

  /**
   * Two bodies are equal when they are the same instance, or when they have
   * equal content types and both know their bytes up front and those bytes are
   * equal. Two bodies built with [[Body.fromStream]] from streams without known
   * bytes are equal only to themselves.
   */
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

  /** A hash consistent with `equals`. */
  override def hashCode: Int = {
    val dataHash = stream.knownChunk.map(_.hashCode).getOrElse(System.identityHashCode(stream))
    dataHash * 31 + contentType.hashCode
  }

  /** Returns the body content as a `Stream[Nothing, Byte]`. */
  def toStream: Stream[Nothing, Byte] = stream

  /**
   * Returns every byte of the body. A body that knows its bytes returns them at
   * once; otherwise the stream is run to the end, blocking the calling thread
   * while it waits for input, and on Scala.js such a wait throws
   * `IllegalStateException`. Prefer [[toChunkAsync]] for a stream that can
   * wait.
   */
  def toChunk: Chunk[Byte] = stream.knownChunk.getOrElse(stream.runCollectAsync.block.getOrElse(Chunk.empty))

  /**
   * Returns every byte of the body in a new array, which the caller may change
   * without affecting the body. It blocks as [[toChunk]] does.
   */
  def toArray: Array[Byte] = toChunk.toArray

  /**
   * Returns every byte of the body as an `Async`, which completes at once for a
   * body that knows its bytes and otherwise when the stream ends.
   *
   * {{{
   * import zio.blocks.streams.Stream
   *
   * val body = Body.fromStream(Stream.fromIterable(List[Byte](104, 105)))
   *
   * body.length // None
   * val bytes = body.toChunkAsync // an Async that completes with Chunk(104,105)
   * }}}
   */
  def toChunkAsync: Async[Chunk[Byte]] =
    stream.knownChunk match {
      case Some(chunk) => Async.succeed(chunk)
      case None        => stream.runCollectAsync.map(_.getOrElse(Chunk.empty))
    }

  /** Returns every byte of the body as an `Async` of a new array. */
  def toArrayAsync: Async[Array[Byte]] = toChunkAsync.map(_.toArray)

  /**
   * Returns the known length of this body in bytes, or `None` if the length is
   * not known without consuming the stream.
   */
  def length: Option[Long] = stream.knownLength

  /**
   * Whether the body is known to have zero bytes. A stream body of unknown
   * length gives `false` here and in [[nonEmpty]].
   */
  def isEmpty: Boolean = stream.knownLength.contains(0L)

  /**
   * Whether the body is known to have at least one byte; `false` when the
   * length is unknown.
   */
  def nonEmpty: Boolean = stream.knownLength.exists(_ > 0L)

  /**
   * Decodes every byte with `charset`, UTF-8 by default, ignoring the charset
   * of the content type; use [[text]] to follow the content type. It blocks as
   * [[toChunk]] does.
   */
  def asString(charset: Charset = Charset.UTF8): String = new String(toArray, charset.name)

  /**
   * Decodes every byte with the charset of the content type, or UTF-8 when the
   * content type names none. It blocks as [[toChunk]] does.
   *
   * {{{
   * val body = Body.fromString("café", Charset.ISO_8859_1)
   *
   * body.asStringFromContentType // café
   * body.asString().length       // 4, ending in U+FFFD: the byte 0xE9 is not valid UTF-8
   * }}}
   */
  def asStringFromContentType: String = asString(contentType.charset.getOrElse(Charset.UTF8))

  /** Alias for [[asStringFromContentType]]. */
  def text: String = asStringFromContentType

  /**
   * Decodes every byte with `charset`, UTF-8 by default, as an `Async`,
   * ignoring the charset of the content type.
   */
  def asStringAsync(charset: Charset = Charset.UTF8): Async[String] =
    toArrayAsync.map(bytes => new String(bytes, charset.name))

  /**
   * Decodes every byte with the charset of the content type, or UTF-8 when it
   * names none, as an `Async`.
   */
  def asStringFromContentTypeAsync: Async[String] =
    asStringAsync(contentType.charset.getOrElse(Charset.UTF8))

  /** Alias for [[asStringFromContentTypeAsync]]. */
  def textAsync: Async[String] = asStringFromContentTypeAsync

  /**
   * Renders the length (or `unknown`) and the content type, not the bytes.
   */
  override def toString: String =
    s"Body(length=${length.map(_.toString).getOrElse("unknown")}, contentType=$contentType)"
}

/** Constructors for [[Body]]. */
object Body {

  /**
   * An empty body with no bytes and `application/octet-stream` content type.
   */
  val empty: Body = new Body(Stream.fromChunk(Chunk.empty[Byte]), ContentType.`application/octet-stream`)

  /**
   * Creates a body holding `chunk`, `application/octet-stream` unless
   * `contentType` says otherwise. The body knows its bytes and length, so
   * `length` is defined and `equals` compares the bytes.
   *
   * {{{
   * import zio.blocks.chunk.Chunk
   *
   * val body = Body.fromChunk(Chunk[Byte](1, 2, 3), ContentType.`application/json`)
   *
   * body.length             // Some(3)
   * body.contentType.render // application/json
   * }}}
   */
  def fromChunk(chunk: Chunk[Byte], contentType: ContentType = ContentType.`application/octet-stream`): Body =
    new Body(Stream.fromChunk(chunk), contentType)

  /**
   * Creates a body that wraps `bytes` without copying them,
   * `application/octet-stream` unless `contentType` says otherwise. A later
   * change to the array changes the body too, so pass an array nothing else
   * writes to.
   *
   * {{{
   * val bytes = Array[Byte](1, 2, 3)
   * val body  = Body.fromArray(bytes)
   * bytes(0) = 9
   *
   * body.toChunk // Chunk(9,2,3)
   * }}}
   */
  def fromArray(bytes: Array[Byte], contentType: ContentType = ContentType.`application/octet-stream`): Body =
    new Body(Stream.fromChunk(Chunk.fromArray(bytes)), contentType)

  /**
   * Creates a body holding `s` encoded with `charset` (UTF-8 by default), with
   * content type `text/plain` and that charset. For JSON or HTML, pass the
   * encoded bytes to [[fromChunk]] or [[fromArray]] with the right content
   * type, or replace the header afterwards.
   *
   * {{{
   * Body.fromString("café").length                                // Some(5)
   * Body.fromString("café", Charset.ISO_8859_1).contentType.render // text/plain; charset=ISO-8859-1
   * }}}
   */
  def fromString(s: String, charset: Charset = Charset.UTF8): Body = {
    val bytes = s.getBytes(charset.name)
    val ct    = ContentType(MediaTypes.text.`plain`, charset = Some(charset))
    new Body(Stream.fromChunk(Chunk.fromArray(bytes)), ct)
  }

  /**
   * Creates a body from `stream`, `application/octet-stream` unless
   * `contentType` says otherwise. The body knows its bytes or length only when
   * the stream does (`knownChunk`, `knownLength`); otherwise `length` is `None`
   * and `equals` compares by instance. Reading the bytes runs the stream.
   */
  def fromStream(
    stream: Stream[Nothing, Byte],
    contentType: ContentType = ContentType.`application/octet-stream`
  ): Body =
    new Body(stream, contentType)
}
