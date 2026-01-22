package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue

/**
 * Pure Data migration engine. Executes a sequence of MigrationActions on
 * DynamicValues.
 */
final case class DynamicMigration(
  actions: Vector[MigrationAction]
) {

  /**
   * Applies this migration to a DynamicValue. Executes actions sequentially,
   * stopping at the first error.
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    var current = value
    val len     = actions.length
    var idx     = 0

    while (idx < len) {
      actions(idx).execute(current) match {
        case Right(newValue) =>
          current = newValue
        case left @ Left(_) =>
          return left
      }
      idx += 1
    }

    Right(current)
  }

  /**
   * Composes this migration with another, executing sequentially. Associativity
   * law: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  /**
   * Returns the structural inverse of this migration. Reverses action order and
   * reverses each action. Law: m.reverse.reverse == m
   */
  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))
}

object DynamicMigration {

  /**
   * Identity migration - returns value unchanged. Law: identity.apply(v) ==
   * Right(v)
   */
  val identity: DynamicMigration = DynamicMigration(Vector.empty)
}
