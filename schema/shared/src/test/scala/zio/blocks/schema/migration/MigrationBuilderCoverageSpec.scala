package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationBuilderCoverageSpec extends SchemaBaseSpec {

  case class SimpleRecord(name: String, age: Int)
  object SimpleRecord { implicit val schema: Schema[SimpleRecord] = Schema.derived }

  case class RecordWithEmail(name: String, age: Int, email: String)
  object RecordWithEmail { implicit val schema: Schema[RecordWithEmail] = Schema.derived }

  case class RecordRenamed(fullName: String, age: Int)
  object RecordRenamed { implicit val schema: Schema[RecordRenamed] = Schema.derived }

  case class RecordWithOptional(name: String, age: Int, nick: Option[String])
  object RecordWithOptional { implicit val schema: Schema[RecordWithOptional] = Schema.derived }

  case class WithList(items: List[Int])
  object WithList { implicit val schema: Schema[WithList] = Schema.derived }

  case class WithMap(data: Map[String, Int])
  object WithMap { implicit val schema: Schema[WithMap] = Schema.derived }

  sealed trait Animal
  object Animal {
    case class Dog(breed: String) extends Animal
    case class Cat(color: String) extends Animal
    implicit val schema: Schema[Animal] = Schema.derived
  }

  private val root = DynamicOptic.root
  private val litI = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
  private val litS = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationBuilderCoverageSpec")(
    suite("addField")(
      test("addField with optic and default expr") {
        val builder = new MigrationBuilder(SimpleRecord.schema, RecordWithEmail.schema, Vector.empty)
          .addField(root.field("email"), litS)
        assertTrue(builder.actions.length == 1 && builder.actions.head.isInstanceOf[MigrationAction.AddField])
      },
      test("addField with typed default value") {
        val builder = new MigrationBuilder(SimpleRecord.schema, RecordWithEmail.schema, Vector.empty)
          .addField[String](root.field("email"), "default")
        assertTrue(builder.actions.length == 1)
      }
    ),
    suite("dropField")(
      test("dropField basic") {
        val builder = new MigrationBuilder(RecordWithEmail.schema, SimpleRecord.schema, Vector.empty)
          .dropField(root.field("email"))
        assertTrue(builder.actions.length == 1 && builder.actions.head.isInstanceOf[MigrationAction.DropField])
      },
      test("dropField with custom reverse default") {
        val builder = new MigrationBuilder(RecordWithEmail.schema, SimpleRecord.schema, Vector.empty)
          .dropField(root.field("email"), litS)
        assertTrue(builder.actions.length == 1)
      }
    ),
    suite("renameField")(
      test("renameField with optics") {
        val builder = new MigrationBuilder(SimpleRecord.schema, RecordRenamed.schema, Vector.empty)
          .renameField(root.field("name"), root.field("fullName"))
        assertTrue(builder.actions.length == 1 && builder.actions.head.isInstanceOf[MigrationAction.RenameField])
      },
      test("renameField with strings") {
        val builder = new MigrationBuilder(SimpleRecord.schema, RecordRenamed.schema, Vector.empty)
          .renameField("name", "fullName")
        assertTrue(builder.actions.length == 1)
      }
    ),
    suite("transformField")(
      test("transformField basic") {
        val builder = new MigrationBuilder(SimpleRecord.schema, SimpleRecord.schema, Vector.empty)
          .transformField(root.field("age"), litI)
        assertTrue(builder.actions.length == 1 && builder.actions.head.isInstanceOf[MigrationAction.TransformValue])
      },
      test("transformField with reverse") {
        val builder = new MigrationBuilder(SimpleRecord.schema, SimpleRecord.schema, Vector.empty)
          .transformField(root.field("age"), litI, litI)
        assertTrue(builder.actions.length == 1)
      }
    ),
    suite("mandateField")(
      test("mandateField with expr") {
        val builder = new MigrationBuilder(RecordWithOptional.schema, SimpleRecord.schema, Vector.empty)
          .mandateField(root.field("nick"), litS)
        assertTrue(builder.actions.length == 1 && builder.actions.head.isInstanceOf[MigrationAction.Mandate])
      },
      test("mandateField with typed default") {
        val builder = new MigrationBuilder(RecordWithOptional.schema, SimpleRecord.schema, Vector.empty)
          .mandateField[String](root.field("nick"), "default")
        assertTrue(builder.actions.length == 1)
      }
    ),
    suite("optionalizeField")(
      test("optionalizeField basic") {
        val builder = new MigrationBuilder(SimpleRecord.schema, RecordWithOptional.schema, Vector.empty)
          .optionalizeField(root.field("name"))
        assertTrue(builder.actions.length == 1 && builder.actions.head.isInstanceOf[MigrationAction.Optionalize])
      },
      test("optionalizeField with reverse default") {
        val builder = new MigrationBuilder(SimpleRecord.schema, RecordWithOptional.schema, Vector.empty)
          .optionalizeField(root.field("name"), litS)
        assertTrue(builder.actions.length == 1)
      }
    ),
    suite("changeFieldType")(
      test("changeFieldType with exprs") {
        val builder = new MigrationBuilder(SimpleRecord.schema, SimpleRecord.schema, Vector.empty)
          .changeFieldType(root.field("age"), litI, litI)
        assertTrue(builder.actions.length == 1 && builder.actions.head.isInstanceOf[MigrationAction.ChangeType])
      },
      test("changeFieldType with type names") {
        val builder = new MigrationBuilder(SimpleRecord.schema, SimpleRecord.schema, Vector.empty)
          .changeFieldType(root.field("age"), "Long", "Int")
        val action = builder.actions.head.asInstanceOf[MigrationAction.ChangeType]
        assertTrue(action.converter.isInstanceOf[DynamicSchemaExpr.CoercePrimitive])
      },
      test("changeFieldType with type names on root path") {
        val builder = new MigrationBuilder(Schema[Int], Schema[Long], Vector.empty)
          .changeFieldType(root, "Long", "Int")
        assertTrue(builder.actions.length == 1)
      }
    ),
    suite("joinFields")(
      test("joinFields creates Join action") {
        val builder = new MigrationBuilder(SimpleRecord.schema, SimpleRecord.schema, Vector.empty)
          .joinFields(root.field("combined"), Vector(root.field("a"), root.field("b")), litS, litS)
        assertTrue(builder.actions.length == 1 && builder.actions.head.isInstanceOf[MigrationAction.Join])
      }
    ),
    suite("splitField")(
      test("splitField creates Split action") {
        val builder = new MigrationBuilder(SimpleRecord.schema, SimpleRecord.schema, Vector.empty)
          .splitField(root.field("combined"), Vector(root.field("a"), root.field("b")), litS, litS)
        assertTrue(builder.actions.length == 1 && builder.actions.head.isInstanceOf[MigrationAction.Split])
      }
    ),
    suite("renameCase")(
      test("renameCase at root") {
        val builder = new MigrationBuilder(Animal.schema, Animal.schema, Vector.empty)
          .renameCase("Dog", "Hound")
        assertTrue(builder.actions.length == 1 && builder.actions.head.isInstanceOf[MigrationAction.RenameCase])
      },
      test("renameCaseAt specific path") {
        val builder = new MigrationBuilder(Animal.schema, Animal.schema, Vector.empty)
          .renameCaseAt(root.field("pet"), "Dog", "Hound")
        val action = builder.actions.head.asInstanceOf[MigrationAction.RenameCase]
        assertTrue(action.at.nodes.length == 1)
      }
    ),
    suite("transformCase")(
      test("transformCase creates nested actions") {
        val builder = new MigrationBuilder(Animal.schema, Animal.schema, Vector.empty)
          .transformCase("Dog", _.renameField("breed", "type"))
        val action = builder.actions.head.asInstanceOf[MigrationAction.TransformCase]
        assertTrue(action.caseName == "Dog" && action.actions.length == 1)
      },
      test("transformCaseAt specific path") {
        val builder = new MigrationBuilder(Animal.schema, Animal.schema, Vector.empty)
          .transformCaseAt(root.field("pet"), "Cat", _.addField(root.field("indoor"), litS))
        val action = builder.actions.head.asInstanceOf[MigrationAction.TransformCase]
        assertTrue(action.at.nodes.length == 1 && action.caseName == "Cat")
      }
    ),
    suite("transformElements")(
      test("creates TransformElements action") {
        val builder = new MigrationBuilder(WithList.schema, WithList.schema, Vector.empty)
          .transformElements(root.field("items"), litI)
        assertTrue(builder.actions.length == 1 && builder.actions.head.isInstanceOf[MigrationAction.TransformElements])
      }
    ),
    suite("transformKeys")(
      test("creates TransformKeys action") {
        val builder = new MigrationBuilder(WithMap.schema, WithMap.schema, Vector.empty)
          .transformKeys(root.field("data"), litS)
        assertTrue(builder.actions.length == 1 && builder.actions.head.isInstanceOf[MigrationAction.TransformKeys])
      }
    ),
    suite("transformValues")(
      test("creates TransformValues action") {
        val builder = new MigrationBuilder(WithMap.schema, WithMap.schema, Vector.empty)
          .transformValues(root.field("data"), litI)
        assertTrue(builder.actions.length == 1 && builder.actions.head.isInstanceOf[MigrationAction.TransformValues])
      }
    ),
    suite("build methods")(
      test("buildPartial creates migration without validation") {
        val builder   = new MigrationBuilder(SimpleRecord.schema, SimpleRecord.schema, Vector.empty)
        val migration = builder.buildPartial
        assertTrue(migration.isEmpty)
      },
      test("build validates and succeeds for valid migration") {
        val builder   = new MigrationBuilder(SimpleRecord.schema, SimpleRecord.schema, Vector.empty)
        val migration = builder.build
        assertTrue(migration.isEmpty)
      },
      test("build throws for invalid migration") {
        val builder = new MigrationBuilder(SimpleRecord.schema, RecordWithEmail.schema, Vector.empty)
        val caught  = try {
          builder.build
          false
        } catch {
          case _: IllegalArgumentException => true
          case _: Throwable                => false
        }
        assertTrue(caught)
      },
      test("buildValidated returns Right for valid") {
        val builder = new MigrationBuilder(SimpleRecord.schema, SimpleRecord.schema, Vector.empty)
        assertTrue(builder.buildValidated.isRight)
      },
      test("buildValidated returns Left for invalid") {
        val builder = new MigrationBuilder(SimpleRecord.schema, RecordWithEmail.schema, Vector.empty)
        assertTrue(builder.buildValidated.isLeft)
      }
    ),
    suite("splitPath")(
      test("splitPath throws on non-field path") {
        val builder = new MigrationBuilder(SimpleRecord.schema, SimpleRecord.schema, Vector.empty)
        val caught  = try {
          builder.addField(DynamicOptic(Vector(DynamicOptic.Node.Elements)), litI)
          false
        } catch {
          case _: IllegalArgumentException => true
          case _: Throwable                => false
        }
        assertTrue(caught)
      }
    ),
    suite("MigrationBuilder.apply")(
      test("creates builder from implicit schemas") {
        val builder = MigrationBuilder[SimpleRecord, SimpleRecord]
        assertTrue(builder.actions.isEmpty)
      }
    ),
    suite("MigrationBuilder.paths helper")(
      test("field single name") {
        val p = MigrationBuilder.paths.field("name")
        assertTrue(p.nodes.length == 1 && p.nodes.head == DynamicOptic.Node.Field("name"))
      },
      test("field multiple names") {
        val p = MigrationBuilder.paths.field("a", "b", "c")
        assertTrue(p.nodes.length == 3)
      },
      test("elements") {
        val p = MigrationBuilder.paths.elements
        assertTrue(p.nodes.head == DynamicOptic.Node.Elements)
      },
      test("mapKeys") {
        val p = MigrationBuilder.paths.mapKeys
        assertTrue(p.nodes.head == DynamicOptic.Node.MapKeys)
      },
      test("mapValues") {
        val p = MigrationBuilder.paths.mapValues
        assertTrue(p.nodes.head == DynamicOptic.Node.MapValues)
      }
    ),
    suite("MigrationBuilder.exprs helper")(
      test("literal creates Literal expr") {
        val e = MigrationBuilder.exprs.literal(42)
        assertTrue(e.isInstanceOf[DynamicSchemaExpr.Literal])
      },
      test("path with optic creates Path expr") {
        val e = MigrationBuilder.exprs.path(root.field("x"))
        assertTrue(e.isInstanceOf[DynamicSchemaExpr.Path])
      },
      test("path with string creates Path expr") {
        val e = MigrationBuilder.exprs.path("name")
        e match {
          case DynamicSchemaExpr.Path(optic) =>
            assertTrue(optic.nodes.head == DynamicOptic.Node.Field("name"))
          case _ => assertTrue(false)
        }
      },
      test("concat creates StringConcat expr") {
        val e = MigrationBuilder.exprs.concat(litS, litS)
        assertTrue(e.isInstanceOf[DynamicSchemaExpr.StringConcat])
      },
      test("defaultValue creates DefaultValue expr") {
        val e = MigrationBuilder.exprs.defaultValue
        assertTrue(e == DynamicSchemaExpr.DefaultValue)
      },
      test("coerce creates CoercePrimitive expr") {
        val e = MigrationBuilder.exprs.coerce(litI, "Long")
        assertTrue(e.isInstanceOf[DynamicSchemaExpr.CoercePrimitive])
      }
    )
  )
}
