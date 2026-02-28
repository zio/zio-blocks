package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Optic, Schema}

/**
 * A builder for constructing [[Migration]] instances through a fluent DSL.
 *
 * The builder accumulates [[MigrationAction]]s and produces either a validated
 * migration (via `build`) or an unvalidated one (via `buildPartial`).
 *
 * {{{
 * val migration = Migration.newBuilder[PersonV0, Person]
 *   .renameField("firstName", "fullName")
 *   .addField("age", 0)
 *   .build
 * }}}
 *
 * @tparam A
 *   the source type
 * @tparam B
 *   the target type
 */
final class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) {

  // ─────────────────────────────────────────────────────────────────────────
  // Record Operations (string-based API)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Add a new field with a default value.
   *
   * @param fieldName
   *   name of the field in the target type
   * @param default
   *   default value for the new field
   */
  def addField[V](fieldName: String, default: V)(implicit schema: Schema[V]): MigrationBuilder[A, B] =
    addFieldDynamic(fieldName, schema.toDynamicValue(default))

  /**
   * Add a new field with a DynamicValue default.
   */
  def addFieldDynamic(fieldName: String, default: DynamicValue): MigrationBuilder[A, B] =
    appendAction(MigrationAction.AddField(DynamicOptic.root, fieldName, default))

  /**
   * Drop a field from the source type.
   *
   * @param fieldName
   *   name of the field to drop
   */
  def dropField(fieldName: String): MigrationBuilder[A, B] =
    appendAction(MigrationAction.DropField(DynamicOptic.root, fieldName, None))

  /**
   * Drop a field with a default for reverse migration.
   *
   * @param fieldName
   *   name of the field to drop
   * @param defaultForReverse
   *   value to use when reversing
   */
  def dropField[V](fieldName: String, defaultForReverse: V)(implicit schema: Schema[V]): MigrationBuilder[A, B] =
    appendAction(
      MigrationAction.DropField(
        DynamicOptic.root,
        fieldName,
        Some(schema.toDynamicValue(defaultForReverse))
      )
    )

  /**
   * Rename a field.
   *
   * @param from
   *   current field name in the source
   * @param to
   *   new field name in the target
   */
  def renameField(from: String, to: String): MigrationBuilder[A, B] =
    appendAction(MigrationAction.Rename(DynamicOptic.root, from, to))

  /**
   * Transform a field's value using a primitive conversion.
   *
   * @param from
   *   source field name
   * @param to
   *   target field name
   * @param transform
   *   the conversion to apply
   */
  def transformField(from: String, to: String, transform: PrimitiveTransform): MigrationBuilder[A, B] =
    appendAction(MigrationAction.TransformValue(DynamicOptic.root, from, to, transform))

  /**
   * Make an optional field mandatory.
   *
   * @param source
   *   source field name (Option type)
   * @param target
   *   target field name (non-Option type)
   * @param default
   *   value to use when source is None
   */
  def mandateField[V](source: String, target: String, default: V)(implicit schema: Schema[V]): MigrationBuilder[A, B] =
    appendAction(
      MigrationAction.Mandate(
        DynamicOptic.root,
        source,
        target,
        schema.toDynamicValue(default)
      )
    )

  /**
   * Make a mandatory field optional.
   *
   * @param source
   *   source field name (non-Option type)
   * @param target
   *   target field name (Option type)
   */
  def optionalizeField(source: String, target: String): MigrationBuilder[A, B] =
    appendAction(MigrationAction.Optionalize(DynamicOptic.root, source, target))

  /**
   * Change a field's primitive type.
   *
   * @param source
   *   source field name
   * @param target
   *   target field name
   * @param converter
   *   the type conversion
   */
  def changeFieldType(source: String, target: String, converter: PrimitiveTransform): MigrationBuilder[A, B] =
    appendAction(MigrationAction.ChangeType(DynamicOptic.root, source, target, converter))

