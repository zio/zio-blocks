package zio.blocks.schema.json.internal

import scala.quoted.*
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.json.Json

object PathMacros {
  def pathInterpolator(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[DynamicOptic] = {
    val _ = args
    import quotes.reflect.*

    // Access the parts of the StringContext
    sc match {
      case '{ StringContext(${ Varargs(parts) }*) } =>
        val partsConst = parts.map {
          case Expr(str) => str
          case _         => report.errorAndAbort("Expected static string parts")
        }

        // For now, only handle static string without args
        if (partsConst.size == 1) {
          val pathStr = partsConst.head
          // Delegate to runtime parser for consistency across platforms
          '{ Json.parsePath(${ Expr(pathStr) }) }
        } else {
          report.warning("Dynamic path interpolation not yet fully implemented for Scala 3")
          '{ DynamicOptic.root }
        }
      case _ =>
        report.errorAndAbort("Cannot extract StringContext parts")
    }
  }

  def jsonInterpolator(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Json] =
    sc match {
      case '{ StringContext(${ Varargs(parts) }*) } =>
        val partsConst = parts.map {
          case Expr(str) => str
          case _         => ""
        }

        if (partsConst.size == 1) {
          val jsonStr = partsConst.head
          '{ Json.parse(${ Expr(jsonStr) }).fold(e => throw e, identity) }
        } else {
          // With args, we need runtime assembly
          val partExprs = Expr.ofSeq(parts)
          '{ Json.fromInterpolation($partExprs, $args.toSeq) }
        }
      case _ =>
        '{ Json.Null }
    }
}
