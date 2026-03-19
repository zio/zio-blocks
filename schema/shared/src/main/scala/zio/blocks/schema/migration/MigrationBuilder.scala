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

/**
 * A fluent builder for constructing [[Migration]]s.
 *
 * The builder accumulates [[MigrationAction]]s and produces a `Migration[A, B]`
 * via [[build]] or [[buildPartial]].
 *
 * Paths are specified using [[DynamicOptic]] directly. A future version will
 * add macro-based selector support for `(A => Any)` style selectors.
 *
 * Example usage:
 * {{{
 * val migration = Migration.newBuilder[PersonV0, Person]
 *   .addField(DynamicOptic.root, "age", DynamicValue.Primitive(PrimitiveValue.Int(0)))
 *   .renameField(DynamicOptic.root, "name", "fullName")
 *   .build
 * }}}
 */
class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) {

  // ─────────────────────────────────────────────────────────────────────────
  // Record Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Adds a new field with a default value.
   *
   * @param at
   *   the record path (e.g., `DynamicOptic.root` for root-level)
   * @param fieldName
   *   the name of the new field
   * @param default
   *   the default value for the new field
   */
  def addField(at: DynamicOptic, fieldName: String, default: DynamicValue): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.AddField(at, fieldName, default))

  /**
   * Drops a field from the record.
   *
   * @param at
   *   the record path
   * @param fieldName
   *   the name of the field to drop
   * @param defaultForReverse
   *   the value to use when reversing (re-adding) the field
   */
  def dropField(
    at: DynamicOptic,
    fieldName: String,
    defaultForReverse: DynamicValue = DynamicValue.Null
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.DropField(at, fieldName, defaultForReverse)
    )

  /**
   * Renames a field.
   *
   * @param at
   *   the record path where the field lives
   * @param from
   *   the current field name
   * @param to
   *   the new field name
   */
  def renameField(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Rename(at, from, to))

  /**
   * Transforms the value at a specific field path.
   *
   * @param at
   *   the path to the value to transform
   * @param newValue
   *   the new value to set
   * @param reverseValue
   *   the value to restore on reverse
   */
  def transformValue(
    at: DynamicOptic,
    newValue: DynamicValue,
    reverseValue: DynamicValue
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformValue(at, newValue, reverseValue)
    )

  /**
   * Makes an optional field mandatory.
   *
   * @param at
   *   the path to the optional field
   * @param default
   *   the default value to use when the field is None/Null
   */
  def mandateField(at: DynamicOptic, default: DynamicValue): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Mandate(at, default))

  /**
   * Makes a mandatory field optional.
   *
   * @param at
   *   the path to the mandatory field
   * @param defaultForReverse
   *   the default value to use for reverse (Mandate) action
   */
  def optionalizeField(at: DynamicOptic, defaultForReverse: DynamicValue = DynamicValue.Null): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Optionalize(at, defaultForReverse))

  /**
   * Changes the type of a field.
   *
   * @param at
   *   the path to the field
   * @param converter
   *   the new converted value
   * @param reverseConverter
   *   the value to restore on reverse
   */
  def changeFieldType(
    at: DynamicOptic,
    converter: DynamicValue,
    reverseConverter: DynamicValue
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.ChangeType(at, converter, reverseConverter)
    )

  /**
   * Joins multiple source fields into a single target field.
   *
   * @param at
   *   the target path for the joined value
   * @param sourcePaths
   *   the paths of the source fields
   * @param combiner
   *   the combined value (pre-computed)
   */
  def joinFields(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: DynamicValue
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Join(at, sourcePaths, combiner)
    )

  /**
   * Splits a single field into multiple target fields.
   *
   * @param at
   *   the source path of the field to split
   * @param targetPaths
   *   the paths for the split results
   * @param splitter
   *   the split value (pre-computed)
   */
  def splitField(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: DynamicValue
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Split(at, targetPaths, splitter)
    )

  // ─────────────────────────────────────────────────────────────────────────
  // Enum Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Renames a case in a variant/enum.
   *
   * @param at
   *   the path to the variant
   * @param from
   *   the current case name
   * @param to
   *   the new case name
   */
  def renameCase(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.RenameCase(at, from, to))

  /**
   * Transforms the inner structure of a specific variant case.
   *
   * @param at
   *   the path to the variant
   * @param caseName
   *   the name of the case to transform
   * @param caseActions
   *   the actions to apply to the case value
   */
  def transformCase(
    at: DynamicOptic,
    caseName: String,
    caseActions: Vector[MigrationAction]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformCase(at, caseName, caseActions)
    )

  // ─────────────────────────────────────────────────────────────────────────
  // Collection / Map Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Transforms all elements in a sequence.
   */
  def transformElements(at: DynamicOptic, elementActions: Vector[MigrationAction]): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformElements(at, elementActions)
    )

  /**
   * Transforms all keys in a map.
   */
  def transformKeys(at: DynamicOptic, keyActions: Vector[MigrationAction]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformKeys(at, keyActions))

  /**
   * Transforms all values in a map.
   */
  def transformValues(at: DynamicOptic, valueActions: Vector[MigrationAction]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformValues(at, valueActions))

  // ─────────────────────────────────────────────────────────────────────────
  // Build
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Builds the migration with full validation. Validates that all required
   * fields in the target schema are accounted for.
   *
   * Note: Full macro-based validation is a future enhancement. Currently
   * performs the same construction as [[buildPartial]].
   */
  def build: Migration[A, B] =
    Migration(new DynamicMigration(actions), sourceSchema, targetSchema)

  /**
   * Builds the migration without full validation. Useful when the migration is
   * known to be correct or when validation is not needed.
   */
  def buildPartial: Migration[A, B] =
    Migration(new DynamicMigration(actions), sourceSchema, targetSchema)
}
