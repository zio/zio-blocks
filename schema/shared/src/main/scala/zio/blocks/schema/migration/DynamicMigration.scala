package zio.blocks.schema.migration

import scala.collection.immutable.Vector
import zio.blocks.schema.{DynamicOptic, SchemaExpr}

/** Pure, serializable migration program (no closures). */
final case class DynamicMigration(actions: Vector[MigrationAction]) extends Product with Serializable { self =>

  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(self.actions ++ that.actions)

  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))

  def apply(
    value: zio.blocks.schema.DynamicValue
  ): Either[MigrationError, zio.blocks.schema.DynamicValue] =
    DynamicMigrationInterpreter(this, value)

}

object DynamicMigration {
  val empty: DynamicMigration = DynamicMigration(Vector.empty)
  val id: DynamicMigration    = empty

  def apply(actions: MigrationAction*): DynamicMigration =
    DynamicMigration(Vector.from(actions))
}

/** The algebra of migrations (pure data). */
sealed trait MigrationAction extends Product with Serializable {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {

  // ─────────────────────────────────────────────
  // Record actions
  // ─────────────────────────────────────────────

  final case class AddField(
    at: DynamicOptic,
    default: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      DropField(at = at, defaultForReverse = default)
  }

  final case class DropField(
    at: DynamicOptic,
    defaultForReverse: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      AddField(at = at, default = defaultForReverse)
  }

  /**
   * Rename the field at `at` to `to`. `at` must point to the field being
   * renamed (ends in .field("old")).
   */
  final case class Rename(
    at: DynamicOptic,
    to: String
  ) extends MigrationAction {

    override def reverse: MigrationAction =
      at.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(oldName)) =>
          val parentNodes = at.nodes.toVector.dropRight(1)
          val parent      = DynamicOptic(parentNodes)
          val newAt       = parent.field(to)
          Rename(at = newAt, to = oldName)

        case _ =>
          // If it's not a field optic, we can't invert safely.
          // Keep best-effort behavior but still "reverse" structurally for the program.
          this
      }
  }

  final case class TransformValue(
    at: DynamicOptic,
    transform: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction = this // best-effort
  }

  final case class Mandate(
    at: DynamicOptic,
    default: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      Optionalize(at)
  }

  final case class Optionalize(
    at: DynamicOptic
  ) extends MigrationAction {
    override def reverse: MigrationAction = this // best-effort
  }

  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction = this // best-effort
  }

  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction = this // best-effort
  }

  final case class ChangeType(
    at: DynamicOptic,
    converter: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction = this // best-effort
  }

  // ─────────────────────────────────────────────
  // Enum actions
  // ─────────────────────────────────────────────

  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      RenameCase(at, from = to, to = from)
  }

  final case class TransformCase(
    at: DynamicOptic,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      TransformCase(at, actions = actions.reverse.map(_.reverse))
  }

  // ─────────────────────────────────────────────
  // Collection / Map actions
  // ─────────────────────────────────────────────

  final case class TransformElements(
    at: DynamicOptic,
    transform: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction = this
  }

  final case class TransformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction = this
  }

  final case class TransformValues(
    at: DynamicOptic,
    transform: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    override def reverse: MigrationAction = this
  }
}
