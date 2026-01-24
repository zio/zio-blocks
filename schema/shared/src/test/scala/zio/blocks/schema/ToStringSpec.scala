package zio.blocks.schema

import zio.test._
import zio.blocks.schema.binding.Binding
import zio.blocks.schema.patch.DynamicPatch

object ToStringSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
  object Person extends CompanionOptics[Person] {
    implicit val schema: Schema[Person] = Schema.derived
    val name: Lens[Person, String]      = $(_.name)
    val age: Lens[Person, Int]          = $(_.age)
  }

  case class Address(street: String, city: String)
  object Address extends CompanionOptics[Address] {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class Company(name: String, address: Address)
  object Company extends CompanionOptics[Company] {
    implicit val schema: Schema[Company] = Schema.derived
    val address: Lens[Company, Address]  = $(_.address)
    val street: Lens[Company, String]    = $(_.address.street)
  }

  sealed trait Shape
  case class Circle(radius: Double)             extends Shape
  case class Rectangle(width: Int, height: Int) extends Shape
  object Shape                                  extends CompanionOptics[Shape] {
    implicit val schema: Schema[Shape]        = Schema.derived
    val circle: Prism[Shape, Circle]          = $(_.when[Circle])
    val rectangle: Prism[Shape, Rectangle]    = $(_.when[Rectangle])
    val circleRadius: Optional[Shape, Double] = $(_.when[Circle].radius)
  }

  object Circle extends CompanionOptics[Circle] {
    implicit val schema: Schema[Circle] = Schema.derived
    val radius: Lens[Circle, Double]    = $(_.radius)
  }

  def spec: Spec[TestEnvironment, Any] = suite("ToStringSpec")(
    typeNameSuite,
    dynamicValueSuite,
    reflectSuite,
    schemaSuite,
    opticSuite,
    dynamicOpticSuite,
    dynamicPatchSuite
  )

  val typeNameSuite: Spec[Any, Nothing] = suite("TypeName.toString")(
    test("renders simple types") {
      val tn = TypeName(Namespace(List("scala")), "Int")
      assertTrue(tn.toString == "scala.Int")
    },
    test("renders generic types with type parameters") {
      val inner = TypeName(Namespace(List("scala")), "Int")
      val tn    = TypeName(Namespace(List("scala")), "Option", Vector(inner))
      assertTrue(tn.toString == "scala.Option[scala.Int]")
    },
    test("renders nested generic types") {
      val int    = TypeName(Namespace(List("scala")), "Int")
      val string = TypeName(Namespace(List("scala")), "String")
      val map    = TypeName(Namespace(List("scala", "collection", "immutable")), "Map", Vector(string, int))
      assertTrue(map.toString == "scala.collection.immutable.Map[scala.String, scala.Int]")
    },
    test("renders types with empty namespace") {
      val tn = TypeName(Namespace(Nil), "MyType")
      assertTrue(tn.toString == "MyType")
    },
    test("renders deeply nested generics") {
      val int    = TypeName(Namespace(List("scala")), "Int")
      val list   = TypeName(Namespace(List("scala")), "List", Vector(int))
      val option = TypeName(Namespace(List("scala")), "Option", Vector(list))
      assertTrue(option.toString == "scala.Option[scala.List[scala.Int]]")
    }
  )

  val dynamicValueSuite: Spec[Any, Nothing] = suite("DynamicValue.toString (EJSON format)")(
    test("renders primitive Int") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Int(42))
      assertTrue(dv.toString == "42")
    },
    test("renders primitive String with quotes") {
      val dv = DynamicValue.Primitive(PrimitiveValue.String("hello"))
      assertTrue(dv.toString == "\"hello\"")
    },
    test("renders primitive Boolean") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Boolean(true))
      assertTrue(dv.toString == "true")
    },
    test("renders Unit as ()") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Unit)
      assertTrue(dv.toString == "()")
    },
    test("renders Record with unquoted keys") {
      val dv = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(42))
        )
      )
      assertTrue(dv.toString == "{ name: \"John\", age: 42 }")
    },
    test("renders empty Record") {
      val dv = DynamicValue.Record(Vector.empty)
      assertTrue(dv.toString == "{  }")
    },
    test("renders Sequence") {
      val dv = DynamicValue.Sequence(
        Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
      )
      assertTrue(dv.toString == "[1, 2, 3]")
    },
    test("renders empty Sequence") {
      val dv = DynamicValue.Sequence(Vector.empty)
      assertTrue(dv.toString == "[]")
    },
    test("renders Map with string keys (quoted)") {
      val dv = DynamicValue.Map(
        Vector(
          DynamicValue.Primitive(PrimitiveValue.String("key1")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.String("key2")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
      )
      assertTrue(dv.toString == "{ \"key1\": 1, \"key2\": 2 }")
    },
    test("renders Map with non-string keys (unquoted)") {
      val dv = DynamicValue.Map(
        Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)) -> DynamicValue.Primitive(PrimitiveValue.String("one")),
          DynamicValue.Primitive(PrimitiveValue.Int(2)) -> DynamicValue.Primitive(PrimitiveValue.String("two"))
        )
      )
      assertTrue(dv.toString == "{ 1: \"one\", 2: \"two\" }")
    },
    test("renders Variant with tag annotation") {
      val dv = DynamicValue.Variant(
        "Some",
        DynamicValue.Record(Vector("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
      )
      assertTrue(dv.toString == "{ value: 42 } @ {tag: \"Some\"}")
    },
    test("escapes special characters in strings") {
      val dv = DynamicValue.Primitive(PrimitiveValue.String("hello\nworld\t\"quoted\""))
      assertTrue(dv.toString == "\"hello\\nworld\\t\\\"quoted\\\"\"")
    },
    test("renders nested structures") {
      val inner = DynamicValue.Record(
        Vector(
          "x" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          "y" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
      )
      val outer = DynamicValue.Record(
        Vector(
          "point" -> inner,
          "label" -> DynamicValue.Primitive(PrimitiveValue.String("origin"))
        )
      )
      assertTrue(outer.toString == "{ point: { x: 1, y: 2 }, label: \"origin\" }")
    },
    test("renders deeply nested sequences") {
      val dv = DynamicValue.Sequence(
        Vector(
          DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          ),
          DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(3)),
              DynamicValue.Primitive(PrimitiveValue.Int(4))
            )
          )
        )
      )
      assertTrue(dv.toString == "[[1, 2], [3, 4]]")
    },
    test("renders temporal types as quoted strings") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Instant(java.time.Instant.parse("2024-01-15T10:30:00Z")))
      assertTrue(dv.toString == "\"2024-01-15T10:30:00Z\"")
    },
    test("renders UUID as quoted string") {
      val uuid = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
      val dv   = DynamicValue.Primitive(PrimitiveValue.UUID(uuid))
      assertTrue(dv.toString == "\"550e8400-e29b-41d4-a716-446655440000\"")
    }
  )

  val reflectSuite: Spec[Any, Nothing] = suite("Reflect.toString (SDL-style)")(
    test("renders primitive types") {
      val reflect = Reflect.int[Binding]
      assertTrue(reflect.toString == "Int")
    },
    test("renders record with fields") {
      val reflect = Schema[Person].reflect
      val str     = reflect.toString
      assertTrue(str.contains("record Person")) &&
      assertTrue(str.contains("name: String")) &&
      assertTrue(str.contains("age: Int"))
    },
    test("renders nested records") {
      val reflect = Schema[Company].reflect
      val str     = reflect.toString
      assertTrue(str.contains("record Company")) &&
      assertTrue(str.contains("address:")) &&
      assertTrue(str.contains("record Address"))
    },
    test("renders variant types") {
      val reflect = Schema[Shape].reflect
      val str     = reflect.toString
      assertTrue(str.contains("variant")) &&
      assertTrue(str.contains("Shape")) &&
      assertTrue(str.contains("Circle")) &&
      assertTrue(str.contains("Rectangle"))
    },
    test("renders sequence types") {
      val reflect = Schema[List[Int]].reflect
      val str     = reflect.toString
      assertTrue(str.contains("sequence List[Int]"))
    },
    test("renders map types") {
      val reflect = Schema[Map[String, Int]].reflect
      val str     = reflect.toString
      assertTrue(str.contains("map Map[String, Int]"))
    }
  )

  val schemaSuite: Spec[Any, Nothing] = suite("Schema.toString")(
    test("wraps Reflect output with Schema prefix") {
      val schema = Schema[Person]
      val str    = schema.toString
      assertTrue(str.startsWith("Schema { ")) &&
      assertTrue(str.endsWith(" }")) &&
      assertTrue(str.contains("record Person"))
    }
  )

  val opticSuite: Spec[Any, Nothing] = suite("Optic.toString")(
    test("Lens renders path style") {
      val lens = Person.name
      assertTrue(lens.toString == "Lens(_.name)")
    },
    test("Lens renders nested path") {
      val lens = Company.street
      assertTrue(lens.toString == "Lens(_.address.street)")
    },
    test("Prism renders when[] style") {
      val prism = Shape.circle
      assertTrue(prism.toString == "Prism(_.when[Circle])")
    },
    test("Optional combines lens and prism styles") {
      val optional = Shape.circleRadius
      val str      = optional.toString
      assertTrue(str.startsWith("Optional(_")) &&
      assertTrue(str.contains(".when[Circle]")) &&
      assertTrue(str.contains(".radius"))
    },
    test("Traversal renders .each for sequences") {
      case class Container(items: List[Int])
      object Container extends CompanionOptics[Container] {
        implicit val schema: Schema[Container] = Schema.derived
        val items: Lens[Container, List[Int]]  = $(_.items)
        val each: Traversal[Container, Int]    = $(_.items.each)
      }
      val traversal = Container.each
      assertTrue(traversal.toString.contains(".each"))
    }
  )

  val dynamicOpticSuite: Spec[Any, Nothing] = suite("DynamicOptic.toString")(
    test("renders root as dot") {
      assertTrue(DynamicOptic.root.toString == ".")
    },
    test("renders field access") {
      val optic = DynamicOptic.root.field("name")
      assertTrue(optic.toString == ".name")
    },
    test("renders case selection") {
      val optic = DynamicOptic.root.caseOf("Some")
      assertTrue(optic.toString == ".when[Some]")
    },
    test("renders index access") {
      val optic = DynamicOptic.root.at(0)
      assertTrue(optic.toString == ".at(0)")
    },
    test("renders atKey with actual key value") {
      val optic = DynamicOptic.root.atKey("myKey")
      assertTrue(optic.toString == ".atKey(\"myKey\")")
    },
    test("renders atIndices with actual indices") {
      val optic = DynamicOptic.root.atIndices(0, 1, 2)
      assertTrue(optic.toString == ".atIndices(0, 1, 2)")
    },
    test("renders atKeys with actual key values") {
      val optic = DynamicOptic.root.atKeys("a", "b", "c")
      assertTrue(optic.toString == ".atKeys(\"a\", \"b\", \"c\")")
    },
    test("renders elements traversal") {
      val optic = DynamicOptic.elements
      assertTrue(optic.toString == ".each")
    },
    test("renders mapKeys traversal") {
      val optic = DynamicOptic.mapKeys
      assertTrue(optic.toString == ".eachKey")
    },
    test("renders mapValues traversal") {
      val optic = DynamicOptic.mapValues
      assertTrue(optic.toString == ".eachValue")
    },
    test("renders wrapped") {
      val optic = DynamicOptic.wrapped
      assertTrue(optic.toString == ".wrapped")
    },
    test("renders chained operations") {
      val optic = DynamicOptic.root.field("users").at(0).field("name")
      assertTrue(optic.toString == ".users.at(0).name")
    },
    test("renders complex path with case and key") {
      val optic = DynamicOptic.root.caseOf("Some").field("value").atKey("key1")
      assertTrue(optic.toString == ".when[Some].value.atKey(\"key1\")")
    },
    test("renders atKey with integer key") {
      val optic = DynamicOptic.root.atKey(42)
      assertTrue(optic.toString == ".atKey(42)")
    }
  )

  val dynamicPatchSuite: Spec[Any, Nothing] = suite("DynamicPatch.toString")(
    test("renders empty patch") {
      val patch = DynamicPatch.empty
      assertTrue(patch.toString == "DynamicPatch {}")
    },
    test("renders Set operation") {
      val patch = DynamicPatch.root(
        DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(42)))
      )
      val str = patch.toString
      assertTrue(str.contains("DynamicPatch {")) &&
      assertTrue(str.contains("~ . = 42"))
    },
    test("renders numeric delta with + sign") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("age"),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
          )
        )
      )
      val str = patch.toString
      assertTrue(str.contains("+5"))
    },
    test("renders negative delta") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("count"),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(-3))
          )
        )
      )
      val str = patch.toString
      assertTrue(str.contains("-3"))
    },
    test("renders sequence append with + marker") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("items"),
            DynamicPatch.Operation.SequenceEdit(
              Vector(
                DynamicPatch.SeqOp.Append(Vector(DynamicValue.Primitive(PrimitiveValue.Int(99))))
              )
            )
          )
        )
      )
      val str = patch.toString
      assertTrue(str.contains("+ ")) &&
      assertTrue(str.contains("[+]"))
    },
    test("renders sequence delete with - marker") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("items"),
            DynamicPatch.Operation.SequenceEdit(
              Vector(
                DynamicPatch.SeqOp.Delete(0, 1)
              )
            )
          )
        )
      )
      val str = patch.toString
      assertTrue(str.contains("- ")) &&
      assertTrue(str.contains("[0]"))
    }
  )
}
