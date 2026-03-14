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

import scala.annotation.unused
import zio.blocks.schema.Schema

/**
 * Builder that bridges compile-time S => A selectors (via [[SelectorMacro]])
 * into [[MigrationAction]] vectors, then produces a [[Migration[A, B]]]. All
 * steps are immutable.
 */
final class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) {

  inline def addField[T](inline target: B => T, default: DynamicSchemaExpr): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.AddField(SelectorMacro.extractPath(target), default)
    )

  inline def dropField[T](
    inline source: A => T,
    defaultForReverse: DynamicSchemaExpr = DynamicSchemaExpr.DefaultValue
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.DropField(SelectorMacro.extractPath(source), defaultForReverse)
    )

  inline def renameField[T](inline from: A => T, inline to: B => T): MigrationBuilder[A, B] = {
    val fromPath = SelectorMacro.extractPath(from)
    val toPath   = SelectorMacro.extractPath(to)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Rename(fromPath, DynamicOptic.terminalName(toPath))
    )
  }

  inline def transformValue[T](inline at: A => T, transform: DynamicSchemaExpr): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformValue(SelectorMacro.extractPath(at), transform)
    )

  inline def changeFieldType[T, U](
    inline source: A => T,
    @unused inline target: B => U,
    converter: DynamicSchemaExpr
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.ChangeType(SelectorMacro.extractPath(source), converter)
    )

  inline def mandateField[T](
    inline source: A => Option[T],
    @unused inline target: B => T,
    default: DynamicSchemaExpr
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Mandate(SelectorMacro.extractPath(source), default)
    )

  inline def optionalizeField[T](
    inline source: A => T,
    @unused inline target: B => Option[T]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Optionalize(SelectorMacro.extractPath(source))
    )

  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  inline def build: Migration[A, B] =
    ${ MigrationMacros.buildImpl[A, B]('this) }
}
