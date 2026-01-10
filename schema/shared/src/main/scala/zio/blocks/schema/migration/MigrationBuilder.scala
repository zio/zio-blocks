package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A type-safe builder for creating migrations between schema versions.
 */
final class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) extends MigrationBuilderPlatform[A, B] {

  // ============================================================================
  // Record Operations
  // ============================================================================

  /**
   * Add a new field with a default value.
   */
  def addField(
    fieldName: String,
    default: DynamicValue,
    at: DynamicOptic = DynamicOptic.root
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.AddField(at, fieldName, default))

  /**
   * Add a new field with a typed default value.
   */
  def addFieldWithDefault[T](fieldName: String, defaultValue: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] = {
    val dynamicDefault = schema.toDynamicValue(defaultValue)
    copy(actions :+ MigrationAction.AddField(DynamicOptic.root, fieldName, dynamicDefault))
  }

  /**
   * Drop a field from the source.
   */
  def dropField(
    fieldName: String,
    at: DynamicOptic = DynamicOptic.root
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.DropField(at, fieldName, None))

  /**
   * Drop a field with a default for reverse.
   */
  def dropFieldWithDefault[T](fieldName: String, defaultForReverse: T)(implicit
    schema: Schema[T]
  ): MigrationBuilder[A, B] = {
    val dynamicDefault = schema.toDynamicValue(defaultForReverse)
    copy(actions :+ MigrationAction.DropField(DynamicOptic.root, fieldName, Some(dynamicDefault)))
  }

  /**
   * Rename a field.
   */
  def renameField(
    from: String,
    to: String,
    at: DynamicOptic = DynamicOptic.root
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.Rename(at, from, to))

  /**
   * Transform a field value using an expression.
   */
  def transformField(
    fieldPath: DynamicOptic,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.TransformValue(fieldPath, transform))

  /**
   * Make an optional field required with a default for None.
   */
  def mandateField(
    fieldName: String,
    default: DynamicValue,
    at: DynamicOptic = DynamicOptic.root
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.Mandate(at, fieldName, default))

  /**
   * Make an optional field required with a typed default.
   */
  def mandateFieldWithDefault[T](fieldName: String, defaultForNone: T)(implicit
    schema: Schema[T]
  ): MigrationBuilder[A, B] = {
    val dynamicDefault = schema.toDynamicValue(defaultForNone)
    copy(actions :+ MigrationAction.Mandate(DynamicOptic.root, fieldName, dynamicDefault))
  }

  /**
   * Make a field optional by wrapping in Some.
   */
  def optionalizeField(
    fieldName: String,
    at: DynamicOptic = DynamicOptic.root
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.Optionalize(at, fieldName))

  /**
   * Change the type of a field using a converter expression.
   */
  def changeFieldType(
    fieldName: String,
    converter: SchemaExpr[DynamicValue, DynamicValue],
    at: DynamicOptic = DynamicOptic.root
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.ChangeType(at, fieldName, converter))

  // ============================================================================
  // Enum Operations
  // ============================================================================

  /**
   * Rename a case in an enum/variant.
   */
  def renameCase(
    from: String,
    to: String,
    at: DynamicOptic = DynamicOptic.root
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.RenameCase(at, from, to))

  /**
   * Transform a case's contents using nested actions.
   */
  def transformCase(
    caseName: String,
    caseActions: Vector[MigrationAction],
    at: DynamicOptic = DynamicOptic.root
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.TransformCase(at, caseName, caseActions))

