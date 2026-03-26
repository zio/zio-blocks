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

trait YamlEncoder[A] { self =>

  def encode(value: A): Yaml

  def contramap[B](f: B => A): YamlEncoder[B] = new YamlEncoder[B] {
    def encode(value: B): Yaml = self.encode(f(value))
  }
}

object YamlEncoder {

  def apply[A](implicit encoder: YamlEncoder[A]): YamlEncoder[A] = encoder

  def instance[A](f: A => Yaml): YamlEncoder[A] = new YamlEncoder[A] {
    def encode(value: A): Yaml = f(value)
  }

  implicit def fromSchema[A](implicit schema: Schema[A]): YamlEncoder[A] = new YamlEncoder[A] {
    private[this] val codec = schema.derive(YamlCodecDeriver)

    def encode(value: A): Yaml = codec.encodeValue(value)
  }
}
