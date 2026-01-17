package zio.blocks.typeid

import java.util.{Collections, IdentityHashMap}
import scala.jdk.CollectionConverters._

/**
 * Handles structural equality and hashing for TypeRepr and TypeId. Accounts for
 * normalization, alias expansion, and substitution.
 */
object TypeEquality {

  def areEqual(a: TypeRepr, b: TypeRepr): Boolean = {
    val na = TypeNormalization.normalize(a)
    val nb = TypeNormalization.normalize(b)
    structurallyEqualRepr(na, nb, Set.empty[(TypeRepr, TypeRepr)])
  }

  private def structurallyEqualRepr(a: TypeRepr, b: TypeRepr, seen: Set[(TypeRepr, TypeRepr)]): Boolean = {
    if (a eq b) true
    else {
      val pair = (a, b)
      if (seen.exists { case (sa, sb) => (sa eq a) && (sb eq b) }) true
      else {
        val newSeen = seen + pair
        (a, b) match {
          case (TypeRepr.Ref(idA), TypeRepr.Ref(idB)) =>
            idA.fullName == idB.fullName && idA.arity == idB.arity

          case (TypeRepr.Applied(tyconA, argsA), TypeRepr.Applied(tyconB, argsB)) =>
            structurallyEqualRepr(tyconA, tyconB, newSeen) &&
            argsA.size == argsB.size &&
            argsA.zip(argsB).forall { case (a, b) => structurallyEqualRepr(a, b, newSeen) }

          case (TypeRepr.Intersection(compsA), TypeRepr.Intersection(compsB)) =>
            compsA.size == compsB.size &&
            compsA.zip(compsB).forall { case (a, b) => structurallyEqualRepr(a, b, newSeen) }

          case (TypeRepr.Union(compsA), TypeRepr.Union(compsB)) =>
            compsA.size == compsB.size &&
            compsA.zip(compsB).forall { case (a, b) => structurallyEqualRepr(a, b, newSeen) }

          case (TypeRepr.Function(paramsA, resA), TypeRepr.Function(paramsB, resB)) =>
            paramsA.size == paramsB.size &&
            paramsA.zip(paramsB).forall { case (a, b) => structurallyEqualRepr(a, b, newSeen) } &&
            structurallyEqualRepr(resA, resB, newSeen)

          case (TypeRepr.Tuple(elemsA), TypeRepr.Tuple(elemsB)) =>
            elemsA.size == elemsB.size &&
            elemsA.zip(elemsB).forall { case (a, b) =>
              a.label == b.label && structurallyEqualRepr(a.tpe, b.tpe, newSeen)
            }

          case (TypeRepr.AnyType, TypeRepr.AnyType)         => true
          case (TypeRepr.NothingType, TypeRepr.NothingType) => true
          case (TypeRepr.UnitType, TypeRepr.UnitType)       => true
          case (TypeRepr.NullType, TypeRepr.NullType)       => true
          case (TypeRepr.AnyKindType, TypeRepr.AnyKindType) => true

          case (TypeRepr.ConstantType(ca), TypeRepr.ConstantType(cb)) => ca == cb

          case (TypeRepr.Structural(pA, mA), TypeRepr.Structural(pB, mB)) =>
            pA.size == pB.size && pA.zip(pB).forall { case (a, b) => structurallyEqualRepr(a, b, newSeen) } &&
            mA.size == mB.size && mA.sortBy(_.name).zip(mB.sortBy(_.name)).forall { case (a, b) =>
              structurallyEqualMember(a, b, newSeen)
            }

          case (TypeRepr.TypeSelect(qA, mA), TypeRepr.TypeSelect(qB, mB)) =>
            qA.asString == qB.asString && mA == mB

          case (TypeRepr.TypeSelect(q, m), TypeRepr.Ref(id)) =>
            // Check if TypeSelect refers to the same type as Ref
            // TermPath.asString might be like "<root>.this.scala" while fullName is "scala"
            val qStr = q.asString.replace("<root>.this.", "").replace(".this", "")
            s"$qStr.$m" == id.fullName

          case (TypeRepr.Ref(id), TypeRepr.TypeSelect(q, m)) =>
            // Check if Ref refers to the same type as TypeSelect
            val qStr = q.asString.replace("<root>.this.", "").replace(".this", "")
            id.fullName == s"$qStr.$m"

          case (TypeRepr.ParamRef(pa, da), TypeRepr.ParamRef(pb, db)) =>
            pa.index == pb.index && da == db

          case (TypeRepr.Singleton(pA), TypeRepr.Singleton(pB)) =>
            pA.asString == pB.asString

          case (TypeRepr.ByName(uA), TypeRepr.ByName(uB)) =>
            structurallyEqualRepr(uA, uB, newSeen)

          case (TypeRepr.Repeated(uA), TypeRepr.Repeated(uB)) =>
            structurallyEqualRepr(uA, uB, newSeen)

          case (TypeRepr.ContextFunction(pA, rA), TypeRepr.ContextFunction(pB, rB)) =>
            pA.size == pB.size && pA.zip(pB).forall { case (a, b) => structurallyEqualRepr(a, b, newSeen) } &&
            structurallyEqualRepr(rA, rB, newSeen)

          case (TypeRepr.PolyFunction(tpsA, rA), TypeRepr.PolyFunction(tpsB, rB)) =>
            tpsA.size == tpsB.size && tpsA.zip(tpsB).forall { case (a, b) =>
              structurallyEqualTypeParam(a, b, newSeen)
            } &&
            structurallyEqualRepr(rA, rB, newSeen)

          case (TypeRepr.DependentFunction(psA, rA), TypeRepr.DependentFunction(psB, rB)) =>
            psA.size == psB.size && psA.zip(psB).forall { case (a, b) => structurallyEqualParam(a, b, newSeen) } &&
            structurallyEqualRepr(rA, rB, newSeen)

          case (TypeRepr.TypeLambda(tpsA, bA), TypeRepr.TypeLambda(tpsB, bB)) =>
            tpsA.size == tpsB.size && tpsA.zip(tpsB).forall { case (a, b) =>
              structurallyEqualTypeParam(a, b, newSeen)
            } &&
            structurallyEqualRepr(bA, bB, newSeen)

          case (TypeRepr.TypeProjection(pA, mA), TypeRepr.TypeProjection(pB, mB)) =>
            mA == mB && structurallyEqualRepr(pA, pB, newSeen)

          case (TypeRepr.ThisType(oA), TypeRepr.ThisType(oB)) =>
            oA.fullName == oB.fullName && oA.arity == oB.arity

          case (TypeRepr.SuperType(tA, mA), TypeRepr.SuperType(tB, mB)) =>
            mA.isDefined == mB.isDefined &&
            mA.zip(mB).forall { case (a, b) => structurallyEqualRepr(a, b, newSeen) } &&
            structurallyEqualRepr(tA, tB, newSeen)

          case (TypeRepr.MatchType(bA, sA, cA), TypeRepr.MatchType(bB, sB, cB)) =>
            cA.size == cB.size &&
            structurallyEqualRepr(bA, bB, newSeen) &&
            structurallyEqualRepr(sA, sB, newSeen) &&
            cA.zip(cB).forall { case (a, b) => structurallyEqualMatchCase(a, b, newSeen) }

          case (TypeRepr.Wildcard(bA), TypeRepr.Wildcard(bB)) =>
            structurallyEqualBounds(bA, bB, newSeen)

          case (TypeRepr.RecType(bA), TypeRepr.RecType(bB)) =>
            structurallyEqualRepr(bA, bB, newSeen)

          case (TypeRepr.RecThis, TypeRepr.RecThis) => true

          case (TypeRepr.Annotated(uA, _), TypeRepr.Annotated(uB, _)) =>
            structurallyEqualRepr(uA, uB, newSeen)

          case _ => false
        }
      }
    }
  }

