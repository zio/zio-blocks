package zio.blocks.schema.migration

import zio.blocks.schema.Schema

final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {
  def apply(value: A): Either[MigrationError, B] = {
    val dynamicValue = sourceSchema.toDynamicValue(value)
    dynamicMigration(dynamicValue).flatMap { migratedValue =>
      targetSchema.fromDynamicValue(migratedValue).left.map(e => MigrationError.ValidationError(e.message))
    }
  }
}

object Migration {
  def newBuilder[A, B](implicit source: Schema[A], target: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(source, target, Vector.empty)
}
