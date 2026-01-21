package zio.blocks.schema.json.internal

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.json.Json
import scala.reflect.macros.whitebox

object PathMacros {

  def pathInterpolator(c: whitebox.Context)(args: c.Expr[Any]*): c.Expr[DynamicOptic] = {
    import c.universe._

    // Access the StringContext parts
    val parts = c.prefix.tree match {
      case Apply(_, List(Apply(_, parts))) =>
        parts.map {
          case Literal(Constant(str: String)) => str
          case _                              => c.abort(c.enclosingPosition, "Invalid StringContext parts")
        }
      case _ => c.abort(c.enclosingPosition, "Invalid StringContext usage")
    }

    if (args.isEmpty && parts.size == 1) {
      // Static path - delegate to runtime parser for consistency
      val pathStr = parts.head
      c.Expr[DynamicOptic](q"zio.blocks.schema.json.Json.parsePath($pathStr)")
    } else {
      // Dynamic path with args
      c.warning(c.enclosingPosition, "Dynamic interpolation for paths is not yet fully supported at compile time.")
      c.Expr[DynamicOptic](q"zio.blocks.schema.DynamicOptic.root")
    }
  }

  def jsonInterpolator(c: whitebox.Context)(args: c.Expr[Any]*): c.Expr[Json] = {
    import c.universe._

    val parts = c.prefix.tree match {
      case Apply(_, List(Apply(_, parts))) =>
        parts.map {
          case Literal(Constant(str: String)) => str
          case _                              => ""
        }
      case _ => List.empty
    }

    if (args.isEmpty && parts.size == 1) {
      val jsonStr = parts.head
      c.Expr[Json](q"zio.blocks.schema.json.Json.parse($jsonStr).fold(e => throw e, identity)")
    } else {
      // With args, we need runtime assembly
      val partsList     = c.Expr[Seq[String]](q"Seq(..$parts)")
      val argsAsAnyList = args.map(a => q"$a : Any")
      val argsList      = c.Expr[Seq[Any]](q"Seq(..$argsAsAnyList)")
      c.Expr[Json](q"zio.blocks.schema.json.Json.fromInterpolation($partsList, $argsList)")
    }
  }
}
