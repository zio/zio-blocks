package zio.blocks.typeid

import scala.language.experimental.macros

/**
 * A typed wrapper around DynamicTypeId that carries a phantom type parameter.
 *
 * In Scala 2, the type parameter is simply `A` (without AnyKind bound, which is
 * Scala 3 specific).
 *
 * @tparam A
 *   The type this TypeId represents (phantom type parameter)
 */
final case class TypeId[A](dynamic: DynamicTypeId) {
  def owner: Owner                  = dynamic.owner
  def name: String                  = dynamic.name
  def typeParams: List[TypeParam]   = dynamic.typeParams
  def kind: TypeDefKind             = dynamic.kind
  def parents: List[TypeRepr]       = dynamic.parents
  def args: List[TypeRepr]          = dynamic.args
  def annotations: List[Annotation] = dynamic.annotations

  def fullName: String                 = dynamic.fullName
  def arity: Int                       = dynamic.arity
  def aliasedTo: Option[TypeRepr]      = dynamic.aliasedTo
  def representation: Option[TypeRepr] = dynamic.representation

  def isClass: Boolean      = dynamic.isClass
  def isTrait: Boolean      = dynamic.isTrait
  def isObject: Boolean     = dynamic.isObject
  def isEnum: Boolean       = dynamic.isEnum
  def isAlias: Boolean      = dynamic.isAlias
  def isOpaque: Boolean     = dynamic.isOpaque
  def isAbstract: Boolean   = dynamic.isAbstract
  def isSealed: Boolean     = dynamic.isSealed
  def isCaseClass: Boolean  = dynamic.isCaseClass
  def isValueClass: Boolean = dynamic.isValueClass

  def enumCases: List[EnumCaseInfo] = dynamic.enumCases

  def show: String = dynamic.show

  def isSubtypeOf[B](other: TypeId[B]): Boolean =
    dynamic.isSubtypeOf(other.dynamic)

  def isSupertypeOf[B](other: TypeId[B]): Boolean =
    dynamic.isSupertypeOf(other.dynamic)

  def isEquivalentTo[B](other: TypeId[B]): Boolean =
    dynamic.isEquivalentTo(other.dynamic)

  override def equals(obj: Any): Boolean = obj match {
    case other: TypeId[_] => dynamic.equals(other.dynamic)
    case _                => false
  }

  override def hashCode(): Int = dynamic.hashCode()

  override def toString: String = dynamic.toString
}

object TypeId {
  def of[A]: TypeId[A] = macro TypeIdMacros.fromImpl[A]
  def from[A]: TypeId[A] = macro TypeIdMacros.fromImpl[A]

  implicit def derived[A]: TypeId[A] = macro TypeIdMacros.fromImpl[A]

  /** Create a TypeId from a DynamicTypeId */
  def apply[A](dynamic: DynamicTypeId): TypeId[A] = new TypeId[A](dynamic)
}
