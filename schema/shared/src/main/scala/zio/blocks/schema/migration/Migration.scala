package zio.blocks.schema.migration

import zio.blocks.schema.Schema

final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {
  def apply(value: A): Either[MigrationError, B] =
    for {
      dynamicInput <- Right(sourceSchema.toDynamicValue(value))
      migrated     <- dynamicMigration(dynamicInput)
      result       <-
        targetSchema
          .fromDynamicValue(migrated)
          .left
          .map(err => MigrationError.InvalidOperation(zio.blocks.schema.DynamicOptic.root, s"Schema read error: $err"))
    } yield result

  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      dynamicMigration ++ that.dynamicMigration,
      sourceSchema,
      that.targetSchema
    )
}
