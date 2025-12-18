/*
package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import scala.quoted._

object Macro {
  trait PathSelector[S] {
    def apply(): DynamicOptic
  }

  def path[S, A](f: S => A): PathSelector[S] = new PathSelector[S] {
    def apply(): DynamicOptic = DynamicOptic.root
  }

  inline def toPath[S, A](inline f: S => A): DynamicOptic = ${ toPathImpl('f) }

  def toPathImpl[S: Type, A: Type](f: Expr[S => A])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect._

    def extractPath(term: Term): DynamicOptic = term match {
      case Select(rest, name) => extractPath(rest).field(name)
      case Apply(Select(rest, "apply"), List(Literal(IntConstant(i)))) => extractPath(rest).at(i)
      case Ident(_) => DynamicOptic.root
      case _ => report.errorAndAbort(s"Unsupported path element: $term")
    }

    val optic = f.asTerm match {
      case Inlined(_, _, Block(List(DefDef(_, _, _, Some(term))), _)) => extractPath(term)
      case _ => report.errorAndAbort(s"Unsupported function shape: ${f.asTerm}")
    }

    '{
      new PathSelector[S] {
        def apply(): DynamicOptic = ${ Expr(optic) }
      }.apply()
    }
  }
}
*/
