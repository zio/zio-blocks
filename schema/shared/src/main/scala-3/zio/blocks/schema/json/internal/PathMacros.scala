package zio.blocks.schema.json.internal

import scala.quoted.*
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.json.Json

object PathMacros {
  def pathInterpolator(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[DynamicOptic] = {
    val _ = args
    import quotes.reflect.*

    // Basic implementation: parse static parts if possible, simplistic for now
    // We access the parts of the StringContext
    sc match {
      case '{ StringContext(${ Varargs(parts) }*) } =>
        val partsConst = parts.map {
          case Expr(str) => str
          case _         => report.errorAndAbort("Expected static string parts")
        }

        // For now, only handle static string without args
        if (partsConst.size == 1) {
          val pathStr = partsConst.head
          parsePath(pathStr)
        } else {
          report.warning("Dynamic path interpolation not yet fully implemented for Scala 3")
          '{ DynamicOptic.root }
        }
      case _ =>
        report.errorAndAbort("Cannot extract StringContext parts")
    }
  }

  def jsonInterpolator(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Json] = {
    val _ = sc
    val _ = args
    '{ Json.Null }
  }

  private def parsePath(path: String)(using Quotes): Expr[DynamicOptic] = {
    // Split by dot and create GenericOptic structure
    val segments = path.split('.').toList.filter(_.nonEmpty)
    segments.foldLeft('{ DynamicOptic.root }) { (acc, segment) =>
      '{ $acc.field(${ Expr(segment) }) }
    }
  }
}
