package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object StructuredMigrationSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Structured Migrations")(
    nestedMigrationSuite,
    fourLevelNestedSuite,
    nestedStepEdgeCasesSuite,
    mergeNestedMapsSuite,
    reverseWithRenameNestedKeysSuite,
    variantMigrationSuite,
    transformCaseSuite,
    variantTypeMismatchSuite,
    variantReverseNestedKeysSuite,
    variantBuilderSuite
  )

  private val nestedMigrationSuite = suite("Nested migrations")(
    test("Apply migration to nested record field") {
      val original = DynamicValue.Record(
        "name"    -> DynamicValue.string("Alice"),
        "address" -> DynamicValue.Record(
          "street" -> DynamicValue.string("123 Main St"),
          "city"   -> DynamicValue.string("NYC")
        )
      )
      val migration = DynamicMigration.record(
        _.nested("address")(
          _.addField("zip", DynamicValue.string("10001"))
        )
      )
      val result = migration(original)

      assertTrue(
        result == Right(
          DynamicValue.Record(
            "name"    -> DynamicValue.string("Alice"),
            "address" -> DynamicValue.Record(
              "street" -> DynamicValue.string("123 Main St"),
              "city"   -> DynamicValue.string("NYC"),
              "zip"    -> DynamicValue.string("10001")
            )
          )
        )
      )
    },
    test("Deeply nested migrations") {
      val original = DynamicValue.Record(
        "person" -> DynamicValue.Record(
          "name"    -> DynamicValue.string("Alice"),
          "contact" -> DynamicValue.Record(
            "email" -> DynamicValue.string("alice@example.com")
          )
        )
      )
      val migration = DynamicMigration.record(
        _.nested("person")(
          _.nested("contact")(
            _.addField("phone", DynamicValue.string("555-1234"))
          )
        )
      )
      val result = migration(original)

      assertTrue(
        result == Right(
          DynamicValue.Record(
            "person" -> DynamicValue.Record(
              "name"    -> DynamicValue.string("Alice"),
              "contact" -> DynamicValue.Record(
                "email" -> DynamicValue.string("alice@example.com"),
                "phone" -> DynamicValue.string("555-1234")
              )
            )
          )
        )
      )
    }
  )

  private val fourLevelNestedSuite = suite("Four-level nested migrations")(
    test("Apply migration at depth 4") {
      val original = DynamicValue.Record(
        "level1" -> DynamicValue.Record(
          "level2" -> DynamicValue.Record(
            "level3" -> DynamicValue.Record(
              "level4" -> DynamicValue.Record(
                "value" -> DynamicValue.string("original")
              )
            )
          )
        )
      )

      val migration = DynamicMigration.record(
        _.nested("level1")(
          _.nested("level2")(
            _.nested("level3")(
              _.nested("level4")(
                _.addField("added", DynamicValue.int(42))
                  .renameField("value", "renamed")
              )
            )
          )
        )
      )
      val result = migration(original)

      assertTrue(
        result == Right(
          DynamicValue.Record(
            "level1" -> DynamicValue.Record(
              "level2" -> DynamicValue.Record(
                "level3" -> DynamicValue.Record(
                  "level4" -> DynamicValue.Record(
                    "renamed" -> DynamicValue.string("original"),
                    "added"   -> DynamicValue.int(42)
                  )
                )
              )
            )
          )
        )
      )
    },
    test("Deeply nested migration is reversible") {
      val original = DynamicValue.Record(
        "a" -> DynamicValue.Record(
          "b" -> DynamicValue.Record(
            "c" -> DynamicValue.Record(
              "d" -> DynamicValue.string("deep")
            )
          )
        )
      )

      val migration = DynamicMigration.record(
        _.nested("a")(
          _.nested("b")(
            _.nested("c")(
              _.addField("e", DynamicValue.int(1))
            )
          )
        )
      )
      val migrated = migration(original)
      val reversed = migration.reverse(migrated.toOption.get)

      assertTrue(reversed == Right(original))
    }
  )

  private val nestedStepEdgeCasesSuite = suite("Nested step edge cases")(
    test("Empty nested step on missing field returns error") {
      val migration = DynamicMigration.record(
        _.nested("missing")(_.addField("x", DynamicValue.int(1)))
      )
      val original = DynamicValue.Record("other" -> DynamicValue.int(42))
      val result   = migration(original)

      result match {
        case Left(MigrationError.FieldNotFound(_, "missing")) => assertTrue(true)
        case _                                                => assertTrue(false)
      }
    }
  )

  private val mergeNestedMapsSuite = suite("MergeNestedMaps coverage")(
    test("andThen with nested fields only in first migration") {
      val m1 = DynamicMigration.record(
        _.nested("inner")(_.addField("a", DynamicValue.int(1)))
      )
      val m2 = DynamicMigration.record(
        _.addField("outer", DynamicValue.string("x"))
      )
      val composed = m1.andThen(m2)

      val original = DynamicValue.Record(
        "inner" -> DynamicValue.Record("existing" -> DynamicValue.int(0))
      )
      val result = composed(original)

      result match {
        case Right(DynamicValue.Record(fields)) =>
          val fieldMap    = fields.toVector.toMap
          val innerFields = fieldMap("inner").asInstanceOf[DynamicValue.Record].fields.toVector.toMap
          assertTrue(
            innerFields.contains("a"),
            fieldMap.contains("outer")
          )
        case _ => assertTrue(false)
      }
    },
    test("andThen with nested fields only in second migration") {
      val m1 = DynamicMigration.record(
        _.addField("outer", DynamicValue.string("x"))
      )
      val m2 = DynamicMigration.record(
        _.nested("inner")(_.addField("b", DynamicValue.int(2)))
      )
      val composed = m1.andThen(m2)

      val original = DynamicValue.Record(
        "inner" -> DynamicValue.Record("existing" -> DynamicValue.int(0))
      )
      val result = composed(original)

      result match {
        case Right(DynamicValue.Record(fields)) =>
          val fieldMap    = fields.toVector.toMap
          val innerFields = fieldMap("inner").asInstanceOf[DynamicValue.Record].fields.toVector.toMap
          assertTrue(
            innerFields.contains("b"),
            fieldMap.contains("outer")
          )
        case _ => assertTrue(false)
      }
    },
    test("Empty nested step is allowed when field doesn't exist") {
      val emptyNestedStep = MigrationStep.Record(Vector.empty, Map("missing" -> MigrationStep.NoOp))
      val migration       = DynamicMigration(emptyNestedStep)
      val original        = DynamicValue.Record("other" -> DynamicValue.int(42))
      val result          = migration(original)

      assertTrue(result.isRight)
    }
  )

  private val reverseWithRenameNestedKeysSuite = suite("Reverse with rename nested keys")(
    test("Record.reverse updates nested keys after field rename") {
      val original = DynamicValue.Record(
        "oldName" -> DynamicValue.Record("inner" -> DynamicValue.string("value"))
      )
      val migration = DynamicMigration.record(
        _.renameField("oldName", "newName")
          .nested("newName")(_.addField("extra", DynamicValue.int(42)))
      )
      val migrated = migration(original)
      val reversed = migration.reverse(migrated.toOption.get)

      assertTrue(reversed == Right(original))
    }
  )

  private val variantMigrationSuite = suite("Variant migrations")(
    test("Rename a case in a variant") {
      val original = DynamicValue.Variant(
        "OldName",
        DynamicValue.Record("value" -> DynamicValue.int(42))
      )
      val migration = DynamicMigration.variant(_.renameCase("OldName", "NewName"))
      val result    = migration(original)

      assertTrue(
        result == Right(
          DynamicValue.Variant(
            "NewName",
            DynamicValue.Record("value" -> DynamicValue.int(42))
          )
        )
      )
    },
    test("Apply nested migration to variant case") {
      val original = DynamicValue.Variant(
        "User",
        DynamicValue.Record("name" -> DynamicValue.string("Alice"))
      )
      val migration = DynamicMigration.variant(
        _.nested("User")(
          _.addField("role", DynamicValue.string("admin"))
        )
      )
      val result = migration(original)

      assertTrue(
        result == Right(
          DynamicValue.Variant(
            "User",
            DynamicValue.Record(
              "name" -> DynamicValue.string("Alice"),
              "role" -> DynamicValue.string("admin")
            )
          )
        )
      )
    }
  )

  private val transformCaseSuite = suite("transformCase")(
    test("transformCase applies nested migration to variant case") {
      val original = DynamicValue.Variant(
        "User",
        DynamicValue.Record("name" -> DynamicValue.string("Alice"))
      )
      val migration = DynamicMigration.variant(
        _.transformCase("User")(_.addField("role", DynamicValue.string("admin")))
      )
      val result = migration(original)

      assertTrue(
        result == Right(
          DynamicValue.Variant(
            "User",
            DynamicValue.Record(
              "name" -> DynamicValue.string("Alice"),
              "role" -> DynamicValue.string("admin")
            )
          )
        )
      )
    },
    test("transformCase with rename applies to renamed case") {
      val original = DynamicValue.Variant(
        "OldCase",
        DynamicValue.Record("data" -> DynamicValue.int(42))
      )
      val migration = DynamicMigration.variant(
        _.renameCase("OldCase", "NewCase")
          .transformCase("NewCase")(_.addField("extra", DynamicValue.boolean(true)))
      )
      val result = migration(original)

      assertTrue(
        result == Right(
          DynamicValue.Variant(
            "NewCase",
            DynamicValue.Record(
              "data"  -> DynamicValue.int(42),
              "extra" -> DynamicValue.boolean(true)
            )
          )
        )
      )
    },
    test("transformCase is reversible") {
      val original = DynamicValue.Variant(
        "Case1",
        DynamicValue.Record("value" -> DynamicValue.string("test"))
      )
      val migration = DynamicMigration.variant(
        _.transformCase("Case1")(_.addField("added", DynamicValue.int(0)))
      )
      val migrated = migration(original)
      val reversed = migration.reverse(migrated.toOption.get)

      assertTrue(reversed == Right(original))
    }
  )

  private val variantTypeMismatchSuite = suite("Variant type mismatch")(
    test("Variant migration fails when applied to non-Variant value") {
      val original  = DynamicValue.Record("field" -> DynamicValue.int(1))
      val migration = DynamicMigration(MigrationStep.Variant.empty.renameCase("A", "B"))
      val result    = migration(original)

      result match {
        case Left(MigrationError.TypeMismatch(_, expected, _)) => assertTrue(expected == "Variant")
        case _                                                 => assertTrue(false)
      }
    }
  )

  private val variantReverseNestedKeysSuite = suite("Variant.reverse updates nested keys")(
    test("Variant.reverse updates nested keys after case rename") {
      val original = DynamicValue.Variant(
        "OldCase",
        DynamicValue.Record("field" -> DynamicValue.string("data"))
      )
      val migration = DynamicMigration.variant(
        _.renameCase("OldCase", "NewCase")
          .nested("NewCase")(_.addField("extra", DynamicValue.int(100)))
      )
      val migrated = migration(original)
      val reversed = migration.reverse(migrated.toOption.get)

      assertTrue(reversed == Right(original))
    }
  )

  private val variantBuilderSuite = suite("Variant builder operations")(
    test("renameCase renames enum case") {
      val builder = MigrationBuilder[StatusV1, StatusV2]
        .renameCase("Active", "Enabled")
      val migration = builder.buildPartial
      val original  = StatusV1.Active
      val result    = migration(original)

      assertTrue(result == Right(StatusV2.Enabled))
    }
  )
}
