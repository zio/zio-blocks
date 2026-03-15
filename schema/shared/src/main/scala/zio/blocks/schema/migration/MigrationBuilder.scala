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

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}
import zio.blocks.schema.migration.MigrationAction._

/**
 * A fluent, value-level builder for constructing [[Migration]][A, B] and
 * [[DynamicMigration]] instances.
 *
 * All paths are expressed as [[DynamicOptic]] values, which can be constructed
 * via the `DynamicOptic.root.field("name")` API or derived from typed
 * `Optic[A, B]` values using `schema.opticFor(...)`.
 *
 * `MigrationBuilder` is immutable — every method returns a new builder with the
 * additional action appended.
 *
 * {{{
 *   val migration: DynamicMigration =
 *     MigrationBuilder[PersonV1, PersonV2]
 *       .rename(DynamicOptic.root.field("name"), "fullName")
 *       .addField(DynamicOptic.root.field("email"), DynamicValue.string(""))
 *       .buildDynamic
 * }}}
 *
 * @tparam A
 *   The source type of the migration.
 * @tparam B
 *   The target type of the migration.
 */
final class MigrationBuilder[A, B] private (
  private val actions: Vector[MigrationAction]
) {

  /** Add a new field at `at` with the supplied `defaultValue`. */
  def addField(at: DynamicOptic, defaultValue: DynamicValue): MigrationBuilder[A, B] =
    append(AddField(at, defaultValue))

  /** Remove the field at `at`. `defaultForReverse` is used when reversing. */
  def dropField(at: DynamicOptic, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    append(DropField(at, defaultForReverse))

  /** Rename the field identified by `at` to `to`. */
  def rename(at: DynamicOptic, to: String): MigrationBuilder[A, B] =
    append(Rename(at, to))

  /**
   * Replace the value at `at` with `newValue`. `oldValue` is used when
   * reversing.
   */
  def transformValue(at: DynamicOptic, newValue: DynamicValue, oldValue: DynamicValue): MigrationBuilder[A, B] =
    append(TransformValue(at, newValue, oldValue))

  /**
   * Mandate (unwrap) an optional field at `at`. Absent values are replaced with
   * `default`.
   */
  def mandate(at: DynamicOptic, default: DynamicValue): MigrationBuilder[A, B] =
    append(Mandate(at, default))

  /**
   * Wrap the value at `at` in `Some(...)`. `defaultForReverse` is used when
   * reversing (mandating).
   */
  def optionalize(at: DynamicOptic, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    append(Optionalize(at, defaultForReverse))

  /** Rename variant case `from` to `to` at path `at`. */
  def renameCase(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    append(RenameCase(at, from, to))

  /**
   * Apply the actions from `inner` to the body of case `caseName` at `at`.
   */
  def transformCase(at: DynamicOptic, caseName: String, inner: MigrationBuilder[_, _]): MigrationBuilder[A, B] =
    append(TransformCase(at, caseName, inner.actions))

  /**
   * Join source fields at `sourcePaths` into a single field at `at` using
   * `combiner` as the combined value.
   */
  def join(at: DynamicOptic, sourcePaths: Vector[DynamicOptic], combiner: DynamicValue): MigrationBuilder[A, B] =
    append(Join(at, sourcePaths, combiner))

  /**
   * Split the field at `at` into multiple fields at `targetPaths` using
   * `splitter` as the split value descriptor.
   */
  def split(at: DynamicOptic, targetPaths: Vector[DynamicOptic], splitter: DynamicValue): MigrationBuilder[A, B] =
    append(Split(at, targetPaths, splitter))

  /** Convert the primitive value at `at` using `converter`. */
  def changeType(at: DynamicOptic, converter: DynamicValue): MigrationBuilder[A, B] =
    append(ChangeType(at, converter))

  /** Apply `elementTransform` to every element of the sequence at `at`. */
  def transformElements(at: DynamicOptic, elementTransform: DynamicValue): MigrationBuilder[A, B] =
    append(TransformElements(at, elementTransform))

  /** Apply `keyTransform` to every key in the map at `at`. */
  def transformKeys(at: DynamicOptic, keyTransform: DynamicValue): MigrationBuilder[A, B] =
    append(TransformKeys(at, keyTransform))

  /** Apply `valueTransform` to every value in the map at `at`. */
  def transformValues(at: DynamicOptic, valueTransform: DynamicValue): MigrationBuilder[A, B] =
    append(TransformValues(at, valueTransform))

  /**
   * Build a typed [[Migration]][A, B] using the supplied source and target
   * schemas.
   */
  def build(from: Schema[A], to: Schema[B]): Migration[A, B] =
    Migration(DynamicMigration(actions), from, to)

  /** Build an untyped [[DynamicMigration]] from the accumulated actions. */
  def buildDynamic: DynamicMigration =
    DynamicMigration(actions)

  private def append(action: MigrationAction): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ action)
}

object MigrationBuilder {

  /** Start a new empty builder for a migration from `A` to `B`. */
  def apply[A, B]: MigrationBuilder[A, B] = new MigrationBuilder(Vector.empty)
}
