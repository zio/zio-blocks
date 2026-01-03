package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests for Scala 3 enum schema derivation and structural conversion (Scala 3
 * only).
 *
 * Scala 3 enums are a more concise way to define sum types.
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
    suite("Schema Derivation")(
      test("simple enum derives schema") {
        val schema = Schema.derived[Color]
        assertTrue(schema != null)
      },
      test("enum with more cases derives schema") {
        val schema = Schema.derived[Status]
        assertTrue(schema != null)
      },
      test("enum with parameters derives schema") {
        val schema = Schema.derived[Shape]
        assertTrue(schema != null)
      }
    ),
    suite("Schema Structure")(
      test("simple enum schema is a Variant") {
        val schema    = Schema.derived[Color]
        val isVariant = schema.reflect match {
          case _: Reflect.Variant[_, _] => true
          case _                        => false
        }
        assertTrue(isVariant)
      },
      test("simple enum has correct number of cases") {
        val schema    = Schema.derived[Color]
        val caseCount = schema.reflect match {
          case v: Reflect.Variant[_, _] => v.cases.size
          case _                        => -1
        }
        assertTrue(caseCount == 3)
      },
      test("simple enum case names are correct") {
        val schema    = Schema.derived[Color]
        val caseNames = schema.reflect match {
          case v: Reflect.Variant[_, _] => v.cases.map(_.name).toSet
          case _                        => Set.empty[String]
        }
        assertTrue(
          caseNames.contains("Red"),
          caseNames.contains("Green"),
          caseNames.contains("Blue")
        )
      },
      test("parameterized enum cases have correct fields") {
        val schema       = Schema.derived[Shape]
        val circleFields = schema.reflect match {
          case v: Reflect.Variant[_, _] =>
            v.cases.find(_.name == "Circle").flatMap { c =>
              c.value match {
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
    suite("DynamicValue Round-Trip")(
      test("simple enum case round-trips correctly") {
        val schema       = Schema.derived[Color]
        val value: Color = Color.Red

        val dynamic = schema.toDynamicValue(value)
        val result  = schema.fromDynamicValue(dynamic)

        assertTrue(result == Right(value))
      },
      test("all simple enum cases round-trip correctly") {
        val schema = Schema.derived[Color]

        val redResult   = schema.fromDynamicValue(schema.toDynamicValue(Color.Red))
        val greenResult = schema.fromDynamicValue(schema.toDynamicValue(Color.Green))
        val blueResult  = schema.fromDynamicValue(schema.toDynamicValue(Color.Blue))

        assertTrue(
          redResult == Right(Color.Red),
          greenResult == Right(Color.Green),
          blueResult == Right(Color.Blue)
        )
      },
      test("parameterized enum case round-trips correctly") {
        val schema        = Schema.derived[Shape]
        val circle: Shape = Shape.Circle(5.0)

        val dynamic = schema.toDynamicValue(circle)
        val result  = schema.fromDynamicValue(dynamic)

        assertTrue(result == Right(circle))
      },
      test("all parameterized enum cases round-trip correctly") {
        val schema = Schema.derived[Shape]

        val circle: Shape = Shape.Circle(3.14)
        val rect: Shape   = Shape.Rectangle(10.0, 20.0)
        val tri: Shape    = Shape.Triangle(5.0, 8.0)

        val circleResult = schema.fromDynamicValue(schema.toDynamicValue(circle))
        val rectResult   = schema.fromDynamicValue(schema.toDynamicValue(rect))
        val triResult    = schema.fromDynamicValue(schema.toDynamicValue(tri))

        assertTrue(
          circleResult == Right(circle),
          rectResult == Right(rect),
          triResult == Right(tri)
        )
      }
    ),
    suite("DynamicValue Structure")(
      test("simple enum produces Variant DynamicValue") {
        val schema       = Schema.derived[Color]
        val value: Color = Color.Green

        val dynamic = schema.toDynamicValue(value)

        val isVariant = dynamic match {
          case DynamicValue.Variant(_, _) => true
          case _                          => false
        }
        assertTrue(isVariant)
      },
      test("Variant DynamicValue has correct case name") {
        val schema       = Schema.derived[Color]
        val value: Color = Color.Blue

        val dynamic = schema.toDynamicValue(value)

        val caseName = dynamic match {
          case DynamicValue.Variant(name, _) => Some(name)
          case _                             => None
        }
        assertTrue(caseName == Some("Blue"))
      },
      test("parameterized enum Variant contains case data") {
        val schema       = Schema.derived[Shape]
        val value: Shape = Shape.Rectangle(10.0, 20.0)

        val dynamic = schema.toDynamicValue(value)

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