  private def structurallyEqualMember(a: Member, b: Member, seen: Set[(TypeRepr, TypeRepr)]): Boolean = (a, b) match {
    case (Member.Val(nA, tA, mA, _, _), Member.Val(nB, tB, mB, _, _)) =>
      nA == nB && mA == mB && structurallyEqualRepr(tA, tB, seen)
    case (Member.Def(nA, tpA, pcA, rA, _, _), Member.Def(nB, tpB, pcB, rB, _, _)) =>
      nA == nB &&
      tpA.size == tpB.size && tpA.zip(tpB).forall { case (x, y) => structurallyEqualTypeParam(x, y, seen) } &&
      pcA.size == pcB.size && pcA.zip(pcB).forall { case (x, y) => structurallyEqualParamClause(x, y, seen) } &&
      structurallyEqualRepr(rA, rB, seen)
    case (Member.TypeMember(nA, tpA, bA, aA, _), Member.TypeMember(nB, tpB, bB, aB, _)) =>
      nA == nB &&
      tpA.size == tpB.size && tpA.zip(tpB).forall { case (x, y) => structurallyEqualTypeParam(x, y, seen) } &&
      structurallyEqualBounds(bA, bB, seen) &&
      aA.isDefined == aB.isDefined && ((aA, aB) match {
        case (Some(aa), Some(ab)) => structurallyEqualRepr(aa, ab, seen)
        case _                    => true
      })
    case _ => false
  }

