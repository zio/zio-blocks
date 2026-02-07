package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for the MigrationBuilder API and method chaining.
 *
 * Covers:
 *   - Builder construction
 *   - Method chaining
 *   - Type-safe field operations
 *   - Build completion
 *   - Builder state management
 */
object MigrationBuilderApiSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Data Types
  // ─────────────────────────────────────────────────────────────────────────

  case class PersonV1(name: String, age: Int)
  case class PersonV2(fullName: String, age: Int, email: String)

  case class AddressV1(street: String, city: String)
  case class AddressV2(street: String, city: String, zipCode: String)

  case class SimpleRecord(value: Int)
  case class SimpleRecordV2(value: Int, extra: String)

  // Schema instances for MigrationBuilder tests
  implicit val schemaPersonV1: Schema[PersonV1]             = Schema.derived
  implicit val schemaPersonV2: Schema[PersonV2]             = Schema.derived
  implicit val schemaAddressV1: Schema[AddressV1]           = Schema.derived
  implicit val schemaAddressV2: Schema[AddressV2]           = Schema.derived
  implicit val schemaSimpleRecord: Schema[SimpleRecord]     = Schema.derived
  implicit val schemaSimpleRecordV2: Schema[SimpleRecordV2] = Schema.derived

  // ─────────────────────────────────────────────────────────────────────────
  // Test Helpers
  // ─────────────────────────────────────────────────────────────────────────

  def dynamicRecord(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(fields: _*)

  def dynamicString(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def dynamicInt(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("MigrationBuilderApiSpec")(
    suite("DynamicMigration construction")(
      test("empty migration via identity") {
        val m = DynamicMigration.identity
        assertTrue(m.isIdentity)
      },
      test("single action migration") {
        val m = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "old", "new")
        )
        assertTrue(m.actions.length == 1)
      },
      test("multi-action migration via Vector") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(0))
          )
        )
        assertTrue(m.actions.length == 2)
      }
    ),
    suite("Migration composition")(
      test("compose two migrations with ++") {
        val m1       = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2       = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))
        val composed = m1 ++ m2
        assertTrue(composed.actions.length == 2)
      },
      test("compose identity with migration") {
        val m        = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val composed = DynamicMigration.identity ++ m
        assertTrue(composed.actions == m.actions)
      },
      test("compose migration with identity") {
        val m        = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val composed = m ++ DynamicMigration.identity
        assertTrue(composed.actions == m.actions)
      },
      test("associativity of composition") {
        val m1    = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2    = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))
        val m3    = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "e", "f"))
        val left  = (m1 ++ m2) ++ m3
        val right = m1 ++ (m2 ++ m3)
        assertTrue(left.actions == right.actions)
      }
    ),
    suite("Migration application order")(
      test("actions applied in order") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "first", "second"),
            MigrationAction.Rename(DynamicOptic.root, "second", "third")
          )
        )
        val input  = dynamicRecord("first" -> dynamicInt(42))
        val result = m.apply(input)
        assertTrue(result == Right(dynamicRecord("third" -> dynamicInt(42))))
      },
      test("later actions see results of earlier actions") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "added", Resolved.Literal.int(1)),
            MigrationAction.Rename(DynamicOptic.root, "added", "renamed")
          )
        )
        val input  = dynamicRecord("existing" -> dynamicString("value"))
        val result = m.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "existing" -> dynamicString("value"),
              "renamed"  -> dynamicInt(1)
            )
          )
        )
      }
    ),
    suite("Typed Migration construction")(
      test("create Migration with schemas") {
        implicit val schemaV1: Schema[SimpleRecord]   = Schema.derived
        implicit val schemaV2: Schema[SimpleRecordV2] = Schema.derived

        val dynMigration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "extra", Resolved.Literal.string("default"))
        )
        val migration = Migration[SimpleRecord, SimpleRecordV2](dynMigration, schemaV1, schemaV2)
        assertTrue(migration.sourceSchema == schemaV1)
        assertTrue(migration.targetSchema == schemaV2)
      },
      test("Migration applies to typed values") {
        implicit val schemaV1: Schema[SimpleRecord]   = Schema.derived
        implicit val schemaV2: Schema[SimpleRecordV2] = Schema.derived

        val dynMigration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "extra", Resolved.Literal.string("default"))
        )
        val migration = Migration[SimpleRecord, SimpleRecordV2](dynMigration, schemaV1, schemaV2)
        val result    = migration.apply(SimpleRecord(42))
        assertTrue(result == Right(SimpleRecordV2(42, "default")))
      }
    ),
    suite("Migration reverse")(
      test("reverse returns reversed migration") {
        val m        = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val reversed = m.reverse
        reversed.actions.head match {
          case MigrationAction.Rename(_, from, to) =>
            assertTrue(from == "b" && to == "a")
          case _ => assertTrue(false)
        }
      },
      test("reverse of composed migration") {
        val m1       = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2       = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))
        val composed = m1 ++ m2
        val reversed = composed.reverse
        // Should be d->c, then b->a
        assertTrue(reversed.actions.length == 2)
        reversed.actions(0) match {
          case MigrationAction.Rename(_, "d", "c") => assertTrue(true)
          case _                                   => assertTrue(false)
        }
        reversed.actions(1) match {
          case MigrationAction.Rename(_, "b", "a") => assertTrue(true)
          case _                                   => assertTrue(false)
        }
      }
    ),
    suite("Action path composition")(
      test("root path") {
        val path   = DynamicOptic.root
        val action = MigrationAction.AddField(path, "field", Resolved.Literal.int(1))
        val input  = dynamicRecord()
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("field" -> dynamicInt(1))))
      },
      test("single field path") {
        val path   = DynamicOptic.root.field("nested")
        val action = MigrationAction.AddField(path, "field", Resolved.Literal.int(1))
        val input  = dynamicRecord("nested" -> dynamicRecord())
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "nested" -> dynamicRecord("field" -> dynamicInt(1))
            )
          )
        )
      },
      test("multi-level path") {
        val path   = DynamicOptic.root.field("a").field("b")
        val action = MigrationAction.AddField(path, "c", Resolved.Literal.int(1))
        val input  = dynamicRecord(
          "a" -> dynamicRecord(
            "b" -> dynamicRecord()
          )
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "a" -> dynamicRecord(
                "b" -> dynamicRecord("c" -> dynamicInt(1))
              )
            )
          )
        )
      }
    ),
    suite("Builder pattern scenarios")(
      test("building migration step by step") {
        // Simulating what a builder would do
        var migration = DynamicMigration.identity
        migration = migration ++ DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
        migration = migration ++ DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal.string(""))
        )
        assertTrue(migration.actions.length == 2)
      },
      test("builder stored in val maintains correctness") {
        val step1 = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
        val step2 = step1 ++ DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "age", Resolved.Literal.int(0))
        )
        val step3 = step2 ++ DynamicMigration.single(
          MigrationAction.DropField(DynamicOptic.root, "temp", Resolved.Literal.int(0))
        )
        // Each step is independent, immutable
        assertTrue(step1.actions.length == 1)
        assertTrue(step2.actions.length == 2)
        assertTrue(step3.actions.length == 3)
      },
      test("fluent chaining") {
        val migration = DynamicMigration.identity ++
          DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b")) ++
          DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d")) ++
          DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "e", "f"))
        assertTrue(migration.actions.length == 3)
      }
    ),
    suite("Complex migration scenarios")(
      test("full schema evolution") {
        // PersonV1 -> PersonV2: rename name->fullName, add email
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "name", "fullName"),
            MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal.string("unknown@example.com"))
          )
        )
        val input = dynamicRecord(
          "name" -> dynamicString("Alice"),
          "age"  -> dynamicInt(30)
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "fullName" -> dynamicString("Alice"),
              "age"      -> dynamicInt(30),
              "email"    -> dynamicString("unknown@example.com")
            )
          )
        )
      },
      test("nested schema evolution") {
        // Update address within a person record
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("address"),
            "zipCode",
            Resolved.Literal.string("00000")
          )
        )
        val input = dynamicRecord(
          "name"    -> dynamicString("Bob"),
          "address" -> dynamicRecord(
            "street" -> dynamicString("123 Main"),
            "city"   -> dynamicString("Boston")
          )
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "name"    -> dynamicString("Bob"),
              "address" -> dynamicRecord(
                "street"  -> dynamicString("123 Main"),
                "city"    -> dynamicString("Boston"),
                "zipCode" -> dynamicString("00000")
              )
            )
          )
        )
      }
    ),
    suite("Immutability guarantees")(
      test("original migration unchanged after composition") {
        val original = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "a", "b")
        )
        val originalActions = original.actions
        val _               = original ++ DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "c", "d")
        )
        // Original should be unchanged
        assertTrue(original.actions == originalActions)
        assertTrue(original.actions.length == 1)
      },
      test("reverse does not modify original") {
        val original = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "a", "b")
        )
        val originalActions = original.actions
        val _               = original.reverse
        assertTrue(original.actions == originalActions)
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // MigrationBuilder.withFieldTracking - buildPartial tests
    // ─────────────────────────────────────────────────────────────────────────
    suite("MigrationBuilder.withFieldTracking - buildPartial")(
      test("buildPartial works even with incomplete field coverage") {
        // buildPartial compiles even if not all fields are handled
        // (unlike build which requires all fields to be accounted for)
        val migration = MigrationBuilder
          .withFieldTracking[PersonV1, PersonV2]
          .addField(select[PersonV2](_.email), "test@test.com")
          .buildPartial // Only added email, but name->fullName not handled

        assertTrue(migration.dynamicMigration.actions.nonEmpty)
      },
      test("buildPartial with partial field coverage") {
        val migration = MigrationBuilder
          .withFieldTracking[PersonV1, PersonV2]
          .renameField(select[PersonV1](_.name), select[PersonV2](_.fullName))
          .addField(select[PersonV2](_.email), "unknown@example.com")
          // Note: age field not explicitly handled
          .buildPartial

        assertTrue(migration.dynamicMigration.actions.size == 2)
      },
      test("buildPartial builder stored in val maintains state") {
        val builder1  = MigrationBuilder.withFieldTracking[PersonV1, PersonV2]
        val builder2  = builder1.addField(select[PersonV2](_.email), "test@test.com")
        val builder3  = builder2.renameField(select[PersonV1](_.name), select[PersonV2](_.fullName))
        val migration = builder3.buildPartial

        assertTrue(migration.dynamicMigration.actions.size == 2)
      },
      test("buildPartial applies migration correctly") {
        val migration = MigrationBuilder
          .withFieldTracking[SimpleRecord, SimpleRecordV2]
          .keepField(select[SimpleRecord](_.value))
          .addField(select[SimpleRecordV2](_.extra), "added")
          .buildPartial

        val input  = SimpleRecord(42)
        val result = migration.migrate(input)

        assertTrue(result == Right(SimpleRecordV2(42, "added")))
      },
      test("build vs buildPartial - build requires complete coverage") {
        // This compiles because ALL fields are handled
        val complete = MigrationBuilder
          .withFieldTracking[SimpleRecord, SimpleRecordV2]
          .keepField(select[SimpleRecord](_.value))
          .addField(select[SimpleRecordV2](_.extra), "added")
          .build // Requires complete coverage

        // This also compiles even with partial coverage
        val partial = MigrationBuilder
          .withFieldTracking[SimpleRecord, SimpleRecordV2]
          .addField(select[SimpleRecordV2](_.extra), "added")
          .buildPartial // Allows partial coverage

        assertTrue(complete.dynamicMigration.actions.size == 1) // Only AddField (keepField is no-op)
        assertTrue(partial.dynamicMigration.actions.size == 1)
      }
    )
    // ─────────────────────────────────────────────────────────────────────────
    // Compile-Time Safety Documentation
    // ─────────────────────────────────────────────────────────────────────────
    //
    // The following examples demonstrate what should NOT compile when using
    // MigrationBuilder.withFieldTracking. These serve as documentation for
    // the compile-time field tracking system.
    //
    // Example 1: Missing target field
    // ─────────────────────────────────────────────────────────────────────────
    // This should NOT compile because 'email' field in PersonV2 is not handled:
    //
    // val willNotCompile = MigrationBuilder.withFieldTracking[PersonV1, PersonV2]
    //   .renameField(select[PersonV1](_.name), select[PersonV2](_.fullName))
    //   .keepField(select[PersonV1](_.age))
    //   // Missing: .addField(select[PersonV2](_.email), "default")
    //   .build  // ERROR: TgtRemaining != EmptyTuple
    //
    // Example 2: Missing source field
    // ─────────────────────────────────────────────────────────────────────────
    // This should NOT compile because 'age' field from PersonV1 is not consumed:
    //
    // val willNotCompile = MigrationBuilder.withFieldTracking[PersonV1, PersonV2]
    //   .renameField(select[PersonV1](_.name), select[PersonV2](_.fullName))
    //   // Missing: .keepField(select[PersonV1](_.age)) or .dropField(...)
    //   .addField(select[PersonV2](_.email), "default")
    //   .build  // ERROR: SrcRemaining != EmptyTuple
    //
    // Example 3: Double-handling a field
    // ─────────────────────────────────────────────────────────────────────────
    // This should NOT compile because 'name' is handled twice:
    //
    // val willNotCompile = MigrationBuilder.withFieldTracking[PersonV1, PersonV2]
    //   .renameField(select[PersonV1](_.name), select[PersonV2](_.fullName))
    //   .dropField(select[PersonV1](_.name))  // ERROR: 'name' already removed from SrcRemaining
    //   .keepField(select[PersonV1](_.age))
    //   .addField(select[PersonV2](_.email), "default")
    //   .build
    //
    // Example 4: Nested migrations require complete coverage too
    // ─────────────────────────────────────────────────────────────────────────
    // When using inField, the nested migration must also have complete coverage:
    //
    // val willNotCompile = MigrationBuilder.withFieldTracking[PersonWithAddress, PersonWithAddressV2]
    //   .keepField(select(_.name))
    //   .inField(select(_.address), select(_.address))(
    //     MigrationBuilder.withFieldTracking[AddressV1, AddressV2]
    //       .keepField(select(_.street))
    //       // Missing: .keepField(select(_.city))
    //       // Missing: .addField(select(_.zipCode), "00000")
    //       .build  // ERROR: Nested migration incomplete
    //   )
    //   .build
    //
    // In contrast, buildPartial (available on withFieldTracking builders) allows
    // partial migrations and will compile regardless of field coverage.
    //
    // Note: MigrationBuilder.create exists but returns EmptyTuple for both
    // field tracking parameters, making it unsuitable for field operations.
    // Use withFieldTracking with buildPartial for partial migrations instead.
    // ─────────────────────────────────────────────────────────────────────────
  )
}
