package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicOptic

/**
 * A sealed trait representing all possible structural transformations in a
 * schema migration.
 *
 * Every action:
 *   - Has an `at: DynamicOptic` field specifying where it operates in the data
 *     tree.
 *   - Has a `reverse: MigrationAction` method returning the structural inverse.
 *   - Is pure serializable data (no closures, no functions).
 *
 * The double-reverse law `action.reverse.reverse == action` holds for every
 * variant by construction.
 */
sealed trait MigrationAction {

  /** The path into the data tree where this action operates. */
  def at: DynamicOptic

  /** Produces the structural inverse of this action. */
  def reverse: MigrationAction
}

object MigrationAction {

  // ─────────────────────────────────────────────────────────────────────────
  // Record Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Adds a new field to a record. The `defaultValue` expression provides the
   * value for the new field.
   *
   * Reverse: [[DropField]] with the same default stored for re-addition.
   */
  final case class AddField(
    at: DynamicOptic,
    fieldName: String,
    defaultValue: MigrationExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, fieldName, defaultValue)
  }

  /**
   * Removes a field from a record. The `reverseDefault` expression stores the
   * default value so that [[AddField]] can restore the field on reversal.
   *
   * Reverse: [[AddField]] with the stored default.
   */
  final case class DropField(
    at: DynamicOptic,
    fieldName: String,
    reverseDefault: MigrationExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, fieldName, reverseDefault)
  }

  /**
   * Renames a field from `fromName` to `toName`.
   *
   * Reverse: [[Rename]] with `fromName` and `toName` swapped.
   */
  final case class Rename(
    at: DynamicOptic,
    fromName: String,
    toName: String
  ) extends MigrationAction {
    def reverse: MigrationAction = Rename(at, toName, fromName)
  }

  /**
   * Transforms the value of a field using `expr`, with `reverseExpr` for the
   * inverse transformation.
   *
   * Reverse: [[TransformValue]] with `expr` and `reverseExpr` swapped.
   */
  final case class TransformValue(
    at: DynamicOptic,
    fieldName: String,
    expr: MigrationExpr,
    reverseExpr: MigrationExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, fieldName, reverseExpr, expr)
  }

  /**
   * Converts an optional field to a required field. The `default` expression
   * supplies a value when the field is `None`.
   *
   * Reverse: [[Optionalize]] with the stored default.
   */
  final case class Mandate(
    at: DynamicOptic,
    fieldName: String,
    default: MigrationExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at, fieldName, default)
  }

  /**
   * Converts a required field to an optional field. The `defaultForNone`
   * expression is stored so that [[Mandate]] can restore the field on reversal.
   *
   * Reverse: [[Mandate]] with the stored default.
   */
  final case class Optionalize(
    at: DynamicOptic,
    fieldName: String,
    defaultForNone: MigrationExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, fieldName, defaultForNone)
  }

  /**
   * Joins multiple source fields into a single target field using `joinExpr`.
   * The `splitExprs` provide the reverse decomposition.
   *
   * Reverse: [[Split]] constructed from the same parameters in reverse roles.
   */
  final case class Join(
    at: DynamicOptic,
    sourceFields: Chunk[String],
    targetField: String,
    joinExpr: MigrationExpr,
    splitExprs: Chunk[(String, MigrationExpr)]
  ) extends MigrationAction {
    def reverse: MigrationAction =
      Split(at, targetField, splitExprs, joinExpr)
  }

  /**
   * Splits a single source field into multiple target fields using
   * `targetExprs`. The `joinExprForReverse` provides the reverse combination.
   *
   * Reverse: [[Join]] constructed from the same parameters in reverse roles.
   */
  final case class Split(
    at: DynamicOptic,
    sourceField: String,
    targetExprs: Chunk[(String, MigrationExpr)],
    joinExprForReverse: MigrationExpr
  ) extends MigrationAction {
    def reverse: MigrationAction =
      Join(at, targetExprs.map(_._1), sourceField, joinExprForReverse, targetExprs)
  }

  /**
   * Changes the type of a field using `coercion`, with `reverseCoercion` for
   * the inverse conversion.
   *
   * Reverse: [[ChangeType]] with `coercion` and `reverseCoercion` swapped.
   */
  final case class ChangeType(
    at: DynamicOptic,
    fieldName: String,
    coercion: MigrationExpr,
    reverseCoercion: MigrationExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(at, fieldName, reverseCoercion, coercion)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Enum Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Renames a variant case from `fromCase` to `toCase`.
   *
   * Reverse: [[RenameCase]] with `fromCase` and `toCase` swapped.
   */
  final case class RenameCase(
    at: DynamicOptic,
    fromCase: String,
    toCase: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, toCase, fromCase)
  }

  /**
   * Transforms the payload of a specific variant case by applying a sequence of
   * inner migration actions.
   *
   * Reverse: [[TransformCase]] with the inner actions reversed (both order and
   * individually).
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Chunk[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction =
      TransformCase(at, caseName, actions.reverse.map(_.reverse))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Collection / Map Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Transforms all elements of a sequence using `expr`, with `reverseExpr` for
   * the inverse.
   *
   * Reverse: [[TransformElements]] with `expr` and `reverseExpr` swapped.
   */
  final case class TransformElements(
    at: DynamicOptic,
    expr: MigrationExpr,
    reverseExpr: MigrationExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, reverseExpr, expr)
  }

  /**
   * Transforms all keys of a map using `expr`, with `reverseExpr` for the
   * inverse.
   *
   * Reverse: [[TransformKeys]] with `expr` and `reverseExpr` swapped.
   */
  final case class TransformKeys(
    at: DynamicOptic,
    expr: MigrationExpr,
    reverseExpr: MigrationExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, reverseExpr, expr)
  }

  /**
   * Transforms all values of a map using `expr`, with `reverseExpr` for the
   * inverse.
   *
   * Reverse: [[TransformValues]] with `expr` and `reverseExpr` swapped.
   */
  final case class TransformValues(
    at: DynamicOptic,
    expr: MigrationExpr,
    reverseExpr: MigrationExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, reverseExpr, expr)
  }
}
