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
 * Structural Reverse:
 * {{{
 *   m.reverse.reverse == m
 * }}}
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Apply this migration to a DynamicValue. Actions are applied in sequence,
   * short-circuiting on first error.
   */
  def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
    actions.foldLeft[Either[SchemaError, DynamicValue]](Right(value)) { (acc, action) =>
      acc.flatMap(action.apply)
    }

  /**
   * Compose this migration with another. The resulting migration applies `this`
   * first, then `that`.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  /**
   * Structural reverse of this migration. Returns a migration that structurally
   * undoes this one. Note: Best-effort semantic inverse (may not recover all
   * data).
   */
  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))

  /** Check if this migration is semantically reversible (can recover data). */
  def isSemanticReversible: Boolean =
    actions.forall {
      case _: MigrationAction.Rename        => true
      case _: MigrationAction.RenameCase    => true
      case _: MigrationAction.TransformCase => true
      case _: MigrationAction.AddField      => true
      case _                                => false
    }

  /** Get a human-readable description of this migration. */
  def describe: String =
    if (actions.isEmpty) "Empty migration"
    else
      actions.map {
        case MigrationAction.AddField(at, name, _)        => s"Add field '$name' at ${at.toString}"
        case MigrationAction.DropField(at, name, _)       => s"Drop field '$name' at ${at.toString}"
        case MigrationAction.Rename(at, from, to)         => s"Rename '$from' to '$to' at ${at.toString}"
        case MigrationAction.TransformValue(at, _)        => s"Transform value at ${at.toString}"
        case MigrationAction.Mandate(at, name, _)         => s"Mandate field '$name' at ${at.toString}"
        case MigrationAction.Optionalize(at, name)        => s"Optionalize field '$name' at ${at.toString}"
        case MigrationAction.Join(at, sources, target, _) =>
          s"Join ${sources.mkString(", ")} into '$target' at ${at.toString}"
        case MigrationAction.Split(at, source, targets, _) =>
          s"Split '$source' into ${targets.mkString(", ")} at ${at.toString}"
        case MigrationAction.ChangeType(at, name, _)    => s"Change type of '$name' at ${at.toString}"
        case MigrationAction.RenameCase(at, from, to)   => s"Rename case '$from' to '$to' at ${at.toString}"
        case MigrationAction.TransformCase(at, name, _) => s"Transform case '$name' at ${at.toString}"
        case MigrationAction.TransformElements(at, _)   => s"Transform elements at ${at.toString}"
        case MigrationAction.TransformKeys(at, _)       => s"Transform keys at ${at.toString}"
        case MigrationAction.TransformValues(at, _)     => s"Transform values at ${at.toString}"
      }.mkString(" → ")
}

object DynamicMigration {

  /** An empty migration that performs no transformations. Identity element. */
  val empty: DynamicMigration = DynamicMigration(Vector.empty)

  /** Create a migration from a single action. */
  def single(action: MigrationAction): DynamicMigration =
    DynamicMigration(Vector(action))

  /** Create a migration from multiple actions. */
  def fromActions(actions: MigrationAction*): DynamicMigration =
    DynamicMigration(actions.toVector)
}
