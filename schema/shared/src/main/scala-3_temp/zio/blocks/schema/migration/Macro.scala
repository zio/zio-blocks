package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import scala.quoted._

object Macro {

  extension [A](iter: Iterable[A])
    def each: A = ???

  extension [K, V](map: Map[K, V])
    def eachKey: K = ???
    def eachValue: V = ???

  // Commenting out select and SchemaExpr.Path for now to focus on toPath
  // inline def select[S, A](inline f: S => A)(using Schema[S], Schema[A]): SchemaExpr[S, A] = ${ selectImpl('f) }

  // def selectImpl[S: Type, A: Type](f: Expr[S => A])(using Quotes): Expr[SchemaExpr[S, A]] = {
  //   import quotes.reflect._
  //   val opticExpr = toPathImpl(f)
  //   '{ SchemaExpr.Path($opticExpr) }
  // }

  // Using asInstanceOf[Any] as a temporary measure to make it compile
  // A proper solution would involve passing ToExpr[K] implicitly or using Schema-driven ToExpr
  private given anyToExpr: ToExpr[Any] = new ToExpr[Any] {
    def apply(value: Any)(using Quotes): Expr[Any] = value match {
      case s: String => Expr(s)
      case i: Int => Expr(i)
      case b: Boolean => Expr(b)
      case l: Long => Expr(l)
      case d: Double => Expr(d)
      case f: Float => Expr(f)
      case c: Char => Expr(c)
      case b: Byte => Expr(b)
      case s: Short => Expr(s)
      case other => quotes.reflect.report.errorAndAbort(s"Unsupported type for ToExpr[Any]: ${other.getClass.getName}")
    }
  }

  private def toExprOpticNode(using Quotes)(node: DynamicOptic.Node): Expr[DynamicOptic.Node] = node match {
    case DynamicOptic.Node.Field(name) => '{ DynamicOptic.Node.Field(${Expr(name)}) }
    case DynamicOptic.Node.Case(name) => '{ DynamicOptic.Node.Case(${Expr(name)}) }
    case DynamicOptic.Node.AtIndex(index) => '{ DynamicOptic.Node.AtIndex(${Expr(index)}) }
    case DynamicOptic.Node.Elements => '{ DynamicOptic.Node.Elements }
    case DynamicOptic.Node.MapKeys => '{ DynamicOptic.Node.MapKeys }
    case DynamicOptic.Node.MapValues => '{ DynamicOptic.Node.MapValues }
    case DynamicOptic.Node.Wrapped => '{ DynamicOptic.Node.Wrapped }
    case DynamicOptic.Node.AtMapKey(key) => '{ DynamicOptic.Node.AtMapKey(${Expr(key.toString)}) } // Cast to String for now
    case DynamicOptic.Node.AtIndices(indices) => '{ DynamicOptic.Node.AtIndices(${Expr.ofSeq(indices.map(Expr(_)))}) }
    case DynamicOptic.Node.AtMapKeys(keys) => '{ DynamicOptic.Node.AtMapKeys(${Expr.ofSeq(keys.map(Expr(_.toString)))}) } // Cast to String for now
    case _ => quotes.reflect.report.errorAndAbort(s"Unsupported DynamicOptic.Node: $node")
  }

  private given ToExpr[DynamicOptic] with {
    def apply(optic: DynamicOptic)(using Quotes): Expr[DynamicOptic] = {
      val nodesExpr = Expr.ofSeq(optic.nodes.map(toExprOpticNode))
      '{ DynamicOptic($nodesExpr.toIndexedSeq) }
    }
  }

  inline def toPath[S, A](inline f: S => A): DynamicOptic = ${ toPathImpl('f) }

  def toPathImpl[S: Type, A: Type](f: Expr[S => A])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect._

    def extractPath(term: Term): DynamicOptic = term match {
      case Apply(Select(_, "each"), List(rest)) => extractPath(rest).elements
      case Apply(Select(_, "eachKey"), List(rest)) => extractPath(rest).mapKeys
      case Apply(Select(_, "eachValue"), List(rest)) => extractPath(rest).mapValues
      case Select(rest, "each") => extractPath(rest).elements
      case Select(rest, "eachKey") => extractPath(rest).mapKeys
      case Select(rest, "eachValue") => extractPath(rest).mapValues
      case Select(rest, name) => extractPath(rest).field(name)
      case Apply(Select(rest, "apply"), List(Literal(IntConstant(i)))) => extractPath(rest).at(i)
      case Ident(_) => DynamicOptic.root
      case Inlined(_, _, body) => extractPath(body)
      case _ => report.errorAndAbort(s"Unsupported path element: $term")
    }

    val optic = f.asTerm match {
      case Inlined(_, _, Block(List(DefDef(_, _, _, Some(term))), _)) => extractPath(term)
      case _ => report.errorAndAbort(s"Unsupported function shape: ${f.asTerm}")
    }

    Expr(optic)
  }
}
