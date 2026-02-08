package zio.blocks.schema.migration

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * Fluent builder for constructing type-safe migrations between schema versions.
 *
 * Accumulates [[MigrationAction]]s and produces a [[Migration]]. All operations
 * take string field names and [[MigrationExpr]] values, ensuring full
 * serializability. The builder itself is a pure data structure with zero
 * closures.
 *
 * Usage:
 * {{{
 * Migration.newBuilder[PersonV0, PersonV1]
 *   .renameField("name", "fullName")
 *   .addField("email", MigrationExpr.Literal(DynamicValue.string("default@example.com")))
 *   .dropField("age", MigrationExpr.Literal(DynamicValue.int(0)))
 *   .buildPartial
 * }}}
 *
 * @param actions
 *   The accumulated migration actions in insertion order
 * @param sourceSchema
 *   The schema for the source type A
 * @param targetSchema
 *   The schema for the target type B
 */
final case class MigrationBuilder[A, B] private[migration] (
  actions: Chunk[MigrationAction],
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) extends MigrationBuilderVersionSpecific[A, B] {

  // ─── Record Operations ──────────────────────────────────────────────

  /** Add a new field at root level with a default value expression. */
  def addField(fieldName: String, defaultValue: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.AddField(DynamicOptic.root, fieldName, defaultValue))

  /** Add a new field at a specific path with a default value expression. */
  def addFieldAt(at: DynamicOptic, fieldName: String, defaultValue: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.AddField(at, fieldName, defaultValue))

  /** Drop a field at root level. Stores `reverseDefault` for reversibility. */
  def dropField(fieldName: String, reverseDefault: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.DropField(DynamicOptic.root, fieldName, reverseDefault))

  /** Drop a field at a specific path. */
  def dropFieldAt(at: DynamicOptic, fieldName: String, reverseDefault: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.DropField(at, fieldName, reverseDefault))

  /** Rename a field at root level. */
  def renameField(fromName: String, toName: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Rename(DynamicOptic.root, fromName, toName))

  /** Rename a field at a specific path. */
  def renameFieldAt(at: DynamicOptic, fromName: String, toName: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Rename(at, fromName, toName))

  /** Transform a field value at root level. */
  def transformField(
    fieldName: String,
    expr: MigrationExpr,
    reverseExpr: MigrationExpr
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValue(DynamicOptic.root, fieldName, expr, reverseExpr))

  /** Transform a field value at a specific path. */
  def transformFieldAt(
    at: DynamicOptic,
    fieldName: String,
    expr: MigrationExpr,
    reverseExpr: MigrationExpr
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValue(at, fieldName, expr, reverseExpr))

  /** Make an optional field required (mandate) at root level. */
  def mandateField(fieldName: String, default: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Mandate(DynamicOptic.root, fieldName, default))

  /** Make an optional field required at a specific path. */
  def mandateFieldAt(at: DynamicOptic, fieldName: String, default: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Mandate(at, fieldName, default))

  /** Make a required field optional (optionalize) at root level. */
  def optionalizeField(fieldName: String, defaultForNone: MigrationExpr): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Optionalize(DynamicOptic.root, fieldName, defaultForNone))

  /** Make a required field optional at a specific path. */
  def optionalizeFieldAt(
    at: DynamicOptic,
    fieldName: String,
    defaultForNone: MigrationExpr
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Optionalize(at, fieldName, defaultForNone))

  /** Change the type of a field at root level. */
  def changeFieldType(
    fieldName: String,
    coercion: MigrationExpr,
    reverseCoercion: MigrationExpr
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.ChangeType(DynamicOptic.root, fieldName, coercion, reverseCoercion))

  /** Change the type of a field at a specific path. */
  def changeFieldTypeAt(
    at: DynamicOptic,
    fieldName: String,
    coercion: MigrationExpr,
    reverseCoercion: MigrationExpr
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.ChangeType(at, fieldName, coercion, reverseCoercion))

  /** Join multiple fields into one at root level. */
  def joinFields(
    sourceFields: Chunk[String],
    targetField: String,
    joinExpr: MigrationExpr,
    splitExprs: Chunk[(String, MigrationExpr)]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Join(DynamicOptic.root, sourceFields, targetField, joinExpr, splitExprs))

  /** Join multiple fields into one at a specific path. */
  def joinFieldsAt(
    at: DynamicOptic,
    sourceFields: Chunk[String],
    targetField: String,
    joinExpr: MigrationExpr,
    splitExprs: Chunk[(String, MigrationExpr)]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Join(at, sourceFields, targetField, joinExpr, splitExprs))

  /** Split one field into multiple at root level. */
  def splitField(
    sourceField: String,
    targetExprs: Chunk[(String, MigrationExpr)],
    joinExprForReverse: MigrationExpr
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Split(DynamicOptic.root, sourceField, targetExprs, joinExprForReverse))

  /** Split one field into multiple at a specific path. */
  def splitFieldAt(
    at: DynamicOptic,
    sourceField: String,
    targetExprs: Chunk[(String, MigrationExpr)],
    joinExprForReverse: MigrationExpr
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Split(at, sourceField, targetExprs, joinExprForReverse))

  // ─── Enum Operations ────────────────────────────────────────────────

  /** Rename an enum case at root level. */
  def renameCase(fromCase: String, toCase: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RenameCase(DynamicOptic.root, fromCase, toCase))

  /** Rename an enum case at a specific path. */
  def renameCaseAt(at: DynamicOptic, fromCase: String, toCase: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RenameCase(at, fromCase, toCase))

  /** Transform case payload with inner actions. */
  def transformCase(caseName: String, innerActions: Chunk[MigrationAction]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformCase(DynamicOptic.root, caseName, innerActions))

  /** Transform case payload with inner actions at a specific path. */
  def transformCaseAt(
    at: DynamicOptic,
    caseName: String,
    innerActions: Chunk[MigrationAction]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformCase(at, caseName, innerActions))

  // ─── Collection Operations ──────────────────────────────────────────

  /** Transform sequence elements at a specific path. */
  def transformElements(
    at: DynamicOptic,
    expr: MigrationExpr,
    reverseExpr: MigrationExpr
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformElements(at, expr, reverseExpr))

  /** Transform map keys at a specific path. */
  def transformKeys(
    at: DynamicOptic,
    expr: MigrationExpr,
    reverseExpr: MigrationExpr
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformKeys(at, expr, reverseExpr))

  /** Transform map values at a specific path. */
  def transformValues(
    at: DynamicOptic,
    expr: MigrationExpr,
    reverseExpr: MigrationExpr
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValues(at, expr, reverseExpr))

  // ─── Raw Action ─────────────────────────────────────────────────────

  /** Add a raw [[MigrationAction]]. */
  def addAction(action: MigrationAction): MigrationBuilder[A, B] =
    copy(actions = actions :+ action)

  // ─── Build ──────────────────────────────────────────────────────────

  /**
   * Build the migration without validation.
   *
   * Assembles all accumulated actions into a [[DynamicMigration]] and wraps it
   * in a typed [[Migration]].
   */
  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /**
   * Build the migration with validation.
   *
   * Validates the accumulated actions for structural consistency:
   *   - Detects duplicate `AddField` actions at the same path and field name
   *     (unless a `DropField` for that field occurs between them).
   *   - Detects conflicting `Rename` actions that rename FROM the same source
   *     field at the same path.
   *
   * All validation errors are accumulated (never short-circuits on the first
   * error). Returns `Left` with all errors if any validation fails, or `Right`
   * with the constructed [[Migration]] on success.
   */
  def build: Either[Chunk[MigrationError], Migration[A, B]] = {
    val errors = MigrationBuilder.validate(actions)
    if (errors.isEmpty) Right(buildPartial)
    else Left(errors)
  }
}

