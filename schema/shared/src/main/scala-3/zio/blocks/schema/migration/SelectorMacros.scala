package zio.blocks.schema.migration

import scala.quoted.*
import zio.blocks.schema.DynamicOptic

/**
 * Scala 3 macro utilities for extracting `DynamicOptic` paths from selector
 * functions (e.g., `_.name`, `_.address.street`).
 *
 * This enables the bounty-required user-facing API where locations are
 * specified using selector expressions rather than raw strings.
 */
object SelectorMacros {

  /**
   * Extract a `DynamicOptic` path from a selector function `S => A`. Supports
   * chained field access: `_.field1.field2.field3`
   */
  inline def extractPath[S, A](inline selector: S => A): DynamicOptic =
    ${ extractPathImpl[S, A]('selector) }

  /**
   * Extract the final field name from a selector function. For
   * `_.address.street`, returns `"street"`.
   */
  inline def extractFieldName[S, A](inline selector: S => A): String =
    ${ extractFieldNameImpl[S, A]('selector) }

  private def extractPathImpl[S: Type, A: Type](
    selector: Expr[S => A]
  )(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    val fieldNames = extractFieldNames(selector.asTerm)

    fieldNames match {
      case Nil =>
        '{ DynamicOptic.root }
      case head :: Nil =>
        val nameExpr = Expr(head)
        '{ DynamicOptic.root.field($nameExpr) }
      case names =>
        // Build the path: DynamicOptic.root.field("a").field("b").field("c")
        // But for selector path, the last field is the leaf, so the "at" path
        // is everything except the last field
        names.init.foldLeft('{ DynamicOptic.root }) { (acc, name) =>
          val nameExpr = Expr(name)
          '{ $acc.field($nameExpr) }
        }
    }
  }

  private def extractFieldNameImpl[S: Type, A: Type](
    selector: Expr[S => A]
  )(using Quotes): Expr[String] = {
    import quotes.reflect.*

    val fieldNames = extractFieldNames(selector.asTerm)
    fieldNames match {
      case Nil =>
        report.errorAndAbort("Selector must access at least one field, e.g., _.name")
      case names =>
        Expr(names.last)
    }
  }

  private def extractFieldNames(term: Quotes#reflectModule#Term)(using q: Quotes): List[String] = {
    import q.reflect.*

    val termCast = term.asInstanceOf[Term]

    def go(t: Term): List[String] = t match {
      // Lambda: (x: S) => x.field1.field2
      case Inlined(_, _, inner) =>
        go(inner)

      case Block(Nil, inner) =>
        go(inner)

      // Lambda expression
      case Lambda(_, body) =>
        go(body)

      // Field selection: x.fieldName
      case Select(qualifier, name) =>
        go(qualifier) :+ name

      // Terminal: the lambda parameter itself (identity)
      case Ident(_) =>
        Nil

      case other =>
        report.errorAndAbort(
          s"Unsupported selector expression. Expected _.field or _.field1.field2, " +
            s"got: ${other.show}"
        )
    }

    go(termCast)
  }
}
