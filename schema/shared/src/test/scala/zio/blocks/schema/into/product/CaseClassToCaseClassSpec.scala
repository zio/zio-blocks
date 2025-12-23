package zio.blocks.schema.into.product

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for Into[CaseClass, CaseClass] conversions.
 *
 * Covers:
 *   - Exact field matching (same name + same type)
 *   - Field reordering with exact matches
 *   - Unique type matching (different names, unique types)
 *   - Position-based matching (tuple-like behavior)
 *   - Combined matching strategies
 *   - Type coercion within products
 */
object CaseClassToCaseClassSpec extends ZIOSpecDefault {

  // === Test Data Types ===

  // Exact match with same field order
  case class PersonA(name: String, age: Int)
  case class PersonB(name: String, age: Int)

  // Exact match with different field order
  case class Point(x: Int, y: Int)
  case class Coord(y: Int, x: Int)

  // Unique type matching (different names, unique types per field)
  case class Person(name: String, age: Int, active: Boolean)
  case class User(username: String, yearsOld: Int, enabled: Boolean)

  // Position-based matching (all same type, different names)
  case class RGB(r: Int, g: Int, b: Int)
  case class ColorValues(red: Int, green: Int, blue: Int)

  // Combined: some fields match by name, others by unique type
  case class Employee(id: Long, name: String, department: Int)
  case class Worker(name: String, team: Int, identifier: Long)

  // Type coercion within products (Int -> Long)
  case class ConfigV1(timeout: Int, retries: Int)
  case class ConfigV2(timeout: Long, retries: Long)

  // Different field counts (source subset of target with optional)
  case class BasicUser(id: String, name: String)
  case class ExtendedUser(id: String, name: String, email: Option[String])

  // Multiple fields of same type requiring disambiguation
  case class Dimensions(width: Int, height: Int, depth: Int)
  case class Size(width: Int, height: Int, depth: Int)

  // Empty case classes
  case class EmptyA()
  case class EmptyB()

  // Empty to optional fields
  case class WithOptional(name: Option[String], age: Option[Int])

  // For default value tests - must be top-level to have accessible companion
  case class WithDefaults(name: String = "default", count: Int = 0)
  case class MixedDefaults(name: String = "unnamed", extra: Option[String])
  case class Small(name: String)
  case class Large(name: String, count: Int = 10, active: Boolean = true)
  case class SmallForOptional(name: String)
  case class LargeWithOptional(name: String, extra: Option[String])

