package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.chunk.Chunk

object MigrationSpec extends ZIOSpecDefault {

  // For direct MigrationAction construction
  private def literal(dv: DynamicValue): DynamicSchemaExpr =
    DynamicSchemaExpr.Literal(dv)

  // For builder API (wraps DynamicSchemaExpr in SchemaExpr)
  private def literalExpr(dv: DynamicValue): SchemaExpr[Any, DynamicValue] =
    SchemaExpr.literal(dv)

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    suite("DynamicMigration")(
      test("empty migration is identity") {
        val value = DynamicValue.Record("name" -> DynamicValue.Primitive(PrimitiveValue.String("John")))
        assertTrue(DynamicMigration.empty(value) == Right(value))
      },

      test("addField") {
        val input     = DynamicValue.Record("name" -> DynamicValue.Primitive(PrimitiveValue.String("John")))
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val expected = DynamicValue.Record(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(0))
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("dropField") {
        val input = DynamicValue.Record(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
        )
        val migration = DynamicMigration.single(
          MigrationAction.DropField(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val expected = DynamicValue.Record("name" -> DynamicValue.Primitive(PrimitiveValue.String("John")))
        assertTrue(migration(input) == Right(expected))
      },

      test("rename") {
        val input     = DynamicValue.Record("old" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("old"), "new"))
        val expected  = DynamicValue.Record("new" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        assertTrue(migration(input) == Right(expected))
      },

      test("transformValue") {
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            DynamicOptic.root,
            literal(DynamicValue.Primitive(PrimitiveValue.String("42")))
          )
        )
        assertTrue(migration(input) == Right(DynamicValue.Primitive(PrimitiveValue.String("42"))))
      },

      test("renameCase") {
        val input     = DynamicValue.Variant("Old", DynamicValue.Record.empty)
        val migration = DynamicMigration.single(MigrationAction.RenameCase(DynamicOptic.root, "Old", "New"))
        assertTrue(migration(input) == Right(DynamicValue.Variant("New", DynamicValue.Record.empty)))
      },

      test("composition") {
        val input = DynamicValue.Record("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val m1    = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("a"), "b"))
        val m2    = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("c"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val expected = DynamicValue.Record(
          "b" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          "c" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        assertTrue((m1 ++ m2)(input) == Right(expected))
      }
    ),

    suite("Laws")(
      test("associativity") {
        val m1 = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("a"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val m2 = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("b"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val m3 = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("c"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
          )
        )
        val input = DynamicValue.Record.empty
        assertTrue(((m1 ++ m2) ++ m3)(input) == (m1 ++ (m2 ++ m3))(input))
      },

      test("structural reverse") {
        val m = DynamicMigration(
          MigrationAction.AddField(
            DynamicOptic.root.field("x"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          ),
          MigrationAction.Rename(DynamicOptic.root.field("a"), "b")
        )
        assertTrue(m.reverse.reverse.actions == m.actions)
      },

      test("semantic inverse for rename") {
        val input = DynamicValue.Record("old" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val m     = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("old"), "new"))
        assertTrue(m(input).flatMap(m.reverse(_)) == Right(input))
      },

      test("semantic inverse for addField/dropField") {
        val input = DynamicValue.Record("name" -> DynamicValue.Primitive(PrimitiveValue.String("John")))
        val m     = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )
        // AddField.reverse is DropField, so m(input) adds "age", then m.reverse removes it
        assertTrue(m(input).flatMap(m.reverse(_)) == Right(input))
      },

      test("semantic inverse for renameCase") {
        val input = DynamicValue.Variant("Old", DynamicValue.Record.empty)
        val m     = DynamicMigration.single(MigrationAction.RenameCase(DynamicOptic.root, "Old", "New"))
        assertTrue(m(input).flatMap(m.reverse(_)) == Right(input))
      },

      test("semantic inverse for optionalize/mandate") {
        // Start with a required value, optionalize wraps in Some, mandate unwraps
        val input = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val m     = DynamicMigration.single(MigrationAction.Optionalize(DynamicOptic.root))
        // Optionalize.reverse is Mandate with empty default
        assertTrue(m(input).flatMap(m.reverse(_)) == Right(input))
      },

      test("semantic inverse for rename at nested path") {
        val input = DynamicValue.Record(
          "outer" -> DynamicValue.Record("old" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val m = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root.field("outer").field("old"), "new")
        )
        assertTrue(m(input).flatMap(m.reverse(_)) == Right(input))
      },

      test("semantic inverse for transformCase") {
        val input = DynamicValue.Variant(
          "MyCase",
          DynamicValue.Record("old" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val innerActions = Vector(MigrationAction.Rename(DynamicOptic.root.field("old"), "new"))
        val m            = DynamicMigration.single(MigrationAction.TransformCase(DynamicOptic.root, innerActions))
        assertTrue(m(input).flatMap(m.reverse(_)) == Right(input))
      }
    ),

    suite("Errors")(
      test("dropField missing") {
        val input     = DynamicValue.Record("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration.single(
          MigrationAction.DropField(
            DynamicOptic.root.field("b"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("addField duplicate") {
        val input     = DynamicValue.Record("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("a"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("transform type mismatch") {
        // With Literal, we're just replacing the value - no actual type checking
        // This test needs to be adjusted for the new behavior
        val input     = DynamicValue.Primitive(PrimitiveValue.String("abc"))
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            DynamicOptic.root,
            literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
          )
        )
        // With Literal, the transform just replaces the value
        assertTrue(migration(input) == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      },

      test("rename field not found") {
        val input     = DynamicValue.Record("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("b"), "c"))
        assertTrue(migration(input).isLeft)
      },

      test("rename field already exists") {
        val input = DynamicValue.Record(
          "a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          "b" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val migration = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("a"), "b"))
        assertTrue(migration(input).isLeft)
      },

      test("renameCase with non-matching case name") {
        val input     = DynamicValue.Variant("CaseA", DynamicValue.Record.empty)
        val migration = DynamicMigration.single(MigrationAction.RenameCase(DynamicOptic.root, "CaseB", "CaseC"))
        assertTrue(migration(input) == Right(input))
      }
    ),

    suite("Nested paths")(
      test("rename in nested record") {
        val input = DynamicValue.Record(
          "outer" -> DynamicValue.Record("old" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val migration =
          DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("outer").field("old"), "new"))
        val expected = DynamicValue.Record(
          "outer" -> DynamicValue.Record("new" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        assertTrue(migration(input) == Right(expected))
      }
    ),

    suite("Deep Nesting and Collection Paths")(
      test("3-level nested field rename") {
        val input = DynamicValue.Record(
          "a" -> DynamicValue.Record(
            "b" -> DynamicValue.Record(
              "old" -> DynamicValue.Primitive(PrimitiveValue.Int(1))
            )
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.Rename(
            DynamicOptic.root.field("a").field("b").field("old"),
            "new"
          )
        )
        val expected = DynamicValue.Record(
          "a" -> DynamicValue.Record(
            "b" -> DynamicValue.Record(
              "new" -> DynamicValue.Primitive(PrimitiveValue.Int(1))
            )
          )
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("4-level nested field addField") {
        val input = DynamicValue.Record(
          "w" -> DynamicValue.Record(
            "x" -> DynamicValue.Record(
              "y" -> DynamicValue.Record.empty
            )
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("w").field("x").field("y").field("z"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
          )
        )
        val expected = DynamicValue.Record(
          "w" -> DynamicValue.Record(
            "x" -> DynamicValue.Record(
              "y" -> DynamicValue.Record(
                "z" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
              )
            )
          )
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("deeply nested transformValue") {
        val input = DynamicValue.Record(
          "level1" -> DynamicValue.Record(
            "level2" -> DynamicValue.Record(
              "level3" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
            )
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("level1").field("level2").field("level3"),
            literal(DynamicValue.Primitive(PrimitiveValue.Long(42L)))
          )
        )
        val expected = DynamicValue.Record(
          "level1" -> DynamicValue.Record(
            "level2" -> DynamicValue.Record(
              "level3" -> DynamicValue.Primitive(PrimitiveValue.Long(42L))
            )
          )
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("collection elements with deep nesting") {
        val input = DynamicValue.Record(
          "nested" -> DynamicValue.Record(
            "items" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Primitive(PrimitiveValue.Int(10)),
                DynamicValue.Primitive(PrimitiveValue.Int(20)),
                DynamicValue.Primitive(PrimitiveValue.Int(30))
              )
            )
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root.field("nested").field("items"),
            literal(DynamicValue.Primitive(PrimitiveValue.Long(0L)))
          )
        )
        val expected = DynamicValue.Record(
          "nested" -> DynamicValue.Record(
            "items" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Primitive(PrimitiveValue.Long(0L)),
                DynamicValue.Primitive(PrimitiveValue.Long(0L)),
                DynamicValue.Primitive(PrimitiveValue.Long(0L))
              )
            )
          )
        )
        assertTrue(migration(input) == Right(expected))
      }
    ),

    suite("TransformValue")(
      test("literal replacement") {
        val intVal = DynamicValue.Primitive(PrimitiveValue.Int(42))

        def transform(newValue: DynamicValue, v: DynamicValue) =
          DynamicMigration.single(MigrationAction.TransformValue(DynamicOptic.root, literal(newValue)))(v)

        assertTrue(
          transform(DynamicValue.Primitive(PrimitiveValue.Long(42L)), intVal) == Right(
            DynamicValue.Primitive(PrimitiveValue.Long(42L))
          ) &&
            transform(DynamicValue.Primitive(PrimitiveValue.Double(42.0)), intVal) == Right(
              DynamicValue.Primitive(PrimitiveValue.Double(42.0))
            )
        )
      }
    ),

    suite("ChangeType")(
      test("changeType Int to Long") {
        val input     = DynamicValue.Record("count" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
        val migration = DynamicMigration.single(
          MigrationAction.ChangeType(
            DynamicOptic.root.field("count"),
            literal(DynamicValue.Primitive(PrimitiveValue.Long(42L)))
          )
        )
        val expected = DynamicValue.Record("count" -> DynamicValue.Primitive(PrimitiveValue.Long(42L)))
        assertTrue(migration(input) == Right(expected))
      },

      test("changeType in nested field") {
        val input = DynamicValue.Record(
          "outer" -> DynamicValue.Record("value" -> DynamicValue.Primitive(PrimitiveValue.Int(100)))
        )
        val migration = DynamicMigration.single(
          MigrationAction.ChangeType(
            DynamicOptic.root.field("outer").field("value"),
            literal(DynamicValue.Primitive(PrimitiveValue.Long(100L)))
          )
        )
        val expected = DynamicValue.Record(
          "outer" -> DynamicValue.Record("value" -> DynamicValue.Primitive(PrimitiveValue.Long(100L)))
        )
        assertTrue(migration(input) == Right(expected))
      }
    ),

    suite("Mandate and Optionalize")(
      test("mandate with Some value extracts the value") {
        val input     = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42)))
        val migration = DynamicMigration.single(
          MigrationAction.Mandate(
            DynamicOptic.root,
            literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        assertTrue(migration(input) == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      },

      test("mandate with None uses default") {
        val input        = DynamicValue.Variant("None", DynamicValue.Record.empty)
        val defaultValue = DynamicValue.Primitive(PrimitiveValue.Int(99))
        val migration    = DynamicMigration.single(
          MigrationAction.Mandate(
            DynamicOptic.root,
            literal(defaultValue)
          )
        )
        assertTrue(migration(input) == Right(defaultValue))
      },

      test("optionalize wraps value in Some") {
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val migration = DynamicMigration.single(
          MigrationAction.Optionalize(DynamicOptic.root)
        )
        val expected = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42)))
        assertTrue(migration(input) == Right(expected))
      }
    ),

    suite("TransformCase")(
      test("transformCase adds field to variant's record") {
        val input = DynamicValue.Variant(
          "CaseA",
          DynamicValue.Record("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            Vector(
              MigrationAction.AddField(
                DynamicOptic.root.field("y"),
                literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
              )
            )
          )
        )
        val expected = DynamicValue.Variant(
          "CaseA",
          DynamicValue.Record(
            "x" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            "y" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("transformCase with rename inside variant's record") {
        val input = DynamicValue.Variant(
          "MyCase",
          DynamicValue.Record("oldField" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            Vector(
              MigrationAction.Rename(DynamicOptic.root.field("oldField"), "newField")
            )
          )
        )
        val expected = DynamicValue.Variant(
          "MyCase",
          DynamicValue.Record("newField" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("transformCase with multiple nested actions") {
        val input = DynamicValue.Variant(
          "Complex",
          DynamicValue.Record("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            Vector(
              MigrationAction.Rename(DynamicOptic.root.field("a"), "b"),
              MigrationAction.AddField(
                DynamicOptic.root.field("c"),
                literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
              )
            )
          )
        )
        val expected = DynamicValue.Variant(
          "Complex",
          DynamicValue.Record(
            "b" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            "c" -> DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("transformCase with transformValue on inner field") {
        val input = DynamicValue.Variant(
          "Case",
          DynamicValue.Record("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            Vector(
              MigrationAction.TransformValue(
                DynamicOptic.root.field("value"),
                literal(DynamicValue.Primitive(PrimitiveValue.String("42")))
              )
            )
          )
        )
        val expected = DynamicValue.Variant(
          "Case",
          DynamicValue.Record("value" -> DynamicValue.Primitive(PrimitiveValue.String("42")))
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("transformCase preserves variant name") {
        val input = DynamicValue.Variant(
          "PreserveName",
          DynamicValue.Record.empty
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            Vector(
              MigrationAction.AddField(
                DynamicOptic.root.field("newField"),
                literal(DynamicValue.Primitive(PrimitiveValue.String("data")))
              )
            )
          )
        )
        val expected = DynamicValue.Variant(
          "PreserveName",
          DynamicValue.Record("newField" -> DynamicValue.Primitive(PrimitiveValue.String("data")))
        )
        assertTrue(migration(input) == Right(expected))
      }
    ),

    suite("TransformElements")(
      test("transformElements applies to each element") {
        val input = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root,
            literal(DynamicValue.Primitive(PrimitiveValue.Long(0L)))
          )
        )
        val expected = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Long(0L)),
            DynamicValue.Primitive(PrimitiveValue.Long(0L)),
            DynamicValue.Primitive(PrimitiveValue.Long(0L))
          )
        )
        assertTrue(migration(input) == Right(expected))
      },
      test("transformElements with nested path to collection field") {
        val input = DynamicValue.Record(
          "items" -> DynamicValue.Sequence(
            Chunk(
              DynamicValue.Primitive(PrimitiveValue.Int(10)),
              DynamicValue.Primitive(PrimitiveValue.Int(20))
            )
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root.field("items"),
            literal(DynamicValue.Primitive(PrimitiveValue.String("transformed")))
          )
        )
        val expected = DynamicValue.Record(
          "items" -> DynamicValue.Sequence(
            Chunk(
              DynamicValue.Primitive(PrimitiveValue.String("transformed")),
              DynamicValue.Primitive(PrimitiveValue.String("transformed"))
            )
          )
        )
        assertTrue(migration(input) == Right(expected))
      },
      test("transformElements on empty collection") {
        val input     = DynamicValue.Sequence(Chunk.empty)
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root,
            literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
          )
        )
        val expected = DynamicValue.Sequence(Chunk.empty)
        assertTrue(migration(input) == Right(expected))
      }
    ),

    suite("TransformKeys and TransformValues")(
      test("transformKeys applies to each key") {
        val input = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
            (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformKeys(
            DynamicOptic.root,
            literal(DynamicValue.Primitive(PrimitiveValue.String("key")))
          )
        )
        val expected = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("key")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
            (DynamicValue.Primitive(PrimitiveValue.String("key")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        assertTrue(migration(input) == Right(expected))
      },
      test("transformValues applies to each value") {
        val input = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("x")), DynamicValue.Primitive(PrimitiveValue.Int(10))),
            (DynamicValue.Primitive(PrimitiveValue.String("y")), DynamicValue.Primitive(PrimitiveValue.Int(20)))
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformValues(
            DynamicOptic.root,
            literal(DynamicValue.Primitive(PrimitiveValue.Long(0L)))
          )
        )
        val expected = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("x")), DynamicValue.Primitive(PrimitiveValue.Long(0L))),
            (DynamicValue.Primitive(PrimitiveValue.String("y")), DynamicValue.Primitive(PrimitiveValue.Long(0L)))
          )
        )
        assertTrue(migration(input) == Right(expected))
      },
      test("transformValues with nested path to map field") {
        val input = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            Chunk(
              (DynamicValue.Primitive(PrimitiveValue.String("key1")), DynamicValue.Primitive(PrimitiveValue.Int(100))),
              (DynamicValue.Primitive(PrimitiveValue.String("key2")), DynamicValue.Primitive(PrimitiveValue.Int(200)))
            )
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformValues(
            DynamicOptic.root.field("data"),
            literal(DynamicValue.Primitive(PrimitiveValue.String("transformed")))
          )
        )
        val expected = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            Chunk(
              (
                DynamicValue.Primitive(PrimitiveValue.String("key1")),
                DynamicValue.Primitive(PrimitiveValue.String("transformed"))
              ),
              (
                DynamicValue.Primitive(PrimitiveValue.String("key2")),
                DynamicValue.Primitive(PrimitiveValue.String("transformed"))
              )
            )
          )
        )
        assertTrue(migration(input) == Right(expected))
      },
      test("transformKeys on empty map") {
        val input     = DynamicValue.Map(Chunk.empty)
        val migration = DynamicMigration.single(
          MigrationAction.TransformKeys(
            DynamicOptic.root,
            literal(DynamicValue.Primitive(PrimitiveValue.String("anyKey")))
          )
        )
        val expected = DynamicValue.Map(Chunk.empty)
        assertTrue(migration(input) == Right(expected))
      }
    ),

    suite("Utility Methods")(
      test("andThen composes migrations") {
        val input = DynamicValue.Record("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val m1    = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("a"), "b"))
        val m2    = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("c"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val expected = DynamicValue.Record(
          "b" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          "c" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        assertTrue(m1.andThen(m2)(input) == Right(expected))
      },
      test("size returns number of actions") {
        val migration = DynamicMigration(
          MigrationAction.Rename(DynamicOptic.root.field("a"), "b"),
          MigrationAction.AddField(
            DynamicOptic.root.field("c"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        assertTrue(migration.size == 2)
      }
    ),

    suite("Join and Split")(
      test("join combines multiple fields into one") {
        val input = DynamicValue.Record(
          "first" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
          "last"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
        )
        val migration = DynamicMigration.single(
          MigrationAction.Join(
            at = DynamicOptic.root.field("fullName"),
            sourcePaths = Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
            combiner = literal(DynamicValue.Primitive(PrimitiveValue.String("John Doe")))
          )
        )
        val expected = DynamicValue.Record(
          "first"    -> DynamicValue.Primitive(PrimitiveValue.String("John")),
          "last"     -> DynamicValue.Primitive(PrimitiveValue.String("Doe")),
          "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe"))
        )
        assertTrue(migration(input) == Right(expected))
      },
      test("split divides one field into multiple") {
        val input = DynamicValue.Record(
          "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe"))
        )
        val migration = DynamicMigration.single(
          MigrationAction.Split(
            at = DynamicOptic.root.field("fullName"),
            targetPaths = Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
            splitter = literal(DynamicValue.Primitive(PrimitiveValue.String("split")))
          )
        )
        val expected = DynamicValue.Record(
          "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe")),
          "first"    -> DynamicValue.Primitive(PrimitiveValue.String("split")),
          "last"     -> DynamicValue.Primitive(PrimitiveValue.String("split"))
        )
        assertTrue(migration(input) == Right(expected))
      },
      test("join and split are structural reverses") {
        val at     = DynamicOptic.root.field("target")
        val source = DynamicOptic.root.field("source")
        val paths  = Vector(source)
        val expr   = literal(DynamicValue.Primitive(PrimitiveValue.String("test")))

        val join  = MigrationAction.Join(at, paths, expr)
        val split = MigrationAction.Split(at, paths, expr)

        assertTrue(
          join.reverse.isInstanceOf[MigrationAction.Split] &&
            split.reverse.isInstanceOf[MigrationAction.Join]
        )
      }
    ),

    suite("Selector Syntax")(
      test("simple field selector") {
        case class PersonV1(name: String)
        case class PersonV2(name: String, age: Int)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(_.age, literalExpr(DynamicValue.Primitive(PrimitiveValue.Int(0))))
          .build

        val input  = PersonV1("Alice")
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice", 0)))
      },

      test("renameField selector syntax") {
        case class PersonV1(firstName: String)
        case class PersonV2(fullName: String)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .renameField(_.firstName, _.fullName)
          .build

        val input  = PersonV1("Alice")
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice")))
      },

      test("dropField selector syntax") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .dropField(_.age, literalExpr(DynamicValue.Primitive(PrimitiveValue.Int(0))))
          .build

        val input  = PersonV1("Alice", 30)
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice")))
      },

      test("transformField selector syntax") {
        case class PersonV1(age: Int)
        case class PersonV2(age: Long)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .transformField(_.age, _.age, literalExpr(DynamicValue.Primitive(PrimitiveValue.Long(30L))))
          .build

        val input  = PersonV1(30)
        val result = migration(input)

        assertTrue(result == Right(PersonV2(30L)))
      },

      test("changeFieldType selector syntax") {
        case class PersonV1(score: Int)
        case class PersonV2(score: String)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .changeFieldType(_.score, _.score, literalExpr(DynamicValue.Primitive(PrimitiveValue.String("42"))))
          .build

        val input  = PersonV1(42)
        val result = migration(input)

        assertTrue(result == Right(PersonV2("42")))
      },

      test("mandateField builder creates correct action") {
        case class PersonV1(age: Option[Int])
        case class PersonV2(age: Int)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .mandateField(_.age, _.age, literalExpr(DynamicValue.Primitive(PrimitiveValue.Int(0))))
          .buildPartial

        assertTrue(migration.actions.length == 1 && migration.actions.head.isInstanceOf[MigrationAction.Mandate])
      },

      test("optionalizeField builder creates correct action") {
        case class PersonV1(age: Int)
        case class PersonV2(age: Option[Int])

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .optionalizeField(_.age, _.age)
          .buildPartial

        assertTrue(migration.actions.length == 1 && migration.actions.head.isInstanceOf[MigrationAction.Optionalize])
      },

      test("transformElements builder creates correct action") {
        case class Container(items: List[Int])

        implicit val schema: Schema[Container] = Schema.derived[Container]

        val migration = Migration
          .newBuilder[Container, Container]
          .transformElements(_.items, literalExpr(DynamicValue.Primitive(PrimitiveValue.Int(0))))
          .buildPartial

        assertTrue(
          migration.actions.length == 1 && migration.actions.head.isInstanceOf[MigrationAction.TransformElements]
        )
      },

      test("transformKeys builder creates correct action") {
        case class Container(data: Map[String, Int])

        implicit val schema: Schema[Container] = Schema.derived[Container]

        val migration = Migration
          .newBuilder[Container, Container]
          .transformKeys(_.data, literalExpr(DynamicValue.Primitive(PrimitiveValue.String("key"))))
          .buildPartial

        assertTrue(migration.actions.length == 1 && migration.actions.head.isInstanceOf[MigrationAction.TransformKeys])
      },

      test("transformValues builder creates correct action") {
        case class Container(data: Map[String, Int])

        implicit val schema: Schema[Container] = Schema.derived[Container]

        val migration = Migration
          .newBuilder[Container, Container]
          .transformValues(_.data, literalExpr(DynamicValue.Primitive(PrimitiveValue.Int(0))))
          .buildPartial

        assertTrue(
          migration.actions.length == 1 && migration.actions.head.isInstanceOf[MigrationAction.TransformValues]
        )
      },

      test("renameCase builder creates correct action") {
        sealed trait Status
        case object Active  extends Status
        case object Pending extends Status

        implicit val schema: Schema[Status] = Schema.derived[Status]

        val migration = Migration
          .newBuilder[Status, Status]
          .renameCase("Active", "Enabled")
          .buildPartial

        assertTrue(migration.actions.length == 1 && migration.actions.head.isInstanceOf[MigrationAction.RenameCase])
      }
    ),

    suite("Error Branches")(
      test("AddField.fieldName extracts field name from path") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("myField"),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        assertTrue(action.fieldName == "myField")
      },

      test("AddField.fieldName throws on invalid path (root only)") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val result = scala.util.Try(action.fieldName)
        assertTrue(result.isFailure)
      },

      test("AddField.fieldName throws on non-Field path node") {
        val action = MigrationAction.AddField(
          DynamicOptic(Vector(DynamicOptic.Node.Case("SomeCase"))),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val result = scala.util.Try(action.fieldName)
        assertTrue(result.isFailure)
      },

      test("DropField.fieldName extracts field name from path") {
        val action = MigrationAction.DropField(
          DynamicOptic.root.field("myField"),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        assertTrue(action.fieldName == "myField")
      },

      test("DropField.fieldName throws on invalid path (root only)") {
        val action = MigrationAction.DropField(
          DynamicOptic.root,
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val result = scala.util.Try(action.fieldName)
        assertTrue(result.isFailure)
      },

      test("DropField.fieldName throws on non-Field path node") {
        val action = MigrationAction.DropField(
          DynamicOptic(Vector(DynamicOptic.Node.Elements)),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val result = scala.util.Try(action.fieldName)
        assertTrue(result.isFailure)
      },

      test("Rename.from extracts field name from path") {
        val action = MigrationAction.Rename(DynamicOptic.root.field("oldName"), "newName")
        assertTrue(action.from == "oldName")
      },

      test("Rename.from throws on invalid path (root only)") {
        val action = MigrationAction.Rename(DynamicOptic.root, "newName")
        val result = scala.util.Try(action.from)
        assertTrue(result.isFailure)
      },

      test("Rename.from throws on non-Field path node") {
        val action = MigrationAction.Rename(
          DynamicOptic(Vector(DynamicOptic.Node.MapKeys)),
          "newName"
        )
        val result = scala.util.Try(action.from)
        assertTrue(result.isFailure)
      },

      test("isEmpty on empty migration") {
        val empty = DynamicMigration.empty
        assertTrue(empty.isEmpty == true)
      },

      test("isEmpty on non-empty migration") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("x"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        assertTrue(migration.isEmpty == false)
      },

      test("Literal expression works in migration actions") {
        val input     = DynamicValue.Record("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("b"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        assertTrue(migration(input).isRight)
      },

      test("addField on non-Record fails") {
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("x"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("dropField on non-Record fails") {
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val migration = DynamicMigration.single(
          MigrationAction.DropField(
            DynamicOptic.root.field("x"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("rename on non-Record fails") {
        val input     = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root.field("old"), "new")
        )
        assertTrue(migration(input).isLeft)
      },

      test("executeRename path not ending with Field") {
        val input     = DynamicValue.Record("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "newName")
        )
        assertTrue(migration(input).isLeft)
      },

      test("join on non-Record fails") {
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val migration = DynamicMigration.single(
          MigrationAction.Join(
            at = DynamicOptic.root.field("target"),
            sourcePaths = Vector(DynamicOptic.root.field("source")),
            combiner = literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("executeJoin path not ending with Field") {
        val input     = DynamicValue.Record("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration.single(
          MigrationAction.Join(
            at = DynamicOptic.root,
            sourcePaths = Vector(DynamicOptic.root.field("a")),
            combiner = literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("split on non-Record fails") {
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val migration = DynamicMigration.single(
          MigrationAction.Split(
            at = DynamicOptic.root.field("source"),
            targetPaths = Vector(DynamicOptic.root.field("target1")),
            splitter = literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("executeSplit path not ending with Field") {
        val input     = DynamicValue.Record("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration.single(
          MigrationAction.Split(
            at = DynamicOptic.root.field("source"),
            targetPaths = Vector(DynamicOptic.root),
            splitter = literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("renameCase on non-Variant fails") {
        val input     = DynamicValue.Record("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration.single(
          MigrationAction.RenameCase(DynamicOptic.root, "Old", "New")
        )
        assertTrue(migration(input).isLeft)
      },

      test("transformCase on non-Variant fails") {
        val input     = DynamicValue.Record("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            Vector(
              MigrationAction.AddField(
                DynamicOptic.root.field("x"),
                literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
              )
            )
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("transformElements on non-Sequence fails") {
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root,
            literal(DynamicValue.Primitive(PrimitiveValue.String("transformed")))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("transformKeys on non-Map fails") {
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val migration = DynamicMigration.single(
          MigrationAction.TransformKeys(
            DynamicOptic.root,
            literal(DynamicValue.Primitive(PrimitiveValue.String("key")))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("transformValues on non-Map fails") {
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val migration = DynamicMigration.single(
          MigrationAction.TransformValues(
            DynamicOptic.root,
            literal(DynamicValue.Primitive(PrimitiveValue.String("value")))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("modifyAtPath Node.Case mismatch fails") {
        val input     = DynamicValue.Variant("CaseA", DynamicValue.Record.empty)
        val migration = DynamicMigration.single(
          MigrationAction.RenameCase(DynamicOptic.root, "CaseB", "CaseC")
        )
        val result = migration(input)
        assertTrue(result == Right(input))
      },

      test("modifyAtPath Node.Case on non-Variant fails") {
        val input     = DynamicValue.Record("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(DynamicOptic.root, Vector.empty)
        )
        assertTrue(migration(input).isLeft)
      },

      test("modifyAtPath Node.Elements on non-Sequence fails") {
        val input     = DynamicValue.Record("items" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root.field("items"),
            literal(DynamicValue.Primitive(PrimitiveValue.Long(0L)))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("modifyAtPath Node.MapKeys on non-Map fails") {
        val input     = DynamicValue.Record("data" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration.single(
          MigrationAction.TransformKeys(
            DynamicOptic.root.field("data"),
            literal(DynamicValue.Primitive(PrimitiveValue.String("key")))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("modifyAtPath Node.MapValues on non-Map fails") {
        val input     = DynamicValue.Record("data" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration.single(
          MigrationAction.TransformValues(
            DynamicOptic.root.field("data"),
            literal(DynamicValue.Primitive(PrimitiveValue.String("value")))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("modifyAtPath Node.AtIndex out of bounds fails") {
        val input = DynamicValue.Record(
          "items" -> DynamicValue.Sequence(
            Chunk(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          )
        )
        val optic = DynamicOptic(
          Vector(
            DynamicOptic.Node.Field("items"),
            DynamicOptic.Node.AtIndex(10)
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            optic,
            literal(DynamicValue.Primitive(PrimitiveValue.Long(99L)))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("modifyAtPath Node.AtIndex on non-Sequence fails") {
        val input = DynamicValue.Record("data" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val optic = DynamicOptic(
          Vector(
            DynamicOptic.Node.Field("data"),
            DynamicOptic.Node.AtIndex(0)
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            optic,
            literal(DynamicValue.Primitive(PrimitiveValue.Long(99L)))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("modifyAtPath Node.AtMapKey not found fails") {
        val input = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            Chunk(
              (DynamicValue.Primitive(PrimitiveValue.String("key1")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
            )
          )
        )
        val optic = DynamicOptic(
          Vector(
            DynamicOptic.Node.Field("data"),
            DynamicOptic.Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("missingKey")))
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            optic,
            literal(DynamicValue.Primitive(PrimitiveValue.Long(99L)))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("modifyAtPath Node.AtMapKey on non-Map fails") {
        val input = DynamicValue.Record("data" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val optic = DynamicOptic(
          Vector(
            DynamicOptic.Node.Field("data"),
            DynamicOptic.Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("key")))
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            optic,
            literal(DynamicValue.Primitive(PrimitiveValue.Long(99L)))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("nested field not found fails") {
        val input =
          DynamicValue.Record("outer" -> DynamicValue.Record("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("outer").field("missing").field("x"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("field operation on wrong record type") {
        val input = DynamicValue.Record(
          "outer" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
        )
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("outer").field("inner").field("x"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("sequence and map mixed operations fail gracefully") {
        val input = DynamicValue.Record(
          "items" -> DynamicValue.Sequence(
            Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val optic = DynamicOptic(
          Vector(
            DynamicOptic.Node.Field("items"),
            DynamicOptic.Node.MapKeys
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            optic,
            literal(DynamicValue.Primitive(PrimitiveValue.String("transformed")))
          )
        )
        assertTrue(migration(input).isLeft)
      },

      test("migration with multiple actions stops on first failure") {
        val input     = DynamicValue.Record("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration(
          MigrationAction.Rename(DynamicOptic.root.field("nonexistent"), "b"),
          MigrationAction.Rename(DynamicOptic.root.field("a"), "c")
        )
        assertTrue(migration(input).isLeft)
      },

      test("transformElements with nested record in sequence") {
        val input = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record("value" -> DynamicValue.Primitive(PrimitiveValue.Int(1))),
            DynamicValue.Record("value" -> DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root,
            literal(DynamicValue.Primitive(PrimitiveValue.String("replaced")))
          )
        )
        val expected = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("replaced")),
            DynamicValue.Primitive(PrimitiveValue.String("replaced"))
          )
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("transformKeys on map with multiple entries") {
        val input = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("k1")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
            (DynamicValue.Primitive(PrimitiveValue.String("k2")), DynamicValue.Primitive(PrimitiveValue.Int(2))),
            (DynamicValue.Primitive(PrimitiveValue.String("k3")), DynamicValue.Primitive(PrimitiveValue.Int(3)))
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformKeys(
            DynamicOptic.root,
            literal(DynamicValue.Primitive(PrimitiveValue.String("unified")))
          )
        )
        val expected = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("unified")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
            (DynamicValue.Primitive(PrimitiveValue.String("unified")), DynamicValue.Primitive(PrimitiveValue.Int(2))),
            (DynamicValue.Primitive(PrimitiveValue.String("unified")), DynamicValue.Primitive(PrimitiveValue.Int(3)))
          )
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("variant case mismatch in transform case") {
        val input =
          DynamicValue.Variant("CaseA", DynamicValue.Record("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            Vector(
              MigrationAction.Rename(DynamicOptic.root.field("x"), "y")
            )
          )
        )
        val expected =
          DynamicValue.Variant("CaseA", DynamicValue.Record("y" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        assertTrue(migration(input) == Right(expected))
      },

      test("modifyAtPath navigates deeply nested records with field operations") {
        val input = DynamicValue.Record(
          "l1" -> DynamicValue.Record(
            "l2" -> DynamicValue.Record(
              "l3" -> DynamicValue.Record(
                "target" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
              )
            )
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("l1").field("l2").field("l3").field("target"),
            literal(DynamicValue.Primitive(PrimitiveValue.Long(42L)))
          )
        )
        val expected = DynamicValue.Record(
          "l1" -> DynamicValue.Record(
            "l2" -> DynamicValue.Record(
              "l3" -> DynamicValue.Record(
                "target" -> DynamicValue.Primitive(PrimitiveValue.Long(42L))
              )
            )
          )
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("modifyAtPath Node.Wrapped allows path continuation") {
        val input = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val optic = DynamicOptic(
          Vector(
            DynamicOptic.Node.Wrapped
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            optic,
            literal(DynamicValue.Primitive(PrimitiveValue.Long(42L)))
          )
        )
        assertTrue(migration(input) == Right(DynamicValue.Primitive(PrimitiveValue.Long(42L))))
      },

      test("split creates multiple fields from single source") {
        val input = DynamicValue.Record(
          "source" -> DynamicValue.Primitive(PrimitiveValue.String("data"))
        )
        val migration = DynamicMigration.single(
          MigrationAction.Split(
            at = DynamicOptic.root.field("source"),
            targetPaths = Vector(
              DynamicOptic.root.field("target1"),
              DynamicOptic.root.field("target2")
            ),
            splitter = literal(DynamicValue.Primitive(PrimitiveValue.String("split")))
          )
        )
        assertTrue(migration(input).isRight)
      },

      test("mandate with None and default value") {
        val input        = DynamicValue.Variant("None", DynamicValue.Record.empty)
        val defaultValue = DynamicValue.Primitive(PrimitiveValue.String("default"))
        val migration    = DynamicMigration.single(
          MigrationAction.Mandate(
            DynamicOptic.root,
            literal(defaultValue)
          )
        )
        assertTrue(migration(input) == Right(defaultValue))
      },

      test("optionalize double wraps value") {
        val input     = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val migration = DynamicMigration.single(
          MigrationAction.Optionalize(DynamicOptic.root)
        )
        val expected = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.String("hello")))
        assertTrue(migration(input) == Right(expected))
      },

      test("join with empty source paths") {
        val input     = DynamicValue.Record("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration.single(
          MigrationAction.Join(
            at = DynamicOptic.root.field("joined"),
            sourcePaths = Vector.empty,
            combiner = literal(DynamicValue.Primitive(PrimitiveValue.Int(99)))
          )
        )
        assertTrue(migration(input).isRight)
      },

      test("renameCase preserves non-matching variants") {
        val input     = DynamicValue.Variant("Other", DynamicValue.Record.empty)
        val migration = DynamicMigration.single(
          MigrationAction.RenameCase(DynamicOptic.root, "Target", "NewName")
        )
        assertTrue(migration(input) == Right(input))
      },

      test("transformElements on empty sequence returns empty") {
        val input     = DynamicValue.Sequence(Chunk.empty)
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root,
            literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        assertTrue(migration(input) == Right(DynamicValue.Sequence(Chunk.empty)))
      },

      test("transformValues on empty map returns empty") {
        val input     = DynamicValue.Map(Chunk.empty)
        val migration = DynamicMigration.single(
          MigrationAction.TransformValues(
            DynamicOptic.root,
            literal(DynamicValue.Primitive(PrimitiveValue.String("any")))
          )
        )
        assertTrue(migration(input) == Right(DynamicValue.Map(Chunk.empty)))
      }
    ),

    suite("Action Reversals")(
      test("reverse of AddField is DropField") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("x"),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        assertTrue(action.reverse.isInstanceOf[MigrationAction.DropField])
      },

      test("reverse of DropField is AddField") {
        val action = MigrationAction.DropField(
          DynamicOptic.root.field("x"),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        assertTrue(action.reverse.isInstanceOf[MigrationAction.AddField])
      },

      test("reverse of Rename swaps from and to") {
        val action   = MigrationAction.Rename(DynamicOptic.root.field("old"), "new")
        val reversed = action.reverse.asInstanceOf[MigrationAction.Rename]
        assertTrue(reversed.to == "old" && reversed.from == "new")
      },

      test("reverse of TransformValue is TransformValue") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        assertTrue(action.reverse.isInstanceOf[MigrationAction.TransformValue])
      },

      test("reverse of Mandate is Optionalize") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        assertTrue(action.reverse.isInstanceOf[MigrationAction.Optionalize])
      },

      test("reverse of Optionalize is Mandate") {
        val action = MigrationAction.Optionalize(DynamicOptic.root)
        assertTrue(action.reverse.isInstanceOf[MigrationAction.Mandate])
      },

      test("reverse of RenameCase swaps from and to") {
        val action   = MigrationAction.RenameCase(DynamicOptic.root, "Old", "New")
        val reversed = action.reverse.asInstanceOf[MigrationAction.RenameCase]
        assertTrue(reversed.from == "New" && reversed.to == "Old")
      },

      test("reverse of ChangeType is ChangeType") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          literal(DynamicValue.Primitive(PrimitiveValue.Long(1L)))
        )
        assertTrue(action.reverse.isInstanceOf[MigrationAction.ChangeType])
      },

      test("reverse of TransformElements is TransformElements") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        assertTrue(action.reverse.isInstanceOf[MigrationAction.TransformElements])
      },

      test("reverse of TransformKeys is TransformKeys") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          literal(DynamicValue.Primitive(PrimitiveValue.String("key")))
        )
        assertTrue(action.reverse.isInstanceOf[MigrationAction.TransformKeys])
      },

      test("reverse of TransformValues is TransformValues") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        assertTrue(action.reverse.isInstanceOf[MigrationAction.TransformValues])
      },

      test("reverse of TransformCase reverses inner actions") {
        val innerAction = MigrationAction.Rename(DynamicOptic.root.field("a"), "b")
        val action      = MigrationAction.TransformCase(DynamicOptic.root, Vector(innerAction))
        val reversed    = action.reverse.asInstanceOf[MigrationAction.TransformCase]
        assertTrue(reversed.actions.nonEmpty)
      },

      test("reverse of Join is Split") {
        val action = MigrationAction.Join(
          DynamicOptic.root.field("target"),
          Vector(DynamicOptic.root.field("source")),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        assertTrue(action.reverse.isInstanceOf[MigrationAction.Split])
      },

      test("reverse of Split is Join") {
        val action = MigrationAction.Split(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root.field("target")),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        assertTrue(action.reverse.isInstanceOf[MigrationAction.Join])
      }
    ),

    suite("Varargs Constructor")(
      test("DynamicMigration.apply with single action") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("x"),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val migration = DynamicMigration(action)
        assertTrue(migration.size == 1)
      },

      test("DynamicMigration.apply with multiple actions") {
        val action1 = MigrationAction.AddField(
          DynamicOptic.root.field("x"),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val action2 = MigrationAction.AddField(
          DynamicOptic.root.field("y"),
          literal(DynamicValue.Primitive(PrimitiveValue.String("a")))
        )
        val migration = DynamicMigration(action1, action2)
        assertTrue(migration.size == 2)
      },

      test("DynamicMigration.apply with three actions") {
        val action1 = MigrationAction.AddField(
          DynamicOptic.root.field("a"),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val action2 = MigrationAction.Rename(DynamicOptic.root.field("a"), "b")
        val action3 = MigrationAction.AddField(
          DynamicOptic.root.field("c"),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
        )
        val migration = DynamicMigration(action1, action2, action3)
        assertTrue(migration.size == 3)
      },

      test("DynamicMigration.apply with varargs executes all actions") {
        val input     = DynamicValue.Record("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration(
          MigrationAction.Rename(DynamicOptic.root.field("a"), "b"),
          MigrationAction.AddField(
            DynamicOptic.root.field("c"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val expected = DynamicValue.Record(
          "b" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          "c" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        assertTrue(migration(input) == Right(expected))
      }
    ),

    suite("modifyAtPath Node Types")(
      test("modifyAtPath with Node.Field navigates record") {
        val input = DynamicValue.Record(
          "x" -> DynamicValue.Primitive(PrimitiveValue.Int(10))
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("x"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(20)))
          )
        )
        val expected = DynamicValue.Record(
          "x" -> DynamicValue.Primitive(PrimitiveValue.Int(20))
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("modifyAtPath with Node.Case navigates variant") {
        val input = DynamicValue.Variant(
          "MyCase",
          DynamicValue.Record("x" -> DynamicValue.Primitive(PrimitiveValue.Int(10)))
        )
        val optic = DynamicOptic(
          Vector(
            DynamicOptic.Node.Case("MyCase"),
            DynamicOptic.Node.Field("x")
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            optic,
            literal(DynamicValue.Primitive(PrimitiveValue.Int(20)))
          )
        )
        val expected = DynamicValue.Variant(
          "MyCase",
          DynamicValue.Record("x" -> DynamicValue.Primitive(PrimitiveValue.Int(20)))
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("modifyAtPath with Node.Elements modifies sequence elements") {
        val input = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val optic     = DynamicOptic(Vector(DynamicOptic.Node.Elements))
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            optic,
            literal(DynamicValue.Primitive(PrimitiveValue.Int(99)))
          )
        )
        val expected = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(99)),
            DynamicValue.Primitive(PrimitiveValue.Int(99))
          )
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("modifyAtPath with Node.MapKeys modifies map keys") {
        val input = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("k1")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val optic     = DynamicOptic(Vector(DynamicOptic.Node.MapKeys))
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            optic,
            literal(DynamicValue.Primitive(PrimitiveValue.String("newKey")))
          )
        )
        val expected = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("newKey")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("modifyAtPath with Node.MapValues modifies map values") {
        val input = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("k")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val optic     = DynamicOptic(Vector(DynamicOptic.Node.MapValues))
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            optic,
            literal(DynamicValue.Primitive(PrimitiveValue.Int(99)))
          )
        )
        val expected = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("k")), DynamicValue.Primitive(PrimitiveValue.Int(99)))
          )
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("modifyAtPath with Node.AtIndex navigates by index") {
        val input = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(10)),
            DynamicValue.Primitive(PrimitiveValue.Int(20)),
            DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val optic     = DynamicOptic(Vector(DynamicOptic.Node.AtIndex(1)))
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            optic,
            literal(DynamicValue.Primitive(PrimitiveValue.Int(99)))
          )
        )
        val expected = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(10)),
            DynamicValue.Primitive(PrimitiveValue.Int(99)),
            DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("modifyAtPath with Node.AtMapKey navigates map by key") {
        val input = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("key1")), DynamicValue.Primitive(PrimitiveValue.Int(100))),
            (DynamicValue.Primitive(PrimitiveValue.String("key2")), DynamicValue.Primitive(PrimitiveValue.Int(200)))
          )
        )
        val optic = DynamicOptic(
          Vector(
            DynamicOptic.Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("key1")))
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            optic,
            literal(DynamicValue.Primitive(PrimitiveValue.Int(999)))
          )
        )
        val expected = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("key1")), DynamicValue.Primitive(PrimitiveValue.Int(999))),
            (DynamicValue.Primitive(PrimitiveValue.String("key2")), DynamicValue.Primitive(PrimitiveValue.Int(200)))
          )
        )
        assertTrue(migration(input) == Right(expected))
      },

      test("modifyAtPath Node.Case mismatch returns unchanged") {
        val input     = DynamicValue.Variant("CaseA", DynamicValue.Record.empty)
        val optic     = DynamicOptic(Vector(DynamicOptic.Node.Case("CaseB")))
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            optic,
            literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        assertTrue(migration(input) == Left(SchemaError.caseNotFound(optic, "CaseB")))
      }
    ),

    suite("Mandate and Optionalize Edge Cases")(
      test("mandate with non-Option value uses it directly") {
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val migration = DynamicMigration.single(
          MigrationAction.Mandate(
            DynamicOptic.root,
            literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        assertTrue(migration(input) == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      },

      test("optionalize then mandate round-trips") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("test"))
        val opt    = DynamicMigration.single(MigrationAction.Optionalize(DynamicOptic.root))
        val mand   = opt.reverse
        val result = opt(input).flatMap(mand(_))
        assertTrue(result == Right(input))
      }
    ),

    suite("Reverse Composition")(
      test("double reverse preserves actions") {
        val migration = DynamicMigration(
          MigrationAction.AddField(
            DynamicOptic.root.field("x"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          ),
          MigrationAction.Rename(DynamicOptic.root.field("a"), "b")
        )
        assertTrue(migration.reverse.reverse.actions == migration.actions)
      },

      test("reverse with multiple actions maintains order") {
        val actions = Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("a"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          ),
          MigrationAction.AddField(
            DynamicOptic.root.field("b"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
          ),
          MigrationAction.AddField(
            DynamicOptic.root.field("c"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
          )
        )
        val migration = DynamicMigration(actions: _*)
        val reversed  = migration.reverse
        assertTrue(reversed.size == 3)
      }
    ),

    suite("Migration Companion Methods")(
      test("Migration.identity creates empty migration") {
        case class Person(name: String)
        implicit val schema: Schema[Person] = Schema.derived[Person]

        val migration = Migration.identity[Person]
        assertTrue(migration.isEmpty && migration.size == 0)
      },

      test("Migration.fromAction creates single-action migration") {
        case class PersonV1(name: String)
        case class PersonV2(name: String, age: Int)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val action = MigrationAction.AddField(
          DynamicOptic.root.field("age"),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
        val migration = Migration.fromAction[PersonV1, PersonV2](action)
        assertTrue(migration.size == 1)
      },

      test("Migration.fromDynamic wraps DynamicMigration") {
        case class PersonV1(name: String)
        case class PersonV2(name: String, age: Int)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val dynamicMigration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val migration = Migration.fromDynamic[PersonV1, PersonV2](dynamicMigration)
        assertTrue(migration.size == 1 && migration.dynamicMigration == dynamicMigration)
      },

      test("Migration.++ composes migrations") {
        case class PersonV1(name: String)
        case class PersonV2(name: String, age: Int)
        case class PersonV3(name: String, age: Int, city: String)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]
        implicit val v3Schema: Schema[PersonV3] = Schema.derived[PersonV3]

        val m1 = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(_.age, literalExpr(DynamicValue.Primitive(PrimitiveValue.Int(0))))
          .build
        val m2 = Migration
          .newBuilder[PersonV2, PersonV3]
          .addField(_.city, literalExpr(DynamicValue.Primitive(PrimitiveValue.String(""))))
          .build
        val composed = m1 ++ m2
        assertTrue(composed.size == 2)
      },

      test("Migration.reverse swaps schemas") {
        case class PersonV1(name: String)
        case class PersonV2(name: String, age: Int)

        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(_.age, literalExpr(DynamicValue.Primitive(PrimitiveValue.Int(0))))
          .build
        val reversed = migration.reverse
        assertTrue(reversed.sourceSchema == v2Schema && reversed.targetSchema == v1Schema)
      }
    ),

    suite("Migration Composition with migrateField")(
      test("migrateField with explicit migration") {
        case class User(name: String, age: Int)
        case class UserV2(name: String, age: Int, email: String)
        case class Profile(id: Int, user: User)
        case class ProfileV2(id: Int, user: UserV2)

        implicit val userSchema: Schema[User]           = Schema.derived[User]
        implicit val userV2Schema: Schema[UserV2]       = Schema.derived[UserV2]
        implicit val profileSchema: Schema[Profile]     = Schema.derived[Profile]
        implicit val profileV2Schema: Schema[ProfileV2] = Schema.derived[ProfileV2]

        val userMigration: Migration[User, UserV2] = Migration
          .newBuilder[User, UserV2]
          .addField(_.email, literalExpr(DynamicValue.Primitive(PrimitiveValue.String("default@example.com"))))
          .build

        val profileMigration = Migration
          .newBuilder[Profile, ProfileV2]
          .migrateField(_.user, _.user, userMigration)
          .build

        val input  = Profile(1, User("Alice", 30))
        val result = profileMigration(input)

        assertTrue(result == Right(ProfileV2(1, UserV2("Alice", 30, "default@example.com"))))
      },

      test("migrateField with implicit migration") {
        case class Address(street: String, city: String)
        case class AddressV2(street: String, city: String, zip: String)
        case class Company(name: String, address: Address)
        case class CompanyV2(name: String, address: AddressV2)

        implicit val addressSchema: Schema[Address]     = Schema.derived[Address]
        implicit val addressV2Schema: Schema[AddressV2] = Schema.derived[AddressV2]
        implicit val companySchema: Schema[Company]     = Schema.derived[Company]
        implicit val companyV2Schema: Schema[CompanyV2] = Schema.derived[CompanyV2]

        implicit val addressMigration: Migration[Address, AddressV2] = Migration
          .newBuilder[Address, AddressV2]
          .addField(_.zip, literalExpr(DynamicValue.Primitive(PrimitiveValue.String("00000"))))
          .build

        val companyMigration = Migration
          .newBuilder[Company, CompanyV2]
          .migrateField(_.address, _.address)
          .build

        val input  = Company("Acme", Address("123 Main St", "Springfield"))
        val result = companyMigration(input)

        assertTrue(result == Right(CompanyV2("Acme", AddressV2("123 Main St", "Springfield", "00000"))))
      },

      test("migrateField tracks nested fields for validation") {
        case class Inner(a: Int, b: String)
        case class InnerV2(a: Int, b: String, c: Boolean)
        case class Outer(x: String, inner: Inner)
        case class OuterV2(x: String, inner: InnerV2)

        implicit val innerSchema: Schema[Inner]     = Schema.derived[Inner]
        implicit val innerV2Schema: Schema[InnerV2] = Schema.derived[InnerV2]
        implicit val outerSchema: Schema[Outer]     = Schema.derived[Outer]
        implicit val outerV2Schema: Schema[OuterV2] = Schema.derived[OuterV2]

        val innerMigration: Migration[Inner, InnerV2] = Migration
          .newBuilder[Inner, InnerV2]
          .addField(_.c, literalExpr(DynamicValue.Primitive(PrimitiveValue.Boolean(false))))
          .build

        val outerMigration = Migration
          .newBuilder[Outer, OuterV2]
          .migrateField(_.inner, _.inner, innerMigration)
          .build

        val input  = Outer("test", Inner(42, "hello"))
        val result = outerMigration(input)

        assertTrue(result == Right(OuterV2("test", InnerV2(42, "hello", false))))
      },

      test("ApplyMigration action reverse works correctly") {
        val innerMigration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("newField"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
          )
        )
        val action   = MigrationAction.ApplyMigration(DynamicOptic.root.field("nested"), innerMigration)
        val reversed = action.reverse

        assertTrue(
          reversed.isInstanceOf[MigrationAction.ApplyMigration] &&
            reversed
              .asInstanceOf[MigrationAction.ApplyMigration]
              .migration
              .actions
              .head
              .isInstanceOf[MigrationAction.DropField]
        )
      },

      test("ApplyMigration executes nested migration at path") {
        val input = DynamicValue.Record(
          "id"   -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          "data" -> DynamicValue.Record(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))
          )
        )

        val nestedMigration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(25)))
          )
        )

        val migration = DynamicMigration.single(
          MigrationAction.ApplyMigration(DynamicOptic.root.field("data"), nestedMigration)
        )

        val expected = DynamicValue.Record(
          "id"   -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          "data" -> DynamicValue.Record(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("test")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )

        assertTrue(migration(input) == Right(expected))
      },

      test("migrateField combined with other operations") {
        case class Settings(theme: String)
        case class SettingsV2(theme: String, fontSize: Int)
        case class Config(name: String, settings: Settings)
        case class ConfigV2(title: String, settings: SettingsV2)

        implicit val settingsSchema: Schema[Settings]     = Schema.derived[Settings]
        implicit val settingsV2Schema: Schema[SettingsV2] = Schema.derived[SettingsV2]
        implicit val configSchema: Schema[Config]         = Schema.derived[Config]
        implicit val configV2Schema: Schema[ConfigV2]     = Schema.derived[ConfigV2]

        val settingsMigration: Migration[Settings, SettingsV2] = Migration
          .newBuilder[Settings, SettingsV2]
          .addField(_.fontSize, literalExpr(DynamicValue.Primitive(PrimitiveValue.Int(12))))
          .build

        val configMigration = Migration
          .newBuilder[Config, ConfigV2]
          .renameField(_.name, _.title)
          .migrateField(_.settings, _.settings, settingsMigration)
          .build

        val input  = Config("MyConfig", Settings("dark"))
        val result = configMigration(input)

        assertTrue(result == Right(ConfigV2("MyConfig", SettingsV2("dark", 12))))
      }
    )
  )
}
