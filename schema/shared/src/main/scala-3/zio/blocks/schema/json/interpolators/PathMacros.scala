package zio.blocks.schema.json.interpolators

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.json.Json
import scala.quoted._

object PathMacros {
  def pImpl(sc: Expr[StringContext], @annotation.nowarn args: Expr[Seq[Any]])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect._
    sc.value match {
      case Some(scVal) =>
        val parts = scVal.parts
        if (parts.size > 1) report.errorAndAbort("p interpolator does not support arguments yet, only static string paths")
        val path = parts.head
        PathParser.parse(path) match {
          case Right(_) =>
            '{ zio.blocks.schema.json.interpolators.PathParser.parseUnsafe(${Expr(path)}) }
          case Left(err) =>
            report.errorAndAbort(s"Invalid path: $err")
        }
      case None => report.errorAndAbort("Invalid usage: StringContext must be statically known")
    }
  }

  def jImpl(sc: Expr[StringContext], @annotation.nowarn args: Expr[Seq[Any]])(using Quotes): Expr[Json] = {
    import quotes.reflect._
    sc.value match {
      case Some(scVal) =>
        val parts = scVal.parts
        if (parts.size > 1) report.errorAndAbort("j interpolator does not support arguments yet")
        val jsonStr = parts.head
        '{ zio.blocks.schema.json.Json.parseUnsafe(${Expr(jsonStr)}) }
      case None => report.errorAndAbort("Invalid usage: StringContext must be statically known")
    }
  }
}
