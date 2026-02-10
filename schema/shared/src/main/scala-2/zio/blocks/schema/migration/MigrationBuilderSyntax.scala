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
    default: DynamicSchemaExpr
  ): MigrationBuilder[A, B, Handled, _ <: TList] =
    macro MigrationBuilderMacrosImpl.addFieldImpl[A, B, Handled, Provided]

  /**
   * Removes a field from a record using selector syntax. Adds the source field
   * name to Handled.
   */
  def dropField(
    source: A => Any,
    defaultForReverse: DynamicSchemaExpr
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
    transform: DynamicSchemaExpr
  ): MigrationBuilder[A, B, _ <: TList, _ <: TList] =
    macro MigrationBuilderMacrosImpl.transformFieldImpl[A, B, Handled, Provided]

  /**
   * Unwraps an Option field using selector syntax, using default for None
   * values. Adds the field name to both Handled and Provided.
   */
  def mandateField(
    at: A => Any,
    default: DynamicSchemaExpr
  ): MigrationBuilder[A, B, _ <: TList, _ <: TList] =
    macro MigrationBuilderMacrosImpl.mandateFieldImpl[A, B, Handled, Provided]

  /**
   * Wraps a field value in Option (as Some) using selector syntax. Adds the
   * field name to both Handled and Provided.
   */
  def optionalizeField(
    at: A => Any,
    defaultForReverse: DynamicSchemaExpr
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
    combiner: DynamicSchemaExpr
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
    splitter: DynamicSchemaExpr
  ): MigrationBuilder[A, B, _ <: TList, _ <: TList] =
    macro MigrationBuilderMacrosImpl.splitFieldImpl[A, B, Handled, Provided]

  /**
   * Applies a transformation to all elements in a sequence using selector
   * syntax. Does not affect field tracking.
   */
  def transformElements(
    at: A => Any,
    transform: DynamicSchemaExpr
  ): MigrationBuilder[A, B, Handled, Provided] =
    macro MigrationBuilderMacrosImpl.transformElementsImpl[A, B, Handled, Provided]

  /**
   * Applies a transformation to all keys in a map using selector syntax. Does
   * not affect field tracking.
   */
  def transformKeys(
    at: A => Any,
    transform: DynamicSchemaExpr
  ): MigrationBuilder[A, B, Handled, Provided] =
    macro MigrationBuilderMacrosImpl.transformKeysImpl[A, B, Handled, Provided]

  /**
   * Applies a transformation to all values in a map using selector syntax. Does
   * not affect field tracking.
   */
  def transformValues(
    at: A => Any,
    transform: DynamicSchemaExpr
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
}

