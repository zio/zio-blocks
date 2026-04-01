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
import zio.blocks.schema.migration.MigrationError.InvalidValue

final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {
  def apply(value: A): Either[MigrationError, B] =
    for {
      source <- Right(sourceSchema.toDynamicValue(value))
      after  <- dynamicMigration(source)
      out    <- targetSchema.fromDynamicValue(after).left.map(fromSchemaError)
    } yield out

  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration[A, C](dynamicMigration ++ that.dynamicMigration, sourceSchema, that.targetSchema)

  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  def reverse: Migration[B, A] = Migration[B, A](dynamicMigration.reverse, targetSchema, sourceSchema)

  private[this] def fromSchemaError(error: SchemaError): MigrationError =
    InvalidValue(zio.blocks.schema.DynamicOptic.root, error.message)
}

object Migration {
  def identity[A](schema: Schema[A]): Migration[A, A] = Migration(DynamicMigration.identity, schema, schema)

  def newBuilder[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    MigrationBuilder[A, B]
}
