package zio.blocks.schema.migration

import scala.collection.immutable.Vector
import zio.blocks.schema.{DynamicOptic, SchemaExpr}

/** Pure, serializable migration program (no closures).
  *
  * Matches zio-blocks issue #519:
  * DynamicMigration(actions: Vector[MigrationAction])
  */
final case class DynamicMigration(actions: Vector[MigrationAction])
    extends Product
    with Serializable { self =>

  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(self.actions ++ that.actions)

  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))
}

object DynamicMigration {
  val empty: DynamicMigration = DynamicMigration(Vector.empty)
  val id: DynamicMigration    = empty

  def apply(actions: MigrationAction*): DynamicMigration =
    DynamicMigration(Vector.from(actions))
}

/** The algebra of migrations (pure data).
  * Exactly the core action set from issue #519.
  */
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

  /** Rename the field at `at` to `to`.
    * Note: `at` must point to the *field being renamed* (end in .field("old")).
    */
  final case class Rename(
      at: DynamicOptic,
      to: String
  ) extends MigrationAction {
    override def reverse: MigrationAction = this // best-effort (needs old name + path rewrite)
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

  /** Transform a *specific* case. In #519, the case is carried by `at`
    * via a `.when[Case]` selector → DynamicOptic.Node.Case("Case").
    */
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
