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

  implicit val addressV1ToV2: Into[AddressV1, AddressV2] = Into.derived

  def spec: Spec[TestEnvironment, Any] = suite("NestedProductsSpec")(
    test("converts nested case class with type coercion") {
      val result = Into.derived[PersonV1, PersonV2].into(PersonV1("Alice", AddressV1("Main St", 12345)))
      assert(result)(isRight(equalTo(PersonV2("Alice", AddressV2("Main St", 12345L)))))
    },
    test("converts multiple nesting levels") {
      case class Inner(value: Int)
      case class Middle(inner: Inner, name: String)
      case class Outer(middle: Middle)

      case class InnerB(value: Long)
      case class MiddleB(inner: InnerB, name: String)
      case class OuterB(middle: MiddleB)

      implicit val innerInto: Into[Inner, InnerB]    = Into.derived
      implicit val middleInto: Into[Middle, MiddleB] = Into.derived

      val result = Into.derived[Outer, OuterB].into(Outer(Middle(Inner(42), "test")))
      assert(result)(isRight(equalTo(OuterB(MiddleB(InnerB(42L), "test")))))
    },
    test("nested case class with field reordering") {
      case class Inner(x: Int, y: Int)
      case class Outer(label: String, inner: Inner)
      case class InnerR(y: Int, x: Int)
      case class OuterR(inner: InnerR, label: String)

      implicit val innerInto: Into[Inner, InnerR] = Into.derived
      val result                                  = Into.derived[Outer, OuterR].into(Outer("test", Inner(1, 2)))
      assert(result)(isRight(equalTo(OuterR(InnerR(2, 1), "test"))))
    }
  )
}
