package zio.blocks.schema.into.product

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for Into[CaseClass, Tuple] conversions.
 *
 * Covers:
 *   - Basic case class to tuple by position
 *   - Type coercion during conversion
 *   - Various tuple arities
 *   - Custom Into instances for fields
 */
object CaseClassToTupleSpec extends ZIOSpecDefault {

  // === Test Data Types ===

  case class Point2D(x: Double, y: Double)
  case class Point3D(x: Double, y: Double, z: Double)
  case class Person(name: String, age: Int)
  case class Config(host: String, port: Int, secure: Boolean)
  case class Pair(first: Int, second: Int)

  // For type coercion tests
  case class IntPair(a: Int, b: Int)
  case class MixedTypes(id: Int, name: String, value: Float)

  // === Test Suite ===
  def spec: Spec[TestEnvironment, Any] = suite("CaseClassToTupleSpec")(
    suite("Basic Positional Mapping")(
      test("converts 2-field case class to Tuple2") {
        val point  = Point2D(x = 1.0, y = 2.0)
        val result = Into.derived[Point2D, (Double, Double)].into(point)

        assert(result)(isRight(equalTo((1.0, 2.0))))
      },
      test("converts 3-field case class to Tuple3") {
        val point  = Point3D(x = 1.0, y = 2.0, z = 3.0)
        val result = Into.derived[Point3D, (Double, Double, Double)].into(point)

        assert(result)(isRight(equalTo((1.0, 2.0, 3.0))))
      },
      test("converts Person to (String, Int)") {
        val person = Person(name = "Alice", age = 30)
        val result = Into.derived[Person, (String, Int)].into(person)

        assert(result)(isRight(equalTo(("Alice", 30))))
      },
      test("converts Config to (String, Int, Boolean)") {
        val config = Config(host = "localhost", port = 8080, secure = true)
        val result = Into.derived[Config, (String, Int, Boolean)].into(config)

        assert(result)(isRight(equalTo(("localhost", 8080, true))))
      }
    ),
    suite("Type Coercion")(
      test("widens Int to Long in tuple elements") {
        val pair   = IntPair(a = 10, b = 20)
        val result = Into.derived[IntPair, (Long, Long)].into(pair)

        assert(result)(isRight(equalTo((10L, 20L))))
      },
      test("widens Float to Double in tuple elements") {
        val mixed  = MixedTypes(id = 1, name = "test", value = 3.14f)
        val result = Into.derived[MixedTypes, (Int, String, Double)].into(mixed)

        assert(result)(isRight(equalTo((1, "test", 3.14f.toDouble))))
      },
      test("combines multiple widening coercions") {
        case class Source(a: Byte, b: Short, c: Int)
        val source = Source(a = 1.toByte, b = 2.toShort, c = 3)
        val result = Into.derived[Source, (Int, Int, Long)].into(source)

        assert(result)(isRight(equalTo((1, 2, 3L))))
      },
      test("fails when narrowing would overflow") {
        case class Wide(value: Long)
        val source = Wide(Long.MaxValue)
        val result = Into.derived[Wide, Tuple1[Int]].into(source)

        assert(result)(isLeft)
      },
      test("succeeds with narrowing when value fits") {
        case class Wide(value: Long)
        val source = Wide(42L)
        val result = Into.derived[Wide, Tuple1[Int]].into(source)

        assert(result)(isRight(equalTo(Tuple1(42))))
      }
    ),
    suite("Custom Into Instances")(
      test("uses implicit Into for field-to-element conversion") {
        case class Source(id: Int, name: String)

        implicit val customIntToLong: Into[Int, Long] = (i: Int) => Right(i.toLong * 2)

        val source = Source(id = 21, name = "test")
        val result = Into.derived[Source, (Long, String)].into(source)

        // Custom Into doubles the value
        assert(result)(isRight(equalTo((42L, "test"))))
      },
      test("propagates conversion errors from custom Into") {
        case class Source(value: Int, label: String)

        implicit val validatingInt: Into[Int, Long] = (i: Int) =>
          if (i > 0) Right(i.toLong)
          else Left(SchemaError.conversionFailed(Nil, "Value must be positive"))

        val invalid = Source(value = -5, label = "fail")
        val valid   = Source(value = 10, label = "success")

        assert(Into.derived[Source, (Long, String)].into(invalid))(isLeft) &&
        assert(Into.derived[Source, (Long, String)].into(valid))(isRight(equalTo((10L, "success"))))
      },
      test("uses nested custom Into for complex field types") {
        case class Inner(x: Int)
        case class Source(inner: Inner, name: String)

        implicit val innerToLong: Into[Inner, Long] = (i: Inner) => Right(i.x.toLong * 10)

        val source = Source(Inner(5), "nested")
        val result = Into.derived[Source, (Long, String)].into(source)

        assert(result)(isRight(equalTo((50L, "nested"))))
      }
    ),
    suite("Single Field")(
      test("converts single-field case class to Tuple1") {
        case class Single(value: String)
        val source = Single("hello")
        val result = Into.derived[Single, Tuple1[String]].into(source)

        assert(result)(isRight(equalTo(Tuple1("hello"))))
      }
    ),
    suite("Larger Tuples")(
      test("converts 4-field case class to Tuple4") {
        case class Quad(a: Int, b: String, c: Boolean, d: Double)
        val source = Quad(1, "two", true, 4.0)
        val result = Into.derived[Quad, (Int, String, Boolean, Double)].into(source)

        assert(result)(isRight(equalTo((1, "two", true, 4.0))))
      },
      test("converts 5-field case class to Tuple5") {
        case class Quint(a: Int, b: Int, c: Int, d: Int, e: Int)
        val source = Quint(1, 2, 3, 4, 5)
        val result = Into.derived[Quint, (Int, Int, Int, Int, Int)].into(source)

        assert(result)(isRight(equalTo((1, 2, 3, 4, 5))))
      }
    )
  )
}
