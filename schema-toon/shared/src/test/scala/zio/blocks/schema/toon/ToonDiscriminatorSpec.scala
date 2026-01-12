package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.toon._
import zio.blocks.schema.json.DiscriminatorKind
import java.nio.charset.StandardCharsets

object ToonDiscriminatorSpec extends ZIOSpecDefault {

  sealed trait Pet
  case class Cat(name: String, lives: Int)    extends Pet
  case class Dog(name: String, breed: String) extends Pet
  object Pet {
    implicit val schema: Schema[Pet] = Schema.derived
  }

  // Create a custom deriver with Field discriminator
  val fieldDeriver = ToonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("type"))

  private def encode[A](codec: ToonBinaryCodec[A], value: A): String = {
    val writer = new ToonWriter(new Array[Byte](16384), ToonWriterConfig)
    codec.encodeValue(value, writer)
    new String(writer.buf, 0, writer.count, StandardCharsets.UTF_8)
  }

  private def decode[A](codec: ToonBinaryCodec[A], input: String): Either[SchemaError, A] =
    codec.decodeFromString(input)

  def spec = suite("ToonDiscriminatorSpec")(
    test("encodes with field discriminator") {
      val codec      = Pet.schema.derive(fieldDeriver)
      val value: Pet = Cat("Whiskers", 9)
      val encoded    = encode(codec, value)
      val expected   = """type: Cat
name: Whiskers
lives: 9"""
      assertTrue(encoded == expected)
    },
    test("round-trips with field discriminator") {
      val codec      = Pet.schema.derive(fieldDeriver)
      val value: Pet = Dog("Buddy", "Golden Retriever")
      val encoded    = encode(codec, value)
      val decoded    = decode(codec, encoded)
      assertTrue(decoded == Right(value))
    }
  )
}
