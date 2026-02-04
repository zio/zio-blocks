package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, SchemaError}

/**
 * A typed migration that transforms values from type `A` to type `B`.
 *
 * `Migration[A, B]` provides a type-safe wrapper around `DynamicMigration`,
 * handling the conversion between typed values and `DynamicValue`
 * automatically.
 *
 * The migration process:
 *   1. Convert input `A` to `DynamicValue` using `sourceSchema`
 *   2. Apply the `dynamicMigration` to transform the dynamic value
 *   3. Convert the result back to `B` using `targetSchema`
 *
 * The schemas can be any `Schema[A]` and `Schema[B]`, including:
 *   - Regular schemas derived from case classes/enums
 *   - Structural schemas (JVM only) for compile-time-only types
 *
 * This allows migrations between current runtime types and past structural
 * versions without requiring old case classes to exist at runtime.
 *
 * @tparam A
 *   The source type
 * @tparam B
 *   The target type
 * @param sourceSchema
 *   Schema for the source type (can be structural or regular)
 * @param targetSchema
 *   Schema for the target type (can be structural or regular)
 * @param dynamicMigration
 *   The underlying untyped migration
 */
final case class Migration[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  dynamicMigration: DynamicMigration
) {

  /**
   * Apply this migration to transform a value from type `A` to type `B`.
   *
   * @param value
   *   The input value to migrate
   * @return
   *   Either a `SchemaError` or the migrated value
   */
  def apply(value: A): Either[SchemaError, B] = {
    val dynamicValue = sourceSchema.toDynamicValue(value)
    dynamicMigration(dynamicValue).flatMap { result =>
      targetSchema.fromDynamicValue(result)
    }
  }

  /**
   * Compose this migration with another, applying this migration first, then
   * the other.
   *
   * @param that
   *   The migration to apply after this one
   * @return
   *   A new migration that applies both in sequence
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    new Migration(sourceSchema, that.targetSchema, dynamicMigration ++ that.dynamicMigration)

  /**
   * Alias for `++`.
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Returns the structural reverse of this migration.
   *
   * Note: Runtime execution of the reverse migration is best-effort. It may
   * fail if information was lost during the forward migration.
   */
  def reverse: Migration[B, A] =
    new Migration(targetSchema, sourceSchema, dynamicMigration.reverse)

  /**
   * Returns true if this migration has no actions (identity migration).
   */
  def isEmpty: Boolean = dynamicMigration.isEmpty

  /**
   * Returns the number of actions in this migration.
   */
  def size: Int = dynamicMigration.size

  /**
   * Get the list of actions in this migration.
   */
  def actions: Vector[MigrationAction] = dynamicMigration.actions
}

object Migration extends MigrationSelectorSyntax with MigrationCompanionVersionSpecific {

  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    new Migration(schema, schema, DynamicMigration.empty)

  def fromAction[A, B](action: MigrationAction)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    new Migration(sourceSchema, targetSchema, DynamicMigration.single(action))

  def fromActions[A, B](actions: MigrationAction*)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions.toVector))

  def fromDynamic[A, B](dynamicMigration: DynamicMigration)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    new Migration(sourceSchema, targetSchema, dynamicMigration)
}
