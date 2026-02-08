package zio.blocks.schema.xml

import zio.blocks.schema.{Schema, SchemaError}

trait XmlSyntaxVersionSpecific {

  extension [A](self: A) {

    def toXml(using schema: Schema[A]): Xml =
      XmlEncoder.fromSchema[A].encode(self)

    def toXmlString(using schema: Schema[A]): String =
      XmlWriter.write(toXml, WriterConfig.default)

    def toXmlString(config: WriterConfig)(using schema: Schema[A]): String =
      XmlWriter.write(toXml, config)

    def toXmlBytes(using schema: Schema[A]): Array[Byte] =
      schema.derive(XmlBinaryCodecDeriver).encode(self)
  }

  extension (self: String) {

    def fromXml[A](using schema: Schema[A]): Either[SchemaError, A] = {
      val codec = schema.derive(XmlBinaryCodecDeriver)
      codec.decode(self.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    }
  }

  extension (self: Array[Byte]) {

    def fromXml[A](using schema: Schema[A]): Either[SchemaError, A] = {
      val codec = schema.derive(XmlBinaryCodecDeriver)
      codec.decode(self)
    }
  }
}

object syntax extends XmlSyntaxVersionSpecific
