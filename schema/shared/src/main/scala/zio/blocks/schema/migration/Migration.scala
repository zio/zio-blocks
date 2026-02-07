package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, DynamicValue, DynamicOptic}

/**
 * Migration (Unified Shared Edition) ----------------------------------
 * Represents a typed, structural transformation from Schema[A] to Schema[B]. *
 * NOTE: This file is in the 'shared' folder. It uses Scala 2 syntax (implicits)
 * which is fully compatible with Scala 3.
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Apply the migration to a typed value. Flow: A -> DynamicValue -> (Migrated)
   * DynamicValue -> B
   */
  def apply(value: A): Either[MigrationError, B] = {
    // 1. Convert Source to DynamicValue (Runtime Representation)
    val dynamicSource: DynamicValue = sourceSchema.toDynamicValue(value)

    // 2. Delegate to the Interpreter Logic
    dynamicMigration.apply(dynamicSource).flatMap { dynamicTarget =>
      // 3. Convert DynamicValue back to Target (B)
      targetSchema.fromDynamicValue(dynamicTarget) match {
        case Left(schemaError) =>
          // [CRITICAL FIX]
          // Decoding failures happen at the object root.
          // We wrap SchemaError into MigrationError with explicit path.
          Left(MigrationError.DecodingError(DynamicOptic.root, schemaError.toString))

        case Right(b) =>
          // Safe cast because Schema[B] guarantees the structure
          // This works in both Scala 2 and Scala 3
          Right(b.asInstanceOf[B])
      }
    }
  }

  /**
   * Compose two migrations sequentially. Law: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++
   * m3)
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      this.dynamicMigration ++ that.dynamicMigration,
      this.sourceSchema,
      that.targetSchema
    )

  /**
   * Alias for ++ (Standard Scala function composition syntax).
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Structurally reverses the migration. Essential for generating
   * backward-compatible schemas automatically. Law: m.reverse.reverse == m
   */
  def reverse: Migration[B, A] =
    Migration(
      dynamicMigration.reverse,
      targetSchema,
      sourceSchema
    )
}

object Migration {

  /**
   * Identity Migration (No-Op). Using 'implicit' to be compatible with both
   * Scala 2 and Scala 3.
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.empty, schema, schema)

  /**
   * [BUILDER API ENTRY POINT] Usage: val migration =
   * Migration.newBuilder[UserV1, UserV2] .renameField(_.name, _.fullName)
   * .build
   */
  def newBuilder[A, B](implicit source: Schema[A], target: Schema[B]): MigrationBuilder[A, B, MigrationState.Empty] =
    MigrationBuilder.make[A, B]
}
