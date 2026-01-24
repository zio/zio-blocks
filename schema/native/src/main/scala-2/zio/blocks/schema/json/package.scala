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
    
    // Type-check each interpolation based on its context
    args.zipWithIndex.foreach { case (arg, idx) =>
      val context = detectInterpolationContext(parts, idx)
      context match {
        case InterpolationContext.Key =>
          checkStringableType(c)(arg, "key position")
        case InterpolationContext.Value =>
          checkHasJsonEncoder(c)(arg, "value position")
        case InterpolationContext.StringLiteral =>
          checkStringableType(c)(arg, "string literal")
      }
    }
    
    val scExpr   = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)
    val argsExpr = c.Expr[Seq[Any]](q"Seq(..$args)")
    reify(JsonInterpolatorRuntime.jsonWithInterpolation(scExpr.splice, argsExpr.splice))
  }

  private sealed trait InterpolationContext
  private object InterpolationContext {
    case object Key extends InterpolationContext
    case object Value extends InterpolationContext
    case object StringLiteral extends InterpolationContext
  }

  private def detectInterpolationContext(parts: Seq[String], argIndex: Int): InterpolationContext = {
    // Track string literal context across all parts up to this point
    var inStringLiteral = false
    var i = 0
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
      val after = if (argIndex + 1 < parts.length) parts(argIndex + 1) else ""
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
    var i = 0
    while (i < text.length) {
      val c = text.charAt(i)
      if (c == '"') {
        // Count consecutive backslashes before this quote
        var backslashCount = 0
        var j = i - 1
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
    val trimmedAfter = after.dropWhile(c => c.isWhitespace)
    
    (trimmedBefore.endsWith("{") || trimmedBefore.endsWith(",")) && trimmedAfter.startsWith(":")
  }

  private def checkStringableType(c: blackbox.Context)(arg: c.Expr[Any], context: String): Unit = {
    import c.universe._
    
    val tpe = arg.tree.tpe.widen
    
    // Check if the type is a stringable primitive type
    val isStringable = tpe <:< typeOf[String] ||
      tpe <:< typeOf[Boolean] ||
      tpe <:< typeOf[Byte] ||
      tpe <:< typeOf[Short] ||
      tpe <:< typeOf[Int] ||
      tpe <:< typeOf[Long] ||
      tpe <:< typeOf[Float] ||
      tpe <:< typeOf[Double] ||
      tpe <:< typeOf[Char] ||
      tpe <:< typeOf[BigDecimal] ||
      tpe <:< typeOf[BigInt] ||
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
      tpe <:< typeOf[java.util.UUID] ||
      tpe <:< typeOf[java.util.Currency]
    
    if (!isStringable) {
      val typeStr = tpe.toString
      c.abort(c.enclosingPosition,
        s"Type error in JSON interpolation at $context:\n" +
        s"  Found: $typeStr\n" +
        s"  Required: A stringable type (primitive types as defined in PrimitiveType)\n" +
        s"  Hint: Only primitive types can be used in $context.\n" +
        s"        Supported types: String, Boolean, Byte, Short, Int, Long, Float, Double, Char,\n" +
        s"        BigDecimal, BigInt, java.time.*, java.util.UUID, java.util.Currency"
      )
    }
  }

  private def checkHasJsonEncoder(c: blackbox.Context)(arg: c.Expr[Any], context: String): Unit = {
    import c.universe._
    
    val tpe = arg.tree.tpe.widen
    
    // Check for special-cased runtime types that don't need explicit JsonEncoder
    val isSpecialType = 
      tpe <:< typeOf[scala.collection.Map[_, _]] ||
      tpe <:< typeOf[scala.collection.Iterable[_]] ||
      tpe <:< typeOf[Array[_]] ||
      tpe <:< typeOf[Option[_]] ||
      tpe <:< typeOf[Json]
    
    if (isSpecialType) {
      // These types are handled specially by the runtime
      return
    }
    
    val encoderType = appliedType(typeOf[JsonEncoder[_]].typeConstructor, tpe)
    
    val encoder = c.inferImplicitValue(encoderType, silent = true)
    if (encoder == EmptyTree) {
      val typeStr = tpe.toString
      c.abort(c.enclosingPosition,
        s"Type error in JSON interpolation at $context:\n" +
        s"  Found: $typeStr\n" +
        s"  Required: A type with an implicit JsonEncoder[$typeStr]\n" +
        s"  Hint: Provide an implicit JsonEncoder[$typeStr] in scope.\n" +
        s"        JsonEncoders can be:\n" +
        s"        - Explicitly defined\n" +
        s"        - Derived from Schema[$typeStr] (ensure implicit Schema[$typeStr] is in scope)\n" +
        s"        - Provided by JsonBinaryCodec"
      )
    }
  }
}
