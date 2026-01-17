package zio.blocks.schema.msgpack

import zio.blocks.schema._
import zio.test._

/**
 * Tests for MessagePack binary codec.
 *
 * Key test categories:
 *   - Primitive type roundtrips
 *   - Record encoding/decoding
 *   - Variant encoding/decoding
 *   - Sequence and Map support
 *   - Forward compatibility (unknown field handling)
 */
object MessagePackFormatSpec extends ZIOSpecDefault {

  override def spec = suite("MessagePackFormatSpec")(
    suite("Primitive types")(
      test("Boolean roundtrip") {
        val codec   = Schema[Boolean].derive(MessagePackFormat.deriver)
        val encoded = codec.encode(true)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(true))
      },
      test("Int roundtrip") {
        val codec   = Schema[Int].derive(MessagePackFormat.deriver)
        val encoded = codec.encode(42)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(42))
      },
      test("Long roundtrip") {
        val codec   = Schema[Long].derive(MessagePackFormat.deriver)
        val encoded = codec.encode(123456789L)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(123456789L))
      },
      test("Double roundtrip") {
        val codec   = Schema[Double].derive(MessagePackFormat.deriver)
        val encoded = codec.encode(3.14159)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(3.14159))
      },
      test("String roundtrip") {
        val codec   = Schema[String].derive(MessagePackFormat.deriver)
        val encoded = codec.encode("Hello, MessagePack!")
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right("Hello, MessagePack!"))
      },
      test("BigInt roundtrip") {
        val codec   = Schema[BigInt].derive(MessagePackFormat.deriver)
        val value   = BigInt("12345678901234567890")
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(value))
      },
      test("BigDecimal roundtrip") {
        val codec   = Schema[BigDecimal].derive(MessagePackFormat.deriver)
        val value   = BigDecimal("12345.67890123456789")
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(value))
      },
      test("UUID roundtrip") {
        val codec   = Schema[java.util.UUID].derive(MessagePackFormat.deriver)
        val value   = java.util.UUID.randomUUID()
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("Time types")(
      test("Instant roundtrip") {
        val codec   = Schema[java.time.Instant].derive(MessagePackFormat.deriver)
        val value   = java.time.Instant.now()
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(value))
      },
      test("LocalDate roundtrip") {
        val codec   = Schema[java.time.LocalDate].derive(MessagePackFormat.deriver)
        val value   = java.time.LocalDate.of(2025, 1, 17)
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(value))
      },
      test("LocalDateTime roundtrip") {
        val codec   = Schema[java.time.LocalDateTime].derive(MessagePackFormat.deriver)
        val value   = java.time.LocalDateTime.of(2025, 1, 17, 10, 30, 0)
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(value))
      },
      test("Duration roundtrip") {
        val codec   = Schema[java.time.Duration].derive(MessagePackFormat.deriver)
        val value   = java.time.Duration.ofHours(1).plusMinutes(30)
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(value))
      },
      test("ZonedDateTime roundtrip") {
        val codec   = Schema[java.time.ZonedDateTime].derive(MessagePackFormat.deriver)
        val value   = java.time.ZonedDateTime.now()
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(value))
      }
    ),
    suite("Collection types")(
      test("List[Int] roundtrip") {
        val codec   = Schema[List[Int]].derive(MessagePackFormat.deriver)
        val value   = List(1, 2, 3, 4, 5)
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(value))
      },
      test("Vector[String] roundtrip") {
        val codec   = Schema[Vector[String]].derive(MessagePackFormat.deriver)
        val value   = Vector("a", "b", "c")
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(value))
      },
      test("Map[String, Int] roundtrip") {
        val codec   = Schema[Map[String, Int]].derive(MessagePackFormat.deriver)
        val value   = Map("one" -> 1, "two" -> 2, "three" -> 3)
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(value))
      }
    )
  )
}
