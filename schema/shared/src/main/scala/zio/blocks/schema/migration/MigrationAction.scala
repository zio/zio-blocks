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
 *
 * Value-level parameters use [[DynamicValue]] rather than `SchemaExpr` so that
 * the entire action ADT remains fully serializable with no runtime bindings.
 * The typed [[MigrationBuilder]] layer converts user-supplied values and
 * expressions into `DynamicValue` at build time.
 */
sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {

  // ── Record Actions ──────────────────────────────────────────────────

  /** Inserts a new field at the given path with a default value. */
  final case class AddField(at: DynamicOptic, default: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = new DropField(at, default)
  }

  /** Removes the field at the given path. */
  final case class DropField(at: DynamicOptic, defaultForReverse: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = new AddField(at, defaultForReverse)
  }

  /** Renames the field addressed by `at` to `to`. */
  final case class Rename(at: DynamicOptic, to: String) extends MigrationAction {
    def reverse: MigrationAction = {
      val nodes = at.nodes
      nodes.last match {
        case DynamicOptic.Node.Field(oldName) =>
          val parentNodes = nodes.dropRight(1)
          new Rename(new DynamicOptic(parentNodes.appended(DynamicOptic.Node.Field(to))), oldName)
        case _ => this
      }
    }
  }

  /** Transforms the value at the given path using a pure expression. */
  final case class TransformValue(at: DynamicOptic, transform: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  /**
   * Converts an optional field to a required one, using `default` for absent
   * values.
   */
  final case class Mandate(at: DynamicOptic, default: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = new Optionalize(at)
  }

  /** Converts a required field to an optional one. */
  final case class Optionalize(at: DynamicOptic) extends MigrationAction {
    def reverse: MigrationAction = new Mandate(at, DynamicValue.Null)
  }

  /** Joins multiple source fields into a single target field. */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Chunk[DynamicOptic],
    combiner: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = new Split(at, sourcePaths, combiner)
  }

  /** Splits a single source field into multiple target fields. */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Chunk[DynamicOptic],
    splitter: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = new Join(at, targetPaths, splitter)
  }

  /** Changes the type of a field using a primitive-to-primitive converter. */
  final case class ChangeType(at: DynamicOptic, converter: DynamicValue) extends MigrationAction {
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

  // ── Collection / Map Actions ────────────────────────────────────────

  /** Transforms each element in a sequence at the given path. */
  final case class TransformElements(at: DynamicOptic, transform: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  /** Transforms each key in a map at the given path. */
  final case class TransformKeys(at: DynamicOptic, transform: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  /** Transforms each value in a map at the given path. */
  final case class TransformValues(at: DynamicOptic, transform: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = this
  }
}
