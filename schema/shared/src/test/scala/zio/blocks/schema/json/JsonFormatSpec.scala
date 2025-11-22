package zio.blocks.schema.json

import zio.blocks.schema.json.JsonTestUtils._
import zio.blocks.schema._
import zio.blocks.schema.binding.Binding
import zio.test._
import zio.test.TestAspect.jvmOnly
import java.time._
import java.util.{Currency, UUID}
import scala.collection.immutable.ArraySeq

object JsonFormatSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("JsonFormatSpec")(
    suite("primitives")(
      test("Unit") {
        roundTrip((), "null")
      },
      test("Unit (decode error)") {
        val codec = Schema[Unit].derive(JsonFormat.deriver)
        decodeError("", codec, "unexpected end of input at: .") &&
        decodeError("true", codec, "expected null at: .")
      },
      test("Boolean") {
        decode(" true", true) &&
        roundTrip(true, "true") &&
        roundTrip(false, "false")
      },
      test("Boolean (decode error)") {
        val codec = Schema[Boolean].derive(JsonFormat.deriver)
        decodeError("", codec, "unexpected end of input at: .") &&
        decodeError("falsy", codec, "illegal boolean at: .") &&
        decodeError("tralse", codec, "illegal boolean at: .")
      },
      test("Byte") {
        roundTrip(1: Byte, "1") &&
        roundTrip(10: Byte, "10") &&
        roundTrip(Byte.MinValue, "-128") &&
        roundTrip(Byte.MaxValue, "127")
      },
      test("Byte (decode error)") {
        val codec = Schema[Byte].derive(JsonFormat.deriver)
        decodeError("-129", codec, "value is too large for byte at: .") &&
        decodeError("128", codec, "value is too large for byte at: .") &&
        decodeError("01", codec, "illegal number with leading zero at: .") &&
        decodeError("1.0", codec, "illegal number at: .") &&
        decodeError("1e-1", codec, "illegal number at: .") &&
        decodeError("null", codec, "illegal number at: .") &&
        decodeError("", codec, "unexpected end of input at: .")
      },
      test("Short") {
        roundTrip(1: Short, "1") &&
        roundTrip(10: Short, "10") &&
        roundTrip(100: Short, "100") &&
        roundTrip(1000: Short, "1000") &&
        roundTrip(10000: Short, "10000") &&
        roundTrip(Short.MinValue, "-32768") &&
        roundTrip(Short.MaxValue, "32767")
      },
      test("Short (decode error)") {
        val codec = Schema[Short].derive(JsonFormat.deriver)
        decodeError("-32769", codec, "value is too large for short at: .") &&
        decodeError("32768", codec, "value is too large for short at: .") &&
        decodeError("01", codec, "illegal number with leading zero at: .") &&
        decodeError("1.0", codec, "illegal number at: .") &&
        decodeError("1e-1", codec, "illegal number at: .") &&
        decodeError("null", codec, "illegal number at: .") &&
        decodeError("", codec, "unexpected end of input at: .")
      },
      test("Int") {
        roundTrip(1, "1") &&
        roundTrip(Int.MinValue, "-2147483648") &&
        roundTrip(Int.MaxValue, "2147483647")
      },
      test("Int (decode error)") {
        val codec = Schema[Int].derive(JsonFormat.deriver)
        decodeError("-2147483649", codec, "value is too large for int at: .") &&
        decodeError("2147483648", codec, "value is too large for int at: .") &&
        decodeError("01", codec, "illegal number with leading zero at: .") &&
        decodeError("1.0", codec, "illegal number at: .") &&
        decodeError("1e-1", codec, "illegal number at: .") &&
        decodeError("null", codec, "illegal number at: .") &&
        decodeError("", codec, "unexpected end of input at: .")
      },
      test("Long") {
        roundTrip(1L, "1") &&
        roundTrip(Long.MinValue, "-9223372036854775808") &&
        roundTrip(Long.MaxValue, "9223372036854775807")
      },
      test("Long (decode error)") {
        val codec = Schema[Long].derive(JsonFormat.deriver)
        decodeError("-9223372036854775809", codec, "value is too large for long at: .") &&
        decodeError("9223372036854775808", codec, "value is too large for long at: .") &&
        decodeError("01", codec, "illegal number with leading zero at: .") &&
        decodeError("1.0", codec, "illegal number at: .") &&
        decodeError("1e-1", codec, "illegal number at: .") &&
        decodeError("null", codec, "illegal number at: .") &&
        decodeError("", codec, "unexpected end of input at: .")
      },
      test("Float") {
        roundTrip(42.0f, "42.0") &&
        roundTrip(Float.MinValue, "-3.4028235E38") &&
        roundTrip(Float.MaxValue, "3.4028235E38") &&
        roundTrip(0.0f, "0.0") &&
        roundTrip(-0.0f, "-0.0") &&
        roundTrip(1.0e17f, "1.0E17") &&
        roundTrip(0.33007812f, "0.33007812") &&
        roundTrip(102067.11f, "102067.11") &&
        roundTrip(1.6777216e7f, "1.6777216E7") &&
        roundTrip(1.4e-45f, "1.4E-45") &&
        roundTrip(9.8e-45f, "9.8E-45") &&
        roundTrip(6.895867e-31f, "6.895867E-31") &&
        roundTrip(1.595711e-5f, "1.595711E-5") &&
        roundTrip(-1.5887592e7f, "-1.5887592E7") &&
        decode("42.00000", 42.0f) &&
        decode("42.000001", 42.0f) &&
        decode("37930954282500097", 3.7930956e16f) && // Fast path
        decode("48696272630054913", 4.8696275e16f) &&
        decode("69564.0e6", 6.9564e10f) &&
        decode("16777217.0", 1.6777216e7f) && // Round-down, halfway
        decode("33554434.0", 3.3554432e7f) &&
        decode("17179870208.0", 1.717987e10f) &&
        decode("16777219.0", 1.677722e7f) && // Round-up, halfway
        decode("33554438.0", 3.355444e7f) &&
        decode("17179872256.0", 1.7179873e10f) &&
        decode("33554435.0", 3.3554436e7f) && // Round-up, above halfway
        decode("17179870209.0", 1.7179871e10f) &&
        decode("1.00000017881393432617187499", 1.0000001f) && // Check exactly halfway, round-up at halfway
        decode("1.000000178813934326171875", 1.0000002f) &&
        decode("1.00000017881393432617187501", 1.0000002f) &&
        decode("36028797018963967.0", 3.6028797e16f) && // 2^n - 1 integer regression
        decode("1.17549435E-38", 1.1754944e-38f) &&
        decode("12345e6789", Float.PositiveInfinity) && // Parse infinities on float overflow
        decode("-12345e6789", Float.NegativeInfinity) &&
        decode("123456789012345678901234567890e9223372036854775799", Float.PositiveInfinity) &&
        decode("-123456789012345678901234567890e9223372036854775799", Float.NegativeInfinity) &&
        decode("12345678901234567890e12345678901234567890", Float.PositiveInfinity) &&
        decode("-12345678901234567890e12345678901234567890", Float.NegativeInfinity) &&
        decode("37879.0e37", Float.PositiveInfinity) &&
        decode("-37879.0e37", Float.NegativeInfinity) &&
        decode("12345e-6789", 0.0f) && // Parse zeroes on float underflow
        decode("-12345e-6789", -0.0f) &&
        decode("0.12345678901234567890e-9223372036854775799", 0.0f) &&
        decode("-0.12345678901234567890e-9223372036854775799", -0.0f) &&
        decode("12345678901234567890e-12345678901234567890", 0.0f) &&
        decode("-12345678901234567890e-12345678901234567890", -0.0f)
      },
      test("Float (decode error)") {
        val codec = Schema[Float].derive(JsonFormat.deriver)
        encodeError(Float.PositiveInfinity, codec, "illegal number: Infinity") &&
        encodeError(Float.NegativeInfinity, codec, "illegal number: -Infinity") &&
        decodeError("null", codec, "illegal number at: .") &&
        decodeError("1. ", codec, "illegal number at: .") &&
        decodeError("1e+e", codec, "illegal number at: .") &&
        decodeError("01", codec, "illegal number with leading zero at: .") &&
        decodeError("", codec, "unexpected end of input at: .")
      },
      test("Double") {
        roundTrip(42.0, "42.0") &&
        roundTrip(Double.MinValue, "-1.7976931348623157E308") &&
        roundTrip(Double.MaxValue, "1.7976931348623157E308") &&
        roundTrip(0.0, "0.0") &&
        roundTrip(-0.0, "-0.0") &&
        roundTrip(0.001, "0.001") &&
        roundTrip(1.0e7, "1.0E7") &&
        roundTrip(8572.431613041595, "8572.431613041595") &&
        roundTrip(4.9e-324, "4.9E-324") &&
        roundTrip(8.707795712926552e15, "8.707795712926552E15") &&
        roundTrip(5.960464477539063e-8, "5.960464477539063E-8") &&
        decode("42.00000000000000", 42.0) &&
        decode("42.000000000000001", 42.0) &&
        decode("6377181959482780", 6.37718195948278e15) && // Fast path
        decode("797671681584247.0e19", 7.97671681584247e33) &&
        decode("35785831.0e24", 3.5785831e31) &&
        decode("1624908.0e17", 1.624908e23) &&
        decode("358875.0e-315", 3.58875e-310) &&              // Middle path
        decode("9007199254740993.0", 9.007199254740992e15) && // Round-down, halfway
        decode("18014398509481986.0", 1.8014398509481984e16) &&
        decode("9223372036854776832.0", 9.223372036854776e18) &&
        decode("11417981541647680316116887983825362587765178368.0", 1.141798154164768e46) &&
        decode("9007199254740995.0", 9.007199254740996e15) && // Round-up, halfway
        decode("18014398509481990.0", 1.801439850948199e16) &&
        decode("9223372036854778880.0", 9.22337203685478e18) &&
        decode("11417981541647682851418088440284165581171589120.0", 1.1417981541647684e46) &&
        decode("9223372036854776833.0", 9.223372036854778e18) && // Round-up, above halfway
        decode("11417981541647680316116887983825362587765178369.0", 1.1417981541647682e46) &&
        decode("36028797018963967.0", 3.602879701896397e16) && // 2^n - 1 integer regression
        decode(
          "11224326888185522059941158352151320185835795563643008",
          1.1224326888185523e52
        ) &&                                             // Regression after reducing an error range
        decode("12345e6789", Double.PositiveInfinity) && // Parse infinities on double overflow
        decode("-12345e6789", Double.NegativeInfinity) &&
        decode("123456789012345678901234567890e9223372036854775799", Double.PositiveInfinity) &&
        decode("-123456789012345678901234567890e9223372036854775799", Double.NegativeInfinity) &&
        decode("12345678901234567890e12345678901234567890", Double.PositiveInfinity) &&
        decode("-12345678901234567890e12345678901234567890", Double.NegativeInfinity) &&
        decode("3190749093868358880.0e291", Double.PositiveInfinity) &&
        decode("-3190749093868358880.0e291", Double.NegativeInfinity) &&
        decode("12345e-6789", 0.0) && // Parse zeroes on double underflow
        decode("-12345e-6789", -0.0) &&
        decode("0.12345678901234567890e-9223372036854775799", 0.0) &&
        decode("-0.12345678901234567890e-9223372036854775799", -0.0) &&
        decode("12345678901234567890e-12345678901234567890", 0.0) &&
        decode("-1234567890123456789e-12345678901234567890", -0.0) &&
        decode("15.0e-334", 0.0)
      },
      test("Double (decode error)") {
        val codec = Schema[Double].derive(JsonFormat.deriver)
        encodeError(Double.PositiveInfinity, codec, "illegal number: Infinity") &&
        encodeError(Double.NegativeInfinity, codec, "illegal number: -Infinity") &&
        decodeError("null", codec, "illegal number at: .") &&
        decodeError("1. ", codec, "illegal number at: .") &&
        decodeError("1e+e", codec, "illegal number at: .") &&
        decodeError("01", codec, "illegal number with leading zero at: .") &&
        decodeError("", codec, "unexpected end of input at: .")
      },
      test("Char") {
        roundTrip('7', "\"7\"") &&
        roundTrip('ї', "\"ї\"") &&
        roundTrip('★', """"★"""") &&
        roundTrip('\\', """"\\"""") &&
        roundTrip('\b', """"\b"""") &&
        roundTrip('\f', """"\f"""") &&
        roundTrip('\n', """"\n"""") &&
        roundTrip('\r', """"\r"""") &&
        roundTrip('\t', """"\t"""") &&
        roundTrip('"', """"\""""") &&
        decode(""""\/"""", '/') &&
        decode("\"\\u0037\"", '7')
      },
      test("Char (decode error)") {
        val codec = Schema[Char].derive(JsonFormat.deriver)
        decodeError("\"WWW\"", codec, "expected '\"' at: .") &&
        decodeError("\"\"", codec, "illegal character at: .") &&
        decodeError(""""\x"""", codec, "illegal escape sequence at: .") &&
        decodeError(""""\x0008"""", codec, "illegal escape sequence at: .") &&
        decodeError("\"\u001F\"", codec, "unescaped control character at: .") &&
        decodeError("", codec, "unexpected end of input at: .")
      },
      test("String") {
        roundTrip("Hello", "\"Hello\"") &&
        roundTrip("Привіт", "\"Привіт\"") &&
        roundTrip("★🎸🎧⋆｡ °⋆", "\"★🎸🎧⋆｡ °⋆\"")
      },
      test("String (decode error)") {
        val codec = Schema[String].derive(JsonFormat.deriver)
        decodeError("", codec, "unexpected end of input at: .") &&
        decodeError("\"abc", codec, "unexpected end of input at: .") &&
        decodeError("\"\\ud834\\ud834\"", codec, "illegal surrogate character pair at: .")
      },
      test("BigInt") {
        roundTrip(BigInt(42), "42") &&
        roundTrip(BigInt(0), "0") &&
        roundTrip(BigInt("-" + "9" * 3), "-" + "9" * 3) &&
        roundTrip(BigInt("9" * 30), "9" * 30) &&
        roundTrip(BigInt("9" * 300), "9" * 300)
      },
      test("BigInt (decode error)") {
        val codec = Schema[BigInt].derive(JsonFormat.deriver)
        decodeError("", codec, "unexpected end of input at: .") &&
        decodeError("08", codec, "illegal number with leading zero at: .") &&
        decodeError("--8", codec, "illegal number at: .")
      },
      test("BigDecimal") {
        roundTrip(BigDecimal("42.0"), "42.0") &&
        roundTrip(BigDecimal("0.0"), "0.0") &&
        roundTrip(BigDecimal("-1." + "1" * 3 + "E+1234"), "-1." + "1" * 3 + "E+1234") &&
        roundTrip(BigDecimal("1." + "1" * 30 + "E+1234"), "1." + "1" * 30 + "E+1234") &&
        decode("1." + "1" * 300 + "E+1234", BigDecimal("1.111111111111111111111111111111111E+1234"))
      },
      test("BigDecimal (decode error)") {
        val codec = Schema[BigDecimal].derive(JsonFormat.deriver)
        decodeError("", codec, "unexpected end of input at: .") &&
        decodeError("1. ", codec, "illegal number at: .") &&
        decodeError("1e+e", codec, "illegal number at: .") &&
        decodeError("--8", codec, "illegal number at: .") &&
        decodeError("08", codec, "illegal number with leading zero at: .")
      },
      test("DayOfWeek") {
        roundTrip(DayOfWeek.WEDNESDAY, "3") // TODO: switch to the string representation
      },
      test("DayOfWeek (decode error)") {
        val codec = Schema[DayOfWeek].derive(JsonFormat.deriver)
        decodeError("", codec, "unexpected end of input at: .") &&
        decodeError("08", codec, "illegal number with leading zero at: .") &&
        decodeError("8", codec, "Invalid value for DayOfWeek: 8 at: .")
      },
      test("Duration") {
        roundTrip(Duration.ofNanos(1234567890123456789L), "\"PT342935H31M30.123456789S\"")
      },
      test("Instant") {
        decode("\"2025-07-18T08:29:13.121409459+01:00\"", Instant.parse("2025-07-18T07:29:13.121409459Z")) &&
        roundTrip(Instant.parse("2025-07-18T08:29:13.121409459Z"), "\"2025-07-18T08:29:13.121409459Z\"")
      },
      test("LocalDate") {
        roundTrip(LocalDate.parse("2025-07-18"), "\"2025-07-18\"")
      },
      test("LocalDateTime") {
        roundTrip(LocalDateTime.parse("2025-07-18T08:29:13.121409459"), "\"2025-07-18T08:29:13.121409459\"")
      },
      test("LocalTime") {
        roundTrip(LocalTime.parse("08:29:13.121409459"), "\"08:29:13.121409459\"")
      },
      test("Month") {
        roundTrip(Month.of(12), "12") // TODO: switch to the string representation
      },
      test("MonthDay") {
        roundTrip(MonthDay.of(12, 31), "\"--12-31\"")
      },
      test("OffsetDateTime") {
        roundTrip(
          OffsetDateTime.parse("2025-07-18T08:29:13.121409459-07:00"),
          "\"2025-07-18T08:29:13.121409459-07:00\""
        )
      },
      test("OffsetTime") {
        roundTrip(OffsetTime.parse("08:29:13.121409459-07:00"), "\"08:29:13.121409459-07:00\"")
      },
      test("Period") {
        roundTrip(Period.of(1, 12, 31), "\"P1Y12M31D\"")
      },
      test("Year") {
        roundTrip(Year.of(2025), "\"2025\"")
      },
      test("YearMonth") {
        roundTrip(YearMonth.of(2025, 7), "\"2025-07\"")
      },
      test("ZoneId") {
        roundTrip(ZoneId.of("UTC"), "\"UTC\"")
      },
      test("ZoneOffset") {
        roundTrip(ZoneOffset.ofTotalSeconds(3600), "\"+01:00\"")
      },
      test("ZonedDateTime") {
        roundTrip(
          ZonedDateTime.parse("2025-07-18T08:29:13.121409459+02:00[Europe/Warsaw]"),
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
        val codec = Schema[Record1].derive(JsonFormat.deriver)
        decodeError("""null""", codec, "expected '{' at: .") &&
        decodeError("""{"""", codec, "unexpected end of input at: .") &&
        decodeError("""{"bl":""", codec, "unexpected end of input at: .bl") &&
        decodeError("""{"bl":true,"b":1,"sh":2,"i":3,"l":""", codec, "unexpected end of input at: .l") &&
        decodeError(
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV""",
          codec,
          "unexpected end of input at: .s"
        ) &&
        decodeError(
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"""",
          codec,
          "unexpected end of input at: ."
        ) &&
        decodeError(
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"b":2,"f":5.0,"d":6.0,"c":"7","s":"VVV"}""",
          codec,
          "duplicated field \"b\" at: ."
        ) &&
        decodeError(
          """{"bl":true,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"}""",
          codec,
          "missing required field \"b\" at: ."
        ) &&
        decodeError(
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7"}""",
          codec,
          "missing required field \"s\" at: ."
        ) &&
        decodeError(
          """{"bl":t,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"}""",
          codec,
          "illegal boolean at: .bl"
        ) &&
        decodeError(
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"]""",
          codec,
          "expected '}' or ',' at: ."
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
        roundTrip(Record4((), Some("VVV")), """{"hіdden":null,"optKеy":"VVV"}""") &&
        roundTrip(Record4((), None), """{"hіdden":null}""")
      },
      test("record with a custom codec for primitives injected by optic") {
        val codec = Record1.schema
          .deriving(JsonFormat.deriver)
          .instance(
            Record1.bl,
            new JsonBinaryCodec[Boolean](JsonBinaryCodec.booleanType) { // stringifies boolean values
              def decodeValue(in: JsonReader, default: Boolean): Boolean = in.readStringAsBoolean()

              def encodeValue(x: Boolean, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            Record1.b,
            new JsonBinaryCodec[Byte](JsonBinaryCodec.byteType) { // stringifies byte values
              def decodeValue(in: JsonReader, default: Byte): Byte = in.readStringAsByte()

              def encodeValue(x: Byte, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            Record1.sh,
            new JsonBinaryCodec[Short](JsonBinaryCodec.shortType) { // stringifies short values
              def decodeValue(in: JsonReader, default: Short): Short = in.readStringAsShort()

              def encodeValue(x: Short, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            Record1.i,
            new JsonBinaryCodec[Int](JsonBinaryCodec.intType) { // stringifies int values
              def decodeValue(in: JsonReader, default: Int): Int = in.readStringAsInt()

              def encodeValue(x: Int, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            Record1.l,
            new JsonBinaryCodec[Long](JsonBinaryCodec.longType) { // stringifies long values
              def decodeValue(in: JsonReader, default: Long): Long = in.readStringAsLong()

              def encodeValue(x: Long, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            Record1.f,
            new JsonBinaryCodec[Float](JsonBinaryCodec.floatType) { // stringifies float values
              def decodeValue(in: JsonReader, default: Float): Float = in.readStringAsFloat()

              def encodeValue(x: Float, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            Record1.d,
            new JsonBinaryCodec[Double](JsonBinaryCodec.doubleType) { // stringifies double values
              def decodeValue(in: JsonReader, default: Double): Double = in.readStringAsDouble()

              def encodeValue(x: Double, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            Record1.c,
            new JsonBinaryCodec[Char](JsonBinaryCodec.charType) { // expecting char code numbers (not one-char strings)
              def decodeValue(in: JsonReader, default: Char): Char = in.readInt().toChar

              def encodeValue(x: Char, out: JsonWriter): Unit = out.writeVal(x.toInt)
            }
          )
          .derive
        roundTrip(
          Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
          """{"bl":"true","b":"1","sh":"2","i":"3","l":"4","f":"5.0","d":"6.0","c":55,"s":"VVV"}""",
          codec
        )
      },
      test("record with a custom codec for primitives injected by type name") {
        val codec = Record3.schema
          .deriving(JsonFormat.deriver)
          .instance(
            TypeName.currency,
            new JsonBinaryCodec[Currency]() { // decode null values as the default one ("USD")
              def decodeValue(in: JsonReader, default: Currency): Currency =
                if (in.isNextToken('n')) {
                  in.readNullOrError(default, "expected null")
                  default
                } else {
                  in.rollbackToken()
                  Currency.getInstance(in.readString(null))
                }

              def encodeValue(x: Currency, out: JsonWriter): Unit = out.writeVal(x.toString)

              override def nullValue: Currency = Currency.getInstance("USD")
            }
          )
          .derive
        roundTrip(
          Record3(UserId(1234567890123456789L), Email("backup@gmail.com"), Currency.getInstance("USD")),
          """{"userId":1234567890123456789,"email":"backup@gmail.com","currency":"USD"}""",
          codec
        ) &&
        decode(
          """{"userId":1234567890123456789,"email":"backup@gmail.com","currency":null}""",
          Record3(UserId(1234567890123456789L), Email("backup@gmail.com"), Currency.getInstance("USD")),
          codec
        )
      },
      test("record with a custom codec for unit injected by optic") {
        val codec = Record4.schema
          .deriving(JsonFormat.deriver)
          .instance(
            Record4.hidden,
            new JsonBinaryCodec[Unit](JsonBinaryCodec.unitType) { // expecting string instead of null
              def decodeValue(in: JsonReader, default: Unit): Unit = {
                in.readString(null)
                ()
              }

              def encodeValue(x: Unit, out: JsonWriter): Unit = out.writeVal("WWW")
            }
          )
          .derive
        roundTrip(Record4((), Some("VVV")), """{"hіdden":"WWW","optKеy":"VVV"}""", codec)
      },
      test("record with a custom codec for None injected by optic") {
        val codec = Record4.schema
          .deriving(JsonFormat.deriver)
          .instance(
            Record4.optKey,
            new JsonBinaryCodec[Option[String]]() { // more efficient decoding than with derived by default
              override def decodeValue(in: JsonReader, default: Option[String]): Option[String] =
                if (in.isNextToken('n')) in.readNullOrError(default, "expected null")
                else {
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
        roundTrip(Record4((), Some("VVV")), """{"hіdden":null,"optKеy":"VVV"}""", codec) &&
        roundTrip(Record4((), None), """{"hіdden":null}""", codec)
      },
      test("record with a custom codec for nested record injected by optic") {
        val codec1 =
          new JsonBinaryCodec[Record1]() { // allows null values which are prohibited in codecs derived by default
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
            new JsonBinaryCodec[Int](JsonBinaryCodec.intType) { // stringifies int values
              def decodeValue(in: JsonReader, default: Int): Int = in.readStringAsInt()

              def encodeValue(x: Int, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            Record2.r1_2_i,
            new JsonBinaryCodec[Int](JsonBinaryCodec.intType) { // expecting FP numbers and truncating them to int
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
            new JsonBinaryCodec[Record1]() { // allows null values which are prohibited for codecs derived by default
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
            new JsonBinaryCodec[Int](JsonBinaryCodec.intType) { // stringifies int values
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

        decode("""null""", List.empty[Int]) &&
        roundTrip(List.empty[Int], """[]""") &&
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
          List(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2)),
          """["2025-01-01","2025-01-02"]"""
        ) &&
        roundTrip(
          List(UUID.fromString("17149f63-783d-4670-b360-3be82b1420e7")),
          """["17149f63-783d-4670-b360-3be82b1420e7"]"""
        )
      },
      test("primitive values (decode error)") {
        val codec = Schema[List[Int]].derive(JsonFormat.deriver)
        decodeError("", codec, "unexpected end of input at: .") &&
        decodeError("true", codec, "expected '[' or null at: .") &&
        decodeError("[1,2,3,4}", codec, "expected ']' or ',' at: .") &&
        decodeError("[1,2,3,4", codec, "unexpected end of input at: .at(3)") &&
        decodeError("""[1,2,3,null]""", codec, "illegal number at: .at(3)")
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
        decode("""null""", Map.empty[Int, Long]) &&
        roundTrip(Map.empty[Int, Long], """{}""") &&
        roundTrip(Map("VVV" -> 1, "WWW" -> 2), """{"VVV":1,"WWW":2}""") &&
        roundTrip(Map(true -> 1.0, false -> 2.0), """{"true":1.0,"false":2.0}""") &&
        roundTrip(Map(1.toByte -> 1.0, 2.toByte -> 2.0), """{"1":1.0,"2":2.0}""") &&
        roundTrip(Map(1.toShort -> 1.0, 2.toShort -> 2.0), """{"1":1.0,"2":2.0}""") &&
        roundTrip(Map(1 -> 1.0, 2 -> 2.0), """{"1":1.0,"2":2.0}""") &&
        roundTrip(Map(1L -> 1.0f, 2L -> 2.0f), """{"1":1.0,"2":2.0}""") &&
        roundTrip(Map(1.0f -> 1L, 2.0f -> 2L), """{"1.0":1,"2.0":2}""") &&
        roundTrip(Map(1.0 -> 1, 2.0 -> 2), """{"1.0":1,"2.0":2}""") &&
        roundTrip(Map('1' -> 1.0, '2' -> 2.0), """{"1":1.0,"2":2.0}""") &&
        roundTrip(Map(BigInt(1) -> 1.0f, BigInt(2) -> 2.0f), """{"1":1.0,"2":2.0}""") &&
        roundTrip(Map(BigDecimal(1.1) -> 1, BigDecimal(2.2) -> 2), """{"1.1":1,"2.2":2}""") &&
        roundTrip(Map(DayOfWeek.of(1) -> 1, DayOfWeek.of(2) -> 2), """{"1":1,"2":2}""") &&
        roundTrip(Map(Duration.ofDays(1) -> 1, Duration.ofHours(2) -> 2), """{"PT24H":1,"PT2H":2}""") &&
        roundTrip(
          Map(
            Instant.parse("2025-07-18T08:29:13.121409459Z") -> 1,
            Instant.parse("2026-01-20T17:02:32.830531701Z") -> 2
          ),
          """{"2025-07-18T08:29:13.121409459Z":1,"2026-01-20T17:02:32.830531701Z":2}"""
        ) &&
        roundTrip(
          Map(LocalDate.of(2025, 1, 1) -> 1, LocalDate.of(2025, 1, 2) -> 2),
          """{"2025-01-01":1,"2025-01-02":2}"""
        ) &&
        roundTrip(
          Map(LocalDateTime.of(2025, 1, 1, 10, 0) -> 1, LocalDateTime.of(2025, 1, 2, 12, 0) -> 2),
          """{"2025-01-01T10:00":1,"2025-01-02T12:00":2}"""
        ) &&
        roundTrip(Map(LocalTime.of(10, 0) -> 1, LocalTime.of(12, 0) -> 2), """{"10:00":1,"12:00":2}""") &&
        roundTrip(Map(Month.of(10) -> 1, Month.of(12) -> 2), """{"10":1,"12":2}""") &&
        roundTrip(Map(MonthDay.of(10, 1) -> 1, MonthDay.of(12, 2) -> 2), """{"--10-01":1,"--12-02":2}""") &&
        roundTrip(
          Map(
            OffsetDateTime.of(LocalDateTime.of(2025, 1, 1, 10, 0), ZoneOffset.ofHours(1))  -> 1,
            OffsetDateTime.of(LocalDateTime.of(2025, 1, 2, 12, 0), ZoneOffset.ofHours(-2)) -> 2
          ),
          """{"2025-01-01T10:00+01:00":1,"2025-01-02T12:00-02:00":2}"""
        ) &&
        roundTrip(
          Map(
            OffsetTime.of(LocalTime.of(10, 0), ZoneOffset.ofHours(1))  -> 1,
            OffsetTime.of(LocalTime.of(12, 0), ZoneOffset.ofHours(-2)) -> 2
          ),
          """{"10:00+01:00":1,"12:00-02:00":2}"""
        ) &&
        roundTrip(Map(Period.of(0, 1, 2) -> 1, Period.of(3, 4, 5) -> 2), """{"P1M2D":1,"P3Y4M5D":2}""") &&
        roundTrip(Map(Year.of(2025) -> 1, Year.of(2026) -> 2), """{"2025":1,"2026":2}""") &&
        roundTrip(Map(YearMonth.of(2025, 1) -> 1, YearMonth.of(2026, 2) -> 2), """{"2025-01":1,"2026-02":2}""") &&
        roundTrip(Map(ZoneId.of("UTC") -> 1, ZoneId.of("GMT+1") -> 2), """{"UTC":1,"GMT+01:00":2}""") &&
        roundTrip(
          Map[ZoneOffset, Int](ZoneOffset.of("+01:00") -> 1, ZoneOffset.of("-02:00") -> 2),
          """{"+01:00":1,"-02:00":2}"""
        ) &&
        roundTrip(
          Map(
            ZonedDateTime.of(LocalDateTime.of(2025, 1, 1, 10, 0), ZoneId.of("Europe/Berlin")) -> 1,
            ZonedDateTime.of(LocalDateTime.of(2025, 1, 2, 12, 0), ZoneId.of("Europe/Riga"))   -> 2
          ),
          """{"2025-01-01T10:00+01:00[Europe/Berlin]":1,"2025-01-02T12:00+02:00[Europe/Riga]":2}"""
        ) &&
        roundTrip(Map(Currency.getInstance("USD") -> 1, Currency.getInstance("PLN") -> 2), """{"USD":1,"PLN":2}""") &&
        roundTrip(
          Map(new UUID(1L, 1L) -> 1, new UUID(2L, 2L) -> 2),
          """{"00000000-0000-0001-0000-000000000001":1,"00000000-0000-0002-0000-000000000002":2}"""
        )
      },
      test("primitive key map (decode error)") {
        val codec = Schema[Map[Int, Long]].derive(JsonFormat.deriver)
        decodeError("", codec, "unexpected end of input at: .") &&
        decodeError("""{"1"""", codec, "unexpected end of input at: .at(0)") &&
        decodeError("""{"1":""", codec, "unexpected end of input at: .atKey(<key>)") &&
        decodeError("""{"1":2]""", codec, "expected '}' or ',' at: .")
      },
      test("unit key map (decode error)") {
        val codec = Schema[Map[Unit, Long]].derive(JsonFormat.deriver)
        encodeError(Map(() -> 1L), codec, "encoding as a JSON key is not supported") &&
        decodeError("""{"null":1}""", codec, "decoding as a JSON key is not supported at: .at(0)")
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
      test("case object enumeration") {
        roundTrip[TrafficLight](TrafficLight.Green, """"Green"""") &&
        roundTrip[TrafficLight](TrafficLight.Yellow, """"Yellow"""") &&
        roundTrip[TrafficLight](TrafficLight.Red, """"Red"""") &&
        roundTrip[Color](Color.Green, """"Green"""") &&
        roundTrip[Color](Color.Yellow, """"Yellow"""") &&
        roundTrip[Color](Color.Red, """"Red"""")
      },
      test("case object enumeration (decode error)") {
        val codec1 = Schema[TrafficLight].derive(JsonFormat.deriver)
        val codec2 = Schema[Color].derive(JsonFormat.deriver)
        decodeError("""null""", codec1, "expected '\"' at: .") &&
        decodeError(""""Black"""", codec1, "illegal enum value \"Black\" at: .") &&
        decodeError("""null""", codec2, "expected '\"' at: .") &&
        decodeError(""""Pink"""", codec2, "illegal enum value \"Pink\" at: .")
      },
      test("ADT") {
        roundTrip[RGBColor](RGBColor.Green, """{"Green":{}}""") &&
        roundTrip[RGBColor](RGBColor.Yellow, """{"Yellow":{}}""") &&
        roundTrip[RGBColor](RGBColor.Red, """{"Red":{}}""") &&
        roundTrip[RGBColor](new RGBColor.Mix(0x123456), """{"Mix":{"rgb":1193046}}""")
      },
      test("ADT (decode error)") {
        val codec1 = Schema[RGBColor].derive(JsonFormat.deriver)
        decodeError("""nuts""", codec1, "expected '{' at: .") &&
        decodeError("""{"Pink":{}}""", codec1, "illegal discriminator at: .") &&
        decodeError("""{"Mix":{"rgb":1]}""", codec1, "expected '}' or ',' at: .when[Mix]") &&
        decodeError("""{"Mix":{"rgb":01}}""", codec1, "illegal number with leading zero at: .when[Mix].rgb") &&
        decodeError("""{"Mix":{"rgb":1193046}]""", codec1, "expected '}' or ',' at: .") &&
        decodeError("""{"Mix":{"color":1193046}}""", codec1, "missing required field \"rgb\" at: .when[Mix]")
      },
      test("option") {
        roundTrip(Option(42), """42""") &&
        roundTrip[Option[Int]](None, """null""")
      },
      test("option (decode error)") {
        val codec = Schema[Option[Int]].derive(JsonFormat.deriver)
        decodeError("""08""", codec, "illegal number with leading zero at: .when[Some].value") &&
        decodeError("""nuts""", codec, "expected null at: .when[None]")
      },
      test("either") {
        roundTrip[Either[String, Int]](Right(42), """{"Right":{"value":42}}""") &&
        roundTrip[Either[String, Int]](Left("VVV"), """{"Left":{"value":"VVV"}}""")
      },
      test("either (decode error)") {
        val codec = Schema[Either[String, Int]].derive(JsonFormat.deriver)
        decodeError("""nuts""", codec, "expected '{' at: .") &&
        decodeError("""{"Middle":{"value":42}}""", codec, "illegal discriminator at: .") &&
        decodeError("""{"Right":{"value":42]}""", codec, "expected '}' or ',' at: .when[Right]") &&
        decodeError("""{"Right":{"value":42}]""", codec, "expected '}' or ',' at: .") &&
        decodeError("""{"Right":{"value":02}}""", codec, "illegal number with leading zero at: .when[Right].value") &&
        decodeError("""{"Left":{"left":"VVV"}}""", codec, "missing required field \"value\" at: .when[Left]")
      }
    ),
    suite("wrapper")(
      test("top-level") {
        roundTrip[UserId](UserId(1234567890123456789L), "1234567890123456789") &&
        roundTrip[Email](Email("john@gmail.com"), "\"john@gmail.com\"")
      },
      test("top-level (decode error)") {
        val codec = Schema[Email].derive(JsonFormat.deriver)
        decodeError("john@gmail.com", codec, "expected '\"' at: .wrapped") &&
        decodeError("\"john&gmail.com\"", codec, "expected e-mail at: .") &&
        decodeError("\"john@gmail.com", codec, "unexpected end of input at: .wrapped")
      },
      test("as a record field") {
        roundTrip[Record3](
          Record3(UserId(1234567890123456789L), Email("backup@gmail.com"), Currency.getInstance("USD")),
          """{"userId":1234567890123456789,"email":"backup@gmail.com","currency":"USD"}"""
        )
      },
      test("as a map key") {
        roundTrip(
          Map(UserId(1234567890123456789L) -> Email("backup@gmail.com")),
          """{"1234567890123456789":"backup@gmail.com"}"""
        )
      },
      test("as a map key (decode error)") {
        val codec = Schema[Map[Email, UserId]].derive(JsonFormat.deriver)
        decodeError("""{john@gmail.com:123}""", codec, "expected '\"' at: .at(0).wrapped") &&
        decodeError("""{"backup&gmail.com":123}""", codec, "expected e-mail at: .at(0)") &&
        decodeError("""{"backup@gmail.com":123""", codec, "unexpected end of input at: .")
      }
    ),
    suite("dynamic value")(
      test("top-level (decode error)") {
        val codec = Schema[DynamicValue].derive(JsonFormat.deriver)
        encodeError(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          codec,
          "encoding as a JSON value is not supported"
        ) &&
        decodeError("1", codec, "decoding as a JSON value is not supported at: .")
      }
    )
  ) @@ jvmOnly

  val millionZeros: String = zeros(1000000)

  private[this] def zeros(n: Int): String = fill('0', n)

  private[this] def fill(ch: Char, n: Int): String = {
    val cs = new Array[Char](n)
    _root_.java.util.Arrays.fill(cs, ch)
    new String(cs)
  }

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

    val bl: Lens[Record1, Boolean] = $(_.bl)
    val b: Lens[Record1, Byte]     = $(_.b)
    val sh: Lens[Record1, Short]   = $(_.sh)
    val i: Lens[Record1, Int]      = $(_.i)
    val l: Lens[Record1, Long]     = $(_.l)
    val f: Lens[Record1, Float]    = $(_.f)
    val d: Lens[Record1, Double]   = $(_.d)
    val c: Lens[Record1, Char]     = $(_.c)
    val s: Lens[Record1, String]   = $(_.s)
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

  case class Record3(userId: UserId, email: Email, currency: Currency)

  object Record3 {
    implicit val schema: Schema[Record3] = Schema.derived
  }

  case class Record4(hіdden: Unit, optKеy: Option[String]) // using non-ASCII chars for field names intentionally

  object Record4 extends CompanionOptics[Record4] {
    implicit val schema: Schema[Record4] = Schema.derived

    val hidden: Lens[Record4, Unit]           = $(_.hіdden)
    val optKey: Lens[Record4, Option[String]] = $(_.optKеy)
  }

  case class Dynamic(primitive: DynamicValue, map: DynamicValue)

  object Dynamic extends CompanionOptics[Dynamic] {
    implicit val schema: Schema[Dynamic] = Schema.derived

    val primitive: Lens[Dynamic, DynamicValue] = $(_.primitive)
    val map: Lens[Dynamic, DynamicValue]       = $(_.map)
  }

  sealed trait Color

  object Color {
    implicit val schema: Schema[Color] = Schema.derived

    case object Red extends Color

    case object Orange extends Color

    case object Yellow extends Color

    case object Green extends Color

    case object Turquoise extends Color

    case object Blue extends Color

    case object Violet extends Color

    case object White extends Color

    case object Gray extends Color

    case object Black extends Color
  }

  sealed abstract class RGBColor(val color: Int)

  object RGBColor {
    implicit val schema: Schema[RGBColor] = Schema.derived

    case object Red extends RGBColor(0xff0000)

    case object Orange extends RGBColor(0x7fff00)

    case object Yellow extends RGBColor(0xffff00)

    case object Green extends RGBColor(0x00ff00)

    case object Turquoise extends RGBColor(0x007fff)

    case object Blue extends RGBColor(0x0000ff)

    case object Violet extends RGBColor(0xff00ff)

    case object White extends RGBColor(0xffffff)

    case object Gray extends RGBColor(0x7f7f7f)

    case object Black extends RGBColor(0x000000)

    case class Mix(rgb: Int) extends RGBColor(rgb)
  }
}
