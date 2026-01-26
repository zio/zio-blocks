package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests for Scala 3 enum to structural union type conversion.
 *
 * Per issue #517: Enums convert to union types with Tag discriminators.
 * Example: enum Color { Red, Green, Blue } → Schema[{type Tag = "Red"} | {type
 * Tag = "Green"} | {type Tag = "Blue"}]
 */
object EnumToUnionSpec extends ZIOSpecDefault {

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
    suite("Structural Conversion")(
      test("simple enum converts to structural union") {
        val schema     = Schema.derived[Color]
        val structural = schema.structural
        assertTrue(structural != null)
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
      test("structural enum has exact union type name") {
        val schema     = Schema.derived[Color]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        assertTrue(typeName == """{Tag:"Red"}|{Tag:"Green"}|{Tag:"Blue"}""")
      },
      test("parameterized enum structural preserves case fields") {
        val schema     = Schema.derived[Shape]
        val structural = schema.structural

        val circleFields = (structural.reflect: @unchecked) match {
          case v: Reflect.Variant[_, _] =>
            v.cases.find(_.name == "Circle").flatMap { c =>
              (c.value: @unchecked) match {
                case r: Reflect.Record[_, _] => Some(r.fields.map(_.name).toSet)
                case _                       => None
              }
            }
          case _ => None
        }
        assertTrue(
          circleFields.isDefined,
          circleFields.get.contains("radius")
        )
      }
    ),
    suite("Structural Schema Behavior")(
      test("structural enum encodes via DynamicValue") {
        val schema       = Schema.derived[Color]
        val structural   = schema.structural
        val value: Color = Color.Red

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val dynamic       = structuralAny.toDynamicValue(value)

        assertTrue(dynamic match {
          case DynamicValue.Variant("Red", _) => true
          case _                              => false
        })
      },
      test("structural enum decodes from DynamicValue") {
        val schema     = Schema.derived[Color]
        val structural = schema.structural

        val dynamic = DynamicValue.Variant("Green", DynamicValue.Record(Vector.empty))

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val result        = structuralAny.fromDynamicValue(dynamic)

        assertTrue(result.isRight)
      },
      test("structural enum round-trips through DynamicValue") {
        val schema       = Schema.derived[Shape]
        val structural   = schema.structural
        val value: Shape = Shape.Circle(5.0)

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val dynamic       = structuralAny.toDynamicValue(value)
        val result        = structuralAny.fromDynamicValue(dynamic)

        assertTrue(result match {
          case Right(recovered) =>
            val shape = recovered.asInstanceOf[Shape]
            shape == value
          case _ => false
        })
      },
      test("all structural enum cases round-trip correctly") {
        val schema     = Schema.derived[Color]
        val structural = schema.structural

        val values = List(Color.Red, Color.Green, Color.Blue)

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val results       = values.map { value =>
          val dynamic = structuralAny.toDynamicValue(value)
          structuralAny.fromDynamicValue(dynamic).map(_.asInstanceOf[Color])
        }

        assertTrue(results == values.map(Right(_)))
      },
      test("parameterized enum structural preserves field data") {
        val schema       = Schema.derived[Shape]
        val structural   = schema.structural
        val value: Shape = Shape.Rectangle(10.0, 20.0)

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val dynamic       = structuralAny.toDynamicValue(value)

        val hasCorrectData = dynamic match {
          case DynamicValue.Variant("Rectangle", DynamicValue.Record(fields)) =>
            val fieldMap = fields.toMap
            fieldMap.get("width").contains(DynamicValue.Primitive(PrimitiveValue.Double(10.0))) &&
            fieldMap.get("height").contains(DynamicValue.Primitive(PrimitiveValue.Double(20.0)))
          case _ => false
        }
        assertTrue(hasCorrectData)
      }
    )
  )
}