object MigrationBuilder {

  /**
   * Create a new builder for migrating from `A` to `B` using implicit schemas.
   */
  def apply[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    MigrationBuilder(Chunk.empty, sourceSchema, targetSchema)

  /** Create a new builder with explicit schemas. */
  def create[A, B](sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    MigrationBuilder(Chunk.empty, sourceSchema, targetSchema)

  /**
   * Validate a sequence of [[MigrationAction]]s for structural consistency.
   *
   * Checks performed:
   *   1. '''Duplicate AddField''': Two `AddField` actions at the same path with
   *      the same field name, without an intervening `DropField` for that
   *      field, produce a [[MigrationError.FieldAlreadyExists]] error.
   *   2. '''Rename conflict''': Two `Rename` actions at the same path renaming
   *      FROM the same source field produce a [[MigrationError.CustomError]].
   *
   * All errors are accumulated into the returned `Chunk`.
   */
  private[migration] def validate(actions: Chunk[MigrationAction]): Chunk[MigrationError] = {
    // Track (path.toString, fieldName) -> already-added for AddField dedup.
    // When a DropField is encountered for the same key, remove it from the map.
    val addedFields = new java.util.HashMap[String, Boolean]()
    // Track (path.toString, fromName) -> already-renamed for Rename conflict detection.
    val renamedFrom = new java.util.HashMap[String, Boolean]()

    val errors = ChunkBuilder.make[MigrationError]()
    val len    = actions.length
    var idx    = 0
    while (idx < len) {
      actions(idx) match {
        case MigrationAction.AddField(at, fieldName, _) =>
          val key = at.toString + "\u0000" + fieldName
          if (addedFields.containsKey(key)) {
            errors += MigrationError.FieldAlreadyExists(
              at,
              fieldName
            )
          } else {
            addedFields.put(key, true)
          }

        case MigrationAction.DropField(at, fieldName, _) =>
          val key = at.toString + "\u0000" + fieldName
          addedFields.remove(key)

        case MigrationAction.Rename(at, fromName, _) =>
          val key = at.toString + "\u0000" + fromName
          if (renamedFrom.containsKey(key)) {
            errors += MigrationError.CustomError(
              at,
              "Conflicting rename: field '" + fromName + "' is renamed more than once at path: " + at.toString
            )
          } else {
            renamedFrom.put(key, true)
          }

        case _ => ()
      }
      idx += 1
    }

    errors.result()
  }
}
