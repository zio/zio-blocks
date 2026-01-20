package zio.blocks.schema.json

import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import zio.blocks.schema.DynamicOptic

object JsonInterpolators {

  implicit class JsonStringContext(val sc: StringContext) extends AnyVal {
    def json(args: Any*): Json = macro JsonInterpolatorMacros.jsonImpl
    def p(args: Any*): DynamicOptic = macro JsonInterpolatorMacros.pathImpl
  }
}

object JsonInterpolatorMacros {

  def jsonImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Json] = {
    import c.universe._

    val parts = sc(c).parts
    if (parts.length > 1) {
      val scExpr   = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)
      val argsExpr = c.Expr[Seq[Any]](q"Seq(..$args)")
      reify {
        JsonInterpolatorRuntime.jsonWithInterpolation(scExpr.splice, argsExpr.splice)
      }
    } else {
      val jsonStr = parts.head
      JsonInterpolatorRuntime.validateJsonSyntax(jsonStr) match {
        case None =>
          val jsonStrLit = c.Expr[String](Literal(Constant(jsonStr)))
          reify(Json.parseUnsafe(jsonStrLit.splice))
        case Some(error) =>
          c.abort(c.enclosingPosition, s"Invalid JSON literal: $error")
      }
    }
  }

  def pathImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[DynamicOptic] = {
    val _     = args // suppress unused warning
    val parts = sc(c).parts
    if (parts.length > 1) {
      c.abort(c.enclosingPosition, "Path interpolator does not support interpolated values")
    }

    val pathStr = parts.head
    parsePath(c)(pathStr) match {
      case Right(expr) => expr
      case Left(error) => c.abort(c.enclosingPosition, s"Invalid path expression: $error")
    }
  }

  private def sc(c: blackbox.Context): StringContext = {
    import c.universe._
    c.prefix.tree match {
      case Apply(_, List(Apply(_, rawParts))) =>
        val parts = rawParts.map {
          case Literal(Constant(part: String)) => part
          case _                               => c.abort(c.enclosingPosition, "Expected string literal parts")
        }
        StringContext(parts: _*)
      case _ =>
        c.abort(c.enclosingPosition, "Expected StringContext")
    }
  }

  private def parsePath(c: blackbox.Context)(path: String): Either[String, c.Expr[DynamicOptic]] = {
    import c.universe._

    var result: Tree = q"_root_.zio.blocks.schema.DynamicOptic.root"
    var remaining    = path.trim
    var position     = 0

    while (remaining.nonEmpty) {
      if (remaining.startsWith(".")) {
        remaining = remaining.drop(1)
        position += 1
        val fieldName = remaining.takeWhile(ch => ch.isLetterOrDigit || ch == '_' || ch == '-')
        if (fieldName.isEmpty) {
          return Left(s"Expected field name after '.' at position $position")
        }
        result = q"$result.field($fieldName)"
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
          result = q"$result.field($fieldName)"
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
          result = q"$result.at($index)"
        }
      } else {
        return Left(s"Unexpected character '${remaining.head}' at position $position")
      }
    }

    Right(c.Expr[DynamicOptic](result))
  }
}
