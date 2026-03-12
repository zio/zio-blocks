package zio.blocks.schema.yaml

import zio.blocks.schema.Schema

trait YamlEncoder[A] { self =>

  def encode(value: A): Yaml

  def contramap[B](f: B => A): YamlEncoder[B] = new YamlEncoder[B] {
    def encode(value: B): Yaml = self.encode(f(value))
  }
}

object YamlEncoder {

  def apply[A](implicit encoder: YamlEncoder[A]): YamlEncoder[A] = encoder

  def instance[A](f: A => Yaml): YamlEncoder[A] = new YamlEncoder[A] {
    def encode(value: A): Yaml = f(value)
  }

  implicit def fromSchema[A](implicit schema: Schema[A]): YamlEncoder[A] = new YamlEncoder[A] {
    private[this] val codec = schema.derive(YamlBinaryCodecDeriver)

    def encode(value: A): Yaml = codec.encodeValue(value)
  }
}
