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
