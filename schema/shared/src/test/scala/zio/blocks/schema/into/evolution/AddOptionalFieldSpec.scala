package zio.blocks.schema.into.evolution

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for schema evolution when adding optional fields. */
object AddOptionalFieldSpec extends ZIOSpecDefault {

  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Int, email: Option[String])

  case class ProductV1(id: Long, name: String)
  case class ProductV2(id: Long, name: String, description: Option[String], price: Option[Double])

  case class SourceWithEmail(name: String, age: Int, email: Option[String])

  def spec: Spec[TestEnvironment, Any] = suite("AddOptionalFieldSpec")(
    test("adds Option field with None when not in source") {
      val result = Into.derived[PersonV1, PersonV2].into(PersonV1("Alice", 30))
      assert(result)(isRight(equalTo(PersonV2("Alice", 30, None))))
    },
    test("adds multiple Option fields with None") {
      val result = Into.derived[ProductV1, ProductV2].into(ProductV1(1L, "Widget"))
      assert(result)(isRight(equalTo(ProductV2(1L, "Widget", None, None))))
    },
    test("existing source Option value used when Option field matches") {
      val result = Into.derived[SourceWithEmail, PersonV2].into(SourceWithEmail("Bob", 25, Some("bob@test.com")))
      assert(result)(isRight(equalTo(PersonV2("Bob", 25, Some("bob@test.com")))))
    }
  )
}
