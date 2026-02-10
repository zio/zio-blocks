package zio.blocks.schema

import zio.blocks.chunk.Chunk
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
      case Right(nodes) => buildDynamicOpticExpr(nodes)
      case Left(error)  => report.errorAndAbort(error.message)
    }
  }

  private def buildDynamicOpticExpr(nodes: Chunk[DynamicOptic.Node])(using Quotes): Expr[DynamicOptic] = {
    val nodeExprs = nodes.map(buildNodeExpr)
    val chunkExpr = Expr.ofSeq(nodeExprs)
    '{ DynamicOptic(Vector($chunkExpr: _*)) }
  }

  private def buildNodeExpr(node: DynamicOptic.Node)(using Quotes): Expr[DynamicOptic.Node] = {
    import DynamicOptic.Node

    node match {
      case Node.Field(name) =>
        val nameExpr = Expr(name)
        '{ new Node.Field($nameExpr) }
      case Node.Case(name) =>
        val nameExpr = Expr(name)
        '{ new Node.Case($nameExpr) }
      case Node.AtIndex(index) =>
        val indexExpr = Expr(index)
        '{ new Node.AtIndex($indexExpr) }
      case Node.AtIndices(indices) =>
        val indicesExpr = Expr(indices)
        '{ new Node.AtIndices($indicesExpr) }
      case Node.AtMapKey(key) =>
        val keyExpr = buildDynamicValueExpr(key)
        '{ new Node.AtMapKey($keyExpr) }
      case Node.AtMapKeys(keys) =>
        val keysExpr = Expr.ofSeq(keys.map(buildDynamicValueExpr))
        '{ Node.AtMapKeys(Seq($keysExpr: _*)) }
      case Node.Elements      => '{ Node.Elements }
      case Node.MapKeys       => '{ Node.MapKeys }
      case Node.MapValues     => '{ Node.MapValues }
      case Node.Wrapped       => '{ Node.Wrapped }
      case Node.TypeSearch(_) =>
        quotes.reflect.report.errorAndAbort(
          "TypeSearch is not supported in path interpolators. Use SchemaSearch via #TypeName syntax instead."
        )

      case Node.SchemaSearch(schemaRepr) =>
        val schemaReprExpr = buildSchemaReprExpr(schemaRepr)
        '{ Node.SchemaSearch($schemaReprExpr) }
    }
  }

  private def buildSchemaReprExpr(repr: SchemaRepr)(using Quotes): Expr[SchemaRepr] =
    repr match {
      case SchemaRepr.Nominal(name) =>
        val nameExpr = Expr(name)
        '{ SchemaRepr.Nominal($nameExpr) }
      case SchemaRepr.Primitive(name) =>
        val nameExpr = Expr(name)
        '{ SchemaRepr.Primitive($nameExpr) }
      case SchemaRepr.Record(fields) =>
        val fieldExprs = fields.map { case (name, fieldRepr) =>
          val nameExpr = Expr(name)
          val reprExpr = buildSchemaReprExpr(fieldRepr)
          '{ ($nameExpr, $reprExpr) }
        }
        val fieldsExpr = Expr.ofSeq(fieldExprs)
        '{ SchemaRepr.Record(Vector($fieldsExpr: _*)) }
      case SchemaRepr.Variant(cases) =>
        val caseExprs = cases.map { case (name, caseRepr) =>
          val nameExpr = Expr(name)
          val reprExpr = buildSchemaReprExpr(caseRepr)
          '{ ($nameExpr, $reprExpr) }
        }
        val casesExpr = Expr.ofSeq(caseExprs)
        '{ SchemaRepr.Variant(Vector($casesExpr: _*)) }
      case SchemaRepr.Sequence(element) =>
        val elementExpr = buildSchemaReprExpr(element)
        '{ SchemaRepr.Sequence($elementExpr) }
      case SchemaRepr.Map(key, value) =>
        val keyExpr   = buildSchemaReprExpr(key)
        val valueExpr = buildSchemaReprExpr(value)
        '{ SchemaRepr.Map($keyExpr, $valueExpr) }
      case SchemaRepr.Optional(inner) =>
        val innerExpr = buildSchemaReprExpr(inner)
        '{ SchemaRepr.Optional($innerExpr) }
      case SchemaRepr.Wildcard =>
        '{ SchemaRepr.Wildcard }
    }

  private def buildDynamicValueExpr(value: DynamicValue)(using Quotes): Expr[DynamicValue] = {
    import quotes.reflect.*

    value match {
      case DynamicValue.Primitive(pv) =>
        val pvExpr = buildPrimitiveValueExpr(pv)
        '{ new DynamicValue.Primitive($pvExpr) }
      case _ => report.errorAndAbort(s"Unsupported DynamicValue type: ${value.getClass.getName}")
    }
  }

  private def buildPrimitiveValueExpr(pv: PrimitiveValue)(using Quotes): Expr[PrimitiveValue] = {
    import quotes.reflect.*

    pv match {
      case PrimitiveValue.String(value) =>
        val valueExpr = Expr(value)
        '{ new PrimitiveValue.String($valueExpr) }
      case PrimitiveValue.Int(value) =>
        val valueExpr = Expr(value)
        '{ new PrimitiveValue.Int($valueExpr) }
      case PrimitiveValue.Char(value) =>
        val valueExpr = Expr(value)
        '{ new PrimitiveValue.Char($valueExpr) }
      case PrimitiveValue.Boolean(value) =>
        val valueExpr = Expr(value)
        '{ new PrimitiveValue.Boolean($valueExpr) }
      case _ => report.errorAndAbort(s"Unsupported PrimitiveValue type: ${pv.getClass.getName}")
    }
  }
}
