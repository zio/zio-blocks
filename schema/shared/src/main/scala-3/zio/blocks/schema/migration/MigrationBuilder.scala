package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * Builder for constructing type-safe, compile-time validated migrations.
 *
 * CRITICAL DESIGN: All builder methods are REGULAR methods (not inline).
 * Only the `select()` macro is inline. This ensures the builder works
 * correctly when stored in a `val`:
 *
 * {{{
 * val builder = MigrationBuilder.create[PersonV0, PersonV1]
 * val b2 = builder.addField(select(_.age), 0)
 * val b3 = b2.renameField(select(_.name), select(_.fullName))
 * b3.build  // ✅ Compiles - type tracking preserved through vals
 * }}}
 *
 * Type parameters track which fields have been handled:
 * @tparam A Source type
 * @tparam B Target type
 * @tparam SrcRemaining Field names from A not yet consumed (as tuple of strings)
 * @tparam TgtRemaining Field names from B not yet provided (as tuple of strings)
 */
final class MigrationBuilder[A, B, SrcRemaining <: Tuple, TgtRemaining <: Tuple] private[migration] (
  private val sourceSchema: Schema[A],
  private val targetSchema: Schema[B],
  private val actions: Vector[MigrationAction]
) {

  // ─────────────────────────────────────────────────────────────────────────
  // Record Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Add a new field to the target schema with a default value.
   *
   * NOT INLINE - takes a FieldSelector that already has the field name as a type parameter.
   */
  def addField[F, Name <: String](
    target: FieldSelector[B, F, Name],
    default: F
  )(using
    ev: FieldSet.Contains[TgtRemaining, Name] =:= true,
    fieldSchema: Schema[F]
  ): MigrationBuilder[A, B, SrcRemaining, FieldSet.Remove[TgtRemaining, Name]] = {
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
  )(using
    ev: FieldSet.Contains[TgtRemaining, Name] =:= true
  ): MigrationBuilder[A, B, SrcRemaining, FieldSet.Remove[TgtRemaining, Name]] = {
    val action = MigrationAction.AddField(DynamicOptic.root, target.name, default)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Drop a field from the source schema.
   */
  def dropField[F, Name <: String](
    source: FieldSelector[A, F, Name]
  )(using
    ev: FieldSet.Contains[SrcRemaining, Name] =:= true
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, Name], TgtRemaining] = {
    val action = MigrationAction.DropField(DynamicOptic.root, source.name, Resolved.Fail("No reverse default"))
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Drop a field with a default value for reverse migration.
   */
  def dropFieldWithDefault[F, Name <: String](
    source: FieldSelector[A, F, Name],
    defaultForReverse: F
  )(using
    ev: FieldSet.Contains[SrcRemaining, Name] =:= true,
    fieldSchema: Schema[F]
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, Name], TgtRemaining] = {
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
  )(using
    srcEv: FieldSet.Contains[SrcRemaining, SrcName] =:= true,
    tgtEv: FieldSet.Contains[TgtRemaining, TgtName] =:= true
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, SrcName], FieldSet.Remove[TgtRemaining, TgtName]] = {
    val action = MigrationAction.Rename(DynamicOptic.root, from.name, to.name)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Keep a field unchanged (field exists in both schemas with same name and type).
   */
  def keepField[F, Name <: String](
    @scala.annotation.unused field: FieldSelector[A, F, Name]
  )(using
    srcEv: FieldSet.Contains[SrcRemaining, Name] =:= true,
    tgtEv: FieldSet.Contains[TgtRemaining, Name] =:= true
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, Name], FieldSet.Remove[TgtRemaining, Name]] = {
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
  )(using
    srcEv: FieldSet.Contains[SrcRemaining, SrcName] =:= true,
    tgtEv: FieldSet.Contains[TgtRemaining, TgtName] =:= true
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, SrcName], FieldSet.Remove[TgtRemaining, TgtName]] = {
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
  )(using
    srcEv: FieldSet.Contains[SrcRemaining, SrcName] =:= true,
    tgtEv: FieldSet.Contains[TgtRemaining, TgtName] =:= true,
    fieldSchema: Schema[F]
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, SrcName], FieldSet.Remove[TgtRemaining, TgtName]] = {
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
  )(using
    srcEv: FieldSet.Contains[SrcRemaining, SrcName] =:= true,
    tgtEv: FieldSet.Contains[TgtRemaining, TgtName] =:= true
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, SrcName], FieldSet.Remove[TgtRemaining, TgtName]] = {
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
   * NOT INLINE - this is a regular method with type constraints.
   * Only compiles when ALL source fields are consumed and ALL target fields are provided.
   */
  def build(using
    srcEmpty: SrcRemaining =:= EmptyTuple,
    tgtEmpty: TgtRemaining =:= EmptyTuple
  ): Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /**
   * Build migration without completeness validation.
   *
   * Use this for partial migrations or testing when not all fields
   * need to be explicitly handled.
   */
  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /**
   * Get the current actions (for debugging/inspection).
   */
  def currentActions: Vector[MigrationAction] = actions

  /**
   * Get a description of the current migration state.
   */
  def describe: String =
    if (actions.isEmpty) "Empty migration builder"
    else actions.map(_.toString).mkString("MigrationBuilder(\n  ", ",\n  ", "\n)")
}

object MigrationBuilder {

  /**
   * Create a new migration builder.
   *
   * The field sets are initialized based on the schemas. In a full
   * implementation, this would use macros to extract field names from
   * the schemas at compile time.
   *
   * For now, this creates a builder with EmptyTuple for both field sets,
   * which means .build will always compile. Full field tracking requires
   * the SchemaFields macro.
   */
  def create[A, B](using
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, EmptyTuple, EmptyTuple] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)

  /**
   * Internal constructor for creating a builder with specific field sets.
   */
  private[migration] def initial[A, B, SrcFields <: Tuple, TgtFields <: Tuple](
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, SrcFields, TgtFields] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}
