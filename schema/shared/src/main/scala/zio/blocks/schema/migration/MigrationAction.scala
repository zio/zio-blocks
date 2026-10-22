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

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicSchemaExpr}

/**
 * One step of a [[DynamicMigration]], at the path [[at]]. Actions are plain
 * data: paths are [[zio.blocks.schema.DynamicOptic]]s and computed values are
 * [[zio.blocks.schema.DynamicSchemaExpr]]s, evaluated when the action runs.
 */
sealed trait MigrationAction {

  /** The path the action works at. */
  def at: DynamicOptic

  /**
   * Returns the action that undoes this one, computed from the action alone.
   * Adding and dropping a field, renaming a field or a case, `MandateField`,
   * `TransformCase` and `MigrateField` have an inverse; the other actions
   * reverse to an [[MigrationAction.Irreversible]], which fails when it runs. A
   * reversed `DropField` adds the field back with its `defaultForReverse`, not
   * with the value it dropped.
   */
  def reverse: MigrationAction
}

/** The kinds of [[MigrationAction]]. */
object MigrationAction {

  // ==================== Record Actions ====================

  /**
   * Adds the field named by the last node of `at` to the record at the rest of
   * the path, with the value of `default`. The expression is evaluated against
   * the whole value the action runs on, not against the record that gets the
   * field. Fails when the record already has the field or `at` does not end
   * with a field.
   *
   * @param at
   *   the path of the new field
   * @param default
   *   the expression whose value the field gets
   */
  final case class AddField(
    at: DynamicOptic,
    default: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = DropField(at, default)

    /** The name of the field, or `None` when `at` does not end with a field. */
    def fieldName: Option[String] = at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => Some(name)
      case _                                   => None
    }
  }

  /**
   * Removes the field named by the last node of `at` from the record at the
   * rest of the path. Fails when the field is missing or `at` does not end with
   * a field.
   *
   * @param at
   *   the path of the field to remove
   * @param defaultForReverse
   *   the expression whose value the field gets back when the action is
   *   reversed
   */
  final case class DropField(
    at: DynamicOptic,
    defaultForReverse: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = AddField(at, defaultForReverse)

    /** The name of the field, or `None` when `at` does not end with a field. */
    def fieldName: Option[String] = at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => Some(name)
      case _                                   => None
    }
  }

  /**
   * Renames the field at `at` to `to`. Fails when the field is missing, the
   * record already has a field named `to`, or `at` does not end with a field.
   *
   * @param at
   *   the path of the field to rename
   * @param to
   *   the new name
   */
  final case class RenameField(
    at: DynamicOptic,
    to: String
  ) extends MigrationAction {
    override def reverse: MigrationAction = {
      // Extract the old field name from the path and create reverse
      val parentPath = DynamicOptic(at.nodes.dropRight(1))
      from match {
        case Some(oldName) => RenameField(parentPath.field(to), oldName)
        case None          => Irreversible(parentPath, "RenameField")
      }
    }

    /**
     * The current name of the field, or `None` when `at` does not end with a
     * field.
     */
    def from: Option[String] = at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => Some(name)
      case _                                   => None
    }
  }

  /**
   * Replaces the value at `at` with the value of `transform`, evaluated against
   * the value it replaces. It has no inverse.
   *
   * @param at
   *   the path of the value
   * @param transform
   *   the expression that computes the new value
   */
  final case class TransformField(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "TransformField")
  }

  /**
   * Turns an optional value into a required one: replaces a `None` at `at` with
   * the value of `default`, evaluated against the whole value the action runs
   * on, unwraps a `Some`, and leaves any other value as it is. A `Some` unwraps
   * to the record that holds its `value` field rather than to the value itself,
   * so a typed migration from `Option[A]` to `A` fails for every `Some` (see
   * `docs/dev/issues.md`).
   *
   * @param at
   *   the path of the optional value
   * @param default
   *   the expression whose value replaces a `None`
   */
  final case class MandateField(
    at: DynamicOptic,
    default: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = OptionalizeField(at)
  }

  /**
   * Turns a required value into an optional one by wrapping the value at `at`
   * in a `Some` case. The `Some` it builds lacks the `value` field that the
   * schema of `Option` expects, so a typed migration to an `Option` field fails
   * to decode the result (see `docs/dev/issues.md`). It has no inverse.
   *
   * @param at
   *   the path of the value
   */
  final case class OptionalizeField(
    at: DynamicOptic
  ) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "OptionalizeField")
  }

  /**
   * Replaces the value at `at` with the value of `converter`, evaluated against
   * the value it replaces. It is meant for a change of primitive type, but runs
   * exactly as [[TransformField]] does: nothing checks the types. It has no
   * inverse.
   *
   * @param at
   *   the path of the value
   * @param converter
   *   the expression that computes the converted value
   */
  final case class ChangeFieldType(
    at: DynamicOptic,
    converter: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "ChangeFieldType")
  }

  // ==================== Enum Actions ====================

  /**
   * Renames the case `from` of the variant at `at` to `to`. A value in another
   * case is left as it is. Fails when the value at `at` is not a variant or
   * either name is empty.
   *
   * @param at
   *   the path of the variant
   * @param from
   *   the current case name
   * @param to
   *   the new case name
   */
  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    override def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /**
   * Runs `actions` on the inner value of the variant at the rest of `at` when
   * it is in the case named by the last node of `at`, and renames that case to
   * `targetCaseName`. A value in another case is left as it is.
   *
   * @param at
   *   the path of the variant, ending with the case to transform
   * @param targetCaseName
   *   the name of the case after the transformation
   * @param actions
   *   the actions to run on the case's inner value
   */
  final case class TransformCase(
    at: DynamicOptic,
    targetCaseName: String,
    actions: Chunk[MigrationAction]
  ) extends MigrationAction {
    override def reverse: MigrationAction = {
      val sourceCaseName =
        at.nodes.lastOption.collect { case DynamicOptic.Node.Case(name) => name }.getOrElse(targetCaseName)
      TransformCase(
        DynamicOptic(at.nodes.dropRight(1) :+ DynamicOptic.Node.Case(targetCaseName)),
        sourceCaseName,
        actions.reverse.map(_.reverse)
      )
    }
  }

  /**
   * Runs `migration` on the value at `at`, as [[Migration]] composition does
   * for a nested field. Its inverse runs the reverse of `migration`.
   *
   * @param at
   *   the path of the nested value
   * @param migration
   *   the migration to run on it
   */
  final case class MigrateField(
    at: DynamicOptic,
    migration: DynamicMigration
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      MigrateField(at, migration.reverse)
  }

  // ==================== Collection Actions ====================

  /**
   * Replaces each element of the sequence at `at` with the value of
   * `transform`, evaluated against that element. Fails at the first element
   * whose evaluation fails. It has no inverse.
   *
   * @param at
   *   the path of the sequence
   * @param transform
   *   the expression whose value replaces each element
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "TransformElements")
  }

  // ==================== Map Actions ====================

  /**
   * Replaces each key of the map at `at` with the value of `transform`,
   * evaluated against that key. Fails at the first key whose evaluation fails.
   * It has no inverse.
   *
   * @param at
   *   the path of the map
   * @param transform
   *   the expression whose value replaces each key
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "TransformKeys")
  }

  /**
   * Replaces each value of the map at `at` with the value of `transform`,
   * evaluated against that value. Fails at the first value whose evaluation
   * fails. It has no inverse.
   *
   * @param at
   *   the path of the map
   * @param transform
   *   the expression whose value replaces each value
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    override def reverse: MigrationAction = Irreversible(at, "TransformValues")
  }

  /**
   * The reverse of an action that has no inverse, such as `TransformField`.
   * Running it always fails with a `TransformFailed` error naming
   * `originalAction`.
   *
   * @param at
   *   the path where the original action worked
   * @param originalAction
   *   the name of the original action
   */
  final case class Irreversible(
    at: DynamicOptic,
    originalAction: String
  ) extends MigrationAction {

    /** Returns this action: an irreversible action stays irreversible. */
    override def reverse: MigrationAction = this
  }
}
