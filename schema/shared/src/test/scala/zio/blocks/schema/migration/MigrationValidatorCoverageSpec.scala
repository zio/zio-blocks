package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationValidatorCoverageSpec extends SchemaBaseSpec {

  case class SimpleRecord(name: String, age: Int)
  object SimpleRecord { implicit val schema: Schema[SimpleRecord] = Schema.derived }

  case class RecordWithEmail(name: String, age: Int, email: String)
  object RecordWithEmail { implicit val schema: Schema[RecordWithEmail] = Schema.derived }

  case class RecordRenamed(fullName: String, age: Int)
  object RecordRenamed { implicit val schema: Schema[RecordRenamed] = Schema.derived }

  case class RecordWithOptional(name: String, age: Int, nick: Option[String])
  object RecordWithOptional { implicit val schema: Schema[RecordWithOptional] = Schema.derived }

  case class RecordMandated(name: String, age: Int, nick: String)
  object RecordMandated { implicit val schema: Schema[RecordMandated] = Schema.derived }

  case class Nested(person: SimpleRecord)
  object Nested { implicit val schema: Schema[Nested] = Schema.derived }

  case class NestedEmail(person: RecordWithEmail)
  object NestedEmail { implicit val schema: Schema[NestedEmail] = Schema.derived }

  sealed trait Animal
  object Animal {
    case class Dog(breed: String) extends Animal
    case class Cat(color: String) extends Animal
    implicit val schema: Schema[Animal] = Schema.derived
  }

  sealed trait AnimalRenamed
  object AnimalRenamed {
    case class Hound(breed: String) extends AnimalRenamed
    case class Cat(color: String)   extends AnimalRenamed
    implicit val schema: Schema[AnimalRenamed] = Schema.derived
  }

  case class WithList(items: List[Int])
  object WithList { implicit val schema: Schema[WithList] = Schema.derived }

  case class WithMap(data: Map[String, Int])
  object WithMap { implicit val schema: Schema[WithMap] = Schema.derived }

  case class RecordAB(a: String, b: String)
  object RecordAB { implicit val schema: Schema[RecordAB] = Schema.derived }

  case class RecordC(c: String)
  object RecordC { implicit val schema: Schema[RecordC] = Schema.derived }

  case class RecordJoined(a: String, combined: String)
  object RecordJoined { implicit val schema: Schema[RecordJoined] = Schema.derived }

  private val root = DynamicOptic.root
  private val litI = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
  private val litS = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationValidatorCoverageSpec")(
    suite("extractStructure")(
      test("extracts record structure") {
        val s = MigrationValidator.extractStructure(SimpleRecord.schema)
        assertTrue(s.isInstanceOf[MigrationValidator.SchemaStructure.Record])
        s match {
          case r: MigrationValidator.SchemaStructure.Record =>
            assertTrue(r.fields.keySet == Set("name", "age"))
          case _ => assertTrue(false)
        }
      },
      test("extracts variant structure") {
        val s = MigrationValidator.extractStructure(Animal.schema)
        assertTrue(s.isInstanceOf[MigrationValidator.SchemaStructure.Variant])
        s match {
          case v: MigrationValidator.SchemaStructure.Variant =>
            assertTrue(v.cases.keySet == Set("Dog", "Cat"))
          case _ => assertTrue(false)
        }
      },
      test("extracts sequence structure") {
        val s = MigrationValidator.extractStructure(WithList.schema)
        s match {
          case r: MigrationValidator.SchemaStructure.Record =>
            assertTrue(r.fields("items").isInstanceOf[MigrationValidator.SchemaStructure.Sequence])
          case _ => assertTrue(false)
        }
      },
      test("extracts map structure") {
        val s = MigrationValidator.extractStructure(WithMap.schema)
        s match {
          case r: MigrationValidator.SchemaStructure.Record =>
            assertTrue(r.fields("data").isInstanceOf[MigrationValidator.SchemaStructure.MapType])
          case _ => assertTrue(false)
        }
      },
      test("extracts optional structure") {
        val s = MigrationValidator.extractStructure(RecordWithOptional.schema)
        s match {
          case r: MigrationValidator.SchemaStructure.Record =>
            assertTrue(r.isOptional("nick") == true)
          case _ => assertTrue(false)
        }
      },
      test("extracts primitive type in fields") {
        val s = MigrationValidator.extractStructure(SimpleRecord.schema)
        s match {
          case r: MigrationValidator.SchemaStructure.Record =>
            assertTrue(r.fields("age").isInstanceOf[MigrationValidator.SchemaStructure.Primitive])
          case _ => assertTrue(false)
        }
      }
    ),
    suite("SchemaStructure fieldNames")(
      test("Record fieldNames") {
        val r = MigrationValidator.SchemaStructure
          .Record("Test", Map("a" -> MigrationValidator.SchemaStructure.Dynamic), Map("a" -> false))
        assertTrue(r.fieldNames == Set("a"))
      },
      test("Variant fieldNames returns cases") {
        val v =
          MigrationValidator.SchemaStructure.Variant("Test", Map("A" -> MigrationValidator.SchemaStructure.Dynamic))
        assertTrue(v.fieldNames == Set("A"))
      },
      test("Sequence fieldNames is empty") {
        val s = MigrationValidator.SchemaStructure.Sequence(MigrationValidator.SchemaStructure.Dynamic)
        assertTrue(s.fieldNames.isEmpty)
      },
      test("MapType fieldNames is empty") {
        val m = MigrationValidator.SchemaStructure
          .MapType(MigrationValidator.SchemaStructure.Dynamic, MigrationValidator.SchemaStructure.Dynamic)
        assertTrue(m.fieldNames.isEmpty)
      },
      test("Primitive fieldNames is empty") {
        val p = MigrationValidator.SchemaStructure.Primitive("Int")
        assertTrue(p.fieldNames.isEmpty)
      },
      test("Optional fieldNames is empty") {
        val o = MigrationValidator.SchemaStructure.Optional(MigrationValidator.SchemaStructure.Dynamic)
        assertTrue(o.fieldNames.isEmpty)
      },
      test("Dynamic fieldNames is empty") {
        assertTrue(MigrationValidator.SchemaStructure.Dynamic.fieldNames.isEmpty)
      }
    ),
    suite("ValidationResult")(
      test("Valid ++ Valid = Valid") {
        val r = MigrationValidator.Valid ++ MigrationValidator.Valid
        assertTrue(r.isValid)
      },
      test("Valid ++ Invalid = Invalid") {
        val r = MigrationValidator.Valid ++ MigrationValidator.Invalid("err")
        assertTrue(!r.isValid && r.errors == List("err"))
      },
      test("Invalid ++ Valid = Invalid") {
        val r = MigrationValidator.Invalid("err") ++ MigrationValidator.Valid
        assertTrue(!r.isValid && r.errors == List("err"))
      },
      test("Invalid ++ Invalid combines errors") {
        val r = MigrationValidator.Invalid("err1") ++ MigrationValidator.Invalid("err2")
        assertTrue(!r.isValid && r.errors == List("err1", "err2"))
      },
      test("Valid.isValid is true") {
        assertTrue(MigrationValidator.Valid.isValid)
      },
      test("Valid.errors is Nil") {
        assertTrue(MigrationValidator.Valid.errors == Nil)
      },
      test("Invalid.isValid is false") {
        assertTrue(!MigrationValidator.Invalid("x").isValid)
      }
    ),
    suite("validate AddField")(
      test("valid AddField to record") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          RecordWithEmail.schema,
          Vector(MigrationAction.AddField(root, "email", litS))
        )
        assertTrue(result.isValid)
      },
      test("AddField already exists fails") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          SimpleRecord.schema,
          Vector(MigrationAction.AddField(root, "name", litS))
        )
        assertTrue(!result.isValid)
      },
      test("AddField to non-record fails") {
        val result = MigrationValidator.validate(
          Schema[Int],
          Schema[Int],
          Vector(MigrationAction.AddField(root, "x", litI))
        )
        assertTrue(!result.isValid)
      }
    ),
    suite("validate DropField")(
      test("valid DropField from record") {
        val result = MigrationValidator.validate(
          RecordWithEmail.schema,
          SimpleRecord.schema,
          Vector(MigrationAction.DropField(root, "email", litS))
        )
        assertTrue(result.isValid)
      },
      test("DropField non-existent fails") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          SimpleRecord.schema,
          Vector(MigrationAction.DropField(root, "missing", litS))
        )
        assertTrue(!result.isValid)
      },
      test("DropField from non-record fails") {
        val result = MigrationValidator.validate(
          Schema[Int],
          Schema[Int],
          Vector(MigrationAction.DropField(root, "x", litS))
        )
        assertTrue(!result.isValid)
      }
    ),
    suite("validate RenameField")(
      test("valid RenameField") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          RecordRenamed.schema,
          Vector(MigrationAction.RenameField(root, "name", "fullName"))
        )
        assertTrue(result.isValid)
      },
      test("RenameField non-existent source fails") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          SimpleRecord.schema,
          Vector(MigrationAction.RenameField(root, "missing", "new"))
        )
        assertTrue(!result.isValid)
      },
      test("RenameField target exists fails") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          SimpleRecord.schema,
          Vector(MigrationAction.RenameField(root, "name", "age"))
        )
        assertTrue(!result.isValid)
      },
      test("RenameField on non-record fails") {
        val result = MigrationValidator.validate(
          Schema[Int],
          Schema[Int],
          Vector(MigrationAction.RenameField(root, "a", "b"))
        )
        assertTrue(!result.isValid)
      }
    ),
    suite("validate RenameCase")(
      test("valid RenameCase") {
        val result = MigrationValidator.validate(
          Animal.schema,
          AnimalRenamed.schema,
          Vector(MigrationAction.RenameCase(root, "Dog", "Hound"))
        )
        assertTrue(result.isValid)
      },
      test("RenameCase non-existent fails") {
        val result = MigrationValidator.validate(
          Animal.schema,
          Animal.schema,
          Vector(MigrationAction.RenameCase(root, "Fish", "Bird"))
        )
        assertTrue(!result.isValid)
      },
      test("RenameCase target exists fails") {
        val result = MigrationValidator.validate(
          Animal.schema,
          Animal.schema,
          Vector(MigrationAction.RenameCase(root, "Dog", "Cat"))
        )
        assertTrue(!result.isValid)
      },
      test("RenameCase on non-variant fails") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          SimpleRecord.schema,
          Vector(MigrationAction.RenameCase(root, "A", "B"))
        )
        assertTrue(!result.isValid)
      }
    ),
    suite("validate Mandate")(
      test("valid Mandate") {
        val result = MigrationValidator.validate(
          RecordWithOptional.schema,
          RecordMandated.schema,
          Vector(MigrationAction.Mandate(root.field("nick"), litS))
        )
        assertTrue(result.isValid)
      },
      test("Mandate non-existent field fails") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          SimpleRecord.schema,
          Vector(MigrationAction.Mandate(root.field("missing"), litS))
        )
        assertTrue(!result.isValid)
      },
      test("Mandate on non-record fails") {
        val result = MigrationValidator.validate(
          Schema[Int],
          Schema[Int],
          Vector(MigrationAction.Mandate(root.field("x"), litI))
        )
        assertTrue(!result.isValid)
      }
    ),
    suite("validate Optionalize")(
      test("valid Optionalize") {
        val result = MigrationValidator.validate(
          RecordMandated.schema,
          RecordWithOptional.schema,
          Vector(MigrationAction.Optionalize(root.field("nick")))
        )
        assertTrue(result.isValid)
      },
      test("Optionalize non-existent field fails") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          SimpleRecord.schema,
          Vector(MigrationAction.Optionalize(root.field("missing")))
        )
        assertTrue(!result.isValid)
      },
      test("Optionalize on non-record fails") {
        val result = MigrationValidator.validate(
          Schema[Int],
          Schema[Int],
          Vector(MigrationAction.Optionalize(root.field("x")))
        )
        assertTrue(!result.isValid)
      }
    ),
    suite("validate TransformValue")(
      test("valid TransformValue with existing path") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          SimpleRecord.schema,
          Vector(MigrationAction.TransformValue(root.field("age"), litI, litI))
        )
        assertTrue(result.isValid)
      },
      test("TransformValue with non-existent path fails") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          SimpleRecord.schema,
          Vector(MigrationAction.TransformValue(root.field("missing"), litI, litI))
        )
        assertTrue(!result.isValid)
      }
    ),
    suite("validate ChangeType")(
      test("ChangeType passes validation") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          SimpleRecord.schema,
          Vector(MigrationAction.ChangeType(root.field("age"), litI, litI))
        )
        assertTrue(result.isValid)
      }
    ),
    suite("validate TransformCase")(
      test("TransformCase passes validation") {
        val result = MigrationValidator.validate(
          Animal.schema,
          Animal.schema,
          Vector(MigrationAction.TransformCase(root, "Dog", Vector.empty))
        )
        assertTrue(result.isValid)
      }
    ),
    suite("validate Identity")(
      test("Identity passes validation") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          SimpleRecord.schema,
          Vector(MigrationAction.Identity)
        )
        assertTrue(result.isValid)
      }
    ),
    suite("validate TransformElements")(
      test("valid TransformElements") {
        val result = MigrationValidator.validate(
          WithList.schema,
          WithList.schema,
          Vector(MigrationAction.TransformElements(root.field("items"), litI, litI))
        )
        assertTrue(result.isValid)
      },
      test("TransformElements on non-sequence fails") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          SimpleRecord.schema,
          Vector(MigrationAction.TransformElements(root.field("name"), litI, litI))
        )
        assertTrue(!result.isValid)
      }
    ),
    suite("validate TransformKeys")(
      test("valid TransformKeys") {
        val result = MigrationValidator.validate(
          WithMap.schema,
          WithMap.schema,
          Vector(MigrationAction.TransformKeys(root.field("data"), litS, litS))
        )
        assertTrue(result.isValid)
      },
      test("TransformKeys on non-map fails") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          SimpleRecord.schema,
          Vector(MigrationAction.TransformKeys(root.field("name"), litS, litS))
        )
        assertTrue(!result.isValid)
      }
    ),
    suite("validate TransformValues")(
      test("valid TransformValues") {
        val result = MigrationValidator.validate(
          WithMap.schema,
          WithMap.schema,
          Vector(MigrationAction.TransformValues(root.field("data"), litI, litI))
        )
        assertTrue(result.isValid)
      },
      test("TransformValues on non-map fails") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          SimpleRecord.schema,
          Vector(MigrationAction.TransformValues(root.field("name"), litI, litI))
        )
        assertTrue(!result.isValid)
      }
    ),
    suite("validate nested path operations")(
      test("AddField in nested record") {
        val result = MigrationValidator.validate(
          Nested.schema,
          NestedEmail.schema,
          Vector(MigrationAction.AddField(root.field("person"), "email", litS))
        )
        assertTrue(result.isValid)
      },
      test("RenameField in nested record") {
        val result = MigrationValidator.validate(
          Nested.schema,
          Nested.schema,
          Vector(MigrationAction.RenameField(root.field("person"), "name", "fullName"))
        )
        assertTrue(result.isValid)
      },
      test("DropField in nested record") {
        val result = MigrationValidator.validate(
          NestedEmail.schema,
          Nested.schema,
          Vector(MigrationAction.DropField(root.field("person"), "email", litS))
        )
        assertTrue(result.isValid)
      },
      test("nested path field not found fails") {
        val result = MigrationValidator.validate(
          Nested.schema,
          Nested.schema,
          Vector(MigrationAction.AddField(root.field("missing"), "x", litI))
        )
        assertTrue(!result.isValid)
      }
    ),
    suite("validate Join")(
      test("valid Join") {
        val result = MigrationValidator.validate(
          RecordAB.schema,
          RecordJoined.schema,
          Vector(
            MigrationAction.Join(
              root.field("combined"),
              Vector(root.field("b")),
              litS,
              litS
            )
          )
        )
        assertTrue(result.isValid)
      },
      test("Join source field not found fails") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          SimpleRecord.schema,
          Vector(
            MigrationAction.Join(
              root.field("combo"),
              Vector(root.field("missing")),
              litS,
              litS
            )
          )
        )
        assertTrue(!result.isValid)
      }
    ),
    suite("validate Split")(
      test("valid Split") {
        val result = MigrationValidator.validate(
          RecordJoined.schema,
          RecordAB.schema,
          Vector(
            MigrationAction.Split(
              root.field("combined"),
              Vector(root.field("b")),
              litS,
              litS
            )
          )
        )
        assertTrue(result.isValid)
      },
      test("Split source field not found fails") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          SimpleRecord.schema,
          Vector(
            MigrationAction.Split(
              root.field("missing"),
              Vector(root.field("a"), root.field("b")),
              litS,
              litS
            )
          )
        )
        assertTrue(!result.isValid)
      }
    ),
    suite("compareStructures edge cases")(
      test("identical schemas validate as valid") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          SimpleRecord.schema,
          Vector.empty
        )
        assertTrue(result.isValid)
      },
      test("different record types without actions is invalid") {
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          RecordWithEmail.schema,
          Vector.empty
        )
        assertTrue(!result.isValid)
      },
      test("type mismatch in primitive fields") {
        // SimpleRecord has String name + Int age. RecordRenamed has String fullName + Int age.
        // Without rename action but with no structural actions, this should fail
        val result = MigrationValidator.validate(
          SimpleRecord.schema,
          RecordRenamed.schema,
          Vector.empty
        )
        assertTrue(!result.isValid)
      }
    ),
    suite("DynamicOpticOps")(
      test("dropLastField on field path") {
        import MigrationValidator.DynamicOpticOps
        val optic          = root.field("name")
        val (parent, name) = optic.dropLastField
        assertTrue(parent.nodes.isEmpty && name == Some("name"))
      },
      test("dropLastField on non-field path") {
        import MigrationValidator.DynamicOpticOps
        val optic          = DynamicOptic(Vector(DynamicOptic.Node.Elements))
        val (parent, name) = optic.dropLastField
        assertTrue(name == None)
      },
      test("dropLastField on empty path") {
        import MigrationValidator.DynamicOpticOps
        val optic          = root
        val (parent, name) = optic.dropLastField
        assertTrue(parent.nodes.isEmpty && name == None)
      },
      test("lastFieldName on field path") {
        import MigrationValidator.DynamicOpticOps
        val optic = root.field("age")
        assertTrue(optic.lastFieldName == Some("age"))
      },
      test("lastFieldName on non-field path") {
        import MigrationValidator.DynamicOpticOps
        val optic = DynamicOptic(Vector(DynamicOptic.Node.Elements))
        assertTrue(optic.lastFieldName == None)
      },
      test("lastFieldName on empty path") {
        import MigrationValidator.DynamicOpticOps
        assertTrue(root.lastFieldName == None)
      }
    )
  )
}
