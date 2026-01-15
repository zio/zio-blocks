package zio.blocks.schema.migration.macros

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.migration.ToDynamicOptic
import scala.quoted.*

object AccessorMacros {

  inline def derive[S, A](inline selector: S => A): ToDynamicOptic[S, A] =
    ${ deriveImpl[S, A]('selector) }

  def deriveImpl[S: Type, A: Type](selector: Expr[S => A])(using Quotes): Expr[ToDynamicOptic[S, A]] = {
    import quotes.reflect.*

    def extractTag(tpe: TypeRepr): String =
      tpe match {
        case Refinement(_, "Tag", ConstantType(StringConstant(value))) => value
        case Refinement(parent, _, _)                                  => extractTag(parent)

        case AndType(left, right) =>
          val leftTag =
            try { extractTag(left) }
            catch { case _ => "" }
          if (leftTag.nonEmpty && leftTag != "Any" && !leftTag.contains("&")) leftTag
          else extractTag(right)

        case _ => tpe.show.split('.').last.stripSuffix(".type")
      }

    def extractPath(tree: Tree): List[DynamicOptic.Node] =
      tree match {
        case Inlined(_, _, target) => extractPath(target)
        case Lambda(_, body)       => extractPath(body)
        case Typed(term, _)        => extractPath(term)
        case Block(Nil, term)      => extractPath(term)

        case Apply(TypeApply(fn, List(tpt)), List(obj)) if fn.show.contains("when") =>
          extractPath(obj) :+ DynamicOptic.Node.Case(extractTag(tpt.tpe))

        case TypeApply(Select(obj, "when"), List(tpt)) =>
          extractPath(obj) :+ DynamicOptic.Node.Case(extractTag(tpt.tpe))

        case Apply(Select(qual, "selectDynamic"), List(Literal(StringConstant(name)))) =>
          extractPath(qual) :+ DynamicOptic.Node.Field(name)

        case Apply(Select(qual, name), _) if name == "reflectiveSelectable" || name == "$asInstanceOf$" =>
          extractPath(qual)

        case Apply(TypeApply(Select(qual, "$asInstanceOf$"), _), _) =>
          extractPath(qual)

        case Select(qual, name) =>
          extractPath(qual) :+ DynamicOptic.Node.Field(name)

        case TypeApply(inner, _) => extractPath(inner)

        case Ident(_) => Nil
        case _        => Nil
      }

    val nodesList = extractPath(selector.asTerm)

    def liftNode(node: DynamicOptic.Node): Expr[DynamicOptic.Node] = node match {
      case DynamicOptic.Node.Field(name) => '{ DynamicOptic.Node.Field(${ Expr(name) }) }
      case DynamicOptic.Node.Case(name)  => '{ DynamicOptic.Node.Case(${ Expr(name) }) }
      case DynamicOptic.Node.Elements    => '{ DynamicOptic.Node.Elements }
      case _                             => '{ DynamicOptic.Node.Elements }
    }

    val liftedNodes = Expr.ofList(nodesList.map(liftNode))

    '{
      new ToDynamicOptic[S, A] {
        def apply(): DynamicOptic =
          DynamicOptic($liftedNodes.toIndexedSeq)
      }
    }
  }
}
