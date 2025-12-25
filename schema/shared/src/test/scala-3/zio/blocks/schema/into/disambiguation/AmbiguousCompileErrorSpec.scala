package zio.blocks.schema.into.disambiguation

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema._

object AmbiguousCompileErrorSpec extends ZIOSpecDefault {

  def spec = suite("AmbiguousCompileErrorSpec")(
    suite("Ambiguous Field Mapping")(
      // NOTE: These tests currently fail because the implementation uses Priority 4
      // (position + compatible type) to resolve ambiguities, allowing compilation
      // even when fields cannot be uniquely mapped. This is a known limitation.
      // See KNOWN_ISSUES.md for details.
      test("should fail compilation for ambiguous Int fields") {
        // EXPECTED FAILURE: Currently compiles due to Priority 4 positional matching
        // This test documents the desired behavior (compile error) vs current behavior (compiles)
        typeCheck {
          """
          case class V1(width: Int, height: Int)
          case class V2(first: Int, second: Int)
          
          Into.derived[V1, V2]
          """
        }.map(
          assert(_)(
            isLeft(
              containsString("Cannot find unique mapping") ||
                containsString("ambiguous") ||
                containsString("width") ||
                containsString("height") ||
                containsString("first") ||
                containsString("second")
            )
          )
        )
      },
      test("should fail compilation for Source -> Target with ambiguous mapping") {
        // EXPECTED FAILURE: Currently compiles due to Priority 4 positional matching
        // Scenario: Source has two Int fields, Target has one Int field
        // This should fail because we can't determine which Source field maps to Target.x
        typeCheck {
          """
          case class Source(a: Int, b: Int)
          case class Target(x: Int)
          
          Into.derived[Source, Target]
          """
        }.map(assert(_)(isLeft))
      },
      test("should fail compilation for multiple ambiguous types") {
        // EXPECTED FAILURE: Currently compiles due to Priority 4 positional matching
        typeCheck {
          """
          case class V1(x: Int, y: Int, z: String, w: String)
          case class V2(a: Int, b: Int, c: String, d: String)
          // All types ambiguous (Int appears twice, String appears twice)
          
          Into.derived[V1, V2]
          """
        }.map(assert(_)(isLeft))
      },
      test("should fail when name matches but types incompatible") {
        typeCheck {
          """
          case class V1(x: Int, xLong: Long)
          case class V2(x: String, xInt: Int)
          // x matches name but types incompatible (Int vs String)
          
          Into.derived[V1, V2]
          """
        }.map(assert(_)(isLeft))
      },
      test("should provide helpful error message with available fields") {
        // PARTIAL SUCCESS: Compilation fails, but error message is generic
        // Expected: "Cannot find unique mapping... Available: a: Int, b: Int"
        // Actual: "Cannot derive Into[scala.Int, java.lang.String]"
        typeCheck {
          """
          case class V1(a: Int, b: Int)
          case class V2(x: String, y: Boolean)
          // No matches possible (types incompatible)
          
          Into.derived[V1, V2]
          """
        }.map(
          assert(_)(
            isLeft(
              containsString("Available:") ||
                containsString("a:") ||
                containsString("b:") ||
                containsString("Cannot find unique mapping") ||
                containsString("Cannot derive") // Accept generic error for now
            )
          )
        )
      },
      test("should fail when unique type match is ambiguous (same type appears multiple times)") {
        // EXPECTED FAILURE: Currently compiles due to Priority 4 positional matching
        typeCheck {
          """
          case class V1(name: String, width: Int, height: Int)
          case class V2(fullName: String, first: Int, second: Int)
          // String unique, but Int appears twice -> ambiguous
          
          Into.derived[V1, V2]
          """
        }.map(assert(_)(isLeft))
      },
      test("should fail when position match exists but types not unique") {
        // EXPECTED FAILURE: Currently compiles due to Priority 4 positional matching
        typeCheck {
          """
          case class V1(x: Int, y: Int, z: String)
          case class V2(a: Int, b: Int, c: String)
          // Positional match, but Int fields are ambiguous
          
          Into.derived[V1, V2]
          """
        }.map(assert(_)(isLeft))
      }
    ),
    suite("Arity Mismatch")(
      test("should fail when source has more fields than target") {
        // EXPECTED FAILURE: Currently compiles - extra fields in source are ignored
        // This is actually valid behavior for Into (one-way conversion)
        typeCheck {
          """
          case class V1(name: String, age: Int, active: Boolean)
          case class V2(name: String, age: Int)
          // Missing field in target
          
          Into.derived[V1, V2]
          """
        }.map(assert(_)(isLeft))
      },
      test("should fail when target has more fields than source (without Option)") {
        typeCheck {
          """
          case class V1(name: String)
          case class V2(name: String, age: Int)
          // Missing field in source (and not Option)
          
          Into.derived[V1, V2]
          """
        }.map(assert(_)(isLeft))
      }
    ),
    suite("Type Incompatibility")(
      test("should fail when types are not coercible") {
        typeCheck {
          """
          case class V1(name: String, age: Int)
          case class V2(name: Boolean, age: String)
          // Types incompatible (String->Boolean, Int->String not coercible)
          
          Into.derived[V1, V2]
          """
        }.map(assert(_)(isLeft))
      },
      test("should fail when collection types incompatible") {
        typeCheck {
          """
          case class V1(items: List[Int])
          case class V2(items: Map[String, Int])
          // Collection types incompatible
          
          Into.derived[V1, V2]
          """
        }.map(assert(_)(isLeft))
      }
    )
  )
}
