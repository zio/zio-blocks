package zio.blocks.schema.into.products

import zio.test._
import zio.blocks.schema._

object CaseClassToTupleSpec extends ZIOSpecDefault {

  def spec = suite("CaseClassToTupleSpec")(
    suite("Basic Conversions")(
      test("should convert simple case class to tuple (Tuple2)") {
        case class Point(x: Double, y: Double)
        type PointTuple = (Double, Double)

        val derivation = Into.derived[Point, PointTuple]
        val input      = Point(1.5, 2.5)
        val result     = derivation.into(input)

        assertTrue(result == Right((1.5, 2.5)))
      },
      test("should convert RGB case class to tuple (Tuple3)") {
        case class RGB(r: Int, g: Int, b: Int)
        type ColorTuple = (Int, Int, Int)

        val derivation = Into.derived[RGB, ColorTuple]
        val input      = RGB(255, 128, 0)
        val result     = derivation.into(input)

        assertTrue(result == Right((255, 128, 0)))
      },
      test("should convert case class with mixed types to tuple") {
        case class Person(name: String, age: Int, active: Boolean)
        type PersonTuple = (String, Int, Boolean)

        val derivation = Into.derived[Person, PersonTuple]
        val input      = Person("Alice", 30, true)
        val result     = derivation.into(input)

        assertTrue(result == Right(("Alice", 30, true)))
      }
    ),
    suite("Different Arity")(
      test("should convert Tuple2 case class") {
        case class Pair(a: Int, b: String)
        type PairTuple = (Int, String)

        val derivation = Into.derived[Pair, PairTuple]
        val input      = Pair(42, "hello")
        val result     = derivation.into(input)

        assertTrue(result == Right((42, "hello")))
      },
      test("should convert Tuple4 case class") {
        case class Quad(a: Int, b: String, c: Boolean, d: Double)
        type QuadTuple = (Int, String, Boolean, Double)

        val derivation = Into.derived[Quad, QuadTuple]
        val input      = Quad(1, "test", true, 3.14)
        val result     = derivation.into(input)

        assertTrue(result == Right((1, "test", true, 3.14)))
      },
      test("should convert Tuple5 case class") {
        case class Five(a: Int, b: Long, c: String, d: Boolean, e: Double)
        type FiveTuple = (Int, Long, String, Boolean, Double)

        val derivation = Into.derived[Five, FiveTuple]
        val input      = Five(1, 2L, "three", true, 5.0)
        val result     = derivation.into(input)

        assertTrue(result == Right((1, 2L, "three", true, 5.0)))
      }
    ),
    suite("With Coercion")(
      test("should convert case class to tuple with element coercion") {
        case class Coerce(a: Int, b: Int)
        type CoerceTuple = (Long, Long)

        val derivation = Into.derived[Coerce, CoerceTuple]
        val input      = Coerce(42, 100)
        val result     = derivation.into(input)

        assertTrue(result == Right((42L, 100L)))
      },
      test("should convert case class to tuple with mixed coercion") {
        case class Mixed(a: Int, b: Double, c: Float)
        type MixedTuple = (Long, Double, Float)

        val derivation = Into.derived[Mixed, MixedTuple]
        val input      = Mixed(42, 3.14, 2.5f)
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_._1) == Right(42L))
        assertTrue(result.map(_._2) == Right(3.14))
        assertTrue(result.map(_._3) == Right(2.5f))
      }
    )
    // Note: Arity mismatch errors are tested at compile-time.
    // The macro correctly fails with clear error messages when arity doesn't match.
  )
}
