package zio.blocks.typeid

/**
 * Represents a type reference - the identity of a named type.
 *
 * TypeRef captures the essential information about a type definition:
 *   - Nominal types (classes, traits, objects, enums)
 *   - Type aliases (type Foo = Bar)
 *   - Opaque types (opaque type Foo = Bar)
 *
 * This is the core data structure that TypeId wraps. TypeRef focuses on type
 * identity while TypeId adds a phantom type parameter for compile-time safety.
 */
sealed trait TypeRef {

  /** The simple name of the type. */
  def name: String

  /** The owner of this type (package, object, class, etc.). */
  def owner: Owner

  /** The type parameters of this type (empty for proper types). */
  def typeParams: List[TypeParam]

  /** The kind of type definition (class, trait, object, enum, etc.). */
  def defKind: TypeDefKind

  /** The fully qualified name of this type. */
  final def fullName: String =
    if (owner.isRoot) name
    else s"${owner.asString}.$name"

  /** The arity of this type (number of type parameters). */
  final def arity: Int = typeParams.size
}

object TypeRef {

  /**
   * Nominal type: class, trait, object, enum.
   *
   * These are types with their own identity that are not aliases or opaque
   * wrappers.
   */
  final case class Nominal(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    defKind: TypeDefKind = TypeDefKind.Unknown
  ) extends TypeRef

  /**
   * Type alias: `type Foo = Bar` or `type StringMap[V] = Map[String, V]`.
   *
   * Type aliases are transparent - they are equal to their underlying type for
   * purposes of equality comparison.
   *
   * @param aliased
   *   The type expression this alias expands to
   */
  final case class Alias(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    aliased: TypeRepr,
    defKind: TypeDefKind = TypeDefKind.TypeAlias
  ) extends TypeRef

  /**
   * Opaque type: `opaque type Email = String` (Scala 3 only).
   *
   * Opaque types maintain nominal identity - they are NOT equal to their
   * underlying representation, even though they share the same runtime
   * representation.
   *
   * @param representation
   *   The underlying type (only visible within the defining scope)
   */
  final case class Opaque(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    representation: TypeRepr,
    defKind: TypeDefKind = TypeDefKind.OpaqueType()
  ) extends TypeRef
}
