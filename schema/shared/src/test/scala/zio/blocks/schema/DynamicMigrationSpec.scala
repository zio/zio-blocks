package zio.blocks.schema

import zio.test._

object DynamicMigrationSpec extends SchemaBaseSpec {

  // Test data
  val nameVal: DynamicValue      = DynamicValue.string("Alice")
  val ageVal: DynamicValue       = DynamicValue.int(30)
  val cityVal: DynamicValue      = DynamicValue.string("NYC")
  val personRecord: DynamicValue = DynamicValue.Record("name" -> nameVal, "age" -> ageVal)
  val nestedRecord: DynamicValue = DynamicValue.Record(
    "person" -> personRecord,
    "city"   -> cityVal
  )

  def spec: Spec[TestEnvironment, Any] = suite("DynamicMigrationSpec")(
    lawsSuite,
    addFieldSuite,
    dropFieldSuite,
    renameSuite,
    mandateOptionalizeSuite,
    renameCaseSuite,
    transformCaseSuite,
    transformValueSuite,
    changeTypeSuite,
    compositionSuite,
    errorSuite
  )

  // ─── Laws ─────────────────────────────────────────────────────────────────

  lazy val lawsSuite: Spec[Any, Nothing] = suite("Laws")(
    test("identity: identity migration returns the original value") {
      val result = DynamicMigration.identity(personRecord)
      assertTrue(result == Right(personRecord))
    },
    test("identity: empty migration returns the original value") {
      val result = DynamicMigration.empty(personRecord)
      assertTrue(result == Right(personRecord))
    },
    test("associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
      val m1 = DynamicMigration(MigrationAction.AddField(DynamicOptic.root, "city", cityVal))
      val m2 = DynamicMigration(MigrationAction.Rename(DynamicOptic.root, "name", "fullName"))
      val m3 = DynamicMigration(MigrationAction.DropField(DynamicOptic.root, "age", ageVal))

      val leftGrouped  = (m1 ++ m2) ++ m3
      val rightGrouped = m1 ++ (m2 ++ m3)

      val resultLeft  = leftGrouped(personRecord)
      val resultRight = rightGrouped(personRecord)

      assertTrue(resultLeft == resultRight) &&
      assertTrue(resultLeft.isRight)
    },
    test("structural reverse: m.reverse.reverse == m") {
      val m = DynamicMigration(
        Vector(
          MigrationAction.AddField(DynamicOptic.root, "city", cityVal),
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName"),
          MigrationAction.DropField(DynamicOptic.root, "age", ageVal)
        )
      )

      assertTrue(m.reverse.reverse == m)
    },
    test("structural reverse: single action reverse.reverse == action") {
      val actions = Vector(
        MigrationAction.AddField(DynamicOptic.root, "x", DynamicValue.int(1)),
        MigrationAction.DropField(DynamicOptic.root, "x", DynamicValue.int(1)),
        MigrationAction.Rename(DynamicOptic.root, "a", "b"),
        MigrationAction.Optionalize(DynamicOptic.root),
        MigrationAction.RenameCase(DynamicOptic.root, "Old", "New"),
        MigrationAction.ChangeType(DynamicOptic.root, DynamicValue.int(1), Some(DynamicValue.string("1"))),
        MigrationAction.TransformValue(DynamicOptic.root, DynamicValue.int(1), Some(DynamicValue.int(2))),
        MigrationAction.TransformElements(DynamicOptic.root, DynamicValue.int(1), Some(DynamicValue.int(2))),
        MigrationAction.TransformKeys(DynamicOptic.root, DynamicValue.int(1), Some(DynamicValue.int(2))),
        MigrationAction.TransformValues(DynamicOptic.root, DynamicValue.int(1), Some(DynamicValue.int(2)))
      )

      assertTrue(actions.forall(a => a.reverse.reverse == a))
    },
    test("best-effort semantic inverse: m.apply(a) == Right(b) => m.reverse.apply(b) == Right(a)") {
      val m = DynamicMigration(
        Vector(
          MigrationAction.AddField(DynamicOptic.root, "city", cityVal),
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
      )

      val Right(migrated) = m(personRecord): @unchecked
      val Right(restored) = m.reverse(migrated): @unchecked

      assertTrue(restored == personRecord)
    }
  )

  // ─── AddField ────────────────────────────────────────────────────────────

  lazy val addFieldSuite: Spec[Any, Nothing] = suite("AddField")(
    test("adds a field to a record at root") {
      val action    = MigrationAction.AddField(DynamicOptic.root, "city", cityVal)
      val migration = DynamicMigration(action)
      val result    = migration(personRecord)

      val expected = DynamicValue.Record("name" -> nameVal, "age" -> ageVal, "city" -> cityVal)
      assertTrue(result == Right(expected))
    },
    test("adds a field to a nested record") {
      val action    = MigrationAction.AddField(DynamicOptic.root.field("person"), "email", DynamicValue.string("a@b.c"))
      val migration = DynamicMigration(action)
      val result    = migration(nestedRecord)

      val expected = DynamicValue.Record(
        "person" -> DynamicValue.Record("name" -> nameVal, "age" -> ageVal, "email" -> DynamicValue.string("a@b.c")),
        "city"   -> cityVal
      )
      assertTrue(result == Right(expected))
    },
    test("fails when field already exists") {
      val action    = MigrationAction.AddField(DynamicOptic.root, "name", cityVal)
      val migration = DynamicMigration(action)
      val result    = migration(personRecord)

      assertTrue(result.isLeft)
    }
  )

  // ─── DropField ───────────────────────────────────────────────────────────

  lazy val dropFieldSuite: Spec[Any, Nothing] = suite("DropField")(
    test("drops a field from a record at root") {
      val action    = MigrationAction.DropField(DynamicOptic.root, "age", ageVal)
      val migration = DynamicMigration(action)
      val result    = migration(personRecord)

      val expected = DynamicValue.Record("name" -> nameVal)
      assertTrue(result == Right(expected))
    },
    test("drops a field from a nested record") {
      val action    = MigrationAction.DropField(DynamicOptic.root.field("person"), "age", ageVal)
      val migration = DynamicMigration(action)
      val result    = migration(nestedRecord)

      val expected = DynamicValue.Record(
        "person" -> DynamicValue.Record("name" -> nameVal),
        "city"   -> cityVal
      )
      assertTrue(result == Right(expected))
    },
    test("fails when field does not exist") {
      val action    = MigrationAction.DropField(DynamicOptic.root, "nonexistent", DynamicValue.Null)
      val migration = DynamicMigration(action)
      val result    = migration(personRecord)

      assertTrue(result.isLeft)
    }
  )

  // ─── Rename ──────────────────────────────────────────────────────────────

  lazy val renameSuite: Spec[Any, Nothing] = suite("Rename")(
    test("renames a field at root") {
      val action    = MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
      val migration = DynamicMigration(action)
      val result    = migration(personRecord)

      val expected = DynamicValue.Record("fullName" -> nameVal, "age" -> ageVal)
      assertTrue(result == Right(expected))
    },
    test("renames a field in a nested record") {
      val action    = MigrationAction.Rename(DynamicOptic.root.field("person"), "name", "fullName")
      val migration = DynamicMigration(action)
      val result    = migration(nestedRecord)

      val expected = DynamicValue.Record(
        "person" -> DynamicValue.Record("fullName" -> nameVal, "age" -> ageVal),
        "city"   -> cityVal
      )
      assertTrue(result == Right(expected))
    },
    test("fails when source field does not exist") {
      val action    = MigrationAction.Rename(DynamicOptic.root, "nonexistent", "newName")
      val migration = DynamicMigration(action)
      val result    = migration(personRecord)

      assertTrue(result.isLeft)
    },
    test("fails when target field already exists") {
      val action    = MigrationAction.Rename(DynamicOptic.root, "name", "age")
      val migration = DynamicMigration(action)
      val result    = migration(personRecord)

      assertTrue(result.isLeft)
    },
    test("rename is reversible") {
      val action    = MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
      val migration = DynamicMigration(action)

      val Right(migrated) = migration(personRecord): @unchecked
      val Right(restored) = migration.reverse(migrated): @unchecked

      assertTrue(restored == personRecord)
    }
  )

  // ─── Mandate / Optionalize ───────────────────────────────────────────────

  lazy val mandateOptionalizeSuite: Spec[Any, Nothing] = suite("Mandate and Optionalize")(
    test("mandate unwraps Some variant") {
      val optionalRecord = DynamicValue.Record(
        "name" -> nameVal,
        "age"  -> DynamicValue.Variant("Some", ageVal)
      )
      val action    = MigrationAction.Mandate(DynamicOptic.root.field("age"), DynamicValue.int(0))
      val migration = DynamicMigration(action)
      val result    = migration(optionalRecord)

      assertTrue(result == Right(personRecord))
    },
    test("mandate uses default for None variant") {
      val optionalRecord = DynamicValue.Record(
        "name" -> nameVal,
        "age"  -> DynamicValue.Variant("None", DynamicValue.Record.empty)
      )
      val action    = MigrationAction.Mandate(DynamicOptic.root.field("age"), DynamicValue.int(0))
      val migration = DynamicMigration(action)
      val result    = migration(optionalRecord)

      val expected = DynamicValue.Record("name" -> nameVal, "age" -> DynamicValue.int(0))
      assertTrue(result == Right(expected))
    },
    test("mandate uses default for Null") {
      val nullRecord = DynamicValue.Record(
        "name" -> nameVal,
        "age"  -> DynamicValue.Null
      )
      val action    = MigrationAction.Mandate(DynamicOptic.root.field("age"), DynamicValue.int(0))
      val migration = DynamicMigration(action)
      val result    = migration(nullRecord)

      val expected = DynamicValue.Record("name" -> nameVal, "age" -> DynamicValue.int(0))
      assertTrue(result == Right(expected))
    },
    test("optionalize wraps value in Some") {
      val action    = MigrationAction.Optionalize(DynamicOptic.root.field("age"))
      val migration = DynamicMigration(action)
      val result    = migration(personRecord)

      val expected = DynamicValue.Record(
        "name" -> nameVal,
        "age"  -> DynamicValue.Variant("Some", ageVal)
      )
      assertTrue(result == Right(expected))
    },
    test("optionalize wraps Null in None") {
      val nullRecord = DynamicValue.Record(
        "name" -> nameVal,
        "age"  -> DynamicValue.Null
      )
      val action    = MigrationAction.Optionalize(DynamicOptic.root.field("age"))
      val migration = DynamicMigration(action)
      val result    = migration(nullRecord)

      val expected = DynamicValue.Record(
        "name" -> nameVal,
        "age"  -> DynamicValue.Variant("None", DynamicValue.Record.empty)
      )
      assertTrue(result == Right(expected))
    }
  )

  // ─── RenameCase ──────────────────────────────────────────────────────────

  lazy val renameCaseSuite: Spec[Any, Nothing] = suite("RenameCase")(
    test("renames a matching variant case") {
      val variant   = DynamicValue.Variant("OldName", personRecord)
      val action    = MigrationAction.RenameCase(DynamicOptic.root, "OldName", "NewName")
      val migration = DynamicMigration(action)
      val result    = migration(variant)

      assertTrue(result == Right(DynamicValue.Variant("NewName", personRecord)))
    },
    test("leaves non-matching variant case unchanged") {
      val variant   = DynamicValue.Variant("Other", personRecord)
      val action    = MigrationAction.RenameCase(DynamicOptic.root, "OldName", "NewName")
      val migration = DynamicMigration(action)
      val result    = migration(variant)

      assertTrue(result == Right(variant))
    },
    test("rename case is reversible") {
      val variant   = DynamicValue.Variant("OldName", personRecord)
      val action    = MigrationAction.RenameCase(DynamicOptic.root, "OldName", "NewName")
      val migration = DynamicMigration(action)

      val Right(migrated) = migration(variant): @unchecked
      val Right(restored) = migration.reverse(migrated): @unchecked

      assertTrue(restored == variant)
    }
  )

  // ─── TransformCase ───────────────────────────────────────────────────────

  lazy val transformCaseSuite: Spec[Any, Nothing] = suite("TransformCase")(
    test("transforms inner value of matching case") {
      val variant = DynamicValue.Variant("Person", personRecord)
      val action  = MigrationAction.TransformCase(
        DynamicOptic.root,
        "Person",
        Vector(MigrationAction.Rename(DynamicOptic.root, "name", "fullName"))
      )
      val migration = DynamicMigration(action)
      val result    = migration(variant)

      val expected = DynamicValue.Variant(
        "Person",
        DynamicValue.Record("fullName" -> nameVal, "age" -> ageVal)
      )
      assertTrue(result == Right(expected))
    },
    test("leaves non-matching case unchanged") {
      val variant = DynamicValue.Variant("Other", personRecord)
      val action  = MigrationAction.TransformCase(
        DynamicOptic.root,
        "Person",
        Vector(MigrationAction.Rename(DynamicOptic.root, "name", "fullName"))
      )
      val migration = DynamicMigration(action)
      val result    = migration(variant)

      assertTrue(result == Right(variant))
    },
    test("transform case is reversible on matching case") {
      val variant = DynamicValue.Variant("Person", personRecord)
      val action  = MigrationAction.TransformCase(
        DynamicOptic.root,
        "Person",
        Vector(MigrationAction.Rename(DynamicOptic.root, "name", "fullName"))
      )
      val migration = DynamicMigration(action)

      val Right(migrated) = migration(variant): @unchecked
      val Right(restored) = migration.reverse(migrated): @unchecked

      assertTrue(restored == variant)
    }
  )

  // ─── TransformValue ─────────────────────────────────────────────────────

  lazy val transformValueSuite: Spec[Any, Nothing] = suite("TransformValue")(
    test("replaces value at path") {
      val action    = MigrationAction.TransformValue(DynamicOptic.root.field("age"), DynamicValue.int(99), None)
      val migration = DynamicMigration(action)
      val result    = migration(personRecord)

      val expected = DynamicValue.Record("name" -> nameVal, "age" -> DynamicValue.int(99))
      assertTrue(result == Right(expected))
    }
  )

  // ─── ChangeType ─────────────────────────────────────────────────────────

  lazy val changeTypeSuite: Spec[Any, Nothing] = suite("ChangeType")(
    test("replaces value at path with converted value") {
      val action = MigrationAction.ChangeType(
        DynamicOptic.root.field("age"),
        DynamicValue.string("30"),
        Some(DynamicValue.int(30))
      )
      val migration = DynamicMigration(action)
      val result    = migration(personRecord)

      val expected = DynamicValue.Record("name" -> nameVal, "age" -> DynamicValue.string("30"))
      assertTrue(result == Right(expected))
    }
  )

  // ─── Composition ────────────────────────────────────────────────────────

  lazy val compositionSuite: Spec[Any, Nothing] = suite("Composition")(
    test("composes multiple actions sequentially") {
      val migration = DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName"),
          MigrationAction.AddField(DynamicOptic.root, "country", DynamicValue.string("US")),
          MigrationAction.DropField(DynamicOptic.root, "age", ageVal)
        )
      )
      val result = migration(personRecord)

      val expected = DynamicValue.Record(
        "fullName" -> nameVal,
        "country"  -> DynamicValue.string("US")
      )
      assertTrue(result == Right(expected))
    },
    test("composes migrations with ++ operator") {
      val m1 = DynamicMigration(MigrationAction.Rename(DynamicOptic.root, "name", "fullName"))
      val m2 = DynamicMigration(MigrationAction.AddField(DynamicOptic.root, "country", DynamicValue.string("US")))

      val composed = m1 ++ m2
      val result   = composed(personRecord)

      val expected = DynamicValue.Record(
        "fullName" -> nameVal,
        "age"      -> ageVal,
        "country"  -> DynamicValue.string("US")
      )
      assertTrue(result == Right(expected))
    },
    test("andThen is an alias for ++") {
      val m1 = DynamicMigration(MigrationAction.Rename(DynamicOptic.root, "name", "fullName"))
      val m2 = DynamicMigration(MigrationAction.AddField(DynamicOptic.root, "country", DynamicValue.string("US")))

      val composed1 = m1 ++ m2
      val composed2 = m1.andThen(m2)

      val result1 = composed1(personRecord)
      val result2 = composed2(personRecord)

      assertTrue(result1 == result2)
    },
    test("complex multi-step migration is reversible") {
      val migration = DynamicMigration(
        Vector(
          MigrationAction.AddField(DynamicOptic.root, "city", cityVal),
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
      )

      val Right(migrated) = migration(personRecord): @unchecked
      val Right(restored) = migration.reverse(migrated): @unchecked

      assertTrue(restored == personRecord)
    }
  )

