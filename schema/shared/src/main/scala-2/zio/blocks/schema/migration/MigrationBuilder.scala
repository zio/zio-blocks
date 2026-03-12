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

import scala.language.experimental.macros

import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr}

final class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  private[migration] val actions: Vector[MigrationAction]
) {

  def addField(
    target: B => Any,
    default: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = macro SelectorMacros.addFieldImpl[A, B]

  def dropField(
    source: A => Any,
    defaultForReverse: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = macro SelectorMacros.dropFieldImpl[A, B]

  def renameField(
    from: A => Any,
    to: B => Any
  ): MigrationBuilder[A, B] = macro SelectorMacros.renameFieldImpl[A, B]

  def transformField(
    from: A => Any,
    to: B => Any,
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = macro SelectorMacros.transformFieldImpl[A, B]

  def changeFieldType(
    source: A => Any,
    target: B => Any,
    converter: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = macro SelectorMacros.changeFieldTypeImpl[A, B]

  def mandateField(
    source: A => Option[_],
    target: B => Any,
    default: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = macro SelectorMacros.mandateFieldImpl[A, B]

  def optionalizeField(
    source: A => Any,
    target: B => Option[_]
  ): MigrationBuilder[A, B] = macro SelectorMacros.optionalizeFieldImpl[A, B]

  def transformElements(
    at: A => Any,
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = macro SelectorMacros.transformElementsImpl[A, B]

  def transformKeys(
    at: A => Any,
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = macro SelectorMacros.transformKeysImpl[A, B]

  def transformValues(
    at: A => Any,
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = macro SelectorMacros.transformValuesImpl[A, B]

  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.RenameCase(DynamicOptic.root, from, to))

  def migrateField[F, G](
    selector: A => F,
    migration: Migration[F, G]
  ): MigrationBuilder[A, B] = macro SelectorMacros.migrateFieldImpl[A, B, F, G]

  def build: Migration[A, B] = {
    val errors = MigrationValidation.validate(sourceSchema, targetSchema, actions)
    if (errors.nonEmpty)
      throw MigrationValidation.ValidationError(
        s"Migration validation failed:\n${errors.mkString("  - ", "\n  - ", "")}"
      )
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions))
  }

  def buildPartial: Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions))
}
