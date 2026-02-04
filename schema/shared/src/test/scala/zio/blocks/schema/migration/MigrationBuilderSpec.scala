package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

object MigrationBuilderSpec extends SchemaBaseSpec {
  
  case class PersonV1(firstName: String, lastName: String)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }
  
  case class PersonV2(fullName: String, age: Int)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }
  
  case class SimpleRecord(a: String, b: Int)
  object SimpleRecord {
    implicit val schema: Schema[SimpleRecord] = Schema.derived
  }
  
  case class ExtendedRecord(a: String, b: Int, c: Boolean, d: Double)
  object ExtendedRecord {
    implicit val schema: Schema[ExtendedRecord] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("MigrationBuilderSpec")(
    suite("addField")(
      test("adds a field with literal default value") {
        val builder = MigrationBuilder[SimpleRecord, ExtendedRecord]
          .addField(DynamicOptic.root.field("c"), true)
          .addField(DynamicOptic.root.field("d"), 3.14)
        val migration = builder.buildPartial
        val result = migration(SimpleRecord("test", 42))
        assertTrue(result.isRight)
      },
      test("adds a field with expression default") {
        val builder = MigrationBuilder[SimpleRecord, SimpleRecord]
          .addField(
            DynamicOptic.root.field("c"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
          )
        val migration = builder.buildPartial
        val result = migration.applyDynamic(DynamicValue.Record(Vector(
          "a" -> DynamicValue.Primitive(PrimitiveValue.String("hello")),
          "b" -> DynamicValue.Primitive(PrimitiveValue.Int(1))
        )))
        assertTrue(result.isRight)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "c"))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("dropField")(
      test("drops a field from the record") {
        val builder = MigrationBuilder[ExtendedRecord, SimpleRecord]
          .dropField(DynamicOptic.root.field("c"))
          .dropField(DynamicOptic.root.field("d"))
        val migration = builder.buildPartial
        val result = migration.applyDynamic(DynamicValue.Record(Vector(
          "a" -> DynamicValue.Primitive(PrimitiveValue.String("test")),
          "b" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          "c" -> DynamicValue.Primitive(PrimitiveValue.Boolean(true)),
          "d" -> DynamicValue.Primitive(PrimitiveValue.Double(1.5))
        )))
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(
              fields.length == 2,
              !fields.exists(_._1 == "c"),
              !fields.exists(_._1 == "d")
            )
          case _ => assertTrue(false)
        }
      }
    ),
    suite("renameField")(
      test("renames a field using path") {
        val builder = MigrationBuilder[SimpleRecord, SimpleRecord]
          .renameField(DynamicOptic.root.field("a"), DynamicOptic.root.field("alpha"))
        val migration = builder.buildPartial
        val result = migration.applyDynamic(DynamicValue.Record(Vector(
          "a" -> DynamicValue.Primitive(PrimitiveValue.String("test")),
          "b" -> DynamicValue.Primitive(PrimitiveValue.Int(1))
        )))
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(
              fields.exists(_._1 == "alpha"),
              !fields.exists(_._1 == "a")
            )
          case _ => assertTrue(false)
        }
      },
      test("renames a field using string names") {
        val builder = MigrationBuilder[SimpleRecord, SimpleRecord]
          .renameField("a", "x")
        val migration = builder.buildPartial
        assertTrue(migration.actions.length == 1)
      }
    ),
    suite("transformField")(
      test("transforms a field value") {
        val builder = MigrationBuilder[SimpleRecord, SimpleRecord]
          .transformField(
            DynamicOptic.root.field("b"),
            DynamicSchemaExpr.Arithmetic(
              DynamicSchemaExpr.Path(DynamicOptic.root),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
              DynamicSchemaExpr.ArithmeticOperator.Add
            )
          )
        val migration = builder.buildPartial
        val result = migration.applyDynamic(DynamicValue.Record(Vector(
          "a" -> DynamicValue.Primitive(PrimitiveValue.String("test")),
          "b" -> DynamicValue.Primitive(PrimitiveValue.Int(5))
        )))
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val bValue = fields.find(_._1 == "b").map(_._2)
            assertTrue(bValue == Some(DynamicValue.Primitive(PrimitiveValue.Int(15))))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("mandateField")(
      test("makes optional field mandatory with literal default") {
        val builder = MigrationBuilder[SimpleRecord, SimpleRecord]
          .mandateField(DynamicOptic.root.field("opt"), 100)
        val migration = builder.buildPartial
        assertTrue(migration.actions.length == 1)
      }
    ),
    suite("optionalizeField")(
      test("makes field optional") {
        val builder = MigrationBuilder[SimpleRecord, SimpleRecord]
          .optionalizeField(DynamicOptic.root.field("b"))
        val migration = builder.buildPartial
        assertTrue(migration.actions.length == 1)
      }
    ),
    suite("changeFieldType")(
      test("changes field type using expression") {
        val builder = MigrationBuilder[SimpleRecord, SimpleRecord]
          .changeFieldType(
            DynamicOptic.root.field("b"),
            DynamicSchemaExpr.CoercePrimitive(
              DynamicSchemaExpr.Path(DynamicOptic.root),
              "String"
            )
          )
        val migration = builder.buildPartial
        assertTrue(migration.actions.length == 1)
      },
      test("changes field type using type names") {
        val builder = MigrationBuilder[SimpleRecord, SimpleRecord]
          .changeFieldType(DynamicOptic.root.field("b"), "String", "Int")
        val migration = builder.buildPartial
        assertTrue(migration.actions.length == 1)
      }
    ),
    suite("renameCase")(
      test("renames a variant case") {
        val builder = MigrationBuilder[SimpleRecord, SimpleRecord]
          .renameCase("OldCase", "NewCase")
        val migration = builder.buildPartial
        val action = migration.actions.head.asInstanceOf[MigrationAction.RenameCase]
        assertTrue(action.from == "OldCase" && action.to == "NewCase")
      },
      test("renames a case at specific path") {
        val builder = MigrationBuilder[SimpleRecord, SimpleRecord]
          .renameCaseAt(DynamicOptic.root.field("status"), "Active", "Enabled")
        val migration = builder.buildPartial
        val action = migration.actions.head.asInstanceOf[MigrationAction.RenameCase]
        assertTrue(
          action.from == "Active",
          action.to == "Enabled",
          action.at.nodes.nonEmpty
        )
      }
    ),
    suite("transformCase")(
      test("transforms a specific case") {
        val builder = MigrationBuilder[SimpleRecord, SimpleRecord]
          .transformCase("MyCase", _.renameField("old", "new"))
        val migration = builder.buildPartial
        val action = migration.actions.head.asInstanceOf[MigrationAction.TransformCase]
        assertTrue(
          action.caseName == "MyCase",
          action.actions.length == 1
        )
      }
    ),
    suite("transformElements")(
      test("transforms collection elements") {
        val builder = MigrationBuilder[SimpleRecord, SimpleRecord]
          .transformElements(
            DynamicOptic.root.field("items"),
            DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Path(DynamicOptic.root), "String")
          )
        val migration = builder.buildPartial
        assertTrue(migration.actions.length == 1)
      }
    ),
    suite("transformKeys")(
      test("transforms map keys") {
        val builder = MigrationBuilder[SimpleRecord, SimpleRecord]
          .transformKeys(
            DynamicOptic.root.field("data"),
            DynamicSchemaExpr.StringConcat(
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("key_"))),
              DynamicSchemaExpr.Path(DynamicOptic.root)
            )
          )
        val migration = builder.buildPartial
        assertTrue(migration.actions.length == 1)
      }
    ),
    suite("transformValues")(
      test("transforms map values") {
        val builder = MigrationBuilder[SimpleRecord, SimpleRecord]
          .transformValues(
            DynamicOptic.root.field("data"),
            DynamicSchemaExpr.Arithmetic(
              DynamicSchemaExpr.Path(DynamicOptic.root),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
              DynamicSchemaExpr.ArithmeticOperator.Add
            )
          )
        val migration = builder.buildPartial
        assertTrue(migration.actions.length == 1)
      }
    ),
    suite("build and buildPartial")(
      test("buildPartial creates migration without validation") {
        val builder = MigrationBuilder[SimpleRecord, SimpleRecord]
          .renameField("a", "b")
          .renameField("b", "c")
        val migration = builder.buildPartial
        assertTrue(migration.actions.length == 2)
      },
      test("build creates migration (same as buildPartial for now)") {
        val builder = MigrationBuilder[SimpleRecord, SimpleRecord]
          .renameField("a", "b")
        val migration = builder.build
        assertTrue(migration.actions.length == 1)
      }
    ),
    suite("Fluent chaining")(
      test("chains multiple operations") {
        val builder = MigrationBuilder[PersonV1, PersonV2]
          .addField(DynamicOptic.root.field("age"), 0)
          .renameField("firstName", "fullName")
          .dropField(DynamicOptic.root.field("lastName"))
        val migration = builder.buildPartial
        assertTrue(migration.actions.length == 3)
      }
    ),
    suite("Helper syntax")(
      test("paths helper creates DynamicOptic") {
        val path = MigrationBuilder.paths.field("a")
        assertTrue(path == DynamicOptic.root.field("a"))
      },
      test("paths helper creates nested path") {
        val path = MigrationBuilder.paths.field("a", "b", "c")
        assertTrue(path == DynamicOptic.root.field("a").field("b").field("c"))
      },
      test("exprs literal creates Literal expression") {
        val expr = MigrationBuilder.exprs.literal(42)
        assertTrue(expr.isInstanceOf[DynamicSchemaExpr.Literal])
      },
      test("exprs path creates Path expression") {
        val expr = MigrationBuilder.exprs.path("fieldName")
        assertTrue(expr.isInstanceOf[DynamicSchemaExpr.Path])
      },
      test("exprs concat creates StringConcat expression") {
        val left = MigrationBuilder.exprs.literal("hello")
        val right = MigrationBuilder.exprs.literal(" world")
        val expr = MigrationBuilder.exprs.concat(left, right)
        assertTrue(expr.isInstanceOf[DynamicSchemaExpr.StringConcat])
      }
    )
  )
}
