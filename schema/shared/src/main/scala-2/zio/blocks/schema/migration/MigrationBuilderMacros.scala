package zio.blocks.schema.migration

import scala.reflect.macros.whitebox
import zio.blocks.schema._

/**
 * Scala 2 macro implementations for MigrationBuilder selector methods.
 *
 * These macros extract DynamicOptic from selector functions like `_.field` and
 * delegate to the existing Phase 8 methods.
 */
private[migration] object MigrationBuilderMacros {

  /**
   * Extracts a DynamicOptic from a selector function.
   *
   * Supports:
   *   - Simple field access: _.field → DynamicOptic.root.field("field")
   *   - Nested fields: _.field.nested → chained optics
   */
  def extractOptic[A, B](c: whitebox.Context)(
    selector: c.Expr[A => B]
  ): c.Expr[DynamicOptic] = {
    import c.universe._

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    =>
        c.abort(c.enclosingPosition, s"Expected a lambda expression, got '$tree'")
    }

    def extractFieldPath(tree: c.Tree): List[String] = {
      def loop(t: c.Tree, acc: List[String]): List[String] = t match {
        case Select(parent, fieldName) =>
          loop(parent, fieldName.decodedName.toString :: acc)
        case _: Ident =>
          acc
        case Typed(expr, _) => // Handle type ascriptions like (_: Type)
          loop(expr, acc)
        case _ =>
          c.abort(
            c.enclosingPosition,
            s"Unsupported selector pattern: '$t'. Only simple field access is supported (e.g., _.field or _.field.nested)"
          )
      }
      loop(tree, Nil)
    }

    val pathBody  = toPathBody(selector.tree)
    val fieldPath = extractFieldPath(pathBody)

    if (fieldPath.isEmpty) {
      c.abort(c.enclosingPosition, "Selector must access at least one field")
    }

    // Build nested DynamicOptic: DynamicOptic.root.field("f1").field("f2")...
    fieldPath.foldLeft(reify(DynamicOptic.root)) { (opticExpr, fieldName) =>
      val fieldNameExpr = c.Expr[String](Literal(Constant(fieldName)))
      reify(opticExpr.splice.field(fieldNameExpr.splice))
    }
  }

  /**
   * Extracts just the field name from a selector (for renameField target).
   */
  def extractFieldName[A, B](c: whitebox.Context)(
    selector: c.Expr[A => B]
  ): c.Expr[String] = {
    import c.universe._

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    =>
        c.abort(c.enclosingPosition, s"Expected a lambda expression, got '$tree'")
    }

    def extractLastFieldName(tree: c.Tree): String = tree match {
      case Select(_, fieldName) => fieldName.decodedName.toString
      case Typed(expr, _)       => // Handle type ascriptions like (_: Type)
        extractLastFieldName(expr)
      case _: Ident =>
        c.abort(c.enclosingPosition, "Selector must access a field")
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Unsupported selector pattern: '$tree'. Only simple field access is supported"
        )
    }

    val pathBody  = toPathBody(selector.tree)
    val fieldName = extractLastFieldName(pathBody)
    c.Expr[String](Literal(Constant(fieldName)))
  }

  /**
   * Extracts multiple DynamicOptics from a sequence of selector functions.
   * Handles both Seq(...) and Vector(...) syntax.
   */
  def extractOptics[A, B](c: whitebox.Context)(
    selectors: c.Expr[Seq[A => B]]
  ): c.Expr[Vector[DynamicOptic]] = {
    import c.universe._

    // Extract the sequence of lambda expressions from various collection syntaxes
    def extractSelectors(tree: c.Tree): List[c.Expr[A => B]] = tree match {
      // Pattern 1: Seq.apply from scala.collection.immutable
      case q"scala.collection.immutable.Seq.apply[$_](..$items)" =>
        items.map(item => c.Expr[A => B](item))

      // Pattern 2: Seq.apply from scala.package
      case q"scala.`package`.Seq.apply[$_](..$items)" =>
        items.map(item => c.Expr[A => B](item))

      // Pattern 3: Vector.apply from scala.collection.immutable
      case q"scala.collection.immutable.Vector.apply[$_](..$items)" =>
        items.map(item => c.Expr[A => B](item))

      // Pattern 4: Vector.apply from scala.package
      case q"scala.`package`.Vector.apply[$_](..$items)" =>
        items.map(item => c.Expr[A => B](item))

      // Pattern 5: Wrapped array (varargs)
      case q"scala.Predef.wrapRefArray[$_](scala.Array.apply[$_](..$items))" =>
        items.map(item => c.Expr[A => B](item))

      // Pattern 6: Type ascription (e.g., Vector(...): Seq[...])
      // Unwrap the type ascription and process the inner tree
      case Typed(inner, _) =>
        extractSelectors(inner)

      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Expected a sequence of selectors (Seq or Vector), got '$tree'. " +
            s"Tree structure: ${showRaw(tree)}"
        )
    }

    val selectorExprs = extractSelectors(selectors.tree)
    val opticExprs    = selectorExprs.map(sel => extractOptic[A, B](c)(sel))

    // Build Vector(optic1, optic2, ...)
    val opticsTrees = opticExprs.map(_.tree)
    c.Expr[Vector[DynamicOptic]](q"_root_.scala.Vector(..$opticsTrees)")
  }

  /**
   * Extracts a DynamicOptic and case name from a selector with .when[CaseType]
   * pattern.
   *
   * Supports:
   *   - _.when[CaseType] → (DynamicOptic.root, "CaseType")
   *   - _.field.when[CaseType] → (DynamicOptic.root.field("field"), "CaseType")
   */
  def extractCaseSelector[A, B](c: whitebox.Context)(
    selector: c.Expr[A => B]
  ): (c.Expr[DynamicOptic], c.Expr[String]) = {
    import c.universe._

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    =>
        c.abort(c.enclosingPosition, s"Expected a lambda expression, got '$tree'")
    }

    def extractFieldPathAndCaseName(tree: c.Tree): (List[String], String) = tree match {
      // Pattern: _.when[CaseType] or _.field.when[CaseType]
      case q"$_[..$_]($parent).when[$caseTree]" =>
        val caseName  = caseTree.tpe.dealias.typeSymbol.name.decodedName.toString
        val fieldPath = extractFieldPath(parent)
        (fieldPath, caseName)
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Expected a selector with .when[CaseType] pattern (e.g., _.when[MyCase] or _.field.when[MyCase]), got '$tree'"
        )
    }

    def extractFieldPath(tree: c.Tree): List[String] = {
      def loop(t: c.Tree, acc: List[String]): List[String] = t match {
        case Select(parent, fieldName) =>
          loop(parent, fieldName.decodedName.toString :: acc)
        case _: Ident =>
          acc
        case Typed(expr, _) => // Handle type ascriptions like (_: Type)
          loop(expr, acc)
        case _ =>
          c.abort(
            c.enclosingPosition,
            s"Unsupported selector pattern before .when[]: '$t'. Only simple field access is supported (e.g., _.field or _.field.nested)"
          )
      }
      loop(tree, Nil)
    }

    val pathBody              = toPathBody(selector.tree)
    val (fieldPath, caseName) = extractFieldPathAndCaseName(pathBody)

    // Build nested DynamicOptic: DynamicOptic.root.field("f1").field("f2")...
    val opticExpr = fieldPath.foldLeft(reify(DynamicOptic.root)) { (opticExpr, fieldName) =>
      val fieldNameExpr = c.Expr[String](Literal(Constant(fieldName)))
      reify(opticExpr.splice.field(fieldNameExpr.splice))
    }

    (opticExpr, c.Expr[String](Literal(Constant(caseName))))
  }
}
