package zio.blocks.schema.migration

import zio.blocks.schema.Schema

/**
 * A typed migration that transforms values from schema A to schema B.
 *
 * Migration wraps an untyped [[DynamicMigration]] along with the source and target
 * schemas, providing type-safe application of migrations. It supports composition
 * and reversal for building complex migration pipelines.
 *
 * @param dynamicMigration the underlying untyped migration
 * @param sourceSchema the schema for type A
 * @param targetSchema the schema for type B
 * @tparam A the source type
 * @tparam B the target type
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Applies this migration to transform a value of type A to type B.
   *
   * @param value the input value
   * @return either a migration error or the transformed value
   */
  def apply(value: A): Either[MigrationError, B] = {
    val dynamicValue = sourceSchema.toDynamicValue(value)
    dynamicMigration(dynamicValue).flatMap { migratedValue =>
      targetSchema.fromDynamicValue(migratedValue).left.map(e => MigrationError.ValidationError(e.message))
    }
  }

  /**
   * Composes this migration with another migration.
   *
   * The resulting migration first applies this migration, then applies `that`.
   *
   * @param that the migration to apply after this one
   * @tparam C the target type of the second migration
   * @return the composed migration from A to C
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] = Migration(
    this.dynamicMigration ++ that.dynamicMigration,
    this.sourceSchema,
    that.targetSchema
  )

  /**
   * Alias for `++`.
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Returns the reverse of this migration.
   *
   * The reverse migration transforms values from B back to A.
   */
  def reverse: Migration[B, A] = Migration(
    dynamicMigration.reverse,
    targetSchema,
    sourceSchema
  )

  /**
   * Returns the actions in this migration for introspection.
   */
  def actions: Vector[MigrationAction] = dynamicMigration.actions
}

object Migration {

  /**
   * Creates a new migration builder for the given source and target types.
   *
   * @param source the source schema
   * @param target the target schema
   * @tparam A the source type
   * @tparam B the target type
   * @return a new migration builder
   */
  def newBuilder[A, B](implicit source: Schema[A], target: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(source, target, Vector.empty)

  /**
   * Creates an identity migration for the given type.
   *
   * An identity migration returns the input unchanged.
   *
   * @param schema the schema for type A
   * @tparam A the type
   * @return an identity migration
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.identity, schema, schema)

  /**
   * Creates a migration from a DynamicMigration and schemas.
   *
   * This is an advanced method for creating migrations programmatically.
   * Most users should use [[newBuilder]] instead.
   */
  def fromDynamic[A, B](
    dynamicMigration: DynamicMigration,
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    Migration(dynamicMigration, sourceSchema, targetSchema)
}
