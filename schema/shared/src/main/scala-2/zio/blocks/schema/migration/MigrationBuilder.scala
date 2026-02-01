package zio.blocks.schema.migration

import scala.language.experimental.macros
import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * Builder for constructing type-safe, compile-time validated migrations.
 *
 * Type parameters track which fields have been handled:
 * @tparam A
 *   Source type
 * @tparam B
 *   Target type
 * @tparam SrcRemaining
 *   Field names from A not yet consumed (as HList)
 * @tparam TgtRemaining
 *   Field names from B not yet provided (as HList)
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
    val action          = MigrationAction.AddField(DynamicOptic.root, target.name, resolvedDefault)
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
    val action          = MigrationAction.DropField(DynamicOptic.root, source.name, resolvedDefault)
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
   * Keep a field unchanged (field exists in both schemas with same name and
   * type).
   */
  def keepField[F, Name <: String](
    @annotation.unused field: FieldSelector[A, F, Name]
  )(implicit
    srcEv: Contains[SrcRemaining, Name],
    tgtEv: Contains[TgtRemaining, Name],
    srcRemove: Remove[SrcRemaining, Name],
    tgtRemove: Remove[TgtRemaining, Name]
  ): MigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] =
    // No action needed - field is kept as-is
    new MigrationBuilder(sourceSchema, targetSchema, actions)

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
    val converter        = Resolved.Convert(fromTypeName, toTypeName, Resolved.Identity)
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
    val renameAction    = if (source.name != target.name) {
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
  // Nested Migration Operations (with compile-time completeness checking)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Apply a nested migration to a record field.
   *
   * The nested migration must be a complete Migration[F1, F2] built with full
   * compile-time field tracking. This ensures validation at ALL nesting levels.
   *
   * {{{
   * case class AddressV0(street: String)
   * case class AddressV1(street: String, city: String)
   * case class PersonV0(name: String, address: AddressV0)
   * case class PersonV1(name: String, address: AddressV1)
   *
   * val migration = MigrationBuilder.withFieldTracking[PersonV0, PersonV1]
   *   .keepField(select(_.name))
   *   .inField(select[PersonV0](_.address), select[PersonV1](_.address))(
   *     MigrationBuilder.withFieldTracking[AddressV0, AddressV1]
   *       .keepField(select(_.street))
   *       .addField(select(_.city), "Unknown")
   *       .build  // ← Compile-time validation for address fields
   *   )
   *   .build  // ← Compile-time validation for person fields
   * }}}
   */
  def inField[F1, F2, SrcName <: String, TgtName <: String](
    srcField: FieldSelector[A, F1, SrcName],
    tgtField: FieldSelector[B, F2, TgtName]
  )(
    nestedMigration: Migration[F1, F2]
  )(implicit
    srcEv: Contains[SrcRemaining, SrcName],
    tgtEv: Contains[TgtRemaining, TgtName],
    srcRemove: Remove[SrcRemaining, SrcName],
    tgtRemove: Remove[TgtRemaining, TgtName]
  ): MigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] = {
    val renameAction = if (srcField.name != tgtField.name) {
      Some(MigrationAction.Rename(DynamicOptic.root, srcField.name, tgtField.name))
    } else None

    val fieldPath       = DynamicOptic.root.field(tgtField.name)
    val prefixedActions = nestedMigration.dynamicMigration.actions.map(_.prefixPath(fieldPath))

    new MigrationBuilder(sourceSchema, targetSchema, actions ++ renameAction.toVector ++ prefixedActions)
  }

  /**
   * Apply a nested migration to a field with the same name in source and
   * target.
   */
  def inFieldSame[F1, F2, Name <: String](
    field: FieldSelector[A, F1, Name]
  )(
    nestedMigration: Migration[F1, F2]
  )(implicit
    srcEv: Contains[SrcRemaining, Name],
    tgtEv: Contains[TgtRemaining, Name],
    srcRemove: Remove[SrcRemaining, Name],
    tgtRemove: Remove[TgtRemaining, Name]
  ): MigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] = {
    val fieldPath       = DynamicOptic.root.field(field.name)
    val prefixedActions = nestedMigration.dynamicMigration.actions.map(_.prefixPath(fieldPath))

    new MigrationBuilder(sourceSchema, targetSchema, actions ++ prefixedActions)
  }

  /**
   * Apply a nested migration to each element in a sequence field.
   *
   * {{{
   * case class PersonV0(name: String, addresses: List[AddressV0])
   * case class PersonV1(name: String, addresses: List[AddressV1])
   *
   * val addressMigration = MigrationBuilder.withFieldTracking[AddressV0, AddressV1]
   *   .keepField(select(_.street))
   *   .addField(select(_.city), "Unknown")
   *   .build
   *
   * val migration = MigrationBuilder.withFieldTracking[PersonV0, PersonV1]
   *   .keepField(select(_.name))
   *   .inElements(select[PersonV0](_.addresses))(addressMigration)
   *   .build
   * }}}
   */
  def inElements[E1, E2, Name <: String](
    field: FieldSelector[A, _ <: Iterable[E1], Name]
  )(
    nestedMigration: Migration[E1, E2]
  )(implicit
    srcEv: Contains[SrcRemaining, Name],
    tgtEv: Contains[TgtRemaining, Name],
    srcRemove: Remove[SrcRemaining, Name],
    tgtRemove: Remove[TgtRemaining, Name]
  ): MigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] = {
    val fieldPath       = DynamicOptic.root.field(field.name).elements
    val prefixedActions = nestedMigration.dynamicMigration.actions.map(_.prefixPath(fieldPath))

    new MigrationBuilder(sourceSchema, targetSchema, actions ++ prefixedActions)
  }

  /**
   * Apply a nested migration to each value in a map field.
   */
  def inMapValues[K, V1, V2, Name <: String](
    field: FieldSelector[A, _ <: Map[K, V1], Name]
  )(
    nestedMigration: Migration[V1, V2]
  )(implicit
    srcEv: Contains[SrcRemaining, Name],
    tgtEv: Contains[TgtRemaining, Name],
    srcRemove: Remove[SrcRemaining, Name],
    tgtRemove: Remove[TgtRemaining, Name]
  ): MigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] = {
    val fieldPath       = DynamicOptic.root.field(field.name).mapValues
    val prefixedActions = nestedMigration.dynamicMigration.actions.map(_.prefixPath(fieldPath))

    new MigrationBuilder(sourceSchema, targetSchema, actions ++ prefixedActions)
  }

  /**
   * Apply a nested migration to a specific enum case.
   */
  def inCase[C1, C2](
    caseName: String
  )(
    nestedMigration: Migration[C1, C2]
  ): MigrationBuilder[A, B, SrcRemaining, TgtRemaining] = {
    val action = MigrationAction.TransformCase(DynamicOptic.root, caseName, nestedMigration.dynamicMigration.actions)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Build Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Build the migration with full compile-time validation.
   *
   * Only compiles when ALL source fields are consumed and ALL target fields are
   * provided.
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
   * Create a new migration builder without compile-time field tracking.
   *
   * This creates a builder with HNil for both field sets, which means .build
   * will always compile. Use [[withFieldTracking]] for full compile-time
   * validation.
   */
  def create[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, HNil, HNil] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)

  /**
   * Create a new migration builder with compile-time field tracking.
   *
   * This macro extracts field names from case class types at compile time,
   * enabling full validation that all fields are handled.
   *
   * The `build` method will only compile when:
   *   - All source fields have been consumed (dropped, renamed, or kept)
   *   - All target fields have been provided (added, renamed, or kept)
   *
   * Example:
   * {{{
   * case class PersonV0(name: String, age: Int)
   * case class PersonV1(fullName: String, age: Int, country: String)
   *
   * val migration = MigrationBuilder.withFieldTracking[PersonV0, PersonV1]
   *   .renameField(select(_.name), select(_.fullName))
   *   .keepField(select(_.age))
   *   .addField(select(_.country), "US")
   *   .build  // ✅ Compiles - all fields handled
   * }}}
   */
  def withFieldTracking[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, _, _] = macro MigrationBuilderMacros.withFieldTrackingImpl[A, B]

  /**
   * Create a migration builder with explicit field names.
   *
   * Use this when you need to specify field names manually (e.g., for
   * non-case-class types or when automatic extraction doesn't work).
   */
  def withFields[A, B](srcFields: String*)(tgtFields: String*)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, _, _] = macro MigrationBuilderMacros.withFieldsImpl[A, B]

  /**
   * Internal constructor for creating a builder with specific field sets.
   */
  private[migration] def initial[A, B, SrcFields, TgtFields](
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, SrcFields, TgtFields] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}
