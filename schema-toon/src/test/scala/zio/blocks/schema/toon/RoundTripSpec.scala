package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema.Schema

object RoundTripSpec extends ZIOSpecDefault {
  // Define test models inside the object to avoid conflicts
  case class RtPerson(name: String, age: Int)
  object RtPerson {
    implicit val schema: Schema[RtPerson] = Schema.derived
  }

  case class RtAddress(street: String, city: String)
  object RtAddress {
    implicit val schema: Schema[RtAddress] = Schema.derived
  }

  case class RtPersonWithAddress(name: String, address: RtAddress)
  object RtPersonWithAddress {
    implicit val schema: Schema[RtPersonWithAddress] = Schema.derived
  }

  sealed trait RtAnimal
  case class RtDog(name: String) extends RtAnimal
  case class RtCat(lives: Int)   extends RtAnimal
  object RtAnimal {
    implicit val schema: Schema[RtAnimal] = Schema.derived
  }

  case class RtWithList(items: List[Int])
  object RtWithList {
    implicit val schema: Schema[RtWithList] = Schema.derived
  }

  def spec = suite("RoundTrip Decoding")(
    suite("Primitives")(
      test("Int round-trip") {
        val codec   = ToonBinaryCodec.intCodec
        val value   = 42
        val encoded = codec.encodeToString(value)
        val decoded = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(value))
      },
      test("String round-trip") {
        val codec   = ToonBinaryCodec.stringCodec
        val value   = "hello"
        val encoded = codec.encodeToString(value)
        val decoded = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(value))
      },
      test("Boolean round-trip") {
        val codec   = ToonBinaryCodec.booleanCodec
        val value   = true
        val encoded = codec.encodeToString(value)
        val decoded = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("Records")(
      test("simple record round-trip") {
        val codec: ToonBinaryCodec[RtPerson] = RtPerson.schema.derive(ToonFormat.deriver)
        val person                           = RtPerson("Alice", 30)
        val encoded                          = codec.encodeToString(person)
        val decoded                          = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(person))
      },
      test("nested record round-trip") {
        val codec: ToonBinaryCodec[RtPersonWithAddress] = RtPersonWithAddress.schema.derive(ToonFormat.deriver)
        val person                                      = RtPersonWithAddress("Bob", RtAddress("123 Main", "Springfield"))
        val encoded                                     = codec.encodeToString(person)
        val decoded                                     = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(person))
      }
    ),
    suite("Sequences")(
      test("inline list round-trip") {
        val schema  = Schema[List[Int]]
        val codec   = schema.derive(ToonFormat.deriver)
        val list    = List(1, 2, 3)
        val encoded = codec.encodeToString(list)
        val decoded = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(list))
      },
      test("record with list round-trip") {
        val codec: ToonBinaryCodec[RtWithList] = RtWithList.schema.derive(ToonFormat.deriver)
        val value                              = RtWithList(List(1, 2, 3))
        val encoded                            = codec.encodeToString(value)
        val decoded                            = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("Variants")(
      test("Dog variant round-trip") {
        val codec: ToonBinaryCodec[RtAnimal] = RtAnimal.schema.derive(ToonFormat.deriver)
        val dog: RtAnimal                    = RtDog("Rex")
        val encoded                          = codec.encodeToString(dog)
        val decoded                          = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(dog))
      },
      test("Cat variant round-trip") {
        val codec: ToonBinaryCodec[RtAnimal] = RtAnimal.schema.derive(ToonFormat.deriver)
        val cat: RtAnimal                    = RtCat(9)
        val encoded                          = codec.encodeToString(cat)
        val decoded                          = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(cat))
      }
    ),
    suite("Maps")(
      test("string map round-trip") {
        val schema  = Schema[Map[String, Int]]
        val codec   = schema.derive(ToonFormat.deriver)
        val map     = Map("a" -> 1, "b" -> 2)
        val encoded = codec.encodeToString(map)
        val decoded = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(map))
      }
    ),
    suite("New Primitives")(
      test("UUID round-trip") {
        val codec   = ToonBinaryCodec.uuidCodec
        val value   = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val encoded = codec.encodeToString(value)
        val decoded = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(value))
      },
      test("Currency round-trip") {
        val codec   = ToonBinaryCodec.currencyCodec
        val value   = java.util.Currency.getInstance("USD")
        val encoded = codec.encodeToString(value)
        val decoded = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(value))
      },
      test("DayOfWeek round-trip") {
        val codec   = ToonBinaryCodec.dayOfWeekCodec
        val value   = java.time.DayOfWeek.MONDAY
        val encoded = codec.encodeToString(value)
        val decoded = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(value))
      },
      test("Month round-trip") {
        val codec   = ToonBinaryCodec.monthCodec
        val value   = java.time.Month.JANUARY
        val encoded = codec.encodeToString(value)
        val decoded = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(value))
      },
      test("Year round-trip") {
        val codec   = ToonBinaryCodec.yearCodec
        val value   = java.time.Year.of(2024)
        val encoded = codec.encodeToString(value)
        val decoded = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(value))
      }
    )
  )
}
