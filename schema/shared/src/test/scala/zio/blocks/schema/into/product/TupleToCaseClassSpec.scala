package zio.blocks.schema.into.product

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for Into[Tuple, CaseClass] conversions.
 *
 * Covers:
 *   - Basic tuple to case class by position
 *   - Type coercion during conversion
 *   - Various tuple arities
 *   - Custom Into instances for elements
 */
object TupleToCaseClassSpec extends ZIOSpecDefault {

  // === Test Data Types ===

  case class Point2D(x: Double, y: Double)
  case class Point3D(x: Double, y: Double, z: Double)
  case class RGBColor(red: Int, green: Int, blue: Int)
  case class Person(name: String, age: Int)
  case class Config(host: String, port: Int, secure: Boolean)

  // For coercion tests
  case class LongPair(a: Long, b: Long)
  case class MixedTarget(id: Long, name: String, value: Double)

  // === Test Suite ===
  def spec: Spec[TestEnvironment, Any] = suite("TupleToCaseClassSpec")(
    suite("Basic Positional Mapping")(
      test("converts Tuple2 to 2-field case class") {
        val tuple  = (1.0, 2.0)
        val result = Into.derived[(Double, Double), Point2D].into(tuple)

        assert(result)(isRight(equalTo(Point2D(x = 1.0, y = 2.0))))
      },
      test("converts Tuple3 to 3-field case class") {
        val tuple  = (255, 128, 64)
        val result = Into.derived[(Int, Int, Int), RGBColor].into(tuple)

        assert(result)(isRight(equalTo(RGBColor(red = 255, green = 128, blue = 64))))
      },
      test("converts (String, Int) to Person") {
        val tuple  = ("Alice", 30)
        val result = Into.derived[(String, Int), Person].into(tuple)

        assert(result)(isRight(equalTo(Person(name = "Alice", age = 30))))
      },
      test("converts (String, Int, Boolean) to Config") {
        val tuple  = ("localhost", 8080, true)
        val result = Into.derived[(String, Int, Boolean), Config].into(tuple)

        assert(result)(isRight(equalTo(Config(host = "localhost", port = 8080, secure = true))))
      }
    ),
    suite("Type Coercion")(
      test("widens Int to Long in case class fields") {
        val tuple  = (10, 20)
        val result = Into.derived[(Int, Int), LongPair].into(tuple)

        assert(result)(isRight(equalTo(LongPair(a = 10L, b = 20L))))
      },
      test("widens multiple types in one conversion") {
        val tuple  = (1, "test", 3.14f)
        val result = Into.derived[(Int, String, Float), MixedTarget].into(tuple)

        assert(result)(isRight(equalTo(MixedTarget(id = 1L, name = "test", value = 3.14f.toDouble))))
      },
      test("widens Byte through Short to Int") {
        case class Target(a: Int, b: Int)
        val tuple  = (1.toByte, 2.toShort)
        val result = Into.derived[(Byte, Short), Target].into(tuple)

        assert(result)(isRight(equalTo(Target(a = 1, b = 2))))
      },
      test("fails when narrowing would overflow") {
        case class Target(value: Int)
        val tuple  = Tuple1(Long.MaxValue)
        val result = Into.derived[Tuple1[Long], Target].into(tuple)

        assert(result)(isLeft)
      },
      test("succeeds with narrowing when value fits") {
        case class Target(value: Int)
        val tuple  = Tuple1(42L)
        val result = Into.derived[Tuple1[Long], Target].into(tuple)

        assert(result)(isRight(equalTo(Target(value = 42))))
      }
    ),
    suite("Custom Into Instances")(
      test("uses implicit Into for element-to-field conversion") {
        case class Target(value: Long, name: String)

        implicit val customIntToLong: Into[Int, Long] = (i: Int) => Right(i.toLong * 3)

        val tuple  = (10, "label")
        val result = Into.derived[(Int, String), Target].into(tuple)

        // Custom Into triples the value
        assert(result)(isRight(equalTo(Target(value = 30L, name = "label"))))
      },
      test("propagates conversion errors from custom Into") {
        case class Target(value: Int, label: String)

        implicit val validatingLong: Into[Long, Int] = (l: Long) =>
          if (l >= 0) Right(l.toInt)
          else Left(SchemaError.conversionFailed(Nil, "Value must be non-negative"))

        val invalid = (-5L, "fail")
        val valid   = (10L, "success")

        assert(Into.derived[(Long, String), Target].into(invalid))(isLeft) &&
        assert(Into.derived[(Long, String), Target].into(valid))(isRight(equalTo(Target(10, "success"))))
      },
      test("uses nested custom Into for complex element types") {
        case class Inner(x: Int)
        case class Target(inner: Long, name: String)

        implicit val innerToLong: Into[Inner, Long] = (i: Inner) => Right(i.x.toLong * 7)

        val tuple  = (Inner(3), "test")
        val result = Into.derived[(Inner, String), Target].into(tuple)

        assert(result)(isRight(equalTo(Target(inner = 21L, name = "test"))))
      }
    ),
    suite("Single Element")(
      test("converts Tuple1 to single-field case class") {
        case class Single(value: String)
        val tuple  = Tuple1("hello")
        val result = Into.derived[Tuple1[String], Single].into(tuple)

        assert(result)(isRight(equalTo(Single("hello"))))
      }
    ),
    suite("Larger Tuples")(
      test("converts Tuple4 to 4-field case class") {
        case class Quad(a: Int, b: String, c: Boolean, d: Double)
        val tuple  = (1, "two", true, 4.0)
        val result = Into.derived[(Int, String, Boolean, Double), Quad].into(tuple)

        assert(result)(isRight(equalTo(Quad(1, "two", true, 4.0))))
      },
      test("converts Tuple5 to 5-field case class") {
        case class Quint(a: Int, b: Int, c: Int, d: Int, e: Int)
        val tuple  = (1, 2, 3, 4, 5)
        val result = Into.derived[(Int, Int, Int, Int, Int), Quint].into(tuple)

        assert(result)(isRight(equalTo(Quint(1, 2, 3, 4, 5))))
      }
    ),
    suite("Field Name Independence")(
      test("tuple elements map to fields regardless of field names") {
        case class Named(firstName: String, lastName: String, yearsOld: Int)
        val tuple  = ("John", "Doe", 42)
        val result = Into.derived[(String, String, Int), Named].into(tuple)

        // Positional mapping: first→firstName, second→lastName, third→yearsOld
        assert(result)(isRight(equalTo(Named(firstName = "John", lastName = "Doe", yearsOld = 42))))
      }
    )
  )
}
