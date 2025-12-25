package zio.blocks.schema.into.products

import zio.test._
import zio.blocks.schema._

object FieldReorderingSpec extends ZIOSpecDefault {

  def spec = suite("FieldReorderingSpec")(
    suite("Basic Reordering")(
      test("should reorder fields using name matching (2 fields)") {
        case class V1(x: Int, y: Int)
        case class V2(y: Int, x: Int) // Reordered but names match

        val derivation = Into.derived[V1, V2]
        val input      = V1(10, 20)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(20, 10))) // y=20, x=10
      },
      test("should reorder fields using name matching (3 fields)") {
        case class PersonV1(name: String, age: Int, email: String)
        case class PersonV2(email: String, name: String, age: Int) // Reordered

        val derivation = Into.derived[PersonV1, PersonV2]
        val input      = PersonV1("Alice", 30, "alice@example.com")
        val result     = derivation.into(input)

        assertTrue(result == Right(PersonV2("alice@example.com", "Alice", 30)))
      },
      test("should reorder fields using name matching (4 fields)") {
        case class V1(a: Int, b: String, c: Boolean, d: Double)
        case class V2(d: Double, b: String, a: Int, c: Boolean) // Reordered

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, "hello", true, 3.14)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(3.14, "hello", 42, true)))
      }
    ),
    suite("Reordering with Mixed Types")(
      test("should reorder fields with mixed types using name matching") {
        case class V1(name: String, age: Int, active: Boolean, score: Double)
        case class V2(score: Double, active: Boolean, name: String, age: Int) // Reordered

        val derivation = Into.derived[V1, V2]
        val input      = V1("Bob", 25, false, 95.5)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(95.5, false, "Bob", 25)))
      },
      test("should reorder fields with all different types") {
        case class V1(a: Int, b: String, c: Boolean, d: Double, e: Long)
        case class V2(e: Long, c: Boolean, a: Int, d: Double, b: String) // Reordered

        val derivation = Into.derived[V1, V2]
        val input      = V1(1, "two", true, 4.0, 5L)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(5L, true, 1, 4.0, "two")))
      }
    ),
    suite("Reordering with Coercion")(
      test("should reorder fields with coercion (Int -> Long)") {
        case class V1(x: Int, y: Int)
        case class V2(y: Long, x: Long) // Reordered + coerced

        val derivation = Into.derived[V1, V2]
        val input      = V1(10, 20)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(20L, 10L)))
      },
      test("should reorder fields with mixed coercion") {
        case class V1(x: Int, y: Double, z: Float)
        case class V2(z: Float, x: Long, y: Double) // Reordered + x coerced

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, 3.14, 2.5f)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(2.5f, 42L, 3.14)))
      },
      test("should reorder fields with multiple coercions") {
        case class V1(a: Int, b: Int, c: Int)
        case class V2(c: Long, a: Double, b: Float) // Reordered + all coerced

        val derivation = Into.derived[V1, V2]
        val input      = V1(10, 20, 30)
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_.a) == Right(10.0))
        assertTrue(result.map(_.b) == Right(20.0f))
        assertTrue(result.map(_.c) == Right(30L))
      }
    ),
    suite("Complex Reordering Scenarios")(
      test("should handle partial reordering (some fields in same position)") {
        case class V1(a: Int, b: String, c: Boolean, d: Double)
        case class V2(a: Int, d: Double, b: String, c: Boolean) // a stays, others reordered

        val derivation = Into.derived[V1, V2]
        val input      = V1(1, "two", true, 4.0)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(1, 4.0, "two", true)))
      },
      test("should handle complete reversal (all fields reversed)") {
        case class V1(a: Int, b: String, c: Boolean)
        case class V2(c: Boolean, b: String, a: Int) // Complete reversal

        val derivation = Into.derived[V1, V2]
        val input      = V1(1, "two", true)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(true, "two", 1)))
      }
    )
  )
}
