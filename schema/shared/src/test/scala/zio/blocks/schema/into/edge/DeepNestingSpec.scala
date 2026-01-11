package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for deeply nested structure conversions. */
object DeepNestingSpec extends ZIOSpecDefault {

  case class AddressA(street: String, zip: Int)
  case class AddressB(street: String, zip: Long)

  case class ContactA(name: String, address: AddressA)
  case class ContactB(name: String, address: AddressB)

  case class EmployeeA(id: Int, contact: ContactA)
  case class EmployeeB(id: Long, contact: ContactB)

  case class InnerA(value: Int)
  case class InnerB(value: Long)

  case class OuterWithListA(items: List[InnerA])
  case class OuterWithListB(items: List[InnerB])

  case class OuterWithOptionA(inner: Option[InnerA])
  case class OuterWithOptionB(inner: Option[InnerB])

  def spec: Spec[TestEnvironment, Any] = suite("DeepNestingSpec")(
    test("3-level nested structure with coercion") {
      implicit val addressInto: Into[AddressA, AddressB] = Into.derived[AddressA, AddressB]
      implicit val contactInto: Into[ContactA, ContactB] = Into.derived[ContactA, ContactB]

      val source = EmployeeA(1, ContactA("Alice", AddressA("123 Main", 10001)))
      val result = Into.derived[EmployeeA, EmployeeB].into(source)

      assert(result)(isRight(equalTo(EmployeeB(1L, ContactB("Alice", AddressB("123 Main", 10001L))))))
    },
    test("nested collections with coercion") {
      implicit val innerInto: Into[InnerA, InnerB] = Into.derived[InnerA, InnerB]
      val result                                   = Into.derived[OuterWithListA, OuterWithListB].into(OuterWithListA(List(InnerA(1), InnerA(2))))

      assert(result)(isRight(equalTo(OuterWithListB(List(InnerB(1L), InnerB(2L))))))
    },
    test("nested Options with coercion") {
      implicit val innerInto: Into[InnerA, InnerB] = Into.derived[InnerA, InnerB]
      val result                                   = Into.derived[OuterWithOptionA, OuterWithOptionB].into(OuterWithOptionA(Some(InnerA(42))))

      assert(result)(isRight(equalTo(OuterWithOptionB(Some(InnerB(42L))))))
    }
  )
}
