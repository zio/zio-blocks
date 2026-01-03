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

  def spec: Spec[TestEnvironment, Any] = suite("DeepNestingSpec")(
    test("3-level nested structure with coercion") {
      implicit val addressInto: Into[AddressA, AddressB] = Into.derived
      implicit val contactInto: Into[ContactA, ContactB] = Into.derived

      val source = EmployeeA(1, ContactA("Alice", AddressA("123 Main", 10001)))
      val result = Into.derived[EmployeeA, EmployeeB].into(source)

      assert(result)(isRight(equalTo(EmployeeB(1L, ContactB("Alice", AddressB("123 Main", 10001L))))))
    },
    test("nested collections with coercion") {
      case class InnerA(value: Int)
      case class InnerB(value: Long)
      case class OuterA(items: List[InnerA])
      case class OuterB(items: List[InnerB])

      implicit val innerInto: Into[InnerA, InnerB] = Into.derived
      val result                                   = Into.derived[OuterA, OuterB].into(OuterA(List(InnerA(1), InnerA(2))))

      assert(result)(isRight(equalTo(OuterB(List(InnerB(1L), InnerB(2L))))))
    },
    test("nested Options with coercion") {
      case class InnerA(value: Int)
      case class InnerB(value: Long)
      case class OuterA(inner: Option[InnerA])
      case class OuterB(inner: Option[InnerB])

      implicit val innerInto: Into[InnerA, InnerB] = Into.derived
      val result                                   = Into.derived[OuterA, OuterB].into(OuterA(Some(InnerA(42))))

      assert(result)(isRight(equalTo(OuterB(Some(InnerB(42L))))))
    }
  )
}
