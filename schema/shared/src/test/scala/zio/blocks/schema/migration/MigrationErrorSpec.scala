package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue
import zio.test._

object MigrationErrorSpec extends ZIOSpecDefault {

  override def spec =
    suite("Migration errors")(
      test("missing path fails") {
        val dv = DynamicValue.Record(Vector.empty)

        // This targets a field path that doesn't exist, so modifyAt should fail with MissingPath
        val mig = DynamicMigration.WrapInArray(Path.root / "nope")

        val out = DynamicMigrationInterpreter(mig, dv)

        assertTrue(out == Left(MigrationError.MissingPath(Path.root / "nope")))
      }
    )
}
