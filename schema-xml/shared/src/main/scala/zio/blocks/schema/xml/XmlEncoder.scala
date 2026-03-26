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

package zio.blocks.schema.xml

import zio.blocks.schema.Schema

trait XmlEncoder[A] { self =>
  def encode(value: A): Xml

  def contramap[B](f: B => A): XmlEncoder[B] = new XmlEncoder[B] {
    def encode(value: B): Xml = self.encode(f(value))
  }
}

object XmlEncoder {
  def apply[A](implicit encoder: XmlEncoder[A]): XmlEncoder[A] = encoder

  def instance[A](f: A => Xml): XmlEncoder[A] = new XmlEncoder[A] {
    def encode(value: A): Xml = f(value)
  }

  implicit def fromSchema[A](implicit schema: Schema[A]): XmlEncoder[A] = new XmlEncoder[A] {
    private[this] val codec = schema.derive(XmlCodecDeriver)

    def encode(value: A): Xml = codec.encodeValue(value)
  }
}
