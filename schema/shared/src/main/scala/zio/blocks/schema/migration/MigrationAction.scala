package zio.blocks.schema.migration

import zio.blocks.schema.migration.optic.DynamicOptic

/**
 * Represents a purely data-driven migration step.
 * Uses DynamicOptic for paths and SchemaExpr for transformation logic.
 * STRICTLY ALGEBRAIC: Uses SchemaExpr instead of raw values where possible.
 */
sealed trait MigrationAction {
  def at: DynamicOptic
}

object MigrationAction {
  
  // --- Simple Actions ---

  final case class AddField(
    at: DynamicOptic,
    defaultValue: SchemaExpr // UPDATED: DynamicValue থেকে SchemaExpr এ পরিবর্তন করা হলো
  ) extends MigrationAction

  final case class RenameField(
    at: DynamicOptic,
    newName: String
  ) extends MigrationAction

  final case class DeleteField(
    at: DynamicOptic,
    defaultForReverse: SchemaExpr = SchemaExpr.DefaultValue // রিভার্স করার জন্য ব্যাকআপ
  ) extends MigrationAction

  final case class ChangeType(
    at: DynamicOptic,
    targetType: String 
  ) extends MigrationAction

  // --- Complex Actions ---

  // ১. ভ্যালু ট্রান্সফর্ম করা (যেমন: স্যালারি ১০% বাড়ানো)
  final case class TransformValue(
    at: DynamicOptic,
    transform: SchemaExpr 
  ) extends MigrationAction

  // ২. অপশনাল ফিল্ডকে ম্যান্ডেটরি করা (Option[T] -> T)
  final case class MandateField(
    at: DynamicOptic,
    default: SchemaExpr 
  ) extends MigrationAction

  // ৩. ম্যান্ডেটরি ফিল্ডকে অপশনাল করা (T -> Option[T])
  final case class OptionalizeField(
    at: DynamicOptic
  ) extends MigrationAction

  // ৪. একাধিক ফিল্ড জোড়া লাগানো (যেমন: first + last = fullName)
  final case class JoinFields(
    at: DynamicOptic, // যেখানে রেজাল্ট বসবে
    sourcePaths: Vector[DynamicOptic], // যেখান থেকে ডাটা আসবে
    combiner: SchemaExpr // কিভাবে জোড়া লাগবে সেই লজিক
  ) extends MigrationAction

  // ৫. একটি ফিল্ড ভেঙে একাধিক করা (যেমন: fullName -> first, last)
  final case class SplitField(
    at: DynamicOptic, // যে ফিল্ডটা ভাঙব
    targetPaths: Vector[DynamicOptic], // যেখানে টুকরোগুলো যাবে
    splitter: SchemaExpr // কিভাবে ভাঙব সেই লজিক
  ) extends MigrationAction
}

final case class DynamicMigration(actions: Vector[MigrationAction]) {
  
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)
}