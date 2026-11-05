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

package zio.blocks.schema

import org.bson.BsonValue

package object bson {

  /**
   * Adds `as` to a `BsonValue`, so `document.as[Order]` decodes it with an
   * implicit `BsonDecoder[Order]`. The module defines no implicit decoders, so
   * put one in scope yourself or pass it explicitly.
   */
  implicit class BsonDecoderOps(private val value: BsonValue) extends AnyVal {

    /**
     * Decodes this value with `decoder`, the same as
     * `decoder.fromBsonValue(value)`.
     */
    def as[A](implicit decoder: BsonDecoder[A]): Either[BsonDecoder.Error, A] =
      decoder.fromBsonValue(value)
  }
}
