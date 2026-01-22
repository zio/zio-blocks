package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * Immutable builder for constructing typed migrations. Follows the builder
 * pattern where each method returns a new instance with the added action.
 *
 * Phase 8: Accepts DynamicOptic directly (macro-based selectors come in Phase
 * 9)
 *
 * @param sourceSchema
 *   Schema for the source type A
 * @param targetSchema
 *   Schema for the target type B
 * @param actions
 *   Accumulated migration actions
 * @tparam A
 *   Source type
 * @tparam B
 *   Target type
 */
final case class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction]
) {

  // ===== Record Operations =====
  // Note: These methods are private[migration] and called by macro-generated code.
  // Users should use the selector-based API from MigrationBuilderSyntax.

  /**
   * Adds a field to a record with a default value.
   */
  private[migration] def addField(
    target: DynamicOptic,
    default: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.AddField(target, default))

  /**
   * Removes a field from a record.
   */
  private[migration] def dropField(
    source: DynamicOptic,
    defaultForReverse: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.DropField(source, defaultForReverse))

  /**
   * Renames a field in a record.
   */
  private[migration] def renameField(
    from: DynamicOptic,
    to: String
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Rename(from, to))

  /**
   * Applies a transformation expression to a field value.
   */
  private[migration] def transformField(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValue(at, transform))

  /**
   * Unwraps an Option field, using default for None values.
   */
  private[migration] def mandateField(
    at: DynamicOptic,
    default: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Mandate(at, default))

  /**
   * Wraps a field value in Option (as Some).
   */
  private[migration] def optionalizeField(
    at: DynamicOptic,
    defaultForReverse: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Optionalize(at, defaultForReverse))

  /**
   * Converts a field from one primitive type to another.
   */
  private[migration] def changeFieldType(
    at: DynamicOptic,
    converter: PrimitiveConverter
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.ChangeType(at, converter))

  // ===== Multi-Field Operations =====

  /**
   * Joins multiple source fields into a single target field using a combiner
   * expression.
   */
  def joinFields(
    target: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Join(target, sourcePaths, combiner))

  /**
   * Splits a single source field into multiple target fields using a splitter
   * expression.
   */
  def splitField(
    source: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Split(source, targetPaths, splitter))

  // ===== Enum Operations =====

  /**
   * Renames a variant case.
   */
  def renameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RenameCase(at, from, to))

  /**
   * Applies nested migration actions to a specific variant case.
   */
  def transformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction]
  ): MigrationBuilder[A, B] =
    copy(actions = this.actions :+ MigrationAction.TransformCase(at, caseName, actions))

  // ===== Collection Operations =====

  /**
   * Applies a transformation to all elements in a sequence.
   */
  def transformElements(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformElements(at, transform))

  /**
   * Applies a transformation to all keys in a map.
   */
  def transformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformKeys(at, transform))

  /**
   * Applies a transformation to all values in a map.
   */
  def transformValues(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValues(at, transform))

  // ===== Build Methods =====

  /**
   * Builds the migration with full validation. Validates that all source fields
   * are handled and all target fields are provided.
   *
   * @return
   *   Right(migration) if validation passes, Left(ValidationError) otherwise
   */
  def build: Either[MigrationError.ValidationError, Migration[A, B]] =
    validate() match {
      case Left(error) => Left(error)
      case Right(())   => Right(buildPartial)
    }

  /**
   * Builds the migration without validation. Useful for partial migrations or
   * when validation is not desired.
   */
  def buildPartial: Migration[A, B] =
    Migration(
      dynamicMigration = DynamicMigration(actions),
      sourceSchema = sourceSchema,
      targetSchema = targetSchema
    )

  // ===== Private Validation Helpers =====

  /**
   * Validates the migration by checking that all source fields are handled and
   * all target fields are provided.
   */
  private def validate(): Either[MigrationError.ValidationError, Unit] = {
    // Extract all field paths from source and target schemas
    val sourcePaths = extractFieldPaths(sourceSchema, "")
    val targetPaths = extractFieldPaths(targetSchema, "")

    // Fields that exist in both schemas are automatically handled/provided
    // (they pass through unchanged unless explicitly migrated)
    val unchanged = sourcePaths.intersect(targetPaths)

    // Track which paths are handled/provided by actions
    val handled  = actions.flatMap(handledSourcePaths).toSet
    val provided = actions.flatMap(providedTargetPaths).toSet

    // Check 1: Unhandled source fields and unprovided target fields
    // Exclude unchanged fields from validation
    val unhandled  = (sourcePaths -- handled) -- unchanged
    val unprovided = (targetPaths -- provided) -- unchanged

    // Check 2: Actions referencing non-existent fields
    // Handled fields must exist in source schema (or be unchanged)
    val invalidHandled = handled -- sourcePaths -- unchanged
    // Provided fields must exist in target schema (or be unchanged)
    val invalidProvided = provided -- targetPaths -- unchanged

    val allErrors = unhandled.nonEmpty || unprovided.nonEmpty ||
      invalidHandled.nonEmpty || invalidProvided.nonEmpty

    if (allErrors) {
      // Combine all validation errors into one
      // Invalid handled/provided fields are reported as unprovided/unhandled
      Left(
        MigrationError.ValidationError(
          path = DynamicOptic.root,
          unhandledSourceFields = unhandled ++ invalidHandled,
          unprovidedTargetFields = unprovided ++ invalidProvided
        )
      )
    } else {
      Right(())
    }
  }

  /**
   * Extracts all field paths from a schema recursively.
   *
   * @param schema
   *   The schema to extract paths from
   * @param prefix
   *   The current path prefix (e.g., "address." for nested fields)
   * @return
   *   Set of all field paths (e.g., "name", "address.street")
   */
  private def extractFieldPaths(schema: Schema[?], prefix: String): Set[String] =
    if (schema.reflect.isInstanceOf[Reflect.Record[binding.Binding, ?]]) {
      val record = schema.reflect.asInstanceOf[Reflect.Record[binding.Binding, Any]]
      // Extract field names and recursively process nested records
      record.fields.flatMap { field =>
        val fieldPath = if (prefix.isEmpty) field.name else s"$prefix.${field.name}"

        // Only include leaf fields (non-records) or recurse into nested records
        if (field.value.isInstanceOf[Reflect.Record[binding.Binding, ?]]) {
          val nestedRecord = field.value.asInstanceOf[Reflect.Record[binding.Binding, Any]]
          // For nested records, don't include the parent field itself
          // Only extract the nested leaf fields
          extractFieldPaths(new Schema(nestedRecord.asInstanceOf[Reflect.Bound[Any]]), fieldPath)
        } else {
          // For leaf fields (primitives, sequences, etc.), include the field
          Set(fieldPath)
        }
      }.toSet
    } else if (schema.reflect.isInstanceOf[Reflect.Variant[binding.Binding, ?]]) {
      val variant = schema.reflect.asInstanceOf[Reflect.Variant[binding.Binding, Any]]
      // For variants, extract case names and nested structure
      variant.cases.flatMap { caseField =>
        val casePath = if (prefix.isEmpty) caseField.name else s"$prefix.${caseField.name}"

        // Recursively extract nested paths in case values
        if (caseField.value.isInstanceOf[Reflect.Record[binding.Binding, ?]]) {
          val nestedRecord = caseField.value.asInstanceOf[Reflect.Record[binding.Binding, Any]]
          // For variant cases with record values, include the case name
          // plus all nested fields
          Set(casePath) ++ extractFieldPaths(
            new Schema(nestedRecord.asInstanceOf[Reflect.Bound[Any]]),
            casePath
          )
        } else {
          // For simple variant cases, just include the case name
          Set(casePath)
        }
      }.toSet
    } else {
      // For primitives, sequences, maps, etc., no fields to extract
      Set.empty
    }

  /**
   * Converts a DynamicOptic to a field path string.
   *
   * @param optic
   *   The optic to convert
   * @return
   *   Path string (e.g., "address.street")
   */
  private def opticToPath(optic: DynamicOptic): String =
    optic.nodes.collect {
      case DynamicOptic.Node.Field(name) => name
      case DynamicOptic.Node.Case(name)  => name
    }
      .mkString(".")

  /**
   * Determines which source paths are handled by an action.
   */
  private def handledSourcePaths(action: MigrationAction): Set[String] =
    action match {
      case MigrationAction.AddField(_, _) =>
        // AddField doesn't handle source fields
        Set.empty

      case MigrationAction.DropField(at, _) =>
        // DropField handles the source field
        Set(opticToPath(at))

      case MigrationAction.Rename(from, _) =>
        // Rename handles the source field
        Set(opticToPath(from))

      case MigrationAction.TransformValue(at, _) =>
        // TransformValue handles the field at the path
        Set(opticToPath(at))

      case MigrationAction.ChangeType(at, _) =>
        // ChangeType handles the field at the path
        Set(opticToPath(at))

      case MigrationAction.Mandate(at, _) =>
        // Mandate handles the field at the path
        Set(opticToPath(at))

      case MigrationAction.Optionalize(at, _) =>
        // Optionalize handles the field at the path
        Set(opticToPath(at))

      case MigrationAction.Join(_, sourcePaths, _) =>
        // Join handles all source fields
        sourcePaths.map(opticToPath).toSet

      case MigrationAction.Split(at, _, _) =>
        // Split handles the source field
        Set(opticToPath(at))

      case MigrationAction.RenameCase(at, _, _) =>
        // RenameCase handles the variant case
        Set(opticToPath(at))

      case MigrationAction.TransformCase(at, caseName, _) =>
        // TransformCase handles the specific variant case
        val basePath = opticToPath(at)
        if (basePath.isEmpty) Set(caseName) else Set(s"$basePath.$caseName")

      case MigrationAction.TransformElements(at, _) =>
        // TransformElements doesn't change structure, handles the collection
        Set(opticToPath(at))

      case MigrationAction.TransformKeys(at, _) =>
        // TransformKeys doesn't change structure, handles the map
        Set(opticToPath(at))

      case MigrationAction.TransformValues(at, _) =>
        // TransformValues doesn't change structure, handles the map
        Set(opticToPath(at))
    }

  /**
   * Determines which target paths are provided by an action.
   */
  private def providedTargetPaths(action: MigrationAction): Set[String] =
    action match {
      case MigrationAction.AddField(at, _) =>
        // AddField provides the target field
        Set(opticToPath(at))

      case MigrationAction.DropField(_, _) =>
        // DropField doesn't provide target fields
        Set.empty

      case MigrationAction.Rename(from, to) =>
        // Rename provides the new field name
        val fromPath = opticToPath(from)
        val parts    = fromPath.split('.')
        if (parts.length == 1) {
          // Top-level field
          Set(to)
        } else {
          // Nested field - replace last component
          val prefix = parts.dropRight(1).mkString(".")
          Set(s"$prefix.$to")
        }

      case MigrationAction.TransformValue(at, _) =>
        // TransformValue provides the field at the path
        Set(opticToPath(at))

      case MigrationAction.ChangeType(at, _) =>
        // ChangeType provides the field at the path
        Set(opticToPath(at))

      case MigrationAction.Mandate(at, _) =>
        // Mandate provides the field at the path
        Set(opticToPath(at))

      case MigrationAction.Optionalize(at, _) =>
        // Optionalize provides the field at the path
        Set(opticToPath(at))

      case MigrationAction.Join(target, _, _) =>
        // Join provides the target field
        Set(opticToPath(target))

      case MigrationAction.Split(_, targetPaths, _) =>
        // Split provides all target fields
        targetPaths.map(opticToPath).toSet

      case MigrationAction.RenameCase(at, _, to) =>
        // RenameCase provides the new case name
        val basePath = opticToPath(at)
        if (basePath.isEmpty) Set(to) else Set(s"$basePath.$to")

      case MigrationAction.TransformCase(at, caseName, _) =>
        // TransformCase provides the case
        val basePath = opticToPath(at)
        if (basePath.isEmpty) Set(caseName) else Set(s"$basePath.$caseName")

      case MigrationAction.TransformElements(at, _) =>
        // TransformElements doesn't change structure, provides the collection
        Set(opticToPath(at))

      case MigrationAction.TransformKeys(at, _) =>
        // TransformKeys doesn't change structure, provides the map
        Set(opticToPath(at))

      case MigrationAction.TransformValues(at, _) =>
        // TransformValues doesn't change structure, provides the map
        Set(opticToPath(at))
    }
}

object MigrationBuilder {

  /**
   * Creates a new migration builder for transforming from type A to type B.
   */
  def newBuilder[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}
