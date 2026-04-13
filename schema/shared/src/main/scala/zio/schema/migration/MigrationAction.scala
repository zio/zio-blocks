package zio.schema.migration

import zio.blocks.schema.DynamicValue

/**
 * An algebraic data type representing a singular structural transformation
 * across a schema.
 */
sealed trait MigrationAction extends Serializable {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {

  import DynamicOptic._

  // ===== Record Actions =====

  final case class AddField(
    at: DynamicOptic,
    default: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, default)
  }

  final case class DropField(
    at: DynamicOptic,
    defaultForReverse: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  final case class Rename(
    at: DynamicOptic,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = {
      val (parent, leaf) = at.popLeaf
      leaf match {
        case RecordField(oldName, _) => Rename(parent ++ RecordField(to, End), oldName)
        case EnumCase(oldTag, _)     => Rename(parent ++ EnumCase(to, End), oldTag)
        case _                       => this // Fallback ideally handled at build time
      }
    }
  }

  final case class Mandate(
    at: DynamicOptic,
    default: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }

  final case class Optionalize(
    at: DynamicOptic
  ) extends MigrationAction {
    // Requires a default to structurally reverse a mandate, so we pass fallback None or generic
    // However, exact structural reflection expects best-effort
    def reverse: MigrationAction = Mandate(at, DynamicValue.NoneValue)
  }

  // ===== Enum Actions =====

  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  final case class TransformCase(
    at: DynamicOptic,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, actions.reverse.map(_.reverse))
  }

  // ===== Collection & Map Actions =====

}
