package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

object StructuralBindingSpec extends ZIOSpecDefault {

  case class Point(x: Int, y: Int)
  case class Person(name: String, age: Int)
  case class Triple(a: String, b: Int, c: Boolean)

  sealed trait Color
  case object Red   extends Color
  case object Green extends Color
  case object Blue  extends Color

  sealed trait Shape
  case class Circle(radius: Double)              extends Shape
  case class Rectangle(width: Double, h: Double) extends Shape

  def spec = suite("StructuralBindingSpec")(
    suite("Product structural schema encoding")(
      test("structural schema encodes via toDynamicValue") {
        val schema     = Schema.derived[Point]
        val structural = schema.structural
        val point      = Point(10, 20)

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val dynamic       = structuralAny.toDynamicValue(point)

        assertTrue(dynamic match {
          case DynamicValue.Record(fields) =>
            val map = fields.toMap
            map.get("x").contains(DynamicValue.Primitive(PrimitiveValue.Int(10))) &&
            map.get("y").contains(DynamicValue.Primitive(PrimitiveValue.Int(20)))
          case _ => false
        })
      },
      test("structural schema decodes via fromDynamicValue") {
        val schema     = Schema.derived[Person]
        val structural = schema.structural

        val dynamic = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val result        = structuralAny.fromDynamicValue(dynamic)

        assertTrue(result match {
          case Right(value) =>
            val p = value.asInstanceOf[Person]
            p.name == "Alice" && p.age == 30
          case _ => false
        })
      },
      test("structural schema round-trips through DynamicValue") {
        val schema     = Schema.derived[Triple]
        val structural = schema.structural
        val triple     = Triple("test", 42, true)

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val dynamic       = structuralAny.toDynamicValue(triple)
        val result        = structuralAny.fromDynamicValue(dynamic)

        assertTrue(result match {
          case Right(value) =>
            val t = value.asInstanceOf[Triple]
            t == triple
          case _ => false
        })
      }
    ),
    suite("Sum type structural schema encoding")(
      test("structural variant encodes case object") {
        val schema       = Schema.derived[Color]
        val structural   = schema.structural
        val color: Color = Red

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val dynamic       = structuralAny.toDynamicValue(color)

        assertTrue(dynamic match {
          case DynamicValue.Variant("Red", _) => true
          case _                              => false
        })
      },
      test("structural variant encodes case class") {
        val schema       = Schema.derived[Shape]
        val structural   = schema.structural
        val shape: Shape = Circle(5.0)

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val dynamic       = structuralAny.toDynamicValue(shape)

        assertTrue(dynamic match {
          case DynamicValue.Variant("Circle", DynamicValue.Record(fields)) =>
            fields.toMap.get("radius").contains(DynamicValue.Primitive(PrimitiveValue.Double(5.0)))
          case _ => false
        })
      },
      test("structural variant decodes case class") {
        val schema     = Schema.derived[Shape]
        val structural = schema.structural

        val dynamic = DynamicValue.Variant(
          "Rectangle",
          DynamicValue.Record(
            Vector(
              "width" -> DynamicValue.Primitive(PrimitiveValue.Double(10.0)),
              "h"     -> DynamicValue.Primitive(PrimitiveValue.Double(20.0))
            )
          )
        )

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val result        = structuralAny.fromDynamicValue(dynamic)

        assertTrue(result match {
          case Right(value) =>
            val s = value.asInstanceOf[Shape]
            s == Rectangle(10.0, 20.0)
          case _ => false
        })
      },
      test("structural variant round-trips all cases") {
        val schema     = Schema.derived[Color]
        val structural = schema.structural

        val structuralAny = structural.asInstanceOf[Schema[Any]]

        def roundTrip(c: Color): Boolean = {
          val dynamic = structuralAny.toDynamicValue(c)
          structuralAny.fromDynamicValue(dynamic) match {
            case Right(value) => value.asInstanceOf[Color] == c
            case _            => false
          }
        }

        assertTrue(
          roundTrip(Red),
          roundTrip(Green),
          roundTrip(Blue)
        )
      }
    ),
    suite("Tuple structural schema encoding")(
      test("tuple structural schema encodes") {
        val schema     = Schema.derived[(String, Int)]
        val structural = schema.structural
        val tuple      = ("hello", 42)

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val dynamic       = structuralAny.toDynamicValue(tuple)

        assertTrue(dynamic match {
          case DynamicValue.Record(fields) =>
            val map = fields.toMap
            map.get("_1").contains(DynamicValue.Primitive(PrimitiveValue.String("hello"))) &&
            map.get("_2").contains(DynamicValue.Primitive(PrimitiveValue.Int(42)))
          case _ => false
        })
      },
      test("tuple structural schema decodes") {
        val schema     = Schema.derived[(String, Int)]
        val structural = schema.structural

        val dynamic = DynamicValue.Record(
          Vector(
            "_1" -> DynamicValue.Primitive(PrimitiveValue.String("world")),
            "_2" -> DynamicValue.Primitive(PrimitiveValue.Int(100))
          )
        )

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val result        = structuralAny.fromDynamicValue(dynamic)

        assertTrue(result match {
          case Right(value) =>
            val t = value.asInstanceOf[(String, Int)]
            t == ("world", 100)
          case _ => false
        })
      },
      test("tuple3 structural schema round-trips") {
        val schema     = Schema.derived[(Int, String, Boolean)]
        val structural = schema.structural
        val tuple      = (1, "two", true)

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val dynamic       = structuralAny.toDynamicValue(tuple)
        val result        = structuralAny.fromDynamicValue(dynamic)

        assertTrue(result match {
          case Right(value) =>
            val t = value.asInstanceOf[(Int, String, Boolean)]
            t == tuple
          case _ => false
        })
      }
    )
  )
}
