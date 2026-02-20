package zio.blocks.schema.toon

import zio.blocks.chunk.Chunk
import zio.blocks.schema.toon.ToonTestUtils._
import zio.blocks.schema.toon.NameMapper._
import zio.blocks.schema._
import zio.test._
import zio.test.TestAspect.jvmOnly
import java.time._
import java.util.{Currency, UUID}

object ToonBinaryCodecDeriverSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("ToonBinaryCodecDeriverSpec")(
    suite("primitives")(
      test("Unit") {
        roundTrip((), "null") &&
        decode("\"null\"", ()) &&
        decodeError[Unit]("", "Expected null, got:  at: .") &&
        decodeError[Unit]("null ,", "Expected null, got: null , at: .") &&
        decodeError[Unit]("true", "Expected null, got: true at: .")
      },
      test("Boolean") {
        roundTrip(true, "true") &&
        roundTrip(false, "false") &&
        decode("\"true\"", true) &&
        decode("\"false\"", false) &&
        decodeError[Boolean]("yes", "Expected boolean, got: yes at: .")
      },
      test("Byte") {
        check(Gen.byte)(x => roundTrip(x, x.toString)) &&
        check(Gen.byte)(x => decode(s"\"$x\"", x)) &&
        roundTrip(1: Byte, "1") &&
        roundTrip(Byte.MinValue, "-128") &&
        roundTrip(Byte.MaxValue, "127") &&
        decode("-0", 0: Byte) &&
        decode("01", 1: Byte) &&
        decode("-01", -1: Byte) &&
        decodeError[Byte]("-129", "Expected byte, got: -129 at: .") &&
        decodeError[Byte]("128", "Expected byte, got: 128 at: .") &&
        decodeError[Byte]("1.0", "Expected byte, got: 1.0 at: .") &&
        decodeError[Byte]("1e1", "Expected byte, got: 1e1 at: .") &&
        decodeError[Byte]("null", "Expected byte, got: null at: .") &&
        decodeError[Byte]("", "Expected byte, got:  at: .") &&
        decodeError[Byte]("1,", "Expected byte, got: 1, at: .")
      },
      test("Short") {
        check(Gen.short)(x => roundTrip(x, x.toString)) &&
        check(Gen.short)(x => decode(s"\"$x\"", x)) &&
        roundTrip(1: Short, "1") &&
        roundTrip(Short.MinValue, "-32768") &&
        roundTrip(Short.MaxValue, "32767") &&
        decode("-0", 0: Short) &&
        decode("01", 1: Short) &&
        decode("-01", -1: Short) &&
        decodeError[Short]("-32769", "Expected short, got: -32769 at: .") &&
        decodeError[Short]("32768", "Expected short, got: 32768 at: .") &&
        decodeError[Short]("1.0", "Expected short, got: 1.0 at: .") &&
        decodeError[Short]("1e1", "Expected short, got: 1e1 at: .") &&
        decodeError[Short]("null", "Expected short, got: null at: .") &&
        decodeError[Short]("", "Expected short, got:  at: .") &&
        decodeError[Short]("1,", "Expected short, got: 1, at: .")
      },
      test("Int") {
        check(Gen.int)(x => roundTrip(x, x.toString)) &&
        check(Gen.int)(x => decode(s"\"$x\"", x)) &&
        roundTrip(42, "42") &&
        roundTrip(Int.MinValue, "-2147483648") &&
        roundTrip(Int.MaxValue, "2147483647") &&
        decode("-0", 0) &&
        decode("01", 1) &&
        decode("-01", -1) &&
        decodeError[Int]("-2147483649", "Expected int, got: -2147483649 at: .") &&
        decodeError[Int]("2147483648", "Expected int, got: 2147483648 at: .") &&
        decodeError[Int]("1.0", "Expected int, got: 1.0 at: .") &&
        decodeError[Int]("1e1", "Expected int, got: 1e1 at: .") &&
        decodeError[Int]("null", "Expected int, got: null at: .") &&
        decodeError[Int]("", "Expected int, got:  at: .") &&
        decodeError[Int]("1,", "Expected int, got: 1, at: .")
      },
      test("Long") {
        check(Gen.long)(x => roundTrip(x, x.toString)) &&
        check(Gen.long)(x => decode(s"\"$x\"", x)) &&
        roundTrip(42L, "42") &&
        roundTrip(Long.MinValue, "-9223372036854775808") &&
        roundTrip(Long.MaxValue, "9223372036854775807") &&
        decode("-0", 0L) &&
        decode("01", 1L) &&
        decode("-01", -1L) &&
        decodeError[Long]("-9223372036854775809", "Expected long, got: -9223372036854775809 at: .") &&
        decodeError[Long]("9223372036854775808", "Expected long, got: 9223372036854775808 at: .") &&
        decodeError[Long]("1.0", "Expected long, got: 1.0 at: .") &&
        decodeError[Long]("1e1", "Expected long, got: 1e1 at: .") &&
        decodeError[Long]("null", "Expected long, got: null at: .") &&
        decodeError[Long]("", "Expected long, got:  at: .") &&
        decodeError[Long]("1,", "Expected long, got: 1, at: .")
      },
      test("Float") {
        check(Gen.float)(x => decode(x.toString, x)) &&
        check(Gen.float)(x => decode(s"\"$x\"", x)) &&
        roundTrip(0.0f, "0") &&
        roundTrip(Float.MinValue, "-340282350000000000000000000000000000000") &&
        roundTrip(Float.MaxValue, "340282350000000000000000000000000000000") &&
        // FIXME: Differs for different JVMs
        // roundTrip(1.0e17f, "100000000000000000") &&
        // roundTrip(1.2621775e-29f, "0.000000000000000000000000000012621775") &&
        roundTrip(0.33007812f, "0.33007812") &&
        roundTrip(102067.11f, "102067.11") &&
        roundTrip(1.6777216e7f, "16777216") &&
        roundTrip(1.0e-45f, "0.0000000000000000000000000000000000000000000014") &&
        roundTrip(1.0e-44f, "0.0000000000000000000000000000000000000000000098") &&
        roundTrip(6.895867e-31f, "0.0000000000000000000000000000006895867") &&
        roundTrip(1.595711e-5f, "0.00001595711") &&
        roundTrip(-1.5887592e7f, "-15887592") &&
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
        decode("-12345e-6789", 0.0f) &&
        decode("0.12345678901234567890e-9223372036854775799", 0.0f) &&
        decode("-0.12345678901234567890e-9223372036854775799", 0.0f) &&
        decode("12345678901234567890e-12345678901234567890", 0.0f) &&
        decode("-12345678901234567890e-12345678901234567890", 0.0f) &&
        decode("0e1", 0.0f) &&
        decode("1.", 1.0f) &&
        decode("01.0", 1.0f) &&
        decode("-01.0", -1.0f) &&
        decode("-0.0", 0.0f) &&
        encode(-0.0f, "0") &&
        encode(Float.NaN, "null") && // should be roundTrip(Float.NaN, "null")
        encode(Float.PositiveInfinity, "null") &&
        encode(Float.NegativeInfinity, "null") &&
        decodeError[Float]("1e+e", "Expected float, got: 1e+e at: .") &&
        decodeError[Float]("", "Expected float, got:  at: .") &&
        decodeError[Float]("1,", "Expected float, got: 1, at: .")
      } @@ jvmOnly,
      test("Double") {
        check(Gen.double)(x => decode(x.toString, x)) &&
        check(Gen.double)(x => decode(s"\"$x\"", x)) &&
        roundTrip(
          Double.MinValue,
          "-179769313486231570000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
        ) &&
        roundTrip(
          Double.MaxValue,
          "179769313486231570000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
        ) &&
        roundTrip(0.0, "0") &&
        roundTrip(0.001, "0.001") &&
        roundTrip(1.0e7, "10000000") &&
        roundTrip(8572.431613041595, "8572.431613041595") &&
        /* FIXME: Result differs between JVM and JS
        roundTrip(
          5.0e-324,
          "0.0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000049"
        ) &&
         */
        roundTrip(8.707795712926552e15, "8707795712926552") &&
        // FIXME: Differs for different JVMs
        // roundTrip(5.960464477539063e-8, "0.00000005960464477539063") &&
        roundTrip(-1.3821488797638562e14, "-138214887976385.62") &&
        roundTrip(9.223372036854776e18, "9223372036854776000") &&
        roundTrip(
          2.2250738585072014e-308,
          "0.000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000022250738585072014"
        ) &&
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
        decode("0e1", 0.0) &&
        decode("1.", 1.0) &&
        decode("01.0", 1.0) &&
        decode("-01.0", -1.0) &&
        decode("-0.0", 0.0) &&
        encode(-0.0, "0") &&
        encode(Double.NaN, "null") && // should be roundTrip(Float.NaN, "null")
        encode(Double.PositiveInfinity, "null") &&
        encode(Double.NegativeInfinity, "null") &&
        decodeError[Double]("1e+e", "Expected double, got: 1e+e at: .") &&
        decodeError[Double]("", "Expected double, got:  at: .") &&
        decodeError[Double]("1,", "Expected double, got: 1, at: .")
      },
      test("Char") {
        roundTrip('A', "A") &&
        roundTrip(',', ",") &&
        roundTrip('7', "\"7\"") &&
        roundTrip('-', "\"-\"") &&
        roundTrip(':', "\":\"") &&
        roundTrip(' ', "\" \"")
      },
      test("String") {
        roundTrip("line1\nline2", "\"line1\\nline2\"") &&
        roundTrip("tab\there", "\"tab\\there\"") &&
        roundTrip("return\rcarriage", "\"return\\rcarriage\"") &&
        roundTrip("C:\\Users\\path", "\"C:\\\\Users\\\\path\"") &&
        roundTrip("[test]", "\"[test]\"") &&
        roundTrip("{key}", "\"{key}\"") &&
        roundTrip("Hello", "Hello") &&
        roundTrip("Hello World", "Hello World") &&
        roundTrip("", "\"\"") &&
        roundTrip("a,b", "a,b") &&
        roundTrip("1,2,3", "1,2,3") &&
        roundTrip("Привіт", "Привіт") &&
        roundTrip("🎸🎧", "🎸🎧") &&
        roundTrip("日本語", "日本語") &&
        roundTrip("foo bar baz", "foo bar baz") &&
        roundTrip("αβγδ", "αβγδ") &&
        roundTrip("中文字符", "中文字符") &&
        roundTrip("עברית", "עברית") &&
        roundTrip("🚀🎉💻", "🚀🎉💻") &&
        roundTrip("日本:語", "\"日本:語\"") &&                         // colon forces quoting
        roundTrip("中文,字符", "中文,字符") &&                           // comma doesn't force quoting at top-level
        roundTrip("Привіт світ", "Привіт світ") &&               // spaces don't force quoting
        roundTrip(" 日本語", "\" 日本語\"") &&                         // leading space forces quoting
        roundTrip("🎸[test]", "\"🎸[test]\"") &&                 // brackets force quoting
        roundTrip("test\\", "\"test\\\\\"") &&                   // ends with backslash
        roundTrip("test\\\\", "\"test\\\\\\\\\"") &&             // ends with two backslashes
        roundTrip("path\\to\\file", "\"path\\\\to\\\\file\"") && // backslashes in the middle
        roundTrip("\\start", "\"\\\\start\"") &&                 // starts with backslash
        roundTrip("quote\"here", "\"quote\\\"here\"") &&         // embedded quote
        roundTrip[String](
          "tab:\there\nnewline\rcarriage\\backslash\"quote",
          "\"tab:\\there\\nnewline\\rcarriage\\\\backslash\\\"quote\""
        ) &&
        roundTrip[String]("", "\"\"") &&
        roundTrip[String](" hello", "\" hello\"") &&
        roundTrip[String]("hello ", "\"hello \"") &&
        roundTrip[String]("  ", "\"  \"") &&
        roundTrip[String]("true", "\"true\"") &&
        roundTrip[String]("false", "\"false\"") &&
        roundTrip[String]("null", "\"null\"") &&
        roundTrip[String]("42", "\"42\"") &&
        roundTrip[String]("-3.14", "\"-3.14\"") &&
        roundTrip[String]("1e6", "\"1e6\"") &&
        roundTrip[String]("05", "\"05\"") &&
        roundTrip[String]("key:value", "\"key:value\"") &&
        roundTrip[String]("10:30", "\"10:30\"") &&
        roundTrip[String]("say \"hi\"", "\"say \\\"hi\\\"\"") &&
        roundTrip[String]("path\\to", "\"path\\\\to\"") &&
        roundTrip[String]("line1\nline2", "\"line1\\nline2\"") &&
        roundTrip[String]("col1\tcol2", "\"col1\\tcol2\"") &&
        roundTrip[String]("return\r", "\"return\\r\"") &&
        roundTrip[String]("[array]", "\"[array]\"") &&
        roundTrip[String]("{object}", "\"{object}\"") &&
        roundTrip[String]("arr[0]", "\"arr[0]\"") &&
        roundTrip[String]("-", "\"-\"") &&
        roundTrip[String]("-flag", "\"-flag\"") &&
        roundTrip[String]("--option", "\"--option\"")
      },
      test("BigInt") {
        check(Gen.bigInt(BigInt("-" + "9" * 20), BigInt("9" * 20)))(x => roundTrip(x, x.toString)) &&
        check(Gen.bigInt(BigInt("-" + "9" * 20), BigInt("9" * 20)))(x => decode(s"\"$x\"", x)) &&
        roundTrip(BigInt(0), "0") &&
        roundTrip(BigInt("-" + "9" * 3), "-" + "9" * 3) &&
        roundTrip(BigInt("9" * 30), "9" * 30) &&
        roundTrip(BigInt("9" * 300), "9" * 300) &&
        decode("-0", BigInt(0)) &&
        decode("01", BigInt(1)) &&
        decode("-01", BigInt(-1)) &&
        encode(BigInt("9" * 1000), "9" * 1000) &&
        decodeError[BigInt]("", "Expected BigInt, got:  at: .") &&
        decodeError[BigInt]("-a", "Expected BigInt, got: -a at: .") &&
        decodeError[BigInt]("1.0", "Expected BigInt, got: 1.0 at: .") &&
        decodeError[BigInt]("1e1", "Expected BigInt, got: 1e1 at: .") &&
        decodeError[BigInt]("1E1", "Expected BigInt, got: 1E1 at: .") &&
        decodeError[BigInt]("null", "Expected BigInt, got: null at: .")
      },
      test("BigDecimal") {
        check(Gen.bigDecimal(BigDecimal("-" + "9" * 20), BigDecimal("9" * 20)))(x => roundTrip(x, x.toString)) &&
        check(Gen.bigDecimal(BigDecimal("-" + "9" * 20), BigDecimal("9" * 20)))(x => decode(s"\"$x\"", x)) &&
        roundTrip(BigDecimal("0.0"), "0") &&
        roundTrip(BigDecimal("126.09999999999999001"), "126.09999999999999001") &&
        roundTrip(BigDecimal("0.0287500000000000000000"), "0.02875") &&
        roundTrip(BigDecimal("1e20"), "100000000000000000000") &&
        roundTrip(BigDecimal("1E-10"), "0.0000000001") &&
        roundTrip(BigDecimal("-1." + "1" * 3 + "E+1234"), "-1111" + "0" * 1231) &&
        // FIXME: throws java.nio.BufferOverflowException
        // encode(BigDecimal("1." + "1" * 30 + "E+123456789"), "1." + "1" * 30 + "E+123456789") &&
        decode("1." + "1" * 300 + "E+1234", BigDecimal("1." + "1" * 300 + "E+1234")) &&
        decode("0e1", BigDecimal(0.0)) &&
        decode("1.", BigDecimal(1.0)) &&
        decode("-0.0", BigDecimal(0)) &&
        decode("01.0", BigDecimal(1)) &&
        decode("-01.0", BigDecimal(-1)) &&
        encode(BigDecimal("1." + "1" * 1000 + "E+1234"), "1" * 1001 + "0" * 234) &&
        decodeError[BigDecimal]("", "Expected BigDecimal, got:  at: .") &&
        decodeError[BigDecimal]("1,", "Expected BigDecimal, got: 1, at: .") &&
        decodeError[BigDecimal]("1e+e", "Expected BigDecimal, got: 1e+e at: .") &&
        decodeError[BigDecimal]("--8", "Expected BigDecimal, got: --8 at: .") &&
        decodeError[BigDecimal]("null", "Expected BigDecimal, got: null at: .") &&
        decodeError[BigDecimal]("1e11111111111", "Expected BigDecimal, got: 1e11111111111 at: .")
      },
      test("DayOfWeek") {
        check(JavaTimeGen.genDayOfWeek)(x => roundTrip(x, x.toString)) &&
        check(JavaTimeGen.genDayOfWeek)(x => decode(s"\"$x\"", x)) &&
        roundTrip(DayOfWeek.MONDAY, "MONDAY") &&
        roundTrip(DayOfWeek.FRIDAY, "FRIDAY") &&
        decodeError[DayOfWeek]("FUNDAY", "Invalid day of week: FUNDAY at: .")
      },
      test("Duration") {
        check(JavaTimeGen.genDuration)(x => roundTrip(x, x.toString)) &&
        check(JavaTimeGen.genDuration)(x => decode(s"\"$x\"", x)) &&
        roundTrip(Duration.ofSeconds(0), "PT0S") &&
        roundTrip(Duration.ofHours(2), "PT2H") &&
        decodeError[Duration]("5 hours", "Invalid duration: 5 hours at: .")
      },
      test("Instant") {
        check(JavaTimeGen.genInstant)(x => roundTrip(x, s"\"$x\"")) &&
        check(JavaTimeGen.genInstant)(x => decode(x.toString, x)) &&
        roundTrip(Instant.EPOCH, "\"1970-01-01T00:00:00Z\"") &&
        decodeError[Instant]("yesterday", "Invalid instant: yesterday at: .")
      },
      test("LocalDate") {
        check(JavaTimeGen.genLocalDate)(x =>
          roundTrip(
            x,
            if (x.getYear < 0) s"\"$x\""
            else x.toString
          )
        ) &&
        check(JavaTimeGen.genLocalDate)(x => decode(s"\"$x\"", x)) &&
        check(JavaTimeGen.genLocalDate)(x => decode(x.toString, x)) &&
        roundTrip(LocalDate.of(2025, 1, 11), "2025-01-11") &&
        decodeError[LocalDate]("2025/01/11", "Invalid local date: 2025/01/11 at: .")
      },
      test("LocalDateTime") {
        check(JavaTimeGen.genLocalDateTime)(x => roundTrip(x, s"\"$x\"")) &&
        check(JavaTimeGen.genLocalDateTime)(x => decode(x.toString, x)) &&
        roundTrip(LocalDateTime.of(2025, 1, 11, 10, 30), "\"2025-01-11T10:30\"")
      },
      test("LocalTime") {
        check(JavaTimeGen.genLocalTime)(x => roundTrip(x, s"\"$x\"")) &&
        check(JavaTimeGen.genLocalTime)(x => decode(x.toString, x)) &&
        roundTrip(LocalTime.of(10, 30), "\"10:30\"") &&
        decodeError[LocalTime]("25:99", "Invalid local time: 25:99 at: .")
      },
      test("Month") {
        check(JavaTimeGen.genMonth)(x => roundTrip(x, x.toString)) &&
        check(JavaTimeGen.genMonth)(x => decode(s"\"$x\"", x)) &&
        roundTrip(Month.JANUARY, "JANUARY") &&
        decodeError[Month]("SMARCH", "Invalid month: SMARCH at: .")
      },
      test("MonthDay") {
        check(JavaTimeGen.genMonthDay)(x => roundTrip(x, s"\"$x\"")) &&
        check(JavaTimeGen.genMonthDay)(x => decode(x.toString, x)) &&
        roundTrip(MonthDay.of(1, 11), "\"--01-11\"")
      },
      test("OffsetDateTime") {
        check(JavaTimeGen.genOffsetDateTime)(x => roundTrip(x, s"\"$x\"")) &&
        check(JavaTimeGen.genOffsetDateTime)(x => decode(x.toString, x)) &&
        roundTrip(
          OffsetDateTime.of(LocalDateTime.of(2025, 1, 11, 10, 30), ZoneOffset.ofHours(1)),
          "\"2025-01-11T10:30+01:00\""
        )
      },
      test("OffsetTime") {
        check(JavaTimeGen.genOffsetTime)(x => roundTrip(x, s"\"$x\"")) &&
        check(JavaTimeGen.genOffsetTime)(x => decode(x.toString, x)) &&
        roundTrip(OffsetTime.of(LocalTime.of(10, 30), ZoneOffset.ofHours(1)), "\"10:30+01:00\"")
      },
      test("Period") {
        check(JavaTimeGen.genPeriod)(x => roundTrip(x, x.toString)) &&
        check(JavaTimeGen.genPeriod)(x => decode(s"\"$x\"", x)) &&
        roundTrip(Period.ofDays(0), "P0D") &&
        roundTrip(Period.of(1, 2, 3), "P1Y2M3D")
      },
      test("Year") {
        check(JavaTimeGen.genYear)(x => roundTrip(x, x.toString)) &&
        check(JavaTimeGen.genYear)(x => decode(s"\"$x\"", x)) &&
        roundTrip(Year.of(2025), "2025")
      },
      test("YearMonth") {
        check(JavaTimeGen.genYearMonth)(x =>
          roundTrip(
            x,
            if (x.getYear < 0) s"\"$x\""
            else if (x.getYear >= 10000) "+" + x.toString
            else x.toString
          )
        ) &&
        check(JavaTimeGen.genYearMonth)(x =>
          decode(
            if (x.getYear >= 10000) s"\"+$x\""
            else s"\"$x\"",
            x
          )
        ) &&
        check(JavaTimeGen.genYearMonth)(x =>
          decode(
            if (x.getYear >= 10000) "+" + x.toString
            else x.toString,
            x
          )
        ) &&
        roundTrip(YearMonth.of(2025, 1), "2025-01")
      },
      test("ZoneId") {
        check(JavaTimeGen.genZoneId)(x =>
          roundTrip(
            x, {
              var s = x.toString
              if (s.indexOf(':') >= 0) s = s"\"$s\""
              s
            }
          )
        ) &&
        check(JavaTimeGen.genZoneId)(x => decode(s"\"$x\"", x)) &&
        check(JavaTimeGen.genZoneId)(x => decode(x.toString, x)) &&
        roundTrip(ZoneId.of("UTC"), "UTC") &&
        decodeError[ZoneId]("Fake/Timezone", "Invalid zone id: Fake/Timezone at: .")
      },
      test("ZoneOffset") {
        check(JavaTimeGen.genZoneOffset)(x =>
          roundTrip(
            x, {
              var s = x.toString
              if (s.indexOf(':') >= 0) s = s"\"$s\""
              s
            }
          )
        ) &&
        check(JavaTimeGen.genZoneOffset)(x => decode(s"\"$x\"", x)) &&
        check(JavaTimeGen.genZoneOffset)(x => decode(x.toString, x)) &&
        roundTrip(ZoneOffset.ofHours(0), "Z") &&
        roundTrip(ZoneOffset.ofHours(1), "\"+01:00\"")
      },
      test("ZonedDateTime") {
        check(JavaTimeGen.genZonedDateTime)(x => roundTrip(x, s"\"$x\"")) &&
        check(JavaTimeGen.genZonedDateTime)(x => decode(x.toString, x)) &&
        roundTrip(
          ZonedDateTime.of(LocalDateTime.of(2025, 1, 11, 10, 30), ZoneId.of("UTC")),
          "\"2025-01-11T10:30Z[UTC]\""
        )
      },
      test("Currency") {
        check(Gen.currency)(x => roundTrip(x, x.toString)) &&
        check(Gen.currency)(x => decode(s"\"$x\"", x)) &&
        roundTrip(Currency.getInstance("USD"), "USD") &&
        decodeError[Currency]("FAKE", "Invalid currency: FAKE at: .")
      },
      test("UUID") {
        check(Gen.uuid)(x => roundTrip(x, x.toString)) &&
        check(Gen.uuid)(x => decode(s"\"$x\"", x)) &&
        roundTrip(
          UUID.fromString("00000000-0000-0001-0000-000000000001"),
          "00000000-0000-0001-0000-000000000001"
        ) &&
        decodeError[UUID]("not-a-uuid", "Invalid UUID: not-a-uuid at: .")
      }
    ),
    suite("records")(
      test("simple record") {
        roundTrip(
          Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
          """bl: true
            |b: 1
            |sh: 2
            |i: 3
            |l: 4
            |f: 5
            |d: 6
            |c: "7"
            |s: VVV""".stripMargin
        )
      },
      test("nested record") {
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(false, 2: Byte, 3: Short, 4, 5L, 6.0f, 7.0, '8', "WWW")
          ),
          """r1_1:
            |  bl: true
            |  b: 1
            |  sh: 2
            |  i: 3
            |  l: 4
            |  f: 5
            |  d: 6
            |  c: "7"
            |  s: VVV
            |r1_2:
            |  bl: false
            |  b: 2
            |  sh: 3
            |  i: 4
            |  l: 5
            |  f: 6
            |  d: 7
            |  c: "8"
            |  s: WWW""".stripMargin
        ) &&
        decodeError[Record2](
          """r1_1:
            |  bl: true
            |  b: 1
            |  sh: 2
            |  i: 3
            |  l: 4
            |  f: 5
            |  d: 6
            |  c: "7"
            |  s: VVV
            |r1_2:
            |  bl: false
            |  b: 2
            |  sh: 3
            |  i: 4
            |  l: 5
            |  f: 6
            |  d: 7
            |  c: ""
            |  s: WWW""".stripMargin,
          "Expected single char, got:  at: .r1_2.c"
        )
      },
      test("record with optional field") {
        roundTrip(
          Record4((), Some("VVV")),
          """hidden: null
            |optKey: VVV""".stripMargin
        ) &&
        roundTrip(Record4((), None), "hidden: null") &&
        roundTrip(AllOptional(None, None, None), "") &&
        roundTrip(AllOptional(None, None, Some(true)), "c: true") &&
        roundTrip(
          RecordWithOptionalMiddle(1, Some("hello"), 2),
          """first: 1
            |optMiddle: hello
            |last: 2""".stripMargin
        ) &&
        roundTrip(
          RecordWithOptionalMiddle(1, None, 2),
          """first: 1
            |last: 2""".stripMargin
        )
      },
      test("record with required optional field") {
        val codec = deriveCodec[Record4](_.withRequireOptionFields(true))
        roundTrip(
          Record4((), Some("VVV")),
          """hidden: null
            |optKey: VVV""".stripMargin,
          codec
        ) &&
        decodeError[Record4]("hidden: null", "Missing required field: optKey at: .", codec)
      },
      test("record with non-transient optional encodes null values in objects") {
        roundTrip(
          Record4((), None),
          """hidden: null
            |optKey: null""".stripMargin,
          deriveCodec[Record4](_.withTransientNone(false))
        )
      },
      test("record with renamed, aliased, and transient fields") {
        encode(Record5(BigInt(1), BigDecimal(2.0)), "bigInt: 1\nbd: 2") &&
        decode("bi: 1\nbd: 2.0", Record5(BigInt(1), BigDecimal(2.0)))
      },
      test("simple record with fields that have default values") {
        roundTrip(Record6(), "") &&
        roundTrip(Record6(bl = true, s = "WWW"), "bl: true\ns: WWW") &&
        roundTrip(
          Record6(true, 2: Byte, 3: Short, 4, 5L, 6.0f, 7.0, '8', "WWW"),
          "bl: true\nb: 2\nsh: 3\ni: 4\nl: 5\nf: 6\nd: 7\nc: \"8\"\ns: WWW"
        )
      },
      test("extra fields are ignored by default") {
        decode(
          """name: Alice
            |age: 25
            |unknownField: ignored""".stripMargin,
          SimplePerson("Alice", 25)
        )
      },
      test("extra fields cause error when rejectExtraFields is true") {
        decodeError(
          """name: Alice
            |age: 25
            |unknownField: ignored""".stripMargin,
          "Unexpected field: unknownField at: .",
          deriveCodec[SimplePerson](_.withRejectExtraFields(true))
        )
      },
      test("known fields work with rejectExtraFields true") {
        decode(
          """name: Bob
            |age: 30""".stripMargin,
          SimplePerson("Bob", 30),
          deriveCodec[SimplePerson](_.withRejectExtraFields(true))
        )
      },
      test("user profile with address") {
        roundTrip(
          UserProfile(
            "John Doe",
            30,
            "john@example.com",
            Address("123 Main St", "Anytown", "12345")
          ),
          """name: John Doe
            |age: 30
            |email: john@example.com
            |address:
            |  street: 123 Main St
            |  city: Anytown
            |  zip: "12345"""".stripMargin
        )
      },
      test("collection field alongside other fields") {
        roundTrip(
          Order("ORD-001", List("Widget", "Gadget"), BigDecimal("99.99")),
          """orderId: ORD-001
            |items[2]: Widget,Gadget
            |total: 99.99""".stripMargin
        ) &&
        roundTrip(
          Order("ORD-001", List.empty, BigDecimal("99.99")),
          """orderId: ORD-001
            |total: 99.99""".stripMargin
        )
      },
      test("collection field alongside other fields with withTransientEmptyCollection false") {
        roundTrip(
          Order("ORD-001", List.empty, BigDecimal("99.99")),
          """orderId: ORD-001
            |items[0]:
            |total: 99.99""".stripMargin,
          deriveCodec[Order](_.withTransientEmptyCollection(false))
        )
      },
      test("collection field alongside other fields with withRequireCollectionFields true") {
        decode(
          """orderId: ORD-001
            |items[0]:
            |total: 99.99""".stripMargin,
          Order("ORD-001", List.empty, BigDecimal("99.99")),
          deriveCodec[Order](_.withRequireCollectionFields(true))
        ) &&
        decodeError[Order](
          """orderId: ORD-001
            |total: 99.99""".stripMargin,
          "Missing required field: items at: .",
          deriveCodec[Order](_.withRequireCollectionFields(true))
        )
      },
      test("default value field") {
        roundTrip(
          ServerConfig("localhost", 80, Some(true)),
          """host: localhost
            |ssl: true""".stripMargin
        ) &&
        roundTrip(
          ServerConfig("localhost", 8080, Some(true)),
          """host: localhost
            |port: 8080
            |ssl: true""".stripMargin
        )
      },
      test("default value field with withTransientDefaultValue false") {
        val codec = deriveCodec[ServerConfig](_.withTransientDefaultValue(false))
        roundTrip(
          ServerConfig("localhost", 80, Some(true)),
          """host: localhost
            |port: 80
            |ssl: true""".stripMargin,
          codec
        ) &&
        roundTrip(
          ServerConfig("localhost", 8080, Some(true)),
          """host: localhost
            |port: 8080
            |ssl: true""".stripMargin,
          codec
        )
      },
      test("default value field with withRequireDefaultValueFields true") {
        val codec = deriveCodec[ServerConfig](_.withRequireDefaultValueFields(true))
        decode(
          """host: localhost
            |port: 8080
            |ssl: true""".stripMargin,
          ServerConfig("localhost", 8080, Some(true)),
          codec
        ) &&
        decodeError[ServerConfig](
          """host: localhost
            |ssl: true""".stripMargin,
          "Missing required field: port at: .",
          codec
        )
      },
      test("map field alongside other fields") {
        roundTrip(
          RecordWithMapField("test", Map("role" -> "admin", "status" -> "active"), 42),
          """name: test
            |metadata:
            |  role: admin
            |  status: active
            |count: 42""".stripMargin
        ) &&
        roundTrip(
          RecordWithMapField("test", Map.empty, 42),
          """name: test
            |count: 42""".stripMargin
        )
      },
      test("map field alongside other fields with withTransientEmptyCollection false") {
        roundTrip(
          RecordWithMapField("test", Map.empty, 42),
          """name: test
            |metadata:
            |count: 42""".stripMargin,
          deriveCodec[RecordWithMapField](_.withTransientEmptyCollection(false))
        )
      },
      test("map field alongside other fields with withRequireCollectionFields true") {
        val codec = deriveCodec[RecordWithMapField](_.withRequireCollectionFields(true))
        decode(
          """name: test
            |metadata:
            |  role: admin
            |  status: active
            |count: 42""".stripMargin,
          RecordWithMapField("test", Map("role" -> "admin", "status" -> "active"), 42),
          codec
        ) &&
        decodeError[RecordWithMapField](
          """name: test
            |count: 42""".stripMargin,
          "Missing required field: metadata at: .",
          codec
        )
      },
      test("record with custom codecs of different field mapping") {
        roundTrip(
          CamelPascalSnakeKebabCases(1, 2, 3, 4, 5, 6, 7, 8),
          """camelCase: 1
            |PascalCase: 2
            |snake_case: 3
            |"kebab-case": 4
            |camel1: 5
            |Pascal1: 6
            |snake_1: 7
            |"kebab-1": 8""".stripMargin
        ) &&
        roundTrip(
          CamelPascalSnakeKebabCases(1, 2, 3, 4, 5, 6, 7, 8),
          """CAMELCASE: 1
            |PASCALCASE: 2
            |SNAKE_CASE: 3
            |"KEBAB-CASE": 4
            |CAMEL1: 5
            |PASCAL1: 6
            |SNAKE_1: 7
            |"KEBAB-1": 8""".stripMargin,
          deriveCodec[CamelPascalSnakeKebabCases](_.withFieldNameMapper(Custom(_.toUpperCase)))
        ) &&
        roundTrip(
          CamelPascalSnakeKebabCases(1, 2, 3, 4, 5, 6, 7, 8),
          """camel_case: 1
            |pascal_case: 2
            |snake_case: 3
            |kebab_case: 4
            |camel1: 5
            |pascal1: 6
            |snake_1: 7
            |kebab_1: 8""".stripMargin,
          deriveCodec[CamelPascalSnakeKebabCases](_.withFieldNameMapper(SnakeCase))
        ) &&
        roundTrip(
          CamelPascalSnakeKebabCases(1, 2, 3, 4, 5, 6, 7, 8),
          """"camel-case": 1
            |"pascal-case": 2
            |"snake-case": 3
            |"kebab-case": 4
            |camel1: 5
            |pascal1: 6
            |"snake-1": 7
            |"kebab-1": 8""".stripMargin,
          deriveCodec[CamelPascalSnakeKebabCases](_.withFieldNameMapper(KebabCase))
        ) &&
        roundTrip(
          CamelPascalSnakeKebabCases(1, 2, 3, 4, 5, 6, 7, 8),
          """CamelCase: 1
            |PascalCase: 2
            |SnakeCase: 3
            |KebabCase: 4
            |Camel1: 5
            |Pascal1: 6
            |Snake1: 7
            |Kebab1: 8""".stripMargin,
          deriveCodec[CamelPascalSnakeKebabCases](_.withFieldNameMapper(PascalCase))
        ) &&
        roundTrip(
          CamelPascalSnakeKebabCases(1, 2, 3, 4, 5, 6, 7, 8),
          """camelCase: 1
            |pascalCase: 2
            |snakeCase: 3
            |kebabCase: 4
            |camel1: 5
            |pascal1: 6
            |snake1: 7
            |kebab1: 8""".stripMargin,
          deriveCodec[CamelPascalSnakeKebabCases](_.withFieldNameMapper(CamelCase))
        )
      }
    ),
    suite("variants")(
      test("case object enumeration") {
        roundTrip[TrafficLight](TrafficLight.Green, "Green") &&
        roundTrip[TrafficLight](TrafficLight.Yellow, "Yellow") &&
        roundTrip[TrafficLight](TrafficLight.Red, "Red")
      },
      test("case object enumeration with case key renaming using case name mapper") {
        roundTrip[TrafficLight](
          TrafficLight.Green,
          """GREEN""",
          deriveCodec[TrafficLight](_.withCaseNameMapper(NameMapper.Custom(_.toUpperCase)))
        ) &&
        roundTrip[TrafficLight](
          TrafficLight.Yellow,
          """yellow""",
          deriveCodec[TrafficLight](_.withCaseNameMapper(NameMapper.SnakeCase))
        )
      },
      test("case object enumeration with toggled off string representation for values") {
        roundTrip[TrafficLight](
          TrafficLight.Green,
          """Green:""",
          deriveCodec[TrafficLight](_.withEnumValuesAsStrings(false))
        )
      },
      test("option") {
        roundTrip(Option(42), "42") &&
        roundTrip[Option[Int]](None, "null")
      },
      test("key discriminator roundtrips correctly") {
        roundTrip[Pet](
          Pet.Cat("Whiskers", 9),
          """Cat:
            |  name: Whiskers
            |  lives: 9""".stripMargin
        ) &&
        roundTrip[Pet](
          Pet.Dog("Buddy", "Lab"),
          """Dog:
            |  name: Buddy
            |  breed: Lab""".stripMargin
        )
      },
      test("field discriminator roundtrips correctly") {
        roundTrip[Pet](
          Pet.Dog("Buddy", "Labrador"),
          """type: Dog
            |name: Buddy
            |breed: Labrador""".stripMargin,
          deriveCodec[Pet](_.withDiscriminatorKind(DiscriminatorKind.Field("type")))
        )
      },
      test("None discriminator roundtrips correctly") {
        roundTrip[Pet](
          Pet.Dog("Buddy", "Labrador"),
          """name: Buddy
            |breed: Labrador""".stripMargin,
          deriveCodec[Pet](_.withDiscriminatorKind(DiscriminatorKind.None))
        ) &&
        roundTrip[Pet](
          Pet.Cat("Whiskers", 9),
          """name: Whiskers
            |lives: 9""".stripMargin,
          deriveCodec[Pet](_.withDiscriminatorKind(DiscriminatorKind.None))
        )
      }
    ),
    suite("sequences")(
      test("primitive values") {
        roundTrip(List(1, 2, 3), "[3]: 1,2,3") &&
        roundTrip(IntList(List(1, 2, 3)), "xs[3]: 1,2,3")
      },
      test("empty sequence") {
        roundTrip[List[Int]](Nil, "[0]:") &&
        roundTrip(IntList(Nil), "")
      },
      test("string values") {
        roundTrip(List("hello", "world"), "[2]: hello,world") &&
        roundTrip(StringList(List("hello", "world")), "xs[2]: hello,world")
      },
      test("list with many elements") {
        roundTrip((1 to 10).toList, "[10]: 1,2,3,4,5,6,7,8,9,10") &&
        roundTrip(IntList((1 to 10).toList), "xs[10]: 1,2,3,4,5,6,7,8,9,10")
      },
      test("ArrayFormat.Tabular encodes record arrays in tabular format") {
        roundTrip(
          PersonList(List(SimplePerson("Alice", 25), SimplePerson("Bob", 30))),
          """people[2]{name,age}:
            |  Alice,25
            |  Bob,30""".stripMargin,
          deriveCodec[PersonList](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("ArrayFormat.List encodes record arrays in list format") {
        roundTrip(
          PersonList(List(SimplePerson("Alice", 25), SimplePerson("Bob", 30))),
          """people[2]:
            |  - name: Alice
            |    age: 25
            |  - name: Bob
            |    age: 30""".stripMargin,
          deriveCodec[PersonList](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("ArrayFormat.Tabular with custom delimiter") {
        roundTrip(
          PersonList(List(SimplePerson("Alice", 25), SimplePerson("Bob", 30))),
          """people[2|]{name|age}:
            |  Alice|25
            |  Bob|30""".stripMargin,
          deriveCodec[PersonList](_.withArrayFormat(ArrayFormat.Tabular).withDelimiter(Delimiter.Pipe))
        )
      },
      test("ArrayFormat.List forces list format even for primitives") {
        roundTrip(
          IntList(List(1, 2, 3)),
          """xs[3]:
            |  - 1
            |  - 2
            |  - 3""".stripMargin,
          deriveCodec[IntList](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("withDelimiter affects inline arrays") {
        roundTrip(
          IntList(List(1, 2, 3)),
          """xs[3|]: 1|2|3""",
          deriveCodec[IntList](_.withDelimiter(Delimiter.Pipe))
        )
      }
    ),
    suite("maps")(
      test("string key map") {
        roundTrip(
          StringIntMap(Map("a" -> 1, "b" -> 2)),
          """a: 1
            |b: 2""".stripMargin
        )
      },
      test("empty map") {
        roundTrip(StringIntMap(Map.empty), "")
      },
      test("map with simple string values") {
        roundTrip(
          StringStringMap(Map("a" -> "hello", "b" -> "world")),
          """a: hello
            |b: world""".stripMargin
        )
      }
    ),
    suite("wrapper")(
      test("top-level") {
        roundTrip[UserId](UserId(1234567890123456789L), "1234567890123456789") &&
        roundTrip[Email](Email("john@gmail.com"), "john@gmail.com") &&
        decodeError[Email]("john&gmail.com", "expected e-mail at: .wrapped")
      },
      test("as a map key") {
        roundTrip(
          Map(UserId(1234567890123456789L) -> Email("backup@gmail.com")),
          """"1234567890123456789": backup@gmail.com"""
        ) &&
        decodeError[Map[Long, Email]](
          """1234567890123456789: backup&gmail.com""",
          "expected e-mail at: .atKey(1234567890123456789).wrapped"
        )
      }
    ),
    suite("dynamic")(
      test("DynamicValue Map encodes correctly") {
        val map = DynamicValue.Map(
          Chunk(
            (
              DynamicValue.Primitive(PrimitiveValue.String("key1")),
              DynamicValue.Primitive(PrimitiveValue.String("value1"))
            ),
            (DynamicValue.Primitive(PrimitiveValue.Int(42)), DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
          )
        )
        val codec   = ToonBinaryCodec.dynamicValueCodec
        val encoded = new String(codec.encode(map), java.nio.charset.StandardCharsets.UTF_8).trim
        assertTrue(encoded.contains("key1: value1")) &&
        assertTrue(encoded.contains("42: true"))
      },
      test("DynamicValue - default config") {
        check(ToonDynamicValueGen.genDynamicValue) { value =>
          val codec      = ToonBinaryCodec.dynamicValueCodec
          val encoded    = codec.encodeToString(value, WriterConfig)
          val decoded    = codec.decode(encoded, ReaderConfig)
          val normalized = ToonDynamicValueGen.normalize(value)
          assertTrue(decoded == Right(normalized))
        }
      },
      test("DynamicValue - tab delimiter") {
        check(ToonDynamicValueGen.genDynamicValue) { value =>
          val codec      = ToonBinaryCodec.dynamicValueCodec
          val writerCfg  = WriterConfig.withDelimiter(Delimiter.Tab)
          val readerCfg  = ReaderConfig.withDelimiter(Delimiter.Tab)
          val encoded    = codec.encodeToString(value, writerCfg)
          val decoded    = codec.decode(encoded, readerCfg)
          val normalized = ToonDynamicValueGen.normalize(value)
          assertTrue(decoded == Right(normalized))
        }
      },
      test("DynamicValue - pipe delimiter") {
        check(ToonDynamicValueGen.genDynamicValue) { value =>
          val codec      = ToonBinaryCodec.dynamicValueCodec
          val writerCfg  = WriterConfig.withDelimiter(Delimiter.Pipe)
          val readerCfg  = ReaderConfig.withDelimiter(Delimiter.Pipe)
          val encoded    = codec.encodeToString(value, writerCfg)
          val decoded    = codec.decode(encoded, readerCfg)
          val normalized = ToonDynamicValueGen.normalize(value)
          assertTrue(decoded == Right(normalized))
        }
      },
      test("DynamicValue - key folding") {
        check(ToonDynamicValueGen.genDynamicValue) { value =>
          val codec      = ToonBinaryCodec.dynamicValueCodec
          val writerCfg  = WriterConfig.withKeyFolding(KeyFolding.Safe)
          val readerCfg  = ReaderConfig.withExpandPaths(PathExpansion.Safe)
          val encoded    = codec.encodeToString(value, writerCfg)
          val decoded    = codec.decode(encoded, readerCfg)
          val normalized = ToonDynamicValueGen.normalize(value)
          assertTrue(decoded == Right(normalized))
        }
      },
      test("DynamicValue - discriminatorField with type") {
        check(ToonDynamicValueGen.genDynamicValue) { value =>
          val codec      = ToonBinaryCodec.dynamicValueCodec
          val discField  = Some("type")
          val writerCfg  = WriterConfig.withDiscriminatorField(discField)
          val readerCfg  = ReaderConfig.withDiscriminatorField(discField)
          val encoded    = codec.encodeToString(value, writerCfg)
          val decoded    = codec.decode(encoded, readerCfg)
          val normalized = ToonDynamicValueGen.normalize(value, discField)
          assertTrue(decoded == Right(normalized))
        }
      },
      test("DynamicValue - discriminatorField with $type") {
        check(ToonDynamicValueGen.genDynamicValue) { value =>
          val codec      = ToonBinaryCodec.dynamicValueCodec
          val discField  = Some("$type")
          val writerCfg  = WriterConfig.withDiscriminatorField(discField)
          val readerCfg  = ReaderConfig.withDiscriminatorField(discField)
          val encoded    = codec.encodeToString(value, writerCfg)
          val decoded    = codec.decode(encoded, readerCfg)
          val normalized = ToonDynamicValueGen.normalize(value, discField)
          assertTrue(decoded == Right(normalized))
        }
      },
      test("DynamicValue - Variant roundtrips with discriminatorField") {
        val variant = DynamicValue.Variant(
          "Dog",
          DynamicValue.Record(Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Buddy")))))
        )
        val codec     = ToonBinaryCodec.dynamicValueCodec
        val discField = Some("type")
        val writerCfg = WriterConfig.withDiscriminatorField(discField)
        val readerCfg = ReaderConfig.withDiscriminatorField(discField)
        val encoded   = codec.encodeToString(variant, writerCfg)
        val decoded   = codec.decode(encoded, readerCfg)
        assertTrue(
          encoded.contains("type: Dog"),
          encoded.contains("name: Buddy"),
          decoded == Right(variant)
        )
      },
      test("leading zeros decode as string, not number") {
        decodeDynamic(
          "value: 05",
          record("value" -> dynamicStr("05")),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("multiple leading zeros decode as string") {
        decodeDynamic(
          "value: 007",
          record("value" -> dynamicStr("007")),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("negative with leading zero decodes as string") {
        decodeDynamic(
          "value: -05",
          record("value" -> dynamicStr("-05")),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("single zero is a valid number") {
        decodeDynamic(
          "value: 0",
          record("value" -> dynamicInt(0)),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("zero with decimal is a valid number") {
        decodeDynamic(
          "value: 0.5",
          record("value" -> DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("0.5")))),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
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

  object Record1 {
    implicit val schema: Schema[Record1] = Schema.derived
  }

  case class Record2(r1_1: Record1, r1_2: Record1)

  object Record2 {
    implicit val schema: Schema[Record2] = Schema.derived
  }

  case class Record4(hidden: Unit, optKey: Option[String])

  object Record4 {
    implicit val schema: Schema[Record4] = Schema.derived
  }

  case class Record5(
    @Modifier.alias("bi") bigInt: BigInt,
    @Modifier.rename("bd") bigDecimal: BigDecimal,
    @Modifier.transient x: String = "x"
  )

  object Record5 extends CompanionOptics[Record5] {
    implicit val schema: Schema[Record5] = Schema.derived
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
  }

  case class IntList(xs: List[Int])

  object IntList {
    implicit val schema: Schema[IntList] = Schema.derived
  }

  case class StringList(xs: List[String])

  object StringList {
    implicit val schema: Schema[StringList] = Schema.derived
  }

  case class StringIntMap(m: Map[String, Int])

  object StringIntMap {
    implicit val schema: Schema[StringIntMap] = Schema.derived
  }

  case class SimplePerson(name: String, age: Int)

  object SimplePerson {
    implicit val schema: Schema[SimplePerson] = Schema.derived
  }

  sealed trait TrafficLight

  object TrafficLight {
    implicit val schema: Schema[TrafficLight] = Schema.derived

    case object Red    extends TrafficLight
    case object Yellow extends TrafficLight
    case object Green  extends TrafficLight
  }

  sealed trait Pet

  object Pet {
    implicit val schema: Schema[Pet] = Schema.derived

    case class Cat(name: String, lives: Int)    extends Pet
    case class Dog(name: String, breed: String) extends Pet
  }

  case class NestedRecord(inner: Option[NestedRecord], value: String)

  object NestedRecord {
    implicit val schema: Schema[NestedRecord] = Schema.derived
  }

  case class AllOptional(a: Option[Int], b: Option[String], c: Option[Boolean])

  object AllOptional {
    implicit val schema: Schema[AllOptional] = Schema.derived
  }

  case class StringStringMap(m: Map[String, String])

  object StringStringMap {
    implicit val schema: Schema[StringStringMap] = Schema.derived
  }

  case class Address(street: String, city: String, zip: String)

  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class UserProfile(name: String, age: Int, email: String, address: Address)

  object UserProfile {
    implicit val schema: Schema[UserProfile] = Schema.derived
  }

  case class Order(orderId: String, items: List[String], total: BigDecimal)

  object Order {
    implicit val schema: Schema[Order] = Schema.derived
  }

  case class ServerConfig(host: String, port: Int = 80, ssl: Option[Boolean])

  object ServerConfig {
    implicit val schema: Schema[ServerConfig] = Schema.derived
  }

  case class PersonList(people: List[SimplePerson])

  case class RecordWithOptionalMiddle(first: Int, optMiddle: Option[String], last: Int)

  object RecordWithOptionalMiddle {
    implicit val schema: Schema[RecordWithOptionalMiddle] = Schema.derived
  }

  object PersonList {
    implicit val schema: Schema[PersonList] = Schema.derived
  }

  case class RecordWithMapField(name: String, metadata: Map[String, String], count: Int)

  object RecordWithMapField {
    implicit val schema: Schema[RecordWithMapField] = Schema.derived
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

  case class UserId(value: Long)

  object UserId {
    implicit lazy val schema: Schema[UserId] =
      Schema[Long].transform[UserId](x => new UserId(x), _.value)
  }

  case class Email(value: String)

  object Email {
    private[this] val EmailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".r

    implicit lazy val schema: Schema[Email] =
      Schema[String].transform[Email](
        {
          case x @ EmailRegex(_*) => new Email(x)
          case _                  => throw SchemaError.validationFailed("expected e-mail")
        },
        _.value
      )
  }
}
