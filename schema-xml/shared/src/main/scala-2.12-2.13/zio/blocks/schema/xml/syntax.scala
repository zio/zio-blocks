package zio.blocks.schema.xml

import zio.blocks.schema.{Schema, SchemaError}

object syntax {

  implicit final class XmlValueOps[A](private val self: A) extends AnyVal {

    def toXml(implicit schema: Schema[A]): Xml =
      XmlEncoder.fromSchema[A].encode(self)

    def toXmlString(implicit schema: Schema[A]): String =
      XmlWriter.write(toXml, WriterConfig.default)

    def toXmlString(config: WriterConfig)(implicit schema: Schema[A]): String =
      XmlWriter.write(toXml, config)

    def toXmlBytes(implicit schema: Schema[A]): Array[Byte] =
      schema.derive(XmlBinaryCodecDeriver).encode(self)
  }

  implicit final class XmlStringOps(private val self: String) extends AnyVal {

    def fromXml[A](implicit schema: Schema[A]): Either[SchemaError, A] = {
      val codec = schema.derive(XmlBinaryCodecDeriver)
      codec.decode(self.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    }
  }

  implicit final class XmlByteArrayOps(private val self: Array[Byte]) extends AnyVal {

    def fromXml[A](implicit schema: Schema[A]): Either[SchemaError, A] = {
      val codec = schema.derive(XmlBinaryCodecDeriver)
      codec.decode(self)
    }
  }
}
