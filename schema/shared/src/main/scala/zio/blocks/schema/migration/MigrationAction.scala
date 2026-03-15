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

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * A single, atomic migration step that operates on a [[DynamicValue]] at a path
 * identified by a [[DynamicOptic]].
 *
 * `MigrationAction` is pure data — no user functions, no closures, no
 * reflection. Every action carries enough information to execute both the
 * forward transformation and its structural reverse.
 *
 * Actions are composed into a [[DynamicMigration]] via [[DynamicMigration.++]].
 */
sealed trait MigrationAction {

  /** The path at which this action operates. */
  def at: DynamicOptic

  /**
   * The structural inverse of this action.
   *
   * Reverse is best-effort: given `m.apply(a) == Right(b)`, it holds that
   * `m.reverse.apply(b) == Right(a)` whenever sufficient information was
   * preserved.
   */
  def reverse: MigrationAction
}

object MigrationAction {

  // ── Record actions ────────────────────────────────────────────────────────

  /**
   * Adds a new field at `at` with the given `defaultValue`.
   *
   * Reverse: [[DropField]] using the same default for potential re-addition.
   */
  final case class AddField(at: DynamicOptic, defaultValue: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, defaultValue)
  }

  /**
   * Removes the field at `at`.
   *
   * `defaultForReverse` is the value used to re-add the field when this action
   * is reversed.
   *
   * Reverse: [[AddField]] with `defaultForReverse`.
   */
  final case class DropField(at: DynamicOptic, defaultForReverse: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  /**
   * Renames the field identified by the last [[DynamicOptic.Node.Field]] in
   * `at` to `to`.
   *
   * Reverse: [[Rename]] using the original field name derived from `at`.
   */
  final case class Rename(at: DynamicOptic, to: String) extends MigrationAction {
    def reverse: MigrationAction = {
      val originalName = at.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(name)) => name
        case _                                   => to
      }
      val parentNodes = if (at.nodes.isEmpty) Chunk.empty[DynamicOptic.Node] else at.nodes.init
      val renamedAt   = new DynamicOptic(parentNodes :+ DynamicOptic.Node.Field(to))
      Rename(renamedAt, originalName)
    }
  }

  /**
   * Replaces the value at `at` with `newValue`.
   *
   * Reverse: [[TransformValue]] with `oldValue` restoring the original.
   */
  final case class TransformValue(at: DynamicOptic, newValue: DynamicValue, oldValue: DynamicValue)
      extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, oldValue, newValue)
  }

  /**
   * Mandates an optional field at `at` by unwrapping the `Option` and replacing
   * absent values with `default`.
   *
   * Reverse: [[Optionalize]].
   */
  final case class Mandate(at: DynamicOptic, default: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at, default)
  }

  /**
   * Makes a non-optional field optional by wrapping it in a `Some` variant.
   *
   * `defaultForReverse` is used to restore the value when reversing.
   *
   * Reverse: [[Mandate]].
   */
  final case class Optionalize(at: DynamicOptic, defaultForReverse: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, defaultForReverse)
  }

  /**
   * Joins multiple source fields (at `sourcePaths`) into a single field at `at`
   * using `combiner`.
   *
   * Constraints: all source and target values must be primitives.
   *
   * Reverse: [[Split]] with the same paths and values.
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = Split(at, sourcePaths, combiner)
  }

  /**
   * Splits a single field at `at` into multiple target fields (at
   * `targetPaths`) using `splitter`.
   *
   * Constraints: source and all target values must be primitives.
   *
   * Reverse: [[Join]] with the same paths and values.
   */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = Join(at, targetPaths, splitter)
  }

  /**
   * Converts the primitive value at `at` from one primitive type to another
   * using `converter`.
   *
   * Constraints: source and target must both be primitive.
   *
   * Reverse: [[ChangeType]] with inverted `converter`.
   */
  final case class ChangeType(at: DynamicOptic, converter: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(at, converter)
  }

  // ── Enum actions ──────────────────────────────────────────────────────────

  /**
   * Renames a variant case from `from` to `to` at the path `at`.
   *
   * Reverse: [[RenameCase]] swapping `from` and `to`.
   */
  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /**
   * Applies a nested sequence of actions to the body of the case `caseName` at
   * `at`.
   *
   * Reverse: [[TransformCase]] with all inner actions reversed in reverse
   * order.
   */
  final case class TransformCase(at: DynamicOptic, caseName: String, actions: Vector[MigrationAction])
      extends MigrationAction {
    def reverse: MigrationAction =
      TransformCase(at, caseName, actions.reverseIterator.map(_.reverse).toVector)
  }

  // ── Collection / Map actions ─────────────────────────────────────────────

  /**
   * Applies `elementTransform` to every element of the sequence at `at`.
   *
   * Reverse: [[TransformElements]] with the inverse transform.
   */
  final case class TransformElements(at: DynamicOptic, elementTransform: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, elementTransform)
  }

  /**
   * Applies `keyTransform` to every key in the map at `at`.
   *
   * Reverse: [[TransformKeys]] with the inverse transform.
   */
  final case class TransformKeys(at: DynamicOptic, keyTransform: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, keyTransform)
  }

  /**
   * Applies `valueTransform` to every value in the map at `at`.
   *
   * Reverse: [[TransformValues]] with the inverse transform.
   */
  final case class TransformValues(at: DynamicOptic, valueTransform: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, valueTransform)
  }
}
