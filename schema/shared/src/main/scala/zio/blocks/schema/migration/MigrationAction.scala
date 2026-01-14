package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A serializable action representing a single schema migration operation.
 *
 * MigrationActions are fully serializable data structures that describe
 * transformations to be applied to DynamicValue instances. They support:
 *   - Record operations (add, drop, rename, transform fields)
 *   - Enum/variant operations (rename, transform cases)
 *   - Collection operations (transform elements, keys, values)
 */
sealed trait MigrationAction

object MigrationAction {

  // Record field operations

  /**
   * Adds a new field to a record with a default value.
   */
  final case class AddField(
    path: DynamicOptic,
    fieldName: String,
    defaultValue: DynamicValue
  ) extends MigrationAction

  /**
   * Drops a field from a record.
   */
  final case class DropField(
    path: DynamicOptic,
    fieldName: String
  ) extends MigrationAction

  /**
   * Renames a field in a record.
   */
  final case class RenameField(
    path: DynamicOptic,
    oldName: String,
    newName: String
  ) extends MigrationAction

  /**
   * Transforms a field's value using an expression.
   */
  final case class TransformField(
    path: DynamicOptic,
    fieldName: String,
    expr: MigrationExpr
  ) extends MigrationAction

  /**
   * Makes an optional field mandatory with a default for None values.
   */
  final case class MandateField(
    path: DynamicOptic,
    fieldName: String,
    defaultValue: DynamicValue
  ) extends MigrationAction

  /**
   * Makes a mandatory field optional (wraps in Some).
   */
  final case class OptionalizeField(
    path: DynamicOptic,
    fieldName: String
  ) extends MigrationAction

  /**
   * Changes a field's type using an expression.
   */
  final case class ChangeFieldType(
    path: DynamicOptic,
    fieldName: String,
    expr: MigrationExpr
  ) extends MigrationAction

  /**
   * Joins multiple fields into a single field.
   */
  final case class JoinFields(
    path: DynamicOptic,
    sourceFields: Vector[String],
    targetField: String,
    expr: MigrationExpr
  ) extends MigrationAction

  /**
   * Splits a field into multiple fields.
   */
  final case class SplitField(
    path: DynamicOptic,
    sourceField: String,
    targetFields: Vector[(String, MigrationExpr)]
  ) extends MigrationAction

  // Enum/variant operations

  /**
   * Renames a case in an enum/sealed trait.
   */
  final case class RenameCase(
    path: DynamicOptic,
    oldName: String,
    newName: String
  ) extends MigrationAction

  /**
   * Transforms a case's payload using an expression.
   */
  final case class TransformCase(
    path: DynamicOptic,
    caseName: String,
    expr: MigrationExpr
  ) extends MigrationAction

  // Collection operations

  /**
   * Transforms elements in a sequence.
   */
  final case class TransformElements(
    path: DynamicOptic,
    expr: MigrationExpr
  ) extends MigrationAction

  /**
   * Transforms keys in a map.
   */
  final case class TransformKeys(
    path: DynamicOptic,
    expr: MigrationExpr
  ) extends MigrationAction

  /**
   * Transforms values in a map.
   */
  final case class TransformValues(
    path: DynamicOptic,
    expr: MigrationExpr
  ) extends MigrationAction

  /**
   * Computes the structural reverse of an action, if possible. Returns empty
   * vector if the action cannot be reversed.
   */
  def reverseAction(action: MigrationAction): Vector[MigrationAction] = action match {
    case AddField(path, name, _) =>
      Vector(DropField(path, name))
    case DropField(_, _) =>
      Vector.empty // Cannot reverse without original value
    case RenameField(path, old, newName) =>
      Vector(RenameField(path, newName, old))
    case RenameCase(path, old, newName) =>
      Vector(RenameCase(path, newName, old))
    case OptionalizeField(_, _) =>
      Vector.empty // Cannot reverse without default
    case _ =>
      Vector.empty // Other operations may not be reversible
  }
}
