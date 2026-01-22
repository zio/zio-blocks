package zio.blocks.schema.json

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object JsonInterpolator {
  implicit class JsonStringContext(val sc: StringContext) extends AnyVal {
    def json(args: Any*): Json = macro JsonInterpolatorMacros.jsonImpl
  }
}

object JsonInterpolatorMacros {
  def jsonImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Json] = {
    import c.universe._

    val scExpr   = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)
    val argsExpr = c.Expr[Seq[Any]](q"Seq(..$args)")
    reify(JsonInterpolatorRuntime.jsonWithInterpolation(scExpr.splice, argsExpr.splice))
  }
}
