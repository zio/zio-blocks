package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}

class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction]
) {
  def build: Migration[A, B] = Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  def addField(path: DynamicOptic, default: DynamicSchemaExpr): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.AddField(path, default))

  def renameField(path: DynamicOptic, name: String): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Rename(path, name))
}
