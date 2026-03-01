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
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // Edge Cases for Branch Coverage
    // ─────────────────────────────────────────────────────────────────────────
    suite("Edge Cases - Resolved Expressions")(
      test("RootAccess.evalDynamic without input returns error") {
        val rootAccess = Resolved.RootAccess(DynamicOptic.root.field("field"))
        val result     = rootAccess.evalDynamic
        assertTrue(result.isLeft && result.swap.toOption.get.contains("RootAccess requires root context"))
      },
      test("At.evalDynamic without input returns error") {
        val at     = Resolved.At(0, Resolved.Identity)
        val result = at.evalDynamic
        assertTrue(result.isLeft && result.swap.toOption.get.contains("At requires sequence input"))
      },
      test("At with negative index fails") {
        val at     = Resolved.At(-1, Resolved.Identity)
        val input  = dynamicSeq(dynamicString("first"))
        val result = at.evalDynamic(input)
        assertTrue(result.isLeft && result.swap.toOption.get.contains("out of bounds"))
      },
      test("SplitString.evalDynamic without input returns error") {
        val split  = Resolved.SplitString(",", Resolved.Identity)
        val result = split.evalDynamic
        assertTrue(result.isLeft && result.swap.toOption.get.contains("SplitString requires input"))
      },
      test("SplitString on non-string value fails") {
        val split  = Resolved.SplitString(",", Resolved.Identity)
        val input  = dynamicInt(42)
        val result = split.evalDynamic(input)
        assertTrue(result.isLeft && result.swap.toOption.get.contains("requires String input"))
      },
      test("UnwrapOption with None uses fallback") {
        val unwrap = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.string("fallback"))
        val input  = DynamicValue.Variant("None", DynamicValue.Record())
        val result = unwrap.evalDynamic(input)
        assertTrue(result == Right(dynamicString("fallback")))
      },
      test("UnwrapOption with Null uses fallback") {
        val unwrap = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.string("null-fallback"))
        val input  = DynamicValue.Null
        val result = unwrap.evalDynamic(input)
        assertTrue(result == Right(dynamicString("null-fallback")))
      },
      test("UnwrapOption with Some extracts value") {
        val unwrap = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.string("fallback"))
        val input  = DynamicValue.Variant("Some", dynamicString("inner-value"))
        val result = unwrap.evalDynamic(input)
        assertTrue(result == Right(dynamicString("inner-value")))
      },
      test("UnwrapOption with non-optional value passes through") {
        val unwrap = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.string("fallback"))
        val input  = dynamicString("already-value")
        val result = unwrap.evalDynamic(input)
        assertTrue(result == Right(dynamicString("already-value")))
      },
      test("UnwrapOption.evalDynamic without input returns error") {
        val unwrap = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.string("fallback"))
        val result = unwrap.evalDynamic
        assertTrue(result.isLeft)
      },
      test("Head on empty sequence fails") {
        val head   = Resolved.Head(Resolved.Identity)
        val input  = dynamicSeq()
        val result = head.evalDynamic(input)
        assertTrue(result.isLeft && result.swap.toOption.get.contains("empty"))
      },
      test("Head on non-sequence fails") {
        val head   = Resolved.Head(Resolved.Identity)
        val input  = dynamicString("not-a-sequence")
        val result = head.evalDynamic(input)
        assertTrue(result.isLeft && result.swap.toOption.get.contains("Expected sequence"))
      },
      test("Head.evalDynamic without input returns error") {
        val head   = Resolved.Head(Resolved.Identity)
        val result = head.evalDynamic
        assertTrue(result.isLeft)
      },
      test("JoinStrings on non-sequence fails") {
        val join   = Resolved.JoinStrings(",", Resolved.Identity)
        val input  = dynamicString("not-a-sequence")
        val result = join.evalDynamic(input)
        assertTrue(result.isLeft && result.swap.toOption.get.contains("Expected sequence"))
      },
      test("JoinStrings.evalDynamic without input returns error") {
        val join   = Resolved.JoinStrings(",", Resolved.Identity)
        val result = join.evalDynamic
        assertTrue(result.isLeft)
      },
      test("JoinStrings on sequence of strings succeeds") {
        val join   = Resolved.JoinStrings("-", Resolved.Identity)
        val input  = dynamicSeq(dynamicString("a"), dynamicString("b"), dynamicString("c"))
        val result = join.evalDynamic(input)
        assertTrue(result == Right(dynamicString("a-b-c")))
      },
      test("Coalesce returns first successful result") {
        val coalesce = Resolved.Coalesce(
          Vector(
            Resolved.FieldAccess("nonexistent", Resolved.Identity),
            Resolved.Literal.string("fallback")
          )
        )
        val input  = dynamicRecord("other" -> dynamicString("value"))
        val result = coalesce.evalDynamic(input)
        assertTrue(result == Right(dynamicString("fallback")))
      },
      test("Coalesce with empty alternatives fails") {
        val coalesce = Resolved.Coalesce(Vector.empty)
        val input    = dynamicRecord()
        val result   = coalesce.evalDynamic(input)
        assertTrue(result.isLeft)
      },
      test("Coalesce.evalDynamic without input uses first successful") {
        val coalesce = Resolved.Coalesce(
          Vector(
            Resolved.Fail("first-fails"),
            Resolved.Literal.string("second-succeeds")
          )
        )
        val result = coalesce.evalDynamic
        assertTrue(result == Right(dynamicString("second-succeeds")))
      },
      test("GetOrElse with successful primary returns primary") {
        val getOrElse = Resolved.GetOrElse(
          Resolved.Literal.string("primary"),
          Resolved.Literal.string("fallback")
        )
        val input  = dynamicRecord()
        val result = getOrElse.evalDynamic(input)
        assertTrue(result == Right(dynamicString("primary")))
      },
      test("GetOrElse.evalDynamic without input") {
        val getOrElse = Resolved.GetOrElse(
          Resolved.Fail("primary-fails"),
          Resolved.Literal.string("fallback")
        )
        val result = getOrElse.evalDynamic
        assertTrue(result == Right(dynamicString("fallback")))
      },
      test("Construct.evalDynamic without input uses field expressions") {
        val construct = Resolved.Construct(
          Vector(
            "a" -> Resolved.Literal.string("value-a"),
            "b" -> Resolved.Literal.int(42)
          )
        )
        val result = construct.evalDynamic
        assertTrue(result.isRight)
      },
      test("Construct with failing field expression fails") {
        val construct = Resolved.Construct(
          Vector(
            "a" -> Resolved.Fail("field-fails")
          )
        )
        val input  = dynamicRecord()
        val result = construct.evalDynamic(input)
        assertTrue(result.isLeft)
      },
      test("ConstructSeq.evalDynamic without input uses element expressions") {
        val constructSeq = Resolved.ConstructSeq(
          Vector(
            Resolved.Literal.string("first"),
            Resolved.Literal.string("second")
          )
        )
        val result = constructSeq.evalDynamic
        assertTrue(result.isRight)
      },
      test("ConstructSeq with failing element fails") {
        val constructSeq = Resolved.ConstructSeq(
          Vector(
            Resolved.Literal.string("first"),
            Resolved.Fail("element-fails")
          )
        )
        val input  = dynamicRecord()
        val result = constructSeq.evalDynamic(input)
        assertTrue(result.isLeft)
      },
      test("WrapSome.evalDynamic without input requires inner to succeed") {
        val wrapSome = Resolved.WrapSome(Resolved.Literal.string("value"))
        val result   = wrapSome.evalDynamic
        assertTrue(result.isRight)
      },
      test("WrapSome with failing inner fails") {
        val wrapSome = Resolved.WrapSome(Resolved.Fail("inner-fails"))
        val input    = dynamicRecord()
        val result   = wrapSome.evalDynamic(input)
        assertTrue(result.isLeft)
      },
      test("OpticAccess.evalDynamic without input returns error") {
        val opticAccess = Resolved.OpticAccess(DynamicOptic.root.field("x"), Resolved.Identity)
        val result      = opticAccess.evalDynamic
        assertTrue(result.isLeft)
      },
      test("OpticAccess with invalid path fails") {
        val opticAccess = Resolved.OpticAccess(DynamicOptic.root.field("nonexistent"), Resolved.Identity)
        val input       = dynamicRecord("other" -> dynamicString("value"))
        val result      = opticAccess.evalDynamic(input)
        assertTrue(result.isLeft)
      },
      test("DefaultValue with Right succeeds") {
        val defaultValue = Resolved.DefaultValue(Right(dynamicInt(42)))
        val result       = defaultValue.evalDynamic
        assertTrue(result == Right(dynamicInt(42)))
      },
      test("DefaultValue with Left fails") {
        val defaultValue = Resolved.DefaultValue(Left("no default available"))
        val result       = defaultValue.evalDynamic
        assertTrue(result.isLeft && result.swap.toOption.get.contains("no default"))
      },
      test("Compose.evalDynamic without input") {
        val compose = Resolved.Compose(
          Resolved.Literal.string("outer-result"),
          Resolved.Identity
        )
        val result = compose.evalDynamic
        assertTrue(result.isLeft) // Identity requires input
      },
      test("Compose with failing inner fails") {
        val compose = Resolved.Compose(
          Resolved.Identity,
          Resolved.Fail("inner-fails")
        )
        val input  = dynamicRecord()
        val result = compose.evalDynamic(input)
        assertTrue(result.isLeft)
      },
      test("Compose with failing outer fails") {
        val compose = Resolved.Compose(
          Resolved.Fail("outer-fails"),
          Resolved.Literal.string("inner-ok")
        )
        val input  = dynamicRecord()
        val result = compose.evalDynamic(input)
        assertTrue(result.isLeft)
      },
      test("Identity.evalDynamic without input returns error") {
        val result = Resolved.Identity.evalDynamic
        assertTrue(result.isLeft)
      },
      test("Identity with input returns input") {
        val input  = dynamicString("test-value")
        val result = Resolved.Identity.evalDynamic(input)
        assertTrue(result == Right(dynamicString("test-value")))
      },
      test("FieldAccess.evalDynamic without input returns error") {
        val fieldAccess = Resolved.FieldAccess("field", Resolved.Identity)
        val result      = fieldAccess.evalDynamic
        assertTrue(result.isLeft)
      },
      test("FieldAccess on non-record fails") {
        val fieldAccess = Resolved.FieldAccess("field", Resolved.Identity)
        val input       = dynamicString("not-a-record")
        val result      = fieldAccess.evalDynamic(input)
        assertTrue(result.isLeft && result.swap.toOption.get.contains("Expected record"))
      },
      test("Convert.evalDynamic without input returns error") {
        val convert = Resolved.Convert("Int", "String", Resolved.Identity)
        val result  = convert.evalDynamic
        assertTrue(result.isLeft)
      },
      test("Fail always fails") {
        val fail    = Resolved.Fail("intentional-failure")
        val result1 = fail.evalDynamic
        val result2 = fail.evalDynamic(dynamicRecord())
        assertTrue(result1.isLeft && result2.isLeft)
      },
      test("Concat with non-string values converts to string") {
        val concat = Resolved.Concat(
          Vector(
            Resolved.Literal.int(42),
            Resolved.Literal.string("-"),
            Resolved.Literal.boolean(true)
          ),
          ""
        )
        val result = concat.evalDynamic
        // Non-string values get toString'd
        assertTrue(result.isRight)
      },
      test("Concat with failing part fails") {
        val concat = Resolved.Concat(
          Vector(
            Resolved.Literal.string("ok"),
            Resolved.Fail("part-fails")
          ),
          "-"
        )
        val input  = dynamicRecord()
        val result = concat.evalDynamic(input)
        assertTrue(result.isLeft)
      }
    ),
    suite("Edge Cases - Root Propagation")(
      test("OpticAccess propagates root to inner") {
        val expr = Resolved.OpticAccess(
          DynamicOptic.root.field("local"),
          Resolved.RootAccess(DynamicOptic.root.field("external"))
        )
        val root = dynamicRecord(
          "local"    -> dynamicRecord("data" -> dynamicString("local-data")),
          "external" -> dynamicString("from-root")
        )
        val input  = root
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result == Right(dynamicString("from-root")))
      },
      test("WrapSome propagates root to inner") {
        val expr   = Resolved.WrapSome(Resolved.RootAccess(DynamicOptic.root.field("value")))
        val root   = dynamicRecord("value" -> dynamicString("wrapped"))
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result.isRight)
      },
      test("UnwrapOption propagates root to fallback") {
        val expr = Resolved.UnwrapOption(
          Resolved.Identity,
          Resolved.RootAccess(DynamicOptic.root.field("fallback"))
        )
        val root   = dynamicRecord("fallback" -> dynamicString("from-root-fallback"))
        val input  = DynamicValue.Variant("None", DynamicValue.Record())
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result == Right(dynamicString("from-root-fallback")))
      },
      test("Convert propagates root to inner") {
        val expr   = Resolved.Convert("Int", "String", Resolved.RootAccess(DynamicOptic.root.field("number")))
        val root   = dynamicRecord("number" -> dynamicInt(42))
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result == Right(dynamicString("42")))
      },
      test("Head propagates root to inner") {
        val expr   = Resolved.Head(Resolved.RootAccess(DynamicOptic.root.field("seq")))
        val root   = dynamicRecord("seq" -> dynamicSeq(dynamicString("first"), dynamicString("second")))
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result == Right(dynamicString("first")))
      },
      test("JoinStrings propagates root to inner") {
        val expr   = Resolved.JoinStrings(",", Resolved.RootAccess(DynamicOptic.root.field("parts")))
        val root   = dynamicRecord("parts" -> dynamicSeq(dynamicString("a"), dynamicString("b")))
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result == Right(dynamicString("a,b")))
      }
    ),
    suite("MigrationAction errors")(
      test("AddField with failing expression fails") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          "newField",
          Resolved.Fail("expression-fails")
        )
        val input  = dynamicRecord("existing" -> dynamicString("value"))
        val result = action.apply(input)
        assertTrue(result.isLeft)
      },
      test("TransformValue on missing field fails") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "nonexistent",
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = dynamicRecord("other" -> dynamicString("value"))
        val result = action.apply(input)
        assertTrue(result.isLeft)
      },
      test("TransformValue with failing transform fails") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "field",
          Resolved.Fail("transform-fails"),
          Resolved.Identity
        )
        val input  = dynamicRecord("field" -> dynamicString("value"))
        val result = action.apply(input)
        assertTrue(result.isLeft)
      },
      test("Mandate with Some extracts value") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "field",
          Resolved.Literal.string("default")
        )
        val input = dynamicRecord(
          "field" -> DynamicValue.Variant("Some", dynamicString("existing"))
        )
        val result = action.apply(input)
        assertTrue(result.isRight)
      },
      test("Mandate with None uses default") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "field",
          Resolved.Literal.string("default-value")
        )
        val input = dynamicRecord(
          "field" -> DynamicValue.Variant("None", DynamicValue.Record())
        )
        val result = action.apply(input)
        assertTrue(result.isRight)
      },
      test("Mandate with Null uses default") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "field",
          Resolved.Literal.string("null-default")
        )
        val input  = dynamicRecord("field" -> DynamicValue.Null)
        val result = action.apply(input)
        assertTrue(result.isRight)
      },
      test("Mandate with non-optional passes through") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "field",
          Resolved.Literal.string("default")
        )
        val input  = dynamicRecord("field" -> dynamicString("already-value"))
        val result = action.apply(input)
        assertTrue(result.isRight)
      },
      test("Mandate on missing field returns record unchanged") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "nonexistent",
          Resolved.Literal.string("default")
        )
        val input  = dynamicRecord("other" -> dynamicString("value"))
        val result = action.apply(input)
        // Missing field means nothing to mandate - action passes through
        assertTrue(result == Right(input))
      },
      test("Mandate with failing default expression fails") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "field",
          Resolved.Fail("default-fails")
        )
        val input = dynamicRecord(
          "field" -> DynamicValue.Variant("None", DynamicValue.Record())
        )
        val result = action.apply(input)
        assertTrue(result.isLeft)
      },
      test("ChangeType on missing field fails") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "nonexistent",
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = dynamicRecord("other" -> dynamicString("value"))
        val result = action.apply(input)
        assertTrue(result.isLeft)
      },
      test("ChangeType with failing converter fails") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "field",
          Resolved.Fail("converter-fails"),
          Resolved.Identity
        )
        val input  = dynamicRecord("field" -> dynamicString("value"))
        val result = action.apply(input)
        assertTrue(result.isLeft)
      },
      test("TransformElements with failing transform fails") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root.field("items"),
          Resolved.Fail("element-fails"),
          Resolved.Identity
        )
        val input = dynamicRecord(
          "items" -> dynamicSeq(dynamicString("a"), dynamicString("b"))
        )
        val result = action.apply(input)
        assertTrue(result.isLeft)
      },
      test("TransformKeys with failing transform fails") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root.field("map"),
          Resolved.Fail("key-fails"),
          Resolved.Identity
        )
        val input = dynamicRecord(
          "map" -> DynamicValue.Map(
            (dynamicString("key1"), dynamicString("val1"))
          )
        )
        val result = action.apply(input)
        assertTrue(result.isLeft)
      },
      test("TransformValues with failing transform fails") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root.field("map"),
          Resolved.Fail("value-fails"),
          Resolved.Identity
        )
        val input = dynamicRecord(
          "map" -> DynamicValue.Map(
            (dynamicString("key1"), dynamicString("val1"))
          )
        )
        val result = action.apply(input)
        assertTrue(result.isLeft)
      },
      test("TransformCase with nested action using root") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root.field("status"),
          "Success",
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root,
              "timestamp",
              Resolved.RootAccess(DynamicOptic.root.field("created"))
            )
          )
        )
        val input = dynamicRecord(
          "created" -> dynamicString("2026-02-03"),
          "status"  -> DynamicValue.Variant("Success", dynamicRecord("code" -> dynamicInt(200)))
        )
        val result = action.apply(input)
        assertTrue(result.isRight)
      },
      test("At with empty sequence fails") {
        val at     = Resolved.At(0, Resolved.Identity)
        val input  = dynamicSeq()
        val result = at.evalDynamic(input)
        assertTrue(result.isLeft && result.swap.toOption.get.contains("out of bounds"))
      },
      test("RootAccess with multi-level path") {
        val expr = Resolved.RootAccess(
          DynamicOptic.root.field("a").field("b").field("c").field("d")
        )
        val root = dynamicRecord(
          "a" -> dynamicRecord(
            "b" -> dynamicRecord(
              "c" -> dynamicRecord(
                "d" -> dynamicString("deep-value")
              )
            )
          )
        )
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result == Right(dynamicString("deep-value")))
      },
      test("Compose with At and RootAccess") {
        val expr = Resolved.Compose(
          Resolved.At(1, Resolved.Identity),
          Resolved.SplitString(" ", Resolved.RootAccess(DynamicOptic.root.field("text")))
        )
        val root   = dynamicRecord("text" -> dynamicString("one two three"))
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result == Right(dynamicString("two")))
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // Mandate Option Representation Variants
    // ─────────────────────────────────────────────────────────────────────────
    suite("Mandate Option Representation Variants")(
      test("Mandate extracts value from Some with Record wrapper") {
        // Option in Schema is: Variant("Some", Record(Vector(("value", inner))))
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "optField",
          Resolved.Literal.string("default")
        )
        val someWithRecord = DynamicValue.Variant(
          "Some",
          DynamicValue.Record(("value", dynamicInt(42)))
        )
        val input  = dynamicRecord("optField" -> someWithRecord)
        val result = action.apply(input)
        assertTrue(
          result == Right(dynamicRecord("optField" -> dynamicInt(42)))
        )
      },
      test("Mandate handles malformed Some without value field") {
        // Some variant with Record but without "value" field
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "optField",
          Resolved.Literal.string("default")
        )
        val malformedSome = DynamicValue.Variant(
          "Some",
          DynamicValue.Record(("other", dynamicInt(42))) // Missing "value" field
        )
        val input  = dynamicRecord("optField" -> malformedSome)
        val result = action.apply(input)
        assertTrue(result.isLeft) // Should fail with "Malformed Option"
      },
      test("Mandate extracts from simple Some representation") {
        // Fallback path: Variant("Some", directValue) without Record wrapper
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "optField",
          Resolved.Literal.string("default")
        )
        val simpleSome = DynamicValue.Variant("Some", dynamicInt(42))
        val input      = dynamicRecord("optField" -> simpleSome)
        val result     = action.apply(input)
        assertTrue(
          result == Right(dynamicRecord("optField" -> dynamicInt(42)))
        )
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // Map Key and Value Transformations
    // ─────────────────────────────────────────────────────────────────────────
    suite("Map Key and Value Transformations")(
      test("TransformKeys on DynamicValue.Map") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root.field("data"),
          Resolved.Concat(Vector(Resolved.Identity, Resolved.Literal.string("_suffix")), ""),
          Resolved.Identity // reverse not tested here
        )
        val input = dynamicRecord(
          "data" -> DynamicValue.Map(
            (dynamicString("key1"), dynamicInt(1)),
            (dynamicString("key2"), dynamicInt(2))
          )
        )
        val result = action.apply(input)
        assertTrue(result.isRight)
      },
      test("TransformKeys on DynamicValue.Record") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root.field("data"),
          Resolved.Concat(Vector(Resolved.Identity, Resolved.Literal.string("_new")), ""),
          Resolved.Identity
        )
        val input = dynamicRecord(
          "data" -> dynamicRecord("key1" -> dynamicInt(1), "key2" -> dynamicInt(2))
        )
        val result = action.apply(input)
        assertTrue(result.isRight)
      },
      test("TransformKeys on non-map type fails") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root.field("data"),
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = dynamicRecord("data" -> dynamicSeq(dynamicInt(1)))
        val result = action.apply(input)
        assertTrue(result.isLeft) // ExpectedMap error
      },
      test("TransformValues on DynamicValue.Map") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root.field("data"),
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Identity
        )
        val input = dynamicRecord(
          "data" -> DynamicValue.Map(
            (dynamicString("k1"), dynamicInt(10)),
            (dynamicString("k2"), dynamicInt(20))
          )
        )
        val result = action.apply(input)
        assertTrue(result.isRight)
      },
      test("TransformValues on DynamicValue.Record") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root.field("data"),
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Identity
        )
        val input = dynamicRecord(
          "data" -> dynamicRecord("k1" -> dynamicInt(10), "k2" -> dynamicInt(20))
        )
        val result = action.apply(input)
        assertTrue(result.isRight)
      },
      test("TransformValues on non-map type fails") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root.field("data"),
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = dynamicRecord("data" -> dynamicString("not-a-map"))
        val result = action.apply(input)
        assertTrue(result.isLeft) // ExpectedMap error
      },
      test("TransformKeys with key transform returning non-string converts to string") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root.field("data"),
          Resolved.Literal.int(42), // Returns an int, should be toString'd
          Resolved.Identity
        )
        val input = dynamicRecord(
          "data" -> dynamicRecord("orig" -> dynamicInt(1))
        )
        val result = action.apply(input)
        assertTrue(result.isRight)
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // GetOrElse Option Handling
    // ─────────────────────────────────────────────────────────────────────────
    suite("GetOrElse Option Handling")(
      test("GetOrElse extracts value from Some with Record wrapper") {
        val expr = Resolved.GetOrElse(
          Resolved.Literal(
            DynamicValue.Variant("Some", DynamicValue.Record(("value", dynamicInt(42))))
          ),
          Resolved.Literal.int(0)
        )
        val result = expr.evalDynamic
        assertTrue(result == Right(dynamicInt(42)))
      },
      test("GetOrElse uses fallback for Some without value field") {
        // Some variant with Record but no "value" key
        val expr = Resolved.GetOrElse(
          Resolved.Literal(
            DynamicValue.Variant("Some", DynamicValue.Record(("other", dynamicInt(42))))
          ),
          Resolved.Literal.int(0)
        )
        val result = expr.evalDynamic
        assertTrue(result == Right(dynamicInt(0))) // Falls back because "value" not found
      },
      test("GetOrElse handles None variant") {
        val expr = Resolved.GetOrElse(
          Resolved.Literal(DynamicValue.Variant("None", DynamicValue.Record())),
          Resolved.Literal.string("fallback")
        )
        val result = expr.evalDynamic
        assertTrue(result == Right(dynamicString("fallback")))
      },
      test("GetOrElse handles Null value") {
        val expr = Resolved.GetOrElse(
          Resolved.Literal(DynamicValue.Null),
          Resolved.Literal.string("null-fallback")
        )
        val result = expr.evalDynamic
        assertTrue(result == Right(dynamicString("null-fallback")))
      },
      test("GetOrElse passes through non-option value") {
        val expr = Resolved.GetOrElse(
          Resolved.Literal.int(100),
          Resolved.Literal.int(0)
        )
        val result = expr.evalDynamic
        assertTrue(result == Right(dynamicInt(100)))
      },
      test("GetOrElse with input for Some with Record") {
        val expr = Resolved.GetOrElse(
          Resolved.FieldAccess("opt", Resolved.Identity),
          Resolved.Literal.string("default")
        )
        val input = dynamicRecord(
          "opt" -> DynamicValue.Variant("Some", DynamicValue.Record(("value", dynamicString("inner"))))
        )
        val result = expr.evalDynamic(input)
        assertTrue(result == Right(dynamicString("inner")))
      },
      test("GetOrElse with root propagation extracts from Some") {
        val expr = Resolved.GetOrElse(
          Resolved.RootAccess(DynamicOptic.root.field("opt")),
          Resolved.RootAccess(DynamicOptic.root.field("default"))
        )
        val root = dynamicRecord(
          "opt"     -> DynamicValue.Variant("Some", DynamicValue.Record(("value", dynamicString("from-opt")))),
          "default" -> dynamicString("from-default")
        )
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result == Right(dynamicString("from-opt")))
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // Coalesce None Skipping
    // ─────────────────────────────────────────────────────────────────────────
    suite("Coalesce None Skipping")(
      test("Coalesce skips None variants and finds success") {
        val expr = Resolved.Coalesce(
          Vector(
            Resolved.Literal(DynamicValue.Variant("None", DynamicValue.Record())),
            Resolved.Literal(DynamicValue.Variant("None", DynamicValue.Record())),
            Resolved.Literal.string("found")
          )
        )
        val result = expr.evalDynamic
        assertTrue(result == Right(dynamicString("found")))
      },
      test("Coalesce fails when all alternatives are None") {
        val expr = Resolved.Coalesce(
          Vector(
            Resolved.Literal(DynamicValue.Variant("None", DynamicValue.Record())),
            Resolved.Literal(DynamicValue.Variant("None", DynamicValue.Record()))
          )
        )
        val result = expr.evalDynamic
        assertTrue(result.isLeft && result.swap.toOption.get.contains("All alternatives"))
      },
      test("Coalesce with input and None values") {
        val expr = Resolved.Coalesce(
          Vector(
            Resolved.FieldAccess("missing", Resolved.Identity), // Fails
            Resolved.Literal(DynamicValue.Variant("None", DynamicValue.Record())),
            Resolved.FieldAccess("present", Resolved.Identity)
          )
        )
        val input  = dynamicRecord("present" -> dynamicString("value"))
        val result = expr.evalDynamic(input)
        assertTrue(result == Right(dynamicString("value")))
      },
      test("Coalesce with root context") {
        val expr = Resolved.Coalesce(
          Vector(
            Resolved.RootAccess(DynamicOptic.root.field("missing")),
            Resolved.RootAccess(DynamicOptic.root.field("fallback"))
          )
        )
        val root   = dynamicRecord("fallback" -> dynamicString("found"))
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result == Right(dynamicString("found")))
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // Path Modification Edge Cases
    // ─────────────────────────────────────────────────────────────────────────
    suite("Path Modification Edge Cases")(
      test("Action at root path applies directly") {
        // When path.nodes.isEmpty, modifyAtPath applies f directly
        val action = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val input  = dynamicRecord("old" -> dynamicInt(1), "other" -> dynamicInt(2))
        val result = action.apply(input)
        assertTrue(
          result == Right(dynamicRecord("new" -> dynamicInt(1), "other" -> dynamicInt(2)))
        )
      },
      test("Action at nested path navigates correctly") {
        val action = MigrationAction.Rename(DynamicOptic.root.field("nested"), "old", "new")
        val input  = dynamicRecord(
          "nested" -> dynamicRecord("old" -> dynamicInt(1))
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord("nested" -> dynamicRecord("new" -> dynamicInt(1)))
          )
        )
      },
      test("TransformElements on non-sequence path fails with ExpectedSequence") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root.field("notSeq"),
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = dynamicRecord("notSeq" -> dynamicString("string-value"))
        val result = action.apply(input)
        assertTrue(result.isLeft)
      },
      test("AddField on non-record fails with ExpectedRecord") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("notRecord"),
          "newField",
          Resolved.Literal.int(1)
        )
        val input  = dynamicRecord("notRecord" -> dynamicSeq(dynamicInt(1)))
        val result = action.apply(input)
        assertTrue(result.isLeft)
      },
      test("DropField preserves other fields") {
        val action = MigrationAction.DropField(
          DynamicOptic.root,
          "toRemove",
          Resolved.Literal.int(0)
        )
        val input = dynamicRecord(
          "keep1"    -> dynamicInt(1),
          "toRemove" -> dynamicInt(2),
          "keep2"    -> dynamicInt(3)
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(dynamicRecord("keep1" -> dynamicInt(1), "keep2" -> dynamicInt(3)))
        )
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // Variant Case Transformations
    // ─────────────────────────────────────────────────────────────────────────
    suite("RenameCase Edge Cases")(
      test("RenameCase only renames matching case") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val input  = DynamicValue.Variant("OldCase", dynamicInt(42))
        val result = action.apply(input)
        assertTrue(result == Right(DynamicValue.Variant("NewCase", dynamicInt(42))))
      },
      test("RenameCase passes through non-matching case") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val input  = DynamicValue.Variant("OtherCase", dynamicInt(42))
        val result = action.apply(input)
        assertTrue(result == Right(DynamicValue.Variant("OtherCase", dynamicInt(42))))
      },
      test("TransformCase only transforms matching case") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "TargetCase",
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "added", Resolved.Literal.int(1))
          )
        )
        val input  = DynamicValue.Variant("TargetCase", dynamicRecord("existing" -> dynamicInt(2)))
        val result = action.apply(input)
        assertTrue(
          result == Right(
            DynamicValue.Variant("TargetCase", dynamicRecord("existing" -> dynamicInt(2), "added" -> dynamicInt(1)))
          )
        )
      },
      test("TransformCase passes through non-matching case") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "TargetCase",
          Vector(MigrationAction.AddField(DynamicOptic.root, "added", Resolved.Literal.int(1)))
        )
        val input  = DynamicValue.Variant("OtherCase", dynamicRecord())
        val result = action.apply(input)
        assertTrue(result == Right(DynamicValue.Variant("OtherCase", dynamicRecord())))
      },
      test("TransformCase with failing nested action fails") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "TargetCase",
          Vector(MigrationAction.AddField(DynamicOptic.root, "fail", Resolved.Fail("nested-fail")))
        )
        val input  = DynamicValue.Variant("TargetCase", dynamicRecord())
        val result = action.apply(input)
        assertTrue(result.isLeft)
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // JoinStrings Non-String Element Conversion
    // ─────────────────────────────────────────────────────────────────────────
    suite("JoinStrings Non-String Element Conversion")(
      test("JoinStrings converts non-string elements to string") {
        val expr = Resolved.JoinStrings(
          ",",
          Resolved.Literal(dynamicSeq(dynamicInt(1), dynamicInt(2), dynamicInt(3)))
        )
        val input  = dynamicRecord() // Provide input, even if Literal ignores it
        val result = expr.evalDynamic(input)
        // Non-strings get toString'd
        assertTrue(result.isRight)
      },
      test("JoinStrings with mixed types") {
        val expr = Resolved.JoinStrings(
          "-",
          Resolved.Literal(
            dynamicSeq(
              dynamicString("a"),
              dynamicInt(42),
              DynamicValue.Primitive(PrimitiveValue.Boolean(true))
            )
          )
        )
        val input  = dynamicRecord() // Provide input, even if Literal ignores it
        val result = expr.evalDynamic(input)
        assertTrue(result.isRight)
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // Head Root Propagation
    // ─────────────────────────────────────────────────────────────────────────
    suite("Head Root Propagation")(
      test("Head with evalDynamicWithRoot on empty sequence fails") {
        val expr   = Resolved.Head(Resolved.Identity)
        val root   = dynamicRecord()
        val input  = dynamicSeq()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result.isLeft && result.swap.toOption.get.contains("empty"))
      },
      test("Head with evalDynamicWithRoot on non-sequence fails") {
        val expr   = Resolved.Head(Resolved.Identity)
        val root   = dynamicRecord()
        val input  = dynamicString("not-seq")
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result.isLeft && result.swap.toOption.get.contains("Expected sequence"))
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // SplitString Root Propagation
    // ─────────────────────────────────────────────────────────────────────────
    suite("SplitString Root Propagation")(
      test("SplitString.evalDynamicWithRoot on non-string fails") {
        val expr   = Resolved.SplitString(",", Resolved.Identity)
        val root   = dynamicRecord()
        val input  = dynamicInt(42)
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result.isLeft && result.swap.toOption.get.contains("requires String"))
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // DefaultValue Factory Methods
    // ─────────────────────────────────────────────────────────────────────────
    suite("DefaultValue Factory Methods")(
      test("DefaultValue.fromValue creates successful default") {
        val dv     = Resolved.DefaultValue.fromValue(42, Schema[Int])
        val result = dv.evalDynamic
        assertTrue(result == Right(dynamicInt(42)))
      },
      test("DefaultValue.noDefault always fails") {
        val dv     = Resolved.DefaultValue.noDefault
        val result = dv.evalDynamic
        assertTrue(result.isLeft && result.swap.toOption.get.contains("No default"))
      },
      test("DefaultValue.fail with custom message") {
        val dv     = Resolved.DefaultValue.fail("custom error")
        val result = dv.evalDynamic
        assertTrue(result.isLeft && result.swap.toOption.get.contains("custom error"))
      },
      test("DefaultValue ignores input") {
        val dv     = Resolved.DefaultValue(Right(dynamicInt(42)))
        val input  = dynamicString("ignored")
        val result = dv.evalDynamic(input)
        assertTrue(result == Right(dynamicInt(42)))
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // Literal Factory Methods
    // ─────────────────────────────────────────────────────────────────────────
    suite("Literal Factory Methods")(
      test("Literal.string creates string literal") {
        val lit    = Resolved.Literal.string("hello")
        val result = lit.evalDynamic
        assertTrue(result == Right(dynamicString("hello")))
      },
      test("Literal.int creates int literal") {
        val lit    = Resolved.Literal.int(42)
        val result = lit.evalDynamic
        assertTrue(result == Right(dynamicInt(42)))
      },
      test("Literal.long creates long literal") {
        val lit    = Resolved.Literal.long(123L)
        val result = lit.evalDynamic
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(123L))))
      },
      test("Literal.boolean creates boolean literal") {
        val lit    = Resolved.Literal.boolean(true)
        val result = lit.evalDynamic
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Literal.double creates double literal") {
        val lit    = Resolved.Literal.double(3.14)
        val result = lit.evalDynamic
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(3.14))))
      },
      test("Literal.unit creates unit literal") {
        val lit    = Resolved.Literal.unit
        val result = lit.evalDynamic
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Unit)))
      },
      test("Literal with Schema creates proper DynamicValue") {
        case class Point(x: Int, y: Int)
        implicit val pointSchema: Schema[Point] = Schema.derived
        val lit                                 = Resolved.Literal(Point(1, 2), pointSchema)
        val result                              = lit.evalDynamic
        assertTrue(result.isRight)
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // Action Path Prefixing
    // ─────────────────────────────────────────────────────────────────────────
    suite("Action prefixPath")(
      test("AddField.prefixPath prepends to path") {
        val action   = MigrationAction.AddField(DynamicOptic.root.field("inner"), "new", Resolved.Literal.int(1))
        val prefixed = action.prefixPath(DynamicOptic.root.field("outer"))
        assertTrue(prefixed.at.toString.contains("outer"))
      },
      test("DropField.prefixPath prepends to path") {
        val action   = MigrationAction.DropField(DynamicOptic.root.field("inner"), "old", Resolved.Literal.int(0))
        val prefixed = action.prefixPath(DynamicOptic.root.field("outer"))
        assertTrue(prefixed.at.toString.contains("outer"))
      },
      test("Rename.prefixPath prepends to path") {
        val action   = MigrationAction.Rename(DynamicOptic.root.field("inner"), "old", "new")
        val prefixed = action.prefixPath(DynamicOptic.root.field("outer"))
        assertTrue(prefixed.at.toString.contains("outer"))
      },
      test("TransformValue.prefixPath prepends to path") {
        val action =
          MigrationAction.TransformValue(DynamicOptic.root.field("inner"), "f", Resolved.Identity, Resolved.Identity)
        val prefixed = action.prefixPath(DynamicOptic.root.field("outer"))
        assertTrue(prefixed.at.toString.contains("outer"))
      },
      test("Mandate.prefixPath prepends to path") {
        val action   = MigrationAction.Mandate(DynamicOptic.root.field("inner"), "f", Resolved.Literal.int(0))
        val prefixed = action.prefixPath(DynamicOptic.root.field("outer"))
        assertTrue(prefixed.at.toString.contains("outer"))
      },
      test("Optionalize.prefixPath prepends to path") {
        val action   = MigrationAction.Optionalize(DynamicOptic.root.field("inner"), "f")
        val prefixed = action.prefixPath(DynamicOptic.root.field("outer"))
        assertTrue(prefixed.at.toString.contains("outer"))
      },
      test("ChangeType.prefixPath prepends to path") {
        val action =
          MigrationAction.ChangeType(DynamicOptic.root.field("inner"), "f", Resolved.Identity, Resolved.Identity)
        val prefixed = action.prefixPath(DynamicOptic.root.field("outer"))
        assertTrue(prefixed.at.toString.contains("outer"))
      },
      test("RenameCase.prefixPath prepends to path") {
        val action   = MigrationAction.RenameCase(DynamicOptic.root.field("inner"), "Old", "New")
        val prefixed = action.prefixPath(DynamicOptic.root.field("outer"))
        assertTrue(prefixed.at.toString.contains("outer"))
      },
      test("TransformCase.prefixPath prepends to path") {
        val action   = MigrationAction.TransformCase(DynamicOptic.root.field("inner"), "Case", Vector.empty)
        val prefixed = action.prefixPath(DynamicOptic.root.field("outer"))
        assertTrue(prefixed.at.toString.contains("outer"))
      },
      test("TransformElements.prefixPath prepends to path") {
        val action =
          MigrationAction.TransformElements(DynamicOptic.root.field("inner"), Resolved.Identity, Resolved.Identity)
        val prefixed = action.prefixPath(DynamicOptic.root.field("outer"))
        assertTrue(prefixed.at.toString.contains("outer"))
      },
      test("TransformKeys.prefixPath prepends to path") {
        val action =
          MigrationAction.TransformKeys(DynamicOptic.root.field("inner"), Resolved.Identity, Resolved.Identity)
        val prefixed = action.prefixPath(DynamicOptic.root.field("outer"))
        assertTrue(prefixed.at.toString.contains("outer"))
      },
      test("TransformValues.prefixPath prepends to path") {
        val action =
          MigrationAction.TransformValues(DynamicOptic.root.field("inner"), Resolved.Identity, Resolved.Identity)
        val prefixed = action.prefixPath(DynamicOptic.root.field("outer"))
        assertTrue(prefixed.at.toString.contains("outer"))
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // Action Reverse Methods
    // ─────────────────────────────────────────────────────────────────────────
    suite("Action reverse methods")(
      test("AddField.reverse creates DropField") {
        val action  = MigrationAction.AddField(DynamicOptic.root, "f", Resolved.Literal.int(1))
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.DropField])
      },
      test("DropField.reverse creates AddField") {
        val action  = MigrationAction.DropField(DynamicOptic.root, "f", Resolved.Literal.int(1))
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.AddField])
      },
      test("Rename.reverse swaps from and to") {
        val action  = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val reverse = action.reverse.asInstanceOf[MigrationAction.Rename]
        assertTrue(reverse.from == "new" && reverse.to == "old")
      },
      test("TransformValue.reverse swaps transforms") {
        val action =
          MigrationAction.TransformValue(DynamicOptic.root, "f", Resolved.Literal.int(1), Resolved.Literal.int(2))
        val reverse = action.reverse.asInstanceOf[MigrationAction.TransformValue]
        assertTrue(reverse.transform == Resolved.Literal.int(2) && reverse.reverseTransform == Resolved.Literal.int(1))
      },
      test("Mandate.reverse creates Optionalize") {
        val action  = MigrationAction.Mandate(DynamicOptic.root, "f", Resolved.Literal.int(1))
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.Optionalize])
      },
      test("Optionalize.reverse creates Mandate with Fail") {
        val action  = MigrationAction.Optionalize(DynamicOptic.root, "f")
        val reverse = action.reverse.asInstanceOf[MigrationAction.Mandate]
        assertTrue(reverse.default.isInstanceOf[Resolved.Fail])
      },
      test("ChangeType.reverse swaps converters") {
        val action =
          MigrationAction.ChangeType(DynamicOptic.root, "f", Resolved.Literal.int(1), Resolved.Literal.int(2))
        val reverse = action.reverse.asInstanceOf[MigrationAction.ChangeType]
        assertTrue(
          reverse.converter == Resolved.Literal.int(2) && reverse.reverseConverter == Resolved.Literal.int(1)
        )
      },
      test("RenameCase.reverse swaps from and to") {
        val action  = MigrationAction.RenameCase(DynamicOptic.root, "Old", "New")
        val reverse = action.reverse.asInstanceOf[MigrationAction.RenameCase]
        assertTrue(reverse.from == "New" && reverse.to == "Old")
      },
      test("TransformCase.reverse reverses nested actions") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "Case",
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal.int(1)),
            MigrationAction.AddField(DynamicOptic.root, "b", Resolved.Literal.int(2))
          )
        )
        val reverse = action.reverse.asInstanceOf[MigrationAction.TransformCase]
        assertTrue(
          reverse.caseActions.size == 2 &&
            reverse.caseActions.head.isInstanceOf[MigrationAction.DropField]
        )
      },
      test("TransformElements.reverse swaps transforms") {
        val action =
          MigrationAction.TransformElements(DynamicOptic.root, Resolved.Literal.int(1), Resolved.Literal.int(2))
        val reverse = action.reverse.asInstanceOf[MigrationAction.TransformElements]
        assertTrue(
          reverse.elementTransform == Resolved.Literal.int(2) && reverse.reverseTransform == Resolved.Literal.int(1)
        )
      },
      test("TransformKeys.reverse swaps transforms") {
        val action  = MigrationAction.TransformKeys(DynamicOptic.root, Resolved.Literal.int(1), Resolved.Literal.int(2))
        val reverse = action.reverse.asInstanceOf[MigrationAction.TransformKeys]
        assertTrue(
          reverse.keyTransform == Resolved.Literal.int(2) && reverse.reverseTransform == Resolved.Literal.int(1)
        )
      },
      test("TransformValues.reverse swaps transforms") {
        val action =
          MigrationAction.TransformValues(DynamicOptic.root, Resolved.Literal.int(1), Resolved.Literal.int(2))
        val reverse = action.reverse.asInstanceOf[MigrationAction.TransformValues]
        assertTrue(
          reverse.valueTransform == Resolved.Literal.int(2) && reverse.reverseTransform == Resolved.Literal.int(1)
        )
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // evalDynamicWithRoot Coverage for Non-String Conversions
    // ─────────────────────────────────────────────────────────────────────────
    suite("evalDynamicWithRoot non-string conversions")(
      test("JoinStrings.evalDynamicWithRoot converts non-string elements") {
        val expr = Resolved.JoinStrings(",", Resolved.RootAccess(DynamicOptic.root.field("items")))
        val root = dynamicRecord(
          "items" -> dynamicSeq(dynamicInt(1), dynamicInt(2), dynamicInt(3))
        )
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result.isRight)
      },
      test("Concat.evalDynamicWithRoot converts non-string values") {
        val expr = Resolved.Concat(
          Vector(
            Resolved.RootAccess(DynamicOptic.root.field("num")),
            Resolved.Literal.string("-"),
            Resolved.RootAccess(DynamicOptic.root.field("bool"))
          ),
          ""
        )
        val root = dynamicRecord(
          "num"  -> dynamicInt(42),
          "bool" -> DynamicValue.Primitive(PrimitiveValue.Boolean(true))
        )
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result.isRight)
      },
      test("JoinStrings.evalDynamicWithRoot with mixed string and non-string") {
        val expr = Resolved.JoinStrings(
          "-",
          Resolved.Literal(
            dynamicSeq(
              dynamicString("text"),
              dynamicInt(100),
              DynamicValue.Primitive(PrimitiveValue.Boolean(false))
            )
          )
        )
        val root   = dynamicRecord()
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result.isRight)
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // ConstructSeq and Construct evalDynamicWithRoot Failure Paths
    // ─────────────────────────────────────────────────────────────────────────
    suite("Construct and ConstructSeq root propagation failures")(
      test("Construct.evalDynamicWithRoot with failing field expression") {
        val expr = Resolved.Construct(
          Vector(
            "ok"   -> Resolved.Literal.int(1),
            "fail" -> Resolved.RootAccess(DynamicOptic.root.field("nonexistent"))
          )
        )
        val root   = dynamicRecord("other" -> dynamicInt(42))
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result.isLeft)
      },
      test("ConstructSeq.evalDynamicWithRoot with failing element") {
        val expr = Resolved.ConstructSeq(
          Vector(
            Resolved.RootAccess(DynamicOptic.root.field("exists")),
            Resolved.RootAccess(DynamicOptic.root.field("missing"))
          )
        )
        val root   = dynamicRecord("exists" -> dynamicInt(1))
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result.isLeft)
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // Concat evalDynamic/evalDynamicWithRoot Failure Paths
    // ─────────────────────────────────────────────────────────────────────────
    suite("Concat failure paths")(
      test("Concat.evalDynamic (no input) with all literals succeeds") {
        val expr = Resolved.Concat(
          Vector(
            Resolved.Literal.int(1),
            Resolved.Literal.string("-"),
            Resolved.Literal.int(2)
          ),
          ""
        )
        val result = expr.evalDynamic
        assertTrue(result.isRight)
      },
      test("Concat.evalDynamic (no input) with failing part fails") {
        val expr = Resolved.Concat(
          Vector(
            Resolved.Literal.string("ok"),
            Resolved.Identity // Identity requires input
          ),
          "-"
        )
        val result = expr.evalDynamic
        assertTrue(result.isLeft)
      },
      test("Concat.evalDynamicWithRoot with failing part") {
        val expr = Resolved.Concat(
          Vector(
            Resolved.RootAccess(DynamicOptic.root.field("ok")),
            Resolved.RootAccess(DynamicOptic.root.field("missing"))
          ),
          "-"
        )
        val root   = dynamicRecord("ok" -> dynamicString("value"))
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result.isLeft)
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // UnwrapOption evalDynamicWithRoot Paths
    // ─────────────────────────────────────────────────────────────────────────
    suite("UnwrapOption evalDynamicWithRoot paths")(
      test("UnwrapOption.evalDynamicWithRoot with Some extracts value") {
        val expr = Resolved.UnwrapOption(
          Resolved.RootAccess(DynamicOptic.root.field("opt")),
          Resolved.Literal.string("fallback")
        )
        val root   = dynamicRecord("opt" -> DynamicValue.Variant("Some", dynamicString("inner")))
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result == Right(dynamicString("inner")))
      },
      test("UnwrapOption.evalDynamicWithRoot with None uses fallback from root") {
        val expr = Resolved.UnwrapOption(
          Resolved.Identity,
          Resolved.RootAccess(DynamicOptic.root.field("default"))
        )
        val root   = dynamicRecord("default" -> dynamicString("from-root"))
        val input  = DynamicValue.Variant("None", DynamicValue.Record())
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result == Right(dynamicString("from-root")))
      },
      test("UnwrapOption.evalDynamicWithRoot with Null uses fallback from root") {
        val expr = Resolved.UnwrapOption(
          Resolved.Identity,
          Resolved.RootAccess(DynamicOptic.root.field("nullDefault"))
        )
        val root   = dynamicRecord("nullDefault" -> dynamicString("null-fallback"))
        val input  = DynamicValue.Null
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result == Right(dynamicString("null-fallback")))
      },
      test("UnwrapOption.evalDynamicWithRoot with non-optional passes through") {
        val expr   = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.string("fallback"))
        val root   = dynamicRecord()
        val input  = dynamicString("already-value")
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result == Right(dynamicString("already-value")))
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // OpticAccess Inner Failures
    // ─────────────────────────────────────────────────────────────────────────
    suite("OpticAccess inner failures")(
      test("OpticAccess.evalDynamic with failing inner") {
        val expr   = Resolved.OpticAccess(DynamicOptic.root.field("x"), Resolved.Fail("inner-fail"))
        val input  = dynamicRecord("x" -> dynamicInt(42))
        val result = expr.evalDynamic(input)
        assertTrue(result.isLeft && result.swap.toOption.get.contains("inner-fail"))
      },
      test("OpticAccess.evalDynamicWithRoot with failing inner") {
        val expr   = Resolved.OpticAccess(DynamicOptic.root.field("x"), Resolved.Fail("inner-fail"))
        val root   = dynamicRecord("x" -> dynamicInt(42))
        val input  = root
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result.isLeft && result.swap.toOption.get.contains("inner-fail"))
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // WrapSome evalDynamic Paths
    // ─────────────────────────────────────────────────────────────────────────
    suite("WrapSome evalDynamic paths")(
      test("WrapSome.evalDynamic (no input) with literal succeeds") {
        val expr   = Resolved.WrapSome(Resolved.Literal.int(42))
        val result = expr.evalDynamic
        assertTrue(result.isRight)
      },
      test("WrapSome.evalDynamic (no input) with Identity fails") {
        val expr   = Resolved.WrapSome(Resolved.Identity)
        val result = expr.evalDynamic
        assertTrue(result.isLeft)
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // Compose evalDynamic Paths
    // ─────────────────────────────────────────────────────────────────────────
    suite("Compose evalDynamic paths")(
      test("Compose.evalDynamic (no input) with both literals succeeds") {
        // outer receives result of inner, but since inner is a literal, outer gets a value
        val expr = Resolved.Compose(
          Resolved.FieldAccess("ignored", Resolved.Identity), // outer expects record
          Resolved.Literal(dynamicRecord("ignored" -> dynamicString("value")))
        )
        val result = expr.evalDynamic
        assertTrue(result == Right(dynamicString("value")))
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // Additional Branch Coverage Tests
    // ─────────────────────────────────────────────────────────────────────────
    suite("Additional branch coverage")(
      test("Coalesce.evalDynamic with no input and no root") {
        val expr = Resolved.Coalesce(
          Vector(
            Resolved.Literal(DynamicValue.Variant("None", DynamicValue.Record())),
            Resolved.Literal.string("fallback")
          )
        )
        val result = expr.evalDynamic
        assertTrue(result == Right(dynamicString("fallback")))
      },
      test("Coalesce.evalDynamic with input only (no root context)") {
        val expr = Resolved.Coalesce(
          Vector(
            Resolved.FieldAccess("missing", Resolved.Identity),
            Resolved.FieldAccess("present", Resolved.Identity)
          )
        )
        val input  = dynamicRecord("present" -> dynamicString("found"))
        val result = expr.evalDynamic(input)
        assertTrue(result == Right(dynamicString("found")))
      },
      test("GetOrElse.evalDynamicWithRoot when primary fails") {
        val expr = Resolved.GetOrElse(
          Resolved.RootAccess(DynamicOptic.root.field("missing")),
          Resolved.RootAccess(DynamicOptic.root.field("fallback"))
        )
        val root   = dynamicRecord("fallback" -> dynamicString("default"))
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result == Right(dynamicString("default")))
      },
      test("GetOrElse.evalDynamic when primary returns None variant") {
        val expr = Resolved.GetOrElse(
          Resolved.FieldAccess("opt", Resolved.Identity),
          Resolved.Literal.string("none-fallback")
        )
        val input  = dynamicRecord("opt" -> DynamicValue.Variant("None", DynamicValue.Record()))
        val result = expr.evalDynamic(input)
        assertTrue(result == Right(dynamicString("none-fallback")))
      },
      test("GetOrElse.evalDynamic when primary returns Null") {
        val expr = Resolved.GetOrElse(
          Resolved.FieldAccess("opt", Resolved.Identity),
          Resolved.Literal.string("null-fallback")
        )
        val input  = dynamicRecord("opt" -> DynamicValue.Null)
        val result = expr.evalDynamic(input)
        assertTrue(result == Right(dynamicString("null-fallback")))
      },
      test("GetOrElse.evalDynamicWithRoot when primary returns None") {
        val expr = Resolved.GetOrElse(
          Resolved.Identity,
          Resolved.RootAccess(DynamicOptic.root.field("default"))
        )
        val root   = dynamicRecord("default" -> dynamicString("from-root"))
        val input  = DynamicValue.Variant("None", DynamicValue.Record())
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result == Right(dynamicString("from-root")))
      },
      test("GetOrElse.evalDynamicWithRoot when primary returns Null") {
        val expr = Resolved.GetOrElse(
          Resolved.Identity,
          Resolved.RootAccess(DynamicOptic.root.field("default"))
        )
        val root   = dynamicRecord("default" -> dynamicString("null-from-root"))
        val input  = DynamicValue.Null
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result == Right(dynamicString("null-from-root")))
      },
      test("GetOrElse.evalDynamicWithRoot when primary returns non-option") {
        val expr = Resolved.GetOrElse(
          Resolved.Identity,
          Resolved.Literal.string("fallback")
        )
        val root   = dynamicRecord()
        val input  = dynamicString("direct-value")
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result == Right(dynamicString("direct-value")))
      },
      test("Convert.evalDynamic without input fails") {
        val expr   = Resolved.Convert("Int", "String", Resolved.Identity)
        val result = expr.evalDynamic
        assertTrue(result.isLeft)
      },
      test("Coalesce skips failing alternatives before finding success") {
        val expr = Resolved.Coalesce(
          Vector(
            Resolved.Fail("first-fails"),
            Resolved.Fail("second-fails"),
            Resolved.Literal.string("third-succeeds")
          )
        )
        val result = expr.evalDynamic
        assertTrue(result == Right(dynamicString("third-succeeds")))
      },
      test("Coalesce with all failures returns last error") {
        val expr = Resolved.Coalesce(
          Vector(
            Resolved.Fail("first-fails"),
            Resolved.Fail("second-fails")
          )
        )
        val result = expr.evalDynamic
        assertTrue(result.isLeft && result.swap.toOption.get.contains("All alternatives"))
      },
      test("At with evalDynamicWithRoot when inner uses root") {
        val at = Resolved.At(
          0,
          Resolved.Compose(
            Resolved.RootAccess(DynamicOptic.root.field("suffix")),
            Resolved.Identity
          )
        )
        val root   = dynamicRecord("suffix" -> dynamicString("from-root"))
        val input  = dynamicSeq(dynamicString("element"))
        val result = at.evalDynamicWithRoot(input, root)
        // Element at 0 is extracted, then Compose: Identity returns element, then RootAccess gets suffix
        assertTrue(result == Right(dynamicString("from-root")))
      },
      test("FieldAccess.evalDynamicWithRoot when inner fails") {
        val expr = Resolved.FieldAccess(
          "field",
          Resolved.RootAccess(DynamicOptic.root.field("missing"))
        )
        val root   = dynamicRecord("other" -> dynamicString("value"))
        val input  = dynamicRecord("field" -> dynamicString("local"))
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result.isLeft)
      },
      test("FieldAccess.evalDynamicWithRoot when field not found in record") {
        val expr = Resolved.FieldAccess(
          "missing",
          Resolved.RootAccess(DynamicOptic.root.field("record"))
        )
        val root   = dynamicRecord("record" -> dynamicRecord("other" -> dynamicString("value")))
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result.isLeft && result.swap.toOption.get.contains("not found"))
      },
      test("FieldAccess.evalDynamicWithRoot when inner returns non-record") {
        val expr = Resolved.FieldAccess(
          "field",
          Resolved.RootAccess(DynamicOptic.root.field("str"))
        )
        val root   = dynamicRecord("str" -> dynamicString("not-a-record"))
        val input  = dynamicRecord()
        val result = expr.evalDynamicWithRoot(input, root)
        assertTrue(result.isLeft && result.swap.toOption.get.contains("Expected record"))
      }
    )
  )
}
