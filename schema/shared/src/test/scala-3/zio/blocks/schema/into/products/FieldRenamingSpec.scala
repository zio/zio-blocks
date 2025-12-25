package zio.blocks.schema.into.products

import zio.test._
import zio.blocks.schema._

object FieldRenamingSpec extends ZIOSpecDefault {

  def spec = suite("FieldRenamingSpec")(
    suite("Basic Renaming (Unique Type Match)")(
      test("should rename fields using unique type matching (2 fields)") {
        case class PersonV1(fullName: String, yearOfBirth: Int)
        case class PersonV2(name: String, birthYear: Int) // Renamed, but unique types

        val derivation = Into.derived[PersonV1, PersonV2]
        val input      = PersonV1("Alice Smith", 1990)
        val result     = derivation.into(input)

        assertTrue(result == Right(PersonV2("Alice Smith", 1990)))
      },
      test("should rename fields using unique type matching (3 fields)") {
        case class V1(name: String, age: Int, active: Boolean)
        case class V2(fullName: String, yearsOld: Int, isActive: Boolean) // All renamed

        val derivation = Into.derived[V1, V2]
        val input      = V1("Bob", 30, true)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2("Bob", 30, true)))
      },
      test("should rename fields with completely different names") {
        case class V1(a: String, b: Int, c: Boolean)
        case class V2(x: String, y: Int, z: Boolean) // Completely different names

        val derivation = Into.derived[V1, V2]
        val input      = V1("first", 42, true)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2("first", 42, true)))
      }
    ),
    suite("Renaming with Mixed Types")(
      test("should rename fields with 3 different types") {
        case class V1(name: String, count: Int, price: Double)
        case class V2(title: String, quantity: Int, cost: Double) // All renamed, unique types

        val derivation = Into.derived[V1, V2]
        val input      = V1("Product", 10, 29.99)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2("Product", 10, 29.99)))
      },
      test("should rename fields with 4 different types") {
        case class V1(a: String, b: Int, c: Boolean, d: Double)
        case class V2(w: String, x: Int, y: Boolean, z: Double) // All renamed

        val derivation = Into.derived[V1, V2]
        val input      = V1("test", 42, true, 3.14)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2("test", 42, true, 3.14)))
      },
      test("should rename fields with 5 different types") {
        case class V1(a: String, b: Int, c: Boolean, d: Double, e: Long)
        case class V2(v: String, w: Int, x: Boolean, y: Double, z: Long) // All renamed

        val derivation = Into.derived[V1, V2]
        val input      = V1("one", 2, true, 4.0, 5L)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2("one", 2, true, 4.0, 5L)))
      }
    ),
    suite("Renaming with Coercion")(
      test("should rename fields with coercion (Int -> Long)") {
        case class V1(count: Int, name: String)
        case class V2(quantity: Long, title: String) // Renamed + coerced

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, "Item")
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(42L, "Item")))
      },
      test("should rename fields with multiple coercions") {
        case class V1(x: Int, y: Double, z: Float)
        case class V2(a: Long, b: Double, c: Float) // Renamed + x coerced

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, 3.14, 2.5f)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(42L, 3.14, 2.5f)))
      }
    ),
    suite("Mixed Renaming and Reordering")(
      test("should handle both renaming and reordering with unique types") {
        case class V1(name: String, age: Int, active: Boolean)
        case class V2(isActive: Boolean, fullName: String, years: Int) // Renamed + reordered, all unique types

        val derivation = Into.derived[V1, V2]
        val input      = V1("Alice", 30, true)
        val result     = derivation.into(input)

        // active -> isActive (unique Boolean), name -> fullName (unique String), age -> years (unique Int)
        assertTrue(result == Right(V2(true, "Alice", 30)))
      },
      test("should handle complex renaming and reordering") {
        case class V1(a: String, b: Int, c: Boolean, d: Double)
        case class V2(z: Double, y: Boolean, x: Int, w: String) // Renamed + completely reordered

        val derivation = Into.derived[V1, V2]
        val input      = V1("first", 2, true, 4.0)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(4.0, true, 2, "first")))
      }
    )
    // Note: Cases with duplicate types (e.g., two String fields) would require
    // position-based matching or should fail with compile error if ambiguous.
    // Those scenarios are tested in disambiguation/AmbiguousCompileErrorSpec.
  )
}
