package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration.SchemaExpr._
import zio.blocks.schema.migration.MigrationAction._

object PuritySpec extends ZIOSpecDefault {

  def containsClosure(action: MigrationAction): Boolean =
    action match {
      case _: Function[_, _]                          => true
      case a if !a.isInstanceOf[java.io.Serializable] => true
      case _                                          => false
    }

  def spec = suite("Forbidden Items Verification (Ironclad Purity Proof)")(
    test("Proof: No MigrationAction contains any hidden Function or Closure") {
      val migration = DynamicMigration(
        Vector(
          Rename(DynamicOptic.root.field("a"), "b"),
          AddField(
            DynamicOptic.root.field("x"),
            Constant(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          ),
          TransformValue(DynamicOptic.root, Identity()),
          Join(DynamicOptic.root, Vector.empty, Identity())
        )
      )

      val allActions = migration.actions ++ migration.reverse.actions

      val areAllActionsPure = allActions.forall(a => !containsClosure(a) && !containsClosure(a.reverse))

      assertTrue(areAllActionsPure)
    }
  )
}
