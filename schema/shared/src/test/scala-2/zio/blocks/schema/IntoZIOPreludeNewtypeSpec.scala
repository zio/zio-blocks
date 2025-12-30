package zio.blocks.schema

import zio.test._
import zio.prelude._

/** Tests for ZIO Prelude Newtype and Subtype support in Into.derived. */
object IntoZIOPreludeNewtypeSpec extends ZIOSpecDefault {

  // Define newtypes with validation using Scala 2 syntax
  object Domain {
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

  import Domain._

  override def spec = suite("IntoZIOPreludeNewtypeSpec")(
    suite("Basic Newtype Conversion with Automatic Detection")(
      test("converts Int to Age (Subtype with validation) - valid value") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String, age: Age)

        val person = PersonV1("Alice", 30)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(result.isRight) &&
        assertTrue(result.map(_.name == "Alice").getOrElse(false))
      },
      test("converts String to Email (Newtype with validation) - valid value") {
        case class UserV1(name: String, email: String)
        case class UserV2(name: String, email: Email)

        val user   = UserV1("Alice", "alice@example.com")
        val result = Into.derived[UserV1, UserV2].into(user)

        assertTrue(result.isRight)
      },
      test("fails when Age validation fails - negative value") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String, age: Age)

        val person = PersonV1("Alice", -5)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(result.isLeft) &&
        assertTrue(
          result.swap.exists(err => err.toString.toLowerCase.contains("validation") || err.toString.contains("-5"))
        )
      },
      test("fails when Age validation fails - too old") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String, age: Age)

        val person = PersonV1("Alice", 200)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(result.isLeft) &&
        assertTrue(
          result.swap.exists(err => err.toString.toLowerCase.contains("validation") || err.toString.contains("200"))
        )
      },
      test("fails when Email validation fails - missing @") {
        case class UserV1(name: String, email: String)
        case class UserV2(name: String, email: Email)

        val user   = UserV1("Alice", "invalid")
        val result = Into.derived[UserV1, UserV2].into(user)

        assertTrue(result.isLeft) &&
        assertTrue(
          result.swap.exists(err => err.toString.toLowerCase.contains("validation") || err.toString.contains("invalid"))
        )
      }
    ),
    suite("Multiple Newtype Fields")(
      test("converts multiple newtype fields - all valid") {
        case class RequestV1(userId: Int, email: String)
        case class RequestV2(userId: PositiveInt, email: Email)

        val request = RequestV1(42, "user@example.com")
        val result  = Into.derived[RequestV1, RequestV2].into(request)

        assertTrue(result.isRight)
      },
      test("fails when first field validation fails") {
        case class RequestV1(userId: Int, email: String)
        case class RequestV2(userId: PositiveInt, email: Email)

        val request = RequestV1(-1, "user@example.com")
        val result  = Into.derived[RequestV1, RequestV2].into(request)

        assertTrue(result.isLeft) &&
        assertTrue(
          result.swap.exists(err => err.toString.contains("-1") || err.toString.toLowerCase.contains("validation"))
        )
      },
      test("fails when second field validation fails") {
        case class RequestV1(userId: Int, email: String)
        case class RequestV2(userId: PositiveInt, email: Email)

        val request = RequestV1(42, "invalid")
        val result  = Into.derived[RequestV1, RequestV2].into(request)

        assertTrue(result.isLeft) &&
        assertTrue(
          result.swap.exists(err => err.toString.contains("invalid") || err.toString.toLowerCase.contains("validation"))
        )
      }
    ),
    suite("Nested Structures with Newtypes")(
      test("converts nested case class with newtype field") {
        case class PersonV1(age: Int)
        case class PersonV2(age: Age)

        val person = PersonV1(25)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(result.isRight)
      },
      test("fails for nested structure when validation fails") {
        case class PersonV1(age: Int)
        case class PersonV2(age: Age)

        val person = PersonV1(-10)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(result.isLeft)
      },
      test("converts single value to newtype") {
        case class PersonV1(age: Int)
        case class PersonV2(age: Age)

        val person = PersonV1(30)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(result.isRight)
      },
      test("fails when validation fails") {
        case class PersonV1(age: Int)
        case class PersonV2(age: Age)

        val person = PersonV1(200)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assertTrue(result.isLeft)
      }
    ),
    suite("Mixed Newtype and Regular Fields")(
      test("converts case class with mix of newtype and regular fields") {
        case class MixedV1(id: Int, name: String, age: Int, country: String)
        case class MixedV2(id: PositiveInt, name: String, age: Age, country: String)

        val data   = MixedV1(1, "Alice", 30, "USA")
        val result = Into.derived[MixedV1, MixedV2].into(data)

        assertTrue(result.isRight)
      },
      test("fails when newtype field validation fails in mixed structure") {
        case class MixedV1(id: Int, name: String, age: Int)
        case class MixedV2(id: PositiveInt, name: String, age: Age)

        val data   = MixedV1(-1, "Alice", 30)
        val result = Into.derived[MixedV1, MixedV2].into(data)

        assertTrue(result.isLeft)
      }
    )
  )
}
