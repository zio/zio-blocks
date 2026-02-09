package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * A single migration action that operates at a specific path. All actions are
 * fully serializable - no closures or functions. Each action supports
 * structural reversal via the `reverse` method.
 */
sealed trait MigrationAction {

  /** The path at which this action operates. */
  def at: DynamicOptic

  /**
   * Returns the structural inverse of this action. Note: Runtime reversal is
   * best-effort and may fail if information was lost during the forward
   * migration.
   */
  def reverse: MigrationAction
}

object MigrationAction {

  // ==================== Record Actions ====================

  /**
   * Add a new field to a record with a default value. The field name is the
   * last component of the `at` path.
   *
   * @param at
   *   The path to the field to add (must end with a Field node)
   * @param default
   *   The default value for the new field
   */
  final case class AddField(
    at: DynamicOptic,
    default: DynamicValue
  ) extends MigrationAction {
    override def reverse: MigrationAction = DropField(at, default)

    /** The field name, extracted from the path */
    def fieldName: String = at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _                                   => throw new IllegalStateException("AddField path must end with a Field node")
    }
  }

  /**
   * Drop a field from a record. The field name is the last component of the
   * `at` path.
   *
   * @param at
   *   The path to the field to drop (must end with a Field node)
   * @param defaultForReverse
   *   Default value to use when reversing (adding the field back)
   */
  final case class DropField(
    at: DynamicOptic,
    defaultForReverse: DynamicValue
  ) extends MigrationAction {
    override def reverse: MigrationAction = AddField(at, defaultForReverse)

    /** The field name, extracted from the path */
    def fieldName: String = at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _                                   => throw new IllegalStateException("DropField path must end with a Field node")
    }
  }

  /**
   * Rename a field in a record.
   *
   * @param at
   *   The path to the field to rename (must end with a Field node)
   * @param to
   *   The new field name
   */
  final case class Rename(
    at: DynamicOptic,
    to: String
  ) extends MigrationAction {
    override def reverse: MigrationAction = {
      val parentPath = DynamicOptic(at.nodes.dropRight(1))
      Rename(parentPath.field(to), from)
    }

    /** The original field name, extracted from the path */
    def from: String = at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _                                   => throw new IllegalStateException("Rename path must end with a Field node")
    }
  }

  /**
   * Transform a value at a path to a new value. For serializable migrations,
   * the transform is represented as a literal value.
   *
   * @param at
   *   The path to the value to transform
   * @param newValue
   *   The new value to set
   */
  final case class TransformValue(
    at: DynamicOptic,
    newValue: DynamicValue
  ) extends MigrationAction {
    override def reverse: MigrationAction = TransformValue(at, newValue)
  }

  /**
   * Transform a value at a path using an expression. The expression is
   * evaluated at migration time against the input value.
   *
   * @param at
   *   The path to the value to transform
   * @param expr
   *   The expression that computes the new value
   * @param reverseExpr
   *   Optional expression for computing the reverse transformation
   */
  final case class TransformValueExpr(
    at: DynamicOptic,
    expr: MigrationExpr,
    reverseExpr: Option[MigrationExpr] = None
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      TransformValueExpr(at, reverseExpr.getOrElse(expr), Some(expr))
  }

  /**
   * Convert an optional field to a required field.
   *
   * @param at
   *   The path to the optional value
   * @param default
   *   The default value to use if the optional is None
   */
  final case class Mandate(
    at: DynamicOptic,
    default: DynamicValue
  ) extends MigrationAction {
    override def reverse: MigrationAction = Optionalize(at)
  }

  /**
   * Convert a required field to an optional field.
   *
   * @param at
   *   The path to the required value
   */
  final case class Optionalize(
    at: DynamicOptic
  ) extends MigrationAction {
    override def reverse: MigrationAction = Mandate(at, DynamicValue.Null)
  }

  // ==================== Nest/Unnest Actions ====================

  /**
   * Nest multiple fields from a record into a new sub-record.
   *
   * For example, nesting "street", "city", "zip" into an "address" sub-record
   * transforms `{street: "...", city: "...", zip: "...", name: "..."}` into
   * `{address: {street: "...", city: "...", zip: "..."}, name: "..."}`.
   *
   * @param at
   *   The path to the record containing the fields
   * @param fieldName
   *   The name of the new sub-record field
   * @param sourceFields
   *   The names of the fields to nest into the sub-record
   */
  final case class Nest(
    at: DynamicOptic,
    fieldName: String,
    sourceFields: Vector[String]
  ) extends MigrationAction {
    override def reverse: MigrationAction = Unnest(at, fieldName, sourceFields)
  }

  /**
   * Unnest fields from a sub-record back into the parent record.
   *
   * This is the reverse of `Nest`. It extracts the specified fields from a
   * sub-record and places them directly in the parent record, then removes the
   * sub-record.
   *
   * @param at
   *   The path to the record containing the sub-record
   * @param fieldName
   *   The name of the sub-record field to unnest
   * @param extractedFields
   *   The names of the fields to extract from the sub-record
   */
  final case class Unnest(
    at: DynamicOptic,
    fieldName: String,
    extractedFields: Vector[String]
  ) extends MigrationAction {
    override def reverse: MigrationAction = Nest(at, fieldName, extractedFields)
  }

  // ==================== Join/Split Actions ====================

  /**
   * Join multiple fields into a single field.
   *
   * @param at
   *   The path to the target location for the joined value
   * @param sourcePaths
   *   The paths to the source values to join
   * @param combinedValue
   *   The pre-computed combined value (for serializable migrations)
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combinedValue: DynamicValue
  ) extends MigrationAction {
    override def reverse: MigrationAction = Split(at, sourcePaths, combinedValue)
  }

  /**
   * Join multiple fields into a single field using an expression.
   *
   * @param at
   *   The path to the target location for the joined value
   * @param sourcePaths
   *   The paths to the source values to join
   * @param combineExpr
   *   Expression that computes the combined value from the input
   * @param splitExprs
   *   Optional expressions for splitting back (for reverse migration)
   */
  final case class JoinExpr(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combineExpr: MigrationExpr,
    splitExprs: Option[Vector[MigrationExpr]] = None
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      splitExprs match {
        case Some(exprs) => SplitExpr(at, sourcePaths, exprs, Some(combineExpr))
        case None        => SplitExpr(at, sourcePaths, sourcePaths.map(p => MigrationExpr.FieldRef(p)), Some(combineExpr))
      }
  }

  /**
   * Split a single field into multiple fields.
   *
   * @param at
   *   The path to the source value to split
   * @param targetPaths
   *   The paths to the target locations
   * @param splitValue
   *   The pre-computed split value (for serializable migrations)
   */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitValue: DynamicValue
  ) extends MigrationAction {
    override def reverse: MigrationAction = Join(at, targetPaths, splitValue)
  }

  /**
   * Split a single field into multiple fields using expressions.
   *
   * @param at
   *   The path to the source value to split
   * @param targetPaths
   *   The paths to the target locations
   * @param splitExprs
   *   Expressions that compute each target value from the input
   * @param combineExpr
   *   Optional expression for joining back (for reverse migration)
   */
  final case class SplitExpr(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitExprs: Vector[MigrationExpr],
    combineExpr: Option[MigrationExpr] = None
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      combineExpr match {
        case Some(expr) => JoinExpr(at, targetPaths, expr, Some(splitExprs))
        case None       => JoinExpr(at, targetPaths, MigrationExpr.FieldRef(at), Some(splitExprs))
      }
  }

  /**
   * Change the type of a value at a path (primitive-to-primitive only).
   *
   * @param at
   *   The path to the value
   * @param convertedValue
   *   The converted value
   */
  final case class ChangeType(
    at: DynamicOptic,
    convertedValue: DynamicValue
  ) extends MigrationAction {
    override def reverse: MigrationAction = ChangeType(at, convertedValue)
  }

  /**
   * Change the type of a value at a path using an expression.
   *
   * @param at
   *   The path to the value
   * @param convertExpr
   *   Expression that converts the value (typically MigrationExpr.Convert)
   * @param reverseExpr
   *   Optional expression for converting back (for reverse migration)
   */
  final case class ChangeTypeExpr(
    at: DynamicOptic,
    convertExpr: MigrationExpr,
    reverseExpr: Option[MigrationExpr] = None
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      ChangeTypeExpr(at, reverseExpr.getOrElse(convertExpr), Some(convertExpr))
  }

  // ==================== Enum Actions ====================

  /**
   * Rename an enum case.
   *
   * @param at
   *   The path to the enum value
   * @param from
   *   The current case name
   * @param to
   *   The new case name
   */
  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    override def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /**
   * Transform the fields within an enum case.
   *
   * @param at
   *   The path to the enum value
   * @param caseName
   *   The name of the case to transform
   * @param actions
   *   The actions to apply to the case's record
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      TransformCase(at, caseName, actions.reverse.map(_.reverse))
  }

  // ==================== Collection Actions ====================

  /**
   * Transform each element in a collection.
   *
   * @param at
   *   The path to the collection
   * @param elementActions
   *   The actions to apply to each element
   */
  final case class TransformElements(
    at: DynamicOptic,
    elementActions: Vector[MigrationAction]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      TransformElements(at, elementActions.reverse.map(_.reverse))
  }

  // ==================== Map Actions ====================

  /**
   * Transform each key in a map.
   *
   * @param at
   *   The path to the map
   * @param keyActions
   *   The actions to apply to each key
   */
  final case class TransformKeys(
    at: DynamicOptic,
    keyActions: Vector[MigrationAction]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      TransformKeys(at, keyActions.reverse.map(_.reverse))
  }

  /**
   * Transform each value in a map.
   *
   * @param at
   *   The path to the map
   * @param valueActions
   *   The actions to apply to each value
   */
  final case class TransformValues(
    at: DynamicOptic,
    valueActions: Vector[MigrationAction]
  ) extends MigrationAction {
    override def reverse: MigrationAction =
      TransformValues(at, valueActions.reverse.map(_.reverse))
  }
}
