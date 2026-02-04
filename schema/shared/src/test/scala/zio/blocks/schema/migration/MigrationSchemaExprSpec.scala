package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, SchemaExpr}
import zio.test._

object MigrationSchemaExprSpec extends ZIOSpecDefault {

  override def spec =
    suite("MigrationSchemaExpr")(
      test("captureDefaultIfMarker replaces marker") {
        val in  = SchemaExpr.DefaultValueMarker.asInstanceOf[SchemaExpr[Any, Int]]
        val out = MigrationSchemaExpr.captureDefaultIfMarker[Any, Int](in, Schema[Int])

        assertTrue(out != SchemaExpr.DefaultValueMarker)
      }
    )
}
