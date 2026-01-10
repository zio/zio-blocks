package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationSpec extends ZIOSpecDefault {

  final case class V1(name: String)
  object V1 { given Schema[V1] = Schema.derived[V1] }

  final case class V2(fullName: String)
  object V2 { given Schema[V2] = Schema.derived[V2] }

  override def spec =
    suite("migration")(
      test("rename field") {
        val prog =
          DynamicMigration.sequence(
            DynamicMigration.RenameField(Path.root, "name", "fullName")
          )

        val mig = Migration.fromProgram[V1, V2](
          prog,
          summon[Schema[V1]],
          summon[Schema[V2]]
        )
        assertTrue(mig(V1("Ada")) == Right(V2("Ada")))
      }
    )
}
