package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

/**
 * A migration action represents a single transformation step in a
 * [[DynamicMigration]].
 *
 * All actions operate at a specific path (represented by [[DynamicOptic]]) and
 * are:
 *   - Pure data (no user functions, closures, or runtime code generation)
 *   - Fully serializable
 *   - Reversible (structurally; runtime is best-effort)
 *
 * This enables migrations to be stored, inspected, and used to generate DDL,
 * data transforms, etc.
 */
sealed trait MigrationAction {

  /**
   * The path at which this action operates.
   */
  def at: DynamicOptic

  /**
   * Get the structural reverse of this action.
   *
   * The reverse of an action undoes the structural change. For semantic
   * correctness, reverse actions may require additional information (e.g.,
   * default values for dropped fields).
   */
  def reverse: MigrationAction
}

object MigrationAction {

  // ==================== Record Actions ====================

  /**
   * Add a new field to a record at the specified path.
   *
   * @param at
   *   path to the record
   * @param name
   *   name of the new field
   * @param default
   *   expression producing the default value for the new field
   */
  final case class AddField(
    at: DynamicOptic,
    name: String,
    default: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, name, default)
  }

  /**
   * Drop a field from a record at the specified path.
   *
   * @param at
   *   path to the record
   * @param name
   *   name of the field to drop
   * @param defaultForReverse
   *   expression producing the default value when reversing (re-adding the
   *   field)
   */
  final case class DropField(
    at: DynamicOptic,
    name: String,
    defaultForReverse: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, name, defaultForReverse)
  }

  /**
   * Rename a field in a record.
   *
   * @param at
   *   path to the record
   * @param from
   *   original field name
   * @param to
   *   new field name
   */
  final case class RenameField(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameField(at, to, from)
  }

  /**
   * Transform the value at a specific field path.
   *
   * @param at
   *   path to the value to transform
   * @param transform
   *   expression that computes the new value
   * @param reverseTransform
   *   expression that reverses the transform (best-effort)
   */
  final case class TransformValue(
    at: DynamicOptic,
    transform: DynamicSchemaExpr,
    reverseTransform: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, reverseTransform, transform)
  }

  /**
   * Make an optional field mandatory by providing a default for None values.
   *
   * @param at
   *   path to the optional field
   * @param default
   *   expression producing value when the original is None
   */
  final case class Mandate(
    at: DynamicOptic,
    default: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }

  /**
   * Make a mandatory field optional (wrapping values in Some).
   *
   * @param at
   *   path to the field
   */
  final case class Optionalize(
    at: DynamicOptic
  ) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, DynamicSchemaExpr.DefaultValue)
  }

  /**
   * Change the type of a field (primitive-to-primitive only).
   *
   * @param at
   *   path to the field
   * @param converter
   *   expression that converts to the new type
   * @param reverseConverter
   *   expression that converts back to the original type
   */
  final case class ChangeType(
    at: DynamicOptic,
    converter: DynamicSchemaExpr,
    reverseConverter: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(at, reverseConverter, converter)
  }

  /**
   * Join multiple fields into a single field.
   *
   * @param at
   *   path to the new combined field
   * @param sourcePaths
   *   paths to the source fields (relative to the same record as `at`)
   * @param combiner
   *   expression that combines the source values into one
   * @param splitter
   *   expression that splits the combined value back (for reverse)
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: DynamicSchemaExpr,
    splitter: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = Split(at, sourcePaths, splitter, combiner)
  }

  /**
   * Split a single field into multiple fields.
   *
   * @param at
   *   path to the field to split
   * @param targetPaths
   *   paths to the target fields (relative to the same record as `at`)
   * @param splitter
   *   expression that produces values for each target field
   * @param combiner
   *   expression that combines back (for reverse)
   */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: DynamicSchemaExpr,
    combiner: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = Join(at, targetPaths, combiner, splitter)
  }

  // ==================== Enum/Variant Actions ====================

  /**
   * Rename a case in a variant/enum.
   *
   * @param at
   *   path to the variant
   * @param from
   *   original case name
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
   * Transform the structure within a specific case of a variant.
   *
   * @param at
   *   path to the variant
   * @param caseName
   *   name of the case to transform
   * @param actions
   *   nested migration actions to apply within the case
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, caseName, actions.map(_.reverse).reverse)
  }

  // ==================== Collection Actions ====================

  /**
   * Transform all elements in a sequence/collection.
   *
   * @param at
   *   path to the sequence
   * @param transform
   *   expression applied to each element
   * @param reverseTransform
   *   expression to reverse the transform
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: DynamicSchemaExpr,
    reverseTransform: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, reverseTransform, transform)
  }

  // ==================== Map Actions ====================

  /**
   * Transform all keys in a map.
   *
   * @param at
   *   path to the map
   * @param transform
   *   expression applied to each key
   * @param reverseTransform
   *   expression to reverse the transform
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: DynamicSchemaExpr,
    reverseTransform: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, reverseTransform, transform)
  }

  /**
   * Transform all values in a map.
   *
   * @param at
   *   path to the map
   * @param transform
   *   expression applied to each value
   * @param reverseTransform
   *   expression to reverse the transform
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: DynamicSchemaExpr,
    reverseTransform: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, reverseTransform, transform)
  }

  // ==================== Composite/No-Op ====================

  /**
   * A no-op action that does nothing. Useful as identity.
   */
  case object Identity extends MigrationAction {
    val at: DynamicOptic         = DynamicOptic.root
    def reverse: MigrationAction = Identity
  }
}
