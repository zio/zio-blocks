package zio.blocks.schema

import zio.blocks.schema.json._
import scala.quoted._

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

    val contextExpr = Expr.ofList(inStringLiteral.map(Expr(_)))
    '{ JsonInterpolatorRuntime.jsonWithInterpolationAndContext($sc, $args, $contextExpr.toArray) }
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
