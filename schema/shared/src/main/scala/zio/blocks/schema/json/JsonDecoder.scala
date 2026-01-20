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
 * Type class for decoding [[Json]] values into Scala types.
 *
 * Implicit resolution prefers explicitly provided [[JsonBinaryCodec]] instances
 * over schema-derived instances, allowing users to override derived behavior.
 */
sealed trait JsonDecoder[A] {

  /**
   * Decodes a [[Json]] value into type `A`.
   *
   * @param json
   *   The JSON value to decode
   * @return
   *   Either a [[JsonError]] on failure, or the decoded value
   */
  def decode(json: Json): Either[JsonError, A]
}

object JsonDecoder extends JsonDecoderLowPriority {

  def apply[A](implicit decoder: JsonDecoder[A]): JsonDecoder[A] = decoder

  /**
   * Higher priority: use an explicitly provided [[JsonBinaryCodec]].
   */
  implicit def fromCodec[A](implicit codec: JsonBinaryCodec[A]): JsonDecoder[A] =
    new JsonDecoder[A] {
      def decode(json: Json): Either[JsonError, A] = {
        val jsonStr = json.encode
        codec.decode(jsonStr.getBytes("UTF-8")) match {
          case Right(value) => Right(value)
          case Left(error)  => Left(JsonError.fromSchemaError(error))
        }
      }
    }
}

/**
 * Lower priority implicits for [[JsonDecoder]].
 */
trait JsonDecoderLowPriority {

  /**
   * Lower priority: derive a codec from an implicit [[Schema]].
   */
  implicit def fromSchema[A](implicit schema: Schema[A]): JsonDecoder[A] =
    new JsonDecoder[A] {
      private lazy val codec: JsonBinaryCodec[A] = schema.derive(JsonBinaryCodecDeriver)

      def decode(json: Json): Either[JsonError, A] = {
        val jsonStr = json.encode
        codec.decode(jsonStr.getBytes("UTF-8")) match {
          case Right(value) => Right(value)
          case Left(error)  => Left(JsonError.fromSchemaError(error))
        }
      }
    }
}
