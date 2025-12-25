package zio.blocks.schema.into.validation

import zio.test._
import zio.blocks.schema._
import zio.prelude.{Newtype, Subtype}

// ZIO Prelude Newtypes for testing
type Name = Name.Type
object Name extends Newtype[String] {
  override inline def assertion: zio.prelude.Assertion[String] = !zio.prelude.Assertion.isEmptyString
}

type Kilogram = Kilogram.Type
object Kilogram extends Newtype[Double]

type Meter = Meter.Type
object Meter extends Subtype[Double]

// Renamed to avoid collision with PositiveInt from zio.blocks.schema package
type PositiveIntNewtype = PositiveIntNewtype.Type
object PositiveIntNewtype extends Newtype[Int] {
  override inline def assertion: zio.prelude.Assertion[Int] = zio.prelude.Assertion.greaterThan(0)
}

object ZIOPreludeNewtypeSpec extends ZIOSpecDefault {

  def spec = suite("ZIOPreludeNewtypeSpec")(
    suite("ZIO Prelude Newtype Conversions")(
      test("should convert String to Name (Newtype)") {
        val derivation = Into.derived[String, Name]
        val input      = "Alice"
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        result.map { name =>
          assertTrue(name.asInstanceOf[String] == "Alice")
        }
      },
      test("should convert Name to String (Newtype unwrap)") {
        val derivation = Into.derived[Name, String]
        val input      = Name("Bob")
        val result     = derivation.into(input)

        assertTrue(result == Right("Bob"))
      },
      test("should convert Int to PositiveIntNewtype (Newtype with assertion)") {
        val derivation  = Into.derived[Int, PositiveIntNewtype]
        val validInput  = 42
        val validResult = derivation.into(validInput)
        assertTrue(validResult.isRight)

        // ZIO Prelude assertions are validated at runtime via the make method
        // Invalid values should fail validation and return Left
        val invalidInput  = -5
        val invalidResult = derivation.into(invalidInput)
        // Validation fails for negative values
        assertTrue(invalidResult.isLeft)
      },
      test("should convert Double to Kilogram (Newtype without assertion)") {
        val derivation = Into.derived[Double, Kilogram]
        val input      = 5.97e24
        val result     = derivation.into(input)

        assertTrue(result.isRight)
      },
      test("should convert Double to Meter (Subtype)") {
        val derivation = Into.derived[Double, Meter]
        val input      = 6378000.0
        val result     = derivation.into(input)

        assertTrue(result.isRight)
      },
      test("should convert Meter to Double (Subtype unwrap)") {
        val derivation = Into.derived[Meter, Double]
        val input      = Meter(6378000.0)
        val result     = derivation.into(input)

        assertTrue(result == Right(6378000.0))
      },
      test("should convert Long to PositiveIntNewtype (with coercion)") {
        val derivation = Into.derived[Long, PositiveIntNewtype]
        val input      = 42L
        val result     = derivation.into(input)

        assertTrue(result.isRight)
      },
      test("should convert case class with newtype fields") {
        case class Person(name: String, age: Int)
        case class PersonWithNewtypes(name: Name, age: PositiveIntNewtype)

        val derivation = Into.derived[Person, PersonWithNewtypes]
        val input      = Person("Alice", 30)
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        result.map { person =>
          assertTrue(person.name.asInstanceOf[String] == "Alice")
          assertTrue(person.age.asInstanceOf[Int] == 30)
        }
      },
      test("should convert case class with multiple newtype fields") {
        case class Raw(name: String, mass: Double, radius: Double)
        case class Validated(name: Name, mass: Kilogram, radius: Meter)

        val derivation = Into.derived[Raw, Validated]
        val input      = Raw("Earth", 5.97e24, 6378000.0)
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        result.map { validated =>
          assertTrue(validated.name.asInstanceOf[String] == "Earth")
          assertTrue(validated.mass.asInstanceOf[Double] == 5.97e24)
          assertTrue(validated.radius.asInstanceOf[Double] == 6378000.0)
        }
      },
      test("should convert nested newtypes") {
        case class Container(value: Int)
        case class ContainerWithNewtype(value: PositiveIntNewtype)

        val derivation = Into.derived[Container, ContainerWithNewtype]
        val input      = Container(42)
        val result     = derivation.into(input)

        assertTrue(result.isRight)
      }
    )
  )
}
