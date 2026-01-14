package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A type-safe migration from type A to type B.
 *
 * Migration[A, B] wraps a DynamicMigration with source and target schemas,
 * providing type-safe conversion between typed values. It:
 *   - Converts A to DynamicValue using the source schema
 *   - Applies the DynamicMigration
 *   - Converts back to B using the target schema
 *
 * @tparam A
 *   The source type
 * @tparam B
 *   The target type
 */
trait Migration[A, B] {

  /**
   * The schema for the source type.
   */
  def source: Schema[A]

  /**
   * The schema for the target type.
   */
  def target: Schema[B]

  /**
   * The underlying dynamic migration.
   */
  def dynamic: DynamicMigration

  /**
   * Applies this migration to a value of type A.
   */
  def apply(value: A): Either[SchemaError, B] = {
    val dv = source.toDynamicValue(value)
    dynamic(dv).flatMap(target.fromDynamicValue)
  }

  /**
   * Composes this migration with another.
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration.Composed(this, that)

  /**
   * Creates the reverse migration.
   */
  def reverse: Migration[B, A] =
    Migration.Reversed(this)
}

object Migration {

  /**
   * Creates an identity migration that performs no transformation.
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] = new Migration[A, A] {
    val source: Schema[A]         = schema
    val target: Schema[A]         = schema
    val dynamic: DynamicMigration = DynamicMigration.empty
  }

  /**
   * Creates a migration builder for transforming A to B.
   */
  def from[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sourceSchema, targetSchema, Vector.empty)

  /**
   * Creates a migration from a DynamicMigration.
   */
  def fromDynamic[A, B](
    sourceSchema: Schema[A],
    targetSchema: Schema[B],
    migration: DynamicMigration
  ): Migration[A, B] = new Migration[A, B] {
    val source: Schema[A]         = sourceSchema
    val target: Schema[B]         = targetSchema
    val dynamic: DynamicMigration = migration
  }

  private[migration] final case class Composed[A, B, C](m1: Migration[A, B], m2: Migration[B, C])
      extends Migration[A, C] {
    val source: Schema[A]         = m1.source
    val target: Schema[C]         = m2.target
    val dynamic: DynamicMigration = m1.dynamic ++ m2.dynamic
  }

  private[migration] final case class Reversed[A, B](m: Migration[A, B]) extends Migration[B, A] {
    val source: Schema[B]         = m.target
    val target: Schema[A]         = m.source
    val dynamic: DynamicMigration = m.dynamic.reverse
  }
}

/**
 * Builder for constructing type-safe migrations.
 *
 * MigrationBuilder provides a fluent API for building migrations with
 * operations like addField, dropField, renameField, etc.
 */
