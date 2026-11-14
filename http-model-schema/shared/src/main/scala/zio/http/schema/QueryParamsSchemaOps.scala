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
import zio.http.QueryParams

/**
 * Typed reads of single query parameters. `query`, `queryAll` and `queryOrElse`
 * look a key up in a [[zio.http.QueryParams]] and decode its value with the
 * `Schema[T]` in scope. `import zio.http.schema._` adds them to every
 * `QueryParams`, and [[RequestSchemaOps]] adds them to a `Request`.
 *
 * Keys match exactly, letter case included. An absent key gives
 * [[QueryParamError.Missing]], and a value that does not decode gives
 * [[QueryParamError.Malformed]] with the raw value and the reason. A key
 * written without a value, as in `?gift` or `?gift=`, is present with the empty
 * string, which decodes as a `String` but not as a number or a `Boolean`.
 *
 * Only these types decode: `String`, `Boolean`, `Char`, `Byte`, `Short`, `Int`,
 * `Long`, `Float`, `Double`, `BigInt`, `BigDecimal` and `java.util.UUID`. Any
 * other `Schema[T]` compiles but gives `Malformed` for every value, a known
 * bug: an `Option`, a collection or a wrapper type gives "Unsupported schema
 * type for string decoding", and a `java.time` type gives "Unsupported
 * primitive type for string decoding". No method checks the `Validation` a
 * schema carries. To read a whole case class of parameters at once, derive a
 * [[QueryCodec]] instead.
 *
 * {{{
 * import zio.http.QueryParams
 * import zio.http.schema._
 *
 * val params = QueryParams.fromEncoded("status=shipped&page=2&tag=gift&tag=rush&limit=abc")
 *
 * params.query[String]("status")      // Right(shipped)
 * params.query[Int]("page")           // Right(2)
 * params.queryAll[String]("tag")      // Right(Chunk(gift,rush))
 * params.query[Long]("customer")      // Left(Missing(customer))
 * params.query[Int]("limit")          // Left(Malformed(limit,abc,Cannot parse 'abc' as Int))
 * params.queryOrElse[Int]("size", 20) // 20
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/schema/ Schema-Based Typed Access]]
 */
final class QueryParamsSchemaOps(private val qp: QueryParams) extends AnyVal {

  /**
   * Decodes the first value of `key` as a `T`. Returns
   * [[QueryParamError.Missing]] when the key is absent and
   * [[QueryParamError.Malformed]] when the first value does not decode; later
   * values of the key are ignored.
   */
  def query[T](key: String)(implicit schema: Schema[T]): Either[QueryParamError, T] =
    qp.getFirst(key) match {
      case None      => Left(QueryParamError.Missing(key))
      case Some(raw) => ParamPrimitiveCodec.decode(raw, schema).left.map(e => QueryParamError.Malformed(key, raw, e))
    }

  /**
   * Decodes every value of `key` as a `T`, in the order they appear. Returns
   * [[QueryParamError.Missing]] when the key is absent, and stops at the first
   * value that does not decode with [[QueryParamError.Malformed]] for it.
   */
  def queryAll[T](key: String)(implicit schema: Schema[T]): Either[QueryParamError, Chunk[T]] =
    qp.get(key) match {
      case None         => Left(QueryParamError.Missing(key))
      case Some(values) =>
        val builder = Chunk.newBuilder[T]
        var i       = 0
        while (i < values.length) {
          ParamPrimitiveCodec.decode(values(i), schema) match {
            case Right(v) => builder += v
            case Left(e)  => return Left(QueryParamError.Malformed(key, values(i), e))
          }
          i += 1
        }
        Right(builder.result())
    }

  /**
   * Decodes the first value of `key` as a `T`, or returns `default` when the
   * key is absent or the value does not decode. A malformed value is dropped
   * without a trace, a known bug: `queryOrElse[Int]("limit", 20)` returns `20`
   * for `?limit=abc`. To reject a bad value, call `query` and fall back only on
   * [[QueryParamError.Missing]]. `default` is evaluated only when it is
   * returned.
   */
  def queryOrElse[T](key: String, default: => T)(implicit schema: Schema[T]): T =
    query[T](key).getOrElse(default)
}
