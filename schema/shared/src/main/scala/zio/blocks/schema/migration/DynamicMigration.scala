package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A pure, algebraic representation of a schema migration.
 *
 * `DynamicMigration` is a serializable ADT that describes a sequence of
 * transformations to apply to a `DynamicValue`. It can be composed, reversed,
 * and serialized for offline migration scenarios.
 *
 * **Design Principles:**
 *   - Pure and referentially transparent
 *   - Fully serializable (can be persisted and transmitted)
 *   - Composable via the monoid instance
 *   - Introspectable (can analyze migration structure)
 *
 * **Common Use Cases:**
 *   - Schema evolution in databases
 *   - API versioning and compatibility
 *   - Data lake migrations
 *   - Registry-based schema management
 *
 * **Laws:**
 *
 * Identity:
 * {{{
 *   DynamicMigration.empty ++ m == m
 *   m ++ DynamicMigration.empty == m
 *   DynamicMigration.empty.apply(v) == Right(v)
 * }}}
 *
 * Associativity:
 * {{{
 *   (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
 * }}}
 *
 * Reversibility (for reversible migrations):
 * {{{
 *   m.reverse.flatMap(r => r.apply(m.apply(v))) == Right(v)
 *   m.reverse.flatMap(_.reverse) == Right(m)
 * }}}
 *
 * Example usage:
 * {{{
 *   val migration = DynamicMigration.fromActions(
 *     MigrationAction.RenameField("name", "fullName"),
 *     MigrationAction.AddField("country", DynamicValue.Primitive(PrimitiveValue.String("USA")))
 *   )
 *
 *   val result = migration.apply(oldValue)
 *   val description = migration.describe  // "Rename field 'name' to 'fullName' → Add field 'country'"
 * }}}
 *
 * @param actions
 *   The sequence of migration actions to apply
 * @see
 *   [[MigrationAction]] for available transformations
 * @see
 *   [[Migration]] for type-safe wrapper
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Apply this migration to a DynamicValue.
   *
   * Actions are applied in sequence, short-circuiting on first error.
   *
   * @param value
   *   The input value to migrate
   * @return
   *   Either an error or the migrated value
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) { (acc, action) =>
      acc.flatMap(action.apply)
    }

  /**
   * Compose this migration with another.
   *
   * The resulting migration applies `this` first, then `that`.
   *
   * **Law: Associativity** `(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)`
   *
   * @param that
   *   The migration to apply after this one
   * @return
   *   The composed migration
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  /**
   * Attempt to reverse this migration.
   *
   * Creates a new migration that undoes the effects of this one. Reversal
   * applies actions in reverse order, each individually reversed.
   *
   * **Law: Structural Reverse**
   * `m.reverse.flatMap(r => r.apply(m.apply(a))) == Right(a)` (for reversible
   * migrations)
   *
   * **Law: Identity** `m.reverse.flatMap(_.reverse) == Right(m)`
   *
   * @return
   *   Either the reversed migration or an error if not reversible
   */
  def reverse: Either[MigrationError, DynamicMigration] = {
    val reversed = actions.reverse.foldLeft[Either[MigrationError, Vector[MigrationAction]]](
      Right(Vector.empty)
    ) { (acc, action) =>
      acc.flatMap { reversedActions =>
        action.reverse.map(reversedActions :+ _)
      }
    }
    reversed.map(DynamicMigration(_))
  }

  /**
   * Check if this migration is reversible.
   *
   * @return
   *   True if all actions can be reversed
   */
  def isReversible: Boolean =
    actions.forall(_.reverse.isRight)

  /**
   * Returns a human-readable description of the migration.
   */
  def describe: String =
    if (actions.isEmpty) "Empty migration"
    else
      actions.map {
        case MigrationAction.DropField(at, _)           => s"Drop field at '$at'"
        case MigrationAction.Rename(at, to)             => s"Rename field at '$at' to '$to'"
        case MigrationAction.AddField(at, _)            => s"Add field at '$at'"
        case MigrationAction.Optionalize(at, _)         => s"Make field at '$at' optional"
        case MigrationAction.Mandate(at, _)             => s"Make field at '$at' mandatory"
        case MigrationAction.RenameCase(at, _, newName) => s"Rename case in '$at' to '$newName'"
        case MigrationAction.RemoveCase(at, name)       => s"Remove case '$name' in '$at'"
        case MigrationAction.Join(at, sources, _)       =>
          s"Join fields ${sources.mkString(", ")} at '$at'"
        case MigrationAction.Split(at, targets, _) =>
          s"Split field into ${targets.mkString(", ")} at '$at'"
        case MigrationAction.TransformValue(at, _)          => s"Transform value at '$at'"
        case MigrationAction.ChangeType(at, _)              => s"Change type of field at '$at'"
        case MigrationAction.TransformCase(at, caseName, _) =>
          s"Transform case '$caseName' in '$at'"
        case MigrationAction.TransformElements(at, _) => s"Transform elements in '$at'"
        case MigrationAction.TransformKeys(at, _)     => s"Transform keys in '$at'"
        case MigrationAction.TransformValues(at, _)   => s"Transform values in '$at'"
      }.mkString(" → ")

}

object DynamicMigration {

  /**
   * An empty migration that performs no transformations.
   *
   * **Law: Identity** `empty ++ m == m ++ empty == m`
   * `empty.apply(v) == Right(v)`
   */
  val empty: DynamicMigration = DynamicMigration(Vector.empty)

  /**
   * Create a migration from a single action.
   */
  def single(action: MigrationAction): DynamicMigration =
    DynamicMigration(Vector(action))

  /**
   * Create a migration from multiple actions.
   */
  def fromActions(actions: MigrationAction*): DynamicMigration =
    DynamicMigration(actions.toVector)
}
