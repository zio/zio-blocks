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

/**
 * Scala 3 specific: Schema.derived instances for migration types.
 * These must be compiled in a separate compilation unit from the macro definitions.
 */
trait MigrationSchemaInstances {

  protected lazy val dynamicMigrationSchema: Schema[DynamicMigration] =
    Schema.derived[DynamicMigration]

  protected lazy val migrationActionSchema: Schema[MigrationAction] =
    Schema.derived[MigrationAction]

  protected lazy val migrationErrorSchema: Schema[MigrationError] =
    Schema.derived[MigrationError]

  protected lazy val dynamicTransformSchema: Schema[DynamicTransform] =
    Schema.derived[DynamicTransform]
}
