package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * A typed, compile-time validated migration from schema A to schema B.
 *
 * Migration wraps a [[DynamicMigration]] with source and target schemas,
 * providing type-safe application and composition. The schemas are used for
 * converting typed values to/from DynamicValue.
 *
 * Migrations are typically constructed using [[MigrationBuilder]], which
 * ensures at compile time that all fields are properly mapped:
 *
 * {{{
 * // Define schemas
 * case class PersonV0(firstName: String, lastName: String)
 * case class PersonV1(fullName: String, age: Int)
 *
 * // Build migration with compile-time safety
 * val migration = Migration.newBuilder[PersonV0, PersonV1]
 *   .addField(select(_.age), 0)
 *   .transformField(...)
 *   .build  // Only compiles if all fields are handled
 *
 * // Apply migration
 * val result: Either[MigrationError, PersonV1] = migration(oldPerson)
 * }}}
 *
 * @tparam A
 *   Source type (may be a structural type for old versions)
 * @tparam B
 *   Target type (may be a structural type or concrete class)
 * @param dynamicMigration
 *   The underlying serializable migration
 * @param sourceSchema
 *   Schema for the source type
 * @param targetSchema
 *   Schema for the target type
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) { self =>

  /**
   * Apply this migration to transform a value of type A to type B.
   *
   * The transformation process:
   *   1. Convert the input value to DynamicValue using sourceSchema
   *   2. Apply the DynamicMigration
   *   3. Convert the result back to type B using targetSchema
   *
   * @param value
   *   The source value to migrate
   * @return
   *   Right with the migrated value, or Left with a MigrationError
   */
  def apply(value: A): Either[MigrationError, B] = {
    val dynamicSource = sourceSchema.toDynamicValue(value)

    dynamicMigration.apply(dynamicSource).flatMap { dynamicTarget =>
      targetSchema.fromDynamicValue(dynamicTarget) match {
        case Right(result) => Right(result)
        case Left(error)   => Left(MigrationError.ConversionFailed(DynamicOptic.root, error.toString))
      }
    }
  }

  /**
   * Alias for [[apply]].
   */
  def migrate(value: A): Either[MigrationError, B] = apply(value)

  /**
   * Apply this migration, throwing an exception on failure.
   *
   * Use `apply` for production code; this is provided for convenience in tests
   * and scripts.
   *
   * @throws MigrationError
   *   if the migration fails
   */
  def unsafeApply(value: A): B =
    apply(value) match {
      case Right(result) => result
      case Left(error)   => throw error
    }

  /**
   * Compose this migration with another: A -> B -> C
   *
   * The resulting migration first applies this migration, then applies the
   * other migration to the result.
   *
   * This operation satisfies associativity:
   * `(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)`
   *
   * @param that
   *   The migration to apply after this one
   * @return
   *   A composed migration from A to C
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      dynamicMigration ++ that.dynamicMigration,
      sourceSchema,
      that.targetSchema
    )

  /**
   * Alias for ++
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Structural reverse of this migration: B -> A
   *
   * The reverse migration applies the reversed DynamicMigration and swaps the
   * source and target schemas.
   *
   * Note: Runtime behavior of the reverse may differ from a true inverse if
   * information was lost during the forward migration (e.g., dropped fields).
   * The reverse is "best effort" - it will succeed if sufficient information
   * exists to reconstruct the original value.
   *
   * This operation satisfies: `m.reverse.reverse == m` (structurally)
   *
   * @return
   *   A migration that reverses this one
   */
  def reverse: Migration[B, A] =
    Migration(
      dynamicMigration.reverse,
      targetSchema,
      sourceSchema
    )

  /**
   * Check if this is the identity migration (no transformation).
   */
  def isIdentity: Boolean = dynamicMigration.isIdentity

  /**
   * Get the number of actions in this migration.
   */
  def actionCount: Int = dynamicMigration.actionCount

  /**
   * Optimize this migration by combining or eliminating redundant actions.
   *
   * @return
   *   An optimized migration with equivalent behavior
   */
  def optimize: Migration[A, B] =
    Migration(dynamicMigration.optimize, sourceSchema, targetSchema)

  /**
   * Get a human-readable description of this migration.
   */
  def describe: String = dynamicMigration.describe
}

object Migration {

  /**
   * Create an identity migration that performs no transformation.
   *
   * For any value v: `identity[A].apply(v) == Right(v)`
   *
   * @param schema
   *   The schema for type A
   * @return
   *   An identity migration for type A
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.identity, schema, schema)

  /**
   * Create a migration directly from a DynamicMigration and schemas.
   *
   * This bypasses compile-time validation. Prefer using [[MigrationBuilder]]
   * for type-safe migration construction.
   *
   * @param dynamicMigration
   *   The underlying migration
   * @param sourceSchema
   *   Schema for the source type
   * @param targetSchema
   *   Schema for the target type
   * @return
   *   A typed Migration wrapping the DynamicMigration
   */
  def fromDynamic[A, B](
    dynamicMigration: DynamicMigration
  )(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    Migration(dynamicMigration, sourceSchema, targetSchema)

  // Note: newBuilder methods are defined in MigrationBuilder companion object
  // since they require platform-specific macro implementations and type-level
  // field tracking that differs between Scala 2 and Scala 3.
  //
  // Usage:
  //   import zio.blocks.schema.migration._
  //   val builder = MigrationBuilder.create[A, B]
  //     .addField(select(_.newField), defaultValue)
  //     .renameField(select(_.old), select(_.new))
  //     .build
}
