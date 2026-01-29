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
  def build: Migration[A, B] = macro MigrationBuilderMacrosImpl.buildImpl[A, B, Handled, Provided]

  /**
   * Adds a field to a record with a default value using selector syntax. Adds
   * the target field name to Provided.
   */
  def addField(
    target: B => Any,
    default: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, _ <: TList] =
    macro MigrationBuilderMacrosImpl.addFieldImpl[A, B, Handled, Provided]

  /**
   * Removes a field from a record using selector syntax. Adds the source field
   * name to Handled.
   */
  def dropField(
    source: A => Any,
    defaultForReverse: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, _ <: TList, Provided] =
    macro MigrationBuilderMacrosImpl.dropFieldImpl[A, B, Handled, Provided]

  /**
   * Renames a field in a record using selector syntax. Adds source to Handled
   * and target to Provided.
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
   * Wraps a field value in Option (as Some) using selector syntax. Adds the
   * field name to both Handled and Provided.
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
   * syntax. All source fields must share a common parent path.
   *
   * Adds all source field names to Handled and the target field name to
   * Provided.
   */
  def joinFields(
    target: B => Any,
    sourcePaths: Seq[A => Any],
    combiner: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, _ <: TList, _ <: TList] =
    macro MigrationBuilderMacrosImpl.joinFieldsImpl[A, B, Handled, Provided]

  /**
   * Splits a single source field into multiple target fields using selector
   * syntax. All target fields must share a common parent path.
   *
   * Adds the source field name to Handled and all target field names to
   * Provided.
   */
  def splitField(
    source: A => Any,
    targetPaths: Seq[B => Any],
    splitter: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, _ <: TList, _ <: TList] =
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
   * Applies a transformation to all keys in a map using selector syntax. Does
   * not affect field tracking.
   */
  def transformKeys(
    at: A => Any,
    transform: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, Provided] =
    macro MigrationBuilderMacrosImpl.transformKeysImpl[A, B, Handled, Provided]

  /**
   * Applies a transformation to all values in a map using selector syntax. Does
   * not affect field tracking.
   */
  def transformValues(
    at: A => Any,
    transform: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, Provided] =
    macro MigrationBuilderMacrosImpl.transformValuesImpl[A, B, Handled, Provided]

  /**
   * Renames a variant case using selector syntax. Adds source case to Handled
   * and target case to Provided (both prefixed with "case:").
   */
  def renameCase(
    from: A => Any,
    to: String
  ): MigrationBuilder[A, B, _ <: TList, _ <: TList] =
    macro MigrationBuilderMacrosImpl.renameCaseImpl[A, B, Handled, Provided]

  /**
   * Applies nested migration actions to a specific variant case using selector
   * syntax. Adds case name to both Handled and Provided (prefixed with
   * "case:").
   */
  def transformCase(
    at: A => Any
  )(
    nestedActions: MigrationBuilder[A, A, TNil, TNil] => MigrationBuilder[A, A, _ <: TList, _ <: TList]
  ): MigrationBuilder[A, B, _ <: TList, _ <: TList] =
    macro MigrationBuilderMacrosImpl.transformCaseImpl[A, B, Handled, Provided]
}

object MigrationBuilderSyntax {
  implicit def toSyntax[A, B, Handled <: TList, Provided <: TList](
    builder: MigrationBuilder[A, B, Handled, Provided]
  ): MigrationBuilderSyntax[A, B, Handled, Provided] =
    new MigrationBuilderSyntax[A, B, Handled, Provided](builder)

  /**
   * Validate a migration with detailed error messages.
   *
   * This macro provides helpful compile-time error messages when validation
   * fails, including specific field paths and case names that need handling or
   * providing.
   *
   * Use this when you want clear feedback about what's missing in a migration
   * without needing to build the migration first.
   *
   * @tparam A
   *   Source type
   * @tparam B
   *   Target type
   * @tparam Handled
   *   Fields/cases that have been handled
   * @tparam Provided
   *   Fields/cases that have been provided
   * @return
   *   Unit if validation passes, compile error otherwise
   */
  def requireValidation[A, B, Handled <: TList, Provided <: TList]: Unit =
    macro MigrationBuilderMacrosImpl.requireValidationImpl[A, B, Handled, Provided]
}

