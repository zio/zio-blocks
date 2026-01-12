package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema._
import java.nio.charset.StandardCharsets

object ToonSpec extends ZIOSpecDefault {

  // Test helpers
  private def encode[A](codec: ToonBinaryCodec[A], value: A): String = {
    val writer = new ToonWriter(new Array[Byte](4096), ToonWriterConfig)
    codec.encodeValue(value, writer)
    new String(writer.buf, 0, writer.count, StandardCharsets.UTF_8)
  }

  private def decode[A](codec: ToonBinaryCodec[A], input: String): Either[SchemaError, A] =
    codec.decodeFromString(input)

  private def roundTrip[A](codec: ToonBinaryCodec[A], value: A): Either[SchemaError, A] = {
    val encoded = encode(codec, value)
    decode(codec, encoded)
  }

  def spec = suite("ToonSpec")(
    // ==================== PRIMITIVE TESTS ====================
    suite("Primitives")(
      test("encode Int") {
        val codec   = ToonBinaryCodec.intCodec
        val encoded = codec.encodeToString(42)
        assertTrue(encoded == "42")
      },
      test("15 billion encodes without scientific notation") {
        val codec   = ToonBinaryCodec.doubleCodec
        val encoded = codec.encodeToString(15000000000.0)
        assertTrue(encoded == "15000000000")
      },
      test("decode Int") {
        val result = decode(ToonBinaryCodec.intCodec, "123")
        assertTrue(result == Right(123))
      },
      test("encode/decode negative Int") {
        val result = roundTrip(ToonBinaryCodec.intCodec, -999)
        assertTrue(result == Right(-999))
      },
      test("encode String simple") {
        val result = encode(ToonBinaryCodec.stringCodec, "hello")
        assertTrue(result == "hello")
      },
      test("encode String with spaces") {
        val result = encode(ToonBinaryCodec.stringCodec, "hello world")
        assertTrue(result == "hello world")
      },
      test("decode String") {
        val result = decode(ToonBinaryCodec.stringCodec, "\"test value\"")
        assertTrue(result == Right("test value"))
      },
      test("encode Boolean true") {
        val result = encode(ToonBinaryCodec.booleanCodec, true)
        assertTrue(result == "true")
      },
      test("encode Boolean false") {
        val result = encode(ToonBinaryCodec.booleanCodec, false)
        assertTrue(result == "false")
      },
      test("decode Boolean true") {
        val result = decode(ToonBinaryCodec.booleanCodec, "true")
        assertTrue(result == Right(true))
      },
      test("decode Boolean false") {
        val result = decode(ToonBinaryCodec.booleanCodec, "false")
        assertTrue(result == Right(false))
      },
      test("encode Long") {
        val result = encode(ToonBinaryCodec.longCodec, 9223372036854775807L)
        assertTrue(result == "9223372036854775807")
      },
      test("decode Long") {
        val result = decode(ToonBinaryCodec.longCodec, "9223372036854775807")
        assertTrue(result == Right(9223372036854775807L))
      },
      test("encode Double") {
        val result = encode(ToonBinaryCodec.doubleCodec, 3.14159)
        assertTrue(result == "3.14159")
      },
      test("encode Float") {
        val result = encode(ToonBinaryCodec.floatCodec, 2.5f)
        assertTrue(result == "2.5")
      },
      test("encode Byte") {
        val result = encode(ToonBinaryCodec.byteCodec, 127.toByte)
        assertTrue(result == "127")
      },
      test("encode Short") {
        val result = encode(ToonBinaryCodec.shortCodec, 32767.toShort)
        assertTrue(result == "32767")
      },
      test("encode Char") {
        val result = encode(ToonBinaryCodec.charCodec, 'A')
        assertTrue(result == "A")
      },
      test("encode Unit") {
        val result = encode(ToonBinaryCodec.unitCodec, ())
        assertTrue(result == "null")
      }
    ),

    // ==================== SCHEMA INTEGRATION TESTS ====================
    suite("Schema Integration")(
      test("Schema.int derives codec") {
        val codec  = Schema.int.derive(ToonFormat.deriver)
        val result = encode(codec, 42)
        assertTrue(result == "42")
      },
      test("Schema.string derives codec") {
        val codec  = Schema.string.derive(ToonFormat.deriver)
        val result = encode(codec, "hello")
        assertTrue(result == "hello")
      },
      test("Schema.boolean derives codec") {
        val codec  = Schema.boolean.derive(ToonFormat.deriver)
        val result = encode(codec, true)
        assertTrue(result == "true")
      },
      test("Schema.long derives codec") {
        val codec  = Schema.long.derive(ToonFormat.deriver)
        val result = encode(codec, 123456789L)
        assertTrue(result == "123456789")
      },
      test("Schema.double derives codec") {
        val codec  = Schema.double.derive(ToonFormat.deriver)
        val result = encode(codec, 1.5)
        assertTrue(result == "1.5")
      }
    ),

    // ==================== DATE/TIME TESTS ====================
    suite("java.time Types")(
      test("encode Instant") {
        import java.time.Instant
        val instant = Instant.parse("2024-01-15T10:30:00Z")
        val result  = encode(ToonBinaryCodec.instantCodec, instant)
        assertTrue(result == "2024-01-15T10:30:00Z")
      },
      test("encode LocalDate") {
        import java.time.LocalDate
        val date   = LocalDate.of(2024, 1, 15)
        val result = encode(ToonBinaryCodec.localDateCodec, date)
        assertTrue(result == "2024-01-15")
      },
      test("encode LocalTime") {
        import java.time.LocalTime
        val time   = LocalTime.of(10, 30, 45)
        val result = encode(ToonBinaryCodec.localTimeCodec, time)
        assertTrue(result == "10:30:45")
      },
      test("encode Duration") {
        import java.time.Duration
        val duration = Duration.ofHours(2).plusMinutes(30)
        val result   = encode(ToonBinaryCodec.durationCodec, duration)
        assertTrue(result == "PT2H30M")
      }
    ),

    // ==================== OTHER TYPES TESTS ====================
    suite("Other Types")(
      test("encode UUID") {
        import java.util.UUID
        val uuid   = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val result = encode(ToonBinaryCodec.uuidCodec, uuid)
        assertTrue(result == "550e8400-e29b-41d4-a716-446655440000")
      },
      test("encode BigInt") {
        val bi     = BigInt("12345678901234567890")
        val result = encode(ToonBinaryCodec.bigIntCodec, bi)
        assertTrue(result == "12345678901234567890")
      },
      test("encode BigDecimal") {
        val bd     = BigDecimal("123456.789")
        val result = encode(ToonBinaryCodec.bigDecimalCodec, bd)
        assertTrue(result == "123456.789")
      },
      test("encode Currency") {
        import java.util.Currency
        val currency = Currency.getInstance("USD")
        val result   = encode(ToonBinaryCodec.currencyCodec, currency)
        assertTrue(result == "USD")
      }
    ),

    // ==================== LIST SCHEMA TESTS ====================
    suite("List/Collection Integration")(
      test("Schema.list[Int] derives codec") {
        val codec  = Schema.list[Int].derive(ToonFormat.deriver)
        val result = encode(codec, List(1, 2, 3))
        assertTrue(result == "[3]: 1,2,3")
      },
      test("Schema.vector[String] derives codec") {
        val codec    = Schema.vector[String].derive(ToonFormat.deriver)
        val result   = encode(codec, Vector("a", "b"))
        val expected = "[2]: a,b"
        assertTrue(result == expected)
      }
    )
  )
}
