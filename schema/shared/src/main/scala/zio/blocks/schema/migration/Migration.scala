package zio.blocks.schema.migration

import zio.blocks.schema.{ DynamicValue, Schema }

/**
 * A type-safe wrapper for `DynamicMigration` that guarantees compatibility
 * between schema `A` and schema `B`.
 */
trait Migration[A, B] {
  def dynamic: DynamicMigration

  /**
   * Migrates a value of type `A` to `B`.
   */
  def migrate(value: A)(implicit schemaA: Schema[A], schemaB: Schema[B]): Either[String, B]

  /**
   * Composes this migration with another migration.
   */
  final def >>>[C](that: Migration[B, C]): Migration[A, C] =
    Migration(this.dynamic + that.dynamic)
}

object Migration {

  private final case class InterpretedMigration[A, B](dynamic: DynamicMigration) extends Migration[A, B] {
    override def migrate(value: A)(implicit schemaA: Schema[A], schemaB: Schema[B]): Either[String, B] = {
      val dynA = schemaA.toDynamicValue(value)
      dynamic.migrate(dynA).flatMap { dynB =>
        schemaB.fromDynamicValue(dynB).left.map(_.message)
      }
    }
  }

  def apply[A, B](dynamic: DynamicMigration): Migration[A, B] = InterpretedMigration(dynamic)

  /**
   * Creates an identity migration.
   */
  def identity[A]: Migration[A, A] = Migration(DynamicMigration.Identity)

  /**
   * Creates a migration that renames a field.
   * Note: This is a "fragile" constructor because it doesn't verify fields at compile time
   * without macros. Ideally we'd use a macro here to check selectors.
   */
  def renameField[A, B](oldName: String, newName: String): Migration[A, B] =
    Migration(DynamicMigration.RenameField(oldName, newName))

  /**
   * Creates a migration that adds a field with a default value.
   */
  def addField[A, B](name: String, defaultValue: DynamicValue): Migration[A, B] =
    Migration(DynamicMigration.AddClassField(name, defaultValue))

  /**
   * Creates a migration that removes a field.
   */
  def removeField[A, B](name: String): Migration[A, B] =
    Migration(DynamicMigration.RemoveField(name))

  /**
   * Manual construction from a DynamicMigration.
   */
  def manual[A, B](dynamic: DynamicMigration): Migration[A, B] =
    Migration(dynamic)

  /**
   * Optimized migration used by macros.
   */
  final case class OptimizedMigration[A, B](
    override val dynamic: DynamicMigration,
    migrateFn: (A, Schema[A], Schema[B]) => Either[String, B]
  ) extends Migration[A, B] {
    override def migrate(value: A)(implicit schemaA: Schema[A], schemaB: Schema[B]): Either[String, B] =
      migrateFn(value, schemaA, schemaB)
  }
}
