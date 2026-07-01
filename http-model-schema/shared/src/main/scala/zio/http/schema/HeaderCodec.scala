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
 * A codec for encoding a value of type `A` into [[zio.http.Headers]] and
 * decoding [[zio.http.Headers]] back into `A`.
 *
 * Instances are typically derived via [[HeaderFormat]] using the schema
 * derivation framework.
 */
abstract class HeaderCodec[A] extends Codec[Headers, HeadersBuilder, A] {
  final def encodeToHeaders(value: A): Headers = {
    val builder = HeaderCodec.threadLocalBuilder.get()
    builder.reset()
    encode(value, builder)
    builder.build()
  }
}

object HeaderCodec {
  private val threadLocalBuilder: ThreadLocal[HeadersBuilder] =
    new ThreadLocal[HeadersBuilder] {
      override def initialValue(): HeadersBuilder = HeadersBuilder.make()
    }
}
