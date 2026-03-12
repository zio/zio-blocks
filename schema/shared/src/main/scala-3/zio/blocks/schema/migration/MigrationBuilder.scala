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

final class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  private[migration] val actions: Vector[MigrationAction]
) {

  inline def addField(
    inline target: B => Any,
    default: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val path = SelectorMacros.extractPath(target)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.AddField(path, default.toDynamic))
  }

  inline def dropField(
    inline source: A => Any,
    defaultForReverse: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val path = SelectorMacros.extractPath(source)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.DropField(path, defaultForReverse.toDynamic)
    )
  }

  inline def renameField(
    inline from: A => Any,
    inline to: B => Any
  ): MigrationBuilder[A, B] = {
    val fromPath = SelectorMacros.extractPath(from)
    val toPath   = SelectorMacros.extractPath(to)
    val toName   = toPath.nodes.last match {
      case DynamicOptic.Node.Field(name) => name
      case _                             => throw new IllegalArgumentException("Target selector must select a field")
    }
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Rename(fromPath, toName))
  }

  inline def transformField(
    inline from: A => Any,
    @scala.annotation.unused inline to: B => Any,
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val fromPath = SelectorMacros.extractPath(from)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformValue(fromPath, transform.toDynamic)
    )
  }

  inline def changeFieldType(
    inline source: A => Any,
    @scala.annotation.unused inline target: B => Any,
    converter: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val fromPath = SelectorMacros.extractPath(source)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.ChangeType(fromPath, converter.toDynamic)
    )
  }

  inline def mandateField(
    inline source: A => Option[?],
    @scala.annotation.unused inline target: B => Any,
    default: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val path = SelectorMacros.extractPath(source)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Mandate(path, default.toDynamic))
  }

  inline def optionalizeField(
    inline source: A => Any,
    @scala.annotation.unused inline target: B => Option[?]
  ): MigrationBuilder[A, B] = {
    val path = SelectorMacros.extractPath(source)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Optionalize(path))
  }

  inline def transformElements(
    inline at: A => Any,
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val path = SelectorMacros.extractPath(at)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformElements(path, transform.toDynamic)
    )
  }

  inline def transformKeys(
    inline at: A => Any,
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val path = SelectorMacros.extractPath(at)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformKeys(path, transform.toDynamic)
    )
  }

  inline def transformValues(
    inline at: A => Any,
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val path = SelectorMacros.extractPath(at)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformValues(path, transform.toDynamic)
    )
  }

  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.RenameCase(DynamicOptic.root, from, to))

  inline def migrateField[F, G](
    inline selector: A => F,
    migration: Migration[F, G]
  ): MigrationBuilder[A, B] = {
    val path = SelectorMacros.extractPath(selector)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.ApplyMigration(path, migration.dynamicMigration)
    )
  }

  @scala.annotation.targetName("migrateFieldImplicit")
  inline def migrateField[F, G](
    inline selector: A => F
  )(implicit migration: Migration[F, G]): MigrationBuilder[A, B] = {
    val path = SelectorMacros.extractPath(selector)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.ApplyMigration(path, migration.dynamicMigration)
    )
  }

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
