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
 * Builder for constructing a Migration[A, B] by appending MigrationActions.
 * Paths are specified via DynamicOptic (e.g. DynamicOptic.root.field("name")).
 * Selector-based API (S => A) requires macro support and is not yet implemented.
 */
final case class MigrationBuilder[A, B](actions: Chunk[MigrationAction]) {

  def addField(at: DynamicOptic, value: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.AddField(at, value))

  def dropField(at: DynamicOptic, defaultForReverse: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.DropField(at, defaultForReverse))

  def renameField(from: DynamicOptic, to: DynamicOptic): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RenameField(from, to))

  def transformValue(at: DynamicOptic, transform: MigrationExpr, inverseTransform: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValue(at, transform, inverseTransform))

  def optionalize(at: DynamicOptic, defaultForReverse: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Optionalize(at, defaultForReverse))

  def mandate(at: DynamicOptic): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Mandate(at))

  def join(
    left: DynamicOptic,
    right: DynamicOptic,
    into: DynamicOptic,
    transform: MigrationExpr,
    inverseLeft: MigrationExpr,
    inverseRight: MigrationExpr
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Join(left, right, into, transform, inverseLeft, inverseRight))

  def split(
    from: DynamicOptic,
    intoLeft: DynamicOptic,
    intoRight: DynamicOptic,
    leftExpr: MigrationExpr,
    rightExpr: MigrationExpr,
    inverseTransform: MigrationExpr
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Split(from, intoLeft, intoRight, leftExpr, rightExpr, inverseTransform))

  def renameCase(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RenameCase(at, from, to))

  def changeFieldType(at: DynamicOptic, converter: MigrationExpr, inverseConverter: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.ChangeFieldType(at, converter, inverseConverter))

  def build(implicit sourceSchema: Schema[A], targetSchema: Schema[B]): Migration[A, B] =
    Migration(sourceSchema, targetSchema, DynamicMigration(actions))

  /** Same as build until macro-based structural validation is implemented. */
  def buildPartial(implicit sourceSchema: Schema[A], targetSchema: Schema[B]): Migration[A, B] =
    build
}

object MigrationBuilder {

  def apply[A, B]: MigrationBuilder[A, B] = MigrationBuilder(Chunk.empty)
}
