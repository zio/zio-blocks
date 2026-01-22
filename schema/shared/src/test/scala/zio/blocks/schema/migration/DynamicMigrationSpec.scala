package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, DynamicOptic, PrimitiveValue, Schema, SchemaExpr}
import zio.test._

object DynamicMigrationSpec extends ZIOSpecDefault {

  def spec = suite("DynamicMigrationSpec")(
    suite("apply")(
      test("should execute a single action") {
        val record = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("age"),
              default = SchemaExpr.Literal(0, Schema.int)
            )
          )
        )

        val result = migration.apply(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(0))
            )
          )
        )
      },
      test("should execute multiple actions in sequence") {
        val record = DynamicValue.Record(
          Vector(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(
              at = DynamicOptic.root.field("firstName"),
              to = "name"
            ),
            MigrationAction.AddField(
              at = DynamicOptic.root.field("age"),
              default = SchemaExpr.Literal(0, Schema.int)
            )
          )
        )

        val result = migration.apply(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(0))
            )
          )
        )
      },
      test("should stop and return error on first failure") {
        val record = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val migration = DynamicMigration(
          Vector(
            MigrationAction.DropField(
              at = DynamicOptic.root.field("age"), // Field doesn't exist
              defaultForReverse = SchemaExpr.Literal(0, Schema.int)
            ),
            MigrationAction.AddField(
              at = DynamicOptic.root.field("country"),
              default = SchemaExpr.Literal("US", Schema.string)
            )
          )
        )

        val result = migration.apply(record)

        assertTrue(result.isLeft)
      }
    ),
    suite("identity")(
      test("should return the value unchanged") {
        val record = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val migration = DynamicMigration.identity

        val result = migration.apply(record)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get == record)
      }
    ),
    suite("++")(
      test("should compose two migrations sequentially") {
        val record = DynamicValue.Record(
          Vector(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val migration1 = DynamicMigration(
          Vector(
            MigrationAction.Rename(
              at = DynamicOptic.root.field("firstName"),
              to = "name"
            )
          )
        )
        val migration2 = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("age"),
              default = SchemaExpr.Literal(0, Schema.int)
            )
          )
        )

        val composed = migration1 ++ migration2
        val result   = composed.apply(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(0))
            )
          )
        )
      },
      test("should be associative") {
        val record = DynamicValue.Record(
          Vector(
            "a" -> DynamicValue.Primitive(PrimitiveValue.String("x"))
          )
        )
        val m1 = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("b"),
              default = SchemaExpr.Literal("y", Schema.string)
            )
          )
        )
        val m2 = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("c"),
              default = SchemaExpr.Literal("z", Schema.string)
            )
          )
        )
        val m3 = DynamicMigration(
          Vector(
            MigrationAction.Rename(
              at = DynamicOptic.root.field("a"),
              to = "d"
            )
          )
        )

        val left  = (m1 ++ m2) ++ m3
        val right = m1 ++ (m2 ++ m3)

        val leftResult  = left.apply(record)
        val rightResult = right.apply(record)

        assertTrue(leftResult == rightResult)
      }
    ),
    suite("reverse")(
      test("should reverse all actions in opposite order") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("age"),
              default = SchemaExpr.Literal(0, Schema.int)
            ),
            MigrationAction.Rename(
              at = DynamicOptic.root.field("firstName"),
              to = "name"
            )
          )
        )

        val reversed = migration.reverse

        assertTrue(reversed.actions.length == 2) &&
        assertTrue(reversed.actions(0).isInstanceOf[MigrationAction.Rename]) &&
        assertTrue(reversed.actions(1).isInstanceOf[MigrationAction.DropField])
      },
      test("reverse.reverse should equal original structurally") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("age"),
              default = SchemaExpr.Literal(0, Schema.int)
            ),
            MigrationAction.Rename(
              at = DynamicOptic.root.field("firstName"),
              to = "name"
            )
          )
        )

        val doubleReversed = migration.reverse.reverse

        assertTrue(doubleReversed == migration)
      },
      test("should support semantic inverse for lossless transformations") {
        val original = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(
              at = DynamicOptic.root.field("name"),
              to = "fullName"
            )
          )
        )

        val forward  = migration.apply(original)
        val backward = forward.flatMap(v => migration.reverse.apply(v))

        assertTrue(backward.isRight) &&
        assertTrue(backward.toOption.get == original)
      }
    )
  )
}
