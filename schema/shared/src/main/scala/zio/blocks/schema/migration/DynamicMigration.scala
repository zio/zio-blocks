package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue

/**
 * An untyped, fully serializable migration represented as pure data.
 *
 * DynamicMigration contains no functions, closures, or reflection - just a
 * sequence of [[MigrationAction]] values that describe how to transform data.
 *
 * This design enables:
 *   - **Serialization**: Migrations can be stored in registries, transmitted
 *     over networks, or persisted to databases
 *   - **Introspection**: The migration structure can be analyzed, optimized, or
 *     transformed
 *   - **Code Generation**: Migrations can be used to generate SQL DDL/DML, data
 *     transformation scripts, or documentation
 *   - **Offline Processing**: Migrations can be applied without runtime
 *     reflection or code generation
 *
 * Example:
 * {{{
 * val migration = DynamicMigration(Vector(
 *   MigrationAction.AddField(DynamicOptic.root, "newField", Resolved.Literal.int(0)),
 *   MigrationAction.Rename(DynamicOptic.root, "oldName", "newName")
 * ))
 *
 * // Apply to data
 * migration.apply(dynamicValue) // Either[MigrationError, DynamicValue]
 *
 * // Compose migrations
 * val combined = migration1 ++ migration2
 *
 * // Reverse (structural)
 * val reversed = migration.reverse
 * }}}
 *
 * @param actions
 *   The sequence of migration actions to apply in order
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) { self =>

  /**
   * Apply this migration to a DynamicValue.
   *
   * Actions are applied sequentially, with the output of each action becoming
   * the input to the next. If any action fails, the entire migration fails with
   * that error.
   *
   * Each action receives the current document state as both the value to
   * transform and the root context. This enables cross-branch operations where
   * expressions can access values from anywhere in the document using
   * `RootAccess`.
   *
   * @param value
   *   The input DynamicValue to transform
   * @return
   *   Right with the transformed value, or Left with a MigrationError
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    var current: Either[MigrationError, DynamicValue] = Right(value)
    var idx                                           = 0
    val len                                           = actions.length

    while (idx < len && current.isRight) {
      // Pass current state as both value and root for cross-branch access
      current = current.flatMap(v => actions(idx).applyWithRoot(v, v))
      idx += 1
    }

    current
  }

  /**
   * Compose two migrations sequentially.
   *
   * The resulting migration first applies this migration's actions, then the
   * other migration's actions.
   *
   * This operation satisfies associativity:
   * `(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)`
   *
   * @param that
   *   The migration to apply after this one
   * @return
   *   A new migration that applies both in sequence
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(actions ++ that.actions)

  /**
   * Alias for ++
   */
  def andThen(that: DynamicMigration): DynamicMigration = this ++ that

  /**
   * Structural reverse of this migration.
   *
   * Each action is reversed, and the order is reversed. This provides a
   * migration that structurally undoes what this migration does.
   *
   * Note: The reverse migration may fail at runtime if information was lost
   * during the forward migration (e.g., dropped fields without proper defaults
   * for reverse).
   *
   * This operation satisfies: `m.reverse.reverse == m` (structurally)
   *
   * @return
   *   A new migration that reverses this one
   */
  def reverse: DynamicMigration =
    DynamicMigration(actions.reverseIterator.map(_.reverse).toVector)

  /**
   * Check if this is the identity migration (no actions).
   *
   * The identity migration transforms any value to itself.
   */
  def isIdentity: Boolean = actions.isEmpty

  /**
   * Get the number of actions in this migration.
   */
  def actionCount: Int = actions.length

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
  def optimize: DynamicMigration =
    DynamicMigration(MigrationOptimizer.optimize(actions))

  /**
   * Create a human-readable description of this migration.
   */
  def describe: String =
    if (actions.isEmpty) "Identity migration (no changes)"
    else actions.map(describeAction).mkString("Migration:\n  ", "\n  ", "")

  private def describeAction(action: MigrationAction): String = action match {
    case MigrationAction.AddField(at, name, _)          => s"AddField($name) at ${at.toString}"
    case MigrationAction.DropField(at, name, _)         => s"DropField($name) at ${at.toString}"
    case MigrationAction.Rename(at, from, to)           => s"Rename($from -> $to) at ${at.toString}"
    case MigrationAction.TransformValue(at, name, _, _) => s"TransformValue($name) at ${at.toString}"
    case MigrationAction.Mandate(at, name, _)           => s"Mandate($name) at ${at.toString}"
    case MigrationAction.Optionalize(at, name)          => s"Optionalize($name) at ${at.toString}"
    case MigrationAction.ChangeType(at, name, _, _)     => s"ChangeType($name) at ${at.toString}"
    case MigrationAction.RenameCase(at, from, to)       => s"RenameCase($from -> $to) at ${at.toString}"
    case MigrationAction.TransformCase(at, name, acts)  =>
      s"TransformCase($name, ${acts.length} actions) at ${at.toString}"
    case MigrationAction.TransformElements(at, _, _)    => s"TransformElements at ${at.toString}"
    case MigrationAction.TransformKeys(at, _, _)        => s"TransformKeys at ${at.toString}"
    case MigrationAction.TransformValues(at, _, _)      => s"TransformValues at ${at.toString}"
    case MigrationAction.TransformField(at, name, acts) =>
      s"TransformField($name, ${acts.length} actions) at ${at.toString}"
    case MigrationAction.TransformEachElement(at, name, acts) =>
      s"TransformEachElement($name, ${acts.length} actions) at ${at.toString}"
    case MigrationAction.TransformEachMapValue(at, name, acts) =>
      s"TransformEachMapValue($name, ${acts.length} actions) at ${at.toString}"
  }
}