// Macro implementations for Scala 2 selector syntax with type tracking.
private[migration] object MigrationBuilderMacrosImpl {
  import scala.reflect.macros.whitebox

  // ==========================================================================
  // build macro implementation
  // ==========================================================================

  /**
   * Compile-time validation for migration completeness.
   *
   * This macro:
   *   1. Extracts full nested field paths from A and B using FieldPaths logic
   *   2. Extracts case names from A and B using CasePaths logic
   *   3. Extracts field/case names from Handled and Provided TList types
   *   4. Computes which paths are removed (in A but not B) and added (in B but
   *      not A)
   *   5. Verifies removed ⊆ handled and added ⊆ provided
   *   6. Emits compile error if validation fails with detailed hints
   */
  def buildImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  ): c.Tree = {
    val helper = new MigrationBuilderMacrosHelper[c.type](c)
    helper.buildImpl[A, B, Handled, Provided]
  }

  // ==========================================================================
  // requireValidation macro implementation
  // ==========================================================================

  /**
   * Compile-time validation with detailed error messages.
   *
   * This macro validates that:
   *   1. All removed field paths (in A but not B) are in Handled
   *   2. All added field paths (in B but not A) are in Provided
   *   3. All removed case names (in A but not B) are in Handled
   *   4. All added case names (in B but not A) are in Provided
   *
   * Produces detailed error messages showing exactly what's missing.
   */
  def requireValidationImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  ): c.Tree = {
    val helper = new MigrationBuilderMacrosHelper[c.type](c)
    helper.requireValidationImpl[A, B, Handled, Provided]
  }

  // ==========================================================================
  // Field extraction helpers
  // ==========================================================================

  /**
   * Extracts the full field path from a selector and returns it as a
   * dot-separated string. For example, `_.address.city` returns "address.city",
   * and `_.name` returns "name".
   */
  private def extractFieldNameFromSelector(c: whitebox.Context)(selector: c.Tree): String = {
    import c.universe._

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    =>
        c.abort(c.enclosingPosition, s"Expected a lambda expression, got '$tree'")
    }

    def extractFullPath(tree: c.Tree): List[String] = {
      def loop(t: c.Tree, acc: List[String]): List[String] = t match {
        case Select(parent, fieldName) =>
          loop(parent, fieldName.decodedName.toString :: acc)
        case Typed(expr, _) =>
          loop(expr, acc)
        case _: Ident =>
          acc
        case _ =>
          c.abort(
            c.enclosingPosition,
            s"Unsupported selector pattern: '$t'. Only simple field access is supported"
          )
      }
      loop(tree, Nil)
    }

    val pathBody  = toPathBody(selector)
    val pathParts = extractFullPath(pathBody)

    if (pathParts.isEmpty) {
      c.abort(c.enclosingPosition, "Selector must access a field")
    }

    pathParts.mkString(".")
  }

  /**
   * Extracts all field names from a Seq of selector expressions.
   *
   * For `Seq(_.firstName, _.lastName)`, returns
   * `List("firstName", "lastName")`. For nested selectors like
   * `Seq(_.person.firstName, _.person.lastName)`, returns
   * `List("person.firstName", "person.lastName")`.
   */
  private def extractFieldNamesFromSeq(c: whitebox.Context)(seqExpr: c.Tree): List[String] = {
    import c.universe._

    def unwrap(tree: c.Tree): c.Tree = tree match {
      case Typed(inner, _) => unwrap(inner)
      case Block(_, expr)  => unwrap(expr)
      case _               => tree
    }

    def extractSelectors(tree: c.Tree): List[c.Tree] = unwrap(tree) match {
      // Seq(a, b, c) or Vector(a, b, c) etc.
      case Apply(_, args) =>
        args
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Expected a Seq/Vector/List literal (e.g., Seq(_.field1, _.field2)), got: ${showRaw(tree)}"
        )
    }

    val selectors = extractSelectors(seqExpr)
    selectors.map(extractFieldNameFromSelector(c)(_))
  }

  /**
   * Creates a literal singleton type for a string.
   */
  private def literalType(c: whitebox.Context)(value: String): c.Type = {
    import c.universe._
    c.internal.constantType(Constant(value))
  }

  /**
   * Computes the concrete result type for appending element X to list L.
   *
   * Instead of creating Append[L, X]#Out (a type projection), this directly
   * computes the concrete result type:
   *   - Append[TNil, X] = X :: TNil
   *   - Append[H :: T, X] = H :: Append[T, X]
   */
  private def appendType(c: whitebox.Context)(listType: c.Type, elemType: c.Type): c.Type = {
    import c.universe._

    val tnilTpe  = typeOf[TNil]
    val tconsSym = symbolOf[TCons[_, _]]
    val tconsTpe = tconsSym.toType.typeConstructor

    def loop(lt: c.Type): c.Type = {
      val dealiased = lt.dealias
      if (dealiased =:= tnilTpe) {
        // Append[TNil, X]#Out = X :: TNil
        appliedType(tconsTpe, List(elemType, tnilTpe))
      } else if (dealiased.typeSymbol == tconsSym) {
        // Append[H :: T, X]#Out = H :: Append[T, X]#Out
        val args = dealiased.typeArgs
        val h    = args(0)
        val t    = args(1)
        appliedType(tconsTpe, List(h, loop(t)))
      } else {
        c.abort(c.enclosingPosition, s"Cannot compute Append for non-TList type: $lt")
      }
    }

    loop(listType)
  }

  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    target: c.Expr[B => Any],
    default: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Tree = {
    import c.universe._

    val builder = q"${c.prefix}.builder"

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

    val builder = q"${c.prefix}.builder"

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

    val builder = q"${c.prefix}.builder"

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

    val builder = q"${c.prefix}.builder"

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

    val builder = q"${c.prefix}.builder"

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

    val builder = q"${c.prefix}.builder"

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

    val builder = q"${c.prefix}.builder"

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

    val builder = q"${c.prefix}.builder"

    val targetFieldName = extractFieldNameFromSelector(c)(target.tree)
    val targetOptic     = MigrationBuilderMacros.extractOptic[B, Any](c)(target)
    val sourceOptics    = MigrationBuilderMacros.extractOptics[A, Any](c)(sourcePaths)

    // Extract all source field names from the Seq
    val sourceFieldNames = extractFieldNamesFromSeq(c)(sourcePaths.tree)

    if (sourceFieldNames.isEmpty) {
      c.abort(c.enclosingPosition, "joinFields requires at least one source field")
    }

    val handledType  = weakTypeOf[Handled]
    val providedType = weakTypeOf[Provided]

    // Build new Handled type by appending all source field names
    val newHandledTpe = sourceFieldNames.foldLeft(handledType) { (acc, name) =>
      appendType(c)(acc, literalType(c)(name))
    }

    // Build new Provided type by appending the target field name
    val newProvidedTpe = appendType(c)(providedType, literalType(c)(targetFieldName))

    val aType = weakTypeOf[A]
    val bType = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, newHandledTpe, newProvidedTpe)
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

    val builder = q"${c.prefix}.builder"

    val sourceFieldName = extractFieldNameFromSelector(c)(source.tree)
    val sourceOptic     = MigrationBuilderMacros.extractOptic[A, Any](c)(source)
    val targetOptics    = MigrationBuilderMacros.extractOptics[B, Any](c)(targetPaths)

    // Extract all target field names from the Seq
    val targetFieldNames = extractFieldNamesFromSeq(c)(targetPaths.tree)

    if (targetFieldNames.isEmpty) {
      c.abort(c.enclosingPosition, "splitField requires at least one target field")
    }

    val handledType  = weakTypeOf[Handled]
    val providedType = weakTypeOf[Provided]

    // Build new Handled type by appending the source field name
    val newHandledTpe = appendType(c)(handledType, literalType(c)(sourceFieldName))

    // Build new Provided type by appending all target field names
    val newProvidedTpe = targetFieldNames.foldLeft(providedType) { (acc, name) =>
      appendType(c)(acc, literalType(c)(name))
    }

    val aType = weakTypeOf[A]
    val bType = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, newHandledTpe, newProvidedTpe)
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

    val builder = q"${c.prefix}.builder"

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

    val builder = q"${c.prefix}.builder"

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

    val builder = q"${c.prefix}.builder"

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

    val builder = q"${c.prefix}.builder"

    val (fromOptic, fromCaseName) = MigrationBuilderMacros.extractCaseSelector[A, Any](c)(from)

    // Extract the literal string value for the target case name
    val toCaseNameStr = to.tree match {
      case Literal(Constant(s: String)) => s
      case _                            => c.abort(c.enclosingPosition, "Target case name must be a string literal")
    }

    // Track cases in Handled/Provided with "case:" prefix
    val fromCaseNameStr = fromCaseName.tree match {
      case Literal(Constant(s: String)) => s"case:$s"
      case _                            => c.abort(c.enclosingPosition, "Could not extract source case name")
    }
    val toCaseNamePrefixed = s"case:$toCaseNameStr"

    val fromCaseType = literalType(c)(fromCaseNameStr)
    val toCaseType   = literalType(c)(toCaseNamePrefixed)

    val handledType    = weakTypeOf[Handled]
    val providedType   = weakTypeOf[Provided]
    val newHandledTpe  = appendType(c)(handledType, fromCaseType)
    val newProvidedTpe = appendType(c)(providedType, toCaseType)

    val aType = weakTypeOf[A]
    val bType = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, newHandledTpe, newProvidedTpe)
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

    val builder = q"${c.prefix}.builder"

    val (atOptic, caseName) = MigrationBuilderMacros.extractCaseSelector[A, Any](c)(at)

    // Track case in both Handled and Provided with "case:" prefix
    val caseNameStr = caseName.tree match {
      case Literal(Constant(s: String)) => s"case:$s"
      case _                            => c.abort(c.enclosingPosition, "Could not extract case name")
    }

    val caseNameType = literalType(c)(caseNameStr)

    val handledType    = weakTypeOf[Handled]
    val providedType   = weakTypeOf[Provided]
    val newHandledTpe  = appendType(c)(handledType, caseNameType)
    val newProvidedTpe = appendType(c)(providedType, caseNameType)

    val aType = weakTypeOf[A]
    val bType = weakTypeOf[B]

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(aType, bType, newHandledTpe, newProvidedTpe)
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

