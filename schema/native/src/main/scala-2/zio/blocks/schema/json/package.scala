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
          case Literal(Constant(part: String)) => processEscapes(part)
          case _                               => c.abort(c.enclosingPosition, "Expected string literal parts")
        }
      case _ => c.abort(c.enclosingPosition, "Expected StringContext")
    }

    // Note: We skip compile-time JSON validation on Native because the compiler
    // cannot invoke the runtime JsonInterpolatorRuntime class during macro expansion.
    // JSON structure validation will happen at runtime instead.

    // Analyze contexts and validate types at compile time
    if (args.nonEmpty) {
      val contexts = analyzeContexts(parts)
      args.zip(contexts).foreach { case (argExpr, ctx) =>
        validateInterpolation(c)(argExpr, ctx)
      }
    }

    // Generate the runtime call with processed parts
    val partsExprs = parts.map(p => q"$p")
    val argsExpr   = c.Expr[Seq[Any]](q"Seq(..$args)")
    c.Expr[Json](q"JsonInterpolatorRuntime.jsonWithInterpolation(new StringContext(..$partsExprs), $argsExpr)")
  }

  private def validateInterpolation(c: blackbox.Context)(argExpr: c.Expr[Any], ctx: String): Unit = {
    import c.universe._

    val argType = argExpr.tree.tpe.widen

    ctx match {
      case "key" =>
        // Key position requires Stringable[A]
        val stringableType = appliedType(typeOf[Stringable[_]].typeConstructor, argType)
        val stringable     = c.inferImplicitValue(stringableType, silent = true)
        if (stringable == EmptyTree) {
          c.abort(
            argExpr.tree.pos,
            s"Type ${argType} cannot be used in key position. " +
              "Only stringable types (primitives, temporal types, UUID, Currency) are allowed as JSON object keys."
          )
        }

      case "string" =>
        // String literal position requires Stringable[A]
        val stringableType = appliedType(typeOf[Stringable[_]].typeConstructor, argType)
        val stringable     = c.inferImplicitValue(stringableType, silent = true)
        if (stringable == EmptyTree) {
          c.abort(
            argExpr.tree.pos,
            s"Type ${argType} cannot be interpolated inside a string literal. " +
              "Only stringable types (primitives, temporal types, UUID, Currency) are allowed inside JSON strings."
          )
        }

      case "value" =>
        // Value position - allow any type (runtime handles Map, Option, Iterable, Array, Json, primitives)
        // This preserves backward compatibility with existing tests
        ()
    }
  }

  private def analyzeContexts(parts: List[String]): List[String] =
    if (parts.length <= 1) Nil
    else {
      val builder  = List.newBuilder[String]
      var inString = false
      var i        = 0
      while (i < parts.length - 1) {
        val before = parts(i)
        val after  = parts(i + 1)
        // Update string state by processing the current part
        inString = updateStringState(before, inString)
        val ctx =
          if (inString) "string"
          else if (isKeyPosition(before, after)) "key"
          else "value"
        builder += ctx
        i += 1
      }
      builder.result()
    }

  /**
   * Updates the string state by scanning the segment, starting from the given
   * initial state. Returns true if we end up inside a string literal, false
   * otherwise.
   */
  private def updateStringState(segment: String, initialInString: Boolean): Boolean = {
    var inString = initialInString
    var i        = 0
    while (i < segment.length) {
      val c = segment.charAt(i)
      if (c == '"') {
        // Count consecutive backslashes immediately preceding this quote.
        // If the count is even (including zero), the quote is not escaped.
        // If the count is odd, the quote is escaped.
        var backslashCount = 0
        var j              = i - 1
        while (j >= 0 && segment.charAt(j) == '\\') {
          backslashCount += 1
          j -= 1
        }
        if ((backslashCount & 1) == 0) {
          inString = !inString
        }
      }
      i += 1
    }
    inString
  }

  private def isKeyPosition(before: String, after: String): Boolean = {
    val trimmedBefore = before.trim
    val trimmedAfter  = after.trim
    (trimmedBefore.endsWith("{") || trimmedBefore.endsWith(",")) &&
    trimmedAfter.startsWith(":")
  }

  /**
   * Process escape sequences in a string literal. This handles common escape
   * sequences like \n, \t, \", \\, etc.
   */
  private def processEscapes(s: String): String = {
    val sb  = new StringBuilder
    var i   = 0
    val len = s.length
    while (i < len) {
      val c = s.charAt(i)
      if (c == '\\' && i + 1 < len) {
        val next = s.charAt(i + 1)
        next match {
          case 'n'                => sb.append('\n'); i += 2
          case 't'                => sb.append('\t'); i += 2
          case 'r'                => sb.append('\r'); i += 2
          case 'b'                => sb.append('\b'); i += 2
          case 'f'                => sb.append('\f'); i += 2
          case '\\'               => sb.append('\\'); i += 2
          case '"'                => sb.append('"'); i += 2
          case '\''               => sb.append('\''); i += 2
          case 'u' if i + 5 < len =>
            // Unicode escape: \uXXXX
            try {
              val hex = s.substring(i + 2, i + 6)
              sb.append(Integer.parseInt(hex, 16).toChar)
              i += 6
            } catch {
              case _: NumberFormatException =>
                sb.append(c)
                i += 1
            }
          case _ =>
            sb.append(c)
            i += 1
        }
      } else {
        sb.append(c)
        i += 1
      }
    }
    sb.toString
  }
}
