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

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveType}

/**
 * A pure-data expression describing a value transformation used within a
 * [[MigrationAction]].
 *
 * `ValueExpr` is intentionally minimal for this ticket — it covers
 * primitive-to-primitive conversions and the built-in string operations needed
 * to represent Join/Split combiners without closures or reflection.
 *
 * Interpretation lives in `DynamicMigration`, not here. The ADT itself carries
 * no executable code, keeping [[DynamicMigration]] fully serializable.
 */
sealed trait ValueExpr

object ValueExpr {

  /**
   * Signals that the field's default value should be sourced from the target
   * schema at runtime. Used in [[MigrationAction.AddField]] and
   * [[MigrationAction.Mandate]] when no explicit default is provided.
   */
  case object DefaultValue extends ValueExpr

  /**
   * Replaces the value unconditionally with the given constant
   * [[DynamicValue]].
   */
  final case class Constant(value: DynamicValue) extends ValueExpr

  /**
   * Converts a primitive value from one [[PrimitiveType]] to another.
   *
   * Only numeric widening, numeric-to-string, and string-to-numeric conversions
   * are supported by the interpreter in [[DynamicMigration]]. Unsupported
   * combinations produce a [[MigrationError]] at runtime.
   */
  final case class PrimitiveConvert(
    from: PrimitiveType[_],
    to: PrimitiveType[_]
  ) extends ValueExpr

  /**
   * Concatenates two string values with the given separator. Used as the
   * `combiner` in [[MigrationAction.Join]] when both source fields are strings.
   *
   * Interpretation: `left + separator + right`
   */
  final case class Concat(separator: String) extends ValueExpr

  /**
   * Splits a single string value on the given separator, producing a
   * [[DynamicValue.Sequence]] of string primitives. Used as the `splitter` in
   * [[MigrationAction.Split]].
   */
  final case class StringSplit(separator: String) extends ValueExpr

}

/**
 * A single, serializable step in a [[DynamicMigration]].
 *
 * Every action is addressed by one or more [[DynamicOptic]] paths and carries
 * only pure data — no closures, no lambdas, no reflection. The 17 variants
 * cover the full range of structural schema evolution operations defined in the
 * ZIO Blocks schema migration specification.
 *
 * Paths are always relative to the root of the [[DynamicValue]] being migrated.
 * The interpreter in [[DynamicMigration]] is responsible for evaluating each
 * action against a concrete value.
 */
sealed trait MigrationAction

object MigrationAction {

  /**
   * Adds a new field at `path` to a [[DynamicValue.Record]], computing its
   * initial value via `defaultValue`.
   *
   * The `path` must address a field that does not yet exist in the source
   * record. The final segment of `path` must be a [[DynamicOptic.Node.Field]]
   * node — that name becomes the new field name.
   *
   * Reverse: [[DropField]] with the same `path`.
   */
  final case class AddField(
    path: DynamicOptic,
    defaultValue: ValueExpr
  ) extends MigrationAction

  /**
   * Removes the field at `path` from a [[DynamicValue.Record]].
   *
   * Reverse: [[AddField]] — though the original value is lost, so the reverse
   * must supply an explicit default.
   */
  final case class DropField(path: DynamicOptic) extends MigrationAction

  /**
   * Renames the field addressed by `path`. The `path` must end in a
   * [[DynamicOptic.Node.Field]] node; the field is renamed to `newName` in
   * place, preserving its value and position.
   *
   * Reverse: [[RenameField]] with `path` updated to the new name and `newName`
   * set to the old name.
   */
  final case class RenameField(
    path: DynamicOptic,
    newName: String
  ) extends MigrationAction

  /**
   * Transforms the value at `path` using the given [[ValueExpr]].
   *
   * Suitable for primitive value coercions, constant replacements, and other
   * in-place value changes that do not alter the field's structural position.
   *
   * Reverse: depends on the `expr`; generally not structurally reversible
   * unless `expr` is [[ValueExpr.PrimitiveConvert]] with an inverse.
   */
  final case class TransformValue(
    path: DynamicOptic,
    expr: ValueExpr
  ) extends MigrationAction

  /**
   * Mandates an optional field at `path`, unwrapping it from its
   * [[DynamicValue.Variant]] `Some`/`None` representation.
   *
   * If the field is `None`, `defaultExpr` is evaluated to supply the required
   * value. If `defaultExpr` is [[ValueExpr.DefaultValue]], the interpreter
   * looks up the field's default in the target schema.
   *
   * Reverse: [[Optionalize]] with the same `path`.
   */
  final case class Mandate(
    path: DynamicOptic,
    defaultExpr: ValueExpr
  ) extends MigrationAction

  /**
   * Optionalizes a required field at `path`, wrapping its current value in a
   * `Some` variant.
   *
   * Reverse: [[Mandate]] with the same `path`.
   */
  final case class Optionalize(path: DynamicOptic) extends MigrationAction

  /**
   * Changes the primitive type of the value at `path` using the conversion
   * described by `expr`.
   *
   * The interpreter in [[DynamicMigration]] supports numeric widening (e.g.
   * `Int → Long`), numeric-to-string, and string-to-numeric conversions.
   * Unsupported combinations produce a [[MigrationError]] at runtime.
   *
   * Reverse: [[ChangeType]] with `expr.from` and `expr.to` swapped.
   */
  final case class ChangeType(
    path: DynamicOptic,
    expr: ValueExpr.PrimitiveConvert
  ) extends MigrationAction

