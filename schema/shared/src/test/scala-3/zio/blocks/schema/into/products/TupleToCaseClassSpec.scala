package zio.blocks.schema.into.products

import zio.test._
import zio.blocks.schema._

object TupleToCaseClassSpec extends ZIOSpecDefault {

  def spec = suite("TupleToCaseClassSpec")(
    suite("Basic Conversions")(
      test("should convert tuple to simple case class (Tuple2)") {
        case class Point(x: Double, y: Double)
        type PointTuple = (Double, Double)

        val derivation = Into.derived[PointTuple, Point]
        val input      = (1.5, 2.5)
        val result     = derivation.into(input)

        assertTrue(result == Right(Point(1.5, 2.5)))
      },
      test("should convert tuple to RGB case class (Tuple3)") {
        case class RGB(r: Int, g: Int, b: Int)
        type ColorTuple = (Int, Int, Int)

        val derivation = Into.derived[ColorTuple, RGB]
        val input      = (255, 128, 0)
        val result     = derivation.into(input)

        assertTrue(result == Right(RGB(255, 128, 0)))
      },
      test("should convert tuple with mixed types to case class") {
        case class Person(name: String, age: Int, active: Boolean)
        type PersonTuple = (String, Int, Boolean)

        val derivation = Into.derived[PersonTuple, Person]
        val input      = ("Alice", 30, true)
        val result     = derivation.into(input)

        assertTrue(result == Right(Person("Alice", 30, true)))
      }
    ),
    suite("Different Arity")(
      test("should convert Tuple2 to case class") {
        case class Pair(a: Int, b: String)
        type PairTuple = (Int, String)

        val derivation = Into.derived[PairTuple, Pair]
        val input      = (42, "hello")
        val result     = derivation.into(input)

        assertTrue(result == Right(Pair(42, "hello")))
      },
      test("should convert Tuple4 to case class") {
        case class Quad(a: Int, b: String, c: Boolean, d: Double)
        type QuadTuple = (Int, String, Boolean, Double)

        val derivation = Into.derived[QuadTuple, Quad]
        val input      = (1, "test", true, 3.14)
        val result     = derivation.into(input)

        assertTrue(result == Right(Quad(1, "test", true, 3.14)))
      },
      test("should convert Tuple5 to case class") {
        case class Five(a: Int, b: Long, c: String, d: Boolean, e: Double)
        type FiveTuple = (Int, Long, String, Boolean, Double)

        val derivation = Into.derived[FiveTuple, Five]
        val input      = (1, 2L, "three", true, 5.0)
        val result     = derivation.into(input)

        assertTrue(result == Right(Five(1, 2L, "three", true, 5.0)))
      }
    ),
    suite("With Coercion")(
      test("should convert tuple to case class with element coercion") {
        case class Coerce(a: Long, b: Long)
        type CoerceTuple = (Int, Int)

        val derivation = Into.derived[CoerceTuple, Coerce]
        val input      = (42, 100)
        val result     = derivation.into(input)

        assertTrue(result == Right(Coerce(42L, 100L)))
      },
      test("should convert tuple to case class with mixed coercion") {
        case class Mixed(a: Long, b: Double, c: Float)
        type MixedTuple = (Int, Double, Float)

        val derivation = Into.derived[MixedTuple, Mixed]
        val input      = (42, 3.14, 2.5f)
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_.a) == Right(42L))
        assertTrue(result.map(_.b) == Right(3.14))
        assertTrue(result.map(_.c) == Right(2.5f))
      },
      test("should convert tuple with numeric narrowing (with validation)") {
        case class Narrow(a: Int, b: Float)
        type NarrowTuple = (Long, Double)

        val derivation = Into.derived[NarrowTuple, Narrow]
        val input      = (42L, 3.14)
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_.a) == Right(42))
        // Float conversion may have precision loss, so check approximately
        assertTrue(result.map(_.b) == Right(3.14f))
      }
    )
    // Note: Arity mismatch errors are tested at compile-time.
    // The macro correctly fails with clear error messages when arity doesn't match.
  )
}

