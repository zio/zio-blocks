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

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

/**
 * A typed migration from schema `A` to schema `B`.
 *
 * The migration is applied by:
 *   1. Converting an `A` value to `DynamicValue` via `sourceSchema`
 *   2. Applying the underlying `DynamicMigration`
 *   3. Converting the result back to `B` via `targetSchema`
 *
 * {{{
 * case class PersonV1(name: String, age: Int)
 * case class PersonV2(fullName: String, age: Int, email: String)
 *
 * val migration = Migration[PersonV1, PersonV2](
 *   Schema.derived[PersonV1],
 *   Schema.derived[PersonV2]
 * ).renameField("name", "fullName")
 *  .addField("email", DynamicValue.Primitive(PrimitiveValue.String("")))
 *
 * migration(PersonV1("Alice", 30)) // Right(PersonV2("Alice", 30, ""))
 * }}}
 */
final case class Migration[A, B] private[migration] (
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /** Apply this migration to a value of type A, producing a B. */
  def apply(value: A): Either[MigrationError, B] = {
    val dv = sourceSchema.toDynamicValue(value)
    dynamicMigration(dv) match {
      case Right(migratedDv) =>
        targetSchema.fromDynamicValue(migratedDv) match {
          case Right(b)  => new Right(b)
          case Left(err) => new Left(MigrationError.SchemaConversionFailed(err))
        }
      case l => l.asInstanceOf[Either[MigrationError, B]]
    }
  }

  /** Compose two typed migrations. */
  def andThen[C](that: Migration[B, C]): Migration[A, C] =
    new Migration(dynamicMigration.andThen(that.dynamicMigration), sourceSchema, that.targetSchema)

  // ---------------------------------------------------------------------------
  // Builder methods (operate on the root record by default)
  // ---------------------------------------------------------------------------

  /** Add a field to the root record. */
  def addField(fieldName: String, defaultValue: DynamicValue): Migration[A, B] =
    addField(DynamicOptic.root, fieldName, defaultValue)

  /** Add a field at the given path. */
  def addField(path: DynamicOptic, fieldName: String, defaultValue: DynamicValue): Migration[A, B] =
    appendAction(MigrationAction.AddField(path, fieldName, defaultValue))

  /** Drop a field from the root record. */
  def dropField(fieldName: String): Migration[A, B] =
    dropField(DynamicOptic.root, fieldName)

  /** Drop a field at the given path. */
  def dropField(path: DynamicOptic, fieldName: String): Migration[A, B] =
    appendAction(MigrationAction.DropField(path, fieldName))

  /** Rename a field in the root record. */
  def renameField(oldName: String, newName: String): Migration[A, B] =
    renameField(DynamicOptic.root, oldName, newName)

  /** Rename a field at the given path. */
  def renameField(path: DynamicOptic, oldName: String, newName: String): Migration[A, B] =
    appendAction(MigrationAction.RenameField(path, oldName, newName))

  /** Set the value at a path. */
  def setValue(path: DynamicOptic, value: DynamicValue): Migration[A, B] =
    appendAction(MigrationAction.SetValue(path, value))

  /** Apply a nested migration to the value at a path. */
  def transformValue(path: DynamicOptic, migration: DynamicMigration): Migration[A, B] =
    appendAction(MigrationAction.TransformValue(path, migration))

  /** Rename a variant case at the root. */
  def renameCase(oldName: String, newName: String): Migration[A, B] =
    renameCase(DynamicOptic.root, oldName, newName)

  /** Rename a variant case at the given path. */
  def renameCase(path: DynamicOptic, oldName: String, newName: String): Migration[A, B] =
    appendAction(MigrationAction.RenameCase(path, oldName, newName))

  /** Transform the inner value of a variant case at the given path. */
  def transformCase(path: DynamicOptic, caseName: String, migration: DynamicMigration): Migration[A, B] =
    appendAction(MigrationAction.TransformCase(path, caseName, migration))

  /** Apply a migration to every element of a sequence at a path. */
  def transformElements(path: DynamicOptic, migration: DynamicMigration): Migration[A, B] =
    appendAction(MigrationAction.TransformElements(path, migration))

  /** Apply a migration to every key of a map at a path. */
  def transformKeys(path: DynamicOptic, migration: DynamicMigration): Migration[A, B] =
    appendAction(MigrationAction.TransformKeys(path, migration))

  /** Apply a migration to every value of a map at a path. */
  def transformValues(path: DynamicOptic, migration: DynamicMigration): Migration[A, B] =
    appendAction(MigrationAction.TransformValues(path, migration))

  /** Reorder fields of a record at the given path. */
  def reorderFields(path: DynamicOptic, fieldOrder: IndexedSeq[String]): Migration[A, B] =
    appendAction(MigrationAction.ReorderFields(path, fieldOrder))

  /** Reorder fields of the root record. */
  def reorderFields(fieldOrder: IndexedSeq[String]): Migration[A, B] =
    reorderFields(DynamicOptic.root, fieldOrder)

  private def appendAction(action: MigrationAction): Migration[A, B] =
    new Migration(new DynamicMigration(dynamicMigration.actions :+ action), sourceSchema, targetSchema)
}

object Migration {

  /** Create an empty migration between two schemas. */
  def apply[A, B](sourceSchema: Schema[A], targetSchema: Schema[B]): Migration[A, B] =
    new Migration(DynamicMigration.identity, sourceSchema, targetSchema)

  /** Identity migration. */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    new Migration(DynamicMigration.identity, schema, schema)
}
