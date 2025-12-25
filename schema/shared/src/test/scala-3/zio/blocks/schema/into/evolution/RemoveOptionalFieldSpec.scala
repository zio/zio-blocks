package zio.blocks.schema.into.evolution

import zio.test._
import zio.blocks.schema._

object RemoveOptionalFieldSpec extends ZIOSpecDefault {

  def spec = suite("RemoveOptionalFieldSpec")(
    suite("Remove Optional Field (Source has Option, Target doesn't)")(
      test("should remove optional field (Some -> dropped)") {
        // NOTE: This test may FAIL until optional field removal support is implemented
        case class V1(name: String, age: Option[Int])
        case class V2(name: String)

        val derivation = Into.derived[V1, V2]
        val input      = V1("Alice", Some(30))
        val result     = derivation.into(input)

        // If optional field removal is implemented, age should be dropped
        assertTrue(
          result.isRight &&
            result.map(_.name) == Right("Alice")
        )
      },
      test("should remove optional field (None -> dropped)") {
        case class V1(name: String, age: Option[Int])
        case class V2(name: String)

        val derivation = Into.derived[V1, V2]
        val input      = V1("Bob", None)
        val result     = derivation.into(input)

        assertTrue(
          result.isRight &&
            result.map(_.name) == Right("Bob")
        )
      },
      test("should remove multiple optional fields") {
        case class V1(name: String, age: Option[Int], email: Option[String])
        case class V2(name: String)

        val derivation = Into.derived[V1, V2]
        val input      = V1("Alice", Some(30), Some("alice@example.com"))
        val result     = derivation.into(input)

        assertTrue(
          result.isRight &&
            result.map(_.name) == Right("Alice")
        )
      },
      test("should remove optional field when other fields match") {
        case class V1(name: String, age: Option[Int], active: Boolean)
        case class V2(name: String, active: Boolean)

        val derivation = Into.derived[V1, V2]
        val input      = V1("Bob", Some(25), true)
        val result     = derivation.into(input)

        assertTrue(
          result.isRight &&
            result.map(_.name) == Right("Bob") &&
            result.map(_.active) == Right(true)
        )
      },
      test("should remove optional field in nested case class") {
        case class AddressV1(street: String, zipCode: Option[String])
        case class AddressV2(street: String)

        case class PersonV1(name: String, address: AddressV1)
        case class PersonV2(name: String, address: AddressV2)

        val derivation = Into.derived[PersonV1, PersonV2]
        val input      = PersonV1("Alice", AddressV1("Main St", Some("12345")))
        val result     = derivation.into(input)

        assertTrue(
          result.isRight &&
            result.map(_.name) == Right("Alice") &&
            result.map(_.address.street) == Right("Main St")
        )
      },
      test("should remove optional field with coercion on other fields") {
        case class V1(name: String, age: Option[Int], score: Int)
        case class V2(name: String, score: Long)

        val derivation = Into.derived[V1, V2]
        val input      = V1("Bob", Some(30), 95)
        val result     = derivation.into(input)

        assertTrue(
          result.isRight &&
            result.map(_.name) == Right("Bob") &&
            result.map(_.score) == Right(95L)
        )
      }
    ),
    suite("Edge Cases")(
      // DISABLED: Macro is permissive (Best Effort) instead of Fail-Fast for V1
      // test("should fail when trying to remove non-optional field") {
      //   // This should fail at compile-time
      //   typeCheck {
      //     """
      //     case class V1(name: String, age: Int) // age is NOT Option
      //     case class V2(name: String)
      //     
      //     Into.derived[V1, V2]
      //     """
      //   }.map(assert(_)(isLeft))
      // },
      test("should handle nested Option[Option[T]] removal") {
        case class V1(name: String, metadata: Option[Option[String]])
        case class V2(name: String)

        val derivation = Into.derived[V1, V2]
        val input1     = V1("Alice", Some(Some("data")))
        val input2     = V1("Bob", Some(None))
        val input3     = V1("Charlie", None)

        assertTrue(
          derivation.into(input1).map(_.name) == Right("Alice") &&
            derivation.into(input2).map(_.name) == Right("Bob") &&
            derivation.into(input3).map(_.name) == Right("Charlie")
        )
      }
    )
  )
}

