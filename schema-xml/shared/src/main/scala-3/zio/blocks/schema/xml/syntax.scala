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
  extension [A](self: A) {
    def toXml(using schema: Schema[A]): Xml = XmlEncoder.fromSchema[A].encode(self)

    def toXmlString(using schema: Schema[A]): String = XmlWriter.write(toXml, WriterConfig.default)

    def toXmlString(config: WriterConfig)(using schema: Schema[A]): String = XmlWriter.write(toXml, config)

    def toXmlBytes(using schema: Schema[A]): Array[Byte] = schema.getInstance(XmlFormat).encode(self)
  }

  extension (self: String) {
    def fromXml[A](using schema: Schema[A]): Either[SchemaError, A] = schema.getInstance(XmlFormat).decode(self)
  }

  extension (self: Array[Byte]) {
    def fromXml[A](using schema: Schema[A]): Either[SchemaError, A] = schema.getInstance(XmlFormat).decode(self)
  }
}
