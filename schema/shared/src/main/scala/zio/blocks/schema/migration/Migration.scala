package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  def apply(value: A): Either[MigrationError, B] = {
    val dynamicValue = sourceSchema.toDynamicValue(value)
    dynamicMigration(dynamicValue).flatMap { migratedDynamic =>
      targetSchema.fromDynamicValue(migratedDynamic) match {
        case Right(result) => Right(result)
        case Left(err)     => Left(MigrationError.incompatibleValue(DynamicOptic.root, err.message))
      }
    }
  }

  def applyDynamic(value: DynamicValue): Either[MigrationError, DynamicValue] =
    dynamicMigration(value)

  def reverse(implicit
    reverseSourceSchema: Schema[B],
    reverseTargetSchema: Schema[A]
  ): Migration[B, A] =
    Migration(
      dynamicMigration.reverse,
      reverseSourceSchema,
      reverseTargetSchema
    )

  def andThen[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      this.dynamicMigration.andThen(that.dynamicMigration),
      this.sourceSchema,
      that.targetSchema
    )
}
