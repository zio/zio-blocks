package zio.blocks.schema.into.evolution

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for schema evolution when removing optional fields.
 *
 * Covers:
 *   - Removing Option[T] fields from source (target doesn't have them)
 *   - Removing multiple optional fields
 *   - Nested structures with removed optional fields
 *   - What happens to the data in removed fields (it's dropped)
 */
object RemoveOptionalFieldSpec extends ZIOSpecDefault {

  // === Test types for removing optional fields ===

  // V2 -> V1: Removing single optional field
  case class PersonV2(name: String, age: Int, email: Option[String])
  case class PersonV1(name: String, age: Int)

  // V2 -> V1: Removing multiple optional fields
  case class ProductV2(id: Long, name: String, description: Option[String], price: Option[Double])
  case class ProductV1(id: Long, name: String)

  // V2 -> V1: Removing optional field with type coercion
  case class ConfigV2(key: String, value: Long, metadata: Option[String])
  case class ConfigV1(key: String, value: Int)

  // Nested types
  case class AddressV2(street: String, city: String, zipCode: Option[String])
  case class AddressV1(street: String, city: String)

  case class ContactV2(name: String, address: AddressV2)
  case class ContactV1(name: String, address: AddressV1)

  // Optional collection field removed
  case class OrderV2(id: Long, customer: String, items: Option[List[String]])
  case class OrderV1(id: Long, customer: String)

  def spec: Spec[TestEnvironment, Any] = suite("RemoveOptionalFieldSpec")(
    suite("Removing Single Optional Field")(
      test("drops Some value when field is removed") {
        val source = PersonV2("Alice", 30, Some("alice@example.com"))
        val result = Into.derived[PersonV2, PersonV1].into(source)

        assert(result)(isRight(equalTo(PersonV1("Alice", 30))))
      },
      test("converts when optional field is None") {
        val source = PersonV2("Bob", 25, None)
        val result = Into.derived[PersonV2, PersonV1].into(source)

        assert(result)(isRight(equalTo(PersonV1("Bob", 25))))
      }
    ),
    suite("Removing Multiple Optional Fields")(
      test("drops multiple Some values") {
        val source = ProductV2(1L, "Widget", Some("A great widget"), Some(19.99))
        val result = Into.derived[ProductV2, ProductV1].into(source)

        assert(result)(isRight(equalTo(ProductV1(1L, "Widget"))))
      },
      test("drops mix of Some and None values") {
        val source = ProductV2(2L, "Gadget", Some("Fancy gadget"), None)
        val result = Into.derived[ProductV2, ProductV1].into(source)

        assert(result)(isRight(equalTo(ProductV1(2L, "Gadget"))))
      },
      test("converts when all optional fields are None") {
        val source = ProductV2(3L, "Thing", None, None)
        val result = Into.derived[ProductV2, ProductV1].into(source)

        assert(result)(isRight(equalTo(ProductV1(3L, "Thing"))))
      }
    ),
    suite("Removing Optional Field with Type Coercion")(
      test("narrows types and drops optional field when value fits") {
        val source = ConfigV2("timeout", 30L, Some("extra"))
        val result = Into.derived[ConfigV2, ConfigV1].into(source)

        assert(result)(isRight(equalTo(ConfigV1("timeout", 30))))
      },
      test("fails when narrowing overflows despite dropped field") {
        val source = ConfigV2("timeout", Long.MaxValue, Some("extra"))
        val result = Into.derived[ConfigV2, ConfigV1].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Nested Structures with Removed Optional Fields")(
      test("removes optional field in nested type") {
        implicit val addressV2ToV1: Into[AddressV2, AddressV1] = Into.derived[AddressV2, AddressV1]

        val source = ContactV2("Alice", AddressV2("123 Main St", "Springfield", Some("12345")))
        val result = Into.derived[ContactV2, ContactV1].into(source)

        assert(result)(isRight(equalTo(ContactV1("Alice", AddressV1("123 Main St", "Springfield")))))
      },
      test("removes optional field when nested value is None") {
        implicit val addressV2ToV1: Into[AddressV2, AddressV1] = Into.derived[AddressV2, AddressV1]

        val source = ContactV2("Bob", AddressV2("456 Oak Ave", "Shelbyville", None))
        val result = Into.derived[ContactV2, ContactV1].into(source)

        assert(result)(isRight(equalTo(ContactV1("Bob", AddressV1("456 Oak Ave", "Shelbyville")))))
      }
    ),
    suite("Removing Optional Collection Field")(
      test("drops optional List field with data") {
        val source = OrderV2(1L, "Customer A", Some(List("item1", "item2")))
        val result = Into.derived[OrderV2, OrderV1].into(source)

        assert(result)(isRight(equalTo(OrderV1(1L, "Customer A"))))
      },
      test("drops None optional List field") {
        val source = OrderV2(2L, "Customer B", None)
        val result = Into.derived[OrderV2, OrderV1].into(source)

        assert(result)(isRight(equalTo(OrderV1(2L, "Customer B"))))
      }
    ),
    suite("Edge Cases")(
      test("removing optional field from two-field case class") {
        case class SourceTwo(value: Int, extra: Option[String])
        case class TargetTwo(value: Int)

        val source = SourceTwo(42, Some("data"))
        val result = Into.derived[SourceTwo, TargetTwo].into(source)

        assert(result)(isRight(equalTo(TargetTwo(42))))
      },
      test("removing many optional fields at once") {
        case class ExpandedV2(
          id: Int,
          opt1: Option[String],
          opt2: Option[Int],
          opt3: Option[Boolean],
          opt4: Option[Long]
        )
        case class MinimalV1(id: Int)

        val source = ExpandedV2(1, Some("a"), Some(2), Some(true), Some(100L))
        val result = Into.derived[ExpandedV2, MinimalV1].into(source)

        assert(result)(isRight(equalTo(MinimalV1(1))))
      }
    ),
    suite("Data Loss Behavior")(
      test("confirms data in removed optional field is lost") {
        val source = PersonV2("Test", 20, Some("important-email@example.com"))
        val result = Into.derived[PersonV2, PersonV1].into(source)

        // The email data is intentionally dropped
        assert(result)(isRight(equalTo(PersonV1("Test", 20))))
      }
    ),
    suite("Optional Field at Different Positions Removed")(
      test("removes optional field from beginning") {
        case class SourceBegin(prefix: Option[String], name: String, age: Int)
        case class TargetBegin(name: String, age: Int)

        val source = SourceBegin(Some("Dr."), "Alice", 30)
        val result = Into.derived[SourceBegin, TargetBegin].into(source)

        assert(result)(isRight(equalTo(TargetBegin("Alice", 30))))
      },
      test("removes optional field from middle") {
        case class SourceMid(name: String, middle: Option[String], age: Int)
        case class TargetMid(name: String, age: Int)

        val source = SourceMid("Bob", Some("James"), 25)
        val result = Into.derived[SourceMid, TargetMid].into(source)

        assert(result)(isRight(equalTo(TargetMid("Bob", 25))))
      },
      test("removes optional field from end") {
        case class SourceEnd(name: String, age: Int, suffix: Option[String])
        case class TargetEnd(name: String, age: Int)

        val source = SourceEnd("Charlie", 35, Some("Jr."))
        val result = Into.derived[SourceEnd, TargetEnd].into(source)

        assert(result)(isRight(equalTo(TargetEnd("Charlie", 35))))
      }
    )
  )
}
