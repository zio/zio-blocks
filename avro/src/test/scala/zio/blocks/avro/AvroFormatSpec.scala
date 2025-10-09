package zio.blocks.avro

import zio.blocks.schema.Schema
import zio.test.Assertion._
import zio.test._
import java.nio.ByteBuffer
import java.util
import java.util.UUID

object AvroFormatSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("AvroFormatSpec")(
    suite("primitives")(
      test("Unit") {
        roundTrip((), 0)
      },
      test("Boolean") {
        roundTrip(true, 1)
      },
      test("Byte") {
        roundTrip(123: Byte, 2)
      },
      test("Short") {
        roundTrip(12345: Short, 3)
      },
      test("Int") {
        roundTrip(1234567890, 5)
      },
      test("Long") {
        roundTrip(1234567890123456789L, 9)
      },
      test("Float") {
        roundTrip(42.0f, 4)
      },
      test("Double") {
        roundTrip(42.0, 8)
      },
      test("Char") {
        roundTrip('a', 2)
      },
      test("String") {
        roundTrip("Hello", 6)
      },
      test("BigInt") {
        roundTrip(BigInt("9" * 20), 10)
      },
      test("BigDecimal") {
        roundTrip(BigDecimal("9." + "9" * 20 + "E+12345"), 15)
      },
      test("DayOfWeek") {
        roundTrip(java.time.DayOfWeek.WEDNESDAY, 1)
      },
      test("Duration") {
        roundTrip(java.time.Duration.ofNanos(1234567890123456789L), 9)
      },
      test("Instant") {
        roundTrip(java.time.Instant.parse("2025-07-18T08:29:13.121409459Z"), 9)
      },
      test("LocalDate") {
        roundTrip(java.time.LocalDate.parse("2025-07-18"), 4)
      },
      test("LocalDateTime") {
        roundTrip(java.time.LocalDateTime.parse("2025-07-18T08:29:13.121409459"), 11)
      },
      test("LocalTime") {
        roundTrip(java.time.LocalTime.parse("08:29:13.121409459"), 7)
      },
      test("Month") {
        roundTrip(java.time.Month.of(12), 1)
      },
      test("MonthDay") {
        roundTrip(java.time.MonthDay.of(12, 31), 2)
      },
      test("OffsetDateTime") {
        roundTrip(java.time.OffsetDateTime.parse("2025-07-18T08:29:13.121409459-07:00"), 14)
      },
      test("OffsetTime") {
        roundTrip(java.time.OffsetTime.parse("08:29:13.121409459-07:00"), 10)
      },
      test("Period") {
        roundTrip(java.time.Period.of(1, 12, 31), 3)
      },
      test("Year") {
        roundTrip(java.time.Year.of(2025), 2)
      },
      test("YearMonth") {
        roundTrip(java.time.YearMonth.of(2025, 7), 3)
      },
      test("ZoneId") {
        roundTrip(java.time.ZoneId.of("UTC"), 4)
      },
      test("ZoneOffset") {
        roundTrip(java.time.ZoneOffset.ofTotalSeconds(0), 1)
      },
      test("ZonedDateTime") {
        roundTrip(java.time.ZonedDateTime.parse("2025-07-18T08:29:13.121409459+02:00[Europe/Warsaw]"), 27)
      },
      test("Currency") {
        roundTrip(java.util.Currency.getInstance("USD"), 3)
      },
      test("UUID") {
        roundTrip(UUID.randomUUID(), 16)
      }
    ),
    suite("records")(
      test("simple record") {
        roundTrip(Record(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV" /*, null*/ ), 22)
      }
      /*
    ),
    suite("enums")(
      test("option") {
        roundTrip(Option(42), 2)
      }
       */
    )
  )

  def roundTrip[A: Schema](value: A, expectedLength: Int): TestResult = {
    val result = encodeToByteArray(out => Schema[A].encode(AvroFormat)(out)(value))
    assert(result.length)(equalTo(expectedLength)) &&
    assert(Schema[A].decode(AvroFormat)(toHeapByteBuffer(result)))(isRight(equalTo(value))) &&
    assert(Schema[A].decode(AvroFormat)(toDirectByteBuffer(result)))(isRight(equalTo(value)))
  }

  def encodeToByteArray(f: ByteBuffer => Unit): Array[Byte] = {
    val byteBuffer = ByteBuffer.allocate(1024)
    f(byteBuffer)
    util.Arrays.copyOf(byteBuffer.array, byteBuffer.position)
  }

  def toHeapByteBuffer(bs: Array[Byte]): ByteBuffer = ByteBuffer.wrap(bs)

  def toDirectByteBuffer(bs: Array[Byte]): ByteBuffer =
    ByteBuffer.allocateDirect(1024).put(bs).position(0).limit(bs.length)

  case class Record(
    bl: Boolean,
    b: Byte,
    sh: Short,
    i: Int,
    l: Long,
    f: Float,
    d: Double,
    c: Char,
    s: String /*,
    r: Record*/
  )

  object Record {
    implicit val schemaRecord: Schema[Record] = Schema.derived
  }
}
