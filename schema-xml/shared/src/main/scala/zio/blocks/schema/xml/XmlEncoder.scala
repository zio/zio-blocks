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
    private[this] val codec = schema.derive(XmlBinaryCodecDeriver)

    def encode(value: A): Xml = codec.encodeValue(value)
  }
}
