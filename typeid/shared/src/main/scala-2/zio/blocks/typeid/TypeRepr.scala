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
  implicit val ordering: Ordering[TypeRepr] = Ordering.by((t: TypeRepr) => t.show)

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
