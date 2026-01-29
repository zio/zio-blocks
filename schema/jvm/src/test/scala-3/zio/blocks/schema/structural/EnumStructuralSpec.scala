package zio.blocks.schema.structural
import zio.blocks.schema.SchemaBaseSpec

import zio.blocks.schema._
import zio.test._

/**
 * Tests for Scala 3 enum structural conversion (JVM only, requires
 * ToStructural).
 */
object EnumStructuralSpec extends SchemaBaseSpec {

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

  def spec = suite("EnumStructuralSpec")(
    suite("Structural Conversion")(
      test("simple enum converts to structural union type") {
        typeCheck("""
          import zio.blocks.schema._
          enum Color { case Red, Green, Blue }
          val schema = Schema.derived[Color]
          val structural: Schema[{def Blue: {}} | {def Green: {}} | {def Red: {}}] = schema.structural
        """).map(result => assertTrue(result.isRight))
      },
      test("parameterized enum converts to structural union type") {
        typeCheck("""
          import zio.blocks.schema._
          enum Shape {
            case Circle(radius: Double)
            case Rectangle(width: Double, height: Double)
            case Triangle(base: Double, height: Double)
          }
          val schema = Schema.derived[Shape]
          val structural: Schema[
            {def Circle: {def radius: Double}} |
            {def Rectangle: {def height: Double; def width: Double}} |
            {def Triangle: {def base: Double; def height: Double}}
          ] = schema.structural
        """).map(result => assertTrue(result.isRight))
      },
      test("structural enum schema is still a Variant") {
        val schema     = Schema.derived[Status]
        val structural = schema.structural
        val isVariant  = (structural.reflect: @unchecked) match {
          case _: Reflect.Variant[_, _] => true
        }
        assertTrue(isVariant)
      },
      test("structural enum preserves case count") {
        val schema     = Schema.derived[Status]
        val structural = schema.structural
        val caseCount  = (structural.reflect: @unchecked) match {
          case v: Reflect.Variant[_, _] => v.cases.size
        }
        assertTrue(caseCount == 3)
      }
    )
  )
}
