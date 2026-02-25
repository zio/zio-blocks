package zio.blocks.schema.comptime

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

/**
 * Negative compile-time tests for Allows (Scala 3). Verifies that the macro
 * rejects types that violate the grammar, with precise error messages.
 */
object AllowsNegativeSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("AllowsNegativeSpec")(
    suite("Primitive violations")(
      test("List[Int] does NOT satisfy Allows[_, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          summon[Allows[List[Int], Primitive]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("Option[Int] does NOT satisfy Allows[_, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          summon[Allows[Option[Int], Primitive]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("A Record does NOT satisfy Allows[_, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Foo(x: Int) derives Schema
          summon[Allows[Foo, Primitive]]
        """).map(r => assertTrue(r.isLeft))
      }
    ),
    suite("Record violations")(
      test("Nested record field fails Record[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Address(street: String) derives Schema
          case class Person(name: String, address: Address) derives Schema
          summon[Allows[Person, Record[Primitive]]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("Sequence[Record] fails Record[Primitive | Sequence[Primitive]]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Item(id: Int) derives Schema
          case class Order(items: List[Item]) derives Schema
          summon[Allows[Order, Record[Primitive | Sequence[Primitive]]]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("Nested sequence fails Record[Primitive | Sequence[Primitive]]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Foo(matrix: List[List[Int]]) derives Schema
          summon[Allows[Foo, Record[Primitive | Sequence[Primitive]]]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("DynamicValue field fails Record[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.{ Schema, DynamicValue }
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Foo(name: String, payload: DynamicValue) derives Schema
          summon[Allows[Foo, Record[Primitive]]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("Option[List[String]] field fails Record[Optional[Primitive]]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Row(id: Int, tags: Option[List[String]]) derives Schema
          summon[Allows[Row, Record[Optional[Primitive]]]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("Variant does NOT satisfy Record[...]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          sealed trait Ev derives Schema
          case class A(x: Int) extends Ev
          summon[Allows[Ev, Record[Primitive]]]
        """).map(r => assertTrue(r.isLeft))
      }
    ),
    suite("Variant violations")(
      test("Record does NOT satisfy Variant[Record[Primitive]]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class JustARecord(id: Int) derives Schema
          summon[Allows[JustARecord, Variant[Record[Primitive]]]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("Variant case with nested record fails Variant[Record[Primitive]]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Inner(x: Int) derives Schema
          sealed trait Outer derives Schema
          case class A(inner: Inner) extends Outer
          case class B(y: String) extends Outer
          summon[Allows[Outer, Variant[Record[Primitive]]]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("Primitive does NOT satisfy Variant[...]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          summon[Allows[Int, Variant[Record[Primitive]]]]
        """).map(r => assertTrue(r.isLeft))
      }
    ),
    suite("Sequence violations")(
      test("List[Address] does NOT satisfy Sequence[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Address(street: String) derives Schema
          summon[Allows[List[Address], Sequence[Primitive]]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("List[List[Int]] does NOT satisfy Sequence[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          summon[Allows[List[List[Int]], Sequence[Primitive]]]
        """).map(r => assertTrue(r.isLeft))
      }
    ),
    suite("Map violations")(
      test("Map[String, Address] does NOT satisfy Map[Primitive, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Address(street: String) derives Schema
          summon[Allows[Map[String, Address], Map[Primitive, Primitive]]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("Map[List[Int], String] does NOT satisfy Map[Primitive, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          summon[Allows[Map[List[Int], String], Map[Primitive, Primitive]]]
        """).map(r => assertTrue(r.isLeft))
      }
    ),
    suite("Optional violations")(
      test("Option[Address] does NOT satisfy Optional[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Address(street: String) derives Schema
          summon[Allows[Option[Address], Optional[Primitive]]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("Option[List[Int]] does NOT satisfy Optional[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          summon[Allows[Option[List[Int]], Optional[Primitive]]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("Option[Option[Int]] does NOT satisfy Optional[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          summon[Allows[Option[Option[Int]], Optional[Primitive]]]
        """).map(r => assertTrue(r.isLeft))
      }
    ),
    suite("Dynamic violations")(
      test("DynamicValue does NOT satisfy Allows[_, Primitive]") {
        typeCheck("""
          import zio.blocks.schema.{ Schema, DynamicValue }
          import zio.blocks.schema.comptime.Allows
          import Allows._
          summon[Allows[DynamicValue, Primitive]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("Record with DynamicValue field does NOT satisfy Record[Primitive]") {
        typeCheck("""
          import zio.blocks.schema.{ Schema, DynamicValue }
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Foo(name: String, payload: DynamicValue) derives Schema
          summon[Allows[Foo, Record[Primitive]]]
        """).map(r => assertTrue(r.isLeft))
      }
    ),
    suite("Recursive violations")(
      test("BadNode violates Record[Primitive | Sequence[Self]] due to DynamicValue field") {
        typeCheck("""
          import zio.blocks.schema.{ Schema, DynamicValue }
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class BadNode(name: String, extra: DynamicValue, children: List[BadNode]) derives Schema
          summon[Allows[BadNode, Record[Primitive | Sequence[Self]]]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("TreeNode does NOT satisfy Record[Primitive] (children is Sequence)") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class TreeNode(value: Int, children: List[TreeNode]) derives Schema
          summon[Allows[TreeNode, Record[Primitive]]]
        """).map(r => assertTrue(r.isLeft))
      },
      test("Mutually recursive types produce compile-time error") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Forest(trees: List[Tree]) derives Schema
          case class Tree(value: Int, children: Forest) derives Schema
          summon[Allows[Forest, Record[Primitive | Sequence[Self]]]]
        """).map(r => assertTrue(r.isLeft))
      }
    ),
    suite("Error messages contain path information")(
      test("Error for nested record violation mentions field name") {
        typeCheck("""
          import zio.blocks.schema.Schema
          import zio.blocks.schema.comptime.Allows
          import Allows._
          case class Address(street: String) derives Schema
          case class Person(name: String, address: Address) derives Schema
          summon[Allows[Person, Record[Primitive]]]
        """).map { r =>
          assertTrue(
            r.isLeft,
            r.swap.exists(msg => msg.contains("address") || msg.contains("Person"))
          )
        }
      }
    )
  )
}
