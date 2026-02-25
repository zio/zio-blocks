package zio.blocks.schema.comptime

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

/**
 * Negative compile-time tests for Allows (Scala 2). Uses `|[A,B]` for unions.
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
        """).map(r => assertTrue(r.isLeft))
      },
      test("Option[Int] does NOT satisfy Allows[_, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[Option[Int], Primitive]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("A Record does NOT satisfy Allows[_, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Foo(x: Int)
          object Foo { implicit val schema: Schema[Foo] = Schema.derived }
          implicitly[Allows[Foo, Primitive]]
        """).map(r => assertTrue(r.isLeft))
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
        """).map(r => assertTrue(r.isLeft))
      },
      test("Sequence[Record] fails Record[`|`[Primitive, Sequence[Primitive]]]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Item(id: Int)
          object Item { implicit val schema: Schema[Item] = Schema.derived }
          case class Order(items: List[Item])
          object Order { implicit val schema: Schema[Order] = Schema.derived }
          implicitly[Allows[Order, Record[`|`[Primitive, Sequence[Primitive]]]]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("DynamicValue field fails Record[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.{ Schema, DynamicValue }
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Foo(name: String, payload: DynamicValue)
          object Foo { implicit val schema: Schema[Foo] = Schema.derived }
          implicitly[Allows[Foo, Record[Primitive]]]
        """).map(r => assertTrue(r.isLeft))
      }
    ),
    suite("Variant violations")(
      test("Record does NOT satisfy Variant[Record[Primitive]]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class JustARecord(id: Int)
          object JustARecord { implicit val schema: Schema[JustARecord] = Schema.derived }
          implicitly[Allows[JustARecord, Variant[Record[Primitive]]]]
        """).map(r => assertTrue(r.isLeft))
      }
    ),
    suite("Sequence violations")(
      test("List[List[Int]] does NOT satisfy Sequence[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[List[List[Int]], Sequence[Primitive]]]
        """).map(r => assertTrue(r.isLeft))
      }
    ),
    suite("Optional violations")(
      test("Option[Option[Int]] does NOT satisfy Optional[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          implicitly[Allows[Option[Option[Int]], Optional[Primitive]]]
        """).map(r => assertTrue(r.isLeft))
      }
    ),
    suite("Recursive violations")(
      test("Mutually recursive types produce compile-time error") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Forest(trees: List[Tree])
          case class Tree(value: Int, children: Forest)
          object Forest { implicit val schema: Schema[Forest] = Schema.derived }
          object Tree   { implicit val schema: Schema[Tree]   = Schema.derived }
          implicitly[Allows[Forest, Record[`|`[Primitive, Sequence[Self]]]]]
        """).map(r => assertTrue(r.isLeft))
      }
    )
  )
}
