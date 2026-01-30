package zio.blocks.schema

/**
 * A typed, schema-aware migration from type `A` to type `B`.
 *
 * Wraps a `DynamicMigration` with source and target schemas, providing
 * type-safe `apply`, composition (`++`), and structural `reverse`.
 *
 * The underlying `DynamicMigration` is fully serializable (no functions or
 * closures). `Migration[A, B]` adds typed conversion via `Schema[A]` and
 * `Schema[B]`.
 */
final case class Migration[A, B] private[schema] (
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Apply this migration to a value of type `A`, producing `B` or an error.
   *
   * Converts `A` to `DynamicValue`, applies the dynamic migration, then
   * converts the result back to `B`.
   */
  def apply(value: A): Either[MigrationError, B] = {
    val dynamicValue = sourceSchema.toDynamicValue(value)
    dynamicMigration(dynamicValue) match {
      case Left(migrationError) => Left(migrationError)
      case Right(transformed)   =>
        targetSchema.fromDynamicValue(transformed) match {
          case Right(result)     => Right(result)
          case Left(schemaError) =>
            Left(
              MigrationError.TransformFailed(
                DynamicOptic.root,
                s"Schema conversion failed: ${schemaError.message}"
              )
            )
        }
    }
  }

  /**
   * Compose two migrations: `A -> B -> C`.
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(this.dynamicMigration ++ that.dynamicMigration, this.sourceSchema, that.targetSchema)

  /**
   * Alias for `++`.
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * The structural reverse of this migration: `B -> A`.
   */
  def reverse: Migration[B, A] =
    Migration(dynamicMigration.reverse, targetSchema, sourceSchema)

  /**
   * Whether this migration has no actions.
   */
  def isEmpty: Boolean = dynamicMigration.isEmpty
}

object Migration {

  /**
   * Create an identity migration (no transformation).
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.identity, schema, schema)

  /**
   * Start building a migration from `A` to `B`.
   */
  def builder[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sourceSchema, targetSchema, Vector.empty)
}
