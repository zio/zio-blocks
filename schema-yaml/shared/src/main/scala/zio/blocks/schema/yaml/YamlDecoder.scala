package zio.blocks.schema.yaml

import zio.blocks.schema.Schema

trait YamlDecoder[A] { self =>

  def decode(yaml: Yaml): Either[YamlError, A]

  def map[B](f: A => B): YamlDecoder[B] = new YamlDecoder[B] {
    def decode(yaml: Yaml): Either[YamlError, B] = self.decode(yaml).map(f)
  }
}

object YamlDecoder {

  def apply[A](implicit decoder: YamlDecoder[A]): YamlDecoder[A] = decoder

  def instance[A](f: Yaml => Either[YamlError, A]): YamlDecoder[A] = new YamlDecoder[A] {
    def decode(yaml: Yaml): Either[YamlError, A] = f(yaml)
  }

  implicit def fromSchema[A](implicit schema: Schema[A]): YamlDecoder[A] = new YamlDecoder[A] {
    private[this] val codec = schema.derive(YamlBinaryCodecDeriver)

    def decode(yaml: Yaml): Either[YamlError, A] = codec.decodeValue(yaml)
  }
}
