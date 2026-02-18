package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

object DynamicMigrationSpec extends SchemaBaseSpec {

  private def intVal(n: Int): DynamicValue       = DynamicValue.int(n)
  private def stringVal(s: String): DynamicValue = DynamicValue.string(s)
  private def doubleVal(d: Double): DynamicValue = DynamicValue.double(d)

  private def record(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(Chunk.from(fields))

  private def variant(caseName: String, value: DynamicValue): DynamicValue =
    DynamicValue.Variant(caseName, value)

  def spec: Spec[Any, Any] = suite("DynamicMigrationSpec")(
    addFieldSuite,
    dropFieldSuite,
    renameSuite,
    transformValueSuite,
    mandateSuite,
    optionalizeSuite,
    renameCaseSuite,
    transformCaseSuite,
    compositionSuite,
    reverseSuite,
    nestedMigrationSuite,
    errorHandlingSuite
  )

  private val addFieldSuite = suite("AddField")(
    test("adds a field to a flat record") {
      val original  = record("name" -> stringVal("Alice"))
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.AddField(DynamicOptic.root.field("age"), intVal(25))
        )
      )
      val result = migration(original)
      assertTrue(result == Right(record("name" -> stringVal("Alice"), "age" -> intVal(25))))
    },
    test("adds a field to a nested record") {
      val original = record(
        "name"    -> stringVal("Alice"),
        "address" -> record("street" -> stringVal("Main St"))
      )
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.AddField(
            DynamicOptic.root.field("address").field("zip"),
            stringVal("12345")
          )
        )
      )
      val result   = migration(original)
      val expected = record(
        "name"    -> stringVal("Alice"),
        "address" -> record("street" -> stringVal("Main St"), "zip" -> stringVal("12345"))
      )
      assertTrue(result == Right(expected))
    }
  )

  private val dropFieldSuite = suite("DropField")(
    test("removes a field from a flat record") {
      val original  = record("name" -> stringVal("Alice"), "age" -> intVal(25))
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.DropField(DynamicOptic.root.field("age"), intVal(0))
        )
      )
      val result = migration(original)
      assertTrue(result == Right(record("name" -> stringVal("Alice"))))
    },
    test("removes a field from a nested record") {
      val original = record(
        "name"    -> stringVal("Alice"),
        "address" -> record("street" -> stringVal("Main St"), "zip" -> stringVal("12345"))
      )
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.DropField(
            DynamicOptic.root.field("address").field("zip"),
            stringVal("")
          )
        )
      )
      val result   = migration(original)
      val expected = record(
        "name"    -> stringVal("Alice"),
        "address" -> record("street" -> stringVal("Main St"))
      )
      assertTrue(result == Right(expected))
    }
  )

  private val renameSuite = suite("Rename")(
    test("renames a top-level field") {
      val original  = record("name" -> stringVal("Alice"), "age" -> intVal(25))
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName")
        )
      )
      val result = migration(original)
      assertTrue(result == Right(record("age" -> intVal(25), "fullName" -> stringVal("Alice"))))
    },
    test("renames a nested field") {
      val original = record(
        "person" -> record("firstName" -> stringVal("Alice"), "age" -> intVal(25))
      )
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root.field("person").field("firstName"), "name")
        )
      )
      val result   = migration(original)
      val expected = record(
        "person" -> record("age" -> intVal(25), "name" -> stringVal("Alice"))
      )
      assertTrue(result == Right(expected))
    }
  )

  private val transformValueSuite = suite("TransformValue")(
    test("replaces a field value") {
      val original  = record("status" -> stringVal("active"))
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.TransformValue(DynamicOptic.root.field("status"), stringVal("inactive"))
        )
      )
      val result = migration(original)
      assertTrue(result == Right(record("status" -> stringVal("inactive"))))
    }
  )

  private val mandateSuite = suite("Mandate")(
    test("converts Null to default value") {
      val original  = record("email" -> DynamicValue.Null)
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.Mandate(DynamicOptic.root.field("email"), stringVal("unknown@test.com"))
        )
      )
      val result = migration(original)
      assertTrue(result == Right(record("email" -> stringVal("unknown@test.com"))))
    },
    test("passes through non-null value") {
      val original  = record("email" -> stringVal("alice@test.com"))
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.Mandate(DynamicOptic.root.field("email"), stringVal("unknown@test.com"))
        )
      )
      val result = migration(original)
      assertTrue(result == Right(record("email" -> stringVal("alice@test.com"))))
    }
  )

  private val optionalizeSuite = suite("Optionalize")(
    test("is a no-op at DynamicValue level") {
      val original  = record("email" -> stringVal("alice@test.com"))
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.Optionalize(DynamicOptic.root.field("email"))
        )
      )
      val result = migration(original)
      assertTrue(result == Right(original))
    }
  )

  private val renameCaseSuite = suite("RenameCase")(
    test("renames a variant case at root") {
      val original  = variant("Dog", record("name" -> stringVal("Rex")))
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.RenameCase(DynamicOptic.root, "Dog", "Canine")
        )
      )
      val result = migration(original)
      assertTrue(result == Right(variant("Canine", record("name" -> stringVal("Rex")))))
    },
    test("does not rename a non-matching case") {
      val original  = variant("Cat", record("name" -> stringVal("Whiskers")))
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.RenameCase(DynamicOptic.root, "Dog", "Canine")
        )
      )
      val result = migration(original)
      assertTrue(result == Right(original))
    }
  )

  private val transformCaseSuite = suite("TransformCase")(
    test("applies nested actions to a variant case value") {
      val original  = variant("Circle", record("r" -> doubleVal(5.0)))
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            Chunk(
              MigrationAction.Rename(DynamicOptic.root.field("r"), "radius"),
              MigrationAction.AddField(DynamicOptic.root.field("color"), stringVal("red"))
            )
          )
        )
      )
      val result   = migration(original)
      val expected = variant("Circle", record("radius" -> doubleVal(5.0), "color" -> stringVal("red")))
      assertTrue(result == Right(expected))
    },
    test("applies nested actions to a variant case at a nested path") {
      val original = record(
        "shape" -> variant("Circle", record("r" -> doubleVal(5.0)))
      )
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.TransformCase(
            DynamicOptic.root.field("shape"),
            Chunk(
              MigrationAction.Rename(DynamicOptic.root.field("r"), "radius")
            )
          )
        )
      )
      val result   = migration(original)
      val expected = record(
        "shape" -> variant("Circle", record("radius" -> doubleVal(5.0)))
      )
      assertTrue(result == Right(expected))
    }
  )

  private val compositionSuite = suite("Composition")(
    test("++ composes two migrations sequentially") {
      val m1 = DynamicMigration(
        Chunk(
          MigrationAction.AddField(DynamicOptic.root.field("age"), intVal(0))
        )
      )
      val m2 = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName")
        )
      )
      val combined = m1 ++ m2
      val original = record("name" -> stringVal("Alice"))
      val result   = combined(original)
      val expected = record("age" -> intVal(0), "fullName" -> stringVal("Alice"))
      assertTrue(
        combined.size == 2,
        result == Right(expected)
      )
    },
    test("identity migration is no-op") {
      val m        = DynamicMigration.empty
      val original = record("name" -> stringVal("Alice"))
      assertTrue(m(original) == Right(original))
    }
  )

  private val reverseSuite = suite("Reverse")(
    test("reverse of AddField is DropField") {
      val action   = MigrationAction.AddField(DynamicOptic.root.field("age"), intVal(0))
      val reversed = action.reverse
      assertTrue(reversed == MigrationAction.DropField(DynamicOptic.root.field("age"), intVal(0)))
    },
    test("reverse of DropField is AddField") {
      val action   = MigrationAction.DropField(DynamicOptic.root.field("age"), intVal(0))
      val reversed = action.reverse
      assertTrue(reversed == MigrationAction.AddField(DynamicOptic.root.field("age"), intVal(0)))
    },
    test("reverse of Rename swaps names") {
      val action   = MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName")
      val reversed = action.reverse
      assertTrue(reversed == MigrationAction.Rename(DynamicOptic.root.field("fullName"), "name"))
    },
    test("reverse of RenameCase swaps names") {
      val action   = MigrationAction.RenameCase(DynamicOptic.root, "Dog", "Canine")
      val reversed = action.reverse
      assertTrue(reversed == MigrationAction.RenameCase(DynamicOptic.root, "Canine", "Dog"))
    },
    test("reverse.reverse == original for structural actions") {
      val action = MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName")
      assertTrue(action.reverse.reverse == action)
    },
    test("AddField/DropField roundtrip preserves value") {
      val original = record("name" -> stringVal("Alice"))
      val m        = DynamicMigration(
        Chunk(
          MigrationAction.AddField(DynamicOptic.root.field("age"), intVal(25))
        )
      )
      val result = for {
        migrated <- m(original)
        restored <- m.reverse(migrated)
      } yield restored
      assertTrue(result == Right(original))
    },
    test("Rename roundtrip preserves value (field order may change)") {
      val original = record("name" -> stringVal("Alice"), "age" -> intVal(25))
      val m        = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName")
        )
      )
      val result = for {
        migrated <- m(original)
        restored <- m.reverse(migrated)
      } yield restored
      assertTrue(result.map(_.sortFields) == Right(original.sortFields))
    },
    test("DynamicMigration.reverse reverses action order and each action") {
      val m = DynamicMigration(
        Chunk(
          MigrationAction.AddField(DynamicOptic.root.field("age"), intVal(0)),
          MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName")
        )
      )
      val reversed = m.reverse
      assertTrue(
        reversed.size == 2,
        reversed.actions(0) == MigrationAction.Rename(DynamicOptic.root.field("fullName"), "name"),
        reversed.actions(1) == MigrationAction.DropField(DynamicOptic.root.field("age"), intVal(0))
      )
    }
  )

  private val nestedMigrationSuite = suite("Nested Migration")(
    test("deep nesting: record inside variant") {
      val original = variant(
        "User",
        record(
          "name"    -> stringVal("Alice"),
          "address" -> record("street" -> stringVal("Main St"))
        )
      )
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            Chunk(
              MigrationAction.AddField(
                DynamicOptic.root.field("address").field("zip"),
                stringVal("12345")
              ),
              MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName")
            )
          )
        )
      )
      val result   = migration(original)
      val expected = variant(
        "User",
        record(
          "address"  -> record("street" -> stringVal("Main St"), "zip" -> stringVal("12345")),
          "fullName" -> stringVal("Alice")
        )
      )
      assertTrue(result == Right(expected))
    },
    test("multi-level composition: V1 -> V2 -> V3") {
      val v1 = record("firstName" -> stringVal("Alice"), "lastName" -> stringVal("Smith"))

      val m1to2 = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root.field("firstName"), "name"),
          MigrationAction.DropField(DynamicOptic.root.field("lastName"), stringVal(""))
        )
      )
      val m2to3 = DynamicMigration(
        Chunk(
          MigrationAction.AddField(DynamicOptic.root.field("email"), stringVal("unknown"))
        )
      )

      val combined = m1to2 ++ m2to3
      val result   = combined(v1)
      val expected = record("name" -> stringVal("Alice"), "email" -> stringVal("unknown"))
      assertTrue(result == Right(expected))
    },
    test("TransformCase nested inside another TransformCase") {
      val original = variant(
        "Wrapper",
        record(
          "inner" -> variant("Item", record("x" -> intVal(1)))
        )
      )
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            Chunk(
              MigrationAction.TransformCase(
                DynamicOptic.root.field("inner"),
                Chunk(
                  MigrationAction.Rename(DynamicOptic.root.field("x"), "value")
                )
              )
            )
          )
        )
      )
      val result   = migration(original)
      val expected = variant(
        "Wrapper",
        record(
          "inner" -> variant("Item", record("value" -> intVal(1)))
        )
      )
      assertTrue(result == Right(expected))
    }
  )

  private val errorHandlingSuite = suite("Error Handling")(
    test("AddField fails when field already exists") {
      val original = record("name" -> stringVal("Alice"))
      val m        = DynamicMigration(
        Chunk(
          MigrationAction.AddField(DynamicOptic.root.field("name"), stringVal("Bob"))
        )
      )
      val result = m(original)
      assertTrue(
        result.isLeft,
        result.left.exists(_.isInstanceOf[MigrationError])
      )
    },
    test("DropField fails when field does not exist") {
      val original = record("name" -> stringVal("Alice"))
      val m        = DynamicMigration(
        Chunk(
          MigrationAction.DropField(DynamicOptic.root.field("age"), intVal(0))
        )
      )
      assertTrue(m(original).isLeft)
    },
    test("Rename fails when source field does not exist") {
      val original = record("name" -> stringVal("Alice"))
      val m        = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root.field("missing"), "newName")
        )
      )
      assertTrue(m(original).isLeft)
    },
    test("error includes path information") {
      val original = record("name" -> stringVal("Alice"))
      val m        = DynamicMigration(
        Chunk(
          MigrationAction.DropField(DynamicOptic.root.field("missing"), intVal(0))
        )
      )
      val result = m(original)
      assertTrue(
        result.isLeft,
        result.left.exists(_.path == DynamicOptic.root.field("missing"))
      )
    }
  )
}
