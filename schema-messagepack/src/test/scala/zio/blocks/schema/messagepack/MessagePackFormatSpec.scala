package zio.blocks.schema.messagepack

import org.msgpack.core.{MessagePacker, MessageUnpacker}
import zio.blocks.schema._

import zio.test._
import zio.blocks.schema.messagepack.MessagePackTestUtils._

import java.time._
import java.util.{Currency, UUID}
import scala.collection.immutable.ArraySeq

object MessagePackFormatSpec extends ZIOSpecDefault {



  case class Record1(bl: Boolean, b: Byte, sh: Short, i: Int, l: Long, f: Float, d: Double, c: Char, s: String)
  object Record1 {
    implicit val schema: Schema[Record1] = Schema.derived
  }

  case class Record2(r1_1: Record1, r1_2: Record1)
  object Record2 {
    implicit val schema: Schema[Record2] = Schema.derived

  }

  case class Record3(userId: UserId, email: Email)
  object Record3 {
    implicit val schema: Schema[Record3] = Schema.derived
  }

  case class Record4(hidden: Unit, optKey: Option[String])
  object Record4 {
    implicit val schema: Schema[Record4] = Schema.derived

  }

  case class Recursive(i: Int, ln: List[Recursive])
  object Recursive {
    implicit lazy val schema: Schema[Recursive] = Schema.derived

  }

  sealed trait TrafficLight
  object TrafficLight {
    case object Red    extends TrafficLight
    case object Yellow extends TrafficLight
    case object Green  extends TrafficLight
    implicit val schema: Schema[TrafficLight] = Schema.derived
  }

  case class UserId(value: Long)
  object UserId {
    implicit val schema: Schema[UserId] = Schema.derived
  }

  case class Email(value: String)
  object Email {
    private val emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".r

    def apply(value: String): Email    = new Email(value)
    implicit val schema: Schema[Email] = Schema
      .derived[Email]
      .wrap[String](
        email => if (emailRegex.matches(email)) Right(new Email(email)) else Left(s"Invalid email: $email"),
        email => email.value
      )
  }

