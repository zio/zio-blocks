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

trait XmlDecoder[A] { self =>
  def decode(xml: Xml): Either[XmlError, A]

  def map[B](f: A => B): XmlDecoder[B] = new XmlDecoder[B] {
    def decode(xml: Xml): Either[XmlError, B] = self.decode(xml) match {
      case Right(v) => new Right(f(v))
      case l        => l.asInstanceOf[Either[XmlError, B]]
    }
  }
}

object XmlDecoder {
  def apply[A](implicit decoder: XmlDecoder[A]): XmlDecoder[A] = decoder

  def instance[A](f: Xml => Either[XmlError, A]): XmlDecoder[A] = new XmlDecoder[A] {
    def decode(xml: Xml): Either[XmlError, A] = f(xml)
  }

  implicit def fromSchema[A](implicit schema: Schema[A]): XmlDecoder[A] = new XmlDecoder[A] {
    private[this] val codec = schema.derive(XmlBinaryCodecDeriver)

    def decode(xml: Xml): Either[XmlError, A] = codec.decodeValue(xml)
  }
}
