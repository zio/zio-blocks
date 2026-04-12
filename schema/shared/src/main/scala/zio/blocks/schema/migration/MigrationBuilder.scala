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

import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr}

/**
 * A builder for constructing a typed [[Migration]] between `A` and `B`.
 *
 * This builder accumulates a sequence of [[MigrationAction]] values, which will
 * be interpreted by [[DynamicMigration]] at runtime.
 */
class MigrationBuilder[A, B] private[migration] (
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) extends MigrationBuilderBuildVersionSpecific[A, B] { self =>

  /**
   * Type-level migration state used for compile-time validation (Scala 3 only).
   */
  type Actions

  /**
   * Adds a field at `at` and initializes it by evaluating `default`.
   */
  private[migration] def addField(
    at: DynamicOptic,
    default: SchemaExpr[Any, _]
  ): MigrationBuilder[A, B] { type Actions = self.Actions } =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ AddField(at, default))
      .asInstanceOf[MigrationBuilder[A, B] { type Actions = self.Actions }]

  /**
   * Drops the field at `at`.
   *
   * `defaultForReverse` is used when reversing this action.
   */
  private[migration] def dropField(
    at: DynamicOptic,
    defaultForReverse: SchemaExpr[Any, _]
  ): MigrationBuilder[A, B] { type Actions = self.Actions } =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ DropField(at, defaultForReverse))
      .asInstanceOf[MigrationBuilder[A, B] { type Actions = self.Actions }]

  /** Renames the field referenced by `at` to `to`. */
  private[migration] def renameField(at: DynamicOptic, to: String): MigrationBuilder[A, B] {
    type Actions = self.Actions
  } =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ Rename(at, to))
      .asInstanceOf[MigrationBuilder[A, B] { type Actions = self.Actions }]

  /** Transforms the value at `at` using `transform`. */
  private[migration] def transformField(
    at: DynamicOptic,
    transform: SchemaExpr[Any, _]
  ): MigrationBuilder[A, B] { type Actions = self.Actions } =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ TransformValue(at, transform))
      .asInstanceOf[MigrationBuilder[A, B] { type Actions = self.Actions }]

  /**
   * Builds a partial migration.
   *
   * For now, this is the same as [[build]], but reserved for future builder
   * variants that may support partial migrations.
   */
  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
}

object MigrationBuilder extends MigrationBuilderVersionSpecific {
  // implemented in version-specific traits
}
