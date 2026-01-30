package zio.blocks.schema.into.evolution

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for schema evolution when adding optional fields.
 *
 * Covers:
 *   - Adding Option[T] fields to target (source doesn't have them)
 *   - Multiple optional fields added
 *   - Nested structures with added optional fields
 */
object AddOptionalFieldSpec extends ZIOSpecDefault {

  // === Test types for adding optional fields ===

  // V1 -> V2: Adding single optional field
  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Int, email: Option[String])

  // V1 -> V2: Adding multiple optional fields
  case class ProductV1(id: Long, name: String)
  case class ProductV2(id: Long, name: String, description: Option[String], price: Option[Double])

  // V1 -> V2: Adding optional field with type coercion
  case class ConfigV1(key: String, value: Int)
  case class ConfigV2(key: String, value: Long, metadata: Option[String])

  // Nested types
  case class AddressV1(street: String, city: String)
  case class AddressV2(street: String, city: String, zipCode: Option[String])

  case class ContactV1(name: String, address: AddressV1)
  case class ContactV2(name: String, address: AddressV2)

  // Optional collection field added
  case class OrderV1(id: Long, customer: String)
  case class OrderV2(id: Long, customer: String, items: Option[List[String]])

  // Optional nested type added
  case class Metadata(createdBy: String, createdAt: Long)
  case class DocumentV1(title: String, content: String)
  case class DocumentV2(title: String, content: String, metadata: Option[Metadata])

  // Empty source types - adding optional fields to empty source
  case class EmptySourceAdd()
  case class AllOptionalTarget(a: Option[Int], b: Option[String], c: Option[Boolean])

  // Case object source - adding optional fields
  case object EmptyObjectAdd
  case class AllOptionalFromObject(x: Option[Long], y: Option[String])

  def spec: Spec[TestEnvironment, Any] = suite("AddOptionalFieldSpec")(
    suite("Adding Single Optional Field")(
      test("adds Option field with None when not in source") {
        val source = PersonV1("Alice", 30)
        val result = Into.derived[PersonV1, PersonV2].into(source)

        assert(result)(isRight(equalTo(PersonV2("Alice", 30, None))))
      },
      test("existing fields still map correctly") {
        val source = PersonV1("Bob", 25)
        val result = Into.derived[PersonV1, PersonV2].into(source)

        assert(result.map(_.name))(isRight(equalTo("Bob"))) &&
        assert(result.map(_.age))(isRight(equalTo(25)))
      }
    ),
    suite("Adding Multiple Optional Fields")(
      test("adds multiple Option fields with None") {
        val source = ProductV1(1L, "Widget")
        val result = Into.derived[ProductV1, ProductV2].into(source)

        assert(result)(isRight(equalTo(ProductV2(1L, "Widget", None, None))))
      }
    ),
    suite("Adding Optional Field with Type Coercion")(
      test("coerces existing fields and adds None for new optional") {
        val source = ConfigV1("timeout", 30)
        val result = Into.derived[ConfigV1, ConfigV2].into(source)

        assert(result)(isRight(equalTo(ConfigV2("timeout", 30L, None))))
      }
    ),
    suite("Nested Structures with Added Optional Fields")(
      test("adds optional field in nested type") {
        implicit val addressV1ToV2: Into[AddressV1, AddressV2] = Into.derived[AddressV1, AddressV2]

        val source = ContactV1("Alice", AddressV1("123 Main St", "Springfield"))
        val result = Into.derived[ContactV1, ContactV2].into(source)

        assert(result)(isRight(equalTo(ContactV2("Alice", AddressV2("123 Main St", "Springfield", None)))))
      }
    ),
    suite("Adding Optional Collection Field")(
      test("adds optional List field with None") {
        val source = OrderV1(1L, "Customer A")
        val result = Into.derived[OrderV1, OrderV2].into(source)

        assert(result)(isRight(equalTo(OrderV2(1L, "Customer A", None))))
      }
    ),
    suite("Adding Optional Nested Type Field")(
      test("adds optional nested case class field with None") {
        val source = DocumentV1("Report", "Content here")
        val result = Into.derived[DocumentV1, DocumentV2].into(source)

        assert(result)(isRight(equalTo(DocumentV2("Report", "Content here", None))))
      }
    ),
    suite("Edge Cases")(
      test("adding optional field to single-field case class") {
        case class SimpleV1(value: Int)
        case class SimpleV2(value: Int, extra: Option[String])

        val source = SimpleV1(42)
        val result = Into.derived[SimpleV1, SimpleV2].into(source)

        assert(result)(isRight(equalTo(SimpleV2(42, None))))
      },
      test("adding many optional fields at once") {
        case class MinimalV1(id: Int)
        case class ExpandedV2(
          id: Int,
          opt1: Option[String],
          opt2: Option[Int],
          opt3: Option[Boolean],
          opt4: Option[Long]
        )

        val source = MinimalV1(1)
        val result = Into.derived[MinimalV1, ExpandedV2].into(source)

        assert(result)(isRight(equalTo(ExpandedV2(1, None, None, None, None))))
      },
      test("empty case class source adds all optional fields with None") {
        val source = EmptySourceAdd()
        val result = Into.derived[EmptySourceAdd, AllOptionalTarget].into(source)

        assert(result)(isRight(equalTo(AllOptionalTarget(None, None, None))))
      },
      test("case object source adds all optional fields with None") {
        val result = Into.derived[EmptyObjectAdd.type, AllOptionalFromObject].into(EmptyObjectAdd)

        assert(result)(isRight(equalTo(AllOptionalFromObject(None, None))))
      }
    ),
    suite("Optional Field at Different Positions")(
      test("optional field at beginning") {
        case class SourceBegin(name: String, age: Int)
        case class TargetBegin(prefix: Option[String], name: String, age: Int)

        val source = SourceBegin("Alice", 30)
        val result = Into.derived[SourceBegin, TargetBegin].into(source)

        assert(result)(isRight(equalTo(TargetBegin(None, "Alice", 30))))
      },
      test("optional field in middle") {
        case class SourceMid(name: String, age: Int)
        case class TargetMid(name: String, middle: Option[String], age: Int)

        val source = SourceMid("Bob", 25)
        val result = Into.derived[SourceMid, TargetMid].into(source)

        assert(result)(isRight(equalTo(TargetMid("Bob", None, 25))))
      },
      test("optional field at end") {
        case class SourceEnd(name: String, age: Int)
        case class TargetEnd(name: String, age: Int, suffix: Option[String])

        val source = SourceEnd("Charlie", 35)
        val result = Into.derived[SourceEnd, TargetEnd].into(source)

        assert(result)(isRight(equalTo(TargetEnd("Charlie", 35, None))))
      }
    )
  )
}
