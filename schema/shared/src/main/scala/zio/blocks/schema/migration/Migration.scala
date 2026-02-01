package zio.blocks.schema.migration

import zio.blocks.schema.Schema

/**
 * A typed migration from schema A to schema B.
 *
 * Migration wraps a DynamicMigration with source and target schemas, enabling
 * type-safe migration operations. Both schemas should be structural schemas
 * when representing historical type versions.
 *
 * @tparam A
 *   source type
 * @tparam B
 *   target type
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Apply this migration to a value of type A, producing a value of type B.
   */
  def apply(value: A): Either[MigrationError, B] = {
    val dynamicSource = sourceSchema.toDynamicValue(value)
    DynamicMigrationInterpreter(dynamicMigration, dynamicSource).flatMap { dynamicTarget =>
      targetSchema.fromDynamicValue(dynamicTarget) match {
        case Left(error) =>
          Left(
            MigrationError.TypeMismatch(
              zio.blocks.schema.DynamicOptic.root,
              targetSchema.reflect.typeId.toString,
              error.message
            )
          )
        case Right(result) => Right(result)
      }
    }
  }

  /**
   * Compose this migration with another to create A -> C.
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      dynamicMigration ++ that.dynamicMigration,
      sourceSchema,
      that.targetSchema
    )

  /**
   * Alias for composition.
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Create a reversed migration from B to A. Note: For actions with "best
   * effort" reversal, the reverse may not produce the exact original value.
   */
  def reverse: Migration[B, A] =
    Migration(dynamicMigration.reverse, targetSchema, sourceSchema)
}

object Migration {

  /**
   * Create an identity migration that performs no transformations.
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.identity, schema, schema)

  /**
   * Create a new migration builder.
   *
   * For historical type versions, use Schema.structural[T] to create
   * compile-time-only type representations with zero runtime overhead.
   *
   * @param source
   *   implicit schema for source type
   * @param target
   *   implicit schema for target type
   */
  def newBuilder[A, B](implicit source: Schema[A], target: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](source, target, zio.blocks.chunk.Chunk.empty)
}
