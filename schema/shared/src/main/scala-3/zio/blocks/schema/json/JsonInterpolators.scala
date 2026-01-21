package zio.blocks.schema.json

import scala.quoted._

object JsonInterpolators {

  extension (inline sc: StringContext) {
    inline def json(inline args: Any*): Json = ${ jsonInterpolatorImpl('sc, 'args) }
  }

  private def jsonInterpolatorImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Json] = {
    import quotes.reflect._

    val parts = sc match {
      case '{ StringContext(${ Varargs(parts) }: _*) } =>
        parts.map { case '{ $part: String } =>
          part.valueOrAbort
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
}
