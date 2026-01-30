package zio.blocks.schema.into.disambiguation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for position-based disambiguation in Into conversions.
 *
 * When types are not unique and names don't match, positional correspondence
 * may be used for tuple-like structures.
 */
object PositionDisambiguationSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("PositionDisambiguationSpec")(
    suite("Tuple to Case Class - Position Based")(
      test("maps tuple elements to case class fields by position") {
        case class Target(name: String, age: Int, active: Boolean)

        val source = ("Alice", 30, true)
        val result = Into.derived[(String, Int, Boolean), Target].into(source)

        assert(result)(isRight(equalTo(Target("Alice", 30, true))))
      },
      test("maps tuple with same types by position") {
        case class Target(first: String, second: String, third: String)

        val source = ("a", "b", "c")
        val result = Into.derived[(String, String, String), Target].into(source)

        assert(result)(isRight(equalTo(Target("a", "b", "c"))))
      }
    ),
    suite("Case Class to Tuple - Position Based")(
      test("maps case class fields to tuple by position") {
        case class Source(name: String, age: Int, active: Boolean)

        val source = Source("Bob", 25, false)
        val result = Into.derived[Source, (String, Int, Boolean)].into(source)

        assert(result)(isRight(equalTo(("Bob", 25, false))))
      },
      test("maps case class with same types to tuple by position") {
        case class Source(first: String, second: String, third: String)

        val source = Source("x", "y", "z")
        val result = Into.derived[Source, (String, String, String)].into(source)

        assert(result)(isRight(equalTo(("x", "y", "z"))))
      }
    ),
    suite("Tuple to Tuple - Position Based")(
      test("maps tuple to tuple with same types by position") {
        val source = (1, 2, 3)
        val result = Into.derived[(Int, Int, Int), (Int, Int, Int)].into(source)

        assert(result)(isRight(equalTo((1, 2, 3))))
      },
      test("maps tuple to tuple with type coercion") {
        val source = (1, 2, 3)
        val result = Into.derived[(Int, Int, Int), (Long, Long, Long)].into(source)

        assert(result)(isRight(equalTo((1L, 2L, 3L))))
      }
    ),
    suite("Position with Type Coercion")(
      test("maps by position and widens Int to Long") {
        case class Target(a: Long, b: Long)

        val source = (10, 20)
        val result = Into.derived[(Int, Int), Target].into(source)

        assert(result)(isRight(equalTo(Target(10L, 20L))))
      },
      test("maps by position and widens multiple types") {
        case class Target(x: Long, y: Double, z: Int)

        val source = (1, 2.5f, 3.toShort)
        val result = Into.derived[(Int, Float, Short), Target].into(source)

        assert(result)(isRight(equalTo(Target(1L, 2.5f.toDouble, 3))))
      }
    ),
    suite("Position with Narrowing")(
      test("narrows by position when values fit") {
        case class Target(a: Int, b: Int)

        val source = (10L, 20L)
        val result = Into.derived[(Long, Long), Target].into(source)

        assert(result)(isRight(equalTo(Target(10, 20))))
      },
      test("fails narrowing when values overflow") {
        case class Target(a: Int, b: Int)

        val source = (Long.MaxValue, 20L)
        val result = Into.derived[(Long, Long), Target].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Larger Tuples")(
      test("maps 4-element tuple by position") {
        case class Target(a: Int, b: Int, c: Int, d: Int)

        val source = (1, 2, 3, 4)
        val result = Into.derived[(Int, Int, Int, Int), Target].into(source)

        assert(result)(isRight(equalTo(Target(1, 2, 3, 4))))
      },
      test("maps 5-element tuple by position") {
        case class Target(a: String, b: String, c: String, d: String, e: String)

        val source = ("1", "2", "3", "4", "5")
        val result = Into.derived[(String, String, String, String, String), Target].into(source)

        assert(result)(isRight(equalTo(Target("1", "2", "3", "4", "5"))))
      }
    ),
    suite("Mixed Position and Type Uniqueness")(
      test("uses position when types repeat in tuple-like conversion") {
        case class Source(x: Int, y: Int)

        val source = Source(10, 20)
        val result = Into.derived[Source, (Int, Int)].into(source)

        assert(result)(isRight(equalTo((10, 20))))
      }
    ),
    suite("Nested Tuples")(
      test("maps nested tuples with same types by position") {
        val source = ((1, 2), (3, 4))
        val result = Into.derived[((Int, Int), (Int, Int)), ((Int, Int), (Int, Int))].into(source)

        assert(result)(isRight(equalTo(((1, 2), (3, 4)))))
      },
      test("maps case class with tuple field - same tuple type") {
        case class Source(pair: (Int, Int), label: String)
        case class Target(pair: (Int, Int), name: String)

        val source = Source((1, 2), "test")
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target((1, 2), "test"))))
      }
    ),
    suite("Single Element")(
      test("Tuple1 maps to single-field case class") {
        case class Target(value: Int)

        val source = Tuple1(42)
        val result = Into.derived[Tuple1[Int], Target].into(source)

        assert(result)(isRight(equalTo(Target(42))))
      },
      test("single-field case class maps to Tuple1") {
        case class Source(value: Int)

        val source = Source(42)
        val result = Into.derived[Source, Tuple1[Int]].into(source)

        assert(result)(isRight(equalTo(Tuple1(42))))
      }
    )
  )
}