  /**
   * Combines the values at `left` and `right` into a single value written to
   * `target`, using `combiner` to describe the merge operation.
   *
   * Both source fields are read before either is removed. The `target` path may
   * be an existing field (overwrite) or a new one. After combining, `left` and
   * `right` are dropped from the record.
   *
   * Reverse: [[Split]] with `from = target`, `toLeft = left`,
   * `toRight = right`, and an appropriate `splitter`.
   */
  final case class Join(
    left: DynamicOptic,
    right: DynamicOptic,
    target: DynamicOptic,
    combiner: ValueExpr
  ) extends MigrationAction

  /**
   * Splits the value at `from` into two values written to `toLeft` and
   * `toRight`, using `splitter` to describe the decomposition.
   *
   * The source field is removed after splitting. The `toLeft` and `toRight`
   * paths may be existing fields (overwrite) or new ones.
   *
   * Reverse: [[Join]] with `left = toLeft`, `right = toRight`, `target = from`,
   * and an appropriate `combiner`.
   */
  final case class Split(
    from: DynamicOptic,
    toLeft: DynamicOptic,
    toRight: DynamicOptic,
    splitter: ValueExpr
  ) extends MigrationAction

  /**
   * Applies `expr` to every element of the sequence at `path`.
   *
   * The `path` must address a [[DynamicValue.Sequence]] node. Each element is
   * transformed in-place. Elements that fail transformation are reported as a
   * [[MigrationError]] with the element index in the path.
   *
   * Reverse: [[TransformElements]] with the inverse of `expr`, if one exists.
   */
  final case class TransformElements(
    path: DynamicOptic,
    expr: ValueExpr
  ) extends MigrationAction

  /**
   * Applies `expr` to every key of the map at `path`.
   *
   * The `path` must address a [[DynamicValue.Map]] node. Key uniqueness after
   * transformation is not enforced — collisions produce undefined behavior.
   *
   * Reverse: [[TransformKeys]] with the inverse of `expr`, if one exists.
   */
  final case class TransformKeys(
    path: DynamicOptic,
    expr: ValueExpr
  ) extends MigrationAction

  /**
   * Applies `expr` to every value of the map at `path`.
   *
   * The `path` must address a [[DynamicValue.Map]] node.
   *
   * Reverse: [[TransformValues]] with the inverse of `expr`, if one exists.
   */
  final case class TransformValues(
    path: DynamicOptic,
    expr: ValueExpr
  ) extends MigrationAction

  /**
   * Renames a [[DynamicValue.Variant]] case from `fromName` to `toName`.
   *
   * If the value at the root is not a `Variant` with case name `fromName`, the
   * action is a no-op.
   *
   * Reverse: [[RenameCase]] with `fromName` and `toName` swapped.
   */
  final case class RenameCase(
    fromName: String,
    toName: String
  ) extends MigrationAction

  /**
   * Applies `expr` to the inner value of a [[DynamicValue.Variant]] whose case
   * name matches `caseName`.
   *
   * If the variant's case name does not match, the action is a no-op.
   *
   * Reverse: [[TransformCase]] with the inverse of `expr`, if one exists.
   */
  final case class TransformCase(
    caseName: String,
    expr: ValueExpr
  ) extends MigrationAction

  /**
   * Applies a nested [[DynamicMigration]] to the value at `path`.
   *
   * The value at `path` is extracted, passed through the nested `migration`,
   * and the result is written back to `path`. This enables composing migrations
   * at different structural levels without flattening all operations into a
   * single action sequence.
   *
   * Typical use is via the [[zio.blocks.schema.migration.MigrationBuilder]]
   * macro DSL method `migrateField`, which accepts a typed
   * `Migration[C, D]` and extracts its underlying `DynamicMigration`.
   *
   * Reverse: [[ApplyMigration]] with `migration.reverse`.
   */
  final case class ApplyMigration(
    path: DynamicOptic,
    migration: DynamicMigration
  ) extends MigrationAction

  /**
   * Copies the value at `from` and inserts it at `to`, leaving `from`
   * intact.
   *
   * Useful for schema evolution where a field must temporarily appear in two
   * places (e.g. during a rolling deployment) or when a value needs to be
   * duplicated into a newly-introduced nested structure.
   *
   * The `to` path must not already exist in the target value; use
   * [[TransformValue]] if you want an overwrite instead.
   *
   * Reverse: [[DropField]] with `path = to` (the copy is discarded;
   * the original at `from` is left in place).
   */
  final case class CopyField(
    from: DynamicOptic,
    to: DynamicOptic
  ) extends MigrationAction

  /**
   * Moves the value at `from` to `to`, removing the source field.
   *
   * Semantically equivalent to read-from-`from`, delete `from`, insert at `to`
   * — but expressed as a single atomic action that can be correctly reversed.
   *
   * Use `MoveField` instead of [[RenameField]] when the target field occupies a
   * different structural path (a different parent record), not just a different
   * name at the same level.
   *
   * Reverse: [[MoveField]] with `from` and `to` swapped.
   */
  final case class MoveField(
    from: DynamicOptic,
    to: DynamicOptic
  ) extends MigrationAction

}
