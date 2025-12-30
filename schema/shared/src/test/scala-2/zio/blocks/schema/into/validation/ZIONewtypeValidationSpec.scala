package zio.blocks.schema.into.validation

import zio.blocks.schema._
import zio.test._
import zio.prelude._

/**
 * Tests for ZIO Prelude Newtype/Subtype validation in Into derivation.
 *
 * ZIO Prelude newtypes with assertion blocks should have their validation
 * automatically applied during Into conversion.
 */
object ZIONewtypeValidationSpec extends ZIOSpecDefault {

  // === Newtype Definitions ===
  object Types {
    object Age extends Subtype[Int] {
      override def assertion = assert {
        zio.prelude.Assertion.between(0, 150)
      }
    }
    type Age = Age.Type

    object Email extends Newtype[String] {
      override def assertion = assert {
        zio.prelude.Assertion.contains("@")
      }
    }
    type Email = Email.Type

    object PositiveInt extends Subtype[Int] {
      override def assertion = assert {
        zio.prelude.Assertion.greaterThan(0)
      }
    }
    type PositiveInt = PositiveInt.Type

    object NonEmptyString extends Newtype[String] {
      override def assertion = assert {
        !zio.prelude.Assertion.isEmptyString
      }
    }
    type NonEmptyString = NonEmptyString.Type
  }
  import Types._

