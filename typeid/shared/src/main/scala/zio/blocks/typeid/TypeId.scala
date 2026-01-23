package zio.blocks.typeid

final case class TypeId[A](
  owner: Owner,
  name: String,
  typeParams: List[TypeParam],
  kind: TypeDefKind,
  parents: List[TypeRepr],
  args: List[TypeRepr] = Nil,
  annotations: List[Annotation] = Nil
) {
  def fullName: String =
    if (owner.isRoot) name
    else s"${owner.asString}.$name"

  def arity: Int = typeParams.size

  def aliasedTo: Option[TypeRepr] = kind match {
    case TypeDefKind.TypeAlias(alias) => Some(alias)
    case _                            => None
  }

  def isClass: Boolean    = kind match { case _: TypeDefKind.Class => true; case _ => false }
  def isTrait: Boolean    = kind match { case _: TypeDefKind.Trait => true; case _ => false }
  def isObject: Boolean   = kind == TypeDefKind.Object
  def isEnum: Boolean     = kind match { case _: TypeDefKind.Enum => true; case _ => false }
  def isAlias: Boolean    = kind match { case _: TypeDefKind.TypeAlias => true; case _ => false }
  def isOpaque: Boolean   = kind match { case _: TypeDefKind.OpaqueType => true; case _ => false }
  def isAbstract: Boolean = kind == TypeDefKind.AbstractType

  def isSealed: Boolean = kind match {
    case TypeDefKind.Trait(isSealed, _) => isSealed
    case _                              => false
  }

  def isCaseClass: Boolean = kind match {
    case TypeDefKind.Class(_, _, isCase, _) => isCase
    case _                                  => false
  }

  def isValueClass: Boolean = kind match {
    case TypeDefKind.Class(_, _, _, isValue) => isValue
    case _                                   => false
  }

  def enumCases: List[EnumCaseInfo] = kind match {
    case TypeDefKind.Enum(cases) => cases
    case _                       => Nil
  }

  override def equals(obj: Any): Boolean = obj match {
    case other: TypeId[_] => TypeEquality.typeIdEquals(this, other)
    case _                => false
  }

  override def hashCode(): Int = TypeEquality.typeIdHashCode(this)

  def isSubtypeOf(other: TypeId[_]): Boolean =
    Subtyping.isSubtype(TypeRepr.Ref(this, Nil), TypeRepr.Ref(other, Nil))

  def isSupertypeOf(other: TypeId[_]): Boolean =
    Subtyping.isSubtype(TypeRepr.Ref(other, Nil), TypeRepr.Ref(this, Nil))

  def isEquivalentTo(other: TypeId[_]): Boolean =
    Subtyping.isEquivalent(TypeRepr.Ref(this, Nil), TypeRepr.Ref(other, Nil))

  def show: String = {
    val ownerStr = if (owner.isRoot) "" else s"${owner.asString}."
    s"$ownerStr$name"
  }

  override def toString: String = show
}

object TypeId extends TypeIdPlatformSpecific
