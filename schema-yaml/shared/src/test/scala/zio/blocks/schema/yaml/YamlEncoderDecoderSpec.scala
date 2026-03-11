package zio.blocks.schema.yaml

import zio.blocks.schema.Schema
import zio.test._

object YamlEncoderDecoderSpec extends YamlBaseSpec {

  case class Simple(value: String)
  object Simple {
    implicit val schema: Schema[Simple] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("YamlEncoder and YamlDecoder")(
    suite("YamlEncoder")(
      test("apply summons implicit") {
        val encoder = YamlEncoder[Simple]
        val yaml    = encoder.encode(Simple("test"))
        assertTrue(yaml.isInstanceOf[Yaml.Mapping])
      },
      test("instance creates encoder") {
        val encoder = YamlEncoder.instance[String](s => Yaml.Scalar(s))
        assertTrue(encoder.encode("hello") == Yaml.Scalar("hello"))
      },
      test("contramap") {
        val encoder    = YamlEncoder.instance[String](s => Yaml.Scalar(s))
        val intEncoder = encoder.contramap[Int](_.toString)
        assertTrue(intEncoder.encode(42) == Yaml.Scalar("42"))
      },
      test("fromSchema implicit") {
        val encoder = implicitly[YamlEncoder[Simple]]
        val yaml    = encoder.encode(Simple("hi"))
        assertTrue(yaml.isInstanceOf[Yaml.Mapping])
      }
    ),
    suite("YamlDecoder")(
      test("apply summons implicit") {
        val decoder = YamlDecoder[Simple]
        val result  = decoder.decode(Yaml.Mapping.fromStringKeys("value" -> Yaml.Scalar("test")))
        assertTrue(result == Right(Simple("test")))
      },
      test("instance creates decoder") {
        val decoder = YamlDecoder.instance[String] {
          case Yaml.Scalar(v, _) => Right(v)
          case _                 => Left(YamlError("not scalar"))
        }
        assertTrue(decoder.decode(Yaml.Scalar("hi")) == Right("hi"))
      },
      test("map transforms result") {
        val decoder = YamlDecoder.instance[String] {
          case Yaml.Scalar(v, _) => Right(v)
          case _                 => Left(YamlError("not scalar"))
        }
        val intDecoder = decoder.map(_.toInt)
        assertTrue(intDecoder.decode(Yaml.Scalar("42")) == Right(42))
      },
      test("fromSchema implicit") {
        val decoder = implicitly[YamlDecoder[Simple]]
        val result  = decoder.decode(Yaml.Mapping.fromStringKeys("value" -> Yaml.Scalar("test")))
        assertTrue(result == Right(Simple("test")))
      }
    )
  )
}
