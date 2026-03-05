package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A typed migration from type A to type B.
 * 
 * Migration[A, B] is a type-safe wrapper around DynamicMigration that carries
 * the source and target schemas. This enables:
 * 
 *   - Type-safe application: `migration.apply(a: A): Either[MigrationError, B]`
 *   - Schema validation: ensure migrations are structurally compatible
 *   - Composition: `m1 ++ m2` produces `Migration[A, C]` from `Migration[A, B]` and `Migration[B, C]`
 * 
 * ==Example Usage==
 * 
 * {{{
 * // Define schemas
 * case class UserV1(name: String, age: Int)
 * case class UserV2(fullName: String, age: Int, email: String)
 * 
 * implicit val schemaV1: Schema[UserV1] = Schema.derive
 * implicit val schemaV2: Schema[UserV2] = Schema.derive
 * 
 * // Create migration
 * val migration: Migration[UserV1, UserV2] = Migration
 *   .builder[UserV1, UserV2]
 *   .renameField(_.name, "fullName")
 *   .addField("email", SchemaExpr.Literal("", Schema[String]))
 *   .build
 * 
 * // Apply migration
 * val v1 = UserV1("Alice", 30)
 * val result: Either[MigrationError, UserV2] = migration(v1)
 * }}}
 * 
 * ==Laws==
 * 
 *   - Identity: Migration.identity[A].apply(a) == Right(a)
 *   - Associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
 *   - Schema Preservation: m.targetSchema == m.reverse.sourceSchema
 */
final case class Migration[-A, +B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Apply this migration to a value of type A.
   * 
   * @param value The value to migrate
   * @return Either a migration error or the migrated value of type B
   */
  def apply(value: A): Either[MigrationError, B] = {
    val dynamicValue = sourceSchema.toDynamicValue(value)
    dynamicMigration.apply(dynamicValue) match {
      case Right(migrated) =>
        targetSchema.fromDynamicValue(migrated) match {
          case Right(result) => Right(result)
          case Left(error) => Left(MigrationError.transformFailed(
            s"Failed to convert migrated value to target type: $error"
          ))
        }
      case Left(error) => Left(error)
    }
  }

  /**
   * Compose this migration with another migration.
   * 
   * The resulting migration applies this migration first, then the other.
   * Type parameter B must match the other migration's input type.
   * 
   * This operation is associative.
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      dynamicMigration ++ that.dynamicMigration,
      sourceSchema,
      that.targetSchema
    )

  /**
   * Returns the reverse of this migration.
   * 
   * The reverse migrates from B back to A. Note that some operations
   * (like TransformValue) may have identity reverses.
   */
  def reverse: Migration[B, A] =
    Migration(
      dynamicMigration.reverse,
      targetSchema,
      sourceSchema
    )

  /**
   * Returns true if this migration has no operations.
   */
  def isEmpty: Boolean = dynamicMigration.isEmpty

  /**
   * Convert to a DynamicMigration (losing type information).
   */
  def toDynamic: DynamicMigration = dynamicMigration

  override def toString: String = {
    s"Migration[${sourceSchema.reflect.typeId.name} => ${targetSchema.reflect.typeId.name}](\n  $dynamicMigration\n)"
  }
}

object Migration {

  /**
   * Create an identity migration for a given schema.
   * 
   * Identity migrations apply no transformations and always succeed.
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.empty, schema, schema)

  /**
   * Create a migration from a DynamicMigration with explicit schemas.
   */
  def apply[A, B](
    dynamicMigration: DynamicMigration,
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    new Migration(dynamicMigration, sourceSchema, targetSchema)

  /**
   * Create a migration from a sequence of actions with explicit schemas.
   */
  def apply[A, B](
    actions: Seq[MigrationAction],
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    new Migration(DynamicMigration(actions.toVector), sourceSchema, targetSchema)

  /**
   * Create a migration builder for constructing migrations type-safely.
   */
  def builder[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    MigrationBuilder[A, B](sourceSchema, targetSchema)

  /**
   * Lift a function into a migration.
   * 
   * Note: This creates a migration that cannot be serialized because it
   * contains a function. Use only for dynamic migrations.
   */
  def fromFunction[A, B](f: A => B)(implicit sourceSchema: Schema[A], targetSchema: Schema[B]): Migration[A, B] = {
    // This is a placeholder - a full implementation would need to represent
    // the function as a SchemaExpr if possible
    Migration(DynamicMigration.empty, sourceSchema, targetSchema)
  }
}
