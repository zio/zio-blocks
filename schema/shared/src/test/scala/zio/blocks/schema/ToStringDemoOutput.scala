package zio.blocks.schema

import zio.blocks.schema.binding.Binding
import zio.blocks.schema.patch.DynamicPatch
import zio.test._

object ToStringDemoOutput extends ZIOSpecDefault {

  case class Point(x: Int, y: Int)
  object Point {
    implicit val schema: Schema[Point] = Schema.derived
  }

  case class Address(street: String, city: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class Person(name: String, age: Int, address: Address)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  def spec = suite("Demo Output")(
    test("print toString examples") {
      println("\n" + "=" * 60)
      println("ZIO Schema toString Implementations Demo")
      println("=" * 60)

      println("\n1. TypeName - Valid Scala type syntax")
      println("-" * 40)
      println(s"TypeName.int         → ${TypeName.int}")
      println(s"TypeName.string      → ${TypeName.string}")
      println(s"Option[String]       → ${TypeName.option(TypeName.string)}")
      println(s"Map[String, Int]     → ${TypeName.map(TypeName.string, TypeName.int)}")
      println(s"List[Option[Int]]    → ${TypeName.list(TypeName.option(TypeName.int))}")

      println("\n2. DynamicOptic - Path syntax")
      println("-" * 40)
      println(s".name                → ${DynamicOptic.root.field("name")}")
      println(s".address.street      → ${DynamicOptic.root.field("address").field("street")}")
      println(s"[0]                  → ${DynamicOptic.root.at(0)}")
      println(s"[*]                  → ${DynamicOptic.elements}")
      println(s".users[*].email      → ${DynamicOptic.root.field("users").elements.field("email")}")

      println("\n3. DynamicValue - EJSON format")
      println("-" * 40)
      val stringVal = DynamicValue.Primitive(PrimitiveValue.String("hello"))
      println(s"String               → $stringVal")

      val intVal = DynamicValue.Primitive(PrimitiveValue.Int(42))
      println(s"Int                  → $intVal")

      val record = DynamicValue.Record(Vector(
        "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
        "age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))
      ))
      println(s"Record               → $record")

      val seq = DynamicValue.Sequence(Vector(
        DynamicValue.Primitive(PrimitiveValue.Int(1)),
        DynamicValue.Primitive(PrimitiveValue.Int(2)),
        DynamicValue.Primitive(PrimitiveValue.Int(3))
      ))
      println(s"Sequence             → $seq")

      val variant = DynamicValue.Variant("Some", DynamicValue.Record(Vector(
        "value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
      )))
      println(s"Variant              → $variant")

      println("\n4. Schema - Full structure")
      println("-" * 40)
      println(s"Schema[Int]          → ${Schema[Int]}")
      println(s"Schema[Point]        → ${Schema[Point]}")

      println("\n5. Reflect - SDL format")
      println("-" * 40)
      println(s"Reflect.int          → ${Reflect.int[Binding]}")
      println(s"Schema[Point].reflect:")
      println(Schema[Point].reflect.toString.split("\n").map("  " + _).mkString("\n"))

      println("\n6. DynamicPatch - Diff format")
      println("-" * 40)
      val patch = DynamicPatch(
        DynamicOptic.root.field("name"),
        DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("John")))
      )
      println(s"Set operation        → $patch")

      println("\n" + "=" * 60)
      assertTrue(true)
    }
  )
}
