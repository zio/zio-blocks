package zio.blocks.schema

import zio.prelude.{Assertion, *}
import zio.test.*
import zio.test.Assertion.*

/**
 * Tests for ZIO Prelude Newtype and Subtype support in the Into type class.
 * These tests verify that the macro automatically detects ZIO Prelude newtypes
 * and uses the 'make' method for validation without requiring explicit implicit
 * instances.
 */
object IntoZIOPreludeNewtypeSpec extends ZIOSpecDefault {

  // Define newtypes with validation
  object Domain {
    object Age extends Subtype[Int] {
      override def assertion: Assertion[Int] =
        zio.prelude.Assertion.between(0, 150)
    }
    type Age = Age.Type

    object Email extends Newtype[String] {
      override def assertion: Assertion[String] =
        zio.prelude.Assertion.contains("@")
    }
    type Email = Email.Type

    object PositiveInt extends Subtype[Int] {
      override def assertion: Assertion[Int] =
        zio.prelude.Assertion.greaterThan(0)
    }
    type PositiveInt = PositiveInt.Type

    object NonEmptyString extends Newtype[String] {
      override def assertion: Assertion[String] =
        !zio.prelude.Assertion.isEmptyString
    }
    type NonEmptyString = NonEmptyString.Type
  }

  import Domain.*

  def spec: Spec[TestEnvironment, Any] = suite("IntoZIOPreludeNewtypeSpec")(
    suite("Basic Newtype Conversion with Automatic Detection")(
      test("converts Int to Age (Subtype) with successful validation") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String, age: Age)

        val person = PersonV1("Alice", 30)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assert(result)(isRight(anything))
      },
      test("converts String to Email (Newtype) with successful validation") {
        case class UserV1(name: String, email: String)
        case class UserV2(name: String, email: Email)

        val user   = UserV1("Bob", "bob@example.com")
        val result = Into.derived[UserV1, UserV2].into(user)

        assert(result)(isRight(anything))
      },
      test("fails when Age validation fails - negative value") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String, age: Age)

        val person = PersonV1("Charlie", -5)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.toLowerCase.contains("validation") || err.toString.contains("-5"))
        )
      },
      test("fails when Age validation fails - too large value") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String, age: Age)

        val person = PersonV1("Diana", 200)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.toLowerCase.contains("validation") || err.toString.contains("200"))
        )
      },
      test("fails when Email validation fails - missing @") {
        case class UserV1(name: String, email: String)
        case class UserV2(name: String, email: Email)

        val user   = UserV1("Eve", "invalid-email")
        val result = Into.derived[UserV1, UserV2].into(user)

        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.toLowerCase.contains("validation") || err.toString.contains("invalid"))
        )
      }
    ),
    suite("Multiple Newtype Fields")(
      test("converts multiple newtype fields - all valid") {
        case class RequestV1(userId: Int, email: String)
        case class RequestV2(userId: PositiveInt, email: Email)

        val request = RequestV1(42, "user@test.com")
        val result  = Into.derived[RequestV1, RequestV2].into(request)

        assert(result)(isRight(anything))
      },
      test("fails when first field validation fails") {
        case class RequestV1(userId: Int, email: String)
        case class RequestV2(userId: PositiveInt, email: Email)

        val request = RequestV1(-1, "user@test.com")
        val result  = Into.derived[RequestV1, RequestV2].into(request)

        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.contains("-1") || err.toString.toLowerCase.contains("validation"))
        )
      },
      test("fails when second field validation fails") {
        case class RequestV1(userId: Int, email: String)
        case class RequestV2(userId: PositiveInt, email: Email)

        val request = RequestV1(42, "invalid")
        val result  = Into.derived[RequestV1, RequestV2].into(request)

        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.contains("invalid") || err.toString.toLowerCase.contains("validation"))
        )
      }
    ),
    suite("Boundary Tests")(
      test("Age at minimum valid value (0)") {
        case class PersonV1(age: Int)
        case class PersonV2(age: Age)

        val person = PersonV1(0)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assert(result)(isRight(anything))
      },
      test("Age at maximum valid value (150)") {
        case class PersonV1(age: Int)
        case class PersonV2(age: Age)

        val person = PersonV1(150)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assert(result)(isRight(anything))
      },
      test("Age just below minimum (boundary fail)") {
        case class PersonV1(age: Int)
        case class PersonV2(age: Age)

        val person = PersonV1(-1)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(result.isLeft)
      },
      test("Age just above maximum (boundary fail)") {
        case class PersonV1(age: Int)
        case class PersonV2(age: Age)

        val person = PersonV1(151)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(result.isLeft)
      }
    ),
    suite("Mixed Newtype and Regular Fields")(
      test("converts case class with mix of newtype and regular fields") {
        case class MixedV1(id: Int, name: String, age: Int, country: String)
        case class MixedV2(id: PositiveInt, name: String, age: Age, country: String)

        val data   = MixedV1(1, "Helen", 28, "USA")
        val result = Into.derived[MixedV1, MixedV2].into(data)

        assert(result)(isRight(anything))
      },
      test("fails only on invalid newtype fields, not regular fields") {
        case class MixedV1(id: Int, name: String, age: Int)
        case class MixedV2(id: PositiveInt, name: String, age: Age)

        val data   = MixedV1(-5, "Ian", 30) // Invalid id
        val result = Into.derived[MixedV1, MixedV2].into(data)

        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.contains("-5") || err.toString.toLowerCase.contains("validation"))
        )
      }
    )
  )
}
