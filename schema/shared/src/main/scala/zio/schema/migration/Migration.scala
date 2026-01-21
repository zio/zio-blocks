package zio.schema.migration

import zio.blocks.schema.Schema

final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {
  /** Apply migration to transform A to B */
  def apply(value: A): Either[MigrationError, B] = {
    // 1. A -> DynamicValue
    // 2. DynamicMigration(dv) -> dv2
    // 3. dv2 -> B
    val dyn = sourceSchema.toDynamicValue(value)
    dynamicMigration.apply(dyn).flatMap { migratedDyn =>
      targetSchema.fromDynamicValue(migratedDyn).left.map { err =>
        MigrationError.EvaluationError(zio.blocks.schema.DynamicOptic(Vector.empty), err.message)
      }
    }
  }

  /** Compose migrations sequentially */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      dynamicMigration ++ that.dynamicMigration,
      sourceSchema,
      that.targetSchema
    )

  /** Alias for ++ */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /** Reverse migration (structural inverse; runtime is best-effort) */
  def reverse: Migration[B, A] =
    Migration(
      dynamicMigration.reverse,
      targetSchema,
      sourceSchema
    )
    
  def assertValid: Either[String, Unit] = Right(()) // Placeholder for full validation
}

object Migration {
  def newBuilder[A, B](implicit schemaA: Schema[A], schemaB: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(schemaA, schemaB, Vector.empty)
    
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration(Vector.empty), schema, schema)
}
