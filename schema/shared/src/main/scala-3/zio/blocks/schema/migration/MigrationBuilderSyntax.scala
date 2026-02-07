package zio.blocks.schema.migration

import zio.blocks.schema._
import scala.annotation.compileTimeOnly

/**
 * Scala 3 extension methods for [[MigrationBuilder]] that provide type-safe
 * selector syntax.
 *
 * These extensions allow using lambda expressions like `_.fieldName` instead of
 * constructing [[DynamicOptic]] paths manually.
 *
 * Usage:
 * {{{
 * import MigrationBuilderSyntax._
 *
 * MigrationBuilder[PersonV1, PersonV2]
 *   .addField(_.age, 0)
 *   .renameField(_.firstName, _.givenName)
 *   .dropField(_.middleName)
 *   .build
 * }}}
 */
object MigrationBuilderSyntax {

  // ==================== Selector-only Syntax ====================
  //
  // These extension methods exist solely to support the selector syntax
  // consumed by `SelectorMacros`. They intentionally fail if evaluated outside
  // of selector macros.

  extension [A](a: A) {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def when[B <: A]: B = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def wrapped[B]: B = ???
  }

  extension [C[_], A](c: C[A]) {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def at(index: Int): A = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def atIndices(indices: Int*): A = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def each: A = ???
  }

  extension [M[_, _], K, V](m: M[K, V]) {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def atKey(key: K): V = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def atKeys(keys: K*): V = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def eachKey: K = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def eachValue: V = ???
  }

  // ==================== Builder Syntax ====================

  extension [A, B](builder: MigrationBuilder[A, B]) {

    /**
     * Add a field with a type-safe selector and literal default.
     */
    inline def addField[T](
      inline selector: B => T,
      default: T
    )(using Schema[T]): MigrationBuilder[A, B] =
      builder.addField(SelectorMacros.toOptic(selector), default)

    /**
     * Add a field with a type-safe selector and expression default.
     */
    inline def addFieldExpr[T](
      inline selector: B => T,
      default: DynamicSchemaExpr
    ): MigrationBuilder[A, B] =
      builder.addField(SelectorMacros.toOptic(selector), default)

    /**
     * Drop a field using a type-safe selector.
     */
    inline def dropField[T](
      inline selector: A => T
    ): MigrationBuilder[A, B] =
      builder.dropField(SelectorMacros.toOptic(selector))

    /**
     * Drop a field with a default for reverse migration.
     */
    inline def dropField[T](
      inline selector: A => T,
      defaultForReverse: DynamicSchemaExpr
    ): MigrationBuilder[A, B] =
      builder.dropField(SelectorMacros.toOptic(selector), defaultForReverse)

    /**
     * Rename a field using type-safe selectors.
     */
    inline def renameField[T, U](
      inline from: A => T,
      inline to: B => U
    ): MigrationBuilder[A, B] =
      builder.renameField(
        SelectorMacros.toOptic(from),
        SelectorMacros.toOptic(to)
      )

    /**
     * Transform a field using a type-safe selector.
     */
    inline def transformField[T](
      inline selector: A => T,
      transform: DynamicSchemaExpr
    ): MigrationBuilder[A, B] =
      builder.transformField(SelectorMacros.toOptic(selector), transform)

    /**
     * Transform a field with reverse expression.
     */
    inline def transformField[T](
      inline selector: A => T,
      transform: DynamicSchemaExpr,
      reverseTransform: DynamicSchemaExpr
    ): MigrationBuilder[A, B] =
      builder.transformField(
        SelectorMacros.toOptic(selector),
        transform,
        reverseTransform
      )

    /**
     * Make an optional field mandatory with type-safe selector.
     */
    inline def mandateField[T](
      inline selector: B => T,
      default: T
    )(using Schema[T]): MigrationBuilder[A, B] =
      builder.mandateField(SelectorMacros.toOptic(selector), default)

    /**
     * Make an optional field mandatory with expression default.
     */
    inline def mandateFieldExpr[T](
      inline selector: B => T,
      default: DynamicSchemaExpr
    ): MigrationBuilder[A, B] =
      builder.mandateField(SelectorMacros.toOptic(selector), default)

