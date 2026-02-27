package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, Schema}

/**
 * A typed, schema-validated migration from values of type `A` to values of type
 * `B`.
 *
 * `Migration[A, B]` wraps a [[DynamicMigration]] (the untyped, serializable
 * core) together with the source and target schemas, providing compile-time type
 * safety while keeping the migration data fully serializable.
 *
 * Laws:
 *   - '''Identity:''' `Migration.identity[A].apply(a) == Right(a)`
 *   - '''Associativity:''' `(m1 ++ m2) ++ m3` behaves identically to
 *     `m1 ++ (m2 ++ m3)`
 *   - '''Structural Reverse:''' `m.reverse.reverse.dynamicMigration == m.dynamicMigration`
 *   - '''Best-Effort Semantic Inverse:''' If `m.apply(a) == Right(b)`, then
 *     `m.reverse.apply(b) == Right(a)` (best-effort)
 *
 * @param dynamicMigration
 *   the underlying serializable migration
 * @param sourceSchema
 *   the schema for the source type `A`
 * @param targetSchema
 *   the schema for the target type `B`
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Applies this migration to a value of type `A`, producing a value of type
   * `B`.
   */
  def apply(value: A): Either[MigrationError, B] = {
    val dv = sourceSchema.toDynamicValue(value)
    dynamicMigration(dv) match {
      case Right(result) =>
        targetSchema.fromDynamicValue(result) match {
          case Right(b)  => Right(b)
          case Left(err) =>
            Left(MigrationError.General(
              zio.blocks.schema.DynamicOptic.root,
              s"Failed to convert migrated value to target type: ${err.message}"
            ))
        }
      case Left(err) => Left(err)
    }
  }

  /**
   * Applies this migration to a [[DynamicValue]] directly.
   */
  def applyDynamic(value: DynamicValue): Either[MigrationError, DynamicValue] =
    dynamicMigration(value)

  /**
   * Returns the structural reverse of this migration.
   */
  def reverse: Migration[B, A] =
    new Migration(dynamicMigration.reverse, targetSchema, sourceSchema)

  /**
   * Composes this migration with another migration, creating a migration from
   * `A` to `C`.
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    new Migration(this.dynamicMigration ++ that.dynamicMigration, this.sourceSchema, that.targetSchema)
}

object Migration {

  /**
   * Creates an identity migration that transforms a value to itself.
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    new Migration(DynamicMigration.identity, schema, schema)

  /**
   * Creates a new [[MigrationBuilder]] for constructing a migration from `A` to
   * `B`.
   */
  def newBuilder[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](Vector.empty, sourceSchema, targetSchema)
}
