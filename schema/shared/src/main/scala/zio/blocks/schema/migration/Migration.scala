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

/**
 * A type-safe migration that transforms values of type `A` into values of type
 * `B`.
 *
 * `Migration[A, B]` wraps a [[DynamicMigration]] together with the source
 * schema `Schema[A]` and target schema `Schema[B]`. This enables:
 *
 *   - Converting `A` to `DynamicValue` (via `sourceSchema`)
 *   - Applying the `DynamicMigration` to transform the shape
 *   - Converting the result back to `B` (via `targetSchema`)
 *
 * Migrations can be composed with `++` and reversed with `reverse`. The
 * `reverse` is a best-effort structural inverse — it will produce correct
 * results when the underlying [[DynamicMigration]] has sufficient information
 * to invert each action.
 *
 * Laws:
 *   - Identity: `Migration.identity[A].apply(a) == Right(a)`
 *   - Associativity: `(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)` (structural)
 *   - Structural reverse: `m.reverse.reverse` has the same actions as `m`
 *
 * @param dynamicMigration
 *   The untyped migration operations (pure data, fully serializable)
 * @param sourceSchema
 *   Schema for the source type `A`
 * @param targetSchema
 *   Schema for the target type `B`
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Apply this migration to transform a value of type `A` into a value of type
   * `B`.
   */
  def apply(value: A): Either[MigrationError, B] = {
    val dynamic = sourceSchema.toDynamicValue(value)
    dynamicMigration(dynamic) match {
      case Right(migrated) =>
        targetSchema.fromDynamicValue(migrated) match {
          case Right(result) => new Right(result)
          case Left(err)     => new Left(MigrationError(s"Failed to construct target value: ${err.message}"))
        }
      case Left(err) => new Left(err)
    }
  }

  /**
   * Compose this migration with another migration from `B` to `C`, producing a
   * migration from `A` to `C`.
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      dynamicMigration ++ that.dynamicMigration,
      sourceSchema,
      that.targetSchema
    )

  /** Alias for `++`. */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * The structural reverse of this migration. This swaps source and target
   * schemas and reverses the dynamic migration.
   *
   * The reverse is a best-effort semantic inverse: for lossless actions (e.g.,
   * Rename, RenameCase), it will fully invert the migration. For lossy actions
   * (e.g., DropField without a stored default, Constant transforms), the
   * reverse uses best-effort defaults.
   */
  def reverse: Migration[B, A] =
    Migration(
      dynamicMigration.reverse,
      targetSchema,
      sourceSchema
    )

  /** Returns true if this migration has no actions (identity). */
  def isEmpty: Boolean = dynamicMigration.isEmpty

  override def toString: String = dynamicMigration.toString
}

object Migration {

  /**
   * Create a new `MigrationBuilder` for building a migration from `A` to `B`.
   *
   * Use the builder methods to specify the transformation actions, then call
   * `.build` or `.buildPartial` to produce the `Migration`.
   */
  def newBuilder[A, B](implicit sa: Schema[A], sb: Schema[B]): MigrationBuilder[A, B] =
    MigrationBuilder.empty[A, B]

  /**
   * Create a `Migration` directly from a `DynamicMigration` and schemas.
   */
  def from[A, B](
    dynamicMigration: DynamicMigration,
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    new Migration(dynamicMigration, sourceSchema, targetSchema)

  /**
   * The identity migration: transforms `A` to `A` without any changes.
   *
   * Laws: `Migration.identity[A].apply(a) == Right(a)` for all `a`.
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    new Migration(DynamicMigration.empty, schema, schema)
}
