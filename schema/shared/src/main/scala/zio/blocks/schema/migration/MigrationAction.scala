package zio.blocks.schema.migration

import zio.blocks.schema.{ DynamicOptic, SchemaExpr }

// ─────────────────────────────────────────────────────────────────────────────
//  Migration Action ADT  (pure data — fully serializable, no functions)
// ─────────────────────────────────────────────────────────────────────────────

sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {

  // ── Record Actions ──────────────────────────────────────────────────────────

  /** Add a new field to a record. Reverse is DropField. */
  final case class AddField(
    at: DynamicOptic,
    default: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, SchemaExpr.DefaultValue)
  }

  /** Remove a field from a record. Reverse is AddField. */
  final case class DropField(
    at: DynamicOptic,
    defaultForReverse: SchemaExpr[?] = SchemaExpr.DefaultValue
  ) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  /** Rename a field or case. Reverse swaps from/to. */
  final case class Rename(
    at: DynamicOptic,
    to: String
  ) extends MigrationAction {
    // Extract the last segment name from `at` for the reverse rename
    def reverse: MigrationAction = {
      val fromName = at.lastFieldName.getOrElse(
        throw new IllegalStateException(s"Cannot reverse Rename: optic $at has no field name")
      )
      Rename(at.withLastFieldName(to), fromName)
    }
  }

  /** Transform the value of a field using a SchemaExpr. */
  final case class TransformValue(
    at: DynamicOptic,
    transform: SchemaExpr[?]
  ) extends MigrationAction {
    // Best-effort reverse — caller must supply inverse transform explicitly
    def reverse: MigrationAction = this
  }

  /** Make an optional field mandatory (Option[A] → A), providing a default. */
  final case class Mandate(
    at: DynamicOptic,
    default: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }

  /** Make a mandatory field optional (A → Option[A]). */
  final case class Optionalize(
    at: DynamicOptic
  ) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, SchemaExpr.DefaultValue)
  }

  /** Combine multiple source fields into a single target field. */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = Split(at, sourcePaths, SchemaExpr.DefaultValue)
  }

  /** Split a single source field into multiple target fields. */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = Join(at, targetPaths, SchemaExpr.DefaultValue)
  }

  /** Change the primitive type of a field with an explicit converter. */
  final case class ChangeType(
    at: DynamicOptic,
    converter: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  // ── Enum / Sum Actions ──────────────────────────────────────────────────────

  /** Rename an enum case. */
  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /** Apply a nested migration to a specific enum case. */
  final case class TransformCase(
    at: DynamicOptic,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, actions.map(_.reverse).reverse)
  }

  // ── Collection / Map Actions ────────────────────────────────────────────────

  /** Transform each element of a collection. */
  final case class TransformElements(
    at: DynamicOptic,
    transform: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  /** Transform each key of a map. */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  /** Transform each value of a map. */
  final case class TransformValues(
    at: DynamicOptic,
    transform: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = this
  }
}
