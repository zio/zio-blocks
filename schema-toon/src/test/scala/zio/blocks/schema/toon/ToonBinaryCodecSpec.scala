package zio.blocks.schema.toon

import zio.blocks.schema._
import zio.test._

object ToonBinaryCodecSpec extends ZIOSpecDefault {
  // Simple case classes for testing
  case class Person(name: String, age: Int)

  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class Address(street: String, city: String)

  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class PersonWithAddress(name: String, age: Int, address: Address)

  object PersonWithAddress {
    implicit val schema: Schema[PersonWithAddress] = Schema.derived
  }

  override def spec: Spec[TestEnvironment, Any] = suite("ToonBinaryCodec")(
    suite("Primitive encoding")(
      test("encodes unit") {
        val encoded = ToonBinaryCodec.unitCodec.encodeToString(())
        assertTrue(encoded == "null")
      },
      test("encodes boolean true") {
        val encoded = ToonBinaryCodec.booleanCodec.encodeToString(true)
        assertTrue(encoded == "true")
      },
      test("encodes boolean false") {
        val encoded = ToonBinaryCodec.booleanCodec.encodeToString(false)
        assertTrue(encoded == "false")
      },
      test("encodes int") {
        val encoded = ToonBinaryCodec.intCodec.encodeToString(42)
        assertTrue(encoded == "42")
      },
      test("encodes negative int") {
        val encoded = ToonBinaryCodec.intCodec.encodeToString(-123)
        assertTrue(encoded == "-123")
      },
      test("encodes long") {
        val encoded = ToonBinaryCodec.longCodec.encodeToString(9876543210L)
        assertTrue(encoded == "9876543210")
      },
      test("encodes float") {
        val encoded = ToonBinaryCodec.floatCodec.encodeToString(3.14f)
        assertTrue(encoded == "3.14")
      },
      test("encodes float NaN as null") {
        val encoded = ToonBinaryCodec.floatCodec.encodeToString(Float.NaN)
        assertTrue(encoded == "null")
      },
      test("encodes double") {
        val encoded = ToonBinaryCodec.doubleCodec.encodeToString(2.718281828)
        assertTrue(encoded == "2.718281828")
      },
      test("encodes string without quotes when safe") {
        val encoded = ToonBinaryCodec.stringCodec.encodeToString("hello")
        assertTrue(encoded == "hello")
      },
      test("encodes string with quotes when needed") {
        val encoded = ToonBinaryCodec.stringCodec.encodeToString("hello, world")
        assertTrue(encoded == "\"hello, world\"")
      },
      test("encodes BigInt") {
        val encoded = ToonBinaryCodec.bigIntCodec.encodeToString(BigInt("12345678901234567890"))
        assertTrue(encoded == "12345678901234567890")
      },
      test("encodes BigDecimal") {
        val encoded = ToonBinaryCodec.bigDecimalCodec.encodeToString(BigDecimal("123.456789"))
        assertTrue(encoded == "123.456789")
      }
    ),
    suite("Primitive round-trip")(
      test("boolean round-trips") {
        val codec   = ToonBinaryCodec.booleanCodec
        val decoded = codec.decodeBytes(codec.encodeToBytes(true), 0, 4)
        assertTrue(decoded == Right(true))
      },
      test("int round-trips") {
        val codec   = ToonBinaryCodec.intCodec
        val bytes   = codec.encodeToBytes(42)
        val decoded = codec.decodeBytes(bytes, 0, bytes.length)
        assertTrue(decoded == Right(42))
      },
      test("long round-trips") {
        val codec   = ToonBinaryCodec.longCodec
        val bytes   = codec.encodeToBytes(9876543210L)
        val decoded = codec.decodeBytes(bytes, 0, bytes.length)
        assertTrue(decoded == Right(9876543210L))
      },
      test("string round-trips") {
        val codec   = ToonBinaryCodec.stringCodec
        val bytes   = codec.encodeToBytes("hello")
        val decoded = codec.decodeBytes(bytes, 0, bytes.length)
        assertTrue(decoded == Right("hello"))
      }
    ),
    suite("Record encoding")(
      test("encodes simple record") {
        val codec   = ToonFormat.codec[Person]
        val encoded = codec.encodeToString(Person("Alice", 30))
        assertTrue(
          encoded == "name: Alice\nage: 30"
        )
      },
      test("encodes nested record") {
        val codec   = ToonFormat.codec[PersonWithAddress]
        val encoded = codec.encodeToString(
          PersonWithAddress("Bob", 25, Address("123 Main St", "Springfield"))
        )
        // Expected format with nested indentation
        assertTrue(
          encoded.contains("name: Bob") &&
            encoded.contains("age: 25") &&
            encoded.contains("address:") &&
            encoded.contains("  street: 123 Main St") &&
            encoded.contains("  city: Springfield")
        )
      }
    ),
    suite("Array encoding")(
      test("encodes int list inline") {
        val codec   = ToonFormat.codec[List[Int]]
        val encoded = codec.encodeToString(List(1, 2, 3))
        assertTrue(encoded == "[3]: 1,2,3")
      },
      test("encodes string list inline") {
        val codec   = ToonFormat.codec[List[String]]
        val encoded = codec.encodeToString(List("a", "b", "c"))
        assertTrue(encoded == "[3]: a,b,c")
      },
      test("encodes empty list") {
        val codec   = ToonFormat.codec[List[Int]]
        val encoded = codec.encodeToString(List.empty[Int])
        assertTrue(encoded == "[0]: ")
      }
    )
  )
}
