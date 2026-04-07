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

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveType, Schema, Validation}
import zio.blocks.typeid.TypeId

/**
 * A pure-data expression describing a value transformation used within a
 * [[MigrationAction]].
 *
 * `ValueExpr` is intentionally minimal for this ticket — it covers
 * primitive-to-primitive conversions and the built-in string operations needed
 * to represent Join/Split combiners without closures or reflection.
 *
 * Interpretation lives in [[DynamicMigration.applyAction]], not here. The ADT
 * itself carries no executable code, keeping [[DynamicMigration]] fully
 * serializable.
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

  private sealed trait ValueExprRepr

  private object ValueExprRepr {
    case object DefaultValue extends ValueExprRepr

    final case class Constant(value: DynamicValue) extends ValueExprRepr

    final case class PrimitiveConvert(
      from: String,
      to: String
    ) extends ValueExprRepr

    final case class Concat(separator: String) extends ValueExprRepr

    final case class StringSplit(separator: String) extends ValueExprRepr

    implicit lazy val schema: Schema[ValueExprRepr]                    = Schema.derived[ValueExprRepr]
    implicit lazy val primitiveConvertSchema: Schema[PrimitiveConvert] = Schema.derived[PrimitiveConvert]
  }

  implicit lazy val primitiveConvertSchema: Schema[ValueExpr.PrimitiveConvert] =
    ValueExprRepr.primitiveConvertSchema.transform[ValueExpr.PrimitiveConvert](
      repr =>
        ValueExpr.PrimitiveConvert(
          decodePrimitiveType(repr.from),
          decodePrimitiveType(repr.to)
        ),
      expr =>
        ValueExprRepr.PrimitiveConvert(
          encodePrimitiveType(expr.from),
          encodePrimitiveType(expr.to)
        )
    )(TypeId.of[ValueExpr.PrimitiveConvert])

  implicit lazy val schema: Schema[ValueExpr] =
    ValueExprRepr.schema.transform[ValueExpr](
      {
        case ValueExprRepr.DefaultValue =>
          ValueExpr.DefaultValue
        case ValueExprRepr.Constant(value) =>
          ValueExpr.Constant(value)
        case ValueExprRepr.PrimitiveConvert(from, to) =>
          ValueExpr.PrimitiveConvert(
            decodePrimitiveType(from),
            decodePrimitiveType(to)
          )
        case ValueExprRepr.Concat(separator) =>
          ValueExpr.Concat(separator)
        case ValueExprRepr.StringSplit(separator) =>
          ValueExpr.StringSplit(separator)
      },
      {
        case ValueExpr.DefaultValue =>
          ValueExprRepr.DefaultValue
        case ValueExpr.Constant(value) =>
          ValueExprRepr.Constant(value)
        case expr: ValueExpr.PrimitiveConvert =>
          ValueExprRepr.PrimitiveConvert(
            encodePrimitiveType(expr.from),
            encodePrimitiveType(expr.to)
          )
        case ValueExpr.Concat(separator) =>
          ValueExprRepr.Concat(separator)
        case ValueExpr.StringSplit(separator) =>
          ValueExprRepr.StringSplit(separator)
      }
    )(TypeId.of[ValueExpr])

  private def encodePrimitiveType(primitiveType: PrimitiveType[_]): String =
    primitiveType.typeId.name

  private def decodePrimitiveType(name: String): PrimitiveType[_] = name match {
    case "Unit"           => PrimitiveType.Unit
    case "Boolean"        => PrimitiveType.Boolean(Validation.None)
    case "Byte"           => PrimitiveType.Byte(Validation.None)
    case "Short"          => PrimitiveType.Short(Validation.None)
    case "Int"            => PrimitiveType.Int(Validation.None)
    case "Long"           => PrimitiveType.Long(Validation.None)
    case "Float"          => PrimitiveType.Float(Validation.None)
    case "Double"         => PrimitiveType.Double(Validation.None)
    case "Char"           => PrimitiveType.Char(Validation.None)
    case "String"         => PrimitiveType.String(Validation.None)
    case "BigInt"         => PrimitiveType.BigInt(Validation.None)
    case "BigDecimal"     => PrimitiveType.BigDecimal(Validation.None)
    case "DayOfWeek"      => PrimitiveType.DayOfWeek(Validation.None)
    case "Duration"       => PrimitiveType.Duration(Validation.None)
    case "Instant"        => PrimitiveType.Instant(Validation.None)
    case "LocalDate"      => PrimitiveType.LocalDate(Validation.None)
    case "LocalDateTime"  => PrimitiveType.LocalDateTime(Validation.None)
    case "LocalTime"      => PrimitiveType.LocalTime(Validation.None)
    case "Month"          => PrimitiveType.Month(Validation.None)
    case "MonthDay"       => PrimitiveType.MonthDay(Validation.None)
    case "OffsetDateTime" => PrimitiveType.OffsetDateTime(Validation.None)
    case "OffsetTime"     => PrimitiveType.OffsetTime(Validation.None)
    case "Period"         => PrimitiveType.Period(Validation.None)
    case "Year"           => PrimitiveType.Year(Validation.None)
    case "YearMonth"      => PrimitiveType.YearMonth(Validation.None)
    case "ZoneId"         => PrimitiveType.ZoneId(Validation.None)
    case "ZoneOffset"     => PrimitiveType.ZoneOffset(Validation.None)
    case "ZonedDateTime"  => PrimitiveType.ZonedDateTime(Validation.None)
    case "Currency"       => PrimitiveType.Currency(Validation.None)
    case "UUID"           => PrimitiveType.UUID(Validation.None)
    case other            => throw new IllegalArgumentException(s"Unsupported primitive type: $other")
  }
}

/**
 * A single, serializable step in a [[DynamicMigration]].
 *
 * Every action is addressed by one or more [[DynamicOptic]] paths and carries
 * only pure data — no closures, no lambdas, no reflection. The 14 variants
 * cover the full range of structural schema evolution operations defined in the
 * ZIO Blocks schema migration specification.
 *
 * Paths are always relative to the root of the [[DynamicValue]] being migrated.
 * The interpreter in [[DynamicMigration.applyAction]] is responsible for
 * evaluating each action against a concrete value.
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

  implicit lazy val schema: Schema[MigrationAction] = Schema.derived[MigrationAction]
}
