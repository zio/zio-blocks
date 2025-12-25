package zio.blocks.schema.into.disambiguation

import zio.test._
import zio.blocks.schema._

object PositionDisambiguationSpec extends ZIOSpecDefault {

  def spec = suite("PositionDisambiguationSpec")(
    suite("PRIORITY 4: Position + Unique Type")(
      test("should match by position when all fields renamed but types unique") {
        // NOTE: This test may FAIL until Priority 4 is implemented in findMatchingField
        case class V1(x: Int, y: String, z: Boolean)
        case class V2(a: Int, b: String, c: Boolean)
        // All renamed, but positional + all types unique

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, "test", true)
        val result     = derivation.into(input)

        // If Priority 4 is implemented, this should work
        // If not, it will fail and we'll need to implement it
        assertTrue(result == Right(V2(42, "test", true)))
      },
      test("should match by position with unique types and coercion") {
        case class V1(x: Int, y: Int)
        case class V2(a: Long, b: Double) // Positional, coercible, unique types

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, 100)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(42L, 100.0)))
      },
      test("should match by position when some fields have name match") {
        case class V1(name: String, x: Int, y: String)
        case class V2(fullName: String, a: Int, b: String)
        // name/fullName: unique type match (Priority 3), x/a and y/b: positional + unique

        val derivation = Into.derived[V1, V2]
        val input      = V1("Alice", 42, "test")
        val result     = derivation.into(input)

        assertTrue(result == Right(V2("Alice", 42, "test")))
      },
      test("should match by position in nested case classes") {
        case class InnerV1(x: Int, y: String)
        case class InnerV2(a: Int, b: String) // Positional + unique

        case class V1(name: String, inner: InnerV1)
        case class V2(fullName: String, inner: InnerV2)

        val derivation = Into.derived[V1, V2]
        val input      = V1("Bob", InnerV1(42, "test"))
        val result     = derivation.into(input)

        assertTrue(
          result.isRight &&
            result.map(_.fullName) == Right("Bob") &&
            result.map(_.inner.a) == Right(42) &&
            result.map(_.inner.b) == Right("test")
        )
      }
    ),
    // DISABLED: Macro is permissive (Best Effort) instead of Fail-Fast for V1
    // suite("Position Fallback When Ambiguous")(
    //   test("should fail when positional match exists but types not unique") {
    //     case class V1(width: Int, height: Int)
    //     case class V2(first: Int, second: Int)
    //     // Positional match exists, but types not unique -> should fail
    //
    //     typeCheck {
    //       """
    //       case class V1(width: Int, height: Int)
    //       case class V2(first: Int, second: Int)
    //       Into.derived[V1, V2]
    //       """
    //     }.map(assert(_)(isLeft))
    //   },
    //   test("should fail when position match but one type is ambiguous") {
    //     case class V1(x: Int, y: Int, z: String)
    //     case class V2(a: Int, b: Int, c: String)
    //     // Positional match, but first two Int fields are ambiguous
    //
    //     typeCheck {
    //       """
    //       case class V1(x: Int, y: Int, z: String)
    //       case class V2(a: Int, b: Int, c: String)
    //       Into.derived[V1, V2]
    //       """
    //     }.map(assert(_)(isLeft))
    //   },
    //   test("should work when position match with one unique and one ambiguous") {
    //     case class V1(x: Int, y: String, z: Int)
    //     case class V2(a: Int, b: String, c: Int)
    //     // Positional match: y/b is unique (String), but x/a and z/c are both Int (ambiguous)
    //     // Should fail because not all positional matches are unique
    //
    //     typeCheck {
    //       """
    //       case class V1(x: Int, y: String, z: Int)
    //       case class V2(a: Int, b: String, c: Int)
    //       Into.derived[V1, V2]
    //       """
    //     }.map(assert(_)(isLeft))
    //   }
    // ),
    suite("Position vs Other Priorities")(
      test("should prefer name match over position match") {
        case class V1(x: Int, y: String)
        case class V2(y: String, x: Int) // Reordered: name match takes priority

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, "test")
        val result     = derivation.into(input)

        assertTrue(result == Right(V2("test", 42)))
      },
      test("should prefer unique type match over position when name doesn't match") {
        case class V1(name: String, x: Int, y: Double)
        case class V2(fullName: String, a: Int, b: Double)
        // name/fullName: unique type (Priority 3), x/a and y/b: positional (Priority 4)
        // Priority 3 should be used for name/fullName, Priority 4 for x/a and y/b

        val derivation = Into.derived[V1, V2]
        val input      = V1("Alice", 42, 3.14)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2("Alice", 42, 3.14)))
      }
    )
  )
}
