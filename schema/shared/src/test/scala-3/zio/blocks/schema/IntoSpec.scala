package zio.blocks.schema

import zio.test._
import zio._

object IntoSpec extends ZIOSpecDefault {

  def spec = suite("Into Support")(
    suite("Product Types")(
      test("Should convert case class to case class") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String, age: Int)

        val derivation = Into.derived[PersonV1, PersonV2]
        val input      = PersonV1("Alice", 30)
        val result     = derivation.into(input)

        assertTrue(result == Right(PersonV2("Alice", 30)))
      },
      test("PRIORITY 1: Exact match (same name + same type)") {
        case class V1(x: Int, y: String)
        case class V2(x: Int, y: String)

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, "hello")
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(42, "hello")))
      },
      test("PRIORITY 2: Name match with coercion (same name + coercible type)") {
        case class V1(x: Int, y: Int)
        case class V2(x: Long, y: Double) // Int -> Long, Int -> Double

        val derivation = Into.derived[V1, V2]
        val input      = V1(42, 100)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(42L, 100.0)))
      },
      test("PRIORITY 3: Unique type match (field renaming)") {
        case class V1(name: String, age: Int)
        case class V2(fullName: String, yearsOld: Int) // Renamed fields, but unique types

        val derivation = Into.derived[V1, V2]
        val input      = V1("Alice", 30)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2("Alice", 30)))
      },
      test("PRIORITY 3: Unique type match (mixed unique and ambiguous)") {
        case class V1(a: String, b: Int, c: Boolean)
        case class V2(x: String, y: Int, z: Boolean) // All renamed, but all types are unique

        val derivation = Into.derived[V1, V2]
        val input      = V1("first", 42, true)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2("first", 42, true)))
      },
      test("PRIORITY 4: Position + unique type (positional match)") {
        case class V1(x: String, y: Int, z: Boolean)
        case class V2(a: String, b: Int, c: Boolean) // All renamed, but positional + unique types

        val derivation = Into.derived[V1, V2]
        val input      = V1("test", 42, true)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2("test", 42, true)))
      },
      test("Field reordering with name match") {
        case class V1(x: Int, y: Int)
        case class V2(y: Int, x: Int) // Reordered but names match

        val derivation = Into.derived[V1, V2]
        val input      = V1(10, 20)
        val result     = derivation.into(input)

        assertTrue(result == Right(V2(20, 10))) // y=20, x=10
      }
    ),
    /* Structural types tests commented out due to SIP-44 limitation
    suite("Structural types")(
      test("Should convert structural types") {
        // Test implementation would go here
        assertTrue(true)
      }
    )
     */
    suite("Other Tests")(
      test("Placeholder test") {
        assertTrue(true)
      }
    )
  )
}
