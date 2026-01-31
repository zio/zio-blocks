/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
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

/**
 * A typed, macro-validated migration from schema A to schema B.
 *
 * Migration[A, B] wraps:
 *   - A DynamicMigration (pure, serializable core)
 *   - The source schema (structural schema for A)
 *   - The target schema (structural schema for B)
 *
 * This provides:
 *   - Type-safe migration application
 *   - Schema validation at build time
 *   - Bidirectional migration support
 *
 * @tparam A
 *   The source type
 * @tparam B
 *   The target type
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /**
   * Apply this migration to transform A to B.
   */
  def apply(value: A): Either[MigrationError, B] = {
    val dynamicValue = sourceSchema.toDynamicValue(value)
    dynamicMigration(dynamicValue).flatMap { migratedValue =>
      targetSchema.fromDynamicValue(migratedValue) match {
        case Right(result)     => Right(result)
        case Left(schemaError) =>
          Left(
            MigrationError.incompatibleSchemas(
              zio.blocks.schema.DynamicOptic.root,
              sourceSchema.reflect.typeId.toString,
              s"${targetSchema.reflect.typeId}: ${schemaError.getMessage}"
            )
          )
      }
    }
  }

  /**
   * Compose migrations sequentially: this andThen that.
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      dynamicMigration ++ that.dynamicMigration,
      sourceSchema,
      that.targetSchema
    )

  /**
   * Alias for ++
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Reverse migration (structural inverse; runtime is best-effort).
   *
   * Note: The reverse migration may fail at runtime if the forward migration
   * loses information that cannot be recovered.
   */
  def reverse: Migration[B, A] =
    Migration(
      dynamicMigration.reverse,
      targetSchema,
      sourceSchema
    )

  /**
   * Check if this migration is empty (identity).
   */
  def isEmpty: Boolean = dynamicMigration.isEmpty

  /**
   * Get the underlying migration actions for introspection.
   */
  def actions: Vector[MigrationAction] = dynamicMigration.actions

  override def toString: String =
    s"Migration[${sourceSchema.reflect.typeId} => ${targetSchema.reflect.typeId}]"
}

object Migration {

  /**
   * Create an identity migration that performs no changes.
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.identity, schema, schema)

  /**
   * Create a migration from actions.
   */
  def apply[A, B](actions: MigrationAction*)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    Migration(DynamicMigration(actions.toVector), sourceSchema, targetSchema)

  /**
   * Create a migration from a DynamicMigration.
   */
  def fromDynamic[A, B](
    dynamicMigration: DynamicMigration
  )(implicit sourceSchema: Schema[A], targetSchema: Schema[B]): Migration[A, B] =
    Migration(dynamicMigration, sourceSchema, targetSchema)

  /**
   * Start building a migration with field tracking. This is the primary entry
   * point for creating migrations.
   */
  def builder[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    MigrationBuilder(sourceSchema, targetSchema, Vector.empty)

  /**
   * Create a simple field rename migration.
   */
  def renameField[A, B](from: String, to: String)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    Migration(DynamicMigration(MigrationAction.RenameField(from, to)), sourceSchema, targetSchema)

  /**
   * Create a migration that adds a field with a default value.
   */
  def addField[A, B](fieldName: String, default: ResolvedExpr)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    Migration(DynamicMigration(MigrationAction.AddField(fieldName, default)), sourceSchema, targetSchema)

  /**
   * Create a migration that drops a field.
   */
  def dropField[A, B](fieldName: String)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    Migration(DynamicMigration(MigrationAction.DropField(fieldName, None)), sourceSchema, targetSchema)
}
