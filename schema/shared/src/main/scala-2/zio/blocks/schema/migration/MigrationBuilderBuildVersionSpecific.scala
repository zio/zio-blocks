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
 * Scala 2 version-specific implementation of [[MigrationBuilder.build]].
 *
 * Scala 2 does not yet provide the type-state + macro validation layer, so
 * `build` currently falls back to [[MigrationBuilder.buildPartial]].
 */
trait MigrationBuilderBuildVersionSpecific[A, B] { self: MigrationBuilder[A, B] =>
  def build: Migration[A, B] = self.buildPartial
}

