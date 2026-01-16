package zio.schema.migration

import scala.quoted.*

/**
 * Higher-Order Abstract Syntax (HOAS) approach for field path extraction.
 *
 * This uses Scala 3's quoted pattern matching with HOAS patterns to extract
 * lambda bodies BEFORE eta-expansion occurs.
 *
 * Based on Scala 3 documentation:
 * https://docs.scala-lang.org/scala3/reference/metaprogramming/macros.html
 */
object HOASPathMacros {

  /**
   * Extract field path using HOAS pattern matching.
   *
   * Pattern: '{ (x: T) => $f(x) } This extracts the function body before
   * eta-expansion.
   */
  inline def extractPathHOAS[A](inline selector: A => Any): FieldPath =
    ${ extractPathHOASImpl[A]('selector) }

  def extractPathHOASImpl[A: Type](selector: Expr[A => Any])(using Quotes): Expr[FieldPath] = {
    import quotes.reflect.*

    // Try HOAS pattern matching first
    selector match {
      // Match: (x: A) => body(x)
      // The $f(x) pattern will eta-expand the body and bind it to f
      case '{ (x: A) => ($f(x): Any) } =>
        // Successfully extracted lambda before eta-expansion
        extractFieldPathFromExpr(f.asTerm)

      // Fallback for underscore syntax or other forms
      case _ =>
        report.errorAndAbort(s"Could not extract field path from selector: ${selector.show}")
    }
  }

  /**
   * Extract field path from an expression term.
   */
  private def extractFieldPathFromExpr(using Quotes)(term: quotes.reflect.Term): Expr[FieldPath] = {
    import quotes.reflect.*

    // If the term is itself a lambda, extract its body
    val bodyTerm = term match {
      case Lambda(_, body) => body
      case other           => other
    }

    def extractNames(t: Term): List[String] = t match {
      // Simple field: x.field
      case Select(Ident(_), fieldName) =>
        List(fieldName.toString)

      // Nested: x.a.b
      case Select(qualifier, fieldName) =>
        extractNames(qualifier) :+ fieldName.toString

      // Lambda parameter (base)
      case Ident(_) =>
        Nil

      // Handle other wrappers
      case Typed(inner, _) =>
        extractNames(inner)
      case Block(Nil, expr) =>
        extractNames(expr)
      case Inlined(_, Nil, expr) =>
        extractNames(expr)

      case _ =>
        Nil
    }

    val fields = extractNames(bodyTerm).filter(_.nonEmpty)

    if (fields.isEmpty) {
      report.errorAndAbort(s"Could not extract field path from: ${term.show}")
    }

    // Build FieldPath expression
    fields match {
      case head :: Nil =>
        '{ FieldPath.Root(${ Expr(head) }) }

      case head :: tail =>
        tail.foldLeft[Expr[FieldPath]]('{ FieldPath.Root(${ Expr(head) }) }) { (acc, field) =>
          '{ FieldPath.Nested($acc, ${ Expr(field) }) }
        }

      case Nil =>
        report.errorAndAbort("Empty field path")
    }
  }
}
