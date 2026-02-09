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

    val nestedAction = MigrationAction.TransformField(
      DynamicOptic.root,
      tgtField.name,
      nestedMigration.dynamicMigration.actions
    )

    new MigrationBuilder(sourceSchema, targetSchema, actions ++ renameAction.toVector :+ nestedAction)
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
    val nestedAction = MigrationAction.TransformField(
      DynamicOptic.root,
      field.name,
      nestedMigration.dynamicMigration.actions
    )

    new MigrationBuilder(sourceSchema, targetSchema, actions :+ nestedAction)
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
    val nestedAction = MigrationAction.TransformEachElement(
      DynamicOptic.root,
      field.name,
      nestedMigration.dynamicMigration.actions
    )

    new MigrationBuilder(sourceSchema, targetSchema, actions :+ nestedAction)
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
    val nestedAction = MigrationAction.TransformEachMapValue(
      DynamicOptic.root,
      field.name,
      nestedMigration.dynamicMigration.actions
    )

    new MigrationBuilder(sourceSchema, targetSchema, actions :+ nestedAction)
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
  // Cross-Branch Field Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Join multiple source fields into a single target field.
   *
   * This operation works across different branches of the document, enabling
   * combining fields that don't share a common parent. Each source field is
   * accessed from the root document using RootAccess.
   *
   * NOTE: Source fields are NOT automatically removed from tracking - use
   * dropField if they should not exist in the target schema.
   *
   * @param sources
   *   Vector of FieldSelectors for the source fields to join
   * @param target
   *   FieldSelector for the target field to create
   * @param separator
   *   String to use between joined values (default: empty string)
   * @return
   *   Builder with the target field marked as provided
   */
  def joinFields[T, TgtName <: String](
    sources: Vector[FieldSelector[A, _, _]],
    target: FieldSelector[B, T, TgtName],
    separator: String = ""
  )(implicit
    tgtEv: Contains[TgtRemaining, TgtName],
    remove: Remove[TgtRemaining, TgtName]
  ): MigrationBuilder[A, B, SrcRemaining, remove.Out] = {
    // Build RootAccess expressions for each source using full optic path
    val accessExprs = sources.map { src =>
      Resolved.RootAccess(src.optic)
    }

    // Combine with Concat
    val combiner = Resolved.Concat(accessExprs, separator)

    // Generate AddField at target's parent path
    val targetParentOptic = target.optic.parent.getOrElse(DynamicOptic.root)
    val action            = MigrationAction.AddField(targetParentOptic, target.name, combiner)

    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Split a source field into multiple target fields.
   *
   * This operation splits a single source field by a separator and assigns each
   * part to a corresponding target field. The source field is accessed from the
   * root document using RootAccess.
   *
   * NOTE: This operation is NOT reversible - the reverse migration will fail.
   * Use dropField on the source if it should not exist in the target schema.
   *
   * @param source
   *   FieldSelector for the source field to split
   * @param targets
   *   Vector of FieldSelectors for the target fields to create
   * @param separator
   *   String to split the source value on
   * @return
   *   Builder with actions added (no type-level tracking due to Vector)
   */
  def splitField[S, SrcName <: String](
    source: FieldSelector[A, S, SrcName],
    targets: Vector[FieldSelector[B, _, _]],
    separator: String
  ): MigrationBuilder[A, B, SrcRemaining, TgtRemaining] = {
    // Use Compose(At(index, Identity), SplitString(...)) - evaluates SplitString first, then extracts element
    val addActions = targets.zipWithIndex.map { case (tgt, idx) =>
      val splitExpr = Resolved.Compose(
        Resolved.At(idx, Resolved.Identity),
        Resolved.SplitString(separator, Resolved.RootAccess(source.optic))
      )
      val targetParentOptic = tgt.optic.parent.getOrElse(DynamicOptic.root)
      MigrationAction.AddField(targetParentOptic, tgt.fieldName, splitExpr)
    }

    new MigrationBuilder(sourceSchema, targetSchema, actions ++ addActions)
  }

  /**
   * Split a source field into two target fields with type-level tracking.
   *
   * This variant explicitly consumes the source field from tracking and marks
   * both target fields as provided. Use when you want compile-time validation.
   *
   * NOTE: This operation is NOT reversible - the reverse migration will fail.
   *
   * @param source
   *   FieldSelector for the source field to split (will be consumed)
   * @param target1
   *   First target field
   * @param target2
   *   Second target field
   * @param separator
   *   String to split the source value on
   */
  def splitFieldTracked[S, SrcName <: String, T1, Tgt1Name <: String, T2, Tgt2Name <: String, SrcOut, Tgt1Out, Tgt2Out](
    source: FieldSelector[A, S, SrcName],
    target1: FieldSelector[B, T1, Tgt1Name],
    target2: FieldSelector[B, T2, Tgt2Name],
    separator: String
  )(implicit
    srcEv: Contains[SrcRemaining, SrcName],
    @annotation.unused srcRemove: Remove.Aux[SrcRemaining, SrcName, SrcOut],
    tgt1Ev: Contains[TgtRemaining, Tgt1Name],
    @annotation.unused tgt1Remove: Remove.Aux[TgtRemaining, Tgt1Name, Tgt1Out],
    tgt2Ev: Contains[Tgt1Out, Tgt2Name],
    @annotation.unused tgt2Remove: Remove.Aux[Tgt1Out, Tgt2Name, Tgt2Out]
  ): MigrationBuilder[A, B, SrcOut, Tgt2Out] = {
    val targets = Vector(target1, target2)
    // Use Compose(At(index, Identity), SplitString(...)) - evaluates SplitString first, then extracts element
    val addActions = targets.zipWithIndex.map { case (tgt, idx) =>
      val splitExpr = Resolved.Compose(
        Resolved.At(idx, Resolved.Identity),
        Resolved.SplitString(separator, Resolved.RootAccess(source.optic))
      )
      val targetParentOptic = tgt.optic.parent.getOrElse(DynamicOptic.root)
      MigrationAction.AddField(targetParentOptic, tgt.fieldName, splitExpr)
    }

    // Also add a dropField action for the source
    val dropAction = MigrationAction.DropField(DynamicOptic.root, source.name, Resolved.Fail("Split is not reversible"))

    new MigrationBuilder(sourceSchema, targetSchema, actions ++ addActions :+ dropAction)
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
