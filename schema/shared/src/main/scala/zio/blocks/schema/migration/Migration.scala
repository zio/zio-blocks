package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{Schema, SchemaError}

/**
 * Typed migration from A to B. Wraps a [[DynamicMigration]] with source and
 * target schemas. Apply converts A to DynamicValue, runs the dynamic migration,
 * then decodes to B.
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /** Apply migration to transform A to B. */
  def apply(value: A): Either[SchemaError, B] =
    for {
      dv2 <- dynamicMigration(sourceSchema.toDynamicValue(value))
      b   <- targetSchema.fromDynamicValue(dv2)
    } yield b

  /** Compose migrations sequentially. */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      dynamicMigration ++ that.dynamicMigration,
      sourceSchema,
      that.targetSchema
    )

  /** Alias for ++ */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /** Reverse migration (structural inverse; runtime is best-effort). */
  def reverse: Migration[B, A] =
    Migration(
      dynamicMigration.reverse,
      targetSchema,
      sourceSchema
    )
}

object Migration {

  /** Identity migration: no transformation. */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.empty, schema, schema)

  /** Start building a migration from source schema A to target schema B. */
  def newBuilder[A, B](sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    MigrationBuilder(sourceSchema, targetSchema, Chunk.empty)

  /** Build a migration from a dynamic migration and schemas. */
  def apply[A, B](
    dynamicMigration: DynamicMigration,
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    new Migration(dynamicMigration, sourceSchema, targetSchema)
}
