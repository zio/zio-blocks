package zio.blocks.schema.migration

import scala.annotation.tailrec
import scala.quoted.*
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
  def extractOptic[A: Type, B: Type](
    selector: Expr[A => B]
  )(using q: Quotes): Expr[DynamicOptic] = {
    import q.reflect.*

    @tailrec
    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               =>
        report.errorAndAbort(s"Expected a lambda expression, got '${term.show}'", term.pos)
    }

    def hasName(t: Term, name: String): Boolean = t match {
      case Select(_, n) => n == name
      case Ident(n)     => n == name
      case _            => false
    }

    def extractOpticExpr(term: Term): Expr[DynamicOptic] = {
      def loop(t: Term, depth: Int): Expr[DynamicOptic] = t match {
        // .each/.eachKey/.eachValue are recognized but stripped — the corresponding
        // TransformElements/TransformKeys/TransformValues actions handle traversal
        case Apply(TypeApply(fn, _), List(parent)) if hasName(fn, "each") =>
          loop(parent, depth + 1)
        case Apply(TypeApply(fn, _), List(parent)) if hasName(fn, "eachKey") =>
          loop(parent, depth + 1)
        case Apply(TypeApply(fn, _), List(parent)) if hasName(fn, "eachValue") =>
          loop(parent, depth + 1)
        case Select(parent, fieldName) =>
          val p = loop(parent, depth + 1)
          val f = Expr(fieldName); '{ $p.field($f) }
        case _: Ident if depth > 0 => '{ DynamicOptic.root }
        case _: Ident              =>
          report.errorAndAbort("Selector must access at least one field", t.pos)
        case Typed(expr, _) => loop(expr, depth)
        case _              =>
          report.errorAndAbort(
            s"Unsupported selector: '${t.show}'. Supported: _.field, _.field.nested, _.seq.each, _.map.eachKey, _.map.eachValue",
            t.pos
          )
      }
      loop(term, 0)
    }

    val pathBody = toPathBody(selector.asTerm)
    extractOpticExpr(pathBody)
  }

  // Extracts just the field name from a selector (for renameField target).
  def extractFieldName[A: Type, B: Type](
    selector: Expr[A => B]
  )(using q: Quotes): Expr[String] = {
    import q.reflect.*

    @tailrec
    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               =>
        report.errorAndAbort(s"Expected a lambda expression, got '${term.show}'", term.pos)
    }

    def extractLastFieldName(term: Term): String = term match {
      case Select(_, fieldName) => fieldName
      case Typed(expr, _)       => // Handle type ascriptions like (_: Type)
        extractLastFieldName(expr)
      case _: Ident =>
        report.errorAndAbort("Selector must access a field", term.pos)
      case _ =>
        report.errorAndAbort(
          s"Unsupported selector pattern: '${term.show}'. Only simple field access is supported",
          term.pos
        )
    }

    val pathBody  = toPathBody(selector.asTerm)
    val fieldName = extractLastFieldName(pathBody)
    Expr(fieldName)
  }

  // Extracts multiple DynamicOptics from a sequence of selector functions.
  def extractOptics[A: Type, B: Type](
    selectors: Expr[Seq[A => B]]
  )(using q: Quotes): Expr[Vector[DynamicOptic]] = {
    import q.reflect.*

    // Extract the sequence of lambda expressions from various collection syntaxes
    def extractSelectors(term: Term): List[Expr[A => B]] = term match {
      case Inlined(_, _, body) => extractSelectors(body)

      // Seq(...) with repeated parameters
      case Apply(TypeApply(Select(_, "apply"), _), List(Typed(Repeated(items, _), _))) =>
        items.map(_.asExprOf[A => B])

      // Direct repeated parameters (varargs)
      case Typed(Repeated(items, _), _) =>
        items.map(_.asExprOf[A => B])

      // Vector(...) or other collections with explicit arguments
      case Apply(TypeApply(Select(_, "apply"), _), args) =>
        args.map(_.asExprOf[A => B])

      // Simple Apply without type parameters
      case Apply(Select(_, "apply"), args) =>
        args.map(_.asExprOf[A => B])

      // Unwrap the type ascription and process the inner term
      case Typed(inner, _) =>
        extractSelectors(inner)

      case _ =>
        report.errorAndAbort(
          s"Expected a sequence of selectors (Seq or Vector), got '${term.show}'. " +
            s"Term structure: ${term.getClass.getSimpleName}",
          term.pos
        )
    }

    val selectorExprs = extractSelectors(selectors.asTerm)
    val opticExprs    = selectorExprs.map(sel => extractOptic[A, B](sel))

    // Build Vector(optic1, optic2, ...)
    val opticsList = Expr.ofList(opticExprs)
    '{ $opticsList.toVector }
  }

  // Extracts a DynamicOptic and case name from a selector with .when[CaseType]
  // pattern. Supports both simple field access and nested fields.
  def extractCaseSelector[A: Type, B: Type](
    selector: Expr[A => B]
  )(using q: Quotes): (Expr[DynamicOptic], Expr[String]) = {
    import q.reflect.*

    @tailrec
    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               =>
        report.errorAndAbort(s"Expected a lambda expression, got '${term.show}'", term.pos)
    }

    def hasName(t: Term, name: String): Boolean = t match {
      case Select(_, n) => n == name
      case Ident(n)     => n == name
      case _            => false
    }

    def extractOpticExpr(term: Term): Expr[DynamicOptic] = {
      def loop(t: Term, depth: Int): Expr[DynamicOptic] = t match {
        case Apply(TypeApply(fn, _), List(parent)) if hasName(fn, "each") =>
          loop(parent, depth + 1)
        case Apply(TypeApply(fn, _), List(parent)) if hasName(fn, "eachKey") =>
          loop(parent, depth + 1)
        case Apply(TypeApply(fn, _), List(parent)) if hasName(fn, "eachValue") =>
          loop(parent, depth + 1)
        case Select(parent, fieldName) =>
          val p = loop(parent, depth + 1)
          val f = Expr(fieldName); '{ $p.field($f) }
        case _: Ident if depth > 0 => '{ DynamicOptic.root }
        case _: Ident              => '{ DynamicOptic.root }
        case Typed(expr, _)        => loop(expr, depth)
        case _                     =>
          report.errorAndAbort(
            s"Unsupported selector before .when[]: '${t.show}'. Supported: _.field, _.field.nested, _.seq.each, _.map.eachKey, _.map.eachValue",
            t.pos
          )
      }
      loop(term, 0)
    }

    def extractOpticAndCaseName(term: Term): (Expr[DynamicOptic], String) = term match {
      // _.when[CaseType] or _.field.when[CaseType]
      case TypeApply(Apply(TypeApply(caseTerm, _), List(parent)), List(typeTree)) if caseTerm match {
            case Select(_, name) => name == "when"
            case Ident(name)     => name == "when"
            case _               => false
          } =>
        val dealiased = typeTree.tpe.dealias
        val caseName  =
          if (dealiased.termSymbol.flags.is(Flags.Enum)) dealiased.termSymbol.name
          else dealiased.typeSymbol.name
        val optic = extractOpticExpr(parent)
        (optic, caseName)

      case _ =>
        report.errorAndAbort(
          s"Expected a selector with .when[CaseType] pattern (e.g., _.when[MyCase] or _.field.when[MyCase]), got '${term.show}'",
          term.pos
        )
    }

    val pathBody              = toPathBody(selector.asTerm)
    val (opticExpr, caseName) = extractOpticAndCaseName(pathBody)

    (opticExpr, Expr(caseName))
  }
}
