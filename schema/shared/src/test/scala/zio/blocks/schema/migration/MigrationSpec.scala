package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.chunk.Chunk

object MigrationSpec extends ZIOSpecDefault {

  // Helper to create a Literal SchemaExpr from a DynamicValue
  private def literal(dv: DynamicValue): SchemaExpr[Any, Any] =
    SchemaExpr.Literal[Any, Any](dv)

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
          .addField(_.age, literal(DynamicValue.Primitive(PrimitiveValue.Int(0))))
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
          .dropField(_.age, literal(DynamicValue.Primitive(PrimitiveValue.Int(0))))
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
          .transformField(_.age, _.age, literal(DynamicValue.Primitive(PrimitiveValue.Long(30L))))
          .build

        val input  = PersonV1(30)
        val result = migration(input)

        assertTrue(result == Right(PersonV2(30L)))
      }
    )
  )
}
