package zio.blocks.typeid

import scala.quoted.*
import zio.blocks.typeid.{TypeId, Owner, TypeParam, Member, TermPath, Param}

object TypeIdMacros {
  def deriveMacro[A <: AnyKind: Type](using q: Quotes): Expr[TypeId[A]] = {
    import q.reflect.*

    def getOwner(sym: Symbol): Expr[Owner] = {
      def loop(s: Symbol, segments: List[Expr[Owner.Segment]]): List[Expr[Owner.Segment]] =
        if (s == Symbol.noSymbol || s == defn.RootClass || s == defn.EmptyPackageClass) {
          segments
        } else {
          val segment = if (s.isPackageDef) {
            '{ Owner.Package(${ Expr(s.name) }) }
          } else if (s.flags.is(Flags.Module)) {
            '{ Owner.Term(${ Expr(s.name) }) }
          } else {
            '{ Owner.Type(${ Expr(s.name) }) }
          }
          loop(s.owner, segment :: segments)
        }
      val segments     = loop(sym.owner, Nil)
      val segmentsList = Expr.ofList(segments)
      '{ Owner($segmentsList) }
    }

    def getTypeParams(sym: Symbol): Expr[List[TypeParam]] = {
      val typeParamSyms = if (sym.isClassDef) {
        sym.primaryConstructor.paramSymss.flatten.filter(_.isTypeParam)
      } else {
        sym.typeMembers.filter(_.isTypeParam)
      }

      val paramsExpr = typeParamSyms.zipWithIndex.map { case (s, i) =>
        '{ TypeParam(${ Expr(s.name) }, ${ Expr(i) }) }
      }
      Expr.ofList(paramsExpr)
    }

    def getTypeRepr(tpe: TypeRepr, ctxParams: List[Symbol]): Expr[zio.blocks.typeid.TypeRepr] =
      tpe.dealias match {
        case TypeRef(_, name) =>
          val sym        = tpe.typeSymbol
          val paramIndex = ctxParams.indexOf(sym)
          if (paramIndex >= 0) {
            '{ zio.blocks.typeid.TypeRepr.ParamRef(TypeParam(${ Expr(sym.name) }, ${ Expr(paramIndex) })) }
          } else {
            val tpeType = tpe.asType
            tpeType match {
              case '[t] =>
                val typeId = '{ TypeId.derive[t] }
                '{ zio.blocks.typeid.TypeRepr.Ref($typeId) }
            }
          }
        case AppliedType(tycon, args) =>
          val tyconExpr = getTypeRepr(tycon, ctxParams)
          val argsExpr  = Expr.ofList(args.map(a => getTypeRepr(a, ctxParams)))
          '{ zio.blocks.typeid.TypeRepr.Applied($tyconExpr, $argsExpr) }
        case TermRef(_, name) =>
          val sym  = tpe.termSymbol
          val path = getTermPath(sym)
          '{ zio.blocks.typeid.TypeRepr.Singleton($path) }
        case ConstantType(c) =>
          val value = c.value match {
            case v: Boolean => Expr(v)
            case v: Byte    => Expr(v)
            case v: Short   => Expr(v)
            case v: Int     => Expr(v)
            case v: Long    => Expr(v)
            case v: Float   => Expr(v)
            case v: Double  => Expr(v)
            case v: Char    => Expr(v)
            case v: String  => Expr(v)
            case ()         => '{ () }
            case _          => '{ "unknown" }
          }
          '{ zio.blocks.typeid.TypeRepr.Constant($value) }
        case Refinement(_, _, _) =>
          val (base, members) = collectRefinements(tpe, ctxParams)
          val baseExpr        = getTypeRepr(base, ctxParams)
          val membersExpr     = Expr.ofList(members)
          '{ zio.blocks.typeid.TypeRepr.Structural(List($baseExpr), $membersExpr) }
        case AndType(left, right) =>
          '{
            zio.blocks.typeid.TypeRepr
              .Intersection(${ getTypeRepr(left, ctxParams) }, ${ getTypeRepr(right, ctxParams) })
          }
        case OrType(left, right) =>
          '{ zio.blocks.typeid.TypeRepr.Union(${ getTypeRepr(left, ctxParams) }, ${ getTypeRepr(right, ctxParams) }) }
        case _ =>
          if (tpe =:= TypeRepr.of[Any]) '{ zio.blocks.typeid.TypeRepr.AnyType }
          else if (tpe =:= TypeRepr.of[Nothing]) '{ zio.blocks.typeid.TypeRepr.NothingType }
          else report.errorAndAbort(s"Unsupported type: ${tpe.show}")
      }

