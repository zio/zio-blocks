package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

object MigrationBuilderSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Data Types
  // ─────────────────────────────────────────────────────────────────────────

  case class PersonV0(name: String, age: Int)
  object PersonV0 {
    implicit val schema: Schema[PersonV0] = Schema.derived
  }

  case class PersonV1(name: String, age: Int, email: String)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(fullName: String, age: Int, email: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class SimpleRecord(x: Int, y: String)
  object SimpleRecord {
    implicit val schema: Schema[SimpleRecord] = Schema.derived
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Dynamic Value Helpers
  // ─────────────────────────────────────────────────────────────────────────

  private def intDV(i: Int): DynamicValue           = DynamicValue.Primitive(PrimitiveValue.Int(i))
  private def stringDV(s: String): DynamicValue     = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def lit(dv: DynamicValue): MigrationExpr  = MigrationExpr.Literal(dv)
  private def litInt(i: Int): MigrationExpr         = lit(intDV(i))
  private def litStr(s: String): MigrationExpr      = lit(stringDV(s))
  private def fieldRef(name: String): MigrationExpr = MigrationExpr.FieldRef(DynamicOptic.root.field(name))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationBuilderSpec")(
    emptyBuilderSuite,
    addFieldSuite,
    dropFieldSuite,
    renameFieldSuite,
    fluentChainingSuite,
    transformFieldSuite,
    changeFieldTypeSuite,
    renameCaseSuite,
    buildVsBuildPartialSuite,
    addActionSuite,
    newBuilderFactorySuite,
    mandateFieldSuite,
    optionalizeFieldSuite,
    joinSplitSuite,
    collectionOperationsSuite,
    atVariantsSuite
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Empty Builder
  // ─────────────────────────────────────────────────────────────────────────

  private val emptyBuilderSuite = suite("empty builder")(
    test("buildPartial produces empty migration") {
      val builder   = MigrationBuilder.create(SimpleRecord.schema, SimpleRecord.schema)
      val migration = builder.buildPartial
      assertTrue(migration.isEmpty)
    },
    test("empty migration passes values through unchanged") {
      val builder   = MigrationBuilder.create(SimpleRecord.schema, SimpleRecord.schema)
      val migration = builder.buildPartial
      val value     = SimpleRecord(42, "hello")
      val result    = migration(value)
      assertTrue(result == Right(value))
    },
    test("empty builder has no actions") {
      val builder = MigrationBuilder.create(SimpleRecord.schema, SimpleRecord.schema)
      assertTrue(builder.actions.isEmpty)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // AddField
  // ─────────────────────────────────────────────────────────────────────────

  private val addFieldSuite = suite("addField")(
    test("adds a field and migrates V0 to V1") {
      val migration = MigrationBuilder
        .create(PersonV0.schema, PersonV1.schema)
        .addField("email", litStr("default@example.com"))
        .buildPartial
      val result = migration(PersonV0("Alice", 30))
      assertTrue(result == Right(PersonV1("Alice", 30, "default@example.com")))
    },
    test("accumulates a single AddField action") {
      val builder = MigrationBuilder
        .create(PersonV0.schema, PersonV1.schema)
        .addField("email", litStr("test"))
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.AddField(at, fieldName, _) =>
            assertTrue(at == DynamicOptic.root, fieldName == "email")
          case _ => assertTrue(false)
        }
      }
    },
    test("addFieldAt adds a field at a nested path") {
      val builder = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .addFieldAt(DynamicOptic.root.field("inner"), "extra", litInt(0))
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.AddField(at, fieldName, _) =>
            assertTrue(at == DynamicOptic.root.field("inner"), fieldName == "extra")
          case _ => assertTrue(false)
        }
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // DropField
  // ─────────────────────────────────────────────────────────────────────────

  private val dropFieldSuite = suite("dropField")(
    test("drops a field and migrates V1 to V0") {
      val migration = MigrationBuilder
        .create(PersonV1.schema, PersonV0.schema)
        .dropField("email", litStr(""))
        .buildPartial
      val result = migration(PersonV1("Alice", 30, "alice@example.com"))
      assertTrue(result == Right(PersonV0("Alice", 30)))
    },
    test("accumulates a single DropField action") {
      val builder = MigrationBuilder
        .create(PersonV1.schema, PersonV0.schema)
        .dropField("email", litStr(""))
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.DropField(at, fieldName, _) =>
            assertTrue(at == DynamicOptic.root, fieldName == "email")
          case _ => assertTrue(false)
        }
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // RenameField
  // ─────────────────────────────────────────────────────────────────────────

  private val renameFieldSuite = suite("renameField")(
    test("renames a field and migrates V1 to V2") {
      val migration = MigrationBuilder
        .create(PersonV1.schema, PersonV2.schema)
        .renameField("name", "fullName")
        .buildPartial
      val result = migration(PersonV1("Alice", 30, "alice@example.com"))
      assertTrue(result == Right(PersonV2("Alice", 30, "alice@example.com")))
    },
    test("accumulates a single Rename action") {
      val builder = MigrationBuilder
        .create(PersonV1.schema, PersonV2.schema)
        .renameField("name", "fullName")
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.Rename(at, fromName, toName) =>
            assertTrue(at == DynamicOptic.root, fromName == "name", toName == "fullName")
          case _ => assertTrue(false)
        }
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Fluent Chaining
  // ─────────────────────────────────────────────────────────────────────────

  private val fluentChainingSuite = suite("fluent chaining")(
    test("multiple operations chained together produce correct migration") {
      // V0(name, age) -> add email -> rename name to fullName -> V2(fullName, age, email)
      val migration = MigrationBuilder
        .create(PersonV0.schema, PersonV2.schema)
        .addField("email", litStr("default@example.com"))
        .renameField("name", "fullName")
        .buildPartial
      val result = migration(PersonV0("Alice", 30))
      assertTrue(result == Right(PersonV2("Alice", 30, "default@example.com")))
    },
    test("chained builder accumulates actions in order") {
      val builder = MigrationBuilder
        .create(PersonV0.schema, PersonV2.schema)
        .addField("email", litStr("x"))
        .renameField("name", "fullName")
        .dropField("unused", litStr(""))
      assertTrue(builder.actions.size == 3) && {
        val isAdd    = builder.actions(0).isInstanceOf[MigrationAction.AddField]
        val isRename = builder.actions(1).isInstanceOf[MigrationAction.Rename]
        val isDrop   = builder.actions(2).isInstanceOf[MigrationAction.DropField]
        assertTrue(isAdd, isRename, isDrop)
      }
    },
    test("builder is immutable - original is unchanged after chaining") {
      val base    = MigrationBuilder.create(SimpleRecord.schema, SimpleRecord.schema)
      val derived = base.addField("extra", litInt(0))
      assertTrue(base.actions.isEmpty, derived.actions.size == 1)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // TransformField
  // ─────────────────────────────────────────────────────────────────────────

  private val transformFieldSuite = suite("transformField")(
    test("transforms a field value using a literal expression") {
      val migration = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .transformField("x", litInt(99), litInt(42))
        .buildPartial
      val result = migration(SimpleRecord(42, "hello"))
      assertTrue(result == Right(SimpleRecord(99, "hello")))
    },
    test("accumulates a single TransformValue action") {
      val builder = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .transformField("x", litInt(99), litInt(42))
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.TransformValue(at, fieldName, _, _) =>
            assertTrue(at == DynamicOptic.root, fieldName == "x")
          case _ => assertTrue(false)
        }
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // ChangeFieldType
  // ─────────────────────────────────────────────────────────────────────────

  private val changeFieldTypeSuite = suite("changeFieldType")(
    test("coerces field type using expressions") {
      val coercion        = MigrationExpr.Coerce(MigrationExpr.FieldRef(DynamicOptic.root), "String")
      val reverseCoercion = MigrationExpr.Coerce(MigrationExpr.FieldRef(DynamicOptic.root), "Int")
      val builder         = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .changeFieldType("x", coercion, reverseCoercion)
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.ChangeType(at, fieldName, _, _) =>
            assertTrue(at == DynamicOptic.root, fieldName == "x")
          case _ => assertTrue(false)
        }
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // RenameCase
  // ─────────────────────────────────────────────────────────────────────────

  private val renameCaseSuite = suite("renameCase")(
    test("accumulates a RenameCase action") {
      val builder = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .renameCase("OldCase", "NewCase")
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.RenameCase(at, fromCase, toCase) =>
            assertTrue(at == DynamicOptic.root, fromCase == "OldCase", toCase == "NewCase")
          case _ => assertTrue(false)
        }
      }
    },
    test("RenameCase applies correctly to a variant DynamicValue") {
      val variant = DynamicValue.Variant("OldCase", intDV(1))
      val dm      = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .renameCase("OldCase", "NewCase")
        .buildPartial
        .dynamicMigration
      val result = dm(variant)
      assertTrue(result == Right(DynamicValue.Variant("NewCase", intDV(1))))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Build vs BuildPartial
  // ─────────────────────────────────────────────────────────────────────────

  private val buildVsBuildPartialSuite = suite("build vs buildPartial")(
    test("buildPartial produces a working migration") {
      val migration = MigrationBuilder
        .create(PersonV0.schema, PersonV1.schema)
        .addField("email", litStr("test@test.com"))
        .buildPartial
      val result = migration(PersonV0("Bob", 25))
      assertTrue(result == Right(PersonV1("Bob", 25, "test@test.com")))
    },
    test("build returns Right with a working migration") {
      val result = MigrationBuilder
        .create(PersonV0.schema, PersonV1.schema)
        .addField("email", litStr("test@test.com"))
        .build
      assertTrue(result.isRight) && {
        val migration = result.toOption.get
        val applied   = migration(PersonV0("Bob", 25))
        assertTrue(applied == Right(PersonV1("Bob", 25, "test@test.com")))
      }
    },
    test("build and buildPartial produce equivalent migrations") {
      val builder = MigrationBuilder
        .create(PersonV0.schema, PersonV1.schema)
        .addField("email", litStr("test@test.com"))
      val partial   = builder.buildPartial
      val validated = builder.build.toOption.get
      val input     = PersonV0("Alice", 30)
      assertTrue(partial(input) == validated(input))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // AddAction (raw)
  // ─────────────────────────────────────────────────────────────────────────

  private val addActionSuite = suite("addAction")(
    test("adds a raw MigrationAction") {
      val rawAction = MigrationAction.AddField(DynamicOptic.root, "email", litStr("raw@test.com"))
      val migration = MigrationBuilder
        .create(PersonV0.schema, PersonV1.schema)
        .addAction(rawAction)
        .buildPartial
      val result = migration(PersonV0("Alice", 30))
      assertTrue(result == Right(PersonV1("Alice", 30, "raw@test.com")))
    },
    test("raw action is appended to existing actions") {
      val rawAction = MigrationAction.Rename(DynamicOptic.root, "a", "b")
      val builder   = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .addField("z", litInt(0))
        .addAction(rawAction)
      assertTrue(builder.actions.size == 2)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Migration.newBuilder Factory
  // ─────────────────────────────────────────────────────────────────────────

  private val newBuilderFactorySuite = suite("Migration.newBuilder")(
    test("creates a working builder via implicit schemas") {
      val migration = Migration
        .newBuilder[PersonV0, PersonV1]
        .addField("email", litStr("factory@test.com"))
        .buildPartial
      val result = migration(PersonV0("Charlie", 40))
      assertTrue(result == Right(PersonV1("Charlie", 40, "factory@test.com")))
    },
    test("newBuilder starts with empty actions") {
      val builder = Migration.newBuilder[SimpleRecord, SimpleRecord]
      assertTrue(builder.actions.isEmpty)
    },
    test("newBuilder produces empty migration that is identity") {
      val migration = Migration.newBuilder[SimpleRecord, SimpleRecord].buildPartial
      assertTrue(migration.isEmpty)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Mandate / Optionalize
  // ─────────────────────────────────────────────────────────────────────────

  private val mandateFieldSuite = suite("mandateField")(
    test("accumulates a Mandate action") {
      val builder = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .mandateField("optField", litInt(0))
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.Mandate(at, fieldName, _) =>
            assertTrue(at == DynamicOptic.root, fieldName == "optField")
          case _ => assertTrue(false)
        }
      }
    }
  )

  private val optionalizeFieldSuite = suite("optionalizeField")(
    test("accumulates an Optionalize action") {
      val builder = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .optionalizeField("reqField", litInt(0))
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.Optionalize(at, fieldName, _) =>
            assertTrue(at == DynamicOptic.root, fieldName == "reqField")
          case _ => assertTrue(false)
        }
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Join / Split
  // ─────────────────────────────────────────────────────────────────────────

  private val joinSplitSuite = suite("joinFields and splitField")(
    test("joinFields accumulates a Join action") {
      val joinExpr   = MigrationExpr.Concat(fieldRef("first"), fieldRef("last"))
      val splitExprs = Chunk(("first", litStr("John")), ("last", litStr("Doe")))
      val builder    = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .joinFields(Chunk("first", "last"), "fullName", joinExpr, splitExprs)
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.Join(at, sourceFields, targetField, _, _) =>
            assertTrue(
              at == DynamicOptic.root,
              sourceFields == Chunk("first", "last"),
              targetField == "fullName"
            )
          case _ => assertTrue(false)
        }
      }
    },
    test("splitField accumulates a Split action") {
      val targetExprs     = Chunk(("first", litStr("John")), ("last", litStr("Doe")))
      val joinExprReverse = MigrationExpr.Concat(fieldRef("first"), fieldRef("last"))
      val builder         = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .splitField("fullName", targetExprs, joinExprReverse)
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.Split(at, sourceField, _, _) =>
            assertTrue(at == DynamicOptic.root, sourceField == "fullName")
          case _ => assertTrue(false)
        }
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Collection Operations
  // ─────────────────────────────────────────────────────────────────────────

  private val collectionOperationsSuite = suite("collection operations")(
    test("transformElements accumulates action with correct path") {
      val builder = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .transformElements(DynamicOptic.root.field("items"), litInt(0), litInt(0))
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.TransformElements(at, _, _) =>
            assertTrue(at == DynamicOptic.root.field("items"))
          case _ => assertTrue(false)
        }
      }
    },
    test("transformKeys accumulates action with correct path") {
      val builder = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .transformKeys(DynamicOptic.root.field("mapping"), litStr("x"), litStr("x"))
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.TransformKeys(at, _, _) =>
            assertTrue(at == DynamicOptic.root.field("mapping"))
          case _ => assertTrue(false)
        }
      }
    },
    test("transformValues accumulates action with correct path") {
      val builder = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .transformValues(DynamicOptic.root.field("mapping"), litInt(0), litInt(0))
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.TransformValues(at, _, _) =>
            assertTrue(at == DynamicOptic.root.field("mapping"))
          case _ => assertTrue(false)
        }
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // At-Variants (nested path operations)
  // ─────────────────────────────────────────────────────────────────────────

  private val atVariantsSuite = suite("at-variants for nested paths")(
    test("renameFieldAt targets a nested path") {
      val builder = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .renameFieldAt(DynamicOptic.root.field("inner"), "old", "new")
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.Rename(at, fromName, toName) =>
            assertTrue(at == DynamicOptic.root.field("inner"), fromName == "old", toName == "new")
          case _ => assertTrue(false)
        }
      }
    },
    test("dropFieldAt targets a nested path") {
      val builder = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .dropFieldAt(DynamicOptic.root.field("inner"), "field", litInt(0))
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.DropField(at, fieldName, _) =>
            assertTrue(at == DynamicOptic.root.field("inner"), fieldName == "field")
          case _ => assertTrue(false)
        }
      }
    },
    test("renameCaseAt targets a nested path") {
      val builder = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .renameCaseAt(DynamicOptic.root.field("status"), "Active", "Enabled")
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.RenameCase(at, fromCase, toCase) =>
            assertTrue(at == DynamicOptic.root.field("status"), fromCase == "Active", toCase == "Enabled")
          case _ => assertTrue(false)
        }
      }
    },
    test("transformCase accumulates correct TransformCase action") {
      val innerActions = Chunk[MigrationAction](
        MigrationAction.AddField(DynamicOptic.root, "extra", litInt(0))
      )
      val builder = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .transformCase("MyCase", innerActions)
      assertTrue(builder.actions.size == 1) && {
        builder.actions(0) match {
          case MigrationAction.TransformCase(at, caseName, actions) =>
            assertTrue(at == DynamicOptic.root, caseName == "MyCase", actions.size == 1)
          case _ => assertTrue(false)
        }
      }
    }
  )
}
