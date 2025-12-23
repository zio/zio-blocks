package zio.blocks.schema.into.product

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for Into[Tuple, Tuple] conversions.
 *
 * Covers:
 *   - Same arity tuple conversions
 *   - Type coercion within tuple elements
 *   - Identity conversions
 *   - Custom Into instances for elements
 */
object TupleToTupleSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("TupleToTupleSpec")(
    suite("Same Type Tuples")(
      test("converts Tuple2 to Tuple2 with same types") {
        val tuple  = (42, "hello")
        val result = Into.derived[(Int, String), (Int, String)].into(tuple)

        assert(result)(isRight(equalTo((42, "hello"))))
      },
      test("converts Tuple3 to Tuple3 with same types") {
        val tuple  = (1, "two", true)
        val result = Into.derived[(Int, String, Boolean), (Int, String, Boolean)].into(tuple)

        assert(result)(isRight(equalTo((1, "two", true))))
      },
      test("converts Tuple4 to Tuple4 with same types") {
        val tuple  = (1, 2.0, "three", false)
        val result = Into.derived[(Int, Double, String, Boolean), (Int, Double, String, Boolean)].into(tuple)

        assert(result)(isRight(equalTo((1, 2.0, "three", false))))
      }
    ),
    suite("Type Coercion")(
      test("widens Int to Long in first position") {
        val tuple  = (42, "hello")
        val result = Into.derived[(Int, String), (Long, String)].into(tuple)

        assert(result)(isRight(equalTo((42L, "hello"))))
      },
      test("widens Int to Long in second position") {
        val tuple  = ("hello", 42)
        val result = Into.derived[(String, Int), (String, Long)].into(tuple)

        assert(result)(isRight(equalTo(("hello", 42L))))
      },
      test("widens multiple elements") {
        val tuple  = (10, 20.toShort, 30.toByte)
        val result = Into.derived[(Int, Short, Byte), (Long, Int, Short)].into(tuple)

        assert(result)(isRight(equalTo((10L, 20, 30.toShort))))
      },
      test("widens Float to Double") {
        val tuple  = (3.14f, "pi")
        val result = Into.derived[(Float, String), (Double, String)].into(tuple)

        assert(result)(isRight(equalTo((3.14f.toDouble, "pi"))))
      },
      test("narrows Long to Int when value fits") {
        val tuple  = (42L, "test")
        val result = Into.derived[(Long, String), (Int, String)].into(tuple)

        assert(result)(isRight(equalTo((42, "test"))))
      },
      test("fails when narrowing would overflow") {
        val tuple  = (Long.MaxValue, "test")
        val result = Into.derived[(Long, String), (Int, String)].into(tuple)

        assert(result)(isLeft)
      }
    ),
    suite("Custom Into Instances")(
      test("uses implicit Into for element conversion") {
        implicit val customIntToLong: Into[Int, Long] = (i: Int) => Right(i.toLong + 100)

        val tuple  = (5, "data")
        val result = Into.derived[(Int, String), (Long, String)].into(tuple)

        assert(result)(isRight(equalTo((105L, "data"))))
      },
      test("uses different custom Into instances for different elements") {
        case class Wrapper(value: Int)

        implicit val wrapperToInt: Into[Wrapper, Int] = (w: Wrapper) => Right(w.value * 2)
        implicit val intToLong: Into[Int, Long]       = (i: Int) => Right(i.toLong * 3)

        val tuple  = (Wrapper(5), 10)
        val result = Into.derived[(Wrapper, Int), (Int, Long)].into(tuple)

        assert(result)(isRight(equalTo((10, 30L))))
      },
      test("propagates conversion errors from custom Into") {
        implicit val validatingInt: Into[Int, Long] = (i: Int) =>
          if (i > 0) Right(i.toLong)
          else Left(SchemaError.conversionFailed(Nil, "Value must be positive"))

        val invalid = (-5, "fail")
        val valid   = (10, "success")

        assert(Into.derived[(Int, String), (Long, String)].into(invalid))(isLeft) &&
        assert(Into.derived[(Int, String), (Long, String)].into(valid))(isRight(equalTo((10L, "success"))))
      }
    ),
    suite("Larger Tuples")(
      test("converts Tuple5") {
        val tuple  = (1, 2, 3, 4, 5)
        val result = Into.derived[(Int, Int, Int, Int, Int), (Long, Long, Long, Long, Long)].into(tuple)

        assert(result)(isRight(equalTo((1L, 2L, 3L, 4L, 5L))))
      },
      test("converts Tuple6 with mixed types") {
        val tuple  = (1, "two", 3.0, true, 5.toByte, 'f')
        val result = Into
          .derived[(Int, String, Double, Boolean, Byte, Char), (Long, String, Double, Boolean, Short, Char)]
          .into(tuple)

        assert(result)(isRight(equalTo((1L, "two", 3.0, true, 5.toShort, 'f'))))
      }
    ),
    suite("Single Element Tuple")(
      test("converts Tuple1 with type coercion") {
        val tuple  = Tuple1(42)
        val result = Into.derived[Tuple1[Int], Tuple1[Long]].into(tuple)

        assert(result)(isRight(equalTo(Tuple1(42L))))
      }
    ),
    suite("Error Propagation")(
      test("fails on first element conversion error") {
        val tuple  = (Long.MaxValue, 10)
        val result = Into.derived[(Long, Int), (Int, Long)].into(tuple)

        // Should fail on first element overflow
        assert(result)(isLeft)
      },
      test("fails on second element conversion error") {
        val tuple  = (10, Long.MaxValue)
        val result = Into.derived[(Int, Long), (Long, Int)].into(tuple)

        // Should fail on second element overflow
        assert(result)(isLeft)
      }
    )
  )
}
