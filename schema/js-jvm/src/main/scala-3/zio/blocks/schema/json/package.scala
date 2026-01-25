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
        rawParts.map { expr =>
          expr.asTerm match {
            case Literal(StringConstant(value)) => processEscapes(value)
            case _                              => report.errorAndAbort(s"Expected string literal, got: ${expr.show}")
          }
        }
      case _ => report.errorAndAbort("Expected a StringContext with string literal parts")
    }

    // Validate JSON structure
    try {
      JsonInterpolatorRuntime.jsonWithInterpolation(new StringContext(parts: _*), (2 to parts.size).map(_ => ""))
    } catch {
      case error if NonFatal(error) =>
        val msg = Option(error.getMessage).getOrElse(error.getClass.getName)
        report.errorAndAbort(s"Invalid JSON literal: $msg")
    }

    // Extract individual argument expressions for type checking
    val argExprs: List[Expr[Any]] = args match {
      case Varargs(exprs) => exprs.toList
      case _              => Nil
    }

    // Analyze contexts and validate types at compile time
    if (argExprs.nonEmpty) {
      val contexts = analyzeContexts(parts.toList)
      argExprs.zip(contexts).foreach { case (argExpr, ctx) =>
        validateInterpolation(argExpr, ctx)
      }
    }

    // Create expressions for the processed parts
    val partsExprs: Seq[Expr[String]]   = parts.map(p => Expr(p))
    val partsVarargs: Expr[Seq[String]] = Varargs(partsExprs)

    // Generate runtime call with processed StringContext
    '{ JsonInterpolatorRuntime.jsonWithInterpolation(new StringContext($partsVarargs*), $args) }
  }

  /**
   * Validates an interpolation based on its context. Reports compile-time
   * errors for invalid types.
   */
  private def validateInterpolation(argExpr: Expr[Any], ctx: String)(using Quotes): Unit = {
    import quotes.reflect._

    val argType = argExpr.asTerm.tpe.widen

    ctx match {
      case "key" =>
        // Key position requires Stringable[A]
        val stringableType = TypeRepr.of[Stringable].appliedTo(argType)
        Implicits.search(stringableType) match {
          case _: ImplicitSearchSuccess => // OK
          case _: ImplicitSearchFailure =>
            report.errorAndAbort(
              s"Type ${argType.show} cannot be used in key position. " +
                "Only stringable types (primitives, temporal types, UUID, Currency) are allowed as JSON object keys.",
              argExpr.asTerm.pos
            )
        }

      case "string" =>
        // String literal position requires Stringable[A]
        val stringableType = TypeRepr.of[Stringable].appliedTo(argType)
        Implicits.search(stringableType) match {
          case _: ImplicitSearchSuccess => // OK
          case _: ImplicitSearchFailure =>
            report.errorAndAbort(
              s"Type ${argType.show} cannot be interpolated inside a string literal. " +
                "Only stringable types (primitives, temporal types, UUID, Currency) are allowed inside JSON strings.",
              argExpr.asTerm.pos
            )
        }

      case "value" =>
        // Value position - allow any type (runtime handles Map, Option, Iterable, Array, Json, primitives)
        // This preserves backward compatibility with existing tests
        ()
    }
  }

  /**
   * Analyzes the interpolation contexts based on JSON structure. Returns a list
   * of contexts ("key", "value", or "string") for each interpolation point.
   */
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
