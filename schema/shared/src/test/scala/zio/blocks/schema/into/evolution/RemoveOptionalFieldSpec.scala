package zio.blocks.schema.into.evolution

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for schema evolution when removing optional fields. */
object RemoveOptionalFieldSpec extends ZIOSpecDefault {

  case class PersonV2(name: String, age: Int, email: Option[String])
  case class PersonV1(name: String, age: Int)

  case class ProductV2(id: Long, name: String, description: Option[String], price: Option[Double])
  case class ProductV1(id: Long, name: String)

  def spec: Spec[TestEnvironment, Any] = suite("RemoveOptionalFieldSpec")(
    test("drops Some value when field is removed") {
      val result = Into.derived[PersonV2, PersonV1].into(PersonV2("Alice", 30, Some("alice@example.com")))
      assert(result)(isRight(equalTo(PersonV1("Alice", 30))))
    },
    test("converts when optional field is None") {
      val result = Into.derived[PersonV2, PersonV1].into(PersonV2("Bob", 25, None))
      assert(result)(isRight(equalTo(PersonV1("Bob", 25))))
    },
    test("drops multiple optional fields") {
      val result = Into.derived[ProductV2, ProductV1].into(ProductV2(1L, "Widget", Some("desc"), Some(19.99)))
      assert(result)(isRight(equalTo(ProductV1(1L, "Widget"))))
    }
  )
}
