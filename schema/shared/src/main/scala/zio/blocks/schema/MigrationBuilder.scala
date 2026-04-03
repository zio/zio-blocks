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
 * A builder for creating typed [[Migration]] instances using a fluent DSL.
 *
 * MigrationBuilder provides a type-safe way to construct migrations between
 * schema versions. All operations are path-based using [[DynamicOptic]], and
 * the builder maintains type information for the source and target types.
 *
 * ==Example Usage==
 *
 * {{{
 * val migration = MigrationBuilder[PersonV0, Person]
 *   .addField(DynamicOptic.root.field("age"), DynamicValue.Primitive(PrimitiveValue.Int(0)))
 *   .renameField(DynamicOptic.root.field("firstName"), "fullName")
 *   .build
 * }}}
 *
 * @tparam A
 *   the source type
 * @tparam B
 *   the target type
 */
final class MigrationBuilder[A, B] private (
  private val actions: Chunk[MigrationAction],
  private val sourceSchema: Schema[A],
  private val targetSchema: Schema[B]
) { self =>

  // ═══════════════════════════════════════════════════════════════════════════════
  // Record Operations
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Adds a new field to the target record with a default value.
   *
   * @param at
   *   the path where the field should be added
   * @param default
   *   the default value for the new field
   * @return
   *   a new builder with the AddField action added
   */
  def addField(at: DynamicOptic, default: DynamicValue): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.AddField(at, default), sourceSchema, targetSchema)

  /**
   * Adds a new field with a typed default value.
   *
   * @param at
   *   the path where the field should be added
   * @param default
   *   the default value for the new field
   * @param schema
   *   the schema for the default value type
   * @return
   *   a new builder with the AddField action added
   */
  def addField[T](at: DynamicOptic, default: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] =
    addField(at, schema.toDynamicValue(default))

  /**
   * Drops a field from the source record.
   *
   * @param at
   *   the path to the field to drop
   * @param defaultForReverse
   *   the default value to use when reversing the migration
   * @return
   *   a new builder with the DropField action added
   */
  def dropField(at: DynamicOptic, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.DropField(at, defaultForReverse), sourceSchema, targetSchema)

  /**
   * Drops a field with a typed default value for reverse migration.
   */
  def dropField[T](at: DynamicOptic, defaultForReverse: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] =
    dropField(at, schema.toDynamicValue(defaultForReverse))

  /**
   * Renames a field from one name to another.
   *
   * @param from
   *   the path to the field with the old name
   * @param to
   *   the new field name
   * @return
   *   a new builder with the Rename action added
   */
  def renameField(from: DynamicOptic, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.Rename(from, to), sourceSchema, targetSchema)

  /**
   * Transforms a value at the specified path using a pure transformation.
   *
   * @param at
   *   the path to the value to transform
   * @param transform
   *   the transformation to apply
   * @return
   *   a new builder with the TransformValue action added
   */
  def transformValue(at: DynamicOptic, transform: DynamicTransform): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.TransformValue(at, transform), sourceSchema, targetSchema)

  /**
   * Transforms a value at the specified path using a typed Transform.
   */
  def transformValue[T, U](at: DynamicOptic, transform: Transform[T, U]): MigrationBuilder[A, B] =
    transformValue(at, transform.toDynamic)

  /**
   * Converts an optional field to a required field with a default.
   *
   * If the optional value is None, the default is used.
   *
   * @param at
   *   the path to the optional field
   * @param default
   *   the default value if the optional is None
   * @return
   *   a new builder with the Mandate action added
   */
  def mandate(at: DynamicOptic, default: DynamicValue): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.Mandate(at, default), sourceSchema, targetSchema)

  /**
   * Converts an optional field to a required field with a typed default.
   */
  def mandate[T](at: DynamicOptic, default: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] =
    mandate(at, schema.toDynamicValue(default))

  /**
   * Converts a required field to an optional field.
   *
   * The value is wrapped in Some.
   *
   * @param at
   *   the path to the required field
   * @return
   *   a new builder with the Optionalize action added
   */
  def optionalize(at: DynamicOptic): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.Optionalize(at), sourceSchema, targetSchema)

  /**
   * Changes the type of a field using a converter transformation.
   *
   * For primitive-to-primitive conversions only (e.g., String to Int).
   *
   * @param at
   *   the path to the field
   * @param converter
   *   the conversion transformation
   * @return
   *   a new builder with the ChangeType action added
   */
  def changeType(at: DynamicOptic, converter: DynamicTransform): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.ChangeType(at, converter), sourceSchema, targetSchema)

  /**
   * Joins multiple source paths into a single value using a combiner.
   *
   * Used for combining fields (e.g., firstName + lastName → fullName).
   *
   * @param at
   *   the target path for the joined value
   * @param sourcePaths
   *   the source paths to join
   * @param combiner
   *   the transformation to combine values
   * @return
   *   a new builder with the Join action added
   */
  def join(at: DynamicOptic, sourcePaths: Chunk[DynamicOptic], combiner: DynamicTransform): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.Join(at, sourcePaths, combiner), sourceSchema, targetSchema)

  /**
   * Joins multiple source paths into a single value (varargs overload).
   */
  def join(at: DynamicOptic, combiner: DynamicTransform, sourcePaths: DynamicOptic*): MigrationBuilder[A, B] =
    join(at, Chunk.from(sourcePaths), combiner)

  /**
   * Splits a single value into multiple target paths.
   *
   * Used for decomposing fields (e.g., fullName → firstName, lastName).
   *
   * @param at
   *   the source path to split
   * @param targetPaths
   *   the target paths for the split values
   * @param splitter
   *   the transformation to split the value
   * @return
   *   a new builder with the Split action added
   */
  def split(at: DynamicOptic, targetPaths: Chunk[DynamicOptic], splitter: DynamicTransform): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.Split(at, targetPaths, splitter), sourceSchema, targetSchema)

  /**
   * Splits a single value into multiple target paths (varargs overload).
   */
  def split(at: DynamicOptic, splitter: DynamicTransform, targetPaths: DynamicOptic*): MigrationBuilder[A, B] =
    split(at, Chunk.from(targetPaths), splitter)

  // ═══════════════════════════════════════════════════════════════════════════════
  // Enum / Variant Operations
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Renames a case in a sum type (enum/sealed trait).
   *
   * @param at
   *   the path to the variant
   * @param from
   *   the current case name
   * @param to
   *   the new case name
   * @return
   *   a new builder with the RenameCase action added
   */
  def renameCase(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.RenameCase(at, from, to), sourceSchema, targetSchema)

  /**
   * Renames a case at the root level.
   */
  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    renameCase(DynamicOptic.root, from, to)

  /**
   * Transforms the contents of a specific case in a sum type.
   *
   * @param at
   *   the path to the variant
   * @param caseName
   *   the name of the case to transform
   * @param caseActions
   *   the actions to apply to the case value
   * @return
   *   a new builder with the TransformCase action added
   */
  def transformCase(at: DynamicOptic, caseName: String, caseActions: MigrationAction*): MigrationBuilder[A, B] =
    new MigrationBuilder(
      actions :+ MigrationAction.TransformCase(at, caseName, Chunk.from(caseActions)),
      sourceSchema,
      targetSchema
    )

  /**
   * Transforms the contents of a case using a nested builder function.
   */
  def transformCase[C, D](
    at: DynamicOptic,
    caseName: String
  )(f: MigrationBuilder[C, D] => MigrationBuilder[C, D]): MigrationBuilder[A, B] = {
    val nestedBuilder = f(new MigrationBuilder[C, D](Chunk.empty, null, null))
    new MigrationBuilder(
      actions :+ MigrationAction.TransformCase(at, caseName, nestedBuilder.actions),
      sourceSchema,
      targetSchema
    )
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // Collection Operations
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Transforms each element in a sequence.
   *
   * @param at
   *   the path to the sequence
   * @param transform
   *   the transformation to apply to each element
   * @return
   *   a new builder with the TransformElements action added
   */
  def transformElements(at: DynamicOptic, transform: DynamicTransform): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.TransformElements(at, transform), sourceSchema, targetSchema)

  /**
   * Transforms all elements at the root level.
   */
  def transformElements(transform: DynamicTransform): MigrationBuilder[A, B] =
    transformElements(DynamicOptic.root, transform)

  // ═══════════════════════════════════════════════════════════════════════════════
  // Map Operations
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Transforms all keys in a map.
   *
   * @param at
   *   the path to the map
   * @param transform
   *   the transformation to apply to each key
   * @return
   *   a new builder with the TransformKeys action added
   */
  def transformKeys(at: DynamicOptic, transform: DynamicTransform): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.TransformKeys(at, transform), sourceSchema, targetSchema)

  /**
   * Transforms all values in a map.
   *
   * @param at
   *   the path to the map
   * @param transform
   *   the transformation to apply to each value
   * @return
   *   a new builder with the TransformValues action added
   */
  def transformValues(at: DynamicOptic, transform: DynamicTransform): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ MigrationAction.TransformValues(at, transform), sourceSchema, targetSchema)

  // ═══════════════════════════════════════════════════════════════════════════════
  // Composition
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Composes this builder with another set of actions.
   */
  def ++(that: MigrationBuilder[A, B]): MigrationBuilder[A, B] =
    new MigrationBuilder(actions ++ that.actions, sourceSchema, targetSchema)

  /**
   * Appends actions from a DynamicMigration.
   */
  def ++(that: DynamicMigration): MigrationBuilder[A, B] =
    new MigrationBuilder(actions ++ that.actions, sourceSchema, targetSchema)

  // ═══════════════════════════════════════════════════════════════════════════════
  // Build Methods
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Builds the final typed Migration with full validation.
   *
   * This method validates that:
   *   - The builder has valid source and target schemas
   *   - Actions reference structurally valid paths
   *
   * @return
   *   a validated Migration[A, B]
   */
  def build: Migration[A, B] = {
    if (sourceSchema == null || targetSchema == null) {
      throw new IllegalStateException("MigrationBuilder requires non-null source and target schemas")
    }
    Migration.fromDynamic(DynamicMigration(actions), sourceSchema, targetSchema)
  }

  /**
   * Builds the migration without full validation.
   *
   * Use this when you want to build a partial migration or when you're
   * confident the migration is correct and want to skip validation overhead.
   *
   * @return
   *   a Migration[A, B] without validation
   */
  def buildPartial: Migration[A, B] =
    Migration.fromDynamic(DynamicMigration(actions), sourceSchema, targetSchema)

  // ═══════════════════════════════════════════════════════════════════════════════
  // Inspection Methods
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Returns the number of actions in this builder.
   */
  def size: Int = actions.length

  /**
   * Returns true if this builder has no actions.
   */
  def isEmpty: Boolean = actions.isEmpty

  /**
   * Returns true if this builder has at least one action.
   */
  def nonEmpty: Boolean = actions.nonEmpty

  /**
   * Returns a view of the actions in this builder.
   */
  def getActions: Chunk[MigrationAction] = actions

  /**
   * Converts this builder to its underlying DynamicMigration.
   */
  def toDynamic: DynamicMigration = DynamicMigration(actions)
}

object MigrationBuilder {

  /**
   * Creates an empty MigrationBuilder.
   */
  def empty[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(Chunk.empty, sourceSchema, targetSchema)

  /**
   * Creates an empty MigrationBuilder.
   *
   * This allows the `MigrationBuilder[A, B]` syntax (no arguments) for
   * starting a migration builder chain.
   */
  def apply[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    empty[A, B]

  /**
   * Creates a MigrationBuilder from existing actions.
   */
  def fromActions[A, B](
    actions: Chunk[MigrationAction]
  )(implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(actions, sourceSchema, targetSchema)

  /**
   * Creates a MigrationBuilder from a DynamicMigration.
   */
  def fromDynamic[A, B](
    dynamic: DynamicMigration
  )(implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(dynamic.actions, sourceSchema, targetSchema)

  /**
   * Creates an identity migration builder that returns the input unchanged.
   */
  def identity[A](implicit schema: Schema[A]): MigrationBuilder[A, A] =
    empty[A, A]
}
