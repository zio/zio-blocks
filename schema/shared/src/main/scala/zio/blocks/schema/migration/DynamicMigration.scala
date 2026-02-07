package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue

/**
 * DynamicMigration: The Serializable Core (Unified Shared Version)
 * --------------------------------------------------------------- This
 * represents the untyped, runtime core of the migration system. Works perfectly
 * in both Scala 2.13 and Scala 3.
 */
final case class DynamicMigration(
  actions: Vector[MigrationAction]
) {

  /** Applies the migration logic to a DynamicValue. */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      case (Right(current), action) => MigrationInterpreter.run(current, action)
      case (left, _)                => left
    }

  /** Composes two migrations sequentially. */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  /** Structurally reverses the migration. */
  def reverse: DynamicMigration =
    DynamicMigration(this.actions.reverse.map(_.reverse))
}

object DynamicMigration {

  /** Identity / No-op migration */
  val empty: DynamicMigration = DynamicMigration(Vector.empty)

  /** Helper to construct from varargs (Convenience for Scala 3 users) */
  def make(actions: MigrationAction*): DynamicMigration =
    DynamicMigration(actions.toVector)

  // =================================================================================
  // Phantom Types for Compile-Time Safety
  // =================================================================================

  /**
   * 🏛️ MigrationList: Used to track the state of the builder in the type
   * system.
   */
  sealed trait MigrationList
  object MigrationList {
    sealed trait MNil                                           extends MigrationList
    sealed trait ::[H <: MigrationAction, T <: MigrationList]   extends MigrationList
    sealed trait Concat[L <: MigrationList, R <: MigrationList] extends MigrationList
  }
}
