package zio.blocks.schema.migration

import zio.schema.DynamicValue

/**
 * Represents a purely data-driven migration plan.
 * This corresponds to the "Untyped Core" in the requirements.
 * It holds a sequence of actions that are fully serializable.
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Composes this migration with another.
   * Logic: Appends the actions of the second migration to the first.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  /**
   * Reverses the migration plan.
   * Logic: Reverse the order of actions, and call reverse on each individual action.
   * This is crucial for 'down' migrations.
   */
  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))

  /**
   * Applies the migration to a dynamic value using the MigrationEngine.
   * This is the entry point for execution.
   * * @param value The input DynamicValue (from Source Schema)
   * @return Either a MigrationError or the transformed DynamicValue (for Target Schema)
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    // 🔥 CONNECTED TO ENGINE: This executes the actual transformation logic
    MigrationEngine.run(value, this)
  }
}

object DynamicMigration {
  // An empty migration that does nothing (Identity)
  val identity: DynamicMigration = DynamicMigration(Vector.empty)
}