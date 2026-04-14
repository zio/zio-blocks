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

import zio.blocks.schema.{DynamicOptic, SchemaExpr}

/**
 * Internal helpers that encode migration actions into the builder's type-level
 * `Actions` state.
 *
 * These are normal methods (not macros). The Scala 3 macro DSL calls these
 * methods with string literals so the action state carries literal singleton
 * types (e.g. `"age"`) rather than widened `String & Singleton`.
 */
private[migration] object MigrationBuilderStateOps {

  def addField[A, B, Acts <: Tuple, F <: String & Singleton](
    builder: MigrationBuilder[A, B] { type Actions = Acts },
    at: DynamicOptic,
    fieldName: F,
    default: SchemaExpr[Any, _]
  ): MigrationBuilder[A, B] { type Actions = ("add", F) *: Acts } = {
    val _ = fieldName
    new MigrationBuilder[A, B](builder.sourceSchema, builder.targetSchema, builder.actions :+ AddField(at, default)) {
      type Actions = ("add", F) *: Acts
    }
  }

  def dropField[A, B, Acts <: Tuple, F <: String & Singleton](
    builder: MigrationBuilder[A, B] { type Actions = Acts },
    at: DynamicOptic,
    fieldName: F,
    defaultForReverse: SchemaExpr[Any, _]
  ): MigrationBuilder[A, B] { type Actions = ("drop", F) *: Acts } = {
    val _ = fieldName
    new MigrationBuilder[A, B](
      builder.sourceSchema,
      builder.targetSchema,
      builder.actions :+ DropField(at, defaultForReverse)
    ) {
      type Actions = ("drop", F) *: Acts
    }
  }

  def renameField[A, B, Acts <: Tuple, O <: String & Singleton, N <: String & Singleton](
    builder: MigrationBuilder[A, B] { type Actions = Acts },
    at: DynamicOptic,
    oldName: O,
    newName: N
  ): MigrationBuilder[A, B] { type Actions = ("rename", O, N) *: Acts } = {
    val _ = oldName
    new MigrationBuilder[A, B](builder.sourceSchema, builder.targetSchema, builder.actions :+ Rename(at, newName)) {
      type Actions = ("rename", O, N) *: Acts
    }
  }

  def transformField[A, B, Acts <: Tuple](
    builder: MigrationBuilder[A, B] { type Actions = Acts },
    at: DynamicOptic,
    transform: SchemaExpr[Any, _]
  ): MigrationBuilder[A, B] { type Actions = Acts } =
    new MigrationBuilder[A, B](
      builder.sourceSchema,
      builder.targetSchema,
      builder.actions :+ TransformValue(at, transform)
    ) {
      type Actions = Acts
    }
}
