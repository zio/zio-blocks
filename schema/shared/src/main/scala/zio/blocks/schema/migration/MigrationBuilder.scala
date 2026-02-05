package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr}

/**
 * Builder for constructing validated migrations between schema versions.
 *
 * `MigrationBuilder` provides a fluent API for specifying structural
 * transformations. Each method appends a `MigrationAction` to the builder's
 * action list. The `.build()` method validates the complete migration using
 * `SchemaShape` symbolic execution, ensuring the transformed source shape
 * matches the target shape.
 *
 * All default values are resolved eagerly at method call time into concrete
 * `SchemaExpr.Literal` instances — no deferred resolution or sentinel types.
 *
 * @param sourceSchema
 *   Schema for the source type A
 * @param targetSchema
 *   Schema for the target type B
 * @param actions
 *   Accumulated migration actions
 * @param _metadata
 *   Migration metadata (id, description, etc.)
 */
final class MigrationBuilder[A, B] private[migration] (
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction],
  private val _metadata: MigrationMetadata
) {

  // ── Field Operations (DynamicOptic-based, no macros) ────────────────

  /**
   * Adds a new field at the specified path with a default value.
   */
  def addFieldAt[T](path: DynamicOptic, default: T)(implicit s: Schema[T]): MigrationBuilder[A, B] = {
    val expr = SchemaExpr.Literal[Any, Any](default.asInstanceOf[Any], s.asInstanceOf[Schema[Any]])
    appendAction(MigrationAction.AddField(path, expr))
  }

  /**
   * Drops a field at the specified path. The action is lossy (no reverse
   * default).
   */
  def dropFieldAt(path: DynamicOptic): MigrationBuilder[A, B] =
    appendAction(MigrationAction.DropField(path, reverseDefault = None))

  /**
   * Drops a field at the specified path with a reverse default (lossless).
   */
  def dropFieldAt[T](path: DynamicOptic, reverseDefault: T)(implicit s: Schema[T]): MigrationBuilder[A, B] = {
    val expr = SchemaExpr.Literal[Any, Any](reverseDefault.asInstanceOf[Any], s.asInstanceOf[Schema[Any]])
    appendAction(MigrationAction.DropField(path, reverseDefault = Some(expr)))
  }

  /**
   * Renames a field. `fromPath` is the DynamicOptic to the existing field,
   * `toName` is the new field name.
   */
  def renameFieldAt(fromPath: DynamicOptic, toName: String): MigrationBuilder[A, B] =
    appendAction(MigrationAction.Rename(fromPath, toName))

  // ── Value Transforms ────────────────────────────────────────────────

  /**
   * Transforms the value at a path using an expression. Lossy (no inverse).
   */
  def transformValueAt(path: DynamicOptic, transform: SchemaExpr[Any, Any]): MigrationBuilder[A, B] =
    appendAction(MigrationAction.TransformValue(path, transform, inverse = None))

  /**
   * Transforms the value at a path with an inverse expression (lossless).
   */
  def transformValueAt(
    path: DynamicOptic,
    transform: SchemaExpr[Any, Any],
    inverse: SchemaExpr[Any, Any]
  ): MigrationBuilder[A, B] =
    appendAction(MigrationAction.TransformValue(path, transform, inverse = Some(inverse)))

  /**
   * Changes the type of a value at a path. Lossy (no inverse converter).
   */
  def changeTypeAt(path: DynamicOptic, converter: SchemaExpr[Any, Any]): MigrationBuilder[A, B] =
    appendAction(MigrationAction.ChangeType(path, converter, inverseConverter = None))

  /**
   * Changes the type of a value at a path with an inverse converter (lossless).
   */
  def changeTypeAt(
    path: DynamicOptic,
    converter: SchemaExpr[Any, Any],
    inverseConverter: SchemaExpr[Any, Any]
  ): MigrationBuilder[A, B] =
    appendAction(MigrationAction.ChangeType(path, converter, inverseConverter = Some(inverseConverter)))

  // ── Optional/Mandate ────────────────────────────────────────────────

  /**
   * Converts an optional field to mandatory, using the given default for None
   * values.
   */
  def mandateAt[T](path: DynamicOptic, default: T)(implicit s: Schema[T]): MigrationBuilder[A, B] = {
    val expr = SchemaExpr.Literal[Any, Any](default.asInstanceOf[Any], s.asInstanceOf[Schema[Any]])
    appendAction(MigrationAction.Mandate(path, expr))
  }

  /**
   * Wraps a mandatory field in Option (Some).
   */
  def optionalizeAt(path: DynamicOptic): MigrationBuilder[A, B] =
    appendAction(MigrationAction.Optionalize(path))

  // ── Join/Split ──────────────────────────────────────────────────────

  /**
   * Joins multiple source fields into a single target field.
   *
   * @param targetPath
   *   Path where the combined value goes
   * @param sourcePaths
   *   Paths to extract values from (all consumed/removed)
   * @param combiner
   *   Expression that combines the extracted values
   * @param targetShape
   *   Explicit output shape for validation
   */
  def joinAt(
    targetPath: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[Any, Any],
    targetShape: SchemaShape = SchemaShape.Dyn
  ): MigrationBuilder[A, B] =
    appendAction(MigrationAction.Join(targetPath, sourcePaths, combiner, inverseSplitter = None, targetShape))

  /**
   * Splits one source field into multiple target fields.
   *
   * @param sourcePath
   *   Path to extract from
   * @param targetPaths
   *   Paths where the split pieces go
   * @param splitter
   *   Expression that produces the pieces
   * @param targetShapes
   *   One per target path, for validation
   */
  def splitAt(
    sourcePath: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[Any, Any],
    targetShapes: Vector[SchemaShape] = Vector.empty
  ): MigrationBuilder[A, B] =
    appendAction(MigrationAction.Split(sourcePath, targetPaths, splitter, inverseJoiner = None, targetShapes))

  // ── Enum Operations ────────────────────────────────────────────────

  /**
   * Renames a case in a variant at the specified path.
   */
  def renameCaseAt(path: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    appendAction(MigrationAction.RenameCase(path, from, to))

  /**
   * Transforms a specific case's value within a variant using sub-actions.
   */
  def transformCaseAt(
    path: DynamicOptic,
    caseName: String,
    subActions: Vector[MigrationAction]
  ): MigrationBuilder[A, B] =
    appendAction(MigrationAction.TransformCase(path, caseName, subActions))

  // ── Collection/Map Operations ──────────────────────────────────────

  /**
   * Transforms each element of a sequence at the path. Lossy (no inverse).
   */
  def transformElementsAt(path: DynamicOptic, transform: SchemaExpr[Any, Any]): MigrationBuilder[A, B] =
    appendAction(MigrationAction.TransformElements(path, transform, inverse = None))

  /**
   * Transforms each element with an inverse (lossless).
   */
  def transformElementsAt(
    path: DynamicOptic,
    transform: SchemaExpr[Any, Any],
    inverse: SchemaExpr[Any, Any]
  ): MigrationBuilder[A, B] =
    appendAction(MigrationAction.TransformElements(path, transform, inverse = Some(inverse)))

  /**
   * Transforms each key of a map at the path. Lossy (no inverse).
   */
  def transformKeysAt(path: DynamicOptic, transform: SchemaExpr[Any, Any]): MigrationBuilder[A, B] =
    appendAction(MigrationAction.TransformKeys(path, transform, inverse = None))

  /**
   * Transforms each key with an inverse (lossless).
   */
  def transformKeysAt(
    path: DynamicOptic,
    transform: SchemaExpr[Any, Any],
    inverse: SchemaExpr[Any, Any]
  ): MigrationBuilder[A, B] =
    appendAction(MigrationAction.TransformKeys(path, transform, inverse = Some(inverse)))

  /**
   * Transforms each value of a map at the path. Lossy (no inverse).
   */
  def transformValuesAt(path: DynamicOptic, transform: SchemaExpr[Any, Any]): MigrationBuilder[A, B] =
    appendAction(MigrationAction.TransformValues(path, transform, inverse = None))

  /**
   * Transforms each value with an inverse (lossless).
   */
  def transformValuesAt(
    path: DynamicOptic,
    transform: SchemaExpr[Any, Any],
    inverse: SchemaExpr[Any, Any]
  ): MigrationBuilder[A, B] =
    appendAction(MigrationAction.TransformValues(path, transform, inverse = Some(inverse)))

  // ── Metadata ──────────────────────────────────────────────────────

  def withId(id: String): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions, _metadata.copy(id = Some(id)))

  def withDescription(desc: String): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions, _metadata.copy(description = Some(desc)))

  def withCreatedBy(author: String): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions, _metadata.copy(createdBy = Some(author)))

  def withTimestamp(epochMillis: Long): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions, _metadata.copy(timestamp = Some(epochMillis)))

  // ── Build ──────────────────────────────────────────────────────────

  /**
   * Validates and builds the migration.
   *
   * Performs full validation using SchemaShape symbolic execution:
   *   1. Extracts source and target shapes from their Reflect trees
   *   2. Symbolically applies all actions to transform the source shape
   *   3. Compares the transformed shape against the target shape
   *
   * @param wrapTransparent
   *   If `true`, Wrap(x) is considered equivalent to x during shape comparison
   * @throws MigrationValidationException
   *   if validation fails
   */
  def build(wrapTransparent: Boolean = false): Migration[A, B] = {
    val sourceShape = SchemaShape.fromReflect(sourceSchema.reflect)
    val targetShape = SchemaShape.fromReflect(targetSchema.reflect)

    // Step 1: Symbolically apply all actions to the source shape
    val transformed = actions.zipWithIndex.foldLeft[Either[List[MigrationError], SchemaShape]](Right(sourceShape)) {
      case (Right(shape), (action, idx)) =>
        SchemaShape.applyAction(shape, action) match {
          case Right(newShape) => Right(newShape)
          case Left(errors)    =>
            val enriched = errors.map(_.copy(actionIndex = Some(idx), action = Some(action)))
            Left(enriched)
        }
      case (left, _) => left
    }

    transformed match {
      case Left(errors) =>
        throw MigrationValidationException(errors)
      case Right(result) =>
        // Step 2: Compare transformed shape against target shape
        val mismatches = SchemaShape.compareShapes(result, targetShape, wrapTransparent = wrapTransparent)
        if (mismatches.nonEmpty)
          throw MigrationValidationException(mismatches)

        // Step 3: Compute fingerprint and build
        val meta = _metadata.copy(
          fingerprint = Some(MigrationMetadata.fingerprint(actions))
        )
        Migration(DynamicMigration(actions, meta), sourceSchema, targetSchema)
    }
  }

  /**
   * Builds the migration with partial validation. Skips the shape comparison
   * step but still validates individual actions (e.g., renaming a field that
   * doesn't exist).
   */
  def buildPartial: Migration[A, B] = {
    val sourceShape = SchemaShape.fromReflect(sourceSchema.reflect)

    val transformed = actions.zipWithIndex.foldLeft[Either[List[MigrationError], SchemaShape]](Right(sourceShape)) {
      case (Right(shape), (action, idx)) =>
        SchemaShape.applyAction(shape, action) match {
          case Right(newShape) => Right(newShape)
          case Left(errors)    =>
            val enriched = errors.map(_.copy(actionIndex = Some(idx), action = Some(action)))
            Left(enriched)
        }
      case (left, _) => left
    }

    transformed match {
      case Left(errors) =>
        throw MigrationValidationException(errors)
      case Right(_) =>
        val meta = _metadata.copy(
          fingerprint = Some(MigrationMetadata.fingerprint(actions))
        )
        Migration(DynamicMigration(actions, meta), sourceSchema, targetSchema)
    }
  }

  /**
   * Builds the migration and immediately applies it to a sample value. Useful
   * for testing and REPL exploration.
   */
  def dryRun(sample: A): Either[MigrationError, B] = build().apply(sample)

  // ── Internal ──────────────────────────────────────────────────────

  private def appendAction(action: MigrationAction): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action, _metadata)
}

object MigrationBuilder {

  /** Creates a new empty builder for migrating from A to B. */
  def apply[A, B](implicit sa: Schema[A], sb: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(sa, sb, Vector.empty, MigrationMetadata.empty)
}
