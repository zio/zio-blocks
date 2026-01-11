package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema.Schema
import java.time._

/**
 * Comprehensive tests for all primitive type codecs.
 */
object ComprehensivePrimitiveSpec extends ZIOSpecDefault {
  def spec = suite("ComprehensivePrimitive")(
    suite("Numeric Types")(
      test("Int - positive") {
        val codec = Schema[Int].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(42) == "42")
      },
      test("Int - negative") {
        val codec = Schema[Int].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(-42) == "-42")
      },
      test("Int - zero") {
        val codec = Schema[Int].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(0) == "0")
      },
      test("Int - max value") {
        val codec = Schema[Int].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(Int.MaxValue) == Int.MaxValue.toString)
      },
      test("Int - min value") {
        val codec = Schema[Int].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(Int.MinValue) == Int.MinValue.toString)
      },
      test("Long - large value") {
        val codec = Schema[Long].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(9223372036854775807L) == "9223372036854775807")
      },
      test("Float - normal") {
        val codec = Schema[Float].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(3.14f) == "3.14")
      },
      test("Float - NaN becomes null") {
        val codec = Schema[Float].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(Float.NaN) == "null")
      },
      test("Double - normal") {
        val codec = Schema[Double].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(3.14159) == "3.14159")
      },
      test("Double - Infinity becomes null") {
        val codec = Schema[Double].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(Double.PositiveInfinity) == "null")
      },
      test("BigInt - large value") {
        val codec = Schema[BigInt].derive(ToonFormat.deriver)
        val big   = BigInt("123456789012345678901234567890")
        assertTrue(codec.encodeToString(big) == "123456789012345678901234567890")
      },
      test("BigDecimal - high precision") {
        val codec = Schema[BigDecimal].derive(ToonFormat.deriver)
        val bd    = BigDecimal("3.141592653589793238462643383279")
        assertTrue(codec.encodeToString(bd) == "3.141592653589793238462643383279")
      }
    ),
    suite("Boolean")(
      test("true") {
        val codec = Schema[Boolean].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(true) == "true")
      },
      test("false") {
        val codec = Schema[Boolean].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(false) == "false")
      }
    ),
    suite("String")(
      test("simple unquoted") {
        val codec = Schema[String].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString("hello") == "hello")
      },
      test("with space - quoted") {
        val codec = Schema[String].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString("hello world") == "\"hello world\"")
      },
      test("with colon - quoted") {
        val codec = Schema[String].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString("key:value") == "\"key:value\"")
      },
      test("with comma - quoted") {
        val codec = Schema[String].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString("a,b,c") == "\"a,b,c\"")
      },
      test("empty string - quoted") {
        val codec = Schema[String].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString("") == "\"\"")
      },
      test("with newline - quoted and escaped") {
        val codec = Schema[String].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString("line1\nline2") == "\"line1\\nline2\"")
      },
      test("with quotes - escaped") {
        val codec = Schema[String].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString("say \"hello\"") == "\"say \\\"hello\\\"\"")
      }
    ),
    suite("Unit")(
      test("unit writes nothing") {
        val codec = Schema[Unit].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(()) == "")
      }
    ),
    suite("Java Time Types")(
      test("Instant") {
        val codec   = Schema[Instant].derive(ToonFormat.deriver)
        val instant = Instant.parse("2024-01-15T10:30:00Z")
        assertTrue(codec.encodeToString(instant) == "\"2024-01-15T10:30:00Z\"")
      },
      test("LocalDate") {
        val codec = Schema[LocalDate].derive(ToonFormat.deriver)
        val date  = LocalDate.of(2024, 1, 15)
        assertTrue(codec.encodeToString(date) == "\"2024-01-15\"")
      },
      test("LocalTime") {
        val codec = Schema[LocalTime].derive(ToonFormat.deriver)
        val time  = LocalTime.of(10, 30, 45)
        assertTrue(codec.encodeToString(time) == "\"10:30:45\"")
      },
      test("LocalDateTime") {
        val codec = Schema[LocalDateTime].derive(ToonFormat.deriver)
        val dt    = LocalDateTime.of(2024, 1, 15, 10, 30, 45)
        assertTrue(codec.encodeToString(dt) == "\"2024-01-15T10:30:45\"")
      },
      test("Duration") {
        val codec = Schema[Duration].derive(ToonFormat.deriver)
        val dur   = Duration.ofHours(2).plusMinutes(30)
        assertTrue(codec.encodeToString(dur) == "\"PT2H30M\"")
      },
      test("Period") {
        val codec  = Schema[Period].derive(ToonFormat.deriver)
        val period = Period.of(1, 2, 3)
        assertTrue(codec.encodeToString(period) == "\"P1Y2M3D\"")
      }
    )
  )
}
