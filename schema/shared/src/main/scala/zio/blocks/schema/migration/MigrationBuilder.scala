package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * A builder for constructing type-safe migrations between schema versions.
 *
 * MigrationBuilder provides a fluent API for defining schema transformations.
 * The builder accumulates MigrationAction instances and produces a typed
 * Migration[A, B].
 *
 * Two API styles are supported:
 *   1. Path-based: addField(path, fieldName, default)
 *   2. Selector-based (via macros): addField(_.fieldName, default)
 *
 * @tparam A
 *   source type
 * @tparam B
 *   target type
 */
final class MigrationBuilder[A, B] private[migration] (
  private val sourceSchema: Schema[A],
  private val targetSchema: Schema[B],
  private val actions: Chunk[MigrationAction]
) {

  // ─────────────────────────────────────────────────────────────────────────
  // SchemaExpr-First API (Issue #519 Primary Interface)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Add a field using a SchemaExpr for the default value.
   *
   * This is the primary public API that accepts SchemaExpr directly.
   */
  def addField[T](fieldName: String, default: zio.blocks.schema.SchemaExpr[A, T])(implicit
    schema: Schema[T]
  ): MigrationBuilder[A, B] =
    addFieldResolved(DynamicOptic.root, fieldName, fromSchemaExpr(default))

  /**
   * Add a field with a literal default value (convenience wrapper).
   */
  def addFieldLiteral[T](fieldName: String, default: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] =
    addFieldResolved(DynamicOptic.root, fieldName, Resolved.Literal(default, schema))

  /**
   * Add a field with a Resolved expression as default (low-level API).
   */
  def addFieldResolved(at: DynamicOptic, fieldName: String, default: Resolved): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.AddField(at, fieldName, default))

  /**
   * Drop a field (no reverse support).
   */
  def dropFieldNoReverse(fieldName: String): MigrationBuilder[A, B] =
    dropFieldResolved(DynamicOptic.root, fieldName, Resolved.Fail(s"Cannot reverse drop of $fieldName"))

  /**
   * Drop a field using a SchemaExpr for the reverse default.
   *
   * This is the primary public API that accepts SchemaExpr directly.
   */
  def dropField[T](fieldName: String, defaultForReverse: zio.blocks.schema.SchemaExpr[B, T])(implicit
    schema: Schema[T]
  ): MigrationBuilder[A, B] =
    dropFieldResolved(DynamicOptic.root, fieldName, fromSchemaExpr(defaultForReverse))

  /**
   * Drop a field with a literal default for reverse migration (convenience
   * wrapper).
   */
  def dropFieldLiteral[T](fieldName: String, defaultForReverse: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] =
    dropFieldResolved(DynamicOptic.root, fieldName, Resolved.Literal(defaultForReverse, schema))

  /**
   * Drop a field with a Resolved expression for reverse (low-level API).
   */
  def dropFieldResolved(at: DynamicOptic, fieldName: String, defaultForReverse: Resolved): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.DropField(at, fieldName, defaultForReverse)
    )

  /**
   * Rename a field.
   */
  def renameField(from: String, to: String): MigrationBuilder[A, B] =
    renameFieldAt(DynamicOptic.root, from, to)

  /**
   * Rename a field at a specific path.
   */
  def renameFieldAt(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Rename(at, from, to))

  /**
   * Transform a field using SchemaExpr for forward and reverse transformations.
   *
   * This is the primary public API that accepts SchemaExpr directly.
   */
  def transformField[T, U](
    fieldName: String,
    transform: zio.blocks.schema.SchemaExpr[T, U],
    reverseTransform: zio.blocks.schema.SchemaExpr[U, T]
  )(implicit schemaT: Schema[T], schemaU: Schema[U]): MigrationBuilder[A, B] =
    transformFieldResolved(
      DynamicOptic.root,
      fieldName,
      fromSchemaExpr(transform),
      fromSchemaExpr(reverseTransform)
    )

  /**
   * Transform a field value with forward and reverse Resolved expressions
   * (low-level API).
   */
  def transformFieldResolved(
    at: DynamicOptic,
    fieldName: String,
    transform: Resolved,
    reverseTransform: Resolved
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformValue(at, fieldName, transform, reverseTransform)
    )

  /**
   * Make an optional field mandatory using a SchemaExpr for the default value.
   *
   * This is the primary public API that accepts SchemaExpr directly.
   */
  def mandateField[T](fieldName: String, default: zio.blocks.schema.SchemaExpr[A, T])(implicit
    schema: Schema[T]
  ): MigrationBuilder[A, B] =
    mandateFieldResolved(DynamicOptic.root, fieldName, fromSchemaExpr(default))

  /**
   * Make an optional field mandatory with a literal default (convenience
   * wrapper).
   */
  def mandateFieldLiteral[T](fieldName: String, default: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] =
    mandateFieldResolved(DynamicOptic.root, fieldName, Resolved.Literal(default, schema))

  /**
   * Make an optional field mandatory with a Resolved default (low-level API).
   */
  def mandateFieldResolved(at: DynamicOptic, fieldName: String, default: Resolved): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Mandate(at, fieldName, default))

  /**
   * Make a mandatory field optional.
   */
  def optionalizeField(fieldName: String): MigrationBuilder[A, B] =
    optionalizeFieldAt(DynamicOptic.root, fieldName)

  /**
   * Make a mandatory field optional at a specific path.
   */
  def optionalizeFieldAt(at: DynamicOptic, fieldName: String): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Optionalize(at, fieldName))

  /**
   * Change a field's type with conversion expressions.
   */
  def changeFieldType(
    fieldName: String,
    fromType: String,
    toType: String
  ): MigrationBuilder[A, B] =
    changeFieldTypeResolved(
      DynamicOptic.root,
      fieldName,
      Resolved.Convert(fromType, toType, Resolved.Identity),
      Resolved.Convert(toType, fromType, Resolved.Identity)
    )

  /**
   * Change a field's type with Resolved converters.
   */
  def changeFieldTypeResolved(
    at: DynamicOptic,
    fieldName: String,
    converter: Resolved,
    reverseConverter: Resolved
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.ChangeType(at, fieldName, converter, reverseConverter)
    )

  // ─────────────────────────────────────────────────────────────────────────
  // Enum Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Rename an enum case.
   */
  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    renameCaseAt(DynamicOptic.root, from, to)

  /**
   * Rename an enum case at a specific path.
   */
  def renameCaseAt(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.RenameCase(at, from, to))

  /**
   * Transform an enum case's internal structure.
   */
  def transformCase[C, D](caseName: String)(
    nestedBuilder: MigrationBuilder[C, D] => MigrationBuilder[C, D]
  )(implicit cSchema: Schema[C], dSchema: Schema[D]): MigrationBuilder[A, B] =
    transformCaseAt(DynamicOptic.root, caseName)(nestedBuilder)

  /**
   * Transform an enum case at a specific path.
   */
  def transformCaseAt[C, D](at: DynamicOptic, caseName: String)(
    nestedBuilder: MigrationBuilder[C, D] => MigrationBuilder[C, D]
  )(implicit cSchema: Schema[C], dSchema: Schema[D]): MigrationBuilder[A, B] = {
    val nested = nestedBuilder(new MigrationBuilder[C, D](cSchema, dSchema, Chunk.empty))
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformCase(at, caseName, nested.actions)
    )
  }

  /**
   * Transform an enum case at a specific path with explicit actions.
   *
   * This overload is used by selector-based APIs where actions are pre-built.
   */
  def transformCaseAt(at: DynamicOptic, caseName: String, caseActions: Chunk[MigrationAction]): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformCase(at, caseName, caseActions)
    )

  // ─────────────────────────────────────────────────────────────────────────
  // Collection Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Transform elements of a sequence with Resolved expressions.
   */
  def transformElementsResolved(
    at: DynamicOptic,
    elementTransform: Resolved,
    reverseTransform: Resolved
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformElements(at, elementTransform, reverseTransform)
    )

  /**
   * Transform map keys with Resolved expressions.
   */
  def transformKeysResolved(
    at: DynamicOptic,
    keyTransform: Resolved,
    reverseTransform: Resolved
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformKeys(at, keyTransform, reverseTransform)
    )

  /**
   * Transform map values with Resolved expressions.
   */
  def transformValuesResolved(
    at: DynamicOptic,
    valueTransform: Resolved,
    reverseTransform: Resolved
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformValues(at, valueTransform, reverseTransform)
    )

  // ─────────────────────────────────────────────────────────────────────────
  // Internal SchemaExpr Conversion
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Convert a SchemaExpr to the internal Resolved representation.
   *
   * This bridges the public SchemaExpr API to the internal Resolved data
   * structure. DefaultValue is resolved at build time using the schema's
   * default value.
   */
  private def fromSchemaExpr[S, T](expr: zio.blocks.schema.SchemaExpr[S, T])(implicit
    schema: Schema[T]
  ): Resolved =
    expr match {
      case zio.blocks.schema.SchemaExpr.Literal(value, litSchema) =>
        Resolved.Literal(value.asInstanceOf[T], litSchema.asInstanceOf[Schema[T]])
      case _: zio.blocks.schema.SchemaExpr.DefaultValue[_, _] =>
        // Resolve DefaultValue at build time using the implicit schema's default
        schema.getDefaultValue match {
          case Some(defaultVal) =>
            Resolved.Literal(schema.toDynamicValue(defaultVal))
          case None =>
            // No default defined - store as failed DefaultValue for clear error at runtime
            Resolved.DefaultValue.fail(s"No default value defined for ${schema.reflect.typeId}")
        }
      case _: zio.blocks.schema.SchemaExpr.Optic[_, _] =>
        // For optic expressions, use Identity - they will be resolved at path level
        Resolved.Identity
      case _ =>
        // For other expressions, default to Identity
        Resolved.Identity
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Join/Split Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Join multiple source fields into a single target field.
   *
   * The combiner expression should combine the source field values into a
   * single primitive value for the target field.
   *
   * @param at
   *   path to the record
   * @param targetFieldName
   *   name of the new combined field
   * @param sourceFieldNames
   *   names of source fields to combine
   * @param combiner
   *   expression that combines source values
   * @param splitter
   *   expression for reverse (to recreate source fields)
   */
  def joinFields(
    at: DynamicOptic,
    targetFieldName: String,
    sourcePaths: Chunk[DynamicOptic],
    combiner: Resolved,
    splitter: Resolved
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Join(at, targetFieldName, sourcePaths, combiner, splitter)
    )

  /**
   * Split a single source field into multiple target fields.
   *
   * The splitter expression should split the source field value into multiple
   * primitive values for the target fields.
   *
   * @param at
   *   path to the record
   * @param sourceFieldName
   *   name of the field to split
   * @param targetFieldNames
   *   names of new target fields
   * @param splitter
   *   expression that splits the source value
   * @param combiner
   *   expression for reverse (to recreate source field)
   */
  def splitField(
    at: DynamicOptic,
    sourceFieldName: String,
    targetPaths: Chunk[DynamicOptic],
    splitter: Resolved,
    combiner: Resolved
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Split(at, sourceFieldName, targetPaths, splitter, combiner)
    )

  // ─────────────────────────────────────────────────────────────────────────
  // Nested Migration Support
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Add multiple actions at once.
   *
   * Used primarily for composing nested migrations where prefixed actions need
   * to be incorporated into this builder.
   */
  def addActions(newActions: Chunk[MigrationAction]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions ++ newActions)

  /**
   * Access the current list of actions (for introspection).
   */
  def currentActions: Chunk[MigrationAction] = actions

  // ─────────────────────────────────────────────────────────────────────────
  // Build Methods
  // ─────────────────────────────────────────────────────────────────────────

  // NOTE: The primary `.build` method is provided as a platform-specific extension:
  // - Scala 3: Uses macro validation via MigrationBuilderSyntax.build
  // - Scala 2: Uses runtime validation via MigrationBuilderSyntax2.build
  // Import the appropriate syntax object to use `.build`.

  /**
   * Build the migration with path validation only.
   *
   * Validates that all paths in actions are valid for their respective schemas,
   * but does NOT verify complete field coverage.
   *
   * Throws IllegalArgumentException if path validation fails.
   */
  def buildPathsOnly: Migration[A, B] = {
    val migration = Migration(DynamicMigration(actions), sourceSchema, targetSchema)
    MigrationValidator.validate(migration) match {
      case MigrationValidator.ValidationResult.Valid         => migration
      case MigrationValidator.ValidationResult.Invalid(errs) =>
        throw new IllegalArgumentException(
          s"Invalid migration:\n${errs.map(_.render).toSeq.mkString("\n")}"
        )
    }
  }

  /**
   * Build the migration with STRICT shape validation.
   *
   * This is the most comprehensive validation mode. It validates:
   *   1. All paths are valid (like .build)
   *   2. All source fields are either kept, renamed, transformed, or dropped
   *   3. All target fields are either from source or added
   *
   * Use this when you want to ensure complete migration coverage. This provides
   * compile-time-like guarantees at runtime by analyzing schema shapes.
   *
   * Throws IllegalArgumentException if validation fails.
   */
  def buildStrict: Migration[A, B] = {
    val migration = buildPathsOnly // First do path validation

    // Then do shape validation
    SchemaShapeValidator.validateShape(migration) match {
      case SchemaShapeValidator.ShapeValidationResult.Complete               => migration
      case incomplete: SchemaShapeValidator.ShapeValidationResult.Incomplete =>
        throw new IllegalArgumentException(
          s"Incomplete migration: ${incomplete.render}\n" +
            s"Coverage: handled=${incomplete.coverage.handledFromSource.size}, " +
            s"provided=${incomplete.coverage.providedToTarget.size}, " +
            s"renamed=${incomplete.coverage.renamedFields.size}, " +
            s"dropped=${incomplete.coverage.droppedFields.size}, " +
            s"added=${incomplete.coverage.addedFields.size}"
        )
    }
  }

  /**
   * Build a partial migration without validation.
   *
   * Use this when you want to build an incomplete migration or when you're
   * confident the migration is valid and want to skip validation overhead.
   */
  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /**
   * Build an untyped DynamicMigration.
   */
  def buildDynamic: DynamicMigration = DynamicMigration(actions)

  /**
   * Get migration coverage analysis without building.
   *
   * Useful for debugging or understanding what fields are handled.
   */
  def analyzeCoverage: SchemaShapeValidator.MigrationCoverage =
    actions.foldLeft(SchemaShapeValidator.MigrationCoverage.empty) { (cov, action) =>
      action match {
        case MigrationAction.AddField(_, fieldName, _)          => cov.addField(fieldName)
        case MigrationAction.DropField(_, fieldName, _)         => cov.dropField(fieldName)
        case MigrationAction.Rename(_, from, to)                => cov.renameField(from, to)
        case MigrationAction.TransformValue(_, fieldName, _, _) => cov.handleField(fieldName).provideField(fieldName)
        case MigrationAction.Mandate(_, fieldName, _)           => cov.handleField(fieldName).provideField(fieldName)
        case MigrationAction.Optionalize(_, fieldName)          => cov.handleField(fieldName).provideField(fieldName)
        case MigrationAction.ChangeType(_, fieldName, _, _)     => cov.handleField(fieldName).provideField(fieldName)
        case _                                                  => cov
      }
    }

}

object MigrationBuilder {

  /**
   * Create a new migration builder.
   */
  def apply[A, B](implicit source: Schema[A], target: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](source, target, Chunk.empty)

  /**
   * Create a migration builder with explicit schemas.
   */
  def create[A, B](source: Schema[A], target: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](source, target, Chunk.empty)
}
