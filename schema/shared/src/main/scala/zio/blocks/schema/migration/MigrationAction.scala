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

import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * A single, pure, serializable migration action that transforms a
 * `DynamicValue` from one schema shape to another.
 *
 * All actions are path-based, using `DynamicOptic` for navigation. Each action
 * also knows how to reverse itself, enabling round-trip and downgrade
 * migrations.
 *
 * `MigrationAction` contains only data (no functions, closures, or
 * reflection), enabling full serializability, inspection, and code generation.
 */
sealed trait MigrationAction {

  /** The path at which this action operates. */
  def at: DynamicOptic

  /**
   * The structural reverse of this action. Applying the reverse is a
   * best-effort semantic inverse: it will succeed when sufficient information
   * is available.
   */
  def reverse: MigrationAction
}

object MigrationAction {

  // ─────────────────────────────────────────────────────────────────────────
  // Record operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Add a new field to a record. The `at` path specifies the new field
   * (the last node must be a `Field` node). The `default` value is used as the
   * field's value.
   *
   * Reverse: `DropField(at, default)`.
   */
  final case class AddField(at: DynamicOptic, default: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, default)
  }

  /**
   * Drop a field from a record. The `at` path specifies the field to remove.
   * The `defaultForReverse` is stored so that the reverse migration can add the
   * field back.
   *
   * Reverse: `AddField(at, defaultForReverse)`.
   */
  final case class DropField(at: DynamicOptic, defaultForReverse: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  /**
   * Rename a field in a record. The `at` path specifies the field to rename
   * (the last node must be a `Field` node with the old name). The `to` string
   * is the new field name.
   *
   * Reverse: renames `to` back to the original name.
   */
  final case class Rename(at: DynamicOptic, to: String) extends MigrationAction {
    def reverse: MigrationAction = {
      val fromName = at.nodes.lastOption match {
        case Some(f: DynamicOptic.Node.Field) => f.name
        case _                                => to
      }
      // Build the reversed path: same prefix, but last node has the new name
      val reversedAt = if (at.nodes.nonEmpty) {
        val prefix = at.nodes.dropRight(1)
        new DynamicOptic(prefix :+ new DynamicOptic.Node.Field(to))
      } else at
      Rename(reversedAt, fromName)
    }
  }

  /**
   * Transform the value at the given path using the provided expression.
   *
   * Reverse: apply `transform.reverse`.
   */
  final case class TransformValue(at: DynamicOptic, transform: DynamicMigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, transform.reverse)
  }

  /**
   * Make an optional field mandatory by unwrapping `Some` and using `default`
   * when the value is `None`.
   *
   * The value at `at` is expected to be a `Variant` with cases `Some` and
   * `None` (the DynamicValue encoding of `Option`).
   *
   * Reverse: `Optionalize(at)`.
   */
  final case class Mandate(at: DynamicOptic, default: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }

  /**
   * Make a mandatory field optional by wrapping its value in `Some`.
   *
   * Reverse: `Mandate(at, DynamicValue.Null)` (best-effort: uses Null as
   * default for the reverse).
   */
  final case class Optionalize(at: DynamicOptic) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, DynamicValue.Null)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Enum / Variant operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Rename a variant case at the given path. Any `Variant` with case name
   * `from` is renamed to `to`.
   *
   * Reverse: `RenameCase(at, to, from)`.
   */
  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /**
   * Apply nested migration actions to the inner value of a specific variant
   * case. Only the case with name `caseName` is transformed; other cases pass
   * through unchanged.
   *
   * Reverse: apply reversed nested actions in reverse order.
   */
  final case class TransformCase(at: DynamicOptic, caseName: String, actions: Vector[MigrationAction])
      extends MigrationAction {
    def reverse: MigrationAction =
      TransformCase(at, caseName, actions.map(_.reverse).reverse)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Collection operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Apply a transformation to every element of a sequence at the given path.
   *
   * Reverse: apply `transform.reverse` to every element.
   */
  final case class TransformElements(at: DynamicOptic, transform: DynamicMigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, transform.reverse)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Map operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Apply a transformation to every key of a map at the given path.
   *
   * Reverse: apply `transform.reverse` to every key.
   */
  final case class TransformKeys(at: DynamicOptic, transform: DynamicMigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, transform.reverse)
  }

  /**
   * Apply a transformation to every value of a map at the given path.
   *
   * Reverse: apply `transform.reverse` to every value.
   */
  final case class TransformValues(at: DynamicOptic, transform: DynamicMigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, transform.reverse)
  }
}
