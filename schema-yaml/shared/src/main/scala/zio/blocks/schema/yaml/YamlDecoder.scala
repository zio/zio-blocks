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

package zio.blocks.schema.yaml

import zio.blocks.schema.Schema

trait YamlDecoder[A] { self =>

  def decode(yaml: Yaml): Either[YamlError, A]

  def map[B](f: A => B): YamlDecoder[B] = new YamlDecoder[B] {
    def decode(yaml: Yaml): Either[YamlError, B] = self.decode(yaml).map(f)
  }
}

object YamlDecoder {

  def apply[A](implicit decoder: YamlDecoder[A]): YamlDecoder[A] = decoder

  def instance[A](f: Yaml => Either[YamlError, A]): YamlDecoder[A] = new YamlDecoder[A] {
    def decode(yaml: Yaml): Either[YamlError, A] = f(yaml)
  }

  implicit def fromSchema[A](implicit schema: Schema[A]): YamlDecoder[A] = new YamlDecoder[A] {
    private[this] val codec = schema.derive(YamlCodecDeriver)

    def decode(yaml: Yaml): Either[YamlError, A] = codec.decodeValue(yaml)
  }
}
