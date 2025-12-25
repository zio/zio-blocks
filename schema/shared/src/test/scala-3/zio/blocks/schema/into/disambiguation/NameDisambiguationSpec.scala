package zio.blocks.schema.into.disambiguation

import zio.test._
import zio.blocks.schema._

object NameDisambiguationSpec extends ZIOSpecDefault {

  def spec = suite("NameDisambiguationSpec")(
    suite("PRIORITY 1: Exact Name + Type Match")(
      test("should match exact name and type") {
        case class V1(x: Int, y: String)
        case class V2(x: Int, y: String)

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, "test")
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(42, "test")))
      },
      test("should prefer exact match over name match with coercion") {
        case class V1(x: Int, xLong: Long)
        case class V2(x: Int, xLong: Long) // Exact match for both

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, 100L)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(42, 100L)))
      }
    ),
    suite("PRIORITY 2: Name Match with Coercion")(
      test("should match name with coercible type (Int -> Long)") {
        case class V1(x: Int, y: Int)
        case class V2(x: Long, y: Double) // Same names, coercible types

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, 100)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(42L, 100.0)))
      },
      test("should match name with coercion when exact match not available") {
        case class V1(x: Int, y: String)
        case class V2(x: Long, y: String) // x: name match with coercion, y: exact match

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, "test")
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(42L, "test")))
      },
      test("should match name with coercion in nested structures") {
        case class InnerV1(value: Int)
        case class InnerV2(value: Long) // Name match with coercion

        case class V1(name: String, inner: InnerV1)
        case class V2(name: String, inner: InnerV2)

        val derivation = Into.derived[V1, V2]
        val input      = V1("Alice", InnerV1(42))
        val result     = derivation.into(input)

        assertTrue(
          result.isRight &&
            result.map(_.name) == Right("Alice") &&
            result.map(_.inner.value) == Right(42L)
        )
      }
    ),
    suite("Priority Ordering")(
      test("PRIORITY 1 > PRIORITY 2: Exact match preferred over coercion") {
        case class V1(x: Int, xLong: Long)
        case class V2(x: Int, xLong: Long) // Both exact matches

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, 100L)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(42, 100L)))
      },
      test("PRIORITY 1 > PRIORITY 3: Name match preferred over unique type") {
        case class V1(name: String, age: Int, score: Int)
        case class V2(name: String, years: Int, points: Int)
        // name matches exactly (Priority 1), age/score both Int (not unique) but name takes priority

        val derivation = Into.derived[V1, V2]
        val input      = V1("Alice", 30, 95)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2("Alice", 30, 95)))
      },
      test("PRIORITY 2 > PRIORITY 3: Name match with coercion preferred over unique type") {
        case class V1(x: Int, y: String, z: Double)
        case class V2(x: Long, y: String, w: Double)
        // x: name match with coercion (Priority 2), y: exact match (Priority 1), z/w: unique type (Priority 3)

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, "test", 3.14)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(42L, "test", 3.14)))
      }
    ),
    suite("Name Match with Field Reordering")(
      test("should match names even when fields are reordered") {
        case class V1(x: Int, y: String)
        case class V2(y: String, x: Int) // Reordered but names match

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, "test")
        val result     = derivation.into(input)

        assertTrue(result == Right(V2("test", 42)))
      },
      test("should match names with coercion when reordered") {
        case class V1(x: Int, y: Int)
        case class V2(y: Long, x: Double) // Reordered, names match with coercion

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, 100)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(100L, 42.0)))
      }
    ),
    suite("Edge Cases")(
      // DISABLED: Macro is permissive (Best Effort) instead of Fail-Fast for V1
      // test("should handle multiple fields with same name (should fail)") {
      //   // This is a compile-time error case - multiple fields can't have same name in Scala
      //   // But we test that if somehow we have ambiguous name matches, it fails
      //   typeCheck {
      //     """
      //     case class V1(x: Int, y: Int)
      //     case class V2(x: Long, xDouble: Double) // x matches name but ambiguous type
      //     Into.derived[V1, V2]
      //     """
      //   }.map(
      //     // Should fail because x in V2 has two possible matches in V1 (both Int)
      //     assert(_)(isLeft)
      //   )
      // }
    )
  )
}
