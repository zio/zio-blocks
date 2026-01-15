package zio.blocks.schema.json

import scala.reflect.macros.blackbox

/**
 * Macro implementations for string interpolators (Scala 2).
 */
object PathMacros {

  def pathInterpolator(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[zio.blocks.schema.DynamicOptic] = {
    import c.universe._

    // Extract the string parts from the StringContext
    val parts = c.prefix.tree match {
      case Apply(_, List(Apply(_, rawParts))) =>
        rawParts.map {
          case Literal(Constant(s: String)) => s
          case other                        => c.abort(other.pos, "Expected string literal")
        }
      case other =>
        c.abort(other.pos, "Expected StringContext")
    }

    if (args.nonEmpty) {
      c.abort(c.enclosingPosition, "Path interpolator does not support interpolated arguments")
    }

    val pathStr = parts.mkString
    if (pathStr.isEmpty) {
      // Empty path = root
      c.Expr[zio.blocks.schema.DynamicOptic](q"_root_.zio.blocks.schema.DynamicOptic.root")
    } else {
      // Parse the path at compile time and generate code to build DynamicOptic
      // Path syntax: field.name, field[0], field[*], etc.
      c.Expr[zio.blocks.schema.DynamicOptic](
        q"_root_.zio.blocks.schema.json.PathParser.parsePath($pathStr)"
      )
    }
  }

  def jsonInterpolator(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Json] = {
    import c.universe._

    // Extract the string parts from the StringContext
    val parts = c.prefix.tree match {
      case Apply(_, List(Apply(_, rawParts))) =>
        rawParts.map {
          case Literal(Constant(s: String)) => s
          case other                        => c.abort(other.pos, "Expected string literal")
        }
      case other =>
        c.abort(other.pos, "Expected StringContext")
    }

    if (args.isEmpty) {
      // No interpolation - parse at compile time for validation
      val jsonStr = parts.mkString
      c.Expr[Json](
        q"_root_.zio.blocks.schema.json.Json.parseUnsafe($jsonStr)"
      )
    } else {
      // With interpolation - build at runtime
      // For now, we'll substitute placeholders and parse
      val argsSeq     = args.toList
      val placeholders = argsSeq.zipWithIndex.map { case (_, i) => s"__PLACEHOLDER_${i}__" }
      val templateParts =
        parts.zipAll(placeholders, "", "").flatMap { case (p, h) => List(p, h) }.dropRight(1)
      val template = templateParts.mkString

      // Generate code that builds JSON with substitutions
      val argsList = argsSeq.map(_.tree)

      c.Expr[Json](q"""
        {
          val __args: Seq[Any] = Seq(..$argsList)
          var __result = $template
          __args.zipWithIndex.foreach { case (__arg, __i) =>
            val __placeholder = "__PLACEHOLDER_" + __i + "__"
            val __jsonValue = __arg match {
              case s: String => "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
              case n: Int => n.toString
              case n: Long => n.toString
              case n: Double => n.toString
              case n: Float => n.toString
              case b: Boolean => b.toString
              case null => "null"
              case j: _root_.zio.blocks.schema.json.Json => j.print
              case other => "\"" + other.toString.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            }
            __result = __result.replace(__placeholder, __jsonValue)
          }
          _root_.zio.blocks.schema.json.Json.parseUnsafe(__result)
        }
      """)
    }
  }
}
