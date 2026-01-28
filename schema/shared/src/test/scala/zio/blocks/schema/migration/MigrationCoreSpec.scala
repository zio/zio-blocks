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
    DynamicValue.Record(fields.toVector)

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
              Vector(
                dynamicString("a"),
                dynamicString("b"),
                dynamicString("c")
              )
            )
          )
        )
      },
      test("WrapSome wraps value in Variant Some") {
        val expr = Resolved.WrapSome(Resolved.Literal.int(42))
        assertTrue(
          expr.evalDynamic == Right(
            DynamicValue.Variant("Some", DynamicValue.Record(Vector(("value", dynamicInt(42)))))
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
    )
  )
}
