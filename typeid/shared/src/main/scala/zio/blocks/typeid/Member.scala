package zio.blocks.typeid

sealed trait Member

object Member {
  final case class Val(
    name: String,
    tpe: TypeRepr,
    isVar: Boolean = false
  ) extends Member

  final case class Def(
    name: String,
    paramLists: List[List[Param]],
    result: TypeRepr
  ) extends Member

  final case class TypeMember(
    name: String,
    typeParams: List[TypeParam],
    lowerBound: Option[TypeRepr],
    upperBound: Option[TypeRepr]
  ) extends Member
}