  private def structurallyEqualTypeParam(a: TypeParam, b: TypeParam, seen: Set[(TypeRepr, TypeRepr)]): Boolean =
    a.index == b.index && a.variance == b.variance && a.kind == b.kind && structurallyEqualBounds(
      a.bounds,
      b.bounds,
      seen
    )

  private def structurallyEqualBounds(a: TypeBounds, b: TypeBounds, seen: Set[(TypeRepr, TypeRepr)]): Boolean =
    a.lower.size == b.lower.size && a.upper.size == b.upper.size &&
      a.lower.zip(b.lower).forall { case (x, y) => structurallyEqualRepr(x, y, seen) } &&
      a.upper.zip(b.upper).forall { case (x, y) => structurallyEqualRepr(x, y, seen) }

  private def structurallyEqualParamClause(a: ParamClause, b: ParamClause, seen: Set[(TypeRepr, TypeRepr)]): Boolean =
    a.getClass == b.getClass && a.params.size == b.params.size &&
      a.params.zip(b.params).forall { case (x, y) => structurallyEqualParam(x, y, seen) }

  private def structurallyEqualParam(a: Param, b: Param, seen: Set[(TypeRepr, TypeRepr)]): Boolean =
    a.hasDefault == b.hasDefault && a.isRepeated == b.isRepeated && structurallyEqualRepr(a.tpe, b.tpe, seen)

  private def structurallyEqualMatchCase(
    a: TypeRepr.MatchTypeCase,
    b: TypeRepr.MatchTypeCase,
    seen: Set[(TypeRepr, TypeRepr)]
  ): Boolean =
    a.bindings.size == b.bindings.size &&
      a.bindings.zip(b.bindings).forall { case (x, y) => structurallyEqualTypeParam(x, y, seen) } &&
      structurallyEqualRepr(a.pattern, b.pattern, seen) &&
      structurallyEqualRepr(a.result, b.result, seen)

  def structuralHash(a: TypeRepr): Int = {
    val na   = TypeNormalization.normalize(a)
    val seen = Collections.newSetFromMap(new IdentityHashMap[AnyRef, java.lang.Boolean]()).asScala
    stableHash(na, seen)
  }

