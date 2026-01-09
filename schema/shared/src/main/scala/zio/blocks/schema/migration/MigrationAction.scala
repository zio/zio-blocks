package zio.blocks.schema.migration

import zio.schema.DynamicValue
import zio.blocks.schema.migration.optic.DynamicOptic

/**
 * Represents a purely data-driven migration step.
 * Uses DynamicOptic for paths and SchemaExpr for transformation logic.
 */
sealed trait MigrationAction {
  def at: DynamicOptic
}

object MigrationAction {
  
  // --- Simple Actions ---

  final case class AddField(
    at: DynamicOptic,
    defaultValue: SchemaExpr // Updated: Phase 3 তে আমরা Pure Algebraic হওয়ার জন্য SchemaExpr ব্যবহার করছি
  ) extends MigrationAction

  final case class RenameField(
    at: DynamicOptic,
    newName: String
  ) extends MigrationAction

  final case class DeleteField(
    at: DynamicOptic,
    defaultForReverse: SchemaExpr = SchemaExpr.DefaultValue
  ) extends MigrationAction

  // 🔥 UPDATED: Matching with MigrationBuilder.changeFieldType
  final case class ChangeType(
    at: DynamicOptic,
    converter: String // Renamed from 'targetType' to 'converter' to match the Builder logic
  ) extends MigrationAction

  // --- Complex Actions ---

  final case class TransformValue(
    at: DynamicOptic,
    transform: SchemaExpr
  ) extends MigrationAction

  final case class MandateField(
    at: DynamicOptic,
    default: SchemaExpr
  ) extends MigrationAction

  final case class OptionalizeField(
    at: DynamicOptic
  ) extends MigrationAction

  final case class JoinFields(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr
  ) extends MigrationAction

  final case class SplitField(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr
  ) extends MigrationAction
}

final case class DynamicMigration(actions: Vector[MigrationAction]) {
  
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)
}