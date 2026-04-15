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
 * Typed migration: pairs a serializable [[DynamicMigration]] with source and
 * target [[Schema]] values.
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  def apply(value: A): Either[MigrationError, B] = {
    val dynamic = sourceSchema.toDynamicValue(value)
    dynamicMigration.apply(dynamic, sourceSchema, targetSchema) match {
      case Right(out) => targetSchema.fromDynamicValue(out)
      case Left(err)  => Left(err)
    }
  }

  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      dynamicMigration = this.dynamicMigration ++ that.dynamicMigration,
      sourceSchema = this.sourceSchema,
      targetSchema = that.targetSchema
    )

  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  def reverse: Migration[B, A] =
    Migration(
      dynamicMigration = dynamicMigration.reverse,
      sourceSchema = targetSchema,
      targetSchema = sourceSchema
    )
}

object Migration {

  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.empty, schema, schema)

  def newBuilder[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B, Any, Any] =
    MigrationBuilder.apply
}
