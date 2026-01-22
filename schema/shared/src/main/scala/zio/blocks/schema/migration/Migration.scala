package zio.blocks.schema.migration

import zio.blocks.schema.Schema

/**
 * Typed migration from type A to type B. Wraps a DynamicMigration with source
 * and target schemas for type safety.
 *
 * @param dynamicMigration
 *   The underlying pure, serializable migration
 * @param sourceSchema
 *   Schema for the source type A
 * @param targetSchema
 *   Schema for the target type B
 * @tparam A
 *   Source type
 * @tparam B
 *   Target type
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Applies this migration to transform A to B.
   *
   * Process:
   *   1. Convert A to DynamicValue using sourceSchema
   *   2. Apply DynamicMigration to transform the DynamicValue
   *   3. Convert result DynamicValue back to B using targetSchema
   *
   * @param value
   *   The source value to migrate
   * @return
   *   Either a MigrationError or the migrated value of type B
   */
  def apply(value: A): Either[MigrationError, B] = {
    // Step 1: Convert A to DynamicValue
    val dynamicValue = sourceSchema.toDynamicValue(value)

    // Step 2: Apply DynamicMigration
    dynamicMigration.apply(dynamicValue).flatMap { migratedDynamic =>
      // Step 3: Convert DynamicValue to B
      targetSchema.fromDynamicValue(migratedDynamic) match {
        case Right(result)     => Right(result)
        case Left(schemaError) =>
          Left(
            MigrationError.FromDynamicValueFailed(
              path = zio.blocks.schema.DynamicOptic.root,
              schemaError = schemaError
            )
          )
      }
    }
  }

  /**
   * Composes this migration with another migration sequentially. Creates a
   * migration from A to C via B.
   *
   * @param that
   *   Migration from B to C
   * @tparam C
   *   Target type of the composed migration
   * @return
   *   Migration from A to C
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      dynamicMigration = this.dynamicMigration ++ that.dynamicMigration,
      sourceSchema = this.sourceSchema,
      targetSchema = that.targetSchema
    )

  /**
   * Alias for ++ (composition)
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Returns the structural reverse of this migration. Flips source and target
   * schemas and reverses the DynamicMigration.
   *
   * @return
   *   Migration from B to A
   */
  def reverse: Migration[B, A] =
    Migration(
      dynamicMigration = this.dynamicMigration.reverse,
      sourceSchema = this.targetSchema,
      targetSchema = this.sourceSchema
    )
}

object Migration {

  /**
   * Identity migration - returns value unchanged.
   *
   * @tparam A
   *   The type to migrate
   * @return
   *   Migration from A to A that performs no transformation
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(
      dynamicMigration = DynamicMigration.identity,
      sourceSchema = schema,
      targetSchema = schema
    )
}
