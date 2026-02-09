package zio.blocks.schema

import zio.blocks.chunk.Chunk
import scala.reflect.macros.blackbox

object PathMacros {

  def pImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[DynamicOptic] = {
    import c.universe._

    c.prefix.tree match {
      case Apply(_, List(Apply(_, parts))) =>
        if (args.nonEmpty) {
          c.abort(
            c.enclosingPosition,
            "Path interpolator does not support runtime arguments. Use only literal strings like p\".field[0]\""
          )
        }

        val pathString = parts match {
          case List(Literal(Constant(str: String))) => str
          case _                                    =>
            c.abort(
              c.enclosingPosition,
              "Path interpolator does not support runtime arguments. Use only literal strings like p\".field[0]\""
            )
        }

        PathParser.parse(pathString) match {
          case Left(error) =>
            c.abort(c.enclosingPosition, error.message)

          case Right(nodes) =>
            buildDynamicOpticExpr(c)(nodes)
        }

      case _ =>
        c.abort(c.enclosingPosition, "Invalid string interpolation")
    }
  }

  private def buildDynamicOpticExpr(c: blackbox.Context)(nodes: Chunk[DynamicOptic.Node]): c.Expr[DynamicOptic] = {
    import c.universe._

    val nodeExprs = nodes.map(node => buildNodeExpr(c)(node))

    val vectorTree = q"_root_.scala.Vector(..$nodeExprs)"

    c.Expr[DynamicOptic](q"_root_.zio.blocks.schema.DynamicOptic($vectorTree)")
  }

  private def buildNodeExpr(c: blackbox.Context)(node: DynamicOptic.Node): c.Tree = {
    import c.universe._
    import DynamicOptic.Node

    node match {
      case Node.Field(name) =>
        q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($name)"

      case Node.Case(name) =>
        q"_root_.zio.blocks.schema.DynamicOptic.Node.Case($name)"

      case Node.AtIndex(index) =>
        q"_root_.zio.blocks.schema.DynamicOptic.Node.AtIndex($index)"

      case Node.AtIndices(indices) =>
        val indicesSeq = indices.toSeq
        q"_root_.zio.blocks.schema.DynamicOptic.Node.AtIndices(_root_.scala.Seq(..$indicesSeq))"

      case Node.AtMapKey(key) =>
        val keyTree = buildDynamicValueExpr(c)(key)
        q"_root_.zio.blocks.schema.DynamicOptic.Node.AtMapKey($keyTree)"

      case Node.AtMapKeys(keys) =>
        val keyTrees = keys.map(buildDynamicValueExpr(c))
        q"_root_.zio.blocks.schema.DynamicOptic.Node.AtMapKeys(_root_.scala.Seq(..$keyTrees))"

      case Node.Elements =>
        q"_root_.zio.blocks.schema.DynamicOptic.Node.Elements"

      case Node.MapKeys =>
        q"_root_.zio.blocks.schema.DynamicOptic.Node.MapKeys"

      case Node.MapValues =>
        q"_root_.zio.blocks.schema.DynamicOptic.Node.MapValues"

      case Node.Wrapped =>
        q"_root_.zio.blocks.schema.DynamicOptic.Node.Wrapped"
    }
  }

  private def buildDynamicValueExpr(c: blackbox.Context)(value: DynamicValue): c.Tree = {
    import c.universe._

    value match {
      case DynamicValue.Primitive(pv) =>
        val pvTree = buildPrimitiveValueExpr(c)(pv)
        q"_root_.zio.blocks.schema.DynamicValue.Primitive($pvTree)"

      case _ =>
        c.abort(c.enclosingPosition, s"Unsupported DynamicValue type: ${value.getClass.getName}")
    }
  }

  private def buildPrimitiveValueExpr(c: blackbox.Context)(pv: PrimitiveValue): c.Tree = {
    import c.universe._

    pv match {
      case PrimitiveValue.String(value) =>
        q"_root_.zio.blocks.schema.PrimitiveValue.String($value)"

      case PrimitiveValue.Int(value) =>
        q"_root_.zio.blocks.schema.PrimitiveValue.Int($value)"

      case PrimitiveValue.Char(value) =>
        q"_root_.zio.blocks.schema.PrimitiveValue.Char($value)"

      case PrimitiveValue.Boolean(value) =>
        q"_root_.zio.blocks.schema.PrimitiveValue.Boolean($value)"

      case _ =>
        c.abort(c.enclosingPosition, s"Unsupported PrimitiveValue type: ${pv.getClass.getName}")
    }
  }
}
