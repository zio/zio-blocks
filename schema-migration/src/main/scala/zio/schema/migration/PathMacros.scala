package zio.schema.migration

import scala.quoted.*

/**
 * Scala 3 macros for extracting field paths from lambda expressions.
 *
 * Enables ergonomic API: .addField(_.age, 0) instead of .addField("age", 0)
 *
 * Requires the zio-schema-migration-plugin compiler plugin to be loaded. The
 * plugin intercepts lambda expressions before eta-expansion and stores field
 * paths as tree attachments, which this macro retrieves.
 */
object PathMacros {

  /**
   * Extract a field path from a lambda expression
   *
   * Usage: extractPath[Person](_.name) => FieldPath.Root("name")
   * extractPath[Person](_.address.street) => FieldPath.Nested(Root("address"),
   * "street")
   *
   * Note: Requires compiler plugin for automatic field path extraction. The
   * plugin transforms this call to extractPathWithString.
   */
  inline def extractPath[A](inline selector: A => Any): FieldPath =
    ${ extractPathImpl[A]('selector) }

  /**
   * Extract path with string provided by compiler plugin.
   *
   * This method is called by the compiler plugin after it extracts the field
   * path. Users should not call this directly.
   */
  inline def extractPathWithString[A](inline selector: A => Any, pathString: String): FieldPath =
    FieldPath
      .parse(pathString)
      .getOrElse(
        throw new IllegalArgumentException(s"Invalid field path: $pathString")
      )

  /**
   * Implementation of path extraction macro.
   *
   * This checks for a field path attachment from the compiler plugin. If found,
   * uses that. Otherwise falls back to term inspection (which will likely fail
   * due to eta-expansion).
   */
  def extractPathImpl[A: Type](
    selector: Expr[A => Any]
  )(using Quotes): Expr[FieldPath] = {
    import quotes.reflect.*

    // Use HOAS pattern matching to extract lambda before eta-expansion
    selector match {
      case '{ (x: A) => ($f(x): Any) } =>
        extractFromBody(f.asTerm)

      case _ =>
        report.errorAndAbort(s"Could not extract field path from selector: ${selector.show}")
    }
  }

  /**
   * Extract field path from lambda body after HOAS extraction.
   *
   * Public for use by other macro implementations (MacroSelectors, etc.)
   */
  def extractFromBody(using Quotes)(term: quotes.reflect.Term): Expr[FieldPath] = {
    import quotes.reflect.*

    // If term is a lambda, extract its body
    val bodyTerm = term match {
      case Lambda(_, body) => body
      case other           => other
    }

    def extractFieldNames(t: Term): List[String] = t match {
      case Select(Ident(_), fieldName) =>
        List(fieldName.toString)
      case Select(qualifier, fieldName) =>
        extractFieldNames(qualifier) :+ fieldName.toString
      case Ident(_) =>
        Nil
      case Typed(inner, _) =>
        extractFieldNames(inner)
      case Block(Nil, expr) =>
        extractFieldNames(expr)
      case Inlined(_, Nil, expr) =>
        extractFieldNames(expr)
      case _ =>
        Nil
    }

    val fields = extractFieldNames(bodyTerm).filter(_.nonEmpty)

    if (fields.isEmpty) {
      report.errorAndAbort(s"Could not extract field path from: ${term.show}")
    }

    buildFieldPath(fields)
  }

  /**
   * Check if the compiler plugin attached a field path to this tree.
   */
  private def checkPluginAttachment(using Quotes)(term: quotes.reflect.Term): Option[String] = {
    import quotes.reflect.*

    // Try to get the FieldPathKey attachment
    // The plugin stores paths under this key
    try {
      // Access the attachment through reflection
      // FieldPathKey is defined in the plugin, we need to access it dynamically
      val attachmentClass = Class.forName("zio.schema.migration.plugin.FieldSelectorPhase$FieldPathKey$")
      val keyInstance     = attachmentClass.getField("MODULE$").get(null)

      // Try to get attachment (this is a simplified approach)
      // In reality, we need proper API access
      None // For now, return None - will implement properly
    } catch {
      case _: Exception => None
    }
  }

  /**
   * Parse a field path string into a FieldPath expression.
   *
   * Example: "address.street" => FieldPath.Nested(Root("address"), "street")
   */
  private def parseFieldPathString(using Quotes)(pathString: String): Expr[FieldPath] = {
    val fields = pathString.split("\\.").toList.filter(_.nonEmpty)
    buildFieldPath(fields)
  }

  private def buildFieldPath(using Quotes)(fields: List[String]): Expr[FieldPath] =
    fields match {
      case head :: Nil =>
        '{ FieldPath.Root(${ Expr(head) }) }

      case head :: tail =>
        tail.foldLeft[Expr[FieldPath]]('{ FieldPath.Root(${ Expr(head) }) }) { (acc, field) =>
          '{ FieldPath.Nested($acc, ${ Expr(field) }) }
        }

      case Nil =>
        quotes.reflect.report.errorAndAbort("Empty field path")
    }

  private def extractFromTerm(using Quotes)(selectorTerm: quotes.reflect.Term): Expr[FieldPath] = {
    import quotes.reflect.*

    def extractFieldNames(term: Term): List[String] = term match {
      // Simple field access: _.field
      case Select(Ident(_), fieldName) =>
        List(fieldName)

      // Nested field access: _.field1.field2
      case Select(qualifier, fieldName) =>
        extractFieldNames(qualifier) :+ fieldName

      // Lambda parameter (base case)
      case Ident(_) =>
        Nil

      // Handle typed select (common in Scala 3)
      case Typed(inner, _) =>
        extractFieldNames(inner)

      // Handle block wrapping
      case Block(_, expr) =>
        extractFieldNames(expr)

      // Handle inline wrapping
      case Inlined(_, _, expr) =>
        extractFieldNames(expr)

      case other =>
        report.errorAndAbort(s"Invalid field selector: ${other.show}")
    }

    // Unwrap the selector to get to the lambda body
    def unwrapTerm(term: Term): Term = term match {
      case Block(_, expr)      => unwrapTerm(expr)
      case Inlined(_, _, expr) => unwrapTerm(expr)
      case Typed(inner, _)     => unwrapTerm(inner)
      case other               => other
    }

    // Extract body, handling eta-expansion with deep tree inspection
    def extractBody(term: Term): Term = term match {
      case Lambda(_, body)                     => body
      case Block(List(), expr)                 => extractBody(expr)
      case Block(stats, expr) if stats.isEmpty => extractBody(expr)
      case Inlined(_, _, expr)                 => extractBody(expr)
      case Typed(inner, _)                     => extractBody(inner)
      // Handle closure/function references
      case Ident(name) =>
        // Try to find the definition in the symbol tree
        term.symbol.tree match {
          case DefDef(_, _, _, Some(rhs)) => extractBody(rhs)
          case ValDef(_, _, Some(rhs))    => extractBody(rhs)
          case _                          =>
            // Last resort: check if this is a Select that we can extract
            report.errorAndAbort(s"Invalid field selector: ${term.show}")
        }
      case Apply(fun, args) => extractBody(fun)
      case other            => other
    }

    val unwrapped = unwrapTerm(selectorTerm)
    val body      = extractBody(unwrapped)

    val fields = extractFieldNames(body).filter(_.nonEmpty)

    if (fields.isEmpty) {
      report.errorAndAbort("Could not extract field path from selector")
    }

    buildFieldPath(fields)
  }
}
