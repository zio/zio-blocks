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
 * A typed migration from schema version A to schema version B.
 *
 * `Migration[A, B]` wraps a [[DynamicMigration]] together with the source and
 * target schemas, providing a type-safe API for applying migrations to typed
 * values.
 *
 * The migration works by:
 *   1. Converting the input value `A` to a [[DynamicValue]] using
 *      `sourceSchema`
 *   2. Applying the [[DynamicMigration]]
 *   3. Converting the result back to `B` using `targetSchema`
 *
 * Laws:
 *   - '''Identity''': `Migration.identity[A].apply(a) == Right(a)`
 *   - '''Associativity''': `(m1 ++ m2) ++ m3` behaves the same as
 *     `m1 ++ (m2 ++ m3)`
 *   - '''Structural Reverse''': `m.reverse.reverse == m` (structurally)
 *   - '''Best-Effort Semantic Inverse''':
 *     `m.apply(a) == Right(b) ⇒ m.reverse.apply(b) == Right(a)` (when
 *     sufficient information exists)
 */
case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Applies this migration to transform a value of type A into type B.
   *
   * @param value
   *   the input value
   * @return
   *   Right(migrated) on success, Left(error) on failure
   */
  def apply(value: A): Either[MigrationError, B] = {
    val dynamicValue = sourceSchema.toDynamicValue(value)
    dynamicMigration(dynamicValue).flatMap { migratedDynamic =>
      targetSchema.fromDynamicValue(migratedDynamic) match {
        case Right(result) => Right(result)
        case Left(err)     =>
          Left(
            MigrationError.ActionFailed(
              "Migration",
              DynamicOptic.root,
              s"Failed to convert migrated value to target type: ${err.message}"
            )
          )
      }
    }
  }

  /**
   * Composes this migration with another, creating a migration from A to C.
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      this.dynamicMigration ++ that.dynamicMigration,
      this.sourceSchema,
      that.targetSchema
    )

  /** Alias for `++`. */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Returns the structural reverse of this migration (from B to A).
   */
  def reverse: Migration[B, A] =
    Migration(dynamicMigration.reverse, targetSchema, sourceSchema)
}

object Migration {

  /**
   * Creates an identity migration that passes values through unchanged.
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.identity, schema, schema)

  /**
   * Creates a new [[MigrationBuilder]] for constructing a migration from type A
   * to type B.
   */
  def newBuilder[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sourceSchema, targetSchema, Vector.empty)
}
