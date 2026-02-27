package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * A pure, serializable description of a single structural transformation.
 *
 * All actions operate at a path represented by [[DynamicOptic]]. Actions form
 * the atoms of a [[DynamicMigration]] and can be reversed to support
 * bidirectional schema evolution.
 *
 * No user functions, closures, or runtime code generation — only pure data.
 */
sealed trait MigrationAction {

  /** The path at which this action operates. */
  def at: DynamicOptic

  /** Structurally reverse this action. */
  def reverse: MigrationAction
}

object MigrationAction {

  // ─────────────────────────────────────────────────────────────────────────
  // Record Actions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Add a new field to a record.
   *
   * @param at
   *   path to the record (or root)
   * @param fieldName
   *   name of the field to add
   * @param default
   *   default value for the new field
   */
  final case class AddField(
    at: DynamicOptic,
    fieldName: String,
    default: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, fieldName, Some(default))
  }

  /**
   * Drop a field from a record.
   *
   * @param at
   *   path to the record (or root)
   * @param fieldName
   *   name of the field to drop
   * @param defaultForReverse
   *   value to use when reversing (adding the field back)
   */
  final case class DropField(
    at: DynamicOptic,
    fieldName: String,
    defaultForReverse: Option[DynamicValue]
  ) extends MigrationAction {
    def reverse: MigrationAction = defaultForReverse match {
      case Some(dv) => AddField(at, fieldName, dv)
      case None     => AddField(at, fieldName, DynamicValue.Null)
    }
  }

  /**
   * Rename a field in a record.
   *
   * @param at
   *   path to the record (or root)
   * @param from
   *   current field name
   * @param to
   *   new field name
   */
  final case class Rename(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = Rename(at, to, from)
  }

  /**
   * Transform a field's value using a primitive conversion.
   *
   * @param at
   *   path to the record (or root)
   * @param fromField
   *   source field name
   * @param toField
   *   target field name (may differ from source if also renaming)
   * @param transform
   *   a [[PrimitiveTransform]] describing the conversion
   */
  final case class TransformValue(
    at: DynamicOptic,
    fromField: String,
    toField: String,
    transform: PrimitiveTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, toField, fromField, transform.reverse)
  }

  /**
   * Make an optional field mandatory by providing a default for None values.
   *
   * @param at
   *   path to the record (or root)
   * @param fieldName
   *   the field to mandate
   * @param default
   *   value to substitute when the source is None/Null
   */
  final case class Mandate(
    at: DynamicOptic,
    fieldName: String,
    targetFieldName: String,
    default: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at, targetFieldName, fieldName)
  }

  /**
   * Make a mandatory field optional (wrapping its value).
   *
   * @param at
   *   path to the record (or root)
   * @param fieldName
   *   the field to optionalize
   */
  final case class Optionalize(
    at: DynamicOptic,
    fieldName: String,
    targetFieldName: String
  ) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, targetFieldName, fieldName, DynamicValue.Null)
  }

  /**
   * Change a field's primitive type.
   *
   * @param at
   *   path to the record (or root)
   * @param fieldName
   *   the field whose type changes
   * @param targetFieldName
   *   the target field name
   * @param converter
   *   a [[PrimitiveTransform]] for the type conversion
   */
  final case class ChangeType(
    at: DynamicOptic,
    fieldName: String,
    targetFieldName: String,
    converter: PrimitiveTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(at, targetFieldName, fieldName, converter.reverse)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Enum Actions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Rename a case in a sum type (enum/sealed trait).
   *
   * @param at
   *   path to the variant value
   * @param from
   *   current case name
   * @param to
   *   new case name
   */
  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /**
   * Transform the fields within a specific enum case.
   *
   * @param at
   *   path to the variant value
   * @param caseName
   *   which case to transform
   * @param actions
   *   nested migration actions to apply to the case's record
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, caseName, actions.reverse.map(_.reverse))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Collection / Map Actions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Transform all elements in a sequence.
   *
   * @param at
   *   path to the sequence
   * @param transform
   *   a [[PrimitiveTransform]] to apply to each element
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: PrimitiveTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, transform.reverse)
  }

  /**
   * Transform all keys in a map.
   *
   * @param at
   *   path to the map
   * @param transform
   *   a [[PrimitiveTransform]] to apply to each key
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: PrimitiveTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, transform.reverse)
  }

  /**
   * Transform all values in a map.
   *
   * @param at
   *   path to the map
   * @param transform
   *   a [[PrimitiveTransform]] to apply to each value
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: PrimitiveTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, transform.reverse)
  }
}
