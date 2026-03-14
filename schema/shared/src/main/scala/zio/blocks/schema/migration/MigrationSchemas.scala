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

import zio.blocks.schema.Schema

/**
 * Mathematical proof of serializability. The compiler will reject
 * Schema.derived if any closure, user-defined function, or non-serializable
 * type leaks into the DynamicMigration ADT.
 */
object MigrationSchemas {

  implicit lazy val dynamicOpticSchema: Schema[DynamicOptic] =
    Schema.derived[DynamicOptic]

  implicit lazy val dynamicSchemaExprSchema: Schema[DynamicSchemaExpr] =
    Schema.derived[DynamicSchemaExpr]

  implicit lazy val migrationActionSchema: Schema[MigrationAction] =
    Schema.derived[MigrationAction]

  implicit lazy val dynamicMigrationSchema: Schema[DynamicMigration] =
    Schema.derived[DynamicMigration]
}
