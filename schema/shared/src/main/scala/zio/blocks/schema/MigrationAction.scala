package zio.blocks.schema

/**
 * A single migration action that transforms a `DynamicValue` at a specific
 * path.
 *
 * All actions operate at a path represented by `DynamicOptic`, enabling
 * path-based diagnostics and introspection. Each action supports structural
 * reversal via `reverse`.
 *
 * The ADT is fully serializable (no functions or closures) and can be used to
 * generate DDL, upgraders, downgraders, and offline data transforms.
 */
sealed trait MigrationAction {

  /**
   * The path in the value structure where this action operates.
   */
  def at: DynamicOptic

  /**
   * The structural reverse of this action.
   *
   * Satisfies the law: `action.reverse.reverse == action`
   */
  def reverse: MigrationAction
}

object MigrationAction {

  // ----- Record operations -----

  /**
   * Add a field at the given path with a default value.
   *
   * Reverse: `DropField` at the same path, using the default for reverse.
   */
  final case class AddField(
    at: DynamicOptic,
    fieldName: String,
    default: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, fieldName, default)
  }

  /**
   * Drop a field at the given path.
   *
   * Requires a `defaultForReverse` so that the reverse (`AddField`) can
   * reconstruct the dropped field.
   */
  final case class DropField(
    at: DynamicOptic,
    fieldName: String,
    defaultForReverse: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, fieldName, defaultForReverse)
  }

  /**
   * Rename a field from `fromName` to `toName` at the given path.
   *
   * Reverse: rename from `toName` back to `fromName`.
   */
  final case class Rename(
    at: DynamicOptic,
    fromName: String,
    toName: String
  ) extends MigrationAction {
    def reverse: MigrationAction = Rename(at, toName, fromName)
  }

  /**
   * Transform the value at a path using a serializable expression.
   *
   * The `transform` is a `DynamicValue` encoding of the transformation (e.g. a
   * primitive conversion). The `reverseTransform` enables structural reversal.
   */
  final case class TransformValue(
    at: DynamicOptic,
    transform: DynamicValue,
    reverseTransform: Option[DynamicValue]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, reverseTransform.getOrElse(transform), Some(transform))
  }

  /**
   * Make an optional field mandatory, providing a default for `None` values.
   *
   * Reverse: `Optionalize`.
   */
  final case class Mandate(
    at: DynamicOptic,
    default: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }

  /**
   * Make a mandatory field optional (wraps values in `Some`).
   *
   * Reverse: `Mandate` (requires a default; uses `DynamicValue.Null` as
   * fallback).
   */
  final case class Optionalize(
    at: DynamicOptic
  ) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, DynamicValue.Null)
  }

  /**
   * Join multiple source fields into a single target field.
   *
   * Reverse: `Split` with the inverse mapping.
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: DynamicValue,
    splitter: Option[DynamicValue]
  ) extends MigrationAction {
    def reverse: MigrationAction = Split(at, sourcePaths, splitter.getOrElse(combiner), Some(combiner))
  }

  /**
   * Split a single source field into multiple target fields.
   *
   * Reverse: `Join` with the inverse mapping.
   */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: DynamicValue,
    combiner: Option[DynamicValue]
  ) extends MigrationAction {
    def reverse: MigrationAction = Join(at, targetPaths, combiner.getOrElse(splitter), Some(splitter))
  }

  /**
   * Change the type of a field (primitive-to-primitive only).
   *
   * The `converter` encodes how to convert the old type to the new type. The
   * `reverseConverter` enables structural reversal.
   */
  final case class ChangeType(
    at: DynamicOptic,
    converter: DynamicValue,
    reverseConverter: Option[DynamicValue]
  ) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(at, reverseConverter.getOrElse(converter), Some(converter))
  }

  // ----- Enum operations -----

  /**
   * Rename a variant case from `fromName` to `toName`.
   *
   * Reverse: rename from `toName` back to `fromName`.
   */
  final case class RenameCase(
    at: DynamicOptic,
    fromName: String,
    toName: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, toName, fromName)
  }

  /**
   * Transform a variant case's inner value using nested migration actions.
   *
   * Reverse: apply reversed nested actions.
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, caseName, actions.reverse.map(_.reverse))
  }

  // ----- Collection / Map operations -----

  /**
   * Transform all elements in a sequence at the given path.
   *
   * The `transform` encodes the element-level transformation.
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: DynamicValue,
    reverseTransform: Option[DynamicValue]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, reverseTransform.getOrElse(transform), Some(transform))
  }

  /**
   * Transform all keys in a map at the given path.
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: DynamicValue,
    reverseTransform: Option[DynamicValue]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, reverseTransform.getOrElse(transform), Some(transform))
  }

  /**
   * Transform all values in a map at the given path.
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: DynamicValue,
    reverseTransform: Option[DynamicValue]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, reverseTransform.getOrElse(transform), Some(transform))
  }
}
