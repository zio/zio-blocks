package zio.blocks.schema.convert

import zio.test.*

object OpaqueTypeDefs {
  opaque type SimpleAge = Int
  object SimpleAge {
    def apply(value: Int): Either[String, SimpleAge] =
      if (value >= 0 && value <= 150) Right(value)
      else Left(s"Invalid age: $value")

    def unsafe(value: Int): SimpleAge = value

    extension (age: SimpleAge) def toInt: Int = age
  }

  opaque type Email = String
  object Email {
    def apply(value: String): Either[String, Email] =
      if (value.contains("@")) Right(value)
      else Left(s"Invalid email: $value")

    def unsafe(value: String): Email = value

    extension (email: Email) def value: String = email
  }

  opaque type PositiveInt = Int
  object PositiveInt {
    def apply(value: Int): Either[String, PositiveInt] =
      if (value > 0) Right(value)
      else Left(s"Value must be positive: $value")

    def unsafe(value: Int): PositiveInt = value

    extension (pi: PositiveInt) def toInt: Int = pi
  }
}

object IntoOpaqueTypeSpec extends ZIOSpecDefault {
  import OpaqueTypeDefs.*

  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: SimpleAge)

  case class PersonWithEmailV1(name: String, age: Int, email: String)
  case class PersonWithEmailV2(name: String, age: SimpleAge, email: Email)

  case class ProductV1(name: String, price: Int, stock: Int)
  case class ProductV2(name: String, price: PositiveInt, stock: PositiveInt)

  def spec: Spec[TestEnvironment, Any] = suite("IntoOpaqueTypeSpec")(
    suite("Opaque Types with Validation")(
      test("converts to opaque types with successful validation") {
        val person = PersonV1("Alice", 30)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(
          result.isRight,
          result.map(_.name).getOrElse("") == "Alice",
          result.map(_.age.toInt).getOrElse(0) == 30
        )
      },
      test("fails when age validation fails - negative value") {
        val person = PersonV1("Bob", -5)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.contains("Validation failed")),
          result.swap.exists(err => err.toString.contains("Invalid age"))
        )
      },
      test("fails when age validation fails - too large value") {
        val person = PersonV1("Charlie", 200)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.contains("Validation failed")),
          result.swap.exists(err => err.toString.contains("Invalid age"))
        )
      },
      test("succeeds with multiple opaque type fields when all validations pass") {
        val person = PersonWithEmailV1("Alice", 30, "alice@example.com")
        val result = Into.derived[PersonWithEmailV1, PersonWithEmailV2].into(person)

        assertTrue(
          result.isRight,
          result.map(_.name).getOrElse("") == "Alice",
          result.map(_.age.toInt).getOrElse(0) == 30,
          result.map(_.email.value).getOrElse("") == "alice@example.com"
        )
      },
      test("fails when email validation fails") {
        val person = PersonWithEmailV1("David", 25, "invalid-email")
        val result = Into.derived[PersonWithEmailV1, PersonWithEmailV2].into(person)

        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.contains("Validation failed")),
          result.swap.exists(err => err.toString.contains("Invalid email"))
        )
      },
      test("converts multiple fields with same opaque type - all valid") {
        val product = ProductV1("Widget", 100, 50)
        val result  = Into.derived[ProductV1, ProductV2].into(product)

        assertTrue(
          result.isRight,
          result.map(_.name).getOrElse("") == "Widget",
          result.map(_.price.toInt).getOrElse(0) == 100,
          result.map(_.stock.toInt).getOrElse(0) == 50
        )
      },
      test("fails when first field validation fails") {
        val product = ProductV1("Widget", -100, 50)
        val result  = Into.derived[ProductV1, ProductV2].into(product)

        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.contains("Validation failed")),
          result.swap.exists(err => err.toString.contains("must be positive"))
        )
      },
      test("fails when second field validation fails") {
        val product = ProductV1("Widget", 100, 0)
        val result  = Into.derived[ProductV1, ProductV2].into(product)

        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.contains("Validation failed")),
          result.swap.exists(err => err.toString.contains("must be positive"))
        )
      },
      test("boundary test - age at minimum valid value") {
        val person = PersonV1("Eve", 0)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(
          result.isRight,
          result.map(_.age.toInt).getOrElse(-1) == 0
        )
      },
      test("boundary test - age at maximum valid value") {
        val person = PersonV1("Frank", 150)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(
          result.isRight,
          result.map(_.age.toInt).getOrElse(-1) == 150
        )
      }
    )
  )
}
