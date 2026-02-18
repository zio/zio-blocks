package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._

/**
 * A typed migration that transforms values of type `A` into values of type `B`.
 * Wraps a [[DynamicMigration]] with source and target [[Schema]] instances for
 * type-safe application.
 *
 * @param dynamicMigration
 *   the underlying untyped, serializable migration
 * @param sourceSchema
 *   the schema of the source type (may be a structural type)
 * @param targetSchema
 *   the schema of the target type (may be a structural type)
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /** Apply this migration, converting a value of type `A` to type `B`. */
  def apply(value: A): Either[SchemaError, B] =
    dynamicMigration(sourceSchema.toDynamicValue(value))
      .flatMap(targetSchema.fromDynamicValue)

  /** Compose two migrations sequentially. */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    new Migration(dynamicMigration ++ that.dynamicMigration, sourceSchema, that.targetSchema)

  /** Alias for `++`. */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /** Structural reverse of this migration (runtime is best-effort). */
  def reverse: Migration[B, A] =
    new Migration(dynamicMigration.reverse, targetSchema, sourceSchema)

  def isEmpty: Boolean = dynamicMigration.isEmpty

  def size: Int = dynamicMigration.size

  def actions: Chunk[MigrationAction] = dynamicMigration.actions
}

object Migration {

  /** Identity migration that passes values through unchanged. */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    new Migration(DynamicMigration.empty, schema, schema)
}
