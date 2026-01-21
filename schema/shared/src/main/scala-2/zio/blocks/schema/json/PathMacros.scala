package zio.blocks.schema.json

import scala.reflect.macros.blackbox
import zio.blocks.schema.DynamicOptic

object PathMacros {

  def pathInterpolator(c: blackbox.Context)(@annotation.unused args: c.Expr[Any]*): c.Expr[DynamicOptic] = {
    import c.universe._

    // Access the StringContext parts
    val stringParts = q"${c.prefix}.sc.parts.mkString"

    c.Expr[DynamicOptic](q"zio.blocks.schema.json.Json.parsePath($stringParts)")
  }

  def jsonInterpolator(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Json] = {
    import c.universe._

    val partsProxy = q"${c.prefix}.sc.parts"
    val argsProxy  = q"Seq(..$args)"

    c.Expr[Json](q"zio.blocks.schema.json.Json.fromInterpolation($partsProxy, $argsProxy)")
  }
}
