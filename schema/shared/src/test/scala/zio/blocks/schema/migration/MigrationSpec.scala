package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr}
import zio.test._

object MigrationSpec extends ZIOSpecDefault {

  case class PersonV1(firstName: String, lastName: String)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(fullName: String, age: Int)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class PersonV3(fullName: String, age: Int, country: String)
  object PersonV3 {
    implicit val schema: Schema[PersonV3] = Schema.derived
  }

  def spec = suite("MigrationSpec")(
    suite("apply")(
      test("should migrate between real case classes") {
        val personV1 = PersonV1("John", "Doe")

        val migration = Migration(
          dynamicMigration = DynamicMigration(
            Vector(
              MigrationAction.Join(
                at = DynamicOptic.root.field("fullName"),
                sourcePaths = Vector(
                  DynamicOptic.root.field("firstName"),
                  DynamicOptic.root.field("lastName")
                ),
                combiner = SchemaExpr.StringConcat(
                  SchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
                  SchemaExpr.StringConcat(
                    SchemaExpr.Literal(" ", Schema.string),
                    SchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
                  )
                )
              ),
              MigrationAction.AddField(
                at = DynamicOptic.root.field("age"),
                default = SchemaExpr.Literal(0, Schema.int)
              )
            )
          ),
          sourceSchema = PersonV1.schema,
          targetSchema = PersonV2.schema
        )

        val result = migration.apply(personV1)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get == PersonV2("John Doe", 0))
      },
      test("should return MigrationError on DynamicMigration failure") {
        val personV1 = PersonV1("John", "Doe")

        val migration = Migration(
          dynamicMigration = DynamicMigration(
            Vector(
              MigrationAction.DropField(
                at = DynamicOptic.root.field("nonExistent"),
                defaultForReverse = SchemaExpr.Literal("", Schema.string)
              )
            )
          ),
          sourceSchema = PersonV1.schema,
          targetSchema = PersonV1.schema
        )

        val result = migration.apply(personV1)

        assertTrue(result.isLeft)
      },
      test("should return MigrationError.FromDynamicValueFailed on schema conversion failure") {
        // Create a migration that produces invalid structure for target schema
        val personV1 = PersonV1("John", "Doe")

        val migration = Migration(
          dynamicMigration = DynamicMigration(
            Vector(
              MigrationAction.DropField(
                at = DynamicOptic.root.field("firstName"),
                defaultForReverse = SchemaExpr.Literal("", Schema.string)
              )
            )
          ),
          sourceSchema = PersonV1.schema,
          targetSchema = PersonV1.schema // Expects firstName to exist
        )

        val result = migration.apply(personV1)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.toOption.get.isInstanceOf[MigrationError.FromDynamicValueFailed])
      }
    ),
    suite("identity")(
      test("should return value unchanged for identity migration") {
        val personV1  = PersonV1("John", "Doe")
        val migration = Migration.identity[PersonV1]

        val result = migration.apply(personV1)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get == personV1)
      }
    ),
    suite("++")(
      test("should compose two migrations sequentially") {
        val personV1 = PersonV1("John", "Doe")

        // V1 -> V2: Join names, add age
        val migration1 = Migration(
          dynamicMigration = DynamicMigration(
            Vector(
              MigrationAction.Join(
                at = DynamicOptic.root.field("fullName"),
                sourcePaths = Vector(
                  DynamicOptic.root.field("firstName"),
                  DynamicOptic.root.field("lastName")
                ),
                combiner = SchemaExpr.StringConcat(
                  SchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
                  SchemaExpr.StringConcat(
                    SchemaExpr.Literal(" ", Schema.string),
                    SchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
                  )
                )
              ),
              MigrationAction.AddField(
                at = DynamicOptic.root.field("age"),
                default = SchemaExpr.Literal(30, Schema.int)
              )
            )
          ),
          sourceSchema = PersonV1.schema,
          targetSchema = PersonV2.schema
        )

        // V2 -> V3: Add country
        val migration2 = Migration(
          dynamicMigration = DynamicMigration(
            Vector(
              MigrationAction.AddField(
                at = DynamicOptic.root.field("country"),
                default = SchemaExpr.Literal("US", Schema.string)
              )
            )
          ),
          sourceSchema = PersonV2.schema,
          targetSchema = PersonV3.schema
        )

        val composed = migration1 ++ migration2
        val result   = composed.apply(personV1)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get == PersonV3("John Doe", 30, "US"))
      },
      test("andThen should be an alias for ++") {
        val personV1 = PersonV1("John", "Doe")

        val migration1 = Migration(
          dynamicMigration = DynamicMigration(
            Vector(
              MigrationAction.AddField(
                at = DynamicOptic.root.field("age"),
                default = SchemaExpr.Literal(0, Schema.int)
              )
            )
          ),
          sourceSchema = PersonV1.schema,
          targetSchema = PersonV1.schema
        )

        val migration2 = Migration.identity[PersonV1]

        val composed1 = migration1 ++ migration2
        val composed2 = migration1.andThen(migration2)

        val result1 = composed1.apply(personV1)
        val result2 = composed2.apply(personV1)

        assertTrue(result1 == result2)
      }
    ),
    suite("reverse")(
      test("should reverse the migration structurally") {
        val migration = Migration(
          dynamicMigration = DynamicMigration(
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
          ),
          sourceSchema = PersonV1.schema,
          targetSchema = PersonV1.schema
        )

        val reversed = migration.reverse

        // Check that schemas are flipped
        assertTrue(reversed.sourceSchema == migration.targetSchema) &&
        assertTrue(reversed.targetSchema == migration.sourceSchema) &&
        // Check that DynamicMigration is reversed
        assertTrue(reversed.dynamicMigration == migration.dynamicMigration.reverse)
      },
      test("reverse.reverse should equal original structurally") {
        val migration = Migration(
          dynamicMigration = DynamicMigration(
            Vector(
              MigrationAction.AddField(
                at = DynamicOptic.root.field("age"),
                default = SchemaExpr.Literal(0, Schema.int)
              )
            )
          ),
          sourceSchema = PersonV1.schema,
          targetSchema = PersonV1.schema
        )

        val doubleReversed = migration.reverse.reverse

        assertTrue(doubleReversed == migration)
      }
    )
  )
}
