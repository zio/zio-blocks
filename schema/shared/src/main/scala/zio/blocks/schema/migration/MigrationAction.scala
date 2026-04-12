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

import zio.blocks.schema.{DynamicOptic, SchemaExpr}

/**
 * A single migration step that can be applied to a
 * [[zio.blocks.schema.DynamicValue]].
 *
 * Actions are addressed by a [[zio.blocks.schema.DynamicOptic]] path and are
 * expected to be interpreted by [[DynamicMigration]].
 */
sealed trait MigrationAction extends Product with Serializable {
  /** The path this action targets within the dynamic value being migrated. */
  def at: DynamicOptic

  /**
   * A best-effort inverse of this action.
   *
   * Not all actions are perfectly reversible; in those cases, `reverse` may
   * return `this` or a lossy inverse.
   */
  def reverse: MigrationAction
}

// Record actions
/** Adds a record field at `at` using `default` to compute its initial value. */
final case class AddField(at: DynamicOptic, default: SchemaExpr[Any, _]) extends MigrationAction {
  def reverse: MigrationAction = DropField(at, default)
}

/**
 * Drops a record field at `at`.
 *
 * `defaultForReverse` is used when computing the reverse action.
 */
final case class DropField(at: DynamicOptic, defaultForReverse: SchemaExpr[Any, _]) extends MigrationAction {
  def reverse: MigrationAction = AddField(at, defaultForReverse)
}

/**
 * Renames a record field referenced by `at` to `to`.
 *
 * Reverse is best-effort: it only succeeds structurally when `at` ends in a
 * field node.
 */
final case class Rename(at: DynamicOptic, to: String) extends MigrationAction {
  def reverse: MigrationAction = {
    val nodes = at.nodes
    if (nodes.isEmpty) this
    else
      nodes.last match {
        case DynamicOptic.Node.Field(from) =>
          val parent = new DynamicOptic(nodes.dropRight(1))
          Rename(parent.field(to), from)
        case _ =>
          // Best-effort structural reverse: if `at` doesn't end in a field node, we can't compute a rename back.
          this
      }
  }
}

/**
 * Transforms the value at `at` by evaluating `transform` and replacing the
 * existing value.
 *
 * Note: reverse is not automatic; this action is treated as non-invertible.
 */
final case class TransformValue(at: DynamicOptic, transform: SchemaExpr[Any, _]) extends MigrationAction {
  def reverse: MigrationAction = this
}

/**
 * Makes an optional field mandatory (if supported by the interpreter),
 * supplying `default` when the optional is empty.
 */
final case class Mandate(at: DynamicOptic, default: SchemaExpr[Any, _]) extends MigrationAction {
  def reverse: MigrationAction = this
}

/** Makes a mandatory field optional (if supported by the interpreter). */
final case class Optionalize(at: DynamicOptic) extends MigrationAction {
  def reverse: MigrationAction = this
}

/**
 * Joins multiple values into a single value at `at` using `combiner`.
 *
 * This action is a placeholder for a higher-level record join operation.
 */
final case class Join(at: DynamicOptic, sourcePaths: Vector[DynamicOptic], combiner: SchemaExpr[Any, _]) extends MigrationAction {
  def reverse: MigrationAction = this
}

/**
 * Splits the value at `at` into multiple target paths using `splitter`.
 *
 * This action is a placeholder for a higher-level record split operation.
 */
final case class Split(at: DynamicOptic, targetPaths: Vector[DynamicOptic], splitter: SchemaExpr[Any, _]) extends MigrationAction {
  def reverse: MigrationAction = this
}

/**
 * Changes the type at `at` using `converter`.
 *
 * Typically used to represent primitive conversions (e.g. `Int` -> `Long`).
 */
final case class ChangeType(at: DynamicOptic, converter: SchemaExpr[Any, _]) extends MigrationAction {
  def reverse: MigrationAction = this
}

// Enum actions
/** Renames an enum case from `from` to `to` at the given `at` path. */
final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
  def reverse: MigrationAction = RenameCase(at, to, from)
}

/**
 * Applies a nested list of actions when a particular enum case is selected.
 */
final case class TransformCase(at: DynamicOptic, actions: Vector[MigrationAction]) extends MigrationAction {
  def reverse: MigrationAction = TransformCase(at, actions.reverse.map(_.reverse))
}

// Collection / map actions
/** Transforms each element of a collection at `at` using `transform`. */
final case class TransformElements(at: DynamicOptic, transform: SchemaExpr[Any, _]) extends MigrationAction {
  def reverse: MigrationAction = this
}

/** Transforms each key of a map at `at` using `transform`. */
final case class TransformKeys(at: DynamicOptic, transform: SchemaExpr[Any, _]) extends MigrationAction {
  def reverse: MigrationAction = this
}

/** Transforms each value of a map at `at` using `transform`. */
final case class TransformValues(at: DynamicOptic, transform: SchemaExpr[Any, _]) extends MigrationAction {
  def reverse: MigrationAction = this
}

