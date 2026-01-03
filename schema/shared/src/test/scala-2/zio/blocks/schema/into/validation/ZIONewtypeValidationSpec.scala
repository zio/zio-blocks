package zio.blocks.schema.into.validation

import zio.blocks.schema._
import zio.test._
import zio.prelude._

/** Tests for Into derivation with ZIO Prelude Newtype/Subtype fields. */
object ZIONewtypeValidationSpec extends ZIOSpecDefault {

  object Types {
    object Age extends Subtype[Int] {
      override def assertion = assert(zio.prelude.Assertion.between(0, 150))
    }
    type Age = Age.Type

    object Email extends Newtype[String] {
      override def assertion = assert(zio.prelude.Assertion.contains("@"))
    }
    type Email = Email.Type
  }
  import Types._

  case class PersonV1(name: String, age: Int, email: String)
  case class PersonV2(name: String, age: Age, email: Email)

  def spec = suite("ZIONewtypeValidationSpec")(
    test("derives Into for case class with multiple newtype fields") {
      val source = PersonV1("Alice", 30, "alice@example.com")
      val result = Into.derived[PersonV1, PersonV2].into(source)
      assertTrue(result.isRight)
    },
    test("nested case class with newtype fields") {
      case class AddressV1(city: String)
      case class AddressV2(city: String)
      case class PersonWithAddressV1(name: String, age: Int, address: AddressV1)
      case class PersonWithAddressV2(name: String, age: Age, address: AddressV2)

      implicit val addressInto: Into[AddressV1, AddressV2] = Into.derived
      val source                                           = PersonWithAddressV1("Bob", 25, AddressV1("NYC"))
      val result                                           = Into.derived[PersonWithAddressV1, PersonWithAddressV2].into(source)
      assertTrue(result.isRight)
    }
  )
}
