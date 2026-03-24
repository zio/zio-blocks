package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicSchemaExpr}

/**
 * Represents a single migration action that operates at a specific path. All
 * actions are fully serializable (no closures or functions) and support
 * structural reversal.
 */
sealed trait MigrationAction {

  /**
   * The path at which this action operates.
   */
  def at: DynamicOptic

  /**
   * Returns the structural inverse of this action. Note: Runtime reversal is
   * best-effort and may fail if information was lost during the forward
   * migration.
   */
  def reverse: MigrationAction
}

object MigrationAction {

  // ==================== Record Actions ====================

  /**
   * Add a new field to a record with a default value. The field name is the
   * last component of the `at` path.
   *
   * @param at
   *   The path to the field to add (must end with a Field node)
   * @param default
   *   The default value expression for the new field
   */
  final case class AddField(
    at: DynamicOptic,
    default: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = DropField(at, default)

    /** The field name, extracted from the path */
    def fieldName: Option[String] = at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => Some(name)
      case _                                   => None
    }
  }

  /**
   * Drop a field from a record. The field name is the last component of the
   * `at` path.
   *
   * @param at
   *   The path to the field to drop (must end with a Field node)
   * @param defaultForReverse
   *   Default value expression to use when reversing (adding the field back)
   */
  final case class DropField(
    at: DynamicOptic,
    defaultForReverse: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = AddField(at, defaultForReverse)

    /** The field name, extracted from the path */
    def fieldName: Option[String] = at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => Some(name)
      case _                                   => None
    }
  }

  /**
   * Rename a field in a record.
   *
   * @param at
   *   The path to the field to rename (must end with a Field node)
   * @param to
   *   The new field name
   */
  final case class RenameField(
    at: DynamicOptic,
    to: String
  ) extends MigrationAction {
    override def reverse: MigrationAction = {
      // Extract the old field name from the path and create reverse
      val parentPath = DynamicOptic(at.nodes.dropRight(1))
      from match {
        case Some(oldName) => RenameField(parentPath.field(to), oldName)
        case None          => Irreversible(at, "RenameField")
      }
    }

    /** The original field name, extracted from the path */
    def from: Option[String] = at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => Some(name)
      case _                                   => None
    }
  }

  /**
   * Transform a value at a path using a pure expression.
   *
   * @param at
   *   The path to the value to transform
   * @param transform
   *   The transformation expression
   */
  final case class TransformField(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "TransformField")
  }

  /**
   * Convert an optional field to a required field.
   *
   * @param at
   *   The path to the optional value
   * @param default
   *   The default value expression to use if the optional is None
   */
  final case class MandateField(
    at: DynamicOptic,
    default: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = OptionalizeField(at)
  }

  /**
   * Convert a required field to an optional field.
   *
   * @param at
   *   The path to the required value
   */
  final case class OptionalizeField(
    at: DynamicOptic
  ) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "OptionalizeField")
  }

  /**
   * Join multiple fields into a single field.
   *
   * @param at
   *   The path to the target location for the joined value
   * @param sourcePaths
   *   The paths to the source values to join
   * @param combiner
   *   The combiner expression
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "Join")
  }

  /**
   * Split a single field into multiple fields.
   *
   * @param at
   *   The path to the source value to split
   * @param targetPaths
   *   The paths to the target locations
   * @param splitter
   *   The splitter expression
   */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "Split")
  }

  /**
   * Change the type of a value at a path (primitive-to-primitive only).
   *
   * @param at
   *   The path to the value
   * @param converter
   *   The conversion expression
   */
  final case class ChangeFieldType(
    at: DynamicOptic,
    converter: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "ChangeFieldType")
  }

  // ==================== Enum Actions ====================

  /**
   * Rename an enum case.
   *
   * @param at
   *   The path to the enum value
   * @param from
   *   The current case name
   * @param to
   *   The new case name
   */
  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    override def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /**
   * Transform the fields within an enum case.
   *
   * @param at
   *   The path to the enum value
   * @param actions
   *   The actions to apply to the case's record
   */
  final case class TransformCase(
    at: DynamicOptic,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      TransformCase(at, actions.reverse.map(_.reverse))
  }

  /**
   * Apply an existing migration to a nested field. This is used for migration
   * composition where a pre-built Migration is applied to a field.
   *
   * @param at
   *   The path to the nested field
   * @param migration
   *   The DynamicMigration to apply to the nested value
   */
  final case class MigrateField(
    at: DynamicOptic,
    migration: DynamicMigration
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      MigrateField(at, migration.reverse)
  }

  // ==================== Collection Actions ====================

  /**
   * Replace each element in a collection with the result of evaluating the
   * expression. The expression is evaluated once against the root input value,
   * and every element is replaced with the same result.
   *
   * @param at
   *   The path to the collection
   * @param transform
   *   The expression whose result replaces each element
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "TransformElements")
  }

  // ==================== Map Actions ====================

  /**
   * Replace each key in a map with the result of evaluating the expression. The
   * expression is evaluated once against the root input value, and every key is
   * replaced with the same result.
   *
   * @param at
   *   The path to the map
   * @param transform
   *   The expression whose result replaces each key
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "TransformKeys")
  }

  /**
   * Replace each value in a map with the result of evaluating the expression.
   * The expression is evaluated once against the root input value, and every
   * value is replaced with the same result.
   *
   * @param at
   *   The path to the map
   * @param transform
   *   The expression whose result replaces each value
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "TransformValues")
  }

  /**
   * Sentinel action representing a non-invertible operation. Executing this
   * action always fails with a descriptive error. This is returned by
   * `.reverse` on actions that cannot be structurally reversed (e.g.,
   * `TransformValue`, `ChangeType`).
   *
   * @param at
   *   The path where the original action operated
   * @param originalAction
   *   The name of the original non-invertible action
   */
  final case class Irreversible(
    at: DynamicOptic,
    originalAction: String
  ) extends MigrationAction {
    override def reverse: MigrationAction = this
  }
}
