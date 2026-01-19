package zio.blocks.schema

import scala.quoted.*
import zio.blocks.schema.internal.*

/**
 * Scala 3 macro implementation for the `p"..."` path interpolator.
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
   *   5. Generates the final expression
   *
   * @param sc
   *   The StringContext expression from the interpolator
   * @param args
   *   The interpolation arguments (must be empty)
   * @return
   *   Expression creating the DynamicOptic
   */
  def pImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    // Step 1: Validate no interpolation arguments
    args match {
      case Varargs(argSeq) if argSeq.nonEmpty =>
        report.errorAndAbort(
          "p interpolator does not support interpolation arguments (use literal strings only)",
          args
        )
      case _ => // OK - no args
    }

    // Step 2: Extract literal string from StringContext
    // Note: Using valueOrAbort requires the extension to use `inline sc: StringContext`
    val literal: String = sc.valueOrAbort.parts match {
      case part :: Nil             => part
      case parts if parts.size > 1 =>
        report.errorAndAbort(
          "p interpolator does not support interpolation arguments (use literal strings only)",
          sc
        )
      case _ =>
        report.errorAndAbort("Invalid StringContext", sc)
    }

    // Step 3: Parse the path expression
    val parseResult = new PathParser(literal).parse()

    parseResult match {
      case ParseSuccess(segments, _) =>
        // Step 4: Build nodes from segments
        val nodes = ASTBuilder.build(segments)

        // Step 5: Generate the expression
        generateExpr(nodes)

      case err: ParseError =>
        val compileError = ErrorReporter.formatError(literal, err)
        report.errorAndAbort(ErrorReporter.toErrorMessage(compileError), sc)
    }
  }

  private def generateExpr(nodes: Vector[DynamicOptic.Node])(using Quotes): Expr[DynamicOptic] = {
    val nodeExprs: Seq[Expr[DynamicOptic.Node]] = nodes.map { node =>
      node match {
        case DynamicOptic.Node.Field(name) =>
          '{ DynamicOptic.Node.Field(${ Expr(name) }) }

        case DynamicOptic.Node.Case(name) =>
          '{ DynamicOptic.Node.Case(${ Expr(name) }) }

        case DynamicOptic.Node.AtIndex(idx) =>
          '{ DynamicOptic.Node.AtIndex(${ Expr(idx) }) }

        case DynamicOptic.Node.AtIndices(indices) =>
          val indicesExpr = Expr(indices.toSeq)
          '{ DynamicOptic.Node.AtIndices($indicesExpr) }

        case DynamicOptic.Node.Elements =>
          '{ DynamicOptic.Node.Elements }

        case DynamicOptic.Node.AtMapKey(key) =>
          val keyExpr = dynamicValueToExpr(key)
          '{ DynamicOptic.Node.AtMapKey($keyExpr) }

        case DynamicOptic.Node.AtMapKeys(keys) =>
          val keyExprs = Expr.ofSeq(keys.map(dynamicValueToExpr).toSeq)
          '{ DynamicOptic.Node.AtMapKeys($keyExprs) }

        case DynamicOptic.Node.MapKeys =>
          '{ DynamicOptic.Node.MapKeys }

        case DynamicOptic.Node.MapValues =>
          '{ DynamicOptic.Node.MapValues }

        case DynamicOptic.Node.Wrapped =>
          '{ DynamicOptic.Node.Wrapped }
      }
    }

    val seqExpr = Expr.ofSeq(nodeExprs)
    '{ new DynamicOptic($seqExpr.toVector) }
  }

  private def dynamicValueToExpr(dv: DynamicValue)(using Quotes): Expr[DynamicValue] =
    dv match {
      case DynamicValue.Primitive(pv) =>
        pv match {
          case PrimitiveValue.String(v) =>
            '{ DynamicValue.Primitive(PrimitiveValue.String(${ Expr(v) })) }

          case PrimitiveValue.Int(v) =>
            '{ DynamicValue.Primitive(PrimitiveValue.Int(${ Expr(v) })) }

          case PrimitiveValue.Char(v) =>
            '{ DynamicValue.Primitive(PrimitiveValue.Char(${ Expr(v) })) }

          case PrimitiveValue.Boolean(v) =>
            '{ DynamicValue.Primitive(PrimitiveValue.Boolean(${ Expr(v) })) }

          case other =>
            import quotes.reflect.*
            report.errorAndAbort(s"Unsupported primitive type in map key: $other")
        }

      case other =>
        import quotes.reflect.*
        report.errorAndAbort(s"Expected Primitive DynamicValue but got: $other")
    }
}
