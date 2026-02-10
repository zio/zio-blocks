package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicSchemaExpr, DynamicValue, PrimitiveValue}
import zio.test._

object DynamicMigrationSpec extends ZIOSpecDefault {

  def spec = suite("DynamicMigrationSpec")(
    suite("apply")(
      test("should execute a single action") {
        val record = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("age"),
              default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
            )
          )
        )

        val result = migration.apply(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(0))
            )
          )
        )
      },
      test("should execute multiple actions in sequence") {
        val record = DynamicValue.Record(
          Chunk(
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
              default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
            )
          )
        )

        val result = migration.apply(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(0))
            )
          )
        )
      },
      test("should stop and return error on first failure") {
        val record = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val migration = DynamicMigration(
          Vector(
            MigrationAction.DropField(
              at = DynamicOptic.root.field("age"), // Field doesn't exist
              defaultForReverse = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
            ),
            MigrationAction.AddField(
              at = DynamicOptic.root.field("country"),
              default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("US")))
            )
          )
        )

        val result = migration.apply(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.FieldNotFound]))
      },
      test("should execute TransformElements action") {
        val record = DynamicValue.Record(
          Chunk(
            "items" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Primitive(PrimitiveValue.Int(1)),
                DynamicValue.Primitive(PrimitiveValue.Int(2))
              )
            )
          )
        )
        val migration = DynamicMigration(
          Vector(
            MigrationAction.TransformElements(
              at = DynamicOptic.root.field("items"),
              transform = DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
                DynamicSchemaExpr.ArithmeticOperator.Add,
                DynamicSchemaExpr.NumericType.IntType
              )
            )
          )
        )

        val result = migration.apply(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "items" -> DynamicValue.Sequence(
                Chunk(
                  DynamicValue.Primitive(PrimitiveValue.Int(11)),
                  DynamicValue.Primitive(PrimitiveValue.Int(12))
                )
              )
            )
          )
        )
      },
      test("should execute Join action") {
        val record = DynamicValue.Record(
          Chunk(
            "first"  -> DynamicValue.Primitive(PrimitiveValue.String("Hello")),
            "second" -> DynamicValue.Primitive(PrimitiveValue.String("World"))
          )
        )
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Join(
              at = DynamicOptic.root.field("combined"),
              sourcePaths = Vector(
                DynamicOptic.root.field("first"),
                DynamicOptic.root.field("second")
              ),
              combiner = DynamicSchemaExpr.StringConcat(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
                DynamicSchemaExpr.StringConcat(
                  DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(" "))),
                  DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
                )
              )
            )
          )
        )

        val result = migration.apply(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk("combined" -> DynamicValue.Primitive(PrimitiveValue.String("Hello World")))
          )
        )
      },
      test("should execute RenameCase action") {
        val record = DynamicValue.Record(
          Chunk(
            "status" -> DynamicValue.Variant(
              "Active",
              DynamicValue.Record(Chunk.empty)
            )
          )
        )
        val migration = DynamicMigration(
          Vector(
            MigrationAction.RenameCase(
              at = DynamicOptic.root.field("status"),
              from = "Active",
              to = "Enabled"
            )
          )
        )

        val result = migration.apply(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "status" -> DynamicValue.Variant(
                "Enabled",
                DynamicValue.Record(Chunk.empty)
              )
            )
          )
        )
      }
    ),
    suite("identity")(
      test("should return the value unchanged") {
        val record = DynamicValue.Record(
          Chunk(
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
          Chunk(
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
              default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
            )
          )
        )

        val composed = migration1 ++ migration2
        val result   = composed.apply(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(0))
            )
          )
        )
      },
      test("should be associative") {
        val record = DynamicValue.Record(
          Chunk(
            "a" -> DynamicValue.Primitive(PrimitiveValue.String("x"))
          )
        )
        val m1 = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("b"),
              default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("y")))
            )
          )
        )
        val m2 = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("c"),
              default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("z")))
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
              default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
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
              default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
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
          Chunk(
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
