package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, Schema}
import zio.blocks.schema.migration.MigrationError._

/**
 * A typed migration from type A to type B.
 * This is the user-facing API that wraps the pure, serializable DynamicMigration.
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) { self =>

  /**
   * Applies this migration to transform a value of type A to type B.
   */
  def apply(value: A): Either[MigrationError, B] = {
    // Convert the input value to DynamicValue
    val dynamicValue = sourceSchema.toDynamicValue(value)

    // Apply the migration
    dynamicMigration(dynamicValue) match {
      case Right(migratedValue) =>
        // Convert back to type B
        Schema.fromDynamicValue[B](targetSchema, migratedValue)
      case Left(error) =>
        Left(error)
    }
  }

  /**
   * Composes this migration with another migration sequentially.
   * The output type of this migration must match the input type of that migration.
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] = {
    val composedMigration = self.dynamicMigration ++ that.dynamicMigration
    Migration(
      dynamicMigration = composedMigration,
      sourceSchema = self.sourceSchema,
      targetSchema = that.targetSchema
    )
  }

  /**
   * Alias for ++ for fluent API.
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = self ++ that

  /**
   * Creates the structural reverse of this migration.
   * Note: This is a best-effort reverse; some transformations may not be reversible.
   */
  def reverse: Migration[B, A] =
    Migration(
      dynamicMigration = dynamicMigration.reverse,
      sourceSchema = targetSchema,
      targetSchema = sourceSchema
    )
}

object Migration {

  /**
   * Creates an identity migration that does nothing.
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(
      dynamicMigration = DynamicMigration.identity,
      sourceSchema = schema,
      targetSchema = schema
    )

  /**
   * Creates a new migration builder.
   */
  def newBuilder[A, B](
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}
