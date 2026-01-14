package zio.blocks.typeid

/**
 * A unique identifier for a Scala type or type constructor.
 *
 * TypeId captures the complete identity of a type including:
 * - Its owner (package/class hierarchy)
 * - Its simple name
 * - Its type parameters
 * - Whether it's a nominal type, type alias, or opaque type
 *
 * TypeId is phantom-typed by the type it identifies, enabling type-safe APIs.
 *
 * @tparam A The type this TypeId identifies (can be a type constructor via AnyKind)
 */
sealed trait TypeId[A <: AnyKind] {
  /** The owner (package/class) containing this type */
  def name: String

  /** The simple name of this type (without package) */
  def owner: Owner

  /** Type parameters this type was declared with */
  def typeParams: List[TypeParam]

  /** The number of type parameters (arity) */
  final def arity: Int = typeParams.size

  /** Full qualified name including owner path */
  final def fullName: String =
    if (owner.segments.isEmpty) name
    else owner.asString + "." + name

  /**
   * Custom equality based on type identity, not structural content.
   * Two TypeIds are equal if they represent the same type identity,
   * regardless of how they were constructed or what additional
   * information they may contain.
   */
  override final def equals(obj: Any): Boolean = obj match {
    case that: TypeId[?] =>
      this.name == that.name &&
      this.owner == that.owner &&
      this.typeParams == that.typeParams &&
      this.getClass == that.getClass
    case _ => false
  }

  /**
   * Custom hash code based on type identity components.
   * Ensures consistent hashing for equivalent type identities.
   */
  override final def hashCode: Int = {
    val state = Seq(name, owner, typeParams, getClass.getName)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }

  override final def toString: String = s"TypeId($fullName, ${typeParams.mkString("[", ", ", "]")})"
}

object TypeId extends TypeIdCompanionVersionSpecific {

  // ===== ADT Implementations =====

  private final case class NominalImpl(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam]
  ) extends TypeId[Nothing]

  private final case class AliasImpl(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    aliased: TypeRepr
  ) extends TypeId[Nothing]

  private final case class OpaqueImpl(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    representation: TypeRepr
  ) extends TypeId[Nothing]

  // ===== Macro API =====
  // Note: derive[A] is provided by TypeIdCompanionVersionSpecific trait
  // See scala-2/TypeIdCompanionVersionSpecific.scala for Scala 2.13
  // See scala-3/TypeIdCompanionVersionSpecific.scala for Scala 3.5+

  // ===== Manual Construction API =====

  /**
   * Manually construct a nominal TypeId.
   * A nominal type is a regular class, trait, or object.
   */
  def nominal[A <: AnyKind](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam]
  ): TypeId[A] =
    NominalImpl(name, owner, typeParams).asInstanceOf[TypeId[A]]

  /**
   * Manually construct an alias TypeId.
   * A type alias is created with `type X = Y`.
   */
  def alias[A <: AnyKind](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    aliased: TypeRepr
  ): TypeId[A] =
    AliasImpl(name, owner, typeParams, aliased).asInstanceOf[TypeId[A]]

  /**
   * Manually construct an opaque TypeId.
   * An opaque type is created with `opaque type X = Y` (Scala 3).
   */
  def opaque[A <: AnyKind](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    representation: TypeRepr
  ): TypeId[A] =
    OpaqueImpl(name, owner, typeParams, representation).asInstanceOf[TypeId[A]]

  // ===== Pattern Matching Support =====

  /**
   * Extractor for nominal types.
   */
  object Nominal {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam])] = id match {
      case impl: NominalImpl => Some((impl.name, impl.owner, impl.typeParams))
      case _                 => None
    }
  }

  /**
   * Extractor for type aliases.
   */
  object Alias {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam], TypeRepr)] = id match {
      case impl: AliasImpl => Some((impl.name, impl.owner, impl.typeParams, impl.aliased))
      case _               => None
    }
  }

  /**
   * Extractor for opaque types.
   */
  object Opaque {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam], TypeRepr)] = id match {
      case impl: OpaqueImpl => Some((impl.name, impl.owner, impl.typeParams, impl.representation))
      case _                => None
    }
  }
}
