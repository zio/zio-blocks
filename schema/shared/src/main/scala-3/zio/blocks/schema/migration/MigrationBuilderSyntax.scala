package zio.blocks.schema.migration

import scala.language.implicitConversions
import zio.blocks.schema._

/**
 * Selector syntax for MigrationBuilder. Methods that accept selector functions
 * like `_.field` instead of manually constructing DynamicOptic instances.
 *
 * Each method returns a builder with refined Handled/Provided type parameters
 * that track which fields have been handled (from source) or provided (for
 * target).
 */
extension [A, B, Handled <: Tuple, Provided <: Tuple](builder: MigrationBuilder[A, B, Handled, Provided]) {

  /**
   * Builds the migration with compile-time validation.
   *
   * This method only compiles when the migration is complete:
   *   - All fields removed from source (in A but not B) must be handled
   *   - All fields added to target (in B but not A) must be provided
   *
   * Fields that exist in both schemas are automatically considered
   * handled/provided.
   *
   * @return
   *   A complete, validated Migration[A, B]
   */
  inline def buildChecked(using
    proof: ValidationProof[A, B, Handled, Provided]
  ): Migration[A, B] =
    builder.buildPartial

  /**
   * Adds a field to a record with a default value using selector syntax. Adds
   * the target field name to Provided.
   */
  transparent inline def addField[FieldName <: String & Singleton](
    inline target: B => Any,
    default: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Handled, Tuple.Append[Provided, FieldName]] =
    ${ MigrationBuilderMacrosImpl.addFieldImpl[A, B, Handled, Provided, FieldName]('builder, 'target, 'default) }

  /**
   * Removes a field from a record using selector syntax. Adds the source field
   * name to Handled.
   */
  transparent inline def dropField[FieldName <: String & Singleton](
    inline source: A => Any,
    defaultForReverse: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Provided] =
    ${
      MigrationBuilderMacrosImpl.dropFieldImpl[A, B, Handled, Provided, FieldName](
        'builder,
        'source,
        'defaultForReverse
      )
    }

  /**
   * Renames a field in a record using selector syntax. Adds source to Handled
   * and target to Provided.
   */
  transparent inline def renameField[FromName <: String & Singleton, ToName <: String & Singleton](
    inline from: A => Any,
    inline to: B => Any
  ): MigrationBuilder[A, B, Tuple.Append[Handled, FromName], Tuple.Append[Provided, ToName]] =
    ${ MigrationBuilderMacrosImpl.renameFieldImpl[A, B, Handled, Provided, FromName, ToName]('builder, 'from, 'to) }

  /**
   * Applies a transformation expression to a field value using selector syntax.
   * Adds the field name to both Handled and Provided.
   */
  transparent inline def transformField[FieldName <: String & Singleton](
    inline at: A => Any,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Tuple.Append[Provided, FieldName]] =
    ${ MigrationBuilderMacrosImpl.transformFieldImpl[A, B, Handled, Provided, FieldName]('builder, 'at, 'transform) }

  /**
   * Unwraps an Option field using selector syntax, using default for None
   * values. Adds the field name to both Handled and Provided.
   */
  transparent inline def mandateField[FieldName <: String & Singleton](
    inline at: A => Any,
    default: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Tuple.Append[Provided, FieldName]] =
    ${ MigrationBuilderMacrosImpl.mandateFieldImpl[A, B, Handled, Provided, FieldName]('builder, 'at, 'default) }

  /**
   * Wraps a field value in Option (as Some) using selector syntax. Adds the
   * field name to both Handled and Provided.
   */
  transparent inline def optionalizeField[FieldName <: String & Singleton](
    inline at: A => Any,
    defaultForReverse: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Tuple.Append[Provided, FieldName]] =
    ${
      MigrationBuilderMacrosImpl.optionalizeFieldImpl[A, B, Handled, Provided, FieldName](
        'builder,
        'at,
        'defaultForReverse
      )
    }

  /**
   * Converts a field from one primitive type to another using selector syntax.
   * Adds the field name to both Handled and Provided.
   */
  transparent inline def changeFieldType[FieldName <: String & Singleton](
    inline at: A => Any,
    converter: PrimitiveConverter
  ): MigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Tuple.Append[Provided, FieldName]] =
    ${ MigrationBuilderMacrosImpl.changeFieldTypeImpl[A, B, Handled, Provided, FieldName]('builder, 'at, 'converter) }

  /**
   * Joins multiple source fields into a single target field using selector
   * syntax. Note: Source fields tracking for joinFields is simplified to track
   * the target only. Full multi-field tracking would require variadic type
   * parameters.
   */
  transparent inline def joinFields[TargetName <: String & Singleton](
    inline target: B => Any,
    inline sourcePaths: Seq[A => Any],
    combiner: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Handled, Tuple.Append[Provided, TargetName]] =
    ${
      MigrationBuilderMacrosImpl.joinFieldsImpl[A, B, Handled, Provided, TargetName](
        'builder,
        'target,
        'sourcePaths,
        'combiner
      )
    }

  /**
   * Splits a single source field into multiple target fields using selector
   * syntax. Note: Target fields tracking for splitField is simplified to track
   * the source only. Full multi-field tracking would require variadic type
   * parameters.
   */
  transparent inline def splitField[SourceName <: String & Singleton](
    inline source: A => Any,
    inline targetPaths: Seq[B => Any],
    splitter: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Tuple.Append[Handled, SourceName], Provided] =
    ${
      MigrationBuilderMacrosImpl.splitFieldImpl[A, B, Handled, Provided, SourceName](
        'builder,
        'source,
        'targetPaths,
        'splitter
      )
    }

  /**
   * Applies a transformation to all elements in a sequence using selector
   * syntax. Does not affect field tracking (operates on collection contents,
   * not structure).
   */
  transparent inline def transformElements(
    inline at: A => Any,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Handled, Provided] =
    ${ MigrationBuilderMacrosImpl.transformElementsImpl[A, B, Handled, Provided]('builder, 'at, 'transform) }

  /**
   * Applies a transformation to all keys in a map using selector syntax. Does
   * not affect field tracking (operates on map contents, not structure).
   */
  transparent inline def transformKeys(
    inline at: A => Any,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Handled, Provided] =
    ${ MigrationBuilderMacrosImpl.transformKeysImpl[A, B, Handled, Provided]('builder, 'at, 'transform) }

  /**
   * Applies a transformation to all values in a map using selector syntax. Does
   * not affect field tracking (operates on map contents, not structure).
   */
  transparent inline def transformValues(
    inline at: A => Any,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Handled, Provided] =
    ${ MigrationBuilderMacrosImpl.transformValuesImpl[A, B, Handled, Provided]('builder, 'at, 'transform) }

  /**
   * Renames a variant case using selector syntax. Does not affect field
   * tracking (case-level operation, not field-level).
   */
  transparent inline def renameCase(
    inline from: A => Any,
    to: String
  ): MigrationBuilder[A, B, Handled, Provided] =
    ${ MigrationBuilderMacrosImpl.renameCaseImpl[A, B, Handled, Provided]('builder, 'from, 'to) }

  /**
   * Applies nested migration actions to a specific variant case using selector
   * syntax. Does not affect field tracking (case-level operation, not
   * field-level).
   */
  transparent inline def transformCase(
    inline at: A => Any
  )(
    nestedActions: MigrationBuilder[A, A, EmptyTuple, EmptyTuple] => MigrationBuilder[A, A, ?, ?]
  ): MigrationBuilder[A, B, Handled, Provided] =
    ${ MigrationBuilderMacrosImpl.transformCaseImpl[A, B, Handled, Provided]('builder, 'at, 'nestedActions) }
}

// Macro implementations for Scala 3 selector syntax with type tracking.
private[migration] object MigrationBuilderMacrosImpl {
  import scala.quoted.*

  def addFieldImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    FieldName <: String & Singleton: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    target: Expr[B => Any],
    default: Expr[SchemaExpr[DynamicValue, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Handled, Tuple.Append[Provided, FieldName]]] = {
    import q.reflect.*
    val optic     = MigrationBuilderMacros.extractOptic[B, Any](target)
    val fieldName = extractFieldNameFromSelector(target.asTerm)

    // Create literal type for field name
    val fieldNameType     = ConstantType(StringConstant(fieldName)).asType.asInstanceOf[Type[FieldName]]
    given Type[FieldName] = fieldNameType

    '{
      $builder
        .addField($optic, $default)
        .asInstanceOf[MigrationBuilder[A, B, Handled, Tuple.Append[Provided, FieldName]]]
    }
  }

  def dropFieldImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    FieldName <: String & Singleton: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    source: Expr[A => Any],
    defaultForReverse: Expr[SchemaExpr[DynamicValue, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Provided]] = {
    import q.reflect.*
    val optic     = MigrationBuilderMacros.extractOptic[A, Any](source)
    val fieldName = extractFieldNameFromSelector(source.asTerm)

    // Create literal type for field name
    val fieldNameType     = ConstantType(StringConstant(fieldName)).asType.asInstanceOf[Type[FieldName]]
    given Type[FieldName] = fieldNameType

    '{
      $builder
        .dropField($optic, $defaultForReverse)
        .asInstanceOf[MigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Provided]]
    }
  }

  def renameFieldImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    FromName <: String & Singleton: Type,
    ToName <: String & Singleton: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    from: Expr[A => Any],
    to: Expr[B => Any]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Tuple.Append[Handled, FromName], Tuple.Append[Provided, ToName]]] = {
    import q.reflect.*
    val fromOptic   = MigrationBuilderMacros.extractOptic[A, Any](from)
    val toFieldName = MigrationBuilderMacros.extractFieldName[B, Any](to)
    val fromName    = extractFieldNameFromSelector(from.asTerm)
    val toName      = extractFieldNameFromSelector(to.asTerm)

    // Create literal types for field names
    val fromNameType     = ConstantType(StringConstant(fromName)).asType.asInstanceOf[Type[FromName]]
    val toNameType       = ConstantType(StringConstant(toName)).asType.asInstanceOf[Type[ToName]]
    given Type[FromName] = fromNameType
    given Type[ToName]   = toNameType

    '{
      $builder
        .renameField($fromOptic, $toFieldName)
        .asInstanceOf[MigrationBuilder[A, B, Tuple.Append[Handled, FromName], Tuple.Append[Provided, ToName]]]
    }
  }

  def transformFieldImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    FieldName <: String & Singleton: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any],
    transform: Expr[SchemaExpr[DynamicValue, ?]]
  )(using
    q: Quotes
  ): Expr[MigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Tuple.Append[Provided, FieldName]]] = {
    import q.reflect.*
    val optic     = MigrationBuilderMacros.extractOptic[A, Any](at)
    val fieldName = extractFieldNameFromSelector(at.asTerm)

    // Create literal type for field name
    val fieldNameType     = ConstantType(StringConstant(fieldName)).asType.asInstanceOf[Type[FieldName]]
    given Type[FieldName] = fieldNameType

    '{
      $builder
        .transformField($optic, $transform)
        .asInstanceOf[MigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Tuple.Append[Provided, FieldName]]]
    }
  }

  def mandateFieldImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    FieldName <: String & Singleton: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any],
    default: Expr[SchemaExpr[DynamicValue, ?]]
  )(using
    q: Quotes
  ): Expr[MigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Tuple.Append[Provided, FieldName]]] = {
    import q.reflect.*
    val optic     = MigrationBuilderMacros.extractOptic[A, Any](at)
    val fieldName = extractFieldNameFromSelector(at.asTerm)

    // Create literal type for field name
    val fieldNameType     = ConstantType(StringConstant(fieldName)).asType.asInstanceOf[Type[FieldName]]
    given Type[FieldName] = fieldNameType

    '{
      $builder
        .mandateField($optic, $default)
        .asInstanceOf[MigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Tuple.Append[Provided, FieldName]]]
    }
  }

  def optionalizeFieldImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    FieldName <: String & Singleton: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any],
    defaultForReverse: Expr[SchemaExpr[DynamicValue, ?]]
  )(using
    q: Quotes
  ): Expr[MigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Tuple.Append[Provided, FieldName]]] = {
    import q.reflect.*
    val optic     = MigrationBuilderMacros.extractOptic[A, Any](at)
    val fieldName = extractFieldNameFromSelector(at.asTerm)

    // Create literal type for field name
    val fieldNameType     = ConstantType(StringConstant(fieldName)).asType.asInstanceOf[Type[FieldName]]
    given Type[FieldName] = fieldNameType

    '{
      $builder
        .optionalizeField($optic, $defaultForReverse)
        .asInstanceOf[MigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Tuple.Append[Provided, FieldName]]]
    }
  }

  def changeFieldTypeImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    FieldName <: String & Singleton: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any],
    converter: Expr[PrimitiveConverter]
  )(using
    q: Quotes
  ): Expr[MigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Tuple.Append[Provided, FieldName]]] = {
    import q.reflect.*
    val optic     = MigrationBuilderMacros.extractOptic[A, Any](at)
    val fieldName = extractFieldNameFromSelector(at.asTerm)

    // Create literal type for field name
    val fieldNameType     = ConstantType(StringConstant(fieldName)).asType.asInstanceOf[Type[FieldName]]
    given Type[FieldName] = fieldNameType

    '{
      $builder
        .changeFieldType($optic, $converter)
        .asInstanceOf[MigrationBuilder[A, B, Tuple.Append[Handled, FieldName], Tuple.Append[Provided, FieldName]]]
    }
  }

  def joinFieldsImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    TargetName <: String & Singleton: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    target: Expr[B => Any],
    sourcePaths: Expr[Seq[A => Any]],
    combiner: Expr[SchemaExpr[DynamicValue, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Handled, Tuple.Append[Provided, TargetName]]] = {
    import q.reflect.*
    val targetOptic  = MigrationBuilderMacros.extractOptic[B, Any](target)
    val sourceOptics = MigrationBuilderMacros.extractOptics[A, Any](sourcePaths)
    val targetName   = extractFieldNameFromSelector(target.asTerm)

    // Create literal type for field name
    val targetNameType     = ConstantType(StringConstant(targetName)).asType.asInstanceOf[Type[TargetName]]
    given Type[TargetName] = targetNameType

    '{
      $builder
        .joinFields($targetOptic, $sourceOptics, $combiner)
        .asInstanceOf[MigrationBuilder[A, B, Handled, Tuple.Append[Provided, TargetName]]]
    }
  }

  def splitFieldImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    SourceName <: String & Singleton: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    source: Expr[A => Any],
    targetPaths: Expr[Seq[B => Any]],
    splitter: Expr[SchemaExpr[DynamicValue, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Tuple.Append[Handled, SourceName], Provided]] = {
    import q.reflect.*
    val sourceOptic  = MigrationBuilderMacros.extractOptic[A, Any](source)
    val targetOptics = MigrationBuilderMacros.extractOptics[B, Any](targetPaths)
    val sourceName   = extractFieldNameFromSelector(source.asTerm)

    // Create literal type for field name
    val sourceNameType     = ConstantType(StringConstant(sourceName)).asType.asInstanceOf[Type[SourceName]]
    given Type[SourceName] = sourceNameType

    '{
      $builder
        .splitField($sourceOptic, $targetOptics, $splitter)
        .asInstanceOf[MigrationBuilder[A, B, Tuple.Append[Handled, SourceName], Provided]]
    }
  }

  def transformElementsImpl[A: Type, B: Type, Handled <: Tuple: Type, Provided <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any],
    transform: Expr[SchemaExpr[DynamicValue, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    val optic = MigrationBuilderMacros.extractOptic[A, Any](at)
    '{ $builder.transformElements($optic, $transform) }
  }

  def transformKeysImpl[A: Type, B: Type, Handled <: Tuple: Type, Provided <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any],
    transform: Expr[SchemaExpr[DynamicValue, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    val optic = MigrationBuilderMacros.extractOptic[A, Any](at)
    '{ $builder.transformKeys($optic, $transform) }
  }

  def transformValuesImpl[A: Type, B: Type, Handled <: Tuple: Type, Provided <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any],
    transform: Expr[SchemaExpr[DynamicValue, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    val optic = MigrationBuilderMacros.extractOptic[A, Any](at)
    '{ $builder.transformValues($optic, $transform) }
  }

  def renameCaseImpl[A: Type, B: Type, Handled <: Tuple: Type, Provided <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    from: Expr[A => Any],
    to: Expr[String]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    val (fromOptic, fromCaseName) = MigrationBuilderMacros.extractCaseSelector[A, Any](from)
    '{ $builder.renameCase($fromOptic, $fromCaseName, $to) }
  }

  def transformCaseImpl[A: Type, B: Type, Handled <: Tuple: Type, Provided <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any],
    nestedActions: Expr[MigrationBuilder[A, A, EmptyTuple, EmptyTuple] => MigrationBuilder[A, A, ?, ?]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    val (atOptic, caseName) = MigrationBuilderMacros.extractCaseSelector[A, Any](at)
    '{
      val sourceSchema                                                 = $builder.sourceSchema
      val emptyBuilder: MigrationBuilder[A, A, EmptyTuple, EmptyTuple] =
        MigrationBuilder(sourceSchema, sourceSchema, Vector.empty)
      val transformedBuilder = $nestedActions.apply(emptyBuilder)
      $builder.transformCase($atOptic, $caseName, transformedBuilder.actions)
    }
  }

  // Helper to extract field name from selector expression
  private def extractFieldNameFromSelector(using q: Quotes)(term: q.reflect.Term): String = {
    import q.reflect.*

    def toPathBody(t: Term): Term = t match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               =>
        report.errorAndAbort(s"Expected a lambda expression, got '${t.show}'", t.pos)
    }

    def extractLastFieldName(t: Term): String = t match {
      case Select(_, fieldName) => fieldName
      case Typed(expr, _)       => extractLastFieldName(expr)
      case _: Ident             =>
        report.errorAndAbort("Selector must access a field", t.pos)
      case _ =>
        report.errorAndAbort(
          s"Unsupported selector pattern: '${t.show}'. Only simple field access is supported",
          t.pos
        )
    }

    val pathBody = toPathBody(term)
    extractLastFieldName(pathBody)
  }
}
