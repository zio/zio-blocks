package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * Represents a single migration action that operates at a specific path.
 * All actions are fully serializable (no closures or functions) and
 * support structural reversal.
 */
sealed trait MigrationAction {

  /**
   * The path at which this action operates.
   */
  def at: DynamicOptic

  /**
   * Returns the structural inverse of this action.
   * Note: Runtime reversal is best-effort and may fail if
   * information was lost during the forward migration.
   */
  def reverse: MigrationAction
}

object MigrationAction {

  // ==================== Record Actions ====================

  /**
   * Add a new field to a record with a default value.
   *
   * @param at The path to the record where the field should be added
   * @param fieldName The name of the field to add
   * @param default The default value for the new field (as DynamicValue for serializability)
   */
  final case class AddField(
    at: DynamicOptic,
    fieldName: String,
    default: DynamicValue
  ) extends MigrationAction {
    override def reverse: MigrationAction = DropField(at, fieldName, Some(default))
  }

  /**
   * Drop a field from a record.
   *
   * @param at The path to the record containing the field
   * @param fieldName The name of the field to drop
   * @param defaultForReverse Optional default value to use when reversing (adding the field back)
   */
  final case class DropField(
    at: DynamicOptic,
    fieldName: String,
    defaultForReverse: Option[DynamicValue]
  ) extends MigrationAction {
    override def reverse: MigrationAction = defaultForReverse match {
      case Some(default) => AddField(at, fieldName, default)
      case None          => AddField(at, fieldName, DynamicValue.Record(Vector.empty)) // Placeholder - will fail at runtime
    }
  }

  /**
   * Rename a field in a record.
   *
   * @param at The path to the field to rename (must end with a Field node)
   * @param to The new field name
   */
  final case class Rename(
    at: DynamicOptic,
    to: String
  ) extends MigrationAction {
    override def reverse: MigrationAction = {
      // Extract the old field name from the path and create reverse
      val parentPath = DynamicOptic(at.nodes.dropRight(1))
      Rename(parentPath.field(to), from)
    }

    /** The original field name, extracted from the path */
    def from: String = at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _ => throw new IllegalStateException("Rename path must end with a Field node")
    }
  }

  /**
   * Transform a value at a path using a pure expression.
   * The expression is represented as a serializable DynamicTransform.
   *
   * @param at The path to the value to transform
   * @param transform The transformation to apply
   * @param reverseTransform The reverse transformation (for structural inverse)
   */
  final case class TransformValue(
    at: DynamicOptic,
    transform: DynamicTransform,
    reverseTransform: DynamicTransform
  ) extends MigrationAction {
    override def reverse: MigrationAction = TransformValue(at, reverseTransform, transform)
  }

  /**
   * Convert an optional field to a required field.
   *
   * @param at The path to the optional value
   * @param default The default value to use if the optional is None
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
   * @param at The path to the required value
   */
  final case class Optionalize(
    at: DynamicOptic
  ) extends MigrationAction {
    override def reverse: MigrationAction = Mandate(at, DynamicValue.Record(Vector.empty)) // Placeholder
  }

  /**
   * Join multiple fields into a single field.
   *
   * @param at The path to the target location for the joined value
   * @param sourcePaths The paths to the source values to join
   * @param combiner The combiner expression
   * @param splitter The reverse splitter expression
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: DynamicTransform,
    splitter: DynamicTransform
  ) extends MigrationAction {
    override def reverse: MigrationAction = Split(at, sourcePaths, splitter, combiner)
  }

  /**
   * Split a single field into multiple fields.
   *
   * @param at The path to the source value to split
   * @param targetPaths The paths to the target locations
   * @param splitter The splitter expression
   * @param combiner The reverse combiner expression
   */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: DynamicTransform,
    combiner: DynamicTransform
  ) extends MigrationAction {
    override def reverse: MigrationAction = Join(at, targetPaths, combiner, splitter)
  }

  /**
   * Change the type of a value at a path (primitive-to-primitive only).
   *
   * @param at The path to the value
   * @param converter The conversion expression
   * @param reverseConverter The reverse conversion expression
   */
  final case class ChangeType(
    at: DynamicOptic,
    converter: DynamicTransform,
    reverseConverter: DynamicTransform
  ) extends MigrationAction {
    override def reverse: MigrationAction = ChangeType(at, reverseConverter, converter)
  }

  // ==================== Enum Actions ====================

  /**
   * Rename an enum case.
   *
   * @param at The path to the enum value
   * @param from The current case name
   * @param to The new case name
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
   * @param at The path to the enum value
   * @param caseName The name of the case to transform
   * @param actions The actions to apply to the case's record
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
   * @param at The path to the collection
   * @param transform The transformation to apply to each element
   * @param reverseTransform The reverse transformation
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: DynamicTransform,
    reverseTransform: DynamicTransform
  ) extends MigrationAction {
    override def reverse: MigrationAction = TransformElements(at, reverseTransform, transform)
  }

  // ==================== Map Actions ====================

  /**
   * Transform each key in a map.
   *
   * @param at The path to the map
   * @param transform The transformation to apply to each key
   * @param reverseTransform The reverse transformation
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: DynamicTransform,
    reverseTransform: DynamicTransform
  ) extends MigrationAction {
    override def reverse: MigrationAction = TransformKeys(at, reverseTransform, transform)
  }

  /**
   * Transform each value in a map.
   *
   * @param at The path to the map
   * @param transform The transformation to apply to each value
   * @param reverseTransform The reverse transformation
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: DynamicTransform,
    reverseTransform: DynamicTransform
  ) extends MigrationAction {
    override def reverse: MigrationAction = TransformValues(at, reverseTransform, transform)
  }
}

/**
 * A serializable representation of a value transformation.
 * This is a pure data structure that can be interpreted to perform
 * primitive-to-primitive transformations.
 */
