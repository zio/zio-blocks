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
      tpe.dealias match {
        case Refinement(_, "Tag", ConstantType(StringConstant(value))) => value
        case Refinement(parent, _, _)                                  => extractTag(parent)
        case AndType(left, right)                                      =>
          val leftTag = try { extractTag(left) }
          catch { case _: Throwable => "" }
          if (leftTag.nonEmpty && leftTag != "Any") leftTag else extractTag(right)
        case _ => tpe.typeSymbol.name.stripSuffix("$")
      }

    // Helper to peel off implicit wrappers (e.g., CollectionOps(obj))
    def unwrapImplicit(term: Term): Term = term match {
      case Apply(_, List(inner)) => inner
      case _                     => term
    }

    def extractPath(tree: Tree): List[DynamicOptic.Node] =
      tree match {
        case Inlined(_, _, target) => extractPath(target)
        case Lambda(_, body)       => extractPath(body)
        case Block(Nil, term)      => extractPath(term)
        case Typed(term, _)        => extractPath(term)

        // Handle Structural Type Wrapper (Selectable)
        case Apply(fun, args) if fun.symbol.name == "reflectiveSelectableFromLangReflectiveCalls" =>
          args.headOption match {
            case Some(obj) => extractPath(obj)
            case None      => report.errorAndAbort("Structural selector wrapper found but no object argument present.")
          }

        case Apply(fun, _) if fun.symbol.name == "reflectiveSelectableFromLangReflectiveCalls" =>
          extractPath(fun)

        case TypeApply(fun, _) if fun.symbol.name == "reflectiveSelectableFromLangReflectiveCalls" =>
          extractPath(fun)

        case TypeApply(Select(qual, "$asInstanceOf$"), _) => extractPath(qual)

        // selectDynamic for Structural Fields
        case Apply(Select(qual, "selectDynamic"), List(Literal(StringConstant(name)))) =>
          extractPath(qual) :+ DynamicOptic.Node.Field(name)

        // Handle .each (Collection Traversal) with Implicit Wrapper support
        case Select(qual, "each") =>
          extractPath(unwrapImplicit(qual)) :+ DynamicOptic.Node.Elements

        // Handle .when[T] (Sum Types) with Implicit Wrapper support
        // [FIX] Corrected 'tpe.tpe' to 'tpt.tpe'
        case TypeApply(Select(qual, "when"), List(tpt)) =>
          extractPath(unwrapImplicit(qual)) :+ DynamicOptic.Node.Case(extractTag(tpt.tpe))

        case Apply(TypeApply(Select(qual, "when"), List(tpt)), _) =>
          extractPath(unwrapImplicit(qual)) :+ DynamicOptic.Node.Case(extractTag(tpt.tpe))

        case Select(qual, name) =>
          extractPath(qual) :+ DynamicOptic.Node.Field(name)

        case Ident(_) => Nil

        case Apply(fun, _) =>
          if (fun.symbol.name == "reflectiveSelectableFromLangReflectiveCalls") extractPath(fun)
          else report.errorAndAbort(s"Invalid Selector: Method call '${fun.show}' is not permitted in migration paths.")

        case _ =>
          report.errorAndAbort(s"Unsupported selector expression: ${tree.show}. Ensure only field access is used.")
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
      ToDynamicOptic[S, A](
        DynamicOptic(zio.blocks.chunk.Chunk.fromIterable($liftedNodes))
      )
    }
  }
}
