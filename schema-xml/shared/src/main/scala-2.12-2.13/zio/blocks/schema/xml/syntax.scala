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

import zio.blocks.schema.{Schema, SchemaError}

object syntax {
  implicit final class XmlValueOps[A](private val self: A) extends AnyVal {
    def toXml(implicit schema: Schema[A]): Xml = XmlEncoder.fromSchema[A].encode(self)

    def toXmlString(implicit schema: Schema[A]): String = XmlWriter.write(toXml, WriterConfig.default)

    def toXmlString(config: WriterConfig)(implicit schema: Schema[A]): String = XmlWriter.write(toXml, config)

    def toXmlBytes(implicit schema: Schema[A]): Array[Byte] = schema.getInstance(XmlFormat).encode(self)
  }

  implicit final class XmlStringOps(private val self: String) extends AnyVal {
    def fromXml[A](implicit schema: Schema[A]): Either[SchemaError, A] = schema.getInstance(XmlFormat).decode(self)
  }

  implicit final class XmlByteArrayOps(private val self: Array[Byte]) extends AnyVal {
    def fromXml[A](implicit schema: Schema[A]): Either[SchemaError, A] = schema.getInstance(XmlFormat).decode(self)
  }
}
