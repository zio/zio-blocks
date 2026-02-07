package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationSerializationSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Migration Serialization")(
    suite("Schema serialization")(
      test("PrimitiveConversion round-trips through DynamicValue") {
        val conversions: Vector[PrimitiveConversion] = Vector(
          PrimitiveConversion.IntToLong,
          PrimitiveConversion.LongToInt,
          PrimitiveConversion.IntToString,
          PrimitiveConversion.StringToInt
        )
        val schema  = Schema[PrimitiveConversion]
        val results = conversions.map { conv =>
          val dv = schema.toDynamicValue(conv)
          schema.fromDynamicValue(dv)
        }
        assertTrue(results.forall(_.isRight)) &&
        assertTrue(results.map(_.toOption.get) == conversions)
      },
      test("MigrationError round-trips through DynamicValue") {
        val errors: Vector[MigrationError] = Vector(
          MigrationError.FieldNotFound(DynamicOptic.root, "name"),
          MigrationError.TypeMismatch(DynamicOptic.root.field("x"), "Record", "Int"),
          MigrationError.TransformFailed(DynamicOptic.root, "reason")
        )
        val schema  = Schema[MigrationError]
        val results = errors.map { e =>
          val dv = schema.toDynamicValue(e)
          schema.fromDynamicValue(dv)
        }
        assertTrue(results.forall(_.isRight)) &&
        assertTrue(results.map(_.toOption.get) == errors)
      }
    ),
    suite("DynamicMigration serialization")(
      test("Complex record migration round-trips through DynamicValue") {
        val migration = DynamicMigration.record(
          _.addField("newField", DynamicValue.int(42))
            .removeField("oldField", DynamicValue.string("default"))
            .renameField("from", "to")
            .transformField("value", DynamicValueTransform.numericAdd(10), DynamicValueTransform.numericAdd(-10))
        )

        val schema   = Schema[DynamicMigration]
        val dv       = schema.toDynamicValue(migration)
        val restored = schema.fromDynamicValue(dv)

        assertTrue(restored.isRight)

        val original = DynamicValue.Record(
          "oldField" -> DynamicValue.string("removed"),
          "from"     -> DynamicValue.string("renamed"),
          "value"    -> DynamicValue.int(5)
        )

        val resultOriginal = migration(original)
        val resultRestored = restored.toOption.get.apply(original)

        assertTrue(resultOriginal == resultRestored)
      },
      test("Nested migration with multiple levels serializes correctly") {
        val migration = DynamicMigration.record(
          _.nested("level1")(
            _.nested("level2")(
              _.addField("deep", DynamicValue.boolean(true))
                .renameField("old", "new")
            )
          )
        )

        val schema   = Schema[DynamicMigration]
        val dv       = schema.toDynamicValue(migration)
        val restored = schema.fromDynamicValue(dv)

        assertTrue(restored.isRight)

        val original = DynamicValue.Record(
          "level1" -> DynamicValue.Record(
            "level2" -> DynamicValue.Record(
              "old" -> DynamicValue.string("value")
            )
          )
        )

        val resultOriginal = migration(original)
        val resultRestored = restored.toOption.get.apply(original)

        assertTrue(resultOriginal == resultRestored)
      },
      test("Variant migration with renames and nested transforms serializes correctly") {
        val migration = DynamicMigration.variant(
          _.renameCase("OldCase", "NewCase")
            .transformCase("NewCase")(_.addField("extra", DynamicValue.int(100)))
        )

        val schema   = Schema[DynamicMigration]
        val dv       = schema.toDynamicValue(migration)
        val restored = schema.fromDynamicValue(dv)

        assertTrue(restored.isRight)

        val original = DynamicValue.Variant("OldCase", DynamicValue.Record("data" -> DynamicValue.string("test")))

        val resultOriginal = migration(original)
        val resultRestored = restored.toOption.get.apply(original)

        assertTrue(resultOriginal == resultRestored)
      },
      test("Sequence and Map migrations serialize correctly") {
        val seqMigration = DynamicMigration(
          MigrationStep.Sequence(MigrationStep.Record.empty.addField("index", DynamicValue.int(0)))
        )
        val mapMigration = DynamicMigration(
          MigrationStep.MapEntries(
            MigrationStep.Record.empty.renameField("id", "key"),
            MigrationStep.Record.empty.addField("version", DynamicValue.int(1))
          )
        )

        val schema = Schema[DynamicMigration]

        val seqDv       = schema.toDynamicValue(seqMigration)
        val seqRestored = schema.fromDynamicValue(seqDv)

        val mapDv       = schema.toDynamicValue(mapMigration)
        val mapRestored = schema.fromDynamicValue(mapDv)

        assertTrue(
          seqRestored.isRight,
          mapRestored.isRight,
          seqRestored.toOption.get.step == seqMigration.step,
          mapRestored.toOption.get.step == mapMigration.step
        )
      }
    ),
    suite("FieldAction serialization")(
      test("All FieldAction types round-trip through DynamicValue") {
        val actions: Vector[FieldAction] = Vector(
          FieldAction.Add("field", DynamicValue.int(42)),
          FieldAction.Remove("field", DynamicValue.string("default")),
          FieldAction.Rename("old", "new"),
          FieldAction.Transform("field", DynamicValueTransform.identity, DynamicValueTransform.identity),
          FieldAction.MakeOptional("field", DynamicValue.Null),
          FieldAction.MakeRequired("field", DynamicValue.int(0)),
          FieldAction.ChangeType("field", PrimitiveConversion.IntToLong, PrimitiveConversion.LongToInt),
          FieldAction
            .JoinFields("target", Vector("a", "b"), DynamicValueTransform.identity, DynamicValueTransform.identity),
          FieldAction.SplitField(
            "source",
            Vector("x", "y"),
            DynamicValueTransform.identity,
            DynamicValueTransform.identity
          )
        )

        val schema  = Schema[FieldAction]
        val results = actions.map { action =>
          val dv       = schema.toDynamicValue(action)
          val restored = schema.fromDynamicValue(dv)
          restored.isRight && restored.toOption.get == action
        }

        assertTrue(results.forall(_ == true))
      }
    ),
    suite("MigrationStep serialization")(
      test("All MigrationStep types round-trip through DynamicValue") {
        val steps: Vector[MigrationStep] = Vector(
          MigrationStep.NoOp,
          MigrationStep.Record.empty.addField("x", DynamicValue.int(1)),
          MigrationStep.Variant.empty.renameCase("A", "B"),
          MigrationStep.Sequence(MigrationStep.Record.empty.addField("i", DynamicValue.int(0))),
          MigrationStep.MapEntries(
            MigrationStep.Record.empty.renameField("id", "key"),
            MigrationStep.Record.empty.addField("version", DynamicValue.int(1))
          )
        )

        val schema  = Schema[MigrationStep]
        val results = steps.map { step =>
          val dv       = schema.toDynamicValue(step)
          val restored = schema.fromDynamicValue(dv)
          restored.isRight && restored.toOption.get == step
        }

        assertTrue(results.forall(_ == true))
      }
    ),
    suite("DynamicValueTransform serialization")(
      test("All DynamicValueTransform types round-trip through DynamicValue") {
        val transforms: Vector[DynamicValueTransform] = Vector(
          DynamicValueTransform.identity,
          DynamicValueTransform.constant(DynamicValue.string("const")),
          DynamicValueTransform.stringAppend(" suffix"),
          DynamicValueTransform.stringPrepend("prefix "),
          DynamicValueTransform.stringReplace("old", "new"),
          DynamicValueTransform.numericAdd(10),
          DynamicValueTransform.numericMultiply(2),
          DynamicValueTransform.wrapInSome,
          DynamicValueTransform.unwrapSome(DynamicValue.Null),
          DynamicValueTransform.stringJoinFields(Vector("a", "b"), "-"),
          DynamicValueTransform.stringSplitToFields(Vector("x", "y"), "-")
        )

        val schema  = Schema[DynamicValueTransform]
        val results = transforms.map { transform =>
          val dv       = schema.toDynamicValue(transform)
          val restored = schema.fromDynamicValue(dv)
          restored.isRight
        }

        assertTrue(results.forall(_ == true))
      }
    )
  )
}