  def spec: Spec[TestEnvironment, Any] = suite("MessagePackFormatSpec")(
    suite("primitives")(
      test("Unit") {
        roundTrip(())
      },
      test("Boolean") {
        roundTrip(true) && roundTrip(false)
      },
      test("Byte") {
        roundTrip(1: Byte) && roundTrip(Byte.MinValue) && roundTrip(Byte.MaxValue)
      },
      test("Short") {
        roundTrip(1: Short) && roundTrip(Short.MinValue) && roundTrip(Short.MaxValue)
      },
      test("Int") {
        roundTrip(1) && roundTrip(Int.MinValue) && roundTrip(Int.MaxValue)
      },
      test("Long") {
        roundTrip(1L) && roundTrip(Long.MinValue) && roundTrip(Long.MaxValue)
      },
      test("Float") {
        roundTrip(42.0f) && roundTrip(Float.MinValue) && roundTrip(Float.MaxValue)
      },
      test("Double") {
        roundTrip(42.0) && roundTrip(Double.MinValue) && roundTrip(Double.MaxValue)
      },
      test("Char") {
        roundTrip('7') && roundTrip(Char.MinValue) && roundTrip(Char.MaxValue)
      },
      test("String") {
        roundTrip("Hello") && roundTrip("") && roundTrip("Unicode: \u2605\uD83C\uDFB8")
      },
      test("BigInt") {
        roundTrip(BigInt("9" * 20)) && roundTrip(BigInt(-123456789))
      },
      test("BigDecimal") {
        roundTrip(BigDecimal("9." + "9" * 20 + "E+12345"))
      },
      test("DayOfWeek") {
        roundTrip(java.time.DayOfWeek.WEDNESDAY)
      },
      test("Duration") {
        roundTrip(java.time.Duration.ofNanos(1234567890123456789L))
      },
      test("Instant") {
        roundTrip(java.time.Instant.parse("2025-07-18T08:29:13.121409459Z"))
      },
      test("LocalDate") {
        roundTrip(java.time.LocalDate.parse("2025-07-18"))
      },
      test("LocalDateTime") {
        roundTrip(java.time.LocalDateTime.parse("2025-07-18T08:29:13.121409459"))
      },
      test("LocalTime") {
        roundTrip(java.time.LocalTime.parse("08:29:13.121409459"))
      },
      test("Month") {
        roundTrip(java.time.Month.of(12))
      },
      test("MonthDay") {
        roundTrip(java.time.MonthDay.of(12, 31))
      },
      test("OffsetDateTime") {
        roundTrip(java.time.OffsetDateTime.parse("2025-07-18T08:29:13.121409459-07:00"))
      },
      test("OffsetTime") {
        roundTrip(java.time.OffsetTime.parse("08:29:13.121409459-07:00"))
      },
      test("Period") {
        roundTrip(java.time.Period.of(1, 12, 31))
      },
      test("Year") {
        roundTrip(java.time.Year.of(2025))
      },
      test("YearMonth") {
        roundTrip(java.time.YearMonth.of(2025, 7))
      },
      test("ZoneId") {
        roundTrip(java.time.ZoneId.of("UTC"))
      },
      test("ZoneOffset") {
        roundTrip(java.time.ZoneOffset.ofTotalSeconds(3600))
      },
      test("ZonedDateTime") {
        roundTrip(java.time.ZonedDateTime.parse("2025-07-18T08:29:13.121409459+02:00[Europe/Warsaw]"))
      },
      test("Currency") {
        roundTrip(Currency.getInstance("USD"))
      },
      test("UUID") {
        roundTrip(UUID.randomUUID())
      }
    ),
    suite("records")(
      test("simple record") {
        roundTrip(Record1(true, 1, 2, 3, 4L, 5.0f, 6.0, '7', "eight"))
      },
      test("record with negative values") {
        roundTrip(Record1(false, -1, -2, -3, -4L, -5.0f, -6.0, 'x', "negative"))
      },
      test("nested record") {
        roundTrip(
          Record2(
            Record1(true, 1, 2, 3, 4L, 5.0f, 6.0, '7', "eight"),
            Record1(false, 8, 9, 10, 11L, 12.0f, 13.0, 'f', "fourteen")
          )
        )
      },
      test("record with wrapper types") {
        roundTrip(Record3(UserId(123L), Email("test@example.com")))
      },
      test("record with optional field - Some") {
        roundTrip(Record4((), Some("key")))
      },
      test("record with optional field - None") {
        roundTrip(Record4((), None))
      },
      test("recursive record - single level") {
        roundTrip(Recursive(1, Nil))
      },
      test("recursive record - nested") {
        roundTrip(Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))))
      },
      test("recursive record - multiple children") {
        roundTrip(Recursive(1, List(Recursive(2, Nil), Recursive(3, Nil), Recursive(4, Nil))))
      }
    ),
    suite("sequences")(
      test("List[Int] - empty") {
        roundTrip(List.empty[Int])
      },
      test("List[Int] - non-empty") {
        roundTrip(List(1, 2, 3, 4, 5))
      },
      test("List[String]") {
        roundTrip(List("a", "b", "c"))
      },
      test("Vector[Int]") {
        roundTrip(Vector(1, 2, 3))
      },
      test("Set[Int]") {
        roundTrip(Set(1, 2, 3))
      },
      test("ArraySeq[Byte]") {
        roundTrip[IndexedSeq[Byte]](ArraySeq[Byte](1, 2, 3, 4, 5))
      },
      test("List[Record1]") {
        roundTrip(
          List(
            Record1(true, 1, 2, 3, 4L, 5.0f, 6.0, '7', "eight"),
            Record1(false, 8, 9, 10, 11L, 12.0f, 13.0, 'f', "fourteen")
          )
        )
      },
      test("nested List[List[Int]]") {
        roundTrip(List(List(1, 2), List(3, 4), List(5, 6)))
      }
    ),
    suite("maps")(
      test("Map[String, Int] - empty") {
        roundTrip(Map.empty[String, Int])
      },
      test("Map[String, Int] - non-empty") {
        roundTrip(Map("a" -> 1, "b" -> 2, "c" -> 3))
      },
      test("Map[String, String]") {
        roundTrip(Map("key1" -> "value1", "key2" -> "value2"))
      },
      test("Map[String, Record1]") {
        roundTrip(
          Map(
            "first"  -> Record1(true, 1, 2, 3, 4L, 5.0f, 6.0, '7', "eight"),
            "second" -> Record1(false, 8, 9, 10, 11L, 12.0f, 13.0, 'f', "fourteen")
          )
        )
      },
      test("Map[Int, String] - non-string keys") {
        roundTrip[Map[Int, String]](Map(1 -> "one", 2 -> "two", 3 -> "three"))
      },
      test("nested Map[String, Map[String, Int]]") {
        roundTrip(Map("outer" -> Map("inner" -> 42)))
      }
    ),
    suite("variants")(
      test("sealed trait - Red") {
        roundTrip[TrafficLight](TrafficLight.Red)
      },
      test("sealed trait - Yellow") {
        roundTrip[TrafficLight](TrafficLight.Yellow)
      },
      test("sealed trait - Green") {
        roundTrip[TrafficLight](TrafficLight.Green)
      },
      test("Option[Int] - Some") {
        roundTrip(Some(42): Option[Int])
      },
      test("Option[Int] - None") {
        roundTrip(None: Option[Int])
      },
      test("Option[String] - Some") {
        roundTrip(Some("hello"): Option[String])
      },
      test("Either[String, Int] - Left") {
        roundTrip[Either[String, Int]](Left("error"))
      },
      test("Either[String, Int] - Right") {
        roundTrip[Either[String, Int]](Right(42))
      },
      test("Option[Record1] - Some") {
        roundTrip(Some(Record1(true, 1, 2, 3, 4L, 5.0f, 6.0, '7', "eight")): Option[Record1])
      },
      test("Option[Record1] - None") {
        roundTrip(None: Option[Record1])
      }
    ),
    suite("wrappers")(
      test("UserId wrapper") {
        roundTrip(UserId(123L))
      },
      test("Email wrapper") {
        roundTrip(Email("valid@email.com"))
      },
      test("UserId in record") {
        roundTrip(Record3(UserId(456L), Email("another@email.org")))
      }
    ),
    suite("DynamicValue")(
      test("Primitive - Unit") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Unit))
      },
      test("Primitive - Boolean") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
      },
      test("Primitive - Int") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Int(42)))
      },
      test("Primitive - String") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.String("hello")))
      },
      test("Primitive - BigInt") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt("12345678901234567890"))))
      },
      test("Primitive - BigDecimal") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("123.456"))))
      },
      test("Primitive - UUID") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.UUID(UUID.randomUUID())))
      },
      test("Primitive - Instant") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Instant(Instant.now())))
      },
      test("Record") {
        roundTrip[DynamicValue](
          DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("test")),
              "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
            )
          )
        )
      },
      test("Variant") {
        roundTrip[DynamicValue](DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42))))
      },
      test("Sequence") {
        roundTrip[DynamicValue](
          DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2)),
              DynamicValue.Primitive(PrimitiveValue.Int(3))
            )
          )
        )
      },
      test("Map") {
        roundTrip[DynamicValue](
          DynamicValue.Map(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.String("key1")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.String("key2")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          )
        )
      },
      test("Nested DynamicValue") {
        roundTrip[DynamicValue](
          DynamicValue.Record(
            Vector(
              "list" -> DynamicValue.Sequence(
                Vector(
                  DynamicValue.Primitive(PrimitiveValue.Int(1)),
                  DynamicValue.Record(Vector("nested" -> DynamicValue.Primitive(PrimitiveValue.String("value"))))
                )
              )
            )
          )
        )
      }
    ),
    suite("edge cases")(
      test("empty string") {
        roundTrip("")
      },
      test("unicode string") {
        roundTrip("\u0000\u001f\u007f\u009f\u2028\u2029\uFFFE\uFFFF")
      },
      test("large string") {
        roundTrip("x" * 10000)
      },
      test("large list") {
        roundTrip((1 to 1000).toList)
      },
      test("deeply nested record") {
        val deep = (1 to 10).foldLeft(Recursive(0, Nil))((acc, i) => Recursive(i, List(acc)))
        roundTrip(deep)
      },
      test("negative BigInt") {
        roundTrip(BigInt("-12345678901234567890"))
      },
      test("very large BigInt") {
        roundTrip(BigInt("9" * 100))
      },
      test("very small BigDecimal") {
        roundTrip(BigDecimal("0." + "0" * 50 + "1"))
      }
    ),
    suite("custom codecs")(
      test("custom int codec via optic") {
        val customCodec = new MessagePackBinaryCodec[Int](MessagePackBinaryCodec.intType) {
          def decodeUnsafe(unpacker: MessageUnpacker): Int    = unpacker.unpackInt() + 1000
          def encode(value: Int, packer: MessagePacker): Unit = packer.packInt(value - 1000)
        }
        val encoded = customCodec.encode(1042)
        val decoded = customCodec.decode(encoded)
        assertTrue(decoded == Right(1042))
      }
    ),
    suite("forward compatibility")(
      test("skip unknown fields in record decoding") {
        // This tests that we can decode a record that has extra fields in the encoded form
        // First, create a simple record codec for a subset of fields
        case class MinimalRecord(i: Int)
        object MinimalRecord {
          implicit val schema: Schema[MinimalRecord] = Schema.derived
        }

        // Encode a full Record1 as a minimal record (simulating forward compatibility)
        val fullRecord = Record1(true, 1, 2, 3, 4L, 5.0f, 6.0, '7', "eight")
        val fullCodec  = Schema[Record1].derive(MessagePackFormat.deriver)
        val encoded    = fullCodec.encode(fullRecord)

        // This test validates the encoding works
        val minCodec = Schema[MinimalRecord].derive(MessagePackFormat.deriver)
        val decoded  = minCodec.decode(encoded)
        assertTrue(decoded == Right(MinimalRecord(3)))
      },
      test("order independent field processing") {
        // Encode and decode a record - the implementation uses HashMap for field lookup
        val record = Record1(true, 1, 2, 3, 4L, 5.0f, 6.0, '7', "eight")
        roundTrip(record)
      }
    )
  )
}
