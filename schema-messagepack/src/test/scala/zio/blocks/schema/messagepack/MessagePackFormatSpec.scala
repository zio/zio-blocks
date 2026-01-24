package zio.blocks.schema.messagepack

import zio.blocks.schema._
import org.msgpack.core.MessagePack
import zio.blocks.schema.messagepack.MessagePackTestUtils._
import zio.test.Assertion._
import zio.test._

import java.util.UUID
import java.util.Currency

object MessagePackFormatSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("MessagePackFormatSpec")(
    suite("Bug Fixes")(
      test("BUG-001 & BUG-003: Enum index validation") {
        val codec = Color.schema.derive(MessagePackFormat.deriver)
        def checkIndex(idx: Int) = {
          val packer = MessagePack.newDefaultBufferPacker()
          packer.packInt(idx)
          val bytes = packer.toByteArray
          assert(codec.decode(bytes))(isLeft)
        }
        checkIndex(-1) && checkIndex(3) // Valid indices are 0, 1, 2
      },
      test("BUG-005: DayOfWeek range validation") {
        val codec = Schema[java.time.DayOfWeek].derive(MessagePackFormat.deriver)
        def checkValue(v: Int) = {
          val packer = MessagePack.newDefaultBufferPacker()
          packer.packInt(v)
          val bytes = packer.toByteArray
          assert(codec.decode(bytes))(isLeft)
        }
        checkValue(0) && checkValue(8)
      },
      test("BUG-005: Month range validation") {
        val codec = Schema[java.time.Month].derive(MessagePackFormat.deriver)
        def checkValue(v: Int) = {
          val packer = MessagePack.newDefaultBufferPacker()
          packer.packInt(v)
          val bytes = packer.toByteArray
          assert(codec.decode(bytes))(isLeft)
        }
        checkValue(0) && checkValue(13)
      },
      test("BUG-016: Optional size validation") {
        val codec = Schema[Option[Int]].derive(MessagePackFormat.deriver)
        val packer = MessagePack.newDefaultBufferPacker()
        packer.packArrayHeader(2)
        packer.packInt(1)
        packer.packInt(2)
        val bytes = packer.toByteArray
        assert(codec.decode(bytes))(isLeft)
      }
    ),
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
        roundTrip(42.0f, 5)
      },
      test("Double") {
        roundTrip(42.0, 9)
      },
      test("Char") {
        roundTrip('7', 2)
      },
      test("String") {
        roundTrip("Hello", 6) &&
        roundTrip("★\uD83C\uDFB8\uD83C\uDFA7⋆｡ °⋆", 24)
      },
      test("BigInt") {
        roundTrip(BigInt("9" * 20), 11)
      },
      test("BigDecimal") {
        roundTrip(BigDecimal("9." + "9" * 20 + "E+12"), 53)
      },
      test("DayOfWeek") {
        roundTrip(java.time.DayOfWeek.WEDNESDAY, 1)
      },
      test("Duration") {
        roundTrip(java.time.Duration.ofNanos(1234567890123456789L), 11)
      },
      test("Instant") {
        roundTrip(java.time.Instant.parse("2025-07-18T08:29:13.121409459Z"), 11)
      },
      test("LocalDate") {
        roundTrip(java.time.LocalDate.parse("2025-07-18"), 6)
      },
      test("LocalDateTime") {
        roundTrip(java.time.LocalDateTime.parse("2025-07-18T08:29:13.121409459"), 14)
      },
      test("LocalTime") {
        roundTrip(java.time.LocalTime.parse("08:29:13.121409459"), 9)
      },
      test("Month") {
        roundTrip(java.time.Month.of(12), 1)
      },
      test("MonthDay") {
        roundTrip(java.time.MonthDay.of(12, 31), 3)
      },
      test("OffsetDateTime") {
        roundTrip(java.time.OffsetDateTime.parse("2025-07-18T08:29:13.121409459-07:00"), 17)
      },
      test("OffsetTime") {
        roundTrip(java.time.OffsetTime.parse("08:29:13.121409459-07:00"), 12)
      },
      test("Period") {
        roundTrip(java.time.Period.of(1, 12, 31), 4)
      },
      test("Year") {
        roundTrip(java.time.Year.of(2025), 3)
      },
      test("YearMonth") {
        roundTrip(java.time.YearMonth.of(2025, 7), 5)
      },
      test("ZoneId") {
        roundTrip(java.time.ZoneId.of("UTC"), 4)
      },
      test("ZoneOffset") {
        roundTrip(java.time.ZoneOffset.ofTotalSeconds(3600), 3)
      },
      test("ZonedDateTime") {
        roundTrip(java.time.ZonedDateTime.parse("2025-07-18T08:29:13.121409459+02:00[Europe/Warsaw]"), 31)
      },
      test("Currency") {
        roundTrip(Currency.getInstance("USD"), 4)
      },
      test("UUID") {
        roundTrip(UUID.randomUUID(), 38)
      }
    ),
    suite("records")(
      test("simple record") {
        roundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"), 46)
      },
      test("nested record") {
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          ),
          103
        )
      },
      test("recursive record") {
        roundTrip(Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))), 24)
      }
    ),
    suite("variants")(
      test("option") {
        roundTrip[Option[Int]](Some(42), 9) &&
        roundTrip[Option[Int]](None, 2)
      },
      test("either") {
        roundTrip[Either[String, Int]](Right(42), 9) &&
        roundTrip[Either[String, Int]](Left("VVV"), 12)
      }
    ),
    suite("sequences")(
      test("List[Int]") {
        roundTrip(List(1, 2, 3), 4)
      },
      test("Array[Int]") {
        implicit val arrayOfIntSchema: Schema[Array[Int]] = Schema.derived
        roundTrip(Array[Int](1, 2, 3), 4)
      },
      test("Vector[Long]") {
        roundTrip(Vector(1L, 2L, 3L), 4)
      }
    ),
    suite("maps")(
      test("string keys") {
        roundTrip(Map("a" -> 1, "b" -> 2), 7)
      },
      test("non-string keys") {
        roundTrip(Map(1 -> 10L, 2 -> 20L), 5)
      }
    ),
    suite("dynamic value")(
      test("primitive") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Int(42)), 3)
      },
      test("record") {
        roundTrip[DynamicValue](
          DynamicValue.Record(
            Vector(
              "i" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
              "s" -> DynamicValue.Primitive(PrimitiveValue.String("VVV"))
            )
          ),
          15
        )
      }
    )
  )

  case class Record1(bl: Boolean, b: Byte, sh: Short, i: Int, l: Long, f: Float, d: Double, c: Char, s: String)
  object Record1 {
    implicit val schema: Schema[Record1] = Schema.derived
  }

  case class Record2(r1_1: Record1, r1_2: Record1)
  object Record2 {
    implicit val schema: Schema[Record2] = Schema.derived
  }

  case class Recursive(i: Int, ln: List[Recursive])
  object Recursive {
    implicit val schema: Schema[Recursive] = Schema.derived
  }

  sealed trait Color
  object Color {
    case object Red extends Color
    case object Green extends Color
    case object Blue extends Color
    implicit val schema: Schema[Color] = Schema.derived
  }

  implicit val optionSchema: Schema[Option[Int]] = Schema.derived
  implicit val eitherSchema: Schema[Either[String, Int]] = Schema.derived
}
