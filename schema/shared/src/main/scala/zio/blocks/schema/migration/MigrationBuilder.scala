/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
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

import zio.blocks.schema.{DynamicValue, PrimitiveValue, Schema}

/**
 * A builder for constructing migrations with fluent API.
 *
 * This builder provides a string-based API for backward compatibility. For the
 * new selector-based API with compile-time safety, use `TypedMigrationBuilder`.
 *
 * @tparam A
 *   Source type
 * @tparam B
 *   Target type
 */
final case class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction]
) {

  // ===========================================================================
  // Record Operations (String-based API for backward compatibility)
  // ===========================================================================

  /**
   * Add a field with a default value.
   */
  def addField(fieldName: String, default: ResolvedExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.AddField(fieldName, default))

  /**
   * Add a field with a literal string default.
   */
  def addFieldString(fieldName: String, default: String): MigrationBuilder[A, B] =
    addField(fieldName, ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(default))))

  /**
   * Add a field with a literal int default.
   */
  def addFieldInt(fieldName: String, default: Int): MigrationBuilder[A, B] =
    addField(fieldName, ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(default))))

  /**
   * Add a field with a literal boolean default.
   */
  def addFieldBoolean(fieldName: String, default: Boolean): MigrationBuilder[A, B] =
    addField(fieldName, ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(default))))

  /**
   * Drop a field from the record.
   */
  def dropField(fieldName: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.DropField(fieldName, None))

  /**
   * Drop a field with a default for reverse migration.
   */
  def dropField(fieldName: String, defaultForReverse: ResolvedExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.DropField(fieldName, Some(defaultForReverse)))

  /**
   * Rename a field.
   */
  def renameField(from: String, to: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RenameField(from, to))

  /**
   * Transform a field's value.
   */
  def transformField(fieldName: String, transform: ResolvedExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformField(fieldName, transform, None))

  /**
   * Transform a field's value with explicit reverse.
   */
  def transformField(
    fieldName: String,
    transform: ResolvedExpr,
    reverseTransform: ResolvedExpr
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformField(fieldName, transform, Some(reverseTransform)))

  /**
   * Make an optional field mandatory.
   */
  def mandateField(fieldName: String, default: ResolvedExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.MandateField(fieldName, default))

  /**
   * Make a mandatory field optional.
   */
  def optionalizeField(fieldName: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.OptionalizeField(fieldName))

  /**
   * Change a field's type.
   */
  def changeFieldType(fieldName: String, converter: ResolvedExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.ChangeFieldType(fieldName, converter))

  /**
   * Explicitly keep a field unchanged (for field tracking).
   */
  def keepField(fieldName: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.KeepField(fieldName))

  // ===========================================================================
  // Nested Operations
  // ===========================================================================

  /**
   * Apply nested actions to a specific field.
   *
   * This enables nested migrations like:
   * {{{
   *   builder.atField("address")(
   *     _.renameField("street", "streetName")
   *      .addFieldString("zipCode", "00000")
   *   )
   * }}}
   */
  def atField(fieldName: String)(
    nested: MigrationBuilder[A, B] => MigrationBuilder[A, B]
  ): MigrationBuilder[A, B] = {
    val nestedBuilder = nested(MigrationBuilder(sourceSchema, targetSchema, Vector.empty))
    copy(actions = actions :+ MigrationAction.AtField(fieldName, nestedBuilder.actions))
  }

  /**
   * Apply nested actions to a variant case.
   */
  def atCase(caseName: String)(
    nested: MigrationBuilder[A, B] => MigrationBuilder[A, B]
  ): MigrationBuilder[A, B] = {
    val nestedBuilder = nested(MigrationBuilder(sourceSchema, targetSchema, Vector.empty))
    copy(actions = actions :+ MigrationAction.AtCase(caseName, nestedBuilder.actions))
  }

  /**
   * Apply nested actions to all sequence elements.
   */
  def atElements(
    nested: MigrationBuilder[A, B] => MigrationBuilder[A, B]
  ): MigrationBuilder[A, B] = {
    val nestedBuilder = nested(MigrationBuilder(sourceSchema, targetSchema, Vector.empty))
    copy(actions = actions :+ MigrationAction.AtElements(nestedBuilder.actions))
  }

  /**
   * Apply nested actions to all map keys.
   */
  def atMapKeys(
    nested: MigrationBuilder[A, B] => MigrationBuilder[A, B]
  ): MigrationBuilder[A, B] = {
    val nestedBuilder = nested(MigrationBuilder(sourceSchema, targetSchema, Vector.empty))
    copy(actions = actions :+ MigrationAction.AtMapKeys(nestedBuilder.actions))
  }

  /**
   * Apply nested actions to all map values.
   */
  def atMapValues(
    nested: MigrationBuilder[A, B] => MigrationBuilder[A, B]
  ): MigrationBuilder[A, B] = {
    val nestedBuilder = nested(MigrationBuilder(sourceSchema, targetSchema, Vector.empty))
    copy(actions = actions :+ MigrationAction.AtMapValues(nestedBuilder.actions))
  }

  // ===========================================================================
  // Enum Operations
  // ===========================================================================

  /**
   * Rename a variant case.
   */
  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RenameCase(from, to))

  /**
   * Transform a variant case.
   */
  def transformCase(caseName: String)(
    nested: MigrationBuilder[A, B] => MigrationBuilder[A, B]
  ): MigrationBuilder[A, B] = {
    val nestedBuilder = nested(MigrationBuilder(sourceSchema, targetSchema, Vector.empty))
    copy(actions = actions :+ MigrationAction.TransformCase(caseName, nestedBuilder.actions))
  }

  // ===========================================================================
  // Collection Operations
  // ===========================================================================

  /**
   * Transform all sequence elements.
   */
  def transformElements(transform: ResolvedExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformElements(transform, None))

  /**
   * Transform all map keys.
   */
  def transformKeys(transform: ResolvedExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformKeys(transform, None))

  /**
   * Transform all map values.
   */
  def transformValues(transform: ResolvedExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValues(transform, None))

  // ===========================================================================
  // Join/Split Operations
  // ===========================================================================

  /**
   * Join multiple fields into one.
   */
  def joinFields(
    sourceFields: Vector[String],
    targetField: String,
    combiner: ResolvedExpr
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.JoinFields(sourceFields, targetField, combiner, None))

  /**
   * Split one field into multiple.
   */
  def splitField(
    sourceField: String,
    targetFields: Vector[String],
    splitter: ResolvedExpr
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.SplitField(sourceField, targetFields, splitter, None))

  // ===========================================================================
  // Build Methods
  // ===========================================================================

  /**
   * Build the migration with full validation.
   */
  def build: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /**
   * Build the migration without full validation.
   */
  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /**
   * Build and return the underlying DynamicMigration.
   */
  def buildDynamic: DynamicMigration =
    DynamicMigration(actions)

}

object MigrationBuilder {

  /**
   * Create a new builder for migrating from A to B.
   */
  def apply[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    MigrationBuilder(sourceSchema, targetSchema, Vector.empty)

  /**
   * Create a builder with field tracking enabled.
   */
  def withFieldTracking[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}
