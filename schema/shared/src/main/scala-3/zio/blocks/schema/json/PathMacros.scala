package zio.blocks.schema.json

import scala.quoted.*

/**
 * Macro implementations for string interpolators (Scala 3).
 */
object PathMacros {

  def pathImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using
    Quotes
  ): Expr[zio.blocks.schema.DynamicOptic] = {
    import quotes.reflect.*

    // Use StringContext standard extraction
    val parts: Seq[String] = sc.valueOrAbort.parts

    // args should be empty for path interpolator
    args match {
      case Varargs(argExprs) if argExprs.nonEmpty =>
        report.errorAndAbort("Path interpolator does not support interpolated arguments")
      case _ =>
        () // OK
    }

    val pathStr = parts.mkString
    if (pathStr.isEmpty) {
      '{ zio.blocks.schema.DynamicOptic.root }
    } else {
      '{ zio.blocks.schema.json.PathParser.parsePath(${ Expr(pathStr) }) }
    }
  }

  def jsonImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Json] = {
    import quotes.reflect.*

    // Use StringContext standard extraction
    val parts: Seq[String] = sc.valueOrAbort.parts

    args match {
      case Varargs(argExprs) if argExprs.isEmpty =>
        // No interpolation
        val jsonStr = parts.mkString
        '{ Json.parseUnsafe(${ Expr(jsonStr) }) }

      case Varargs(argExprs) =>
        // With interpolation - build at runtime
        val partsExpr = Expr(parts.toList)
        val argsSeq   = Expr.ofList(argExprs.toList)

        '{
          val partsList          = $partsExpr
          val argsList: Seq[Any] = $argsSeq
          val sb                 = new StringBuilder
          var i                  = 0
          while (i < partsList.length) {
            sb.append(partsList(i))
            if (i < argsList.length) {
              val arg       = argsList(i)
              val jsonValue = arg match {
                case s: String =>
                  "\"" + s
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t") + "\""
                case n: Int     => n.toString
                case n: Long    => n.toString
                case n: Double  => n.toString
                case n: Float   => n.toString
                case n: Boolean => n.toString
                case null       => "null"
                case j: Json    => j.print
                case other      => "\"" + other.toString.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
              }
              sb.append(jsonValue)
            }
            i += 1
          }
          Json.parseUnsafe(sb.toString)
        }

      case _ =>
        report.errorAndAbort("Unexpected args pattern")
    }
  }
}
