package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * Immutable builder for constructing typed migrations.
 */
final case class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction]
) {

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

  /**
   * Joins multiple source fields into a single target field using a combiner
   * expression.
   */
  private[migration] def joinFields(
    target: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Join(target, sourcePaths, combiner))

  /**
   * Splits a single source field into multiple target fields using a splitter
   * expression.
   */
  private[migration] def splitField(
    source: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Split(source, targetPaths, splitter))

  /**
   * Renames a variant case.
   */
  private[migration] def renameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RenameCase(at, from, to))

  /**
   * Applies nested migration actions to a specific variant case.
   */
  private[migration] def transformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction]
  ): MigrationBuilder[A, B] =
    copy(actions = this.actions :+ MigrationAction.TransformCase(at, caseName, actions))

  /**
   * Applies a transformation to all elements in a sequence.
   */
  private[migration] def transformElements(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformElements(at, transform))

  /**
   * Applies a transformation to all keys in a map.
   */
  private[migration] def transformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformKeys(at, transform))

  /**
   * Applies a transformation to all values in a map.
   */
  private[migration] def transformValues(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValues(at, transform))

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
   * Builds the migration without validation.
   */
  def buildPartial: Migration[A, B] =
    Migration(
      dynamicMigration = DynamicMigration(actions),
      sourceSchema = sourceSchema,
      targetSchema = targetSchema
    )

  /**
   * Validates the migration by checking that all source fields are handled and
   * all target fields are provided.
   */
  private def validate(): Either[MigrationError.ValidationError, Unit] = {
    // Extract all field paths from source and target schemas
    val sourcePaths = extractFieldPaths(sourceSchema, "")
    val targetPaths = extractFieldPaths(targetSchema, "")

    // Fields that exist in both schemas are automatically handled/provided
    val unchanged = sourcePaths.intersect(targetPaths)

    // Track which paths are handled/provided by actions
    val handled  = actions.flatMap(action => handledSourcePaths(action, sourceSchema)).toSet
    val provided = actions.flatMap(action => providedTargetPaths(action, targetSchema)).toSet

    // Unhandled source fields and unprovided target fields
    // Exclude unchanged fields from validation
    val unhandled  = (sourcePaths -- handled) -- unchanged
    val unprovided = (targetPaths -- provided) -- unchanged

    // Actions referencing non-existent fields
    // Handled fields must exist in source schema
    val invalidHandled = handled -- sourcePaths
    // Provided fields must exist in target schema
    val invalidProvided = provided -- targetPaths

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
   * Creates a Schema from a Reflect.Bound. Extracted to avoid repeated local
   * definitions.
   */
  private def schemaFor[T](bound: Reflect.Bound[T]): Schema[T] = new Schema(bound)

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

        // Recurse into nested records and variants
        if (field.value.isInstanceOf[Reflect.Record[binding.Binding, ?]]) {
          val nestedRecord = field.value.asInstanceOf[Reflect.Record[binding.Binding, Any]]
          // For nested records, include both the field itself and its nested fields
          Set(fieldPath) ++ extractFieldPaths(schemaFor(nestedRecord), fieldPath)
        } else if (field.value.isInstanceOf[Reflect.Variant[binding.Binding, ?]]) {
          val nestedVariant = field.value.asInstanceOf[Reflect.Variant[binding.Binding, Any]]
          // For nested variants, include both the field itself and all case paths
          Set(fieldPath) ++ extractFieldPaths(schemaFor(nestedVariant), fieldPath)
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
            schemaFor(nestedRecord),
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
    opticToPathParts(optic).mkString(".")

  /**
   * Converts a DynamicOptic to a field path as Vector[String]. Using Vector
   * instead of string splitting avoids issues with field names containing dots.
   */
  private def opticToPathParts(optic: DynamicOptic): Vector[String] =
    optic.nodes.collect {
      case DynamicOptic.Node.Field(name) => name
      case DynamicOptic.Node.Case(name)  => name
    }.toVector

  /**
   * Extracts all field paths for a specific variant case. For a case like
   * CreditCardV1(number: String, cvv: String), returns: Set("CreditCardV1",
   * "CreditCardV1.number", "CreditCardV1.cvv")
   *
   * @param schema
   *   The schema containing the variant
   * @param caseName
   *   The name of the variant case
   * @param prefix
   *   The current path prefix (e.g., "payment" for nested variants)
   * @return
   *   Set of case name and all nested field paths
   */
  private def extractVariantCasePaths(
    schema: Schema[?],
    caseName: String,
    prefix: String
  ): Set[String] =
    schema.reflect match {
      case variant: Reflect.Variant[binding.Binding, ?] =>
        variant.caseByName(caseName) match {
          case Some(caseField) =>
            // Construct the case path
            val casePath = if (prefix.isEmpty) caseName else s"$prefix.$caseName"

            // Check if the case value is a record
            caseField.value match {
              case record: Reflect.Record[binding.Binding, ?] =>
                // Recursively extract nested field paths
                val nestedSchema = schemaFor(record)
                Set(casePath) ++ extractFieldPaths(nestedSchema, casePath)
              case _ =>
                // Simple case (case object or primitive value)
                Set(casePath)
            }
          case None =>
            // Case not found, return empty set
            Set.empty
        }
      case _ =>
        // Not a variant, return empty set
        Set.empty
    }

  /**
   * Navigates through schema structure following optic nodes. Returns the
   * schema at the target path (e.g., for nested variants).
   *
   * @param schema
   *   The starting schema
   * @param optic
   *   The optic path to navigate
   * @return
   *   Optional schema at the target path
   */
  private def navigateToSchema(
    schema: Schema[?],
    optic: DynamicOptic
  ): Option[Schema[?]] =
    if (optic.nodes.isEmpty) {
      Some(schema)
    } else {
      optic.nodes.foldLeft[Option[Schema[_]]](Option(schema)) { (schemaOpt, node) =>
        schemaOpt.flatMap { s =>
          node match {
            case DynamicOptic.Node.Field(name) =>
              s.reflect match {
                case record: Reflect.Record[binding.Binding, ?] =>
                  record.fieldByName(name).map { field =>
                    new Schema[Any](field.value.asInstanceOf[Reflect.Bound[Any]]): Schema[_]
                  }
                case _ => None
              }
            case DynamicOptic.Node.Case(name) =>
              s.reflect match {
                case variant: Reflect.Variant[binding.Binding, ?] =>
                  variant.caseByName(name).map { caseField =>
                    new Schema[Any](caseField.value.asInstanceOf[Reflect.Bound[Any]]): Schema[_]
                  }
                case _ => None
              }
            case _ => Some(s)
          }
        }
      }
    }

  /**
   * Returns the field path and all nested paths for a field/record at the given
   * optic.
   */
  private def pathWithNested(schema: Schema[?], optic: DynamicOptic): Set[String] = {
    val fieldPath = opticToPath(optic)
    navigateToSchema(schema, optic) match {
      case Some(s) => Set(fieldPath) ++ extractFieldPaths(s, fieldPath)
      case None    => Set(fieldPath)
    }
  }

  /**
   * Returns the case path for a variant case, using extractVariantCasePaths if
   * navigable, otherwise falls back to simple path construction.
   */
  private def casePathWithNested(
    schema: Schema[?],
    optic: DynamicOptic,
    caseName: String
  ): Set[String] = {
    val basePath = opticToPath(optic)
    navigateToSchema(schema, optic) match {
      case Some(s) => extractVariantCasePaths(s, caseName, basePath)
      case None    => if (basePath.isEmpty) Set(caseName) else Set(s"$basePath.$caseName")
    }
  }

  /**
   * Determines which source paths are handled by an action.
   */
  private def handledSourcePaths(action: MigrationAction, sourceSchema: Schema[?]): Set[String] =
    action match {
      case MigrationAction.AddField(_, _) =>
        // AddField doesn't handle source fields
        Set.empty

      case MigrationAction.DropField(at, _) =>
        // DropField handles the source field and all nested fields if it's a record
        pathWithNested(sourceSchema, at)

      case MigrationAction.Rename(from, _) =>
        // Rename handles the source field and all nested fields if it's a record
        pathWithNested(sourceSchema, from)

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

      case MigrationAction.RenameCase(at, from, _) =>
        // RenameCase handles the variant case and all nested fields
        casePathWithNested(sourceSchema, at, from)

      case MigrationAction.TransformCase(at, caseName, nestedActions) =>
        // TransformCase handles the specific variant case and nested actions
        // Note: caseName might be a TARGET case name (after RenameCase), so we need
        // to try looking it up in the source schema. If not found, only track nested actions.
        navigateToSchema(sourceSchema, at) match {
          case Some(schema) =>
            schema.reflect match {
              case variant: Reflect.Variant[binding.Binding, ?] =>
                variant.caseByName(caseName) match {
                  case Some(caseField) =>
                    // Case found in source schema - handle it and nested actions
                    val basePath = opticToPath(at)
                    val casePath = if (basePath.isEmpty) caseName else s"$basePath.$caseName"

                    // Get inner schema for this case
                    val caseInnerSchema = schemaFor(caseField.value)

                    // Recursively analyze nested actions
                    val nestedHandled = nestedActions.flatMap { nestedAction =>
                      handledSourcePaths(nestedAction, caseInnerSchema)
                    }.toSet

                    // Prefix nested paths with case path
                    val prefixedNested = nestedHandled.map { path =>
                      if (path.isEmpty) casePath else s"$casePath.$path"
                    }

                    // Return case name + nested handled paths
                    Set(casePath) ++ prefixedNested
                  case None =>
                    // Case not found in source - might be a renamed case
                    // Don't mark the case itself as handled (RenameCase already did that)
                    // Only return what nested actions would handle (though this is unusual)
                    Set.empty
                }
              case _ => Set.empty
            }
          case None => Set.empty
        }

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
  private def providedTargetPaths(action: MigrationAction, targetSchema: Schema[?]): Set[String] =
    action match {
      case MigrationAction.AddField(at, _) =>
        // AddField provides the target field and all nested fields if it's a record
        pathWithNested(targetSchema, at)

      case MigrationAction.DropField(_, _) =>
        // DropField doesn't provide target fields
        Set.empty

      case MigrationAction.Rename(from, to) =>
        // Rename provides the new field name and all nested fields if it's a record
        // Use Vector[String] to avoid issues with field names containing dots
        val fromPathParts = opticToPathParts(from)
        val toPathParts   = if (fromPathParts.length <= 1) {
          // Top-level field
          Vector(to)
        } else {
          // Nested field - replace last component
          fromPathParts.dropRight(1) :+ to
        }
        val toPath = toPathParts.mkString(".")

        // Build target optic by replacing last node
        val toOptic = if (from.nodes.isEmpty) {
          DynamicOptic.root.field(to)
        } else {
          val parentNodes = from.nodes.dropRight(1)
          DynamicOptic(parentNodes :+ DynamicOptic.Node.Field(to))
        }

        // Navigate to target field and extract nested paths if it's a record
        navigateToSchema(targetSchema, toOptic) match {
          case Some(schema) =>
            Set(toPath) ++ extractFieldPaths(schema, toPath)
          case None =>
            Set(toPath)
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
        // RenameCase provides the new case name and all nested fields
        casePathWithNested(targetSchema, at, to)

      case MigrationAction.TransformCase(at, caseName, nestedActions) =>
        // TransformCase provides the case and nested action results
        navigateToSchema(targetSchema, at) match {
          case Some(schema) =>
            schema.reflect match {
              case variant: Reflect.Variant[binding.Binding, ?] =>
                variant.caseByName(caseName) match {
                  case Some(caseField) =>
                    val basePath = opticToPath(at)
                    val casePath = if (basePath.isEmpty) caseName else s"$basePath.$caseName"

                    // Get inner TARGET schema
                    val caseInnerSchema = schemaFor(caseField.value)

                    // Recursively analyze what nested actions provide
                    val nestedProvided = nestedActions.flatMap { nestedAction =>
                      providedTargetPaths(nestedAction, caseInnerSchema)
                    }.toSet

                    // Prefix nested paths
                    val prefixedNested = nestedProvided.map { path =>
                      if (path.isEmpty) casePath else s"$casePath.$path"
                    }

                    Set(casePath) ++ prefixedNested
                  case None => Set.empty
                }
              case _ =>
                // Fallback to old behavior
                val basePath = opticToPath(at)
                if (basePath.isEmpty) Set(caseName) else Set(s"$basePath.$caseName")
            }
          case None =>
            // Fallback to old behavior
            val basePath = opticToPath(at)
            if (basePath.isEmpty) Set(caseName) else Set(s"$basePath.$caseName")
        }

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
