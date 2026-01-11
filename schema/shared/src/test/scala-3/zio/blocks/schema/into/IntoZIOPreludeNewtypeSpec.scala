package zio.blocks.schema.into

import zio.blocks.schema.Into
import zio.prelude.{Assertion, *}
import zio.test.*
import zio.test.Assertion.*

/** Tests for ZIO Prelude Newtype and Subtype support in Into.derived. */
object IntoZIOPreludeNewtypeSpec extends ZIOSpecDefault {

  // Define newtypes with validation
  object Domain {
    object Age extends Subtype[Int] {
      override def assertion: Assertion[Int] =
        zio.prelude.Assertion.between(0, 150)
    }
    type Age = Age.Type

    object NonEmptyString extends Newtype[String] {
      override def assertion: Assertion[String] =
        !zio.prelude.Assertion.isEmptyString
    }
    type NonEmptyString = NonEmptyString.Type
  }

  import Domain.*

  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: NonEmptyString, age: Age)

  def spec: Spec[TestEnvironment, Any] = suite("IntoZIOPreludeNewtypeSpec")(
    test("converts Int to Age (Subtype) with successful validation") {

      val person = PersonV1("Alice", 30)
      val result = Into.derived[PersonV1, PersonV2].into(person)

      assert(result)(isRight(anything))
    },
    test("fails when Age validation fails - negative value") {

      val person = PersonV1("Charlie", -5)
      val result = Into.derived[PersonV1, PersonV2].into(person)

      assertTrue(
        result.isLeft,
        result.swap.exists(err => err.toString.toLowerCase.contains("validation") || err.toString.contains("-5"))
      )
    },
    test("derive primitive to NewType conversion with successful validation") {

      val name   = "Bob"
      val result = Into.derived[String, NonEmptyString].into(name)

      assert(result)(isRight(anything))
    }
  )
}
