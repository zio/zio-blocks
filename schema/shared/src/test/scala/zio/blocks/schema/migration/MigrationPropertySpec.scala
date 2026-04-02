package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Gen

/**
 * Property-based tests for migrations.
 *
 * Covers:
 *   - Algebraic laws
 *   - Invariants
 *   - Round-trip properties
 *   - Composition properties
 *   - Optimization properties
 */
object MigrationPropertySpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Generators
  // ─────────────────────────────────────────────────────────────────────────

  val genFieldName: Gen[Any, String] =
    Gen.alphaNumericStringBounded(1, 20)

  val genPrimitiveValue: Gen[Any, DynamicValue] =
    Gen.oneOf(
      Gen.int.map(i => DynamicValue.Primitive(PrimitiveValue.Int(i))),
      Gen.string.map(s => DynamicValue.Primitive(PrimitiveValue.String(s))),
      Gen.boolean.map(b => DynamicValue.Primitive(PrimitiveValue.Boolean(b))),
      Gen.long.map(l => DynamicValue.Primitive(PrimitiveValue.Long(l)))
    )

  val genSimpleRecord: Gen[Any, DynamicValue] =
    for {
      numFields <- Gen.int(0, 5)
      fields    <- Gen.listOfN(numFields)(
                  for {
                    name  <- genFieldName
                    value <- genPrimitiveValue
                  } yield (name, value)
                )
    } yield DynamicValue.Record(fields.distinctBy(_._1).toSeq: _*)

  val genRenameAction: Gen[Any, MigrationAction.Rename] =
    for {
      from <- genFieldName
      to   <- genFieldName
    } yield MigrationAction.Rename(DynamicOptic.root, from, to)

  val genAddFieldAction: Gen[Any, MigrationAction.AddField] =
    for {
      name  <- genFieldName
      value <- genPrimitiveValue
    } yield MigrationAction.AddField(DynamicOptic.root, name, Resolved.Literal(value))

  val genDropFieldAction: Gen[Any, MigrationAction.DropField] =
    for {
      name  <- genFieldName
      value <- genPrimitiveValue
    } yield MigrationAction.DropField(DynamicOptic.root, name, Resolved.Literal(value))

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("MigrationPropertySpec")(
    suite("Identity laws")(
      test("identity.apply(v) == Right(v)") {
        check(genSimpleRecord) { value =>
          val result = DynamicMigration.identity.apply(value)
          assertTrue(result == Right(value))
        }
      },
      test("identity.isIdentity == true") {
        assertTrue(DynamicMigration.identity.isIdentity)
      },
      test("identity.actionCount == 0") {
        assertTrue(DynamicMigration.identity.actionCount == 0)
      },
      test("identity.reverse.isIdentity == true") {
        assertTrue(DynamicMigration.identity.reverse.isIdentity)
      }
    ),
    suite("Composition laws")(
      test("left identity: identity ++ m == m (structurally)") {
        check(genRenameAction) { action =>
          val m        = DynamicMigration.single(action)
          val composed = DynamicMigration.identity ++ m
          assertTrue(composed.actions == m.actions)
        }
      },
      test("right identity: m ++ identity == m (structurally)") {
        check(genRenameAction) { action =>
          val m        = DynamicMigration.single(action)
          val composed = m ++ DynamicMigration.identity
          assertTrue(composed.actions == m.actions)
        }
      },
      test("associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
        check(genRenameAction, genRenameAction, genRenameAction) { (a1, a2, a3) =>
          val m1 = DynamicMigration.single(a1)
          val m2 = DynamicMigration.single(a2)
          val m3 = DynamicMigration.single(a3)

          val leftAssoc  = (m1 ++ m2) ++ m3
          val rightAssoc = m1 ++ (m2 ++ m3)

          assertTrue(leftAssoc.actions == rightAssoc.actions)
        }
      },
      test("composition preserves action count") {
        check(genRenameAction, genRenameAction) { (a1, a2) =>
          val m1       = DynamicMigration.single(a1)
          val m2       = DynamicMigration.single(a2)
          val composed = m1 ++ m2
          assertTrue(composed.actionCount == m1.actionCount + m2.actionCount)
        }
      }
    ),
    suite("Reverse laws")(
      test("reverse.reverse == original (structurally)") {
        check(genRenameAction) { action =>
          val m             = DynamicMigration.single(action)
          val doubleReverse = m.reverse.reverse
          assertTrue(m.actions == doubleReverse.actions)
        }
      },
      test("(m1 ++ m2).reverse == m2.reverse ++ m1.reverse") {
        check(genRenameAction, genRenameAction) { (a1, a2) =>
          val m1 = DynamicMigration.single(a1)
          val m2 = DynamicMigration.single(a2)

          val composedReverse = (m1 ++ m2).reverse
          val reverseComposed = m2.reverse ++ m1.reverse

          assertTrue(composedReverse.actions == reverseComposed.actions)
        }
      },
      test("reverse preserves action count") {
        check(genRenameAction, genAddFieldAction) { (a1, a2) =>
          val m        = DynamicMigration(Vector(a1, a2))
          val reversed = m.reverse
          assertTrue(reversed.actionCount == m.actionCount)
        }
      }
    ),
    suite("Rename properties")(
      test("rename(a,b).reverse == rename(b,a)") {
        check(genFieldName, genFieldName) { (from, to) =>
          val action   = MigrationAction.Rename(DynamicOptic.root, from, to)
          val reversed = action.reverse
          reversed match {
            case MigrationAction.Rename(_, revFrom, revTo) =>
              assertTrue(revFrom == to && revTo == from)
            case _ => assertTrue(false)
          }
        }
      },
      test("rename preserves field value") {
        check(genFieldName, genFieldName, genPrimitiveValue) { (from, to, value) =>
          val action = MigrationAction.Rename(DynamicOptic.root, from, to)
          val input  = DynamicValue.Record(from -> value)
          val result = action.apply(input)
          result match {
            case Right(DynamicValue.Record(fields)) =>
              val renamed = fields.find(_._1 == to)
              assertTrue(renamed.exists(_._2 == value))
            case _ => assertTrue(false)
          }
        }
      }
    ),
    suite("AddField/DropField properties")(
      test("addField.reverse == dropField") {
        check(genFieldName, genPrimitiveValue) { (name, value) =>
          val addAction = MigrationAction.AddField(DynamicOptic.root, name, Resolved.Literal(value))
          val reversed  = addAction.reverse
          assertTrue(reversed.isInstanceOf[MigrationAction.DropField])
        }
      },
      test("dropField.reverse == addField") {
        check(genFieldName, genPrimitiveValue) { (name, value) =>
          val dropAction = MigrationAction.DropField(DynamicOptic.root, name, Resolved.Literal(value))
          val reversed   = dropAction.reverse
          assertTrue(reversed.isInstanceOf[MigrationAction.AddField])
        }
      },
      test("addField then dropField returns original") {
        check(genSimpleRecord, genFieldName, genPrimitiveValue) { (record, name, value) =>
          val addAction  = MigrationAction.AddField(DynamicOptic.root, name, Resolved.Literal(value))
          val dropAction = MigrationAction.DropField(DynamicOptic.root, name, Resolved.Literal(value))
          val migration  = DynamicMigration(Vector(addAction, dropAction))

          val result = migration.apply(record)
          assertTrue(result == Right(record))
        }
      }
    ),
    suite("Optimization properties")(
      test("optimize preserves behavior for renames") {
        check(genFieldName, genFieldName, genFieldName, genPrimitiveValue) { (a, b, c, value) =>
          val unoptimized = DynamicMigration(
            Vector(
              MigrationAction.Rename(DynamicOptic.root, a, b),
              MigrationAction.Rename(DynamicOptic.root, b, c)
            )
          )
          val optimized = unoptimized.optimize

          val input       = DynamicValue.Record(a -> value)
          val unoptResult = unoptimized.apply(input)
          val optResult   = optimized.apply(input)

          assertTrue(unoptResult == optResult)
        }
      },
      test("optimize reduces action count for consecutive renames") {
        check(genFieldName, genFieldName, genFieldName) { (a, b, c) =>
          val migration = DynamicMigration(
            Vector(
              MigrationAction.Rename(DynamicOptic.root, a, b),
              MigrationAction.Rename(DynamicOptic.root, b, c)
            )
          )
          val optimized = migration.optimize
          assertTrue(optimized.actionCount <= migration.actionCount)
        }
      },
      test("optimize of identity is identity") {
        val optimized = DynamicMigration.identity.optimize
        assertTrue(optimized.isIdentity)
      },
      test("optimize is idempotent") {
        check(genRenameAction, genRenameAction) { (a1, a2) =>
          val m     = DynamicMigration(Vector(a1, a2))
          val once  = m.optimize
          val twice = once.optimize
          assertTrue(once.actions == twice.actions)
        }
      }
    ),
    suite("Error handling properties")(
      test("failed action stops migration") {
        check(genSimpleRecord) { record =>
          val migration = DynamicMigration(
            Vector(
              MigrationAction.AddField(DynamicOptic.root, "good", Resolved.Literal.int(1)),
              MigrationAction.AddField(DynamicOptic.root, "bad", Resolved.Fail("error")),
              MigrationAction.AddField(DynamicOptic.root, "unreached", Resolved.Literal.int(3))
            )
          )
          val result = migration.apply(record)
          assertTrue(result.isLeft)
        }
      },
      test("error on non-record for record actions") {
        check(genPrimitiveValue, genFieldName) { (primitive, name) =>
          val action = MigrationAction.AddField(DynamicOptic.root, name, Resolved.Literal.int(1))
          val result = action.apply(primitive)
          assertTrue(result.isLeft)
        }
      }
    ),
    suite("Round-trip properties")(
      test("rename round-trip") {
        check(genFieldName, genFieldName, genPrimitiveValue) { (from, to, value) =>
          val forward = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, from, to))
          val input   = DynamicValue.Record(from -> value)

          val result = for {
            migrated <- forward.apply(input)
            back     <- forward.reverse.apply(migrated)
          } yield back

          assertTrue(result == Right(input))
        }
      },
      test("add/drop round-trip for existing field") {
        check(genFieldName, genPrimitiveValue, genPrimitiveValue) { (name, originalValue, defaultValue) =>
          val input = DynamicValue.Record(name -> originalValue)
          val drop  = DynamicMigration.single(
            MigrationAction.DropField(DynamicOptic.root, name, Resolved.Literal(defaultValue))
          )

          val result = for {
            dropped  <- drop.apply(input)
            restored <- drop.reverse.apply(dropped)
          } yield restored

          // Note: reverse uses the stored default, not original value
          result match {
            case Right(DynamicValue.Record(fields)) =>
              // Field should be restored with default value
              assertTrue(fields.exists(_._1 == name))
            case _ => assertTrue(false)
          }
        }
      }
    ),
    suite("Sequence operation properties")(
      test("transformElements preserves length") {
        check(Gen.int(0, 20)) { length =>
          val elements = (0 until length).map(i => DynamicValue.Primitive(PrimitiveValue.Int(i)))
          val input    = DynamicValue.Sequence(elements.toSeq: _*)
          val action   = MigrationAction.TransformElements(
            DynamicOptic.root,
            Resolved.Identity,
            Resolved.Identity
          )
          val result = action.apply(input)
          result match {
            case Right(DynamicValue.Sequence(resultElements)) =>
              assertTrue(resultElements.length == length)
            case _ => assertTrue(false)
          }
        }
      },
      test("transformElements with identity is no-op") {
        check(Gen.int(0, 10)) { length =>
          val elements = (0 until length).map(i => DynamicValue.Primitive(PrimitiveValue.Int(i)))
          val input    = DynamicValue.Sequence(elements.toSeq: _*)
          val action   = MigrationAction.TransformElements(
            DynamicOptic.root,
            Resolved.Identity,
            Resolved.Identity
          )
          val result = action.apply(input)
          assertTrue(result == Right(input))
        }
      }
    ),
    suite("Case operation properties")(
      test("renameCase only affects matching case") {
        check(genFieldName, genFieldName, genFieldName, genPrimitiveValue) { (from, to, other, value) =>
          val action = MigrationAction.RenameCase(DynamicOptic.root, from, to)

          // Non-matching case should be unchanged
          val otherInput  = DynamicValue.Variant(other, value)
          val otherResult = action.apply(otherInput)
          if (other != from) {
            assertTrue(otherResult == Right(otherInput))
          } else {
            assertTrue(otherResult.isRight)
          }
        }
      },
      test("renameCase.reverse.reverse == renameCase") {
        check(genFieldName, genFieldName) { (from, to) =>
          val action        = MigrationAction.RenameCase(DynamicOptic.root, from, to)
          val doubleReverse = action.reverse.reverse
          assertTrue(action == doubleReverse)
        }
      }
    ),
    suite("Description properties")(
      test("identity has description") {
        val desc = DynamicMigration.identity.describe
        assertTrue(desc.nonEmpty)
      },
      test("all migrations have descriptions") {
        check(genRenameAction) { action =>
          val migration = DynamicMigration.single(action)
          val desc      = migration.describe
          assertTrue(desc.nonEmpty)
        }
      }
    )
  )
}
