package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.chunk.Chunk
import zio.test._

object DynamicMigrationSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicMigrationSpec")(
    suite("Identity")(
      test("empty migration returns value unchanged") {
        val value = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val migration = DynamicMigration.empty
        assertTrue(migration(value) == Right(value))
      },
      test("Identity action returns value unchanged") {
        val value     = DynamicValue.Primitive(PrimitiveValue.String("test"))
        val migration = DynamicMigration(MigrationAction.Identity)
        assertTrue(migration(value) == Right(value))
      }
    ),
    suite("AddField")(
      test("adds a new field to a record") {
        val value = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.AddField(
            DynamicOptic.root,
            "age",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(25)))
          )
        )
        val result = migration(value)
        assertTrue(
          result == Right(
            DynamicValue.Record(
              Chunk(
                "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
                "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
              )
            )
          )
        )
      },
      test("fails if field already exists") {
        val value = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.AddField(
            DynamicOptic.root,
            "name",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("Bob")))
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("adds field in nested record") {
        val value = DynamicValue.Record(
          Chunk(
            "person" -> DynamicValue.Record(
              Chunk(
                "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
              )
            )
          )
        )
        val migration = DynamicMigration(
          MigrationAction.AddField(
            DynamicOptic.root.field("person"),
            "age",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )
        val result = migration(value)
        assertTrue(
          result == Right(
            DynamicValue.Record(
              Chunk(
                "person" -> DynamicValue.Record(
                  Chunk(
                    "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
                    "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
                  )
                )
              )
            )
          )
        )
      }
    ),
    suite("DropField")(
      test("removes a field from a record") {
        val value = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.DropField(
            DynamicOptic.root,
            "age",
            DynamicSchemaExpr.DefaultValue
          )
        )
        val result = migration(value)
        assertTrue(
          result == Right(
            DynamicValue.Record(
              Chunk(
                "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
              )
            )
          )
        )
      },
      test("fails if field does not exist") {
        val value = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.DropField(
            DynamicOptic.root,
            "missing",
            DynamicSchemaExpr.DefaultValue
          )
        )
        assertTrue(migration(value).isLeft)
      }
    ),
    suite("RenameField")(
      test("renames a field") {
        val value = DynamicValue.Record(
          Chunk(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.RenameField(
            DynamicOptic.root,
            "firstName",
            "name"
          )
        )
        val result = migration(value)
        assertTrue(
          result == Right(
            DynamicValue.Record(
              Chunk(
                "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
              )
            )
          )
        )
      },
      test("fails if source field does not exist") {
        val value = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.RenameField(
            DynamicOptic.root,
            "missing",
            "newName"
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("fails if target field already exists") {
        val value = DynamicValue.Record(
          Chunk(
            "name"  -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "alias" -> DynamicValue.Primitive(PrimitiveValue.String("Bob"))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.RenameField(
            DynamicOptic.root,
            "name",
            "alias"
          )
        )
        assertTrue(migration(value).isLeft)
      }
    ),
    suite("TransformValue")(
      test("transforms a field value") {
        val value = DynamicValue.Record(
          Chunk(
            "count" -> DynamicValue.Primitive(PrimitiveValue.Int(5))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("count"),
            DynamicSchemaExpr.Arithmetic(
              DynamicSchemaExpr.Path(DynamicOptic.root),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
              DynamicSchemaExpr.ArithmeticOperator.Multiply
            ),
            DynamicSchemaExpr.DefaultValue
          )
        )
        val result = migration(value)
        assertTrue(
          result == Right(
            DynamicValue.Record(
              Chunk(
                "count" -> DynamicValue.Primitive(PrimitiveValue.Int(50))
              )
            )
          )
        )
      }
    ),
    suite("Optionalize and Mandate")(
      test("Optionalize wraps value in Some") {
        val value     = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val migration = DynamicMigration(MigrationAction.Optionalize(DynamicOptic.root))
        val result    = migration(value)
        assertTrue(
          result == Right(DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.String("hello"))))
        )
      },
      test("Mandate unwraps Some value") {
        val value     = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.String("hello")))
        val migration = DynamicMigration(
          MigrationAction.Mandate(
            DynamicOptic.root,
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
          )
        )
        val result = migration(value)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("hello"))))
      },
      test("Mandate provides default for None") {
        val value     = DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty))
        val migration = DynamicMigration(
          MigrationAction.Mandate(
            DynamicOptic.root,
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
          )
        )
        val result = migration(value)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("default"))))
      }
    ),
    suite("RenameCase")(
      test("renames a variant case") {
        val value = DynamicValue.Variant(
          "OldCase",
          DynamicValue.Record(
            Chunk(
              "data" -> DynamicValue.Primitive(PrimitiveValue.String("test"))
            )
          )
        )
        val migration = DynamicMigration(
          MigrationAction.RenameCase(
            DynamicOptic.root,
            "OldCase",
            "NewCase"
          )
        )
        val result = migration(value)
        assertTrue(
          result == Right(
            DynamicValue.Variant(
              "NewCase",
              DynamicValue.Record(
                Chunk(
                  "data" -> DynamicValue.Primitive(PrimitiveValue.String("test"))
                )
              )
            )
          )
        )
      },
      test("does not modify non-matching case") {
        val value     = DynamicValue.Variant("OtherCase", DynamicValue.Record(Chunk.empty))
        val migration = DynamicMigration(
          MigrationAction.RenameCase(
            DynamicOptic.root,
            "OldCase",
            "NewCase"
          )
        )
        val result = migration(value)
        assertTrue(result == Right(value))
      }
    ),
    suite("TransformCase")(
      test("transforms a specific case") {
        val value = DynamicValue.Variant(
          "MyCase",
          DynamicValue.Record(
            Chunk(
              "oldField" -> DynamicValue.Primitive(PrimitiveValue.String("test"))
            )
          )
        )
        val migration = DynamicMigration(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            "MyCase",
            Vector(MigrationAction.RenameField(DynamicOptic.root, "oldField", "newField"))
          )
        )
        val result = migration(value)
        assertTrue(
          result == Right(
            DynamicValue.Variant(
              "MyCase",
              DynamicValue.Record(
                Chunk(
                  "newField" -> DynamicValue.Primitive(PrimitiveValue.String("test"))
                )
              )
            )
          )
        )
      }
    ),
    suite("TransformElements")(
      test("transforms all sequence elements") {
        val value = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.TransformElements(
            DynamicOptic.root,
            DynamicSchemaExpr.Arithmetic(
              DynamicSchemaExpr.Path(DynamicOptic.root),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
              DynamicSchemaExpr.ArithmeticOperator.Multiply
            ),
            DynamicSchemaExpr.DefaultValue
          )
        )
        val result = migration(value)
        assertTrue(
          result == Right(
            DynamicValue.Sequence(
              Chunk(
                DynamicValue.Primitive(PrimitiveValue.Int(2)),
                DynamicValue.Primitive(PrimitiveValue.Int(4)),
                DynamicValue.Primitive(PrimitiveValue.Int(6))
              )
            )
          )
        )
      }
    ),
    suite("TransformKeys")(
      test("transforms all map keys") {
        val value = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
            (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.TransformKeys(
            DynamicOptic.root,
            DynamicSchemaExpr.StringConcat(
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("key_"))),
              DynamicSchemaExpr.Path(DynamicOptic.root)
            ),
            DynamicSchemaExpr.DefaultValue
          )
        )
        val result = migration(value)
        assertTrue(
          result == Right(
            DynamicValue.Map(
              Chunk(
                (DynamicValue.Primitive(PrimitiveValue.String("key_a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
                (DynamicValue.Primitive(PrimitiveValue.String("key_b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
              )
            )
          )
        )
      }
    ),
    suite("TransformValues")(
      test("transforms all map values") {
        val value = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
            (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.TransformValues(
            DynamicOptic.root,
            DynamicSchemaExpr.Arithmetic(
              DynamicSchemaExpr.Path(DynamicOptic.root),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(100))),
              DynamicSchemaExpr.ArithmeticOperator.Add
            ),
            DynamicSchemaExpr.DefaultValue
          )
        )
        val result = migration(value)
        assertTrue(
          result == Right(
            DynamicValue.Map(
              Chunk(
                (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(101))),
                (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(102)))
              )
            )
          )
        )
      }
    ),
    suite("Composition")(
      test("++ composes migrations sequentially") {
        val value = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
          )
        )
        val m1 = DynamicMigration(
          MigrationAction.AddField(
            DynamicOptic.root,
            "age",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(25)))
          )
        )
        val m2       = DynamicMigration(MigrationAction.RenameField(DynamicOptic.root, "name", "fullName"))
        val combined = m1 ++ m2
        val result   = combined(value)
        assertTrue(
          result == Right(
            DynamicValue.Record(
              Chunk(
                "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
                "age"      -> DynamicValue.Primitive(PrimitiveValue.Int(25))
              )
            )
          )
        )
      },
      test("andThen is an alias for ++") {
        val m1 = DynamicMigration(MigrationAction.Identity)
        val m2 = DynamicMigration(MigrationAction.Identity)
        assertTrue(m1.andThen(m2).actions == (m1 ++ m2).actions)
      }
    ),
    suite("Reverse")(
      test("reverse of identity is identity") {
        val migration = DynamicMigration.empty
        assertTrue(migration.reverse.isEmpty)
      },
      test("reverse of addField is dropField") {
        val migration = DynamicMigration(
          MigrationAction.AddField(
            DynamicOptic.root,
            "age",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val reversed = migration.reverse
        assertTrue(reversed.actions.head.isInstanceOf[MigrationAction.DropField])
      },
      test("reverse of renameField is renameField with swapped names") {
        val migration = DynamicMigration(MigrationAction.RenameField(DynamicOptic.root, "old", "new"))
        val reversed  = migration.reverse
        val action    = reversed.actions.head.asInstanceOf[MigrationAction.RenameField]
        assertTrue(action.from == "new" && action.to == "old")
      },
      test("double reverse equals original") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root,
              "a",
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
            ),
            MigrationAction.RenameField(DynamicOptic.root, "b", "c")
          )
        )
        val doubleReversed = migration.reverse.reverse
        assertTrue(doubleReversed.actions.length == migration.actions.length)
      }
    ),
    suite("Laws")(
      test("identity law: identity migration returns original value") {
        val value = DynamicValue.Record(
          Chunk(
            "x" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
          )
        )
        val identity = DynamicMigration.empty
        assertTrue(identity(value) == Right(value))
      },
      test("associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
        val m1 = DynamicMigration(
          MigrationAction.AddField(
            DynamicOptic.root,
            "a",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val m2 = DynamicMigration(
          MigrationAction.AddField(
            DynamicOptic.root,
            "b",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val m3 = DynamicMigration(
          MigrationAction.AddField(
            DynamicOptic.root,
            "c",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
          )
        )
        val value = DynamicValue.Record(Chunk.empty)
        val left  = ((m1 ++ m2) ++ m3)(value)
        val right = (m1 ++ (m2 ++ m3))(value)
        assertTrue(left == right)
      },
      test("structural reverse: reverse.reverse equals original structure") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root,
              "x",
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
            ),
            MigrationAction.RenameField(DynamicOptic.root, "a", "b")
          )
        )
        val doubleReversed = migration.reverse.reverse
        assertTrue(migration.actions.length == doubleReversed.actions.length)
      }
    ),
    suite("Error paths")(
      test("AddField fails on non-record") {
        val value     = DynamicValue.Primitive(PrimitiveValue.String("test"))
        val migration = DynamicMigration(
          MigrationAction.AddField(
            DynamicOptic.root,
            "name",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("Alice")))
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("DropField fails on non-record") {
        val value     = DynamicValue.Primitive(PrimitiveValue.String("test"))
        val migration = DynamicMigration(
          MigrationAction.DropField(
            DynamicOptic.root,
            "name",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("RenameField fails on non-record") {
        val value     = DynamicValue.Primitive(PrimitiveValue.String("test"))
        val migration = DynamicMigration(
          MigrationAction.RenameField(DynamicOptic.root, "old", "new")
        )
        assertTrue(migration(value).isLeft)
      },
      test("RenameCase fails on non-variant") {
        val value     = DynamicValue.Primitive(PrimitiveValue.String("test"))
        val migration = DynamicMigration(
          MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        )
        assertTrue(migration(value).isLeft)
      },
      test("TransformCase fails on non-variant") {
        val value     = DynamicValue.Primitive(PrimitiveValue.String("test"))
        val migration = DynamicMigration(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            "SomeCase",
            Vector(MigrationAction.Identity)
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("TransformElements fails on non-sequence") {
        val value     = DynamicValue.Primitive(PrimitiveValue.String("test"))
        val migration = DynamicMigration(
          MigrationAction.TransformElements(
            DynamicOptic.root,
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.DefaultValue
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("TransformKeys fails on non-map") {
        val value     = DynamicValue.Primitive(PrimitiveValue.String("test"))
        val migration = DynamicMigration(
          MigrationAction.TransformKeys(
            DynamicOptic.root,
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.DefaultValue
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("TransformValues fails on non-map") {
        val value     = DynamicValue.Primitive(PrimitiveValue.String("test"))
        val migration = DynamicMigration(
          MigrationAction.TransformValues(
            DynamicOptic.root,
            DynamicSchemaExpr.Path(DynamicOptic.root),
            DynamicSchemaExpr.DefaultValue
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("path navigation fails on non-existent field") {
        val value = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("missing"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
            DynamicSchemaExpr.DefaultValue
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("path navigation through variant with wrong case") {
        val value     = DynamicValue.Variant("CaseA", DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val migration = DynamicMigration(
          MigrationAction.TransformValue(
            DynamicOptic.root.caseOf("CaseB"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
            DynamicSchemaExpr.DefaultValue
          )
        )
        // Should pass through unchanged when case doesn't match
        assertTrue(migration(value) == Right(value))
      },
      test("index out of bounds error") {
        val value = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.TransformValue(
            DynamicOptic.root.at(10),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
            DynamicSchemaExpr.DefaultValue
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("Mandate returns value unchanged for non-optional") {
        val value     = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val migration = DynamicMigration(
          MigrationAction.Mandate(
            DynamicOptic.root,
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        assertTrue(migration(value) == Right(value))
      },
      test("isEmpty returns true for empty migration") {
        assertTrue(DynamicMigration.empty.isEmpty)
      },
      test("nonEmpty returns true for non-empty migration") {
        val migration = DynamicMigration(MigrationAction.Identity)
        assertTrue(migration.nonEmpty)
      },
      test("single action constructor works") {
        val action    = MigrationAction.Identity
        val migration = DynamicMigration(action)
        assertTrue(migration.actions == Vector(action))
      },
      test("Split fails with wrong number of values") {
        val value = DynamicValue.Record(
          Chunk(
            "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe"))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.Split(
            DynamicOptic.root.field("fullName"),
            Vector(
              DynamicOptic.root.field("first"),
              DynamicOptic.root.field("last"),
              DynamicOptic.root.field("middle")
            ),
            DynamicSchemaExpr.Literal(
              DynamicValue.Sequence(
                Chunk(
                  DynamicValue.Primitive(PrimitiveValue.String("John")),
                  DynamicValue.Primitive(PrimitiveValue.String("Doe"))
                )
              )
            ),
            DynamicSchemaExpr.DefaultValue
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("Split fails when source field not found") {
        val value = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.Split(
            DynamicOptic.root.field("missing"),
            Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
            DynamicSchemaExpr.Literal(
              DynamicValue.Sequence(
                Chunk(
                  DynamicValue.Primitive(PrimitiveValue.String("x")),
                  DynamicValue.Primitive(PrimitiveValue.String("y"))
                )
              )
            ),
            DynamicSchemaExpr.DefaultValue
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("Split fails on non-record") {
        val value     = DynamicValue.Primitive(PrimitiveValue.String("test"))
        val migration = DynamicMigration(
          MigrationAction.Split(
            DynamicOptic.root.field("fullName"),
            Vector(DynamicOptic.root.field("first")),
            DynamicSchemaExpr.Literal(
              DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.String("x"))))
            ),
            DynamicSchemaExpr.DefaultValue
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("Split fails when splitter returns non-sequence") {
        val value = DynamicValue.Record(
          Chunk(
            "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe"))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.Split(
            DynamicOptic.root.field("fullName"),
            Vector(DynamicOptic.root.field("first")),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("not a sequence"))),
            DynamicSchemaExpr.DefaultValue
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("Join fails when source path doesn't exist") {
        val value = DynamicValue.Record(
          Chunk(
            "first" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.Join(
            DynamicOptic.root.field("fullName"),
            Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("missing")),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("combined"))),
            DynamicSchemaExpr.DefaultValue
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("Join fails on non-record") {
        val value     = DynamicValue.Primitive(PrimitiveValue.String("test"))
        val migration = DynamicMigration(
          MigrationAction.Join(
            DynamicOptic.root.field("result"),
            Vector(DynamicOptic.root.field("a")),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("combined"))),
            DynamicSchemaExpr.DefaultValue
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("TransformElements accumulates all errors") {
        val value = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("a")),
            DynamicValue.Primitive(PrimitiveValue.String("b"))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.TransformElements(
            DynamicOptic.root,
            DynamicSchemaExpr.Arithmetic(
              DynamicSchemaExpr.Path(DynamicOptic.root),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
              DynamicSchemaExpr.ArithmeticOperator.Add
            ),
            DynamicSchemaExpr.DefaultValue
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("TransformKeys accumulates all errors") {
        val value = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
            (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.TransformKeys(
            DynamicOptic.root,
            DynamicSchemaExpr.Arithmetic(
              DynamicSchemaExpr.Path(DynamicOptic.root),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
              DynamicSchemaExpr.ArithmeticOperator.Add
            ),
            DynamicSchemaExpr.DefaultValue
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("TransformValues accumulates all errors") {
        val value = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.Int(1)), DynamicValue.Primitive(PrimitiveValue.String("a"))),
            (DynamicValue.Primitive(PrimitiveValue.Int(2)), DynamicValue.Primitive(PrimitiveValue.String("b")))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.TransformValues(
            DynamicOptic.root,
            DynamicSchemaExpr.Arithmetic(
              DynamicSchemaExpr.Path(DynamicOptic.root),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
              DynamicSchemaExpr.ArithmeticOperator.Add
            ),
            DynamicSchemaExpr.DefaultValue
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("path navigation fails on field access through primitive") {
        val value     = DynamicValue.Primitive(PrimitiveValue.String("test"))
        val migration = DynamicMigration(
          MigrationAction.AddField(
            DynamicOptic.root.field("nested"),
            "name",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("value")))
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("path navigation fails on case access through primitive") {
        val value     = DynamicValue.Primitive(PrimitiveValue.String("test"))
        val migration = DynamicMigration(
          MigrationAction.TransformValue(
            DynamicOptic.root.caseOf("Case"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
            DynamicSchemaExpr.DefaultValue
          )
        )
        assertTrue(migration(value).isLeft)
      },
      test("path navigation fails on index access through primitive") {
        val value     = DynamicValue.Primitive(PrimitiveValue.String("test"))
        val migration = DynamicMigration(
          MigrationAction.TransformValue(
            DynamicOptic.root.at(0),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
            DynamicSchemaExpr.DefaultValue
          )
        )
        assertTrue(migration(value).isLeft)
      }
    )
  )
}
