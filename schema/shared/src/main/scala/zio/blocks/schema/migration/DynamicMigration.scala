package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicValue, SchemaError}

/**
 * A fully serializable, untyped migration operating on `DynamicValue`.
 *
 * `DynamicMigration` is a sequence of `MigrationAction` steps applied in order.
 * Because every `MigrationAction` is a case class with no closures, the entire
 * migration is serializable to JSON, binary, or any format with a
 * `Schema[DynamicMigration]` codec.
 *
 * == Algebraic Laws ==
 *
 *   - '''Identity''': `DynamicMigration.identity.migrate(v) == Right(v)` for all
 *     `v`.
 *   - '''Associativity''':
 *     `(a ++ b) ++ c == a ++ (b ++ c)` (both produce identical migration results
 *     for any input).
 *   - '''Composition''':
 *     `(a ++ b).migrate(v) == a.migrate(v).flatMap(b.migrate)`.
 *
 * == Serialization ==
 *
 * `DynamicMigration` contains no `Schema[A]` references, no functions, and no
 * closures. It operates purely on `DynamicValue` + `DynamicOptic` paths. This
 * allows it to be persisted to migration registries, transmitted over the wire,
 * and used to generate SQL DDL/DML.
 */
final case class DynamicMigration(actions: Chunk[MigrationAction]) {

  /**
   * Apply this migration to a `DynamicValue`, threading through each action
   * sequentially. Returns the first error encountered, with path context.
   */
  def migrate(value: DynamicValue): Either[SchemaError, DynamicValue] = {
    var current = value
    val len     = actions.length
    var idx     = 0
    while (idx < len) {
      actions(idx)(current) match {
        case Right(v) => current = v
        case left     => return left
      }
      idx += 1
    }
    Right(current)
  }

  /**
   * Compose two migrations. The result applies `this` first, then `that`.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(actions ++ that.actions)

  /**
   * Compute the structural reverse of this migration.
   *
   * Returns `Some` if every action in this migration is reversible. The
   * reversed migration applies the reversed actions in reverse order, such
   * that `m.reverse.flatMap(_.migrate(m.migrate(v).toOption.get))` recovers
   * the original value for reversible migrations.
   *
   * Returns `None` if any action is irreversible (e.g., dropping a field
   * without a known default).
   */
  def reverse: Option[DynamicMigration] = {
    val reversed = Chunk.newBuilder[MigrationAction]
    // We need to reverse both the order AND each action
    val arr = actions.toArray
    var idx = arr.length - 1
    while (idx >= 0) {
      arr(idx).reverse match {
        case Some(rev) => reversed += rev
        case None      => return None
      }
      idx -= 1
    }
    Some(new DynamicMigration(reversed.result()))
  }

  /**
   * Check if this migration is the identity (no actions).
   */
  def isEmpty: Boolean = actions.isEmpty

  /**
   * The number of actions in this migration.
   */
  def size: Int = actions.length
}

object DynamicMigration {

  /**
   * The identity migration — passes values through unchanged.
   */
  val identity: DynamicMigration = new DynamicMigration(Chunk.empty)

  /**
   * Create a migration from a single action.
   */
  def apply(action: MigrationAction): DynamicMigration =
    new DynamicMigration(Chunk.single(action))

  /**
   * Create a migration from multiple actions.
   */
  def apply(actions: MigrationAction*): DynamicMigration =
    new DynamicMigration(Chunk.fromIterable(actions))
}
