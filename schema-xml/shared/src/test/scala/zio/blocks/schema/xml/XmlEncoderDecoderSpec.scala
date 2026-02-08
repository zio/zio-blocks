package zio.blocks.schema.xml

import zio.blocks.schema.{Schema, SchemaBaseSpec}
import zio.test._

object XmlEncoderDecoderSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class Address(street: String, city: String, zipCode: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("XmlEncoderDecoderSpec")(
    suite("XmlEncoder")(
      test("encode simple case class") {
        val person  = Person("Alice", 30)
        val encoder = XmlEncoder.fromSchema[Person]
        val xml     = encoder.encode(person)
        assertTrue(xml.isInstanceOf[Xml.Element])
      },
      test("encode with contramap") {
        val encoder       = XmlEncoder.fromSchema[Int]
        val stringEncoder = encoder.contramap[String](_.toInt)
        val xml           = stringEncoder.encode("42")
        assertTrue(xml.isInstanceOf[Xml.Element])
      }
    ),
    suite("XmlDecoder")(
      test("decode simple case class") {
        val person  = Person("Bob", 25)
        val encoder = XmlEncoder.fromSchema[Person]
        val decoder = XmlDecoder.fromSchema[Person]
        val xml     = encoder.encode(person)
        val result  = decoder.decode(xml)
        assertTrue(result == Right(person))
      },
      test("decode with map") {
        val encoder    = XmlEncoder.fromSchema[Int]
        val decoder    = XmlDecoder.fromSchema[Int]
        val mapDecoder = decoder.map(_.toString)
        val xml        = encoder.encode(42)
        val result     = mapDecoder.decode(xml)
        assertTrue(result == Right("42"))
      }
    ),
    suite("XmlEncoder and XmlDecoder round-trip")(
      test("round-trip Person") {
        val person  = Person("Charlie", 35)
        val encoder = XmlEncoder.fromSchema[Person]
        val decoder = XmlDecoder.fromSchema[Person]
        val result  = decoder.decode(encoder.encode(person))
        assertTrue(result == Right(person))
      },
      test("round-trip Address") {
        val address = Address("123 Main St", "Springfield", "12345")
        val encoder = XmlEncoder.fromSchema[Address]
        val decoder = XmlDecoder.fromSchema[Address]
        val result  = decoder.decode(encoder.encode(address))
        assertTrue(result == Right(address))
      },
      test("round-trip primitive Int") {
        val value   = 42
        val encoder = XmlEncoder.fromSchema[Int]
        val decoder = XmlDecoder.fromSchema[Int]
        val result  = decoder.decode(encoder.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip primitive String") {
        val value   = "Hello, World!"
        val encoder = XmlEncoder.fromSchema[String]
        val decoder = XmlDecoder.fromSchema[String]
        val result  = decoder.decode(encoder.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip Option[String] Some") {
        val value   = Some("test")
        val encoder = XmlEncoder.fromSchema[Option[String]]
        val decoder = XmlDecoder.fromSchema[Option[String]]
        val result  = decoder.decode(encoder.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip Option[String] None") {
        val value   = None
        val encoder = XmlEncoder.fromSchema[Option[String]]
        val decoder = XmlDecoder.fromSchema[Option[String]]
        val result  = decoder.decode(encoder.encode(value))
        assertTrue(result == Right(value))
      }
    )
  )
}
