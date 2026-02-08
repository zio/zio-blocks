package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

object MigrationIntegrationSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Data Types — Issue #519 Example
  // ─────────────────────────────────────────────────────────────────────────

  case class PersonV0(firstName: String, lastName: String)
  object PersonV0 {
    implicit val schema: Schema[PersonV0] = Schema.derived
  }

  case class PersonV1(firstName: String, surname: String, email: String)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Test Data Types — Multi-Step Evolution
  // ─────────────────────────────────────────────────────────────────────────

  case class AppConfigV0(host: String, port: Int)
  object AppConfigV0 {
    implicit val schema: Schema[AppConfigV0] = Schema.derived
  }

  case class AppConfigV1(host: String, port: Int, maxRetries: Int)
  object AppConfigV1 {
    implicit val schema: Schema[AppConfigV1] = Schema.derived
  }

  case class AppConfigV2(hostname: String, port: Int, maxRetries: Int, timeout: Long)
  object AppConfigV2 {
    implicit val schema: Schema[AppConfigV2] = Schema.derived
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Test Data Types — Additional Schemas
  // ─────────────────────────────────────────────────────────────────────────

  case class SimpleRecord(x: Int, y: String)
  object SimpleRecord {
    implicit val schema: Schema[SimpleRecord] = Schema.derived
  }

  case class Nested(inner: SimpleRecord, label: String)
  object Nested {
    implicit val schema: Schema[Nested] = Schema.derived
  }

  case class WithOption(name: String, tag: Option[String])
  object WithOption {
    implicit val schema: Schema[WithOption] = Schema.derived
  }

  case class PersonNameAge(name: String, age: Int)
  object PersonNameAge {
    implicit val schema: Schema[PersonNameAge] = Schema.derived
  }

  case class PersonNameAgeEmail(name: String, age: Int, email: String)
  object PersonNameAgeEmail {
    implicit val schema: Schema[PersonNameAgeEmail] = Schema.derived
  }

  case class PersonFullNameAgeEmail(fullName: String, age: Int, email: String)
  object PersonFullNameAgeEmail {
    implicit val schema: Schema[PersonFullNameAgeEmail] = Schema.derived
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Dynamic Value Helpers
  // ─────────────────────────────────────────────────────────────────────────

  private def stringDV(s: String): DynamicValue    = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def intDV(i: Int): DynamicValue          = DynamicValue.Primitive(PrimitiveValue.Int(i))
  private def longDV(l: Long): DynamicValue        = DynamicValue.Primitive(PrimitiveValue.Long(l))
  private def lit(dv: DynamicValue): MigrationExpr = MigrationExpr.Literal(dv)
  private def litInt(i: Int): MigrationExpr        = lit(intDV(i))
  private def litLong(l: Long): MigrationExpr      = lit(longDV(l))
  private def litStr(s: String): MigrationExpr     = lit(stringDV(s))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationIntegrationSpec")(
    issue519Suite,
    algebraicLawsSuite,
    multiStepEvolutionSuite,
    enumVariantMigrationSuite,
    collectionMigrationSuite,
    dynamicValueRoundTripSuite,
    builderIntegrationSuite,
    errorPathSuite,
    compositionReversalSuite
  )

  // ═══════════════════════════════════════════════════════════════════════════
  // 1. Issue #519 Example — The primary use case from the GitHub issue
  // ═══════════════════════════════════════════════════════════════════════════

  private val issue519Suite = suite("issue 519 example")(
    test("PersonV0 to PersonV1 migration with rename and addField") {
      // The core example: PersonV0(firstName, lastName) -> PersonV1(firstName, surname, email)
      // Rename lastName -> surname, add email with default
      val migration = Migration
        .newBuilder[PersonV0, PersonV1]
        .renameField("lastName", "surname")
        .addField("email", litStr("unknown@example.com"))
        .buildPartial

      val v0     = PersonV0("John", "Doe")
      val result = migration(v0)
      assertTrue(result == Right(PersonV1("John", "Doe", "unknown@example.com")))
    },
    test("PersonV0 to PersonV1 migration passes build validation") {
      val buildResult = Migration
        .newBuilder[PersonV0, PersonV1]
        .renameField("lastName", "surname")
        .addField("email", litStr("unknown@example.com"))
        .build

      assertTrue(buildResult.isRight) && {
        val migration = buildResult.toOption.get
        val result    = migration(PersonV0("Jane", "Smith"))
        assertTrue(result == Right(PersonV1("Jane", "Smith", "unknown@example.com")))
      }
    },
    test("PersonV1 back to PersonV0 via reverse migration") {
      val forward = Migration
        .newBuilder[PersonV0, PersonV1]
        .renameField("lastName", "surname")
        .addField("email", litStr("unknown@example.com"))
        .buildPartial

      val reversed = forward.reverse
      val v1       = PersonV1("John", "Doe", "john@doe.com")
      val result   = reversed(v1)
      // Reverse: drop email, rename surname -> lastName
      assertTrue(result == Right(PersonV0("John", "Doe")))
    },
    test("round-trip PersonV0 -> PersonV1 -> PersonV0 recovers original") {
      val forward = Migration
        .newBuilder[PersonV0, PersonV1]
        .renameField("lastName", "surname")
        .addField("email", litStr("unknown@example.com"))
        .buildPartial

      val v0        = PersonV0("Alice", "Wonderland")
      val roundTrip = forward(v0).flatMap(forward.reverse.apply)
      assertTrue(roundTrip == Right(v0))
    }
  )

  // ═══════════════════════════════════════════════════════════════════════════
  // 2. Algebraic Laws
  // ═══════════════════════════════════════════════════════════════════════════

  private val algebraicLawsSuite = suite("algebraic laws")(
    suite("identity law")(
      test("identity on simple case class returns Right(a)") {
        val m     = Migration.identity[SimpleRecord]
        val value = SimpleRecord(42, "hello")
        assertTrue(m(value) == Right(value))
      },
      test("identity on nested case class returns Right(a)") {
        val m     = Migration.identity[Nested]
        val value = Nested(SimpleRecord(1, "a"), "label")
        assertTrue(m(value) == Right(value))
      },
      test("identity on case class with Option returns Right(a)") {
        val m  = Migration.identity[WithOption]
        val v1 = WithOption("test", Some("tag"))
        val v2 = WithOption("test", None)
        assertTrue(m(v1) == Right(v1), m(v2) == Right(v2))
      },
      test("identity migration isEmpty") {
        val m = Migration.identity[SimpleRecord]
        assertTrue(m.isEmpty)
      },
      test("identity at DynamicMigration level returns value unchanged") {
        val dv     = DynamicValue.Record(Chunk(("x", intDV(1)), ("y", stringDV("a"))))
        val result = DynamicMigration.empty(dv)
        assertTrue(result == Right(dv))
      }
    ),
    suite("associativity law")(
      test("((m1 ++ m2) ++ m3) produces same result as (m1 ++ (m2 ++ m3))") {
        // m1: PersonNameAge -> PersonNameAgeEmail (add email)
        val m1 = Migration
          .newBuilder[PersonNameAge, PersonNameAgeEmail]
          .addField("email", litStr("default@example.com"))
          .buildPartial

        // m2: PersonNameAgeEmail -> PersonFullNameAgeEmail (rename name -> fullName)
        val m2 = Migration
          .newBuilder[PersonNameAgeEmail, PersonFullNameAgeEmail]
          .renameField("name", "fullName")
          .buildPartial

        // m3: PersonFullNameAgeEmail -> PersonFullNameAgeEmail (identity)
        val m3 = Migration.identity[PersonFullNameAgeEmail]

        val input = PersonNameAge("Alice", 30)

        val leftAssoc  = (m1 ++ m2) ++ m3
        val rightAssoc = m1 ++ (m2 ++ m3)

        assertTrue(leftAssoc(input) == rightAssoc(input))
      },
      test("associativity with three non-trivial migrations") {
        // Chain of add, rename, and transform operations at the DynamicMigration level
        val dm1 = DynamicMigration(
          Chunk(
            MigrationAction.AddField(DynamicOptic.root, "extra", litInt(10))
          )
        )
        val dm2 = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "extra", "bonus")
          )
        )
        val dm3 = DynamicMigration(
          Chunk(
            MigrationAction.TransformValue(DynamicOptic.root, "bonus", litInt(99), litInt(10))
          )
        )

        val input = DynamicValue.Record(Chunk(("name", stringDV("test"))))

        val leftAssoc  = (dm1 ++ dm2) ++ dm3
        val rightAssoc = dm1 ++ (dm2 ++ dm3)

        assertTrue(leftAssoc(input) == rightAssoc(input))
      }
    ),
    suite("double-reverse law")(
      test("m.reverse.reverse has identical DynamicMigration to m for rename") {
        val m = Migration
          .newBuilder[PersonV0, PersonV1]
          .renameField("lastName", "surname")
          .addField("email", litStr("default@example.com"))
          .buildPartial

        assertTrue(m.reverse.reverse.dynamicMigration == m.dynamicMigration)
      },
      test("double reverse at DynamicMigration level for multiple actions") {
        val dm = DynamicMigration(
          Chunk(
            MigrationAction.AddField(DynamicOptic.root, "x", litInt(1)),
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.RenameCase(DynamicOptic.root, "Old", "New")
          )
        )
        assertTrue(dm.reverse.reverse == dm)
      },
      test("double reverse for TransformValue action") {
        val dm = DynamicMigration(
          Chunk(
            MigrationAction.TransformValue(DynamicOptic.root, "x", litInt(99), litInt(42))
          )
        )
        assertTrue(dm.reverse.reverse == dm)
      }
    ),
    suite("semantic inverse")(
      test("rename is perfectly invertible via m.apply then m.reverse.apply") {
        val m = Migration
          .newBuilder[PersonNameAgeEmail, PersonFullNameAgeEmail]
          .renameField("name", "fullName")
          .buildPartial
        val input     = PersonNameAgeEmail("Alice", 30, "alice@example.com")
        val roundTrip = m(input).flatMap(m.reverse.apply)
        assertTrue(roundTrip == Right(input))
      },
      test("add+drop pair is invertible via reverse") {
        val m = Migration
          .newBuilder[PersonNameAge, PersonNameAgeEmail]
          .addField("email", litStr("default@example.com"))
          .buildPartial

        val input     = PersonNameAge("Bob", 25)
        val roundTrip = m(input).flatMap(m.reverse.apply)
        assertTrue(roundTrip == Right(input))
      }
    )
  )

  // ═══════════════════════════════════════════════════════════════════════════
  // 3. Multi-Step Evolution (V0 -> V1 -> V2)
  // ═══════════════════════════════════════════════════════════════════════════

  private val multiStepEvolutionSuite = suite("multi-step evolution")(
    test("V0 to V1: add maxRetries with default 3") {
      val m1 = Migration
        .newBuilder[AppConfigV0, AppConfigV1]
        .addField("maxRetries", litInt(3))
        .buildPartial

      val result = m1(AppConfigV0("localhost", 8080))
      assertTrue(result == Right(AppConfigV1("localhost", 8080, 3)))
    },
    test("V1 to V2: rename host to hostname, add timeout with default 5000") {
      val m2 = Migration
        .newBuilder[AppConfigV1, AppConfigV2]
        .renameField("host", "hostname")
        .addField("timeout", litLong(5000L))
        .buildPartial

      val result = m2(AppConfigV1("localhost", 8080, 3))
      assertTrue(result == Right(AppConfigV2("localhost", 8080, 3, 5000L)))
    },
    test("composed V0 to V2 produces correct result") {
      val m1 = Migration
        .newBuilder[AppConfigV0, AppConfigV1]
        .addField("maxRetries", litInt(3))
        .buildPartial

      val m2 = Migration
        .newBuilder[AppConfigV1, AppConfigV2]
        .renameField("host", "hostname")
        .addField("timeout", litLong(5000L))
        .buildPartial

      val composed = m1 ++ m2
      val result   = composed(AppConfigV0("prod.example.com", 443))
      assertTrue(result == Right(AppConfigV2("prod.example.com", 443, 3, 5000L)))
    },
    test("andThen produces same result as ++") {
      val m1 = Migration
        .newBuilder[AppConfigV0, AppConfigV1]
        .addField("maxRetries", litInt(3))
        .buildPartial

      val m2 = Migration
        .newBuilder[AppConfigV1, AppConfigV2]
        .renameField("host", "hostname")
        .addField("timeout", litLong(5000L))
        .buildPartial

      val input = AppConfigV0("test.local", 9090)
      assertTrue((m1 ++ m2)(input) == m1.andThen(m2)(input))
    },
    test("composed migration matches sequential application") {
      val m1 = Migration
        .newBuilder[AppConfigV0, AppConfigV1]
        .addField("maxRetries", litInt(3))
        .buildPartial

      val m2 = Migration
        .newBuilder[AppConfigV1, AppConfigV2]
        .renameField("host", "hostname")
        .addField("timeout", litLong(5000L))
        .buildPartial

      val input          = AppConfigV0("example.com", 80)
      val composedResult = (m1 ++ m2)(input)
      val seqResult      = m1(input).flatMap(m2.apply)
      assertTrue(composedResult == seqResult)
    },
    test("reverse of composed migration V2 back to V0") {
      val m1 = Migration
        .newBuilder[AppConfigV0, AppConfigV1]
        .addField("maxRetries", litInt(3))
        .buildPartial

      val m2 = Migration
        .newBuilder[AppConfigV1, AppConfigV2]
        .renameField("host", "hostname")
        .addField("timeout", litLong(5000L))
        .buildPartial

      val composed = m1 ++ m2
      val v0       = AppConfigV0("test.com", 3000)
      val forward  = composed(v0)
      assertTrue(forward.isRight) && {
        val backward = composed.reverse(forward.toOption.get)
        assertTrue(backward == Right(v0))
      }
    }
  )

  // ═══════════════════════════════════════════════════════════════════════════
  // 4. Enum/Variant Migration
  // ═══════════════════════════════════════════════════════════════════════════

  private val enumVariantMigrationSuite = suite("enum/variant migration")(
    test("rename variant case from CreditCard to CreditCardV1") {
      val variant = DynamicValue.Variant(
        "CreditCard",
        DynamicValue.Record(Chunk(("number", stringDV("4111111111111111"))))
      )
      val dm = DynamicMigration(
        Chunk(
          MigrationAction.RenameCase(DynamicOptic.root, "CreditCard", "CreditCardV1")
        )
      )
      val result = dm(variant)
      assertTrue(
        result == Right(
          DynamicValue.Variant(
            "CreditCardV1",
            DynamicValue.Record(Chunk(("number", stringDV("4111111111111111"))))
          )
        )
      )
    },
    test("transform case internal structure: rename field inside variant case") {
      // CreditCard(number) -> CreditCard(cardNumber)
      val variant = DynamicValue.Variant(
        "CreditCard",
        DynamicValue.Record(Chunk(("number", stringDV("4111111111111111"))))
      )
      val innerActions = Chunk[MigrationAction](
        MigrationAction.Rename(DynamicOptic.root, "number", "cardNumber")
      )
      val dm = DynamicMigration(
        Chunk(
          MigrationAction.TransformCase(DynamicOptic.root, "CreditCard", innerActions)
        )
      )
      val result = dm(variant)
      assertTrue(
        result == Right(
          DynamicValue.Variant(
            "CreditCard",
            DynamicValue.Record(Chunk(("cardNumber", stringDV("4111111111111111"))))
          )
        )
      )
    },
    test("migration touching only one case leaves other cases unchanged") {
      val bankTransfer = DynamicValue.Variant(
        "BankTransfer",
        DynamicValue.Record(Chunk(("routingNumber", stringDV("021000021"))))
      )
      val dm = DynamicMigration(
        Chunk(
          MigrationAction.RenameCase(DynamicOptic.root, "CreditCard", "CreditCardV1")
        )
      )
      val result = dm(bankTransfer)
      assertTrue(result == Right(bankTransfer))
    },
    test("transform case adds a field to one case") {
      val variant = DynamicValue.Variant(
        "CreditCard",
        DynamicValue.Record(Chunk(("number", stringDV("4111"))))
      )
      val innerActions = Chunk[MigrationAction](
        MigrationAction.AddField(DynamicOptic.root, "cvv", litStr("000"))
      )
      val dm = DynamicMigration(
        Chunk(
          MigrationAction.TransformCase(DynamicOptic.root, "CreditCard", innerActions)
        )
      )
      val result = dm(variant)
      assertTrue(
        result == Right(
          DynamicValue.Variant(
            "CreditCard",
            DynamicValue.Record(Chunk(("number", stringDV("4111")), ("cvv", stringDV("000"))))
          )
        )
      )
    },
    test("rename case via builder and apply to variant") {
      val dm = MigrationBuilder
        .create(SimpleRecord.schema, SimpleRecord.schema)
        .renameCase("Active", "Enabled")
        .buildPartial
        .dynamicMigration

      val variant = DynamicValue.Variant("Active", intDV(1))
      val result  = dm(variant)
      assertTrue(result == Right(DynamicValue.Variant("Enabled", intDV(1))))
    }
  )

  // ═══════════════════════════════════════════════════════════════════════════
  // 5. Collection Migration
  // ═══════════════════════════════════════════════════════════════════════════

  private val collectionMigrationSuite = suite("collection migration")(
    test("TransformElements replaces all elements in a sequence") {
      val seq = DynamicValue.Sequence(Chunk(intDV(1), intDV(2), intDV(3)))
      val dm  = DynamicMigration(
        Chunk(
          MigrationAction.TransformElements(DynamicOptic.root, litInt(0), litInt(0))
        )
      )
      val result = dm(seq)
      assertTrue(result == Right(DynamicValue.Sequence(Chunk(intDV(0), intDV(0), intDV(0)))))
    },
    test("TransformKeys transforms all keys in a map") {
      val m = DynamicValue.Map(
        Chunk(
          (stringDV("a"), intDV(1)),
          (stringDV("b"), intDV(2))
        )
      )
      val expr = MigrationExpr.Concat(MigrationExpr.FieldRef(DynamicOptic.root), litStr("_key"))
      val dm   = DynamicMigration(
        Chunk(
          MigrationAction.TransformKeys(DynamicOptic.root, expr, expr)
        )
      )
      val result = dm(m)
      assertTrue(
        result == Right(
          DynamicValue.Map(
            Chunk(
              (stringDV("a_key"), intDV(1)),
              (stringDV("b_key"), intDV(2))
            )
          )
        )
      )
    },
    test("TransformValues transforms all values in a map") {
      val m = DynamicValue.Map(
        Chunk(
          (stringDV("a"), intDV(1)),
          (stringDV("b"), intDV(2))
        )
      )
      val dm = DynamicMigration(
        Chunk(
          MigrationAction.TransformValues(DynamicOptic.root, litInt(99), litInt(0))
        )
      )
      val result = dm(m)
      assertTrue(
        result == Right(
          DynamicValue.Map(
            Chunk(
              (stringDV("a"), intDV(99)),
              (stringDV("b"), intDV(99))
            )
          )
        )
      )
    },
    test("TransformElements inside a record field") {
      // Record containing a sequence field — navigate into field, then transform elements
      val record = DynamicValue.Record(
        Chunk(
          ("items", DynamicValue.Sequence(Chunk(intDV(1), intDV(2), intDV(3))))
        )
      )
      val dm = DynamicMigration(
        Chunk(
          MigrationAction.TransformElements(DynamicOptic.root.field("items"), litInt(42), litInt(0))
        )
      )
      val result = dm(record)
      assertTrue(
        result == Right(
          DynamicValue.Record(
            Chunk(
              ("items", DynamicValue.Sequence(Chunk(intDV(42), intDV(42), intDV(42))))
            )
          )
        )
      )
    },
    test("TransformElements on empty sequence is a no-op") {
      val seq = DynamicValue.Sequence(Chunk.empty)
      val dm  = DynamicMigration(
        Chunk(
          MigrationAction.TransformElements(DynamicOptic.root, litInt(99), litInt(0))
        )
      )
      val result = dm(seq)
      assertTrue(result == Right(DynamicValue.Sequence(Chunk.empty)))
    }
  )

  // ═══════════════════════════════════════════════════════════════════════════
  // 6. Round-Trip Through DynamicValue
  // ═══════════════════════════════════════════════════════════════════════════

  private val dynamicValueRoundTripSuite = suite("round-trip through DynamicValue")(
    test("typed value -> toDynamicValue -> DynamicMigration -> fromDynamicValue -> typed value") {
      // Full pipeline: PersonNameAge -> add email -> PersonNameAgeEmail
      val source = PersonNameAge("Alice", 30)
      val dm     = DynamicMigration(
        Chunk(
          MigrationAction.AddField(DynamicOptic.root, "email", litStr("alice@example.com"))
        )
      )

      // Step 1: toDynamicValue
      val sourceDV = PersonNameAge.schema.toDynamicValue(source)
      // Step 2: apply DynamicMigration
      val migrated = dm(sourceDV)
      assertTrue(migrated.isRight) && {
        // Step 3: fromDynamicValue
        val typed = PersonNameAgeEmail.schema.fromDynamicValue(migrated.toOption.get)
        assertTrue(typed == Right(PersonNameAgeEmail("Alice", 30, "alice@example.com")))
      }
    },
    test("typed migration wraps the full DynamicValue pipeline") {
      val migration = Migration.fromDynamic(
        DynamicMigration(
          Chunk(
            MigrationAction.AddField(DynamicOptic.root, "email", litStr("default@test.com"))
          )
        ),
        PersonNameAge.schema,
        PersonNameAgeEmail.schema
      )
      val result = migration(PersonNameAge("Bob", 42))
      assertTrue(result == Right(PersonNameAgeEmail("Bob", 42, "default@test.com")))
    },
    test("identity round-trip preserves nested structures") {
      val value = Nested(SimpleRecord(100, "nested"), "outer")
      val m     = Migration.identity[Nested]
      assertTrue(m(value) == Right(value))
    },
    test("complex migration pipeline preserves data integrity") {
      // V0 -> V1 -> V2 and verify each intermediate step is correct
      val v0 = AppConfigV0("db.host.com", 5432)

      val m1 = Migration
        .newBuilder[AppConfigV0, AppConfigV1]
        .addField("maxRetries", litInt(5))
        .buildPartial

      val step1 = m1(v0)
      assertTrue(step1 == Right(AppConfigV1("db.host.com", 5432, 5))) && {
        val m2 = Migration
          .newBuilder[AppConfigV1, AppConfigV2]
          .renameField("host", "hostname")
          .addField("timeout", litLong(10000L))
          .buildPartial

        val step2 = m2(step1.toOption.get)
        assertTrue(step2 == Right(AppConfigV2("db.host.com", 5432, 5, 10000L)))
      }
    }
  )

  // ═══════════════════════════════════════════════════════════════════════════
  // 7. Builder Integration
  // ═══════════════════════════════════════════════════════════════════════════

  private val builderIntegrationSuite = suite("builder integration")(
    test("fluent builder chains multiple operations correctly") {
      // Combines addField + renameField in one builder
      val migration = Migration
        .newBuilder[PersonNameAge, PersonFullNameAgeEmail]
        .addField("email", litStr("nobody@example.com"))
        .renameField("name", "fullName")
        .buildPartial

      val result = migration(PersonNameAge("Charlie", 35))
      assertTrue(result == Right(PersonFullNameAgeEmail("Charlie", 35, "nobody@example.com")))
    },
    test("build validation catches duplicate addField") {
      val result = Migration
        .newBuilder[SimpleRecord, SimpleRecord]
        .addField("extra", litInt(1))
        .addField("extra", litInt(2))
        .build

      assertTrue(result.isLeft) && {
        val errors = result.swap.toOption.get
        assertTrue(errors.nonEmpty) && {
          val hasDupError = errors.exists {
            case MigrationError.FieldAlreadyExists(_, "extra") => true
            case _                                             => false
          }
          assertTrue(hasDupError)
        }
      }
    },
    test("build validation catches conflicting renames") {
      val result = Migration
        .newBuilder[SimpleRecord, SimpleRecord]
        .renameField("x", "a")
        .renameField("x", "b")
        .build

      assertTrue(result.isLeft) && {
        val errors = result.swap.toOption.get
        assertTrue(errors.nonEmpty) && {
          val hasRenameConflict = errors.exists {
            case MigrationError.CustomError(_, reason) => reason.contains("renamed more than once")
            case _                                     => false
          }
          assertTrue(hasRenameConflict)
        }
      }
    },
    test("buildPartial works even with duplicate addField") {
      val migration = Migration
        .newBuilder[SimpleRecord, SimpleRecord]
        .addField("extra", litInt(1))
        .addField("extra", litInt(2))
        .buildPartial

      // buildPartial does not validate, so it creates a migration
      // When applied, the second AddField will fail at runtime because the field already exists
      assertTrue(!migration.isEmpty)
    },
    test("build and buildPartial produce equivalent results for valid builder") {
      val builder = Migration
        .newBuilder[PersonNameAge, PersonNameAgeEmail]
        .addField("email", litStr("test@test.com"))

      val partial   = builder.buildPartial
      val validated = builder.build.toOption.get
      val input     = PersonNameAge("Test", 20)
      assertTrue(partial(input) == validated(input))
    },
    test("empty builder produces identity migration") {
      val m = Migration.newBuilder[SimpleRecord, SimpleRecord].buildPartial
      assertTrue(m.isEmpty) && {
        val result = m(SimpleRecord(1, "test"))
        assertTrue(result == Right(SimpleRecord(1, "test")))
      }
    },
    test("addAction with raw MigrationAction works") {
      val rawAction = MigrationAction.AddField(DynamicOptic.root, "email", litStr("raw@test.com"))
      val migration = Migration
        .newBuilder[PersonNameAge, PersonNameAgeEmail]
        .addAction(rawAction)
        .buildPartial

      val result = migration(PersonNameAge("Raw", 50))
      assertTrue(result == Right(PersonNameAgeEmail("Raw", 50, "raw@test.com")))
    }
  )

  // ═══════════════════════════════════════════════════════════════════════════
  // 8. Error Path Information
  // ═══════════════════════════════════════════════════════════════════════════

  private val errorPathSuite = suite("error path information")(
    test("renaming a non-existent field produces FieldNotFound error") {
      val dm = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root, "nonexistent", "other")
        )
      )
      val record = DynamicValue.Record(Chunk(("name", stringDV("Alice")), ("age", intDV(30))))
      val result = dm(record)
      assertTrue(result.isLeft) && {
        result.swap.toOption.get match {
          case MigrationError.FieldNotFound(path, fieldName) =>
            assertTrue(path == DynamicOptic.root, fieldName == "nonexistent")
          case _ => assertTrue(false)
        }
      }
    },
    test("adding a duplicate field produces FieldAlreadyExists error") {
      val dm = DynamicMigration(
        Chunk(
          MigrationAction.AddField(DynamicOptic.root, "name", litStr("duplicate"))
        )
      )
      val record = DynamicValue.Record(Chunk(("name", stringDV("Alice"))))
      val result = dm(record)
      assertTrue(result.isLeft) && {
        result.swap.toOption.get match {
          case MigrationError.FieldAlreadyExists(path, fieldName) =>
            assertTrue(path == DynamicOptic.root, fieldName == "name")
          case _ => assertTrue(false)
        }
      }
    },
    test("type mismatch when expecting Record but getting Primitive") {
      val dm = DynamicMigration(
        Chunk(
          MigrationAction.AddField(DynamicOptic.root, "x", litInt(1))
        )
      )
      val result = dm(intDV(42))
      assertTrue(result.isLeft) && {
        result.swap.toOption.get match {
          case MigrationError.TypeMismatch(path, expected, actual) =>
            assertTrue(path == DynamicOptic.root, expected == "Record", actual == "Primitive")
          case _ => assertTrue(false)
        }
      }
    },
    test("invalid coercion produces descriptive error") {
      val dm = DynamicMigration(
        Chunk(
          MigrationAction.ChangeType(
            DynamicOptic.root,
            "x",
            MigrationExpr.Coerce(MigrationExpr.FieldRef(DynamicOptic.root), "Int"),
            MigrationExpr.Coerce(MigrationExpr.FieldRef(DynamicOptic.root), "String")
          )
        )
      )
      val record = DynamicValue.Record(Chunk(("x", stringDV("not_a_number"))))
      val result = dm(record)
      assertTrue(result.isLeft) && {
        result.swap.toOption.get match {
          case MigrationError.InvalidCoercion(_, _, _) => assertTrue(true)
          case _                                       => assertTrue(false)
        }
      }
    },
    test("nested navigation failure includes path info") {
      val dm = DynamicMigration(
        Chunk(
          MigrationAction.AddField(DynamicOptic.root.field("missing"), "x", litInt(1))
        )
      )
      val record = DynamicValue.Record(Chunk(("name", stringDV("test"))))
      val result = dm(record)
      assertTrue(result.isLeft) && {
        result.swap.toOption.get match {
          case MigrationError.NavigationFailure(path, reason) =>
            assertTrue(
              path == DynamicOptic.root.field("missing"),
              reason.contains("not found")
            )
          case _ => assertTrue(false)
        }
      }
    },
    test("deeply nested path error reports correct path") {
      val level3 = DynamicValue.Record(Chunk(("deep", intDV(42))))
      val level2 = DynamicValue.Record(Chunk(("mid", level3)))
      val level1 = DynamicValue.Record(Chunk(("top", level2)))
      // Try to rename a non-existent field at a deep path
      val dm = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root.field("top").field("mid"), "missing", "renamed")
        )
      )
      val result = dm(level1)
      assertTrue(result.isLeft) && {
        result.swap.toOption.get match {
          case MigrationError.FieldNotFound(path, fieldName) =>
            assertTrue(
              path == DynamicOptic.root.field("top").field("mid"),
              fieldName == "missing"
            )
          case _ => assertTrue(false)
        }
      }
    },
    test("typed migration error wraps DynamicValue conversion failure") {
      // Add an int field where target schema expects a string
      val dm = DynamicMigration(
        Chunk(
          MigrationAction.AddField(DynamicOptic.root, "email", litInt(42))
        )
      )
      val m      = Migration.fromDynamic(dm, PersonNameAge.schema, PersonNameAgeEmail.schema)
      val result = m(PersonNameAge("Alice", 30))
      // This should fail because email should be String but we added Int
      assertTrue(result.isLeft) && {
        result.swap.toOption.get match {
          case MigrationError.CustomError(_, reason) =>
            assertTrue(reason.contains("Failed to convert"))
          case _ => assertTrue(false)
        }
      }
    },
    test("error message is human-readable") {
      val err     = MigrationError.FieldNotFound(DynamicOptic.root.field("person"), "address")
      val message = err.message
      assertTrue(
        message.contains("address"),
        message.contains("not found")
      )
    }
  )

  // ═══════════════════════════════════════════════════════════════════════════
  // 9. Composition of Reversed Migrations
  // ═══════════════════════════════════════════════════════════════════════════

  private val compositionReversalSuite = suite("composition of reversed migrations")(
    test("m ++ m.reverse approximates identity for rename") {
      // Forward renames name -> fullName, reverse renames fullName -> name, net effect: identity
      val m = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
      )
      val record   = DynamicValue.Record(Chunk(("name", stringDV("Alice")), ("age", intDV(30))))
      val composed = m ++ m.reverse
      val result   = composed(record)
      assertTrue(result == Right(record))
    },
    test("m ++ m.reverse approximates identity for add/drop") {
      // Forward adds "extra", reverse drops "extra", net effect: identity
      val m = DynamicMigration(
        Chunk(
          MigrationAction.AddField(DynamicOptic.root, "extra", litInt(42))
        )
      )
      val record   = DynamicValue.Record(Chunk(("name", stringDV("Alice"))))
      val composed = m ++ m.reverse
      val result   = composed(record)
      assertTrue(result == Right(record))
    },
    test("(m1 ++ m2).reverse semantically equals m2.reverse ++ m1.reverse for rename chain") {
      val m1 = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root, "a", "b")
        )
      )
      val m2 = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root, "b", "c")
        )
      )

      val record = DynamicValue.Record(Chunk(("a", intDV(1))))

      // Forward
      val forward = (m1 ++ m2)(record)
      assertTrue(forward.isRight) && {
        val forwardVal = forward.toOption.get

        // Reverse two ways
        val composedReverse   = (m1 ++ m2).reverse(forwardVal)
        val sequentialReverse = (m2.reverse ++ m1.reverse)(forwardVal)

        assertTrue(composedReverse == sequentialReverse)
      }
    },
    test("(m1 ++ m2).reverse.dynamicMigration == m2.reverse ++ m1.reverse for structural equality") {
      val m1 = DynamicMigration(
        Chunk(
          MigrationAction.AddField(DynamicOptic.root, "x", litInt(1))
        )
      )
      val m2 = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root, "a", "b")
        )
      )

      // The reverse of a concatenated migration should structurally equal
      // the reversed individual migrations concatenated in reverse order
      assertTrue((m1 ++ m2).reverse == (m2.reverse ++ m1.reverse))
    },
    test("typed reverse composition recovers original value") {
      val m1 = Migration
        .newBuilder[PersonNameAge, PersonNameAgeEmail]
        .addField("email", litStr("default@example.com"))
        .buildPartial

      val m2 = Migration
        .newBuilder[PersonNameAgeEmail, PersonFullNameAgeEmail]
        .renameField("name", "fullName")
        .buildPartial

      val composed = m1 ++ m2
      val input    = PersonNameAge("Recovery", 99)
      val forward  = composed(input)
      assertTrue(forward.isRight) && {
        val backward = composed.reverse(forward.toOption.get)
        assertTrue(backward == Right(input))
      }
    }
  )
}
