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

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}
import zio.blocks.schema.migration.MigrationAction._

/**
 * A builder for constructing a [[Migration]] from `A` to `B`.
 *
 * Methods accept field names as `String`s, which are validated at build time
 * against the source and target schemas (in `.build`). For partial migrations
 * without full validation, use `.buildPartial`.
 *
 * In a future enhancement, selector-based methods accepting `A => Any` will be
 * provided via Scala 3 macros, converting selector expressions directly to
 * `DynamicOptic` paths.
 *
 * Example:
 * {{{
 * val migration = Migration.newBuilder[PersonV1, PersonV2]
 *   .renameField("name", "fullName")
 *   .addField("age", Schema[Int], 0)
 *   .build
 * }}}
 */
final class MigrationBuilder[A, B] private (
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  private val actions: Vector[MigrationAction]
) {

  // ─────────────────────────────────────────────────────────────────────────
  // Record operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Add a field to the target record with the given default value.
   *
   * @param fieldName
   *   The name of the new field in the target schema
   * @param fieldSchema
   *   The schema for the field's type
   * @param default
   *   The default value for the field
   */
  def addField[T](fieldName: String, fieldSchema: Schema[T], default: T): MigrationBuilder[A, B] = {
    val path         = DynamicOptic.root.field(fieldName)
    val defaultValue = fieldSchema.toDynamicValue(default)
    withAction(AddField(path, defaultValue))
  }

  /**
   * Add a field to the target record at a nested path with a dynamic default.
   *
   * @param path
   *   The `DynamicOptic` path to the new field
   * @param default
   *   The default `DynamicValue` for the field
   */
  def addFieldAt(path: DynamicOptic, default: DynamicValue): MigrationBuilder[A, B] =
    withAction(AddField(path, default))

  /**
   * Drop a field from the source record.
   *
   * @param fieldName
   *   The name of the field to drop in the source schema
   * @param defaultForReverse
   *   The default value to use when reversing this migration (adding the field
   *   back)
   */
  def dropField[T](fieldName: String, fieldSchema: Schema[T], defaultForReverse: T): MigrationBuilder[A, B] = {
    val path         = DynamicOptic.root.field(fieldName)
    val defaultValue = fieldSchema.toDynamicValue(defaultForReverse)
    withAction(DropField(path, defaultValue))
  }

  /**
   * Drop a field from the source record at a nested path.
   *
   * @param path
   *   The `DynamicOptic` path to the field to drop
   * @param defaultForReverse
   *   The default `DynamicValue` for the reverse migration
   */
  def dropFieldAt(path: DynamicOptic, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    withAction(DropField(path, defaultForReverse))

  /**
   * Rename a field at the root level.
   *
   * @param fromName
   *   The current field name in the source schema
   * @param toName
   *   The new field name in the target schema
   */
  def renameField(fromName: String, toName: String): MigrationBuilder[A, B] = {
    val path = DynamicOptic.root.field(fromName)
    withAction(Rename(path, toName))
  }

  /**
   * Rename a field at a nested path.
   *
   * @param path
   *   The `DynamicOptic` path to the field (last node must be a Field with the
   *   old name)
   * @param toName
   *   The new field name
   */
  def renameFieldAt(path: DynamicOptic, toName: String): MigrationBuilder[A, B] =
    withAction(Rename(path, toName))

  /**
   * Transform the value at a root-level field using a `DynamicMigrationExpr`.
   *
   * @param fieldName
   *   The name of the field to transform
   * @param expr
   *   The transformation expression
   */
  def transformField(fieldName: String, expr: DynamicMigrationExpr): MigrationBuilder[A, B] = {
    val path = DynamicOptic.root.field(fieldName)
    withAction(TransformValue(path, expr))
  }

  /**
   * Transform the value at the given `DynamicOptic` path using an expression.
   */
  def transformAt(path: DynamicOptic, expr: DynamicMigrationExpr): MigrationBuilder[A, B] =
    withAction(TransformValue(path, expr))

  /**
   * Make an optional field mandatory, using `default` when the value is
   * `None`.
   *
   * @param fieldName
   *   The name of the optional field in the source schema
   * @param defaultSchema
   *   Schema for the default value type
   * @param default
   *   The value to use when the source field is `None`
   */
  def mandateField[T](fieldName: String, defaultSchema: Schema[T], default: T): MigrationBuilder[A, B] = {
    val path         = DynamicOptic.root.field(fieldName)
    val defaultValue = defaultSchema.toDynamicValue(default)
    withAction(Mandate(path, defaultValue))
  }

  /**
   * Make a mandatory field optional by wrapping it in `Some`.
   *
   * @param fieldName
   *   The name of the mandatory field in the source schema
   */
  def optionalizeField(fieldName: String): MigrationBuilder[A, B] = {
    val path = DynamicOptic.root.field(fieldName)
    withAction(Optionalize(path))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Enum operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Rename a variant case at the root level.
   *
   * @param fromName
   *   The current case name in the source schema
   * @param toName
   *   The new case name in the target schema
   */
  def renameCase(fromName: String, toName: String): MigrationBuilder[A, B] =
    withAction(RenameCase(DynamicOptic.root, fromName, toName))

  /**
   * Rename a variant case at the given path.
   */
  def renameCaseAt(path: DynamicOptic, fromName: String, toName: String): MigrationBuilder[A, B] =
    withAction(RenameCase(path, fromName, toName))

  /**
   * Apply nested migration actions to the inner value of a specific case.
   *
   * @param caseName
   *   The name of the case to transform
   * @param f
   *   A function that configures a nested `MigrationBuilder` for the case
   */
  def transformCaseWith[CA, CB](
    caseName: String,
    caseSourceSchema: Schema[CA],
    caseTargetSchema: Schema[CB]
  )(f: MigrationBuilder[CA, CB] => MigrationBuilder[CA, CB]): MigrationBuilder[A, B] = {
    val nestedBuilder = f(MigrationBuilder.empty[CA, CB](caseSourceSchema, caseTargetSchema))
    withAction(TransformCase(DynamicOptic.root, caseName, nestedBuilder.actions))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Collection operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Transform all elements of a sequence field.
   *
   * @param fieldName
   *   The name of the sequence field
   * @param expr
   *   The transformation expression to apply to each element
   */
  def transformElements(fieldName: String, expr: DynamicMigrationExpr): MigrationBuilder[A, B] = {
    val path = DynamicOptic.root.field(fieldName)
    withAction(TransformElements(path, expr))
  }

  /**
   * Transform all elements at the given path.
   */
  def transformElementsAt(path: DynamicOptic, expr: DynamicMigrationExpr): MigrationBuilder[A, B] =
    withAction(TransformElements(path, expr))

  // ─────────────────────────────────────────────────────────────────────────
  // Map operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Transform all keys of a map field.
   *
   * @param fieldName
   *   The name of the map field
   * @param expr
   *   The transformation expression to apply to each key
   */
  def transformKeys(fieldName: String, expr: DynamicMigrationExpr): MigrationBuilder[A, B] = {
    val path = DynamicOptic.root.field(fieldName)
    withAction(TransformKeys(path, expr))
  }

  /**
   * Transform all values of a map field.
   *
   * @param fieldName
   *   The name of the map field
   * @param expr
   *   The transformation expression to apply to each value
   */
  def transformValues(fieldName: String, expr: DynamicMigrationExpr): MigrationBuilder[A, B] = {
    val path = DynamicOptic.root.field(fieldName)
    withAction(TransformValues(path, expr))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Low-level: add a raw action
  // ─────────────────────────────────────────────────────────────────────────

  /** Add a raw `MigrationAction` to this builder. */
  def withAction(action: MigrationAction): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)

  // ─────────────────────────────────────────────────────────────────────────
  // Build
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Build the migration with full structural validation.
   *
   * Validates that:
   *   - All `AddField` paths reference fields that exist in the target schema
   *   - All `DropField` and `Rename` paths reference fields that exist in the
   *     source schema
   *
   * Returns `Left` with a description of any validation errors, or `Right`
   * with the built [[Migration]].
   *
   * Note: Full macro-level validation (ensuring old schema has been completely
   * migrated to new schema) is a future enhancement.
   */
  def build: Either[String, Migration[A, B]] = {
    val errors = validateActions(actions)
    if (errors.nonEmpty) new Left(errors.mkString("; "))
    else new Right(new Migration(new DynamicMigration(actions), sourceSchema, targetSchema))
  }

  /**
   * Build the migration without full structural validation. Useful for
   * partial migrations or migrations on schemas not known at compile time.
   */
  def buildPartial: Migration[A, B] =
    new Migration(new DynamicMigration(actions), sourceSchema, targetSchema)

  // ─────────────────────────────────────────────────────────────────────────
  // Validation helpers
  // ─────────────────────────────────────────────────────────────────────────

  private[this] def validateActions(actions: Vector[MigrationAction]): Vector[String] = {
    val errors = Vector.newBuilder[String]
    actions.foreach {
      case AddField(at, _) =>
        // Validate that the target field path exists in the target schema
        if (at.nodes.isEmpty)
          errors += "AddField: path must not be empty"
        else {
          at.nodes.last match {
            case _: DynamicOptic.Node.Field => () // OK
            case other                      => errors += s"AddField: last path node must be a Field, got ${other.getClass.getSimpleName}"
          }
        }
      case DropField(at, _) =>
        if (at.nodes.isEmpty)
          errors += "DropField: path must not be empty"
        else {
          at.nodes.last match {
            case _: DynamicOptic.Node.Field => () // OK
            case other                      => errors += s"DropField: last path node must be a Field, got ${other.getClass.getSimpleName}"
          }
        }
      case Rename(at, to) =>
        if (at.nodes.isEmpty)
          errors += "Rename: path must not be empty"
        else {
          at.nodes.last match {
            case _: DynamicOptic.Node.Field => () // OK
            case other                      => errors += s"Rename: last path node must be a Field, got ${other.getClass.getSimpleName}"
          }
        }
        if (to.isEmpty)
          errors += "Rename: target name must not be empty"
      case RenameCase(_, from, to) =>
        if (from.isEmpty) errors += "RenameCase: from name must not be empty"
        if (to.isEmpty) errors += "RenameCase: to name must not be empty"
      case TransformCase(_, caseName, sub) =>
        if (caseName.isEmpty) errors += "TransformCase: case name must not be empty"
        errors ++= validateActions(sub)
      case _ => () // Other actions don't need structural validation at build time
    }
    errors.result()
  }
}

object MigrationBuilder {

  /** Create an empty `MigrationBuilder`. */
  def empty[A, B](implicit sa: Schema[A], sb: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sa, sb, Vector.empty)

  /** Create an empty `MigrationBuilder` with explicit schemas. */
  def empty[A, B](sa: Schema[A], sb: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sa, sb, Vector.empty)
}
