package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

/**
 * A pure, serializable description of a single structural transformation.
 *
 * All record actions encode the target field in the [[DynamicOptic]] path
 * (i.e., the path terminates in the field being operated on). Actions form the
 * atoms of a [[DynamicMigration]] and can be reversed to support bidirectional
 * schema evolution.
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
   *   path to the field to add (terminates in the field name)
   * @param default
   *   expression that produces the default value for the new field
   */
  final case class AddField(
    at: DynamicOptic,
    default: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, default)
  }

  /**
   * Drop a field from a record.
   *
   * @param at
   *   path to the field to drop (terminates in the field name)
   * @param defaultForReverse
   *   expression used when reversing (re-adding the field)
   */
  final case class DropField(
    at: DynamicOptic,
    defaultForReverse: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  /**
   * Rename a field in a record.
   *
   * @param at
   *   path to the field to rename (terminates in the old field name)
   * @param to
   *   new field name
   */
  final case class Rename(
    at: DynamicOptic,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = {
      val nodes   = at.nodes
      val oldName = nodes.last.asInstanceOf[DynamicOptic.Node.Field].name
      val newPath = new DynamicOptic(nodes.updated(nodes.length - 1, DynamicOptic.Node.Field(to)))
      Rename(newPath, oldName)
    }
  }

  /**
   * Transform a field's value using a pure expression.
   *
   * @param at
   *   path to the field to transform (terminates in the field name)
   * @param transform
   *   a [[DynamicSchemaExpr]] describing the conversion
   */
  final case class TransformValue(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, transform.reverse)
  }

  /**
   * Make an optional field mandatory by providing a default for None values.
   *
   * @param at
   *   path to the optional field (terminates in the field name)
   * @param default
   *   expression to evaluate when the source is None/Null
   */
  final case class Mandate(
    at: DynamicOptic,
    default: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }

  /**
   * Make a mandatory field optional (wrapping its value).
   *
   * @param at
   *   path to the field to optionalize (terminates in the field name)
   */
  final case class Optionalize(
    at: DynamicOptic
  ) extends MigrationAction {
    def reverse: MigrationAction =
      Mandate(at, DynamicSchemaExpr.Literal(zio.blocks.schema.DynamicValue.Null))
  }

  /**
   * Change a field's primitive type.
   *
   * @param at
   *   path to the field whose type changes (terminates in the field name)
   * @param converter
   *   a [[DynamicSchemaExpr]] for the type conversion
   */
  final case class ChangeType(
    at: DynamicOptic,
    converter: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(at, converter.reverse)
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
   *   a [[DynamicSchemaExpr]] to apply to each element
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, transform.reverse)
  }

  /**
   * Transform all keys in a map.
   *
   * @param at
   *   path to the map
   * @param transform
   *   a [[DynamicSchemaExpr]] to apply to each key
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, transform.reverse)
  }

  /**
   * Transform all values in a map.
   *
   * @param at
   *   path to the map
   * @param transform
   *   a [[DynamicSchemaExpr]] to apply to each value
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, transform.reverse)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Join / Split Actions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Join multiple source fields into a single target field (as a nested
   * record).
   *
   * The source fields are removed from the record and combined into a new
   * [[DynamicValue.Record]] stored under `targetField`.
   *
   * @param at
   *   path to the record (or root)
   * @param sourceFields
   *   names of the fields to join
   * @param targetField
   *   name of the combined target field
   */
  final case class Join(
    at: DynamicOptic,
    sourceFields: Vector[String],
    targetField: String
  ) extends MigrationAction {
    def reverse: MigrationAction = Split(at, targetField, sourceFields)
  }

  /**
   * Split a single source field (expected to be a record) into multiple target
   * fields.
   *
   * The source field is removed and its inner fields are spread into the parent
   * record. Only fields listed in `targetFields` are extracted.
   *
   * @param at
   *   path to the record (or root)
   * @param sourceField
   *   name of the field to split (must contain a record)
   * @param targetFields
   *   names of the fields to extract from the nested record
   */
  final case class Split(
    at: DynamicOptic,
    sourceField: String,
    targetFields: Vector[String]
  ) extends MigrationAction {
    def reverse: MigrationAction = Join(at, targetFields, sourceField)
  }
}
