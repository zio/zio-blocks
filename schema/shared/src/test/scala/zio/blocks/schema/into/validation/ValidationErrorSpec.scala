package zio.blocks.schema.into.validation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for validation error handling and messages in Into derivation.
 *
 * This tests that validation errors are properly reported and contain useful
 * context.
 */
object ValidationErrorSpec extends ZIOSpecDefault {

  // === Test Case Classes ===
  case class LongValue(value: Long)
  case class IntValue(value: Int)

  case class MultiField(a: Long, b: Long, c: Long)
  case class MultiFieldInt(a: Int, b: Int, c: Int)

  case class NamedFields(count: Long, score: Long)
  case class NamedFieldsInt(count: Int, score: Int)

  def spec: Spec[TestEnvironment, Any] = suite("ValidationErrorSpec")(
    suite("Error Type")(
      test("narrowing overflow returns Left") {
        val source = LongValue(Long.MaxValue)
        val result = Into.derived[LongValue, IntValue].into(source)

        assert(result)(isLeft)
      },
      test("successful conversion returns Right") {
        val source = LongValue(100L)
        val result = Into.derived[LongValue, IntValue].into(source)

        assert(result)(isRight)
      }
    ),
    suite("Error Content")(
      test("error is SchemaError type") {
        val source = LongValue(Long.MaxValue)
        val result = Into.derived[LongValue, IntValue].into(source)

        result match {
          case Left(err) => assertTrue(err.isInstanceOf[SchemaError])
          case Right(_)  => assertTrue(false)
        }
      },
      test("error message contains overflow context") {
        val source = LongValue(Long.MaxValue)
        val result = Into.derived[LongValue, IntValue].into(source)

        result match {
          case Left(err) =>
            val msg = err.toString.toLowerCase
            assertTrue(
              msg.contains("overflow") ||
                msg.contains("range") ||
                msg.contains("conversion") ||
                msg.contains("narrowing")
            )
          case Right(_) => assertTrue(false)
        }
      }
    ),
    suite("Fail Fast Behavior")(
      test("fails on first invalid field") {
        val source = MultiField(Long.MaxValue, Long.MaxValue, Long.MaxValue)
        val result = Into.derived[MultiField, MultiFieldInt].into(source)

        // Should fail fast - one error
        assert(result)(isLeft)
      },
      test("first valid field doesn't prevent second field error") {
        val source = MultiField(100L, Long.MaxValue, 200L)
        val result = Into.derived[MultiField, MultiFieldInt].into(source)

        assert(result)(isLeft)
      },
      test("second field failure when first is valid") {
        val source = NamedFields(100L, Long.MaxValue)
        val result = Into.derived[NamedFields, NamedFieldsInt].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Successful Conversion")(
      test("all fields converted correctly on success") {
        val source = MultiField(1L, 2L, 3L)
        val result = Into.derived[MultiField, MultiFieldInt].into(source)

        assert(result)(isRight(equalTo(MultiFieldInt(1, 2, 3))))
      },
      test("named fields converted correctly") {
        val source = NamedFields(100L, 200L)
        val result = Into.derived[NamedFields, NamedFieldsInt].into(source)

        assert(result)(isRight(equalTo(NamedFieldsInt(100, 200))))
      }
    ),
    suite("Either Operations")(
      test("can use map on Right result") {
        val source = LongValue(100L)
        val result = Into.derived[LongValue, IntValue].into(source)

        val mapped = result.map(_.value * 2)
        assert(mapped)(isRight(equalTo(200)))
      },
      test("can use flatMap on Right result") {
        val source = LongValue(100L)
        val result = Into.derived[LongValue, IntValue].into(source)

        val flatMapped = result.flatMap(v => Right(v.value + 1))
        assert(flatMapped)(isRight(equalTo(101)))
      },
      test("map is no-op on Left") {
        val source = LongValue(Long.MaxValue)
        val result = Into.derived[LongValue, IntValue].into(source)

        val mapped = result.map(_.value * 2)
        assert(mapped)(isLeft)
      },
      test("flatMap is no-op on Left") {
        val source = LongValue(Long.MaxValue)
        val result = Into.derived[LongValue, IntValue].into(source)

        val flatMapped = result.flatMap(v => Right(v.value + 1))
        assert(flatMapped)(isLeft)
      }
    )
  )
}
