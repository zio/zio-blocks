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
 * Phantom type tracking which target fields have been accounted for in this
 * builder. Populated via intersection types by selector macros (pending
 * implementation).
 */
sealed trait BuilderState

/**
 * Marks that a target field named `Name` has been addressed by a migration
 * action.
 */
sealed trait HasField[Name <: String] extends BuilderState

/**
 * Builder for constructing a Migration[A, B] by appending MigrationActions.
 *
 * Two path-specification styles are available:
 *   - Selector-based (preferred): `_.fieldName` lambdas converted to
 *     DynamicOptic at compile time via MigrationBuilderVersionSpecific macros.
 *   - Optic-based (internal): explicit `DynamicOptic` values for testing and
 *     low-level use.
 *
 * The `State` phantom type parameter is intended to track which target fields
 * have been addressed at compile time via `HasField` intersection types.
 * Population of `State` currently requires macro support (pending); all methods
 * preserve `State` unchanged for now.
 */
class MigrationBuilder[A, B, State <: BuilderState] private[schema] (val actions: Chunk[MigrationAction])
    extends MigrationBuilderVersionSpecific[A, B, State] {

  def addField(at: DynamicOptic, value: MigrationExpr): MigrationBuilder[A, B, State] =
    new MigrationBuilder(actions :+ MigrationAction.AddField(at, value))

  def dropField(at: DynamicOptic, defaultForReverse: MigrationExpr): MigrationBuilder[A, B, State] =
    new MigrationBuilder(actions :+ MigrationAction.DropField(at, defaultForReverse))

  def renameField(from: DynamicOptic, to: DynamicOptic): MigrationBuilder[A, B, State] =
    new MigrationBuilder(actions :+ MigrationAction.RenameField(from, to))

  def transformValue(
    at: DynamicOptic,
    transform: MigrationExpr,
    inverseTransform: MigrationExpr
  ): MigrationBuilder[A, B, State] =
    new MigrationBuilder(actions :+ MigrationAction.TransformValue(at, transform, inverseTransform))

  def optionalize(at: DynamicOptic, defaultForReverse: MigrationExpr): MigrationBuilder[A, B, State] =
    new MigrationBuilder(actions :+ MigrationAction.Optionalize(at, defaultForReverse))

  def mandate(at: DynamicOptic): MigrationBuilder[A, B, State] =
    new MigrationBuilder(actions :+ MigrationAction.Mandate(at))

  def join(
    left: DynamicOptic,
    right: DynamicOptic,
    into: DynamicOptic,
    transform: MigrationExpr,
    inverseLeft: MigrationExpr,
    inverseRight: MigrationExpr
  ): MigrationBuilder[A, B, State] =
    new MigrationBuilder(actions :+ MigrationAction.Join(left, right, into, transform, inverseLeft, inverseRight))

  def split(
    from: DynamicOptic,
    intoLeft: DynamicOptic,
    intoRight: DynamicOptic,
    leftExpr: MigrationExpr,
    rightExpr: MigrationExpr,
    inverseTransform: MigrationExpr
  ): MigrationBuilder[A, B, State] =
    new MigrationBuilder(
      actions :+ MigrationAction.Split(from, intoLeft, intoRight, leftExpr, rightExpr, inverseTransform)
    )

  def renameCase(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B, State] =
    new MigrationBuilder(actions :+ MigrationAction.RenameCase(at, from, to))

  def changeFieldType(
    at: DynamicOptic,
    converter: MigrationExpr,
    inverseConverter: MigrationExpr
  ): MigrationBuilder[A, B, State] =
    new MigrationBuilder(actions :+ MigrationAction.ChangeFieldType(at, converter, inverseConverter))

  def build(implicit sourceSchema: Schema[A], targetSchema: Schema[B]): Migration[A, B] =
    Migration(sourceSchema, targetSchema, DynamicMigration(actions))

  /** Same as build until macro-based structural validation is implemented. */
  def buildPartial(implicit sourceSchema: Schema[A], targetSchema: Schema[B]): Migration[A, B] =
    build
}

object MigrationBuilder {

  def apply[A, B]: MigrationBuilder[A, B, BuilderState] = new MigrationBuilder(Chunk.empty)
}
