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

import zio.blocks.schema._

/**
 * Scala 2.13: [[CompanionOptics.dynamicOptic]] cannot be invoked from the same
 * compiler batch as its macro implementation on Scala.js; this builder only
 * accepts explicit [[DynamicOptic]] paths. Use the Scala 3 [[MigrationBuilder]]
 * for selector-based API.
 */
class MigrationBuilder[A, B, SourceRemainder, TargetRemainder] private (
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) {

  def addFieldAt(at: DynamicOptic, default: MigrationExpr): MigrationBuilder[A, B, SourceRemainder, TargetRemainder] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.AddField(at, default))

  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
}

object MigrationBuilder {

  type Complete = Any

  def apply[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, Complete, Complete] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}
