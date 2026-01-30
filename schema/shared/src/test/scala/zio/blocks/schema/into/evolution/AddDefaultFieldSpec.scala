package zio.blocks.schema.into.evolution

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for schema evolution with default values.
 *
 * Covers:
 *   - Using default values for missing fields in target
 *   - Default values combined with optional fields
 *   - Default values with type coercion
 *   - Precedence of source values over defaults
 *
 * Note: Default value support works in both Scala 2.13 and Scala 3.
 */
object AddDefaultFieldSpec extends ZIOSpecDefault {

  // === Test types with default values ===

  // Single default value
  case class PersonV1(name: String)
  case class PersonV2(name: String, age: Int = 0)

  // Multiple default values
  case class ConfigV1(key: String)
  case class ConfigV2(key: String, timeout: Int = 30, retries: Int = 3)

  // Default value with type coercion
  case class CounterV1(name: String)
  case class CounterV2(name: String, count: Long = 0L)

  // Default combined with optional
  case class SimpleV1(id: Int)
  case class ComplexV2(id: Int, name: String = "unknown", description: Option[String])

  // Non-primitive default values
  case class ContainerV1(id: Int)
  case class ContainerV2(id: Int, tags: List[String] = List.empty)

  // Nested type with defaults
  case class InnerDefault(value: Int = 42)
  case class OuterV1(name: String)
  case class OuterV2(name: String, inner: InnerDefault = InnerDefault())

  // Source with existing fields that match target defaults
  case class SourceWithAge(name: String, age: Int)
  case class SourceZero(key: String, timeout: Int, retries: Int)

  // Edge case types - empty source with all defaults target
  case class EmptySource()
  case class AllDefaultsTarget(a: Int = 1, b: String = "default", c: Boolean = true)

  // Edge case - case object as empty source
  case object EmptyObject
  case class AllDefaultsFromObject(x: Int = 100, y: String = "fromObject")

  // Edge case - multiple String fields need defaults at different positions
  case class SourcePos(name: String)
  case class TargetPos(prefix: String = "Mr.", name: String, suffix: String = "")

  // Unique type disambiguation with extra default
  case class SourceUnique(value: Int, flag: Boolean)
  case class TargetUnique(number: Int, active: Boolean, extra: String = "none")

  // Complex default values
  case class SourceOpt(id: Int)
  case class TargetOpt(id: Int, data: Option[String] = Some("default"))

  case class SourceMap(id: Int)
  case class TargetMap(id: Int, metadata: Map[String, Int] = Map("version" -> 1))

  def spec: Spec[TestEnvironment, Any] = suite("AddDefaultFieldSpec")(
    suite("Single Default Value")(
      test("uses default value for missing field") {
        val source = PersonV1("Alice")
        val result = Into.derived[PersonV1, PersonV2].into(source)

        assert(result)(isRight(equalTo(PersonV2("Alice", 0))))
      },
      test("existing fields map correctly with default present") {
        val source = PersonV1("Bob")
        val result = Into.derived[PersonV1, PersonV2].into(source)

        assert(result.map(_.name))(isRight(equalTo("Bob")))
      }
    ),
    suite("Multiple Default Values")(
      test("uses multiple defaults for missing fields") {
        val source = ConfigV1("database")
        val result = Into.derived[ConfigV1, ConfigV2].into(source)

        assert(result)(isRight(equalTo(ConfigV2("database", 30, 3))))
      }
    ),
    suite("Default Value with Type Coercion")(
      test("applies coercion to existing field with default on new field") {
        val source = CounterV1("hits")
        val result = Into.derived[CounterV1, CounterV2].into(source)

        assert(result)(isRight(equalTo(CounterV2("hits", 0L))))
      }
    ),
    suite("Default Combined with Optional")(
      test("uses default for required field and None for optional") {
        val source = SimpleV1(1)
        val result = Into.derived[SimpleV1, ComplexV2].into(source)

        assert(result)(isRight(equalTo(ComplexV2(1, "unknown", None))))
      }
    ),
    suite("Non-Primitive Default Values")(
      test("uses empty List as default") {
        val source = ContainerV1(1)
        val result = Into.derived[ContainerV1, ContainerV2].into(source)

        assert(result)(isRight(equalTo(ContainerV2(1, List.empty))))
      }
    ),
    suite("Precedence: Source Values over Defaults")(
      test("source value used when field exists in both") {
        case class SourceWithAge(name: String, age: Int)

        val source = SourceWithAge("Charlie", 25)
        val result = Into.derived[SourceWithAge, PersonV2].into(source)

        assert(result)(isRight(equalTo(PersonV2("Charlie", 25))))
      },
      test("source value overrides default even when source is 0") {
        case class SourceZero(key: String, timeout: Int, retries: Int)

        val source = SourceZero("test", 0, 0)
        val result = Into.derived[SourceZero, ConfigV2].into(source)

        assert(result)(isRight(equalTo(ConfigV2("test", 0, 0))))
      }
    ),
    suite("Edge Cases")(
      test("empty case class source uses all defaults") {
        val source = EmptySource()
        val result = Into.derived[EmptySource, AllDefaultsTarget].into(source)

        assert(result)(isRight(equalTo(AllDefaultsTarget(1, "default", true))))
      },
      test("case object source uses all defaults in target") {
        val result = Into.derived[EmptyObject.type, AllDefaultsFromObject].into(EmptyObject)

        assert(result)(isRight(equalTo(AllDefaultsFromObject(100, "fromObject"))))
      },
      test("default value at different positions") {
        val source = SourcePos("Smith")
        val result = Into.derived[SourcePos, TargetPos].into(source)

        assert(result)(isRight(equalTo(TargetPos("Mr.", "Smith", ""))))
      }
    ),
    suite("Defaults with Unique Type Disambiguation")(
      test("unique type matching still works with defaults present") {
        val source = SourceUnique(42, true)
        val result = Into.derived[SourceUnique, TargetUnique].into(source)

        assert(result)(isRight(equalTo(TargetUnique(42, true, "none"))))
      }
    ),
    suite("Complex Default Values")(
      test("uses default Option[T] value") {
        val source = SourceOpt(1)
        val result = Into.derived[SourceOpt, TargetOpt].into(source)

        assert(result)(isRight(equalTo(TargetOpt(1, Some("default")))))
      },
      test("uses default Map value") {

        val source = SourceMap(1)
        val result = Into.derived[SourceMap, TargetMap].into(source)

        assert(result)(isRight(equalTo(TargetMap(1, Map("version" -> 1)))))
      }
    )
  )
}
