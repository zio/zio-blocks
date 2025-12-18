package zio.blocks.schema.migration

import zio.blocks.schema.Schema

final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {
  def apply(value: A): Either[MigrationError, B] =
    for {
      dynamicValue <- Right(sourceSchema.toDynamicValue(value))
      migrated     <- dynamicMigration(dynamicValue)
      result       <- targetSchema.fromDynamicValue(migrated).left.map(err => MigrationError.ConversionError(err.toString))
    } yield result

  def andThen[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      dynamicMigration ++ that.dynamicMigration,
      sourceSchema,
      that.targetSchema
    )

  def ++[C](that: Migration[B, C]): Migration[A, C] = andThen(that)

  def reverse: Migration[B, A] =
    Migration(
      dynamicMigration.reverse,
      targetSchema,
      sourceSchema
    )
}

object Migration {
    def newBuilder[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
        MigrationBuilder(sourceSchema, targetSchema)
}
