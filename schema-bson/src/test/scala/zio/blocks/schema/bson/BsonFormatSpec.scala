package zio.blocks.schema.bson

import zio.blocks.schema._
import zio.blocks.schema.bson.BsonTestUtils._
import zio.blocks.schema.binding.Binding
import zio.test._
import java.time._
import java.util.{Currency, UUID}
import scala.collection.immutable.ArraySeq

object BsonFormatSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("BsonFormatSpec")(
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
      test("Byte positive") {
        roundTrip(1: Byte)
      },
      test("Byte min") {
        roundTrip(Byte.MinValue)
      },
      test("Byte max") {
        roundTrip(Byte.MaxValue)
      },
      test("Short positive") {
        roundTrip(1: Short)
      },
      test("Short min") {
        roundTrip(Short.MinValue)
      },
      test("Short max") {
        roundTrip(Short.MaxValue)
      },
      test("Int positive") {
        roundTrip(42)
      },
      test("Int min") {
        roundTrip(Int.MinValue)
      },
      test("Int max") {
        roundTrip(Int.MaxValue)
      },
      test("Long positive") {
        roundTrip(42L)
      },
      test("Long min") {
        roundTrip(Long.MinValue)
      },
      test("Long max") {
        roundTrip(Long.MaxValue)
      },
      test("Float positive") {
        roundTrip(42.0f)
      },
      test("Float min") {
        roundTrip(Float.MinValue)
      },
      test("Float max") {
        roundTrip(Float.MaxValue)
      },
      test("Double positive") {
        roundTrip(42.0)
      },
      test("Double min") {
        roundTrip(Double.MinValue)
      },
      test("Double max") {
        roundTrip(Double.MaxValue)
      },
      test("Char") {
        roundTrip('7')
      },
      test("Char min") {
        roundTrip(Char.MinValue)
      },
      test("Char max") {
        roundTrip(Char.MaxValue)
      },
      test("String") {
        roundTrip("Hello")
      },
      test("String unicode") {
        roundTrip("Hello World")
      },
      test("BigInt") {
        roundTrip(BigInt("9" * 20))
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
        roundTrip(Record4((), Some("VVV")))
      },
      test("record with None") {
        roundTrip(Record4((), None))
      }
    ),
    suite("sequences")(
      test("Array[Unit]") {
        implicit val arrayOfUnitSchema: Schema[Array[Unit]] = Schema.derived
        roundTrip(Array[Unit]((), (), ()))
      },
      test("Array[Boolean]") {
        implicit val arrayOfBooleanSchema: Schema[Array[Boolean]] = Schema.derived
        roundTrip(Array[Boolean](true, false, true))
      },
      test("Array[Byte]") {
        implicit val arrayOfByteSchema: Schema[Array[Byte]] = Schema.derived
        roundTrip(Array[Byte](1: Byte, 2: Byte, 3: Byte))
      },
      test("Array[Short]") {
        implicit val arrayOfShortSchema: Schema[Array[Short]] = Schema.derived
        roundTrip(Array[Short](1: Short, 2: Short, 3: Short))
      },
      test("Array[Char]") {
        implicit val arrayOfCharSchema: Schema[Array[Char]] = Schema.derived
        roundTrip(Array('1', '2', '3'))
      },
      test("Array[Float]") {
        implicit val arrayOfFloatSchema: Schema[Array[Float]] = Schema.derived
        roundTrip(Array[Float](1.0f, 2.0f, 3.0f))
      },
      test("Array[Int]") {
        implicit val arrayOfIntSchema: Schema[Array[Int]] = Schema.derived
        roundTrip(Array[Int](1, 2, 3))
      },
      test("Array[Double]") {
        implicit val arrayOfDoubleSchema: Schema[Array[Double]] = Schema.derived
        roundTrip(Array[Double](1.0, 2.0, 3.0))
      },
      test("Array[Long]") {
        implicit val arrayOfLongSchema: Schema[Array[Long]] = Schema.derived
        roundTrip(Array[Long](1, 2, 3))
      },
      test("List[Int]") {
        roundTrip((1 to 100).toList)
      },
      test("Set[Long]") {
        roundTrip(Set(1L, 2L, 3L))
      },
      test("ArraySeq[Float]") {
        implicit val arraySeqOfFloatSchema: Schema[ArraySeq[Float]] = Schema.derived
        roundTrip(ArraySeq(1.0f, 2.0f, 3.0f))
      },
      test("Vector[Double]") {
        roundTrip(Vector(1.0, 2.0, 3.0))
      },
      test("List[String]") {
        roundTrip(List("1", "2", "3"))
      },
      test("List[BigInt]") {
        roundTrip(List(BigInt(1), BigInt(2), BigInt(3)))
      },
      test("List[Record1]") {
        roundTrip(
          List(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          )
        )
      },
      test("List[Recursive]") {
        roundTrip(
          List(
            Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))),
            Recursive(4, List(Recursive(5, List(Recursive(6, Nil)))))
          )
        )
      }
    ),
    suite("maps")(
      test("Map[String, Unit]") {
        roundTrip(Map("VVV" -> (), "WWW" -> ()))
      },
      test("Map[String, Boolean]") {
        roundTrip(Map("VVV" -> true, "WWW" -> false))
      },
      test("Map[String, Byte]") {
        roundTrip(Map("VVV" -> (1: Byte), "WWW" -> (2: Byte)))
      },
      test("Map[String, Short]") {
        roundTrip(Map("VVV" -> (1: Short), "WWW" -> (2: Short)))
      },
      test("Map[String, Char]") {
        roundTrip(Map("VVV" -> '1', "WWW" -> '2'))
      },
      test("Map[String, Int]") {
        roundTrip(Map("VVV" -> 1, "WWW" -> 2))
      },
      test("Map[String, Long]") {
        roundTrip(Map("VVV" -> 1L, "WWW" -> 2L))
      },
      test("Map[String, Float]") {
        roundTrip(Map("VVV" -> 1.0f, "WWW" -> 2.0f))
      },
      test("Map[String, Double]") {
        roundTrip(Map("VVV" -> 1.0, "WWW" -> 2.0))
      },
      test("Map[String, String]") {
        roundTrip(Map("VVV" -> "1", "WWW" -> "2"))
      },
      test("Map[String, BigInt]") {
        roundTrip(Map("VVV" -> BigInt(1), "WWW" -> BigInt(2)))
      },
      test("Map[String, Record1]") {
        roundTrip(
          Map(
            "VVV" -> Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            "WWW" -> Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          )
        )
      },
      test("Map[String, Recursive]") {
        roundTrip(
          Map(
            "VVV" -> Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))),
            "WWW" -> Recursive(4, List(Recursive(5, List(Recursive(6, Nil)))))
          )
        )
      },
      test("Map[Int, Long] (non-string key)") {
        roundTrip(Map(1 -> 1L, 2 -> 2L))
      },
      test("Map[Recursive, Int] (non-string key with recursive values)") {
        roundTrip(
          Map(
            Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))) -> 1,
            Recursive(4, List(Recursive(5, List(Recursive(6, Nil))))) -> 2
          )
        )
      },
      test("nested maps") {
        roundTrip(Map("VVV" -> Map(1 -> 1L, 2 -> 2L)))
      }
    ),
    suite("variants")(
      test("constant values Red") {
        roundTrip[TrafficLight](TrafficLight.Red)
      },
      test("constant values Yellow") {
        roundTrip[TrafficLight](TrafficLight.Yellow)
      },
      test("constant values Green") {
        roundTrip[TrafficLight](TrafficLight.Green)
      },
      test("option Some") {
        roundTrip(Option(42))
      },
      test("option None") {
        roundTrip[Option[Int]](None)
      },
      test("either Right") {
        roundTrip[Either[String, Int]](Right(42))
      },
      test("either Left") {
        roundTrip[Either[String, Int]](Left("VVV"))
      }
    ),
    suite("wrapper")(
      test("UserId wrapper") {
        roundTrip[UserId](UserId(1234567890123456789L))
      },
      test("Email wrapper") {
        roundTrip[Email](Email("john@gmail.com"))
      },
      test("as a record field") {
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
      test("Record") {
        roundTrip[DynamicValue](
          DynamicValue.Record(
            Vector(
              ("i", DynamicValue.Primitive(PrimitiveValue.Int(1))),
              ("s", DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          )
        )
      },
      test("Variant") {
        roundTrip[DynamicValue](DynamicValue.Variant("Int", DynamicValue.Primitive(PrimitiveValue.Int(1))))
      },
      test("Sequence") {
        roundTrip[DynamicValue](
          DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.String("VVV"))
            )
          )
        )
      },
      test("Map") {
        roundTrip[DynamicValue](
          DynamicValue.Map(
            Vector(
              (DynamicValue.Primitive(PrimitiveValue.Long(1L)), DynamicValue.Primitive(PrimitiveValue.Int(1))),
              (DynamicValue.Primitive(PrimitiveValue.Long(2L)), DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          )
        )
      }
    ),
    suite("edge cases")(
      test("empty string") {
        roundTrip("")
      },
      test("empty list") {
        roundTrip(List.empty[Int])
      },
      test("empty map") {
        roundTrip(Map.empty[String, Int])
      },
      test("deeply nested record") {
        roundTrip(Recursive(1, List(Recursive(2, List(Recursive(3, List(Recursive(4, List(Recursive(5, Nil))))))))))
      },
      test("large list") {
        roundTrip((1 to 1000).toList)
      },
      test("special characters in string") {
        roundTrip("Hello\nWorld\t!")
      },
      test("null character in string") {
        roundTrip("Hello\u0000World")
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
        TypeName(Namespace(Seq("zio", "blocks", "bson"), Seq("BsonFormatSpec")), "Email"),
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
}
