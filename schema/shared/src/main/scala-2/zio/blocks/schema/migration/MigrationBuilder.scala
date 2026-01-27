package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * Builder for constructing type-safe, compile-time validated migrations (Scala 2).
 *
 * CRITICAL DESIGN: All builder methods are REGULAR methods (not macros).
 * Only the `select()` macro is a macro. This ensures the builder works
 * correctly when stored in a `val`.
 *
 * Type parameters track which fields have been handled:
 * @tparam A Source type
 * @tparam B Target type
 * @tparam SrcRemaining Field names from A not yet consumed (as HList)
 * @tparam TgtRemaining Field names from B not yet provided (as HList)
 */
final class MigrationBuilder[A, B, SrcRemaining, TgtRemaining] private[migration] (
  private val sourceSchema: Schema[A],
  private val targetSchema: Schema[B],
  private val actions: Vector[MigrationAction]
) {

  import FieldSet._

  // ─────────────────────────────────────────────────────────────────────────
  // Record Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Add a new field to the target schema with a default value.
   */
  def addField[F, Name <: String](
    target: FieldSelector[B, F, Name],
    default: F
  )(implicit
    ev: Contains[TgtRemaining, Name],
    fieldSchema: Schema[F],
    remove: Remove[TgtRemaining, Name]
  ): MigrationBuilder[A, B, SrcRemaining, remove.Out] = {
    val resolvedDefault = Resolved.Literal(fieldSchema.toDynamicValue(default))
    val action = MigrationAction.AddField(DynamicOptic.root, target.name, resolvedDefault)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Add a field with a resolved expression as default.
   */
  def addFieldExpr[F, Name <: String](
    target: FieldSelector[B, F, Name],
    default: Resolved
  )(implicit
    ev: Contains[TgtRemaining, Name],
    remove: Remove[TgtRemaining, Name]
  ): MigrationBuilder[A, B, SrcRemaining, remove.Out] = {
    val action = MigrationAction.AddField(DynamicOptic.root, target.name, default)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Drop a field from the source schema.
   */
  def dropField[F, Name <: String](
    source: FieldSelector[A, F, Name]
  )(implicit
    ev: Contains[SrcRemaining, Name],
    remove: Remove[SrcRemaining, Name]
  ): MigrationBuilder[A, B, remove.Out, TgtRemaining] = {
    val action = MigrationAction.DropField(DynamicOptic.root, source.name, Resolved.Fail("No reverse default"))
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Drop a field with a default value for reverse migration.
   */
  def dropFieldWithDefault[F, Name <: String](
    source: FieldSelector[A, F, Name],
    defaultForReverse: F
  )(implicit
    ev: Contains[SrcRemaining, Name],
    fieldSchema: Schema[F],
    remove: Remove[SrcRemaining, Name]
  ): MigrationBuilder[A, B, remove.Out, TgtRemaining] = {
    val resolvedDefault = Resolved.Literal(fieldSchema.toDynamicValue(defaultForReverse))
    val action = MigrationAction.DropField(DynamicOptic.root, source.name, resolvedDefault)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Rename a field from source to target.
   */
  def renameField[F, SrcName <: String, TgtName <: String](
    from: FieldSelector[A, F, SrcName],
    to: FieldSelector[B, F, TgtName]
  )(implicit
    srcEv: Contains[SrcRemaining, SrcName],
    tgtEv: Contains[TgtRemaining, TgtName],
    srcRemove: Remove[SrcRemaining, SrcName],
    tgtRemove: Remove[TgtRemaining, TgtName]
  ): MigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] = {
    val action = MigrationAction.Rename(DynamicOptic.root, from.name, to.name)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Keep a field unchanged (field exists in both schemas with same name and type).
   */
  def keepField[F, Name <: String](
    field: FieldSelector[A, F, Name]
  )(implicit
    srcEv: Contains[SrcRemaining, Name],
    tgtEv: Contains[TgtRemaining, Name],
    srcRemove: Remove[SrcRemaining, Name],
    tgtRemove: Remove[TgtRemaining, Name]
  ): MigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] = {
    // No action needed - field is kept as-is
    new MigrationBuilder(sourceSchema, targetSchema, actions)
  }

  /**
   * Transform a field's value using a primitive conversion.
   */
  def changeFieldType[F1, F2, SrcName <: String, TgtName <: String](
    from: FieldSelector[A, F1, SrcName],
    to: FieldSelector[B, F2, TgtName],
    fromTypeName: String,
    toTypeName: String
  )(implicit
    srcEv: Contains[SrcRemaining, SrcName],
    tgtEv: Contains[TgtRemaining, TgtName],
    srcRemove: Remove[SrcRemaining, SrcName],
    tgtRemove: Remove[TgtRemaining, TgtName]
  ): MigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] = {
    val converter = Resolved.Convert(fromTypeName, toTypeName, Resolved.Identity)
    val reverseConverter = Resolved.Convert(toTypeName, fromTypeName, Resolved.Identity)

    val renameAction = if (from.name != to.name) {
      Some(MigrationAction.Rename(DynamicOptic.root, from.name, to.name))
    } else None

    val transformAction = MigrationAction.ChangeType(DynamicOptic.root, to.name, converter, reverseConverter)

    new MigrationBuilder(sourceSchema, targetSchema, actions ++ renameAction.toVector :+ transformAction)
  }

  /**
   * Make an optional field mandatory.
   */
  def mandateField[F, SrcName <: String, TgtName <: String](
    source: FieldSelector[A, Option[F], SrcName],
    target: FieldSelector[B, F, TgtName],
    default: F
  )(implicit
    srcEv: Contains[SrcRemaining, SrcName],
    tgtEv: Contains[TgtRemaining, TgtName],
    fieldSchema: Schema[F],
    srcRemove: Remove[SrcRemaining, SrcName],
    tgtRemove: Remove[TgtRemaining, TgtName]
  ): MigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] = {
    val resolvedDefault = Resolved.Literal(fieldSchema.toDynamicValue(default))
    val renameAction = if (source.name != target.name) {
      Some(MigrationAction.Rename(DynamicOptic.root, source.name, target.name))
    } else None
    val mandateAction = MigrationAction.Mandate(DynamicOptic.root, target.name, resolvedDefault)

    new MigrationBuilder(sourceSchema, targetSchema, actions ++ renameAction.toVector :+ mandateAction)
  }

  /**
   * Make a mandatory field optional.
   */
  def optionalizeField[F, SrcName <: String, TgtName <: String](
    source: FieldSelector[A, F, SrcName],
    target: FieldSelector[B, Option[F], TgtName]
  )(implicit
    srcEv: Contains[SrcRemaining, SrcName],
    tgtEv: Contains[TgtRemaining, TgtName],
    srcRemove: Remove[SrcRemaining, SrcName],
    tgtRemove: Remove[TgtRemaining, TgtName]
  ): MigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] = {
    val renameAction = if (source.name != target.name) {
      Some(MigrationAction.Rename(DynamicOptic.root, source.name, target.name))
    } else None
    val optionalizeAction = MigrationAction.Optionalize(DynamicOptic.root, target.name)

    new MigrationBuilder(sourceSchema, targetSchema, actions ++ renameAction.toVector :+ optionalizeAction)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Enum Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Rename an enum case.
   */
  def renameCase(from: String, to: String): MigrationBuilder[A, B, SrcRemaining, TgtRemaining] = {
    val action = MigrationAction.RenameCase(DynamicOptic.root, from, to)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Build Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Build the migration with full compile-time validation.
   *
   * Only compiles when ALL source fields are consumed and ALL target fields are provided.
   */
  def build(implicit
    srcEmpty: IsEmpty[SrcRemaining],
    tgtEmpty: IsEmpty[TgtRemaining]
  ): Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /**
   * Build migration without completeness validation.
   */
  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /**
   * Get the current actions (for debugging/inspection).
   */
  def currentActions: Vector[MigrationAction] = actions
}

object MigrationBuilder {

  import FieldSet._

  /**
   * Create a new migration builder.
   *
   * For now, this creates a builder with HNil for both field sets,
   * which means .build will always compile. Full field tracking requires
   * the SchemaFields macro.
   */
  def create[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, HNil, HNil] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)

  /**
   * Internal constructor for creating a builder with specific field sets.
   */
  private[migration] def initial[A, B, SrcFields, TgtFields](
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, SrcFields, TgtFields] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}
