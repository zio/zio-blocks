package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * A type-safe migration that transforms values of type `A` into values of type
 * `B`.
 *
 * `Migration` follows the same dual-layer architecture as
 * [[zio.blocks.schema.patch.Patch]]: a typed wrapper around the untyped
 * [[DynamicMigration]] core. The `sourceSchema` and `targetSchema` provide the
 * type-safe conversion between typed values and
 * [[zio.blocks.schema.DynamicValue]].
 *
 * '''Laws:'''
 *   - '''Identity:''' `Migration.identity[A].apply(a) == Right(a)`
 *   - '''Associativity:'''
 *     `((m1 ++ m2) ++ m3).apply(a) == (m1 ++ (m2 ++ m3)).apply(a)`
 *   - '''Double-reverse:'''
 *     `m.reverse.reverse.dynamicMigration == m.dynamicMigration`
 *   - '''Composition:''' `(m1 ++ m2).apply(a) == m1.apply(a).flatMap(m2.apply)`
 *
 * @param dynamicMigration
 *   The untyped migration operations
 * @param sourceSchema
 *   The schema for the source type A, enabling A to DynamicValue conversion
 * @param targetSchema
 *   The schema for the target type B, enabling DynamicValue to B conversion
 */
final case class Migration[A, B] private[migration] (
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Apply this migration to a value of type `A`, producing a value of type `B`.
   *
   * The process is:
   *   1. Convert `A` to `DynamicValue` using `sourceSchema`
   *   2. Apply the `dynamicMigration` to transform the `DynamicValue`
   *   3. Convert the resulting `DynamicValue` to `B` using `targetSchema`
   *
   * Errors can occur at step 2 (migration action failure) or step 3 (target
   * schema conversion failure).
   */
  def apply(value: A): Either[MigrationError, B] = {
    val dv = sourceSchema.toDynamicValue(value)
    dynamicMigration(dv) match {
      case Right(migrated) =>
        targetSchema.fromDynamicValue(migrated) match {
          case Right(b)          => Right(b)
          case Left(schemaError) =>
            Left(
              MigrationError.CustomError(
                DynamicOptic.root,
                "Failed to convert migrated DynamicValue to target type: " + schemaError.message
              )
            )
        }
      case Left(err) => Left(err)
    }
  }

  /**
   * Compose two migrations. The result applies this migration first (A to B),
   * then `that` migration (B to C), yielding A to C.
   *
   * The underlying dynamic migrations are concatenated.
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(this.dynamicMigration ++ that.dynamicMigration, this.sourceSchema, that.targetSchema)

  /** Alias for `++`. */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Produce the structural inverse of this migration. Swaps source and target
   * schemas and reverses the underlying dynamic migration.
   *
   * Note: semantic invertibility depends on whether every action in the
   * migration has a lossless reverse (e.g., added fields must store defaults
   * for re-addition on reversal).
   */
  def reverse: Migration[B, A] =
    Migration(dynamicMigration.reverse, targetSchema, sourceSchema)

  /** Check if this migration is empty (no actions). */
  def isEmpty: Boolean = dynamicMigration.isEmpty
}

object Migration {

  /**
   * Creates an identity migration that passes values through unchanged.
   *
   * The identity migration uses the same schema for source and target and
   * contains no actions.
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.empty, schema, schema)

  /**
   * Creates a `Migration[A, B]` from a pre-built `DynamicMigration` and
   * schemas.
   *
   * This is the primary entry point for constructing migrations from individual
   * [[MigrationAction]]s.
   */
  def fromDynamic[A, B](
    dynamicMigration: DynamicMigration,
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] = Migration(dynamicMigration, sourceSchema, targetSchema)

  /**
   * Creates a new [[MigrationBuilder]] for constructing a migration from `A` to
   * `B` using the fluent builder API.
   */
  def newBuilder[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    MigrationBuilder[A, B]
}
