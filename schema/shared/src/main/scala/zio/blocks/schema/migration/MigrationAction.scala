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
 * A pure-data description of a single migration step. Every case is fully
 * serializable — no opaque functions are stored anywhere.
 *
 * A `path` is a sequence of record-field names that navigates from the root
 * [[DynamicValue]] to the target node. An empty path means "the value
 * itself". For variant cases the case name is the last element of a case
 * path.
 */
sealed trait MigrationAction

object MigrationAction {

  // ── No-op ──────────────────────────────────────────────────────────────────

  /** Passes the value through unchanged. */
  case object Identity extends MigrationAction

  // ── Record field operations ─────────────────────────────────────────────────

  /**
   * Remove the record field identified by `path`.
   *
   * `path.init` navigates to the parent record; `path.last` is the field name
   * to drop.
   */
  final case class DropField(path: List[String]) extends MigrationAction

  /**
   * Insert a field at `path` with the constant `value`.
   *
   * `path.init` navigates to the parent record; `path.last` is the new field
   * name.
   */
  final case class AddField(path: List[String], value: DynamicValue) extends MigrationAction

  /**
   * Rename a field from `srcPath.last` to `tgtPath.last` within the same
   * parent record (`srcPath.init` must equal `tgtPath.init`).
   */
  final case class RenameField(srcPath: List[String], tgtPath: List[String]) extends MigrationAction

  /**
   * Apply a [[FieldTransform]] to the primitive value reached by `path`.
   */
  final case class TransformValue(path: List[String], transform: FieldTransform) extends MigrationAction

  // ── Variant case operations ─────────────────────────────────────────────────

  /**
   * Rename a variant case from `srcPath.last` to `tgtPath.last`. The path
   * prefix navigates to the [[DynamicValue.Variant]] node.
   */
  final case class RenameCase(srcPath: List[String], tgtPath: List[String]) extends MigrationAction

  /**
   * Declare that the variant case reached by `casePath` no longer exists in
   * the target schema. Applying this action when the current value IS that
   * case produces a [[zio.blocks.schema.SchemaError]]. When the current case
   * is different this action is a no-op.
   */
  final case class DropCase(casePath: List[String]) extends MigrationAction

  // ── Option operations ───────────────────────────────────────────────────────

  /**
   * Convert an `Option[T]` field at `path` to a plain `T`.
   *
   *  - `Some(v)` → unwrapped `v`
   *  - `None`    → `default`
   */
  final case class Mandate(path: List[String], default: DynamicValue) extends MigrationAction

  /**
   * Convert a plain `T` field at `path` to `Option[T]` by wrapping it in
   * `Some`.
   */
  final case class Optionalize(path: List[String]) extends MigrationAction

  // ── Collection operations ───────────────────────────────────────────────────

  /**
   * Apply `action` to every element of the sequence at `path`.
   */
  final case class TransformElements(path: List[String], action: MigrationAction) extends MigrationAction

  /**
   * Apply `action` to every key of the map at `path`.
   */
  final case class TransformKeys(path: List[String], action: MigrationAction) extends MigrationAction

  /**
   * Apply `action` to every value of the map at `path`.
   */
  final case class TransformValues(path: List[String], action: MigrationAction) extends MigrationAction

  // ── Composition ─────────────────────────────────────────────────────────────

  /**
   * Apply each action in `actions` left-to-right, threading the
   * [[DynamicValue]] through.
   */
  final case class Sequence(actions: List[MigrationAction]) extends MigrationAction
}
