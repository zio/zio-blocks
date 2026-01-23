package zio.blocks.schema

import zio.blocks.schema.json._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

package object json {
  implicit class JsonStringContext(val sc: StringContext) extends AnyVal {
    def json(args: Any*): Json = macro JsonInterpolatorMacros.jsonImpl
  }
}

private object JsonInterpolatorMacros {
  def jsonImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Json] = {
    import c.universe._

    val parts = c.prefix.tree match {
      case Apply(_, List(Apply(_, rawParts))) =>
        rawParts.map {
          case Literal(Constant(part: String)) => part
          case _                               => c.abort(c.enclosingPosition, "Expected string literal parts")
        }
      case _ => c.abort(c.enclosingPosition, "Expected StringContext")
    }

    // Detect which interpolations are inside JSON string literals
    val inStringLiteral = detectStringLiteralContext(parts)

    val scExpr   = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)
    val argsExpr = c.Expr[Seq[Any]](q"Seq(..$args)")
    val contextExpr = c.Expr[Array[Boolean]](q"Array(..$inStringLiteral)")

    reify(JsonInterpolatorRuntime.jsonWithInterpolationAndContext(scExpr.splice, argsExpr.splice, contextExpr.splice))
  }

  /**
   * Detects which interpolations are inside JSON string literals.
   * Returns a list of booleans, one for each interpolation.
   */
  private def detectStringLiteralContext(parts: List[String]): List[Boolean] = {
    if (parts.size <= 1) return Nil

    val result = scala.collection.mutable.ArrayBuffer[Boolean]()
    var insideString = false
    var i = 0
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
    var i = 0
    while (i < s.length) {
      if (s.charAt(i) == '"') {
        // Check if it's escaped
        var backslashCount = 0
        var j = i - 1
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
