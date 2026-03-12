package zio.blocks.schema.yaml

import zio.blocks.schema.SchemaError

object YamlSyntax {
  implicit class YamlEncoderOps[A](private val value: A) extends AnyVal {
    def toYaml(implicit encoder: YamlEncoder[A]): Yaml         = encoder.encode(value)
    def toYamlString(implicit encoder: YamlEncoder[A]): String =
      YamlWriter.write(encoder.encode(value))
    def toYamlBytes(implicit encoder: YamlEncoder[A]): Array[Byte] =
      YamlWriter.writeToBytes(encoder.encode(value))
  }

  implicit class YamlStringOps(private val s: String) extends AnyVal {
    def fromYaml[A](implicit decoder: YamlDecoder[A]): Either[SchemaError, A] =
      YamlReader.read(s) match {
        case Left(err)   => Left(SchemaError(err.getMessage))
        case Right(yaml) =>
          decoder.decode(yaml) match {
            case Left(err) => Left(SchemaError(err.getMessage))
            case Right(v)  => Right(v)
          }
      }
  }
}
