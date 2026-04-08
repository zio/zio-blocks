package zio.schema.migration

import zio.schema.{DynamicValue, Schema}

/**
 * Public typed façade used by library users.
 *
 * @param dynamicMigration the pure, serialisable description
 * @param sourceSchema     structural schema of the source type A
 * @param targetSchema     structural schema of the target type B
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {
  /** Transform a concrete A into B, or return a MigrationError. */
  def apply(value: A): Either[MigrationError, B] =
    DynamicValue.fromSchema(sourceSchema, value).flatMap { dv =>
      dynamicMigration(dv).flatMap(DynamicValue.toSchema(targetSchema))
    }

  /** Sequential composition */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(this.dynamicMigration ++ that.dynamicMigration, sourceSchema, that.targetSchema)

  /** Alias for ++ */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /** Structural reverse – best‑effort inverse */
  def reverse: Migration[B, A] =
    Migration(dynamicMigration.reverse, targetSchema, sourceSchema)
}
