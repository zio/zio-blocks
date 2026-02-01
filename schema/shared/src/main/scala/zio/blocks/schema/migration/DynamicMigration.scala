package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk

/**
 * A DynamicMigration is a pure data structure representing a sequence of
 * migration actions. It is fully serializable and can be stored, transmitted,
 * and applied later.
 *
 * This is the untyped representation that can be applied to DynamicValue
 * instances.
 */
final case class DynamicMigration(actions: Chunk[MigrationAction]) {

  /**
   * Compose this migration with another, creating a sequential migration.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(actions ++ that.actions)

  /**
   * Alias for sequential composition.
   */
  def andThen(that: DynamicMigration): DynamicMigration = this ++ that

  /**
   * Create a reversed migration that undoes this migration. For actions with
   * "best effort" reversal (like TransformValue), the reverse may not produce
   * the exact original value.
   */
  def reverse: DynamicMigration =
    DynamicMigration(actions.map(_.reverse).reverse)

  /**
   * Check if this migration has no actions (identity migration).
   */
  def isEmpty: Boolean = actions.isEmpty

  /**
   * The number of actions in this migration.
   */
  def size: Int = actions.size

  /**
   * Alias for size to match common usage patterns.
   */
  def actionCount: Int = size

  /**
   * Check if this is the identity migration (no actions).
   */
  def isIdentity: Boolean = actions.isEmpty

  /**
   * Create a human-readable description of this migration.
   *
   * Each action is described on its own line with path and operation details.
   * This is useful for debugging and logging migration operations.
   */
  def describe: String =
    if (actions.isEmpty) "Identity migration (no changes)"
    else {
      val descriptions = actions.map(describeAction)
      s"Migration with ${actions.size} actions:\n  ${descriptions.mkString("\n  ")}"
    }

  private def describeAction(action: MigrationAction): String = action match {
    case MigrationAction.AddField(at, name, _)           => s"AddField($name) at ${at.toScalaString}"
    case MigrationAction.DropField(at, name, _)          => s"DropField($name) at ${at.toScalaString}"
    case MigrationAction.Rename(at, from, to)            => s"Rename($from -> $to) at ${at.toScalaString}"
    case MigrationAction.TransformValue(at, name, _, _)  => s"Transform($name) at ${at.toScalaString}"
    case MigrationAction.Mandate(at, name, _)            => s"Mandate($name) at ${at.toScalaString}"
    case MigrationAction.Optionalize(at, name)           => s"Optionalize($name) at ${at.toScalaString}"
    case MigrationAction.ChangeType(at, name, _, _)      => s"ChangeType($name) at ${at.toScalaString}"
    case MigrationAction.RenameCase(at, from, to)        => s"RenameCase($from -> $to) at ${at.toScalaString}"
    case MigrationAction.TransformCase(at, name, _)      => s"TransformCase($name) at ${at.toScalaString}"
    case MigrationAction.TransformElements(at, _, _)     => s"TransformElements at ${at.toScalaString}"
    case MigrationAction.TransformKeys(at, _, _)         => s"TransformKeys at ${at.toScalaString}"
    case MigrationAction.TransformValues(at, _, _)       => s"TransformValues at ${at.toScalaString}"
    case MigrationAction.Join(at, target, sources, _, _) =>
      s"Join(${sources.mkString(", ")} -> $target) at ${at.toScalaString}"
    case MigrationAction.Split(at, source, targets, _, _) =>
      s"Split($source -> ${targets.mkString(", ")}) at ${at.toScalaString}"
  }

  /**
   * Optimize this migration by combining or eliminating redundant actions.
   *
   * Optimizations include:
   *   - Combining consecutive renames: Rename(a->b) + Rename(b->c) =
   *     Rename(a->c)
   *   - Eliminating inverse pairs: AddField(x) + DropField(x) = identity
   *   - Removing no-op actions
   *
   * @return
   *   An optimized migration with equivalent behavior
   */
  def optimize: DynamicMigration = {
    val optimized = actions.foldLeft(Chunk.empty[MigrationAction]) { (acc, action) =>
      if (acc.isEmpty) Chunk.single(action)
      else {
        val last = acc.last
        (last, action) match {
          // Combine consecutive renames on same path
          case (MigrationAction.Rename(at1, from1, to1), MigrationAction.Rename(at2, from2, to2))
              if at1 == at2 && to1 == from2 =>
            acc.dropRight(1) :+ MigrationAction.Rename(at1, from1, to2)

          // Eliminate inverse pairs: AddField then DropField
          case (MigrationAction.AddField(at1, name1, _), MigrationAction.DropField(at2, name2, _))
              if at1 == at2 && name1 == name2 =>
            acc.dropRight(1)

          // Eliminate inverse pairs: DropField then AddField
          case (MigrationAction.DropField(at1, name1, _), MigrationAction.AddField(at2, name2, _))
              if at1 == at2 && name1 == name2 =>
            acc.dropRight(1)

          // Eliminate no-op renames
          case (_, MigrationAction.Rename(_, from, to)) if from == to =>
            acc

          case _ =>
            acc :+ action
        }
      }
    }
    DynamicMigration(optimized)
  }

  /**
   * Apply this migration to a DynamicValue.
   *
   * This is the Issue #519 required method for executing migrations on dynamic
   * values without requiring typed schemas.
   *
   * @param value
   *   the DynamicValue to transform
   * @return
   *   Either an error message or the transformed DynamicValue
   */
  def apply(value: zio.blocks.schema.DynamicValue): Either[String, zio.blocks.schema.DynamicValue] =
    DynamicMigrationInterpreter.run(this, value)
}

object DynamicMigration {

  /**
   * Empty migration that performs no transformations.
   */
  val identity: DynamicMigration = DynamicMigration(Chunk.empty)

  /**
   * Create a migration from a single action.
   */
  def single(action: MigrationAction): DynamicMigration =
    DynamicMigration(Chunk.single(action))

  /**
   * Create a migration from a single action (alias for single).
   */
  def apply(action: MigrationAction): DynamicMigration =
    single(action)

  /**
   * Create a migration from varargs of actions.
   */
  def apply(actions: MigrationAction*): DynamicMigration =
    DynamicMigration(Chunk.fromIterable(actions))

  /**
   * Schema instance for serialization.
   */
  implicit lazy val schema: zio.blocks.schema.Schema[DynamicMigration] = {
    import zio.blocks.schema.Schema
    Schema[Chunk[MigrationAction]].transform(
      (actions: Chunk[MigrationAction]) => DynamicMigration(actions),
      (dm: DynamicMigration) => dm.actions
    )
  }
}
