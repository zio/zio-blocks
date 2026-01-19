package zio.blocks.schema

import scala.quoted.*

object PathMacros {

  inline def pImpl(sc: StringContext, args: => Seq[Any]): DynamicOptic =
    ${ pImplMacro('sc, 'args) }

  private def pImplMacro(
      scExpr: Expr[StringContext],
      argsExpr: Expr[Seq[Any]]
  )(using Quotes): Expr[DynamicOptic] =
  {
    import quotes.reflect.*

    // Reject interpolation
    argsExpr match {
      case Varargs(Seq()) => ()
      case _ =>
        report.error("Interpolated arguments not allowed in p\"...\" expressions", scExpr)
        return '{ DynamicOptic.root }
    }

    // Extract literal
    val literal: String =
      scExpr.value match {
        case Some(sc) =>
          if sc.parts.size != 1 then {
            report.error("Interpolated arguments not allowed in p\"...\" expressions", scExpr)
            return '{ DynamicOptic.root }
          }
          sc.parts.head
        case None =>
          report.error("Literal string required in p\"...\" expressions", scExpr)
          return '{ DynamicOptic.root }
      }

    // Parse path
    PathParser.parse(literal) match {
      case Left(err) =>
        report.error(
          s"Path parsing failed: ${err.message} at index ${err.index}",
          scExpr
        )
        '{ DynamicOptic.root }

      case Right(nodes) =>
        val nodeExprs: List[Expr[DynamicOptic.Node]] =
          nodes.map(convertNode).toList

        val vecExpr: Expr[Vector[DynamicOptic.Node]] =
          '{ Vector(${Varargs(nodeExprs)}*) }

        '{ DynamicOptic($vecExpr) }
    }
  }

  // -----------------------------
  // Node conversion
  // -----------------------------
  private def convertNode(node: DynamicOptic.Node)(using Quotes): Expr[DynamicOptic.Node] = {
    import quotes.reflect.*

    node match {
      case DynamicOptic.Node.Field(name) =>
        '{ DynamicOptic.Node.Field(${Expr(name)}) }

      case DynamicOptic.Node.AtIndex(i) =>
        '{ DynamicOptic.Node.AtIndex(${Expr(i)}) }

      case DynamicOptic.Node.AtIndices(is) =>
        '{ DynamicOptic.Node.AtIndices(Seq(${Varargs(is.map(Expr(_)))}*)) }

      case DynamicOptic.Node.Elements =>
        '{ DynamicOptic.Node.Elements }

      case DynamicOptic.Node.AtMapKey(v) =>
        val dv = convertDynamicValue(v)
        '{ DynamicOptic.Node.AtMapKey($dv) }

      case DynamicOptic.Node.AtMapKeys(vs) =>
        val dv = vs.map(convertDynamicValue)
        '{ DynamicOptic.Node.AtMapKeys(Seq(${Varargs(dv)}*)) }

      case DynamicOptic.Node.MapValues =>
        '{ DynamicOptic.Node.MapValues }

      case DynamicOptic.Node.MapKeys =>
        '{ DynamicOptic.Node.MapKeys }

      case DynamicOptic.Node.Case(n) =>
        '{ DynamicOptic.Node.Case(${Expr(n)}) }

      case other =>
        report.error(
          s"Unsupported DynamicOptic.Node encountered in macro: $other",
          Position.ofMacroExpansion
        )
        '{ DynamicOptic.Node.Elements }
    }
  }

  // -----------------------------
  // DynamicValue conversion
  // -----------------------------
  private def convertDynamicValue(v: DynamicValue)(using Quotes): Expr[DynamicValue] = {
    import quotes.reflect.*

    v match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        '{ DynamicValue.Primitive(PrimitiveValue.String(${Expr(s)})) }

      case DynamicValue.Primitive(PrimitiveValue.Int(i)) =>
        '{ DynamicValue.Primitive(PrimitiveValue.Int(${Expr(i)})) }

      case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) =>
        '{ DynamicValue.Primitive(PrimitiveValue.Boolean(${Expr(b)})) }

      case DynamicValue.Primitive(PrimitiveValue.Char(c)) =>
        '{ DynamicValue.Primitive(PrimitiveValue.Char(${Expr(c)})) }

      case other =>
        report.error(
          s"Unsupported map key type in p\"...\" macro: $other",
          Position.ofMacroExpansion
        )
        '{ DynamicValue.Primitive(PrimitiveValue.String("")) }
    }
  }
}
