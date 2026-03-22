package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, SchemaExpr}

/**
 * A migration action represents a single structural transformation operation.
 * 
 * All actions operate at a path (represented by DynamicOptic) and can be
 * reversed to enable bidirectional migrations.
 * 
 * Migration actions are pure data - they contain no functions, closures, or
 * reflection, making them fully serializable.
 */
sealed trait MigrationAction extends Product with Serializable {
  
  /**
   * The path where this action operates.
   */
  def at: DynamicOptic
  
  /**
   * Returns the structural reverse of this action.
   * 
   * The reverse of a reverse should be equivalent to the original action
   * (structural inverse law: m.reverse.reverse == m).
   */
  def reverse: MigrationAction
}

object MigrationAction {

  // ============================================================================
  // Record Actions
  // ============================================================================

  /**
   * Adds a new field with a default value.
   * 
   * Reverse: DropField with the same default used for the reverse.
   */
  final case class AddField(
    at: DynamicOptic,
    fieldName: String,
    default: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, fieldName, default)
  }

  /**
   * Drops a field from a record.
   * 
   * Reverse: AddField with the default value used for the reverse migration.
   */
  final case class DropField(
    at: DynamicOptic,
    fieldName: String,
    defaultForReverse: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, fieldName, defaultForReverse)
  }

  /**
   * Renames a field from one name to another.
   * 
   * Reverse: Rename with swapped names.
   */
  final case class RenameField(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameField(at, to, from)
  }

  /**
   * Transforms a field value using a SchemaExpr.
   * 
   * The transform must be a primitive-to-primitive transformation.
   */
  final case class TransformValue(
    at: DynamicOptic,
    fieldName: String,
    transform: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = this // Best-effort: identity reverse
  }

  /**
   * Mandates that an optional field must have a value, providing a default
   * if the field is missing.
   * 
   * Reverse: Optionalize with the same default.
   */
  final case class MandateField(
    at: DynamicOptic,
    fieldName: String,
    default: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = OptionalizeField(at, fieldName)
  }

  /**
   * Makes a required field optional.
   * 
   * Reverse: MandateField with a default value.
   */
  final case class OptionalizeField(
    at: DynamicOptic,
    fieldName: String
  ) extends MigrationAction {
    def reverse: MigrationAction = this // Cannot determine default in reverse
  }

  /**
   * Changes the type of a field using a converter.
   * 
   * The converter must handle primitive-to-primitive conversion only.
   */
  final case class ChangeFieldType(
    at: DynamicOptic,
    fieldName: String,
    converter: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = this // Best-effort: identity reverse
  }

  /**
   * Joins multiple source paths into a single field using a combiner.
   * 
   * The combiner produces a primitive value from multiple inputs.
   */
  final case class JoinFields(
    at: DynamicOptic,
    targetField: String,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = SplitField(at, targetField, sourcePaths, combiner)
  }

  /**
   * Splits a single field into multiple target paths using a splitter.
   */
  final case class SplitField(
    at: DynamicOptic,
    sourceField: String,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = JoinFields(at, sourceField, targetPaths, splitter)
  }

  // ============================================================================
  // Enum Actions
  // ============================================================================

  /**
   * Renames a case in a sum type (enum).
   * 
   * Reverse: RenameCase with swapped names.
   */
  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /**
   * Transforms a case using nested migration actions.
   * 
   * This allows applying field-level transformations within a specific case.
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(
      at,
      caseName,
      actions.map(_.reverse).reverse
    )
  }

  // ============================================================================
  // Collection Actions
  // ============================================================================

  /**
   * Transforms each element in a collection.
   * 
   * The transform operates on each element individually.
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = this // Best-effort: identity reverse
  }

  // ============================================================================
  // Map Actions
  // ============================================================================

  /**
   * Transforms all keys in a map.
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = this // Best-effort: identity reverse
  }

  /**
   * Transforms all values in a map.
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: SchemaExpr[?]
  ) extends MigrationAction {
    def reverse: MigrationAction = this // Best-effort: identity reverse
  }

  // ============================================================================
  // Convenience Constructors
  // ============================================================================

  def addField(at: DynamicOptic, fieldName: String, default: SchemaExpr[?]): MigrationAction =
    AddField(at, fieldName, default)

  def dropField(at: DynamicOptic, fieldName: String, defaultForReverse: SchemaExpr[?]): MigrationAction =
    DropField(at, fieldName, defaultForReverse)

  def renameField(at: DynamicOptic, from: String, to: String): MigrationAction =
    RenameField(at, from, to)

  def transformValue(at: DynamicOptic, fieldName: String, transform: SchemaExpr[?]): MigrationAction =
    TransformValue(at, fieldName, transform)

  def mandateField(at: DynamicOptic, fieldName: String, default: SchemaExpr[?]): MigrationAction =
    MandateField(at, fieldName, default)

  def optionalizeField(at: DynamicOptic, fieldName: String): MigrationAction =
    OptionalizeField(at, fieldName)

  def changeFieldType(at: DynamicOptic, fieldName: String, converter: SchemaExpr[?]): MigrationAction =
    ChangeFieldType(at, fieldName, converter)

  def joinFields(
    at: DynamicOptic,
    targetField: String,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[?]
  ): MigrationAction =
    JoinFields(at, targetField, sourcePaths, combiner)

  def splitField(
    at: DynamicOptic,
    sourceField: String,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[?]
  ): MigrationAction =
    SplitField(at, sourceField, targetPaths, splitter)

  def renameCase(at: DynamicOptic, from: String, to: String): MigrationAction =
    RenameCase(at, from, to)

  def transformCase(at: DynamicOptic, caseName: String, actions: Vector[MigrationAction]): MigrationAction =
    TransformCase(at, caseName, actions)

  def transformElements(at: DynamicOptic, transform: SchemaExpr[?]): MigrationAction =
    TransformElements(at, transform)

  def transformKeys(at: DynamicOptic, transform: SchemaExpr[?]): MigrationAction =
    TransformKeys(at, transform)

  def transformValues(at: DynamicOptic, transform: SchemaExpr[?]): MigrationAction =
    TransformValues(at, transform)
}
