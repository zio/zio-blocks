package zio.blocks.schema

/**
 * A fluent builder for constructing typed migrations from `A` to `B`.
 *
 * Provides methods that accept `DynamicOptic` paths directly. Version-specific
 * traits (`MigrationBuilderVersionSpecific`) add macro-based selector methods
 * for ergonomic use with lambda expressions (e.g. `_.fieldName`).
 *
 * @param sourceSchema
 *   Schema for the source type
 * @param targetSchema
 *   Schema for the target type
 * @param actions
 *   Accumulated migration actions
 */
class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) extends MigrationBuilderVersionSpecific[A, B] {

  /**
   * Build the migration with full validation.
   */
  def build: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /**
   * Build the migration without full validation.
   */
  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  // ----- Record operations (DynamicOptic-based) -----

  def addField(at: DynamicOptic, fieldName: String, default: DynamicValue): MigrationBuilder[A, B] =
    withAction(MigrationAction.AddField(at, fieldName, default))

  def dropField(at: DynamicOptic, fieldName: String, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    withAction(MigrationAction.DropField(at, fieldName, defaultForReverse))

  def renameField(at: DynamicOptic, fromName: String, toName: String): MigrationBuilder[A, B] =
    withAction(MigrationAction.Rename(at, fromName, toName))

  def transformValue(
    at: DynamicOptic,
    transform: DynamicValue,
    reverseTransform: Option[DynamicValue] = None
  ): MigrationBuilder[A, B] =
    withAction(MigrationAction.TransformValue(at, transform, reverseTransform))

  def mandate(at: DynamicOptic, default: DynamicValue): MigrationBuilder[A, B] =
    withAction(MigrationAction.Mandate(at, default))

  def optionalize(at: DynamicOptic): MigrationBuilder[A, B] =
    withAction(MigrationAction.Optionalize(at))

  def changeType(
    at: DynamicOptic,
    converter: DynamicValue,
    reverseConverter: Option[DynamicValue] = None
  ): MigrationBuilder[A, B] =
    withAction(MigrationAction.ChangeType(at, converter, reverseConverter))

  // ----- Enum operations -----

  def renameCase(at: DynamicOptic, fromName: String, toName: String): MigrationBuilder[A, B] =
    withAction(MigrationAction.RenameCase(at, fromName, toName))

  def transformCase(
    at: DynamicOptic,
    caseName: String,
    nestedActions: Vector[MigrationAction]
  ): MigrationBuilder[A, B] =
    withAction(MigrationAction.TransformCase(at, caseName, nestedActions))

  // ----- Collection / Map operations -----

  def transformElements(
    at: DynamicOptic,
    transform: DynamicValue,
    reverseTransform: Option[DynamicValue] = None
  ): MigrationBuilder[A, B] =
    withAction(MigrationAction.TransformElements(at, transform, reverseTransform))

  def transformKeys(
    at: DynamicOptic,
    transform: DynamicValue,
    reverseTransform: Option[DynamicValue] = None
  ): MigrationBuilder[A, B] =
    withAction(MigrationAction.TransformKeys(at, transform, reverseTransform))

  def transformValues(
    at: DynamicOptic,
    transform: DynamicValue,
    reverseTransform: Option[DynamicValue] = None
  ): MigrationBuilder[A, B] =
    withAction(MigrationAction.TransformValues(at, transform, reverseTransform))

  // ----- Raw action -----

  /**
   * Add a raw `MigrationAction` to this builder.
   */
  def addAction(action: MigrationAction): MigrationBuilder[A, B] =
    withAction(action)

  private def withAction(action: MigrationAction): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
}
