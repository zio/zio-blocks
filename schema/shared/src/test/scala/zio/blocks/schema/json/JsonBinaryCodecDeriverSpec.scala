package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema.json.JsonTestUtils._
import zio.blocks.schema._
import zio.blocks.schema.JavaTimeGen._

import zio.blocks.schema.json.NameMapper._
import zio.blocks.typeid.TypeId
import zio.test._
import zio.test.Assertion._
import java.math.MathContext
import java.nio.charset.StandardCharsets
import java.time._
import java.util.{Currency, UUID}
import scala.collection.immutable.ArraySeq

object JsonBinaryCodecDeriverSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonBinaryCodecDeriverSpec")(
    suite("primitives")(
      test("Unit") {
        roundTrip((), "{}") &&
        decodeError[Unit]("", "unexpected end of input at: .") &&
        decodeError[Unit]("{} ,", "expected end of input at: .") &&
        decodeError[Unit]("null", "expected an empty JSON object at: .") &&
        decodeError[Unit]("""{"extra":null}""", "expected an empty JSON object at: .")
      },
      test("Boolean") {
        decode(" true", true) &&
        roundTrip(true, "true") &&
        roundTrip(false, "false") &&
        decodeError[Boolean]("", "unexpected end of input at: .") &&
        decodeError[Boolean]("false,", "expected end of input at: .") &&
        decodeError[Boolean]("falsy", "illegal boolean at: .") &&
        decodeError[Boolean]("tralse", "illegal boolean at: .")
      },
      test("Byte") {
        check(Gen.byte)(x => roundTrip(x, x.toString)) &&
        roundTrip(1: Byte, "1") &&
        roundTrip(10: Byte, "10") &&
        roundTrip(Byte.MinValue, "-128") &&
        roundTrip(Byte.MaxValue, "127") &&
        decode("-0", 0: Byte) &&
        decodeError[Byte]("-129", "value is too large for byte at: .") &&
        decodeError[Byte]("128", "value is too large for byte at: .") &&
        decodeError[Byte]("01", "illegal number with leading zero at: .") &&
        decodeError[Byte]("-01", "illegal number with leading zero at: .") &&
        decodeError[Byte]("1.0", "illegal number at: .") &&
        decodeError[Byte]("1e1", "illegal number at: .") &&
        decodeError[Byte]("1E1", "illegal number at: .") &&
        decodeError[Byte]("null", "illegal number at: .") &&
        decodeError[Byte]("", "unexpected end of input at: .") &&
        decodeError[Byte]("1,", "expected end of input at: .")
      },
      test("Short") {
        check(Gen.short)(x => roundTrip(x, x.toString)) &&
        roundTrip(1: Short, "1") &&
        roundTrip(10: Short, "10") &&
        roundTrip(100: Short, "100") &&
        roundTrip(1000: Short, "1000") &&
        roundTrip(Short.MinValue, "-32768") &&
        roundTrip(Short.MaxValue, "32767") &&
        decode("-0", 0: Short) &&
        decodeError[Short]("-32769", "value is too large for short at: .") &&
        decodeError[Short]("32768", "value is too large for short at: .") &&
        decodeError[Short]("01", "illegal number with leading zero at: .") &&
        decodeError[Short]("-01", "illegal number with leading zero at: .") &&
        decodeError[Short]("1.0", "illegal number at: .") &&
        decodeError[Short]("1e1", "illegal number at: .") &&
        decodeError[Short]("1E1", "illegal number at: .") &&
        decodeError[Short]("null", "illegal number at: .") &&
        decodeError[Short]("", "unexpected end of input at: .") &&
        decodeError[Short]("1   ,", "expected end of input at: .")
      },
      test("Int") {
        check(Gen.int)(x => roundTrip(x, x.toString)) &&
        roundTrip(Int.MinValue, "-2147483648") &&
        roundTrip(Int.MaxValue, "2147483647") &&
        decode("-0", 0) &&
        decodeError[Int]("-2147483649", "value is too large for int at: .") &&
        decodeError[Int]("2147483648", "value is too large for int at: .") &&
        decodeError[Int]("01", "illegal number with leading zero at: .") &&
        decodeError[Int]("-01", "illegal number with leading zero at: .") &&
        decodeError[Int]("1.0", "illegal number at: .") &&
        decodeError[Int]("1E1", "illegal number at: .") &&
        decodeError[Int]("1e1", "illegal number at: .") &&
        decodeError[Int]("null", "illegal number at: .") &&
        decodeError[Int]("", "unexpected end of input at: .") &&
        decodeError[Int]("1,", "expected end of input at: .")
      },
      test("Long") {
        check(Gen.long)(x => roundTrip(x, x.toString)) &&
        roundTrip(Long.MinValue, "-9223372036854775808") &&
        roundTrip(Long.MaxValue, "9223372036854775807") &&
        decode("-0", 0L) &&
        decodeError[Long]("-92233720368547758091234567", "value is too large for long at: .") &&
        decodeError[Long]("-9223372036854775809", "value is too large for long at: .") &&
        decodeError[Long]("9223372036854775808", "value is too large for long at: .") &&
        decodeError[Long]("01", "illegal number with leading zero at: .") &&
        decodeError[Long]("-01", "illegal number with leading zero at: .") &&
        decodeError[Long]("1.0", "illegal number at: .") &&
        decodeError[Long]("1e1", "illegal number at: .") &&
        decodeError[Long]("1E1", "illegal number at: .") &&
        decodeError[Long]("null", "illegal number at: .") &&
        decodeError[Long]("", "unexpected end of input at: .") &&
        decodeError[Long]("1,", "expected end of input at: .")
      },
      test("Float") {
        check(Gen.float)(x => decode(x.toString, x)) &&
        roundTrip(Float.MinValue, "-3.4028235E38") &&
        roundTrip(Float.MaxValue, "3.4028235E38") &&
        roundTrip(0.0f, "0.0") &&
        roundTrip(-0.0f, "-0.0") &&
        roundTrip(1.0e17f, "1.0E17") &&
        roundTrip(0.33007812f, "0.33007812") &&
        roundTrip(102067.11f, "102067.11") &&
        roundTrip(1.6777216e7f, "1.6777216E7") &&
        roundTrip(1.0e-45f, "1.0E-45") &&
        roundTrip(1.0e-44f, "1.0E-44") &&
        roundTrip(6.895867e-31f, "6.895867E-31") &&
        roundTrip(1.595711e-5f, "1.595711E-5") &&
        roundTrip(-1.5887592e7f, "-1.5887592E7") &&
        roundTrip(1.2621775e-29f, "1.2621775E-29") &&
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
        decode("-12345678901234567890e-12345678901234567890", -0.0f) &&
        encodeError(Float.PositiveInfinity, "illegal number: Infinity") &&
        encodeError(Float.NegativeInfinity, "illegal number: -Infinity") &&
        decodeError[Float]("null", "illegal number at: .") &&
        decodeError[Float]("1. ", "illegal number at: .") &&
        decodeError[Float]("1e+e", "illegal number at: .") &&
        decodeError[Float]("01", "illegal number with leading zero at: .") &&
        decodeError[Float]("-01", "illegal number with leading zero at: .") &&
        decodeError[Float]("", "unexpected end of input at: .") &&
        decodeError[Float]("1,", "expected end of input at: .")
      },
      test("Double") {
        check(Gen.double)(x => decode(x.toString, x)) &&
        roundTrip(Double.MinValue, "-1.7976931348623157E308") &&
        roundTrip(Double.MaxValue, "1.7976931348623157E308") &&
        roundTrip(0.0, "0.0") &&
        roundTrip(-0.0, "-0.0") &&
        roundTrip(0.001, "0.001") &&
        roundTrip(1.0e7, "1.0E7") &&
        roundTrip(8572.431613041595, "8572.431613041595") &&
        roundTrip(5.0e-324, "5.0E-324") &&
        roundTrip(8.707795712926552e15, "8.707795712926552E15") &&
        roundTrip(5.960464477539063e-8, "5.960464477539063E-8") &&
        roundTrip(-1.3821488797638562e14, "-1.3821488797638562E14") &&
        roundTrip(9.223372036854776e18, "9.223372036854776E18") &&
        roundTrip(2.2250738585072014e-308, "2.2250738585072014E-308") &&
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
        decode("15.0e-334", 0.0) &&
        encodeError(Double.PositiveInfinity, "illegal number: Infinity") &&
        encodeError(Double.NegativeInfinity, "illegal number: -Infinity") &&
        decodeError[Double]("null", "illegal number at: .") &&
        decodeError[Double]("1. ", "illegal number at: .") &&
        decodeError[Double]("1e+e", "illegal number at: .") &&
        decodeError[Double]("01", "illegal number with leading zero at: .") &&
        decodeError[Double]("-01", "illegal number with leading zero at: .") &&
        decodeError[Double]("", "unexpected end of input at: .") &&
        decodeError[Double]("1,", "expected end of input at: .")
      },
      test("Char") {
        check(Gen.char.filter(x => x >= ' ' && (x < 0xd800 || x > 0xdfff))) { // excluding control and surrogate chars
          x => roundTrip(x, s""""${x.toString}"""")
        } &&
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
        encode(1.toChar, "\"\\u0001\"") &&
        encode(127.toChar, "\"\\u007f\"") &&
        encode('★', "\"\\u2605\"", WriterConfig.withEscapeUnicode(true)) &&
        decode(""""\/"""", '/') &&
        decode("\"\\u0037\"", '7') &&
        decodeError[Char]("\"WWW\"", "expected '\"' at: .") &&
        decodeError[Char]("\"\"", "illegal character at: .") &&
        decodeError[Char](""""\x"""", "illegal escape sequence at: .") &&
        decodeError[Char](""""\x0008"""", "illegal escape sequence at: .") &&
        decodeError[Char]("\"\u001f\"", "unescaped control character at: .") &&
        decodeError[Char]("", "unexpected end of input at: .") &&
        decodeError[Char]("\"W\",", "expected end of input at: .") &&
        decodeError[Char]("\"\\uZ000\"", "expected hex digit at: .") &&
        decodeError[Char]("\"\\u0Z00\"", "expected hex digit at: .") &&
        decodeError[Char]("\"\\u00Z0\"", "expected hex digit at: .") &&
        decodeError[Char]("\"\\u000Z\"", "expected hex digit at: .") &&
        decodeError[Char]("\"\\u×000\"", "expected hex digit at: .") &&
        decodeError[Char]("\"\\u0×00\"", "expected hex digit at: .") &&
        decodeError[Char]("\"\\u00×0\"", "expected hex digit at: .") &&
        decodeError[Char]("\"\\u000×\"", "expected hex digit at: .") &&
        decodeError[Char]("\"\\u000\"", "expected hex digit at: .") &&
        decodeError[Char]("\"\\u00", "unexpected end of input at: .") &&
        decodeError[Char]("\"\\u0", "unexpected end of input at: .") &&
        decodeError[Char]("\"\\", "unexpected end of input at: .") &&
        encodeError[Char](0xdd1e.toChar, "illegal char sequence of surrogate pair") &&
        encodeError[Char](
          0xdd1e.toChar,
          "illegal char sequence of surrogate pair",
          WriterConfig.withEscapeUnicode(true)
        ) &&
        decodeError[Char]("\"\\udd1e\"", "illegal surrogate character at: .") &&
        decodeError[Char]("\"\\ud834\"", "illegal surrogate character at: .") &&
        decodeError[Char](Array[Byte](0x22.toByte, 0x80.toByte), "malformed byte(s): 0x80 at: .") &&
        decodeError[Char](Array[Byte](0x22.toByte, 0xc0.toByte, 0x80.toByte), "malformed byte(s): 0xc0, 0x80 at: .") &&
        decodeError[Char](Array[Byte](0x22.toByte, 0xc8.toByte, 0x08.toByte), "malformed byte(s): 0xc8, 0x08 at: .") &&
        decodeError[Char](Array[Byte](0x22.toByte, 0xc8.toByte, 0xff.toByte), "malformed byte(s): 0xc8, 0xff at: .") &&
        decodeError[Char](
          Array[Byte](0x22.toByte, 0xe0.toByte, 0x80.toByte, 0x80.toByte),
          "malformed byte(s): 0xe0, 0x80, 0x80 at: ."
        ) &&
        decodeError[Char](
          Array[Byte](0x22.toByte, 0xe0.toByte, 0xff.toByte, 0x80.toByte),
          "malformed byte(s): 0xe0, 0xff, 0x80 at: ."
        ) &&
        decodeError[Char](
          Array[Byte](0x22.toByte, 0xe8.toByte, 0x88.toByte, 0x08.toByte),
          "malformed byte(s): 0xe8, 0x88, 0x08 at: ."
        ) &&
        decodeError[Char](
          Array[Byte](0x22.toByte, 0xf0.toByte, 0x80.toByte, 0x80.toByte, 0x80.toByte),
          "illegal surrogate character at: ."
        )
      },
      test("String") {
        check(
          Gen
            .listOfBounded(0, 5)( // excluding control, surrogate and must be escaped chars
              Gen.char.filter(x => x >= ' ' && x != '"' && x != '\\' && (x < 0xd800 || x > 0xdfff))
            )
            .map(_.mkString)
        )(x => roundTrip(x, s""""$x"""")) &&
        roundTrip("Hello", "\"Hello\"") &&
        roundTrip("Привіт", "\"Привіт\"") &&
        roundTrip("★🎸🎧⋆｡°⋆", "\"★🎸🎧⋆｡°⋆\"") &&
        roundTrip(
          "\u007f\n\u0001\u0002\u0002\u0003\u0004\u0005\u0006\u0007\u0008 ",
          "\"\\u007f\\n\\u0001\\u0002\\u0002\\u0003\\u0004\\u0005\\u0006\\u0007\\b \""
        ) &&
        roundTrip("\\\b\f\n\r\t\"", """"\\\b\f\n\r\t\""""") &&
        roundTrip(
          "Привіт ",
          "\"\\u041f\\u0440\\u0438\\u0432\\u0456\\u0442 \"",
          ReaderConfig,
          WriterConfig.withEscapeUnicode(true)
        ) &&
        roundTrip(
          "★🎸🎧⋆｡°⋆ ",
          "\"\\u2605\\ud83c\\udfb8\\ud83c\\udfa7\\u22c6\\uff61\\u00b0\\u22c6 \"",
          ReaderConfig,
          WriterConfig.withEscapeUnicode(true)
        ) &&
        roundTrip(
          "\u007f\n\u0001\u0002\u0002\u0003\u0004\u0005\u0006\u0007\u0008 ",
          "\"\\u007f\\n\\u0001\\u0002\\u0002\\u0003\\u0004\\u0005\\u0006\\u0007\\b \"",
          ReaderConfig,
          WriterConfig.withEscapeUnicode(true)
        ) &&
        decode(""""\/"""", "/") &&
        decodeError[String]("", "unexpected end of input at: .") &&
        decodeError[String]("\"abc", "unexpected end of input at: .") &&
        decodeError[String]("\"abc\",", "expected end of input at: .") &&
        decodeError[String]("\"\u001f\"", "unescaped control character at: .") &&
        decodeError[String]("\"\\ud834\\ud834\"", "illegal surrogate character pair at: .") &&
        decodeError[String]("\"\\x0008\"", "illegal escape sequence at: .") &&
        decodeError[String]("\"\\uZ000\"", "expected hex digit at: .") &&
        decodeError[String]("\"\\u0Z00\"", "expected hex digit at: .") &&
        decodeError[String]("\"\\u00Z0\"", "expected hex digit at: .") &&
        decodeError[String]("\"\\u000Z\"", "expected hex digit at: .") &&
        decodeError[String]("\"\\u×000\"", "expected hex digit at: .") &&
        decodeError[String]("\"\\u0×00\"", "expected hex digit at: .") &&
        decodeError[String]("\"\\u00×0\"", "expected hex digit at: .") &&
        decodeError[String]("\"\\u000×\"", "expected hex digit at: .") &&
        decodeError[String]("\"\\u000\"", "expected hex digit at: .") &&
        decodeError[String]("\"\\u00", "unexpected end of input at: .") &&
        decodeError[String]("\"\\u0", "unexpected end of input at: .") &&
        decodeError[String]("\"\\", "unexpected end of input at: .") &&
        decodeError[String]("\"\\udd1e\"", "unexpected end of input at: .") &&
        decodeError[String]("\"\\ud834\"", "unexpected end of input at: .") &&
        decodeError[String]("\"\\ud834\\\"", "unexpected end of input at: .") &&
        decodeError[String]("\"\\ud834\\x\"", "unexpected end of input at: .") &&
        decodeError[String]("\"\\ud834d83423\"", "illegal escape sequence at: .") &&
        decodeError[String]("\"\\ud834\\d834\"", "illegal escape sequence at: .") &&
        decodeError[String]("\"\\udf45\\udf45\"", "illegal surrogate character pair at: .") &&
        decodeError[String]("\"\\ud834\\ud834\"", "illegal surrogate character pair at: .") &&
        encodeError[String](0xdd1e.toChar.toString, "illegal char sequence of surrogate pair") &&
        encodeError[String](
          0xdd1e.toChar.toString,
          "illegal char sequence of surrogate pair",
          WriterConfig.withEscapeUnicode(true)
        ) &&
        decodeError[String](Array[Byte](0x22.toByte, 0x80.toByte), "malformed byte(s): 0x80 at: .") &&
        decodeError[String](
          Array[Byte](0x22.toByte, 0xc0.toByte, 0x80.toByte),
          "malformed byte(s): 0xc0, 0x80 at: ."
        ) &&
        decodeError[String](
          Array[Byte](0x22.toByte, 0xc8.toByte, 0x08.toByte),
          "malformed byte(s): 0xc8, 0x08 at: ."
        ) &&
        decodeError[String](
          Array[Byte](0x22.toByte, 0xc8.toByte, 0xff.toByte),
          "malformed byte(s): 0xc8, 0xff at: ."
        ) &&
        decodeError[String](
          Array[Byte](0x22.toByte, 0xe0.toByte, 0x80.toByte, 0x80.toByte),
          "malformed byte(s): 0xe0, 0x80, 0x80 at: ."
        ) &&
        decodeError[String](
          Array[Byte](0x22.toByte, 0xe0.toByte, 0xff.toByte, 0x80.toByte),
          "malformed byte(s): 0xe0, 0xff, 0x80 at: ."
        ) &&
        decodeError[String](
          Array[Byte](0x22.toByte, 0xe8.toByte, 0x88.toByte, 0x08.toByte),
          "malformed byte(s): 0xe8, 0x88, 0x08 at: ."
        ) &&
        decodeError[String](
          Array[Byte](0x22.toByte, 0xf0.toByte, 0x80.toByte, 0x80.toByte, 0x80.toByte),
          "malformed byte(s): 0xf0, 0x80, 0x80, 0x80 at: ."
        ) &&
        decodeError[String](
          Array[Byte](0x22.toByte, 0xf0.toByte, 0x9d.toByte, 0x04.toByte, 0x9e.toByte),
          "malformed byte(s): 0xf0, 0x9d, 0x04, 0x9e at: ."
        ) &&
        decodeError[String](
          Array[Byte](0x22.toByte, 0xf0.toByte, 0x9d.toByte, 0x84.toByte, 0xff.toByte),
          "malformed byte(s): 0xf0, 0x9d, 0x84, 0xff at: ."
        ) &&
        decodeError[String](
          Array[Byte](0x22.toByte, 0xf0.toByte, 0x9d.toByte, 0xff.toByte, 0x9e.toByte),
          "malformed byte(s): 0xf0, 0x9d, 0xff, 0x9e at: ."
        ) &&
        decodeError[String](
          Array[Byte](0x22.toByte, 0xf0.toByte, 0xff.toByte, 0x84.toByte, 0x9e.toByte),
          "malformed byte(s): 0xf0, 0xff, 0x84, 0x9e at: ."
        ) &&
        decodeError[String](
          Array[Byte](0x22.toByte, 0xf0.toByte, 0x9d.toByte, 0x84.toByte, 0x0e.toByte),
          "malformed byte(s): 0xf0, 0x9d, 0x84, 0x0e at: ."
        )
      },
      test("BigInt") {
        check(Gen.bigInt(BigInt("-" + "9" * 20), BigInt("9" * 20)))(x => roundTrip(x, x.toString)) &&
        roundTrip(BigInt(0), "0") &&
        roundTrip(BigInt("-" + "9" * 3), "-" + "9" * 3) &&
        roundTrip(BigInt("9" * 30), "9" * 30) &&
        roundTrip(BigInt("9" * 300), "9" * 300) &&
        decode("-0", BigInt(0)) &&
        encode(BigInt("9" * 1000), "9" * 1000) &&
        decodeError[BigInt]("", "unexpected end of input at: .") &&
        decodeError[BigInt]("01", "illegal number with leading zero at: .") &&
        decodeError[BigInt]("-a", "illegal number at: .") &&
        decodeError[BigInt]("1.0", "illegal number at: .") &&
        decodeError[BigInt]("1e1", "illegal number at: .") &&
        decodeError[BigInt]("1E1", "illegal number at: .") &&
        decodeError[BigInt]("null", "illegal number at: .") &&
        decodeError[BigInt]("1" * 308, "value exceeds limit for number of digits at: .")
      },
      test("BigDecimal") {
        check(Gen.bigDecimal(BigDecimal("-" + "9" * 20), BigDecimal("9" * 20)))(x => roundTrip(x, x.toString)) &&
        roundTrip(BigDecimal("0.0"), "0.0") &&
        roundTrip(BigDecimal("126.09999999999999001"), "126.09999999999999001") &&
        roundTrip(BigDecimal("0.0287500000000000000000"), "0.0287500000000000000000") &&
        roundTrip(BigDecimal("-1." + "1" * 3 + "E+1234"), "-1." + "1" * 3 + "E+1234") &&
        decode("-0.0", BigDecimal("0.0")) &&
        decode("1." + "1" * 300 + "E+1234", BigDecimal("1.111111111111111111111111111111111E+1234")) &&
        encode(BigDecimal("1." + "1" * 30 + "E+123456789"), "1." + "1" * 30 + "E+123456789") &&
        encode(BigDecimal("1." + "1" * 1000 + "E+1234"), "1." + "1" * 1000 + "E+1234") &&
        decodeError[BigDecimal]("", "unexpected end of input at: .") &&
        decodeError[BigDecimal]("1,", "expected end of input at: .") &&
        decodeError[BigDecimal]("1. ", "illegal number at: .") &&
        decodeError[BigDecimal]("1e+e", "illegal number at: .") &&
        decodeError[BigDecimal]("--8", "illegal number at: .") &&
        decodeError[BigDecimal]("null", "illegal number at: .") &&
        decodeError[BigDecimal]("1e2147483648", "illegal number at: .") &&
        decodeError[BigDecimal]("1e11111111111", "illegal number at: .") &&
        decodeError[BigDecimal]("1" * 308, "value exceeds limit for number of digits at: .") &&
        decodeError[BigDecimal]("0." + "1" * 308, "value exceeds limit for number of digits at: .") &&
        decodeError[BigDecimal]("1" * 154 + "." + "1" * 154, "value exceeds limit for number of digits at: .") &&
        decodeError[BigDecimal]("1" * 300 + "e10000", "value exceeds limit for scale at: .") &&
        decodeError[BigDecimal]("01", "illegal number with leading zero at: .")
      },
      test("DayOfWeek") {
        check(genDayOfWeek)(x => roundTrip(x, s""""$x"""")) &&
        decodeError[DayOfWeek]("", "unexpected end of input at: .") &&
        decodeError[DayOfWeek](""""Mon"""", "illegal day of week value at: .")
      },
      test("Duration") {
        check(genDuration)(x => roundTrip(x, s""""$x"""")) &&
        roundTrip(Duration.ofSeconds(0), """"PT0S"""") &&
        decodeError[Duration]("""null""", "expected '\"' at: .") &&
        decodeError[Duration](""""""", "unexpected end of input at: .") &&
        decodeError[Duration]("""""""", "expected 'P' or '-' at: .") &&
        decodeError[Duration](""""-"""", "expected 'P' at: .") &&
        decodeError[Duration](""""PXD"""", "expected '-' or digit at: .") &&
        decodeError[Duration](""""PT0SX""", """expected '"' at: .""") &&
        decodeError[Duration](""""P-XD"""", "expected digit at: .") &&
        decodeError[Duration](""""P1XD"""", "expected 'D' or digit at: .") &&
        decodeError[Duration](""""P106751991167301D"""", "illegal duration at: .") &&
        decodeError[Duration](""""P1067519911673000D"""", "illegal duration at: .") &&
        decodeError[Duration](""""P-106751991167301D"""", "illegal duration at: .") &&
        decodeError[Duration](""""P1DX1H"""", """expected 'T' or '"' at: .""") &&
        decodeError[Duration](""""P1DTXH"""", "expected '-' or digit at: .") &&
        decodeError[Duration](""""P1DT-XH"""", "expected digit at: .") &&
        decodeError[Duration](""""P1DT1XH"""", "expected 'H' or 'M' or 'S' or '.' or digit at: .") &&
        decodeError[Duration](""""P0DT2562047788015216H"""", "illegal duration at: .") &&
        decodeError[Duration](""""P0DT-2562047788015216H"""", "illegal duration at: .") &&
        decodeError[Duration](""""P0DT153722867280912931M"""", "illegal duration at: .") &&
        decodeError[Duration](""""P0DT-153722867280912931M"""", "illegal duration at: .") &&
        decodeError[Duration](""""P0DT9223372036854775808S"""", "illegal duration at: .") &&
        decodeError[Duration](""""P0DT92233720368547758000S"""", "illegal duration at: .") &&
        decodeError[Duration](""""P0DT-9223372036854775809S"""", "illegal duration at: .") &&
        decodeError[Duration](""""P1DT1HXM"""", """expected '"' or '-' or digit at: .""") &&
        decodeError[Duration](""""P1DT1H-XM"""", "expected digit at: .") &&
        decodeError[Duration](""""P1DT1H1XM"""", "expected 'M' or 'S' or '.' or digit at: .") &&
        decodeError[Duration](""""P0DT0H153722867280912931M"""", "illegal duration at: .") &&
        decodeError[Duration](""""P0DT0H-153722867280912931M"""", "illegal duration at: .") &&
        decodeError[Duration](""""P0DT0H9223372036854775808S"""", "illegal duration at: .") &&
        decodeError[Duration](""""P0DT0H92233720368547758000S"""", "illegal duration at: .") &&
        decodeError[Duration](""""P0DT0H-9223372036854775809S"""", "illegal duration at: .") &&
        decodeError[Duration](""""P1DT1H1MXS"""", """expected '"' or '-' or digit at: .""") &&
        decodeError[Duration](""""P1DT1H1M-XS"""", "expected digit at: .") &&
        decodeError[Duration](""""P1DT1H1M0XS"""", "expected 'S' or '.' or digit at: .") &&
        decodeError[Duration](""""P1DT1H1M0.XS"""", "expected 'S' or digit at: .") &&
        decodeError[Duration](""""P1DT1H1M0.012345678XS"""", "expected 'S' at: .") &&
        decodeError[Duration](""""P1DT1H1M0.0123456789S"""", "expected 'S' at: .") &&
        decodeError[Duration](""""P0DT0H0M9223372036854775808S"""", "illegal duration at: .") &&
        decodeError[Duration](""""P0DT0H0M92233720368547758080S"""", "illegal duration at: .") &&
        decodeError[Duration](""""P0DT0H0M-9223372036854775809S"""", "illegal duration at: .") &&
        decodeError[Duration](""""P106751991167300DT24H"""", "illegal duration at: .") &&
        decodeError[Duration](""""P0DT2562047788015215H60M"""", "illegal duration at: .") &&
        decodeError[Duration](""""P0DT0H153722867280912930M60S"""", "illegal duration at: .")
      },
      test("Instant") {
        check(genInstant)(x => roundTrip(x, s""""$x"""")) &&
        roundTrip(Instant.parse("+626426443-02-28T07:54:31Z"), """"+626426443-02-28T07:54:31Z"""") &&
        roundTrip(Instant.ofEpochSecond(-7596485350362790L), """"-240721068-02-29T06:46:50Z"""") &&
        decode("\"2025-07-18T08:29:13.121409459+01:00\"", Instant.parse("2025-07-18T07:29:13.121409459Z")) &&
        decode("\"2025-07-18T08:29:13.121409459-01:00\"", Instant.parse("2025-07-18T09:29:13.121409459Z")) &&
        check(Gen.char) { ch =>
          val nonNumber              = if (ch >= '0' && ch <= '9' || ch == '-' || ch == '+') 'X' else ch
          val nonDigit               = if (ch >= '0' && ch <= '9') 'X' else ch
          val nonDigitOrSignOrZ      = if (ch >= '0' && ch <= '9' || ch == '+' || ch == '-' || ch == 'Z') 'X' else ch
          val nonDigitOrDash         = if (ch >= '0' && ch <= '9' || ch == '-') 'X' else ch
          val nonDash                = if (ch == '-') 'X' else ch
          val nonDoubleQuotes        = if (ch == '"') 'X' else ch
          val nonT                   = if (ch == 'T') 'X' else ch
          val nonColon               = if (ch == ':') 'X' else ch
          val nonDotOrSignOrZ        = if (ch == '.' || ch == '+' || ch == '-' || ch == 'Z') 'X' else ch
          val nonSignOrZ             = if (ch == '+' || ch == '-' || ch == 'Z') 'X' else ch
          val nonColonOrDoubleQuotes = if (ch == ':' || ch == '"') 'X' else ch
          decodeError[Instant](s""""${nonNumber}008-01-20T07:24:33Z"""", "expected '-' or '+' or digit at: .") &&
          decodeError[Instant](s""""2${nonDigit}08-01-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[Instant](s""""20${nonDigit}8-01-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[Instant](s""""200${nonDigit}-01-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[Instant](s""""2008${nonDash}01-20T07:24:33Z"""", "expected '-' at: .") &&
          decodeError[Instant](s""""2008-${nonDigit}0-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[Instant](s""""2008-0${nonDigit}-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[Instant](s""""2008-01${nonDash}20T07:24:33Z"""", "expected '-' at: .") &&
          decodeError[Instant](s""""2008-01-${nonDigit}0T07:24:33Z"""", "expected digit at: .") &&
          decodeError[Instant](s""""2008-01-2${nonDigit}T07:24:33Z"""", "expected digit at: .") &&
          decodeError[Instant](s""""2008-01-20${nonT}07:24:33Z"""", "expected 'T' at: .") &&
          decodeError[Instant](s""""2008-01-20T${nonDigit}7:24:33Z"""", "expected digit at: .") &&
          decodeError[Instant](s""""2008-01-20T0${nonDigit}:24:33Z"""", "expected digit at: .") &&
          decodeError[Instant](s""""2008-01-20T07${nonColon}24:33Z"""", "expected ':' at: .") &&
          decodeError[Instant](s""""2008-01-20T07:${nonDigit}4:33Z"""", "expected digit at: .") &&
          decodeError[Instant](s""""2008-01-20T07:2${nonDigit}:33Z"""", "expected digit at: .") &&
          decodeError[Instant](s""""2008-01-20T07:24${nonColon}33Z"""", "expected ':' at: .") &&
          decodeError[Instant](s""""2008-01-20T07:24:${nonDigit}3Z"""", "expected digit at: .") &&
          decodeError[Instant](s""""2008-01-20T07:24:3${nonDigit}Z"""", "expected digit at: .") &&
          decodeError[Instant](
            s""""2008-01-20T07:24:33${nonDotOrSignOrZ}"""",
            "expected '.' or '+' or '-' or 'Z' at: ."
          ) &&
          decodeError[Instant](s""""2008-01-20T07:24:33Z${nonDoubleQuotes}"""", """expected '"' at: .""") &&
          decodeError[Instant](
            s""""2008-01-20T07:24:33.${nonDigitOrSignOrZ}"""",
            "expected '+' or '-' or 'Z' or digit at: ."
          ) &&
          decodeError[Instant](
            s""""2008-01-20T07:24:33.000${nonDigitOrSignOrZ}"""",
            "expected '+' or '-' or 'Z' or digit at: ."
          ) &&
          decodeError[Instant](
            s""""2008-01-20T07:24:33.123456789${nonSignOrZ}"""",
            "expected '+' or '-' or 'Z' at: ."
          ) &&
          decodeError[Instant](s""""2008-01-20T07:24:33+${nonDigit}0"""", "expected digit at: .") &&
          decodeError[Instant](s""""2008-01-20T07:24:33-${nonDigit}0"""", "expected digit at: .") &&
          decodeError[Instant](s""""2008-01-20T07:24:33.+${nonDigit}0"""", "expected digit at: .") &&
          decodeError[Instant](s""""2008-01-20T07:24:33.+1${nonDigit}"""", "expected digit at: .") &&
          decodeError[Instant](
            s""""2008-01-20T07:24:33.+10${nonColonOrDoubleQuotes}"""",
            """expected ':' or '"' at: ."""
          ) &&
          decodeError[Instant](s""""2008-01-20T07:24:33.+10:${nonDigit}0"""", "expected digit at: .") &&
          decodeError[Instant](s""""2008-01-20T07:24:33.+10:1${nonDigit}"""", "expected digit at: .") &&
          decodeError[Instant](
            s""""2008-01-20T07:24:33.+10:10${nonColonOrDoubleQuotes}10"""",
            """expected ':' or '"' at: ."""
          ) &&
          decodeError[Instant](s""""2008-01-20T07:24:33.+10:10:${nonDigit}0"""", "expected digit at: .") &&
          decodeError[Instant](s""""2008-01-20T07:24:33.+10:10:1${nonDigit}"""", "expected digit at: .") &&
          decodeError[Instant](s""""+${nonDigit}0000-01-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[Instant](s""""+1${nonDigit}000-01-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[Instant](s""""+10${nonDigit}00-01-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[Instant](s""""+100${nonDigit}0-01-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[Instant](s""""+1000${nonDigit}-01-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[Instant](s""""-1000${nonDigitOrDash}-01-20T07:24:33Z"""", "expected '-' or digit at: .") &&
          decodeError[Instant](s""""+10000${nonDigitOrDash}-01-20T07:24:33Z"""", "expected '-' or digit at: .") &&
          decodeError[Instant](s""""+100000${nonDigitOrDash}-01-20T07:24:33Z"""", "expected '-' or digit at: .") &&
          decodeError[Instant](s""""+1000000${nonDigitOrDash}-01-20T07:24:33Z"""", "expected '-' or digit at: .") &&
          decodeError[Instant](s""""+10000000${nonDigitOrDash}-01-20T07:24:33Z"""", "expected '-' or digit at: .") &&
          decodeError[Instant](s""""+100000000${nonDigitOrDash}-01-20T07:24:33Z"""", "expected '-' or digit at: .")
        } &&
        decodeError[Instant]("""null""", "expected '\"' at: .") &&
        decodeError[Instant](""""""", "unexpected end of input at: .") &&
        decodeError[Instant](""""2008-01-20T07:24:33Z""", "unexpected end of input at: .") &&
        decodeError[Instant](""""+1000000000=01-20T07:24:33Z"""", "expected '-' at: .") &&
        decodeError[Instant](""""-0000-01-20T07:24:33Z"""", "illegal year at: .") &&
        decodeError[Instant](""""+1000000001-01-20T07:24:33Z"""", "illegal year at: .") &&
        decodeError[Instant](""""+4000000000-01-20T07:24:33Z"""", "illegal year at: .") &&
        decodeError[Instant](""""+9999999999-01-20T07:24:33Z"""", "illegal year at: .") &&
        decodeError[Instant](""""-1000000001-01-20T07:24:33Z"""", "illegal year at: .") &&
        decodeError[Instant](""""-4000000000-01-20T07:24:33Z"""", "illegal year at: .") &&
        decodeError[Instant](""""-9999999999-01-20T07:24:33Z"""", "illegal year at: .") &&
        decodeError[Instant](""""2008-00-20T07:24:33Z"""", "illegal month at: .") &&
        decodeError[Instant](""""2008-13-20T07:24:33Z"""", "illegal month at: .") &&
        decodeError[Instant](""""2008-01-00T07:24:33Z"""", "illegal day at: .") &&
        decodeError[Instant](""""2008-01-32T07:24:33Z"""", "illegal day at: .") &&
        decodeError[Instant](""""2007-02-29T07:24:33Z"""", "illegal day at: .") &&
        decodeError[Instant](""""2008-02-30T07:24:33Z"""", "illegal day at: .") &&
        decodeError[Instant](""""2008-03-32T07:24:33Z"""", "illegal day at: .") &&
        decodeError[Instant](""""2008-04-31T07:24:33Z"""", "illegal day at: .") &&
        decodeError[Instant](""""2008-05-32T07:24:33Z"""", "illegal day at: .") &&
        decodeError[Instant](""""2008-06-31T07:24:33Z"""", "illegal day at: .") &&
        decodeError[Instant](""""2008-07-32T07:24:33Z"""", "illegal day at: .") &&
        decodeError[Instant](""""2008-08-32T07:24:33Z"""", "illegal day at: .") &&
        decodeError[Instant](""""2008-09-31T07:24:33Z"""", "illegal day at: .") &&
        decodeError[Instant](""""2008-10-32T07:24:33Z"""", "illegal day at: .") &&
        decodeError[Instant](""""2008-11-31T07:24:33Z"""", "illegal day at: .") &&
        decodeError[Instant](""""2008-01-20T24:24:33Z"""", "illegal hour at: .") &&
        decodeError[Instant](""""2008-01-20T07:60:33Z"""", "illegal minute at: .") &&
        decodeError[Instant](""""2008-01-20T07:24:60Z"""", "illegal second at: .") &&
        decodeError[Instant](""""2008-01-20T07:24:33.+20:10"""", "illegal timezone offset hour at: .") &&
        decodeError[Instant](""""2008-01-20T07:24:33.+10:60"""", "illegal timezone offset minute at: .") &&
        decodeError[Instant](""""2008-01-20T07:24:33.+10:10:60"""", "illegal timezone offset second at: .") &&
        decodeError[Instant](""""2008-01-20T07:24:33.+18:00:01"""", "illegal timezone offset at: .") &&
        decodeError[Instant](""""2008-01-20T07:24:33.-18:00:01"""", "illegal timezone offset at: .")
      },
      test("LocalDate") {
        check(genLocalDate)(x => roundTrip(x, s""""$x"""")) &&
        check(Gen.char) { ch =>
          val nonNumber       = if (ch >= '0' && ch <= '9' || ch == '-' || ch == '+') 'X' else ch
          val nonDigit        = if (ch >= '0' && ch <= '9') 'X' else ch
          val nonDigitOrDash  = if (ch >= '0' && ch <= '9' || ch == '-') 'X' else ch
          val nonDash         = if (ch == '-') 'X' else ch
          val nonDoubleQuotes = if (ch == '"') 'X' else ch
          decodeError[LocalDate](s""""${nonNumber}008-01-20"""", "expected '-' or '+' or digit at: .") &&
          decodeError[LocalDate](s""""2${nonDigit}08-01-20"""", "expected digit at: .") &&
          decodeError[LocalDate](s""""20${nonDigit}8-01-20"""", "expected digit at: .") &&
          decodeError[LocalDate](s""""200${nonDigit}-01-20"""", "expected digit at: .") &&
          decodeError[LocalDate](s""""2008${nonDash}01-20"""", "expected '-' at: .") &&
          decodeError[LocalDate](s""""+${nonDigit}0000-01-20"""", "expected digit at: .") &&
          decodeError[LocalDate](s""""+1${nonDigit}000-01-20"""", "expected digit at: .") &&
          decodeError[LocalDate](s""""+10${nonDigit}00-01-20"""", "expected digit at: .") &&
          decodeError[LocalDate](s""""+100${nonDigit}0-01-20"""", "expected digit at: .") &&
          decodeError[LocalDate](s""""+1000${nonDigit}-01-20"""", "expected digit at: .") &&
          decodeError[LocalDate](s""""-1000${nonDigitOrDash}-01-20"""", "expected '-' or digit at: .") &&
          decodeError[LocalDate](s""""+10000${nonDigitOrDash}-01-20"""", "expected '-' or digit at: .") &&
          decodeError[LocalDate](s""""+100000${nonDigitOrDash}-01-20"""", "expected '-' or digit at: .") &&
          decodeError[LocalDate](s""""+1000000${nonDigitOrDash}-01-20"""", "expected '-' or digit at: .") &&
          decodeError[LocalDate](s""""+10000000${nonDigitOrDash}-01-20"""", "expected '-' or digit at: .") &&
          decodeError[LocalDate](s""""+999999999${nonDash}01-20"""", "expected '-' at: .") &&
          decodeError[LocalDate](s""""2008-${nonDigit}1-20"""", "expected digit at: .") &&
          decodeError[LocalDate](s""""2008-0${nonDigit}-20"""", "expected digit at: .") &&
          decodeError[LocalDate](s""""2008-01${nonDash}20"""", "expected '-' at: .") &&
          decodeError[LocalDate](s""""2008-01-${nonDigit}0"""", "expected digit at: .") &&
          decodeError[LocalDate](s""""2008-01-2${nonDigit}"""", "expected digit at: .") &&
          decodeError[LocalDate](s""""2008-01-20${nonDoubleQuotes}"""", """expected '"' at: .""")
        } &&
        decodeError[LocalDate]("""null""", "expected '\"' at: .") &&
        decodeError[LocalDate](""""""", "unexpected end of input at: .") &&
        decodeError[LocalDate](""""2008-01-20""", "unexpected end of input at: .") &&
        decodeError[LocalDate](""""+1000000000-01-20"""", "expected '-' at: .") &&
        decodeError[LocalDate](""""-1000000000-01-20"""", "expected '-' at: .") &&
        decodeError[LocalDate](""""-0000-01-20"""", "illegal year at: .") &&
        decodeError[LocalDate](""""2008-00-20"""", "illegal month at: .") &&
        decodeError[LocalDate](""""2008-13-20"""", "illegal month at: .") &&
        decodeError[LocalDate](""""2008-01-00"""", "illegal day at: .") &&
        decodeError[LocalDate](""""2008-01-32"""", "illegal day at: .") &&
        decodeError[LocalDate](""""2007-02-29"""", "illegal day at: .") &&
        decodeError[LocalDate](""""2008-02-30"""", "illegal day at: .") &&
        decodeError[LocalDate](""""2008-03-32"""", "illegal day at: .") &&
        decodeError[LocalDate](""""2008-04-31"""", "illegal day at: .") &&
        decodeError[LocalDate](""""2008-05-32"""", "illegal day at: .") &&
        decodeError[LocalDate](""""2008-06-31"""", "illegal day at: .") &&
        decodeError[LocalDate](""""2008-07-32"""", "illegal day at: .") &&
        decodeError[LocalDate](""""2008-08-32"""", "illegal day at: .") &&
        decodeError[LocalDate](""""2008-09-31"""", "illegal day at: .") &&
        decodeError[LocalDate](""""2008-10-32"""", "illegal day at: .") &&
        decodeError[LocalDate](""""2008-11-31"""", "illegal day at: .") &&
        decodeError[LocalDate](""""2008-12-32"""", "illegal day at: .")
      },
      test("LocalDateTime") {
        check(genLocalDateTime)(x => roundTrip(x, s""""$x"""")) &&
        check(Gen.char) { ch =>
          val nonNumber              = if (ch >= '0' && ch <= '9' || ch == '-' || ch == '+') 'X' else ch
          val nonDigit               = if (ch >= '0' && ch <= '9') 'X' else ch
          val nonDigitOrDash         = if (ch >= '0' && ch <= '9' || ch == '-') 'X' else ch
          val nonDigitOrDoubleQuotes = if (ch >= '0' && ch <= '9' || ch == '"') 'X' else ch
          val nonDash                = if (ch == '-') 'X' else ch
          val nonDoubleQuotes        = if (ch == '"') 'X' else ch
          val nonT                   = if (ch == 'T') 'X' else ch
          val nonColon               = if (ch == ':') 'X' else ch
          val nonColonOrDoubleQuotes = if (ch == ':' || ch == '"') 'X' else ch
          val nonDotOrDoubleQuotes   = if (ch == '.' || ch == '"') 'X' else ch
          decodeError[LocalDateTime](s""""${nonNumber}008-01-20T07:24:33"""", "expected '-' or '+' or digit at: .") &&
          decodeError[LocalDateTime](s""""2${nonDigit}08-01-20T07:24:33"""", "expected digit at: .") &&
          decodeError[LocalDateTime](s""""20${nonDigit}8-01-20T07:24:33"""", "expected digit at: .") &&
          decodeError[LocalDateTime](s""""200${nonDigit}-01-20T07:24:33"""", "expected digit at: .") &&
          decodeError[LocalDateTime](s""""2008${nonDash}01-20T07:24:33"""", "expected '-' at: .") &&
          decodeError[LocalDateTime](s""""+${nonDigit}0000-01-20T07:24:33"""", "expected digit at: .") &&
          decodeError[LocalDateTime](s""""+1${nonDigit}000-01-20T07:24:33"""", "expected digit at: .") &&
          decodeError[LocalDateTime](s""""+10${nonDigit}00-01-20T07:24:33"""", "expected digit at: .") &&
          decodeError[LocalDateTime](s""""+100${nonDigit}0-01-20T07:24:33"""", "expected digit at: .") &&
          decodeError[LocalDateTime](s""""+1000${nonDigit}-01-20T07:24:33"""", "expected digit at: .") &&
          decodeError[LocalDateTime](s""""-1000${nonDigitOrDash}-01-20T07:24:33"""", "expected '-' or digit at: .") &&
          decodeError[LocalDateTime](s""""+10000${nonDigitOrDash}-01-20T07:24:33"""", "expected '-' or digit at: .") &&
          decodeError[LocalDateTime](s""""+100000${nonDigitOrDash}-01-20T07:24:33"""", "expected '-' or digit at: .") &&
          decodeError[LocalDateTime](
            s""""+1000000${nonDigitOrDash}-01-20T07:24:33"""",
            "expected '-' or digit at: ."
          ) &&
          decodeError[LocalDateTime](
            s""""+10000000${nonDigitOrDash}-01-20T07:24:33"""",
            "expected '-' or digit at: ."
          ) &&
          decodeError[LocalDateTime](s""""+999999999${nonDash}01-20T07:24:33"""", "expected '-' at: .") &&
          decodeError[LocalDateTime](s""""2008-${nonDigit}1-20T07:24:33"""", "expected digit at: .") &&
          decodeError[LocalDateTime](s""""2008-0${nonDigit}-20T07:24:33"""", "expected digit at: .") &&
          decodeError[LocalDateTime](s""""2008-01${nonDash}20T07:24:33"""", "expected '-' at: .") &&
          decodeError[LocalDateTime](s""""2008-01-${nonDigit}0T07:24:33"""", "expected digit at: .") &&
          decodeError[LocalDateTime](s""""2008-01-2${nonDigit}T07:24:33"""", "expected digit at: .") &&
          decodeError[LocalDateTime](s""""2008-01-20${nonT}07:24:33"""", "expected 'T' at: .") &&
          decodeError[LocalDateTime](s""""2008-01-20T${nonDigit}7:24:33"""", "expected digit at: .") &&
          decodeError[LocalDateTime](s""""2008-01-20T0${nonDigit}:24:33"""", "expected digit at: .") &&
          decodeError[LocalDateTime](s""""2008-01-20T07${nonColon}24:33"""", "expected ':' at: .") &&
          decodeError[LocalDateTime](s""""2008-01-20T07:${nonDigit}4:33"""", "expected digit at: .") &&
          decodeError[LocalDateTime](s""""2008-01-20T07:2${nonDigit}:33"""", "expected digit at: .") &&
          decodeError[LocalDateTime](
            s""""2008-01-20T07:24${nonColonOrDoubleQuotes}33"""",
            """expected ':' or '"' at: ."""
          ) &&
          decodeError[LocalDateTime](s""""2008-01-20T07:24:${nonDigit}3"""", "expected digit at: .") &&
          decodeError[LocalDateTime](s""""2008-01-20T07:24:3${nonDigit}"""", "expected digit at: .") &&
          decodeError[LocalDateTime](
            s""""2008-01-20T07:24:33${nonDotOrDoubleQuotes}"""",
            """expected '.' or '"' at: ."""
          ) &&
          decodeError[LocalDateTime](
            s""""2008-01-20T07:24:33.${nonDigitOrDoubleQuotes}"""",
            """expected '"' or digit at: ."""
          ) &&
          decodeError[LocalDateTime](s""""2008-01-20T07:24:33.123456789${nonDoubleQuotes}"""", """expected '"' at: .""")
        } &&
        decodeError[LocalDateTime]("""null""", "expected '\"' at: .") &&
        decodeError[LocalDateTime](""""2008-01-20T24:24"   """, "illegal hour at: .") &&
        decodeError[LocalDateTime](""""""", "unexpected end of input at: .") &&
        decodeError[LocalDateTime](""""2008-01-20T07:24:33""", "unexpected end of input at: .") &&
        decodeError[LocalDateTime](""""+1000000000-01-20T07:24:33"""", "expected '-' at: .") &&
        decodeError[LocalDateTime](""""-1000000000-01-20T07:24:33"""", "expected '-' at: .") &&
        decodeError[LocalDateTime](""""-0000-01-20T07:24:33"""", "illegal year at: .") &&
        decodeError[LocalDateTime](""""2008-00-20T07:24:33"""", "illegal month at: .") &&
        decodeError[LocalDateTime](""""2008-13-20T07:24:33"""", "illegal month at: .") &&
        decodeError[LocalDateTime](""""2008-01-00T07:24:33"""", "illegal day at: .") &&
        decodeError[LocalDateTime](""""2008-01-32T07:24:33"""", "illegal day at: .") &&
        decodeError[LocalDateTime](""""2007-02-29T07:24:33"""", "illegal day at: .") &&
        decodeError[LocalDateTime](""""2008-02-30T07:24:33"""", "illegal day at: .") &&
        decodeError[LocalDateTime](""""2008-03-32T07:24:33"""", "illegal day at: .") &&
        decodeError[LocalDateTime](""""2008-04-31T07:24:33"""", "illegal day at: .") &&
        decodeError[LocalDateTime](""""2008-05-32T07:24:33"""", "illegal day at: .") &&
        decodeError[LocalDateTime](""""2008-06-31T07:24:33"""", "illegal day at: .") &&
        decodeError[LocalDateTime](""""2008-07-32T07:24:33"""", "illegal day at: .") &&
        decodeError[LocalDateTime](""""2008-08-32T07:24:33"""", "illegal day at: .") &&
        decodeError[LocalDateTime](""""2008-09-31T07:24:33"""", "illegal day at: .") &&
        decodeError[LocalDateTime](""""2008-10-32T07:24:33"""", "illegal day at: .") &&
        decodeError[LocalDateTime](""""2008-11-31T07:24:33"""", "illegal day at: .") &&
        decodeError[LocalDateTime](""""2008-12-32T07:24:33"""", "illegal day at: .") &&
        decodeError[LocalDateTime](""""2008-01-20T24:24:33"""", "illegal hour at: .") &&
        decodeError[LocalDateTime](""""2008-01-20T07:60:33"""", "illegal minute at: .") &&
        decodeError[LocalDateTime](""""2008-01-20T07:24:60"""", "illegal second at: .")
      },
      test("LocalTime") {
        check(genLocalTime)(x => roundTrip(x, s""""$x"""")) &&
        check(Gen.char) { ch =>
          val nonDigit               = if (ch >= '0' && ch <= '9') 'X' else ch
          val nonDigitOrDoubleQuotes = if (ch >= '0' && ch <= '9' || ch == '"') 'X' else ch
          val nonColon               = if (ch == ':') 'X' else ch
          val nonColonOrDoubleQuotes = if (ch == ':' || ch == '"') 'X' else ch
          val nonDoubleQuotes        = if (ch == '"') 'X' else ch
          val nonDotOrDoubleQuotes   = if (ch == '.' || ch == '"') 'X' else ch
          decodeError[LocalTime](s""""${nonDigit}7:24:33"""", "expected digit at: .") &&
          decodeError[LocalTime](s""""0${nonDigit}:24:33"""", "expected digit at: .") &&
          decodeError[LocalTime](s""""07${nonColon}24:33"""", "expected ':' at: .") &&
          decodeError[LocalTime](s""""07:${nonDigit}4:33"""", "expected digit at: .") &&
          decodeError[LocalTime](s""""07:2${nonDigit}:33"""", "expected digit at: .") &&
          decodeError[LocalTime](s""""07:24${nonColonOrDoubleQuotes}33"""", """expected ':' or '"' at: .""") &&
          decodeError[LocalTime](s""""07:24:${nonDigit}3"""", "expected digit at: .") &&
          decodeError[LocalTime](s""""07:24:3${nonDigit}"""", "expected digit at: .") &&
          decodeError[LocalTime](s""""07:24:33${nonDotOrDoubleQuotes}"""", """expected '.' or '"' at: .""") &&
          decodeError[LocalTime](s""""07:24:33.${nonDigitOrDoubleQuotes}"""", """expected '"' or digit at: .""") &&
          decodeError[LocalTime](s""""07:24:33.123456789${nonDoubleQuotes}"""", """expected '"' at: .""")
        } &&
        decodeError[LocalTime]("""null""", "expected '\"' at: .") &&
        decodeError[LocalTime](""""24:24"   """, "illegal hour at: .") &&
        decodeError[LocalTime](""""""", "unexpected end of input at: .") &&
        decodeError[LocalTime](""""07:24:33""", "unexpected end of input at: .") &&
        decodeError[LocalTime](""""24:24:33"""", "illegal hour at: .") &&
        decodeError[LocalTime](""""07:60:33"""", "illegal minute at: .") &&
        decodeError[LocalTime](""""07:24:60"""", "illegal second at: .")
      },
      test("Month") {
        check(genMonth)(x => roundTrip(x, s""""$x"""")) &&
        decodeError[Month]("", "unexpected end of input at: .") &&
        decodeError[Month](""""Jum"""", "illegal month value at: .")
      },
      test("MonthDay") {
        check(genMonthDay)(x => roundTrip(x, s""""$x"""")) &&
        check(Gen.char) { ch =>
          val nonDigit        = if (ch >= '0' && ch <= '9') 'X' else ch
          val nonDash         = if (ch == '-') 'X' else ch
          val nonDoubleQuotes = if (ch == '"') 'X' else ch
          decodeError[MonthDay](s""""--${nonDigit}1-20"""", "expected digit at: .") &&
          decodeError[MonthDay](s""""--0${nonDigit}-20"""", "expected digit at: .") &&
          decodeError[MonthDay](s""""--01${nonDash}20"""", "expected '-' at: .") &&
          decodeError[MonthDay](s""""--01-${nonDigit}0"""", "expected digit at: .") &&
          decodeError[MonthDay](s""""--01-2${nonDigit}"""", "expected digit at: .") &&
          decodeError[MonthDay](s""""--01-20${nonDoubleQuotes}"""", """expected '"' at: .""")
        } &&
        decodeError[MonthDay]("""null""", "expected '\"' at: .") &&
        decodeError[MonthDay](""""""", "unexpected end of input at: .") &&
        decodeError[MonthDay](""""=-01-20"""", "expected '-' at: .") &&
        decodeError[MonthDay](""""-=01-20"""", "expected '-' at: .") &&
        decodeError[MonthDay](""""--00-20"""", "illegal month at: .") &&
        decodeError[MonthDay](""""--13-20"""", "illegal month at: .") &&
        decodeError[MonthDay](""""--01-00"""", "illegal day at: .") &&
        decodeError[MonthDay](""""--01-32"""", "illegal day at: .") &&
        decodeError[MonthDay](""""--02-30"""", "illegal day at: .") &&
        decodeError[MonthDay](""""--03-32"""", "illegal day at: .") &&
        decodeError[MonthDay](""""--04-31"""", "illegal day at: .") &&
        decodeError[MonthDay](""""--05-32"""", "illegal day at: .") &&
        decodeError[MonthDay](""""--06-31"""", "illegal day at: .") &&
        decodeError[MonthDay](""""--07-32"""", "illegal day at: .") &&
        decodeError[MonthDay](""""--08-32"""", "illegal day at: .") &&
        decodeError[MonthDay](""""--09-31"""", "illegal day at: .") &&
        decodeError[MonthDay](""""--10-32"""", "illegal day at: .") &&
        decodeError[MonthDay](""""--11-31"""", "illegal day at: .") &&
        decodeError[MonthDay](""""--12-32"""", "illegal day at: .")
      },
      test("OffsetDateTime") {
        check(genOffsetDateTime)(x => roundTrip(x, s""""$x"""")) &&
        check(Gen.char) { ch =>
          val nonNumber              = if (ch >= '0' && ch <= '9' || ch == '-' || ch == '+') 'X' else ch
          val nonNumberOrZ           = if (ch >= '0' && ch <= '9' || ch == '-' || ch == '+' || ch == 'Z') 'X' else ch
          val nonDigit               = if (ch >= '0' && ch <= '9') 'X' else ch
          val nonDigitOrDash         = if (ch >= '0' && ch <= '9' || ch == '-') 'X' else ch
          val nonDash                = if (ch == '-') 'X' else ch
          val nonDoubleQuotes        = if (ch == '"') 'X' else ch
          val nonT                   = if (ch == 'T') 'X' else ch
          val nonColon               = if (ch == ':') 'X' else ch
          val nonColonOrDoubleQuotes = if (ch == ':' || ch == '"') 'X' else ch
          val nonColonOrSignOrZ      = if (ch == ':' || ch == '-' || ch == '+' || ch == 'Z') 'X' else ch
          val nonDotOrSignOrZ        = if (ch == '.' || ch == '-' || ch == '+' || ch == 'Z') 'X' else ch
          val nonSignOrZ             = if (ch == '.' || ch == '-' || ch == '+' || ch == 'Z') 'X' else ch
          decodeError[OffsetDateTime](s""""${nonNumber}008-01-20T07:24:33Z"""", "expected '-' or '+' or digit at: .") &&
          decodeError[OffsetDateTime](s""""2${nonDigit}08-01-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""20${nonDigit}8-01-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""200${nonDigit}-01-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""2008${nonDash}01-20T07:24:33Z"""", "expected '-' at: .") &&
          decodeError[OffsetDateTime](s""""+${nonDigit}0000-01-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""+1${nonDigit}000-01-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""+10${nonDigit}00-01-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""+100${nonDigit}0-01-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""+1000${nonDigit}-01-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""-1000${nonDigitOrDash}-01-20T07:24:33Z"""", "expected '-' or digit at: .") &&
          decodeError[OffsetDateTime](
            s""""+10000${nonDigitOrDash}-01-20T07:24:33Z"""",
            "expected '-' or digit at: ."
          ) &&
          decodeError[OffsetDateTime](
            s""""+100000${nonDigitOrDash}-01-20T07:24:33Z"""",
            "expected '-' or digit at: ."
          ) &&
          decodeError[OffsetDateTime](
            s""""+1000000${nonDigitOrDash}-01-20T07:24:33Z"""",
            "expected '-' or digit at: ."
          ) &&
          decodeError[OffsetDateTime](
            s""""+10000000${nonDigitOrDash}-01-20T07:24:33Z"""",
            "expected '-' or digit at: ."
          ) &&
          decodeError[OffsetDateTime](s""""+999999999${nonDash}01-20T07:24:33Z"""", "expected '-' at: .") &&
          decodeError[OffsetDateTime](s""""2008-${nonDigit}1-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""2008-0${nonDigit}-20T07:24:33Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""2008-01${nonDash}20T07:24:33Z"""", "expected '-' at: .") &&
          decodeError[OffsetDateTime](s""""2008-01-${nonDigit}0T07:24:33Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""2008-01-2${nonDigit}T07:24:33Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""2008-01-20${nonT}07:24:33Z"""", "expected 'T' at: .") &&
          decodeError[OffsetDateTime](s""""2008-01-20T${nonDigit}7:24:33Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""2008-01-20T0${nonDigit}:24:33Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""2008-01-20T07${nonColon}24:33Z"""", "expected ':' at: .") &&
          decodeError[OffsetDateTime](s""""2008-01-20T07:${nonDigit}4:33Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""2008-01-20T07:2${nonDigit}:33Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](
            s""""2008-01-20T07:24${nonColonOrSignOrZ}33Z"""",
            "expected ':' or '+' or '-' or 'Z' at: ."
          ) &&
          decodeError[OffsetDateTime](s""""2008-01-20T07:24:${nonDigit}3Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""2008-01-20T07:24:3${nonDigit}Z"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](
            s""""2008-01-20T07:24:33${nonDotOrSignOrZ}"""",
            "expected '.' or '+' or '-' or 'Z' at: ."
          ) &&
          decodeError[OffsetDateTime](s""""2008-01-20T07:24:33Z${nonDoubleQuotes}"""", """expected '"' at: .""") &&
          decodeError[OffsetDateTime](
            s""""2008-01-20T07:24:33.${nonNumberOrZ}"""",
            "expected '+' or '-' or 'Z' or digit at: ."
          ) &&
          decodeError[OffsetDateTime](
            s""""2008-01-20T07:24:33.000${nonNumberOrZ}"""",
            "expected '+' or '-' or 'Z' or digit at: ."
          ) &&
          decodeError[OffsetDateTime](
            s""""2008-01-20T07:24:33.123456789${nonSignOrZ}"""",
            "expected '+' or '-' or 'Z' at: ."
          ) &&
          decodeError[OffsetDateTime](s""""2008-01-20T07:24+${nonDigit}0"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""2008-01-20T07:24+1${nonDigit}"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""2008-01-20T07:24:33+${nonDigit}0"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""2008-01-20T07:24:33-${nonDigit}0"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""2008-01-20T07:24:33.+${nonDigit}0"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""2008-01-20T07:24:33.+1${nonDigit}"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](
            s""""2008-01-20T07:24:33.+10${nonColonOrDoubleQuotes}"""",
            """expected ':' or '"' at: ."""
          ) &&
          decodeError[OffsetDateTime](s""""2008-01-20T07:24:33.+10:${nonDigit}0"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""2008-01-20T07:24:33.+10:1${nonDigit}"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](
            s""""2008-01-20T07:24:33.+10:10${nonColonOrDoubleQuotes}10"""",
            """expected ':' or '"' at: ."""
          ) &&
          decodeError[OffsetDateTime](s""""2008-01-20T07:24:33.+10:10:${nonDigit}0"""", "expected digit at: .") &&
          decodeError[OffsetDateTime](s""""2008-01-20T07:24:33.+10:10:1${nonDigit}"""", "expected digit at: .")
        } &&
        decodeError[OffsetDateTime]("""null""", "expected '\"' at: .") &&
        decodeError[OffsetDateTime](""""""", "unexpected end of input at: .") &&
        decodeError[OffsetDateTime](""""2008-01-20T07:24:33Z""", "unexpected end of input at: .") &&
        decodeError[OffsetDateTime](""""+1000000000-01-20T07:24:33Z"""", "expected '-' at: .") &&
        decodeError[OffsetDateTime](""""-1000000000-01-20T07:24:33Z"""", "expected '-' at: .") &&
        decodeError[OffsetDateTime](""""-0000-01-20T07:24:33Z"""", "illegal year at: .") &&
        decodeError[OffsetDateTime](""""2008-00-20T07:24:33Z"""", "illegal month at: .") &&
        decodeError[OffsetDateTime](""""2008-13-20T07:24:33Z"""", "illegal month at: .") &&
        decodeError[OffsetDateTime](""""2008-01-00T07:24:33Z"""", "illegal day at: .") &&
        decodeError[OffsetDateTime](""""2008-01-32T07:24:33Z"""", "illegal day at: .") &&
        decodeError[OffsetDateTime](""""2007-02-29T07:24:33Z"""", "illegal day at: .") &&
        decodeError[OffsetDateTime](""""2008-02-30T07:24:33Z"""", "illegal day at: .") &&
        decodeError[OffsetDateTime](""""2008-03-32T07:24:33Z"""", "illegal day at: .") &&
        decodeError[OffsetDateTime](""""2008-04-31T07:24:33Z"""", "illegal day at: .") &&
        decodeError[OffsetDateTime](""""2008-05-32T07:24:33Z"""", "illegal day at: .") &&
        decodeError[OffsetDateTime](""""2008-06-31T07:24:33Z"""", "illegal day at: .") &&
        decodeError[OffsetDateTime](""""2008-07-32T07:24:33Z"""", "illegal day at: .") &&
        decodeError[OffsetDateTime](""""2008-08-32T07:24:33Z"""", "illegal day at: .") &&
        decodeError[OffsetDateTime](""""2008-09-31T07:24:33Z"""", "illegal day at: .") &&
        decodeError[OffsetDateTime](""""2008-10-32T07:24:33Z"""", "illegal day at: .") &&
        decodeError[OffsetDateTime](""""2008-11-31T07:24:33Z"""", "illegal day at: .") &&
        decodeError[OffsetDateTime](""""2008-12-32T07:24:33Z"""", "illegal day at: .") &&
        decodeError[OffsetDateTime](""""2008-01-20T24:24:33Z"""", "illegal hour at: .") &&
        decodeError[OffsetDateTime](""""2008-01-20T07:60:33Z"""", "illegal minute at: .") &&
        decodeError[OffsetDateTime](""""2008-01-20T07:24:60Z"""", "illegal second at: .") &&
        decodeError[OffsetDateTime](""""2008-01-20T07:24:33.+20:10"""", "illegal timezone offset hour at: .") &&
        decodeError[OffsetDateTime](""""2008-01-20T07:24:33.+10:60"""", "illegal timezone offset minute at: .") &&
        decodeError[OffsetDateTime](""""2008-01-20T07:24:33.+10:10:60"""", "illegal timezone offset second at: .") &&
        decodeError[OffsetDateTime](""""2008-01-20T07:24:33.+18:00:01"""", "illegal timezone offset at: .") &&
        decodeError[OffsetDateTime](""""2008-01-20T07:24:33.-18:00:01"""", "illegal timezone offset at: .")
      },
      test("OffsetTime") {
        check(genOffsetTime)(x => roundTrip(x, s""""$x"""")) &&
        check(Gen.char) { ch =>
          val nonNumberOrZ           = if (ch >= '0' && ch <= '9' || ch == '-' || ch == '+' || ch == 'Z') 'X' else ch
          val nonSignOrZ             = if (ch == '-' || ch == '+' || ch == 'Z') 'X' else ch
          val nonDigit               = if (ch >= '0' && ch <= '9') 'X' else ch
          val nonColon               = if (ch == ':') 'X' else ch
          val nonColonOrDoubleQuotes = if (ch == ':' || ch == '"') 'X' else ch
          val nonDoubleQuotes        = if (ch == '"') 'X' else ch
          val nonDotOrSignOrZ        = if (ch == '.' || ch == '-' || ch == '+' || ch == 'Z') 'X' else ch
          val nonColonOrSignOrZ      = if (ch == ':' || ch == '-' || ch == '+' || ch == 'Z') 'X' else ch
          decodeError[OffsetTime](s""""${nonDigit}7:24:33Z"""", "expected digit at: .") &&
          decodeError[OffsetTime](s""""0${nonDigit}:24:33Z"""", "expected digit at: .") &&
          decodeError[OffsetTime](s""""07${nonColon}24:33Z"""", "expected ':' at: .") &&
          decodeError[OffsetTime](s""""07:${nonDigit}4:33Z"""", "expected digit at: .") &&
          decodeError[OffsetTime](s""""07:2${nonDigit}:33Z"""", "expected digit at: .") &&
          decodeError[OffsetTime](s""""07:24${nonColonOrSignOrZ}33Z"""", "expected ':' or '+' or '-' or 'Z' at: .") &&
          decodeError[OffsetTime](s""""07:24:${nonDigit}3Z"""", "expected digit at: .") &&
          decodeError[OffsetTime](s""""07:24:3${nonDigit}Z"""", "expected digit at: .") &&
          decodeError[OffsetTime](s""""07:24:33${nonDotOrSignOrZ}"""", "expected '.' or '+' or '-' or 'Z' at: .") &&
          decodeError[OffsetTime](s""""07:24:33.${nonNumberOrZ}"""", "expected '+' or '-' or 'Z' or digit at: .") &&
          decodeError[OffsetTime](s""""07:24:33.123456789${nonSignOrZ}"""", "expected '+' or '-' or 'Z' at: .") &&
          decodeError[OffsetTime](s""""07:24:33.+10${nonColonOrDoubleQuotes}"""", """expected ':' or '"' at: .""") &&
          decodeError[OffsetTime](s""""07:24:33.+10:${nonDigit}"""", "expected digit at: .") &&
          decodeError[OffsetTime](s""""07:24:33.+10:1${nonDigit}"""", "expected digit at: .") &&
          decodeError[OffsetTime](
            s""""07:24:33.+10:10${nonColonOrDoubleQuotes}10"""",
            """expected ':' or '"' at: ."""
          ) &&
          decodeError[OffsetTime](s""""07:24:33.+10:10:${nonDigit}0"""", "expected digit at: .") &&
          decodeError[OffsetTime](s""""07:24:33.+10:10:1${nonDigit}"""", "expected digit at: .") &&
          decodeError[OffsetTime](s""""07:24:33.+10:10:10${nonDoubleQuotes}"""", """expected '"' at: .""")
        } &&
        decodeError[OffsetTime]("""null""", "expected '\"' at: .") &&
        decodeError[OffsetTime](""""""", "unexpected end of input at: .") &&
        decodeError[OffsetTime](""""07:24:33Z""", "unexpected end of input at: .") &&
        decodeError[OffsetTime](""""24:24:33Z"""", "illegal hour at: .") &&
        decodeError[OffsetTime](""""07:60:33Z"""", "illegal minute at: .") &&
        decodeError[OffsetTime](""""07:24:60Z"""", "illegal second at: .") &&
        decodeError[OffsetTime](""""07:24:33.+19:10"""", "illegal timezone offset hour at: .") &&
        decodeError[OffsetTime](""""07:24:33.+10:60"""", "illegal timezone offset minute at: .") &&
        decodeError[OffsetTime](""""07:24:33.+10:10:60"""", "illegal timezone offset second at: .") &&
        decodeError[OffsetTime](""""07:24:33.+18:00:01"""", "illegal timezone offset at: .") &&
        decodeError[OffsetTime](""""07:24:33.-18:00:01"""", "illegal timezone offset at: .")
      },
      test("Period") {
        check(genPeriod)(x => roundTrip(x, s""""$x"""")) &&
        roundTrip(Period.ofDays(0), """"P0D"""") &&
        roundTrip(Period.ofYears(Int.MinValue), """"P-2147483648Y"""") &&
        roundTrip(Period.ofMonths(Int.MinValue), """"P-2147483648M"""") &&
        roundTrip(Period.ofDays(Int.MinValue), """"P-2147483648D"""") &&
        decodeError[Period]("""null""", "expected '\"' at: .") &&
        decodeError[Period](""""""", "unexpected end of input at: .") &&
        decodeError[Period]("""""""", "expected 'P' or '-' at: .") &&
        decodeError[Period](""""-"""", "expected 'P' at: .") &&
        decodeError[Period](""""PXY"""", "expected '-' or digit at: .") &&
        decodeError[Period](""""P-XY"""", "expected digit at: .") &&
        decodeError[Period](""""P1XY"""", "expected 'Y' or 'M' or 'W' or 'D' or digit at: .") &&
        decodeError[Period](""""P2147483648Y"""", "illegal period at: .") &&
        decodeError[Period](""""P21474836470Y"""", "illegal period at: .") &&
        decodeError[Period](""""P-2147483649Y"""", "illegal period at: .") &&
        decodeError[Period](""""P2147483648M"""", "illegal period at: .") &&
        decodeError[Period](""""P21474836470M"""", "illegal period at: .") &&
        decodeError[Period](""""P-2147483649M"""", "illegal period at: .") &&
        decodeError[Period](""""P2147483648W"""", "illegal period at: .") &&
        decodeError[Period](""""P21474836470W"""", "illegal period at: .") &&
        decodeError[Period](""""P-2147483649W"""", "illegal period at: .") &&
        decodeError[Period](""""P2147483648D"""", "illegal period at: .") &&
        decodeError[Period](""""P21474836470D"""", "illegal period at: .") &&
        decodeError[Period](""""P-2147483649D"""", "illegal period at: .") &&
        decodeError[Period](""""P1YXM"""", """expected '"' or '-' or digit at: .""") &&
        decodeError[Period](""""P1Y-XM"""", "expected digit at: .") &&
        decodeError[Period](""""P1Y1XM"""", "expected 'M' or 'W' or 'D' or digit at: .") &&
        decodeError[Period](""""P1Y2147483648M"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y21474836470M"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y-2147483649M"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y2147483648W"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y21474836470W"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y-2147483649W"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y2147483648D"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y21474836470D"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y-2147483649D"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y1MXW"""", """expected '"' or '-' or digit at: .""") &&
        decodeError[Period](""""P1Y1M-XW"""", "expected digit at: .") &&
        decodeError[Period](""""P1Y1M1XW"""", "expected 'W' or 'D' or digit at: .") &&
        decodeError[Period](""""P1Y1M306783379W"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y1M3067833790W"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y1M-306783379W"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y1M2147483648D"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y1M21474836470D"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y1M-2147483649D"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y1M1WXD"""", """expected '"' or '-' or digit at: .""") &&
        decodeError[Period](""""P1Y1M1W-XD"""", "expected digit at: .") &&
        decodeError[Period](""""P1Y1M1W1XD"""", "expected 'D' or digit at: .") &&
        decodeError[Period](""""P1Y1M306783378W8D"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y1M-306783378W-8D"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y1M1W2147483647D"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y1M-1W-2147483648D"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y1M0W2147483648D"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y1M0W21474836470D"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y1M0W-2147483649D"""", "illegal period at: .") &&
        decodeError[Period](""""P1Y1M1W1DX""", """expected '"' at: .""")
      },
      test("Year") {
        check(genYear)(x => roundTrip(x, s""""${toISO8601(x)}"""")) &&
        check(Gen.char) { ch =>
          val nonNumber              = if (ch >= '0' && ch <= '9' || ch == '-' || ch == '+') 'X' else ch
          val nonDigit               = if (ch >= '0' && ch <= '9') 'X' else ch
          val nonDigitOrDoubleQuotes = if (ch >= '0' && ch <= '9' || ch == '"') 'X' else ch
          decodeError[Year](s""""${nonNumber}008"""", "expected '-' or '+' or digit at: .") &&
          decodeError[Year](s""""2${nonDigit}08"""", "expected digit at: .") &&
          decodeError[Year](s""""20${nonDigit}8"""", "expected digit at: .") &&
          decodeError[Year](s""""200${nonDigit}"""", "expected digit at: .") &&
          decodeError[Year](s""""+${nonDigit}0000"""", "expected digit at: .") &&
          decodeError[Year](s""""+1${nonDigit}000"""", "expected digit at: .") &&
          decodeError[Year](s""""+10${nonDigit}00"""", "expected digit at: .") &&
          decodeError[Year](s""""+100${nonDigit}0"""", "expected digit at: .") &&
          decodeError[Year](s""""+1000${nonDigit}"""", "expected digit at: .") &&
          decodeError[Year](s""""-1000${nonDigitOrDoubleQuotes}"""", """expected '"' or digit at: .""") &&
          decodeError[Year](s""""+10000${nonDigitOrDoubleQuotes}"""", """expected '"' or digit at: .""") &&
          decodeError[Year](s""""+100000${nonDigitOrDoubleQuotes}"""", """expected '"' or digit at: .""") &&
          decodeError[Year](s""""+1000000${nonDigitOrDoubleQuotes}"""", """expected '"' or digit at: .""") &&
          decodeError[Year](s""""+10000000${nonDigitOrDoubleQuotes}"""", """expected '"' or digit at: .""")
        } &&
        decodeError[Year]("""null""", "expected '\"' at: .") &&
        decodeError[Year](""""""", "unexpected end of input at: .") &&
        decodeError[Year](""""2008""", "unexpected end of input at: .") &&
        decodeError[Year](""""+2008"""", "expected digit at: .") &&
        decodeError[Year](""""+1000000000"""", """expected '"' at: .""") &&
        decodeError[Year](""""-1000000000"""", """expected '"' at: .""") &&
        decodeError[Year](""""-0000"""", "illegal year at: .")
      },
      test("YearMonth") {
        check(genYearMonth)(x => roundTrip(x, s""""${toISO8601(x)}"""")) &&
        check(Gen.char) { ch =>
          val nonNumber       = if (ch >= '0' && ch <= '9' || ch == '-' || ch == '+') 'X' else ch
          val nonDigit        = if (ch >= '0' && ch <= '9') 'X' else ch
          val nonDigitOrDash  = if (ch >= '0' && ch <= '9' || ch == '-') 'X' else ch
          val nonDash         = if (ch == '-') 'X' else ch
          val nonDoubleQuotes = if (ch == '"') 'X' else ch
          decodeError[YearMonth](s""""${nonNumber}008-01"""", "expected '-' or '+' or digit at: .") &&
          decodeError[YearMonth](s""""2${nonDigit}08-01"""", "expected digit at: .") &&
          decodeError[YearMonth](s""""20${nonDigit}8-01"""", "expected digit at: .") &&
          decodeError[YearMonth](s""""200${nonDigit}-01"""", "expected digit at: .") &&
          decodeError[YearMonth](s""""2008${nonDash}01"""", "expected '-' at: .") &&
          decodeError[YearMonth](s""""+${nonDigit}0000-01"""", "expected digit at: .") &&
          decodeError[YearMonth](s""""+1${nonDigit}000-01"""", "expected digit at: .") &&
          decodeError[YearMonth](s""""+10${nonDigit}00-01"""", "expected digit at: .") &&
          decodeError[YearMonth](s""""+100${nonDigit}0-01"""", "expected digit at: .") &&
          decodeError[YearMonth](s""""+1000${nonDigitOrDash}-01"""", "expected digit at: .") &&
          decodeError[YearMonth](s""""-1000${nonDigitOrDash}-01"""", "expected '-' or digit at: .") &&
          decodeError[YearMonth](s""""+10000${nonDigitOrDash}-01"""", "expected '-' or digit at: .") &&
          decodeError[YearMonth](s""""+100000${nonDigitOrDash}-01"""", "expected '-' or digit at: .") &&
          decodeError[YearMonth](s""""+1000000${nonDigitOrDash}-01"""", "expected '-' or digit at: .") &&
          decodeError[YearMonth](s""""+10000000${nonDigitOrDash}-01"""", "expected '-' or digit at: .") &&
          decodeError[YearMonth](s""""+999999999${nonDash}01"""", "expected '-' at: .") &&
          decodeError[YearMonth](s""""2008-${nonDigit}1"""", "expected digit at: .") &&
          decodeError[YearMonth](s""""2008-0${nonDigit}"""", "expected digit at: .") &&
          decodeError[YearMonth](s""""2008-01${nonDoubleQuotes}"""", """expected '"' at: .""")
        } &&
        decodeError[YearMonth]("""null""", "expected '\"' at: .") &&
        decodeError[YearMonth](""""""", "unexpected end of input at: .") &&
        decodeError[YearMonth](""""2008-01""", "unexpected end of input at: .") &&
        decodeError[YearMonth](""""+1000000000-01"""", "expected '-' at: .") &&
        decodeError[YearMonth](""""-1000000000-01"""", "expected '-' at: .") &&
        decodeError[YearMonth](""""-0000-01"""", "illegal year at: .") &&
        decodeError[YearMonth](""""2008-00"""", "illegal month at: .") &&
        decodeError[YearMonth](""""2008-13"""", "illegal month at: .")
      },
      test("ZoneId") {
        check(genZoneId)(x => roundTrip(x, s""""$x"""")) &&
        decodeError[ZoneId]("""null""", "expected '\"' at: .") &&
        decodeError[ZoneId](""""""", "unexpected end of input at: .") &&
        decodeError[ZoneId]("""""""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""+"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""+1X"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""XXX"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""+10="""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""+10:"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""+10:1"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""+19:10"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""+10:60"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""+10:10:60"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""+18:00:01"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""-18:00:01"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UT+"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UT+10="""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UT+10:"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UT+10:1"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UT+19:10"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UT+10:60"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UT+10:10:60"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UT+18:00:01"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UT-18:00:01"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UTC+"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UTC+10="""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UTC+10:"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UTC+10:1"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UTC+19:10"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UTC+10:60"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UTC+10:10:60"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UTC+18:00:01"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""UTC-18:00:01"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""GMT+"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""GMT+10="""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""GMT+10:"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""GMT+10:1"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""GMT+19:10"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""GMT+10:60"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""GMT+10:10:60"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""GMT+18:00:01"""", "illegal timezone at: .") &&
        decodeError[ZoneId](""""GMT-18:00:01"""", "illegal timezone at: .")
      },
      test("ZoneOffset") {
        check(genZoneOffset)(x => roundTrip(x, s""""$x"""")) &&
        roundTrip(ZoneOffset.ofHours(0), """"Z"""") &&
        check(Gen.char) { ch =>
          val nonDigit               = if (ch >= '0' && ch <= '9') 'X' else ch
          val nonColonOrDoubleQuotes = if (ch == ':' || ch == '"') 'X' else ch
          val nonDoubleQuotes        = if (ch == '"') 'X' else ch
          decodeError[ZoneOffset](s""""+${nonDigit}0:10:10"""", "expected digit at: .") &&
          decodeError[ZoneOffset](s""""+1${nonDigit}:10:10"""", "expected digit at: .") &&
          decodeError[ZoneOffset](s""""+10${nonColonOrDoubleQuotes}10:10"""", """expected ':' or '"' at: .""") &&
          decodeError[ZoneOffset](s""""+10:${nonDigit}0:10"""", "expected digit at: .") &&
          decodeError[ZoneOffset](s""""+10:1${nonDigit}:10"""", "expected digit at: .") &&
          decodeError[ZoneOffset](s""""+10:10${nonColonOrDoubleQuotes}10"""", """expected ':' or '"' at: .""") &&
          decodeError[ZoneOffset](s""""+10:10:${nonDigit}0"""", "expected digit at: .") &&
          decodeError[ZoneOffset](s""""+10:10:1${nonDigit}"""", "expected digit at: .") &&
          decodeError[ZoneOffset](s""""+10:10:10${nonDoubleQuotes}"""", """expected '"' at: .""")
        } &&
        decodeError[ZoneOffset]("""null""", "expected '\"' at: .") &&
        decodeError[ZoneOffset](""""""", "unexpected end of input at: .") &&
        decodeError[ZoneOffset]("""""""", "expected '+' or '-' or 'Z' at: .") &&
        decodeError[ZoneOffset](""""+19:10"""", "illegal timezone offset hour at: .") &&
        decodeError[ZoneOffset](""""+10:60"""", "illegal timezone offset minute at: .") &&
        decodeError[ZoneOffset](""""+10:10:60"""", "illegal timezone offset second at: .") &&
        decodeError[ZoneOffset](""""+18:00:01"""", "illegal timezone offset at: .") &&
        decodeError[ZoneOffset](""""-18:00:01"""", "illegal timezone offset at: .")
      },
      test("ZonedDateTime") {
        check(genZonedDateTime)(x => roundTrip(x, s""""$x"""")) &&
        check(Gen.char) { ch =>
          val nonNumber         = if (ch >= '0' && ch <= '9' || ch == '-' || ch == '+') 'X' else ch
          val nonDigit          = if (ch >= '0' && ch <= '9') 'X' else ch
          val nonDigitOrDash    = if (ch >= '0' && ch <= '9' || ch == '-') 'X' else ch
          val nonDash           = if (ch == '-') 'X' else ch
          val nonT              = if (ch == 'T') 'X' else ch
          val nonColon          = if (ch == ':') 'X' else ch
          val nonColonOrSignOrZ = if (ch == ':' || ch == '-' || ch == '+' || ch == 'Z') 'X' else ch
          decodeError[ZonedDateTime](
            s""""${nonNumber}008-01-20T07:24:33Z[UTC]"""",
            "expected '-' or '+' or digit at: ."
          ) &&
          decodeError[ZonedDateTime](s""""2${nonDigit}08-01-20T07:24:33Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""20${nonDigit}8-01-20T07:24:33Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""200${nonDigit}-01-20T07:24:33Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""2008${nonDash}01-20T07:24:33Z[UTC]"""", "expected '-' at: .") &&
          decodeError[ZonedDateTime](s""""+${nonDigit}0000-01-20T07:24:33Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""+1${nonDigit}000-01-20T07:24:33Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""+10${nonDigit}00-01-20T07:24:33Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""+100${nonDigit}0-01-20T07:24:33Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""+1000${nonDigit}-01-20T07:24:33Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](
            s""""-1000${nonDigitOrDash}-01-20T07:24:33Z[UTC]"""",
            "expected '-' or digit at: ."
          ) &&
          decodeError[ZonedDateTime](
            s""""+10000${nonDigitOrDash}-01-20T07:24:33Z[UTC]"""",
            "expected '-' or digit at: ."
          ) &&
          decodeError[ZonedDateTime](
            s""""+100000${nonDigitOrDash}-01-20T07:24:33Z[UTC]"""",
            "expected '-' or digit at: ."
          ) &&
          decodeError[ZonedDateTime](
            s""""+1000000${nonDigitOrDash}-01-20T07:24:33Z[UTC]"""",
            "expected '-' or digit at: ."
          ) &&
          decodeError[ZonedDateTime](
            s""""+10000000${nonDigitOrDash}-01-20T07:24:33Z[UTC]"""",
            "expected '-' or digit at: ."
          ) &&
          decodeError[ZonedDateTime](s""""+999999999${nonDash}01-20T07:24:33Z[UTC]"""", "expected '-' at: .") &&
          decodeError[ZonedDateTime](s""""2008-${nonDigit}1-20T07:24:33Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""2008-0${nonDigit}-20T07:24:33Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""2008-01${nonDash}20T07:24:33Z[UTC]"""", "expected '-' at: .") &&
          decodeError[ZonedDateTime](s""""2008-01-${nonDigit}0T07:24:33Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""2008-01-2${nonDigit}T07:24:33Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""2008-01-20${nonT}07:24:33Z[UTC]"""", "expected 'T' at: .") &&
          decodeError[ZonedDateTime](s""""2008-01-20T${nonDigit}7:24:33Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""2008-01-20T0${nonDigit}:24:33Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""2008-01-20T07${nonColon}24:33Z[UTC]"""", "expected ':' at: .") &&
          decodeError[ZonedDateTime](s""""2008-01-20T07:${nonDigit}4:33Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""2008-01-20T07:2${nonDigit}:33Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](
            s""""2008-01-20T07:24${nonColonOrSignOrZ}33Z[UTC]"""",
            "expected ':' or '+' or '-' or 'Z' at: ."
          ) &&
          decodeError[ZonedDateTime](s""""2008-01-20T07:24:${nonDigit}3Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""2008-01-20T07:24:3${nonDigit}Z[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""2008-01-20T07:24:33.+${nonDigit}0:10[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""2008-01-20T07:24:33.+1${nonDigit}:10[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""2008-01-20T07:24:33.+10:${nonDigit}0[UTC]"""", "expected digit at: .") &&
          decodeError[ZonedDateTime](s""""2008-01-20T07:24:33.+10:1${nonDigit}[UTC]"""", "expected digit at: .")
        } &&
        decodeError[ZonedDateTime]("""null""", "expected '\"' at: .") &&
        decodeError[ZonedDateTime](""""""", "unexpected end of input at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33Z[UTC]""", "unexpected end of input at: .") &&
        decodeError[ZonedDateTime](""""+1000000000-01-20T07:24:33Z[UTC]"""", "expected '-' at: .") &&
        decodeError[ZonedDateTime](""""-1000000000-01-20T07:24:33Z[UTC]"""", "expected '-' at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33X[UTC]"""", "expected '.' or '+' or '-' or 'Z' at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33ZZ""", """expected '[' or '"' at: .""") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.[UTC]"""", "expected '+' or '-' or 'Z' or digit at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.000[UTC]"""", "expected '+' or '-' or 'Z' or digit at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.123456789X[UTC]"""", "expected '+' or '-' or 'Z' at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.1234567890[UTC]"""", "expected '+' or '-' or 'Z' at: .") &&
        decodeError[ZonedDateTime](""""-0000-01-20T07:24:33Z[UTC]"""", "illegal year at: .") &&
        decodeError[ZonedDateTime](""""2008-00-20T07:24:33Z[UTC]"""", "illegal month at: .") &&
        decodeError[ZonedDateTime](""""2008-13-20T07:24:33Z[UTC]"""", "illegal month at: .") &&
        decodeError[ZonedDateTime](""""2008-01-00T07:24:33Z[UTC]"""", "illegal day at: .") &&
        decodeError[ZonedDateTime](""""2008-01-32T07:24:33Z[UTC]"""", "illegal day at: .") &&
        decodeError[ZonedDateTime](""""2007-02-29T07:24:33Z[UTC]"""", "illegal day at: .") &&
        decodeError[ZonedDateTime](""""2008-02-30T07:24:33Z[UTC]"""", "illegal day at: .") &&
        decodeError[ZonedDateTime](""""2008-03-32T07:24:33Z[UTC]"""", "illegal day at: .") &&
        decodeError[ZonedDateTime](""""2008-04-31T07:24:33Z[UTC]"""", "illegal day at: .") &&
        decodeError[ZonedDateTime](""""2008-05-32T07:24:33Z[UTC]"""", "illegal day at: .") &&
        decodeError[ZonedDateTime](""""2008-06-31T07:24:33Z[UTC]"""", "illegal day at: .") &&
        decodeError[ZonedDateTime](""""2008-07-32T07:24:33Z[UTC]"""", "illegal day at: .") &&
        decodeError[ZonedDateTime](""""2008-08-32T07:24:33Z[UTC]"""", "illegal day at: .") &&
        decodeError[ZonedDateTime](""""2008-09-31T07:24:33Z[UTC]"""", "illegal day at: .") &&
        decodeError[ZonedDateTime](""""2008-10-32T07:24:33Z[UTC]"""", "illegal day at: .") &&
        decodeError[ZonedDateTime](""""2008-11-31T07:24:33Z[UTC]"""", "illegal day at: .") &&
        decodeError[ZonedDateTime](""""2008-12-32T07:24:33Z[UTC]"""", "illegal day at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T24:24:33Z[UTC]"""", "illegal hour at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:60:33Z[UTC]"""", "illegal minute at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:60Z[UTC]"""", "illegal second at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33+[UTC]"""", "expected digit at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33-[UTC]"""", "expected digit at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.+[UTC]"""", "expected digit at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.+1[UTC]"""", "expected digit at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.+10=[UTC]"""", """expected ':' or '[' or '"' at: .""") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.+10:[UTC]"""", "expected digit at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.+10:1[UTC]"""", "expected digit at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.+10:10[]"""", "illegal timezone at: .") &&
        decodeError[ZonedDateTime](
          """"2008-01-20T07:24:33.+10:10=10[UTC]"""",
          """expected ':' or '[' or '"' at: ."""
        ) &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.+10:10:X0[UTC]"""", "expected digit at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.+10:10:1,[UTC]"""", "expected digit at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.+10:10:10[UTC]X"""", """expected '"' at: .""") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.+18:01[UTC]"""", "illegal timezone offset at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.-18:01[UTC]"""", "illegal timezone offset at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.+20:10[UTC]"""", "illegal timezone offset hour at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.+10:60[UTC]"""", "illegal timezone offset minute at: .") &&
        decodeError[ZonedDateTime](""""2008-01-20T07:24:33.+10:10:60[UTC]"""", "illegal timezone offset second at: .")
      },
      test("Currency") {
        check(Gen.currency)(x => roundTrip(x, s""""$x"""")) &&
        decodeError[Currency]("""null""", "expected '\"' at: .") &&
        decodeError[Currency](""""XYZ""", "unexpected end of input at: .") &&
        decodeError[Currency](""""XYZ"""", "illegal currency value at: .")
      },
      test("UUID") {
        check(Gen.uuid)(x => roundTrip(x, s""""$x"""")) &&
        check(Gen.char) { ch =>
          val nonHexDigit     = if (ch >= '0' && ch <= '9' || ch >= 'A' && ch <= 'F' || ch >= 'a' && ch <= 'f') 'X' else ch
          val nonDash         = if (ch == '-') 'X' else ch
          val nonDoubleQuotes = if (ch == '"') 'X' else ch
          decodeError[UUID](s""""${nonHexDigit}0000000-0000-0000-0000-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""0${nonHexDigit}000000-0000-0000-0000-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00${nonHexDigit}00000-0000-0000-0000-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""000${nonHexDigit}0000-0000-0000-0000-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""0000${nonHexDigit}000-0000-0000-0000-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000${nonHexDigit}00-0000-0000-0000-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""000000${nonHexDigit}0-0000-0000-0000-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""0000000${nonHexDigit}-0000-0000-0000-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000${nonDash}0000-0000-0000-000000000000"""", "expected '-' at: .") &&
          decodeError[UUID](s""""00000000-${nonHexDigit}000-0000-0000-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0${nonHexDigit}00-0000-0000-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-00${nonHexDigit}0-0000-0000-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-000${nonHexDigit}-0000-0000-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000${nonDash}0000-0000-000000000000"""", "expected '-' at: .") &&
          decodeError[UUID](s""""00000000-0000-${nonHexDigit}000-0000-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0${nonHexDigit}00-0000-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-00${nonHexDigit}0-0000-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-000${nonHexDigit}-0000-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0000${nonDash}0000-000000000000"""", "expected '-' at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-${nonHexDigit}000-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-0${nonHexDigit}00-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-00${nonHexDigit}0-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-000${nonHexDigit}-000000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-0000${nonDash}000000000000"""", "expected '-' at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-0000-${nonHexDigit}00000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-0000-0${nonHexDigit}0000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-0000-00${nonHexDigit}000000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-0000-000${nonHexDigit}00000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-0000-0000${nonHexDigit}0000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-0000-00000${nonHexDigit}000000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-0000-000000${nonHexDigit}00000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-0000-0000000${nonHexDigit}0000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-0000-00000000${nonHexDigit}000"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-0000-000000000${nonHexDigit}00"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-0000-0000000000${nonHexDigit}0"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-0000-00000000000${nonHexDigit}"""", "expected hex digit at: .") &&
          decodeError[UUID](s""""00000000-0000-0000-0000-000000000000${nonDoubleQuotes}""", """expected '"' at: .""")
        } &&
        decodeError[UUID]("""null""", "expected '\"' at: .")
      }
    ),
    suite("records")(
      test("simple record") {
        roundTrip(
          Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"}"""
        ) &&
        roundTrip(
          Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
          """{
            |  "bl": true,
            |  "b": 1,
            |  "sh": 2,
            |  "i": 3,
            |  "l": 4,
            |  "f": 5.0,
            |  "d": 6.0,
            |  "c": "7",
            |  "s": "VVV"
            |}""".stripMargin,
          readerConfig = ReaderConfig,
          writerConfig = WriterConfig.withIndentionStep(2)
        ) &&
        decode(
          """{"f":5.0,"d":6.0,"c":"7","b":1,"sh":2,"bl":true,"i":3,"s":"VVV","l":4}""",
          Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
        ) &&
        decode(
          """{"f":5.0,"d":6.0,"c":"7","b":1,"sh":2,"bl":true,"i":3,"s":"VVV","l":4},""",
          Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
          readerConfig = ReaderConfig.withCheckForEndOfInput(false)
        ) &&
        decode(
          """{"f":5.0,"d":6.0,"extra1":null,"c":"7","b":1,"sh":2,"bl":true,"i":3,"s":"VVV","l":4,"extra2":[1,2,"\"test\\",[],{}],"extra3":{"l1":{"l2":\"value\\"}},"extra4":false}""",
          Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
        ) &&
        decodeError[Record1]("""null""", "expected '{' at: .") &&
        decodeError[Record1]("""{"""", "unexpected end of input at: .") &&
        decodeError[Record1]("""{"bl":""", "unexpected end of input at: .bl") &&
        decodeError[Record1]("""{"bl":true,"b":1,"sh":2,"i":3,"l":""", "unexpected end of input at: .l") &&
        decodeError[Record1](
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV""",
          "unexpected end of input at: .s"
        ) &&
        decodeError[Record1](
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"},""",
          "expected end of input at: ."
        ) &&
        decodeError[Record1](
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"""",
          "unexpected end of input at: ."
        ) &&
        decodeError[Record1](
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"b":2,"f":5.0,"d":6.0,"c":"7","s":"VVV"}""",
          "duplicated field \"b\" at: ."
        ) &&
        decodeError[Record1](
          """{"bl":true,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"}""",
          "missing required field \"b\" at: ."
        ) &&
        decodeError[Record1](
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7"}""",
          "missing required field \"s\" at: ."
        ) &&
        decodeError[Record1](
          """{"bl":t,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"}""",
          "illegal boolean at: .bl"
        ) &&
        decodeError[Record1](
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"]""",
          "expected '}' or ',' at: ."
        ) &&
        decodeError[Record1](
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV","extra":OMG}""",
          "expected value at: ."
        ) &&
        decodeError[Record1](
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV","rejected":1}""",
          "unexpected field \"rejected\" at: .",
          Schema[Record1].derive(JsonBinaryCodecDeriver.withRejectExtraFields(true))
        )
      },
      test("simple record with fields that have default values") {
        roundTrip(
          Record6(),
          """{}"""
        ) &&
        roundTrip(
          Record6(bl = true, s = "WWW"),
          """{"bl":true,"s":"WWW"}"""
        ) &&
        roundTrip(
          Record6(true, 2: Byte, 3: Short, 4, 5L, 6.0f, 7.0, '8', "WWW"),
          """{"bl":true,"b":2,"sh":3,"i":4,"l":5,"f":6.0,"d":7.0,"c":"8","s":"WWW"}"""
        )
      },
      test("tuple record") {
        type TupleTest = Tuple10[Unit, Boolean, Byte, Short, Int, Long, Float, Double, Char, String]

        implicit val schema: Schema[TupleTest] = Schema.derived

        roundTrip[TupleTest](
          ((), true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
          """[{},true,1,2,3,4,5.0,6.0,"7","VVV"]"""
        ) &&
        decodeError[TupleTest]("""""", "unexpected end of input at: .") &&
        decodeError[TupleTest]("""[{},true,1,2,3,4,5.0,6.0,"7","VVV"],""", "expected end of input at: .") &&
        decodeError[TupleTest]("""{{},true,1,2,3,4,5.0,6.0,"7","VVV"}""", "expected '[' at: .") &&
        decodeError[TupleTest]("""[{},true,1,2,3,4,5.0,6.0,"7","VVV"}""", "expected ']' at: .") &&
        decodeError[TupleTest]("""[{},true,1,2,3,4,5.0,6.0,7,"VVV"}""", "expected '\"' at: ._9") &&
        decodeError[TupleTest]("""[{},true,1,2,3,4,5.0,6.0]""", "expected ',' at: .") &&
        decodeError[TupleTest]("""[{},true,1,2,3,4,5.0,6.0,"7"]""", "expected ',' at: .")
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
      test("big record with up to 128 fields") {
        roundTrip(BigProduct(f00 = true, f69 = 1), """{"f00":true,"f69":1}""") &&
        roundTrip(BigProduct(f00 = true, f63 = Some(2), f69 = 1), """{"f00":true,"f63":2,"f69":1}""") &&
        roundTrip(
          BigProduct(f00 = true, f67 = Some(BigProduct(f00 = false, f69 = 2)), f69 = 1),
          """{"f00":true,"f67":{"f00":false,"f69":2},"f69":1}"""
        ) &&
        decodeError[BigProduct]("""{"f69":1}""", "missing required field \"f00\" at: .") &&
        decodeError[BigProduct]("""{"f00":true}""", "missing required field \"f69\" at: .") &&
        decodeError[BigProduct]("""{"f00":true,"f69":1,"f69":1}""", "duplicated field \"f69\" at: .") &&
        decodeError[BigProduct](
          """{"f00":true,"f67":{"f69":2},"f69":1}""",
          "missing required field \"f00\" at: .f67.when[Some].value"
        )
      },
      test("record with transient field") {
        encode(BigProduct(f00 = true, f66 = Some(2), f69 = 1), """{"f00":true,"f69":1}""") &&
        decode("""{"f00":true,"f66":2,"f69":1}""", BigProduct(f00 = true, f66 = Some(1), f69 = 1))
      },
      test("record with array field") {
        roundTrip(Arrays(Array()), """{}""") &&
        roundTrip(Arrays(Array("VVV", "WWW")), """{"xs":["VVV","WWW"]}""")
      },
      test("recursive record") {
        roundTrip(
          Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))),
          """{"i":1,"ln":[{"i":2,"ln":[{"i":3}]}]}"""
        )
      },
      test("record with unit and optional fields") {
        roundTrip(Record4((), Some("VVV")), """{"hіdden":{},"optKеy":"VVV"}""") &&
        roundTrip(Record4((), None), """{"hіdden":{}}""")
      },
      test("record with custom codec that enforces encoding of fields with empty option values") {
        val codec = Schema[Record4].derive(JsonBinaryCodecDeriver.withTransientNone(false))
        roundTrip(Record4((), Some("VVV")), """{"hіdden":{},"optKеy":"VVV"}""", codec) &&
        roundTrip(Record4((), None), """{"hіdden":{},"optKеy":null}""", codec)
      },
      test("record with custom codec that require decoding of fields with empty option values") {
        val codec = Schema[Record4].derive(JsonBinaryCodecDeriver.withRequireOptionFields(true))
        roundTrip(Record4((), Some("VVV")), """{"hіdden":{},"optKеy":"VVV"}""", codec) &&
        roundTrip(Record4((), None), """{"hіdden":{},"optKеy":null}""", codec) &&
        decodeError("""{"hіdden":{}}""", "missing required field \"optKеy\" at: .", codec)
      },
      test("record with custom codecs of different field mapping") {
        roundTrip(
          CamelPascalSnakeKebabCases(1, 2, 3, 4, 5, 6, 7, 8),
          """{"camelCase":1,"PascalCase":2,"snake_case":3,"kebab-case":4,"camel1":5,"Pascal1":6,"snake_1":7,"kebab-1":8}"""
        ) &&
        roundTrip(
          CamelPascalSnakeKebabCases(1, 2, 3, 4, 5, 6, 7, 8),
          """{"CAMELCASE":1,"PASCALCASE":2,"SNAKE_CASE":3,"KEBAB-CASE":4,"CAMEL1":5,"PASCAL1":6,"SNAKE_1":7,"KEBAB-1":8}""",
          Schema[CamelPascalSnakeKebabCases].derive(JsonBinaryCodecDeriver.withFieldNameMapper(Custom(_.toUpperCase)))
        ) &&
        roundTrip(
          CamelPascalSnakeKebabCases(1, 2, 3, 4, 5, 6, 7, 8),
          """{"camel_case":1,"pascal_case":2,"snake_case":3,"kebab_case":4,"camel1":5,"pascal1":6,"snake_1":7,"kebab_1":8}""",
          Schema[CamelPascalSnakeKebabCases].derive(JsonBinaryCodecDeriver.withFieldNameMapper(SnakeCase))
        ) &&
        roundTrip(
          CamelPascalSnakeKebabCases(1, 2, 3, 4, 5, 6, 7, 8),
          """{"camel-case":1,"pascal-case":2,"snake-case":3,"kebab-case":4,"camel1":5,"pascal1":6,"snake-1":7,"kebab-1":8}""",
          Schema[CamelPascalSnakeKebabCases].derive(JsonBinaryCodecDeriver.withFieldNameMapper(KebabCase))
        ) &&
        roundTrip(
          CamelPascalSnakeKebabCases(1, 2, 3, 4, 5, 6, 7, 8),
          """{"CamelCase":1,"PascalCase":2,"SnakeCase":3,"KebabCase":4,"Camel1":5,"Pascal1":6,"Snake1":7,"Kebab1":8}""",
          Schema[CamelPascalSnakeKebabCases].derive(JsonBinaryCodecDeriver.withFieldNameMapper(PascalCase))
        ) &&
        roundTrip(
          CamelPascalSnakeKebabCases(1, 2, 3, 4, 5, 6, 7, 8),
          """{"camelCase":1,"pascalCase":2,"snakeCase":3,"kebabCase":4,"camel1":5,"pascal1":6,"snake1":7,"kebab1":8}""",
          Schema[CamelPascalSnakeKebabCases].derive(JsonBinaryCodecDeriver.withFieldNameMapper(CamelCase))
        )
      },
      test("record with a custom codec for primitives injected by optic and field renaming using modifier overriding") {
        val codec1 = Record1.schema
          .deriving(JsonBinaryCodecDeriver)
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
        val codec2 = Record5.schema
          .deriving(JsonBinaryCodecDeriver.withFieldNameMapper(SnakeCase))
          .modifier(Record5.bigDecimal, Modifier.rename("bigDec"))
          .instance(
            Record5.bigInt,
            new JsonBinaryCodec[BigInt]() { // stringifies BigInt values
              def decodeValue(in: JsonReader, default: BigInt): BigInt = in.readStringAsBigInt(default, Int.MaxValue)

              def encodeValue(x: BigInt, out: JsonWriter): Unit = out.writeValAsString(x)

              override val nullValue: BigInt = BigInt(0)
            }
          )
          .instance(
            Record5.bigDecimal,
            new JsonBinaryCodec[BigDecimal]() { // stringifies BigDecimal values
              def decodeValue(in: JsonReader, default: BigDecimal): BigDecimal =
                in.readStringAsBigDecimal(default, MathContext.UNLIMITED, Int.MaxValue, Int.MaxValue)

              def encodeValue(x: BigDecimal, out: JsonWriter): Unit = out.writeValAsString(x)

              override val nullValue: BigDecimal = BigDecimal(0)
            }
          )
          .derive
        val bigIntStr = "9" * 400
        roundTrip(
          Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
          """{"bl":"true","b":"1","sh":"2","i":"3","l":"4","f":"5.0","d":"6.0","c":55,"s":"VVV"}""",
          codec1
        ) &&
        encode(
          Record5(BigInt(12345), BigDecimal("1E-2147483647")),
          """{"big_int":"12345","bigDec":"1E-2147483647"}""",
          codec2
        ) &&
        roundTrip(
          Record5(BigInt(bigIntStr), BigDecimal(bigIntStr)),
          s"""{"big_int":"$bigIntStr","bigDec":"$bigIntStr"}""",
          codec2
        ) &&
        decode("""{"big_int":null,"bigDec":null}""", Record5(BigInt(0), BigDecimal(0)), codec2) &&
        decodeError("""{"big_int":null}""", "missing required field \"bigDec\" at: .", codec2) &&
        decodeError("""{"big_int":null,"bigDec":1}""", "expected '\"' or null at: .bigDecimal", codec2)
      },
      test("record with field name aliases") {
        val codec = Record5.schema
          .deriving(JsonBinaryCodecDeriver)
          .modifier(Record5.bigDecimal, Modifier.alias("bd"))
          .modifier(Record5.bigInt, Modifier.alias("bi"))
          .derive
        decode("""{"bi":1,"bd":2.0}""", Record5(BigInt(1), BigDecimal(2.0)), codec)
      },
      test("record with duplicated field names") {
        assert(scala.util.Try {
          Record5.schema
            .deriving(JsonBinaryCodecDeriver)
            .modifier(Record5.bigDecimal, Modifier.rename("bigInt"))
            .derive
        }.toEither)(isLeft(hasError("Cannot derive codec - duplicated name detected: 'bigInt'"))) &&
        assert(scala.util.Try {
          Record5.schema
            .deriving(JsonBinaryCodecDeriver)
            .modifier(Record5.bigDecimal, Modifier.alias("bigInt"))
            .derive
        }.toEither)(isLeft(hasError("Cannot derive codec - duplicated name detected: 'bigInt'")))
      },
      test("record with an `AnyVal` field that uses a custom schema") {
        roundTrip(Counter(PosInt.applyUnsafe(1)), """{"value":1}""") &&
        decodeError[Counter]("""{"value":-1}""", "Expected positive value at: .value.wrapped")
      },
      test("record with fields that have default values and custom codecs") {
        val codec1 = Schema[Record6]
          .deriving(JsonBinaryCodecDeriver)
          .instance(
            Record6.bl,
            new JsonBinaryCodec[Boolean](JsonBinaryCodec.booleanType) { // stringifies boolean values
              def decodeValue(in: JsonReader, default: Boolean): Boolean = in.readStringAsBoolean()

              def encodeValue(x: Boolean, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            Record6.b,
            new JsonBinaryCodec[Byte](JsonBinaryCodec.byteType) { // stringifies byte values
              def decodeValue(in: JsonReader, default: Byte): Byte = in.readStringAsByte()

              def encodeValue(x: Byte, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            Record6.sh,
            new JsonBinaryCodec[Short](JsonBinaryCodec.shortType) { // stringifies short values
              def decodeValue(in: JsonReader, default: Short): Short = in.readStringAsShort()

              def encodeValue(x: Short, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            Record6.i,
            new JsonBinaryCodec[Int](JsonBinaryCodec.intType) { // stringifies int values
              def decodeValue(in: JsonReader, default: Int): Int = in.readStringAsInt()

              def encodeValue(x: Int, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            Record6.l,
            new JsonBinaryCodec[Long](JsonBinaryCodec.longType) { // stringifies long values
              def decodeValue(in: JsonReader, default: Long): Long = in.readStringAsLong()

              def encodeValue(x: Long, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            Record6.f,
            new JsonBinaryCodec[Float](JsonBinaryCodec.floatType) { // stringifies float values
              def decodeValue(in: JsonReader, default: Float): Float = in.readStringAsFloat()

              def encodeValue(x: Float, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            Record6.d,
            new JsonBinaryCodec[Double](JsonBinaryCodec.doubleType) { // stringifies double values
              def decodeValue(in: JsonReader, default: Double): Double = in.readStringAsDouble()

              def encodeValue(x: Double, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            Record6.c,
            new JsonBinaryCodec[Char](JsonBinaryCodec.charType) { // expecting char code numbers (not one-char strings)
              def decodeValue(in: JsonReader, default: Char): Char = in.readInt().toChar

              def encodeValue(x: Char, out: JsonWriter): Unit = out.writeVal(x.toInt)
            }
          )
          .derive
        val codec2 = Schema[Record6].derive(JsonBinaryCodecDeriver.withTransientDefaultValue(false))
        val codec3 = Schema[Record6].derive(JsonBinaryCodecDeriver.withRequireDefaultValueFields(true))
        roundTrip(
          Record6(true, 2.toByte, 3.toShort, 4, 5L, 6.0f, 7.0, '8', "WWW"),
          """{"bl":"true","b":"2","sh":"3","i":"4","l":"5","f":"6.0","d":"7.0","c":56,"s":"WWW"}""",
          codec1
        ) &&
        roundTrip(
          Record6(),
          """{"bl":false,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"}""",
          codec2
        ) &&
        roundTrip(
          Record6(),
          """{"bl":false,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"}""",
          codec3
        ) &&
        decodeError("""{}""", "missing required field \"bl\" at: .", codec3) &&
        decodeError("""{"bl":false}""", "missing required field \"b\" at: .", codec3) &&
        decodeError("""{"bl":false,"s":"VVV"}""", "missing required field \"b\" at: .", codec3)
      },
      test("tuple record with a custom codec for primitives injected by type names") {
        val codec = Schema
          .derived[Tuple10[Unit, Boolean, Byte, Short, Int, Long, Float, Double, Char, String]]
          .deriving(JsonBinaryCodecDeriver)
          .instance(
            TypeId.boolean,
            new JsonBinaryCodec[Boolean](JsonBinaryCodec.booleanType) { // stringifies boolean values
              def decodeValue(in: JsonReader, default: Boolean): Boolean = in.readStringAsBoolean()

              def encodeValue(x: Boolean, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            TypeId.byte,
            new JsonBinaryCodec[Byte](JsonBinaryCodec.byteType) { // stringifies byte values
              def decodeValue(in: JsonReader, default: Byte): Byte = in.readStringAsByte()

              def encodeValue(x: Byte, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            TypeId.short,
            new JsonBinaryCodec[Short](JsonBinaryCodec.shortType) { // stringifies short values
              def decodeValue(in: JsonReader, default: Short): Short = in.readStringAsShort()

              def encodeValue(x: Short, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            TypeId.int,
            new JsonBinaryCodec[Int](JsonBinaryCodec.intType) { // stringifies int values
              def decodeValue(in: JsonReader, default: Int): Int = in.readStringAsInt()

              def encodeValue(x: Int, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            TypeId.long,
            new JsonBinaryCodec[Long](JsonBinaryCodec.longType) { // stringifies long values
              def decodeValue(in: JsonReader, default: Long): Long = in.readStringAsLong()

              def encodeValue(x: Long, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            TypeId.float,
            new JsonBinaryCodec[Float](JsonBinaryCodec.floatType) { // stringifies float values
              def decodeValue(in: JsonReader, default: Float): Float = in.readStringAsFloat()

              def encodeValue(x: Float, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            TypeId.double,
            new JsonBinaryCodec[Double](JsonBinaryCodec.doubleType) { // stringifies double values
              def decodeValue(in: JsonReader, default: Double): Double = in.readStringAsDouble()

              def encodeValue(x: Double, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .instance(
            TypeId.char,
            new JsonBinaryCodec[Char](JsonBinaryCodec.charType) { // expecting char code numbers (not one-char strings)
              def decodeValue(in: JsonReader, default: Char): Char = in.readInt().toChar

              def encodeValue(x: Char, out: JsonWriter): Unit = out.writeVal(x.toInt)
            }
          )
          .derive
        roundTrip(
          ((), true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
          """[{},"true","1","2","3","4","5.0","6.0",55,"VVV"]""",
          codec
        )
      },
      test("record with a custom codec for primitives injected by type name") {
        val codec = Record3.schema
          .deriving(JsonBinaryCodecDeriver)
          .instance(
            TypeId.currency,
            new JsonBinaryCodec[Currency]() { // decode null values as the default one ("USD")
              def decodeValue(in: JsonReader, default: Currency): Currency =
                if (in.isNextToken('n')) {
                  in.rollbackToken()
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
          Record3(UserId(1234567890123456789L), Email("backup@gmail.com"), Currency.getInstance("USD"), Map.empty),
          """{"userId":1234567890123456789,"email":"backup@gmail.com","currency":"USD"}""",
          codec
        ) &&
        decode(
          """{"userId":1234567890123456789,"email":"backup@gmail.com","currency":null}""",
          Record3(UserId(1234567890123456789L), Email("backup@gmail.com"), Currency.getInstance("USD"), Map.empty),
          codec
        )
      },
      test("record with a custom codec for unit injected by optic") {
        val codec = Record4.schema
          .deriving(JsonBinaryCodecDeriver)
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
          .deriving(JsonBinaryCodecDeriver)
          .instance(
            Record4.optKey,
            new JsonBinaryCodec[Option[String]]() { // more efficient decoding than with derived by default
              override def decodeValue(in: JsonReader, default: Option[String]): Option[String] =
                if (in.isNextToken('n')) {
                  in.rollbackToken()
                  in.readNullOrError(default, "expected null")
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
        roundTrip(Record4((), Some("VVV")), """{"hіdden":{},"optKеy":"VVV"}""", codec) &&
        roundTrip(Record4((), None), """{"hіdden":{}}""", codec)
      },
      test("record with a custom codec for nested record injected by optic") {
        val codec1 =
          new JsonBinaryCodec[Record1]() { // allows null values which are prohibited in codecs derived by default
            private val codec = Record1.schema.derive(JsonBinaryCodecDeriver)

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
          .deriving(JsonBinaryCodecDeriver)
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
      test("record with a custom codec for nested primitives injected by type name and by optic") {
        val codec = Record2.schema
          .deriving(JsonBinaryCodecDeriver)
          .instance(
            TypeId.int,
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
      test("record with a custom codec for nested primitives injected by type and term name") {
        val stringifyIntCodec = new JsonBinaryCodec[Int](JsonBinaryCodec.intType) {
          def decodeValue(in: JsonReader, default: Int): Int = in.readStringAsInt()

          def encodeValue(x: Int, out: JsonWriter): Unit = out.writeValAsString(x)
        }
        val codec = Record2.schema
          .deriving(JsonBinaryCodecDeriver)
          .instance(Record1.schema.reflect.typeId, "i", stringifyIntCodec)
          .derive
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(false, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "WWW")
          ),
          // "i" fields are stringified, other fields remain unchanged
          """{"r1_1":{"bl":true,"b":1,"sh":2,"i":"3","l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"},"r1_2":{"bl":false,"b":1,"sh":2,"i":"3","l":4,"f":5.0,"d":6.0,"c":"7","s":"WWW"}}""",
          codec
        )
      },
      test("record with type and term name override taking priority over type-only override") {
        val stringifyIntCodec = new JsonBinaryCodec[Int](JsonBinaryCodec.intType) {
          def decodeValue(in: JsonReader, default: Int): Int = in.readStringAsInt()

          def encodeValue(x: Int, out: JsonWriter): Unit = out.writeValAsString(x)
        }
        val doubleIntCodec = new JsonBinaryCodec[Int](JsonBinaryCodec.intType) {
          def decodeValue(in: JsonReader, default: Int): Int = in.readDouble().toInt

          def encodeValue(x: Int, out: JsonWriter): Unit = out.writeVal(x.toDouble)
        }
        val codec = Record2.schema
          .deriving(JsonBinaryCodecDeriver)
          .instance(TypeId.int, doubleIntCodec)
          .instance(Record1.schema.reflect.typeId, "i", stringifyIntCodec)
          .derive
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(false, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "WWW")
          ),
          // "i" fields use type+termName override (stringified "3") instead of type-only override (would be 3.0)
          """{"r1_1":{"bl":true,"b":1,"sh":2,"i":"3","l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"},"r1_2":{"bl":false,"b":1,"sh":2,"i":"3","l":4,"f":5.0,"d":6.0,"c":"7","s":"WWW"}}""",
          codec
        )
      },
      test("record with optic override taking priority over type and term name override") {
        val stringifyIntCodec = new JsonBinaryCodec[Int](JsonBinaryCodec.intType) {
          def decodeValue(in: JsonReader, default: Int): Int = in.readStringAsInt()

          def encodeValue(x: Int, out: JsonWriter): Unit = out.writeValAsString(x)
        }
        val doubleIntCodec = new JsonBinaryCodec[Int](JsonBinaryCodec.intType) {
          def decodeValue(in: JsonReader, default: Int): Int = in.readDouble().toInt

          def encodeValue(x: Int, out: JsonWriter): Unit = out.writeVal(x.toDouble)
        }
        val codec = Record2.schema
          .deriving(JsonBinaryCodecDeriver)
          .instance(Record2.r1_2_i, doubleIntCodec)
          .instance(Record1.schema.reflect.typeId, "i", stringifyIntCodec)
          .derive
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(false, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "WWW")
          ),
          // r1_1.i uses type+termName override (stringified), r1_2.i uses optic override (double)
          """{"r1_1":{"bl":true,"b":1,"sh":2,"i":"3","l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"},"r1_2":{"bl":false,"b":1,"sh":2,"i":3.0,"l":4,"f":5.0,"d":6.0,"c":"7","s":"WWW"}}""",
          codec
        )
      },
      test("record with a custom codec for a nested record injected by type and term name") {
        val nullableRecord1Codec = new JsonBinaryCodec[Record1]() {
          private val codec = Record1.schema.derive(JsonBinaryCodecDeriver)

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
        val codec = Record2.schema
          .deriving(JsonBinaryCodecDeriver)
          .instance(Record2.schema.reflect.typeId, "r1_2", nullableRecord1Codec)
          .derive
        // r1_2 uses the nullable override, r1_1 uses the default (non-nullable) codec
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            null
          ),
          """{"r1_1":{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"},"r1_2":null}""",
          codec
        )
      },
      test("record with a custom codec for a nested sequence injected by type and term name") {
        val emptyListCodec = new JsonBinaryCodec[List[Recursive]]() {
          def decodeValue(in: JsonReader, default: List[Recursive]): List[Recursive] = {
            in.skip()
            Nil
          }

          def encodeValue(x: List[Recursive], out: JsonWriter): Unit = {
            out.writeArrayStart()
            out.writeArrayEnd()
          }
        }
        val codec = Recursive.schema
          .deriving(JsonBinaryCodecDeriver.withTransientEmptyCollection(false).withRequireCollectionFields(true))
          .instance(Recursive.schema.reflect.typeId, "ln", emptyListCodec)
          .derive
        encode(
          Recursive(42, List(Recursive(1, Nil))),
          """{"i":42,"ln":[]}""",
          codec
        )
      },
      test("record with a custom codec for a nested variant injected by type and term name") {
        implicit val catSchema: Schema[Cat] = Schema.derived
        val fixedAgeCodec                   = new JsonBinaryCodec[Either[String, Int]]() {
          def decodeValue(in: JsonReader, default: Either[String, Int]): Either[String, Int] = Right(in.readInt())

          def encodeValue(x: Either[String, Int], out: JsonWriter): Unit = x match {
            case Right(n) => out.writeVal(n)
            case Left(_)  => out.writeVal(0)
          }
        }
        val codec = catSchema
          .deriving(JsonBinaryCodecDeriver)
          .instance(catSchema.reflect.typeId, "age", fixedAgeCodec)
          .derive
        roundTrip(Cat("Misty", Right(7), 9), """{"name":"Misty","age":7,"livesLeft":9}""", codec)
      },
      test("record with a custom codec for a nested map injected by type and term name") {
        val fixedMapCodec = new JsonBinaryCodec[Map[Currency, String]]() {
          def decodeValue(in: JsonReader, default: Map[Currency, String]): Map[Currency, String] = {
            in.skip()
            Map(Currency.getInstance("EUR") -> "W")
          }

          def encodeValue(x: Map[Currency, String], out: JsonWriter): Unit = {
            out.writeObjectStart()
            out.writeKey("EUR")
            out.writeVal("W")
            out.writeObjectEnd()
          }

          override val nullValue: Map[Currency, String] = Map.empty
        }
        val codec = Record3.schema
          .deriving(JsonBinaryCodecDeriver)
          .instance(Record3.schema.reflect.typeId, "accounts", fixedMapCodec)
          .derive
        encode(
          Record3(
            UserId(42L),
            Email("a@b.com"),
            Currency.getInstance("USD"),
            Map(Currency.getInstance("USD") -> "V")
          ),
          """{"userId":42,"email":"a@b.com","currency":"USD","accounts":{"EUR":"W"}}""",
          codec
        )
      },
      test("record with a custom codec for a nested wrapper injected by type and term name") {
        val stringifiedUserIdCodec = new JsonBinaryCodec[UserId]() {
          def decodeValue(in: JsonReader, default: UserId): UserId = UserId(in.readStringAsLong())

          def encodeValue(x: UserId, out: JsonWriter): Unit = out.writeValAsString(x.value)
        }
        val codec = Record3.schema
          .deriving(JsonBinaryCodecDeriver)
          .instance(Record3.schema.reflect.typeId, "userId", stringifiedUserIdCodec)
          .derive
        roundTrip(
          Record3(
            UserId(42L),
            Email("a@b.com"),
            Currency.getInstance("USD"),
            Map(Currency.getInstance("USD") -> "V")
          ),
          """{"userId":"42","email":"a@b.com","currency":"USD","accounts":{"USD":"V"}}""",
          codec
        )
      },
      test("record with a custom codec for a nested dynamic value injected by type and term name") {
        val nullDynamicCodec = new JsonBinaryCodec[DynamicValue]() {
          def decodeValue(in: JsonReader, default: DynamicValue): DynamicValue = {
            in.skip()
            DynamicValue.Null
          }

          def encodeValue(x: DynamicValue, out: JsonWriter): Unit = out.writeNull()

          override val nullValue: DynamicValue = DynamicValue.Null
        }
        val codec = Dynamic.schema
          .deriving(JsonBinaryCodecDeriver)
          .instance(Dynamic.schema.reflect.typeId, "primitive", nullDynamicCodec)
          .derive
        encode(
          Dynamic(DynamicValue.Primitive(PrimitiveValue.Int(1)), DynamicValue.Primitive(PrimitiveValue.Int(1))),
          """{"primitive":null,"record":1}""",
          codec
        )
      },
      test("record with field renamed by type and term name modifier") {
        val codec = Record2.schema
          .deriving(JsonBinaryCodecDeriver)
          .modifier(Record1.schema.reflect.typeId, "i", Modifier.rename("int"))
          .derive
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(false, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "WWW")
          ),
          """{"r1_1":{"bl":true,"b":1,"sh":2,"int":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"},"r1_2":{"bl":false,"b":1,"sh":2,"int":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"WWW"}}""",
          codec
        )
      },
      test("record with reflect modifier by type") {
        val codec = Record1.schema
          .deriving(JsonBinaryCodecDeriver)
          .modifier(Record1.schema.reflect.typeId, Modifier.config("json.type", "custom"))
          .derive
        roundTrip(
          Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"}""",
          codec
        )
      },
      test("record with reflect modifier by optic") {
        val codec = Record1.schema
          .deriving(JsonBinaryCodecDeriver)
          .modifier(Record1.i, Modifier.config("json.format", "custom"))
          .derive
        roundTrip(
          Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
          """{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"}""",
          codec
        )
      },
      test("record with reflect modifiers by both optic and type") {
        val codec = Record2.schema
          .deriving(JsonBinaryCodecDeriver)
          .modifier(Record2.r1_1_i, Modifier.config("json.format", "custom"))
          .modifier(TypeId.int, Modifier.config("json.type", "number"))
          .derive
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(false, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "WWW")
          ),
          """{"r1_1":{"bl":true,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"VVV"},"r1_2":{"bl":false,"b":1,"sh":2,"i":3,"l":4,"f":5.0,"d":6.0,"c":"7","s":"WWW"}}""",
          codec
        )
      },
      test("record with a custom codec for nested record injected by type name") {
        val codec = Record2.schema
          .deriving(JsonBinaryCodecDeriver)
          .instance(
            Record1.schema.reflect.typeId,
            new JsonBinaryCodec[Record1]() { // allows null values which are prohibited for codecs derived by default
              private val codec = Record1.schema.derive(JsonBinaryCodecDeriver)

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
          .deriving(JsonBinaryCodecDeriver.withTransientEmptyCollection(false).withRequireCollectionFields(true))
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
        ) &&
        decodeError(
          """{"i":"1","ln":[{"i":"2","ln":[{"i":"3"}]}]}""",
          "missing required field \"ln\" at: .ln.at(0).ln.at(0)",
          codec
        )
      },
      test("decode and encode record fields as raw untouched bytes using a custom codec") {
        case class Message(param1: String, param2: String, payload: RawVal, param3: String)

        object Message extends CompanionOptics[Message] {
          implicit val schema: Schema[Message] = Schema.derived

          val payload: Lens[Message, RawVal] = $(_.payload)
        }

        val rawVal = RawVal("""{"x":[-1.0,1,4.0E20],"y":{"xx":true,"yy":false,"zz":null},"z":"Z"}""")
        val codec  = Schema[Message].deriving(JsonBinaryCodecDeriver).instance(Message.payload, RawVal.codec).derive
        assertTrue(rawVal.isValid) &&
        roundTrip(
          Message("A", "B", rawVal, "C"),
          """{"param1":"A","param2":"B","payload":{"x":[-1.0,1,4.0E20],"y":{"xx":true,"yy":false,"zz":null},"z":"Z"},"param3":"C"}""",
          codec
        )
      }
    ),
    suite("sequences")(
      test("primitive values") {
        implicit val arrayOfUnitSchema: Schema[Array[Unit]]         = Schema.derived
        implicit val arrayOfBooleanSchema: Schema[Array[Boolean]]   = Schema.derived
        implicit val arrayOfByteSchema: Schema[Array[Byte]]         = Schema.derived
        implicit val arrayOfShortSchema: Schema[Array[Short]]       = Schema.derived
        implicit val arrayOfCharSchema: Schema[Array[Char]]         = Schema.derived
        implicit val arrayOfIntSchema: Schema[Array[Int]]           = Schema.derived
        implicit val arrayOfFloatSchema: Schema[Array[Float]]       = Schema.derived
        implicit val arrayOfLongSchema: Schema[Array[Long]]         = Schema.derived
        implicit val arrayOfDoubleSchema: Schema[Array[Double]]     = Schema.derived
        implicit val arraySeqOfFloatSchema: Schema[ArraySeq[Float]] = Schema.derived

        decode("""null""", List.empty[Int]) &&
        roundTrip(List.empty[Int], """[]""") &&
        roundTrip(Array[Unit](), """[]""") &&
        roundTrip(Array[Unit]((), (), ()), """[{},{},{}]""") &&
        roundTrip(Array[Boolean](), """[]""") &&
        roundTrip(Array[Boolean](true, false, true), """[true,false,true]""") &&
        decodeError[Array[Boolean]]("1", "expected '[' or null at: .") &&
        decodeError[Array[Boolean]]("[true,false,true,false}", "expected ']' or ',' at: .") &&
        decodeError[Array[Boolean]]("[true,false,true,false", "unexpected end of input at: .at(3)") &&
        roundTrip(Array[Byte](), """[]""") &&
        roundTrip(Array[Byte](1: Byte, 2: Byte, 3: Byte), """[1,2,3]""") &&
        decodeError[Array[Byte]]("true", "expected '[' or null at: .") &&
        decodeError[Array[Byte]]("[1,2,3,4}", "expected ']' or ',' at: .") &&
        decodeError[Array[Byte]]("[1,2,3,4", "unexpected end of input at: .at(3)") &&
        roundTrip(Array[Short](), """[]""") &&
        roundTrip(Array[Short](1: Short, 2: Short, 3: Short), """[1,2,3]""") &&
        decodeError[Array[Short]]("true", "expected '[' or null at: .") &&
        decodeError[Array[Short]]("[1,2,3,4}", "expected ']' or ',' at: .") &&
        decodeError[Array[Short]]("[1,2,3,4", "unexpected end of input at: .at(3)") &&
        roundTrip(Array[Char](), """[]""") &&
        roundTrip(Array[Char]('1', '2', '3'), """["1","2","3"]""") &&
        decodeError[Array[Char]]("true", "expected '[' or null at: .") &&
        decodeError[Array[Char]]("""["1","2","3","4"}""", "expected ']' or ',' at: .") &&
        decodeError[Array[Char]]("""["1","2","3","4""", "unexpected end of input at: .at(3)") &&
        roundTrip(Array[Int](), """[]""") &&
        roundTrip(Array[Int](1, 2, 3), """[1,2,3]""") &&
        decodeError[Array[Int]]("true", "expected '[' or null at: .") &&
        decodeError[Array[Int]]("[1,2,3,4}", "expected ']' or ',' at: .") &&
        decodeError[Array[Int]]("[1,2,3,4", "unexpected end of input at: .at(3)") &&
        roundTrip(Array[Float](), """[]""") &&
        roundTrip(Array[Float](1.0f, 2.0f, 3.0f), """[1.0,2.0,3.0]""") &&
        decodeError[Array[Float]]("true", "expected '[' or null at: .") &&
        decodeError[Array[Float]]("[1.0,2.0,3.0,4.0}", "expected ']' or ',' at: .") &&
        decodeError[Array[Float]]("[1.0,2.0,3.0,4.0", "unexpected end of input at: .at(3)") &&
        roundTrip(Array[Long](), """[]""") &&
        roundTrip(Array[Long](1L, 2L, 3L), """[1,2,3]""") &&
        decodeError[Array[Long]]("true", "expected '[' or null at: .") &&
        decodeError[Array[Long]]("[1,2,3,4}", "expected ']' or ',' at: .") &&
        decodeError[Array[Long]]("[1,2,3,4", "unexpected end of input at: .at(3)") &&
        roundTrip(Array[Double](), """[]""") &&
        roundTrip(Array[Double](1.0, 2.0, 3.0), """[1.0,2.0,3.0]""") &&
        decodeError[Array[Double]]("true", "expected '[' or null at: .") &&
        decodeError[Array[Double]]("[1.0,2.0,3.0,4.0}", "expected ']' or ',' at: .") &&
        decodeError[Array[Double]]("[1.0,2.0,3.0,4.0", "unexpected end of input at: .at(3)") &&
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
        roundTrip(List(Month.MAY, Month.JUNE, Month.JULY), """["MAY","JUNE","JULY"]""") &&
        roundTrip(
          List(UUID.fromString("17149f63-783d-4670-b360-3be82b1420e7")),
          """["17149f63-783d-4670-b360-3be82b1420e7"]"""
        ) &&
        roundTrip(
          List(1, 2, 3),
          """[
            |  1,
            |  2,
            |  3
            |]""".stripMargin,
          readerConfig = ReaderConfig,
          writerConfig = WriterConfig.withIndentionStep(2)
        ) &&
        roundTrip(
          List("1", "2", "3"),
          """[
            |  "1",
            |  "2",
            |  "3"
            |]""".stripMargin,
          readerConfig = ReaderConfig,
          writerConfig = WriterConfig.withIndentionStep(2)
        ) &&
        roundTrip(
          List(Month.MAY, Month.JUNE, Month.JULY),
          """[
            |  "MAY",
            |  "JUNE",
            |  "JULY"
            |]""".stripMargin,
          readerConfig = ReaderConfig,
          writerConfig = WriterConfig.withIndentionStep(2)
        ) &&
        decodeError[List[Int]]("", "unexpected end of input at: .") &&
        decodeError[List[Int]]("true", "expected '[' or null at: .") &&
        decodeError[List[Int]]("[1,2,3,4}", "expected ']' or ',' at: .") &&
        decodeError[List[Int]]("[1,2,3,4", "unexpected end of input at: .at(3)") &&
        decodeError[List[Int]]("""[1,2,3,null]""", "illegal number at: .at(3)")
      } @@ TestAspect.exceptJS, // TODO: Fix Scala 3.5 + Scala.js incompatibility
      test("primitive values with custom codecs") {
        val codec1 = Schema
          .derived[Array[Boolean]]
          .deriving(JsonBinaryCodecDeriver)
          .instance(
            TypeId.boolean,
            new JsonBinaryCodec[Boolean](JsonBinaryCodec.booleanType) { // stringifies boolean values
              def decodeValue(in: JsonReader, default: Boolean): Boolean = in.readStringAsBoolean()

              def encodeValue(x: Boolean, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .derive
        val codec2 = Schema
          .derived[Array[Byte]]
          .deriving(JsonBinaryCodecDeriver)
          .instance(
            TypeId.byte,
            new JsonBinaryCodec[Byte](JsonBinaryCodec.byteType) { // stringifies byte values
              def decodeValue(in: JsonReader, default: Byte): Byte = in.readStringAsByte()

              def encodeValue(x: Byte, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .derive
        val codec3 = Schema
          .derived[Array[Char]]
          .deriving(JsonBinaryCodecDeriver)
          .instance(
            TypeId.char,
            new JsonBinaryCodec[Char](JsonBinaryCodec.charType) { // char values as numbers
              def decodeValue(in: JsonReader, default: Char): Char = in.readInt().toChar

              def encodeValue(x: Char, out: JsonWriter): Unit = out.writeVal(x.toInt)
            }
          )
          .derive
        val codec4 = Schema
          .derived[Array[Short]]
          .deriving(JsonBinaryCodecDeriver)
          .instance(
            TypeId.short,
            new JsonBinaryCodec[Short](JsonBinaryCodec.shortType) { // stringifies short values
              def decodeValue(in: JsonReader, default: Short): Short = in.readStringAsShort()

              def encodeValue(x: Short, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .derive
        val codec5 = Schema
          .derived[Array[Int]]
          .deriving(JsonBinaryCodecDeriver)
          .instance(
            TypeId.int,
            new JsonBinaryCodec[Int](JsonBinaryCodec.intType) { // stringifies int values
              def decodeValue(in: JsonReader, default: Int): Int = in.readStringAsInt()

              def encodeValue(x: Int, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .derive
        val codec6 = Schema
          .derived[Array[Float]]
          .deriving(JsonBinaryCodecDeriver)
          .instance(
            TypeId.float,
            new JsonBinaryCodec[Float](JsonBinaryCodec.floatType) { // stringifies float values
              def decodeValue(in: JsonReader, default: Float): Float = in.readStringAsFloat()

              def encodeValue(x: Float, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .derive
        val codec7 = Schema
          .derived[Array[Long]]
          .deriving(JsonBinaryCodecDeriver)
          .instance(
            TypeId.long,
            new JsonBinaryCodec[Long](JsonBinaryCodec.longType) { // stringifies long values
              def decodeValue(in: JsonReader, default: Long): Long = in.readStringAsLong()

              def encodeValue(x: Long, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .derive
        val codec8 = Schema
          .derived[Array[Double]]
          .deriving(JsonBinaryCodecDeriver)
          .instance(
            TypeId.double,
            new JsonBinaryCodec[Double](JsonBinaryCodec.doubleType) { // stringifies double values
              def decodeValue(in: JsonReader, default: Double): Double = in.readStringAsDouble()

              def encodeValue(x: Double, out: JsonWriter): Unit = out.writeValAsString(x)
            }
          )
          .derive
        roundTrip(Array[Boolean](true, false, true), """["true","false","true"]""", codec1) &&
        decodeError("true", "expected '[' or null at: .", codec1) &&
        decodeError("""["true","false","true","false"}""", "expected ']' or ',' at: .", codec1) &&
        decodeError("""["true","false","true","false""", "unexpected end of input at: .at(3)", codec1) &&
        roundTrip(Array[Byte](1: Byte, 2: Byte, 3: Byte), """["1","2","3"]""", codec2) &&
        decodeError("true", "expected '[' or null at: .", codec2) &&
        decodeError("""["1","2","3","4"}""", "expected ']' or ',' at: .", codec2) &&
        decodeError("""["1","2","3","4""", "unexpected end of input at: .at(3)", codec2) &&
        roundTrip(Array[Char]('1', '2', '3'), """[49,50,51]""", codec3) &&
        decodeError("true", "expected '[' or null at: .", codec3) &&
        decodeError("""[49,50,51,52}""", "expected ']' or ',' at: .", codec3) &&
        decodeError("""[49,50,51,52""", "unexpected end of input at: .at(3)", codec3) &&
        roundTrip(Array[Short](1: Short, 2: Short, 3: Short), """["1","2","3"]""", codec4) &&
        decodeError("true", "expected '[' or null at: .", codec4) &&
        decodeError("""["1","2","3","4"}""", "expected ']' or ',' at: .", codec4) &&
        decodeError("""["1","2","3","4""", "unexpected end of input at: .at(3)", codec4) &&
        roundTrip(Array[Int](1, 2, 3), """["1","2","3"]""", codec5) &&
        decodeError("true", "expected '[' or null at: .", codec5) &&
        decodeError("""["1","2","3","4"}""", "expected ']' or ',' at: .", codec5) &&
        decodeError("""["1","2","3","4""", "unexpected end of input at: .at(3)", codec5) &&
        roundTrip(Array[Float](1.0f, 2.0f, 3.0f), """["1.0","2.0","3.0"]""", codec6) &&
        decodeError("true", "expected '[' or null at: .", codec6) &&
        decodeError("""["1","2","3","4"}""", "expected ']' or ',' at: .", codec6) &&
        decodeError("""["1","2","3","4""", "unexpected end of input at: .at(3)", codec6) &&
        roundTrip(Array[Long](1L, 2L, 3L), """["1","2","3"]""", codec7) &&
        decodeError("true", "expected '[' or null at: .", codec7) &&
        decodeError("""["1","2","3","4"}""", "expected ']' or ',' at: .", codec7) &&
        decodeError("""["1","2","3","4""", "unexpected end of input at: .at(3)", codec7) &&
        roundTrip(Array[Double](1.0, 2.0, 3.0), """["1.0","2.0","3.0"]""", codec8) &&
        decodeError("true", "expected '[' or null at: .", codec8) &&
        decodeError("""["1","2","3","4"}""", "expected ']' or ',' at: .", codec8) &&
        decodeError("""["1","2","3","4""", "unexpected end of input at: .at(3)", codec8)
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
          """[{"i":1,"ln":[{"i":2,"ln":[{"i":3}]}]},{"i":4,"ln":[{"i":5,"ln":[{"i":6}]}]}]"""
        )
      },
      test("reentrant encoding using custom codecs") {
        val codec = Schema
          .derived[Array[ZonedDateTime]]
          .deriving(JsonBinaryCodecDeriver)
          .instance(
            TypeId.zonedDateTime,
            new JsonBinaryCodec[ZonedDateTime]() {
              def decodeValue(in: JsonReader, default: ZonedDateTime): ZonedDateTime = in.readZonedDateTime(default)

              def encodeValue(x: ZonedDateTime, out: JsonWriter): Unit =
                if (x.getSecond != 0 || x.getNano != 0) out.writeVal(x)
                else { // enforce serialization of seconds if zero seconds and nanos
                  val buf    = JsonBinaryCodec.zonedDateTimeCodec.encode(x)
                  val len    = buf.length
                  val newBuf = new Array[Byte](len + 3)
                  var pos    = 0
                  while ({ // copy up to `:` separator between hours and minutes
                    val b = buf(pos)
                    newBuf(pos) = b
                    pos += 1
                    b != ':'
                  }) ()
                  newBuf(pos) = buf(pos) // copy minutes
                  newBuf(pos + 1) = buf(pos + 1)
                  pos += 2
                  newBuf(pos) = ':' // set zero seconds
                  newBuf(pos + 1) = '0'
                  newBuf(pos + 2) = '0'
                  while (pos < len) { // copy the rest of the value
                    newBuf(pos + 3) = buf(pos)
                    pos += 1
                  }
                  out.writeRawVal(newBuf)
                }
            }
          )
          .derive
        roundTrip(
          Array(ZonedDateTime.parse("2020-04-10T10:07:00Z"), ZonedDateTime.parse("2020-04-10T10:07:10Z")),
          """["2020-04-10T10:07:00Z","2020-04-10T10:07:10Z"]""",
          codec
        )
      },
      test("reentrant decoding using custom codecs") {
        val codec = Schema
          .derived[Array[OffsetDateTime]]
          .deriving(JsonBinaryCodecDeriver)
          .instance(
            TypeId.offsetDateTime,
            new JsonBinaryCodec[OffsetDateTime]() {
              private[this] val maxLen = 44 // should be enough for the longest offset date time value
              private[this] val pool   = new ThreadLocal[Array[Byte]] {
                override def initialValue(): Array[Byte] = new Array[Byte](maxLen + 2)
              }
              private[this] val config = ReaderConfig.withCheckForEndOfInput(false).withPreferredCharBufSize(maxLen + 8)

              def decodeValue(in: JsonReader, default: OffsetDateTime): OffsetDateTime = {
                val buf = pool.get
                val s   = in.readString(null)
                val len = s.length
                if (
                  len <= maxLen && {
                    buf(0) = '"'
                    var bits, i = 0
                    while (i < len) {
                      val ch = s.charAt(i)
                      buf(i + 1) = ch.toByte
                      bits |= ch
                      i += 1
                    }
                    buf(i + 1) = '"'
                    bits < 0x80
                  }
                ) {
                  JsonBinaryCodec.offsetDateTimeCodec.decode(buf, config) match {
                    case Right(x) => return x
                    case _        => ()
                  }
                }
                in.decodeError("illegal offset date time")
              }

              def encodeValue(x: OffsetDateTime, out: JsonWriter): Unit = out.writeVal(x)
            }
          )
          .derive
        decode(
          "[\"2020-01-01T12:34:56.789\\u002B08:00\"]",
          Array(OffsetDateTime.parse("2020-01-01T12:34:56.789+08:00")),
          codec
        ) &&
        decodeError(
          """["2020-01-01ї12:34:56.789-08:00"]""",
          "illegal offset date time at: .at(0)",
          codec
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
        roundTrip(Map(BigInt("9" * 20) -> 1.0f, BigInt(2) -> 2.0f), """{"99999999999999999999":1.0,"2":2.0}""") &&
        roundTrip(Map(BigDecimal(1.1) -> 1, BigDecimal(2.2) -> 2), """{"1.1":1,"2.2":2}""") &&
        roundTrip(Map(DayOfWeek.of(1) -> 1, DayOfWeek.of(2) -> 2), """{"MONDAY":1,"TUESDAY":2}""") &&
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
        roundTrip(Map(Month.of(10) -> 1, Month.of(12) -> 2), """{"OCTOBER":1,"DECEMBER":2}""") &&
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
        ) &&
        roundTrip(
          Map(1 -> 1.0, 2 -> 2.0),
          """{
            |  "1": 1.0,
            |  "2": 2.0
            |}""".stripMargin,
          readerConfig = ReaderConfig,
          writerConfig = WriterConfig.withIndentionStep(2)
        ) &&
        decodeError[Map[DayOfWeek, Long]]("""{"Mon":1}""", "illegal day of week value at: .at(0)") &&
        decodeError[Map[Month, Long]]("""{"Jun":1}""", "illegal month value at: .at(0)") &&
        decodeError[Map[Currency, Long]]("""{"JJJ":1}""", "illegal currency value at: .at(0)") &&
        decodeError[Map[Int, Long]]("", "unexpected end of input at: .") &&
        decodeError[Map[Int, Long]]("true", "expected '{' or null at: .") &&
        decodeError[Map[Int, Long]]("""{"1"""", "unexpected end of input at: .at(0)") &&
        decodeError[Map[Int, Long]]("""{"1":""", "unexpected end of input at: .atKey(1)") &&
        decodeError[Map[Int, Long]]("""{"1":2]""", "expected '}' or ',' at: .") &&
        encodeError(Map(() -> 1L), "encoding as JSON key is not supported") &&
        decodeError[Map[Unit, Long]]("""{"null":1}""", "decoding as JSON key is not supported at: .at(0)")
      },
      test("primitive key with recursive values") {
        roundTrip(
          Map(
            1 -> Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))),
            2 -> Recursive(4, List(Recursive(5, List(Recursive(6, Nil)))))
          ),
          """{"1":{"i":1,"ln":[{"i":2,"ln":[{"i":3}]}]},"2":{"i":4,"ln":[{"i":5,"ln":[{"i":6}]}]}}"""
        )
      },
      test("nested maps") {
        roundTrip(Map("VVV" -> Map(1 -> 1L, 2 -> 2L)), """{"VVV":{"1":1,"2":2}}""")
      }
    ),
    suite("variants")(
      test("case object enumeration") {
        roundTrip[TrafficLight](TrafficLight.Green, """"Green"""") &&
        roundTrip[TrafficLight](TrafficLight.Yellow, """"Yellow"""") &&
        roundTrip[TrafficLight](TrafficLight.Red, """"Rеd"""") &&
        roundTrip[Color](Color.Green, """"Green"""") &&
        roundTrip[Color](Color.Yellow, """"Yellow"""") &&
        roundTrip[Color](Color.Orаnge, """"Orаnge"""") &&
        roundTrip[Color](Color.Red, """"Red"""") &&
        decodeError[TrafficLight]("""null""", "expected '\"' at: .") &&
        decodeError[TrafficLight](""""Black"""", "illegal enum value \"Black\" at: .") &&
        decodeError[Color]("""null""", "expected '\"' at: .") &&
        decodeError[Color](""""Pink"""", "illegal enum value \"Pink\" at: .")
      },
      test("case object enumeration with key discriminator") {
        val codec1 = Schema[TrafficLight].derive(JsonBinaryCodecDeriver.withEnumValuesAsStrings(false))
        val codec2 = Schema[Color].derive(JsonBinaryCodecDeriver.withEnumValuesAsStrings(false))
        roundTrip(TrafficLight.Green, """{"Green":{}}""", codec1) &&
        roundTrip(TrafficLight.Yellow, """{"Yellow":{}}""", codec1) &&
        roundTrip(TrafficLight.Red, """{"Rеd":{}}""", codec1) &&
        roundTrip(Color.Green, """{"Green":{}}""", codec2) &&
        roundTrip(Color.Yellow, """{"Yellow":{}}""", codec2) &&
        roundTrip(Color.Orаnge, """{"Orаnge":{}}""", codec2) &&
        roundTrip(Color.Red, """{"Red":{}}""", codec2)
      },
      test("case object enumeration with field discriminator") {
        val codec1 = Schema[TrafficLight].derive(
          JsonBinaryCodecDeriver.withEnumValuesAsStrings(false).withDiscriminatorKind(DiscriminatorKind.Field("$type"))
        )
        val codec2 = Schema[Color].derive(
          JsonBinaryCodecDeriver.withEnumValuesAsStrings(false).withDiscriminatorKind(DiscriminatorKind.Field("$type"))
        )
        roundTrip(TrafficLight.Green, """{"$type":"Green"}""", codec1) &&
        roundTrip(TrafficLight.Yellow, """{"$type":"Yellow"}""", codec1) &&
        roundTrip(TrafficLight.Red, """{"$type":"Rеd"}""", codec1) &&
        roundTrip(Color.Green, """{"$type":"Green"}""", codec2) &&
        roundTrip(Color.Yellow, """{"$type":"Yellow"}""", codec2) &&
        roundTrip(Color.Orаnge, """{"$type":"Orаnge"}""", codec2) &&
        roundTrip(Color.Red, """{"$type":"Red"}""", codec2)
      },
      test("ADT with nested trait hierarchy") {
        val codec1 = Schema[GeoJSON].derive(
          JsonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("type"))
        )
        val codec2 = Schema[GeoJSON].derive(
          JsonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.None).withRequireCollectionFields(true)
        )
        val value = Feature(geometry = Point((1.0, 2.0)))
        roundTrip(value, """{"type":"Feature","geometry":{"type":"Point","coordinates":[1.0,2.0]}}""", codec1) &&
        roundTrip(value, """{"geometry":{"coordinates":[1.0,2.0]}}""", codec2) &&
        roundTrip[GeoJSON](value, """{"Feature":{"geometry":{"Point":{"coordinates":[1.0,2.0]}}}}""") &&
        decodeError(
          """{"type":"Feature","geometry":{"type":"Point","coordinates":[01,02]}}""",
          "illegal number with leading zero at: .when[SimpleGeoJSON].when[Feature].geometry.when[SimpleGeometry].when[Point].coordinates._1",
          codec1
        ) &&
        decodeError("""{"geometry":{"coordinates":[01,02]}}""", "expected a variant value at: .", codec2) &&
        decodeError[GeoJSON](
          """{"Feature":{"geometry":{"Point":{"coordinates":[01,02]}}}}""",
          "illegal number with leading zero at: .when[SimpleGeoJSON].when[Feature].geometry.when[SimpleGeometry].when[Point].coordinates._1"
        )
      },
      test("ADT with case key renaming using case name mapper") {
        roundTrip[RGBColor](
          RGBColor.Green,
          """{"GREEN":{}}""",
          Schema[RGBColor].derive(JsonBinaryCodecDeriver.withCaseNameMapper(NameMapper.Custom(_.toUpperCase)))
        ) &&
        roundTrip[RGBColor](
          RGBColor.Yellow,
          """{"yellow":{}}""",
          Schema[RGBColor].derive(JsonBinaryCodecDeriver.withCaseNameMapper(NameMapper.SnakeCase))
        )
      },
      test("ADT with case key renaming and aliasing using annotation") {
        roundTrip[RGBColor](RGBColor.Green, """{"Green":{}}""") &&
        roundTrip[RGBColor](RGBColor.Yellow, """{"Yellow":{}}""") &&
        roundTrip[RGBColor](RGBColor.Orаnge, """{"Orаnge":{}}""") &&
        roundTrip[RGBColor](RGBColor.Red, """{"Red":{}}""") &&
        roundTrip[RGBColor](RGBColor.Mix(0x123456), """{"Mixed":{"color":1193046}}""") &&
        decode[RGBColor]("""{"Azure":{}}""", RGBColor.Blue) &&
        decode[RGBColor]("""{"Blue":{}}""", RGBColor.Blue) &&
        decode[RGBColor]("""{"Indigo":{}}""", RGBColor.Blue) &&
        decode[RGBColor]("""{"Navy":{}}""", RGBColor.Blue) &&
        decode[RGBColor]("""{"Periwinkle":{}}""", RGBColor.Blue) &&
        decode[RGBColor]("""{"Ultramarine":{}}""", RGBColor.Blue) &&
        decodeError[RGBColor]("""null""", "expected '{' at: .") &&
        decodeError[RGBColor]("""{"Pink":{}}""", "illegal discriminator at: .") &&
        decodeError[RGBColor]("""{"Mixed":{"color":1]}""", "expected '}' or ',' at: .when[Mix]") &&
        decodeError[RGBColor]("""{"Mixed":{"color":01}}""", "illegal number with leading zero at: .when[Mix].rgb") &&
        decodeError[RGBColor]("""{"Mixed":{"color":1193046}]""", "expected '}' or ',' at: .") &&
        decodeError[RGBColor]("""{"Mixed":{"rgb":1193046}}""", "missing required field \"color\" at: .when[Mix]")
      },
      test("option") {
        roundTrip(Option(42), """42""") &&
        roundTrip[Option[Int]](None, """null""") &&
        decodeError[Option[Int]]("""08""", "illegal number with leading zero at: .when[Some].value") &&
        decodeError[Option[Int]]("""nuts""", "expected null at: .when[None]")
      },
      test("either") {
        roundTrip[Either[String, Int]](Right(42), """{"Right":{"value":42}}""") &&
        roundTrip[Either[String, Int]](Left("VVV"), """{"Left":{"value":"VVV"}}""") &&
        decodeError[Either[String, Int]]("""null""", "expected '{' at: .") &&
        decodeError[Either[String, Int]]("""{"Middle":{"value":42}}""", "illegal discriminator at: .") &&
        decodeError[Either[String, Int]]("""{"Right":{"value":42]}""", "expected '}' or ',' at: .when[Right]") &&
        decodeError[Either[String, Int]]("""{"Right":{"value":42}]""", "expected '}' or ',' at: .") &&
        decodeError[Either[String, Int]](
          """{"Right":{"value":02}}""",
          "illegal number with leading zero at: .when[Right].value"
        ) &&
        decodeError[Either[String, Int]](
          """{"Left":{"left":"VVV"}}""",
          "missing required field \"value\" at: .when[Left]"
        )
      },
      test("either with the discriminator field") {
        val codec = Schema[Either[String, Int]].derive(
          JsonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("$type")).withRejectExtraFields(true)
        )
        roundTrip(Right(42), """{"$type":"Right","value":42}""", codec) &&
        roundTrip(Left("VVV"), """{"$type":"Left","value":"VVV"}""", codec) &&
        decodeError("""null""", "expected '{' at: .", codec) &&
        decodeError("""{"$type":"X","value":42}}""", "illegal value of discriminator field \"$type\" at: .", codec) &&
        decodeError("""{"$type":"Right","value":42]""", "expected '}' or ',' at: .when[Right]", codec) &&
        decodeError(
          """{"$type":"Right","value":02}""",
          "illegal number with leading zero at: .when[Right].value",
          codec
        ) &&
        decodeError("""{"$type":"Left","left":"VVV"}""", "unexpected field \"left\" at: .when[Left]", codec) &&
        decodeError("""{"Left":{"value":"VVV"}}""", "missing required field \"$type\" at: .", codec)
      },
      test("nested ADTs") {
        roundTrip[Pet](
          Dog("Rex", Right(1), "German Shepherd"),
          """{"Dog":{"name":"Rex","age":{"Right":{"value":1}},"breed":"German Shepherd"}}"""
        ) &&
        roundTrip[Pet](
          Cat("Misty", Left("unknown"), 7),
          """{"Cat":{"name":"Misty","age":{"Left":{"value":"unknown"}},"livesLeft":7}}"""
        ) &&
        roundTrip[Pet](
          Bird("Tweety", Right(15), RGBColor.Turquoise),
          """{"Bird":{"name":"Tweety","age":{"Right":{"value":15}},"color":{"Turquoise":{}}}}"""
        )
      },
      test("nested ADTs with the discriminator field") {
        val codec = Schema[Pet].derive(JsonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("$type")))
        roundTrip(
          Dog("Rex", Right(1), "German Shepherd"),
          """{"$type":"Dog","name":"Rex","age":{"$type":"Right","value":1},"breed":"German Shepherd"}""",
          codec
        ) &&
        roundTrip(
          Cat("Misty", Left("unknown"), 7),
          """{"$type":"Cat","name":"Misty","age":{"$type":"Left","value":"unknown"},"livesLeft":7}""",
          codec
        ) &&
        roundTrip(
          Bird("Tweety", Right(15), RGBColor.Turquoise),
          """{"$type":"Bird","name":"Tweety","age":{"$type":"Right","value":15},"color":{"$type":"Turquoise"}}""",
          codec
        )
      },
      test("variant with custom case names") {
        val codec = Color.schema
          .deriving(JsonBinaryCodecDeriver)
          .modifier(Color.red, Modifier.rename("Rose"))
          .derive
        roundTrip(Color.Red, """"Rose"""", codec)
      },
      test("variant with case name aliases") {
        val codec = Color.schema
          .deriving(JsonBinaryCodecDeriver)
          .modifier(Color.red, Modifier.alias("Rose"))
          .modifier(Color.red, Modifier.alias("Ruby"))
          .modifier(Color.red, Modifier.alias("Coral"))
          .modifier(Color.red, Modifier.alias("Scarlet"))
          .modifier(Color.red, Modifier.alias("Oxblood"))
          .modifier(Color.red, Modifier.alias("Vermilion"))
          .modifier(Color.red, Modifier.alias("Crimson"))
          .modifier(Color.red, Modifier.alias("Garnet"))
          .modifier(Color.red, Modifier.alias("Salmon"))
          .modifier(Color.red, Modifier.alias("Maroon"))
          .derive
        decode(""""Red"""", Color.Red, codec) &&
        decode(""""Rose"""", Color.Red, codec) &&
        decode(""""Ruby"""", Color.Red, codec) &&
        decode(""""Coral"""", Color.Red, codec) &&
        decode(""""Scarlet"""", Color.Red, codec) &&
        decode(""""Oxblood"""", Color.Red, codec) &&
        decode(""""Vermilion"""", Color.Red, codec) &&
        decode(""""Crimson"""", Color.Red, codec) &&
        decode(""""Garnet"""", Color.Red, codec) &&
        decode(""""Salmon"""", Color.Red, codec) &&
        decode(""""Maroon"""", Color.Red, codec)
      },
      test("variant with duplicated case names") {
        assert(scala.util.Try {
          Color.schema
            .deriving(JsonBinaryCodecDeriver)
            .modifier(Color.red, Modifier.rename("Black"))
            .derive
        }.toEither)(isLeft(hasError("Cannot derive codec - duplicated name detected: 'Black'")))
      },
      test("variant with a custom codec for a case injected by type and term name") {
        val fixedDogCodec = new JsonBinaryCodec[Dog]() {
          def decodeValue(in: JsonReader, default: Dog): Dog = {
            in.skip()
            Dog("Rex", Right(1), "Mutt")
          }

          def encodeValue(x: Dog, out: JsonWriter): Unit = {
            out.writeObjectStart()
            out.writeKey("n")
            out.writeVal(x.name)
            out.writeObjectEnd()
          }
        }
        val codec = Pet.schema
          .deriving(JsonBinaryCodecDeriver)
          .instance(Pet.schema.reflect.typeId, "Dog", fixedDogCodec)
          .derive
        roundTrip[Pet](Dog("Rex", Right(1), "Mutt"), """{"Dog":{"n":"Rex"}}""", codec) &&
        roundTrip[Pet](
          Cat("Misty", Left("unknown"), 7),
          """{"Cat":{"name":"Misty","age":{"Left":{"value":"unknown"}},"livesLeft":7}}""",
          codec
        )
      },
      test("variant with case renamed by type and term name modifier") {
        val codec = Color.schema
          .deriving(JsonBinaryCodecDeriver)
          .modifier(Color.schema.reflect.typeId, "Red", Modifier.rename("Rose"))
          .derive
        roundTrip(Color.Red, """"Rose"""", codec)
      },
      test("variant with both optic and type-based term modifiers") {
        val codec = Color.schema
          .deriving(JsonBinaryCodecDeriver)
          .modifier(Color.red, Modifier.alias("Coral"))
          .modifier(Color.schema.reflect.typeId, "Red", Modifier.rename("Rose"))
          .derive
        roundTrip(Color.Red, """"Rose"""", codec) &&
        decode(""""Coral"""", Color.Red, codec)
      }
    ),
    suite("wrapper")(
      test("top-level") {
        roundTrip[UserId](UserId(1234567890123456789L), "1234567890123456789") &&
        roundTrip[Email](Email("john@gmail.com"), "\"john@gmail.com\"") &&
        decodeError[Email]("john@gmail.com", "expected '\"' at: .wrapped") &&
        decodeError[Email]("\"john&gmail.com\"", "expected e-mail at: .wrapped") &&
        decodeError[Email]("\"john@gmail.com", "unexpected end of input at: .wrapped")
      },
      test("as a record field") {
        roundTrip[Record3](
          Record3(
            UserId(1234567890123456789L),
            Email("backup@gmail.com"),
            Currency.getInstance("USD"),
            Map(
              Currency.getInstance("USD") -> "VVV",
              Currency.getInstance("EUR") -> "WWW"
            )
          ),
          """{"userId":1234567890123456789,"email":"backup@gmail.com","currency":"USD","accounts":{"USD":"VVV","EUR":"WWW"}}"""
        )
      },
      test("as a map key") {
        roundTrip(
          Map(UserId(1234567890123456789L) -> Email("backup@gmail.com")),
          """{"1234567890123456789":"backup@gmail.com"}"""
        ) &&
        decodeError[Map[Email, UserId]]("""{john@gmail.com:123}""", "expected '\"' at: .at(0).wrapped") &&
        decodeError[Map[Email, UserId]]("""{"backup&gmail.com":123}""", "expected e-mail at: .at(0).wrapped") &&
        decodeError[Map[Email, UserId]]("""{"backup@gmail.com":123""", "unexpected end of input at: .")
      }
    ),
    suite("dynamic value")(
      test("top-level") {
        roundTrip[DynamicValue](DynamicValue.Null, "null") &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Unit), "{}") &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Boolean(true)), "true") &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Boolean(false)), "false") &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Byte(1: Byte)), "1") &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Short(1: Short)), "1") &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Int(1)), "1") &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Long(12345678901L)), "12345678901") &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Float(1.0f)), "1.0") &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Double(1.0)), "1.0") &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Char('X')), "\"X\"") &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.String("VVV")), "\"VVV\"") &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.BigInt(123)), "123") &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.BigDecimal(123.45)), "123.45") &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY)), "\"MONDAY\"") &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Duration(Duration.ofSeconds(60))), "\"PT1M\"") &&
        encode[DynamicValue](
          DynamicValue.Primitive(PrimitiveValue.Instant(Instant.EPOCH)),
          "\"1970-01-01T00:00:00Z\""
        ) &&
        encode[DynamicValue](
          DynamicValue.Primitive(PrimitiveValue.LocalDate(LocalDate.of(2025, 12, 1))),
          "\"2025-12-01\""
        ) &&
        encode[DynamicValue](
          DynamicValue.Primitive(
            PrimitiveValue.LocalDateTime(LocalDateTime.of(LocalDate.of(2025, 12, 1), LocalTime.of(12, 30)))
          ),
          "\"2025-12-01T12:30\""
        ) &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.LocalTime(LocalTime.of(12, 30))), "\"12:30\"") &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Month(Month.MAY)), "\"MAY\"") &&
        encode[DynamicValue](
          DynamicValue.Primitive(PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1))),
          "\"--05-01\""
        ) &&
        encode[DynamicValue](
          DynamicValue.Primitive(
            PrimitiveValue.OffsetDateTime(
              OffsetDateTime
                .of(LocalDateTime.of(LocalDate.of(2025, 12, 1), LocalTime.of(12, 30)), ZoneOffset.ofHours(1))
            )
          ),
          "\"2025-12-01T12:30+01:00\""
        ) &&
        encode[DynamicValue](
          DynamicValue.Primitive(PrimitiveValue.OffsetTime(OffsetTime.of(LocalTime.of(12, 30), ZoneOffset.ofHours(1)))),
          "\"12:30+01:00\""
        ) &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Period(Period.ofDays(1))), "\"P1D\"") &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Year(Year.of(2025))), "\"2025\"") &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.YearMonth(YearMonth.of(2025, 1))), "\"2025-01\"") &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.ZoneId(ZoneId.of("UTC"))), "\"UTC\"") &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.ZoneOffset(ZoneOffset.ofHours(1))), "\"+01:00\"") &&
        encode[DynamicValue](
          DynamicValue.Primitive(
            PrimitiveValue.ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC")))
          ),
          "\"1970-01-01T00:00Z[UTC]\""
        ) &&
        encode[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Currency(Currency.getInstance("USD"))), "\"USD\"") &&
        encode[DynamicValue](
          DynamicValue.Primitive(PrimitiveValue.UUID(new UUID(1L, 2L))),
          "\"00000000-0000-0001-0000-000000000002\""
        ) &&
        roundTrip[DynamicValue](
          DynamicValue.Record(
            Chunk(
              ("i", DynamicValue.Primitive(PrimitiveValue.Int(1))),
              ("s", DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          ),
          """{"i":1,"s":"VVV"}"""
        ) &&
        encode[DynamicValue](
          DynamicValue.Variant("Int", DynamicValue.Primitive(PrimitiveValue.Int(1))),
          """{"Int":1}"""
        ) &&
        roundTrip[DynamicValue](DynamicValue.Sequence(Chunk.empty), "[]") &&
        roundTrip[DynamicValue](
          DynamicValue.Sequence(
            Chunk(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.String("VVV"))
            )
          ),
          """[1,"VVV"]"""
        ) &&
        roundTrip[DynamicValue](DynamicValue.Map(Chunk.empty), "{}") &&
        encode[DynamicValue](
          DynamicValue.Map(
            Chunk(
              (DynamicValue.Primitive(PrimitiveValue.Boolean(true)), DynamicValue.Primitive(PrimitiveValue.Int(1))),
              (DynamicValue.Primitive(PrimitiveValue.Byte(1: Byte)), DynamicValue.Primitive(PrimitiveValue.Int(2))),
              (DynamicValue.Primitive(PrimitiveValue.Short(1: Short)), DynamicValue.Primitive(PrimitiveValue.Int(3))),
              (DynamicValue.Primitive(PrimitiveValue.Int(1)), DynamicValue.Primitive(PrimitiveValue.Int(4))),
              (DynamicValue.Primitive(PrimitiveValue.Long(1)), DynamicValue.Primitive(PrimitiveValue.Int(5))),
              (DynamicValue.Primitive(PrimitiveValue.Float(1.0f)), DynamicValue.Primitive(PrimitiveValue.Int(6))),
              (DynamicValue.Primitive(PrimitiveValue.Double(1.0)), DynamicValue.Primitive(PrimitiveValue.Int(7))),
              (DynamicValue.Primitive(PrimitiveValue.Char('X')), DynamicValue.Primitive(PrimitiveValue.Int(8))),
              (DynamicValue.Primitive(PrimitiveValue.String("VVV")), DynamicValue.Primitive(PrimitiveValue.Int(9))),
              (DynamicValue.Primitive(PrimitiveValue.BigInt(123)), DynamicValue.Primitive(PrimitiveValue.Int(10))),
              (
                DynamicValue.Primitive(PrimitiveValue.BigDecimal(123.45)),
                DynamicValue.Primitive(PrimitiveValue.Int(11))
              ),
              (
                DynamicValue.Primitive(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY)),
                DynamicValue.Primitive(PrimitiveValue.Int(12))
              ),
              (
                DynamicValue.Primitive(PrimitiveValue.Duration(Duration.ofSeconds(60))),
                DynamicValue.Primitive(PrimitiveValue.Int(13))
              ),
              (
                DynamicValue.Primitive(PrimitiveValue.Instant(Instant.EPOCH)),
                DynamicValue.Primitive(PrimitiveValue.Int(14))
              ),
              (
                DynamicValue.Primitive(PrimitiveValue.LocalDate(LocalDate.of(2025, 12, 1))),
                DynamicValue.Primitive(PrimitiveValue.Int(15))
              ),
              (
                DynamicValue.Primitive(
                  PrimitiveValue.LocalDateTime(LocalDateTime.of(LocalDate.of(2025, 12, 1), LocalTime.of(12, 30)))
                ),
                DynamicValue.Primitive(PrimitiveValue.Int(16))
              ),
              (
                DynamicValue.Primitive(PrimitiveValue.LocalTime(LocalTime.of(12, 30))),
                DynamicValue.Primitive(PrimitiveValue.Int(17))
              ),
              (DynamicValue.Primitive(PrimitiveValue.Month(Month.MAY)), DynamicValue.Primitive(PrimitiveValue.Int(18))),
              (
                DynamicValue.Primitive(PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1))),
                DynamicValue.Primitive(PrimitiveValue.Int(19))
              ),
              (
                DynamicValue.Primitive(
                  PrimitiveValue.OffsetDateTime(
                    OffsetDateTime
                      .of(LocalDateTime.of(LocalDate.of(2025, 12, 1), LocalTime.of(12, 30)), ZoneOffset.ofHours(1))
                  )
                ),
                DynamicValue.Primitive(PrimitiveValue.Int(20))
              ),
              (
                DynamicValue.Primitive(
                  PrimitiveValue.OffsetTime(OffsetTime.of(LocalTime.of(12, 30), ZoneOffset.ofHours(1)))
                ),
                DynamicValue.Primitive(PrimitiveValue.Int(21))
              ),
              (
                DynamicValue.Primitive(PrimitiveValue.Period(Period.ofDays(1))),
                DynamicValue.Primitive(PrimitiveValue.Int(22))
              ),
              (
                DynamicValue.Primitive(PrimitiveValue.Year(Year.of(2025))),
                DynamicValue.Primitive(PrimitiveValue.Int(23))
              ),
              (
                DynamicValue.Primitive(PrimitiveValue.YearMonth(YearMonth.of(2025, 1))),
                DynamicValue.Primitive(PrimitiveValue.Int(24))
              ),
              (
                DynamicValue.Primitive(PrimitiveValue.ZoneId(ZoneId.of("UTC"))),
                DynamicValue.Primitive(PrimitiveValue.Int(25))
              ),
              (
                DynamicValue.Primitive(PrimitiveValue.ZoneOffset(ZoneOffset.ofHours(1))),
                DynamicValue.Primitive(PrimitiveValue.Int(26))
              ),
              (
                DynamicValue.Primitive(
                  PrimitiveValue.ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC")))
                ),
                DynamicValue.Primitive(PrimitiveValue.Int(27))
              ),
              (
                DynamicValue.Primitive(PrimitiveValue.Currency(Currency.getInstance("USD"))),
                DynamicValue.Primitive(PrimitiveValue.Int(28))
              ),
              (
                DynamicValue.Primitive(PrimitiveValue.UUID(new UUID(1L, 2L))),
                DynamicValue.Primitive(PrimitiveValue.Int(29))
              )
            )
          ),
          """{"true":1,"1":2,"1":3,"1":4,"1":5,"1.0":6,"1.0":7,"X":8,"VVV":9,"123":10,"123.45":11,"MONDAY":12,"PT1M":13,"1970-01-01T00:00:00Z":14,"2025-12-01":15,"2025-12-01T12:30":16,"12:30":17,"MAY":18,"--05-01":19,"2025-12-01T12:30+01:00":20,"12:30+01:00":21,"P1D":22,"2025":23,"2025-01":24,"UTC":25,"+01:00":26,"1970-01-01T00:00Z[UTC]":27,"USD":28,"00000000-0000-0001-0000-000000000002":29}"""
        ) &&
        encodeError[DynamicValue](
          DynamicValue.Map(
            Chunk((DynamicValue.Primitive(PrimitiveValue.Unit), DynamicValue.Primitive(PrimitiveValue.Int(1))))
          ),
          "encoding as JSON key is not supported"
        ) &&
        encodeError[DynamicValue](
          DynamicValue.Map(
            Chunk((DynamicValue.Sequence(Chunk.empty), DynamicValue.Primitive(PrimitiveValue.Int(1))))
          ),
          "encoding as JSON key is not supported"
        ) &&
        decodeError[DynamicValue]("""{"1":2]""", "expected '}' or ',' at: .") &&
        decodeError[DynamicValue]("[1,2}", "expected ']' or ',' at: .") &&
        decodeError[DynamicValue]("nuts", "expected JSON value at: .")
      },
      test("as record field values") {
        val value = Dynamic(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Record(
            Chunk(
              ("VVV", DynamicValue.Primitive(PrimitiveValue.Int(1))),
              ("WWW", DynamicValue.Primitive(PrimitiveValue.Int(2)))
            )
          )
        )
        roundTrip[Dynamic](value, """{"primitive":1,"record":{"VVV":1,"WWW":2}}""")
      }
    )
  )

  private[this] def toISO8601(year: Year): String = {
    val x = year.getValue
    if (x > 9999) s"+$x"
    else if (x > 99 && x <= 999) s"0$x"
    else if (x > 9 && x <= 99) s"00$x"
    else if (x >= 0 && x <= 9) s"000$x"
    else if (x >= -9 && x < 0) s"-000${-x}"
    else if (x >= -99 && x < 9) s"-00${-x}"
    else if (x >= -999 && x < 99) s"-0${-x}"
    else x.toString
  }

  private[this] def toISO8601(x: YearMonth): String = {
    val s = x.toString
    if (x.getYear < 0 && !s.startsWith("-")) s"-$s"
    else if (x.getYear > 9999 && !s.startsWith("+"))
      s"+$s" // '+' is required for years that exceed 4 digits, see ISO 8601:2004 sections 3.4.2, 4.1.2.4
    else s
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

    @Modifier.rename("Rеd") // using non-ASCII chars for field names intentionally
    case object Red extends TrafficLight

    case object Yellow extends TrafficLight

    case object Green extends TrafficLight
  }

  implicit val eitherSchema: Schema[Either[String, Int]] = Schema.derived

  case class UserId(value: Long)

  object UserId {
    implicit lazy val typeId: TypeId[UserId] = TypeId.of[UserId]
    implicit lazy val schema: Schema[UserId] =
      Schema[Long].transform[UserId](x => new UserId(x), _.value)
  }

  case class Email(value: String)

  object Email {
    private[this] val EmailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".r

    implicit lazy val typeId: TypeId[Email] = TypeId.of[Email]
    implicit lazy val schema: Schema[Email] =
      Schema[String].transform[Email](
        {
          case x @ EmailRegex(_*) => new Email(x)
          case _                  => throw SchemaError.validationFailed("expected e-mail")
        },
        _.value
      )
  }

  case class Record3(userId: UserId, email: Email, currency: Currency, accounts: Map[Currency, String])

  object Record3 {
    implicit val schema: Schema[Record3] = Schema.derived
  }

  case class Record4(hіdden: Unit, optKеy: Option[String]) // using non-ASCII chars for field names intentionally

  object Record4 extends CompanionOptics[Record4] {
    implicit val schema: Schema[Record4] = Schema.derived

    val hidden: Lens[Record4, Unit]           = $(_.hіdden)
    val optKey: Lens[Record4, Option[String]] = $(_.optKеy)
  }

  case class Record5(bigInt: BigInt, @Modifier.rename("bigDecimal") bigDecimal: BigDecimal)

  object Record5 extends CompanionOptics[Record5] {
    implicit val schema: Schema[Record5] = Schema.derived

    val bigInt: Lens[Record5, BigInt]         = $(_.bigInt)
    val bigDecimal: Lens[Record5, BigDecimal] = $(_.bigDecimal)
  }

  case class Record6(
    bl: Boolean = false,
    b: Byte = 1.toByte,
    sh: Short = 2.toShort,
    i: Int = 3,
    l: Long = 4L,
    f: Float = 5.0f,
    d: Double = 6.0,
    c: Char = '7',
    s: String = "VVV"
  )

  object Record6 extends CompanionOptics[Record6] {
    implicit val schema: Schema[Record6] = Schema.derived

    val bl: Lens[Record6, Boolean] = $(_.bl)
    val b: Lens[Record6, Byte]     = $(_.b)
    val sh: Lens[Record6, Short]   = $(_.sh)
    val i: Lens[Record6, Int]      = $(_.i)
    val l: Lens[Record6, Long]     = $(_.l)
    val f: Lens[Record6, Float]    = $(_.f)
    val d: Lens[Record6, Double]   = $(_.d)
    val c: Lens[Record6, Char]     = $(_.c)
    val s: Lens[Record6, String]   = $(_.s)
  }

  case class Dynamic(primitive: DynamicValue, record: DynamicValue)

  object Dynamic extends CompanionOptics[Dynamic] {
    implicit val schema: Schema[Dynamic] = Schema.derived

    val primitive: Lens[Dynamic, DynamicValue] = $(_.primitive)
    val record: Lens[Dynamic, DynamicValue]    = $(_.record)
  }

  sealed trait Color

  object Color extends CompanionOptics[Color] {
    implicit val schema: Schema[Color] = Schema.derived

    case object Red extends Color

    case object Orаnge extends Color // using non-ASCII chars for field names intentionally

    case object Yellow extends Color

    case object Green extends Color

    case object Turquoise extends Color

    case object Blue extends Color

    case object Violet extends Color

    case object White extends Color

    case object Gray extends Color

    case object Black extends Color

    val red: Prism[Color, Color.Red.type] = $(_.when[Color.Red.type])
  }

  sealed abstract class RGBColor(val color: Int)

  object RGBColor {
    implicit val schema: Schema[RGBColor] = Schema.derived

    case object Red extends RGBColor(0xff0000)

    case object Orаnge extends RGBColor(0x7fff00) // using non-ASCII chars for field names intentionally

    case object Yellow extends RGBColor(0xffff00)

    case object Green extends RGBColor(0x00ff00)

    case object Turquoise extends RGBColor(0x007fff)

    @Modifier.alias("Azure")
    @Modifier.alias("Indigo")
    @Modifier.alias("Navy")
    @Modifier.alias("Periwinkle")
    @Modifier.alias("Ultramarine")
    case object Blue extends RGBColor(0x0000ff)

    case object Violet extends RGBColor(0xff00ff)

    case object White extends RGBColor(0xffffff)

    case object Gray extends RGBColor(0x7f7f7f)

    case object Black extends RGBColor(0x000000)

    @Modifier.rename("Mixed")
    case class Mix(@Modifier.rename("color") rgb: Int) extends RGBColor(rgb)
  }

  case class CamelPascalSnakeKebabCases(
    camelCase: Int,
    PascalCase: Int,
    snake_case: Int,
    `kebab-case`: Int,
    camel1: Int,
    Pascal1: Int,
    snake_1: Int,
    `kebab-1`: Int
  )

  object CamelPascalSnakeKebabCases {
    implicit val schema: Schema[CamelPascalSnakeKebabCases] = Schema.derived
  }

  sealed trait Pet {
    def name: String

    def age: Either[String, Int]
  }

  object Pet {
    implicit val schema: Schema[Pet] = Schema.derived
  }

  case class Cat(name: String, age: Either[String, Int], livesLeft: Int) extends Pet

  case class Dog(name: String, age: Either[String, Int], breed: String) extends Pet

  case class Bird(name: String, age: Either[String, Int], color: RGBColor) extends Pet

  case class BigProduct(
    f00: Boolean,
    f01: Option[Byte] = None,
    f02: Option[Short] = None,
    f03: Option[Int] = None,
    f04: Option[Long] = None,
    f05: Option[Float] = None,
    f06: Option[Double] = None,
    f07: Option[Char] = None,
    f08: Option[String] = None,
    f09: Option[Int] = None,
    f10: Option[Int] = None,
    f11: Option[Int] = None,
    f12: Option[Int] = None,
    f13: Option[Int] = None,
    f14: Option[Int] = None,
    f15: Option[Int] = None,
    f16: Option[Int] = None,
    f17: Option[Int] = None,
    f18: Option[Int] = None,
    f19: Option[Int] = None,
    f20: Option[Int] = None,
    f21: Option[Int] = None,
    f22: Option[Int] = None,
    f23: Option[Int] = None,
    f24: Option[Int] = None,
    f25: Option[Int] = None,
    f26: Option[Int] = None,
    f27: Option[Int] = None,
    f28: Option[Int] = None,
    f29: Option[Int] = None,
    f30: Option[Int] = None,
    f31: Option[Int] = None,
    f32: Option[Int] = None,
    f33: Option[Int] = None,
    f34: Option[Int] = None,
    f35: Option[Int] = None,
    f36: Option[Int] = None,
    f37: Option[Int] = None,
    f38: Option[Int] = None,
    f39: Option[Int] = None,
    f40: Option[Int] = None,
    f41: Option[Int] = None,
    f42: Option[Int] = None,
    f43: Option[Int] = None,
    f44: Option[Int] = None,
    f45: Option[Int] = None,
    f46: Option[Int] = None,
    f47: Option[Int] = None,
    f48: Option[Int] = None,
    f49: Option[Int] = None,
    f50: Option[Int] = None,
    f51: Option[Int] = None,
    f52: Option[Int] = None,
    f53: Option[Int] = None,
    f54: Option[Int] = None,
    f55: Option[Int] = None,
    f56: Option[Int] = None,
    f57: Option[Int] = None,
    f58: Option[Int] = None,
    f59: Option[Int] = None,
    f60: Option[Int] = None,
    f61: Option[Int] = None,
    f62: Option[Int] = None,
    f63: Option[Int] = None,
    f64: Option[Int] = None,
    f65: Option[Int] = None,
    @Modifier.transient() f66: Option[Int] = Some(1),
    f67: Option[BigProduct] = None,
    f68: List[Int] = List(1, 2, 3),
    f69: Int
  )

  object BigProduct {
    implicit val schema: Schema[BigProduct] = Schema.derived
  }

  sealed trait Geometry extends Product with Serializable

  sealed trait SimpleGeometry extends Geometry

  case class Point(coordinates: (Double, Double)) extends SimpleGeometry

  case class MultiPoint(coordinates: IndexedSeq[(Double, Double)]) extends SimpleGeometry

  case class LineString(coordinates: IndexedSeq[(Double, Double)]) extends SimpleGeometry

  case class MultiLineString(coordinates: IndexedSeq[IndexedSeq[(Double, Double)]]) extends SimpleGeometry

  case class Polygon(coordinates: IndexedSeq[IndexedSeq[(Double, Double)]]) extends SimpleGeometry

  case class MultiPolygon(coordinates: IndexedSeq[IndexedSeq[IndexedSeq[(Double, Double)]]]) extends SimpleGeometry

  case class GeometryCollection(geometries: IndexedSeq[SimpleGeometry]) extends Geometry

  sealed trait GeoJSON extends Product with Serializable

  sealed trait SimpleGeoJSON extends GeoJSON

  case class Feature(
    properties: Map[String, String] = Map.empty,
    geometry: Geometry,
    bbox: Option[(Double, Double, Double, Double)] = None
  ) extends SimpleGeoJSON

  case class FeatureCollection(
    features: IndexedSeq[SimpleGeoJSON],
    bbox: Option[(Double, Double, Double, Double)] = None
  ) extends GeoJSON

  object GeoJSON {
    implicit val schema: Schema[GeoJSON] = Schema.derived
  }

  case class Arrays(xs: Array[String]) {
    override def hashCode(): Int = java.util.Arrays.hashCode(xs.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case that: Arrays => java.util.Arrays.equals(xs.asInstanceOf[Array[AnyRef]], that.xs.asInstanceOf[Array[AnyRef]])
      case _            => false
    }
  }

  object Arrays {
    implicit val schema: Schema[Arrays] = Schema.derived
  }

  case class PosInt private (value: Int) extends AnyVal

  object PosInt {
    def apply(value: Int): Either[SchemaError, PosInt] =
      if (value >= 0) Right(new PosInt(value))
      else Left(SchemaError.validationFailed("Expected positive value"))

    def applyUnsafe(value: Int): PosInt =
      if (value >= 0) new PosInt(value)
      else throw new IllegalArgumentException("Expected positive value")

    implicit lazy val typeId: TypeId[PosInt] = TypeId.of[PosInt]
    implicit lazy val schema: Schema[PosInt] =
      Schema[Int].transform[PosInt](PosInt.applyUnsafe, _.value)
  }

  case class Counter(value: PosInt)

  object Counter {
    implicit val schema: Schema[Counter] = Schema.derived[Counter]
  }

  object RawVal {
    def apply(s: String) = new RawVal(s)

    val codec: JsonBinaryCodec[RawVal] = new JsonBinaryCodec[RawVal] {
      override def decodeValue(in: JsonReader, default: RawVal): RawVal = new RawVal(in.readRawValAsBytes())

      override def encodeValue(x: RawVal, out: JsonWriter): Unit = out.writeRawVal(x.bs)

      override val nullValue: RawVal = new RawVal(Array.emptyByteArray)
    }

    private case class Nested(xx: Boolean, yy: Boolean)

    private case class TopLevel(y: Nested)

    private case object TopLevel {
      val codec: JsonBinaryCodec[TopLevel] = Schema.derived.derive(JsonBinaryCodecDeriver)
    }

    implicit val schema: Schema[RawVal] = Schema.derived
  }

  case class RawVal private (bs: Array[Byte]) {
    def this(s: String) = this(s.getBytes(StandardCharsets.UTF_8))

    override lazy val hashCode: Int = java.util.Arrays.hashCode(bs)

    override def equals(obj: Any): Boolean = obj match {
      case that: RawVal => java.util.Arrays.equals(bs, that.bs)
      case _            => false
    }

    lazy val isValid: Boolean = RawVal.TopLevel.codec.decode(bs) match {
      case Right(topLevel) => topLevel.y.xx & !topLevel.y.yy
      case _               => false
    }
  }
}
