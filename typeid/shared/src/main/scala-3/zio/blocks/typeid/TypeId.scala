package zio.blocks.typeid

/**
 * A typed wrapper around DynamicTypeId that carries a phantom type parameter.
 *
 * In Scala 3, the type parameter uses `A <: AnyKind` to support type
 * constructors like `List`, `Map`, etc. This allows `TypeId.of[List]` to work
 * correctly.
 *
 * @tparam A
 *   The type this TypeId represents (phantom type parameter)
 */
final case class TypeId[A <: AnyKind](dynamic: DynamicTypeId) {
  export dynamic.{
    owner,
    name,
    typeParams,
    kind,
    parents,
    args,
    annotations,
    fullName,
    arity,
    aliasedTo,
    representation,
    isClass,
    isTrait,
    isObject,
    isEnum,
    isAlias,
    isOpaque,
    isAbstract,
    isSealed,
    isCaseClass,
    isValueClass,
    enumCases,
    show
  }

  def isSubtypeOf[B <: AnyKind](other: TypeId[B]): Boolean =
    dynamic.isSubtypeOf(other.dynamic)

  def isSupertypeOf[B <: AnyKind](other: TypeId[B]): Boolean =
    dynamic.isSupertypeOf(other.dynamic)

  def isEquivalentTo[B <: AnyKind](other: TypeId[B]): Boolean =
    dynamic.isEquivalentTo(other.dynamic)

  override def equals(obj: Any): Boolean = obj match {
    case other: TypeId[_] => dynamic.equals(other.dynamic)
    case _                => false
  }

  override def hashCode(): Int = dynamic.hashCode()

  override def toString: String = dynamic.toString
}

object TypeId {
  inline def of[A <: AnyKind]: TypeId[A]   = TypeIdMacros.derived[A]
  inline def from[A <: AnyKind]: TypeId[A] = TypeIdMacros.derived[A]

  inline given derived[A <: AnyKind]: TypeId[A] = TypeIdMacros.derived[A]

  /** Create a TypeId from a DynamicTypeId */
  def apply[A <: AnyKind](dynamic: DynamicTypeId): TypeId[A] = new TypeId[A](dynamic)
}
