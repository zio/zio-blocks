package zio.blocks.schema.migration

// format: off
import zio.blocks.schema._

/**
 * A type-safe builder for creating migrations between schema versions.
 *
 * ==Design for Structural Type Compatibility==
 *
 * This builder is designed to work with any `Schema[A]` and `Schema[B]`,
 * including future structural type schemas derived via `Schema.structural`.
 * Key design decisions that enable this:
 *
 *   - All transformations operate on `DynamicValue`, not concrete types
 *   - Path-based actions use `DynamicOptic` which works with any schema structure
 *   - The builder is parameterized by types `A` and `B` but internally works
 *     with the dynamic representation
 *
 * When `Schema.structural` is implemented (separately specified work), users
 * can define old schema versions as structural types:
 *
 * {{{
 *   type PersonV0 = { val name: String; val age: Int }
 *   type PersonV1 = { val fullName: String; val age: Int; val country: String }
 *
 *   // No runtime case classes needed for old versions!
 *   val migration = Migration.builder[PersonV0, PersonV1]
 *     .renameField(_.name, _.fullName)
 *     .addField(_.country, DynamicValue.Primitive(PrimitiveValue.String("USA")))
 *     .build
 * }}}
 *
 * ==Compile-Time Validation==
 *
 * The `build` method performs compile-time validation (via macros) to ensure
 * all source fields are handled and all target fields are produced. Use
 * `buildPartial` to skip validation during incremental development.
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
   * Add a new field with a default value. (Internal - use selector-based API)
   */
  private[migration] def addField(
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
   * Drop a field from the source. (Internal - use selector-based API)
   */
  private[migration] def dropField(
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
   * Rename a field. (Internal - use selector-based API)
   */
  private[migration] def renameField(
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
   * Make an optional field required with a default for None. (Internal - use
   * selector-based API)
   */
  private[migration] def mandateField(
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
   * Make a field optional by wrapping in Some. (Internal - use selector-based
   * API)
   */
  private[migration] def optionalizeField(
    fieldName: String,
    at: DynamicOptic = DynamicOptic.root
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.Optionalize(at, fieldName))

  /**
   * Change the type of a field using a converter expression. (Internal - use
   * selector-based API)
   */
  private[migration] def changeFieldType(
    fieldName: String,
    converter: SchemaExpr[DynamicValue, DynamicValue],
    at: DynamicOptic = DynamicOptic.root
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.ChangeType(at, fieldName, converter))

  // ============================================================================
  // Join / Split Operations
  // ============================================================================

  /**
   * Join multiple source fields into a single target field.
   *
   * Example:
   * {{{
   *   .joinFields(
   *     source = Vector(_.firstName, _.lastName),
   *     target = _.fullName,
   *     combiner = concatenateExpr,
   *     splitterForReverse = Some(splitExpr)  // Makes migration reversible
   *   )
   * }}}
   *
   * The combiner receives a `DynamicValue.Sequence` containing the source values
   * in the order they appear in `sourcePaths`.
   *
   * @param sourcePaths Paths to source fields to combine
   * @param targetPath Path to target field
   * @param combiner Expression to combine source values (receives Sequence)
   * @param splitterForReverse Optional splitter expression for reverse migration
   */
  def joinFields(
    sourcePaths: Vector[DynamicOptic],
    targetPath: DynamicOptic,
    combiner: SchemaExpr[DynamicValue, DynamicValue],
    splitterForReverse: Option[SchemaExpr[DynamicValue, DynamicValue]] = None
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.Join(DynamicOptic.root, sourcePaths, targetPath, combiner, splitterForReverse))

  /**
   * Split a source field into multiple target fields.
   *
   * Example:
   * {{{
   *   .splitField(
   *     source = _.fullName,
   *     targets = Vector(_.firstName, _.lastName),
   *     splitter = splitBySpaceExpr,
   *     combinerForReverse = Some(concatenateExpr)  // Makes migration reversible
   *   )
   * }}}
   *
   * The splitter should return a `DynamicValue.Sequence` with one value for each
   * target path in the same order.
   *
   * @param sourcePath Path to source field to split
   * @param targetPaths Paths to target fields
   * @param splitter Expression to split source value (should return Sequence)
   * @param combinerForReverse Optional combiner expression for reverse migration
   */
  def splitField(
    sourcePath: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[DynamicValue, DynamicValue],
    combinerForReverse: Option[SchemaExpr[DynamicValue, DynamicValue]] = None
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.Split(DynamicOptic.root, sourcePath, targetPaths, splitter, combinerForReverse))

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
   * Build migration with validation and return errors for unmapped fields.
   * This is called by the compile-time validation macro (in Scala 3) or directly (in Scala 2).
   */
  def buildValidating: Migration[A, B] = {
    // Extract field names affected by actions
    val handledSourceFields = actions.flatMap(extractSourceFields).toSet
    val handledTargetFields = actions.flatMap(extractTargetFields).toSet

    // Extract source and target field names from schemas
    val sourceFields = extractSchemaFields(sourceSchema)
    val targetFields = extractSchemaFields(targetSchema)

    // Fields that exist in both source and target are implicitly handled
    val implicitFields = sourceFields.intersect(targetFields)

    // Check for unmapped source fields
    val unmappedSource = sourceFields.diff(handledSourceFields).diff(implicitFields)
    val unmappedTarget = targetFields.diff(handledTargetFields).diff(implicitFields)

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
    case MigrationAction.Join(_, sources, _, _, _) => sources.flatMap(extractLeafFieldName).toSet
    case MigrationAction.Split(_, source, _, _, _) => extractLeafFieldName(source).toSet
    case _                                      => Set.empty
  }

  private def extractTargetFields(action: MigrationAction): Set[String] = action match {
    case MigrationAction.Rename(_, _, to)        => Set(to)
    case MigrationAction.AddField(_, name, _)    => Set(name)
    case MigrationAction.Mandate(_, name, _)     => Set(name)
    case MigrationAction.Optionalize(_, name)    => Set(name)
    case MigrationAction.ChangeType(_, name, _)  => Set(name)
    case MigrationAction.Join(_, _, target, _, _)   => extractLeafFieldName(target).toSet
    case MigrationAction.Split(_, _, targets, _, _) => targets.flatMap(extractLeafFieldName).toSet
    case _                                       => Set.empty
  }

  /** Extract the leaf field name from a DynamicOptic path. */
  private def extractLeafFieldName(path: DynamicOptic): Option[String] =
    path.nodes.lastOption.collect {
      case DynamicOptic.Node.Field(name) => name
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
