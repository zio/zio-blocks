package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests for Scala 3 enum structural conversion (JVM only, requires
 * ToStructural).
 */
object EnumStructuralSpec extends ZIOSpecDefault {

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
        val schema     = Schema.derived[Color]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        assertTrue(
          typeName.contains("Blue") || typeName.contains("Red") || typeName.contains("Green"),
          typeName.contains("|") || typeName.contains("Tag")
        )
      },
      test("parameterized enum converts to structural union type") {
        val schema     = Schema.derived[Shape]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        assertTrue(
          typeName.contains("Circle") || typeName.contains("Rectangle") || typeName.contains("Triangle"),
          typeName.contains("radius") || typeName.contains("width") || typeName.contains("base")
        )
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
