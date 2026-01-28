package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Core tests for Migration and DynamicMigration functionality.
 *
 * Tests cover:
 *   - Identity migration
 *   - Migration application
 *   - Migration composition
 *   - Migration reversal
 *   - DynamicMigration operations
 *   - MigrationOptimizer
 */
object MigrationCoreSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Data Types
  // ─────────────────────────────────────────────────────────────────────────

  case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(fullName: String, age: Int, country: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class SimpleRecord(x: Int, y: String)
  object SimpleRecord {
    implicit val schema: Schema[SimpleRecord] = Schema.derived
  }

  case class Address(street: String, city: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class Employee(name: String, address: Address)
  object Employee {
    implicit val schema: Schema[Employee] = Schema.derived
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Test Helpers
  // ─────────────────────────────────────────────────────────────────────────

  def dynamicRecord(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(fields: _*)

  def dynamicString(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def dynamicInt(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  def dynamicBool(b: Boolean): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Boolean(b))

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("MigrationCoreSpec")(
    suite("DynamicMigration")(
      suite("Identity")(
        test("identity migration returns input unchanged") {
          val input  = dynamicRecord("name" -> dynamicString("Alice"), "age" -> dynamicInt(30))
          val result = DynamicMigration.identity.apply(input)
          assertTrue(result == Right(input))
        },
        test("identity migration isIdentity returns true") {
          assertTrue(DynamicMigration.identity.isIdentity)
        },
        test("identity migration has zero actions") {
          assertTrue(DynamicMigration.identity.actionCount == 0)
        },
        test("identity migration describe returns identity message") {
          assertTrue(DynamicMigration.identity.describe.contains("Identity"))
        }
      ),
      suite("Single Action")(
        test("single AddField action adds field with default") {
          val migration = DynamicMigration.single(
            MigrationAction.AddField(DynamicOptic.root, "newField", Resolved.Literal.int(42))
          )
          val input  = dynamicRecord("existing" -> dynamicString("value"))
          val result = migration.apply(input)
          assertTrue(
            result == Right(
              dynamicRecord(
                "existing" -> dynamicString("value"),
                "newField" -> dynamicInt(42)
              )
            )
          )
        },
        test("single DropField action removes field") {
          val migration = DynamicMigration.single(
            MigrationAction.DropField(DynamicOptic.root, "toRemove", Resolved.Literal.string("default"))
          )
          val input = dynamicRecord(
            "keep"     -> dynamicString("kept"),
            "toRemove" -> dynamicString("removed")
          )
          val result = migration.apply(input)
          assertTrue(result == Right(dynamicRecord("keep" -> dynamicString("kept"))))
        },
        test("single Rename action renames field") {
          val migration = DynamicMigration.single(
            MigrationAction.Rename(DynamicOptic.root, "oldName", "newName")
          )
          val input  = dynamicRecord("oldName" -> dynamicString("value"))
          val result = migration.apply(input)
          assertTrue(result == Right(dynamicRecord("newName" -> dynamicString("value"))))
        }
      ),
      suite("Composition")(
        test("++ composes migrations sequentially") {
          val m1 = DynamicMigration.single(
            MigrationAction.AddField(DynamicOptic.root, "field1", Resolved.Literal.int(1))
          )
          val m2 = DynamicMigration.single(
            MigrationAction.AddField(DynamicOptic.root, "field2", Resolved.Literal.int(2))
          )
          val composed = m1 ++ m2
          val input    = dynamicRecord()
          val result   = composed.apply(input)
          assertTrue(
            result == Right(
              dynamicRecord(
                "field1" -> dynamicInt(1),
                "field2" -> dynamicInt(2)
              )
            )
          )
        },
        test("andThen is alias for ++") {
          val m1 = DynamicMigration.single(
            MigrationAction.Rename(DynamicOptic.root, "a", "b")
          )
          val m2 = DynamicMigration.single(
            MigrationAction.Rename(DynamicOptic.root, "b", "c")
          )
          val composed1 = m1 ++ m2
          val composed2 = m1.andThen(m2)
          assertTrue(composed1.actions == composed2.actions)
        },
        test("composition is associative") {
          val m1 = DynamicMigration.single(
            MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal.int(1))
          )
          val m2 = DynamicMigration.single(
            MigrationAction.AddField(DynamicOptic.root, "b", Resolved.Literal.int(2))
          )
          val m3 = DynamicMigration.single(
            MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(3))
          )
          val leftAssoc  = (m1 ++ m2) ++ m3
          val rightAssoc = m1 ++ (m2 ++ m3)
          val input      = dynamicRecord()
          assertTrue(leftAssoc.apply(input) == rightAssoc.apply(input))
        },
        test("identity is left identity for ++") {
          val m = DynamicMigration.single(
            MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal.int(42))
          )
          val composed = DynamicMigration.identity ++ m
          val input    = dynamicRecord()
          assertTrue(composed.apply(input) == m.apply(input))
        },
        test("identity is right identity for ++") {
          val m = DynamicMigration.single(
            MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal.int(42))
          )
          val composed = m ++ DynamicMigration.identity
          val input    = dynamicRecord()
          assertTrue(composed.apply(input) == m.apply(input))
        }
      ),
      suite("Reverse")(
        test("reverse of identity is identity") {
          assertTrue(DynamicMigration.identity.reverse.isIdentity)
        },
        test("reverse of AddField is DropField") {
          val migration = DynamicMigration.single(
            MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.int(42))
          )
          val reversed = migration.reverse
          assertTrue(reversed.actions.head.isInstanceOf[MigrationAction.DropField])
        },
        test("reverse of DropField is AddField") {
          val migration = DynamicMigration.single(
            MigrationAction.DropField(DynamicOptic.root, "field", Resolved.Literal.int(42))
          )
          val reversed = migration.reverse
          assertTrue(reversed.actions.head.isInstanceOf[MigrationAction.AddField])
        },
        test("reverse of Rename swaps from/to") {
          val migration = DynamicMigration.single(
            MigrationAction.Rename(DynamicOptic.root, "old", "new")
          )
          val reversed = migration.reverse
          reversed.actions.head match {
            case MigrationAction.Rename(_, from, to) =>
              assertTrue(from == "new" && to == "old")
            case _ => assertTrue(false)
          }
        },
        test("reverse reverses action order") {
          val m = DynamicMigration(
            Vector(
              MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal.int(1)),
              MigrationAction.AddField(DynamicOptic.root, "b", Resolved.Literal.int(2)),
              MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(3))
            )
          )
          val reversed = m.reverse
          assertTrue(
            reversed.actions(0).asInstanceOf[MigrationAction.DropField].fieldName == "c" &&
              reversed.actions(1).asInstanceOf[MigrationAction.DropField].fieldName == "b" &&
              reversed.actions(2).asInstanceOf[MigrationAction.DropField].fieldName == "a"
          )
        },
        test("double reverse equals original structurally") {
          val migration = DynamicMigration(
            Vector(
              MigrationAction.Rename(DynamicOptic.root, "a", "b"),
              MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(1))
            )
          )
          val doubleReversed = migration.reverse.reverse
          assertTrue(migration.actions == doubleReversed.actions)
        }
      ),
      suite("ActionCount and Describe")(
        test("actionCount returns correct count") {
          val m = DynamicMigration(
            Vector(
              MigrationAction.Rename(DynamicOptic.root, "a", "b"),
              MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(1)),
              MigrationAction.DropField(DynamicOptic.root, "d", Resolved.Literal.int(2))
            )
          )
          assertTrue(m.actionCount == 3)
        },
        test("describe includes all action types") {
          val m = DynamicMigration(
            Vector(
              MigrationAction.Rename(DynamicOptic.root, "a", "b"),
              MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(1))
            )
          )
          val desc = m.describe
          assertTrue(desc.contains("Rename") && desc.contains("AddField"))
        }
      )
    ),
    suite("MigrationOptimizer")(
      test("combines consecutive renames: a->b, b->c becomes a->c") {
        val actions = Vector(
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.Rename(DynamicOptic.root, "b", "c")
        )
        val optimized = MigrationOptimizer.optimize(actions)
        assertTrue(optimized.length == 1) &&
        assertTrue(optimized.head match {
          case MigrationAction.Rename(_, from, to) => from == "a" && to == "c"
          case _                                   => false
        })
      },
      test("eliminates rename back and forth: a->b, b->a becomes empty") {
        val actions = Vector(
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.Rename(DynamicOptic.root, "b", "a")
        )
        val optimized = MigrationOptimizer.optimize(actions)
        assertTrue(optimized.isEmpty)
      },
      test("eliminates add then drop of same field") {
        val actions = Vector(
          MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal.int(1)),
          MigrationAction.DropField(DynamicOptic.root, "x", Resolved.Literal.int(1))
        )
        val optimized = MigrationOptimizer.optimize(actions)
        assertTrue(optimized.isEmpty)
      },
      test("does not optimize unrelated actions") {
        val actions = Vector(
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.Rename(DynamicOptic.root, "c", "d")
        )
        val optimized = MigrationOptimizer.optimize(actions)
        assertTrue(optimized.length == 2)
      },
      test("optimize through DynamicMigration.optimize") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.Rename(DynamicOptic.root, "b", "c")
          )
        )
        val optimized = m.optimize
        assertTrue(optimized.actionCount == 1)
      }
    ),
    suite("MigrationAction.apply")(
      test("AddField fails on non-record") {
        val action = MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.int(1))
        val result = action.apply(dynamicString("not a record"))
        assertTrue(result.isLeft)
      },
      test("DropField fails on non-record") {
        val action = MigrationAction.DropField(DynamicOptic.root, "field", Resolved.Literal.int(1))
        val result = action.apply(dynamicString("not a record"))
        assertTrue(result.isLeft)
      },
      test("Rename fails on non-record") {
        val action = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val result = action.apply(dynamicString("not a record"))
        assertTrue(result.isLeft)
      },
      test("AddField with expression evaluation failure returns error") {
        val action = MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Fail("test error"))
        val result = action.apply(dynamicRecord())
        assertTrue(result.isLeft)
      }
    ),
    suite("Resolved expressions")(
      test("Literal evaluates to constant value") {
        val expr = Resolved.Literal.int(42)
        assertTrue(expr.evalDynamic == Right(dynamicInt(42)))
      },
      test("Identity returns input unchanged") {
        val input = dynamicString("test")
        assertTrue(Resolved.Identity.evalDynamic(input) == Right(input))
      },
      test("Identity without input fails") {
        assertTrue(Resolved.Identity.evalDynamic.isLeft)
      },
      test("FieldAccess extracts field from record") {
        val expr  = Resolved.FieldAccess("name", Resolved.Identity)
        val input = dynamicRecord("name" -> dynamicString("Alice"), "age" -> dynamicInt(30))
        assertTrue(expr.evalDynamic(input) == Right(dynamicString("Alice")))
      },
      test("FieldAccess on non-record fails") {
        val expr = Resolved.FieldAccess("name", Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicString("not a record")).isLeft)
      },
      test("FieldAccess on missing field fails") {
        val expr  = Resolved.FieldAccess("missing", Resolved.Identity)
        val input = dynamicRecord("name" -> dynamicString("Alice"))
        assertTrue(expr.evalDynamic(input).isLeft)
      },
      test("Concat joins strings with separator") {
        val expr = Resolved.Concat(
          Vector(Resolved.Literal.string("a"), Resolved.Literal.string("b"), Resolved.Literal.string("c")),
          "-"
        )
        assertTrue(expr.evalDynamic(dynamicRecord()) == Right(dynamicString("a-b-c")))
      },
      test("SplitString splits string by separator") {
        val expr   = Resolved.SplitString("-", Resolved.Literal.string("a-b-c"))
        val result = expr.evalDynamic(dynamicRecord())
        assertTrue(
          result == Right(
            DynamicValue.Sequence(
              dynamicString("a"),
              dynamicString("b"),
              dynamicString("c")
            )
          )
        )
      },
      test("WrapSome wraps value in Variant Some") {
        val expr = Resolved.WrapSome(Resolved.Literal.int(42))
        assertTrue(
          expr.evalDynamic == Right(
            DynamicValue.Variant("Some", DynamicValue.Record(("value", dynamicInt(42))))
          )
        )
      },
      test("UnwrapOption extracts Some value") {
        val expr  = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.int(0))
        val input = DynamicValue.Variant("Some", dynamicInt(42))
        assertTrue(expr.evalDynamic(input) == Right(dynamicInt(42)))
      },
      test("UnwrapOption uses fallback for None") {
        val expr  = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.int(0))
        val input = DynamicValue.Variant("None", DynamicValue.Primitive(PrimitiveValue.Unit))
        assertTrue(expr.evalDynamic(input) == Right(dynamicInt(0)))
      },
      test("Compose applies inner then outer") {
        val inner    = Resolved.FieldAccess("x", Resolved.Identity)
        val outer    = Resolved.Convert("Int", "String", Resolved.Identity)
        val composed = Resolved.Compose(outer, inner)
        val input    = dynamicRecord("x" -> dynamicInt(42))
        assertTrue(composed.evalDynamic(input) == Right(dynamicString("42")))
      },
      test("Fail always returns error") {
        val expr = Resolved.Fail("test error")
        assertTrue(expr.evalDynamic.isLeft)
        assertTrue(expr.evalDynamic(dynamicRecord()).isLeft)
      }
    ),
    suite("Migration typed wrapper")(
      test("Migration.apply converts typed value through DynamicValue") {
        val migration = Migration[SimpleRecord, SimpleRecord](
          DynamicMigration.identity,
          SimpleRecord.schema,
          SimpleRecord.schema
        )
        val input = SimpleRecord(1, "test")
        assertTrue(migration.apply(input) == Right(input))
      },
      test("Migration.++ composes typed migrations") {
        val m1 = Migration[SimpleRecord, SimpleRecord](
          DynamicMigration.identity,
          SimpleRecord.schema,
          SimpleRecord.schema
        )
        val m2 = Migration[SimpleRecord, SimpleRecord](
          DynamicMigration.identity,
          SimpleRecord.schema,
          SimpleRecord.schema
        )
        val composed = m1 ++ m2
        val input    = SimpleRecord(1, "test")
        assertTrue(composed.apply(input) == Right(input))
      },
      test("Migration.isIdentity returns true for identity migration") {
        val migration = Migration[SimpleRecord, SimpleRecord](
          DynamicMigration.identity,
          SimpleRecord.schema,
          SimpleRecord.schema
        )
        assertTrue(migration.isIdentity)
      },
      test("Migration.unsafeApply returns value on success") {
        val migration = Migration[SimpleRecord, SimpleRecord](
          DynamicMigration.identity,
          SimpleRecord.schema,
          SimpleRecord.schema
        )
        val input = SimpleRecord(1, "test")
        assertTrue(migration.unsafeApply(input) == input)
      }
    ),
    suite("MigrationAction edge cases")(
      test("TransformValue transforms field value") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "value",
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Convert("String", "Int", Resolved.Identity)
        )
        val input  = dynamicRecord("value" -> dynamicInt(42))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("value" -> dynamicString("42"))))
      },
      test("TransformValue fails on non-record") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "value",
          Resolved.Identity,
          Resolved.Identity
        )
        assertTrue(action.apply(dynamicInt(42)).isLeft)
      },
      test("TransformValue fails when field not found") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "missing",
          Resolved.Identity,
          Resolved.Identity
        )
        val input = dynamicRecord("other" -> dynamicInt(1))
        assertTrue(action.apply(input).isLeft)
      },
      test("Optionalize wraps field value in Some") {
        val action = MigrationAction.Optionalize(DynamicOptic.root, "value")
        val input  = dynamicRecord("value" -> dynamicInt(42))
        val result = action.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val valueField = fields.find(_._1 == "value")
            assertTrue(valueField.exists(_._2.isInstanceOf[DynamicValue.Variant]))
          case _ => assertTrue(false)
        }
      },
      test("Optionalize fails on non-record") {
        val action = MigrationAction.Optionalize(DynamicOptic.root, "value")
        assertTrue(action.apply(dynamicInt(42)).isLeft)
      },
      test("Mandate extracts value from Some") {
        val action    = MigrationAction.Mandate(DynamicOptic.root, "value", Resolved.Literal.int(0))
        val someValue = DynamicValue.Variant("Some", DynamicValue.Record(("value", dynamicInt(42))))
        val input     = dynamicRecord("value" -> someValue)
        val result    = action.apply(input)
        assertTrue(result == Right(dynamicRecord("value" -> dynamicInt(42))))
      },
      test("Mandate uses default for None") {
        val action    = MigrationAction.Mandate(DynamicOptic.root, "value", Resolved.Literal.int(99))
        val noneValue = DynamicValue.Variant("None", DynamicValue.Record())
        val input     = dynamicRecord("value" -> noneValue)
        val result    = action.apply(input)
        assertTrue(result == Right(dynamicRecord("value" -> dynamicInt(99))))
      },
      test("Mandate fails on non-record") {
        val action = MigrationAction.Mandate(DynamicOptic.root, "value", Resolved.Literal.int(0))
        assertTrue(action.apply(dynamicInt(42)).isLeft)
      },
      test("RenameCase renames variant case") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val input  = DynamicValue.Variant("OldCase", dynamicRecord())
        val result = action.apply(input)
        result match {
          case Right(DynamicValue.Variant(name, _)) => assertTrue(name == "NewCase")
          case _                                    => assertTrue(false)
        }
      },
      test("RenameCase leaves other cases unchanged") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val input  = DynamicValue.Variant("OtherCase", dynamicRecord())
        val result = action.apply(input)
        result match {
          case Right(DynamicValue.Variant(name, _)) => assertTrue(name == "OtherCase")
          case _                                    => assertTrue(false)
        }
      },
      test("RenameCase passes through non-variant unchanged") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        // RenameCase passes through non-variant values unchanged
        assertTrue(action.apply(dynamicInt(42)) == Right(dynamicInt(42)))
      },
      test("TransformElements transforms sequence elements") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Convert("String", "Int", Resolved.Identity)
        )
        val input  = DynamicValue.Sequence(dynamicInt(1), dynamicInt(2))
        val result = action.apply(input)
        result match {
          case Right(DynamicValue.Sequence(elements)) =>
            assertTrue(elements.length == 2 && elements.head == dynamicString("1"))
          case _ => assertTrue(false)
        }
      },
      test("TransformElements fails on non-sequence") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        assertTrue(action.apply(dynamicInt(42)).isLeft)
      },
      test("TransformElements handles empty sequence") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = DynamicValue.Sequence()
        val result = action.apply(input)
        assertTrue(result == Right(DynamicValue.Sequence()))
      },
      test("TransformKeys transforms record keys") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Concat(Vector(Resolved.Identity, Resolved.Literal.string("_suffix")), ""),
          Resolved.Identity // reverse not needed for this test
        )
        val input  = dynamicRecord("key1" -> dynamicInt(1), "key2" -> dynamicInt(2))
        val result = action.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1.contains("_suffix")))
          case _ => assertTrue(false)
        }
      },
      test("TransformKeys transforms Map keys") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = DynamicValue.Map((dynamicString("a"), dynamicInt(1)), (dynamicString("b"), dynamicInt(2)))
        val result = action.apply(input)
        assertTrue(result.isRight)
      },
      test("TransformKeys fails on non-map") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        assertTrue(action.apply(dynamicInt(42)).isLeft)
      },
      test("TransformValues transforms record values") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Convert("String", "Int", Resolved.Identity)
        )
        val input  = dynamicRecord("key" -> dynamicInt(42))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("key" -> dynamicString("42"))))
      },
      test("TransformValues transforms Map values") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = DynamicValue.Map((dynamicString("a"), dynamicInt(1)))
        val result = action.apply(input)
        assertTrue(result.isRight)
      },
      test("TransformValues fails on non-map") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        assertTrue(action.apply(dynamicInt(42)).isLeft)
      },
      test("ChangeType converts field type") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "value",
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Convert("String", "Int", Resolved.Identity)
        )
        val input  = dynamicRecord("value" -> dynamicInt(42))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("value" -> dynamicString("42"))))
      },
      test("ChangeType fails when field missing") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "missing",
          Resolved.Identity,
          Resolved.Identity
        )
        val input = dynamicRecord("other" -> dynamicInt(1))
        assertTrue(action.apply(input).isLeft)
      },
      test("TransformCase transforms matching case contents") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "TestCase",
          Vector(MigrationAction.Rename(DynamicOptic.root, "old", "new"))
        )
        val input  = DynamicValue.Variant("TestCase", dynamicRecord("old" -> dynamicInt(1)))
        val result = action.apply(input)
        result match {
          case Right(DynamicValue.Variant(name, DynamicValue.Record(fields))) =>
            assertTrue(name == "TestCase" && fields.exists(_._1 == "new"))
          case _ => assertTrue(false)
        }
      },
      test("TransformCase leaves non-matching cases unchanged") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "TestCase",
          Vector(MigrationAction.Rename(DynamicOptic.root, "old", "new"))
        )
        val input  = DynamicValue.Variant("OtherCase", dynamicRecord("old" -> dynamicInt(1)))
        val result = action.apply(input)
        result match {
          case Right(DynamicValue.Variant(name, DynamicValue.Record(fields))) =>
            assertTrue(name == "OtherCase" && fields.exists(_._1 == "old"))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("MigrationOptimizer edge cases")(
      test("preserves non-optimizable mixed actions") {
        val actions = Vector(
          MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal.int(1)),
          MigrationAction.Rename(DynamicOptic.root, "b", "c"),
          MigrationAction.DropField(DynamicOptic.root, "d", Resolved.Literal.int(0))
        )
        val optimized = MigrationOptimizer.optimize(actions)
        assertTrue(optimized.length == 3)
      },
      test("chains multiple renames: a->b->c->d becomes a->d") {
        val actions = Vector(
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.Rename(DynamicOptic.root, "b", "c"),
          MigrationAction.Rename(DynamicOptic.root, "c", "d")
        )
        val optimized = MigrationOptimizer.optimize(actions)
        assertTrue(optimized.length == 1)
        optimized.head match {
          case MigrationAction.Rename(_, from, to) =>
            assertTrue(from == "a" && to == "d")
          case _ => assertTrue(false)
        }
      },
      test("handles empty actions") {
        val actions   = Vector.empty[MigrationAction]
        val optimized = MigrationOptimizer.optimize(actions)
        assertTrue(optimized.isEmpty)
      },
      test("handles single action") {
        val actions   = Vector(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val optimized = MigrationOptimizer.optimize(actions)
        assertTrue(optimized.length == 1)
      }
    ),
    suite("DynamicMigration additional cases")(
      test("isIdentity returns false for non-empty migration") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "a", "b")
        )
        assertTrue(!migration.isIdentity)
      },
      test("actions property returns all actions") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(1))
          )
        )
        assertTrue(m.actions.length == 2)
      },
      test("apply propagates action errors") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Fail("expression error"))
        )
        val result = migration.apply(dynamicRecord())
        assertTrue(result.isLeft)
      }
    ),
    suite("MigrationAction reverse")(
      test("TransformValue reverse swaps transforms") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "value",
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Convert("String", "Int", Resolved.Identity)
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.TransformValue])
      },
      test("Optionalize reverse is Mandate") {
        val action   = MigrationAction.Optionalize(DynamicOptic.root, "value")
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Mandate])
      },
      test("Mandate reverse is Optionalize") {
        val action   = MigrationAction.Mandate(DynamicOptic.root, "value", Resolved.Literal.int(0))
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Optionalize])
      },
      test("RenameCase reverse swaps names") {
        val action   = MigrationAction.RenameCase(DynamicOptic.root, "Old", "New")
        val reversed = action.reverse
        reversed match {
          case MigrationAction.RenameCase(_, from, to) =>
            assertTrue(from == "New" && to == "Old")
          case _ => assertTrue(false)
        }
      },
      test("TransformElements reverse swaps transforms") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Convert("String", "Int", Resolved.Identity)
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.TransformElements])
      },
      test("TransformKeys reverse swaps transforms") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.TransformKeys])
      },
      test("TransformValues reverse swaps transforms") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.TransformValues])
      },
      test("ChangeType reverse swaps converters") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "value",
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Convert("String", "Int", Resolved.Identity)
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.ChangeType])
      },
      test("TransformCase reverse reverses nested actions") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "TestCase",
          Vector(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.TransformCase])
      }
    ),
    suite("Additional coverage tests")(
      test("AddField with FieldAccess default") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          "derived",
          Resolved.FieldAccess("source", Resolved.Identity)
        )
        val input  = dynamicRecord("source" -> dynamicInt(42))
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "source"  -> dynamicInt(42),
              "derived" -> dynamicInt(42)
            )
          )
        )
      },
      test("Concat expression with non-string values converts to string") {
        val expr = Resolved.Concat(
          Vector(Resolved.Literal.int(1), Resolved.Literal.int(2)),
          "-"
        )
        val result = expr.evalDynamic
        assertTrue(result.isRight)
      },
      test("Compose with literal outer") {
        val expr = Resolved.Compose(
          Resolved.Literal.string("constant"),
          Resolved.Identity
        )
        assertTrue(expr.evalDynamic(dynamicInt(42)) == Right(dynamicString("constant")))
      },
      test("ConstructSeq with error propagation") {
        val expr = Resolved.ConstructSeq(
          Vector(Resolved.Literal.int(1), Resolved.Fail("error"))
        )
        assertTrue(expr.evalDynamic.isLeft)
      },
      test("Literal.fromValue with schema") {
        val value = 42
        val lit   = Resolved.Literal(DynamicValue.Primitive(PrimitiveValue.Int(value)))
        assertTrue(lit.evalDynamic == Right(dynamicInt(42)))
      },
      test("DropField removes field preserving others") {
        val action = MigrationAction.DropField(
          DynamicOptic.root,
          "toRemove",
          Resolved.Literal.int(0)
        )
        val input = dynamicRecord(
          "keep1"    -> dynamicInt(1),
          "toRemove" -> dynamicInt(99),
          "keep2"    -> dynamicInt(2)
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "keep1" -> dynamicInt(1),
              "keep2" -> dynamicInt(2)
            )
          )
        )
      },
      test("Rename preserves field order") {
        val action = MigrationAction.Rename(DynamicOptic.root, "a", "z")
        val input  = dynamicRecord("a" -> dynamicInt(1), "b" -> dynamicInt(2))
        val result = action.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.head._1 == "z")
          case _ => assertTrue(false)
        }
      },
      test("TransformValue with failing transform") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "value",
          Resolved.Fail("transform failed"),
          Resolved.Identity
        )
        val input = dynamicRecord("value" -> dynamicInt(42))
        assertTrue(action.apply(input).isLeft)
      },
      test("Mandate with fallback simple representation") {
        val action    = MigrationAction.Mandate(DynamicOptic.root, "value", Resolved.Literal.int(0))
        val someValue = DynamicValue.Variant("Some", dynamicInt(42))
        val input     = dynamicRecord("value" -> someValue)
        val result    = action.apply(input)
        assertTrue(result == Right(dynamicRecord("value" -> dynamicInt(42))))
      },
      test("Mandate with missing field does nothing") {
        val action = MigrationAction.Mandate(DynamicOptic.root, "missing", Resolved.Literal.int(0))
        val input  = dynamicRecord("other" -> dynamicInt(1))
        val result = action.apply(input)
        assertTrue(result == Right(input))
      },
      test("TransformCase with empty nested actions") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "TestCase",
          Vector.empty
        )
        val input  = DynamicValue.Variant("TestCase", dynamicRecord("a" -> dynamicInt(1)))
        val result = action.apply(input)
        assertTrue(result == Right(input))
      },
      test("TransformElements with transform error") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Fail("element error"),
          Resolved.Identity
        )
        val input = DynamicValue.Sequence(dynamicInt(1))
        assertTrue(action.apply(input).isLeft)
      },
      test("TransformKeys with transform producing non-string") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Literal.int(1),
          Resolved.Identity
        )
        val input  = dynamicRecord("key" -> dynamicInt(1))
        val result = action.apply(input)
        assertTrue(result.isRight)
      },
      test("TransformKeys with error") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Fail("key error"),
          Resolved.Identity
        )
        val input = dynamicRecord("key" -> dynamicInt(1))
        assertTrue(action.apply(input).isLeft)
      },
      test("TransformValues with error") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Resolved.Fail("value error"),
          Resolved.Identity
        )
        val input = dynamicRecord("key" -> dynamicInt(1))
        assertTrue(action.apply(input).isLeft)
      },
      test("ChangeType with failing converter") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "value",
          Resolved.Fail("conversion error"),
          Resolved.Identity
        )
        val input = dynamicRecord("value" -> dynamicInt(42))
        assertTrue(action.apply(input).isLeft)
      },
      test("TransformKeys on Map with error") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Fail("key error"),
          Resolved.Identity
        )
        val input = DynamicValue.Map((dynamicString("a"), dynamicInt(1)))
        assertTrue(action.apply(input).isLeft)
      },
      test("TransformValues on Map with error") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Resolved.Fail("value error"),
          Resolved.Identity
        )
        val input = DynamicValue.Map((dynamicString("a"), dynamicInt(1)))
        assertTrue(action.apply(input).isLeft)
      }
    )
  )
}
