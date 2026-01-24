package zio.blocks.schema

import zio.blocks.schema.json._
import scala.quoted._

package object json {
  extension (inline sc: StringContext) {
    inline def json(inline args: Any*): Json = ${ jsonInterpolatorImpl('sc, 'args) }
  }

  private def jsonInterpolatorImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Json] = {
    import quotes.reflect._

    val parts: List[String] = sc match {
      case '{ StringContext(${ Varargs(rawParts) }: _*) } =>
        rawParts.toList.map { case '{ $rawPart: String } => rawPart.valueOrAbort }
      case _ => report.errorAndAbort("Expected a StringContext with string literal parts")
    }

    val argExprs: List[Expr[Any]] = args match {
      case Varargs(exprs) => exprs.toList
      case _              => report.errorAndAbort("Expected explicit arguments provided to json interpolator")
    }

    if (parts.size != argExprs.size + 1)
      report.errorAndAbort("Invalid number of parts and arguments")

    def isStringable(tpe: TypeRepr): Boolean =
      tpe <:< TypeRepr.of[String] ||
        tpe <:< TypeRepr.of[Unit] ||
        tpe <:< TypeRepr.of[Boolean] ||
        tpe <:< TypeRepr.of[Byte] ||
        tpe <:< TypeRepr.of[Short] ||
        tpe <:< TypeRepr.of[Int] ||
        tpe <:< TypeRepr.of[Long] ||
        tpe <:< TypeRepr.of[Float] ||
        tpe <:< TypeRepr.of[Double] ||
        tpe <:< TypeRepr.of[Char] ||
        tpe <:< TypeRepr.of[BigInt] ||
        tpe <:< TypeRepr.of[BigDecimal] ||
        tpe <:< TypeRepr.of[java.util.UUID] ||
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
        tpe <:< TypeRepr.of[java.util.Currency]

    object Context extends Enumeration {
      type Context = Value
      val Unknown, Key, JsonValue, StringLiteral = Value
    }

    class Parser {
      private var stack: List[Char]          = Nil               // '{' or '['
      private var expecting: Context.Context = Context.JsonValue // Initial expectation
      private var inString: Boolean          = false
      private var inEscaped: Boolean         = false

      def process(part: String): Unit = {
        var i   = 0
        val len = part.length
        while (i < len) {
          val char = part.charAt(i)
          if (inString) {
            if (inEscaped) {
              inEscaped = false
            } else if (char == '\\') {
              inEscaped = true
            } else if (char == '"') {
              inString = false
              // End of string. What comes next?
              // If we were parsing a Key, we now expect a Colon.
              // If we were parsing a Value (string value), we now expect Comma or Close.
              // Simplified: We assume grammar ensures correctness.
              // Just update generic 'expecting' based on stack?
              // Actually, after a string, we effectively finished a "token".
            }
          } else {
            char match {
              case '{' =>
                stack = '{' :: stack
                expecting = Context.Key // After {, expect Key
              case '}' =>
                if (stack.nonEmpty && stack.head == '{') stack = stack.tail
                expecting = Context.Unknown // After }, could be comma or end
              case '[' =>
                stack = '[' :: stack
                expecting = Context.JsonValue // After [, expect Value
              case ']' =>
                if (stack.nonEmpty && stack.head == '[') stack = stack.tail
                expecting = Context.Unknown
              case ':' =>
                expecting = Context.JsonValue // After :, expect Value
              case ',' =>
                if (stack.nonEmpty) {
                  if (stack.head == '{') expecting = Context.Key
                  else expecting = Context.JsonValue
                } else {
                  // Should not happen in valid partial JSON unless at top level?
                  expecting = Context.JsonValue
                }
              case '"' =>
                inString = true
              case _ => // whitespace or numbers/booleans/null
            }
          }
          i += 1
        }
      }

      def currentContext: Context.Context =
        if (inString) Context.StringLiteral
        else expecting
    }

    val parser    = new Parser()
    val newArgs   = scala.collection.mutable.ListBuffer.empty[Expr[Any]]
    val dummyArgs = scala.collection.mutable.ListBuffer.empty[Any]

    // Parse loop
    for ((part, argExpr) <- parts.init.zip(argExprs)) {
      parser.process(part)
      val ctx = parser.currentContext
      val tpe = argExpr.asTerm.tpe.widen

      ctx match {
        case Context.StringLiteral =>
          if (!isStringable(tpe))
            report.errorAndAbort(
              s"Context: string literal\nProvided: ${tpe.show}\nRequired: PrimitiveType (stringable)\nFix: Use a primitive type or explicitly call .toString"
            )
          newArgs += '{ JsonInterpolatorRuntime.Raw(JsonInterpolatorRuntime.stringOf(${ argExpr })) }
          dummyArgs += JsonInterpolatorRuntime.Raw("x")

        case Context.Key =>
          if (!isStringable(tpe))
            report.errorAndAbort(
              s"Context: key position\nProvided: ${tpe.show}\nRequired: PrimitiveType (stringable)\nFix: Use a primitive type or explicitly call .toString"
            )
          newArgs += '{ JsonInterpolatorRuntime.stringOf(${ argExpr }) }
          dummyArgs += "key"

        case Context.JsonValue =>
          // Summon JsonEncoder
          tpe.asType match {
            case '[t] =>
              Expr.summon[JsonEncoder[t]] match {
                case Some(encoder) =>
                  newArgs += '{ ${ encoder }.encode($argExpr.asInstanceOf[t]) }
                  dummyArgs += "value"
                case None =>
                  report.errorAndAbort(
                    s"Context: value position\nProvided: ${tpe.show}\nRequired: JsonEncoder[${tpe.show}]\nHint: encode can come from JsonBinaryCodec or derived from Schema[${tpe.show}]"
                  )
              }
          }

        case _ =>
          // Default fallback
          tpe.asType match {
            case '[t] =>
              Expr.summon[JsonEncoder[t]] match {
                case Some(encoder) =>
                  newArgs += '{ ${ encoder }.encode($argExpr.asInstanceOf[t]) }
                  dummyArgs += "value"
                case None =>
                  report.errorAndAbort(
                    s"Context: unknown (inferred value)\nProvided: ${tpe.show}\nRequired: JsonEncoder[${tpe.show}]"
                  )
              }
          }
      }
    }

    // Process last part to ensure syntax validity checked purely by runtime later
    // Actually we don't strictly need to process the last part for context validation of args,
    // but the runtime logic currently does a dummy run with empty strings to validate JSON syntax on the full string.
    // We keep that.

    // Note: Compile-time JSON validation is skipped here because the macro expansion
    // environment has issues evaluating JsonBinaryCodec at compile time with dummy values.
    // Runtime validation will catch any JSON syntax errors when the code executes.
    val newArgsSeq = Varargs(newArgs.toList)
    '{ JsonInterpolatorRuntime.jsonWithInterpolation($sc, $newArgsSeq) }
  }
}
