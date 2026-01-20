package zio.blocks.schema.json

import scala.quoted.*
import zio.blocks.schema.DynamicOptic

object PathMacros {

  def pathInterpolator(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[DynamicOptic] = {
    // Generate: Json.parsePath(sc.parts.mkString)
    val _ = args // Suppress unused warning
    '{
      val parts = $sc.parts.mkString
      Json.parsePath(parts)
    }
  }

  def jsonInterpolator(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Json] =
    // Generate: Json.fromInterpolation(sc.parts, args)
    '{
      Json.fromInterpolation($sc.parts, $args)
    }
}
