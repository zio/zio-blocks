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

package zio.blocks.schema

/**
 * A typed migration from type `A` to type `B`.
 *
 * `Migration[A, B]` is a type-safe wrapper around [[DynamicMigration]] that
 * provides compile-time type safety while leveraging the same serializable
 * migration engine.
 *
 * ==Properties==
 *
 *   - '''Type Safe''': Source and target types are tracked at compile time
 *   - '''Serializable'': The underlying DynamicMigration can be serialized
 *   - '''Composable'': Migrations can be composed sequentially
 *   - '''Reversible'': Structural reverse is supported
 *
 * @tparam A
 *   the source type
 * @tparam B
 *   the target type
 */
final case class Migration[A, B] private (
  dynamic: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) { self =>

  /**
   * Applies this migration to a value of type `A`.
   *
   * @param value
   *   the input value to migrate
   * @return
   *   Either a MigrationError or the migrated value of type `B`
   */
  def apply(value: A): Either[MigrationError, B] = {
    val dynamicValue = sourceSchema.toDynamicValue(value)
    dynamic(dynamicValue).flatMap { migrated =>
      targetSchema.fromDynamicValue(migrated).left.map(MigrationError.fromSchemaError)
    }
  }

  /**
   * Composes this migration with another, creating a migration that applies
   * this first, then `that`.
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      dynamic ++ that.dynamic,
      sourceSchema,
      that.targetSchema
    )

  /**
   * Alias for `andThen`. Composes this migration with another.
   *
   * This operation is associative:
   * {{{
   *   (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
   * }}}
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] = andThen(that)

  /**
   * Alias for `andThen` using the `>>>` operator.
   */
  def >>>[C](that: Migration[B, C]): Migration[A, C] = andThen(that)

  /**
   * Returns the structural reverse of this migration.
   *
   * The reverse migration goes from `B` back to `A`.
   */
  def reverse: Migration[B, A] =
    Migration(
      dynamic.reverse,
      targetSchema,
      sourceSchema
    )

  /**
   * Converts this typed migration to its underlying dynamic representation.
   */
  def toDynamic: DynamicMigration = dynamic

  /**
   * Returns the number of actions in this migration.
   */
  def size: Int = dynamic.size

  /**
   * Returns true if this migration has no actions.
   */
  def isEmpty: Boolean = dynamic.isEmpty

  /**
   * Returns true if this migration has at least one action.
   */
  def nonEmpty: Boolean = dynamic.nonEmpty
}

object Migration {

  /**
   * Creates an identity migration that returns the input unchanged.
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.empty, schema, schema)

  /**
   * Creates a Migration from a DynamicMigration and explicit schemas.
   */
  def fromDynamic[A, B](
    dynamic: DynamicMigration,
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    Migration(dynamic, sourceSchema, targetSchema)

  /**
   * Creates a migration using the MigrationBuilder DSL.
   */
  def builder[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    MigrationBuilder.empty[A, B]
}
