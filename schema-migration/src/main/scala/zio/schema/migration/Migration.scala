package zio.schema.migration

import zio.schema._

/**
 * A type-safe migration from schema A to schema B.
 *
 * Wraps a DynamicMigration with source and target schemas,
 * providing type safety at compile time while maintaining
 * the ability to serialize the underlying migration.
 *
 * @tparam A Source schema type
 * @tparam B Target schema type
 */
case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Apply this migration to a value of type A, producing a value of type B
   */
  def apply(value: A): Either[MigrationError, B] = {
    for {
      // Convert to DynamicValue using source schema
      dynamic <- toDynamic(value)
      // Apply the migration
      migrated <- dynamicMigration(dynamic)
      // Convert back to typed value using target schema
      result <- fromDynamic(migrated)
    } yield result
  }

  /**
   * Compose this migration with another migration
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      this.dynamicMigration ++ that.dynamicMigration,
      this.sourceSchema,
      that.targetSchema
    )

  /**
   * Reverse this migration if possible
   */
  def reverse: Either[MigrationError, Migration[B, A]] =
    dynamicMigration.reverse.map { reversed =>
      Migration(reversed, targetSchema, sourceSchema)
    }

  /**
   * Optimize the underlying dynamic migration
   */
  def optimize: Migration[A, B] =
    Migration(dynamicMigration.optimize, sourceSchema, targetSchema)

  /**
   * Extract just the serializable dynamic migration
   */
  def toDynamic: DynamicMigration = dynamicMigration

  private def toDynamic(value: A): Either[MigrationError, DynamicValue] = {
    try {
      Right(DynamicValue.fromSchemaAndValue(sourceSchema, value))
    } catch {
      case e: Exception =>
        Left(MigrationError.ValidationFailed(
          "root",
          s"Failed to convert to DynamicValue: ${e.getMessage}"
        ))
    }
  }

  private def fromDynamic(value: DynamicValue): Either[MigrationError, B] = {
    value.toTypedValue(targetSchema) match {
      case Left(error) =>
        Left(MigrationError.ValidationFailed(
          "root",
          s"Failed to convert from DynamicValue: $error"
        ))
      case Right(result) =>
        Right(result)
    }
  }
}

object Migration {
  /**
   * Create a new migration builder for transforming A to B
   */
  def newBuilder[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sourceSchema, targetSchema, zio.Chunk.empty)

  /**
   * Create an identity migration (no transformation)
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.empty, schema, schema)

  /**
   * Create a migration from a single action
   */
  def single[A, B](
    action: MigrationAction
  )(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    Migration(DynamicMigration.single(action), sourceSchema, targetSchema)
}
