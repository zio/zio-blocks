package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.DynamicValue

/**
 * A serializable, path-based migration action that transforms
 * [[zio.blocks.schema.DynamicValue]] structures between schema versions.
 *
 * Every action carries a [[DynamicOptic]] path specifying where in the
 * structure the transformation applies, enabling nested record, variant, and
 * collection migrations.
 */
sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {

  // ── Record Actions ──────────────────────────────────────────────────

  /** Inserts a new field at the given path with a default value. */
  final case class AddField(at: DynamicOptic, defaultValue: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, defaultValue)
  }

  /** Removes the field at the given path. */
  final case class DropField(at: DynamicOptic, defaultForReverse: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  /** Renames the field addressed by `at` to `to`. */
  final case class Rename(at: DynamicOptic, to: String) extends MigrationAction {
    def reverse: MigrationAction = {
      val nodes    = at.nodes
      val lastNode = nodes.last
      lastNode match {
        case DynamicOptic.Node.Field(oldName) =>
          val parentNodes = nodes.dropRight(1)
          new Rename(new DynamicOptic(parentNodes.appended(DynamicOptic.Node.Field(to))), oldName)
        case _ => this
      }
    }
  }

  /** Replaces the value at the given path with `newValue`. */
  final case class TransformValue(at: DynamicOptic, newValue: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  /**
   * Converts an optional field to a required one, using `defaultValue` for
   * absent values.
   */
  final case class Mandate(at: DynamicOptic, defaultValue: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = new Optionalize(at)
  }

  /** Converts a required field to an optional one. */
  final case class Optionalize(at: DynamicOptic) extends MigrationAction {
    def reverse: MigrationAction = new Mandate(at, DynamicValue.Null)
  }

  /** Changes the type of a field, replacing with `newDefaultValue`. */
  final case class ChangeType(at: DynamicOptic, newDefaultValue: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  // ── Enum Actions ────────────────────────────────────────────────────

  /** Renames a variant case tag from `from` to `to`. */
  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = new RenameCase(at, to, from)
  }

  /**
   * Applies a nested list of migration actions to the value inside a variant
   * case. This is the core mechanism for nested migrations -- the `actions`
   * operate relative to the case value, not the document root.
   */
  final case class TransformCase(
    at: DynamicOptic,
    actions: Chunk[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction =
      new TransformCase(at, actions.reverse.map(_.reverse))
  }
}
