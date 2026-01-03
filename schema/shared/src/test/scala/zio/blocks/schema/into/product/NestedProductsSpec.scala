package zio.blocks.schema.into.product

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for nested product conversions. */
object NestedProductsSpec extends ZIOSpecDefault {

  case class AddressV1(street: String, zip: Int)
  case class PersonV1(name: String, address: AddressV1)

  case class AddressV2(street: String, zip: Long)
  case class PersonV2(name: String, address: AddressV2)

  case class Inner(value: Int)
  case class Middle(inner: Inner, name: String)
  case class Outer(middle: Middle)

  case class InnerB(value: Long)
  case class MiddleB(inner: InnerB, name: String)
  case class OuterB(middle: MiddleB)

  case class InnerR(x: Int, y: Int)
  case class OuterR(label: String, inner: InnerR)
  case class InnerRSwapped(y: Int, x: Int)
  case class OuterRSwapped(inner: InnerRSwapped, label: String)

  implicit val addressV1ToV2: Into[AddressV1, AddressV2] = Into.derived[AddressV1, AddressV2]

  def spec: Spec[TestEnvironment, Any] = suite("NestedProductsSpec")(
    test("converts nested case class with type coercion") {
      val result = Into.derived[PersonV1, PersonV2].into(PersonV1("Alice", AddressV1("Main St", 12345)))
      assert(result)(isRight(equalTo(PersonV2("Alice", AddressV2("Main St", 12345L)))))
    },
    test("converts multiple nesting levels") {
      implicit val innerInto: Into[Inner, InnerB]    = Into.derived[Inner, InnerB]
      implicit val middleInto: Into[Middle, MiddleB] = Into.derived[Middle, MiddleB]

      val result = Into.derived[Outer, OuterB].into(Outer(Middle(Inner(42), "test")))
      assert(result)(isRight(equalTo(OuterB(MiddleB(InnerB(42L), "test")))))
    },
    test("nested case class with field reordering") {
      implicit val innerInto: Into[InnerR, InnerRSwapped] = Into.derived[InnerR, InnerRSwapped]
      val result                                          = Into.derived[OuterR, OuterRSwapped].into(OuterR("test", InnerR(1, 2)))
      assert(result)(isRight(equalTo(OuterRSwapped(InnerRSwapped(2, 1), "test"))))
    }
  )
}
