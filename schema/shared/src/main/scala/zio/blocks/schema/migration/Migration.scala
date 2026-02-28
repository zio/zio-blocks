package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

/**
 * A typed, schema-aware migration that transforms values of type A to values of
 * type B.
 *
 * `Migration[A, B]` wraps a [[DynamicMigration]] with source and target
 * schemas, enabling type-safe application and schema validation. The schemas
 * describe the structural types of the source and target versions.
 *
 * Migrations can be composed sequentially using `++` / `andThen` and reversed
 * using `reverse`.
 *
 * {{{
 * type PersonV0 = { val firstName: String; val lastName: String }
 *
 * case class Person(fullName: String, age: Int)
 *
 * val migration: Migration[PersonV0, Person] = ...
 *
 * migration(oldPerson) // Right(newPerson)
 * }}}
 *
 * @param dynamicMigration
 *   the untyped migration actions
 * @param sourceSchema
 *   schema for the source type
 * @param targetSchema
 *   schema for the target type
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Apply this migration to transform a value of type A to type B.
   *
   * The value is first converted to [[DynamicValue]], then the migration
   * actions are applied, and finally the result is converted back to type B
   * using the target schema.
   */
  def apply(value: A): Either[MigrationError, B] = {
    val dynamicInput = sourceSchema.toDynamicValue(value)
    dynamicMigration(dynamicInput).flatMap { dynamicOutput =>
      targetSchema.fromDynamicValue(dynamicOutput) match {
        case Right(result) => Right(result)
        case Left(err)     =>
          Left(MigrationError.Failed(DynamicOptic.root, s"Schema conversion failed: ${err.message}"))
      }
    }
  }

  /**
   * Apply this migration at the DynamicValue level (skip the typed conversion).
   */
  def applyDynamic(value: DynamicValue): Either[MigrationError, DynamicValue] =
    dynamicMigration(value)

  /** Compose two migrations sequentially: A -> B -> C. */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(this.dynamicMigration ++ that.dynamicMigration, this.sourceSchema, that.targetSchema)

  /** Alias for `++`. */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Reverse this migration (structural inverse).
   *
   * The reversed migration transforms B back to A. This is a best-effort
   * semantic inverse: when sufficient information exists (e.g., defaults for
   * dropped fields), the reverse migration can recover the original value.
   */
  def reverse: Migration[B, A] =
    Migration(dynamicMigration.reverse, targetSchema, sourceSchema)

  /** Check if this migration has no actions (identity). */
  def isEmpty: Boolean = dynamicMigration.isEmpty
}

object Migration {

  /**
   * Create an identity migration that passes values through unchanged.
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.empty, schema, schema)

  /**
   * Create a new [[MigrationBuilder]] for constructing a migration from A to B.
   */
  def newBuilder[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}
