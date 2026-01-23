package zio.blocks.typeid

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

  final case class TypeAlias(alias: TypeRepr) extends TypeDefKind

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
