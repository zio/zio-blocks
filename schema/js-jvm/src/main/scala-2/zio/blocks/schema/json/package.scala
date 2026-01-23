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
    try {
      JsonInterpolatorRuntime.jsonWithInterpolation(new StringContext(parts: _*), (2 to parts.size).map(_ => ""))
      val scExpr   = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)
      val argsExpr = c.Expr[Seq[Any]](q"Seq(..$args)")
      reify(JsonInterpolatorRuntime.jsonWithInterpolation(scExpr.splice, argsExpr.splice))
    } catch {
      case error if NonFatal(error) => c.abort(c.enclosingPosition, s"Invalid JSON literal: ${error.getMessage}")
    }
  }
}
