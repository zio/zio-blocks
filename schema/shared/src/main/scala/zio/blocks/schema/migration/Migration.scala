package zio.blocks.schema.migration

import zio.blocks.schema.Schema

/**
 * Represents a typed, macro-validated migration that transforms data from a source schema `A`
 * to a target schema `B`.
 *
 * A `Migration` instance encapsulates a `DynamicMigration` (the untyped, serializable core)
 * along with the `Schema` instances for the source and target types. This allows for type-safe
 * application and composition of migration steps.
 *
 * @param dynamicMigration The underlying untyped, serializable migration logic.
 * @param sourceSchema The schema for the source type `A`.
 * @param targetSchema The schema for the target type `B`.
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {
  /**
   * Applies this migration to a value of type `A`, transforming it into a value of type `B`.
   * The process involves converting `A` to a `DynamicValue`, applying the `DynamicMigration`,
   * and then converting the resulting `DynamicValue` back to `B`.
   *
   * @param value The input value of type `A`.
   * @return An `Either[MigrationError, B]` representing the transformed value on success,
   *         or a `MigrationError` if the migration or conversion fails.
   */
  def apply(value: A): Either[MigrationError, B] =
    for {
      dynamicValue <- Right(sourceSchema.toDynamicValue(value))
      migrated     <- dynamicMigration(dynamicValue)
      result       <- targetSchema.fromDynamicValue(migrated).left.map(err => MigrationError.ConversionError(err.toString))
    } yield result

  /**
   * Composes this migration with another migration sequentially.
   * The resulting migration transforms data from this migration's source type `A`
   * to the `that` migration's target type `C`, via the intermediate type `B`.
   *
   * @param that The subsequent migration to compose with.
   * @tparam C The target type of the composed migration.
   * @return A new `Migration[A, C]` instance representing the sequential composition.
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      dynamicMigration ++ that.dynamicMigration,
      sourceSchema,
      that.targetSchema
    )

  /**
   * An alias for `andThen`.
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] = andThen(that)

  /**
   * Returns the structural inverse of this migration.
   * The reversed migration transforms data from `B` back to `A`.
   *
   * Note: The semantic inverse is best-effort; not all migrations are perfectly reversible
   * (e.g., dropping a field loses information that cannot be recovered).
   *
   * @return A new `Migration[B, A]` instance representing the reversed migration.
   */
  def reverse: Migration[B, A] =
    Migration(
      dynamicMigration.reverse,
      targetSchema,
      sourceSchema
    )
}

/**
 * Companion object for the `Migration` class, providing factory methods.
 */
object Migration {
  /**
   * Creates a new `MigrationBuilder` instance to construct a migration.
   * This is the entry point for defining new schema migrations using a fluent DSL.
   *
   * @param sourceSchema The implicit schema for the source type `A`.
   * @param targetSchema The implicit schema for the target type `B`.
   * @tparam A The source type of the migration.
   * @tparam B The target type of the migration.
   * @return A new `MigrationBuilder[A, B]` instance.
   */
    def newBuilder[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
        MigrationBuilder(sourceSchema, targetSchema)
}
