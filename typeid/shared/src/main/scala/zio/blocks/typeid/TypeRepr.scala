package zio.blocks.typeid

sealed trait TypeRepr

object TypeRepr {
  final case class Ref(id: TypeId[_]) extends TypeRepr

  final case class ParamRef(param: TypeParam) extends TypeRepr

  final case class Applied(
    tycon: TypeRepr,
    args: List[TypeRepr]
  ) extends TypeRepr

  final case class Structural(
    parents: List[TypeRepr],
    members: List[Member]
  ) extends TypeRepr

  final case class Intersection(left: TypeRepr, right: TypeRepr) extends TypeRepr

  final case class Union(left: TypeRepr, right: TypeRepr) extends TypeRepr

  final case class Tuple(elems: List[TypeRepr]) extends TypeRepr

  final case class Function(params: List[TypeRepr], result: TypeRepr) extends TypeRepr

  final case class Singleton(path: TermPath) extends TypeRepr

  final case class Constant(value: Any) extends TypeRepr

  case object AnyType extends TypeRepr

  case object NothingType extends TypeRepr
}
