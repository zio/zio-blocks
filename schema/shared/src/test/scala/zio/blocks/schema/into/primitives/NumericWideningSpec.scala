package zio.blocks.schema.into.primitives

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for numeric widening conversions (always safe).
 *
 * Covers:
 *   - Byte → Short → Int → Long
 *   - Float → Double
 *   - Int → Float (lossy but widening)
 *   - Long → Double (lossy but widening)
 */
object NumericWideningSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("NumericWideningSpec")(
    suite("Byte Widening")(
      test("widens Byte to Short") {
        val result = Into.derived[Byte, Short].into(42.toByte)
        assert(result)(isRight(equalTo(42.toShort)))
      },
      test("widens Byte to Int") {
        val result = Into.derived[Byte, Int].into(42.toByte)
        assert(result)(isRight(equalTo(42)))
      },
      test("widens Byte to Long") {
        val result = Into.derived[Byte, Long].into(42.toByte)
        assert(result)(isRight(equalTo(42L)))
      },
      test("widens negative Byte to Long") {
        val result = Into.derived[Byte, Long].into((-100).toByte)
        assert(result)(isRight(equalTo(-100L)))
      },
      test("widens Byte.MaxValue to Long") {
        val result = Into.derived[Byte, Long].into(Byte.MaxValue)
        assert(result)(isRight(equalTo(Byte.MaxValue.toLong)))
      },
      test("widens Byte.MinValue to Long") {
        val result = Into.derived[Byte, Long].into(Byte.MinValue)
        assert(result)(isRight(equalTo(Byte.MinValue.toLong)))
      }
    ),
    suite("Short Widening")(
      test("widens Short to Int") {
        val result = Into.derived[Short, Int].into(1000.toShort)
        assert(result)(isRight(equalTo(1000)))
      },
      test("widens Short to Long") {
        val result = Into.derived[Short, Long].into(1000.toShort)
        assert(result)(isRight(equalTo(1000L)))
      },
      test("widens Short.MaxValue to Int") {
        val result = Into.derived[Short, Int].into(Short.MaxValue)
        assert(result)(isRight(equalTo(Short.MaxValue.toInt)))
      },
      test("widens Short.MinValue to Int") {
        val result = Into.derived[Short, Int].into(Short.MinValue)
        assert(result)(isRight(equalTo(Short.MinValue.toInt)))
      }
    ),
    suite("Int Widening")(
      test("widens Int to Long") {
        val result = Into.derived[Int, Long].into(1000000)
        assert(result)(isRight(equalTo(1000000L)))
      },
      test("widens Int.MaxValue to Long") {
        val result = Into.derived[Int, Long].into(Int.MaxValue)
        assert(result)(isRight(equalTo(Int.MaxValue.toLong)))
      },
      test("widens Int.MinValue to Long") {
        val result = Into.derived[Int, Long].into(Int.MinValue)
        assert(result)(isRight(equalTo(Int.MinValue.toLong)))
      },
      test("widens negative Int to Long") {
        val result = Into.derived[Int, Long].into(-999999)
        assert(result)(isRight(equalTo(-999999L)))
      }
    ),
    suite("Float Widening")(
      test("widens Float to Double") {
        val result = Into.derived[Float, Double].into(3.14f)
        assert(result)(isRight(equalTo(3.14f.toDouble)))
      },
      test("widens Float.MaxValue to Double") {
        val result = Into.derived[Float, Double].into(Float.MaxValue)
        assert(result)(isRight(equalTo(Float.MaxValue.toDouble)))
      },
      test("widens Float.MinValue to Double") {
        val result = Into.derived[Float, Double].into(Float.MinValue)
        assert(result)(isRight(equalTo(Float.MinValue.toDouble)))
      },
      test("widens Float.NaN to Double") {
        val result = Into.derived[Float, Double].into(Float.NaN)
        assert(result.map(_.isNaN))(isRight(isTrue))
      },
      test("widens Float.PositiveInfinity to Double") {
        val result = Into.derived[Float, Double].into(Float.PositiveInfinity)
        assert(result)(isRight(equalTo(Double.PositiveInfinity)))
      },
      test("widens Float.NegativeInfinity to Double") {
        val result = Into.derived[Float, Double].into(Float.NegativeInfinity)
        assert(result)(isRight(equalTo(Double.NegativeInfinity)))
      }
    ),
    suite("Chained Widening")(
      test("widens Byte through Short to Int") {
        val byteToShort = Into.derived[Byte, Short]
        val shortToInt  = Into.derived[Short, Int]

        val byte: Byte = 42
        val result     = for {
          short <- byteToShort.into(byte)
          int   <- shortToInt.into(short)
        } yield int

        assert(result)(isRight(equalTo(42)))
      },
      test("widens Byte through Short through Int to Long") {
        val byteToShort = Into.derived[Byte, Short]
        val shortToInt  = Into.derived[Short, Int]
        val intToLong   = Into.derived[Int, Long]

        val byte: Byte = 100
        val result     = for {
          short <- byteToShort.into(byte)
          int   <- shortToInt.into(short)
          long  <- intToLong.into(int)
        } yield long

        assert(result)(isRight(equalTo(100L)))
      }
    ),
    suite("Widening in Products")(
      test("widens Int field to Long in case class") {
        case class Source(value: Int)
        case class Target(value: Long)

        val source = Source(42)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(42L))))
      },
      test("widens multiple fields with different widening paths") {
        case class Source(a: Byte, b: Short, c: Int, d: Float)
        case class Target(a: Long, b: Int, c: Long, d: Double)

        val source = Source(1.toByte, 2.toShort, 3, 4.0f)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(1L, 2, 3L, 4.0))))
      }
    ),
    suite("Identity Conversions")(
      test("Int to Int is identity") {
        val result = Into.derived[Int, Int].into(42)
        assert(result)(isRight(equalTo(42)))
      },
      test("Long to Long is identity") {
        val result = Into.derived[Long, Long].into(999999999999L)
        assert(result)(isRight(equalTo(999999999999L)))
      },
      test("Double to Double is identity") {
        val result = Into.derived[Double, Double].into(3.14159)
        assert(result)(isRight(equalTo(3.14159)))
      }
    )
  )
}
