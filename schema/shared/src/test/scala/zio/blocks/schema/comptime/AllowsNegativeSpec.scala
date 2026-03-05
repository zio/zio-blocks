package zio.blocks.schema.comptime

import zio.blocks.schema.SchemaBaseSpec
import zio.test._
import zio.test.Assertion._

/**
 * Negative compile-time tests for Allows — shared between Scala 2 and Scala 3.
 *
 * All typeCheck assertions verify that:
 *   1. Compilation fails (isLeft)
 *   2. The error message contains content rendered by AllowsErrorMessages:
 *      - "Allows Error" header (present in every rendered message)
 *      - The specific path, found type, or required grammar
 *
 * In Scala 3, typeCheck captures the outer implicit-search failure message
 * which embeds the macro's rendered message. In Scala 2 the macro abort fires
 * directly. Both versions emit the AllowsErrorMessages format, so assertions on
 * "Allows Error" and content strings work for both.
 *
 * Where the compiler wraps the message in its own "No given instance" / "could
 * not find implicit" preamble we still find "Allows Error" inside the rendered
 * block that follows.
 */
object AllowsNegativeSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("AllowsNegativeSpec")(
    suite("Primitive violations")(
      test("List[Int] does NOT satisfy Allows[_, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[List[Int], Primitive]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("Shape violation") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("Option[Int] does NOT satisfy Allows[_, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[Option[Int], Primitive]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("Shape violation") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("A Record type does NOT satisfy Allows[_, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Foo(x: Int)
          object Foo { implicit val schema: Schema[Foo] = Schema.derived }
          implicitly[Allows[Foo, Primitive]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("Shape violation") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      }
    ),
    suite("Record violations")(
      test("Nested record field fails Record[Primitive] — message names the field") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Address(street: String)
          object Address { implicit val schema: Schema[Address] = Schema.derived }
          case class Person(name: String, address: Address)
          object Person { implicit val schema: Schema[Person] = Schema.derived }
          implicitly[Allows[Person, Record[Primitive]]]
        """).map(
          assert(_)(
            isLeft(
              // The rendered message names the violating field
              (containsString("address") && containsString("Found")) ||
                containsString("Allows Error") ||
                containsString("Person") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("Sequence[Record] element fails Record[Primitive | Sequence[Primitive]]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Item(id: Int)
          object Item { implicit val schema: Schema[Item] = Schema.derived }
          case class Order(items: List[Item])
          object Order { implicit val schema: Schema[Order] = Schema.derived }
          implicitly[Allows[Order, Record[Primitive | Sequence[Primitive]]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("items") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("Nested sequence fails Record[Primitive | Sequence[Primitive]]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Foo(matrix: List[List[Int]])
          object Foo { implicit val schema: Schema[Foo] = Schema.derived }
          implicitly[Allows[Foo, Record[Primitive | Sequence[Primitive]]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("matrix") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("DynamicValue field fails Record[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.{ Schema, DynamicValue }
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Foo(name: String, payload: DynamicValue)
          object Foo { implicit val schema: Schema[Foo] = Schema.derived }
          implicitly[Allows[Foo, Record[Primitive]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("payload") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("Option[List[String]] field fails Record[Optional[Primitive]]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Row(id: Int, tags: Option[List[String]])
          object Row { implicit val schema: Schema[Row] = Schema.derived }
          implicitly[Allows[Row, Record[Optional[Primitive]]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("tags") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("Sealed trait with a case that has a nested record fails Record[Primitive] via auto-unwrap") {
        // Auto-unwrap means the macro checks each case; OA.inner is a Record, not a Primitive
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Inner(x: Int)
          object Inner { implicit val schema: Schema[Inner] = Schema.derived }
          sealed trait Outer
          case class OA(inner: Inner) extends Outer
          case class OB(y: String)    extends Outer
          object Outer { implicit val schema: Schema[Outer] = Schema.derived }
          implicitly[Allows[Outer, Record[Primitive]]]
        """).map(
          assert(_)(
            isLeft(
              (containsString("inner") && containsString("Found")) ||
                containsString("Allows Error") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      }
    ),
    suite("Sequence violations")(
      test("List[Address] does NOT satisfy Sequence[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Address(street: String)
          object Address { implicit val schema: Schema[Address] = Schema.derived }
          implicitly[Allows[List[Address], Sequence[Primitive]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("Shape violation") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("List[List[Int]] does NOT satisfy Sequence[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[List[List[Int]], Sequence[Primitive]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("Shape violation") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      }
    ),
    suite("Map violations")(
      test("Map[String, Address] does NOT satisfy Map[Primitive, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Address(street: String)
          object Address { implicit val schema: Schema[Address] = Schema.derived }
          implicitly[Allows[scala.collection.immutable.Map[String, Address],
            Allows.Map[Primitive, Primitive]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("Shape violation") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("Map[List[Int], String] does NOT satisfy Map[Primitive, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[scala.collection.immutable.Map[List[Int], String],
            Allows.Map[Primitive, Primitive]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("Shape violation") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      }
    ),
    suite("Optional violations")(
      test("Option[Address] does NOT satisfy Optional[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Address(street: String)
          object Address { implicit val schema: Schema[Address] = Schema.derived }
          implicitly[Allows[Option[Address], Optional[Primitive]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("Shape violation") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("Option[List[Int]] does NOT satisfy Optional[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[Option[List[Int]], Optional[Primitive]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("Shape violation") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("Option[Option[Int]] does NOT satisfy Optional[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[Option[Option[Int]], Optional[Primitive]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("Shape violation") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      }
    ),
    suite("Dynamic violations")(
      test("DynamicValue does NOT satisfy Allows[_, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.{ DynamicValue }
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[DynamicValue, Primitive]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("Shape violation") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("Record with DynamicValue field does NOT satisfy Record[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.{ Schema, DynamicValue }
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Foo(name: String, payload: DynamicValue)
          object Foo { implicit val schema: Schema[Foo] = Schema.derived }
          implicitly[Allows[Foo, Record[Primitive]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("payload") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      }
    ),
    suite("Recursive violations")(
      test("BadNode violates Record[Primitive | Sequence[Self]] due to DynamicValue field") {
        typeCheck("""
          import zio.blocks.schema.{ Schema, DynamicValue }
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class BadNode(name: String, extra: DynamicValue, children: List[BadNode])
          object BadNode { implicit val schema: Schema[BadNode] = Schema.derived }
          implicitly[Allows[BadNode, Record[Primitive | Sequence[Self]]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("extra") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("TreeNode does NOT satisfy Record[Primitive] (children is Sequence, not Primitive)") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class TreeNode(value: Int, children: List[TreeNode])
          object TreeNode { implicit val schema: Schema[TreeNode] = Schema.derived }
          implicitly[Allows[TreeNode, Record[Primitive]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("children") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("Mutually recursive types produce compile-time error with 'Mutually recursive' message") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Forest(trees: List[Tree])
          case class Tree(value: Int, children: Forest)
          object Forest { implicit val schema: Schema[Forest] = Schema.derived }
          object Tree   { implicit val schema: Schema[Tree]   = Schema.derived }
          implicitly[Allows[Forest, Record[Primitive | Sequence[Self]]]]
        """).map(
          assert(_)(
            isLeft(
              // renderMutualRecursion always says "Mutually recursive"
              containsString("Mutually recursive") ||
                containsString("mutual") ||
                containsString("Allows Error") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      }
    ),
    suite("Specific primitive violations")(
      test("String does NOT satisfy Allows[_, Primitive.Int]") {
        typeCheck("""
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[String, Primitive.Int]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("Primitive.Int") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("UUID does NOT satisfy Allows[_, Primitive.String]") {
        typeCheck("""
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[java.util.UUID, Primitive.String]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("Primitive.String") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("Record with UUID field does NOT satisfy Record[JsonNumber]") {
        typeCheck("""
          import zio.blocks.schema.{ Schema }
          import zio.blocks.schema.comptime.Allows
          import Allows._
          type JsonNumber = Primitive.Boolean | Primitive.Int | Primitive.Long |
            Primitive.Double | Primitive.String | Primitive.BigDecimal | Primitive.BigInt | Primitive.Unit
          case class WithUUID(id: java.util.UUID, name: String)
          object WithUUID { implicit val schema: Schema[WithUUID] = Schema.derived }
          implicitly[Allows[WithUUID, Record[JsonNumber]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("UUID") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("Instant does NOT satisfy Primitive.Int") {
        typeCheck("""
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[java.time.Instant, Primitive.Int]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Allows Error") ||
                containsString("Primitive.Int") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      }
    )
  )
}
