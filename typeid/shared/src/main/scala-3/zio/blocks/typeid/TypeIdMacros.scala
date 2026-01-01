package zio.blocks.typeid

import scala.quoted.*

object TypeIdMacros {
  def deriveMacro[A <: AnyKind: Type](using Quotes): Expr[TypeId[A]] = {
    import quotes.reflect.*

    val tpe    = TypeRepr.of[A]
    val symbol = tpe.typeSymbol

    def extractOwner(s: Symbol): Owner = {
      def loop(curr: Symbol): List[Owner.Segment] =
        if (
          curr == Symbol.noSymbol || curr == defn.RootPackage || curr == defn.RootClass || curr.name == "<empty>" || curr.name == "<root>"
        ) Nil
        else {
          val segment =
            if (curr.flags.is(Flags.Package)) Owner.Package(curr.name)
            else if (curr.isTerm) Owner.Term(curr.name)
            else Owner.Type(curr.name)
          loop(curr.owner) :+ segment
        }
      Owner(loop(s.owner))
    }

    val name  = if (symbol.name == "<refinement>") "Refinement" else symbol.name
    val owner = extractOwner(symbol)

    val typeParamsExpressed = tpe match {
      case AppliedType(_, args) =>
        args.zipWithIndex.map { (arg, idx) =>
          val pName = arg.typeSymbol.name
          '{ TypeParam(${ Expr(pName) }, ${ Expr(idx) }) }
        }
      case _ =>
        symbol.primaryConstructor.paramSymss.flatten.filter(_.isType).zipWithIndex.map { (p, idx) =>
          '{ TypeParam(${ Expr(p.name) }, ${ Expr(idx) }) }
        }
    }

    val ownerSegmentsExpr = Expr.ofList(owner.segments.map {
      case Owner.Package(n) => '{ Owner.Package(${ Expr(n) }) }
      case Owner.Term(n)    => '{ Owner.Term(${ Expr(n) }) }
      case Owner.Type(n)    => '{ Owner.Type(${ Expr(n) }) }
    })

    val ownerExpr      = '{ Owner($ownerSegmentsExpr) }
    val typeParamsExpr = Expr.ofList(typeParamsExpressed)

    '{ TypeId.nominal[A](${ Expr(name) }, $ownerExpr, $typeParamsExpr) }
  }
}
