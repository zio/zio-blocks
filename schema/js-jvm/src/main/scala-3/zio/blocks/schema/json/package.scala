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
    try {
      JsonInterpolatorRuntime.jsonWithInterpolation(new StringContext(parts: _*), (2 to parts.size).map(_ => ""))
      '{ JsonInterpolatorRuntime.jsonWithInterpolation($sc, $args) }
    } catch {
      case error if NonFatal(error) => report.errorAndAbort(s"Invalid JSON literal: ${error.getMessage}")
    }
  }
}
