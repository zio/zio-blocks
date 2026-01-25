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

    // Validate JSON syntax with placeholder values
    try {
      val placeholders = (0 until parts.length - 1).map { i =>
        val context = detectInterpolationContext(parts, i)
        context match {
          case InterpolationContext.Key           => "x"
          case InterpolationContext.Value         => "null"
          case InterpolationContext.StringLiteral => "x"
        }
      }
      JsonInterpolatorRuntime.jsonWithInterpolation(new StringContext(parts: _*), placeholders)
    } catch {
      case error if NonFatal(error) => report.errorAndAbort(s"Invalid JSON literal: ${error.getMessage}")
    }

    // Type-check each interpolation based on its context
    args match {
      case Varargs(argExprs) =>
        argExprs.zipWithIndex.foreach { case (arg, idx) =>
          val context = detectInterpolationContext(parts, idx)
          context match {
            case InterpolationContext.Key =>
              checkStringableType(arg, "key position")
            case InterpolationContext.Value =>
              checkHasJsonEncoder(arg, "value position")
            case InterpolationContext.StringLiteral =>
              checkStringableType(arg, "string literal")
          }
        }
      case _ => // No args to check
    }

    '{ JsonInterpolatorRuntime.jsonWithInterpolation($sc, $args) }
  }

  private enum InterpolationContext {
    case Key, Value, StringLiteral
  }

  private def detectInterpolationContext(parts: Seq[String], argIndex: Int): InterpolationContext = {
    // Track string literal context across all parts up to this point
    var inStringLiteral = false
    var i               = 0
    while (i <= argIndex) {
      if (isInStringLiteral(parts(i))) {
        inStringLiteral = !inStringLiteral
      }
      i += 1
    }

    if (inStringLiteral) {
      InterpolationContext.StringLiteral
    } else {
      val before = parts(argIndex)
      val after  = if (argIndex + 1 < parts.length) parts(argIndex + 1) else ""
      // Check if this is a key position (after '{' or ',' and before ':')
      if (isKeyPosition(before, after)) {
        InterpolationContext.Key
      } else {
        InterpolationContext.Value
      }
    }
  }

  private def isInStringLiteral(text: String): Boolean = {
    var inQuote = false
    var i       = 0
    while (i < text.length) {
      val c = text.charAt(i)
      if (c == '"') {
        // Count consecutive backslashes before this quote
        var backslashCount = 0
        var j              = i - 1
        while (j >= 0 && text.charAt(j) == '\\') {
          backslashCount += 1
          j -= 1
        }
        // If there's an even number of backslashes (including 0), the quote is not escaped
        if (backslashCount % 2 == 0) {
          inQuote = !inQuote
        }
      }
      i += 1
    }
    inQuote
  }

  private def isKeyPosition(before: String, after: String): Boolean = {
    val trimmedBefore = before.reverse.dropWhile(c => c.isWhitespace).reverse
    val trimmedAfter  = after.dropWhile(c => c.isWhitespace)

    (trimmedBefore.endsWith("{") || trimmedBefore.endsWith(",")) && trimmedAfter.startsWith(":")
  }

  private def checkStringableType(arg: Expr[Any], context: String)(using Quotes): Unit = {
    import quotes.reflect._

    val tpe = arg.asTerm.tpe.widen

    // Check if the type is a stringable primitive type
    val isStringable = tpe <:< TypeRepr.of[String] ||
      tpe <:< TypeRepr.of[Boolean] ||
      tpe <:< TypeRepr.of[Byte] ||
      tpe <:< TypeRepr.of[Short] ||
      tpe <:< TypeRepr.of[Int] ||
      tpe <:< TypeRepr.of[Long] ||
      tpe <:< TypeRepr.of[Float] ||
      tpe <:< TypeRepr.of[Double] ||
      tpe <:< TypeRepr.of[Char] ||
      tpe <:< TypeRepr.of[BigDecimal] ||
      tpe <:< TypeRepr.of[BigInt] ||
      tpe <:< TypeRepr.of[java.time.DayOfWeek] ||
      tpe <:< TypeRepr.of[java.time.Duration] ||
      tpe <:< TypeRepr.of[java.time.Instant] ||
      tpe <:< TypeRepr.of[java.time.LocalDate] ||
      tpe <:< TypeRepr.of[java.time.LocalDateTime] ||
      tpe <:< TypeRepr.of[java.time.LocalTime] ||
      tpe <:< TypeRepr.of[java.time.Month] ||
      tpe <:< TypeRepr.of[java.time.MonthDay] ||
      tpe <:< TypeRepr.of[java.time.OffsetDateTime] ||
      tpe <:< TypeRepr.of[java.time.OffsetTime] ||
      tpe <:< TypeRepr.of[java.time.Period] ||
      tpe <:< TypeRepr.of[java.time.Year] ||
      tpe <:< TypeRepr.of[java.time.YearMonth] ||
      tpe <:< TypeRepr.of[java.time.ZoneId] ||
      tpe <:< TypeRepr.of[java.time.ZoneOffset] ||
      tpe <:< TypeRepr.of[java.time.ZonedDateTime] ||
      tpe <:< TypeRepr.of[java.util.UUID] ||
      tpe <:< TypeRepr.of[java.util.Currency]

    if (!isStringable) {
      val typeStr = tpe.show
      report.errorAndAbort(
        s"Type error in JSON interpolation at $context:\n" +
          s"  Found: $typeStr\n" +
          s"  Required: A stringable type (primitive types as defined in PrimitiveType)\n" +
          s"  Hint: Only primitive types can be used in $context.\n" +
          s"        Supported types: String, Boolean, Byte, Short, Int, Long, Float, Double, Char,\n" +
          s"        BigDecimal, BigInt, java.time.*, java.util.UUID, java.util.Currency"
      )
    }
  }

  private def checkHasJsonEncoder(arg: Expr[Any], context: String)(using Quotes): Unit =
    // NOTE:
    // The JSON interpolator runtime does not use JsonEncoder instances; it
    // pattern-matches on a fixed set of types and otherwise falls back to
    // `toString`. To avoid giving a misleading guarantee at compile time, this
    // check is intentionally a no-op and does not require an implicit
    // JsonEncoder for `arg`.
    ()
}
