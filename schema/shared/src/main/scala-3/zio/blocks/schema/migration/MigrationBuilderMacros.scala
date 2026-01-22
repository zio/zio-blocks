package zio.blocks.schema.migration

import scala.annotation.tailrec
import scala.quoted.*
import zio.blocks.schema._

/**
 * Scala 3 macro implementations for MigrationBuilder selector methods.
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

    def extractFieldPath(term: Term): List[String] = {
      def loop(t: Term, acc: List[String]): List[String] = t match {
        case Select(parent, fieldName) =>
          loop(parent, fieldName :: acc)
        case _: Ident =>
          acc
        case Typed(expr, _) => // Handle type ascriptions like (_: Type)
          loop(expr, acc)
        case _ =>
          report.errorAndAbort(
            s"Unsupported selector pattern: '${t.show}'. Only simple field access is supported (e.g., _.field or _.field.nested)",
            t.pos
          )
      }
      loop(term, Nil)
    }

    val pathBody  = toPathBody(selector.asTerm)
    val fieldPath = extractFieldPath(pathBody)

    if (fieldPath.isEmpty) {
      report.errorAndAbort("Selector must access at least one field", selector.asTerm.pos)
    }

    // Build nested DynamicOptic: DynamicOptic.root.field("f1").field("f2")...
    fieldPath.foldLeft('{ DynamicOptic.root }) { (opticExpr, fieldName) =>
      val fieldNameExpr = Expr(fieldName)
      '{ $opticExpr.field($fieldNameExpr) }
    }
  }

  /**
   * Extracts just the field name from a selector (for renameField target).
   */
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

  /**
   * Extracts multiple DynamicOptics from a sequence of selector functions.
   * Handles both Seq(...) and Vector(...) syntax.
   */
  def extractOptics[A: Type, B: Type](
    selectors: Expr[Seq[A => B]]
  )(using q: Quotes): Expr[Vector[DynamicOptic]] = {
    import q.reflect.*

    // Extract the sequence of lambda expressions from various collection syntaxes
    def extractSelectors(term: Term): List[Expr[A => B]] = term match {
      case Inlined(_, _, body) => extractSelectors(body)

      // Pattern 1: Seq(...) with repeated parameters
      // Example: Seq(_.field1, _.field2)
      case Apply(TypeApply(Select(_, "apply"), _), List(Typed(Repeated(items, _), _))) =>
        items.map(_.asExprOf[A => B])

      // Pattern 2: Direct repeated parameters (varargs)
      case Typed(Repeated(items, _), _) =>
        items.map(_.asExprOf[A => B])

      // Pattern 3: Vector(...) or other collections with explicit arguments
      // Example: Vector(_.field1, _.field2)
      case Apply(TypeApply(Select(_, "apply"), _), args) =>
        args.map(_.asExprOf[A => B])

      // Pattern 4: Simple Apply without type parameters
      case Apply(Select(_, "apply"), args) =>
        args.map(_.asExprOf[A => B])

      // Pattern 5: Type ascription (e.g., Vector(...): Seq[...])
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

  /**
   * Extracts a DynamicOptic and case name from a selector with .when[CaseType]
   * pattern.
   *
   * Supports:
   *   - _.when[CaseType] → (DynamicOptic.root, "CaseType")
   *   - _.field.when[CaseType] → (DynamicOptic.root.field("field"), "CaseType")
   */
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

    def extractFieldPath(term: Term): List[String] = {
      def loop(t: Term, acc: List[String]): List[String] = t match {
        case Select(parent, fieldName) =>
          loop(parent, fieldName :: acc)
        case _: Ident =>
          acc
        case Typed(expr, _) => // Handle type ascriptions like (_: Type)
          loop(expr, acc)
        case _ =>
          report.errorAndAbort(
            s"Unsupported selector pattern before .when[]: '${t.show}'. Only simple field access is supported (e.g., _.field or _.field.nested)",
            t.pos
          )
      }
      loop(term, Nil)
    }

    def extractFieldPathAndCaseName(term: Term): (List[String], String) = term match {
      // Pattern: _.when[CaseType] or _.field.when[CaseType]
      // Structure: TypeApply(Apply(TypeApply(caseTerm, _), List(parent)), List(typeTree))
      case TypeApply(Apply(TypeApply(caseTerm, _), List(parent)), List(typeTree)) if caseTerm match {
            case Select(_, name) => name == "when"
            case Ident(name)     => name == "when"
            case _               => false
          } =>
        val caseName  = typeTree.tpe.dealias.typeSymbol.name
        val fieldPath = extractFieldPath(parent)
        (fieldPath, caseName)

      case _ =>
        report.errorAndAbort(
          s"Expected a selector with .when[CaseType] pattern (e.g., _.when[MyCase] or _.field.when[MyCase]), got '${term.show}'",
          term.pos
        )
    }

    val pathBody              = toPathBody(selector.asTerm)
    val (fieldPath, caseName) = extractFieldPathAndCaseName(pathBody)

    // Build nested DynamicOptic: DynamicOptic.root.field("f1").field("f2")...
    val opticExpr = fieldPath.foldLeft('{ DynamicOptic.root }) { (opticExpr, fieldName) =>
      val fieldNameExpr = Expr(fieldName)
      '{ $opticExpr.field($fieldNameExpr) }
    }

    (opticExpr, Expr(caseName))
  }
}
