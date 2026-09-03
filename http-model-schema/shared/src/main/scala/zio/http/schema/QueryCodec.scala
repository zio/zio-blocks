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

import zio.blocks.schema.codec.Codec
import zio.http.{QueryParams, QueryParamsBuilder}

/**
 * Encodes a value of type `A` as [[zio.http.QueryParams]] and decodes it back,
 * so a whole case class maps onto a query string in one call. Derive one with
 * `Schema[A].derive(DefaultQueryFormat)`, or extend this class and write
 * `encode` and `decode` by hand.
 *
 * A derived codec writes a record as one parameter per field, named exactly as
 * the field, and matches names exactly when decoding, letter case included. A
 * field can be a primitive, an `Option`, a sequence such as `List` or `Chunk`
 * (one repeated parameter per element), or a wrapper of one of these built with
 * `Schema#transform`. `None` writes nothing, and an absent `Option` or sequence
 * parameter decodes to `None` or an empty sequence. A present but empty value,
 * as in `size=`, is decoded like any other, so an `Option[Int]` field fails on
 * it. A field that is not a sequence reads the first value of its parameter and
 * ignores the rest, and parameters that match no field are ignored. A schema
 * that is not a record uses the single key `value`.
 *
 * `decode` reports failures as a [[zio.blocks.schema.SchemaError]]:
 * `Missing field 'page'` for an absent required parameter, or the
 * [[QueryParamError.Malformed]] message for a value that does not parse. Field
 * default values are ignored, a known bug, so a missing `limit: Int = 20` fails
 * with `Missing field 'limit'`. An `Option` of a sequence decodes only its
 * first element, also a known bug.
 *
 * Derivation throws `UnsupportedOperationException` for a field of any other
 * shape, such as a nested record, a `Map` or a sealed trait. A top-level
 * `Option` encodes `None` as absent parameters and decodes absent parameters as
 * `None`. A top-level sealed trait, `Map` or `DynamicValue` schema derives, but
 * its codec throws `UnsupportedOperationException` on every `encode` and
 * `decode`. A field of another primitive type, such as `java.time.Instant`,
 * derives too, but `encode` throws and `decode` fails for it, a known bug.
 *
 * {{{
 * import zio.blocks.schema.Schema
 * import zio.http.QueryParams
 *
 * final case class OrderSearch(status: String, page: Int, size: Option[Int], tags: List[String])
 * object OrderSearch {
 *   implicit val schema: Schema[OrderSearch] = Schema.derived
 * }
 *
 * val codec = Schema[OrderSearch].derive(DefaultQueryFormat)
 *
 * codec.encodeToQueryParams(OrderSearch("open", 2, None, List("gift", "rush"))).encode
 * // status=open&page=2&tags=gift&tags=rush
 * codec.decode(QueryParams.fromEncoded("status=open&page=1&tags=gift"))
 * // Right(OrderSearch(open,1,None,List(gift)))
 * codec.decode(QueryParams.fromEncoded("status=open"))
 * // Left(zio.blocks.schema.SchemaError: Missing field 'page' at: .)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/schema-codecs/ HeaderCodec and QueryCodec]]
 */
abstract class QueryCodec[A] extends Codec[QueryParams, QueryParamsBuilder, A] {

  /**
   * Encodes `value` into a new [[zio.http.QueryParams]]. It fills a builder
   * that every `QueryCodec` on the current thread shares, so a call made from
   * inside another codec's `encode` on the same thread clears the output that
   * codec was writing and leaves its own entries there. Inside `encode`, write
   * to the `output` builder instead.
   */
  final def encodeToQueryParams(value: A): QueryParams =
    ParamCodecSupport.withBuilder(QueryCodec.threadLocalBuilder, _.reset(), _.build())(encode(value, _))
}

/** Holds the per-thread builder that `encodeToQueryParams` reuses. */
object QueryCodec {
  private[schema] val threadLocalBuilder: ThreadLocal[QueryParamsBuilder] =
    new ThreadLocal[QueryParamsBuilder] {
      override def initialValue(): QueryParamsBuilder = QueryParamsBuilder.make()
    }
}