  private def stableHash(tpe: TypeRepr, seen: collection.mutable.Set[AnyRef]): Int = {
    val ref = tpe.asInstanceOf[AnyRef]
    if (seen.contains(ref)) 0
    else {
      seen.add(ref)
      tpe match {
        case TypeRepr.Ref(id) =>
          var h = "Ref".hashCode
          h = 31 * h + id.fullName.hashCode()
          h = 31 * h + id.arity
          h

        case TypeRepr.TypeSelect(q, m) =>
          // For stable hashing, normalize the path and combine with name
          val qStr = q.asString.replace("<root>.this.", "").replace(".this", "")
          // Hash the same way as Ref for consistency
          var h = "Ref".hashCode // Use same type marker as Ref
          h = 31 * h + s"$qStr.$m".hashCode() // Use full name like Ref
          h = 31 * h + 0 // arity is 0 for simple types
          h

        case TypeRepr.Applied(tycon, args) =>
          var h = stableHash(tycon, seen)
          args.foreach(a => h = 31 * h + stableHash(a, seen))
          h

        case TypeRepr.Intersection(comps) =>
          var h = "Intersection".hashCode
          comps.foreach(c => h = 31 * h + stableHash(c, seen))
          h
        case TypeRepr.Union(comps) =>
          var h = "Union".hashCode
          comps.foreach(c => h = 31 * h + stableHash(c, seen))
          h
        case TypeRepr.ConstantType(c) =>
          c.hashCode()
        case TypeRepr.Structural(parents, members) =>
          var h = "Structural".hashCode
          parents.foreach(p => h = 31 * h + stableHash(p, seen))
          members.sortBy(_.name).foreach(m => h = 31 * h + stableMemberHash(m, seen))
          h
        case TypeRepr.Singleton(path) =>
          path.asString.hashCode
        case TypeRepr.Function(params, res) =>
          var h = "Function".hashCode
          params.foreach(p => h = 31 * h + stableHash(p, seen))
          h = 31 * h + stableHash(res, seen)
          h
        case TypeRepr.Tuple(elems) =>
          var h = "Tuple".hashCode
          elems.foreach { e =>
            h = 31 * h + e.label.fold(0)(_.hashCode)
            h = 31 * h + stableHash(e.tpe, seen)
          }
          h
        case TypeRepr.ContextFunction(params, res) =>
          var h = "ContextFunction".hashCode
          params.foreach(p => h = 31 * h + stableHash(p, seen))
          h = 31 * h + stableHash(res, seen)
          h
        case TypeRepr.PolyFunction(tps, res) =>
          var h = "PolyFunction".hashCode
          tps.foreach(tp => h = 31 * h + stableTypeParamHash(tp, seen))
          h = 31 * h + stableHash(res, seen)
          h
        case TypeRepr.DependentFunction(params, res) =>
          var h = "DependentFunction".hashCode
          params.foreach(p => h = 31 * h + stableParamHash(p, seen))
          h = 31 * h + stableHash(res, seen)
          h
        case TypeRepr.TypeLambda(tps, body) =>
          var h = "TypeLambda".hashCode
          tps.foreach(tp => h = 31 * h + stableTypeParamHash(tp, seen))
          h = 31 * h + stableHash(body, seen)
          h
        case TypeRepr.TypeProjection(prefix, member) =>
          31 * stableHash(prefix, seen) + member.hashCode
        case TypeRepr.ThisType(owner) =>
          31 * owner.fullName.hashCode + owner.arity
        case TypeRepr.SuperType(thisType, mixin) =>
          31 * stableHash(thisType, seen) + mixin.map(stableHash(_, seen)).getOrElse(0)
        case TypeRepr.MatchType(bound, scrutinee, cases) =>
          var h = "MatchType".hashCode
          h = 31 * h + stableHash(bound, seen)
          h = 31 * h + stableHash(scrutinee, seen)
          cases.foreach(c => h = 31 * h + stableMatchCaseHash(c, seen))
          h
        case TypeRepr.Wildcard(bounds) =>
          31 * "Wildcard".hashCode + stableBoundsHash(bounds, seen)
        case TypeRepr.RecType(body) =>
          31 * "RecType".hashCode + stableHash(body, seen)
        case TypeRepr.RecThis                  => "RecThis".hashCode
        case TypeRepr.Annotated(underlying, _) =>
          31 * "Annotated".hashCode + stableHash(underlying, seen)
        case TypeRepr.AnyType     => "Any".hashCode
        case TypeRepr.AnyKindType => "AnyKind".hashCode
        case TypeRepr.NothingType => "Nothing".hashCode
        case TypeRepr.UnitType    => "Unit".hashCode
        case TypeRepr.NullType    => "Null".hashCode
        case _                    => tpe.getClass.getSimpleName.hashCode
      }
    }
  }

  private def stableMemberHash(m: Member, seen: collection.mutable.Set[AnyRef]): Int = m match {
    case Member.Val(n, t, isMut, _, _) =>
      var h = 31 * "val".hashCode + n.hashCode
      h = 31 * h + (if (isMut) 1 else 0)
      h = 31 * h + stableHash(t, seen)
      h
    case Member.Def(n, tps, clauses, res, _, _) =>
      var h = 31 * "def".hashCode + n.hashCode
      tps.foreach(tp => h = 31 * h + stableTypeParamHash(tp, seen))
      clauses.foreach(c => h = 31 * h + stableParamClauseHash(c, seen))
      h = 31 * h + stableHash(res, seen)
      h
    case Member.TypeMember(n, tps, bounds, alias, _) =>
      var h = 31 * "type".hashCode + n.hashCode
      tps.foreach(tp => h = 31 * h + stableTypeParamHash(tp, seen))
      h = 31 * h + stableBoundsHash(bounds, seen)
      h = 31 * h + (if (alias.isDefined) 1 else 0)
      alias.foreach(a => h = 31 * h + stableHash(a, seen))
      h
  }