sealed trait DynamicTransform

object DynamicTransform {

  /**
   * Use the schema's default value.
   */
  case object DefaultValue extends DynamicTransform

  /**
   * Use a literal value.
   */
  final case class Literal(value: DynamicValue) extends DynamicTransform

  /**
   * Identity transformation (no-op).
   */
  case object Identity extends DynamicTransform

  /**
   * Convert to string.
   */
  case object ToString extends DynamicTransform

  /**
   * Parse a string to an integer.
   */
  case object ParseInt extends DynamicTransform

  /**
   * Parse a string to a long.
   */
  case object ParseLong extends DynamicTransform

  /**
   * Parse a string to a double.
   */
  case object ParseDouble extends DynamicTransform

  /**
   * Parse a string to a boolean.
   */
  case object ParseBoolean extends DynamicTransform

  /**
   * Convert an integer to a long.
   */
  case object IntToLong extends DynamicTransform

  /**
   * Convert an integer to a double.
   */
  case object IntToDouble extends DynamicTransform

  /**
   * Convert a long to an integer (may truncate).
   */
  case object LongToInt extends DynamicTransform

  /**
   * Convert a long to a double.
   */
  case object LongToDouble extends DynamicTransform

  /**
   * Convert a double to an integer (may truncate).
   */
  case object DoubleToInt extends DynamicTransform

  /**
   * Convert a double to a long (may truncate).
   */
  case object DoubleToLong extends DynamicTransform

  /**
   * Concatenate string fields.
   *
   * @param separator The separator between concatenated values
   * @param fieldNames The names of fields to concatenate (in order)
   */
  final case class ConcatFields(separator: String, fieldNames: Vector[String]) extends DynamicTransform

  /**
   * Split a string into multiple values.
   *
   * @param separator The separator to split on
   * @param fieldNames The names of fields to populate with split values
   */
  final case class SplitToFields(separator: String, fieldNames: Vector[String]) extends DynamicTransform

  /**
   * Compose two transforms sequentially.
   */
  final case class Compose(first: DynamicTransform, second: DynamicTransform) extends DynamicTransform
}

