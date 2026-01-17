package zio.blocks.schema.migration

// format: off
// import zio.blocks.schema._

/**
 * Version-specific methods for MigrationBuilder (Scala 2). Provides
 * macro-based selector syntax for field operations.
 */
private[migration] trait MigrationBuilderPlatform[A, B] { self: MigrationBuilder[A, B] =>
  import scala.language.experimental.macros

  /**
   * Build the final migration. (Scala 2 validation is runtime-only via buildValidating).
   */
  /**
   * Build the final migration.
   */
  def build: Migration[A, B] = macro MigrationBuilderMacros.buildImpl[A, B]

  // ============================================================================
  // Selector-based Macros
  // ============================================================================


  def addField(target: B => Any, default: zio.blocks.schema.DynamicValue): MigrationBuilder[A, B] = macro MigrationBuilderMacros.addFieldImpl[A, B]

  def dropField(source: A => Any): MigrationBuilder[A, B] = macro MigrationBuilderMacros.dropFieldImpl[A, B]

  def renameField(from: A => Any, to: B => Any): MigrationBuilder[A, B] = macro MigrationBuilderMacros.renameFieldImpl[A, B]

  def optionalizeField(source: A => Any): MigrationBuilder[A, B] = macro MigrationBuilderMacros.optionalizeFieldImpl[A, B]

  def transformField(
    source: A => Any,
    target: B => Any,
    transform: zio.blocks.schema.SchemaExpr[zio.blocks.schema.DynamicValue, zio.blocks.schema.DynamicValue]
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.transformFieldImpl[A, B]

  def mandateField(
    source: A => Option[_],
    target: B => Any,
    default: zio.blocks.schema.DynamicValue
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.mandateFieldImpl[A, B]

  // ============================================================================
  // Join / Split with type-safe selectors
  // ============================================================================

  /**
   * Join two source fields into a single target field using type-safe selectors.
   */
  def joinFields2(
    source1: A => Any,
    source2: A => Any
  )(
    target: B => Any
  )(
    combiner: zio.blocks.schema.SchemaExpr[zio.blocks.schema.DynamicValue, zio.blocks.schema.DynamicValue],
    splitterForReverse: Option[zio.blocks.schema.SchemaExpr[zio.blocks.schema.DynamicValue, zio.blocks.schema.DynamicValue]]
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.joinFields2Impl[A, B]

  /**
   * Split a source field into two target fields using type-safe selectors.
   */
  def splitField2(
    source: A => Any
  )(
    target1: B => Any,
    target2: B => Any
  )(
    splitter: zio.blocks.schema.SchemaExpr[zio.blocks.schema.DynamicValue, zio.blocks.schema.DynamicValue],
    combinerForReverse: Option[zio.blocks.schema.SchemaExpr[zio.blocks.schema.DynamicValue, zio.blocks.schema.DynamicValue]]
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.splitField2Impl[A, B]
}
