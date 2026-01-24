package zio.blocks.schema

import zio.blocks.schema.json._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

import scala.util.control.NonFatal

package object json {
  implicit class JsonStringContext(val sc: StringContext) extends AnyVal {
    def json(args: Any*): Json = macro JsonInterpolatorMacros.jsonImpl
  }
}

private object JsonInterpolatorMacros {
  def jsonImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Json] = {
    import c.universe._

    val parts: List[String] = c.prefix.tree match {
      case Apply(_, List(Apply(_, rawParts))) =>
        rawParts.map {
          case Literal(Constant(part: String)) => part
          case _                               => c.abort(c.enclosingPosition, "Expected string literal parts")
        }
      case _ => c.abort(c.enclosingPosition, "Expected StringContext")
    }

    if (parts.size != args.size + 1)
      c.abort(c.enclosingPosition, "Invalid number of parts and arguments")

    def isStringable(tpe: Type): Boolean =
      tpe <:< typeOf[String] ||
        tpe <:< typeOf[Unit] ||
        tpe <:< typeOf[Boolean] ||
        tpe <:< typeOf[Byte] ||
        tpe <:< typeOf[Short] ||
        tpe <:< typeOf[Int] ||
        tpe <:< typeOf[Long] ||
        tpe <:< typeOf[Float] ||
        tpe <:< typeOf[Double] ||
        tpe <:< typeOf[Char] ||
        tpe <:< typeOf[BigInt] ||
        tpe <:< typeOf[BigDecimal] ||
        tpe <:< typeOf[java.util.UUID] ||
        tpe <:< typeOf[java.time.DayOfWeek] ||
        tpe <:< typeOf[java.time.Duration] ||
        tpe <:< typeOf[java.time.Instant] ||
        tpe <:< typeOf[java.time.LocalDate] ||
        tpe <:< typeOf[java.time.LocalDateTime] ||
        tpe <:< typeOf[java.time.LocalTime] ||
        tpe <:< typeOf[java.time.Month] ||
        tpe <:< typeOf[java.time.MonthDay] ||
        tpe <:< typeOf[java.time.OffsetDateTime] ||
        tpe <:< typeOf[java.time.OffsetTime] ||
        tpe <:< typeOf[java.time.Period] ||
        tpe <:< typeOf[java.time.Year] ||
        tpe <:< typeOf[java.time.YearMonth] ||
        tpe <:< typeOf[java.time.ZoneId] ||
        tpe <:< typeOf[java.time.ZoneOffset] ||
        tpe <:< typeOf[java.time.ZonedDateTime] ||
        tpe <:< typeOf[java.util.Currency]

    object Context extends Enumeration {
      type Context = Value
      val Unknown, Key, JsonValue, StringLiteral = Value
    }

    class Parser {
      private var stack: List[Char]          = Nil
      private var expecting: Context.Context = Context.JsonValue
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
            }
          } else {
            char match {
              case '{' =>
                stack = '{' :: stack
                expecting = Context.Key
              case '}' =>
                if (stack.nonEmpty && stack.head == '{') stack = stack.tail
                expecting = Context.Unknown
              case '[' =>
                stack = '[' :: stack
                expecting = Context.JsonValue
              case ']' =>
                if (stack.nonEmpty && stack.head == '[') stack = stack.tail
                expecting = Context.Unknown
              case ':' =>
                expecting = Context.JsonValue
              case ',' =>
                if (stack.nonEmpty) {
                  if (stack.head == '{') expecting = Context.Key
                  else expecting = Context.JsonValue
                } else expecting = Context.JsonValue
              case '"' =>
                inString = true
              case _ =>
            }
          }
          i += 1
        }
      }

      def currentContext: Context.Context =
        if (inString) Context.StringLiteral
        else expecting
    }

    val parser    = new Parser
    val newArgs   = scala.collection.mutable.ListBuffer.empty[c.Expr[Any]]
    val dummyArgs = scala.collection.mutable.ListBuffer.empty[Any]

    parts.init.zip(args).foreach { case (part, arg) =>
      parser.process(part)
      val ctx = parser.currentContext
      val tpe = arg.tree.tpe

      ctx match {
        case Context.StringLiteral =>
          if (!isStringable(tpe))
            c.abort(
              c.enclosingPosition,
              s"Context: string literal\nProvided: $tpe\nRequired: PrimitiveType (stringable)\nFix: Use a primitive type or explicitly call .toString"
            )
          newArgs += c.Expr[Any](
            q"zio.blocks.schema.json.JsonInterpolatorRuntime.Raw(zio.blocks.schema.json.JsonInterpolatorRuntime.stringOf($arg))"
          )
          dummyArgs += JsonInterpolatorRuntime.Raw("x")

        case Context.Key =>
          if (!isStringable(tpe))
            c.abort(
              c.enclosingPosition,
              s"Context: key position\nProvided: $tpe\nRequired: PrimitiveType (stringable)\nFix: Use a primitive type or explicitly call .toString"
            )
          newArgs += c.Expr[Any](q"zio.blocks.schema.json.JsonInterpolatorRuntime.stringOf($arg)")
          dummyArgs += "key"

        case Context.JsonValue =>
          val encoderType = appliedType(typeOf[JsonEncoder[_]], tpe)
          val encoder     = c.inferImplicitValue(encoderType)
          if (encoder == EmptyTree)
            c.abort(
              c.enclosingPosition,
              s"Context: value position\nProvided: $tpe\nRequired: JsonEncoder[$tpe]\nHint: encode can come from JsonBinaryCodec or derived from Schema[$tpe]"
            )
          newArgs += c.Expr[Any](q"$encoder.encode($arg)")
          dummyArgs += "value"

        case _ =>
          val encoderType = appliedType(typeOf[JsonEncoder[_]], tpe)
          val encoder     = c.inferImplicitValue(encoderType)
          if (encoder == EmptyTree)
            c.abort(
              c.enclosingPosition,
              s"Context: unknown (inferred value)\nProvided: $tpe\nRequired: JsonEncoder[$tpe]"
            )
          newArgs += c.Expr[Any](q"$encoder.encode($arg)")
          dummyArgs += "value"
      }
    }

    try {
      JsonInterpolatorRuntime.jsonWithInterpolation(new StringContext(parts: _*), dummyArgs.toSeq)
      val scExpr   = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)
      val argsExpr = c.Expr[Seq[Any]](q"Seq(..$newArgs)")
      reify(JsonInterpolatorRuntime.jsonWithInterpolation(scExpr.splice, argsExpr.splice))
    } catch {
      case error if NonFatal(error) => c.abort(c.enclosingPosition, s"Invalid JSON literal: ${error.getMessage}")
    }
  }
}