    def collectRefinements(tpe: TypeRepr, ctxParams: List[Symbol]): (TypeRepr, List[Expr[Member]]) =
      tpe match {
        case Refinement(parent, name, info) =>
          val (base, members) = collectRefinements(parent, ctxParams)
          val member          = info match {
            case TypeBounds(low, high) =>
              val lowExpr  = getTypeRepr(low, ctxParams)
              val highExpr = getTypeRepr(high, ctxParams)
              '{ Member.TypeMember(${ Expr(name) }, Nil, Some($lowExpr), Some($highExpr)) }
            case MethodType(paramNames, paramTypes, resType) =>
              val params = paramNames.zip(paramTypes).map { case (n, t) =>
                '{ Param(${ Expr(n) }, ${ getTypeRepr(t, ctxParams) }) }
              }
              val paramsExpr = Expr.ofList(params)
              val resExpr    = getTypeRepr(resType, ctxParams)
              '{ Member.Def(${ Expr(name) }, Nil, List($paramsExpr), $resExpr) }
            case ByNameType(t) =>
              val tExpr = getTypeRepr(t, ctxParams)
              '{ Member.Def(${ Expr(name) }, Nil, Nil, $tExpr) }
            case _ =>
              val tpeExpr = getTypeRepr(info, ctxParams)
              '{ Member.Val(${ Expr(name) }, $tpeExpr) }
          }
          (base, member :: members)
        case _ => (tpe, Nil)
      }

    def getTermPath(sym: Symbol): Expr[TermPath] = {
      def loop(s: Symbol, segments: List[Expr[TermPath.Segment]]): List[Expr[TermPath.Segment]] =
        if (s == Symbol.noSymbol || s == defn.RootClass || s == defn.EmptyPackageClass) {
          segments
        } else {
          val segment = if (s.isPackageDef) {
            '{ TermPath.Package(${ Expr(s.name) }) }
          } else {
            '{ TermPath.Term(${ Expr(s.name) }) }
          }
          loop(s.owner, segment :: segments)
        }
      val segments     = loop(sym, Nil)
      val segmentsList = Expr.ofList(segments)
      '{ TermPath($segmentsList) }
    }

    val tpe = TypeRepr.of[A]
    val sym = tpe.typeSymbol

    if (sym == Symbol.noSymbol) {
      report.errorAndAbort(s"Could not find symbol for type ${tpe.show}")
    }

    val name       = Expr(sym.name)
    val owner      = getOwner(sym)
    val typeParams = getTypeParams(sym)

    val ctxParams = if (sym.isClassDef) {
      sym.primaryConstructor.paramSymss.flatten.filter(_.isTypeParam)
    } else {
      sym.typeMembers.filter(_.isTypeParam)
    }

    if (sym.isClassDef) {
      '{ TypeId.NominalImpl($name, $owner, $typeParams).asInstanceOf[TypeId[A]] }
    } else if (sym.flags.is(Flags.Opaque)) {
      sym.tree match {
        case TypeDef(_, rhs) =>
          val reprType = rhs match {
            case t: TypeTree => t.tpe
            case _           => report.errorAndAbort(s"Unexpected RHS for opaque type ${sym.name}")
          }
          val reprExpr = getTypeRepr(reprType, ctxParams)
          '{ TypeId.OpaqueImpl($name, $owner, $typeParams, $reprExpr).asInstanceOf[TypeId[A]] }
        case _ => report.errorAndAbort(s"Unexpected tree for opaque type ${sym.name}")
      }
    } else if (sym.isTypeDef) {
      sym.tree match {
        case TypeDef(_, rhs) =>
          rhs match {
            case t: TypeTree =>
              val aliasedExpr = getTypeRepr(t.tpe, ctxParams)
              '{ TypeId.AliasImpl($name, $owner, $typeParams, $aliasedExpr).asInstanceOf[TypeId[A]] }
            case t: TypeBoundsTree =>
              '{ TypeId.NominalImpl($name, $owner, $typeParams).asInstanceOf[TypeId[A]] }
            case _ =>
              '{ TypeId.NominalImpl($name, $owner, $typeParams).asInstanceOf[TypeId[A]] }
          }
        case _ => '{ TypeId.NominalImpl($name, $owner, $typeParams).asInstanceOf[TypeId[A]] }
      }
    } else {
      '{ TypeId.NominalImpl($name, $owner, $typeParams).asInstanceOf[TypeId[A]] }
    }
  }
}
