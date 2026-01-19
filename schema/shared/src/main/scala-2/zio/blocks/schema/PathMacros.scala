package zio.blocks.schema

import scala.reflect.macros.blackbox.Context
import zio.blocks.schema.internal._

/**
 * Scala 2.13 macro implementation for the `p"..."` path interpolator.
 *
 * This object provides compile-time parsing of path expressions, converting
 * them into `DynamicOptic` instances with position-aware error reporting.
 *
 * @see
 *   [[PathInterpolatorSyntax]] for usage examples
 */
object PathMacros {

  /**
   * Macro implementation that transforms a `StringContext` into a
   * `DynamicOptic`.
   *
   * This is called by the `p"..."` string interpolator at compile time. It:
   *   1. Validates that no interpolation arguments are provided
   *   2. Extracts the literal path string
   *   3. Parses the path using [[PathParser]]
   *   4. Converts parse results to `DynamicOptic.Node` instances
   *   5. Generates the final expression tree
   *
   * @param c
   *   The macro Context
   * @param args
   *   The interpolation arguments (must be empty)
   * @return
   *   Tree creating the DynamicOptic
   */
  def pImpl(c: Context)(args: c.Expr[Any]*): c.Tree = {
    import c.universe._

    // Step 1: Reject any interpolation arguments
    if (args.nonEmpty) {
      c.abort(c.enclosingPosition, "p interpolator does not support interpolation. Use only literal strings.")
    }

    // Step 2: Extract literal string from StringContext
    val literal = c.prefix.tree match {
      case Apply(_, List(Apply(_, parts))) =>
        parts match {
          case List(Literal(Constant(s: String))) => s
          case _                                  =>
            c.abort(c.enclosingPosition, "p interpolator only supports literal strings (no interpolation)")
        }
      case _ =>
        c.abort(c.enclosingPosition, "Unexpected macro application context")
    }

    // Step 3: Parse the path expression
    val parseResult = new PathParser(literal).parse()

    parseResult match {
      case ParseSuccess(segments, _) =>
        // Step 4: Build nodes from segments
        val nodes = ASTBuilder.build(segments)

        // Step 5: Generate the expression tree
        generateTree(c)(nodes)

      case err: ParseError =>
        val compileError = ErrorReporter.formatError(literal, err)
        c.abort(c.enclosingPosition, ErrorReporter.toErrorMessage(compileError))
    }
  }

  private def generateTree(c: Context)(nodes: Vector[DynamicOptic.Node]): c.Tree = {
    import c.universe._

    val nodeTreesSeq: Seq[c.Tree] = nodes.map { node =>
      node match {
        case DynamicOptic.Node.Field(name) =>
          q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($name)"

        case DynamicOptic.Node.Case(name) =>
          q"_root_.zio.blocks.schema.DynamicOptic.Node.Case($name)"

        case DynamicOptic.Node.AtIndex(idx) =>
          q"_root_.zio.blocks.schema.DynamicOptic.Node.AtIndex($idx)"

        case DynamicOptic.Node.AtIndices(indices) =>
          val indicesSeq = indices.toSeq
          q"_root_.zio.blocks.schema.DynamicOptic.Node.AtIndices(_root_.scala.Seq(..$indicesSeq))"

        case DynamicOptic.Node.Elements =>
          q"_root_.zio.blocks.schema.DynamicOptic.Node.Elements"

        case DynamicOptic.Node.AtMapKey(key) =>
          val keyTree = dynamicValueToTree(c)(key)
          q"_root_.zio.blocks.schema.DynamicOptic.Node.AtMapKey($keyTree)"

        case DynamicOptic.Node.AtMapKeys(keys) =>
          val keyTrees = keys.map(dynamicValueToTree(c)).toSeq
          q"_root_.zio.blocks.schema.DynamicOptic.Node.AtMapKeys(_root_.scala.Seq(..$keyTrees))"

        case DynamicOptic.Node.MapKeys =>
          q"_root_.zio.blocks.schema.DynamicOptic.Node.MapKeys"

        case DynamicOptic.Node.MapValues =>
          q"_root_.zio.blocks.schema.DynamicOptic.Node.MapValues"

        case DynamicOptic.Node.Wrapped =>
          q"_root_.zio.blocks.schema.DynamicOptic.Node.Wrapped"
      }
    }

    q"new _root_.zio.blocks.schema.DynamicOptic(_root_.scala.Vector(..$nodeTreesSeq))"
  }

  private def dynamicValueToTree(c: Context)(dv: DynamicValue): c.Tree = {
    import c.universe._

    dv match {
      case DynamicValue.Primitive(pv) =>
        pv match {
          case PrimitiveValue.String(v) =>
            q"_root_.zio.blocks.schema.DynamicValue.Primitive(_root_.zio.blocks.schema.PrimitiveValue.String($v))"

          case PrimitiveValue.Int(v) =>
            q"_root_.zio.blocks.schema.DynamicValue.Primitive(_root_.zio.blocks.schema.PrimitiveValue.Int($v))"

          case PrimitiveValue.Char(v) =>
            q"_root_.zio.blocks.schema.DynamicValue.Primitive(_root_.zio.blocks.schema.PrimitiveValue.Char($v))"

          case PrimitiveValue.Boolean(v) =>
            q"_root_.zio.blocks.schema.DynamicValue.Primitive(_root_.zio.blocks.schema.PrimitiveValue.Boolean($v))"

          case other =>
            c.abort(c.enclosingPosition, s"Unsupported primitive type in map key: $other")
        }

      case other =>
        c.abort(c.enclosingPosition, s"Expected Primitive DynamicValue but got: $other")
    }
  }
}
