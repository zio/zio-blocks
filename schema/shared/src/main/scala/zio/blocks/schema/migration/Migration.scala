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

import zio.blocks.schema.{Schema, SchemaError}

final case class Migration[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  dynamicMigration: DynamicMigration
) {

  def apply(value: A): Either[SchemaError, B] = {
    val dynamicValue = sourceSchema.toDynamicValue(value)
    dynamicMigration(dynamicValue).flatMap(targetSchema.fromDynamicValue)
  }

  def ++[C](that: Migration[B, C]): Migration[A, C] =
    new Migration(sourceSchema, that.targetSchema, dynamicMigration ++ that.dynamicMigration)

  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  def reverse: Migration[B, A] =
    new Migration(targetSchema, sourceSchema, dynamicMigration.reverse)

  def isEmpty: Boolean = dynamicMigration.isEmpty

  def size: Int = dynamicMigration.size

  def actions: Vector[MigrationAction] = dynamicMigration.actions
}

object Migration extends MigrationCompanionVersionSpecific with MigrationSelectorSyntax {

  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    new Migration(schema, schema, DynamicMigration.empty)

  def fromAction[A, B](action: MigrationAction)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    new Migration(sourceSchema, targetSchema, DynamicMigration.single(action))

  def fromActions[A, B](actions: MigrationAction*)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions.toVector))

  def fromDynamic[A, B](dynamicMigration: DynamicMigration)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    new Migration(sourceSchema, targetSchema, dynamicMigration)
}
