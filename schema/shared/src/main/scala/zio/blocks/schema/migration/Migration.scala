package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, DynamicOptic}

/**
 * A typed migration from schema A to schema B.
 * 
 * This is the user-facing API that wraps DynamicMigration with type safety.
 * 
 * Key features:
 * - Type-safe: ensures source and target schemas match
 * - Composable: can chain migrations with ++
 * - Reversible: has a structural inverse
 * - Introspectable: can inspect the underlying actions
 * 
 * Note: The schemas A and B are typically structural types representing
 * old versions of data, not runtime case classes.
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {
  
  /**
   * Apply this migration to transform a value of type A to type B.
   */
  def apply(value: A): Either[MigrationError, B] = {
    // Convert A to DynamicValue
    val dynamicValue = sourceSchema.toDynamicValue(value)
    
    // Apply the dynamic migration
    dynamicMigration.apply(dynamicValue).flatMap { result =>
      // Convert back to B
      targetSchema.fromDynamicValue(result).left.map { schemaError =>
        MigrationError.validationFailed(
          DynamicOptic.root,
          s"Failed to convert result to target schema: ${schemaError.message}"
        )
      }
    }
  }

  /**
   * Compose this migration with another migration sequentially.
   * The result applies this migration first, then the other.
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] = {
    Migration(
      this.dynamicMigration ++ that.dynamicMigration,
      this.sourceSchema,
      that.targetSchema
    )
  }

  /**
   * Alias for ++
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Return the structural reverse of this migration.
   * 
   * Note: This is a structural reverse, not necessarily a perfect semantic inverse.
   */
  def reverse: Migration[B, A] = {
    Migration(
      dynamicMigration.reverse,
      targetSchema,
      sourceSchema
    )
  }

  /**
   * Get a human-readable description of this migration.
   */
  def describe: String = dynamicMigration.describe

  /**
   * Check if this migration is empty (identity).
   */
  def isEmpty: Boolean = dynamicMigration.isEmpty

  /**
   * Get the number of actions in this migration.
   */
  def size: Int = dynamicMigration.size
}

object Migration {
  
  /**
   * Create an identity migration (no-op).
   */
  def identity[A](schema: Schema[A]): Migration[A, A] = {
    Migration(DynamicMigration.identity, schema, schema)
  }

  /**
   * Create a new migration builder.
   * 
   * Example:
   * {{{
   * val migration = Migration.builder[PersonV0, PersonV1]
   *   .addField(_.country, "USA")
   *   .renameField(_.name, _.fullName)
   *   .build
   * }}}
   */
  def builder[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] = {
    new MigrationBuilder[A, B](sourceSchema, targetSchema, Vector.empty)
  }

  /**
   * Alternative syntax for creating a builder.
   */
  def newBuilder[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] = builder[A, B]
}

