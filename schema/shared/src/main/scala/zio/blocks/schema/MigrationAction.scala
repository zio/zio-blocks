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

package zio.blocks.schema

import zio.blocks.chunk.Chunk

/**
 * A migration action represents a single transformation step in a migration.
 *
 * All actions operate at a path represented by [[DynamicOptic]], enabling
 * precise targeting of nested structures.
 *
 * Actions are fully serializable as pure data, containing no user functions or
 * closures.
 */
sealed trait MigrationAction extends Product with Serializable {

  /** The path where this action applies. */
  def at: DynamicOptic

  /** The structural reverse of this action. */
  def reverse: MigrationAction
}

object MigrationAction {

  // ═══════════════════════════════════════════════════════════════════════════════
  // Record Actions
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Adds a new field to a record with a default value.
   *
   * The reverse action is [[DropField]] with the same default.
   */
  final case class AddField(
    at: DynamicOptic,
    default: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, default)
  }

  /**
   * Removes a field from a record.
   *
   * The default value is used when reversing the migration to reconstruct the
   * dropped field.
   */
  final case class DropField(
    at: DynamicOptic,
    defaultForReverse: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  /**
   * Renames a field within a record.
   *
   * The reverse action renames the field back to its original name.
   */
  final case class Rename(
    at: DynamicOptic,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = {
      val parentPath = DynamicOptic(at.nodes.dropRight(1))
      Rename(parentPath.field(to), fieldNameFromPath(at))
    }
  }

  /**
   * Transforms a value at the specified path using a pure expression.
   *
   * The transform expression must be a primitive-to-primitive transformation.
   */
  final case class TransformValue(
    at: DynamicOptic,
    transform: DynamicTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, transform.reverse)
  }

  /**
   * Converts an optional field to a required field with a default.
   *
   * If the optional value is None, the default is used.
   */
  final case class Mandate(
    at: DynamicOptic,
    default: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }

  /**
   * Converts a required field to an optional field.
   *
   * The value is wrapped in Some.
   */
  final case class Optionalize(
    at: DynamicOptic
  ) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, DynamicValue.Null)
  }

  /**
   * Joins multiple source paths into a single value using a combiner.
   *
   * Used for combining fields (e.g., firstName + lastName → fullName).
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Chunk[DynamicOptic],
    combiner: DynamicTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = Split(at, sourcePaths, combiner.reverse)
  }

  /**
   * Splits a single value into multiple target paths using a splitter.
   *
   * Used for decomposing fields (e.g., fullName → firstName, lastName).
   */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Chunk[DynamicOptic],
    splitter: DynamicTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = Join(at, targetPaths, splitter.reverse)
  }

  /**
   * Changes the type of a field using a converter expression.
   *
   * For primitive-to-primitive conversions only (e.g., String to Int).
   */
  final case class ChangeType(
    at: DynamicOptic,
    converter: DynamicTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(at, converter.reverse)
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // Enum Actions
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Renames a case in a sum type (enum/sealed trait).
   *
   * The reverse action renames the case back.
   */
  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /**
   * Transforms the contents of a specific case in a sum type.
   *
   * The actions are applied to the case value.
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Chunk[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, caseName, actions.map(_.reverse))
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // Collection / Map Actions
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Transforms each element in a sequence.
   *
   * The transform is applied to all elements at the path.
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: DynamicTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, transform.reverse)
  }

  /**
   * Transforms all keys in a map.
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: DynamicTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, transform.reverse)
  }

  /**
   * Transforms all values in a map.
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: DynamicTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, transform.reverse)
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // Helper Methods
  // ═══════════════════════════════════════════════════════════════════════════════

  private def fieldNameFromPath(optic: DynamicOptic): String =
    optic.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _                                   => ""
    }
}
