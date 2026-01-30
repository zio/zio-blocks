package zio.blocks.schema.into.disambiguation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for unique type disambiguation in Into conversions.
 *
 * When each type appears exactly once in both source and target, fields can be
 * mapped by their unique type regardless of name.
 */
object UniqueTypeDisambiguationSpec extends ZIOSpecDefault {

  // === Test types for unique type matching ===

  // Basic unique types
  case class SourceUniqueTypes(name: String, age: Int, active: Boolean)
  case class TargetUniqueTypes(fullName: String, years: Int, enabled: Boolean)

  // With type coercion
  case class SourceWithCoercion(id: Int, score: Float, label: String)
  case class TargetWithCoercion(identifier: Long, rating: Double, tag: String)

  // Multiple unique types
  case class SourceManyUnique(a: String, b: Int, c: Long, d: Boolean, e: Double)
  case class TargetManyUnique(s: String, i: Int, l: Long, b: Boolean, d: Double)

  // Unique type with Option
  case class SourceWithOption(name: String, count: Option[Int])
  case class TargetWithOption(label: String, amount: Option[Int])

  // Unique collection types
  case class SourceWithCollections(names: List[String], counts: Vector[Int])
  case class TargetWithCollections(labels: List[String], amounts: Vector[Int])

  // Custom types for uniqueness
  case class Email(value: String)
  case class PhoneNumber(value: String)
  case class Address(street: String, city: String)

  case class SourceCustomTypes(email: Email, phone: PhoneNumber, location: Address)
  case class TargetCustomTypes(contactEmail: Email, contactPhone: PhoneNumber, homeAddress: Address)

  // Nested unique types
  case class Inner(x: Int, y: Int)
  case class SourceNested(data: Inner, label: String)
  case class TargetNested(info: Inner, name: String)

  def spec: Spec[TestEnvironment, Any] = suite("UniqueTypeDisambiguationSpec")(
    suite("Basic Unique Type Matching")(
      test("maps fields by unique type when names differ") {
        val source = SourceUniqueTypes("Alice", 30, true)
        val result = Into.derived[SourceUniqueTypes, TargetUniqueTypes].into(source)

        assert(result)(isRight(equalTo(TargetUniqueTypes("Alice", 30, true))))
      },
      test("handles all primitive types uniquely") {
        val source = SourceManyUnique("test", 42, 100L, false, 3.14)
        val result = Into.derived[SourceManyUnique, TargetManyUnique].into(source)

        assert(result)(isRight(equalTo(TargetManyUnique("test", 42, 100L, false, 3.14))))
      }
    ),
    suite("Unique Type with Coercion")(
      test("maps and coerces unique types") {
        val source = SourceWithCoercion(42, 3.14f, "label")
        val result = Into.derived[SourceWithCoercion, TargetWithCoercion].into(source)

        assert(result)(isRight(equalTo(TargetWithCoercion(42L, 3.14f.toDouble, "label"))))
      }
    ),
    suite("Unique Optional Types")(
      test("maps Option fields by unique inner type") {
        val source = SourceWithOption("test", Some(42))
        val result = Into.derived[SourceWithOption, TargetWithOption].into(source)

        assert(result)(isRight(equalTo(TargetWithOption("test", Some(42)))))
      },
      test("handles None in optional fields") {
        val source = SourceWithOption("test", None)
        val result = Into.derived[SourceWithOption, TargetWithOption].into(source)

        assert(result)(isRight(equalTo(TargetWithOption("test", None))))
      }
    ),
    suite("Unique Collection Types")(
      test("maps collection fields by unique element type") {
        val source = SourceWithCollections(List("a", "b"), Vector(1, 2, 3))
        val result = Into.derived[SourceWithCollections, TargetWithCollections].into(source)

        assert(result)(isRight(equalTo(TargetWithCollections(List("a", "b"), Vector(1, 2, 3)))))
      }
    ),
    suite("Custom Unique Types")(
      test("maps custom case class types uniquely") {
        val source = SourceCustomTypes(
          Email("alice@example.com"),
          PhoneNumber("555-1234"),
          Address("123 Main St", "Springfield")
        )
        val result = Into.derived[SourceCustomTypes, TargetCustomTypes].into(source)

        assert(result)(
          isRight(
            equalTo(
              TargetCustomTypes(
                Email("alice@example.com"),
                PhoneNumber("555-1234"),
                Address("123 Main St", "Springfield")
              )
            )
          )
        )
      }
    ),
    suite("Nested Unique Types")(
      test("maps nested case class by unique type") {
        val source = SourceNested(Inner(10, 20), "test")
        val result = Into.derived[SourceNested, TargetNested].into(source)

        assert(result)(isRight(equalTo(TargetNested(Inner(10, 20), "test"))))
      }
    ),
    suite("Single Field")(
      test("single field always maps by unique type") {
        case class SourceSingle(value: Int)
        case class TargetSingle(number: Int)

        val source = SourceSingle(42)
        val result = Into.derived[SourceSingle, TargetSingle].into(source)

        assert(result)(isRight(equalTo(TargetSingle(42))))
      }
    ),
    suite("Two Fields")(
      test("two unique types map regardless of order") {
        case class Source2(a: String, b: Int)
        case class Target2(x: Int, y: String)

        val source = Source2("hello", 42)
        val result = Into.derived[Source2, Target2].into(source)

        assert(result)(isRight(equalTo(Target2(42, "hello"))))
      }
    ),
    suite("With Reordering")(
      test("unique types allow complete field reordering") {
        case class SourceReorder(a: String, b: Int, c: Boolean, d: Long)
        case class TargetReorder(w: Long, x: Boolean, y: Int, z: String)

        val source = SourceReorder("s", 1, true, 100L)
        val result = Into.derived[SourceReorder, TargetReorder].into(source)

        assert(result)(isRight(equalTo(TargetReorder(100L, true, 1, "s"))))
      }
    ),
    suite("Edge Cases")(
      test("unique types with default values still match by type") {
        case class SourceDefaults(name: String, count: Int)
        case class TargetDefaults(label: String = "default", amount: Int = 0)

        val source = SourceDefaults("test", 42)
        val result = Into.derived[SourceDefaults, TargetDefaults].into(source)

        assert(result)(isRight(equalTo(TargetDefaults("test", 42))))
      }
    )
  )
}
