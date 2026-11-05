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

package zio.http.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.Schema
import zio.http.{Header, Headers}

/**
 * Typed reads of single headers. `header`, `headerAll` and `headerOrElse` look
 * a name up in [[zio.http.Headers]] and decode its value with the `Schema[T]`
 * in scope, or with a [[zio.http.Header.Codec]] such as `Header.ContentLength`.
 * `import zio.http.schema._` adds them to every `Headers`, and
 * [[RequestSchemaOps]] and [[ResponseSchemaOps]] forward to them.
 *
 * Names match without regard to letter case, as everywhere in `Headers`. An
 * absent header gives [[HeaderError.Missing]], and a value that does not decode
 * gives [[HeaderError.Malformed]] with the raw value and the reason; both carry
 * the name as the caller spelled it. The `Schema[T]` methods decode the same
 * types as [[QueryParamsSchemaOps]]: `String`, `Boolean`, `Char`, the numeric
 * types and `java.util.UUID`. Any other schema compiles but always gives
 * `Malformed`, a known bug. To read a whole case class of headers at once,
 * derive a [[HeaderCodec]] instead.
 *
 * {{{
 * import zio.http.{Header, Headers}
 *
 * val headers = Headers(
 *   "X-Request-Id"   -> "8812",
 *   "X-Order-Tag"    -> "gift",
 *   "x-order-tag"    -> "rush",
 *   "Content-Length" -> "512",
 *   "X-Api-Version"  -> "two"
 * )
 *
 * headers.header[Long]("x-request-id")           // Right(8812)
 * headers.headerAll[String]("X-ORDER-TAG")       // Right(Chunk(gift,rush))
 * headers.header(Header.ContentLength)           // Right(ContentLength(512))
 * headers.header[Int]("X-Api-Version")           // Left(Malformed(X-Api-Version,two,Cannot parse 'two' as Int))
 * headers.header[String]("Idempotency-Key")      // Left(Missing(Idempotency-Key))
 * headers.headerOrElse[Int]("X-Retry-After", 30) // 30
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/schema/ Schema-Based Typed Access]]
 */
final class HeadersSchemaOps(private val headers: Headers) extends AnyVal {

  /**
   * Decodes the first header named `name`, in any letter case, as a `T`.
   * Returns [[HeaderError.Missing]] when there is none and
   * [[HeaderError.Malformed]] when its value does not decode; later headers
   * with the same name are not tried.
   */
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name, such as one with a space
  def header[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, T] =
    headers.rawGet(name) match {
      case None      => Left(HeaderError.Missing(name))
      case Some(raw) => StringDecoder.decode(raw, schema).left.map(e => HeaderError.Malformed(name, raw, e))
    }

  /**
   * Decodes the first header named `headerCodec.name`, in any letter case, with
   * the codec. Returns [[HeaderError.Missing]] when there is none and
   * [[HeaderError.Malformed]], with the codec's message, when it does not
   * parse. Unlike `Headers#get`, which skips a value that fails to parse, a
   * malformed first value gives `Malformed` even when a later one parses.
   */
  def header[A](headerCodec: Header.Codec[A]): Either[HeaderError, A] =
    headers.rawGet(headerCodec.name) match {
      case None      => Left(HeaderError.Missing(headerCodec.name))
      case Some(raw) => headerCodec.parse(raw).left.map(e => HeaderError.Malformed(headerCodec.name, raw, e))
    }

  /**
   * Decodes every header named `name`, in any letter case, as a `T`, in the
   * order they appear. Returns [[HeaderError.Missing]] when there is none, and
   * stops at the first value that does not decode with
   * [[HeaderError.Malformed]] for it.
   */
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name, such as one with a space
  def headerAll[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, Chunk[T]] = {
    val values = headers.rawGetAll(name)
    if (values.isEmpty) Left(HeaderError.Missing(name))
    else {
      val builder = Chunk.newBuilder[T]
      var i       = 0
      while (i < values.length) {
        StringDecoder.decode(values(i), schema) match {
          case Right(v) => builder += v
          case Left(e)  => return Left(HeaderError.Malformed(name, values(i), e))
        }
        i += 1
      }
      Right(builder.result())
    }
  }

  /**
   * Decodes every header named `headerCodec.name`, in any letter case, with the
   * codec, in the order they appear. Returns [[HeaderError.Missing]] when there
   * is none, and stops at the first value that does not parse with
   * [[HeaderError.Malformed]] for it.
   */
  def headerAll[A](headerCodec: Header.Codec[A]): Either[HeaderError, Chunk[A]] = {
    val values = headers.rawGetAll(headerCodec.name)
    if (values.isEmpty) Left(HeaderError.Missing(headerCodec.name))
    else {
      val builder = Chunk.newBuilder[A]
      var i       = 0
      while (i < values.length) {
        headerCodec.parse(values(i)) match {
          case Right(value) => builder += value
          case Left(error)  => return Left(HeaderError.Malformed(headerCodec.name, values(i), error))
        }
        i += 1
      }
      Right(builder.result())
    }
  }

  /**
   * Decodes the first header named `name` as a `T`, or returns `default` when
   * there is none or its value does not decode. A malformed value is dropped
   * without a trace, a known bug: `headerOrElse[Int]("X-Retry-After", 30)`
   * returns `30` for `X-Retry-After: soon`. `default` is evaluated only when it
   * is returned.
   */
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name, such as one with a space
  def headerOrElse[T](name: String, default: => T)(implicit schema: Schema[T]): T =
    header[T](name).getOrElse(default)
}
