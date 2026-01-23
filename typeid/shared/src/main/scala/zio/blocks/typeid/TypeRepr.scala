package zio.blocks.typeid

sealed trait TypeRepr {
  def dealias: TypeRepr = TypeNormalization.dealias(this)

  def show: String = this match {
    case TypeRepr.Ref(id, args) =>
      if (args.isEmpty) id.show
      else s"${id.show}[${args.map(_.show).mkString(", ")}]"
    case TypeRepr.AppliedType(tpe, args) =>
      s"${tpe.show}[${args.map(_.show).mkString(", ")}]"
    case TypeRepr.Union(types) =>
      types.map(_.show).mkString(" | ")
    case TypeRepr.Intersection(types) =>
      types.map(_.show).mkString(" & ")
    case TypeRepr.Structural(members) =>
      s"{ ${members.mkString("; ")} }"
    case TypeRepr.Function(params, result) =>
      s"(${params.map(_.show).mkString(", ")}) => ${result.show}"
    case TypeRepr.Tuple(elements) =>
      s"(${elements.map(_.show).mkString(", ")})"
    case TypeRepr.ConstantType(value) =>
      value.toString
    case TypeRepr.TypeParamRef(name, _)        => name
    case TypeRepr.ThisType(tpe)                => s"${tpe.show}.this"
    case TypeRepr.SuperType(thisTpe, superTpe) => s"${thisTpe.show}.super[${superTpe.show}]"
    case TypeRepr.TypeLambda(params, result)   => s"[..] =>> ${result.show}"
    case TypeRepr.Wildcard(bounds)             => "?"
    case TypeRepr.TypeProjection(qual, name)   => s"${qual.show}#$name"
    case TypeRepr.AnyType                      => "Any"
    case TypeRepr.AnyKindType                  => "AnyKind"
    case TypeRepr.NothingType                  => "Nothing"
    case TypeRepr.NullType                     => "Null"
    case TypeRepr.UnitType                     => "Unit"
  }
}

object TypeRepr {
  implicit val ordering: Ordering[TypeRepr] = new Ordering[TypeRepr] {
    private def typeTag(t: TypeRepr): Int = t match {
      case _: Ref            => 0
      case _: AppliedType    => 1
      case _: Union          => 2
      case _: Intersection   => 3
      case _: Structural     => 4
      case _: Function       => 5
      case _: Tuple          => 6
      case _: ConstantType   => 7
      case _: TypeParamRef   => 8
      case _: ThisType       => 9
      case _: SuperType      => 10
      case _: TypeLambda     => 11
      case _: Wildcard       => 12
      case _: TypeProjection => 13
      case AnyType           => 14
      case AnyKindType       => 15
      case NothingType       => 16
      case NullType          => 17
      case UnitType          => 18
    }

    private def compareList(xs: List[TypeRepr], ys: List[TypeRepr]): Int = {
      val sizeCompare = xs.size.compare(ys.size)
      if (sizeCompare != 0) sizeCompare
      else xs.zip(ys).map { case (x, y) => compare(x, y) }.find(_ != 0).getOrElse(0)
    }

    def compare(x: TypeRepr, y: TypeRepr): Int = {
      val tagCompare = typeTag(x).compare(typeTag(y))
      if (tagCompare != 0) tagCompare
      else
        (x, y) match {
          case (Ref(id1, args1), Ref(id2, args2)) =>
            val idCompare = id1.fullName.compare(id2.fullName)
            if (idCompare != 0) idCompare else compareList(args1, args2)
          case (AppliedType(t1, args1), AppliedType(t2, args2)) =>
            val tCompare = compare(t1, t2)
            if (tCompare != 0) tCompare else compareList(args1, args2)
          case (Union(types1), Union(types2))               => compareList(types1.sorted, types2.sorted)
          case (Intersection(types1), Intersection(types2)) => compareList(types1.sorted, types2.sorted)
          case (Structural(m1), Structural(m2))             => m1.mkString.compare(m2.mkString)
          case (Function(p1, r1), Function(p2, r2))         =>
            val pCompare = compareList(p1, p2)
            if (pCompare != 0) pCompare else compare(r1, r2)
          case (Tuple(e1), Tuple(e2))                       => compareList(e1, e2)
          case (ConstantType(v1), ConstantType(v2))         => v1.toString.compare(v2.toString)
          case (TypeParamRef(n1, i1), TypeParamRef(n2, i2)) =>
            val nCompare = n1.compare(n2)
            if (nCompare != 0) nCompare else i1.compare(i2)
          case (ThisType(t1), ThisType(t2))               => compare(t1, t2)
          case (SuperType(th1, su1), SuperType(th2, su2)) =>
            val thCompare = compare(th1, th2)
            if (thCompare != 0) thCompare else compare(su1, su2)
          case (TypeLambda(_, r1), TypeLambda(_, r2))           => compare(r1, r2)
          case (Wildcard(_), Wildcard(_))                       => 0
          case (TypeProjection(q1, n1), TypeProjection(q2, n2)) =>
            val qCompare = compare(q1, q2)
            if (qCompare != 0) qCompare else n1.compare(n2)
          case _ => 0
        }
    }
  }

