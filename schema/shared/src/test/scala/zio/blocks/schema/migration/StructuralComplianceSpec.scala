package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import scala.language.reflectiveCalls

object StructuralComplianceSpec extends ZIOSpecDefault {

  type UserV1_Structural = { def name: String; def age: Int }

  case class UserV2_Real(fullName: String, age: Int)

  private case class UserV1_Shadow(name: String, age: Int)

  implicit val schemaStruct: Schema[UserV1_Structural] =
    Schema.derived[UserV1_Shadow].asInstanceOf[Schema[UserV1_Structural]]

  implicit val schemaDst: Schema[UserV2_Real] = Schema.derived

  def spec = suite("Structural Type Compliance Test")(
    test("MUST support migration from Structural Types (Compile-time only)") {

      val migration = MigrationBuilder
        .make[UserV1_Structural, UserV2_Real]
        .renameField(
          (old: UserV1_Structural) => old.name,
          (curr: UserV2_Real) => curr.fullName
        )
        .build

      val dynamicCore = migration.dynamicMigration
      val action      = dynamicCore.actions.head.asInstanceOf[MigrationAction.Rename]

      val pathCorrect = action.at.nodes.head.asInstanceOf[DynamicOptic.Node.Field].name == "name"

      assertTrue(pathCorrect)
    }
  )
}