  private def stableTypeParamHash(param: TypeParam, seen: collection.mutable.Set[AnyRef]): Int = {
    var h = 31 * param.index + param.variance.hashCode()
    h = 31 * h + param.kind.hashCode()
    h = 31 * h + stableBoundsHash(param.bounds, seen)
    h
  }

  private def stableBoundsHash(bounds: TypeBounds, seen: collection.mutable.Set[AnyRef]): Int = {
    var h = "Bounds".hashCode
    bounds.lower.foreach(l => h = 31 * h + stableHash(l, seen))
    bounds.upper.foreach(u => h = 31 * h + stableHash(u, seen))
    h
  }

  private def stableParamClauseHash(clause: ParamClause, seen: collection.mutable.Set[AnyRef]): Int = {
    var h = clause.getClass.getSimpleName.hashCode
    clause.params.foreach(p => h = 31 * h + stableParamHash(p, seen))
    h
  }

  private def stableParamHash(param: Param, seen: collection.mutable.Set[AnyRef]): Int = {
    var h = 31 * (if (param.hasDefault) 1 else 0) + (if (param.isRepeated) 1 else 0)
    h = 31 * h + stableHash(param.tpe, seen)
    h
  }

  private def stableMatchCaseHash(mtc: TypeRepr.MatchTypeCase, seen: collection.mutable.Set[AnyRef]): Int = {
    var h = "MatchCase".hashCode
    mtc.bindings.foreach(tp => h = 31 * h + stableTypeParamHash(tp, seen))
    h = 31 * h + stableHash(mtc.pattern, seen)
    h = 31 * h + stableHash(mtc.result, seen)
    h
  }
}

object TypeNormalization {

  def normalize(repr: TypeRepr): TypeRepr = {
    val seen = Collections.newSetFromMap(new IdentityHashMap[AnyRef, java.lang.Boolean]()).asScala
    loop(repr, seen)
  }

