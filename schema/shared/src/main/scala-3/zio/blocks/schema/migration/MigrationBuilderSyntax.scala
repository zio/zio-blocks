package zio.blocks.schema.migration

import scala.language.implicitConversions
import zio.blocks.schema._

/**
 * Scala 3 macro-based selector syntax for MigrationBuilder.
 *
 * Provides ergonomic methods that accept selector functions like `_.field`
 * instead of manually constructing DynamicOptic instances.
 */
extension [A, B](builder: MigrationBuilder[A, B]) {

  // ===== Record Operations with Selectors =====

  /**
   * Adds a field to a record with a default value using selector syntax.
   *
   * Example:
   * {{{
   * builder.addField(_.age, SchemaExpr.Literal(0, Schema.int))
   * }}}
   */
  transparent inline def addField(
    inline target: B => Any,
    default: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacrosImpl.addFieldImpl[A, B]('builder, 'target, 'default) }

  /**
   * Removes a field from a record using selector syntax.
   *
   * Example:
   * {{{
   * builder.dropField(_.age, SchemaExpr.Literal(0, Schema.int))
   * }}}
   */
  transparent inline def dropField(
    inline source: A => Any,
    defaultForReverse: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacrosImpl.dropFieldImpl[A, B]('builder, 'source, 'defaultForReverse) }

  /**
   * Renames a field in a record using selector syntax.
   *
   * Example:
   * {{{
   * builder.renameField(_.firstName, _.fullName)
   * }}}
   */
  transparent inline def renameField(
    inline from: A => Any,
    inline to: B => Any
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacrosImpl.renameFieldImpl[A, B]('builder, 'from, 'to) }

  /**
   * Applies a transformation expression to a field value using selector syntax.
   *
   * Example:
   * {{{
   * builder.transformField(_.age, SchemaExpr.Arithmetic(...))
   * }}}
   */
  transparent inline def transformField(
    inline at: A => Any,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacrosImpl.transformFieldImpl[A, B]('builder, 'at, 'transform) }

  /**
   * Unwraps an Option field using selector syntax, using default for None
   * values.
   *
   * Example:
   * {{{
   * builder.mandateField(_.name, SchemaExpr.Literal("Unknown", Schema.string))
   * }}}
   */
  transparent inline def mandateField(
    inline at: A => Any,
    default: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacrosImpl.mandateFieldImpl[A, B]('builder, 'at, 'default) }

  /**
   * Wraps a field value in Option (as Some) using selector syntax.
   *
   * Example:
   * {{{
   * builder.optionalizeField(_.name)
   * }}}
   */
  transparent inline def optionalizeField(
    inline at: A => Any,
    defaultForReverse: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacrosImpl.optionalizeFieldImpl[A, B]('builder, 'at, 'defaultForReverse) }

  /**
   * Converts a field from one primitive type to another using selector syntax.
   *
   * Example:
   * {{{
   * builder.changeFieldType(_.age, PrimitiveConverter.StringToInt)
   * }}}
   */
  transparent inline def changeFieldType(
    inline at: A => Any,
    converter: PrimitiveConverter
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacrosImpl.changeFieldTypeImpl[A, B]('builder, 'at, 'converter) }

  // ===== Multi-Field Operations with Selectors =====

  /**
   * Joins multiple source fields into a single target field using selector
   * syntax.
   *
   * Example:
   * {{{
   * builder.joinFields(
   *   _.fullName,
   *   Vector(_.firstName, _.lastName),
   *   SchemaExpr.StringConcat(...)
   * )
   * }}}
   */
  transparent inline def joinFields(
    inline target: B => Any,
    inline sourcePaths: Seq[A => Any],
    combiner: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacrosImpl.joinFieldsImpl[A, B]('builder, 'target, 'sourcePaths, 'combiner) }

  /**
   * Splits a single source field into multiple target fields using selector
   * syntax.
   *
   * Example:
   * {{{
   * builder.splitField(
   *   _.fullName,
   *   Vector(_.firstName, _.lastName),
   *   SchemaExpr.StringSplit(...)
   * )
   * }}}
   */
  transparent inline def splitField(
    inline source: A => Any,
    inline targetPaths: Seq[B => Any],
    splitter: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacrosImpl.splitFieldImpl[A, B]('builder, 'source, 'targetPaths, 'splitter) }

  // ===== Collection Operations with Selectors =====

  /**
   * Applies a transformation to all elements in a sequence using selector
   * syntax.
   *
   * Example:
   * {{{
   * builder.transformElements(_.items, SchemaExpr.Arithmetic(...))
   * }}}
   */
  transparent inline def transformElements(
    inline at: A => Any,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacrosImpl.transformElementsImpl[A, B]('builder, 'at, 'transform) }

  /**
   * Applies a transformation to all keys in a map using selector syntax.
   *
   * Example:
   * {{{
   * builder.transformKeys(_.data, SchemaExpr.StringUppercase(...))
   * }}}
   */
  transparent inline def transformKeys(
    inline at: A => Any,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacrosImpl.transformKeysImpl[A, B]('builder, 'at, 'transform) }

  /**
   * Applies a transformation to all values in a map using selector syntax.
   *
   * Example:
   * {{{
   * builder.transformValues(_.data, SchemaExpr.Arithmetic(...))
   * }}}
   */
  transparent inline def transformValues(
    inline at: A => Any,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacrosImpl.transformValuesImpl[A, B]('builder, 'at, 'transform) }

  // ===== Enum Operations with Selectors =====

  /**
   * Renames a variant case using selector syntax.
   *
   * Example:
   * {{{
   * builder.renameCase(_.when[OldCaseName], "NewCaseName")
   * }}}
   */
  transparent inline def renameCase(
    inline from: A => Any,
    to: String
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacrosImpl.renameCaseImpl[A, B]('builder, 'from, 'to) }

  /**
   * Applies nested migration actions to a specific variant case using selector
   * syntax.
   *
   * Example:
   * {{{
   * builder.transformCase(_.when[CreditCard])(
   *   _.renameField(_.cardNumber, _.number)
   * )
   * }}}
   */
  transparent inline def transformCase(
    inline at: A => Any
  )(nestedActions: MigrationBuilder[A, A] => MigrationBuilder[A, A]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacrosImpl.transformCaseImpl[A, B]('builder, 'at, 'nestedActions) }
}

/**
 * Companion object for Scala 2/3 compatibility. In Scala 3, extensions are
 * automatically available. This object exists only to allow
 * `import MigrationBuilderSyntax._` in shared code.
 */
object MigrationBuilderSyntax

/**
 * Macro implementations for Scala 3 selector syntax.
 */
private[migration] object MigrationBuilderMacrosImpl {
  import scala.quoted.*

  def addFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    target: Expr[B => Any],
    default: Expr[SchemaExpr[DynamicValue, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = MigrationBuilderMacros.extractOptic[B, Any](target)
    '{ $builder.addField($optic, $default) }
  }

  def dropFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Any],
    defaultForReverse: Expr[SchemaExpr[DynamicValue, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = MigrationBuilderMacros.extractOptic[A, Any](source)
    '{ $builder.dropField($optic, $defaultForReverse) }
  }

  def renameFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    from: Expr[A => Any],
    to: Expr[B => Any]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val fromOptic   = MigrationBuilderMacros.extractOptic[A, Any](from)
    val toFieldName = MigrationBuilderMacros.extractFieldName[B, Any](to)
    '{ $builder.renameField($fromOptic, $toFieldName) }
  }

  def transformFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Any],
    transform: Expr[SchemaExpr[DynamicValue, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = MigrationBuilderMacros.extractOptic[A, Any](at)
    '{ $builder.transformField($optic, $transform) }
  }

  def mandateFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Any],
    default: Expr[SchemaExpr[DynamicValue, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = MigrationBuilderMacros.extractOptic[A, Any](at)
    '{ $builder.mandateField($optic, $default) }
  }

  def optionalizeFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Any],
    defaultForReverse: Expr[SchemaExpr[DynamicValue, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = MigrationBuilderMacros.extractOptic[A, Any](at)
    '{ $builder.optionalizeField($optic, $defaultForReverse) }
  }

  def changeFieldTypeImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Any],
    converter: Expr[PrimitiveConverter]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = MigrationBuilderMacros.extractOptic[A, Any](at)
    '{ $builder.changeFieldType($optic, $converter) }
  }

  def joinFieldsImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    target: Expr[B => Any],
    sourcePaths: Expr[Seq[A => Any]],
    combiner: Expr[SchemaExpr[DynamicValue, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val targetOptic  = MigrationBuilderMacros.extractOptic[B, Any](target)
    val sourceOptics = MigrationBuilderMacros.extractOptics[A, Any](sourcePaths)
    '{ $builder.joinFields($targetOptic, $sourceOptics, $combiner) }
  }

  def splitFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Any],
    targetPaths: Expr[Seq[B => Any]],
    splitter: Expr[SchemaExpr[DynamicValue, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val sourceOptic  = MigrationBuilderMacros.extractOptic[A, Any](source)
    val targetOptics = MigrationBuilderMacros.extractOptics[B, Any](targetPaths)
    '{ $builder.splitField($sourceOptic, $targetOptics, $splitter) }
  }

  def transformElementsImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Any],
    transform: Expr[SchemaExpr[DynamicValue, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = MigrationBuilderMacros.extractOptic[A, Any](at)
    '{ $builder.transformElements($optic, $transform) }
  }

  def transformKeysImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Any],
    transform: Expr[SchemaExpr[DynamicValue, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = MigrationBuilderMacros.extractOptic[A, Any](at)
    '{ $builder.transformKeys($optic, $transform) }
  }

  def transformValuesImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Any],
    transform: Expr[SchemaExpr[DynamicValue, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = MigrationBuilderMacros.extractOptic[A, Any](at)
    '{ $builder.transformValues($optic, $transform) }
  }

  def renameCaseImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    from: Expr[A => Any],
    to: Expr[String]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val (fromOptic, fromCaseName) = MigrationBuilderMacros.extractCaseSelector[A, Any](from)
    '{ $builder.renameCase($fromOptic, $fromCaseName, $to) }
  }

  def transformCaseImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Any],
    nestedActions: Expr[MigrationBuilder[A, A] => MigrationBuilder[A, A]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val (atOptic, caseName) = MigrationBuilderMacros.extractCaseSelector[A, Any](at)
    '{
      val sourceSchema       = $builder.sourceSchema
      val emptyBuilder       = MigrationBuilder(sourceSchema, sourceSchema, Vector.empty)
      val transformedBuilder = $nestedActions.apply(emptyBuilder)
      $builder.transformCase($atOptic, $caseName, transformedBuilder.actions)
    }
  }
}
