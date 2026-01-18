package zio.blocks.schema.migration

import zio.blocks.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

final case class MigrationError(message: String, path: DynamicOptic = DynamicOptic.root)

final case class DynamicMigration(actions: Chunk[MigrationAction]) {

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    Right(value)

  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))
}

final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {
  def apply(value: A): Either[MigrationError, B] = {
    val dynamicOld = sourceSchema.toDynamicValue(value)

    targetSchema.fromDynamicValue(dynamicOld).left.map { schemaError =>
      MigrationError(s"Conversion failed: ${schemaError.message}")
    }
  }

  def reverse: Migration[B, A] = Migration(dynamicMigration.reverse, targetSchema, sourceSchema)
}