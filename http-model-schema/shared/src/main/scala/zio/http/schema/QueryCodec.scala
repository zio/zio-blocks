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
 * A codec for encoding a value of type `A` into [[zio.http.QueryParams]] and
 * decoding [[zio.http.QueryParams]] back into `A`.
 *
 * Instances are typically derived via [[QueryFormat]] using the schema
 * derivation framework.
 */
abstract class QueryCodec[A] extends Codec[QueryParams, QueryParamsBuilder, A] {
  final def encodeToQueryParams(value: A): QueryParams = {
    val builder = QueryCodec.threadLocalBuilder.get()
    builder.reset()
    encode(value, builder)
    builder.build()
  }
}

object QueryCodec {
  private val threadLocalBuilder: ThreadLocal[QueryParamsBuilder] =
    new ThreadLocal[QueryParamsBuilder] {
      override def initialValue(): QueryParamsBuilder = QueryParamsBuilder.make()
    }
}
