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

import zio.blocks.chunk.Chunk
import zio.blocks.schema.Schema

/** The builder entry point of the [[Migration]] companion. */
trait MigrationCompanionVersionSpecific {

  /**
   * Starts an empty [[MigrationBuilder]] from `A` to `B`; [[Migration]] shows
   * an example. On Scala 2 the builder does not compile outside ZIO Blocks (see
   * `docs/dev/issues.md`): build a [[DynamicMigration]] and call
   * [[Migration.fromDynamic]] instead.
   */
  def newBuilder[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, Any] =
    new MigrationBuilder(sourceSchema, targetSchema, Chunk.empty)
}
