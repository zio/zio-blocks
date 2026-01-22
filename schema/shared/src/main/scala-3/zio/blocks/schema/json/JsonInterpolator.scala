package zio.blocks.schema.json

import scala.quoted._

object JsonInterpolator {
  extension (inline sc: StringContext) {
    inline def json(inline args: Any*): Json = ${ jsonInterpolatorImpl('sc, 'args) }
  }

  private def jsonInterpolatorImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Json] =
    '{ JsonInterpolatorRuntime.jsonWithInterpolation($sc, $args) }
}
