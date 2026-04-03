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

import zio.blocks.schema.{Schema, DynamicOptic, SchemaExpr, DynamicValue}

class MigrationBuilder[Source, Target, Current](
  sourceSchema: Schema[Source],
  targetSchema: Schema[Target],
  val dynamicMigration: DynamicMigration
) {
  // Methods for building migrations dynamically
  def addField[F](at: DynamicOptic, default: SchemaExpr[Any, Any]): MigrationBuilder[Source, Target, Current] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      dynamicMigration ++ DynamicMigration(Vector(MigrationAction.AddField(at, default)))
    )

  def dropField(at: DynamicOptic): MigrationBuilder[Source, Target, Current] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      dynamicMigration ++ DynamicMigration(
        Vector(MigrationAction.DropField(at, SchemaExpr.DefaultValue(Schema[DynamicValue])))
      )
    )

  def rename(at: DynamicOptic, to: String): MigrationBuilder[Source, Target, Current] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      dynamicMigration ++ DynamicMigration(Vector(MigrationAction.Rename(at, to)))
    )

  def mandate(at: DynamicOptic, default: SchemaExpr[Any, Any]): MigrationBuilder[Source, Target, Current] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      dynamicMigration ++ DynamicMigration(Vector(MigrationAction.Mandate(at, default)))
    )

  def optionalize(at: DynamicOptic): MigrationBuilder[Source, Target, Current] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      dynamicMigration ++ DynamicMigration(Vector(MigrationAction.Optionalize(at)))
    )

  def renameCase(at: DynamicOptic, from: String, to: String): MigrationBuilder[Source, Target, Current] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      dynamicMigration ++ DynamicMigration(Vector(MigrationAction.RenameCase(at, from, to)))
    )

  def transformCase(at: DynamicOptic, actions: DynamicMigration): MigrationBuilder[Source, Target, Current] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      dynamicMigration ++ DynamicMigration(Vector(MigrationAction.TransformCase(at, actions.actions)))
    )

  def buildPartial: Migration[Source, Target] = Migration(sourceSchema, targetSchema, dynamicMigration)
}

object MigrationBuilder {
  def apply[Source, Target, Current](
    sourceSchema: Schema[Source],
    targetSchema: Schema[Target],
    dynamicMigration: DynamicMigration
  ): MigrationBuilder[Source, Target, Current] =
    new MigrationBuilder(sourceSchema, targetSchema, dynamicMigration)
}
