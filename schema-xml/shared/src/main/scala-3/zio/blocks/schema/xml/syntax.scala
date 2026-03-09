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
