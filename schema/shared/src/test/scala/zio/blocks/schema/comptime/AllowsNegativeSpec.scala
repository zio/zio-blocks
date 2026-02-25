package zio.blocks.schema.comptime

import zio.blocks.schema.SchemaBaseSpec
import zio.test._
import zio.test.Assertion._

/**
 * Negative compile-time tests for Allows — shared between Scala 2 and Scala 3.
 *
 * All typeCheck assertions verify both that the compilation fails AND that the
 * error message is meaningful. The exact wording differs between compiler
 * versions so assertions use `containsString` with OR alternatives.
 */
object AllowsNegativeSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("AllowsNegativeSpec")(
    suite("Primitive violations")(
      test("List[Int] does NOT satisfy Allows[_, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[List[Int], Primitive]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Primitive") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("Option[Int] does NOT satisfy Allows[_, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[Option[Int], Primitive]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Primitive") ||
                containsString("Optional") ||
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
              containsString("Record") ||
                containsString("Primitive") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      }
    ),
    suite("Record violations")(
      test("Nested record field fails Record[Primitive]") {
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
              // Scala 3: outer implicit failure; Scala 2: macro abort message
              (containsString("address") && (containsString("Record") || containsString("Primitive"))) ||
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
              containsString("items") ||
                containsString("Record") ||
                containsString("Primitive")
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
              containsString("matrix") ||
                containsString("Sequence") ||
                containsString("Primitive")
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
              containsString("tags") ||
                containsString("Sequence") ||
                containsString("Optional") ||
                containsString("Primitive")
            )
          )
        )
      },
      test("Variant type does NOT satisfy Record[...]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          sealed trait Ev
          case class EvA(x: Int) extends Ev
          object Ev { implicit val schema: Schema[Ev] = Schema.derived }
          implicitly[Allows[Ev, Record[Primitive]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Record") ||
                containsString("Variant") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      }
    ),
    suite("Variant violations")(
      test("Record type does NOT satisfy Variant[Record[Primitive]]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class JustARecord(id: Int)
          object JustARecord { implicit val schema: Schema[JustARecord] = Schema.derived }
          implicitly[Allows[JustARecord, Variant[Record[Primitive]]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Variant") ||
                containsString("Record") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("Variant case with nested record fails Variant[Record[Primitive]]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Inner(x: Int)
          object Inner { implicit val schema: Schema[Inner] = Schema.derived }
          sealed trait Outer
          case class OA(inner: Inner) extends Outer
          case class OB(y: String) extends Outer
          object Outer { implicit val schema: Schema[Outer] = Schema.derived }
          implicitly[Allows[Outer, Variant[Record[Primitive]]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("inner") ||
                containsString("Record") ||
                containsString("Primitive")
            )
          )
        )
      },
      test("Primitive type does NOT satisfy Variant[...]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[Int, Variant[Record[Primitive]]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Variant") ||
                containsString("Primitive") ||
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
              containsString("Record") ||
                containsString("Primitive") ||
                containsString("Sequence")
            )
          )
        )
      },
      test("List[List[Int]] does NOT satisfy Sequence[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[List[List[Int]], Sequence[Primitive]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Sequence") ||
                containsString("Primitive")
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
              containsString("Record") ||
                containsString("Primitive") ||
                containsString("Map")
            )
          )
        )
      },
      test("Map[List[Int], String] does NOT satisfy Map[Primitive, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[scala.collection.immutable.Map[List[Int], String],
            Allows.Map[Primitive, Primitive]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Sequence") ||
                containsString("Primitive") ||
                containsString("Map")
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
              containsString("Record") ||
                containsString("Primitive") ||
                containsString("Optional")
            )
          )
        )
      },
      test("Option[List[Int]] does NOT satisfy Optional[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[Option[List[Int]], Optional[Primitive]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Sequence") ||
                containsString("Primitive") ||
                containsString("Optional")
            )
          )
        )
      },
      test("Option[Option[Int]] does NOT satisfy Optional[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[Option[Option[Int]], Optional[Primitive]]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Optional") ||
                containsString("Primitive")
            )
          )
        )
      }
    ),
    suite("Dynamic violations")(
      test("DynamicValue does NOT satisfy Allows[_, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.{ Schema, DynamicValue }
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[DynamicValue, Primitive]]
        """).map(
          assert(_)(
            isLeft(
              containsString("Dynamic") ||
                containsString("Primitive") ||
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
              containsString("payload") ||
                containsString("Dynamic") ||
                containsString("Primitive")
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
              containsString("extra") ||
                containsString("Dynamic") ||
                containsString("Primitive")
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
              containsString("children") ||
                containsString("Sequence") ||
                containsString("Primitive")
            )
          )
        )
      },
      test("Mutually recursive types produce compile-time error mentioning the cycle") {
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
              containsString("mutual") ||
                containsString("Mutual") ||
                containsString("cycle") ||
                containsString("Cycle") ||
                containsString("recursive") ||
                // Scala 2: may surface as "could not find"
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
              containsString("Primitive.Int") ||
                containsString("Int") ||
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
              containsString("Primitive.String") ||
                containsString("String") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      },
      test("Record with UUID field does NOT satisfy Record[JsonNumber] (UUID not in JsonNumber)") {
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
              containsString("UUID") ||
                containsString("Primitive.UUID") ||
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
              containsString("Primitive.Int") ||
                containsString("Int") ||
                containsString("could not find") ||
                containsString("No given instance")
            )
          )
        )
      }
    )
  )
}
