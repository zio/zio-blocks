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

/**
 * Scala 2 version-specific surface for [[MigrationBuilder]].
 *
 * The Scala 3 migration DSL uses inline selector macros; the Scala 2 DSL will
 * be provided via macros in a Scala 2-specific implementation.
 */
trait MigrationBuilderVersionSpecific {
  def apply[A, B](
    sourceSchema: zio.blocks.schema.Schema[A],
    targetSchema: zio.blocks.schema.Schema[B]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sourceSchema, targetSchema, Vector.empty)
}