  // === Test Suite ===
  def spec: Spec[TestEnvironment, Any] = suite("CaseClassToCaseClassSpec")(
    suite("Exact Field Matching")(
      test("converts when field names and types match exactly") {
        val source = PersonA("Alice", 30)
        val result = Into.derived[PersonA, PersonB].into(source)

        assert(result)(isRight(equalTo(PersonB("Alice", 30))))
      },
      test("converts with same field names but different order") {
        val point  = Point(x = 1, y = 2)
        val result = Into.derived[Point, Coord].into(point)

        // x→x, y→y matched by name (not position)
        assert(result)(isRight(equalTo(Coord(y = 2, x = 1))))
      },
      test("converts dimensions with all matching names") {
        val dims   = Dimensions(width = 10, height = 20, depth = 30)
        val result = Into.derived[Dimensions, Size].into(dims)

        assert(result)(isRight(equalTo(Size(width = 10, height = 20, depth = 30))))
      }
    ),
    suite("Unique Type Matching")(
      test("maps fields by unique type when names differ") {
        val person = Person(name = "Alice", age = 30, active = true)
        val result = Into.derived[Person, User].into(person)

        // String→String (unique), Int→Int (unique), Boolean→Boolean (unique)
        assert(result)(isRight(equalTo(User(username = "Alice", yearsOld = 30, enabled = true))))
      },
      test("handles unique Long type matching") {
        case class Source(id: Long, label: String)
        case class Target(identifier: Long, name: String)

        val source = Source(123L, "test")
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(identifier = 123L, name = "test"))))
      }
    ),
    suite("Position-Based Matching")(
      test("maps fields by position when types match but names differ (tuple-like)") {
        val rgb    = RGB(r = 255, g = 128, b = 64)
        val result = Into.derived[RGB, ColorValues].into(rgb)

        // r→red, g→green, b→blue by position
        assert(result)(isRight(equalTo(ColorValues(red = 255, green = 128, blue = 64))))
      }
    ),
    suite("Combined Matching Strategies")(
      test("uses name match for matching names and unique type for others") {
        val employee = Employee(id = 123L, name = "Bob", department = 5)
        val result   = Into.derived[Employee, Worker].into(employee)

        // name→name (exact), id→identifier (Long unique), department→team (Int unique)
        assert(result)(isRight(equalTo(Worker(name = "Bob", team = 5, identifier = 123L))))
      }
    ),
    suite("Type Coercion Within Products")(
      test("widens Int to Long in product fields") {
        val config = ConfigV1(timeout = 30, retries = 3)
        val result = Into.derived[ConfigV1, ConfigV2].into(config)

        assert(result)(isRight(equalTo(ConfigV2(timeout = 30L, retries = 3L))))
      },
      test("fails when narrowing would overflow") {
        case class WideConfig(value: Long)
        case class NarrowConfig(value: Int)

        val source = WideConfig(Long.MaxValue)
        val result = Into.derived[WideConfig, NarrowConfig].into(source)

        assert(result)(isLeft)
      },
      test("succeeds when narrowing value fits") {
        case class WideConfig(value: Long)
        case class NarrowConfig(value: Int)

        val source = WideConfig(42L)
        val result = Into.derived[WideConfig, NarrowConfig].into(source)

        assert(result)(isRight(equalTo(NarrowConfig(42))))
      }
    ),
    suite("Optional Field Handling")(
      test("adds None for missing optional field in target") {
        val basic  = BasicUser(id = "123", name = "Alice")
        val result = Into.derived[BasicUser, ExtendedUser].into(basic)

        assert(result)(isRight(equalTo(ExtendedUser(id = "123", name = "Alice", email = None))))
      },
      test("drops optional field when going from extended to basic") {
        case class Extended(id: String, name: String, extra: Option[Int])
        case class Basic(id: String, name: String)

        val extended = Extended(id = "456", name = "Bob", extra = Some(42))
        val result   = Into.derived[Extended, Basic].into(extended)

        assert(result)(isRight(equalTo(Basic(id = "456", name = "Bob"))))
      }
    ),
    suite("Multiple Coercion Paths")(
      test("applies multiple independent coercions") {
        case class SourceMulti(a: Int, b: Short, c: Float)
        case class TargetMulti(a: Long, b: Int, c: Double)

        val source = SourceMulti(a = 100, b = 50.toShort, c = 3.14f)
        val result = Into.derived[SourceMulti, TargetMulti].into(source)

        assert(result)(isRight(equalTo(TargetMulti(a = 100L, b = 50, c = 3.14f.toDouble))))
      }
    ),
    suite("Identity Conversions")(
      test("converts to same type") {
        val point  = Point(x = 5, y = 10)
        val result = Into.derived[Point, Point].into(point)

        assert(result)(isRight(equalTo(Point(x = 5, y = 10))))
      }
    ),
    suite("Empty Case Classes")(
      test("converts empty case class to empty case class") {
        val source = EmptyA()
        val result = Into.derived[EmptyA, EmptyB].into(source)

        assert(result)(isRight(equalTo(EmptyB())))
      },
      test("converts empty case class to itself") {
        val source = EmptyA()
        val result = Into.derived[EmptyA, EmptyA].into(source)

        assert(result)(isRight(equalTo(EmptyA())))
      },
      test("converts empty case class to case class with all optional fields") {
        val source = EmptyA()
        val result = Into.derived[EmptyA, WithOptional].into(source)

        assert(result)(isRight(equalTo(WithOptional(name = None, age = None))))
      },
      test("converts empty case class to case class with default values") {
        val source = EmptyA()
        val result = Into.derived[EmptyA, WithDefaults].into(source)

        assert(result)(isRight(equalTo(WithDefaults("default", 0))))
      },
      test("converts empty case class to case class with mixed optional and default") {
        val source = EmptyA()
        val result = Into.derived[EmptyA, MixedDefaults].into(source)

        assert(result)(isRight(equalTo(MixedDefaults("unnamed", None))))
      },
      test("converts case class with optional fields to empty when all are None") {
        val source = WithOptional(name = None, age = None)
        val result = Into.derived[WithOptional, EmptyA].into(source)

        assert(result)(isRight(equalTo(EmptyA())))
      },
      test("converts case class with Some values - optional fields dropped when target is empty") {
        val source = WithOptional(name = Some("Alice"), age = Some(30))
        val result = Into.derived[WithOptional, EmptyA].into(source)

        assert(result)(isRight(equalTo(EmptyA())))
      },
      test("source with fewer fields uses defaults for missing target fields") {
        val source = Small("test")
        val result = Into.derived[Small, Large].into(source)

        assert(result)(isRight(equalTo(Large("test", 10, true))))
      },
      test("source with fewer fields uses None for missing optional target fields") {
        val source = SmallForOptional("test")
        val result = Into.derived[SmallForOptional, LargeWithOptional].into(source)

        assert(result)(isRight(equalTo(LargeWithOptional("test", None))))
      }
    ) @@ TestAspect.tag("empty-case-class"),
    suite("Custom Into Instances")(
      test("uses implicit Into for field conversion") {
        case class Inner(value: Int)
        case class Source(inner: Inner, name: String)
        case class Target(inner: Long, name: String)

        implicit val innerToLong: Into[Inner, Long] = (i: Inner) => Right(i.value.toLong * 2)

        val source = Source(Inner(21), "test")
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(inner = 42L, name = "test"))))
      },
      test("uses implicit Into that can fail") {
        case class Validated(value: Int)
        case class Source(validated: Validated, name: String)
        case class Target(validated: Int, name: String)

        implicit val validatedToInt: Into[Validated, Int] = (v: Validated) =>
          if (v.value > 0) Right(v.value)
          else Left(SchemaError.conversionFailed(Nil, s"Value must be positive: ${v.value}"))

        val validSource   = Source(Validated(10), "success")
        val invalidSource = Source(Validated(-5), "fail")

        assert(Into.derived[Source, Target].into(validSource))(isRight(equalTo(Target(10, "success")))) &&
        assert(Into.derived[Source, Target].into(invalidSource))(isLeft)
      }
    )
  )
}
