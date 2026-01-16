package zio.blocks.schema.migration

import zio.blocks.schema.*
import zio.blocks.schema.migration.MigrationAction.*
import zio.test.*

object MigrationValidatorSpec extends ZIOSpecDefault {

  final case class A(a: Int)
  object A { given Schema[A] = Schema.derived[A] }

  final case class B(a: Int, b: Int = 1)
  object B { given Schema[B] = Schema.derived[B] }

  override def spec =
    suite("MigrationValidator")(
      test("AddField requires path to exist in target schema") {
        val prog = DynamicMigration(
          AddField(
            at = DynamicOptic.root.field("nope"),
            default = SchemaExpr.DefaultValueMarker.asInstanceOf[SchemaExpr[Any, Any]]
          )
        )

        val out = MigrationValidator.validate(prog, summon[Schema[A]], summon[Schema[B]])
        assertTrue(out.isLeft)
      }
    )
}
