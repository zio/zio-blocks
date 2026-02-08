package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

/**
 * A type-safe migration from schema `A` to schema `B`.
 *
 * `Migration[A, B]` wraps a `DynamicMigration` together with the source and
 * target schemas, providing compile-time type safety while delegating to the
 * fully serializable `DynamicMigration` for runtime execution.
 *
 * The `DynamicMigration` is introspectable but `Migration[A, B]` adds schema
 * bindings that make it type-safe.
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Apply migration to transform A to B.
   */
  def apply(value: A): Either[MigrationError, B] =
    migrate(value)

  /**
   * Migrates a value of type `A` to `B`.
   */
  def migrate(value: A): Either[MigrationError, B] = {
    val dynA = sourceSchema.toDynamicValue(value)
    dynamicMigration.migrate(dynA).flatMap { dynB =>
      targetSchema.fromDynamicValue(dynB).left.map { err =>
        MigrationError.TransformFailed(err.message, DynamicOptic.root)
      }
    }
  }

  /**
   * Compose migrations sequentially: `this` then `that`.
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(this.dynamicMigration ++ that.dynamicMigration, this.sourceSchema, that.targetSchema)

  /**
   * Alias for `++`.
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Alias for `++`.
   */
  def >>>[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Structural reverse.
   */
  def reverse: Migration[B, A] =
    Migration(dynamicMigration.reverse, targetSchema, sourceSchema)
}

object Migration {

  /**
   * Create a new migration builder.
   */
  def newBuilder[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sourceSchema, targetSchema, Vector.empty)

  /**
   * Creates an identity migration.
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.identity, schema, schema)

  /**
   * Creates a migration from a `DynamicMigration`.
   */
  def fromDynamic[A, B](dynamic: DynamicMigration)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    Migration(dynamic, sourceSchema, targetSchema)

  // ──────────────── Convenience Constructors ────────────────

  /**
   * Creates a migration that renames a field.
   */
  def renameField[A, B](oldName: String, newName: String)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    Migration(DynamicMigration.renameField(oldName, newName), sourceSchema, targetSchema)

  /**
   * Creates a migration that adds a field with a default value.
   */
  def addField[A, B](name: String, defaultValue: DynamicValue)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    Migration(DynamicMigration.addField(name, defaultValue), sourceSchema, targetSchema)

  /**
   * Creates a migration that removes a field.
   */
  def removeField[A, B](name: String)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    Migration(DynamicMigration.dropField(name), sourceSchema, targetSchema)

  /**
   * Manual construction from a DynamicMigration.
   */
  def manual[A, B](dynamic: DynamicMigration)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    Migration(dynamic, sourceSchema, targetSchema)
}
