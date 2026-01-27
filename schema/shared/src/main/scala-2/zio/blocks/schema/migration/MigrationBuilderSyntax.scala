package zio.blocks.schema.migration

import scala.language.experimental.macros
import scala.language.implicitConversions
import zio.blocks.schema._
import TypeLevel._

/**
 * Selector syntax for MigrationBuilder. Methods that accept selector functions
 * like `_.field` instead of manually constructing DynamicOptic instances.
 *
 * In Scala 2, the Handled and Provided type parameters are TList types that
 * accumulate field names at compile time (analogous to Scala 3 Tuples).
 */
final class MigrationBuilderSyntax[A, B, Handled <: TList, Provided <: TList](
  val builder: MigrationBuilder[A, B, Handled, Provided]
) extends AnyVal {

  /**
   * Adds a field to a record with a default value using selector syntax.
   * Adds the target field name to Provided.
   */
  def addField(
    target: B => Any,
    default: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, _ <: TList] =
    macro MigrationBuilderMacrosImpl.addFieldImpl[A, B, Handled, Provided]

  /**
   * Removes a field from a record using selector syntax.
   * Adds the source field name to Handled.
   */
  def dropField(
    source: A => Any,
    defaultForReverse: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, _ <: TList, Provided] =
    macro MigrationBuilderMacrosImpl.dropFieldImpl[A, B, Handled, Provided]

  /**
   * Renames a field in a record using selector syntax.
   * Adds source to Handled and target to Provided.
   */
  def renameField(
    from: A => Any,
    to: B => Any
  ): MigrationBuilder[A, B, _ <: TList, _ <: TList] =
    macro MigrationBuilderMacrosImpl.renameFieldImpl[A, B, Handled, Provided]

  /**
   * Applies a transformation expression to a field value using selector syntax.
   * Adds the field name to both Handled and Provided.
   */
  def transformField(
    at: A => Any,
    transform: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, _ <: TList, _ <: TList] =
    macro MigrationBuilderMacrosImpl.transformFieldImpl[A, B, Handled, Provided]

  /**
   * Unwraps an Option field using selector syntax, using default for None
   * values. Adds the field name to both Handled and Provided.
   */
  def mandateField(
    at: A => Any,
    default: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, _ <: TList, _ <: TList] =
    macro MigrationBuilderMacrosImpl.mandateFieldImpl[A, B, Handled, Provided]

  /**
   * Wraps a field value in Option (as Some) using selector syntax.
   * Adds the field name to both Handled and Provided.
   */
  def optionalizeField(
    at: A => Any,
    defaultForReverse: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, _ <: TList, _ <: TList] =
    macro MigrationBuilderMacrosImpl.optionalizeFieldImpl[A, B, Handled, Provided]

  /**
   * Converts a field from one primitive type to another using selector syntax.
   * Adds the field name to both Handled and Provided.
   */
  def changeFieldType(
    at: A => Any,
    converter: PrimitiveConverter
  ): MigrationBuilder[A, B, _ <: TList, _ <: TList] =
    macro MigrationBuilderMacrosImpl.changeFieldTypeImpl[A, B, Handled, Provided]

  /**
   * Joins multiple source fields into a single target field using selector
   * syntax. Adds target to Provided.
   */
  def joinFields(
    target: B => Any,
    sourcePaths: Seq[A => Any],
    combiner: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, _ <: TList] =
    macro MigrationBuilderMacrosImpl.joinFieldsImpl[A, B, Handled, Provided]

  /**
   * Splits a single source field into multiple target fields using selector
   * syntax. Adds source to Handled.
   */
  def splitField(
    source: A => Any,
    targetPaths: Seq[B => Any],
    splitter: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, _ <: TList, Provided] =
    macro MigrationBuilderMacrosImpl.splitFieldImpl[A, B, Handled, Provided]

  /**
   * Applies a transformation to all elements in a sequence using selector
   * syntax. Does not affect field tracking.
   */
  def transformElements(
    at: A => Any,
    transform: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, Provided] =
    macro MigrationBuilderMacrosImpl.transformElementsImpl[A, B, Handled, Provided]

  /**
   * Applies a transformation to all keys in a map using selector syntax.
   * Does not affect field tracking.
   */
  def transformKeys(
    at: A => Any,
    transform: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, Provided] =
    macro MigrationBuilderMacrosImpl.transformKeysImpl[A, B, Handled, Provided]

  /**
   * Applies a transformation to all values in a map using selector syntax.
   * Does not affect field tracking.
   */
  def transformValues(
    at: A => Any,
    transform: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, Provided] =
    macro MigrationBuilderMacrosImpl.transformValuesImpl[A, B, Handled, Provided]

  /**
   * Renames a variant case using selector syntax.
   * Does not affect field tracking.
   */
  def renameCase(
    from: A => Any,
    to: String
  ): MigrationBuilder[A, B, Handled, Provided] =
    macro MigrationBuilderMacrosImpl.renameCaseImpl[A, B, Handled, Provided]

  /**
   * Applies nested migration actions to a specific variant case using selector
   * syntax. Does not affect field tracking.
   */
  def transformCase(
    at: A => Any
  )(
    nestedActions: MigrationBuilder[A, A, TNil, TNil] => MigrationBuilder[A, A, _ <: TList, _ <: TList]
  ): MigrationBuilder[A, B, Handled, Provided] =
    macro MigrationBuilderMacrosImpl.transformCaseImpl[A, B, Handled, Provided]
}

