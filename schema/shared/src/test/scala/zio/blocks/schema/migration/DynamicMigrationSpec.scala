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
    renameEdgeSuite,
    transformValueSuite,
    changeTypeSuite,
    mandateSuite,
    mandateVariantSuite,
    optionalizeSuite,
    renameCaseSuite,
    transformCaseSuite,
    transformCaseEdgeSuite,
    compositionSuite,
    reverseSuite,
    reverseSuiteFull,
    nestedMigrationSuite,
    unsupportedActionsSuite,
    toStringSuite,
    migrationErrorSuite,
    actionCoverageSuite,
    errorHandlingSuite,
    additionalBranchCoverageSuite
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

  private val changeTypeSuite = suite("ChangeType")(
    test("replaces field value with new type") {
      val original  = record("count" -> intVal(42))
      val migration = DynamicMigration(
        Chunk(MigrationAction.ChangeType(DynamicOptic.root.field("count"), stringVal("42")))
      )
      assertTrue(migration(original) == Right(record("count" -> stringVal("42"))))
    }
  )

  private val mandateVariantSuite = suite("Mandate variants")(
    test("unwraps Variant Some") {
      val original  = record("email" -> variant("Some", stringVal("a@b.com")))
      val migration = DynamicMigration(
        Chunk(MigrationAction.Mandate(DynamicOptic.root.field("email"), stringVal("default")))
      )
      assertTrue(migration(original) == Right(record("email" -> stringVal("a@b.com"))))
    },
    test("replaces Variant None with default") {
      val original  = record("email" -> variant("None", DynamicValue.unit))
      val migration = DynamicMigration(
        Chunk(MigrationAction.Mandate(DynamicOptic.root.field("email"), stringVal("default")))
      )
      assertTrue(migration(original) == Right(record("email" -> stringVal("default"))))
    }
  )

  private val transformCaseEdgeSuite = suite("TransformCase edge cases")(
    test("TransformCase at root on non-variant applies nested actions directly") {
      val original  = record("x" -> intVal(1))
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            Chunk(MigrationAction.AddField(DynamicOptic.root.field("y"), intVal(2)))
          )
        )
      )
      assertTrue(migration(original) == Right(record("x" -> intVal(1), "y" -> intVal(2))))
    },
    test("TransformCase at nested path on non-variant record") {
      val original  = record("inner" -> record("x" -> intVal(1)))
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.TransformCase(
            DynamicOptic.root.field("inner"),
            Chunk(MigrationAction.AddField(DynamicOptic.root.field("y"), intVal(2)))
          )
        )
      )
      assertTrue(migration(original) == Right(record("inner" -> record("x" -> intVal(1), "y" -> intVal(2)))))
    },
    test("TransformCase at nested path propagates nested error") {
      val original  = record("inner" -> variant("A", record()))
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.TransformCase(
            DynamicOptic.root.field("inner"),
            Chunk(MigrationAction.DropField(DynamicOptic.root.field("nonexistent"), DynamicValue.Null))
          )
        )
      )
      assertTrue(migration(original).isLeft)
    }
  )

  private val unsupportedActionsSuite = suite("Unsupported actions return errors")(
    test("Join returns unsupported error") {
      val m = DynamicMigration(
        Chunk(MigrationAction.Join(DynamicOptic.root, Chunk.empty, DynamicValue.Null))
      )
      assertTrue(m(record()).isLeft)
    },
    test("Split returns unsupported error") {
      val m = DynamicMigration(
        Chunk(MigrationAction.Split(DynamicOptic.root, Chunk.empty, DynamicValue.Null))
      )
      assertTrue(m(record()).isLeft)
    },
    test("TransformElements returns unsupported error") {
      val m = DynamicMigration(
        Chunk(MigrationAction.TransformElements(DynamicOptic.root, DynamicValue.Null))
      )
      assertTrue(m(record()).isLeft)
    },
    test("TransformKeys returns unsupported error") {
      val m = DynamicMigration(
        Chunk(MigrationAction.TransformKeys(DynamicOptic.root, DynamicValue.Null))
      )
      assertTrue(m(record()).isLeft)
    },
    test("TransformValues returns unsupported error") {
      val m = DynamicMigration(
        Chunk(MigrationAction.TransformValues(DynamicOptic.root, DynamicValue.Null))
      )
      assertTrue(m(record()).isLeft)
    }
  )

  private val toStringSuite = suite("toString")(
    test("empty migration") {
      assertTrue(DynamicMigration.empty.toString == "DynamicMigration {}")
    },
    test("non-empty migration") {
      val m = DynamicMigration(Chunk(MigrationAction.AddField(DynamicOptic.root.field("x"), intVal(1))))
      assertTrue(m.toString.startsWith("DynamicMigration {"))
    }
  )

  private val migrationErrorSuite = suite("MigrationError")(
    test("message without path") {
      val err = MigrationError("test error")
      assertTrue(err.getMessage == "test error")
    },
    test("message with path") {
      val err = MigrationError("test error", DynamicOptic.root.field("name"))
      assertTrue(err.getMessage.contains("test error"), err.getMessage.contains(".name"))
    },
    test("atField prepends field to path") {
      val err = MigrationError("fail").atField("parent")
      assertTrue(err.path.nodes.nonEmpty)
    },
    test("atCase prepends case to path") {
      val err = MigrationError("fail").atCase("MyCase")
      assertTrue(err.path.nodes.nonEmpty)
    }
  )

  private val renameEdgeSuite = suite("Rename edge cases")(
    test("Rename with empty path returns error") {
      val m = DynamicMigration(Chunk(MigrationAction.Rename(DynamicOptic.root, "newName")))
      assertTrue(m(record("x" -> intVal(1))).isLeft)
    },
    test("Rename.reverse with non-field last node returns self") {
      val optic    = DynamicOptic.root.caseOf("Foo")
      val action   = MigrationAction.Rename(optic, "bar")
      val reversed = action.reverse
      assertTrue(reversed == action)
    }
  )

  private val reverseSuiteFull = suite("Reverse additional")(
    test("Join.reverse is Split") {
      val paths  = Chunk(DynamicOptic.root.field("a"))
      val action = MigrationAction.Join(DynamicOptic.root.field("target"), paths, DynamicValue.Null)
      assertTrue(action.reverse.isInstanceOf[MigrationAction.Split])
    },
    test("Split.reverse is Join") {
      val paths  = Chunk(DynamicOptic.root.field("a"))
      val action = MigrationAction.Split(DynamicOptic.root.field("source"), paths, DynamicValue.Null)
      assertTrue(action.reverse.isInstanceOf[MigrationAction.Join])
    },
    test("TransformElements.reverse is self") {
      val action = MigrationAction.TransformElements(DynamicOptic.root, DynamicValue.Null)
      assertTrue(action.reverse eq action)
    },
    test("TransformKeys.reverse is self") {
      val action = MigrationAction.TransformKeys(DynamicOptic.root, DynamicValue.Null)
      assertTrue(action.reverse eq action)
    },
    test("TransformValues.reverse is self") {
      val action = MigrationAction.TransformValues(DynamicOptic.root, DynamicValue.Null)
      assertTrue(action.reverse eq action)
    },
    test("ChangeType.reverse is self") {
      val action = MigrationAction.ChangeType(DynamicOptic.root.field("x"), stringVal("0"))
      assertTrue(action.reverse eq action)
    },
    test("TransformValue.reverse is self") {
      val action = MigrationAction.TransformValue(DynamicOptic.root.field("x"), stringVal("0"))
      assertTrue(action.reverse eq action)
    },
    test("Mandate.reverse is Optionalize") {
      val action = MigrationAction.Mandate(DynamicOptic.root.field("x"), intVal(0))
      assertTrue(action.reverse.isInstanceOf[MigrationAction.Optionalize])
    },
    test("Optionalize.reverse is Mandate with Null default") {
      val action   = MigrationAction.Optionalize(DynamicOptic.root.field("x"))
      val reversed = action.reverse.asInstanceOf[MigrationAction.Mandate]
      assertTrue(reversed.default == DynamicValue.Null)
    },
    test("TransformCase.reverse reverses nested actions") {
      val action = MigrationAction.TransformCase(
        DynamicOptic.root,
        Chunk(
          MigrationAction.AddField(DynamicOptic.root.field("a"), intVal(1)),
          MigrationAction.AddField(DynamicOptic.root.field("b"), intVal(2))
        )
      )
      val reversed = action.reverse.asInstanceOf[MigrationAction.TransformCase]
      assertTrue(
        reversed.actions.length == 2,
        reversed.actions(0).isInstanceOf[MigrationAction.DropField],
        reversed.actions(1).isInstanceOf[MigrationAction.DropField]
      )
    }
  )

  private val actionCoverageSuite = suite("Action coverage")(
    test("all action types have correct at field") {
      val root                           = DynamicOptic.root
      val f                              = root.field("x")
      val actions: List[MigrationAction] = List(
        MigrationAction.AddField(f, intVal(0)),
        MigrationAction.DropField(f, intVal(0)),
        MigrationAction.Rename(f, "y"),
        MigrationAction.TransformValue(f, intVal(1)),
        MigrationAction.Mandate(f, intVal(0)),
        MigrationAction.Optionalize(f),
        MigrationAction.ChangeType(f, stringVal("0")),
        MigrationAction.RenameCase(root, "A", "B"),
        MigrationAction.TransformCase(root, Chunk.empty),
        MigrationAction.Join(f, Chunk.empty, DynamicValue.Null),
        MigrationAction.Split(f, Chunk.empty, DynamicValue.Null),
        MigrationAction.TransformElements(f, DynamicValue.Null),
        MigrationAction.TransformKeys(f, DynamicValue.Null),
        MigrationAction.TransformValues(f, DynamicValue.Null)
      )
      assertTrue(actions.forall(a => a.at != null))
    },
    test("all actions have stable reverse.reverse") {
      val f                              = DynamicOptic.root.field("x")
      val actions: List[MigrationAction] = List(
        MigrationAction.AddField(f, intVal(0)),
        MigrationAction.DropField(f, intVal(0)),
        MigrationAction.Rename(f, "y"),
        MigrationAction.TransformValue(f, intVal(1)),
        MigrationAction.Mandate(f, intVal(0)),
        MigrationAction.Optionalize(f),
        MigrationAction.ChangeType(f, stringVal("0")),
        MigrationAction.RenameCase(DynamicOptic.root, "A", "B"),
        MigrationAction.TransformCase(DynamicOptic.root, Chunk.empty),
        MigrationAction.Join(f, Chunk.empty, DynamicValue.Null),
        MigrationAction.Split(f, Chunk.empty, DynamicValue.Null),
        MigrationAction.TransformElements(f, DynamicValue.Null),
        MigrationAction.TransformKeys(f, DynamicValue.Null),
        MigrationAction.TransformValues(f, DynamicValue.Null)
      )
      assertTrue(actions.forall(a => a.reverse != null && a.reverse.reverse != null))
    },
    test("action equality and hashCode") {
      val a1 = MigrationAction.AddField(DynamicOptic.root.field("x"), intVal(1))
      val a2 = MigrationAction.AddField(DynamicOptic.root.field("x"), intVal(1))
      val a3 = MigrationAction.AddField(DynamicOptic.root.field("y"), intVal(1))
      assertTrue(a1 == a2, a1.hashCode == a2.hashCode, a1 != a3)
    },
    test("action toString contains class name") {
      val actions: List[MigrationAction] = List(
        MigrationAction.AddField(DynamicOptic.root.field("x"), intVal(0)),
        MigrationAction.DropField(DynamicOptic.root.field("x"), intVal(0)),
        MigrationAction.Rename(DynamicOptic.root.field("x"), "y"),
        MigrationAction.TransformValue(DynamicOptic.root.field("x"), intVal(1)),
        MigrationAction.Mandate(DynamicOptic.root.field("x"), intVal(0)),
        MigrationAction.Optionalize(DynamicOptic.root.field("x")),
        MigrationAction.ChangeType(DynamicOptic.root.field("x"), stringVal("0")),
        MigrationAction.RenameCase(DynamicOptic.root, "A", "B"),
        MigrationAction.TransformCase(DynamicOptic.root, Chunk.empty),
        MigrationAction.Join(DynamicOptic.root.field("x"), Chunk.empty, DynamicValue.Null),
        MigrationAction.Split(DynamicOptic.root.field("x"), Chunk.empty, DynamicValue.Null),
        MigrationAction.TransformElements(DynamicOptic.root.field("x"), DynamicValue.Null),
        MigrationAction.TransformKeys(DynamicOptic.root.field("x"), DynamicValue.Null),
        MigrationAction.TransformValues(DynamicOptic.root.field("x"), DynamicValue.Null)
      )
      assertTrue(actions.forall(_.toString.nonEmpty))
    },
    test("DynamicMigration isEmpty and size") {
      val empty    = DynamicMigration.empty
      val nonEmpty = DynamicMigration(Chunk(MigrationAction.Optionalize(DynamicOptic.root.field("x"))))
      assertTrue(empty.isEmpty, empty.size == 0, !nonEmpty.isEmpty, nonEmpty.size == 1)
    },
    test("Migration.identity law") {
      val intSchema: Schema[Int] = Schema[Int]
      val m                      = Migration.identity[Int](intSchema)
      assertTrue(m.isEmpty, m(42) == Right(42))
    },
    test("RenameCase at nested path") {
      val original = record("shape" -> variant("Old", record("x" -> intVal(1))))
      val m        = DynamicMigration(
        Chunk(MigrationAction.RenameCase(DynamicOptic.root.field("shape"), "Old", "New"))
      )
      val result = m(original)
      assertTrue(result == Right(record("shape" -> variant("New", record("x" -> intVal(1))))))
    }
  )

  private val errorHandlingSuite = suite("Error Handling")(
    test("AddField fails when field already exists") {
      val original = record("name" -> stringVal("Alice"))
      val m        = DynamicMigration(
        Chunk(MigrationAction.AddField(DynamicOptic.root.field("name"), stringVal("Bob")))
      )
      val result = m(original)
      assertTrue(result.isLeft, result.left.exists(_.isInstanceOf[MigrationError]))
    },
    test("DropField fails when field does not exist") {
      val original = record("name" -> stringVal("Alice"))
      val m        = DynamicMigration(
        Chunk(MigrationAction.DropField(DynamicOptic.root.field("age"), intVal(0)))
      )
      assertTrue(m(original).isLeft)
    },
    test("Rename fails when source field does not exist") {
      val original = record("name" -> stringVal("Alice"))
      val m        = DynamicMigration(
        Chunk(MigrationAction.Rename(DynamicOptic.root.field("missing"), "newName"))
      )
      assertTrue(m(original).isLeft)
    },
    test("error includes path information") {
      val original = record("name" -> stringVal("Alice"))
      val m        = DynamicMigration(
        Chunk(MigrationAction.DropField(DynamicOptic.root.field("missing"), intVal(0)))
      )
      val result = m(original)
      assertTrue(result.isLeft, result.left.exists(_.path == DynamicOptic.root.field("missing")))
    }
  )

  private val additionalBranchCoverageSuite = suite("Additional branch coverage")(
    test("multi-action chain where middle action fails") {
      val original = record("a" -> intVal(1), "b" -> intVal(2))
      val m        = DynamicMigration(
        Chunk(
          MigrationAction.AddField(DynamicOptic.root.field("c"), intVal(3)),
          MigrationAction.DropField(DynamicOptic.root.field("nonexistent"), intVal(0)),
          MigrationAction.AddField(DynamicOptic.root.field("d"), intVal(4))
        )
      )
      assertTrue(m(original).isLeft)
    },
    test("TransformCase at root on Variant where nested migration fails") {
      val original = variant("A", record("x" -> intVal(1)))
      val m        = DynamicMigration(
        Chunk(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            Chunk(MigrationAction.DropField(DynamicOptic.root.field("nonexistent"), DynamicValue.Null))
          )
        )
      )
      assertTrue(m(original).isLeft)
    },
    test("TransformCase at root on non-Variant where nested migration fails") {
      val original = record("x" -> intVal(1))
      val m        = DynamicMigration(
        Chunk(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            Chunk(MigrationAction.DropField(DynamicOptic.root.field("nonexistent"), DynamicValue.Null))
          )
        )
      )
      assertTrue(m(original).isLeft)
    },
    test("TransformCase nested path non-Variant where nested migration fails") {
      val original = record("inner" -> record("x" -> intVal(1)))
      val m        = DynamicMigration(
        Chunk(
          MigrationAction.TransformCase(
            DynamicOptic.root.field("inner"),
            Chunk(MigrationAction.DropField(DynamicOptic.root.field("nonexistent"), DynamicValue.Null))
          )
        )
      )
      assertTrue(m(original).isLeft)
    },
    test("RenameCase at nested path with non-matching variant name") {
      val original = record("shape" -> variant("Circle", record("r" -> intVal(5))))
      val m        = DynamicMigration(
        Chunk(MigrationAction.RenameCase(DynamicOptic.root.field("shape"), "Square", "Rectangle"))
      )
      val result = m(original)
      assertTrue(result == Right(record("shape" -> variant("Circle", record("r" -> intVal(5))))))
    },
    test("Migration.andThen composes two migrations") {
      val intSchema: Schema[Int] = Schema[Int]
      val m1                     = Migration.identity[Int](intSchema)
      val m2                     = Migration.identity[Int](intSchema)
      val composed               = m1.andThen(m2)
      assertTrue(composed(42) == Right(42))
    },
    test("Migration.apply fails when fromDynamicValue fails") {
      val stringSchema: Schema[String] = Schema[String]
      val intSchema: Schema[Int]       = Schema[Int]
      val m                            = Migration[String, Int](DynamicMigration.empty, stringSchema, intSchema)
      assertTrue(m("hello").isLeft)
    },
    test("DynamicMigration composition via ++") {
      val m1       = DynamicMigration(Chunk(MigrationAction.AddField(DynamicOptic.root.field("a"), intVal(1))))
      val m2       = DynamicMigration(Chunk(MigrationAction.AddField(DynamicOptic.root.field("b"), intVal(2))))
      val composed = m1 ++ m2
      val result   = composed(record())
      assertTrue(result == Right(record("a" -> intVal(1), "b" -> intVal(2))))
    }
  )
}
