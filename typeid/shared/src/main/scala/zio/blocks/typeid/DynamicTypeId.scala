package zio.blocks.typeid

/**
 * A dynamic (untyped) representation of a type's identity.
 *
 * This case class contains all the data and methods for type identity
 * operations. The typed wrapper `TypeId[A]` delegates to this class, allowing
 * version-specific type parameter bounds (e.g., `A <: AnyKind` in Scala 3).
 */
final case class DynamicTypeId(
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

  def representation: Option[TypeRepr] = kind match {
    case _: TypeDefKind.OpaqueType => None // Opaque type representation is internal
    case _                         => None
  }

  def isClass: Boolean = kind match {
    case _: TypeDefKind.Class => true
    case _                    => false
  }

  def isTrait: Boolean = kind match {
    case _: TypeDefKind.Trait => true
    case _                    => false
  }

  def isObject: Boolean = kind == TypeDefKind.Object

  def isEnum: Boolean = kind match {
    case _: TypeDefKind.Enum => true
    case _                   => false
  }

  def isAlias: Boolean = kind match {
    case _: TypeDefKind.TypeAlias => true
    case _                        => false
  }

  def isOpaque: Boolean = kind match {
    case _: TypeDefKind.OpaqueType => true
    case _                         => false
  }

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
    case other: DynamicTypeId => TypeEquality.dynamicTypeIdEquals(this, other)
    case _                    => false
  }

  override def hashCode(): Int = TypeEquality.dynamicTypeIdHashCode(this)

  def isSubtypeOf(other: DynamicTypeId): Boolean =
    Subtyping.isSubtype(TypeRepr.Ref(this, Nil), TypeRepr.Ref(other, Nil))

  def isSupertypeOf(other: DynamicTypeId): Boolean =
    Subtyping.isSubtype(TypeRepr.Ref(other, Nil), TypeRepr.Ref(this, Nil))

  def isEquivalentTo(other: DynamicTypeId): Boolean =
    Subtyping.isEquivalent(TypeRepr.Ref(this, Nil), TypeRepr.Ref(other, Nil))

  def show: String = {
    val ownerStr = if (owner.isRoot) "" else s"${owner.asString}."
    s"$ownerStr$name"
  }

  override def toString: String = show
}
