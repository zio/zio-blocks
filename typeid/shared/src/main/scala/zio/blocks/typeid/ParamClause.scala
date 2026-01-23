package zio.blocks.typeid

sealed trait ParamClause {
  def params: List[Param]
  def isEmpty: Boolean = params.isEmpty
  def size: Int        = params.size
}

object ParamClause {
  final case class Regular(params: List[Param])  extends ParamClause
  final case class Using(params: List[Param])    extends ParamClause
  final case class Implicit(params: List[Param]) extends ParamClause

  val empty: ParamClause = Regular(Nil)
}

final case class Param(
  name: String,
  tpe: TypeRepr,
  hasDefault: Boolean = false,
  isRepeated: Boolean = false
)
