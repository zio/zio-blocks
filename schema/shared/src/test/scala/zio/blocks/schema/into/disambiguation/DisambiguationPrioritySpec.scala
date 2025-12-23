package zio.blocks.schema.into.disambiguation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for disambiguation priority in Into conversions.
 *
 * The priority order is:
 *   1. Exact match: Same name + same type
 *   2. Name match with coercion: Same name + coercible type
 *   3. Unique type match: Type appears only once in both
 *   4. Position + unique type: Positional correspondence
 */
object DisambiguationPrioritySpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("DisambiguationPrioritySpec")(
    suite("Priority 1: Exact Name+Type Match")(
      test("exact match takes highest priority") {
        case class Source(name: String, count: Int)
        case class Target(name: String, count: Int)

        val source = Source("test", 42)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target("test", 42))))
      },
      test("exact match preferred over unique type when both available") {
        // Both fields have unique types, but exact name match should still be used
        case class Source(id: Long, active: Boolean)
        case class Target(id: Long, active: Boolean)

        val source = Source(123L, true)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(123L, true))))
      }
    ),
    suite("Priority 2: Name Match with Coercion")(
      test("name match with coercion takes priority over unique type match") {
        case class Source(value: Int, flag: Boolean)
        case class Target(value: Long, flag: Boolean)

        val source = Source(42, true)
        val result = Into.derived[Source, Target].into(source)

        // Should use name match with coercion for 'value', not just unique type
        assert(result)(isRight(equalTo(Target(42L, true))))
      },
      test("multiple name matches with coercion") {
        case class Source(min: Int, max: Int, label: String)
        case class Target(min: Long, max: Long, label: String)

        val source = Source(1, 100, "range")
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(1L, 100L, "range"))))
      }
    ),
    suite("Priority 3: Unique Type Match")(
      test("unique type match when names don't match") {
        case class Source(firstName: String, age: Int, active: Boolean)
        case class Target(fullName: String, years: Int, enabled: Boolean)

        val source = Source("Alice", 30, true)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target("Alice", 30, true))))
      },
      test("unique type match with coercion") {
        case class Source(id: Int, label: String, flag: Boolean)
        case class Target(identifier: Long, name: String, active: Boolean)

        val source = Source(1, "test", false)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(1L, "test", false))))
      }
    ),
    suite("Mixed Priorities")(
      test("combines exact match with unique type match") {
        case class Source(id: Long, name: String, active: Boolean)
        case class Target(id: Long, label: String, enabled: Boolean)

        // 'id' matches by name+type, others by unique type
        val source = Source(123L, "test", true)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(123L, "test", true))))
      },
      test("combines name+coercion with unique type match") {
        case class Source(count: Int, name: String, active: Boolean)
        case class Target(count: Long, label: String, enabled: Boolean)

        // 'count' matches by name+coercion, others by unique type
        val source = Source(42, "test", false)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(42L, "test", false))))
      }
    ),
    suite("Fallback to Lower Priority")(
      test("falls back to unique type when name doesn't exist in target") {
        case class Source(sourceValue: Int, name: String)
        case class Target(targetValue: Int, name: String)

        // 'name' exact match, 'sourceValue' -> 'targetValue' by unique type
        val source = Source(42, "test")
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(42, "test"))))
      }
    ),
    suite("Complex Disambiguation Scenarios")(
      test("three String fields - all must use name matching") {
        case class Source(first: String, middle: String, last: String)
        case class Target(first: String, middle: String, last: String)

        val source = Source("A", "B", "C")
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target("A", "B", "C"))))
      },
      test("two String fields with one Int - String by name, Int by unique type") {
        case class Source(firstName: String, lastName: String, age: Int)
        case class Target(firstName: String, lastName: String, years: Int)

        // Strings match by name, Int matches by unique type
        val source = Source("John", "Doe", 30)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target("John", "Doe", 30))))
      }
    ),
    suite("Priority with Optional Fields")(
      test("exact name+type match for Option fields") {
        case class Source(value: Option[Int])
        case class Target(value: Option[Int])

        val source = Source(Some(42))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Some(42)))))
      },
      test("name match with coercion for Option fields") {
        case class Source(value: Option[Int])
        case class Target(value: Option[Long])

        val source = Source(Some(42))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Some(42L)))))
      }
    ),
    suite("Priority with Collection Fields")(
      test("exact name+type match for List fields") {
        case class Source(items: List[Int])
        case class Target(items: List[Int])

        val source = Source(List(1, 2, 3))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(List(1, 2, 3)))))
      },
      test("name match with element coercion for List fields") {
        case class Source(items: List[Int])
        case class Target(items: List[Long])

        val source = Source(List(1, 2, 3))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(List(1L, 2L, 3L)))))
      }
    )
  )
}
