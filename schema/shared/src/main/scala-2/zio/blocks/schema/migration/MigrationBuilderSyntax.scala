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
   * Extracts the full field path from a selector and returns it as a list of
   * field names. For example, `_.address.city` returns List("address", "city"),
   * and `_.name` returns List("name").
   */
  private def extractFieldNamesFromSelector(c: whitebox.Context)(selector: c.Tree): List[String] = {
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

    pathParts
  }

  /**
   * Extracts all field paths from a Seq of selector expressions.
   *
   * For `Seq(_.firstName, _.lastName)`, returns
   * `List(List("firstName"), List("lastName"))`. For nested selectors like
   * `Seq(_.person.firstName, _.person.lastName)`, returns
   * `List(List("person", "firstName"), List("person", "lastName"))`.
   */
  private def extractFieldPathsFromSeq(c: whitebox.Context)(seqExpr: c.Tree): List[List[String]] = {
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
    selectors.map(extractFieldNamesFromSelector(c)(_))
  }

  /**
   * Creates a structured path tuple type from a list of field names. Each field
   * becomes a ("field", "name") tuple segment.
   *
   * List("address", "city") -> (("field", "address"), ("field", "city"))
   */
  private def fieldPathToTupleType(c: whitebox.Context)(fieldNames: List[String]): c.Type = {
    import c.universe._

    if (fieldNames.isEmpty) {
      typeOf[Unit] // EmptyTuple equivalent in Scala 2
    } else {
      // Build tuple type for each field segment
      val tuple2Type = typeOf[(_, _)].typeConstructor
      val fieldLit   = c.internal.constantType(Constant("field"))

      // Create segment types: ("field", "fieldName") for each field
      val segmentTypes = fieldNames.map { name =>
        val nameLit = c.internal.constantType(Constant(name))
        appliedType(tuple2Type, List(fieldLit, nameLit))
      }

      // Build nested tuple from right to left
      segmentTypes.reduceRight { (seg, acc) =>
        appliedType(tuple2Type, List(seg, acc))
      }
    }
  }

  /**
   * Creates a structured path tuple type for a case name. The case becomes a
   * (("case", "name"),) single-element tuple.
   *
   * "Success" -> (("case", "Success"),)
   */
  private def casePathToTupleType(c: whitebox.Context)(caseName: String): c.Type = {
    import c.universe._

    val tuple2Type = typeOf[(_, _)].typeConstructor
    val caseLit    = c.internal.constantType(Constant("case"))
    val nameLit    = c.internal.constantType(Constant(caseName))

    // Create ("case", caseName) tuple
    val segmentType = appliedType(tuple2Type, List(caseLit, nameLit))

    // Wrap in single-element tuple: (segment,) which is just Tuple1
    val tuple1Type = typeOf[Tuple1[_]].typeConstructor
    appliedType(tuple1Type, List(segmentType))
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

    val fieldNames    = extractFieldNamesFromSelector(c)(target.tree)
    val optic         = MigrationBuilderMacros.extractOptic[B, Any](c)(target)
    val fieldPathType = fieldPathToTupleType(c)(fieldNames)

    val handledType    = weakTypeOf[Handled]
    val providedType   = weakTypeOf[Provided]
    val newProvidedTpe = appendType(c)(providedType, fieldPathType)

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

    val fieldNames    = extractFieldNamesFromSelector(c)(source.tree)
    val optic         = MigrationBuilderMacros.extractOptic[A, Any](c)(source)
    val fieldPathType = fieldPathToTupleType(c)(fieldNames)

    val handledType   = weakTypeOf[Handled]
    val providedType  = weakTypeOf[Provided]
    val newHandledTpe = appendType(c)(handledType, fieldPathType)

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

    val fromFieldNames = extractFieldNamesFromSelector(c)(from.tree)
    val toFieldNames   = extractFieldNamesFromSelector(c)(to.tree)
    val fromOptic      = MigrationBuilderMacros.extractOptic[A, Any](c)(from)
    val toNameExpr     = MigrationBuilderMacros.extractFieldName[B, Any](c)(to)

    val fromFieldPathType = fieldPathToTupleType(c)(fromFieldNames)
    val toFieldPathType   = fieldPathToTupleType(c)(toFieldNames)

    val handledType    = weakTypeOf[Handled]
    val providedType   = weakTypeOf[Provided]
    val newHandledTpe  = appendType(c)(handledType, fromFieldPathType)
    val newProvidedTpe = appendType(c)(providedType, toFieldPathType)

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

    val fieldNames    = extractFieldNamesFromSelector(c)(at.tree)
    val optic         = MigrationBuilderMacros.extractOptic[A, Any](c)(at)
    val fieldPathType = fieldPathToTupleType(c)(fieldNames)

    val handledType    = weakTypeOf[Handled]
    val providedType   = weakTypeOf[Provided]
    val newHandledTpe  = appendType(c)(handledType, fieldPathType)
    val newProvidedTpe = appendType(c)(providedType, fieldPathType)

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

    val fieldNames    = extractFieldNamesFromSelector(c)(at.tree)
    val optic         = MigrationBuilderMacros.extractOptic[A, Any](c)(at)
    val fieldPathType = fieldPathToTupleType(c)(fieldNames)

    val handledType    = weakTypeOf[Handled]
    val providedType   = weakTypeOf[Provided]
    val newHandledTpe  = appendType(c)(handledType, fieldPathType)
    val newProvidedTpe = appendType(c)(providedType, fieldPathType)

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

    val fieldNames    = extractFieldNamesFromSelector(c)(at.tree)
    val optic         = MigrationBuilderMacros.extractOptic[A, Any](c)(at)
    val fieldPathType = fieldPathToTupleType(c)(fieldNames)

    val handledType    = weakTypeOf[Handled]
    val providedType   = weakTypeOf[Provided]
    val newHandledTpe  = appendType(c)(handledType, fieldPathType)
    val newProvidedTpe = appendType(c)(providedType, fieldPathType)

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

    val fieldNames    = extractFieldNamesFromSelector(c)(at.tree)
    val optic         = MigrationBuilderMacros.extractOptic[A, Any](c)(at)
    val fieldPathType = fieldPathToTupleType(c)(fieldNames)

    val handledType    = weakTypeOf[Handled]
    val providedType   = weakTypeOf[Provided]
    val newHandledTpe  = appendType(c)(handledType, fieldPathType)
    val newProvidedTpe = appendType(c)(providedType, fieldPathType)

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

    val targetFieldNames = extractFieldNamesFromSelector(c)(target.tree)
    val targetOptic      = MigrationBuilderMacros.extractOptic[B, Any](c)(target)
    val sourceOptics     = MigrationBuilderMacros.extractOptics[A, Any](c)(sourcePaths)

    // Extract all source field paths from the Seq
    val sourceFieldPaths = extractFieldPathsFromSeq(c)(sourcePaths.tree)

    if (sourceFieldPaths.isEmpty) {
      c.abort(c.enclosingPosition, "joinFields requires at least one source field")
    }

    val handledType  = weakTypeOf[Handled]
    val providedType = weakTypeOf[Provided]

    // Build new Handled type by appending all source field path tuples
    val newHandledTpe = sourceFieldPaths.foldLeft(handledType) { (acc, fieldNames) =>
      appendType(c)(acc, fieldPathToTupleType(c)(fieldNames))
    }

    // Build new Provided type by appending the target field path tuple
    val newProvidedTpe = appendType(c)(providedType, fieldPathToTupleType(c)(targetFieldNames))

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

    val sourceFieldNames = extractFieldNamesFromSelector(c)(source.tree)
    val sourceOptic      = MigrationBuilderMacros.extractOptic[A, Any](c)(source)
    val targetOptics     = MigrationBuilderMacros.extractOptics[B, Any](c)(targetPaths)

    // Extract all target field paths from the Seq
    val targetFieldPaths = extractFieldPathsFromSeq(c)(targetPaths.tree)

    if (targetFieldPaths.isEmpty) {
      c.abort(c.enclosingPosition, "splitField requires at least one target field")
    }

    val handledType  = weakTypeOf[Handled]
    val providedType = weakTypeOf[Provided]

    // Build new Handled type by appending the source field path tuple
    val newHandledTpe = appendType(c)(handledType, fieldPathToTupleType(c)(sourceFieldNames))

    // Build new Provided type by appending all target field path tuples
    val newProvidedTpe = targetFieldPaths.foldLeft(providedType) { (acc, fieldNames) =>
      appendType(c)(acc, fieldPathToTupleType(c)(fieldNames))
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

    // Extract source case name for tracking
    val fromCaseNameStr = fromCaseName.tree match {
      case Literal(Constant(s: String)) => s
      case _                            => c.abort(c.enclosingPosition, "Could not extract source case name")
    }

    // Track cases with structured tuples
    val fromCasePathType = casePathToTupleType(c)(fromCaseNameStr)
    val toCasePathType   = casePathToTupleType(c)(toCaseNameStr)

    val handledType    = weakTypeOf[Handled]
    val providedType   = weakTypeOf[Provided]
    val newHandledTpe  = appendType(c)(handledType, fromCasePathType)
    val newProvidedTpe = appendType(c)(providedType, toCasePathType)

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

    // Extract case name for tracking
    val caseNameStr = caseName.tree match {
      case Literal(Constant(s: String)) => s
      case _                            => c.abort(c.enclosingPosition, "Could not extract case name")
    }

    // Track case with structured tuple
    val casePathType = casePathToTupleType(c)(caseNameStr)

    val handledType    = weakTypeOf[Handled]
    val providedType   = weakTypeOf[Provided]
    val newHandledTpe  = appendType(c)(handledType, casePathType)
    val newProvidedTpe = appendType(c)(providedType, casePathType)

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

    // Extract shape trees for both types
    val treeA = extractShapeTree(aType, Set.empty, "Migration validation")
    val treeB = extractShapeTree(bType, Set.empty, "Migration validation")

    // Compute diff using TreeDiff
    val (removedPaths, addedPaths) = TreeDiff.diff(treeA, treeB)

    // Convert paths to flat strings for validation
    val removed = removedPaths.map(ShapeExtraction.MigrationPaths.pathToFlatString).sorted
    val added   = addedPaths.map(ShapeExtraction.MigrationPaths.pathToFlatString).sorted

    // Extract field names from TList types
    val handled  = extractTListElements(handledType)
    val provided = extractTListElements(providedType)

    // Check what's missing
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

    // Extract shape trees for both types
    val treeA = extractShapeTree(aType, Set.empty, "Migration validation")
    val treeB = extractShapeTree(bType, Set.empty, "Migration validation")

    // Compute diff using TreeDiff
    val (removedPaths, addedPaths) = TreeDiff.diff(treeA, treeB)

    // Convert paths to flat strings for validation
    val removed = removedPaths.map(ShapeExtraction.MigrationPaths.pathToFlatString).sorted
    val added   = addedPaths.map(ShapeExtraction.MigrationPaths.pathToFlatString).sorted

    // Extract field names from TList types
    val handled  = extractTListElements(handledType)
    val provided = extractTListElements(providedType)

    // Check what's missing
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
   * Handles both old flat string format ("a.b") and new structured tuple format
   * ((("field", "a"), ("field", "b"))).
   *
   * Given a type like (("field", "a"), ("field", "b")) :: TNil, returns
   * List("a.b").
   */
  private def extractTListElements(tpe: c.Type): List[String] = {
    val tlistSym  = typeOf[TList].typeSymbol
    val tnilSym   = typeOf[TNil].typeSymbol
    val tconsSym  = typeOf[TCons[_, _]].typeSymbol
    val tuple2Sym = typeOf[(_, _)].typeSymbol
    val tuple1Sym = typeOf[Tuple1[_]].typeSymbol

    /**
     * Extract a segment string from a segment type. ("field", "name") -> "name"
     * ("case", "name") -> "case:name"
     */
    def segmentToString(segType: c.Type): Option[String] = {
      val dealiased = segType.dealias
      dealiased match {
        // Tuple2 segment: ("field", "name") or ("case", "name")
        case t if t.typeSymbol == tuple2Sym =>
          val args = t.typeArgs
          if (args.size == 2) {
            (args(0).dealias, args(1).dealias) match {
              case (ConstantType(Constant("field")), ConstantType(Constant(name: String))) =>
                Some(name)
              case (ConstantType(Constant("case")), ConstantType(Constant(name: String))) =>
                Some(s"case:$name")
              case _ => None
            }
          } else None
        // Single string segment: "element", "key", "value", "wrapped"
        case ConstantType(Constant(s: String)) =>
          Some(s)
        case _ => None
      }
    }

    /**
     * Extract all segments from a path tuple type and join them. (("field",
     * "a"), ("field", "b")) -> "a.b" Tuple1(("case", "X")) -> "case:X"
     */
    def pathTupleToString(pathType: c.Type): Option[String] = {
      val dealiased = pathType.dealias

      // Check for Tuple1 (single-element case path)
      if (dealiased.typeSymbol == tuple1Sym) {
        val args = dealiased.typeArgs
        if (args.size == 1) {
          return segmentToString(args.head)
        }
      }

      // Check for Tuple2 (nested path segments)
      if (dealiased.typeSymbol == tuple2Sym) {
        val args = dealiased.typeArgs
        if (args.size == 2) {
          // Check if this is a segment tuple ("field"/"case", name)
          val firstArg = args(0).dealias
          firstArg match {
            case ConstantType(Constant("field" | "case")) =>
              // This is a single segment, not nested
              return segmentToString(dealiased)
            case _ =>
              // This is a nested tuple: (segment1, segment2) or (segment1, (segment2, ...))
              val segments = extractPathSegments(dealiased)
              if (segments.nonEmpty) {
                return Some(segments.mkString("."))
              }
          }
        }
      }

      // Fall back to string literal (for backward compat or simple paths)
      dealiased match {
        case ConstantType(Constant(s: String)) => Some(s)
        case _                                 => None
      }
    }

    /**
     * Recursively extract segments from a nested tuple path. (("field", "a"),
     * ("field", "b")) -> List("a", "b") (("field", "a"), (("field", "b"),
     * ("field", "c"))) -> List("a", "b", "c")
     */
    def extractPathSegments(pathType: c.Type): List[String] = {
      val dealiased = pathType.dealias

      if (dealiased.typeSymbol == tuple2Sym) {
        val args = dealiased.typeArgs
        if (args.size == 2) {
          val first  = args(0).dealias
          val second = args(1).dealias

          // Check if first is a segment or a nested tuple
          first match {
            case ConstantType(Constant("field" | "case")) =>
              // This is a segment tuple, extract it
              segmentToString(dealiased).toList
            case _ if first.typeSymbol == tuple2Sym =>
              // First is a segment tuple, second might be another segment or nested tuple
              val firstSeg               = segmentToString(first)
              val restSegs: List[String] = second.typeSymbol match {
                case sym if sym == tuple2Sym =>
                  // Check if second is a segment or nested
                  second.typeArgs.headOption.map(_.dealias).flatMap {
                    case ConstantType(Constant("field" | "case")) =>
                      // Second is a single segment
                      segmentToString(second)
                    case _ =>
                      // Second is nested
                      None
                  } match {
                    case Some(seg) => List(seg)
                    case None      => extractPathSegments(second)
                  }
                case _ =>
                  segmentToString(second).toList
              }
              firstSeg.toList ++ restSegs
            case _ =>
              Nil
          }
        } else Nil
      } else {
        Nil
      }
    }

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

        // Try to extract as structured path tuple first, fall back to string literal
        val headStr = pathTupleToString(head).getOrElse {
          c.abort(c.enclosingPosition, s"Could not extract path from TList element: ${head.dealias}")
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
