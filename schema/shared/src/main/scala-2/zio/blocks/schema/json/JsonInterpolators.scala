package zio.blocks.schema.json

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object JsonInterpolators {

  implicit class JsonStringContext(val sc: StringContext) extends AnyVal {
    def json(args: Any*): Json = macro JsonInterpolatorMacros.jsonImpl
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
}
