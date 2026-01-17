package zio.blocks.typeid

/**
 * Classifies what kind of type definition a TypeId represents.
 */
sealed trait TypeDefKind

object TypeDefKind {
  final case class Class(
    isFinal: Boolean = false,
    isAbstract: Boolean = false,
    isCase: Boolean = false,
    isValue: Boolean = false
  ) extends TypeDefKind

  final case class Trait(
    isSealed: Boolean = false,
    knownSubtypes: List[TypeRepr] = Nil
  ) extends TypeDefKind

  case object Object extends TypeDefKind

  final case class Enum(
    cases: List[EnumCaseInfo]
  ) extends TypeDefKind

  final case class EnumCase(
    parentEnum: TypeRepr,
    ordinal: Int,
    isObjectCase: Boolean
  ) extends TypeDefKind

  case object TypeAlias extends TypeDefKind

  final case class OpaqueType(
    publicBounds: TypeBounds
  ) extends TypeDefKind

  case object AbstractType extends TypeDefKind
}

final case class EnumCaseInfo(
  name: String,
  ordinal: Int,
  params: List[EnumCaseParam],
  isObjectCase: Boolean
) {
  def arity: Int = params.size
}

final case class EnumCaseParam(
  name: String,
  tpe: TypeRepr
)

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
