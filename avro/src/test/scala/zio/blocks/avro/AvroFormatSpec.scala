package zio.blocks.avro

import org.apache.avro.io.{BinaryDecoder, BinaryEncoder}
import zio.blocks.schema.{CompanionOptics, DynamicValue, Lens, Optional, PrimitiveValue, Schema, TypeName}
import zio.blocks.avro.AvroTestUtils._
import zio.test._
import java.util.UUID
import scala.collection.immutable.ArraySeq

object AvroFormatSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("AvroFormatSpec")(
    suite("primitives")(
      test("Unit") {
        roundTrip((), 0)
      },
      test("Boolean") {
        roundTrip(true, 1) &&
        roundTrip(false, 1)
      },
      test("Byte") {
        roundTrip(1: Byte, 1) &&
        roundTrip(Byte.MinValue, 2) &&
        roundTrip(Byte.MaxValue, 2)
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
        roundTrip(Long.MinValue, 10) &&
        roundTrip(Long.MaxValue, 10)
      },
      test("Float") {
        roundTrip(42.0f, 4) &&
        roundTrip(Float.MinValue, 4) &&
        roundTrip(Float.MaxValue, 4)
      },
      test("Double") {
        roundTrip(42.0, 8) &&
        roundTrip(Double.MinValue, 8) &&
        roundTrip(Double.MaxValue, 8)
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
        roundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"), 22)
      },
      test("nested record") {
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          ),
          44
        )
      },
      test("recursive record") {
        roundTrip(Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))), 8)
      },
      test("record with unit and variant fields") {
        roundTrip(Record4((), Some("VVV")), 5) &&
        roundTrip(Record4((), None), 1)
      },
      test("record with a custom codec for primitives injected by optic") {
        val codec: AvroBinaryCodec[Record1] = Record1.schema
          .deriving(AvroFormat.deriver)
          .instance(
            Record1.i,
            new AvroBinaryCodec[Int](AvroBinaryCodec.intType) {
              def decode(d: BinaryDecoder): Int = java.lang.Integer.valueOf(d.readString())

              def encode(x: Int, e: BinaryEncoder): Unit = e.writeString(x.toString)
            }
          )
          .derive
        shortRoundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"), 23, codec)
      },
      test("record with a custom codec for primitives injected by type name") {
        val codec: AvroBinaryCodec[Record1] = Record1.schema
          .deriving(AvroFormat.deriver)
          .instance(
            TypeName.int,
            new AvroBinaryCodec[Int](AvroBinaryCodec.intType) {
              def decode(d: BinaryDecoder): Int = java.lang.Integer.valueOf(d.readString())

              def encode(x: Int, e: BinaryEncoder): Unit = e.writeString(x.toString)
            }
          )
          .derive
        shortRoundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"), 23, codec)
      },
      test("record with a custom codec for unit injected by optic") {
        val codec: AvroBinaryCodec[Record4] = Record4.schema
          .deriving(AvroFormat.deriver)
          .instance(
            Record4.hidden,
            new AvroBinaryCodec[Unit](AvroBinaryCodec.unitType) {
              def decode(d: BinaryDecoder): Unit = d.readString()

              def encode(x: Unit, e: BinaryEncoder): Unit = e.writeString("WWW")
            }
          )
          .derive
        shortRoundTrip(Record4((), Some("VVV")), 9, codec)
      },
      test("record with a custom codec for None injected by optic") {
        val codec: AvroBinaryCodec[Record4] = Record4.schema
          .deriving(AvroFormat.deriver)
          .instance(
            Record4.optKey_None,
            new AvroBinaryCodec[None.type](AvroBinaryCodec.unitType) {
              def decode(d: BinaryDecoder): None.type = {
                val _ = d.readString()
                None
              }

              def encode(x: None.type, e: BinaryEncoder): Unit = e.writeString("WWW")
            }
          )
          .derive
        shortRoundTrip(Record4((), None), 5, codec)
      },
      test("record with a custom codec for nested record injected by optic") {
        val codec: AvroBinaryCodec[Record2] = Record2.schema
          .deriving(AvroFormat.deriver)
          .instance(
            Record2.r1_1,
            new AvroBinaryCodec[Record1]() {
              private val default = Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
              private val codec   = Record1.schema.derive(AvroFormat.deriver).asInstanceOf[AvroBinaryCodec[Record1]]

              def decode(d: BinaryDecoder): Record1 =
                if (d.readBoolean()) default
                else codec.decode(d)

              def encode(x: Record1, e: BinaryEncoder): Unit =
                if (x == default) e.writeBoolean(true)
                else {
                  e.writeBoolean(false)
                  codec.encode(x, e)
                }
            }
          )
          .instance(
            Record2.r1_2,
            new AvroBinaryCodec[Record1]() {
              private val default = Record1(false, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "WWW")
              private val codec   = Record1.schema.derive(AvroFormat.deriver).asInstanceOf[AvroBinaryCodec[Record1]]

              def decode(d: BinaryDecoder): Record1 =
                if (d.readBoolean()) default
                else codec.decode(d)

              def encode(x: Record1, e: BinaryEncoder): Unit =
                if (x == default) e.writeBoolean(true)
                else {
                  e.writeBoolean(false)
                  codec.encode(x, e)
                }
            }
          )
          .derive
        shortRoundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(false, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "WWW")
          ),
          2,
          codec
        )
      },
      test("record with a custom codec for nested primitives injected by optic") {
        val codec: AvroBinaryCodec[Record2] = Record2.schema
          .deriving(AvroFormat.deriver)
          .instance(
            TypeName.int,
            new AvroBinaryCodec[Int](AvroBinaryCodec.intType) {
              def decode(d: BinaryDecoder): Int = java.lang.Integer.valueOf(d.readString())

              def encode(x: Int, e: BinaryEncoder): Unit = e.writeString(x.toString)
            }
          )
          .instance(
            Record2.r1_2_i,
            new AvroBinaryCodec[Int](AvroBinaryCodec.intType) {
              def decode(d: BinaryDecoder): Int = d.readDouble().toInt

              def encode(x: Int, e: BinaryEncoder): Unit = e.writeDouble(x.toDouble)
            }
          )
          .derive
        shortRoundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(false, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "WWW")
          ),
          52,
          codec
        )
      },
      test("record with a custom codec for nested record injected by type name") {
        val codec: AvroBinaryCodec[Record2] = Record2.schema
          .deriving(AvroFormat.deriver)
          .instance(
            Record1.schema.reflect.typeName,
            new AvroBinaryCodec[Record1]() {
              private val default = Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
              private val codec   = Record1.schema.derive(AvroFormat.deriver).asInstanceOf[AvroBinaryCodec[Record1]]

              def decode(d: BinaryDecoder): Record1 =
                if (d.readBoolean()) default
                else codec.decode(d)

              def encode(x: Record1, e: BinaryEncoder): Unit =
                if (x == default) e.writeBoolean(true)
                else {
                  e.writeBoolean(false)
                  codec.encode(x, e)
                }
            }
          )
          .derive
        shortRoundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          ),
          2,
          codec
        )
      }
    ),
    suite("sequences")(
      test("primitive values") {
        implicit val arrayOfUnitSchema: Schema[Array[Unit]]       = Schema.derived
        implicit val arrayOfBooleanSchema: Schema[Array[Boolean]] = Schema.derived
        implicit val arrayOfByteSchema: Schema[Array[Byte]]       = Schema.derived
        implicit val arrayOfShortSchema: Schema[Array[Short]]     = Schema.derived
        implicit val arrayOfCharSchema: Schema[Array[Char]]       = Schema.derived

        roundTrip(Array[Unit]((), (), ()), 2) &&
        roundTrip(Array[Boolean](true, false, true), 5) &&
        roundTrip(Array[Byte](1: Byte, 2: Byte, 3: Byte), 5) &&
        roundTrip(Array[Short](1: Short, 2: Short, 3: Short), 5) &&
        roundTrip(Array('1', '2', '3'), 5) &&
        roundTrip(List(1, 2, 3), 5) &&
        roundTrip(ArraySeq(1L, 2L, 3L), 5) &&
        roundTrip(Set(1.0f, 2.0f, 3.0f), 14) &&
        roundTrip(Vector(1.0, 2.0, 3.0), 26) &&
        roundTrip(List("1", "2", "3"), 8) &&
        roundTrip(List(BigInt(1), BigInt(2), BigInt(3)), 8) &&
        roundTrip(List(BigDecimal(1.0), BigDecimal(2.0), BigDecimal(3.0)), 17) &&
        roundTrip(List(java.time.LocalDate.of(2025, 1, 1), java.time.LocalDate.of(2025, 1, 2)), 10) &&
        roundTrip(List(new java.util.UUID(1L, 1L), new java.util.UUID(2L, 2L), new java.util.UUID(3L, 3L)), 50)
      },
      test("complex values") {
        roundTrip(
          List(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          ),
          46
        )
      },
      test("recursive values") {
        roundTrip(
          List(
            Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))),
            Recursive(4, List(Recursive(5, List(Recursive(6, Nil)))))
          ),
          18
        )
      }
    ),
    suite("maps")(
      test("string keys and primitive values") {
        roundTrip(Map("VVV" -> (), "WWW" -> ()), 10) &&
        roundTrip(Map("VVV" -> true, "WWW" -> false), 12) &&
        roundTrip(Map("VVV" -> (1: Byte), "WWW" -> (2: Byte)), 12) &&
        roundTrip(Map("VVV" -> (1: Short), "WWW" -> (2: Short)), 12) &&
        roundTrip(Map("VVV" -> '1', "WWW" -> '2'), 12) &&
        roundTrip(Map("VVV" -> 1, "WWW" -> 2), 12) &&
        roundTrip(Map("VVV" -> 1L, "WWW" -> 2L), 12) &&
        roundTrip(Map("VVV" -> 1.0f, "WWW" -> 2.0f), 18) &&
        roundTrip(Map("VVV" -> 1.0, "WWW" -> 2.0), 26) &&
        roundTrip(Map("VVV" -> "1", "WWW" -> "2"), 14) &&
        roundTrip(Map("VVV" -> BigInt(1), "WWW" -> BigInt(2)), 14) &&
        roundTrip(Map("VVV" -> BigDecimal(1.0), "WWW" -> BigDecimal(2.0)), 20) &&
        roundTrip(Map("VVV" -> java.time.LocalDate.of(2025, 1, 1), "WWW" -> java.time.LocalDate.of(2025, 1, 2)), 18) &&
        roundTrip(Map("VVV" -> new java.util.UUID(1L, 1L), "WWW" -> new java.util.UUID(2L, 2L)), 42)
      },
      test("string keys and complex values") {
        roundTrip(
          Map(
            "VVV" -> Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            "WWW" -> Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          ),
          54
        )
      },
      test("string keys and recursive values") {
        roundTrip(
          Map(
            "VVV" -> Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))),
            "WWW" -> Recursive(4, List(Recursive(5, List(Recursive(6, Nil)))))
          ),
          26
        )
      },
      test("non string key map") {
        roundTrip(Map(1 -> 1L, 2 -> 2L), 6)
      },
      test("non string key with recursive values") {
        roundTrip(
          Map(
            Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))) -> 1,
            Recursive(4, List(Recursive(5, List(Recursive(6, Nil))))) -> 2
          ),
          20
        )
      },
      test("nested maps") {
        roundTrip(Map("VVV" -> Map(1 -> 1L, 2 -> 2L)), 12) &&
        roundTrip(Map(Map(1 -> 1L, 2 -> 2L) -> "WWW"), 12)
      }
    ),
    suite("enums")(
      test("constant values") {
        roundTrip[TrafficLight](TrafficLight.Green, 1) &&
        roundTrip[TrafficLight](TrafficLight.Yellow, 1) &&
        roundTrip[TrafficLight](TrafficLight.Red, 1)
      },
      test("option") {
        roundTrip(Option(42), 2) &&
        roundTrip[Option[Int]](None, 1)
      },
      test("either") {
        roundTrip[Either[String, Int]](Right(42), 2) &&
        roundTrip[Either[String, Int]](Left("VVV"), 5)
      }
    ),
    suite("wrapper")(
      test("top-level") {
        roundTrip[UserId](UserId(1234567890123456789L), 9) &&
        roundTrip[Email](Email("john@gmail.com"), 15)
      },
      test("as a record field") {
        roundTrip[Record3](Record3(UserId(1234567890123456789L), Email("backup@gmail.com")), 26)
      }
    ),
    suite("dynamic value")(
      test("top-level") {
        shortRoundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Int(1)), 3) &&
        shortRoundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.String("VVV")), 6) &&
        shortRoundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.UUID(UUID.randomUUID())), 18) &&
        shortRoundTrip[DynamicValue](
          DynamicValue.Record(
            Vector(
              ("i", DynamicValue.Primitive(PrimitiveValue.Int(1))),
              ("s", DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          ),
          16
        ) &&
        shortRoundTrip[DynamicValue](DynamicValue.Variant("Int", DynamicValue.Primitive(PrimitiveValue.Int(1))), 8) &&
        shortRoundTrip[DynamicValue](
          DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.String("VVV"))
            )
          ),
          12
        ) &&
        shortRoundTrip[DynamicValue](
          DynamicValue.Map(
            Vector(
              (DynamicValue.Primitive(PrimitiveValue.Long(1L)), DynamicValue.Primitive(PrimitiveValue.Int(1))),
              (DynamicValue.Primitive(PrimitiveValue.Long(2L)), DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          ),
          18
        )
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

  object Recursive {
    implicit val schema: Schema[Recursive] = Schema.derived
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
    implicit val schema: Schema[Email] = Schema.derived.wrapTotal(x => new Email(x), _.value)
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
}
