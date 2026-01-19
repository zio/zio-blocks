package zio.blocks.schema

import scala.reflect.macros.blackbox

object PathMacros {

  def pImpl(c: blackbox.Context)
           (args: c.Expr[Any]*): c.Expr[DynamicOptic] = 
  {
    import c.universe._

    // Reject interpolation
    if (args.nonEmpty) {
      c.abort(c.enclosingPosition,
        "Interpolated arguments not allowed in p\"...\" expressions"
      )
    }

    // Extract literal
    val literal: String = c.prefix.tree match {
      case Apply(_, List(Apply(_, List(Literal(Constant(s: String)))))) => s
      case _ =>
        c.abort(c.enclosingPosition,
          "Literal string required in p\"...\" expressions"
        )
    }

    // Parse
    PathParser.parse(literal) match {

      case Left(err) =>
        c.abort(
          c.enclosingPosition,
          s"Path parsing failed: ${err.message} at index ${err.index}"
        )

      case Right(nodes) =>
        val nodeTrees: List[Tree] = nodes.map(nodeToTree(c)(_))

        // Build Vector syntax tree correctly
        val vectorTree =
          Apply(
            Select(Ident(TermName("Vector")), TermName("apply")),
            nodeTrees
          )

        // Wrap into DynamicOptic constructor **as Expr**
        c.Expr[DynamicOptic](
          Apply(
            Ident(TermName("DynamicOptic")),
            List(vectorTree)
          )
        )
    }
  }

  // Convert each parsed node into Scala-2 AST
  private def nodeToTree(c: blackbox.Context)
                        (node: DynamicOptic.Node): c.universe.Tree =
  {
    import c.universe._

    node match {
      case DynamicOptic.Node.Field(name) =>
        q"DynamicOptic.Node.Field($name)"

      case DynamicOptic.Node.AtIndex(i) =>
        q"DynamicOptic.Node.AtIndex($i)"

      case DynamicOptic.Node.AtIndices(is) =>
        val seqTree = q"Seq(..$is)"
        q"DynamicOptic.Node.AtIndices($seqTree)"

      case DynamicOptic.Node.Elements =>
        q"DynamicOptic.Node.Elements"

      case DynamicOptic.Node.AtMapKey(v) =>
        q"DynamicOptic.Node.AtMapKey(${valueToTree(c)(v)})"

      case DynamicOptic.Node.AtMapKeys(vs) =>
        val seqTree = q"Seq(..${vs.map(valueToTree(c))})"
        q"DynamicOptic.Node.AtMapKeys($seqTree)"

      case DynamicOptic.Node.MapValues =>
        q"DynamicOptic.Node.MapValues"

      case DynamicOptic.Node.MapKeys =>
        q"DynamicOptic.Node.MapKeys"

      case DynamicOptic.Node.Case(n) =>
        q"DynamicOptic.Node.Case($n)"

      case _ =>
        c.abort(c.enclosingPosition, "Unsupported DynamicOptic.Node in Scala-2 macro")
    }
  }

  private def valueToTree(c: blackbox.Context)
                         (v: DynamicValue): c.universe.Tree =
  {
    import c.universe._

    v match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        q"DynamicValue.Primitive(PrimitiveValue.String($s))"

      case DynamicValue.Primitive(PrimitiveValue.Int(i)) =>
        q"DynamicValue.Primitive(PrimitiveValue.Int($i))"

      case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) =>
        q"DynamicValue.Primitive(PrimitiveValue.Boolean($b))"

      case DynamicValue.Primitive(PrimitiveValue.Char(ch)) =>
        q"DynamicValue.Primitive(PrimitiveValue.Char($ch))"

      case _ =>
        c.abort(c.enclosingPosition, "Unsupported map key type in Scala-2 macro")
    }
  }
}
