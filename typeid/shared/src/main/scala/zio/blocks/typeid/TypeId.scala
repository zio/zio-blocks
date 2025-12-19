package zio.blocks.typeid

import scala.language.experimental.macros

sealed trait TypeId[A <: AnyKind] {
  def name: String
  def owner: Owner
  def typeParams: List[TypeParam]

  final def arity: Int = typeParams.size

  final def fullName: String =
    if (owner.segments.isEmpty) name
    else owner.asString + "." + name
}

object TypeId extends TypeIdPlatformSpecific {
  private[typeid] final case class NominalImpl(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam]
  ) extends TypeId[Nothing]

  private[typeid] final case class AliasImpl(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    aliased: TypeRepr
  ) extends TypeId[Nothing]

  private[typeid] final case class OpaqueImpl(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    representation: TypeRepr
  ) extends TypeId[Nothing]

  /** Manual construction: nominal type */
  def nominal[A <: AnyKind](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam]
  ): TypeId[A] =
    NominalImpl(name, owner, typeParams).asInstanceOf[TypeId[A]]

  /** Manual construction: type alias */
  def alias[A <: AnyKind](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    aliased: TypeRepr
  ): TypeId[A] =
    AliasImpl(name, owner, typeParams, aliased).asInstanceOf[TypeId[A]]

  /** Manual construction: opaque type */
  def opaque[A <: AnyKind](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    representation: TypeRepr
  ): TypeId[A] =
    OpaqueImpl(name, owner, typeParams, representation).asInstanceOf[TypeId[A]]

  /** Pattern matching support */
  object Nominal {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam])] = id match {
      case impl: NominalImpl => Some((impl.name, impl.owner, impl.typeParams))
      case _                 => None
    }
  }

  object Alias {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam], TypeRepr)] = id match {
      case impl: AliasImpl => Some((impl.name, impl.owner, impl.typeParams, impl.aliased))
      case _               => None
    }
  }

  object Opaque {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam], TypeRepr)] = id match {
      case impl: OpaqueImpl => Some((impl.name, impl.owner, impl.typeParams, impl.representation))
      case _                => None
    }
  }
}
