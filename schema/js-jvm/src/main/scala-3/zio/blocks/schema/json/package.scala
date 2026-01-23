package zio.blocks.schema

import zio.blocks.schema.json._
import scala.quoted._
import scala.util.control.NonFatal

package object json {
  extension (inline sc: StringContext) {
    inline def json(inline args: Any*): Json = ${ jsonInterpolatorImpl('sc, 'args) }
  }

  private def jsonInterpolatorImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Json] = {
    import quotes.reflect._

    val parts = sc match {
      case '{ StringContext(${ Varargs(rawParts) }: _*) } =>
        rawParts.map { case '{ $rawPart: String } => rawPart.valueOrAbort }
      case _ => report.errorAndAbort("Expected a StringContext with string literal parts")
    }

    // Detect which interpolations are inside JSON string literals
    val inStringLiteral = detectStringLiteralContext(parts)

    // Note: Compile-time type checking is disabled for Scala 3 due to limitations
    // with implicit search in macros. The Scala 2 version has full compile-time checking.
    // Runtime type checking will still catch any type errors.

    try {
      // Validate JSON syntax at compile time
      // Build a complete JSON string for validation by reconstructing with placeholders
      val validationString = new StringBuilder()
      var i                = 0
      while (i < parts.length) {
        validationString.append(parts(i))
        if (i < inStringLiteral.length) {
          if (inStringLiteral(i)) {
            // Inside string literal - add placeholder text
            validationString.append("x")
          } else {
            // Value or key position - add empty string
            validationString.append("\"\"")
          }
        }
        i += 1
      }

      // Validate the reconstructed JSON
      Json.parse(validationString.toString) match {
        case Left(error) => report.errorAndAbort(s"Invalid JSON literal: ${error.getMessage}")
        case Right(_)    => // Valid JSON
      }

      val contextExpr = Expr.ofList(inStringLiteral.map(Expr(_)))
      '{ JsonInterpolatorRuntime.jsonWithInterpolationAndContext($sc, $args, $contextExpr.toArray) }
    } catch {
      case error if NonFatal(error) => report.errorAndAbort(s"Invalid JSON literal: ${error.getMessage}")
    }
  }

  /**
   * Detects which interpolations are inside JSON string literals. Returns a
   * list of booleans, one for each interpolation.
   */
  private def detectStringLiteralContext(parts: Seq[String]): List[Boolean] = {
    if (parts.size <= 1) return Nil

    val result       = scala.collection.mutable.ArrayBuffer[Boolean]()
    var insideString = false
    var i            = 0
    while (i < parts.size - 1) {
      val part = parts(i)
      // Count unescaped quotes to determine if we toggle string state
      val quoteCount = countUnescapedQuotes(part)
      if (quoteCount % 2 == 1) {
        insideString = !insideString
      }
      result += insideString
      i += 1
    }
    result.toList
  }

  /**
   * Detects which interpolations are in key positions (before a colon). Returns
   * a list of booleans, one for each interpolation.
   */
  private def detectKeyPositions(parts: Seq[String], inStringLiteral: List[Boolean]): List[Boolean] = {
    if (parts.size <= 1) return Nil

    val result = scala.collection.mutable.ArrayBuffer[Boolean]()
    var i      = 0
    while (i < parts.size - 1) {
      // An interpolation is in key position if:
      // 1. It's not inside a string literal
      // 2. The next part starts with a colon (after optional whitespace)
      val isKey = if (i < inStringLiteral.length && !inStringLiteral(i)) {
        val nextPart = if (i + 1 < parts.length) parts(i + 1) else ""
        nextPart.trim.startsWith(":")
      } else {
        false
      }
      result += isKey
      i += 1
    }
    result.toList
  }

  /**
   * Counts unescaped double quotes in a string.
   */
  private def countUnescapedQuotes(s: String): Int = {
    var count = 0
    var i     = 0
    while (i < s.length) {
      if (s.charAt(i) == '"') {
        // Check if it's escaped
        var backslashCount = 0
        var j              = i - 1
        while (j >= 0 && s.charAt(j) == '\\') {
          backslashCount += 1
          j -= 1
        }
        // If even number of backslashes (including 0), the quote is not escaped
        if (backslashCount % 2 == 0) {
          count += 1
        }
      }
      i += 1
    }
    count
  }
}
