package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr}

/**
 * Represents a single migration action that transforms data at a specific path.
 * All actions operate on a path represented by DynamicOptic.
 */
sealed trait MigrationAction { self =>

  /**
   * The path at which this action operates.
   */
  def at: DynamicOptic

  /**
   * Creates the reverse action for this migration.
   * Some actions may not be perfectly reversible.
   */
  def reverse: MigrationAction

  /**
   * Composes this action with another action, returning a sequence of actions.
   */
  def ++(that: MigrationAction): Vector[MigrationAction] =
    Vector(self, that)
}

object MigrationAction {

  /**
   * Adds a new field at the specified path with a default value.
   */
  final case class AddField(
    at: DynamicOptic,
    default: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, default) // Use same default for reverse
  }

  /**
   * Drops a field at the specified path.
   */
  final case class DropField(
    at: DynamicOptic,
    defaultForReverse: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  /**
   * Renames a field at the specified path.
   */
  final case class Rename(
    at: DynamicOptic,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = Rename(at, to) // Reversal handled specially
  }

  /**
   * Transforms a field value using a SchemaExpr.
   */
  final case class TransformValue(
    at: DynamicOptic,
    transform: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, transform) // May not be reversible
  }

  /**
   * Makes a field mandatory at the specified path, providing a default value.
   */
  final case class Mandate(
    at: DynamicOptic,
    default: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }

  /**
   * Converts a mandatory field to optional.
   */
  final case class Optionalize(
    at: DynamicOptic
  ) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at) // Cannot restore original value
  }

  /**
   * Joins multiple source paths into a single target path.
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = Join(at, sourcePaths, combiner) // Not easily reversible
  }

  /**
   * Splits a source path into multiple target paths.
   */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = Split(at, targetPaths, splitter) // Not easily reversible
  }

  /**
   * Changes the type of a field using a converter.
   */
  final case class ChangeType(
    at: DynamicOptic,
    converter: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(at, converter) // May not be reversible
  }

  // Enum Actions

  /**
   * Renames a case in a sum type.
   */
  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /**
   * Transforms a case in a sum type using nested migration actions.
   */
  final case class TransformCase(
    at: DynamicOptic,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, actions.map(_.reverse))
  }

  // Collection / Map Actions

  /**
   * Transforms each element in a sequence.
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, transform)
  }

  /**
   * Transforms keys in a map.
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, transform)
  }

  /**
   * Transforms values in a map.
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, transform)
  }

  // Identity action - no transformation
  final case class Identity(
    at: DynamicOptic = DynamicOptic(IndexedSeq.empty)
  ) extends MigrationAction {
    def reverse: MigrationAction = Identity(at)
  }
}
