package zio.blocks.schema.migration

import scala.reflect.macros.whitebox
import zio.blocks.schema._

/**
 * Implementations for MigrationBuilder selector methods. Extracts DynamicOptic
 * from selector functions like `_.field`
 */
private[migration] object MigrationBuilderMacros {

  /**
   * Extracts a DynamicOptic from a selector function. Supports both simple
   * field access and nested fields.
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

    def extractOpticExpr(tree: c.Tree): c.Expr[DynamicOptic] = {
      def loop(t: c.Tree, depth: Int): c.Expr[DynamicOptic] = t match {
        // .each/.eachKey/.eachValue are recognized but stripped — the corresponding
        // TransformElements/TransformKeys/TransformValues actions handle traversal
        case q"$_[..$_]($parent).each" =>
          loop(parent, depth + 1)
        case q"$_[..$_]($parent).eachKey" =>
          loop(parent, depth + 1)
        case q"$_[..$_]($parent).eachValue" =>
          loop(parent, depth + 1)
        case Select(parent, fieldName) =>
          val parentOptic   = loop(parent, depth + 1)
          val fieldNameExpr = c.Expr[String](Literal(Constant(fieldName.decodedName.toString)))
          reify(parentOptic.splice.field(fieldNameExpr.splice))
        case _: Ident if depth > 0 =>
          reify(DynamicOptic.root)
        case _: Ident =>
          c.abort(c.enclosingPosition, "Selector must access at least one field")
        case Typed(expr, _) =>
          loop(expr, depth)
        case _ =>
          c.abort(
            c.enclosingPosition,
            s"Unsupported selector: '$t'. Supported: _.field, _.field.nested, _.seq.each, _.map.eachKey, _.map.eachValue"
          )
      }
      loop(tree, 0)
    }

    val pathBody = toPathBody(selector.tree)
    extractOpticExpr(pathBody)
  }

  // Extracts just the field name from a selector (for renameField target).
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

  // Extracts multiple DynamicOptics from a sequence of selector functions.
  def extractOptics[A, B](c: whitebox.Context)(
    selectors: c.Expr[Seq[A => B]]
  ): c.Expr[Vector[DynamicOptic]] = {
    import c.universe._

    // Extract the sequence of lambda expressions from various collection syntaxes
    def extractSelectors(tree: c.Tree): List[c.Expr[A => B]] = tree match {
      // Seq.apply from scala.collection.immutable
      case q"scala.collection.immutable.Seq.apply[$_](..$items)" =>
        items.map(item => c.Expr[A => B](item))

      // Seq.apply from scala.package
      case q"scala.`package`.Seq.apply[$_](..$items)" =>
        items.map(item => c.Expr[A => B](item))

      // Vector.apply from scala.collection.immutable
      case q"scala.collection.immutable.Vector.apply[$_](..$items)" =>
        items.map(item => c.Expr[A => B](item))

      // Vector.apply from scala.package
      case q"scala.`package`.Vector.apply[$_](..$items)" =>
        items.map(item => c.Expr[A => B](item))

      // Wrapped array (varargs)
      case q"scala.Predef.wrapRefArray[$_](scala.Array.apply[$_](..$items))" =>
        items.map(item => c.Expr[A => B](item))

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

  // Extracts a DynamicOptic and case name from a selector with .when[CaseType]
  // pattern. Supports both simple field access and nested fields.
  def extractCaseSelector[A, B](c: whitebox.Context)(
    selector: c.Expr[A => B]
  ): (c.Expr[DynamicOptic], c.Expr[String]) = {
    import c.universe._

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    =>
        c.abort(c.enclosingPosition, s"Expected a lambda expression, got '$tree'")
    }

    def extractOpticExpr(tree: c.Tree): c.Expr[DynamicOptic] = {
      def loop(t: c.Tree, depth: Int): c.Expr[DynamicOptic] = t match {
        case q"$_[..$_]($parent).each" =>
          loop(parent, depth + 1)
        case q"$_[..$_]($parent).eachKey" =>
          loop(parent, depth + 1)
        case q"$_[..$_]($parent).eachValue" =>
          loop(parent, depth + 1)
        case Select(parent, fieldName) =>
          val parentOptic   = loop(parent, depth + 1)
          val fieldNameExpr = c.Expr[String](Literal(Constant(fieldName.decodedName.toString)))
          reify(parentOptic.splice.field(fieldNameExpr.splice))
        case _: Ident if depth > 0 =>
          reify(DynamicOptic.root)
        case _: Ident =>
          reify(DynamicOptic.root)
        case Typed(expr, _) =>
          loop(expr, depth)
        case _ =>
          c.abort(
            c.enclosingPosition,
            s"Unsupported selector before .when[]: '$t'. Supported: _.field, _.field.nested, _.seq.each, _.map.eachKey, _.map.eachValue"
          )
      }
      loop(tree, 0)
    }

    def extractOpticAndCaseName(tree: c.Tree): (c.Expr[DynamicOptic], String) = tree match {
      // _.when[CaseType] or _.field.when[CaseType]
      case q"$_[..$_]($parent).when[$caseTree]" =>
        val caseName = caseTree.tpe.dealias.typeSymbol.name.decodedName.toString
        val optic    = extractOpticExpr(parent)
        (optic, caseName)
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Expected a selector with .when[CaseType] pattern (e.g., _.when[MyCase] or _.field.when[MyCase]), got '$tree'"
        )
    }

    val pathBody              = toPathBody(selector.tree)
    val (opticExpr, caseName) = extractOpticAndCaseName(pathBody)

    (opticExpr, c.Expr[String](Literal(Constant(caseName))))
  }
}