  private def loop(r: TypeRepr, seen: collection.mutable.Set[AnyRef]): TypeRepr = {
    val ref = r.asInstanceOf[AnyRef]
    if (seen.contains(ref)) r
    else {
      seen.add(ref)
      r match {
        case TypeRepr.Ref(id) if id.isAlias =>
          id.aliasedTo match {
            case Some(aliased) => loop(aliased, seen)
            case None          => r
          }
        case TypeRepr.Ref(id) if id.isOpaque =>
          // Opaque types are nomads! Do NOT expand them or they lose identity.
          r

        case TypeRepr.TypeSelect(path, name) =>
          // For top-level types like scala.Int, convert to Ref
          val pathStr = path.asString.replace(".this", "")
          (pathStr, name) match {
            case ("scala", "Int")                       => TypeRepr.Ref(TypeId.Int)
            case ("scala", "Long")                      => TypeRepr.Ref(TypeId.Long)
            case ("scala", "Double")                    => TypeRepr.Ref(TypeId.Double)
            case ("scala", "Float")                     => TypeRepr.Ref(TypeId.Float)
            case ("scala", "Short")                     => TypeRepr.Ref(TypeId.Short)
            case ("scala", "Byte")                      => TypeRepr.Ref(TypeId.Byte)
            case ("scala", "Char")                      => TypeRepr.Ref(TypeId.Char)
            case ("scala", "Boolean")                   => TypeRepr.Ref(TypeId.Boolean)
            case ("scala", "Unit")                      => TypeRepr.Ref(TypeId.Unit)
            case ("scala", "Any")                       => TypeRepr.Ref(TypeId.Any)
            case ("scala", "AnyRef")                    => TypeRepr.Ref(TypeId.AnyRef)
            case ("scala", "AnyVal")                    => TypeRepr.Ref(TypeId.AnyVal)
            case ("scala", "Nothing")                   => TypeRepr.Ref(TypeId.Nothing)
            case ("scala", "Null")                      => TypeRepr.Ref(TypeId.Null)
            case ("java.lang" | "lang", "String")       => TypeRepr.Ref(TypeId.String)
            case ("scala.Predef" | "scala", "String")   => TypeRepr.Ref(TypeId.String)
            case ("immutable", "List")                  => TypeRepr.Ref(TypeId.ListTypeId)
            case ("scala.collection.immutable", "List") => TypeRepr.Ref(TypeId.ListTypeId)
            case ("immutable", "Map")                   => TypeRepr.Ref(TypeId.MapTypeId)
            case ("scala.collection.immutable", "Map")  => TypeRepr.Ref(TypeId.MapTypeId)
            case ("scala", "Either")                    => TypeRepr.Ref(TypeId.EitherTypeId)
            case ("util", "Either")                     => TypeRepr.Ref(TypeId.EitherTypeId)
            case ("scala", "Option")                    => TypeRepr.Ref(TypeId.OptionTypeId)
            case ("scala", "Function1")                 => TypeRepr.Ref(TypeId.Function1TypeId)
            case _                                      =>
              // For custom types, we can't convert to Ref without a registry
              // Keep as TypeSelect for now
              r
          }

        case TypeRepr.Applied(tycon, args) =>
          val nTycon = loop(tycon, seen)
          val nArgs  = args.map(loop(_, seen))
          (nTycon, nArgs) match {
            case (TypeRepr.TypeLambda(params, body), _) if params.size == nArgs.size =>
              val subs = params.zip(nArgs).toMap
              loop(body.substitute(subs), seen)
            case _ =>
              // If tycon was normalized to Ref, we need to get the aliasedTo
              nTycon match {
                case TypeRepr.Ref(id) if id.isAlias =>
                  id.aliasedTo match {
                    case Some(aliased) =>
                      // Apply the normalized args to the aliased type
                      aliased match {
                        case TypeRepr.TypeLambda(params, body) if params.size == nArgs.size =>
                          val subs = params.zip(nArgs).toMap
                          loop(body.substitute(subs), seen)
                        case TypeRepr.Applied(aliasedTycon, _) =>
                          // If aliased is Applied, normalize the tycon and apply normalized args
                          val nAliasedTycon = loop(aliasedTycon, seen)
                          loop(TypeRepr.Applied(nAliasedTycon, nArgs), seen)
                        case _ =>
                          TypeRepr.Applied(nTycon, nArgs)
                      }
                    case None => TypeRepr.Applied(nTycon, nArgs)
                  }
                case _ => TypeRepr.Applied(nTycon, nArgs)
              }
          }

        case TypeRepr.Intersection(comps) =>
          val nComps = comps.map(loop(_, seen)).distinct.sortBy(stableKey)
          if (nComps.size == 1) nComps.head else TypeRepr.Intersection(nComps)

        case TypeRepr.Union(comps) =>
          val nComps = comps.map(loop(_, seen)).distinct.sortBy(stableKey)
          if (nComps.size == 1) nComps.head else TypeRepr.Union(nComps)

        case TypeRepr.Annotated(underlying, _) =>
          loop(underlying, seen)

        case _ => r
      }
    }
  }

  private def stableKey(tpe: TypeRepr): String = {
    def loop(r: TypeRepr, depth: Int): String =
      if (depth > 10) "..."
      else
        r match {
          case TypeRepr.Ref(id)              => s"Ref(${id.fullName})"
          case TypeRepr.Applied(tycon, args) =>
            s"App(${loop(tycon, depth + 1)},${args.map(loop(_, depth + 1)).mkString(",")})"
          case TypeRepr.Intersection(comps) => s"Int(${comps.map(loop(_, depth + 1)).sorted.mkString("&")})"
          case TypeRepr.Union(comps)        => s"Uni(${comps.map(loop(_, depth + 1)).sorted.mkString("|")})"
          case TypeRepr.ParamRef(p, d)      => s"Param(${p.name},$d)"
          case TypeRepr.TypeSelect(q, m)    => s"Select(${q.asString},$m)"
          case TypeRepr.ConstantType(v)     => s"Const($v)"
          case TypeRepr.AnyType             => "Any"
          case TypeRepr.NothingType         => "Nothing"
          case TypeRepr.UnitType            => "Unit"
          case TypeRepr.Function(ps, r)     => s"Func(${ps.map(loop(_, depth + 1)).mkString(",")},${loop(r, depth + 1)})"
          case TypeRepr.Tuple(es)           => s"Tuple(${es.map(e => loop(e.tpe, depth + 1)).mkString(",")})"
          case _                            => r.getClass.getSimpleName
        }
    loop(tpe, 0)
  }
}
