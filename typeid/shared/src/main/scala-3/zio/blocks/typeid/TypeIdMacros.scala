package zio.blocks.typeid

import scala.quoted.*

private[typeid] object TypeIdMacros {
  
  def deriveImpl[A <: AnyKind: Type](using Quotes): Expr[TypeId[A]] = {
    import quotes.reflect.*
    
    val tpe = TypeRepr.of[A]
    val typeSymbol = tpe.typeSymbol
    
    val ownerExpr = extractOwner(typeSymbol.owner)
    val typeParamsExpr = extractTypeParams(tpe)
    
    if (tpe.isOpaqueAlias) {
      val underlying = tpe.translucentSuperType
      val underlyingExpr = extractTypeRepr(underlying)
      '{
        TypeId.opaque[A](
          ${Expr(typeSymbol.name)},
          ${ownerExpr},
          ${typeParamsExpr},
          ${underlyingExpr}
        )
      }
    }
    else if (typeSymbol.isAliasType) {
      val underlying = tpe.dealias
      if (underlying =:= tpe) {
        '{
          TypeId.nominal[A](
            ${Expr(typeSymbol.name)},
            ${ownerExpr},
            ${typeParamsExpr}
          )
        }
      } else {
        val underlyingExpr = extractTypeRepr(underlying)
        '{
          TypeId.alias[A](
            ${Expr(typeSymbol.name)},
            ${ownerExpr},
            ${typeParamsExpr},
            ${underlyingExpr}
          )
        }
      }
    }
    else {
      '{
        TypeId.nominal[A](
          ${Expr(typeSymbol.name)},
          ${ownerExpr},
          ${typeParamsExpr}
        )
      }
    }
  }
  
  private def extractOwner(using Quotes)(symbol: quotes.reflect.Symbol): Expr[Owner] = {
    import quotes.reflect.*
    
    def loop(sym: Symbol, acc: List[Expr[Owner.Segment]]): List[Expr[Owner.Segment]] = {
      if (sym == defn.RootPackage || sym == defn.RootClass) {
        acc
      } else {
        val name = sym.name
        val segment: Expr[Owner.Segment] =
          if (sym.isPackageDef) {
            '{ Owner.Segment.Package(${Expr(name)}) }
          } else if (sym.flags.is(Flags.Module)) {
            val cleanName = if (name.endsWith("$")) name.dropRight(1) else name
            '{ Owner.Segment.Term(${Expr(cleanName)}) }
          } else {
            '{ Owner.Segment.Type(${Expr(name)}) }
          }
        loop(sym.owner, segment :: acc)
      }
    }
    
    val segments = loop(symbol, Nil).reverse
    Expr.ofList(segments) match {
      case '{ ${list}: List[Owner.Segment] } => '{ Owner(${list}) }
    }
  }
  
  private def extractTypeParams(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[List[TypeParam]] = {
    import quotes.reflect.*
    
    val typeParams = tpe.typeSymbol.typeMembers.collect {
      case sym if sym.isTypeParam => sym
    }
    val paramExprs = typeParams.zipWithIndex.map { case (sym, idx) =>
      '{ TypeParam(${Expr(sym.name)}, ${Expr(idx)}) }
    }
    Expr.ofList(paramExprs)
  }
  
  private def extractTypeRepr(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[TypeRepr] = {
    import quotes.reflect.*
    
    tpe match {
      case AppliedType(tycon, args) =>
        val tyconExpr = extractTypeRepr(tycon)
        val argsExprs = args.map(extractTypeRepr)
        Expr.ofList(argsExprs) match {
          case '{ ${list}: List[TypeRepr] } =>
            '{ TypeRepr.Applied(${tyconExpr}, ${list}) }
        }
      
      case ref if ref.typeSymbol != Symbol.noSymbol =>
        ref.asType match {
          case '[t] =>
            val typeIdExpr = deriveImpl[t]
            '{ TypeRepr.Ref(${typeIdExpr}) }
        }
      
      case AndType(left, right) =>
        val leftExpr = extractTypeRepr(left)
        val rightExpr = extractTypeRepr(right)
        '{ TypeRepr.Intersection(${leftExpr}, ${rightExpr}) }
      
      case OrType(left, right) =>
        val leftExpr = extractTypeRepr(left)
        val rightExpr = extractTypeRepr(right)
        '{ TypeRepr.Union(${leftExpr}, ${rightExpr}) }
      
      case ConstantType(const) =>
        val valueExpr = const.value match {
          case s: String => Expr(s)
          case i: Int => Expr(i)
          case l: Long => Expr(l)
          case b: Boolean => Expr(b)
          case c: Char => Expr(c)
          case f: Float => Expr(f)
          case d: Double => Expr(d)
          case other => Expr(other.toString)
        }
        valueExpr match {
          case '{ ${v}: t } => '{ TypeRepr.Constant(${v}) }
        }
      
      case _ if tpe =:= TypeRepr.of[Any] =>
        '{ TypeRepr.AnyType }
      
      case _ if tpe =:= TypeRepr.of[Nothing] =>
        '{ TypeRepr.NothingType }
      
      case _ =>
        tpe.asType match {
          case '[t] =>
            val typeIdExpr = deriveImpl[t]
            '{ TypeRepr.Ref(${typeIdExpr}) }
        }
    }
  }
}
