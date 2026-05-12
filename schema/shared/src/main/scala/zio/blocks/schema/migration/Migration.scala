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
 * A typed, schema-validated migration from values of type `A` to values of
 * type `B`.
 *
 * `Migration[A, B]` is the user-facing API built on top of the pure,
 * serializable [[DynamicMigration]] core. It couples a structural
 * transformation with the source and target [[Schema]]s so that typed values
 * can be migrated end-to-end:
 *
 * {{{
 * // V1 structural type (compile-time only, no runtime representation)
 * type PersonV1 = { def name: String; def age: Int }
 *
 * // V2 real type
 * case class Person(fullName: String, age: Int, country: String)
 *
 * val migration: Migration[PersonV1, Person] = Migration(
 *   DynamicMigration.Identity
 *     .andThen(DynamicMigration.RenameField("name", "fullName"))
 *     .andThen(DynamicMigration.AddField("country", DynamicValue.Primitive(PrimitiveValue.String("Unknown")))),
 *   schemaV1,
 *   Schema[Person]
 * )
 *
 * migration(v1Person) // Right(Person("Alice", 30, "Unknown"))
 * }}}
 *
 * ==Composition==
 *
 * Migrations compose via `andThen`, allowing multi-step version chains:
 *
 * {{{
 * val v1ToV2: Migration[V1, V2] = ...
 * val v2ToV3: Migration[V2, V3] = ...
 * val v1ToV3: Migration[V1, V3] = v1ToV2.andThen(v2ToV3)
 * }}}
 *
 * ==Inversion==
 *
 * When all constituent [[DynamicMigration]] operations are invertible, the
 * migration can be reversed:
 *
 * {{{
 * val downgrade: Option[Migration[V2, V1]] = v1ToV2.invert
 * }}}
 *
 * @param dynamicMigration
 *   The underlying structural transformation (pure data, serializable)
 * @param sourceSchema
 *   Schema for the source type `A`
 * @param targetSchema
 *   Schema for the target type `B`
 *
 * @see [[DynamicMigration]] for the full ADT of available operations
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Migrates a typed value from `A` to `B`.
   *
   * Converts `value` to a [[DynamicValue]] using `sourceSchema`, applies the
   * [[DynamicMigration]], and decodes the result using `targetSchema`.
   *
   * @return
   *   `Right(b)` on success, or `Left(SchemaError)` if the migration or
   *   decoding fails.
   */
  def apply(value: A): Either[SchemaError, B] =
    dynamicMigration(sourceSchema.toDynamicValue(value)).flatMap(targetSchema.fromDynamicValue)

  /**
   * Applies the underlying [[DynamicMigration]] directly to a [[DynamicValue]].
   *
   * Useful when working with raw dynamic data (e.g. from external storage)
   * without deserialising to type `A` first.
   */
  def applyDynamic(value: DynamicValue): Either[SchemaError, DynamicValue] =
    dynamicMigration(value)

  /**
   * Composes this migration with `that`, yielding a migration from `A` to `C`.
   *
   * Equivalent to applying `this` and then `that` sequentially.
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] =
    Migration(dynamicMigration.andThen(that.dynamicMigration), sourceSchema, that.targetSchema)

  /**
   * Returns the inverse migration (from `B` to `A`) if all constituent
   * operations are invertible.
   *
   * @return
   *   `Some(Migration[B, A])` when the migration is fully invertible, or
   *   `None` if any operation (such as `AddField` or `RemoveField`) cannot be
   *   reversed.
   */
  def invert: Option[Migration[B, A]] =
    dynamicMigration.invert.map(Migration(_, targetSchema, sourceSchema))
}

object Migration {

  /**
   * Creates a no-op migration that returns values of type `A` unchanged.
   */
  def identity[A](schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.Identity, schema, schema)
}