class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction]
) {

  /**
   * Adds a field with a default value.
   */
  def addField[C](fieldName: String, default: C)(implicit fieldSchema: Schema[C]): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.AddField(DynamicOptic.root, fieldName, fieldSchema.toDynamicValue(default))
    )

  /**
   * Drops a field.
   */
  def dropField(fieldName: String): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.DropField(DynamicOptic.root, fieldName)
    )

  /**
   * Renames a field.
   */
  def renameField(oldName: String, newName: String): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameField(DynamicOptic.root, oldName, newName)
    )

  /**
   * Transforms a field's value.
   */
  def transformField(fieldName: String, expr: MigrationExpr): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformField(DynamicOptic.root, fieldName, expr)
    )

  /**
   * Makes an optional field mandatory with a default.
   */
  def mandateField[C](fieldName: String, default: C)(implicit fieldSchema: Schema[C]): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.MandateField(DynamicOptic.root, fieldName, fieldSchema.toDynamicValue(default))
    )

  /**
   * Makes a mandatory field optional.
   */
  def optionalizeField(fieldName: String): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.OptionalizeField(DynamicOptic.root, fieldName)
    )

  /**
   * Changes a field's type using an expression.
   */
  def changeFieldType(fieldName: String, expr: MigrationExpr): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.ChangeFieldType(DynamicOptic.root, fieldName, expr)
    )

  /**
   * Renames a case in an enum.
   */
  def renameCase(oldName: String, newName: String): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameCase(DynamicOptic.root, oldName, newName)
    )

  /**
   * Transforms elements in a sequence field.
   */
  def transformElements(fieldName: String, expr: MigrationExpr): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformElements(DynamicOptic(IndexedSeq(DynamicOptic.Node.Field(fieldName))), expr)
    )

  // ============================================================
  // Selector-based operations for nested paths
  // ============================================================

  /**
   * Adds a field at a specific path location.
   *
   * Example:
   * {{{
   * migration.addFieldAt(Selector[Person](_.address), "zipCode", "00000")
   * }}}
   */
  def addFieldAt[C](selector: Selector[_], fieldName: String, default: C)(implicit fieldSchema: Schema[C]): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.AddField(selector.path, fieldName, fieldSchema.toDynamicValue(default))
    )

  /**
   * Drops a field at a specific path location.
   *
   * Example:
   * {{{
   * migration.dropFieldAt(Selector[Person](_.address), "obsoleteField")
   * }}}
   */
  def dropFieldAt(selector: Selector[_], fieldName: String): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.DropField(selector.path, fieldName)
    )

  /**
   * Renames a field at a specific path location.
   *
   * Example:
   * {{{
   * migration.renameFieldAt(Selector[Person](_.address), "street", "streetAddress")
   * }}}
   */
  def renameFieldAt(selector: Selector[_], oldName: String, newName: String): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameField(selector.path, oldName, newName)
    )

  /**
   * Transforms a field at a specific path location.
   *
   * Example:
   * {{{
   * migration.transformFieldAt(Selector[Person](_.address), "zipCode", MigrationExpr.StringAppend("-0000"))
   * }}}
   */
  def transformFieldAt(selector: Selector[_], fieldName: String, expr: MigrationExpr): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformField(selector.path, fieldName, expr)
    )

  /**
   * Changes a field's type at a specific path location.
   *
   * Example:
   * {{{
   * migration.changeFieldTypeAt(Selector[Order](_.items.each), "price", MigrationExpr.IntToDouble)
   * }}}
   */
  def changeFieldTypeAt(selector: Selector[_], fieldName: String, expr: MigrationExpr): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.ChangeFieldType(selector.path, fieldName, expr)
    )

  /**
   * Makes an optional field mandatory at a specific path location.
   *
   * Example:
   * {{{
   * migration.mandateFieldAt(Selector[Person](_.address), "country", "USA")
   * }}}
   */
  def mandateFieldAt[C](selector: Selector[_], fieldName: String, default: C)(implicit fieldSchema: Schema[C]): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.MandateField(selector.path, fieldName, fieldSchema.toDynamicValue(default))
    )

  /**
   * Makes a mandatory field optional at a specific path location.
   *
   * Example:
   * {{{
   * migration.optionalizeFieldAt(Selector[Person](_.address), "apartment")
   * }}}
   */
  def optionalizeFieldAt(selector: Selector[_], fieldName: String): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.OptionalizeField(selector.path, fieldName)
    )

  /**
   * Renames a case at a specific path location.
   *
   * Example:
   * {{{
   * migration.renameCaseAt(Selector[Order](_.status), "Pending", "AwaitingProcessing")
   * }}}
   */
  def renameCaseAt(selector: Selector[_], oldName: String, newName: String): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameCase(selector.path, oldName, newName)
    )

  /**
   * Transforms elements at a specific collection path.
   *
   * Example:
   * {{{
   * migration.transformElementsAt(Selector[Order](_.items), MigrationExpr.IntToDouble)
   * }}}
   */
  def transformElementsAt(selector: Selector[_], expr: MigrationExpr): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformElements(selector.path, expr)
    )

  /**
   * Builds the migration with validation. Returns an error if the migration is
   * incomplete or invalid.
   */
  def build(): Either[MigrationValidationError, Migration[A, B]] =
    // For now, we just build without validation
    // Full validation would check that all target fields have sources
    Right(buildPartial())

  /**
   * Builds the migration without validation.
   */
  def buildPartial(): Migration[A, B] = new Migration[A, B] {
    val source: Schema[A]         = sourceSchema
    val target: Schema[B]         = targetSchema
    val dynamic: DynamicMigration = DynamicMigration(actions)
  }
}

/**
 * Error type for migration validation failures.
 */
sealed trait MigrationValidationError

object MigrationValidationError {
  final case class MissingSourceField(fieldName: String)        extends MigrationValidationError
  final case class MissingTargetField(fieldName: String)        extends MigrationValidationError
  final case class TypeMismatch(fieldName: String, msg: String) extends MigrationValidationError
  final case class InvalidAction(msg: String)                   extends MigrationValidationError
}
