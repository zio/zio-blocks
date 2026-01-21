package zio.blocks.schema.json

import scala.quoted._

object JsonInterpolators {
  extension (inline sc: StringContext) {
    inline def json(inline args: Any*): Json = ${ jsonImpl('sc, 'args) }
  }

  def jsonImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Json] = {

    
    // We delegate to runtime for now.
    // Ideally we would validate at compile time here.
    '{ zio.blocks.schema.json.JsonInterpolatorRuntime.interpolate($sc.parts, $args) }
  }
}
