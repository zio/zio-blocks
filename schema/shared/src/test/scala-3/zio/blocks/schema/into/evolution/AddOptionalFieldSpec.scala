package zio.blocks.schema.into.evolution

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema._

object AddOptionalFieldSpec extends ZIOSpecDefault {

  def spec = suite("AddOptionalFieldSpec")(
    suite("Add Optional Field (Missing -> None)")(
      test("should add optional field when missing in source (should set to None)") {
        // NOTE: This test may FAIL until optional field support is implemented
        case class V1(name: String)
        case class V2(name: String, age: Option[Int])

        val derivation = Into.derived[V1, V2]
        val input      = V1("Alice")
        val result     = derivation.into(input)

        // If optional field support is implemented, age should be None
        // If not, this will fail at compile-time
        assertTrue(
          result.isRight &&
            result.map(_.name) == Right("Alice") &&
            result.map(_.age) == Right(None)
        )
      },
      test("should add multiple optional fields") {
        case class V1(name: String)
        case class V2(name: String, age: Option[Int], active: Option[Boolean])

        val derivation = Into.derived[V1, V2]
        val input      = V1("Bob")
        val result     = derivation.into(input)

        assertTrue(
          result.isRight &&
            result.map(_.name) == Right("Bob") &&
            result.map(_.age) == Right(None) &&
            result.map(_.active) == Right(None)
        )
      },
      test("should add optional field when source has matching field") {
        case class V1(name: String, age: Int)
        case class V2(name: String, age: Int, email: Option[String])

        val derivation = Into.derived[V1, V2]
        val input      = V1("Alice", 30)
        val result     = derivation.into(input)

        assertTrue(
          result.isRight &&
            result.map(_.name) == Right("Alice") &&
            result.map(_.age) == Right(30) &&
            result.map(_.email) == Right(None)
        )
      },
      test("should add optional field with nested case class") {
        case class AddressV1(street: String)
        case class AddressV2(street: String, zipCode: Option[String])

        case class PersonV1(name: String, address: AddressV1)
        case class PersonV2(name: String, address: AddressV2)

        val derivation = Into.derived[PersonV1, PersonV2]
        val input      = PersonV1("Bob", AddressV1("Main St"))
        val result     = derivation.into(input)

        assertTrue(
          result.isRight &&
            result.map(_.name) == Right("Bob") &&
            result.map(_.address.street) == Right("Main St") &&
            result.map(_.address.zipCode) == Right(None)
        )
      },
      test("should add optional field with coercion") {
        case class V1(name: String, age: Int)
        case class V2(name: String, age: Long, score: Option[Double])

        val derivation = Into.derived[V1, V2]
        val input      = V1("Alice", 30)
        val result     = derivation.into(input)

        assertTrue(
          result.isRight &&
            result.map(_.name) == Right("Alice") &&
            result.map(_.age) == Right(30L) &&
            result.map(_.score) == Right(None)
        )
      },
      test("should add optional field in list elements") {
        case class ItemV1(name: String)
        case class ItemV2(name: String, price: Option[Double])

        case class V1(items: List[ItemV1])
        case class V2(items: List[ItemV2])

        val derivation = Into.derived[V1, V2]
        val input      = V1(List(ItemV1("Apple"), ItemV1("Banana")))
        val result     = derivation.into(input)

        assertTrue(
          result.isRight &&
            result.map(_.items.length) == Right(2) &&
            result.map(_.items.head.name) == Right("Apple") &&
            result.map(_.items.head.price) == Right(None)
        )
      }
    ),
    suite("Edge Cases")(
      test("should handle optional field with default value (should still be None)") {
        // Even if target has default value, missing source field should map to None
        case class V1(name: String)
        case class V2(name: String, age: Option[Int] = Some(0)) // Default value

        val derivation = Into.derived[V1, V2]
        val input      = V1("Alice")
        val result     = derivation.into(input)

        // Should be None, not the default Some(0)
        assertTrue(
          result.isRight &&
            result.map(_.age) == Right(None)
        )
      },
      test("should fail when target has non-optional field missing in source") {
        // This should fail at compile-time
        typeCheck {
          """
          case class V1(name: String)
          case class V2(name: String, age: Int) // age is NOT Option, should fail
          
          Into.derived[V1, V2]
          """
        }.map(assert(_)(isLeft))
      }
    )
  )
}

