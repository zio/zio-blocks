package zio.blocks.schema

import zio.test._
import zio._

// Opaque types for testing
opaque type UserId = String
object UserId {
  def apply(s: String): Either[String, UserId] =
    if (s.nonEmpty && s.length <= 50) Right(s)
    else Left("UserId must be non-empty and at most 50 characters")
}

opaque type PositiveInt = Int
object PositiveInt {
  def apply(n: Int): Either[String, PositiveInt] =
    if (n > 0) Right(n)
    else Left(s"PositiveInt must be positive, got $n")
}

opaque type Age = Int
object Age {
  def apply(value: Int): Either[String, Age] =
    if (value >= 0 && value <= 150) Right(value)
    else Left(s"Invalid age: $value")
}

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
    suite("Tuple Conversions")(
      test("Case class to tuple") {
        case class RGB(r: Int, g: Int, b: Int)
        type ColorTuple = (Int, Int, Int)

        val derivation = Into.derived[RGB, ColorTuple]
        val input      = RGB(255, 128, 0)
        val result     = derivation.into(input)

        assertTrue(result == Right((255, 128, 0)))
      },
      test("Tuple to case class") {
        case class RGB(r: Int, g: Int, b: Int)
        type ColorTuple = (Int, Int, Int)

        val derivation = Into.derived[ColorTuple, RGB]
        val input      = (255, 128, 0)
        val result     = derivation.into(input)

        assertTrue(result == Right(RGB(255, 128, 0)))
      },
      test("Tuple to tuple (same arity)") {
        type Tuple1 = (Int, String)
        type Tuple2 = (Long, String)

        val derivation = Into.derived[Tuple1, Tuple2]
        val input      = (42, "hello")
        val result     = derivation.into(input)

        assertTrue(result == Right((42L, "hello")))
      },
      test("Tuple to tuple (with coercion)") {
        type Tuple1 = (Int, Double)
        type Tuple2 = (Long, Float)

        val derivation = Into.derived[Tuple1, Tuple2]
        val input      = (42, 3.14)
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        result.fold(
          _ => assertTrue(false),
          tuple => {
            val (i, f) = tuple
            assertTrue(i == 42L)
            assertTrue((f - 3.14f).abs < 0.001) // Tolleranza per Float precision
          }
        )
      }
    ),
    suite("Opaque Types")(
      test("Should convert underlying type to opaque type (direct)") {
        val derivation = Into.derived[String, UserId]
        val input      = "alice123"
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_.toString) == Right("alice123"))
      },
      test("Should convert underlying type to opaque type (with validation failure)") {
        val derivation = Into.derived[String, UserId]
        val input      = "a" * 100 // Too long
        val result     = derivation.into(input)

        assertTrue(result.isLeft)
        assertTrue(result.left.exists(_.message.contains("UserId must be non-empty and at most 50 characters")))
      },
      test("Should convert Int to opaque type Int") {
        val derivation = Into.derived[Int, PositiveInt]
        val input      = 42
        val result     = derivation.into(input)

        assertTrue(result.isRight)
      },
      test("Should convert Int to opaque type Int (validation failure)") {
        val derivation = Into.derived[Int, PositiveInt]
        val input      = -5
        val result     = derivation.into(input)

        assertTrue(result.isLeft)
        assertTrue(result.left.exists(_.message.contains("PositiveInt must be positive")))
      },
      test("Should convert with coercion (Long to opaque Int)") {
        val derivation = Into.derived[Long, PositiveInt]
        val input      = 42L
        val result     = derivation.into(input)

        assertTrue(result.isRight)
      },
      test("Should convert case class with opaque type field") {
        case class Raw(age: Int)
        case class Validated(age: Age)

        val derivation = Into.derived[Raw, Validated]
        val input      = Raw(30)
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_.age.toString) == Right("30"))
      },
      test("Should convert case class with opaque type field (validation failure)") {
        case class Raw(age: Int)
        case class Validated(age: Age)

        val derivation = Into.derived[Raw, Validated]
        val input      = Raw(-5)
        val result     = derivation.into(input)

        assertTrue(result.isLeft)
        assertTrue(result.left.exists(_.message.contains("Invalid age: -5")))
      },
      test("Should convert nested case class with opaque type") {
        case class PersonV1(name: String, id: String)
        case class PersonV2(name: String, id: UserId)

        val derivation = Into.derived[PersonV1, PersonV2]
        val input      = PersonV1("Alice", "alice123")
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_.name) == Right("Alice"))
        assertTrue(result.map(_.id.toString) == Right("alice123"))
      },
      test("Should convert nested case class with opaque type (validation failure)") {
        case class PersonV1(name: String, id: String)
        case class PersonV2(name: String, id: UserId)

        val derivation = Into.derived[PersonV1, PersonV2]
        val input      = PersonV1("Alice", "a" * 100) // Too long
        val result     = derivation.into(input)

        assertTrue(result.isLeft)
        assertTrue(result.left.exists(_.message.contains("UserId must be non-empty and at most 50 characters")))
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
