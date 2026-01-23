package zio.blocks.typeid

sealed trait Member

object Member {
  final case class Val(name: String, tpe: TypeRepr)                                   extends Member
  final case class Def(name: String, params: List[ParamClause], resultType: TypeRepr) extends Member
  final case class Type(name: String, bounds: TypeBounds)                             extends Member
}
