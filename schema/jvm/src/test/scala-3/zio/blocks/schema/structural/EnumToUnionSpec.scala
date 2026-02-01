package zio.blocks.schema.structural
import zio.blocks.schema.SchemaBaseSpec

import zio.blocks.schema._
import zio.test._

/**
 * Tests for Scala 3 enum to structural union type conversion.
 *
 * Per issue #517: Enums convert to union types with Tag discriminators.
 * Example: enum Color { Red, Green, Blue } → Schema[{type Tag = "Red"} | {type
 * Tag = "Green"} | {type Tag = "Blue"}]
 */
object EnumToUnionSpec extends SchemaBaseSpec {

  enum Color {
    case Red, Green, Blue
  }

  enum Status {
    case Active, Inactive, Suspended
  }

  enum Shape {
    case Circle(radius: Double)
    case Rectangle(width: Double, height: Double)
    case Triangle(base: Double, height: Double)
  }

  def spec = suite("EnumToUnionSpec")(
    test("simple enum converts to structural union type") {
      typeCheck("""
        import zio.blocks.schema._
        enum Color { case Red, Green, Blue }
        val schema: Schema[Color] = Schema.derived[Color]
        val structural: Schema[{def Blue: {}} | {def Green: {}} | {def Red: {}}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    },
    test("parameterized enum converts to structural union type with fields") {
      typeCheck("""
        import zio.blocks.schema._
        enum Shape {
          case Circle(radius: Double)
          case Rectangle(width: Double, height: Double)
          case Triangle(base: Double, height: Double)
        }
        val schema: Schema[Shape] = Schema.derived[Shape]
        val structural: Schema[{def Circle: {def radius: Double}} | {def Rectangle: {def height: Double; def width: Double}} | {def Triangle: {def base: Double; def height: Double}}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    },
    test("structural enum schema is a Variant") {
      val schema     = Schema.derived[Color]
      val structural = schema.structural
      val isVariant  = (structural.reflect: @unchecked) match {
        case _: Reflect.Variant[_, _] => true
        case _                        => false
      }
      assertTrue(isVariant)
    },
    test("structural enum preserves case count") {
      val schema     = Schema.derived[Status]
      val structural = schema.structural
      val caseCount  = (structural.reflect: @unchecked) match {
        case v: Reflect.Variant[_, _] => v.cases.size
        case _                        => -1
      }
      assertTrue(caseCount == 3)
    },
    test("structural enum encodes anonymous instance via DynamicValue") {
      val schema     = Schema.derived[Color]
      val structural = schema.structural

      val redInstance: { def Red: {} } = new { def Red: {} = new {} }
      val dynamic                      = structural.toDynamicValue(redInstance)

      assertTrue(dynamic match {
        case DynamicValue.Variant("Red", _) => true
        case _                              => false
      })
    },
    test("parameterized enum structural encodes anonymous instance with fields") {
      val schema     = Schema.derived[Shape]
      val structural = schema.structural

      val circleInstance: { def Circle: { def radius: Double } } = new {
        def Circle: { def radius: Double } = new { def radius: Double = 5.0 }
      }

      val dynamic = structural.toDynamicValue(circleInstance)

      assertTrue(dynamic match {
        case DynamicValue.Variant("Circle", DynamicValue.Record(fields)) =>
          fields.toMap.get("radius").contains(DynamicValue.Primitive(PrimitiveValue.Double(5.0)))
        case _ => false
      })
    }
  )
}
