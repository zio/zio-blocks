package zio.blocks.schema.thrift

import zio.blocks.schema._
import zio.blocks.schema.thrift.ThriftTestUtils._
import zio.blocks.schema.binding.Binding
import zio.test._
import java.time._
import java.util.UUID
import java.util.Currency
import scala.collection.immutable.ArraySeq

object ThriftFormatSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("ThriftFormatSpec")(
    suite("primitives")(
      test("Unit") {
        roundTrip(())
      },
      test("Boolean true") {
        roundTrip(true)
      },
      test("Boolean false") {
        roundTrip(false)
      },
      test("Byte") {
        roundTrip(1: Byte) &&
        roundTrip(Byte.MinValue) &&
        roundTrip(Byte.MaxValue)
      },
      test("Short") {
        roundTrip(1: Short) &&
        roundTrip(Short.MinValue) &&
        roundTrip(Short.MaxValue)
      },
      test("Int") {
        roundTrip(1) &&
        roundTrip(Int.MinValue) &&
        roundTrip(Int.MaxValue)
      },
      test("Long") {
        roundTrip(1L) &&
        roundTrip(Long.MinValue) &&
        roundTrip(Long.MaxValue)
      },
      test("Float") {
        roundTrip(42.0f) &&
        roundTrip(Float.MinValue) &&
        roundTrip(Float.MaxValue) &&
        roundTrip(Float.PositiveInfinity) &&
        roundTrip(Float.NegativeInfinity)
      },
      test("Float NaN") {
        val codec   = Schema[Float].derive(ThriftFormat.deriver)
        val encoded = codec.encode(Float.NaN)
        codec.decode(encoded) match {
          case Right(decoded) => assertTrue(decoded.isNaN)
          case Left(_)        => assertTrue(false)
        }
      },
      test("Double") {
        roundTrip(42.0) &&
        roundTrip(Double.MinValue) &&
        roundTrip(Double.MaxValue) &&
        roundTrip(Double.PositiveInfinity) &&
        roundTrip(Double.NegativeInfinity)
      },
      test("Double NaN") {
        val codec   = Schema[Double].derive(ThriftFormat.deriver)
        val encoded = codec.encode(Double.NaN)
        codec.decode(encoded) match {
          case Right(decoded) => assertTrue(decoded.isNaN)
          case Left(_)        => assertTrue(false)
        }
      },
      test("Char") {
        roundTrip('7') &&
        roundTrip(Char.MinValue) &&
        roundTrip(Char.MaxValue)
      },
      test("String") {
        roundTrip("Hello") &&
        roundTrip("") &&
        roundTrip("Unicode test: \u0041\u0042\u0043")
      },
      test("BigInt") {
        roundTrip(BigInt("9" * 20)) &&
        roundTrip(BigInt(0)) &&
        roundTrip(BigInt(-1))
      },
      test("BigDecimal") {
        roundTrip(BigDecimal("9." + "9" * 20 + "E+12345")) &&
        roundTrip(BigDecimal(0)) &&
        roundTrip(BigDecimal("123.456"))
      },
      test("DayOfWeek") {
        roundTrip(java.time.DayOfWeek.WEDNESDAY) &&
        roundTrip(java.time.DayOfWeek.MONDAY) &&
        roundTrip(java.time.DayOfWeek.SUNDAY)
      },
      test("Duration") {
        roundTrip(java.time.Duration.ofNanos(1234567890123456789L)) &&
        roundTrip(java.time.Duration.ZERO) &&
        roundTrip(java.time.Duration.ofDays(365))
      },
      test("Instant") {
        roundTrip(java.time.Instant.parse("2025-07-18T08:29:13.121409459Z")) &&
        roundTrip(java.time.Instant.EPOCH) &&
        roundTrip(java.time.Instant.MIN) &&
        roundTrip(java.time.Instant.MAX)
      },
      test("LocalDate") {
        roundTrip(java.time.LocalDate.parse("2025-07-18")) &&
        roundTrip(java.time.LocalDate.MIN) &&
        roundTrip(java.time.LocalDate.MAX)
      },
      test("LocalDateTime") {
        roundTrip(java.time.LocalDateTime.parse("2025-07-18T08:29:13.121409459")) &&
        roundTrip(java.time.LocalDateTime.MIN) &&
        roundTrip(java.time.LocalDateTime.MAX)
      },
      test("LocalTime") {
        roundTrip(java.time.LocalTime.parse("08:29:13.121409459")) &&
        roundTrip(java.time.LocalTime.MIN) &&
        roundTrip(java.time.LocalTime.MAX)
      },
      test("Month") {
        roundTrip(java.time.Month.of(12)) &&
        roundTrip(java.time.Month.JANUARY) &&
        roundTrip(java.time.Month.DECEMBER)
      },
      test("MonthDay") {
        roundTrip(java.time.MonthDay.of(12, 31)) &&
        roundTrip(java.time.MonthDay.of(1, 1))
      },
      test("OffsetDateTime") {
        roundTrip(java.time.OffsetDateTime.parse("2025-07-18T08:29:13.121409459-07:00")) &&
        roundTrip(java.time.OffsetDateTime.MIN) &&
        roundTrip(java.time.OffsetDateTime.MAX)
      },
      test("OffsetTime") {
        roundTrip(java.time.OffsetTime.parse("08:29:13.121409459-07:00")) &&
        roundTrip(java.time.OffsetTime.MIN) &&
        roundTrip(java.time.OffsetTime.MAX)
      },
      test("Period") {
        roundTrip(java.time.Period.of(1, 12, 31)) &&
        roundTrip(java.time.Period.ZERO)
      },
      test("Year") {
        roundTrip(java.time.Year.of(2025)) &&
        roundTrip(java.time.Year.MIN_VALUE) &&
        roundTrip(java.time.Year.MAX_VALUE)
      },
      test("YearMonth") {
        roundTrip(java.time.YearMonth.of(2025, 7)) &&
        roundTrip(java.time.YearMonth.of(Year.MIN_VALUE, 1)) &&
        roundTrip(java.time.YearMonth.of(Year.MAX_VALUE, 12))
      },
      test("ZoneId") {
        roundTrip(java.time.ZoneId.of("UTC")) &&
        roundTrip(java.time.ZoneId.of("America/New_York")) &&
        roundTrip(java.time.ZoneId.of("Europe/London"))
      },
      test("ZoneOffset") {
        roundTrip(java.time.ZoneOffset.ofTotalSeconds(3600)) &&
        roundTrip(java.time.ZoneOffset.UTC) &&
        roundTrip(java.time.ZoneOffset.MIN) &&
        roundTrip(java.time.ZoneOffset.MAX)
      },
      test("ZonedDateTime") {
        roundTrip(java.time.ZonedDateTime.parse("2025-07-18T08:29:13.121409459+02:00[Europe/Warsaw]"))
      },
      test("Currency") {
        roundTrip(Currency.getInstance("USD")) &&
        roundTrip(Currency.getInstance("EUR")) &&
        roundTrip(Currency.getInstance("GBP"))
      },
      test("UUID") {
        roundTrip(UUID.randomUUID()) &&
        roundTrip(new UUID(0L, 0L)) &&
        roundTrip(new UUID(Long.MaxValue, Long.MaxValue))
      }
    ),
    suite("records")(
      test("simple record") {
        roundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"))
      },
      test("nested record") {
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          )
        )
      },
      test("recursive record") {
        roundTrip(Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))))
      },
      test("record with unit and variant fields") {
        roundTrip(Record4((), Some("VVV"))) &&
        roundTrip(Record4((), None))
      },
      test("record with empty fields") {
        roundTrip(EmptyRecord())
      },
      test("record with all primitive fields") {
        roundTrip(
          AllPrimitives(
            unit = (),
            boolean = true,
            byte = 42,
            short = 1000,
            int = 100000,
            long = 10000000000L,
            float = 3.14f,
            double = 2.718281828,
            char = 'X',
            string = "test"
          )
        )
      }
    ),
    suite("sequences")(
      test("List of Int") {
        roundTrip((1 to 100).toList)
      },
      test("Set of Long") {
        roundTrip(Set(1L, 2L, 3L))
      },
      test("Vector of Double") {
        roundTrip(Vector(1.0, 2.0, 3.0))
      },
      test("List of String") {
        roundTrip(List("1", "2", "3"))
      },
      test("Empty List") {
        roundTrip(List.empty[Int])
      },
      test("List of records") {
        roundTrip(
          List(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          )
        )
      },
      test("List of recursive values") {
        roundTrip(
          List(
            Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))),
            Recursive(4, List(Recursive(5, List(Recursive(6, Nil)))))
          )
        )
      },
      test("Nested lists") {
        roundTrip(List(List(1, 2, 3), List(4, 5, 6)))
      },
      test("ArraySeq of Float") {
        implicit val arraySeqOfFloatSchema: Schema[ArraySeq[Float]] = Schema.derived
        roundTrip(ArraySeq(1.0f, 2.0f, 3.0f))
      },
      test("Array of Byte") {
        implicit val arrayOfByteSchema: Schema[Array[Byte]] = Schema.derived
        val arr                                             = Array[Byte](1, 2, 3, 4, 5)
        val codec                                           = Schema[Array[Byte]].derive(ThriftFormat.deriver)
        val encoded                                         = codec.encode(arr)
        codec.decode(encoded) match {
          case Right(decoded) => assertTrue(arr.sameElements(decoded))
          case Left(_)        => assertTrue(false)
        }
      }
    ),
    suite("maps")(
      test("Map with String keys and Int values") {
        roundTrip(Map("a" -> 1, "b" -> 2, "c" -> 3))
      },
      test("Map with String keys and complex values") {
        roundTrip(
          Map(
            "VVV" -> Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            "WWW" -> Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          )
        )
      },
      test("Map with Int keys and Long values") {
        roundTrip(Map(1 -> 1L, 2 -> 2L))
      },
      test("Empty Map") {
        roundTrip(Map.empty[String, Int])
      },
      test("Map with recursive values") {
        roundTrip(
          Map(
            "VVV" -> Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))),
            "WWW" -> Recursive(4, List(Recursive(5, List(Recursive(6, Nil)))))
          )
        )
      },
      test("Nested maps") {
        roundTrip(Map("outer" -> Map("inner1" -> 1, "inner2" -> 2)))
      }
    ),
    suite("variants")(
      test("constant values - TrafficLight") {
        roundTrip[TrafficLight](TrafficLight.Green) &&
        roundTrip[TrafficLight](TrafficLight.Yellow) &&
        roundTrip[TrafficLight](TrafficLight.Red)
      },
      test("Option Some") {
        roundTrip(Option(42))
      },
      test("Option None") {
        roundTrip[Option[Int]](None)
      },
      test("Either Right") {
        roundTrip[Either[String, Int]](Right(42))
      },
      test("Either Left") {
        roundTrip[Either[String, Int]](Left("error"))
      },
      test("Sealed trait with data") {
        roundTrip[Shape](Shape.Circle(5.0)) &&
        roundTrip[Shape](Shape.Rectangle(3.0, 4.0)) &&
        roundTrip[Shape](Shape.Point)
      }
    ),
    suite("wrapper")(
      test("UserId wrapper") {
        roundTrip[UserId](UserId(1234567890123456789L))
      },
      test("Email wrapper with validation") {
        roundTrip[Email](Email("john@gmail.com"))
      },
      test("Email decode error") {
        val stringCodec = Schema[String].derive(ThriftFormat.deriver)
        val bytes       = stringCodec.encode("invalid-email")
        decodeError[Email](bytes, "Expected Email")
      },
      test("Record with wrapper fields") {
        roundTrip[Record3](Record3(UserId(1234567890123456789L), Email("backup@gmail.com")))
      }
    ),
    suite("dynamic value")(
      test("Primitive Unit") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Unit))
      },
      test("Primitive Boolean") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
      },
      test("Primitive Byte") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Byte(1: Byte)))
      },
      test("Primitive Short") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Short(1: Short)))
      },
      test("Primitive Int") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Int(1)))
      },
      test("Primitive Long") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Long(1L)))
      },
      test("Primitive Float") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Float(1.0f)))
      },
      test("Primitive Double") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Double(1.0)))
      },
      test("Primitive Char") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Char('1')))
      },
      test("Primitive String") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.String("VVV")))
      },
      test("Primitive BigInt") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.BigInt(123)))
      },
      test("Primitive BigDecimal") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.BigDecimal(123.45)))
      },
      test("Primitive DayOfWeek") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY)))
      },
      test("Primitive Duration") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Duration(Duration.ofSeconds(60))))
      },
      test("Primitive Instant") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Instant(Instant.EPOCH)))
      },
      test("Primitive LocalDate") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.LocalDate(LocalDate.MAX)))
      },
      test("Primitive LocalDateTime") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.LocalDateTime(LocalDateTime.MAX)))
      },
      test("Primitive LocalTime") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.LocalTime(LocalTime.MAX)))
      },
      test("Primitive Month") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Month(Month.MAY)))
      },
      test("Primitive MonthDay") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1))))
      },
      test("Primitive OffsetDateTime") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX)))
      },
      test("Primitive OffsetTime") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.OffsetTime(OffsetTime.MAX)))
      },
      test("Primitive Period") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Period(Period.ofDays(1))))
      },
      test("Primitive Year") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Year(Year.of(2025))))
      },
      test("Primitive YearMonth") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.YearMonth(YearMonth.of(2025, 1))))
      },
      test("Primitive ZoneId") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.ZoneId(ZoneId.of("UTC"))))
      },
      test("Primitive ZoneOffset") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.ZoneOffset(ZoneOffset.MAX)))
      },
      test("Primitive ZonedDateTime") {
        roundTrip[DynamicValue](
          DynamicValue.Primitive(
            PrimitiveValue.ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC")))
          )
        )
      },
      test("Primitive Currency") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Currency(Currency.getInstance("USD"))))
      },
      test("Primitive UUID") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.UUID(UUID.randomUUID())))
      },
      test("Record dynamic value") {
        roundTrip[DynamicValue](
          DynamicValue.Record(
            Vector(
              ("i", DynamicValue.Primitive(PrimitiveValue.Int(1))),
              ("s", DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          )
        )
      },
      test("Variant dynamic value") {
        roundTrip[DynamicValue](DynamicValue.Variant("Int", DynamicValue.Primitive(PrimitiveValue.Int(1))))
      },
      test("Sequence dynamic value") {
        roundTrip[DynamicValue](
          DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.String("VVV"))
            )
          )
        )
      },
      test("Map dynamic value") {
        roundTrip[DynamicValue](
          DynamicValue.Map(
            Vector(
              (DynamicValue.Primitive(PrimitiveValue.Long(1L)), DynamicValue.Primitive(PrimitiveValue.Int(1))),
              (DynamicValue.Primitive(PrimitiveValue.Long(2L)), DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          )
        )
      },
      test("Dynamic value as record field values") {
        val value = Dynamic(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Map(
            Vector(
              (DynamicValue.Primitive(PrimitiveValue.Long(1L)), DynamicValue.Primitive(PrimitiveValue.Int(1))),
              (DynamicValue.Primitive(PrimitiveValue.Long(2L)), DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          )
        )
        roundTrip[Dynamic](value)
      }
    ),
    suite("edge cases")(
      test("Empty string") {
        roundTrip("")
      },
      test("Very long string") {
        roundTrip("x" * 10000)
      },
      test("Large list") {
        roundTrip((1 to 1000).toList)
      },
      test("Deeply nested structure") {
        def buildNested(depth: Int): Recursive =
          if (depth <= 0) Recursive(0, Nil)
          else Recursive(depth, List(buildNested(depth - 1)))
        roundTrip(buildNested(20))
      },
      test("Unicode in string") {
        roundTrip("Hello \u4E16\u754C \uD83D\uDE00")
      },
      test("Special characters in string") {
        roundTrip("Line1\nLine2\tTab\r\nCRLF")
      }
    ),
    suite("error handling")(
      test("decode empty bytes") {
        decodeError[Int](Array.empty[Byte], "Unexpected end of input")
      },
      test("decode truncated data") {
        decodeError[String](Array[Byte](100), "Unexpected end of input")
      }
    )
  )

  case class Record1(
    bl: Boolean,
    b: Byte,
    sh: Short,
    i: Int,
    l: Long,
    f: Float,
    d: Double,
    c: Char,
    s: String
  )

  object Record1 extends CompanionOptics[Record1] {
    implicit val schema: Schema[Record1] = Schema.derived

    val i: Lens[Record1, Int] = $(_.i)
  }

  case class Record2(
    r1_1: Record1,
    r1_2: Record1
  )

  object Record2 extends CompanionOptics[Record2] {
    implicit val schema: Schema[Record2] = Schema.derived

    val r1_1: Lens[Record2, Record1] = $(_.r1_1)
    val r1_2: Lens[Record2, Record1] = $(_.r1_2)
    val r1_1_i: Lens[Record2, Int]   = $(_.r1_1.i)
    val r1_2_i: Lens[Record2, Int]   = $(_.r1_2.i)
  }

  case class Recursive(i: Int, ln: List[Recursive])

  object Recursive extends CompanionOptics[Recursive] {
    implicit val schema: Schema[Recursive]   = Schema.derived
    val i: Lens[Recursive, Int]              = $(_.i)
    val ln: Lens[Recursive, List[Recursive]] = $(_.ln)
  }

  sealed trait TrafficLight

  object TrafficLight {
    implicit val schema: Schema[TrafficLight] = Schema.derived

    case object Red extends TrafficLight

    case object Yellow extends TrafficLight

    case object Green extends TrafficLight
  }

  implicit val eitherSchema: Schema[Either[String, Int]] = Schema.derived

  case class UserId(value: Long)

  object UserId {
    implicit val schema: Schema[UserId] = Schema.derived.wrapTotal(x => new UserId(x), _.value)
  }

  case class Email(value: String)

  object Email {
    private[this] val EmailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".r

    implicit val schema: Schema[Email] = new Schema(
      new Reflect.Wrapper[Binding, Email, String](
        Schema[String].reflect,
        TypeName(Namespace(Seq("zio", "blocks", "thrift"), Seq("ThriftFormatSpec")), "Email"),
        None,
        new Binding.Wrapper(
          {
            case x @ EmailRegex(_*) => new Right(new Email(x))
            case _                  => new Left("Expected Email")
          },
          _.value
        )
      )
    )
  }

  case class Record3(userId: UserId, email: Email)

  object Record3 {
    implicit val schema: Schema[Record3] = Schema.derived
  }

  case class Record4(hidden: Unit, optKey: Option[String])

  object Record4 extends CompanionOptics[Record4] {
    implicit val schema: Schema[Record4] = Schema.derived

    val hidden: Lens[Record4, Unit]               = $(_.hidden)
    val optKey: Lens[Record4, Option[String]]     = $(_.optKey)
    val optKey_None: Optional[Record4, None.type] = $(_.optKey.when[None.type])
  }

  case class Dynamic(primitive: DynamicValue, map: DynamicValue)

  object Dynamic extends CompanionOptics[Dynamic] {
    implicit val schema: Schema[Dynamic] = Schema.derived

    val primitive: Lens[Dynamic, DynamicValue] = $(_.primitive)
    val map: Lens[Dynamic, DynamicValue]       = $(_.map)
  }

  case class EmptyRecord()

  object EmptyRecord {
    implicit val schema: Schema[EmptyRecord] = Schema.derived
  }

  case class AllPrimitives(
    unit: Unit,
    boolean: Boolean,
    byte: Byte,
    short: Short,
    int: Int,
    long: Long,
    float: Float,
    double: Double,
    char: Char,
    string: String
  )

  object AllPrimitives {
    implicit val schema: Schema[AllPrimitives] = Schema.derived
  }

  sealed trait Shape

  object Shape {
    implicit val schema: Schema[Shape] = Schema.derived

    case class Circle(radius: Double)                   extends Shape
    case class Rectangle(width: Double, height: Double) extends Shape
    case object Point                                   extends Shape
  }
}
