package zio.blocks.typeid

import scala.annotation.tailrec

private[typeid] object TypeIdOps {

  private[typeid] def createImpl[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    typeArgs: List[TypeRepr],
    defKind: TypeDefKind,
    selfType: Option[TypeRepr],
    aliasedTo: Option[TypeRepr],
    representation: Option[TypeRepr],
    annotations: List[Annotation]
  ): TypeId[A] =
    TypeId.makeImpl[A](name, owner, typeParams, typeArgs, defKind, selfType, aliasedTo, representation, annotations)

  def normalize(id: TypeId[_]): TypeId[_] = normalizeWithArgs(id, id.typeArgs)

  def unapplied(id: TypeId[_]): TypeId[_] =
    if (!id.isApplied) id
    else
      createImpl(
        id.name,
        id.owner,
        id.typeParams,
        Nil,
        id.defKind,
        id.selfType,
        id.aliasedTo,
        id.representation,
        id.annotations
      )

  @tailrec
  private[typeid] def normalizeWithArgs(id: TypeId[_], accumulatedArgs: List[TypeRepr]): TypeId[_] =
    id.aliasedTo match {
      case Some(TypeRepr.Ref(aliased)) =>
        normalizeWithArgs(aliased, accumulatedArgs)
      case Some(TypeRepr.Applied(TypeRepr.Ref(aliased), appliedArgs)) =>
        val resolvedArgs = resolveTypeArgs(id.typeParams, accumulatedArgs, appliedArgs)
        normalizeWithArgs(aliased, resolvedArgs)
      case _ =>
        if (accumulatedArgs == id.typeArgs) id
        else
          createImpl(
            id.name,
            id.owner,
            id.typeParams,
            accumulatedArgs,
            id.defKind,
            id.selfType,
            id.aliasedTo,
            id.representation,
            id.annotations
          )
    }

  private[typeid] def resolveTypeArgs(
    aliasParams: List[TypeParam],
    actualArgs: List[TypeRepr],
    targetArgs: List[TypeRepr]
  ): List[TypeRepr] =
    if (aliasParams.isEmpty || actualArgs.isEmpty) targetArgs
    else {
      val paramToArg: Map[Int, TypeRepr] = aliasParams
        .zip(actualArgs)
        .map { case (param, arg) =>
          param.index -> arg
        }
        .toMap
      targetArgs.map(substituteTypeRepr(_, paramToArg))
    }

  private[typeid] def substituteTypeRepr(repr: TypeRepr, subst: Map[Int, TypeRepr]): TypeRepr = repr match {
    case TypeRepr.ParamRef(param, _) =>
      subst.getOrElse(param.index, repr)
    case TypeRepr.Applied(tycon, args) =>
      TypeRepr.Applied(substituteTypeRepr(tycon, subst), args.map(substituteTypeRepr(_, subst)))
    case TypeRepr.Ref(id) if id.isAlias =>
      val normId = normalize(id)
      if (normId eq id) repr else TypeRepr.Ref(normId)
    case _ => repr
  }

  def structurallyEqual(a: TypeId[_], b: TypeId[_]): Boolean = {
    val normA = normalize(a)
    val normB = normalize(b)

    if (normA.isOpaque && normB.isOpaque) {
      normA.fullName == normB.fullName && normA.typeParams == normB.typeParams &&
      typeArgsEqual(normA.typeArgs, normB.typeArgs)
    } else if (normA.isOpaque || normB.isOpaque) {
      false
    } else {
      normA.fullName == normB.fullName && normA.typeParams == normB.typeParams &&
      typeArgsEqual(normA.typeArgs, normB.typeArgs) &&
      aliasedToEqual(normA.aliasedTo, normB.aliasedTo)
    }
  }

  private[typeid] def aliasedToEqual(a: Option[TypeRepr], b: Option[TypeRepr]): Boolean = (a, b) match {
    case (Some(reprA), Some(reprB)) => typeReprEqual(reprA, reprB)
    case (None, None)               => true
    case _                          => false
  }

  private[typeid] def typeArgsEqual(argsA: List[TypeRepr], argsB: List[TypeRepr]): Boolean =
    if (argsA.size != argsB.size) false
    else argsA.zip(argsB).forall { case (a, b) => typeReprEqual(a, b) }

  private[typeid] def typeReprEqual(a: TypeRepr, b: TypeRepr): Boolean = {
    val normA = flattenTupleRepr(a)
    val normB = flattenTupleRepr(b)
    normalizedTypeReprEqual(normA, normB)
  }

  private[typeid] def normalizedTypeReprEqual(a: TypeRepr, b: TypeRepr): Boolean = (a, b) match {
    case (TypeRepr.Ref(idA), TypeRepr.Ref(idB)) =>
      structurallyEqual(idA, idB)
    case (TypeRepr.Applied(tyconA, argsA), TypeRepr.Applied(tyconB, argsB)) =>
      normalizedTypeReprEqual(tyconA, tyconB) && normalizedTypeArgsEqual(argsA, argsB)
    case (TypeRepr.ParamRef(paramA, depthA), TypeRepr.ParamRef(paramB, depthB)) =>
      paramA.name == paramB.name && paramA.index == paramB.index && depthA == depthB
    case (TypeRepr.Union(typesA), TypeRepr.Union(typesB)) =>
      normalizedTypeReprsEqualAsSet(typesA, typesB)
    case (TypeRepr.Intersection(typesA), TypeRepr.Intersection(typesB)) =>
      normalizedTypeReprsEqualAsSet(typesA, typesB)
    case (TypeRepr.Tuple(elemsA), TypeRepr.Tuple(elemsB)) =>
      elemsA.size == elemsB.size && elemsA.zip(elemsB).forall { case (ea, eb) =>
        ea.label == eb.label && normalizedTypeReprEqual(ea.tpe, eb.tpe)
      }
    case _ =>
      a == b
  }

  private[typeid] def normalizedTypeArgsEqual(argsA: List[TypeRepr], argsB: List[TypeRepr]): Boolean =
    if (argsA.size != argsB.size) false
    else argsA.zip(argsB).forall { case (a, b) => normalizedTypeReprEqual(a, b) }

  private[typeid] def normalizedTypeReprsEqualAsSet(as: List[TypeRepr], bs: List[TypeRepr]): Boolean =
    as.size == bs.size && as.forall(a => bs.exists(b => normalizedTypeReprEqual(a, b)))

  private[typeid] def typeReprsEqualAsSet(as: List[TypeRepr], bs: List[TypeRepr]): Boolean =
    as.size == bs.size && as.forall(a => bs.exists(b => typeReprEqual(a, b)))

  private[typeid] def flattenTupleRepr(repr: TypeRepr): TypeRepr = repr match {
    case TypeRepr.Tuple(elems) =>
      TypeRepr.Tuple(flattenTupleElements(elems))
    case TypeRepr.Applied(tycon, args) =>
      TypeRepr.Applied(flattenTupleRepr(tycon), args.map(flattenTupleRepr))
    case other => other
  }

  private def flattenTupleElements(elems: List[TupleElement]): List[TupleElement] =
    elems.flatMap { elem =>
      flattenTupleRepr(elem.tpe) match {
        case TypeRepr.Tuple(innerElems) if elem.label.isEmpty =>
          flattenTupleElements(innerElems)
        case TypeRepr.Ref(id) if isEmptyTuple(id) =>
          Nil
        case normalizedTpe =>
          List(TupleElement(elem.label, normalizedTpe))
      }
    }

  private def isEmptyTuple(id: TypeId[_]): Boolean =
    id.fullName == "scala.Tuple$package.EmptyTuple" || id.name == "EmptyTuple"

  def structuralHash(id: TypeId[_]): Int = {
    val norm = normalize(id)
    if (norm.isOpaque) {
      ("opaque", norm.fullName, norm.typeParams, typeArgsHash(norm.typeArgs)).hashCode()
    } else {
      (norm.fullName, norm.typeParams, typeArgsHash(norm.typeArgs), aliasedToHash(norm.aliasedTo)).hashCode()
    }
  }

  private[typeid] def aliasedToHash(aliasedTo: Option[TypeRepr]): Int =
    aliasedTo.map(typeReprHash).getOrElse(0)

  private[typeid] def typeArgsHash(args: List[TypeRepr]): Int =
    args.map(typeReprHash).hashCode()

  private[typeid] def typeReprHash(repr: TypeRepr): Int = {
    val normalized = flattenTupleRepr(repr)
    normalizedTypeReprHash(normalized)
  }

  private[typeid] def normalizedTypeReprHash(repr: TypeRepr): Int = repr match {
    case TypeRepr.Ref(id)              => structuralHash(id)
    case TypeRepr.Applied(tycon, args) => (normalizedTypeReprHash(tycon), args.map(normalizedTypeReprHash)).hashCode()
    case TypeRepr.Union(types)         => ("union", types.map(normalizedTypeReprHash).toSet).hashCode()
    case TypeRepr.Intersection(types)  => ("intersection", types.map(normalizedTypeReprHash).toSet).hashCode()
    case TypeRepr.Tuple(elems)         => ("tuple", elems.map(e => (e.label, normalizedTypeReprHash(e.tpe)))).hashCode()
    case other                         => other.hashCode()
  }

  def checkParents(parents: List[TypeRepr], target: TypeId[_], visited: Set[String]): Boolean =
    parents.exists {
      case TypeRepr.Ref(id) =>
        if (visited.contains(id.fullName)) false
        else if (structurallyEqual(id, target)) true
        else checkParents(id.defKind.baseTypes, target, visited + id.fullName)
      case TypeRepr.Applied(TypeRepr.Ref(id), _) =>
        if (visited.contains(id.fullName)) false
        else if (id.fullName == target.fullName) true
        else checkParents(id.defKind.baseTypes, target, visited + id.fullName)
      case _ => false
    }

  def checkAppliedSubtyping(sub: TypeId[_], sup: TypeId[_]): Boolean = {
    if (sub.fullName != sup.fullName) return false
    if (sub.typeArgs.size != sup.typeArgs.size) return false
    if (sub.typeParams.size != sub.typeArgs.size) return false

    sub.typeParams.zip(sub.typeArgs.zip(sup.typeArgs)).forall { case (param, (subArg, supArg)) =>
      param.variance match {
        case Variance.Covariant =>
          isTypeReprSubtypeOf(subArg, supArg)
        case Variance.Contravariant =>
          isTypeReprSubtypeOf(supArg, subArg)
        case Variance.Invariant =>
          subArg == supArg
      }
    }
  }

  def isTypeReprSubtypeOf(sub: TypeRepr, sup: TypeRepr): Boolean = (sub, sup) match {
    case (TypeRepr.Ref(subId), TypeRepr.Ref(supId)) =>
      subId.isSubtypeOf(supId)
    case (TypeRepr.Applied(TypeRepr.Ref(subTycon), subArgs), TypeRepr.Applied(TypeRepr.Ref(supTycon), supArgs)) =>
      if (subTycon.fullName != supTycon.fullName) false
      else if (subArgs.size != supArgs.size) false
      else if (subTycon.typeParams.size != subArgs.size) false
      else {
        subTycon.typeParams.zip(subArgs.zip(supArgs)).forall { case (param, (subArg, supArg)) =>
          param.variance match {
            case Variance.Covariant     => isTypeReprSubtypeOf(subArg, supArg)
            case Variance.Contravariant => isTypeReprSubtypeOf(supArg, subArg)
            case Variance.Invariant     => subArg == supArg
          }
        }
      }
    case _ => sub == sup
  }

  object Owners {
    val scala: Owner                    = Owner.fromPackagePath("scala")
    val scalaUtil: Owner                = Owner.fromPackagePath("scala.util")
    val scalaCollectionImmutable: Owner = Owner.fromPackagePath("scala.collection.immutable")
    val javaLang: Owner                 = Owner.fromPackagePath("java.lang")
    val javaIo: Owner                   = Owner.fromPackagePath("java.io")
    val javaTime: Owner                 = Owner.fromPackagePath("java.time")
    val javaUtil: Owner                 = Owner.fromPackagePath("java.util")
    val zioBlocksChunk: Owner           = Owner.fromPackagePath("zio.blocks.chunk")
  }
}
