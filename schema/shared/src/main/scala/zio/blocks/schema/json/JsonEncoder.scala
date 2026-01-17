/*
 * Copyright 2023 ZIO Blocks Maintainers
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

package zio.blocks.schema.json

import zio.blocks.schema.Schema

/**
 * Type class for encoding Scala types into [[Json]] values.
 *
 * Implicit resolution prefers explicitly provided [[JsonBinaryCodec]] instances over schema-derived instances,
 * allowing users to override derived behavior.
 */
sealed trait JsonEncoder[A] {

  /**
   * Encodes a value of type `A` into [[Json]].
   *
   * @param value
   *   The value to encode
   * @return
   *   The encoded JSON value
   */
  def encode(value: A): Json
}

object JsonEncoder extends JsonEncoderLowPriority {

  def apply[A](implicit encoder: JsonEncoder[A]): JsonEncoder[A] = encoder

  /**
   * Higher priority: use an explicitly provided [[JsonBinaryCodec]].
   */
  implicit def fromCodec[A](implicit codec: JsonBinaryCodec[A]): JsonEncoder[A] =
    new JsonEncoder[A] {
      def encode(value: A): Json = {
        val bytes = codec.encode(value)
        Json.parse(new String(bytes, "UTF-8")).getOrElse(Json.Null)
      }
    }
}

/**
 * Lower priority implicits for [[JsonEncoder]].
 */
trait JsonEncoderLowPriority {

  /**
   * Lower priority: derive a codec from an implicit [[Schema]].
   */
  implicit def fromSchema[A](implicit schema: Schema[A]): JsonEncoder[A] =
    new JsonEncoder[A] {
      private lazy val codec: JsonBinaryCodec[A] = schema.derive(JsonBinaryCodecDeriver)

      def encode(value: A): Json = {
        val bytes = codec.encode(value)
        Json.parse(new String(bytes, "UTF-8")).getOrElse(Json.Null)
      }
    }
}
