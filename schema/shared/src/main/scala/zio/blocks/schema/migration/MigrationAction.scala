package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * A reified, serializable action in a schema migration.
 *
 * Every action stores the path (`DynamicOptic`) at which the action operates,
 * making the entire `DynamicMigration` introspectable and serializable for DDL
 * generation, offline migrations, etc.
 */
sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {

  // ──────────────── Record Actions ────────────────

  /**
   * Add a new field with a default value.
   */
  final case class AddField(at: DynamicOptic, fieldName: String, default: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, fieldName, default)
  }

  /**
   * Drop a field, keeping a default for reverse reconstruction.
   */
  final case class DropField(at: DynamicOptic, fieldName: String, defaultForReverse: DynamicValue)
      extends MigrationAction {
    def reverse: MigrationAction = AddField(at, fieldName, defaultForReverse)
  }

  /**
   * Rename a field.
   */
  final case class Rename(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = Rename(at, to, from)
  }

  /**
   * Transform the value of a field using a nested `DynamicMigration`.
   */
  final case class TransformValue(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, migration.reverse)
  }

  /**
   * Make an optional field mandatory, providing a default for `None` values.
   */
  final case class Mandate(at: DynamicOptic, fieldName: String, default: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at, fieldName)
  }

  /**
   * Make a mandatory field optional.
   */
  final case class Optionalize(at: DynamicOptic, fieldName: String) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, fieldName, DynamicValue.Null)
  }

  /**
   * Change the type of a field using a converter migration.
   */
  final case class ChangeType(at: DynamicOptic, fieldName: String, converter: DynamicMigration)
      extends MigrationAction {
    def reverse: MigrationAction = ChangeType(at, fieldName, converter.reverse)
  }

  /**
   * Change the type of a field using a `MigrationExpr` (zero-closure, fully
   * serializable).
   */
  final case class ChangeTypeExpr(at: DynamicOptic, fieldName: String, expr: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = ChangeTypeExpr(at, fieldName, expr.reverse)
  }

  /**
   * Nest multiple fields into a sub-record.
   */
  final case class Nest(at: DynamicOptic, fieldNames: Vector[String], intoField: String) extends MigrationAction {
    def reverse: MigrationAction = Unnest(at, intoField, fieldNames)
  }

  /**
   * Unnest a sub-record into the parent record.
   */
  final case class Unnest(at: DynamicOptic, fieldName: String, expectedFields: Vector[String]) extends MigrationAction {
    def reverse: MigrationAction = Nest(at, expectedFields, fieldName)
  }

  /**
   * Join multiple source fields into a single target field using a
   * `MigrationExpr`.
   *
   * The `combiner` receives a Record containing the source fields and produces
   * the target value. This is pure data — no closures.
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[String],
    targetField: String,
    combiner: MigrationExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = Split(at, targetField, sourcePaths, combiner.reverse)
  }

  /**
   * Split a single source field into multiple target fields using a
   * `MigrationExpr`.
   *
   * The `splitter` receives the source value and produces a Record containing
   * the target fields. This is pure data — no closures.
   */
  final case class Split(
    at: DynamicOptic,
    sourceField: String,
    targetFields: Vector[String],
    splitter: MigrationExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = Join(at, targetFields, sourceField, splitter.reverse)
  }

  // ──────────────── Enum Actions ────────────────

  /**
   * Rename a case in an enum (variant).
   */
  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /**
   * Transform the payload of a specific enum case.
   */
  final case class TransformCase(at: DynamicOptic, caseName: String, actions: Vector[MigrationAction])
      extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, caseName, actions.reverse.map(_.reverse))
  }

  // ──────────────── Collection / Map Actions ────────────────

  /**
   * Transform all elements in a sequence.
   */
  final case class TransformElements(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, migration.reverse)
  }

  /**
   * Transform all keys in a map.
   */
  final case class TransformKeys(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, migration.reverse)
  }

  /**
   * Transform all values in a map.
   */
  final case class TransformValues(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, migration.reverse)
  }
}
