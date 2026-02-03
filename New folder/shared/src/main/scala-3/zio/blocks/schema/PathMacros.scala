package zio.blocks.schema

import scala.quoted.*

object PathMacros {

  def pImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    args match {
      case Varargs(argExprs) if argExprs.nonEmpty =>
        report.errorAndAbort(
          "Path interpolator does not support runtime arguments. Use only literal strings like p\".field[0]\""
        )
      case _ =>
    }

    val term                = sc.asTerm
    val parts: List[String] = term match {
      case Inlined(_, _, Apply(_, List(Typed(Repeated(values, _), _)))) =>
        values.collect { case Literal(StringConstant(s)) =>
          s
        }
      case Apply(_, List(Typed(Repeated(values, _), _))) =>
        values.collect { case Literal(StringConstant(s)) =>
          s
        }
      case _ =>
        report.errorAndAbort(s"Unable to extract string literal from path interpolator")
    }

    if (parts.length != 1) {
      report.errorAndAbort(
        "Path interpolator does not support runtime arguments. Use only literal strings like p\".field[0]\""
      )
    }

    val pathString = parts.head

    PathParser.parse(pathString) match {
      case Left(error) =>
        report.errorAndAbort(error.message)

      case Right(nodes) =>
        buildDynamicOpticExpr(nodes)
    }
  }

  private def buildDynamicOpticExpr(nodes: Vector[DynamicOptic.Node])(using Quotes): Expr[DynamicOptic] = {
    val nodeExprs  = nodes.map(buildNodeExpr)
    val vectorExpr = Expr.ofSeq(nodeExprs)

    '{ DynamicOptic(Vector($vectorExpr: _*)) }
  }

  private def buildNodeExpr(node: DynamicOptic.Node)(using Quotes): Expr[DynamicOptic.Node] = {
    import DynamicOptic.Node

    node match {
      case Node.Field(name) =>
        val nameExpr = Expr(name)
        '{ Node.Field($nameExpr) }

      case Node.Case(name) =>
        val nameExpr = Expr(name)
        '{ Node.Case($nameExpr) }

      case Node.AtIndex(index) =>
        val indexExpr = Expr(index)
        '{ Node.AtIndex($indexExpr) }

      case Node.AtIndices(indices) =>
        val indicesExpr = Expr(indices)
        '{ Node.AtIndices($indicesExpr) }

      case Node.AtMapKey(key) =>
        val keyExpr = buildDynamicValueExpr(key)
        '{ Node.AtMapKey($keyExpr) }

      case Node.AtMapKeys(keys) =>
        val keysExpr = Expr.ofSeq(keys.map(buildDynamicValueExpr))
        '{ Node.AtMapKeys(Seq($keysExpr: _*)) }

      case Node.Elements =>
        '{ Node.Elements }

      case Node.MapKeys =>
        '{ Node.MapKeys }

      case Node.MapValues =>
        '{ Node.MapValues }

      case Node.Wrapped =>
        '{ Node.Wrapped }
    }
  }

  private def buildDynamicValueExpr(value: DynamicValue)(using Quotes): Expr[DynamicValue] = {
    import quotes.reflect.*

    value match {
      case DynamicValue.Primitive(pv) =>
        val pvExpr = buildPrimitiveValueExpr(pv)
        '{ DynamicValue.Primitive($pvExpr) }

      case _ =>
        report.errorAndAbort(s"Unsupported DynamicValue type: ${value.getClass.getName}")
    }
  }

  private def buildPrimitiveValueExpr(pv: PrimitiveValue)(using Quotes): Expr[PrimitiveValue] = {
    import quotes.reflect.*

    pv match {
      case PrimitiveValue.String(value) =>
        val valueExpr = Expr(value)
        '{ PrimitiveValue.String($valueExpr) }

      case PrimitiveValue.Int(value) =>
        val valueExpr = Expr(value)
        '{ PrimitiveValue.Int($valueExpr) }

      case PrimitiveValue.Char(value) =>
        val valueExpr = Expr(value)
        '{ PrimitiveValue.Char($valueExpr) }

      case PrimitiveValue.Boolean(value) =>
        val valueExpr = Expr(value)
        '{ PrimitiveValue.Boolean($valueExpr) }

      case _ =>
        report.errorAndAbort(s"Unsupported PrimitiveValue type: ${pv.getClass.getName}")
    }
  }
}
