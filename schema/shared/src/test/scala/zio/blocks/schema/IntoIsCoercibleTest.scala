package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

/**
 * Tests to verify that implicit Into resolution can handle all cases that
 * isCoercible currently handles. If these tests pass, we can potentially remove
 * isCoercible from the macro.
 */
object IntoIsCoercibleTest extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("IntoIsCoercibleTest")(
    suite("Numeric Widening - Should work via implicit Into instances")(
      test("Byte to wider types in case class") {
        case class Source(b: Byte, name: String)
        case class TargetShort(b: Short, name: String)
        case class TargetInt(b: Int, name: String)
        case class TargetLong(b: Long, name: String)
        case class TargetFloat(b: Float, name: String)
        case class TargetDouble(b: Double, name: String)

        val source = Source(42.toByte, "test")

        val resultShort  = Into.derived[Source, TargetShort].into(source)
        val resultInt    = Into.derived[Source, TargetInt].into(source)
        val resultLong   = Into.derived[Source, TargetLong].into(source)
        val resultFloat  = Into.derived[Source, TargetFloat].into(source)
        val resultDouble = Into.derived[Source, TargetDouble].into(source)

        assert(resultShort)(isRight(equalTo(TargetShort(42.toShort, "test")))) &&
        assert(resultInt)(isRight(equalTo(TargetInt(42, "test")))) &&
        assert(resultLong)(isRight(equalTo(TargetLong(42L, "test")))) &&
        assert(resultFloat)(isRight(equalTo(TargetFloat(42f, "test")))) &&
        assert(resultDouble)(isRight(equalTo(TargetDouble(42.0, "test"))))
      },
      test("Short to wider types in case class") {
        case class Source(s: Short, name: String)
        case class TargetInt(s: Int, name: String)
        case class TargetLong(s: Long, name: String)
        case class TargetFloat(s: Float, name: String)
        case class TargetDouble(s: Double, name: String)

        val source = Source(1000.toShort, "test")

        val resultInt    = Into.derived[Source, TargetInt].into(source)
        val resultLong   = Into.derived[Source, TargetLong].into(source)
        val resultFloat  = Into.derived[Source, TargetFloat].into(source)
        val resultDouble = Into.derived[Source, TargetDouble].into(source)

        assert(resultInt)(isRight(equalTo(TargetInt(1000, "test")))) &&
        assert(resultLong)(isRight(equalTo(TargetLong(1000L, "test")))) &&
        assert(resultFloat)(isRight(equalTo(TargetFloat(1000f, "test")))) &&
        assert(resultDouble)(isRight(equalTo(TargetDouble(1000.0, "test"))))
      },
      test("Int to wider types in case class") {
        case class Source(i: Int, name: String)
        case class TargetLong(i: Long, name: String)
        case class TargetFloat(i: Float, name: String)
        case class TargetDouble(i: Double, name: String)

        val source = Source(100000, "test")

        val resultLong   = Into.derived[Source, TargetLong].into(source)
        val resultFloat  = Into.derived[Source, TargetFloat].into(source)
        val resultDouble = Into.derived[Source, TargetDouble].into(source)

        assert(resultLong)(isRight(equalTo(TargetLong(100000L, "test")))) &&
        assert(resultFloat)(isRight(equalTo(TargetFloat(100000f, "test")))) &&
        assert(resultDouble)(isRight(equalTo(TargetDouble(100000.0, "test"))))
      },
      test("Long to wider types in case class") {
        case class Source(l: Long, name: String)
        case class TargetFloat(l: Float, name: String)
        case class TargetDouble(l: Double, name: String)

        val source = Source(9999999L, "test")

        val resultFloat  = Into.derived[Source, TargetFloat].into(source)
        val resultDouble = Into.derived[Source, TargetDouble].into(source)

        assert(resultFloat)(isRight(equalTo(TargetFloat(9999999f, "test")))) &&
        assert(resultDouble)(isRight(equalTo(TargetDouble(9999999.0, "test"))))
      },
      test("Float to Double in case class") {
        case class Source(f: Float, name: String)
        case class Target(f: Double, name: String)

        val source = Source(3.14f, "test")
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(3.14f.toDouble, "test"))))
      }
    ),
    suite("Numeric Widening in Tuples - Should work via implicit Into instances")(
      test("Byte to Int in tuple") {
        val source = (42.toByte, "test")
        val result = Into.derived[(Byte, String), (Int, String)].into(source)

        assert(result)(isRight(equalTo((42, "test"))))
      },
      test("Int to Long in tuple") {
        val source = (12345, "test")
        val result = Into.derived[(Int, String), (Long, String)].into(source)

        assert(result)(isRight(equalTo((12345L, "test"))))
      },
      test("Float to Double in tuple") {
        val source = (2.5f, "test")
        val result = Into.derived[(Float, String), (Double, String)].into(source)

        assert(result)(isRight(equalTo((2.5, "test"))))
      }
    ),
    suite("Numeric Narrowing - Should work via implicit Into instances with validation")(
      test("Long to Int in case class - success") {
        case class Source(value: Long)
        case class Target(value: Int)

        val source = Source(42L)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(42))))
      },
      test("Long to Int in case class - overflow") {
        case class Source(value: Long)
        case class Target(value: Int)

        val source = Source(Long.MaxValue)
        val result = Into.derived[Source, Target].into(source)

        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.contains("out of range"))
        )
      },
      test("Double to Float in case class - success") {
        case class Source(value: Double)
        case class Target(value: Float)

        val source = Source(1.5)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(1.5f))))
      },
      test("Double to Float in case class - overflow") {
        case class Source(value: Double)
        case class Target(value: Float)

        val source = Source(Double.MaxValue)
        val result = Into.derived[Source, Target].into(source)

        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.contains("out of range"))
        )
      }
    ),
    suite("Numeric conversions in coproducts - Should work via implicit Into instances")(
      test("Byte to Int in coproduct case class") {
        sealed trait SourceADT
        case class SourceCase(value: Byte) extends SourceADT

        sealed trait TargetADT
        case class TargetCase(value: Int) extends TargetADT

        val source: SourceADT = SourceCase(42.toByte)
        val result            = Into.derived[SourceADT, TargetADT].into(source)

        assert(result)(isRight(equalTo(TargetCase(42): TargetADT)))
      },
      test("Int to Long in coproduct case class") {
        sealed trait SourceADT
        case class SourceCase(value: Int) extends SourceADT

        sealed trait TargetADT
        case class TargetCase(value: Long) extends TargetADT

        val source: SourceADT = SourceCase(12345)
        val result            = Into.derived[SourceADT, TargetADT].into(source)

        assert(result)(isRight(equalTo(TargetCase(12345L): TargetADT)))
      }
    ),
    suite("Multiple numeric conversions - Should work via implicit Into instances")(
      test("Multiple fields with different numeric widening") {
        case class Source(b: Byte, s: Short, i: Int, l: Long)
        case class Target(b: Int, s: Long, i: Double, l: Double)

        val source = Source(10.toByte, 100.toShort, 1000, 10000L)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(10, 100L, 1000.0, 10000.0))))
      }
    )
  )
}