  def canonicalize(tpe: TypeRepr): TypeRepr = tpe match {
    case Union(types) =>
      val sorted = types.map(canonicalize).sortBy(_.show).distinct
      if (sorted.size == 1) sorted.head else Union(sorted)
    case Intersection(types) =>
      val sorted = types.map(canonicalize).sortBy(_.show).distinct
      if (sorted.size == 1) sorted.head else Intersection(sorted)
    case AppliedType(t, args) => AppliedType(canonicalize(t), args.map(canonicalize))
    case Ref(id, args)        => Ref(id, args.map(canonicalize))
    case other                => other
  }

  final case class Ref(
    id: TypeId[_],
    args: List[TypeRepr]
  ) extends TypeRepr

  final case class AppliedType(
    tpe: TypeRepr,
    args: List[TypeRepr]
  ) extends TypeRepr

  final case class Union(
    types: List[TypeRepr]
  ) extends TypeRepr {
    override def equals(obj: Any): Boolean = obj match {
      case Union(thatTypes) => types.size == thatTypes.size && types.toSet == thatTypes.toSet
      case _                => false
    }
    override def hashCode(): Int = types.map(_.hashCode).sum + "Union".hashCode
  }

  final case class Intersection(
    types: List[TypeRepr]
  ) extends TypeRepr {
    override def equals(obj: Any): Boolean = obj match {
      case Intersection(thatTypes) => types.size == thatTypes.size && types.toSet == thatTypes.toSet
      case _                       => false
    }
    override def hashCode(): Int = types.map(_.hashCode).sum + "Intersection".hashCode
  }

  final case class Structural(
    members: List[Member]
  ) extends TypeRepr

  final case class Function(
    params: List[TypeRepr],
    result: TypeRepr
  ) extends TypeRepr

  final case class Tuple(
    elements: List[TypeRepr]
  ) extends TypeRepr

  final case class ConstantType(
    value: Constant
  ) extends TypeRepr

  final case class TypeParamRef(
    name: String,
    index: Int
  ) extends TypeRepr

  final case class ThisType(
    tpe: TypeRepr
  ) extends TypeRepr

  final case class SuperType(
    thisTpe: TypeRepr,
    superTpe: TypeRepr
  ) extends TypeRepr

  final case class TypeLambda(
    params: List[TypeParam],
    result: TypeRepr
  ) extends TypeRepr

  final case class Wildcard(
    bounds: TypeBounds
  ) extends TypeRepr

  final case class TypeProjection(
    qualifier: TypeRepr,
    name: String
  ) extends TypeRepr

  case object AnyType     extends TypeRepr
  case object AnyKindType extends TypeRepr
  case object NothingType extends TypeRepr
  case object NullType    extends TypeRepr
  case object UnitType    extends TypeRepr
}