  /**
   * Transform a case using a builder function.
   */
  def transformCaseWith[CaseA, CaseB](
    caseName: String,
    caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB],
    at: DynamicOptic = DynamicOptic.root
  )(implicit caseASchema: Schema[CaseA], caseBSchema: Schema[CaseB]): MigrationBuilder[A, B] = {
    val caseBuilder = MigrationBuilder.create[CaseA, CaseB](caseASchema, caseBSchema)
    val builtCase   = caseMigration(caseBuilder)
    copy(actions :+ MigrationAction.TransformCase(at, caseName, builtCase.actions))
  }

  // ============================================================================
  // Collection Operations
  // ============================================================================

  /**
   * Transform all elements in a sequence.
   */
  def transformElements(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.TransformElements(at, transform))

  // ============================================================================
  // Map Operations
  // ============================================================================

  /**
   * Transform all keys in a map.
   */
  def transformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.TransformKeys(at, transform))

  /**
   * Transform all values in a map.
   */
  def transformValues(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.TransformValues(at, transform))

  // ============================================================================
  // Build Methods
  // ============================================================================

  /**
   * Build the final migration with validation. Warns about unmapped fields at
   * runtime.
   */
  def build: Migration[A, B] = {
    // Extract field names affected by actions
    val handledSourceFields = actions.flatMap(extractSourceFields).toSet
    val handledTargetFields = actions.flatMap(extractTargetFields).toSet

    // Extract source and target field names from schemas (runtime introspection)
    val sourceFields = extractSchemaFields(sourceSchema)
    val targetFields = extractSchemaFields(targetSchema)

    // Check for unmapped source fields (fields in source not handled by drop/rename/transform)
    val unmappedSource = sourceFields.diff(handledSourceFields)
    if (unmappedSource.nonEmpty) {
      System.err.println(
        s"[Migration Warning] Unmapped source fields: ${unmappedSource.mkString(", ")}. " +
          "These fields will be dropped silently."
      )
    }

    // Check for unmapped target fields (fields in target not added/renamed to)
    val unmappedTarget = targetFields.diff(handledTargetFields)
    if (unmappedTarget.nonEmpty) {
      System.err.println(
        s"[Migration Warning] Unmapped target fields: ${unmappedTarget.mkString(", ")}. " +
          "These fields may be missing in the result."
      )
    }

    Migration[A, B](DynamicMigration(actions), sourceSchema, targetSchema)
  }

  /**
   * Build migration with validation and return errors for unmapped fields. This
   * is called by the compile-time validation macro.
   */
  def buildWithValidation: Migration[A, B] = {
    // Extract field names affected by actions
    val handledSourceFields = actions.flatMap(extractSourceFields).toSet
    val handledTargetFields = actions.flatMap(extractTargetFields).toSet

    // Extract source and target field names from schemas
    val sourceFields = extractSchemaFields(sourceSchema)
    val targetFields = extractSchemaFields(targetSchema)

    // Check for unmapped source fields
    val unmappedSource = sourceFields.diff(handledSourceFields)
    val unmappedTarget = targetFields.diff(handledTargetFields)

    // Throw at runtime if there are unmapped fields (compile-time check already warned)
    if (unmappedSource.nonEmpty || unmappedTarget.nonEmpty) {
      val errors = List(
        if (unmappedSource.nonEmpty)
          Some(s"Unmapped source fields: ${unmappedSource.mkString(", ")}")
        else None,
        if (unmappedTarget.nonEmpty)
          Some(s"Unmapped target fields: ${unmappedTarget.mkString(", ")}")
        else None
      ).flatten

      throw new IllegalStateException(
        s"Migration validation failed: ${errors.mkString("; ")}"
      )
    }

    Migration[A, B](DynamicMigration(actions), sourceSchema, targetSchema)
  }

  /**
   * Build migration without validation (for incremental development).
   */
  def buildPartial: Migration[A, B] =
    Migration[A, B](DynamicMigration(actions), sourceSchema, targetSchema)

  // ============================================================================
  // Validation Helpers
  // ============================================================================

  private def extractSourceFields(action: MigrationAction): Set[String] = action match {
    case MigrationAction.Rename(_, from, _)     => Set(from)
    case MigrationAction.DropField(_, name, _)  => Set(name)
    case MigrationAction.Mandate(_, name, _)    => Set(name)
    case MigrationAction.Optionalize(_, name)   => Set(name)
    case MigrationAction.ChangeType(_, name, _) => Set(name)
    case MigrationAction.Join(_, sources, _, _) => sources.toSet
    case MigrationAction.Split(_, source, _, _) => Set(source)
    case _                                      => Set.empty
  }

  private def extractTargetFields(action: MigrationAction): Set[String] = action match {
    case MigrationAction.Rename(_, _, to)        => Set(to)
    case MigrationAction.AddField(_, name, _)    => Set(name)
    case MigrationAction.Mandate(_, name, _)     => Set(name)
    case MigrationAction.Optionalize(_, name)    => Set(name)
    case MigrationAction.ChangeType(_, name, _)  => Set(name)
    case MigrationAction.Join(_, _, target, _)   => Set(target)
    case MigrationAction.Split(_, _, targets, _) => targets.toSet
    case _                                       => Set.empty
  }

  private def extractSchemaFields[T](schema: Schema[T]): Set[String] =
    // Use Reflect to extract field names from schema
    schema.reflect match {
      case record: Reflect.Record.Bound[_] =>
        record.fields.map(_.name).toSet
      case _ => Set.empty
    }

  // ============================================================================
  // Internal
  // ============================================================================

  private def copy(newActions: Vector[MigrationAction]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sourceSchema, targetSchema, newActions)
}

object MigrationBuilder {

  /** Create a new migration builder using implicits. */
  def apply[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sourceSchema, targetSchema, Vector.empty)

  /** Create a builder with explicit schemas. */
  def create[A, B](sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sourceSchema, targetSchema, Vector.empty)

  /** Internal constructor for creating builders with actions. */
  private[migration] def withActions[A, B](
    sourceSchema: Schema[A],
    targetSchema: Schema[B],
    actions: Vector[MigrationAction]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sourceSchema, targetSchema, actions)
}
