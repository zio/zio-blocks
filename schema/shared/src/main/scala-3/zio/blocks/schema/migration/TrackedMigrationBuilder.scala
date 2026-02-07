package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A compile-time-tracked migration builder for Scala 3.
 *
 * This wraps a [[MigrationBuilder]] and adds type-level field name tracking
 * using Tuple types. Each call to `addField`, `dropField`, or `renameField`
 * appends the field name to the appropriate tuple type parameter.
 *
 * At `.build` time, the compiler requires [[MigrationComplete]] evidence, which
 * uses a macro to verify that all source fields are handled and all target
 * fields are provided. If the migration is incomplete, a compile-time error is
 * produced with hints about which fields need attention.
 *
 * For methods that don't change field names (transformField, mandateField,
 * optionalizeField, etc.), the field is assumed auto-mapped by same name.
 *
 * Usage:
 * {{{
 * import MigrationBuilderSyntax._
 *
 * Migration.checkedBuilder[PersonV1, PersonV2]
 *   .addField(_.email, "unknown@example.com")
 *   .dropField(_.ssn)
 *   .renameField(_.firstName, _.givenName)
 *   .build  // compile error if incomplete
 * }}}
 *
 * @tparam A
 *   source type
 * @tparam B
 *   target type
 * @tparam SH
 *   Tuple of source field name literal types that have been explicitly handled
 * @tparam TP
 *   Tuple of target field name literal types that have been explicitly provided
 */
final class TrackedMigrationBuilder[A, B, SH <: Tuple, TP <: Tuple](
  private[migration] val inner: MigrationBuilder[A, B]
) {

  // ==================== Tracked Operations (transparent inline) ====================

  /** Add a field with a literal default. Tracks the target field name. */
  transparent inline def addField[T](
    inline selector: B => T,
    default: T
  )(using schema: Schema[T]): TrackedMigrationBuilder[A, B, SH, ?] =
    ${ TrackedMigrationBuilderMacros.addFieldImpl[A, B, SH, TP, T]('this, 'selector, 'default, 'schema) }

  /** Add a field with an expression default. Tracks the target field name. */
  transparent inline def addFieldExpr[T](
    inline selector: B => T,
    default: DynamicSchemaExpr
  ): TrackedMigrationBuilder[A, B, SH, ?] =
    ${ TrackedMigrationBuilderMacros.addFieldExprImpl[A, B, SH, TP, T]('this, 'selector, 'default) }

  /** Drop a field from the source. Tracks the source field name. */
  transparent inline def dropField[T](
    inline selector: A => T
  ): TrackedMigrationBuilder[A, B, ?, TP] =
    ${ TrackedMigrationBuilderMacros.dropFieldImpl[A, B, SH, TP, T]('this, 'selector) }

  /** Drop a field with a reverse default. Tracks the source field name. */
  transparent inline def dropField[T](
    inline selector: A => T,
    defaultForReverse: DynamicSchemaExpr
  ): TrackedMigrationBuilder[A, B, ?, TP] =
    ${ TrackedMigrationBuilderMacros.dropFieldWithReverseImpl[A, B, SH, TP, T]('this, 'selector, 'defaultForReverse) }

  /** Rename a field. Tracks both source (handled) and target (provided). */
  transparent inline def renameField[T, U](
    inline from: A => T,
    inline to: B => U
  ): TrackedMigrationBuilder[A, B, ?, ?] =
    ${ TrackedMigrationBuilderMacros.renameFieldImpl[A, B, SH, TP]('this, 'from, 'to) }

  // ==================== Non-tracked Operations (auto-mapped by same name) ====================

  /** Transform a field value (field name unchanged, auto-mapped). */
  inline def transformField[T](
    inline selector: A => T,
    transform: DynamicSchemaExpr
  ): TrackedMigrationBuilder[A, B, SH, TP] = {
    val optic = SelectorMacros.toOptic(selector)
    new TrackedMigrationBuilder(inner.transformField(optic, transform))
  }

  /** Transform a field with reverse expression. */
  inline def transformField[T](
    inline selector: A => T,
    transform: DynamicSchemaExpr,
    reverseTransform: DynamicSchemaExpr
  ): TrackedMigrationBuilder[A, B, SH, TP] = {
    val optic = SelectorMacros.toOptic(selector)
    new TrackedMigrationBuilder(inner.transformField(optic, transform, reverseTransform))
  }

  /** Make an optional field mandatory (field name unchanged). */
  inline def mandateField[T](
    inline selector: B => T,
    default: T
  )(using schema: Schema[T]): TrackedMigrationBuilder[A, B, SH, TP] = {
    val optic = SelectorMacros.toOptic(selector)
    new TrackedMigrationBuilder(inner.mandateField(optic, default)(schema))
  }

  /** Make a mandatory field optional (field name unchanged). */
  inline def optionalizeField[T](
    inline selector: A => T
  ): TrackedMigrationBuilder[A, B, SH, TP] = {
    val optic = SelectorMacros.toOptic(selector)
    new TrackedMigrationBuilder(inner.optionalizeField(optic))
  }

  /** Change field type (field name unchanged). */
  inline def changeFieldType[T](
    inline selector: A => T,
    converter: DynamicSchemaExpr
  ): TrackedMigrationBuilder[A, B, SH, TP] = {
    val optic = SelectorMacros.toOptic(selector)
    new TrackedMigrationBuilder(inner.changeFieldType(optic, converter))
  }

  /** Rename an enum case. */
  def renameCase(from: String, to: String): TrackedMigrationBuilder[A, B, SH, TP] =
    new TrackedMigrationBuilder(inner.renameCase(from, to))

  /** Transform elements in a collection field. */
  inline def transformElements[T](
    inline selector: A => Seq[T],
    transform: DynamicSchemaExpr
  ): TrackedMigrationBuilder[A, B, SH, TP] = {
    val optic = SelectorMacros.toOptic(selector)
    new TrackedMigrationBuilder(inner.transformElements(optic, transform))
  }

  /** Transform map keys. */
  inline def transformKeys[K, V](
    inline selector: A => Map[K, V],
    transform: DynamicSchemaExpr
  ): TrackedMigrationBuilder[A, B, SH, TP] = {
    val optic = SelectorMacros.toOptic(selector)
    new TrackedMigrationBuilder(inner.transformKeys(optic, transform))
  }

  /** Transform map values. */
  inline def transformValues[K, V](
    inline selector: A => Map[K, V],
    transform: DynamicSchemaExpr
  ): TrackedMigrationBuilder[A, B, SH, TP] = {
    val optic = SelectorMacros.toOptic(selector)
    new TrackedMigrationBuilder(inner.transformValues(optic, transform))
  }

  // ==================== Build Methods ====================

  /**
   * Build with compile-time field validation AND runtime structural validation.
   *
   * Requires [[MigrationComplete]] evidence, which the compiler generates via
   * macro. If any source fields are unhandled or target fields unprovided, a
   * compile error is produced.
   *
   * Also runs [[MigrationValidator]] at runtime for structural schema checks.
   */
  inline def build(using MigrationComplete[A, B, SH, TP]): Migration[A, B] =
    inner.build

  /**
   * Build with compile-time field validation only (skip runtime validation).
   *
   * Useful when runtime validation is too restrictive or for testing.
   */
  inline def buildChecked(using MigrationComplete[A, B, SH, TP]): Migration[A, B] =
    inner.buildPartial

  /**
   * Build without any compile-time validation.
   *
   * Also skips runtime validation. Use for partial migrations.
   */
  def buildPartial: Migration[A, B] =
    inner.buildPartial
}
