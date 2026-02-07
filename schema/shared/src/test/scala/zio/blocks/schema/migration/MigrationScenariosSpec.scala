package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationScenariosSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Migration Scenarios")(
    compositionSuite,
    incompatibleCompositionSuite,
    compositionEdgeCasesSuite,
    reversibilitySuite,
    migrationLawsSuite,
    schemaEvolutionSuite,
    edgeCasesSuite,
    errorPathsSuite,
    migrationClassMethodsSuite
  )

  private val compositionSuite = suite("Composition")(
    test("andThen composes two migrations") {
      val original   = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
      val migration1 = DynamicMigration.record(_.addField("age", DynamicValue.int(30)))
      val migration2 = DynamicMigration.record(_.addField("active", DynamicValue.boolean(true)))
      val composed   = migration1.andThen(migration2)
      val result     = composed(original)

      assertTrue(
        result == Right(
          DynamicValue.Record(
            "name"   -> DynamicValue.string("Alice"),
            "age"    -> DynamicValue.int(30),
            "active" -> DynamicValue.boolean(true)
          )
        )
      )
    },
    test("Identity migration is neutral for andThen") {
      val original     = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
      val migration    = DynamicMigration.record(_.addField("age", DynamicValue.int(30)))
      val withIdentity = DynamicMigration.identity.andThen(migration)
      val result       = withIdentity(original)

      assertTrue(
        result == Right(
          DynamicValue.Record(
            "name" -> DynamicValue.string("Alice"),
            "age"  -> DynamicValue.int(30)
          )
        )
      )
    },
    test("andThen is associative: (m1 andThen m2) andThen m3 == m1 andThen (m2 andThen m3)") {
      val original = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
      val m1       = DynamicMigration.record(_.addField("age", DynamicValue.int(30)))
      val m2       = DynamicMigration.record(_.addField("active", DynamicValue.boolean(true)))
      val m3       = DynamicMigration.record(_.addField("score", DynamicValue.int(100)))

      val leftAssoc  = (m1.andThen(m2)).andThen(m3)
      val rightAssoc = m1.andThen(m2.andThen(m3))

      val resultLeft  = leftAssoc(original)
      val resultRight = rightAssoc(original)

      assertTrue(resultLeft == resultRight)
    },
    test("Identity migration is right-neutral: m andThen identity == m") {
      val original          = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
      val migration         = DynamicMigration.record(_.addField("age", DynamicValue.int(30)))
      val withIdentityRight = migration.andThen(DynamicMigration.identity)

      val resultMigration    = migration(original)
      val resultWithIdentity = withIdentityRight(original)

      assertTrue(resultMigration == resultWithIdentity)
    }
  )

  private val incompatibleCompositionSuite = suite("Incompatible step composition")(
    test("andThen throws for incompatible step types Record+Variant") {
      val recordMigration  = DynamicMigration.record(_.addField("x", DynamicValue.int(1)))
      val variantMigration = DynamicMigration.variant(_.renameCase("A", "B"))

      val caught = try {
        recordMigration.andThen(variantMigration)
        None
      } catch {
        case e: IllegalArgumentException => Some(e.getMessage)
        case _: Throwable                => None
      }

      assertTrue(
        caught.isDefined,
        caught.exists(_.contains("Cannot compose incompatible"))
      )
    },
    test("andThen throws for incompatible step types Sequence+Record") {
      val seqMigration    = DynamicMigration.sequence(DynamicMigration.identity)
      val recordMigration = DynamicMigration.record(_.addField("x", DynamicValue.int(1)))

      val caught = try {
        seqMigration.andThen(recordMigration)
        None
      } catch {
        case e: IllegalArgumentException => Some(e.getMessage)
        case _: Throwable                => None
      }

      assertTrue(
        caught.isDefined,
        caught.exists(_.contains("Cannot compose incompatible"))
      )
    }
  )

  private val compositionEdgeCasesSuite = suite("Composition edge cases")(
    test("Composing add then remove of same field results in no net change") {
      val m1       = DynamicMigration.record(_.addField("temp", DynamicValue.int(42)))
      val m2       = DynamicMigration.record(_.removeField("temp", DynamicValue.int(0)))
      val composed = m1.andThen(m2)

      val original = DynamicValue.Record("existing" -> DynamicValue.string("value"))
      val result   = composed(original)

      assertTrue(result == Right(original))
    },
    test("Composing rename then add to old name works correctly") {
      val m1       = DynamicMigration.record(_.renameField("old", "new"))
      val m2       = DynamicMigration.record(_.addField("old", DynamicValue.string("reused")))
      val composed = m1.andThen(m2)

      val original = DynamicValue.Record("old" -> DynamicValue.int(1))
      val result   = composed(original)

      result match {
        case Right(DynamicValue.Record(fields)) =>
          val fieldMap = fields.toVector.toMap
          assertTrue(
            fieldMap.get("new") == Some(DynamicValue.int(1)),
            fieldMap.get("old") == Some(DynamicValue.string("reused"))
          )
        case _ => assertTrue(false)
      }
    },
    test("Nested migration composition merges correctly") {
      val m1 = DynamicMigration.record(
        _.nested("inner")(_.addField("a", DynamicValue.int(1)))
      )
      val m2 = DynamicMigration.record(
        _.nested("inner")(_.addField("b", DynamicValue.int(2)))
      )
      val composed = m1.andThen(m2)

      val original = DynamicValue.Record(
        "inner" -> DynamicValue.Record("existing" -> DynamicValue.string("x"))
      )
      val result = composed(original)

      assertTrue(
        result == Right(
          DynamicValue.Record(
            "inner" -> DynamicValue.Record(
              "existing" -> DynamicValue.string("x"),
              "a"        -> DynamicValue.int(1),
              "b"        -> DynamicValue.int(2)
            )
          )
        )
      )
    },
    test("Composing variant and nested case migrations") {
      val m1       = DynamicMigration.variant(_.renameCase("A", "B"))
      val m2       = DynamicMigration.variant(_.transformCase("B")(_.addField("x", DynamicValue.int(1))))
      val composed = m1.andThen(m2)

      val original = DynamicValue.Variant("A", DynamicValue.Record("data" -> DynamicValue.string("test")))
      val result   = composed(original)

      assertTrue(
        result == Right(
          DynamicValue.Variant(
            "B",
            DynamicValue.Record(
              "data" -> DynamicValue.string("test"),
              "x"    -> DynamicValue.int(1)
            )
          )
        )
      )
    }
  )

  private val reversibilitySuite = suite("Reversibility")(
    test("Adding and removing a field are inverses") {
      val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
      val migration = DynamicMigration.record(_.addField("age", DynamicValue.int(30)))
      val migrated  = migration(original)
      val reversed  = migration.reverse(migrated.toOption.get)

      assertTrue(reversed == Right(original))
    },
    test("Renaming a field is reversible") {
      val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
      val migration = DynamicMigration.record(_.renameField("name", "fullName"))
      val migrated  = migration(original)
      val reversed  = migration.reverse(migrated.toOption.get)

      assertTrue(reversed == Right(original))
    },
    test("Complex migration is reversible") {
      val original = DynamicValue.Record(
        "name" -> DynamicValue.string("Alice"),
        "age"  -> DynamicValue.int(30)
      )
      val migration = DynamicMigration.record(
        _.renameField("name", "fullName")
          .addField("active", DynamicValue.boolean(true))
      )
      val migrated = migration(original)
      val reversed = migration.reverse(migrated.toOption.get)

      assertTrue(reversed == Right(original))
    }
  )

  private val migrationLawsSuite = suite("Migration laws")(
    test("Identity law: identity migration returns original value unchanged") {
      val values = Vector(
        DynamicValue.Record("name" -> DynamicValue.string("Alice"), "age" -> DynamicValue.int(30)),
        DynamicValue.Variant("Active", DynamicValue.Record("since" -> DynamicValue.string("2024"))),
        DynamicValue.Sequence(DynamicValue.int(1), DynamicValue.int(2), DynamicValue.int(3)),
        DynamicValue.Map(DynamicValue.string("key") -> DynamicValue.int(42)),
        DynamicValue.int(42),
        DynamicValue.string("hello"),
        DynamicValue.Null
      )
      val identity = DynamicMigration.identity
      val results  = values.map(v => identity(v) == Right(v))
      assertTrue(results.forall(_ == true))
    },
    test("Structural reverse law: m.reverse.reverse equals m structurally") {
      val migrations = Vector(
        DynamicMigration.record(_.addField("newField", DynamicValue.int(0))),
        DynamicMigration.record(_.removeField("oldField", DynamicValue.string(""))),
        DynamicMigration.record(_.renameField("old", "new")),
        DynamicMigration.record(
          _.addField("a", DynamicValue.int(1))
            .renameField("b", "c")
            .removeField("d", DynamicValue.string("default"))
        ),
        DynamicMigration.variant(_.renameCase("OldCase", "NewCase")),
        DynamicMigration.record(
          _.nested("inner")(_.addField("deep", DynamicValue.boolean(true)))
        )
      )

      val results = migrations.map { m =>
        val doubleReversed = m.reverse.reverse
        doubleReversed.step == m.step
      }
      assertTrue(results.forall(_ == true))
    },
    test("Round-trip law: apply then reverse returns original value") {
      val original = DynamicValue.Record(
        "name"    -> DynamicValue.string("Alice"),
        "age"     -> DynamicValue.int(30),
        "address" -> DynamicValue.Record(
          "city"   -> DynamicValue.string("NYC"),
          "street" -> DynamicValue.string("123 Main")
        )
      )

      val migration = DynamicMigration.record(
        _.addField("active", DynamicValue.boolean(true))
          .renameField("age", "years")
          .nested("address")(_.addField("zip", DynamicValue.string("10001")))
      )

      val migrated = migration(original)
      val reversed = migrated.flatMap(migration.reverse.apply)

      assertTrue(reversed == Right(original))
    },
    test("Associativity law: (m1 ++ m2) ++ m3 produces same result as m1 ++ (m2 ++ m3)") {
      val original = DynamicValue.Record("x" -> DynamicValue.int(1))
      val m1       = DynamicMigration.record(_.addField("a", DynamicValue.int(10)))
      val m2       = DynamicMigration.record(_.addField("b", DynamicValue.int(20)))
      val m3       = DynamicMigration.record(_.addField("c", DynamicValue.int(30)))

      val leftAssoc  = (m1.andThen(m2)).andThen(m3)
      val rightAssoc = m1.andThen(m2.andThen(m3))

      val resultLeft  = leftAssoc(original)
      val resultRight = rightAssoc(original)

      assertTrue(
        resultLeft == resultRight,
        resultLeft.isRight
      )
    }
  )

  private val schemaEvolutionSuite = suite("Schema evolution scenarios")(
    test("Multi-version migration chain V1 -> V2 -> V3") {
      val v1ToV2 = DynamicMigration.record(_.addField("age", DynamicValue.int(0)))
      val v2ToV3 = DynamicMigration.record(_.addField("active", DynamicValue.boolean(true)))
      val v1ToV3 = v1ToV2.andThen(v2ToV3)

      val personV1 = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
      val result   = v1ToV3(personV1)

      assertTrue(
        result == Right(
          DynamicValue.Record(
            "name"   -> DynamicValue.string("Alice"),
            "age"    -> DynamicValue.int(0),
            "active" -> DynamicValue.boolean(true)
          )
        )
      )

      val reversed  = v1ToV3.reverse
      val roundTrip = result.flatMap(reversed.apply)
      assertTrue(roundTrip == Right(personV1))
    },
    test("Complex record evolution with rename, add, remove, and transform") {
      val migration = DynamicMigration.record(
        _.renameField("firstName", "name")
          .removeField("middleName", DynamicValue.string(""))
          .addField("fullName", DynamicValue.string(""))
          .transformField("age", DynamicValueTransform.numericAdd(1), DynamicValueTransform.numericAdd(-1))
      )

      val original = DynamicValue.Record(
        "firstName"  -> DynamicValue.string("John"),
        "middleName" -> DynamicValue.string("Q"),
        "age"        -> DynamicValue.int(29)
      )

      val result = migration(original)

      result match {
        case Right(DynamicValue.Record(fields)) =>
          val fieldMap = fields.toVector.toMap
          assertTrue(
            fieldMap.get("name") == Some(DynamicValue.string("John")),
            fieldMap.get("age") == Some(DynamicValue.int(30)),
            fieldMap.get("fullName") == Some(DynamicValue.string("")),
            !fieldMap.contains("firstName"),
            !fieldMap.contains("middleName")
          )
        case _ => assertTrue(false)
      }
    },
    test("Nested object evolution with deep field changes") {
      val migration = DynamicMigration.record(
        _.renameField("user", "account")
          .nested("account")(
            _.renameField("email", "primaryEmail")
              .addField("verified", DynamicValue.boolean(false))
              .nested("profile")(
                _.addField("avatar", DynamicValue.string("default.png"))
              )
          )
      )

      val original = DynamicValue.Record(
        "user" -> DynamicValue.Record(
          "email"   -> DynamicValue.string("user@example.com"),
          "profile" -> DynamicValue.Record(
            "displayName" -> DynamicValue.string("User")
          )
        )
      )

      val result   = migration(original)
      val reversed = result.flatMap(migration.reverse.apply)

      assertTrue(
        result.isRight,
        reversed == Right(original)
      )
    },
    test("Enum evolution with case rename and nested field migration") {
      val migration = DynamicMigration.variant(
        _.renameCase("Premium", "Pro")
          .transformCase("Pro")(
            _.addField("tier", DynamicValue.int(1))
              .renameField("expiresAt", "validUntil")
          )
          .transformCase("Free")(
            _.addField("adsEnabled", DynamicValue.boolean(true))
          )
      )

      val premiumUser = DynamicValue.Variant(
        "Premium",
        DynamicValue.Record("expiresAt" -> DynamicValue.string("2025-12-31"))
      )

      val freeUser = DynamicValue.Variant(
        "Free",
        DynamicValue.Record("signupDate" -> DynamicValue.string("2024-01-01"))
      )

      val premiumResult = migration(premiumUser)
      val freeResult    = migration(freeUser)

      premiumResult match {
        case Right(DynamicValue.Variant("Pro", DynamicValue.Record(fields))) =>
          val fieldMap = fields.toVector.toMap
          assertTrue(
            fieldMap.get("validUntil") == Some(DynamicValue.string("2025-12-31")),
            fieldMap.get("tier") == Some(DynamicValue.int(1)),
            !fieldMap.contains("expiresAt")
          )
        case _ => assertTrue(false)
      }

      freeResult match {
        case Right(DynamicValue.Variant("Free", DynamicValue.Record(fields))) =>
          val fieldMap = fields.toVector.toMap
          assertTrue(
            fieldMap.get("adsEnabled") == Some(DynamicValue.boolean(true)),
            fieldMap.get("signupDate") == Some(DynamicValue.string("2024-01-01"))
          )
        case _ => assertTrue(false)
      }
    }
  )

  private val edgeCasesSuite = suite("Edge cases")(
    test("Variant with unmatched case passes through unchanged") {
      val migration = DynamicMigration.variant(
        _.renameCase("OldCase", "NewCase")
          .transformCase("NewCase")(_.addField("x", DynamicValue.int(1)))
      )

      val unmatchedVariant = DynamicValue.Variant(
        "OtherCase",
        DynamicValue.Record("data" -> DynamicValue.string("untouched"))
      )

      val result = migration(unmatchedVariant)

      assertTrue(result == Right(unmatchedVariant))
    },
    test("Empty record with no fields migrates correctly") {
      val migration = DynamicMigration.record(_.addField("first", DynamicValue.int(1)))
      val empty     = DynamicValue.Record()
      val result    = migration(empty)

      assertTrue(result == Right(DynamicValue.Record("first" -> DynamicValue.int(1))))
    },
    test("Empty sequence migration returns empty sequence") {
      val migration = DynamicMigration.sequence(
        DynamicMigration.record(_.addField("x", DynamicValue.int(0)))
      )
      val empty  = DynamicValue.Sequence()
      val result = migration(empty)

      assertTrue(result == Right(DynamicValue.Sequence()))
    },
    test("Empty map migration returns empty map") {
      val migration = DynamicMigration(
        MigrationStep.MapEntries(
          MigrationStep.NoOp,
          MigrationStep.Record.empty.addField("version", DynamicValue.int(1))
        )
      )
      val empty  = DynamicValue.Map()
      val result = migration(empty)

      assertTrue(result == Right(DynamicValue.Map()))
    },
    test("Deeply nested empty migration step acts as identity") {
      val migration = DynamicMigration.record(
        _.nested("a")(
          _.nested("b")(
            _.nested("c")(identity)
          )
        )
      )

      val original = DynamicValue.Record(
        "a" -> DynamicValue.Record(
          "b" -> DynamicValue.Record(
            "c" -> DynamicValue.Record("value" -> DynamicValue.int(42))
          )
        )
      )

      val result = migration(original)
      assertTrue(result == Right(original))
    },
    test("Migration on nested field that doesn't exist in some records") {
      val migration = DynamicMigration.record(
        _.nested("optional")(_.addField("added", DynamicValue.int(1)))
      )

      val withField = DynamicValue.Record(
        "optional" -> DynamicValue.Record("existing" -> DynamicValue.string("yes"))
      )

      val withoutField = DynamicValue.Record(
        "other" -> DynamicValue.string("no optional field")
      )

      val resultWith    = migration(withField)
      val resultWithout = migration(withoutField)

      assertTrue(resultWith.isRight)
      assertTrue(resultWithout.isLeft)
      assertTrue(resultWithout.left.exists(_.isInstanceOf[MigrationError.FieldNotFound]))
    },
    test("Sequence with mixed element types handles migration correctly") {
      val original = DynamicValue.Sequence(
        DynamicValue.Record("type" -> DynamicValue.string("a"), "value" -> DynamicValue.int(1)),
        DynamicValue.Record("type" -> DynamicValue.string("b"), "value" -> DynamicValue.int(2))
      )

      val migration = DynamicMigration.sequence(
        DynamicMigration.record(_.addField("processed", DynamicValue.boolean(true)))
      )

      val result = migration(original)

      assertTrue(
        result == Right(
          DynamicValue.Sequence(
            DynamicValue.Record(
              "type"      -> DynamicValue.string("a"),
              "value"     -> DynamicValue.int(1),
              "processed" -> DynamicValue.boolean(true)
            ),
            DynamicValue.Record(
              "type"      -> DynamicValue.string("b"),
              "value"     -> DynamicValue.int(2),
              "processed" -> DynamicValue.boolean(true)
            )
          )
        )
      )
    }
  )

  private val errorPathsSuite = suite("Error paths include location")(
    test("Nested field not found error includes full path") {
      val migration = DynamicMigration.record(
        _.nested("level1")(
          _.nested("level2")(
            _.removeField("nonexistent", DynamicValue.Null)
          )
        )
      )

      val original = DynamicValue.Record(
        "level1" -> DynamicValue.Record(
          "level2" -> DynamicValue.Record(
            "existing" -> DynamicValue.string("value")
          )
        )
      )

      val result = migration(original)

      result match {
        case Left(MigrationError.FieldNotFound(source, fieldName)) =>
          assertTrue(
            fieldName == "nonexistent",
            source.toScalaString.contains("level1"),
            source.toScalaString.contains("level2")
          )
        case _ => assertTrue(false)
      }
    },
    test("Transform failure at nested level includes path in error") {
      val failingTransform = DynamicValueTransform.stringAppend(" suffix")

      val migration = DynamicMigration.record(
        _.nested("container")(
          _.transformField("value", failingTransform, DynamicValueTransform.identity)
        )
      )

      val original = DynamicValue.Record(
        "container" -> DynamicValue.Record(
          "value" -> DynamicValue.int(42)
        )
      )

      val result = migration(original)

      result match {
        case Left(MigrationError.TransformFailed(source, reason)) =>
          assertTrue(
            reason.contains("StringAppend"),
            source.toScalaString.contains("container"),
            source.toScalaString.contains("value")
          )
        case _ => assertTrue(false)
      }
    },
    test("Sequence element failure includes index in path") {
      val migration = DynamicMigration.sequence(
        DynamicMigration.record(_.removeField("required", DynamicValue.Null))
      )

      val original = DynamicValue.Sequence(
        DynamicValue.Record("required" -> DynamicValue.int(1)),
        DynamicValue.Record("required" -> DynamicValue.int(2)),
        DynamicValue.Record("missing"  -> DynamicValue.int(3))
      )

      val result = migration(original)

      result match {
        case Left(MigrationError.FieldNotFound(source, _)) =>
          assertTrue(source.toScalaString.contains(".at(2)"))
        case _ => assertTrue(false)
      }
    },
    test("Map value migration failure includes path context") {
      val migration = DynamicMigration(
        MigrationStep.MapEntries(
          MigrationStep.NoOp,
          MigrationStep.Record.empty
            .withFieldAction(FieldAction.Remove("required", DynamicValue.Null))
        )
      )

      val original = DynamicValue.Map(
        DynamicValue.string("key1") -> DynamicValue.Record("required" -> DynamicValue.int(1)),
        DynamicValue.string("key2") -> DynamicValue.Record("other" -> DynamicValue.int(2))
      )

      val result = migration(original)

      result match {
        case Left(MigrationError.FieldNotFound(source, _)) =>
          assertTrue(source.toScalaString.contains(".eachValue"))
        case _ => assertTrue(false)
      }
    },
    test("Type mismatch error includes expected and actual types") {
      val migration = DynamicMigration.record(_.addField("x", DynamicValue.int(1)))
      val original  = DynamicValue.Sequence(DynamicValue.int(1), DynamicValue.int(2))

      val result = migration(original)

      result match {
        case Left(MigrationError.TypeMismatch(_, expected, _)) =>
          assertTrue(expected == "Record")
        case _ => assertTrue(false)
      }
    }
  )

  private val migrationClassMethodsSuite = suite("Migration class methods")(
    test("Migration.applyDynamic applies migration to DynamicValue") {
      val migration = MigrationBuilder[PersonV1, PersonV2]
        .addField("age", DynamicValue.int(25))
        .buildPartial

      val dynamicInput = Schema[PersonV1].toDynamicValue(PersonV1("Alice"))
      val result       = migration.applyDynamic(dynamicInput)

      result match {
        case Right(dv) =>
          val fields = dv.asInstanceOf[DynamicValue.Record].fields.toVector.toMap
          assertTrue(
            fields.get("name") == Some(DynamicValue.string("Alice")),
            fields.get("age") == Some(DynamicValue.int(25))
          )
        case Left(_) => assertTrue(false)
      }
    },
    test("Migration.reverse returns reversed migration") {
      val migration = MigrationBuilder[PersonV1, PersonV2]
        .addField("age", DynamicValue.int(30))
        .buildPartial

      val reversed = migration.reverse

      assertTrue(
        reversed.sourceSchema == Schema[PersonV2],
        reversed.targetSchema == Schema[PersonV1]
      )

      val person2        = PersonV2("Bob", 30)
      val dynamicInput   = Schema[PersonV2].toDynamicValue(person2)
      val reversedDynMig = reversed.dynamicMigration
      val result         = reversedDynMig(dynamicInput)

      result match {
        case Right(dv) =>
          val fields = dv.asInstanceOf[DynamicValue.Record].fields.toVector.toMap
          assertTrue(
            fields.get("name") == Some(DynamicValue.string("Bob")),
            !fields.contains("age")
          )
        case Left(_) => assertTrue(false)
      }
    },
    test("Migration.andThen composes two migrations") {
      val m1 = MigrationBuilder[PersonV1, PersonV2]
        .addField("age", DynamicValue.int(25))
        .buildPartial

      val m2 = MigrationBuilder[PersonV2, PersonV3]
        .addField("active", DynamicValue.boolean(true))
        .buildPartial

      val composed = m1.andThen(m2)
      val result   = composed(PersonV1("Charlie"))

      assertTrue(result == Right(PersonV3("Charlie", 25, true)))
    },
    test("Migration.apply returns IncompatibleValue when target schema conversion fails") {
      val migration = MigrationBuilder[PersonV1, PersonV2]
        .addField("age", DynamicValue.string("not an int"))
        .buildPartial

      val result = migration(PersonV1("Test"))

      assertTrue(
        result.isLeft,
        result.swap.exists(_.isInstanceOf[MigrationError.IncompatibleValue])
      )
    }
  )
}
