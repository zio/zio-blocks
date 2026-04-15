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
 * An individual atomic migration action. Each action carries a [[DynamicOptic]]
 * path designating where in the structure to apply the action.
 *
 * Actions are pure data and fully serializable (nested transforms are expressed
 * as [[DynamicMigration]] rather than Scala functions).
 */
sealed trait MigrationAction {
  def path: DynamicOptic
}

object MigrationAction {

  // ---------------------------------------------------------------------------
  // Record field operations
  // ---------------------------------------------------------------------------

  /** Add a new field with the given name and default value to a record. */
  final case class AddField(
    path: DynamicOptic,
    fieldName: String,
    defaultValue: DynamicValue
  ) extends MigrationAction

  /** Drop a field from a record. */
  final case class DropField(
    path: DynamicOptic,
    fieldName: String
  ) extends MigrationAction

  /** Rename a field in a record. */
  final case class RenameField(
    path: DynamicOptic,
    oldName: String,
    newName: String
  ) extends MigrationAction

  /** Set the value at a path to a fixed value. */
  final case class SetValue(
    path: DynamicOptic,
    value: DynamicValue
  ) extends MigrationAction

  // ---------------------------------------------------------------------------
  // Value transformation
  // ---------------------------------------------------------------------------

  /** Apply a nested migration to the value at the given path. */
  final case class TransformValue(
    path: DynamicOptic,
    migration: DynamicMigration
  ) extends MigrationAction

  // ---------------------------------------------------------------------------
  // Variant/enum operations
  // ---------------------------------------------------------------------------

  /** Rename a variant case. */
  final case class RenameCase(
    path: DynamicOptic,
    oldName: String,
    newName: String
  ) extends MigrationAction

  /** Apply a nested migration to the value inside a specific variant case. */
  final case class TransformCase(
    path: DynamicOptic,
    caseName: String,
    migration: DynamicMigration
  ) extends MigrationAction

  // ---------------------------------------------------------------------------
  // Collection operations
  // ---------------------------------------------------------------------------

  /** Apply a nested migration to every element of a sequence. */
  final case class TransformElements(
    path: DynamicOptic,
    migration: DynamicMigration
  ) extends MigrationAction

  /** Apply a nested migration to every key of a map. */
  final case class TransformKeys(
    path: DynamicOptic,
    migration: DynamicMigration
  ) extends MigrationAction

  /** Apply a nested migration to every value of a map. */
  final case class TransformValues(
    path: DynamicOptic,
    migration: DynamicMigration
  ) extends MigrationAction

  // ---------------------------------------------------------------------------
  // Field ordering
  // ---------------------------------------------------------------------------

  /** Reorder the fields of a record to match the given order. */
  final case class ReorderFields(
    path: DynamicOptic,
    fieldOrder: IndexedSeq[String]
  ) extends MigrationAction
}
