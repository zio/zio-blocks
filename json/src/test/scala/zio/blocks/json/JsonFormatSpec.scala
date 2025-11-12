package zio.blocks.json

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonWriter}
import zio.blocks.json.JsonTestUtils._
import zio.blocks.schema._
import zio.blocks.schema.binding.Binding
import zio.test._
import java.util.{Currency, UUID}
import scala.collection.immutable.ArraySeq

object JsonFormatSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("JsonFormatSpec")(
    suite("primitives")(
      test("Unit") {
        roundTrip((), "null")
      },
      test("Boolean") {
        roundTrip(true, "true") &&
        roundTrip(false, "false")
      },
      test("Boolean (decode error)") {
        val booleanCodec = Schema[Boolean].derive(JsonFormat.deriver)
        decodeError("", booleanCodec, "unexpected end of input at: .") &&
        decodeError("tralse", booleanCodec, "illegal boolean at: .")
      },
      test("Byte") {
        roundTrip(1: Byte, "1") &&
        roundTrip(Byte.MinValue, "-128") &&
        roundTrip(Byte.MaxValue, "127")
      },
      test("Byte (decode error)") {
        val byteCodec = Schema[Byte].derive(JsonFormat.deriver)
        decodeError("-129", byteCodec, "value is too large for byte at: .") &&
        decodeError("128", byteCodec, "value is too large for byte at: .") &&
        decodeError("01", byteCodec, "illegal number with leading zero at: .") &&
        decodeError("null", byteCodec, "illegal number at: .") &&
        decodeError("", byteCodec, "unexpected end of input at: .")
      },
      test("Short") {
        roundTrip(1: Short, "1") &&
        roundTrip(Short.MinValue, "-32768") &&
        roundTrip(Short.MaxValue, "32767")
      },
      test("Short (decode error)") {
        val shortCodec = Schema[Short].derive(JsonFormat.deriver)
        decodeError("-32769", shortCodec, "value is too large for short at: .") &&
        decodeError("32768", shortCodec, "value is too large for short at: .") &&
        decodeError("01", shortCodec, "illegal number with leading zero at: .") &&
        decodeError("null", shortCodec, "illegal number at: .") &&
        decodeError("", shortCodec, "unexpected end of input at: .")
      },
      test("Int") {
        roundTrip(1, "1") &&
        roundTrip(Int.MinValue, "-2147483648") &&
        roundTrip(Int.MaxValue, "2147483647")
      },
      test("Int (decode error)") {
        val intCodec = Schema[Int].derive(JsonFormat.deriver)
        decodeError("-2147483649", intCodec, "value is too large for int at: .") &&
        decodeError("2147483648", intCodec, "value is too large for int at: .") &&
        decodeError("01", intCodec, "illegal number with leading zero at: .") &&
        decodeError("null", intCodec, "illegal number at: .") &&
        decodeError("", intCodec, "unexpected end of input at: .")
      },
      test("Long") {
        roundTrip(1L, "1") &&
        roundTrip(Long.MinValue, "-9223372036854775808") &&
        roundTrip(Long.MaxValue, "9223372036854775807")
      },
      test("Long (decode error)") {
        val longCodec = Schema[Long].derive(JsonFormat.deriver)
        decodeError("-9223372036854775809", longCodec, "value is too large for long at: .") &&
        decodeError("9223372036854775808", longCodec, "value is too large for long at: .") &&
        decodeError("01", longCodec, "illegal number with leading zero at: .") &&
        decodeError("null", longCodec, "illegal number at: .") &&
        decodeError("", longCodec, "unexpected end of input at: .")
      },
      test("Float") {
        roundTrip(42.0f, "42.0") &&
        roundTrip(Float.MinValue, "-3.4028235E38") &&
        roundTrip(Float.MaxValue, "3.4028235E38") &&
        decode("42.00000", 42.0f) &&
        decode("42.000001", 42.0f)
      },
      test("Float (decode error)") {
        val floatCodec = Schema[Float].derive(JsonFormat.deriver)
        decodeError("null", floatCodec, "illegal number at: .") &&
        decodeError("", floatCodec, "unexpected end of input at: .")
      },
      test("Double") {
        roundTrip(42.0, "42.0") &&
        roundTrip(Double.MinValue, "-1.7976931348623157E308") &&
        roundTrip(Double.MaxValue, "1.7976931348623157E308") &&
        decode("42.00000000000000", 42.0) &&
        decode("42.000000000000001", 42.0)
      },
      test("Double (decode error)") {
        val doubleCodec = Schema[Double].derive(JsonFormat.deriver)
        decodeError("null", doubleCodec, "illegal number at: .") &&
        decodeError("", doubleCodec, "unexpected end of input at: .")
      },
      test("Char") {
        roundTrip('7', "\"7\"") &&
        roundTrip('ї', "\"ї\"") &&
        roundTrip('°', "\"°\"") &&
        decode("\"\\u0037\"", '7')
      },
      test("Char (decode error)") {
        val charCodec = Schema[Char].derive(JsonFormat.deriver)
        decodeError("\"WWW\"", charCodec, "expected '\"' at: .") &&
        decodeError("\"\"", charCodec, "illegal character at: .") &&
        decodeError("", charCodec, "unexpected end of input at: .")
      },
      test("String") {
        roundTrip("Hello", "\"Hello\"") &&
        roundTrip("Привіт", "\"Привіт\"") &&
        roundTrip("★🎸🎧⋆｡ °⋆", "\"★🎸🎧⋆｡ °⋆\"")
      },
      test("String (decode error)") {
        val stringCodec = Schema[String].derive(JsonFormat.deriver)
        decodeError("", stringCodec, "unexpected end of input at: .") &&
        decodeError("\"abc", stringCodec, "unexpected end of input at: .") &&
        decodeError("\"\\ud834\\ud834\"", stringCodec, "illegal surrogate character pair at: .")
      },
      test("BigInt") {
        roundTrip(BigInt("9" * 20), "9" * 20)
      },
      test("BigDecimal") {
        roundTrip(BigDecimal("9." + "9" * 20 + "E+1234"), "9." + "9" * 20 + "E+1234")
      },
      test("DayOfWeek") {
        roundTrip(java.time.DayOfWeek.WEDNESDAY, "3") // TODO: switch to the string representation
      },
      test("Duration") {
        roundTrip(java.time.Duration.ofNanos(1234567890123456789L), "\"PT342935H31M30.123456789S\"")
      },
      test("Instant") {
        roundTrip(java.time.Instant.parse("2025-07-18T08:29:13.121409459Z"), "\"2025-07-18T08:29:13.121409459Z\"")
      },
      test("LocalDate") {
        roundTrip(java.time.LocalDate.parse("2025-07-18"), "\"2025-07-18\"")
      },
      test("LocalDateTime") {
        roundTrip(java.time.LocalDateTime.parse("2025-07-18T08:29:13.121409459"), "\"2025-07-18T08:29:13.121409459\"")
      },
      test("LocalTime") {
        roundTrip(java.time.LocalTime.parse("08:29:13.121409459"), "\"08:29:13.121409459\"")
      },
      test("Month") {
        roundTrip(java.time.Month.of(12), "12") // TODO: switch to the string representation
      },
      test("MonthDay") {
        roundTrip(java.time.MonthDay.of(12, 31), "\"--12-31\"")
      },
      test("OffsetDateTime") {
        roundTrip(
          java.time.OffsetDateTime.parse("2025-07-18T08:29:13.121409459-07:00"),
          "\"2025-07-18T08:29:13.121409459-07:00\""
        )
      },
      test("OffsetTime") {
        roundTrip(java.time.OffsetTime.parse("08:29:13.121409459-07:00"), "\"08:29:13.121409459-07:00\"")
      },
      test("Period") {
        roundTrip(java.time.Period.of(1, 12, 31), "\"P1Y12M31D\"")
      },
      test("Year") {
        roundTrip(java.time.Year.of(2025), "\"2025\"")
      },
      test("YearMonth") {
        roundTrip(java.time.YearMonth.of(2025, 7), "\"2025-07\"")
      },
      test("ZoneId") {
        roundTrip(java.time.ZoneId.of("UTC"), "\"UTC\"")
      },
      test("ZoneOffset") {
        roundTrip(java.time.ZoneOffset.ofTotalSeconds(3600), "\"+01:00\"")
      },
      test("ZonedDateTime") {
        roundTrip(
          java.time.ZonedDateTime.parse("2025-07-18T08:29:13.121409459+02:00[Europe/Warsaw]"),
          "\"2025-07-18T08:29:13.121409459+02:00[Europe/Warsaw]\""
        )
      },
      test("Currency") {
        roundTrip(Currency.getInstance("USD"), "\"USD\"")
      },
      test("UUID") {
        roundTrip(UUID.fromString("17149f63-783d-4670-b360-3be82b1420e7"), "\"17149f63-783d-4670-b360-3be82b1420e7\"")
      }
    ),
    suite("records")(
      test("simple record") {
        roundTrip(
          Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"}"""
        ) &&
        decode(
          """{"f":5.0,"d":6.0,"c":"7","b":1,"sh":2,"bl":true,"i":3,"s":"VVV","l":4}""",
          Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
        ) &&
        decode(
          """{"f":5.0,"d":6.0,"extra1":null,"c":"7","b":1,"sh":2,"bl":true,"i":3,"s":"VVV","l":4,"extra2":[1,2,"\"test\\",[],{}]}""",
          Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
        )
      },
      test("simple record (decode error)") {
        val record1Codec = Schema[Record1].derive(JsonFormat.deriver)
        decodeError("""{"""", record1Codec, "unexpected end of input at: .") &&
        decodeError("""{"bl":""", record1Codec, "unexpected end of input at: .bl") &&
        decodeError("""{"bl":true,"b":1,"sh":2,"i":3,"l":""", record1Codec, "unexpected end of input at: .l") &&
        decodeError(
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV""",
          record1Codec,
          "unexpected end of input at: .s"
        ) &&
        decodeError(
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"""",
          record1Codec,
          "unexpected end of input at: ."
        ) &&
        decodeError(
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"b":2,"f":5.0,"d":6.0,"c":"7","s":"VVV"}""",
          record1Codec,
          "duplicated field \"b\" at: ."
        ) &&
        decodeError(
          """{"bl":true,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"}""",
          record1Codec,
          "missing required field \"b\" at: ."
        ) &&
        decodeError(
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7"}""",
          record1Codec,
          "missing required field \"s\" at: ."
        ) &&
        decodeError(
          """{"bl":t,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"}""",
          record1Codec,
          "illegal boolean at: .bl"
        )
      },
      test("nested record") {
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          ),
          """{"r1_1":{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"},"r1_2":{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"}}"""
        )
      },
      test("recursive record") {
        roundTrip(
          Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))),
          """{"i":1,"ln":[{"i":2,"ln":[{"i":3,"ln":[]}]}]}"""
        )
      },
      test("record with unit and variant fields") {
        roundTrip(Record4((), Some("VVV")), """{"hidden":null,"optKey":{"Some":{"value":"VVV"}}}""") &&
        roundTrip(Record4((), None), """{"hidden":null,"optKey":{"None":{}}}""")
      },
      test("record with a custom codec for primitives injected by optic") {
        val codec = Record1.schema
          .deriving(JsonFormat.deriver)
          .instance(
            Record1.i,
            new JsonBinaryCodec[Int](JsonBinaryCodec.intType) {
              def decodeValue(in: JsonReader, default: Int): Int = in.readStringAsInt()

              def encodeValue(x: Int, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .derive
        roundTrip(
          Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
          """{"bl":true,"b":1,"sh":2,"i":"3","l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"}""",
          codec
        )
      },
      test("record with a custom codec for primitives injected by type name") {
        val codec = Record1.schema
          .deriving(JsonFormat.deriver)
          .instance(
            TypeName.int,
            new JsonBinaryCodec[Int](JsonBinaryCodec.intType) {
              def decodeValue(in: JsonReader, default: Int): Int = in.readStringAsInt()

              def encodeValue(x: Int, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .derive
        roundTrip(
          Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
          """{"bl":true,"b":1,"sh":2,"i":"3","l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"}""",
          codec
        )
      },
      test("record with a custom codec for unit injected by optic") {
        val codec = Record4.schema
          .deriving(JsonFormat.deriver)
          .instance(
            Record4.hidden,
            new JsonBinaryCodec[Unit](JsonBinaryCodec.unitType) {
              def decodeValue(in: JsonReader, default: Unit): Unit = {
                in.readString(null)
                ()
              }

              def encodeValue(x: Unit, out: JsonWriter): Unit = out.writeVal("WWW")
            }
          )
          .derive
        roundTrip(Record4((), Some("VVV")), """{"hidden":"WWW","optKey":{"Some":{"value":"VVV"}}}""", codec)
      },
      test("record with a custom codec for None injected by optic") {
        val codec = Record4.schema
          .deriving(JsonFormat.deriver)
          .instance(
            Record4.optKey,
            new JsonBinaryCodec[Option[String]]() {
              override def decodeValue(in: JsonReader, default: Option[String]): Option[String] =
                if (in.isNextToken('n')) {
                  in.rollbackToken()
                  in.skip()
                  None
                } else {
                  in.rollbackToken()
                  new Some(in.readString(null))
                }

              override def encodeValue(x: Option[String], out: JsonWriter): Unit =
                if (x eq None) out.writeNull()
                else out.writeVal(x.get)

              override def nullValue: Option[String] = None
            }
          )
          .derive
        roundTrip(Record4((), Some("VVV")), """{"hidden":null,"optKey":"VVV"}""", codec) &&
        roundTrip(Record4((), None), """{"hidden":null,"optKey":null}""", codec)
      },
      test("record with a custom codec for nested record injected by optic") {
        val codec1 = new JsonBinaryCodec[Record1]() {
          private val codec = Record1.schema.derive(JsonFormat.deriver)

          override def decodeValue(in: JsonReader, default: Record1): Record1 =
            if (in.isNextToken('n')) {
              in.rollbackToken()
              in.skip()
              null
            } else {
              in.rollbackToken()
              codec.decodeValue(in, default)
            }

          override def encodeValue(x: Record1, out: JsonWriter): Unit =
            if (x eq null) out.writeNull()
            else codec.encodeValue(x, out)
        }
        val codec2 = Record2.schema
          .deriving(JsonFormat.deriver)
          .instance(Record2.r1_1, codec1)
          .instance(Record2.r1_2, codec1)
          .derive
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(false, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "WWW")
          ),
          """{"r1_1":{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"},"r1_2":{"bl":false,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"WWW"}}""",
          codec2
        ) &&
        roundTrip(Record2(null, null), """{"r1_1":null,"r1_2":null}""", codec2)
      },
      test("record with a custom codec for nested primitives injected by optic") {
        val codec = Record2.schema
          .deriving(JsonFormat.deriver)
          .instance(
            TypeName.int,
            new JsonBinaryCodec[Int](JsonBinaryCodec.intType) {
              def decodeValue(in: JsonReader, default: Int): Int = in.readStringAsInt()

              def encodeValue(x: Int, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            Record2.r1_2_i,
            new JsonBinaryCodec[Int](JsonBinaryCodec.intType) {
              def decodeValue(in: JsonReader, default: Int): Int = in.readDouble().toInt

              def encodeValue(x: Int, out: JsonWriter): Unit = out.writeVal(x.toDouble)
            }
          )
          .derive
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(false, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "WWW")
          ),
          """{"r1_1":{"bl":true,"b":1,"sh":2,"i":"3","l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"},"r1_2":{"bl":false,"b":1,"sh":2,"i":3.0,"l":4,"f":5.0,"d":6.0,"c":"7","s":"WWW"}}""",
          codec
        )
      },
      test("record with a custom codec for nested record injected by type name") {
        val codec = Record2.schema
          .deriving(JsonFormat.deriver)
          .instance(
            Record1.schema.reflect.typeName,
            new JsonBinaryCodec[Record1]() {
              private val codec = Record1.schema.derive(JsonFormat.deriver)

              override def decodeValue(in: JsonReader, default: Record1): Record1 =
                if (in.isNextToken('n')) {
                  in.rollbackToken()
                  in.skip()
                  null
                } else {
                  in.rollbackToken()
                  codec.decodeValue(in, default)
                }

              override def encodeValue(x: Record1, out: JsonWriter): Unit =
                if (x eq null) out.writeNull()
                else codec.encodeValue(x, out)
            }
          )
          .derive
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          ),
          """{"r1_1":{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"},"r1_2":{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"}}""",
          codec
        ) &&
        roundTrip(Record2(null, null), """{"r1_1":null,"r1_2":null}""", codec)
      },
      test("recursive record with a custom codec") {
        val codec = Recursive.schema
          .deriving(JsonFormat.deriver)
          .instance(
            Recursive.i,
            new JsonBinaryCodec[Int](JsonBinaryCodec.intType) {
              def decodeValue(in: JsonReader, default: Int): Int = in.readStringAsInt()

              def encodeValue(x: Int, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .derive
        roundTrip(
          Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))),
          """{"i":"1","ln":[{"i":"2","ln":[{"i":"3","ln":[]}]}]}""",
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

        roundTrip(Array[Unit]((), (), ()), """[null,null,null]""") &&
        roundTrip(Array[Boolean](true, false, true), """[true,false,true]""") &&
        roundTrip(Array[Byte](1: Byte, 2: Byte, 3: Byte), """[1,2,3]""") &&
        roundTrip(Array[Short](1: Short, 2: Short, 3: Short), """[1,2,3]""") &&
        roundTrip(Array('1', '2', '3'), """["1","2","3"]""") &&
        roundTrip((1 to 100).toList, (1 to 100).mkString("[", ",", "]")) &&
        roundTrip(Set(1L, 2L, 3L), """[1,2,3]""") &&
        roundTrip(ArraySeq(1.0f, 2.0f, 3.0f), """[1.0,2.0,3.0]""") &&
        roundTrip(Vector(1.0, 2.0, 3.0), """[1.0,2.0,3.0]""") &&
        roundTrip(List("1", "2", "3"), """["1","2","3"]""") &&
        roundTrip(List(BigInt(1), BigInt(2), BigInt(3)), """[1,2,3]""") &&
        roundTrip(
          List(java.time.LocalDate.of(2025, 1, 1), java.time.LocalDate.of(2025, 1, 2)),
          """["2025-01-01","2025-01-02"]"""
        ) &&
        roundTrip(
          List(UUID.fromString("17149f63-783d-4670-b360-3be82b1420e7")),
          """["17149f63-783d-4670-b360-3be82b1420e7"]"""
        )
      },
      test("primitive values (decode error)") {
        val intListCodec = Schema[List[Int]].derive(JsonFormat.deriver)
        decodeError("", intListCodec, "unexpected end of input at: .") &&
        decodeError("[1,2,3,4", intListCodec, "unexpected end of input at: .at(3)") &&
        decodeError("""[1,2,3,null]""", intListCodec, "illegal number at: .at(3)")
      },
      test("complex values") {
        roundTrip(
          List(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          ),
          """[{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"},{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"}]"""
        )
      },
      test("recursive values") {
        roundTrip(
          List(
            Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))),
            Recursive(4, List(Recursive(5, List(Recursive(6, Nil)))))
          ),
          """[{"i":1,"ln":[{"i":2,"ln":[{"i":3,"ln":[]}]}]},{"i":4,"ln":[{"i":5,"ln":[{"i":6,"ln":[]}]}]}]"""
        )
      }
    ),
    suite("maps")(
      test("primitive key map") {
        roundTrip(Map("VVV" -> 1, "WWW" -> 2), """{"VVV":1,"WWW":2}""") &&
        roundTrip(Map(1 -> 1.0, 2 -> 2.0), """{"1":1.0,"2":2.0}""") &&
        roundTrip(Map(BigInt(1) -> 1.0f, BigInt(2) -> 2.0f), """{"1":1.0,"2":2.0}""") &&
        roundTrip(
          Map(java.time.LocalDate.of(2025, 1, 1) -> 1, java.time.LocalDate.of(2025, 1, 2) -> 2),
          """{"2025-01-01":1,"2025-01-02":2}"""
        )
      },
      test("primitive key map (decode error)") {
        val intToLongMapCodec = Schema[Map[Int, Long]].derive(JsonFormat.deriver)
        decodeError("", intToLongMapCodec, "unexpected end of input at: .") &&
        decodeError("""{"1"""", intToLongMapCodec, "unexpected end of input at: .at(0)") &&
        decodeError("""{"1":""", intToLongMapCodec, "unexpected end of input at: .atKey(<key>)")
      },
      test("primitive key with recursive values") {
        roundTrip(
          Map(
            1 -> Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))),
            2 -> Recursive(4, List(Recursive(5, List(Recursive(6, Nil)))))
          ),
          """{"1":{"i":1,"ln":[{"i":2,"ln":[{"i":3,"ln":[]}]}]},"2":{"i":4,"ln":[{"i":5,"ln":[{"i":6,"ln":[]}]}]}}"""
        )
      },
      test("nested maps") {
        roundTrip(Map("VVV" -> Map(1 -> 1L, 2 -> 2L)), """{"VVV":{"1":1,"2":2}}""")
      }
    ),
    suite("enums")(
      test("constant values") {
        roundTrip[TrafficLight](TrafficLight.Green, """{"Green":{}}""") &&
        roundTrip[TrafficLight](TrafficLight.Yellow, """{"Yellow":{}}""") &&
        roundTrip[TrafficLight](TrafficLight.Red, """{"Red":{}}""")
      },
      test("constant values (decode error)") {
        val trafficLightCodec = Schema[TrafficLight].derive(JsonFormat.deriver)
        decodeError("""{"Black":{}}""", trafficLightCodec, "unexpected discriminator key at: .")
      },
      test("option") {
        roundTrip(Option(42), """{"Some":{"value":42}}""") &&
        roundTrip[Option[Int]](None, """{"None":{}}""")
      },
      test("option (decode error)") {
        val intOptionCodec = Schema[Option[Int]].derive(JsonFormat.deriver)
        decodeError("""{"Option":{"value":42}}""", intOptionCodec, "unexpected discriminator key at: .") &&
        decodeError("""{"Some":{"value":}}""", intOptionCodec, "illegal number at: .when[Some].value")
      },
      test("either") {
        roundTrip[Either[String, Int]](Right(42), """{"Right":{"value":42}}""") &&
        roundTrip[Either[String, Int]](Left("VVV"), """{"Left":{"value":"VVV"}}""")
      }
    ),
    suite("wrapper")(
      test("top-level") {
        roundTrip[UserId](UserId(1234567890123456789L), "1234567890123456789") &&
        roundTrip[Email](Email("john@gmail.com"), "\"john@gmail.com\"")
      },
      test("top-level (decode error)") {
        val emailCodec = Schema[Email].derive(JsonFormat.deriver)
        decodeError("\"john&gmail.com\"", emailCodec, "expected e-mail at: .") &&
        decodeError("\"john@gmail.com", emailCodec, "unexpected end of input at: .wrapped")
      },
      test("as a record field") {
        roundTrip[Record3](
          Record3(UserId(1234567890123456789L), Email("backup@gmail.com")),
          """{"userId":1234567890123456789,"email":"backup@gmail.com"}"""
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
        TypeName(Namespace(Seq("zio", "blocks", "avro"), Seq("JsonFormatSpec")), "Email"),
        None,
        new Binding.Wrapper(
          {
            case x @ EmailRegex(_*) => new Right(new Email(x))
            case _                  => new Left("expected e-mail")
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

    val hidden: Lens[Record4, Unit]           = $(_.hidden)
    val optKey: Lens[Record4, Option[String]] = $(_.optKey)
  }

  case class Dynamic(primitive: DynamicValue, map: DynamicValue)

  object Dynamic extends CompanionOptics[Dynamic] {
    implicit val schema: Schema[Dynamic] = Schema.derived

    val primitive: Lens[Dynamic, DynamicValue] = $(_.primitive)
    val map: Lens[Dynamic, DynamicValue]       = $(_.map)
  }
}