// Macro implementations for Scala 2 selector syntax with type tracking.
private[migration] object MigrationBuilderMacrosImpl {
  import scala.reflect.macros.whitebox

  // Build macro implementation

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

  // Field extraction helpers

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
    default: c.Expr[DynamicSchemaExpr]
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

    q"""$builder.withAction(_root_.zio.blocks.schema.migration.MigrationAction.AddField($optic, $default)).asInstanceOf[$resultType]"""
  }

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    source: c.Expr[A => Any],
    defaultForReverse: c.Expr[DynamicSchemaExpr]
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

    q"""$builder.withAction(_root_.zio.blocks.schema.migration.MigrationAction.DropField($optic, $defaultForReverse)).asInstanceOf[$resultType]"""
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

    q"""$builder.withAction(_root_.zio.blocks.schema.migration.MigrationAction.Rename($fromOptic, $toNameExpr)).asInstanceOf[$resultType]"""
  }

  // Shared helper for field ops that add to both Handled and Provided

  /**
   * Shared implementation for field operations that add the same field path to
   * both Handled and Provided type lists.
   *
   * @param buildCall
   *   Function that takes (builder, optic) and returns the builder method call
   *   tree
   */
  private def dualTrackingFieldOpImpl[
    A: c.WeakTypeTag,
    B: c.WeakTypeTag,
    Handled: c.WeakTypeTag,
    Provided: c.WeakTypeTag
  ](
    c: whitebox.Context
  )(
    at: c.Expr[A => Any]
  )(
    buildCall: (c.Tree, c.Tree) => c.Tree
  ): c.Tree = {
    import c.universe._

    val builder       = q"${c.prefix}.builder"
    val fieldNames    = extractFieldNamesFromSelector(c)(at.tree)
    val optic         = MigrationBuilderMacros.extractOptic[A, Any](c)(at)
    val fieldPathType = fieldPathToTupleType(c)(fieldNames)

    val newHandledTpe  = appendType(c)(weakTypeOf[Handled], fieldPathType)
    val newProvidedTpe = appendType(c)(weakTypeOf[Provided], fieldPathType)

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(weakTypeOf[A], weakTypeOf[B], newHandledTpe, newProvidedTpe)
    )

    q"${buildCall(builder, optic.tree)}.asInstanceOf[$resultType]"
  }

  def transformFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Any],
    transform: c.Expr[DynamicSchemaExpr]
  ): c.Tree = {
    import c.universe._
    dualTrackingFieldOpImpl[A, B, Handled, Provided](c)(at) { (builder, optic) =>
      q"$builder.withAction(_root_.zio.blocks.schema.migration.MigrationAction.TransformValue($optic, $transform))"
    }
  }

  def mandateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Any],
    default: c.Expr[DynamicSchemaExpr]
  ): c.Tree = {
    import c.universe._
    dualTrackingFieldOpImpl[A, B, Handled, Provided](c)(at) { (builder, optic) =>
      q"$builder.withAction(_root_.zio.blocks.schema.migration.MigrationAction.Mandate($optic, $default))"
    }
  }

  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Any],
    defaultForReverse: c.Expr[DynamicSchemaExpr]
  ): c.Tree = {
    import c.universe._
    dualTrackingFieldOpImpl[A, B, Handled, Provided](c)(at) { (builder, optic) =>
      q"$builder.withAction(_root_.zio.blocks.schema.migration.MigrationAction.Optionalize($optic, $defaultForReverse))"
    }
  }

  def changeFieldTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Any],
    converter: c.Expr[PrimitiveConverter]
  ): c.Tree = {
    import c.universe._
    dualTrackingFieldOpImpl[A, B, Handled, Provided](c)(at) { (builder, optic) =>
      q"$builder.withAction(_root_.zio.blocks.schema.migration.MigrationAction.ChangeType($optic, $converter))"
    }
  }

  def joinFieldsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    target: c.Expr[B => Any],
    sourcePaths: c.Expr[Seq[A => Any]],
    combiner: c.Expr[DynamicSchemaExpr]
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

    // Validate that all source paths share the same parent
    if (sourceFieldPaths.length > 1) {
      val parents = sourceFieldPaths.map(_.dropRight(1))
      if (!parents.forall(_ == parents.head)) {
        c.abort(
          c.enclosingPosition,
          s"joinFields source fields must share common parent. Found paths: ${sourceFieldPaths.map(_.mkString(".")).mkString(", ")}"
        )
      }
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

    q"""$builder.withAction(_root_.zio.blocks.schema.migration.MigrationAction.Join($targetOptic, $sourceOptics, $combiner)).asInstanceOf[$resultType]"""
  }

  def splitFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    source: c.Expr[A => Any],
    targetPaths: c.Expr[Seq[B => Any]],
    splitter: c.Expr[DynamicSchemaExpr]
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

    // Validate that all target paths share the same parent
    if (targetFieldPaths.length > 1) {
      val parents = targetFieldPaths.map(_.dropRight(1))
      if (!parents.forall(_ == parents.head)) {
        c.abort(
          c.enclosingPosition,
          s"splitField target fields must share common parent. Found paths: ${targetFieldPaths.map(_.mkString(".")).mkString(", ")}"
        )
      }
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

    q"""$builder.withAction(_root_.zio.blocks.schema.migration.MigrationAction.Split($sourceOptic, $targetOptics, $splitter)).asInstanceOf[$resultType]"""
  }

  // Shared helper for passthrough ops that don't affect Handled/Provided

  /**
   * Shared implementation for operations that don't modify the Handled or
   * Provided type lists (like transformElements, transformKeys,
   * transformValues).
   */
  private def passthroughOpImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Any]
  )(
    buildCall: (c.Tree, c.Tree) => c.Tree
  ): c.Tree = {
    import c.universe._

    val builder = q"${c.prefix}.builder"
    val optic   = MigrationBuilderMacros.extractOptic[A, Any](c)(at)

    val resultType = appliedType(
      typeOf[MigrationBuilder[_, _, _, _]].typeConstructor,
      List(weakTypeOf[A], weakTypeOf[B], weakTypeOf[Handled], weakTypeOf[Provided])
    )

    q"${buildCall(builder, optic.tree)}.asInstanceOf[$resultType]"
  }

  def transformElementsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Any],
    transform: c.Expr[DynamicSchemaExpr]
  ): c.Tree = {
    import c.universe._
    passthroughOpImpl[A, B, Handled, Provided](c)(at) { (builder, optic) =>
      q"$builder.withAction(_root_.zio.blocks.schema.migration.MigrationAction.TransformElements($optic, $transform))"
    }
  }

  def transformKeysImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Any],
    transform: c.Expr[DynamicSchemaExpr]
  ): c.Tree = {
    import c.universe._
    passthroughOpImpl[A, B, Handled, Provided](c)(at) { (builder, optic) =>
      q"$builder.withAction(_root_.zio.blocks.schema.migration.MigrationAction.TransformKeys($optic, $transform))"
    }
  }

  def transformValuesImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Any],
    transform: c.Expr[DynamicSchemaExpr]
  ): c.Tree = {
    import c.universe._
    passthroughOpImpl[A, B, Handled, Provided](c)(at) { (builder, optic) =>
      q"$builder.withAction(_root_.zio.blocks.schema.migration.MigrationAction.TransformValues($optic, $transform))"
    }
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

    q"""$builder.withAction(_root_.zio.blocks.schema.migration.MigrationAction.RenameCase($fromOptic, $fromCaseName, $to)).asInstanceOf[$resultType]"""
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
      $builder.withAction(_root_.zio.blocks.schema.migration.MigrationAction.TransformCase($atOptic, $caseName, transformedBuilder.actions)).asInstanceOf[$resultType]
    }"""
  }
}

/**
 * Helper class that mixes in MacroHelpers trait for path-dependent type safety.
 */
private[migration] class MigrationBuilderMacrosHelper[C <: scala.reflect.macros.whitebox.Context](val c: C)
    extends MacroHelpers {
  import c.universe._

  // Type symbols cached for reuse across methods
  private lazy val tnilSym   = typeOf[TNil].typeSymbol
  private lazy val tconsSym  = typeOf[TCons[_, _]].typeSymbol
  private lazy val tuple2Sym = typeOf[(_, _)].typeSymbol
  private lazy val tuple1Sym = typeOf[Tuple1[_]].typeSymbol

  def buildImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag]: c.Tree = {
    val builder = q"${c.prefix}.builder"

    val aType        = weakTypeOf[A]
    val bType        = weakTypeOf[B]
    val handledType  = weakTypeOf[Handled]
    val providedType = weakTypeOf[Provided]

    // Extract shape trees for both types
    val treeA = extractShapeTree(aType, Set.empty, "Migration validation")
    val treeB = extractShapeTree(bType, Set.empty, "Migration validation")

    // Compute diff using TreeDiff - returns List[List[Segment]]
    val (removed, added) = TreeDiff.diff(treeA, treeB)

    // Extract handled/provided as List[List[Segment]] (structural comparison)
    val handled: List[List[Segment]]  = extractTListPaths(handledType)
    val provided: List[List[Segment]] = extractTListPaths(providedType)

    // Compare as List[List[Segment]] - full structural comparison
    val missingHandled  = removed.filterNot(path => handled.contains(path))
    val missingProvided = added.filterNot(path => provided.contains(path))

    if (missingHandled.nonEmpty || missingProvided.nonEmpty) {
      c.abort(c.enclosingPosition, buildValidationErrorMessage(aType, bType, missingHandled, missingProvided))
    }

    q"$builder.buildPartial"
  }

  /**
   * Builds a detailed error message for migration validation failures.
   */
  private def buildValidationErrorMessage(
    aType: c.Type,
    bType: c.Type,
    missingHandled: List[List[Segment]],
    missingProvided: List[List[Segment]]
  ): String = {
    val unhandledStrs  = missingHandled.map(Path.render).sorted
    val unprovidedStrs = missingProvided.map(Path.render).sorted

    val (unhandledPaths, unhandledCases)   = unhandledStrs.partition(!_.startsWith("case:"))
    val (unprovidedPaths, unprovidedCases) = unprovidedStrs.partition(!_.startsWith("case:"))

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

    sb.toString
  }

  // TList Path Extraction - Split into helper methods

  /**
   * Extracts paths from a TList type as List[List[Segment]]. Preserves full
   * structural information for comparison.
   *
   * Given a type like (("field", "a"), ("field", "b")) :: TNil, returns
   * List(List(Segment.Field("a"), Segment.Field("b"))).
   */
  private def extractTListPaths(tpe: c.Type): List[List[Segment]] =
    tlistToTypes(tpe).flatMap { elemType =>
      pathTupleToSegments(elemType) match {
        case Some(segments) => Some(segments)
        case None           =>
          c.abort(c.enclosingPosition, s"Could not extract path from TList element: ${elemType.dealias}")
      }
    }

  /**
   * Converts a TList type to a List of its element types. TCons[A, TCons[B,
   * TNil]] -> List(A, B)
   */
  private def tlistToTypes(tpe: c.Type): List[c.Type] = {
    val dealiased = tpe.dealias
    dealiased.typeSymbol match {
      case sym if sym == tnilSym  => Nil
      case sym if sym == tconsSym =>
        val args = dealiased.typeArgs
        if (args.size != 2) {
          c.abort(c.enclosingPosition, s"Invalid TCons type: $dealiased")
        }
        args(0) :: tlistToTypes(args(1))
      case _ => Nil // Generic TList bounds
    }
  }

  /**
   * Convert a segment type to a Segment. ("field", "name") ->
   * Segment.Field("name") ("case", "name") -> Segment.Case("name") "element"
   * literal -> Segment.Element
   */
  private def segmentFromType(segType: c.Type): Option[Segment] = {
    val dealiased = segType.dealias
    dealiased match {
      case t if t.typeSymbol == tuple2Sym && t.typeArgs.size == 2 =>
        (t.typeArgs(0).dealias, t.typeArgs(1).dealias) match {
          case (ConstantType(Constant("field")), ConstantType(Constant(name: String))) =>
            Some(Segment.Field(name))
          case (ConstantType(Constant("case")), ConstantType(Constant(name: String))) =>
            Some(Segment.Case(name))
          case _ => None
        }
      case ConstantType(Constant("element")) => Some(Segment.Element)
      case ConstantType(Constant("key"))     => Some(Segment.Key)
      case ConstantType(Constant("value"))   => Some(Segment.Value)
      case ConstantType(Constant("wrapped")) => Some(Segment.Wrapped)
      case _                                 => None
    }
  }

  /**
   * Check if a type is a segment tuple ("field"/"case", name).
   */
  private def isSegmentTuple(tpe: c.Type): Boolean = {
    val dealiased = tpe.dealias
    dealiased.typeSymbol == tuple2Sym &&
    dealiased.typeArgs.headOption.exists { arg =>
      arg.dealias match {
        case ConstantType(Constant("field" | "case")) => true
        case _                                        => false
      }
    }
  }

  /**
   * Extract all segments from a path tuple type as List[Segment]. (("field",
   * "a"), ("field", "b")) -> List(Segment.Field("a"), Segment.Field("b"))
   * Tuple1(("case", "X")) -> List(Segment.Case("X"))
   */
  private def pathTupleToSegments(pathType: c.Type): Option[List[Segment]] = {
    val dealiased = pathType.dealias

    dealiased.typeSymbol match {
      case sym if sym == tuple1Sym =>
        // Single-element case path: Tuple1(("case", "X"))
        dealiased.typeArgs.headOption.flatMap(segmentFromType).map(List(_))

      case sym if sym == tuple2Sym && dealiased.typeArgs.size == 2 =>
        val first  = dealiased.typeArgs(0).dealias
        val second = dealiased.typeArgs(1).dealias

        if (isSegmentTuple(dealiased)) {
          // This is itself a segment tuple
          segmentFromType(dealiased).map(List(_))
        } else if (isSegmentTuple(first)) {
          // Nested: (segment, rest)
          for {
            firstSeg <- segmentFromType(first)
            restSegs <- extractRestSegments(second)
          } yield firstSeg :: restSegs
        } else {
          None
        }

      case _ => None
    }
  }

  /**
   * Extract remaining segments from the "rest" part of a nested tuple. Handles
   * both single segments and nested tuples.
   */
  private def extractRestSegments(tpe: c.Type): Option[List[Segment]] =
    if (isSegmentTuple(tpe)) {
      segmentFromType(tpe).map(List(_))
    } else {
      pathTupleToSegments(tpe)
    }

  /**
   * Convert a path (List[Segment]) to a flat string representation for error
   * messages.
   */
}
