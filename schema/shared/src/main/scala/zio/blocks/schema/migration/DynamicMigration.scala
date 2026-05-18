/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue

/**
 * An untyped, fully serializable migration that operates on [[DynamicValue]].
 *
 * `DynamicMigration` is the pure-data core of the migration system. It contains
 * a sequence of [[MigrationAction]]s that are applied sequentially to transform
 * data from one schema version to another.
 *
 * Key properties:
 *   - '''Fully serializable''': No user functions, closures, or runtime code
 *     generation.
 *   - '''Composable''': Migrations can be sequentially composed with `++`.
 *   - '''Reversible''': Every migration has a structural reverse via `reverse`.
 *   - '''Introspectable''': The ADT can be inspected, transformed, and used to
 *     generate DDL, upgraders, downgraders, etc.
 *
 * Laws:
 *   - '''Identity''': `DynamicMigration.identity.apply(v) == Right(v)`
 *   - '''Associativity''': `(m1 ++ m2) ++ m3` behaves the same as
 *     `m1 ++ (m2 ++ m3)`
 *   - '''Structural Reverse''': `m.reverse.reverse == m`
 */
case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Applies this migration to a [[DynamicValue]], executing all actions
   * sequentially.
   *
   * @param value
   *   the input value to migrate
   * @return
   *   Right(migrated) on success, Left(error) on first failure
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    var current: DynamicValue = value
    val iter                  = actions.iterator
    while (iter.hasNext) {
      iter.next().apply(current) match {
        case Right(next) => current = next
        case left        => return left
      }
    }
    Right(current)
  }

  /**
   * Composes this migration with another, creating a new migration that first
   * applies this migration's actions, then the other's.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(this.actions ++ that.actions)

  /** Alias for `++`. */
  def andThen(that: DynamicMigration): DynamicMigration = this ++ that

  /**
   * Returns the structural reverse of this migration. Each action is reversed
   * and the order is reversed.
   *
   * Law: `m.reverse.reverse == m`
   */
  def reverse: DynamicMigration =
    new DynamicMigration(actions.reverseIterator.map(_.reverse).toVector)

  /** Returns true if this migration has no actions. */
  def isEmpty: Boolean = actions.isEmpty
}

object DynamicMigration {

  /** The identity migration — applies no transformations. */
  val identity: DynamicMigration = new DynamicMigration(Vector.empty)

  /** Creates a migration from a single action. */
  def apply(action: MigrationAction): DynamicMigration =
    new DynamicMigration(Vector(action))

  /** Creates a migration from multiple actions. */
  def apply(actions: MigrationAction*): DynamicMigration =
    new DynamicMigration(actions.toVector)
}
