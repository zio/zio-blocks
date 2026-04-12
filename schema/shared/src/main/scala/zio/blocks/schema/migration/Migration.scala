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

import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * A typed schema migration from `A` to `B`.
 *
 * This wraps a [[DynamicMigration]] (which operates on
 * [[zio.blocks.schema.DynamicValue]]) together with a source and target
 * [[zio.blocks.schema.Schema]] to perform encoding/decoding.
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {
  /**
   * Migrates a value of type `A` to type `B`.
   *
   * The value is encoded using `sourceSchema`, migrated dynamically, then
   * decoded using `targetSchema`.
   */
  def apply(value: A): Either[MigrationError, B] =
    for {
      dv       <- Right(sourceSchema.toDynamicValue(value))
      migrated <- dynamicMigration(dv)
      result <- targetSchema
        .fromDynamicValue(migrated)
        .left
        .map(e => MigrationFailed(DynamicOptic.root, e.toString))
    } yield result

  /** Sequentially composes this migration with `that` migration. */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(dynamicMigration ++ that.dynamicMigration, sourceSchema, that.targetSchema)

  /** Alias for `++`, emphasizing left-to-right sequencing. */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /** Reverses this migration, swapping source/target schemas. */
  def reverse: Migration[B, A] =
    Migration(dynamicMigration.reverse, targetSchema, sourceSchema)
}

object Migration {
  /** The identity migration, which applies no dynamic actions. */
  def identity[A](schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration(Vector.empty), schema, schema)

  /**
   * Creates a new [[MigrationBuilder]] for `A -> B` using implicit schemas.
   *
   * Note: this is a convenience entry point; selector macros (if any) are
   * layered on top of the builder API.
   */
  def newBuilder[A, B](implicit sa: Schema[A], sb: Schema[B]) =
    MigrationBuilder(sa, sb)
}
