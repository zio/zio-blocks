package zio.blocks.schema

import zio.blocks.schema.binding.Binding
import zio.blocks.schema.patch.DynamicPatch
import zio.test._
import zio.test.Assertion._

object ToStringSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("ToStringSpec")(
    typeNameSpec,
    namespaceSpec,
    dynamicOpticSpec,
    dynamicValueSpec,
    reflectSpec,
    schemaSpec,
    dynamicPatchSpec,
    termSpec
  )

  // ================== TypeName tests ==================

  val typeNameSpec: Spec[TestEnvironment, Any] = suite("TypeName toString")(
    test("renders simple primitive types") {
      assert(TypeName.int.toString)(equalTo("scala.Int")) &&
      assert(TypeName.string.toString)(equalTo("scala.String")) &&
      assert(TypeName.boolean.toString)(equalTo("scala.Boolean")) &&
      assert(TypeName.double.toString)(equalTo("scala.Double")) &&
      assert(TypeName.long.toString)(equalTo("scala.Long")) &&
      assert(TypeName.unit.toString)(equalTo("scala.Unit"))
    },
    test("renders parameterized types with single type parameter") {
      assert(TypeName.option(TypeName.string).toString)(equalTo("scala.Option[scala.String]")) &&
      assert(TypeName.list(TypeName.int).toString)(equalTo("scala.collection.immutable.List[scala.Int]")) &&
      assert(TypeName.vector(TypeName.double).toString)(equalTo("scala.collection.immutable.Vector[scala.Double]")) &&
      assert(TypeName.set(TypeName.string).toString)(equalTo("scala.collection.immutable.Set[scala.String]"))
    },
    test("renders parameterized types with multiple type parameters") {
      assert(TypeName.map(TypeName.string, TypeName.int).toString)(
        equalTo("scala.collection.immutable.Map[scala.String, scala.Int]")
      )
    },
    test("renders nested parameterized types") {
      assert(TypeName.option(TypeName.list(TypeName.int)).toString)(
        equalTo("scala.Option[scala.collection.immutable.List[scala.Int]]")
      ) &&
      assert(TypeName.list(TypeName.option(TypeName.string)).toString)(
        equalTo("scala.collection.immutable.List[scala.Option[scala.String]]")
      ) &&
      assert(TypeName.map(TypeName.string, TypeName.list(TypeName.int)).toString)(
        equalTo("scala.collection.immutable.Map[scala.String, scala.collection.immutable.List[scala.Int]]")
      )
    },
    test("renders java.time types") {
      assert(TypeName.instant.toString)(equalTo("java.time.Instant")) &&
      assert(TypeName.localDate.toString)(equalTo("java.time.LocalDate")) &&
      assert(TypeName.duration.toString)(equalTo("java.time.Duration")) &&
      assert(TypeName.period.toString)(equalTo("java.time.Period"))
    },
    test("renders java.util types") {
      assert(TypeName.uuid.toString)(equalTo("java.util.UUID")) &&
      assert(TypeName.currency.toString)(equalTo("java.util.Currency"))
    }
  )

  // ================== Namespace tests ==================

  val namespaceSpec: Spec[TestEnvironment, Any] = suite("Namespace toString")(
    test("renders scala namespace") {
      assert(Namespace.scala.toString)(equalTo("scala"))
    },
    test("renders nested namespaces") {
      assert(Namespace.scalaCollectionImmutable.toString)(equalTo("scala.collection.immutable"))
    },
    test("renders java namespaces") {
      assert(Namespace.javaTime.toString)(equalTo("java.time")) &&
      assert(Namespace.javaUtil.toString)(equalTo("java.util"))
    }
  )

  // ================== DynamicOptic tests ==================

  val dynamicOpticSpec: Spec[TestEnvironment, Any] = suite("DynamicOptic toString")(
    test("renders field access") {
      assert(DynamicOptic.root.field("name").toString)(equalTo(".name")) &&
      assert(DynamicOptic.root.field("address").field("street").toString)(equalTo(".address.street"))
    },
    test("renders case/variant selection") {
      assert(DynamicOptic.root.caseOf("Some").toString)(equalTo("<Some>")) &&
      assert(DynamicOptic.root.caseOf("X").field("y").toString)(equalTo("<X>.y"))
    },
    test("renders index access") {
      assert(DynamicOptic.root.at(0).toString)(equalTo("[0]")) &&
      assert(DynamicOptic.root.at(0).at(1).toString)(equalTo("[0][1]"))
    },
    test("renders multiple indices") {
      assert(DynamicOptic.root.atIndices(0, 1, 2).toString)(equalTo("[0,1,2]"))
    },
    test("renders map key access") {
      assert(DynamicOptic.root.atKey("host").toString)(equalTo("{\"host\"}")) &&
      assert(DynamicOptic.root.atKeys("x", "y", "z").toString)(equalTo("{\"x\", \"y\", \"z\"}"))
    },
    test("renders collection traversals") {
      assert(DynamicOptic.elements.toString)(equalTo("[*]")) &&
      assert(DynamicOptic.mapKeys.toString)(equalTo("{*:}")) &&
      assert(DynamicOptic.mapValues.toString)(equalTo("{*}"))
    },
    test("renders wrapper unwrapping") {
      assert(DynamicOptic.wrapped.toString)(equalTo(".~"))
    },
    test("renders complex composed paths") {
      assert(DynamicOptic.root.field("users").elements.field("email").toString)(
        equalTo(".users[*].email")
      ) &&
      assert(DynamicOptic.root.caseOf("Success").field("data").toString)(
        equalTo("<Success>.data")
      )
    }
  )

  // ================== DynamicValue tests (EJSON format) ==================

  val dynamicValueSpec: Spec[TestEnvironment, Any] = suite("DynamicValue toString (EJSON)")(
    test("renders primitive strings") {
      val value = DynamicValue.Primitive(PrimitiveValue.String("hello"))
      assert(value.toString)(equalTo("\"hello\""))
    },
    test("renders primitive numbers") {
      assert(DynamicValue.Primitive(PrimitiveValue.Int(42)).toString)(equalTo("42")) &&
      assert(DynamicValue.Primitive(PrimitiveValue.Long(123L)).toString)(equalTo("123")) &&
      assert(DynamicValue.Primitive(PrimitiveValue.Double(3.14)).toString)(equalTo("3.14"))
    },
    test("renders primitive booleans") {
      assert(DynamicValue.Primitive(PrimitiveValue.Boolean(true)).toString)(equalTo("true")) &&
      assert(DynamicValue.Primitive(PrimitiveValue.Boolean(false)).toString)(equalTo("false"))
    },
    test("renders null for Unit") {
      assert(DynamicValue.Primitive(PrimitiveValue.Unit).toString)(equalTo("null"))
    },
    test("renders typed primitives with metadata") {
      val instant = java.time.Instant.ofEpochMilli(1705312800000L)
      val instantValue = DynamicValue.Primitive(PrimitiveValue.Instant(instant))
      val bigIntValue = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(12345)))
      assert(instantValue.toString)(containsString("@ {type: \"instant\"}")) &&
      assert(bigIntValue.toString)(equalTo("12345 @ {type: \"bigint\"}"))
    },
    test("renders records with unquoted keys") {
      val record = DynamicValue.Record(Vector(
        "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
        "age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))
      ))
      assert(record.toString)(equalTo("{ name: \"John\", age: 30 }"))
    },
    test("renders empty records") {
      val emptyRecord = DynamicValue.Record(Vector.empty)
      assert(emptyRecord.toString)(equalTo("{  }"))
    },
    test("renders variants with tag metadata") {
      val noneVariant = DynamicValue.Variant("None", DynamicValue.Record(Vector.empty))
      val someVariant = DynamicValue.Variant("Some", DynamicValue.Record(Vector(
        "value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
      )))
      assert(noneVariant.toString)(equalTo("{  } @ {tag: \"None\"}")) &&
      assert(someVariant.toString)(equalTo("{ value: 42 } @ {tag: \"Some\"}"))
    },
    test("renders sequences") {
      val seq = DynamicValue.Sequence(Vector(
        DynamicValue.Primitive(PrimitiveValue.Int(1)),
        DynamicValue.Primitive(PrimitiveValue.Int(2)),
        DynamicValue.Primitive(PrimitiveValue.Int(3))
      ))
      assert(seq.toString)(equalTo("[1, 2, 3]"))
    },
    test("renders empty sequences") {
      val emptySeq = DynamicValue.Sequence(Vector.empty)
      assert(emptySeq.toString)(equalTo("[]"))
    },
    test("renders maps") {
      val map = DynamicValue.Map(Vector(
        DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
        DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
      ))
      assert(map.toString)(equalTo("{ \"a\": 1, \"b\": 2 }"))
    },
    test("renders maps with non-string keys") {
      val map = DynamicValue.Map(Vector(
        DynamicValue.Primitive(PrimitiveValue.Int(1)) -> DynamicValue.Primitive(PrimitiveValue.String("one")),
        DynamicValue.Primitive(PrimitiveValue.Int(2)) -> DynamicValue.Primitive(PrimitiveValue.String("two"))
      ))
      assert(map.toString)(equalTo("{ 1: \"one\", 2: \"two\" }"))
    },
    test("escapes special characters in strings") {
      val value = DynamicValue.Primitive(PrimitiveValue.String("hello\nworld\t\"quoted\""))
      assert(value.toString)(equalTo("\"hello\\nworld\\t\\\"quoted\\\"\""))
    },
    test("renders nested structures") {
      val nested = DynamicValue.Record(Vector(
        "user" -> DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
          "scores" -> DynamicValue.Sequence(Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(95)),
            DynamicValue.Primitive(PrimitiveValue.Int(87))
          ))
        ))
      ))
      assert(nested.toString)(equalTo("{ user: { name: \"Alice\", scores: [95, 87] } }"))
    }
  )

  // ================== Reflect tests (SDL format) ==================

  // Test case classes for Reflect tests
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

  sealed trait Status
  case object Active extends Status
  case class Inactive(reason: String) extends Status
  object Status {
    implicit val schema: Schema[Status] = Schema.derived
  }

  val reflectSpec: Spec[TestEnvironment, Any] = suite("Reflect toString (SDL)")(
    test("renders primitive types") {
      val intReflect = Reflect.int[Binding]
      val stringReflect = Reflect.string[Binding]
      assert(intReflect.toString)(equalTo("Int")) &&
      assert(stringReflect.toString)(equalTo("String"))
    },
    test("renders simple records") {
      val reflect = Schema[Point].reflect
      assert(reflect.toString)(containsString("record Point")) &&
      assert(reflect.toString)(containsString("x: Int")) &&
      assert(reflect.toString)(containsString("y: Int"))
    },
    test("renders nested records fully expanded") {
      val reflect = Schema[Person].reflect
      // Person should be fully expanded with Address inlined
      assert(reflect.toString)(containsString("record Person")) &&
      assert(reflect.toString)(containsString("name: String")) &&
      assert(reflect.toString)(containsString("age: Int")) &&
      assert(reflect.toString)(containsString("address:"))
    },
    test("renders variants with cases") {
      val reflect = Schema[Status].reflect
      assert(reflect.toString)(containsString("variant zio.blocks.schema.ToStringSpec.Status")) &&
      assert(reflect.toString)(containsString("Active")) &&
      assert(reflect.toString)(containsString("Inactive"))
    },
    test("renders sequences") {
      val reflect = Schema[List[Int]].reflect
      assert(reflect.toString)(containsString("sequence")) &&
      assert(reflect.toString)(containsString("Int"))
    },
    test("renders maps") {
      val reflect = Schema[Map[String, Int]].reflect
      assert(reflect.toString)(containsString("map")) &&
      assert(reflect.toString)(containsString("String")) &&
      assert(reflect.toString)(containsString("Int"))
    },
    test("renders Option as variant") {
      val reflect = Schema[Option[Int]].reflect
      assert(reflect.toString)(containsString("variant")) &&
      assert(reflect.toString)(containsString("Some")) &&
      assert(reflect.toString)(containsString("None"))
    }
  )

  // ================== Schema tests ==================

  val schemaSpec: Spec[TestEnvironment, Any] = suite("Schema toString")(
    test("renders simple schema") {
      val schema = Schema[Int]
      assert(schema.toString)(equalTo("Schema { Int }"))
    },
    test("renders complex schema with full structure") {
      val schema = Schema[Point]
      assert(schema.toString)(containsString("Schema {")) &&
      assert(schema.toString)(containsString("record Point"))
    }
  )

  // ================== DynamicPatch tests ==================

  val dynamicPatchSpec: Spec[TestEnvironment, Any] = suite("DynamicPatch toString")(
    test("renders empty patch") {
      val patch = DynamicPatch.empty
      assert(patch.toString)(equalTo("DynamicPatch {}"))
    },
    test("renders set operation") {
      val patch = DynamicPatch(
        DynamicOptic.root.field("name"),
        DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("John")))
      )
      assert(patch.toString)(containsString(".name ="))
    },
    test("renders primitive delta") {
      val patch = DynamicPatch(
        DynamicOptic.root.field("age"),
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.LongDelta(5L))
      )
      assert(patch.toString)(containsString(".age"))
    },
    test("renders sequence insert") {
      val patch = DynamicPatch(
        DynamicOptic.root.field("items"),
        DynamicPatch.Operation.SequenceEdit(Vector(
          DynamicPatch.SeqOp.Insert(0, Vector(DynamicValue.Primitive(PrimitiveValue.String("new item"))))
        ))
      )
      assert(patch.toString)(containsString(".items"))
    },
    test("renders sequence delete") {
      val patch = DynamicPatch(
        DynamicOptic.root.field("items"),
        DynamicPatch.Operation.SequenceEdit(Vector(
          DynamicPatch.SeqOp.Delete(0, 1)
        ))
      )
      assert(patch.toString)(containsString(".items"))
    },
    test("renders map add") {
      val patch = DynamicPatch(
        DynamicOptic.root.field("config"),
        DynamicPatch.Operation.MapEdit(Vector(
          DynamicPatch.MapOp.Add(
            DynamicValue.Primitive(PrimitiveValue.String("key")),
            DynamicValue.Primitive(PrimitiveValue.String("value"))
          )
        ))
      )
      assert(patch.toString)(containsString(".config"))
    },
    test("renders map remove") {
      val patch = DynamicPatch(
        DynamicOptic.root.field("config"),
        DynamicPatch.Operation.MapEdit(Vector(
          DynamicPatch.MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.String("oldKey")))
        ))
      )
      assert(patch.toString)(containsString(".config"))
    },
    test("renders multi-operation patch") {
      val patch = DynamicPatch(Vector(
        DynamicPatch.DynamicPatchOp(
          DynamicOptic.root.field("name"),
          DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("John")))
        ),
        DynamicPatch.DynamicPatchOp(
          DynamicOptic.root.field("age"),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
        )
      ))
      assert(patch.toString)(containsString("DynamicPatch")) &&
      assert(patch.toString)(containsString(".name")) &&
      assert(patch.toString)(containsString(".age"))
    }
  )

  // ================== Term tests ==================

  val termSpec: Spec[TestEnvironment, Any] = suite("Term toString")(
    test("renders simple term") {
      val reflect = Schema[Point].reflect
      reflect match {
        case r: Reflect.Record[Binding, Point] =>
          val nameTerm = r.fields.find(_.name == "x").get
          assert(nameTerm.toString)(containsString("x:")) &&
          assert(nameTerm.toString)(containsString("Int"))
        case _ => assertTrue(false)
      }
    }
  )
}
