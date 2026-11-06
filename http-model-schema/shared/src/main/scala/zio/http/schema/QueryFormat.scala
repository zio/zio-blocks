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

import zio.blocks.schema.derive.{Derivable, Deriver}

/**
 * A query-string format for `Schema#derive`: it pairs a MIME type with the
 * [[zio.blocks.schema.derive.Deriver]] that builds its codecs, and supplies the
 * implicit `Derivable` that lets `Schema[A].derive(format)` take the format
 * object itself. `DefaultQueryFormat` is the built-in one; extend this class to
 * report another MIME type or to plug in another deriver. Not every schema
 * derives: [[QueryCodec]] lists the shapes the built-in deriver supports.
 *
 * @tparam TC
 *   the codec type the deriver builds, a `QueryCodec` or a subclass
 * @param mimeType
 *   the media type the format reports; nothing in this module reads it
 * @param deriver
 *   the deriver that `Schema#derive` calls to build a codec
 */
abstract class QueryFormat[TC[A] <: QueryCodec[A]](val mimeType: String, val deriver: Deriver[TC]) {

  /** Lets `Schema#derive` accept this format object in place of its deriver. */
  implicit final def derivable: Derivable[this.type, TC] =
    new Derivable[this.type, TC] {
      def deriver(d: QueryFormat.this.type): Deriver[TC] = QueryFormat.this.deriver
    }
}

/** The type of the `DefaultQueryFormat` object. */
sealed abstract class DefaultQueryFormat
    extends QueryFormat[QueryCodec]("application/x-www-form-urlencoded", QueryCodecDeriver)

/**
 * The built-in query-string format: MIME type
 * `application/x-www-form-urlencoded`, codecs built by [[QueryCodecDeriver]].
 * `Schema[A].derive(DefaultQueryFormat)` returns a `QueryCodec[A]`.
 */
case object DefaultQueryFormat extends DefaultQueryFormat
