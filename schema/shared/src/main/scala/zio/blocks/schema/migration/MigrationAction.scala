package zio.blocks.schema.migration

import zio.schema.DynamicValue
// আমরা আমাদের নতুন তৈরি করা অপটিক ইমপোর্ট করছি
import zio.blocks.schema.migration.optic.DynamicOptic

/**
 * Represents a purely data-driven migration step.
 * Refactored to use DynamicOptic for precise path navigation.
 */
sealed trait MigrationAction {
  // প্রতিটি অ্যাকশনের একটি নির্দিষ্ট লোকেশন (at) থাকবে
  def at: DynamicOptic
}

object MigrationAction {
  
  final case class AddField(
    at: DynamicOptic, // আগে ছিল path: Vector[String], এখন আপগ্রেড হলো
    defaultValue: DynamicValue
  ) extends MigrationAction

  final case class RenameField(
    at: DynamicOptic,
    newName: String
  ) extends MigrationAction

  final case class DeleteField(
    at: DynamicOptic
  ) extends MigrationAction

  final case class ChangeType(
    at: DynamicOptic,
    targetType: String 
  ) extends MigrationAction
}

// এই ক্লাসের কালেকশন লজিকও আপডেট করা হলো
final case class DynamicMigration(actions: Vector[MigrationAction]) {
  
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)
    
  // TODO: Implement reverse logic in future steps
}