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
 * A type-safe migration from schema version `A` to schema version `B`.
 *
 * `Migration` wraps a [[DynamicMigration]] together with the source
 * [[Schema]][A] and target [[Schema]][B], providing a fully type-safe API while
 * retaining all the serializability and introspectability properties of the
 * underlying [[DynamicMigration]].
 *
 * ===Laws===
 *
 *   - '''Identity''': `Migration.identity(s).apply(a) == Right(a)`
 *   - '''Reverse''': if `m.apply(a) == Right(b)` then
 *     `m.reverse.apply(b) == Right(a)` (when information is preserved)
 *   - '''Structural reverse''':
 *     `m.reverse.reverse.dynamicMigration == m.dynamicMigration`
 *
 * @param dynamicMigration
 *   The underlying untyped migration.
 * @param from
 *   The source schema.
 * @param to
 *   The target schema.
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  from: Schema[A],
  to: Schema[B]
) {

  /**
   * Apply this migration to `value`, converting an `A` into a `B`, or returning
   * the first [[MigrationError]] encountered.
   */
  def apply(value: A): Either[MigrationError, B] = {
    val dv = from.toDynamicValue(value)
    dynamicMigration(dv).flatMap { result =>
      to.fromDynamicValue(result).left.map { err =>
        MigrationError.DecodeFailed(DynamicOptic.root, err.message)
      }
    }
  }

  /**
   * The structural inverse of this migration.
   *
   * Satisfies: `m.reverse.reverse.dynamicMigration == m.dynamicMigration`
   */
  def reverse: Migration[B, A] =
    Migration(dynamicMigration.reverse, to, from)

  /**
   * Compose this migration with `that`, producing a migration from `A` to `C`.
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] =
    Migration(dynamicMigration ++ that.dynamicMigration, from, that.to)
}

object Migration {

  /**
   * The identity migration for schema `A`: applies no transformations and
   * returns the input unchanged.
   */
  def identity[A](schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.identity, schema, schema)
}
