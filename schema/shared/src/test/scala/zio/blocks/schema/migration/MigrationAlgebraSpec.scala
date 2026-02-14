package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema.migration.SharedTestModels._
import zio.blocks.schema.migration.SchemaExpr
//import scala.annotation.nowarn

object MigrationAlgebraSpec extends ZIOSpecDefault {

  // [UNIFIED CONFIGURATION]

  def spec = suite("Requirement: Pure, Algebraic & Serializable Core")(
    suite("1. Purity & Serialization Proof")(
      test("Actions must be purely data (Serializable) with no hidden closures") {
        val migration = MigrationBuilder
          .make[UserV1, UserV2]
          .renameField((u: UserV1) => u.name, (u: UserV2) => u.fullName)
          .addField(
            (u: UserV2) => u.status,
            SchemaExpr.Constant(
              zio.blocks.schema.DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.String("Pending"))
            )
          )
          .build

        val actions        = migration.dynamicMigration.actions
        val isSerializable = actions.forall(_.isInstanceOf[java.io.Serializable])

        assertTrue(isSerializable)
      }
    ),

    suite("2. Algebraic Laws Verification")(
      test("Law: Identity (Migration[A, A] should be empty)") {
        val m = MigrationBuilder.make[UserV1, UserV1].build
        assertTrue(m.dynamicMigration.actions.isEmpty)
      },

      test("Law: Associativity ((A ++ B) ++ C == A ++ (B ++ C))") {
        val m1 = MigrationBuilder
          .make[UserV1, UserV2]
          .renameField((u: UserV1) => u.name, (u: UserV2) => u.fullName)
          .addField(
            (u: UserV2) => u.status,
            SchemaExpr.Constant(
              zio.blocks.schema.DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.String("Pending"))
            )
          )
          .build

        val combinedLeft  = (m1.dynamicMigration ++ m1.dynamicMigration) ++ m1.dynamicMigration
        val combinedRight = m1.dynamicMigration ++ (m1.dynamicMigration ++ m1.dynamicMigration)

        assertTrue(combinedLeft == combinedRight)
      }
    )
  )
}
