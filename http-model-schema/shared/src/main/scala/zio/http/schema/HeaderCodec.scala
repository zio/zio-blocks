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
import zio.http.{Headers, HeadersBuilder}

/**
 * Encodes a value of type `A` as [[zio.http.Headers]] and decodes it back, so a
 * whole case class maps onto a set of headers in one call. Derive one with
 * `Schema[A].derive(DefaultHeaderFormat)`, or extend this class and write
 * `encode` and `decode` by hand.
 *
 * A derived codec writes a record as one header per field. The header name is
 * the field name in upper case with a hyphen before each upper-case letter
 * after the first, so `requestId` becomes `REQUEST-ID`; `Headers` stores names
 * in lower case, so the encoded header reads `request-id`, and decoding ignores
 * letter case. Field shapes and failures follow the rules of [[QueryCodec]]:
 * primitives, `Option`, sequences (one repeated header per element) and
 * wrappers of these; field default values are ignored, a known bug. Error
 * messages use the name before lower-casing, as in
 * `Missing field 'API-VERSION'`. A schema that is not a record uses the single
 * header `Value` (stored as `value`).
 *
 * {{{
 * import zio.blocks.schema.Schema
 * import zio.http.Headers
 *
 * final case class OrderHeaders(requestId: String, apiVersion: Int, ifMatch: Option[String])
 * object OrderHeaders {
 *   implicit val schema: Schema[OrderHeaders] = Schema.derived
 * }
 *
 * val codec = Schema[OrderHeaders].derive(DefaultHeaderFormat)
 *
 * codec.encodeToHeaders(OrderHeaders("r-17", 2, None)).toList
 * // List((request-id,r-17), (api-version,2))
 * codec.decode(Headers("Request-Id" -> "r-18", "API-VERSION" -> "3"))
 * // Right(OrderHeaders(r-18,3,None))
 * codec.decode(Headers("request-id" -> "r-19", "api-version" -> "two"))
 * // Left(zio.blocks.schema.SchemaError: Malformed header 'API-VERSION' value 'two': Cannot parse 'two' as Int)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/schema-codecs/ HeaderCodec and QueryCodec]]
 */
abstract class HeaderCodec[A] extends Codec[Headers, HeadersBuilder, A] {

  /**
   * Encodes `value` into a new [[zio.http.Headers]]. It fills a builder that
   * every `HeaderCodec` on the current thread shares, so a call made from
   * inside another codec's `encode` on the same thread clears the output that
   * codec was writing and leaves its own entries there. Inside `encode`, write
   * to the `output` builder instead.
   */
  final def encodeToHeaders(value: A): Headers = {
    val builder = HeaderCodec.threadLocalBuilder.get()
    builder.reset()
    encode(value, builder)
    builder.build()
  }
}

/** Holds the per-thread builder that `encodeToHeaders` reuses. */
object HeaderCodec {
  private val threadLocalBuilder: ThreadLocal[HeadersBuilder] =
    new ThreadLocal[HeadersBuilder] {
      override def initialValue(): HeadersBuilder = HeadersBuilder.make()
    }
}
