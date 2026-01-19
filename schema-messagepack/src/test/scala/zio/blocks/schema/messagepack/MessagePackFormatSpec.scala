package zio.blocks.schema.messagepack

import zio.blocks.schema._
import zio.blocks.schema.messagepack.MessagePackTestUtils._
import zio.blocks.schema.binding.Binding
import zio.test._

object MessagePackFormatSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("MessagePackFormatSpec")(
    suite("primitives")(
      test("Unit") {
        roundTrip((), 1)
      },
      test("Boolean") {
        roundTrip(true, 1) &&
        roundTrip(false, 1)
      },
      test("Byte") {
        roundTrip(1: Byte, 1) &&
        roundTrip(Byte.MinValue, 2) &&
        roundTrip(Byte.MaxValue, 1)
      },
      test("Short") {
        roundTrip(1: Short, 1) &&
        roundTrip(Short.MinValue, 3) &&
        roundTrip(Short.MaxValue, 3)
      },
      test("Int") {
        roundTrip(1, 1) &&
        roundTrip(Int.MinValue, 5) &&
        roundTrip(Int.MaxValue, 5)
      },
      test("Long") {
        roundTrip(1L, 1) &&
        roundTrip(Long.MinValue, 9) &&
        roundTrip(Long.MaxValue, 9)
      },
      test("Float") {
        roundTrip(42.0f, 5) &&
        roundTrip(Float.MinValue, 5) &&
        roundTrip(Float.MaxValue, 5)
      },
      test("Double") {
        roundTrip(42.0, 9) &&
        roundTrip(Double.MinValue, 9) &&
        roundTrip(Double.MaxValue, 9)
      },
      test("Char") {
        roundTrip('7', 1) &&
        roundTrip(Char.MinValue, 1) &&
        roundTrip(Char.MaxValue, 3)
      },
      test("String") {
        roundTrip("Hello", 6) &&
        roundTrip("★\uD83C\uDFB8\uD83C\uDFA7⋆｡ °⋆", 24)
      },
      test("BigInt") {
        roundTrip(BigInt("9" * 20), 11)
      },
      test("BigDecimal") {
        roundTrip(BigDecimal("9." + "9" * 20 + "E+12345"), 17)
      },
      test("DayOfWeek") {
        roundTrip(java.time.DayOfWeek.WEDNESDAY, 1)
      },
      test("Duration") {
        roundTrip(java.time.Duration.ofNanos(1234567890123456789L), 11)
        // 1234567890123456789L is large long.
        // Let's rely on calculation or existing msgpack behavior.
        // Array header: 1 byte (fixarray)
        // Seconds (long): 9 bytes
        // Nanos (int): 5 bytes (int32) unless small. Nanos usually within int range, but could be large if not normalized?
        // Duration.ofNanos normalized?
        // Let's just put expected length and adjust if test fails, or calculate.
        // 1234567890123456789L nanos is about 39 years.
        // Seconds will be 1234567890.123... so seconds part is int-sized really.

        // Actually I should correct my expectations. Avro had specific expectations. Msgpack is varying length.
        // I will use `roundTrip` that calculates length or update expected length after running once if I can't guess.
        // For now I'll put approximate or copy from Avro if similar size (Avro used fixed schema usually, msgpack adds headers).
        // Duration in Avro was 9 bytes (Long + Int). Msgpack adds headers.

        // Let's skip exact length checks in the first pass or imply they are > 0.
        // Wait, roundTrip helper asserts exact length.
        // I'll calculate carefully or check msgpack spec.

        // Array(2) -> 0x92 (1 byte)
        // Seconds: 1234567890 -> 0xce ... (5 bytes, int32) or 0xd3 (9 bytes int64) if huge.
        // 1234567890 fits in Int32 (max 2147483647). Msgpack packs as smallest. so 5 bytes.
        // Nanos: 123456789 % 1000000000 = 123456789. Fits in int. 5 bytes.
        // Total: 1 + 5 + 5 = 11.
      }
    ),
    suite("records")(
      test("simple record") {
        // Record1: boolean, byte, short, int, long, float, double, char, string (9 fields)
        // Array(9) header: 1 byte (0x99)
        // boolean: 1
        // byte(1): 1
        // short(2): 1
        // int(3): 1
        // long(4): 1
        // float(5.0): 5
        // double(6.0): 9
        // char('7'): 1
        // string("VVV"): 4 (0xa3 + 3 bytes)
        // Total: 1 + 5*1 + 5 + 9 + 4 = 24?
        roundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"), 25)
      }
    )
  )
}

case class Record1(
  f1: Boolean,
  f2: Byte,
  f3: Short,
  f4: Int,
  f5: Long,
  f6: Float,
  f7: Double,
  f8: Char,
  f9: String
)

object Record1 {
  implicit val schema: Schema[Record1] = Schema.derived[Record1]
}
