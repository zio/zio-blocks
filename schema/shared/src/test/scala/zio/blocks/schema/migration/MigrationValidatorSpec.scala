package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._
import zio.test._

object MigrationValidatorSpec extends ZIOSpecDefault {

  final case class A(a: Int)
  object A { implicit val schema: Schema[A] = Schema.derived[A] }

  final case class B(a: Int, b: Int = 1)
  object B { implicit val schema: Schema[B] = Schema.derived[B] }

  override def spec =
    suite("MigrationValidator")(
      test("AddField requires path to exist in target schema") {
        val prog = DynamicMigration(
          AddField(
            at = DynamicOptic.root.field("nope"),
            default = SchemaExpr.DefaultValueMarker.asInstanceOf[SchemaExpr[Any, Any]]
          )
        )

        val out = MigrationValidator.validate(prog, implicitly[Schema[A]], implicitly[Schema[B]])
        assertTrue(out.isLeft)
      }
    )
}