  // ─── Error handling ─────────────────────────────────────────────────────

  lazy val errorSuite: Spec[Any, Nothing] = suite("Error handling")(
    test("error includes path information") {
      val action    = MigrationAction.DropField(DynamicOptic.root.field("person"), "nonexistent", DynamicValue.Null)
      val migration = DynamicMigration(action)
      val result    = migration(nestedRecord)

      assertTrue(result.isLeft) &&
      assertTrue(result.left.toOption.exists(_.path.nodes.nonEmpty))
    },
    test("stops at first error in a multi-action migration") {
      val migration = DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName"),
          MigrationAction.DropField(DynamicOptic.root, "nonexistent", DynamicValue.Null), // fails
          MigrationAction.AddField(DynamicOptic.root, "city", cityVal)                    // should not execute
        )
      )
      val result = migration(personRecord)

      assertTrue(result.isLeft)
    },
    test("error on non-record target for record operations") {
      val action    = MigrationAction.AddField(DynamicOptic.root, "x", DynamicValue.int(1))
      val migration = DynamicMigration(action)
      val result    = migration(DynamicValue.string("not a record"))

      assertTrue(result.isLeft)
    },
    test("error on invalid path") {
      val action    = MigrationAction.Rename(DynamicOptic.root.field("nonexistent").field("deep"), "a", "b")
      val migration = DynamicMigration(action)
      val result    = migration(personRecord)

      assertTrue(result.isLeft)
    },
    test("toString on empty migration") {
      assertTrue(DynamicMigration.empty.toString == "DynamicMigration {}")
    },
    test("toString on non-empty migration") {
      val migration = DynamicMigration(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
      assertTrue(migration.toString.contains("DynamicMigration"))
    },
    test("isEmpty on identity migration") {
      assertTrue(DynamicMigration.identity.isEmpty)
    },
    test("isEmpty on non-empty migration") {
      val migration = DynamicMigration(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
      assertTrue(!migration.isEmpty)
    }
  )
}