  // ─────────────────────────────────────────────────────────────────────────
  // Selector-based operations (using typed optics)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Add a new field identified by a typed optic on the target type.
   *
   * {{{
   * Migration.newBuilder[PersonV0, PersonV1]
   *   .addField(targetOptic, defaultValue)
   * }}}
   */
  def addField[V](target: Optic[B, V], default: V)(implicit schema: Schema[V]): MigrationBuilder[A, B] = {
    val (at, fieldName) = splitOptic(target.toDynamic)
    appendAction(MigrationAction.AddField(at, fieldName, schema.toDynamicValue(default)))
  }

  /**
   * Drop a field identified by a typed optic on the source type.
   */
  def dropField(source: Optic[A, ?]): MigrationBuilder[A, B] = {
    val (at, fieldName) = splitOptic(source.toDynamic)
    appendAction(MigrationAction.DropField(at, fieldName, None))
  }

  /**
   * Rename a field from the source type to the target type using typed optics.
   */
  def renameField(from: Optic[A, ?], to: Optic[B, ?]): MigrationBuilder[A, B] = {
    val (fromAt, fromName) = splitOptic(from.toDynamic)
    val (_, toName)        = splitOptic(to.toDynamic)
    appendAction(MigrationAction.Rename(fromAt, fromName, toName))
  }