object MigrationBuilderSyntax {
  implicit def toSyntax[A, B, Handled <: TList, Provided <: TList](
    builder: MigrationBuilder[A, B, Handled, Provided]
  ): MigrationBuilderSyntax[A, B, Handled, Provided] =
    new MigrationBuilderSyntax[A, B, Handled, Provided](builder)
}

// Macro implementations for Scala 2 selector syntax with type tracking.
private[migration] object MigrationBuilderMacrosImpl {
  import scala.reflect.macros.whitebox

  /**
   * Extracts the field name from a selector and returns it as a literal type.
   */
  private def extractFieldNameFromSelector(c: whitebox.Context)(selector: c.Tree): String = {
    import c.universe._

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    =>
        c.abort(c.enclosingPosition, s"Expected a lambda expression, got '$tree'")
    }

    def extractLastFieldName(tree: c.Tree): String = tree match {
      case Select(_, fieldName) => fieldName.decodedName.toString
      case Typed(expr, _)       => extractLastFieldName(expr)
      case _: Ident             =>
        c.abort(c.enclosingPosition, "Selector must access a field")
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Unsupported selector pattern: '$tree'. Only simple field access is supported"
        )
    }

    val pathBody = toPathBody(selector)
    extractLastFieldName(pathBody)
  }

  /**
   * Creates a literal singleton type for a string.
   */
  private def literalType(c: whitebox.Context)(value: String): c.Type = {
    import c.universe._
    c.internal.constantType(Constant(value))
  }

  /**
   * Creates the TList Append result type: Append[L, X]#Out
   */
  private def appendType(c: whitebox.Context)(listType: c.Type, elemType: c.Type): c.Type = {
    import c.universe._
    val appendTpe  = typeOf[Append[_, _]].typeConstructor
    val appliedTpe = appliedType(appendTpe, List(listType, elemType))
    appliedTpe.member(TypeName("Out")).asType.toType.asSeenFrom(appliedTpe, appliedTpe.typeSymbol)
  }

  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    target: c.Expr[B => Any],
    default: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Tree = {
    import c.universe._

    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => b
      case _                 => c.abort(c.enclosingPosition, "Could not extract builder from prefix")
    }

    val fieldName     = extractFieldNameFromSelector(c)(target.tree)
    val optic         = MigrationBuilderMacros.extractOptic[B, Any](c)(target)
    val fieldNameType = literalType(c)(fieldName)

    val handledType    = weakTypeOf[Handled]
    val providedType   = weakTypeOf[Provided]
    val newProvidedTpe = appendType(c)(providedType, fieldNameType)

    val aType = weakTypeOf[A]
    val bType = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, handledType, newProvidedTpe)
    )

    q"""$builder.addField($optic, $default).asInstanceOf[$resultType]"""
  }

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    source: c.Expr[A => Any],
    defaultForReverse: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Tree = {
    import c.universe._

    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => b
      case _                 => c.abort(c.enclosingPosition, "Could not extract builder from prefix")
    }

    val fieldName     = extractFieldNameFromSelector(c)(source.tree)
    val optic         = MigrationBuilderMacros.extractOptic[A, Any](c)(source)
    val fieldNameType = literalType(c)(fieldName)

    val handledType   = weakTypeOf[Handled]
    val providedType  = weakTypeOf[Provided]
    val newHandledTpe = appendType(c)(handledType, fieldNameType)

    val aType = weakTypeOf[A]
    val bType = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, newHandledTpe, providedType)
    )

    q"""$builder.dropField($optic, $defaultForReverse).asInstanceOf[$resultType]"""
  }

  def renameFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any]
  ): c.Tree = {
    import c.universe._

    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => b
      case _                 => c.abort(c.enclosingPosition, "Could not extract builder from prefix")
    }

    val fromFieldName = extractFieldNameFromSelector(c)(from.tree)
    val toFieldName   = extractFieldNameFromSelector(c)(to.tree)
    val fromOptic     = MigrationBuilderMacros.extractOptic[A, Any](c)(from)
    val toNameExpr    = MigrationBuilderMacros.extractFieldName[B, Any](c)(to)

    val fromFieldNameType = literalType(c)(fromFieldName)
    val toFieldNameType   = literalType(c)(toFieldName)

    val handledType    = weakTypeOf[Handled]
    val providedType   = weakTypeOf[Provided]
    val newHandledTpe  = appendType(c)(handledType, fromFieldNameType)
    val newProvidedTpe = appendType(c)(providedType, toFieldNameType)

    val aType = weakTypeOf[A]
    val bType = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, newHandledTpe, newProvidedTpe)
    )

    q"""$builder.renameField($fromOptic, $toNameExpr).asInstanceOf[$resultType]"""
  }

  def transformFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Any],
    transform: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Tree = {
    import c.universe._

    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => b
      case _                 => c.abort(c.enclosingPosition, "Could not extract builder from prefix")
    }

    val fieldName     = extractFieldNameFromSelector(c)(at.tree)
    val optic         = MigrationBuilderMacros.extractOptic[A, Any](c)(at)
    val fieldNameType = literalType(c)(fieldName)

    val handledType    = weakTypeOf[Handled]
    val providedType   = weakTypeOf[Provided]
    val newHandledTpe  = appendType(c)(handledType, fieldNameType)
    val newProvidedTpe = appendType(c)(providedType, fieldNameType)

    val aType = weakTypeOf[A]
    val bType = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, newHandledTpe, newProvidedTpe)
    )

    q"""$builder.transformField($optic, $transform).asInstanceOf[$resultType]"""
  }

  def mandateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Any],
    default: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Tree = {
    import c.universe._

    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => b
      case _                 => c.abort(c.enclosingPosition, "Could not extract builder from prefix")
    }

    val fieldName     = extractFieldNameFromSelector(c)(at.tree)
    val optic         = MigrationBuilderMacros.extractOptic[A, Any](c)(at)
    val fieldNameType = literalType(c)(fieldName)

    val handledType    = weakTypeOf[Handled]
    val providedType   = weakTypeOf[Provided]
    val newHandledTpe  = appendType(c)(handledType, fieldNameType)
    val newProvidedTpe = appendType(c)(providedType, fieldNameType)

    val aType = weakTypeOf[A]
    val bType = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, newHandledTpe, newProvidedTpe)
    )

    q"""$builder.mandateField($optic, $default).asInstanceOf[$resultType]"""
  }

  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Any],
    defaultForReverse: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Tree = {
    import c.universe._

    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => b
      case _                 => c.abort(c.enclosingPosition, "Could not extract builder from prefix")
    }

    val fieldName     = extractFieldNameFromSelector(c)(at.tree)
    val optic         = MigrationBuilderMacros.extractOptic[A, Any](c)(at)
    val fieldNameType = literalType(c)(fieldName)

    val handledType    = weakTypeOf[Handled]
    val providedType   = weakTypeOf[Provided]
    val newHandledTpe  = appendType(c)(handledType, fieldNameType)
    val newProvidedTpe = appendType(c)(providedType, fieldNameType)

    val aType = weakTypeOf[A]
    val bType = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, newHandledTpe, newProvidedTpe)
    )

    q"""$builder.optionalizeField($optic, $defaultForReverse).asInstanceOf[$resultType]"""
  }

  def changeFieldTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Any],
    converter: c.Expr[PrimitiveConverter]
  ): c.Tree = {
    import c.universe._

    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => b
      case _                 => c.abort(c.enclosingPosition, "Could not extract builder from prefix")
    }

    val fieldName     = extractFieldNameFromSelector(c)(at.tree)
    val optic         = MigrationBuilderMacros.extractOptic[A, Any](c)(at)
    val fieldNameType = literalType(c)(fieldName)

    val handledType    = weakTypeOf[Handled]
    val providedType   = weakTypeOf[Provided]
    val newHandledTpe  = appendType(c)(handledType, fieldNameType)
    val newProvidedTpe = appendType(c)(providedType, fieldNameType)

    val aType = weakTypeOf[A]
    val bType = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, newHandledTpe, newProvidedTpe)
    )

    q"""$builder.changeFieldType($optic, $converter).asInstanceOf[$resultType]"""
  }

  def joinFieldsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    target: c.Expr[B => Any],
    sourcePaths: c.Expr[Seq[A => Any]],
    combiner: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Tree = {
    import c.universe._

    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => b
      case _                 => c.abort(c.enclosingPosition, "Could not extract builder from prefix")
    }

    val targetFieldName = extractFieldNameFromSelector(c)(target.tree)
    val targetOptic     = MigrationBuilderMacros.extractOptic[B, Any](c)(target)
    val sourceOptics    = MigrationBuilderMacros.extractOptics[A, Any](c)(sourcePaths)
    val fieldNameType   = literalType(c)(targetFieldName)

    val handledType    = weakTypeOf[Handled]
    val providedType   = weakTypeOf[Provided]
    val newProvidedTpe = appendType(c)(providedType, fieldNameType)

    val aType = weakTypeOf[A]
    val bType = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, handledType, newProvidedTpe)
    )

    q"""$builder.joinFields($targetOptic, $sourceOptics, $combiner).asInstanceOf[$resultType]"""
  }

  def splitFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    source: c.Expr[A => Any],
    targetPaths: c.Expr[Seq[B => Any]],
    splitter: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Tree = {
    import c.universe._

    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => b
      case _                 => c.abort(c.enclosingPosition, "Could not extract builder from prefix")
    }

    val sourceFieldName = extractFieldNameFromSelector(c)(source.tree)
    val sourceOptic     = MigrationBuilderMacros.extractOptic[A, Any](c)(source)
    val targetOptics    = MigrationBuilderMacros.extractOptics[B, Any](c)(targetPaths)
    val fieldNameType   = literalType(c)(sourceFieldName)

    val handledType   = weakTypeOf[Handled]
    val providedType  = weakTypeOf[Provided]
    val newHandledTpe = appendType(c)(handledType, fieldNameType)

    val aType = weakTypeOf[A]
    val bType = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, newHandledTpe, providedType)
    )

    q"""$builder.splitField($sourceOptic, $targetOptics, $splitter).asInstanceOf[$resultType]"""
  }

  def transformElementsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Any],
    transform: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Tree = {
    import c.universe._

    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => b
      case _                 => c.abort(c.enclosingPosition, "Could not extract builder from prefix")
    }

    val optic = MigrationBuilderMacros.extractOptic[A, Any](c)(at)

    val handledType  = weakTypeOf[Handled]
    val providedType = weakTypeOf[Provided]
    val aType        = weakTypeOf[A]
    val bType        = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, handledType, providedType)
    )

    q"""$builder.transformElements($optic, $transform).asInstanceOf[$resultType]"""
  }

  def transformKeysImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Any],
    transform: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Tree = {
    import c.universe._

    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => b
      case _                 => c.abort(c.enclosingPosition, "Could not extract builder from prefix")
    }

    val optic = MigrationBuilderMacros.extractOptic[A, Any](c)(at)

    val handledType  = weakTypeOf[Handled]
    val providedType = weakTypeOf[Provided]
    val aType        = weakTypeOf[A]
    val bType        = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, handledType, providedType)
    )

    q"""$builder.transformKeys($optic, $transform).asInstanceOf[$resultType]"""
  }

  def transformValuesImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Any],
    transform: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Tree = {
    import c.universe._

    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => b
      case _                 => c.abort(c.enclosingPosition, "Could not extract builder from prefix")
    }

    val optic = MigrationBuilderMacros.extractOptic[A, Any](c)(at)

    val handledType  = weakTypeOf[Handled]
    val providedType = weakTypeOf[Provided]
    val aType        = weakTypeOf[A]
    val bType        = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, handledType, providedType)
    )

    q"""$builder.transformValues($optic, $transform).asInstanceOf[$resultType]"""
  }

  def renameCaseImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    from: c.Expr[A => Any],
    to: c.Expr[String]
  ): c.Tree = {
    import c.universe._

    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => b
      case _                 => c.abort(c.enclosingPosition, "Could not extract builder from prefix")
    }

    val (fromOptic, fromCaseName) = MigrationBuilderMacros.extractCaseSelector[A, Any](c)(from)

    val handledType  = weakTypeOf[Handled]
    val providedType = weakTypeOf[Provided]
    val aType        = weakTypeOf[A]
    val bType        = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, handledType, providedType)
    )

    q"""$builder.renameCase($fromOptic, $fromCaseName, $to).asInstanceOf[$resultType]"""
  }

  def transformCaseImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Any]
  )(
    nestedActions: c.Expr[MigrationBuilder[A, A, TNil, TNil] => MigrationBuilder[A, A, _ <: TList, _ <: TList]]
  ): c.Tree = {
    import c.universe._

    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => b
      case _                 => c.abort(c.enclosingPosition, "Could not extract builder from prefix")
    }

    val (atOptic, caseName) = MigrationBuilderMacros.extractCaseSelector[A, Any](c)(at)

    val handledType  = weakTypeOf[Handled]
    val providedType = weakTypeOf[Provided]
    val aType        = weakTypeOf[A]
    val bType        = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, handledType, providedType)
    )

    q"""{
      val sourceSchema = $builder.sourceSchema
      val emptyBuilder: _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $aType, _root_.zio.blocks.schema.migration.TypeLevel.TNil, _root_.zio.blocks.schema.migration.TypeLevel.TNil] =
        _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $aType, _root_.zio.blocks.schema.migration.TypeLevel.TNil, _root_.zio.blocks.schema.migration.TypeLevel.TNil](sourceSchema, sourceSchema, _root_.scala.Vector.empty)
      val transformedBuilder = $nestedActions.apply(emptyBuilder)
      $builder.transformCase($atOptic, $caseName, transformedBuilder.actions).asInstanceOf[$resultType]
    }"""
  }
}
