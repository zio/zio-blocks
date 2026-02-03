package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for cross-branch field operations.
 *
 * Covers:
 *   - RootAccess expression for accessing values from the root document
 *   - At expression for extracting elements by index from sequences
 *   - Root propagation through all Resolved variants
 *   - MigrationAction with root context
 *   - Builder API for joinFields and splitField
 *   - End-to-end cross-branch migration scenarios
 *   - Serialization of new expression types
 */
object CrossBranchMigrationSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Helpers
  // ─────────────────────────────────────────────────────────────────────────

  def dynamicRecord(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(fields: _*)

  def dynamicString(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def dynamicInt(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  def dynamicSeq(elements: DynamicValue*): DynamicValue =
    DynamicValue.Sequence(elements: _*)

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("CrossBranchMigrationSpec")(
    suite("RootAccess expression")(
      test("access root field from nested context") {
        // Scenario: We're in a nested record context but need to access a root field
        val rootAccess = Resolved.RootAccess(DynamicOptic.root.field("rootField"))

        val root = dynamicRecord(
          "rootField"  -> dynamicString("root-value"),
          "nestedData" -> dynamicRecord("localField" -> dynamicString("local-value"))
        )

        // The input in a nested context would just be the nested data
        val input = dynamicRecord("localField" -> dynamicString("local-value"))

        // evalDynamicWithRoot lets us access root even when input is nested
        val result = rootAccess.evalDynamicWithRoot(input, root)

        assertTrue(result == Right(dynamicString("root-value")))
      },
      test("access deeply nested field from root") {
        val rootAccess = Resolved.RootAccess(DynamicOptic.root.field("level1").field("level2").field("deepValue"))

        val root = dynamicRecord(
          "level1" -> dynamicRecord(
            "level2" -> dynamicRecord(
              "deepValue" -> dynamicString("found-it")
            )
          )
        )

        val input = dynamicRecord() // Empty input - we're accessing from root

        val result = rootAccess.evalDynamicWithRoot(input, root)

        assertTrue(result == Right(dynamicString("found-it")))
      },
      test("fails when path not found") {
        val rootAccess = Resolved.RootAccess(DynamicOptic.root.field("nonexistent"))

        val root  = dynamicRecord("existingField" -> dynamicString("value"))
        val input = dynamicRecord()

        val result = rootAccess.evalDynamicWithRoot(input, root)

        assertTrue(result.isLeft)
      },
      test("fallback to input-as-root for evalDynamic") {
        val rootAccess = Resolved.RootAccess(DynamicOptic.root.field("myField"))

        // When using evalDynamic (without explicit root), input IS the root
        val input = dynamicRecord("myField" -> dynamicString("direct-value"))

        val result = rootAccess.evalDynamic(input)

        assertTrue(result == Right(dynamicString("direct-value")))
      }
    ),
    suite("At expression")(
      test("extract first element (index 0)") {
        val at    = Resolved.At(0, Resolved.Identity)
        val input = dynamicSeq(dynamicString("first"), dynamicString("second"), dynamicString("third"))

        val result = at.evalDynamic(input)

        assertTrue(result == Right(dynamicString("first")))
      },
      test("extract middle element") {
        val at    = Resolved.At(1, Resolved.Identity)
        val input = dynamicSeq(dynamicString("first"), dynamicString("second"), dynamicString("third"))

        val result = at.evalDynamic(input)

        assertTrue(result == Right(dynamicString("second")))
      },
      test("extract last element") {
        val at    = Resolved.At(2, Resolved.Identity)
        val input = dynamicSeq(dynamicString("first"), dynamicString("second"), dynamicString("third"))

        val result = at.evalDynamic(input)

        assertTrue(result == Right(dynamicString("third")))
      },
      test("fails on out of bounds index") {
        val at    = Resolved.At(10, Resolved.Identity)
        val input = dynamicSeq(dynamicString("only-one"))

        val result = at.evalDynamic(input)

        assertTrue(result.isLeft)
      },
      test("fails on non-sequence input") {
        val at    = Resolved.At(0, Resolved.Identity)
        val input = dynamicString("not-a-sequence")

        val result = at.evalDynamic(input)

        assertTrue(result.isLeft)
      },
      test("works with inner expression") {
        // At(1, FieldAccess("name", Identity)) - get field "name" from the second element
        val at = Resolved.At(
          1,
          Resolved.FieldAccess("name", Resolved.Identity)
        )
        val input = dynamicSeq(
          dynamicRecord("name" -> dynamicString("first-name")),
          dynamicRecord("name" -> dynamicString("second-name")),
          dynamicRecord("name" -> dynamicString("third-name"))
        )

        val result = at.evalDynamic(input)

        assertTrue(result == Right(dynamicString("second-name")))
      },
      test("propagates root to inner expression") {
        // At(0, RootAccess(field("external"))) - get element at 0, then access root
        val at = Resolved.At(
          0,
          Resolved.RootAccess(DynamicOptic.root.field("external"))
        )

        val root  = dynamicRecord("external" -> dynamicString("from-root"))
        val input = dynamicSeq(dynamicString("ignored"))

        val result = at.evalDynamicWithRoot(input, root)

        assertTrue(result == Right(dynamicString("from-root")))
      }
    ),
    suite("Root propagation")(
      test("FieldAccess propagates root to inner") {
        // FieldAccess: inner gets a record from root, then extract field from it
        val expr = Resolved.FieldAccess(
          "data",
          Resolved.RootAccess(DynamicOptic.root.field("nested"))
        )

        val root = dynamicRecord(
          "external" -> dynamicString("root-value"),
          "nested"   -> dynamicRecord(
            "data" -> dynamicString("from-root-nested")
          )
        )

        val input = dynamicRecord("data" -> dynamicString("local-data"))

        val result = expr.evalDynamicWithRoot(input, root)

        // Inner RootAccess gets root.nested = {data: "from-root-nested"}
        // Then FieldAccess extracts "data" from that record
        assertTrue(result == Right(dynamicString("from-root-nested")))
      },
      test("Concat propagates root to all parts") {
        val expr = Resolved.Concat(
          Vector(
            Resolved.RootAccess(DynamicOptic.root.field("part1")),
            Resolved.Literal.string("-"),
            Resolved.RootAccess(DynamicOptic.root.field("part2"))
          ),
          ""
        )

        val root = dynamicRecord(
          "part1" -> dynamicString("hello"),
          "part2" -> dynamicString("world")
        )

        val input = dynamicRecord() // Empty - we access root

        val result = expr.evalDynamicWithRoot(input, root)

        assertTrue(result == Right(dynamicString("hello-world")))
      },
      test("SplitString propagates root to inner") {
        val expr = Resolved.SplitString(
          ",",
          Resolved.RootAccess(DynamicOptic.root.field("csv"))
        )

        val root  = dynamicRecord("csv" -> dynamicString("a,b,c"))
        val input = dynamicRecord()

        val result = expr.evalDynamicWithRoot(input, root)

        assertTrue(
          result == Right(
            dynamicSeq(dynamicString("a"), dynamicString("b"), dynamicString("c"))
          )
        )
      },
      test("Compose propagates root to both expressions") {
        // Compose(outer, inner) - apply inner first, then outer
        // Both should have access to root
        val expr = Resolved.Compose(
          Resolved.RootAccess(DynamicOptic.root.field("finalValue")), // outer
          Resolved.FieldAccess("ignored", Resolved.Identity)          // inner
        )

        val root = dynamicRecord(
          "finalValue" -> dynamicString("from-root")
        )

        val input = dynamicRecord("ignored" -> dynamicString("local"))

        // Compose: inner evaluates first (gets "local"), then outer is evaluated
        // But outer is RootAccess, which ignores the result of inner and gets "finalValue" from root
        val result = expr.evalDynamicWithRoot(input, root)

        assertTrue(result == Right(dynamicString("from-root")))
      },
      test("Construct propagates root to all field expressions") {
        val expr = Resolved.Construct(
          Vector(
            "field1" -> Resolved.RootAccess(DynamicOptic.root.field("src1")),
            "field2" -> Resolved.RootAccess(DynamicOptic.root.field("src2"))
          )
        )

        val root = dynamicRecord(
          "src1" -> dynamicString("value1"),
          "src2" -> dynamicString("value2")
        )

        val input = dynamicRecord()

        val result = expr.evalDynamicWithRoot(input, root)

        assertTrue(
          result == Right(
            dynamicRecord(
              "field1" -> dynamicString("value1"),
              "field2" -> dynamicString("value2")
            )
          )
        )
      },
      test("ConstructSeq propagates root to all elements") {
        val expr = Resolved.ConstructSeq(
          Vector(
            Resolved.RootAccess(DynamicOptic.root.field("a")),
            Resolved.RootAccess(DynamicOptic.root.field("b"))
          )
        )

        val root = dynamicRecord(
          "a" -> dynamicString("first"),
          "b" -> dynamicString("second")
        )

        val input = dynamicRecord()

        val result = expr.evalDynamicWithRoot(input, root)

        assertTrue(
          result == Right(
            dynamicSeq(dynamicString("first"), dynamicString("second"))
          )
        )
      },
      test("Coalesce propagates root to all alternatives") {
        val expr = Resolved.Coalesce(
          Vector(
            Resolved.RootAccess(DynamicOptic.root.field("nonexistent")),
            Resolved.RootAccess(DynamicOptic.root.field("fallback"))
          )
        )

        val root = dynamicRecord(
          "fallback" -> dynamicString("found")
        )

        val input = dynamicRecord()

        val result = expr.evalDynamicWithRoot(input, root)

        assertTrue(result == Right(dynamicString("found")))
      },
      test("GetOrElse propagates root to primary and fallback") {
        val expr = Resolved.GetOrElse(
          Resolved.RootAccess(DynamicOptic.root.field("optional")),
          Resolved.RootAccess(DynamicOptic.root.field("default"))
        )

        // When optional is missing, should use default
        val root = dynamicRecord(
          "default" -> dynamicString("default-value")
        )

        val input = dynamicRecord()

        val result = expr.evalDynamicWithRoot(input, root)

        assertTrue(result == Right(dynamicString("default-value")))
      }
    ),
    suite("MigrationAction with root")(
      test("AddField with RootAccess expression") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          "combined",
          Resolved.Concat(
            Vector(
              Resolved.RootAccess(DynamicOptic.root.field("firstName")),
              Resolved.Literal.string(" "),
              Resolved.RootAccess(DynamicOptic.root.field("lastName"))
            ),
            ""
          )
        )

        val input = dynamicRecord(
          "firstName" -> dynamicString("John"),
          "lastName"  -> dynamicString("Doe")
        )

        val result = action.apply(input)

        assertTrue(
          result == Right(
            dynamicRecord(
              "firstName" -> dynamicString("John"),
              "lastName"  -> dynamicString("Doe"),
              "combined"  -> dynamicString("John Doe")
            )
          )
        )
      },
      test("TransformValue with RootAccess in expression") {
        // TransformValue passes the field VALUE to the transform, not the whole record.
        // Use Identity to get the current field value, and RootAccess to access other fields.
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "targetField",
          transform = Resolved.Concat(
            Vector(
              Resolved.Identity, // The current field value
              Resolved.Literal.string(" (from: "),
              Resolved.RootAccess(DynamicOptic.root.field("source")),
              Resolved.Literal.string(")")
            ),
            ""
          ),
          reverseTransform = Resolved.Identity // Simple reverse - just return the value as-is
        )

        val input = dynamicRecord(
          "source"      -> dynamicString("origin"),
          "targetField" -> dynamicString("value")
        )

        val result = action.apply(input)

        assertTrue(
          result == Right(
            dynamicRecord(
              "source"      -> dynamicString("origin"),
              "targetField" -> dynamicString("value (from: origin)")
            )
          )
        )
      }
    ),
    suite("End-to-end cross-branch scenarios")(
      test("join fields from different branches") {
        // Scenario: combine address.street with origin.country into location.combined
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root,
              "combined",
              Resolved.Concat(
                Vector(
                  Resolved.RootAccess(DynamicOptic.root.field("address").field("street")),
                  Resolved.Literal.string(", "),
                  Resolved.RootAccess(DynamicOptic.root.field("origin").field("country"))
                ),
                ""
              )
            )
          )
        )

        val input = dynamicRecord(
          "address" -> dynamicRecord("street" -> dynamicString("123 Main St")),
          "origin"  -> dynamicRecord("country" -> dynamicString("USA"))
        )

        val result = migration.apply(input)

        assertTrue(
          result == Right(
            dynamicRecord(
              "address"  -> dynamicRecord("street" -> dynamicString("123 Main St")),
              "origin"   -> dynamicRecord("country" -> dynamicString("USA")),
              "combined" -> dynamicString("123 Main St, USA")
            )
          )
        )
      },
      test("split field into different locations") {
        // Scenario: split "fullName" into firstName and lastName
        // Use Compose to: first evaluate SplitString to get sequence, then At extracts element
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root,
              "firstName",
              Resolved.Compose(
                Resolved.At(0, Resolved.Identity),
                Resolved.SplitString(" ", Resolved.RootAccess(DynamicOptic.root.field("fullName")))
              )
            ),
            MigrationAction.AddField(
              DynamicOptic.root,
              "lastName",
              Resolved.Compose(
                Resolved.At(1, Resolved.Identity),
                Resolved.SplitString(" ", Resolved.RootAccess(DynamicOptic.root.field("fullName")))
              )
            )
          )
        )

        val input = dynamicRecord("fullName" -> dynamicString("John Doe"))

        val result = migration.apply(input)

        assertTrue(
          result == Right(
            dynamicRecord(
              "fullName"  -> dynamicString("John Doe"),
              "firstName" -> dynamicString("John"),
              "lastName"  -> dynamicString("Doe")
            )
          )
        )
      },
      test("complex cross-branch with nested access") {
        // Access person.address.city and settings.preferences.defaultCity
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root,
              "cityInfo",
              Resolved.Concat(
                Vector(
                  Resolved.Literal.string("Current: "),
                  Resolved.RootAccess(DynamicOptic.root.field("person").field("address").field("city")),
                  Resolved.Literal.string(", Default: "),
                  Resolved.RootAccess(DynamicOptic.root.field("settings").field("preferences").field("defaultCity"))
                ),
                ""
              )
            )
          )
        )

        val input = dynamicRecord(
          "person" -> dynamicRecord(
            "address" -> dynamicRecord("city" -> dynamicString("New York"))
          ),
          "settings" -> dynamicRecord(
            "preferences" -> dynamicRecord("defaultCity" -> dynamicString("Boston"))
          )
        )

        val result = migration.apply(input)

        assertTrue(
          result.isRight && {
            val record = result.toOption.get
            record match {
              case DynamicValue.Record(fields) =>
                fields.exists { case (name, value) =>
                  name == "cityInfo" &&
                  value == dynamicString("Current: New York, Default: Boston")
                }
              case _ => false
            }
          }
        )
      }
    ),
    suite("Serialization")(
      test("RootAccess round-trip") {
        import MigrationSchemas._

        val original = Resolved.RootAccess(DynamicOptic.root.field("test").field("nested"))

        val schema   = Schema[Resolved]
        val dynamic  = schema.toDynamicValue(original)
        val restored = schema.fromDynamicValue(dynamic)

        assertTrue(restored == Right(original))
      },
      test("At round-trip") {
        import MigrationSchemas._

        val original = Resolved.At(5, Resolved.Identity)

        val schema   = Schema[Resolved]
        val dynamic  = schema.toDynamicValue(original)
        val restored = schema.fromDynamicValue(dynamic)

        assertTrue(restored == Right(original))
      },
      test("Nested At with RootAccess round-trip") {
        import MigrationSchemas._

        val original = Resolved.At(
          2,
          Resolved.SplitString(",", Resolved.RootAccess(DynamicOptic.root.field("csv")))
        )

        val schema   = Schema[Resolved]
        val dynamic  = schema.toDynamicValue(original)
        val restored = schema.fromDynamicValue(dynamic)

        assertTrue(restored == Right(original))
      },
      test("Migration with cross-branch actions round-trip") {
        import MigrationSchemas._

        val original = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root,
              "joined",
              Resolved.Concat(
                Vector(
                  Resolved.RootAccess(DynamicOptic.root.field("a")),
                  Resolved.RootAccess(DynamicOptic.root.field("b"))
                ),
                "-"
              )
            )
          )
        )

        val schema   = Schema[DynamicMigration]
        val dynamic  = schema.toDynamicValue(original)
        val restored = schema.fromDynamicValue(dynamic)

        assertTrue(restored == Right(original))
      }
    )
  )
}
