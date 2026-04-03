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

final case class Migration[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  dynamicMigration: DynamicMigration
) {
  def apply(value: A): Either[MigrationError, B] = {
    val dynApp = for {
      dynSrc <- Right(sourceSchema.toDynamicValue(value))
      dynTgt <- dynamicMigration.apply(dynSrc)
      b      <- targetSchema.fromDynamicValue(dynTgt).left.map(e => MigrationError.EvaluationError(e.message))
    } yield b
    dynApp
  }

  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(sourceSchema, that.targetSchema, dynamicMigration ++ that.dynamicMigration)

  def reverse: Migration[B, A] = Migration(targetSchema, sourceSchema, dynamicMigration.reverse)
}

object Migration {
  def derive[A, B](implicit source: Schema[A], target: Schema[B]): MigrationBuilder[A, B, A] =
    new MigrationBuilder[A, B, A](source, target, DynamicMigration.empty)
}
