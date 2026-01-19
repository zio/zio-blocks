package zio.blocks.schema.json

import scala.quoted._
import zio.blocks.schema.DynamicOptic

object JsonInterpolators {

  extension (inline sc: StringContext) {
    inline def json(inline args: Any*): Json = ${ jsonInterpolatorImpl('sc, 'args) }
    inline def p(inline args: Any*): DynamicOptic = ${ pathInterpolatorImpl('sc, 'args) }
  }

  private def jsonInterpolatorImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Json] = {
    import quotes.reflect._

    val parts = sc match {
      case '{ StringContext(${ Varargs(parts) }: _*) } =>
        parts.map {
          case '{ $part: String } => part.valueOrAbort
        }
      case _ =>
        report.errorAndAbort("Expected a StringContext with string literal parts")
    }

    if (parts.length > 1) {
      '{ JsonInterpolatorRuntime.jsonWithInterpolation($sc, $args) }
    } else {
      val jsonStr = parts.head
      JsonInterpolatorRuntime.validateJsonSyntax(jsonStr) match {
        case None =>
          '{ Json.parseUnsafe(${ Expr(jsonStr) }) }
        case Some(error) =>
          report.errorAndAbort(s"Invalid JSON literal: $error")
      }
    }
  }

  private def pathInterpolatorImpl(sc: Expr[StringContext], @scala.annotation.unused args: Expr[Seq[Any]])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect._

    val parts = sc match {
      case '{ StringContext(${ Varargs(parts) }: _*) } =>
        parts.map {
          case '{ $part: String } => part.valueOrAbort
        }
      case _ =>
        report.errorAndAbort("Expected a StringContext with string literal parts")
    }

    if (parts.length > 1) {
      report.errorAndAbort("Path interpolator does not support interpolated values")
    }

    val pathStr = parts.head
    parsePath(pathStr) match {
      case Right(path) =>
        path
      case Left(error) =>
        report.errorAndAbort(s"Invalid path expression: $error")
    }
  }

  private def parsePath(path: String)(using Quotes): Either[String, Expr[DynamicOptic]] = {

    var result: Expr[DynamicOptic] = '{ DynamicOptic.root }
    var remaining                  = path.trim
    var position                   = 0

    while (remaining.nonEmpty) {
      if (remaining.startsWith(".")) {
        remaining = remaining.drop(1)
        position += 1
        val fieldName = remaining.takeWhile(c => c.isLetterOrDigit || c == '_' || c == '-')
        if (fieldName.isEmpty) {
          return Left(s"Expected field name after '.' at position $position")
        }
        val fieldExpr = Expr(fieldName)
        result = '{ $result.field($fieldExpr) }
        remaining = remaining.drop(fieldName.length)
        position += fieldName.length
      } else if (remaining.startsWith("[")) {
        remaining = remaining.drop(1)
        position += 1

        if (remaining.startsWith("\"")) {
          remaining = remaining.drop(1)
          position += 1
          val endQuote = remaining.indexOf('"')
          if (endQuote < 0) {
            return Left(s"Unterminated string in bracket notation at position $position")
          }
          val fieldName = remaining.take(endQuote)
          remaining = remaining.drop(endQuote + 1)
          position += endQuote + 1
          if (!remaining.startsWith("]")) {
            return Left(s"Expected ']' after field name at position $position")
          }
          remaining = remaining.drop(1)
          position += 1
          val fieldExpr = Expr(fieldName)
          result = '{ $result.field($fieldExpr) }
        } else {
          val indexStr = remaining.takeWhile(_.isDigit)
          if (indexStr.isEmpty) {
            return Left(s"Expected array index at position $position")
          }
          val index = indexStr.toInt
          remaining = remaining.drop(indexStr.length)
          position += indexStr.length
          if (!remaining.startsWith("]")) {
            return Left(s"Expected ']' after index at position $position")
          }
          remaining = remaining.drop(1)
          position += 1
          val indexExpr = Expr(index)
          result = '{ $result.at($indexExpr) }
        }
      } else {
        return Left(s"Unexpected character '${remaining.head}' at position $position")
      }
    }

    Right(result)
  }
}
