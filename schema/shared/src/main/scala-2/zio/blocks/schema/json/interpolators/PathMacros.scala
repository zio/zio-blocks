package zio.blocks.schema.json.interpolators

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.json.Json
import scala.reflect.macros.blackbox

object PathMacros {
  def pImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[DynamicOptic] = {
    import c.universe._
    
    val sc = c.prefix.tree match {
      case Apply(_, List(Apply(_, parts))) => parts.map { case Literal(Constant(str: String)) => str }
      case _ => c.abort(c.enclosingPosition, "Invalid string interpolator usage")
    }
    
    if (args.nonEmpty) {
      c.abort(c.enclosingPosition, "p interpolator does not support arguments yet, only static string paths")
    }
    
    val path = sc.mkString("")
    PathParser.parse(path) match {
      case Right(_) =>
        c.Expr[DynamicOptic](q"zio.blocks.schema.json.interpolators.PathParser.parseUnsafe($path)")
      case Left(err) =>
        c.abort(c.enclosingPosition, s"Invalid path: $err")
    }
  }

  def jImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Json] = {
    import c.universe._
    
    val sc = c.prefix.tree match {
      case Apply(_, List(Apply(_, parts))) => parts.map { case Literal(Constant(str: String)) => str }
      case _ => c.abort(c.enclosingPosition, "Invalid string interpolator usage")
    }

    if (args.nonEmpty) {
      c.abort(c.enclosingPosition, "j interpolator does not support arguments yet")
    }
    
    val jsonStr = sc.mkString("")
    c.Expr[Json](q"zio.blocks.schema.json.Json.parseUnsafe($jsonStr)")
  }
}
