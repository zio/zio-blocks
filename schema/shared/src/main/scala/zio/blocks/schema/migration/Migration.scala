package zio.blocks.schema.migration

import zio.blocks.schema.Schema

case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  def apply(value: A): Either[MigrationError, B] = {
    val sourceDyn = sourceSchema.toDynamicValue(value)

    dynamicMigration.apply(sourceDyn).flatMap { migratedDyn =>
      targetSchema.fromDynamicValue(migratedDyn).left.map { err =>
        MigrationError.DecodingError(err.toString)
      }
    }
  }

  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(this.dynamicMigration ++ that.dynamicMigration, this.sourceSchema, that.targetSchema)

  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  def reverse: Migration[B, A] =
    Migration(this.dynamicMigration.reverse, targetSchema, sourceSchema)
}

object Migration {

  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(
      DynamicMigration(Vector.empty),
      schema,
      schema
    )

  def newBuilder[A, B](implicit source: Schema[A], target: Schema[B]): MigrationBuilder[A, B] =
    MigrationBuilder.make[A, B]
}
