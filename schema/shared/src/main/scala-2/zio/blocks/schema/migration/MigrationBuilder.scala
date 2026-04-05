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

import zio.blocks.schema._

/**
 * Fluent builder for [[Migration]]. Selector arguments use the same syntax as
 * [[CompanionOptics.optic]]; paths are stored as [[DynamicOptic]] values inside
 * [[MigrationAction]] (not exposed in the public API).
 *
 * Use [[buildPartial]] for an unchecked migration. On Scala 3, use
 * [[TrackedMigrationBuilder]] and its `build` when you need compile-time proof
 * that all product fields are accounted for (flat field lists only).
 */
class MigrationBuilder[A, B, SourceRemainder, TargetRemainder] private (
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) {

  def addField[C](target: B => C, default: MigrationExpr)(implicit
    schemaB: Schema[B]
  ): MigrationBuilder[A, B, SourceRemainder, TargetRemainder] = {
    val at = new CompanionOptics[B] {}.dynamicOptic(target)(schemaB)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.AddField(at, default))
  }

  def dropField[C](source: A => C, defaultForReverse: MigrationExpr)(implicit
    schemaA: Schema[A]
  ): MigrationBuilder[A, B, SourceRemainder, TargetRemainder] = {
    val at = new CompanionOptics[A] {}.dynamicOptic(source)(schemaA)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.DropField(at, defaultForReverse))
  }

  def renameField[C1, C2](from: A => C1, to: B => C2)(implicit
    schemaA: Schema[A],
    schemaB: Schema[B]
  ): MigrationBuilder[A, B, SourceRemainder, TargetRemainder] = {
    val fromPath = new CompanionOptics[A] {}.dynamicOptic(from)(schemaA)
    val toPath   = new CompanionOptics[B] {}.dynamicOptic(to)(schemaB)
    val newName  = toPath.nodes.lastOption.collect { case f: DynamicOptic.Node.Field => f.name }.getOrElse("")
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Rename(fromPath, newName))
  }

  /**
   * Transform the target field using `transform`. To read the source field,
   * include [[MigrationExpr.RootPath]] with the same path as `from` inside
   * `transform` (built with [[CompanionOptics.dynamicOptic]] on the source
   * schema).
   */
  def transformField[C1, C2](from: A => C1, to: B => C2, transform: MigrationExpr)(implicit
    schemaA: Schema[A],
    schemaB: Schema[B]
  ): MigrationBuilder[A, B, SourceRemainder, TargetRemainder] = {
    val _      = (from, schemaA)
    val toPath = new CompanionOptics[B] {}.dynamicOptic(to)(schemaB)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformValue(toPath, transform))
  }

  def mandateField[C1, C2](source: A => C1, target: B => C2, default: MigrationExpr)(implicit
    schemaA: Schema[A],
    schemaB: Schema[B]
  ): MigrationBuilder[A, B, SourceRemainder, TargetRemainder] = {
    val sourcePath = new CompanionOptics[A] {}.dynamicOptic(source)(schemaA)
    val targetPath = new CompanionOptics[B] {}.dynamicOptic(target)(schemaB)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Mandate(targetPath, default))
  }

  def optionalizeField[C1, C2](source: A => C1, target: B => C2)(implicit
    schemaA: Schema[A],
    schemaB: Schema[B]
  ): MigrationBuilder[A, B, SourceRemainder, TargetRemainder] = {
    val sourcePath = new CompanionOptics[A] {}.dynamicOptic(source)(schemaA)
    val targetPath = new CompanionOptics[B] {}.dynamicOptic(target)(schemaB)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Optionalize(targetPath))
  }

  def changeFieldType[C1, C2](source: A => C1, target: B => C2, converter: MigrationExpr)(implicit
    schemaA: Schema[A],
    schemaB: Schema[B]
  ): MigrationBuilder[A, B, SourceRemainder, TargetRemainder] = {
    val targetPath = new CompanionOptics[B] {}.dynamicOptic(target)(schemaB)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.ChangeType(targetPath, converter))
  }

  def renameCase(from: String, to: String): MigrationBuilder[A, B, SourceRemainder, TargetRemainder] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.RenameCase(DynamicOptic.root, from, to))

  /**
   * Enum case rename at an explicit path (internal; prefer [[renameCase]] at
   * root).
   */
  private[migration] def renameCaseAt(
    from: String,
    to: String,
    at: DynamicOptic
  ): MigrationBuilder[A, B, SourceRemainder, TargetRemainder] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.RenameCase(at, from, to))

  def transformElements[C](at: A => Vector[?], transform: MigrationExpr)(implicit
    schemaA: Schema[A]
  ): MigrationBuilder[A, B, SourceRemainder, TargetRemainder] = {
    val path = new CompanionOptics[A] {}.dynamicOptic(at.asInstanceOf[A => C])(schemaA)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformElements(path, transform))
  }

  def transformKeys[C](at: A => Map[?, ?], transform: MigrationExpr)(implicit
    schemaA: Schema[A]
  ): MigrationBuilder[A, B, SourceRemainder, TargetRemainder] = {
    val path = new CompanionOptics[A] {}.dynamicOptic(at.asInstanceOf[A => C])(schemaA)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformKeys(path, transform))
  }

  def transformValues[C](at: A => Map[?, ?], transform: MigrationExpr)(implicit
    schemaA: Schema[A]
  ): MigrationBuilder[A, B, SourceRemainder, TargetRemainder] = {
    val path = new CompanionOptics[A] {}.dynamicOptic(at.asInstanceOf[A => C])(schemaA)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformValues(path, transform))
  }

  def joinField(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: MigrationExpr
  ): MigrationBuilder[A, B, SourceRemainder, TargetRemainder] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Join(at, sourcePaths, combiner))

  def splitField(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: MigrationExpr
  ): MigrationBuilder[A, B, SourceRemainder, TargetRemainder] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Split(at, targetPaths, splitter))

  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
}

object MigrationBuilder {

  type Complete = Any

  def apply[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, Complete, Complete] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}
