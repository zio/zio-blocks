package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicSchemaExpr, DynamicValue}

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
    def fieldName: String = at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _                                   => throw new IllegalStateException("AddField path must end with a Field node")
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
    def fieldName: String = at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _                                   => throw new IllegalStateException("DropField path must end with a Field node")
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
  final case class Rename(
    at: DynamicOptic,
    to: String
  ) extends MigrationAction {
    override def reverse: MigrationAction = {
      // Extract the old field name from the path and create reverse
      val parentPath = DynamicOptic(at.nodes.dropRight(1))
      Rename(parentPath.field(to), from)
    }

    /** The original field name, extracted from the path */
    def from: String = at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _                                   => throw new IllegalStateException("Rename path must end with a Field node")
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
  final case class TransformValue(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = TransformValue(at, transform) // Note: true reverse needs inverse function
  }

  /**
   * Convert an optional field to a required field.
   *
   * @param at
   *   The path to the optional value
   * @param default
   *   The default value expression to use if the optional is None
   */
  final case class Mandate(
    at: DynamicOptic,
    default: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = Optionalize(at)
  }

  /**
   * Convert a required field to an optional field.
   *
   * @param at
   *   The path to the required value
   */
  final case class Optionalize(
    at: DynamicOptic
  ) extends MigrationAction {
    override def reverse: MigrationAction = Mandate(
      at,
      DynamicSchemaExpr.Literal(DynamicValue.Record.empty)
    )
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
    override def reverse: MigrationAction = Split(at, sourcePaths, combiner) // Note: true reverse needs inverse
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
    override def reverse: MigrationAction = Join(at, targetPaths, splitter) // Note: true reverse needs inverse
  }

  /**
   * Change the type of a value at a path (primitive-to-primitive only).
   *
   * @param at
   *   The path to the value
   * @param converter
   *   The conversion expression
   */
  final case class ChangeType(
    at: DynamicOptic,
    converter: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = ChangeType(at, converter) // Note: true reverse needs inverse
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
   * Transform a nested record field with its own migration.
   *
   * @param at
   *   The path to the nested field
   * @param actions
   *   The actions to apply to the nested record
   */
  final case class TransformNested(
    at: DynamicOptic,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      TransformNested(at, actions.reverse.map(_.reverse))
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
  final case class ApplyMigration(
    at: DynamicOptic,
    migration: DynamicMigration
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      ApplyMigration(at, migration.reverse)
  }

  // ==================== Collection Actions ====================

  /**
   * Transform each element in a collection.
   *
   * @param at
   *   The path to the collection
   * @param transform
   *   The transformation expression to apply to each element
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = TransformElements(at, transform) // Note: true reverse needs inverse
  }

  // ==================== Map Actions ====================

  /**
   * Transform each key in a map.
   *
   * @param at
   *   The path to the map
   * @param transform
   *   The transformation expression to apply to each key
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = TransformKeys(at, transform) // Note: true reverse needs inverse
  }

  /**
   * Transform each value in a map.
   *
   * @param at
   *   The path to the map
   * @param transform
   *   The transformation expression to apply to each value
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = TransformValues(at, transform) // Note: true reverse needs inverse
  }
}
