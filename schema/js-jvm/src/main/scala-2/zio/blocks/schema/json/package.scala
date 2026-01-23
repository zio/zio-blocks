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

    // Detect which interpolations are in key positions
    val inKeyPosition = detectKeyPositions(parts, inStringLiteral)

    // Perform compile-time type checking
    var argIdx = 0
    while (argIdx < args.length) {
      val arg = args(argIdx)
      val argType = arg.actualType

      if (argIdx < inStringLiteral.length && inStringLiteral(argIdx)) {
        // String literal position - must have PrimitiveType[A]
        val primitiveTypeType = appliedType(typeOf[zio.blocks.schema.PrimitiveType[_]].typeConstructor, argType)
        val primitiveTypeInstance = c.inferImplicitValue(primitiveTypeType, silent = true)

        if (primitiveTypeInstance == EmptyTree) {
          c.abort(
            arg.tree.pos,
            s"Type ${argType} cannot be interpolated inside a JSON string literal. " +
            s"Only stringable types (those with PrimitiveType[${argType}]) are allowed. " +
            s"Stringable types include: primitives (Int, String, Boolean, etc.), BigInt, BigDecimal, " +
            s"java.time types (Instant, LocalDate, etc.), UUID, and Currency."
          )
        }
      } else if (argIdx < inKeyPosition.length && inKeyPosition(argIdx)) {
        // Key position - must have PrimitiveType[A]
        val primitiveTypeType = appliedType(typeOf[zio.blocks.schema.PrimitiveType[_]].typeConstructor, argType)
        val primitiveTypeInstance = c.inferImplicitValue(primitiveTypeType, silent = true)

        if (primitiveTypeInstance == EmptyTree) {
          c.abort(
            arg.tree.pos,
            s"Type ${argType} cannot be used as a JSON key. " +
            s"Only stringable types (those with PrimitiveType[${argType}]) are allowed as keys. " +
            s"Stringable types include: primitives (Int, String, Boolean, etc.), BigInt, BigDecimal, " +
            s"java.time types (Instant, LocalDate, etc.), UUID, and Currency."
          )
        }
      } else {
        // Value position - must have JsonEncoder[A]
        val jsonEncoderType = appliedType(typeOf[zio.blocks.schema.json.JsonEncoder[_]].typeConstructor, argType)
        val jsonEncoderInstance = c.inferImplicitValue(jsonEncoderType, silent = true)

        if (jsonEncoderInstance == EmptyTree) {
          c.abort(
            arg.tree.pos,
            s"Type ${argType} cannot be interpolated as a JSON value. " +
            s"No implicit JsonEncoder[${argType}] found. " +
            s"Either derive a Schema[${argType}] (which provides JsonEncoder automatically) " +
            s"or provide an explicit JsonEncoder[${argType}] instance."
          )
        }
      }

      argIdx += 1
    }

    try {
      // Validate JSON syntax at compile time
      // Build a complete JSON string for validation by reconstructing with placeholders
      val validationString = new StringBuilder()
      var i = 0
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
        case Left(error) => c.abort(c.enclosingPosition, s"Invalid JSON literal: ${error.getMessage}")
        case Right(_) => // Valid JSON
      }

      val scExpr   = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)
      val argsExpr = c.Expr[Seq[Any]](q"Seq(..$args)")
      val contextExpr = c.Expr[Array[Boolean]](q"Array(..$inStringLiteral)")

      reify(JsonInterpolatorRuntime.jsonWithInterpolationAndContext(scExpr.splice, argsExpr.splice, contextExpr.splice))
    } catch {
      case error if NonFatal(error) => c.abort(c.enclosingPosition, s"Invalid JSON literal: ${error.getMessage}")
    }
  }

  /**
   * Detects which interpolations are inside JSON string literals.
   * Returns an array of booleans, one for each interpolation.
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
   * Detects which interpolations are in key positions (before a colon).
   * Returns an array of booleans, one for each interpolation.
   */
  private def detectKeyPositions(parts: List[String], inStringLiteral: List[Boolean]): List[Boolean] = {
    if (parts.size <= 1) return Nil

    val result = scala.collection.mutable.ArrayBuffer[Boolean]()
    var i = 0
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
