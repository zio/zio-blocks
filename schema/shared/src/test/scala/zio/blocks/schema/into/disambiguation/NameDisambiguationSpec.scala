package zio.blocks.schema.into.disambiguation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for name-based disambiguation in Into conversions.
 *
 * When types are not unique, fields must match by name. Type coercion can still
 * be applied when names match.
 */
object NameDisambiguationSpec extends ZIOSpecDefault {

  // === Test types for name-based matching ===

  // Same types, must match by name
  case class SourceSameTypes(firstName: String, lastName: String)
  case class TargetSameTypes(firstName: String, lastName: String)

  // Same types with coercion, match by name
  case class SourceSameTypesCoercion(count: Int, total: Int)
  case class TargetSameTypesCoercion(count: Long, total: Long)

  // Multiple strings require name matching
  case class SourceMultiString(name: String, description: String, category: String)
  case class TargetMultiString(name: String, description: String, category: String)

  // Multiple ints require name matching
  case class SourceMultiInt(x: Int, y: Int, z: Int)
  case class TargetMultiInt(x: Int, y: Int, z: Int)

  // Mixed: some unique, some need name matching
  case class SourceMixed(id: Long, firstName: String, lastName: String, active: Boolean)
  case class TargetMixed(id: Long, firstName: String, lastName: String, active: Boolean)

  // Name match with type coercion
  case class SourceNameCoercion(min: Int, max: Int, avg: Float)
  case class TargetNameCoercion(min: Long, max: Long, avg: Double)

  // Collection fields with same element type
  case class SourceSameCollections(items: List[String], tags: List[String])
  case class TargetSameCollections(items: List[String], tags: List[String])

  // Optional fields with same inner type
  case class SourceSameOptions(primary: Option[Int], secondary: Option[Int])
  case class TargetSameOptions(primary: Option[Int], secondary: Option[Int])

  def spec: Spec[TestEnvironment, Any] = suite("NameDisambiguationSpec")(
    suite("Same Type Fields - Name Matching Required")(
      test("maps two String fields by name") {
        val source = SourceSameTypes("John", "Doe")
        val result = Into.derived[SourceSameTypes, TargetSameTypes].into(source)

        assert(result)(isRight(equalTo(TargetSameTypes("John", "Doe"))))
      },
      test("maps three String fields by name") {
        val source = SourceMultiString("Product", "A great product", "Electronics")
        val result = Into.derived[SourceMultiString, TargetMultiString].into(source)

        assert(result)(isRight(equalTo(TargetMultiString("Product", "A great product", "Electronics"))))
      },
      test("maps three Int fields by name") {
        val source = SourceMultiInt(10, 20, 30)
        val result = Into.derived[SourceMultiInt, TargetMultiInt].into(source)

        assert(result)(isRight(equalTo(TargetMultiInt(10, 20, 30))))
      }
    ),
    suite("Name Match with Type Coercion")(
      test("matches by name and applies Int to Long coercion") {
        val source = SourceSameTypesCoercion(100, 500)
        val result = Into.derived[SourceSameTypesCoercion, TargetSameTypesCoercion].into(source)

        assert(result)(isRight(equalTo(TargetSameTypesCoercion(100L, 500L))))
      },
      test("matches by name with multiple coercions") {
        val source = SourceNameCoercion(1, 10, 5.5f)
        val result = Into.derived[SourceNameCoercion, TargetNameCoercion].into(source)

        assert(result)(isRight(equalTo(TargetNameCoercion(1L, 10L, 5.5f.toDouble))))
      }
    ),
    suite("Mixed Unique and Name-Matched Fields")(
      test("uses unique type for unique fields, name for duplicates") {
        val source = SourceMixed(123L, "John", "Doe", true)
        val result = Into.derived[SourceMixed, TargetMixed].into(source)

        assert(result)(isRight(equalTo(TargetMixed(123L, "John", "Doe", true))))
      }
    ),
    suite("Collection Fields Requiring Name Match")(
      test("maps List[String] fields by name when type is not unique") {
        val source = SourceSameCollections(List("a", "b"), List("x", "y", "z"))
        val result = Into.derived[SourceSameCollections, TargetSameCollections].into(source)

        assert(result)(isRight(equalTo(TargetSameCollections(List("a", "b"), List("x", "y", "z")))))
      }
    ),
    suite("Optional Fields Requiring Name Match")(
      test("maps Option[Int] fields by name when type is not unique") {
        val source = SourceSameOptions(Some(1), Some(2))
        val result = Into.derived[SourceSameOptions, TargetSameOptions].into(source)

        assert(result)(isRight(equalTo(TargetSameOptions(Some(1), Some(2)))))
      },
      test("handles None values correctly") {
        val source = SourceSameOptions(None, Some(42))
        val result = Into.derived[SourceSameOptions, TargetSameOptions].into(source)

        assert(result)(isRight(equalTo(TargetSameOptions(None, Some(42)))))
      }
    ),
    suite("Priority: Name Match over Type Match")(
      test("exact name match takes priority over unique type") {
        // When a field has exact name match, it should use that over type matching
        case class Source(value: Int, count: Int)
        case class Target(value: Int, count: Int)

        val source = Source(1, 2)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(1, 2))))
      }
    ),
    suite("Complex Name Matching")(
      test("four fields of same type match by name") {
        case class Source4Same(a: String, b: String, c: String, d: String)
        case class Target4Same(a: String, b: String, c: String, d: String)

        val source = Source4Same("1", "2", "3", "4")
        val result = Into.derived[Source4Same, Target4Same].into(source)

        assert(result)(isRight(equalTo(Target4Same("1", "2", "3", "4"))))
      },
      test("many fields of same type all match by name") {
        case class SourceMany(f1: Int, f2: Int, f3: Int, f4: Int, f5: Int)
        case class TargetMany(f1: Int, f2: Int, f3: Int, f4: Int, f5: Int)

        val source = SourceMany(1, 2, 3, 4, 5)
        val result = Into.derived[SourceMany, TargetMany].into(source)

        assert(result)(isRight(equalTo(TargetMany(1, 2, 3, 4, 5))))
      }
    ),
    suite("Name Match with Narrowing")(
      test("narrows Long to Int when names match and values fit") {
        case class SourceNarrow(a: Long, b: Long)
        case class TargetNarrow(a: Int, b: Int)

        val source = SourceNarrow(10L, 20L)
        val result = Into.derived[SourceNarrow, TargetNarrow].into(source)

        assert(result)(isRight(equalTo(TargetNarrow(10, 20))))
      },
      test("fails when narrowing overflows despite name match") {
        case class SourceNarrow(a: Long, b: Long)
        case class TargetNarrow(a: Int, b: Int)

        val source = SourceNarrow(Long.MaxValue, 20L)
        val result = Into.derived[SourceNarrow, TargetNarrow].into(source)

        assert(result)(isLeft)
      }
    )
  )
}
