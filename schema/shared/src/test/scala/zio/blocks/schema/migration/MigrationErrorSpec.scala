package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}
import zio.blocks.schema.migration.MigrationAction._
import zio.test._

object MigrationErrorSpec extends ZIOSpecDefault {

  override def spec =
    suite("Migration errors")(
      test("missing path fails") {
        val dv = DynamicValue.Record.empty

        // Focus a missing record field => MissingPath
        val mig = DynamicMigration(
          Rename(at = DynamicOptic.root.field("nope"), to = "x")
        )

        val out = DynamicMigrationInterpreter(mig, dv)

        assertTrue(
          out == Left(MigrationError.MissingPath(DynamicOptic.root.field("nope")))
        )
      }
    )
}
