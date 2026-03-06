package zio.blocks.schema.migration

import zio.blocks.schema.Schema

/**
 * A typed migration from schema version `A` to schema version `B`.
 *
 * Wraps a [[DynamicMigration]] together with the source and target schemas,
 * providing a type-safe apply method that converts values end-to-end:
 *
 * A → DynamicValue → (apply actions) → DynamicValue → B
 *
 * Migrations compose sequentially and support structural reversal.
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Transform a value of type `A` into type `B` by converting through
   * DynamicValue and applying all migration actions.
   */
  def apply(value: A): Either[MigrationError, B] = {
    val dv = sourceSchema.toDynamicValue(value)
    dynamicMigration(dv).flatMap { migrated =>
      targetSchema.fromDynamicValue(migrated) match {
        case Right(b)        => Right(b)
        case Left(schemaErr) =>
          Left(MigrationError(s"Failed to materialize target type: ${schemaErr.message}"))
      }
    }
  }

  /** Sequential composition: apply this migration, then `that`. */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(dynamicMigration ++ that.dynamicMigration, sourceSchema, that.targetSchema)

  /** Alias for `++`. */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Structural reverse. Inverts the action list so the migration goes from `B`
   * back to `A`. Runtime correctness is best-effort — it works when sufficient
   * information (defaults, inverse converters) was provided.
   */
  def reverse: Migration[B, A] =
    Migration(dynamicMigration.reverse, targetSchema, sourceSchema)
}

object Migration {

  /** Identity migration — returns the input unchanged. */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.empty, schema, schema)

  /**
   * Start building a migration from schema `A` to schema `B`.
   */
  def newBuilder[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}