object DynamicMigration {

  /**
   * The identity migration that performs no transformation.
   *
   * For any value v: `identity.apply(v) == Right(v)`
   */
  val identity: DynamicMigration = DynamicMigration(Vector.empty)

  /**
   * Create a migration with a single action.
   */
  def single(action: MigrationAction): DynamicMigration =
    DynamicMigration(Vector(action))

  /**
   * Create a migration from multiple actions.
   */
  def apply(actions: MigrationAction*): DynamicMigration =
    DynamicMigration(actions.toVector)

  /**
   * Schema for DynamicMigration enabling serialization to JSON, Protobuf, etc.
   *
   * With this schema, migrations can be stored in registries, transmitted over
   * networks, and persisted to databases.
   */
  implicit def schema: zio.blocks.schema.Schema[DynamicMigration] =
    MigrationSchemas.dynamicMigrationSchema
}

/**
 * Optimizer for migration actions.
 *
 * Performs simplifications on a sequence of actions to produce an equivalent
 * but more efficient migration.
 */
object MigrationOptimizer {

  /**
   * Optimize a sequence of migration actions.
   *
   * Current optimizations:
   *   - Remove consecutive add/drop of the same field
   *   - Combine consecutive renames (a->b, b->c becomes a->c)
   *   - Remove identity transforms
   */
  def optimize(actions: Vector[MigrationAction]): Vector[MigrationAction] = {
    if (actions.isEmpty) return actions

    // First pass: optimize pairs
    val pairOptimized = optimizePairs(actions)

    // Second pass: filter out single no-op actions
    val filtered = pairOptimized.filter(!isNoOp(_))

    // Recursively optimize if we made changes
    if (filtered.length != actions.length) optimize(filtered)
    else filtered
  }

  private def optimizePairs(actions: Vector[MigrationAction]): Vector[MigrationAction] = {
    if (actions.length < 2) return actions

    val result = Vector.newBuilder[MigrationAction]
    var idx    = 0
    val len    = actions.length

    while (idx < len) {
      val current = actions(idx)

      // Check for optimization opportunities with next action
      if (idx + 1 < len) {
        val next = actions(idx + 1)
        optimizePair(current, next) match {
          case Some(optimized) =>
            // Pair was optimized
            optimized.foreach(result += _)
            idx += 2
          case None =>
            // No optimization possible
            result += current
            idx += 1
        }
      } else {
        result += current
        idx += 1
      }
    }

    result.result()
  }

  /**
   * Check if an action is a no-op.
   */
  private def isNoOp(action: MigrationAction): Boolean = action match {
    // Rename to the same name is a no-op
    case MigrationAction.Rename(_, from, to) if from == to     => true
    case MigrationAction.RenameCase(_, from, to) if from == to => true
    case _                                                     => false
  }

  /**
   * Try to optimize a pair of consecutive actions.
   *
   * @return
   *   Some(optimized) if the pair can be simplified, None otherwise
   */
  private def optimizePair(
    a: MigrationAction,
    b: MigrationAction
  ): Option[Vector[MigrationAction]] = (a, b) match {
    // AddField followed by DropField of same field = no-op
    case (MigrationAction.AddField(at1, name1, _), MigrationAction.DropField(at2, name2, _))
        if at1 == at2 && name1 == name2 =>
      Some(Vector.empty)

    // DropField followed by AddField of same field = just change the default
    case (MigrationAction.DropField(at1, name1, _), MigrationAction.AddField(at2, name2, default))
        if at1 == at2 && name1 == name2 =>
      // This is a "replace value" operation - we could potentially optimize
      // but it changes semantics, so leave as-is for now
      None

    // Consecutive renames: a->b, b->c becomes a->c
    case (MigrationAction.Rename(at1, from1, to1), MigrationAction.Rename(at2, from2, to2))
        if at1 == at2 && to1 == from2 =>
      Some(Vector(MigrationAction.Rename(at1, from1, to2)))

    // Rename back and forth = no-op
    case (MigrationAction.Rename(at1, from1, to1), MigrationAction.Rename(at2, from2, to2))
        if at1 == at2 && from1 == to2 && to1 == from2 =>
      Some(Vector.empty)

    // RenameCase: same optimization as Rename
    case (MigrationAction.RenameCase(at1, from1, to1), MigrationAction.RenameCase(at2, from2, to2))
        if at1 == at2 && to1 == from2 =>
      Some(Vector(MigrationAction.RenameCase(at1, from1, to2)))

    case (MigrationAction.RenameCase(at1, from1, to1), MigrationAction.RenameCase(at2, from2, to2))
        if at1 == at2 && from1 == to2 && to1 == from2 =>
      Some(Vector.empty)

    // No optimization found
    case _ => None
  }
}
