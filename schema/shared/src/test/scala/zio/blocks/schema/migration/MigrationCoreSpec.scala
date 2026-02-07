package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationCoreSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Migration Core")(
    migrationExprSuite,
    migrationStepSuite,
    fieldActionSuite
  )

  private val migrationExprSuite = suite("MigrationExpr")(
    test("Literal returns constant DynamicValue") {
      val expr   = MigrationExpr.literal(42)
      val result = expr.evalDynamic(DynamicValue.Null)
      assertTrue(result == Right(DynamicValue.int(42)))
    },
    test("DefaultValue extracts schema default when available") {
      val schemaWithDefault = Schema[Int].defaultValue(99)
      val expr              = MigrationExpr.DefaultValue[Any](schemaWithDefault)
      val result            = expr.evalDynamic(DynamicValue.Null)
      assertTrue(result == Right(DynamicValue.int(99)))
    },
    test("DefaultValue fails when schema has no default") {
      val schemaWithoutDefault = Schema[Int]
      val expr                 = MigrationExpr.DefaultValue[Any](schemaWithoutDefault)
      val result               = expr.evalDynamic(DynamicValue.Null)
      assertTrue(result.isLeft)
    },
    test("FieldAccess retrieves value from input") {
      val input  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
      val expr   = MigrationExpr.field[Any, String](DynamicOptic.root.field("name"))
      val result = expr.evalDynamic(input)
      assertTrue(result == Right(DynamicValue.string("Alice")))
    },
    test("Transform applies DynamicValueTransform to result") {
      val expr = MigrationExpr.transform[Any, Int, Int](
        MigrationExpr.literal(10),
        DynamicValueTransform.numericMultiply(2)
      )
      val result = expr.evalDynamic(DynamicValue.Null)
      assertTrue(result == Right(DynamicValue.int(20)))
    },
    test("Concat joins two string expressions") {
      val expr = MigrationExpr.concat(
        MigrationExpr.literal("Hello"),
        MigrationExpr.literal(" World")
      )
      val result = expr.evalDynamic(DynamicValue.Null)
      assertTrue(result == Right(DynamicValue.string("Hello World")))
    },
    test("PrimitiveConvert applies conversion") {
      val expr = MigrationExpr.convert[Any, Int, Long](
        MigrationExpr.literal(42),
        PrimitiveConversion.IntToLong
      )
      val result = expr.evalDynamic(DynamicValue.Null)
      assertTrue(result == Right(DynamicValue.long(42L)))
    },
    test("FieldAccess fails when field is not found") {
      val input  = DynamicValue.Record("other" -> DynamicValue.string("value"))
      val expr   = MigrationExpr.field[Any, String](DynamicOptic.root.field("name"))
      val result = expr.evalDynamic(input)
      assertTrue(
        result.isLeft,
        result.swap.getOrElse("").contains("Field not found")
      )
    },
    test("Concat fails when left side is not a string") {
      val expr = MigrationExpr.Concat(
        MigrationExpr
          .Transform(
            MigrationExpr.literal(42),
            DynamicValueTransform.identity
          )
          .asInstanceOf[MigrationExpr[Any, String]],
        MigrationExpr.literal(" world")
      )
      val result = expr.evalDynamic(DynamicValue.Null)
      assertTrue(
        result.isLeft,
        result.swap.getOrElse("").contains("Expected string on left side")
      )
    },
    test("Concat fails when right side is not a string") {
      val expr = MigrationExpr.Concat(
        MigrationExpr.literal("hello "),
        MigrationExpr
          .Transform(
            MigrationExpr.literal(42),
            DynamicValueTransform.identity
          )
          .asInstanceOf[MigrationExpr[Any, String]]
      )
      val result = expr.evalDynamic(DynamicValue.Null)
      assertTrue(
        result.isLeft,
        result.swap.getOrElse("").contains("Expected string on right side")
      )
    },
    test("Concat fails when left side is non-Primitive DynamicValue") {
      val expr = MigrationExpr.Concat(
        MigrationExpr
          .Transform(
            MigrationExpr.literal(42),
            DynamicValueTransform.constant(DynamicValue.Record("x" -> DynamicValue.int(1)))
          )
          .asInstanceOf[MigrationExpr[Any, String]],
        MigrationExpr.literal(" world")
      )
      val result = expr.evalDynamic(DynamicValue.Null)
      assertTrue(
        result.isLeft,
        result.swap.getOrElse("").contains("Expected string on left side")
      )
    },
    test("Concat fails when right side is non-Primitive DynamicValue") {
      val expr = MigrationExpr.Concat(
        MigrationExpr.literal("hello "),
        MigrationExpr
          .Transform(
            MigrationExpr.literal(42),
            DynamicValueTransform.constant(DynamicValue.Record("x" -> DynamicValue.int(1)))
          )
          .asInstanceOf[MigrationExpr[Any, String]]
      )
      val result = expr.evalDynamic(DynamicValue.Null)
      assertTrue(
        result.isLeft,
        result.swap.getOrElse("").contains("Expected string on right side")
      )
    }
  )

  private val migrationStepSuite = suite("MigrationStep")(
    suite("Basic operations")(
      test("NoOp step does nothing") {
        val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration = DynamicMigration(MigrationStep.NoOp)
        val result    = migration(original)

        assertTrue(result == Right(original))
      },
      test("Empty record step is equivalent to NoOp") {
        val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration = DynamicMigration(MigrationStep.Record.empty)
        val result    = migration(original)

        assertTrue(result == Right(original))
      }
    ),
    suite("isEmpty")(
      test("Sequence.isEmpty returns true when elementStep is NoOp") {
        val seq = MigrationStep.Sequence(MigrationStep.NoOp)
        assertTrue(seq.isEmpty)
      },
      test("Sequence.isEmpty returns false when elementStep has actions") {
        val seq = MigrationStep.Sequence(MigrationStep.Record.empty.addField("x", DynamicValue.int(1)))
        assertTrue(!seq.isEmpty)
      },
      test("MapEntries.isEmpty returns true when both key and value steps are NoOp") {
        val mapEntries = MigrationStep.MapEntries(MigrationStep.NoOp, MigrationStep.NoOp)
        assertTrue(mapEntries.isEmpty)
      },
      test("MapEntries.isEmpty returns false when keyStep has actions") {
        val mapEntries = MigrationStep.MapEntries(
          MigrationStep.Record.empty.addField("k", DynamicValue.int(1)),
          MigrationStep.NoOp
        )
        assertTrue(!mapEntries.isEmpty)
      },
      test("MapEntries.isEmpty returns false when valueStep has actions") {
        val mapEntries = MigrationStep.MapEntries(
          MigrationStep.NoOp,
          MigrationStep.Record.empty.addField("v", DynamicValue.int(1))
        )
        assertTrue(!mapEntries.isEmpty)
      },
      test("Record.isEmpty returns true with empty fieldActions and empty nestedFields") {
        val record = MigrationStep.Record.empty
        assertTrue(record.isEmpty)
      },
      test("Record.isEmpty returns true with empty fieldActions and all-empty nestedFields") {
        val record = MigrationStep.Record(Vector.empty, Map("nested" -> MigrationStep.NoOp))
        assertTrue(record.isEmpty)
      },
      test("Variant.isEmpty returns true with empty renames and all-empty nestedCases") {
        val variant = MigrationStep.Variant(Map.empty, Map("case" -> MigrationStep.NoOp))
        assertTrue(variant.isEmpty)
      }
    ),
    suite("reverse")(
      test("Record.reverse reverses fieldActions and updates nestedFields keys") {
        val record = MigrationStep.Record.empty
          .addField("a", DynamicValue.int(1))
          .renameField("x", "y")
          .nested("y")(_.addField("inner", DynamicValue.string("test")))

        val reversed = record.reverse.asInstanceOf[MigrationStep.Record]

        val renamedBack = reversed.fieldActions.exists {
          case FieldAction.Rename("y", "x") => true
          case _                            => false
        }
        assertTrue(
          renamedBack,
          reversed.nestedFields.contains("x")
        )
      },
      test("Variant.reverse reverses renames and updates nestedCases keys") {
        val variant = MigrationStep.Variant.empty
          .renameCase("CaseA", "CaseB")
          .nested("CaseB")(_.addField("field", DynamicValue.int(1)))

        val reversed = variant.reverse.asInstanceOf[MigrationStep.Variant]

        assertTrue(
          reversed.renames.get("CaseB") == Some("CaseA"),
          reversed.nestedCases.contains("CaseA")
        )
      },
      test("Sequence.reverse reverses the elementStep") {
        val seq = MigrationStep.Sequence(
          MigrationStep.Record.empty.addField("x", DynamicValue.int(1))
        )
        val reversed = seq.reverse.asInstanceOf[MigrationStep.Sequence]

        val innerReversed = reversed.elementStep.asInstanceOf[MigrationStep.Record]
        assertTrue(
          innerReversed.fieldActions.exists {
            case FieldAction.Remove("x", _) => true
            case _                          => false
          }
        )
      },
      test("MapEntries.reverse reverses both key and value steps") {
        val mapEntries = MigrationStep.MapEntries(
          MigrationStep.Record.empty.addField("k", DynamicValue.int(1)),
          MigrationStep.Record.empty.addField("v", DynamicValue.int(2))
        )
        val reversed = mapEntries.reverse.asInstanceOf[MigrationStep.MapEntries]

        val keyReversed   = reversed.keyStep.asInstanceOf[MigrationStep.Record]
        val valueReversed = reversed.valueStep.asInstanceOf[MigrationStep.Record]

        assertTrue(
          keyReversed.fieldActions.exists {
            case FieldAction.Remove("k", _) => true
            case _                          => false
          },
          valueReversed.fieldActions.exists {
            case FieldAction.Remove("v", _) => true
            case _                          => false
          }
        )
      },
      test("NoOp.reverse returns NoOp") {
        val noop     = MigrationStep.NoOp
        val reversed = noop.reverse
        assertTrue(reversed == MigrationStep.NoOp)
      },
      test("Record.nested starts fresh Record when existing nested is not a Record") {
        val record = MigrationStep.Record(
          Vector.empty,
          Map("field" -> MigrationStep.Sequence(MigrationStep.NoOp))
        )
        val updated = record.nested("field")(_.addField("newField", DynamicValue.int(1)))
        assertTrue(
          updated.nestedFields("field").isInstanceOf[MigrationStep.Record],
          updated.nestedFields("field").asInstanceOf[MigrationStep.Record].fieldActions.nonEmpty
        )
      },
      test("Variant.nested starts fresh Record when existing nested is not a Record") {
        val variant = MigrationStep.Variant(
          Map.empty,
          Map("CaseA" -> MigrationStep.NoOp)
        )
        val updated = variant.nested("CaseA")(_.addField("newField", DynamicValue.int(1)))
        assertTrue(
          updated.nestedCases("CaseA").isInstanceOf[MigrationStep.Record],
          updated.nestedCases("CaseA").asInstanceOf[MigrationStep.Record].fieldActions.nonEmpty
        )
      }
    )
  )

  private val fieldActionSuite = suite("FieldAction")(
    suite("Basic operations")(
      test("Add field adds a new field to a record") {
        val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration = DynamicMigration.record(_.addField("age", DynamicValue.int(30)))
        val result    = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "name" -> DynamicValue.string("Alice"),
              "age"  -> DynamicValue.int(30)
            )
          )
        )
      },
      test("Remove field removes a field from a record") {
        val original = DynamicValue.Record(
          "name" -> DynamicValue.string("Alice"),
          "age"  -> DynamicValue.int(30)
        )
        val migration = DynamicMigration.record(_.removeField("age", DynamicValue.int(0)))
        val result    = migration(original)

        assertTrue(result == Right(DynamicValue.Record("name" -> DynamicValue.string("Alice"))))
      },
      test("Rename field renames a field in a record") {
        val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration = DynamicMigration.record(_.renameField("name", "fullName"))
        val result    = migration(original)

        assertTrue(result == Right(DynamicValue.Record("fullName" -> DynamicValue.string("Alice"))))
      },
      test("Transform field applies a transformation to a field value") {
        val original  = DynamicValue.Record("count" -> DynamicValue.int(5))
        val migration = DynamicMigration.record(
          _.transformField(
            "count",
            DynamicValueTransform.numericAdd(10),
            DynamicValueTransform.numericAdd(-10)
          )
        )
        val result = migration(original)

        assertTrue(result == Right(DynamicValue.Record("count" -> DynamicValue.int(15))))
      },
      test("MakeOptional wraps a field value in Some") {
        val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration = DynamicMigration.record(_.makeFieldOptional("name", DynamicValue.string("")))
        val result    = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val nameField = fields.find(_._1 == "name").map(_._2)
            nameField match {
              case Some(DynamicValue.Variant("Some", _)) => assertTrue(true)
              case _                                     => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },
      test("MakeRequired unwraps Some to extract the inner value") {
        val original = DynamicValue.Record(
          "name" -> DynamicValue.Variant("Some", DynamicValue.Record("value" -> DynamicValue.string("Alice")))
        )
        val migration = DynamicMigration.record(_.makeFieldRequired("name", DynamicValue.string("")))
        val result    = migration(original)

        assertTrue(result == Right(DynamicValue.Record("name" -> DynamicValue.string("Alice"))))
      },
      test("MakeRequired uses default for None") {
        val original = DynamicValue.Record(
          "name" -> DynamicValue.Variant("None", DynamicValue.Record())
        )
        val migration = DynamicMigration.record(_.makeFieldRequired("name", DynamicValue.string("default")))
        val result    = migration(original)

        assertTrue(result == Right(DynamicValue.Record("name" -> DynamicValue.string("default"))))
      },
      test("ChangeType converts field value type") {
        val original  = DynamicValue.Record("count" -> DynamicValue.int(42))
        val migration = DynamicMigration.record(
          _.changeFieldType(
            "count",
            PrimitiveConversion.IntToLong,
            PrimitiveConversion.LongToInt
          )
        )
        val result = migration(original)

        assertTrue(result == Right(DynamicValue.Record("count" -> DynamicValue.long(42L))))
      }
    ),
    suite("Chained field actions")(
      test("Multiple field actions are applied in order") {
        val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration = DynamicMigration.record(
          _.addField("age", DynamicValue.int(30))
            .renameField("name", "fullName")
            .addField("active", DynamicValue.boolean(true))
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "fullName" -> DynamicValue.string("Alice"),
              "age"      -> DynamicValue.int(30),
              "active"   -> DynamicValue.boolean(true)
            )
          )
        )
      }
    ),
    suite("reverse methods")(
      test("Add.reverse returns Remove") {
        val add      = FieldAction.Add("field", DynamicValue.int(42))
        val reversed = add.reverse
        assertTrue(reversed == FieldAction.Remove("field", DynamicValue.int(42)))
      },
      test("Remove.reverse returns Add") {
        val remove   = FieldAction.Remove("field", DynamicValue.string("default"))
        val reversed = remove.reverse
        assertTrue(reversed == FieldAction.Add("field", DynamicValue.string("default")))
      },
      test("Rename.reverse swaps from and to") {
        val rename   = FieldAction.Rename("old", "new")
        val reversed = rename.reverse
        assertTrue(reversed == FieldAction.Rename("new", "old"))
      },
      test("Transform.reverse swaps forward and backward") {
        val transform = FieldAction.Transform(
          "field",
          DynamicValueTransform.stringAppend(" suffix"),
          DynamicValueTransform.stringPrepend("prefix ")
        )
        val reversed = transform.reverse
        reversed match {
          case FieldAction.Transform(name, fwd, bwd) =>
            assertTrue(
              name == "field",
              fwd == DynamicValueTransform.stringPrepend("prefix "),
              bwd == DynamicValueTransform.stringAppend(" suffix")
            )
          case _ => assertTrue(false)
        }
      },
      test("MakeOptional.reverse returns MakeRequired") {
        val makeOpt  = FieldAction.MakeOptional("field", DynamicValue.int(0))
        val reversed = makeOpt.reverse
        assertTrue(reversed == FieldAction.MakeRequired("field", DynamicValue.int(0)))
      },
      test("MakeRequired.reverse returns MakeOptional") {
        val makeReq  = FieldAction.MakeRequired("field", DynamicValue.string("default"))
        val reversed = makeReq.reverse
        assertTrue(reversed == FieldAction.MakeOptional("field", DynamicValue.string("default")))
      },
      test("ChangeType.reverse swaps forward and backward conversions") {
        val changeType = FieldAction.ChangeType(
          "field",
          PrimitiveConversion.IntToLong,
          PrimitiveConversion.LongToInt
        )
        val reversed = changeType.reverse
        assertTrue(
          reversed == FieldAction.ChangeType("field", PrimitiveConversion.LongToInt, PrimitiveConversion.IntToLong)
        )
      },
      test("JoinFields.reverse returns SplitField") {
        val join = FieldAction.JoinFields(
          "combined",
          Vector("a", "b"),
          DynamicValueTransform.identity,
          DynamicValueTransform.constant(DynamicValue.string("split"))
        )
        val reversed = join.reverse
        reversed match {
          case FieldAction.SplitField(sourceName, targetNames, splitter, combiner) =>
            assertTrue(
              sourceName == "combined",
              targetNames == Vector("a", "b"),
              splitter == DynamicValueTransform.constant(DynamicValue.string("split")),
              combiner == DynamicValueTransform.identity
            )
          case _ => assertTrue(false)
        }
      },
      test("SplitField.reverse returns JoinFields") {
        val split = FieldAction.SplitField(
          "source",
          Vector("x", "y"),
          DynamicValueTransform.identity,
          DynamicValueTransform.constant(DynamicValue.string("joined"))
        )
        val reversed = split.reverse
        reversed match {
          case FieldAction.JoinFields(targetName, sourceNames, combiner, splitter) =>
            assertTrue(
              targetName == "source",
              sourceNames == Vector("x", "y"),
              combiner == DynamicValueTransform.constant(DynamicValue.string("joined")),
              splitter == DynamicValueTransform.identity
            )
          case _ => assertTrue(false)
        }
      }
    )
  )
}
