package zio.schema.migration

import zio.schema._
import zio.Chunk

/**
 * A pure, serializable representation of a schema migration. Contains no type
 * information - operates only on DynamicValue.
 *
 * Can be:
 *   - Serialized to JSON/binary
 *   - Stored in a registry
 *   - Applied at runtime without schema definitions
 *   - Reversed if all actions are reversible
 */
case class DynamicMigration(actions: Chunk[MigrationAction]) {

  /**
   * Apply this migration to a dynamic value
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      case (Right(v), action) => action(v)
      case (left, _)          => left
    }

  /**
   * Reverse this migration if possible. Fails if any action is irreversible.
   */
  def reverse: Either[MigrationError, DynamicMigration] = {
    val reversed = actions.reverse.map(_.reverse)
    if (reversed.forall(_.isDefined)) {
      Right(DynamicMigration(Chunk.fromIterable(reversed.flatten)))
    } else {
      Left(
        MigrationError.IrreversibleMigration(
          "Migration contains irreversible actions"
        )
      )
    }
  }

  /**
   * Compose two migrations sequentially
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  /**
   * Optimize the migration by combining or eliminating redundant actions
   */
  def optimize: DynamicMigration = {
    val optimized = optimizeActions(actions)
    DynamicMigration(optimized)
  }

  private def optimizeActions(actions: Chunk[MigrationAction]): Chunk[MigrationAction] =
    actions.foldLeft(Chunk.empty[MigrationAction]) { (acc, action) =>
      (acc.lastOption, action) match {
        // Optimize: rename(a, b) ++ rename(b, c) => rename(a, c)
        case (Some(MigrationAction.RenameField(oldPath1, newPath1)), MigrationAction.RenameField(oldPath2, newPath2))
            if newPath1 == oldPath2 =>
          acc.dropRight(1) :+ MigrationAction.RenameField(oldPath1, newPath2)

        // Optimize: add(x, v) ++ drop(x) => noop
        case (Some(MigrationAction.AddField(path1, _)), MigrationAction.DropField(path2)) if path1 == path2 =>
          acc.dropRight(1)

        // Optimize: drop(x) ++ add(x, v) => replace (could be transform)
        case (Some(MigrationAction.DropField(path1)), MigrationAction.AddField(path2, value)) if path1 == path2 =>
          // This is effectively a field replacement
          acc.dropRight(1) :+ action

        // No optimization
        case _ =>
          acc :+ action
      }
    }

}

object DynamicMigration {
  val empty: DynamicMigration = DynamicMigration(Chunk.empty)

  def single(action: MigrationAction): DynamicMigration =
    DynamicMigration(Chunk.single(action))

  /** Schema for serializing DynamicMigration */
  implicit val schema: Schema[DynamicMigration] = DeriveSchema.gen[DynamicMigration]
}