/**
 * Helper class that mixes in MacroHelpers trait for path-dependent type safety.
 */
private[migration] class MigrationBuilderMacrosHelper[C <: scala.reflect.macros.whitebox.Context](val c: C)
    extends MacroHelpers {
  import c.universe._

  def buildImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag]: c.Tree = {
    val builder = q"${c.prefix}.builder"

    val aType        = weakTypeOf[A]
    val bType        = weakTypeOf[B]
    val handledType  = weakTypeOf[Handled]
    val providedType = weakTypeOf[Provided]

    // Extract full nested field paths from A and B (using FieldPaths logic)
    val pathsA = extractFieldPathsFromType(aType, "", Set.empty).sorted
    val pathsB = extractFieldPathsFromType(bType, "", Set.empty).sorted

    // Extract case names from A and B (using CasePaths logic)
    val casesA = extractCaseNamesFromType(aType).sorted.map(name => s"case:$name")
    val casesB = extractCaseNamesFromType(bType).sorted.map(name => s"case:$name")

    // Combine paths and cases for validation
    val allPathsA = pathsA ++ casesA
    val allPathsB = pathsB ++ casesB

    // Extract field names from TList types
    val handled  = extractTListElements(handledType)
    val provided = extractTListElements(providedType)

    // Compute what needs to be handled/provided
    val unchanged       = allPathsA.intersect(allPathsB)
    val removed         = allPathsA.diff(unchanged)
    val added           = allPathsB.diff(unchanged)
    val missingHandled  = removed.diff(handled)
    val missingProvided = added.diff(provided)

    if (missingHandled.nonEmpty || missingProvided.nonEmpty) {
      val (unhandledPaths, unhandledCases)   = missingHandled.partition(!_.startsWith("case:"))
      val (unprovidedPaths, unprovidedCases) = missingProvided.partition(!_.startsWith("case:"))

      val sb = new StringBuilder
      sb.append(s"Migration validation failed for ${aType.typeSymbol.name} => ${bType.typeSymbol.name}:\n")

      if (unhandledPaths.nonEmpty) {
        sb.append("\nUnhandled paths from source (need dropField or renameField):\n")
        unhandledPaths.sorted.foreach(p => sb.append(s"  - $p\n"))
      }

      if (unprovidedPaths.nonEmpty) {
        sb.append("\nUnprovided paths for target (need addField or renameField):\n")
        unprovidedPaths.sorted.foreach(p => sb.append(s"  - $p\n"))
      }

      if (unhandledCases.nonEmpty) {
        sb.append("\nUnhandled cases from source (need renameCase or transformCase):\n")
        unhandledCases.sorted.map(_.stripPrefix("case:")).foreach(cn => sb.append(s"  - $cn\n"))
      }

      if (unprovidedCases.nonEmpty) {
        sb.append("\nUnprovided cases for target (need renameCase):\n")
        unprovidedCases.sorted.map(_.stripPrefix("case:")).foreach(cn => sb.append(s"  - $cn\n"))
      }

      // Add hints with example paths
      sb.append("\n")
      if (unhandledPaths.nonEmpty) {
        val example      = unhandledPaths.head
        val selectorPath = example.split("\\.").mkString(".")
        sb.append(s"Hint: Use .dropField(_.$selectorPath, default) to handle removed fields\n")
      }
      if (unprovidedPaths.nonEmpty) {
        val example      = unprovidedPaths.head
        val selectorPath = example.split("\\.").mkString(".")
        sb.append(s"Hint: Use .addField(_.$selectorPath, default) to provide new fields\n")
      }
      if (unhandledPaths.nonEmpty && unprovidedPaths.nonEmpty) {
        sb.append("Hint: Use .renameField(_.oldPath, _.newPath) when a field was renamed\n")
      }
      if (unhandledCases.nonEmpty || unprovidedCases.nonEmpty) {
        sb.append("Hint: Use .renameCase(_.when[OldCase], \"NewCase\") when a case was renamed\n")
      }

      c.abort(c.enclosingPosition, sb.toString)
    }

    q"$builder.buildPartial"
  }

  def requireValidationImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag]
    : c.Tree = {
    val aType        = weakTypeOf[A]
    val bType        = weakTypeOf[B]
    val handledType  = weakTypeOf[Handled]
    val providedType = weakTypeOf[Provided]

    // Extract full nested field paths from A and B
    val pathsA = extractFieldPathsFromType(aType, "", Set.empty).sorted
    val pathsB = extractFieldPathsFromType(bType, "", Set.empty).sorted

    // Extract case names from A and B
    val casesA = extractCaseNamesFromType(aType).sorted.map(name => s"case:$name")
    val casesB = extractCaseNamesFromType(bType).sorted.map(name => s"case:$name")

    // Combine paths and cases for validation
    val allPathsA = pathsA ++ casesA
    val allPathsB = pathsB ++ casesB

    // Extract field names from TList types
    val handled  = extractTListElements(handledType)
    val provided = extractTListElements(providedType)

    // Compute what needs to be handled/provided
    val unchanged       = allPathsA.intersect(allPathsB)
    val removed         = allPathsA.diff(unchanged)
    val added           = allPathsB.diff(unchanged)
    val missingHandled  = removed.diff(handled)
    val missingProvided = added.diff(provided)

    if (missingHandled.nonEmpty || missingProvided.nonEmpty) {
      val (unhandledPaths, unhandledCases)   = missingHandled.partition(!_.startsWith("case:"))
      val (unprovidedPaths, unprovidedCases) = missingProvided.partition(!_.startsWith("case:"))

      val sb = new StringBuilder
      sb.append(s"Migration validation failed for ${aType.typeSymbol.name} => ${bType.typeSymbol.name}:\n")

      if (unhandledPaths.nonEmpty) {
        sb.append("\nUnhandled paths from source (need dropField or renameField):\n")
        unhandledPaths.sorted.foreach(p => sb.append(s"  - $p\n"))
      }

      if (unprovidedPaths.nonEmpty) {
        sb.append("\nUnprovided paths for target (need addField or renameField):\n")
        unprovidedPaths.sorted.foreach(p => sb.append(s"  - $p\n"))
      }

      if (unhandledCases.nonEmpty) {
        sb.append("\nUnhandled cases from source (need renameCase or transformCase):\n")
        unhandledCases.sorted.map(_.stripPrefix("case:")).foreach(cn => sb.append(s"  - $cn\n"))
      }

      if (unprovidedCases.nonEmpty) {
        sb.append("\nUnprovided cases for target (need renameCase):\n")
        unprovidedCases.sorted.map(_.stripPrefix("case:")).foreach(cn => sb.append(s"  - $cn\n"))
      }

      // Add hints
      sb.append("\n")
      if (unhandledPaths.nonEmpty) {
        val example      = unhandledPaths.head
        val selectorPath = example.split("\\.").mkString(".")
        sb.append(s"Hint: Use .dropField(_.$selectorPath, default) to handle removed fields\n")
      }
      if (unprovidedPaths.nonEmpty) {
        val example      = unprovidedPaths.head
        val selectorPath = example.split("\\.").mkString(".")
        sb.append(s"Hint: Use .addField(_.$selectorPath, default) to provide new fields\n")
      }
      if (unhandledPaths.nonEmpty && unprovidedPaths.nonEmpty) {
        sb.append("Hint: Use .renameField(_.oldPath, _.newPath) when a field was renamed\n")
      }
      if (unhandledCases.nonEmpty || unprovidedCases.nonEmpty) {
        sb.append("Hint: Use .renameCase(_.when[OldCase], \"NewCase\") when a case was renamed\n")
      }

      c.abort(c.enclosingPosition, sb.toString)
    }

    q"()"
  }

  /**
   * Extracts field name strings from a TList type.
   *
   * Given a type like "a" :: "b" :: TNil, returns List("a", "b").
   */
  private def extractTListElements(tpe: c.Type): List[String] = {
    val tlistSym = typeOf[TList].typeSymbol
    val tnilSym  = typeOf[TNil].typeSymbol
    val tconsSym = typeOf[TCons[_, _]].typeSymbol

    def loop(t: c.Type): List[String] = {
      val dealiased = t.dealias
      val sym       = dealiased.typeSymbol

      if (sym == tnilSym) {
        Nil
      } else if (sym == tconsSym) {
        // TCons[H, T] - extract H and recurse on T
        val args = dealiased.typeArgs
        if (args.size != 2) {
          c.abort(c.enclosingPosition, s"Invalid TCons type: $dealiased")
        }
        val head = args(0)
        val tail = args(1)

        // Extract string literal from head type
        val headStr = head.dealias match {
          case ConstantType(Constant(s: String)) => s
          case other                             =>
            c.abort(c.enclosingPosition, s"Expected string literal type in TList, got: $other")
        }

        headStr :: loop(tail)
      } else if (sym == tlistSym) {
        // Generic TList - cannot extract elements
        Nil
      } else {
        c.abort(c.enclosingPosition, s"Unexpected type in TList position: $dealiased (symbol: $sym)")
      }
    }

    loop(tpe)
  }
}