  // === Test Case Classes ===
  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Age)

  case class PersonWithEmailV1(name: String, age: Int, email: String)
  case class PersonWithEmailV2(name: String, age: Age, email: Email)

  case class ProductV1(name: String, price: Int, stock: Int)
  case class ProductV2(name: String, price: PositiveInt, stock: PositiveInt)

  case class UserV1(name: String, email: String)
  case class UserV2(name: NonEmptyString, email: Email)

  def spec = suite("ZIONewtypeValidationSpec")(
    suite("Basic Newtype Validation")(
      test("succeeds with valid age (Subtype)") {
        val source = PersonV1("Alice", 30)
        val result = Into.derived[PersonV1, PersonV2].into(source)

        assertTrue(result.isRight) &&
        assertTrue(result.map(_.name == "Alice").getOrElse(false))
      },
      test("fails with negative age") {
        val source = PersonV1("Bob", -5)
        val result = Into.derived[PersonV1, PersonV2].into(source)

        assertTrue(result.isLeft)
      },
      test("fails with age over 150") {
        val source = PersonV1("Charlie", 200)
        val result = Into.derived[PersonV1, PersonV2].into(source)

        assertTrue(result.isLeft)
      },
      test("boundary - age at 0") {
        val source = PersonV1("Zero", 0)
        val result = Into.derived[PersonV1, PersonV2].into(source)

        assertTrue(result.isRight)
      },
      test("boundary - age at 150") {
        val source = PersonV1("Max", 150)
        val result = Into.derived[PersonV1, PersonV2].into(source)

        assertTrue(result.isRight)
      }
    ),
    suite("Email Newtype Validation")(
      test("succeeds with valid email") {
        val source = PersonWithEmailV1("Alice", 30, "alice@example.com")
        val result = Into.derived[PersonWithEmailV1, PersonWithEmailV2].into(source)

        assertTrue(result.isRight)
      },
      test("fails with invalid email - missing @") {
        val source = PersonWithEmailV1("Bob", 30, "invalid")
        val result = Into.derived[PersonWithEmailV1, PersonWithEmailV2].into(source)

        assertTrue(result.isLeft)
      }
    ),
    suite("Multiple Newtype Fields")(
      test("succeeds when all fields are valid") {
        val source = PersonWithEmailV1("Alice", 30, "alice@example.com")
        val result = Into.derived[PersonWithEmailV1, PersonWithEmailV2].into(source)

        assertTrue(result.isRight)
      },
      test("fails when first newtype field is invalid") {
        val source = PersonWithEmailV1("Alice", -5, "alice@example.com")
        val result = Into.derived[PersonWithEmailV1, PersonWithEmailV2].into(source)

        assertTrue(result.isLeft)
      },
      test("fails when second newtype field is invalid") {
        val source = PersonWithEmailV1("Alice", 30, "invalid")
        val result = Into.derived[PersonWithEmailV1, PersonWithEmailV2].into(source)

        assertTrue(result.isLeft)
      }
    ),
    suite("Same Newtype Multiple Fields")(
      test("succeeds when all PositiveInt fields are valid") {
        val source = ProductV1("Widget", 100, 50)
        val result = Into.derived[ProductV1, ProductV2].into(source)

        assertTrue(result.isRight)
      },
      test("fails when first PositiveInt field is invalid") {
        val source = ProductV1("Widget", -100, 50)
        val result = Into.derived[ProductV1, ProductV2].into(source)

        assertTrue(result.isLeft)
      },
      test("fails when second PositiveInt field is zero") {
        val source = ProductV1("Widget", 100, 0)
        val result = Into.derived[ProductV1, ProductV2].into(source)

        assertTrue(result.isLeft)
      }
    ),
    suite("NonEmptyString Newtype")(
      test("succeeds with non-empty string") {
        val source = UserV1("Alice", "alice@example.com")
        val result = Into.derived[UserV1, UserV2].into(source)

        assertTrue(result.isRight)
      },
      test("fails with empty name") {
        val source = UserV1("", "alice@example.com")
        val result = Into.derived[UserV1, UserV2].into(source)

        assertTrue(result.isLeft)
      }
    ),
    suite("Nested Newtypes")(
      test("validates newtypes in nested case classes") {
        case class AddressV1(city: String, zip: Int)
        case class AddressV2(city: NonEmptyString, zip: PositiveInt)
        case class PersonWithAddressV1(name: String, address: AddressV1)
        case class PersonWithAddressV2(name: NonEmptyString, address: AddressV2)

        implicit val addressInto: Into[AddressV1, AddressV2] = Into.derived[AddressV1, AddressV2]
        val source                                           = PersonWithAddressV1("Alice", AddressV1("NYC", 10001))
        val result                                           = Into.derived[PersonWithAddressV1, PersonWithAddressV2].into(source)

        assertTrue(result.isRight)
      },
      test("nested newtype validation failure propagates") {
        case class AddressV1(city: String, zip: Int)
        case class AddressV2(city: NonEmptyString, zip: PositiveInt)
        case class PersonWithAddressV1(name: String, address: AddressV1)
        case class PersonWithAddressV2(name: NonEmptyString, address: AddressV2)

        implicit val addressInto: Into[AddressV1, AddressV2] = Into.derived[AddressV1, AddressV2]
        val source                                           = PersonWithAddressV1("Alice", AddressV1("", 10001)) // empty city
        val result                                           = Into.derived[PersonWithAddressV1, PersonWithAddressV2].into(source)

        assertTrue(result.isLeft)
      }
    ),
    suite("Newtypes in Coproducts")(
      test("validates newtypes in sealed trait cases - valid") {
        sealed trait RequestV1
        case class CreateV1(name: String, age: Int) extends RequestV1

        sealed trait RequestV2
        case class CreateV2(name: NonEmptyString, age: Age) extends RequestV2

        val source: RequestV1 = CreateV1("Alice", 30)
        val result            = Into.derived[RequestV1, RequestV2].into(source)

        assertTrue(result.isRight)
      },
      test("validates newtypes in sealed trait cases - invalid") {
        sealed trait RequestV1
        case class CreateV1(name: String, age: Int) extends RequestV1

        sealed trait RequestV2
        case class CreateV2(name: NonEmptyString, age: Age) extends RequestV2

        val source: RequestV1 = CreateV1("Alice", -5) // invalid age
        val result            = Into.derived[RequestV1, RequestV2].into(source)

        assertTrue(result.isLeft)
      }
    )
  )
}
