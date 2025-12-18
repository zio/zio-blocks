package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}

final case class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction] = Vector.empty
) {

  def addField(target: DynamicOptic, default: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.AddField(target, default))

  def dropField(source: DynamicOptic, defaultForReverse: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.DropField(source, defaultForReverse))

  def renameField(from: DynamicOptic, to: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RenameField(from, to))

  def transformField(from: DynamicOptic, to: DynamicOptic, transform: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValue(from, transform)) // a bit simplified for now

  def build: Migration[A, B] =
    Migration(
      DynamicMigration(actions),
      sourceSchema,
      targetSchema
    )
}


