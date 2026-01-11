package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema.Schema

sealed trait Pet
case class Cat(name: String, lives: Int)    extends Pet
case class Dog(name: String, breed: String) extends Pet

object Pet {
  implicit val schema: Schema[Pet] = Schema.derived
}

object VariantCodecSpec extends ZIOSpecDefault {
  def spec = suite("VariantCodec")(
    test("variant with key discriminator - Cat") {
      val codec: ToonBinaryCodec[Pet] = Pet.schema.derive(ToonFormat.deriver)
      val pet: Pet                    = Cat("Whiskers", 9)
      val encoded                     = codec.encodeToString(pet)
      // Cat case should have discriminator
      assertTrue(encoded.contains("Cat:") && encoded.contains("name: Whiskers") && encoded.contains("lives: 9"))
    },
    test("variant with key discriminator - Dog") {
      val codec: ToonBinaryCodec[Pet] = Pet.schema.derive(ToonFormat.deriver)
      val pet: Pet                    = Dog("Rex", "German Shepherd")
      val encoded                     = codec.encodeToString(pet)
      assertTrue(encoded.contains("Dog:") && encoded.contains("name: Rex"))
    }
  )
}