    /**
     * Make a mandatory field optional using a type-safe selector.
     */
    inline def optionalizeField[T](
      inline selector: A => T
    ): MigrationBuilder[A, B] =
      builder.optionalizeField(SelectorMacros.toOptic(selector))

    /**
     * Make a mandatory field optional using a type-safe selector with a reverse
     * default.
     */
    inline def optionalizeFieldExpr[T](
      inline selector: A => T,
      defaultForReverse: DynamicSchemaExpr
    ): MigrationBuilder[A, B] =
      builder.optionalizeField(SelectorMacros.toOptic(selector), defaultForReverse)

    /**
     * Change field type using type-safe selector.
     */
    inline def changeFieldType[T](
      inline selector: A => T,
      converter: DynamicSchemaExpr
    ): MigrationBuilder[A, B] =
      builder.changeFieldType(
        SelectorMacros.toOptic(selector),
        converter
      )

    /**
     * Change field type with converter and reverse converter.
     */
    inline def changeFieldType[T](
      inline selector: A => T,
      converter: DynamicSchemaExpr,
      reverseConverter: DynamicSchemaExpr
    ): MigrationBuilder[A, B] =
      builder.changeFieldType(
        SelectorMacros.toOptic(selector),
        converter,
        reverseConverter
      )

    /**
     * Transform all elements in a sequence field.
     */
    inline def transformElements[T](
      inline selector: A => Seq[T],
      transform: DynamicSchemaExpr
    ): MigrationBuilder[A, B] =
      builder.transformElements(SelectorMacros.toOptic(selector), transform)

    /**
     * Transform all elements with reverse transform.
     */
    inline def transformElements[T](
      inline selector: A => Seq[T],
      transform: DynamicSchemaExpr,
      reverseTransform: DynamicSchemaExpr
    ): MigrationBuilder[A, B] =
      builder.transformElements(
        SelectorMacros.toOptic(selector),
        transform,
        reverseTransform
      )

    /**
     * Transform map keys using type-safe selector.
     */
    inline def transformKeys[K, V](
      inline selector: A => Map[K, V],
      transform: DynamicSchemaExpr
    ): MigrationBuilder[A, B] =
      builder.transformKeys(SelectorMacros.toOptic(selector), transform)

    /**
     * Transform map values using type-safe selector.
     */
    inline def transformValues[K, V](
      inline selector: A => Map[K, V],
      transform: DynamicSchemaExpr
    ): MigrationBuilder[A, B] =
      builder.transformValues(SelectorMacros.toOptic(selector), transform)
  }

  /**
   * Convenient extension for accessing paths object.
   */
  extension (paths: MigrationBuilder.paths.type) {

    /**
     * Create a path from a type-safe selector.
     */
    inline def from[A, B](inline selector: A => B): DynamicOptic =
      SelectorMacros.toOptic(selector)
  }

  // ==================== Tracked Builder ====================

  /**
   * Convert an untracked builder to a compile-time-tracked builder.
   *
   * The tracked builder accumulates field names at the type level and validates
   * migration completeness at compile time via [[MigrationComplete]].
   */
  extension [A, B](builder: MigrationBuilder[A, B]) {
    def tracked: TrackedMigrationBuilder[A, B, EmptyTuple, EmptyTuple] =
      new TrackedMigrationBuilder(builder)
  }

  /**
   * Create a compile-time-tracked migration builder.
   *
   * This is the recommended entry point for migrations that should be validated
   * at compile time. Equivalent to `Migration.newBuilder[A, B].tracked`.
   *
   * Usage:
   * {{{
   * val migration = MigrationBuilderSyntax.checkedBuilder[PersonV1, PersonV2]
   *   .addField(_.email, "unknown@example.com")
   *   .renameField(_.name, _.fullName)
   *   .build  // compile error if incomplete
   * }}}
   */
  def checkedBuilder[A, B](using
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): TrackedMigrationBuilder[A, B, EmptyTuple, EmptyTuple] =
    new TrackedMigrationBuilder(new MigrationBuilder(sourceSchema, targetSchema, Vector.empty))
}
