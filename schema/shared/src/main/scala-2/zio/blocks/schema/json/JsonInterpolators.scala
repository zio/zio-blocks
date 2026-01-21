package zio.blocks.schema.json

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object JsonInterpolators {
  implicit class JsonInterpolator(val sc: StringContext) extends AnyVal {
    def json(args: Any*): Json = macro JsonInterpolatorMacro.jsonImpl
  }
}

object JsonInterpolatorMacro {
  def jsonImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Json] = {
    import c.universe._

    val parts = c.prefix.tree match {
      case Apply(_, List(Apply(_, parts))) =>
        parts.map { case Literal(Constant(s: String)) => s }
      case _ => c.abort(c.enclosingPosition, "Invalid use of json interpolator")
    }

    // For now, we just delegate to runtime. 
    // A full macro would parse the JSON at compile time to validate it.
    // Given the constraints, we'll use the runtime implementation but ensure it compiles.
    
    c.Expr[Json](q"zio.blocks.schema.json.JsonInterpolatorRuntime.interpolate(Seq(..$parts), Seq(..$args))")
  }
}