  /**
   * Change a field's type using typed optics and a primitive transform.
   */
  def changeFieldType(
    source: Optic[A, ?],
    target: Optic[B, ?],
    converter: PrimitiveTransform
  ): MigrationBuilder[A, B] = {
    val (at, sourceName) = splitOptic(source.toDynamic)
    val (_, targetName)  = splitOptic(target.toDynamic)
    appendAction(MigrationAction.ChangeType(at, sourceName, targetName, converter))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Nested Record Operations (path-based)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Add a field at a nested path.
   */
  def addFieldAt[V](
    at: DynamicOptic,
    fieldName: String,
    default: V
  )(implicit schema: Schema[V]): MigrationBuilder[A, B] =
    appendAction(MigrationAction.AddField(at, fieldName, schema.toDynamicValue(default)))

  /**
   * Drop a field at a nested path.
   */
  def dropFieldAt(at: DynamicOptic, fieldName: String): MigrationBuilder[A, B] =
    appendAction(MigrationAction.DropField(at, fieldName, None))

  /**
   * Rename a field at a nested path.
   */
  def renameFieldAt(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    appendAction(MigrationAction.Rename(at, from, to))

  // ─────────────────────────────────────────────────────────────────────────
  // Enum Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Rename a case in a sum type.
   *
   * @param from
   *   current case name
   * @param to
   *   new case name
   */
  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    appendAction(MigrationAction.RenameCase(DynamicOptic.root, from, to))

  /**
   * Rename a case at a nested path.
   */
  def renameCaseAt(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    appendAction(MigrationAction.RenameCase(at, from, to))

  /**
   * Transform a specific case's fields using a nested builder.
   *
   * @param caseName
   *   which case to transform
   * @param f
   *   a function that configures the nested migration builder
   */
  def transformCase(caseName: String)(
    f: MigrationBuilder[A, B] => MigrationBuilder[A, B]
  ): MigrationBuilder[A, B] = {
    val nested      = f(new MigrationBuilder(sourceSchema, targetSchema, Vector.empty))
    val caseActions = nested.actions
    appendAction(MigrationAction.TransformCase(DynamicOptic.root, caseName, caseActions))
  }

  /**
   * Transform a case at a nested path.
   */
  def transformCaseAt(at: DynamicOptic, caseName: String)(
    f: MigrationBuilder[A, B] => MigrationBuilder[A, B]
  ): MigrationBuilder[A, B] = {
    val nested      = f(new MigrationBuilder(sourceSchema, targetSchema, Vector.empty))
    val caseActions = nested.actions
    appendAction(MigrationAction.TransformCase(at, caseName, caseActions))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Collection / Map Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Transform all elements in a sequence at the given path.
   */
  def transformElements(at: DynamicOptic, transform: PrimitiveTransform): MigrationBuilder[A, B] =
    appendAction(MigrationAction.TransformElements(at, transform))

  /**
   * Transform all keys in a map at the given path.
   */
  def transformMapKeys(at: DynamicOptic, transform: PrimitiveTransform): MigrationBuilder[A, B] =
    appendAction(MigrationAction.TransformKeys(at, transform))

  /**
   * Transform all values in a map at the given path.
   */
  def transformMapValues(at: DynamicOptic, transform: PrimitiveTransform): MigrationBuilder[A, B] =
    appendAction(MigrationAction.TransformValues(at, transform))

  // ─────────────────────────────────────────────────────────────────────────
  // Build
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Build the migration with validation.
   *
   * Validates that the accumulated actions would transform a value conforming
   * to the source schema into a value conforming to the target schema.
   */
  def build: Migration[A, B] = {
    // Validate: check that all source fields are accounted for
    // and all target fields are produced
    val sourceFields = extractFieldNames(sourceSchema)
    val targetFields = extractFieldNames(targetSchema)

    if (sourceFields.nonEmpty && targetFields.nonEmpty) {
      // Track field transformations
      var unmappedTarget = targetFields
      var currentFields  = sourceFields

      actions.foreach {
        case MigrationAction.AddField(_, fieldName, _) =>
          unmappedTarget = unmappedTarget - fieldName
          currentFields = currentFields + fieldName
        case MigrationAction.DropField(_, fieldName, _) =>
          currentFields = currentFields - fieldName
        case MigrationAction.Rename(_, from, to) =>
          currentFields = (currentFields - from) + to
          unmappedTarget = unmappedTarget - to
        case MigrationAction.TransformValue(_, _, toField, _) =>
          unmappedTarget = unmappedTarget - toField
        case MigrationAction.Mandate(_, _, targetFieldName, _) =>
          unmappedTarget = unmappedTarget - targetFieldName
        case MigrationAction.Optionalize(_, _, targetFieldName) =>
          unmappedTarget = unmappedTarget - targetFieldName
        case MigrationAction.ChangeType(_, _, targetFieldName, _) =>
          unmappedTarget = unmappedTarget - targetFieldName
        case _ => // enum/collection actions don't affect field mapping
      }

      // Fields that exist in both source and target are implicitly mapped
      unmappedTarget = unmappedTarget -- currentFields

      if (unmappedTarget.nonEmpty) {
        throw new IllegalArgumentException(
          s"Migration validation failed: target fields not accounted for: ${unmappedTarget.mkString(", ")}. " +
            "Use addField, renameField, or transformField to map all target fields, or use buildPartial to skip validation."
        )
      }
    }

    Migration(new DynamicMigration(actions), sourceSchema, targetSchema)
  }

  /**
   * Build the migration without full validation.
   *
   * This is useful for partial migrations or when the schema structures are not
   * fully derivable at compile time.
   */
  def buildPartial: Migration[A, B] =
    Migration(new DynamicMigration(actions), sourceSchema, targetSchema)

  // ─────────────────────────────────────────────────────────────────────────
  // Internal
  // ─────────────────────────────────────────────────────────────────────────

  private def appendAction(action: MigrationAction): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)

  private def splitOptic(optic: DynamicOptic): (DynamicOptic, String) = {
    val nodes = optic.nodes
    if (nodes.isEmpty) throw new IllegalArgumentException("Optic must target at least one field")
    val last = nodes.last match {
      case f: DynamicOptic.Node.Field => f.name
      case other                      =>
        throw new IllegalArgumentException(
          "Expected field access as last path segment, got: " + other.getClass.getSimpleName
        )
    }
    val parent =
      if (nodes.length == 1) DynamicOptic.root
      else new DynamicOptic(nodes.dropRight(1))
    (parent, last)
  }

  private def extractFieldNames(schema: Schema[?]): Set[String] =
    schema.reflect.asRecord match {
      case Some(record) =>
        val terms = record.fields
        val len   = terms.length
        val sb    = Set.newBuilder[String]
        var idx   = 0
        while (idx < len) {
          sb += terms(idx).name
          idx += 1
        }
        sb.result()
      case None => Set.empty
    }
}
