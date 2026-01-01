package zio.blocks.typeid

// ============================================================================
// Owner: Where a type is defined
// ============================================================================

final case class Owner(segments: List[Owner.Segment]) {
  def asString: String = segments.map(_.name).mkString(".")
}

object Owner {
  sealed trait Segment { def name: String }

  final case class Package(name: String) extends Segment
  final case class Term(name: String)    extends Segment
  final case class Type(name: String)    extends Segment

  val Root: Owner = Owner(Nil)
}

// ============================================================================
// TypeParam: Type parameter specification
// ============================================================================

final case class TypeParam(
  name: String,
  index: Int
  // Can extend with: variance, bounds, kind
)

// ============================================================================
// TypeId: Identity of a type or type constructor (phantom-typed by A)
// ============================================================================

sealed trait TypeId[A] {
  def name: String
  def owner: Owner
  def typeParams: List[TypeParam]

  def withTypeParams(params: List[TypeParam]): TypeId[A]

  final def arity: Int = typeParams.size

  final def fullName: String =
    if (owner.segments.isEmpty) name
    else owner.asString + "." + name
}

object TypeId extends TypeIdVersionSpecific {
  private final case class NominalImpl(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam]
  ) extends TypeId[Nothing] {
    def withTypeParams(params: List[TypeParam]): TypeId[Nothing] = copy(typeParams = params)
  }

  private final case class AliasImpl(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    aliased: TypeRepr
  ) extends TypeId[Nothing] {
    def withTypeParams(params: List[TypeParam]): TypeId[Nothing] = copy(typeParams = params)
  }

  private final case class OpaqueImpl(
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    representation: TypeRepr
  ) extends TypeId[Nothing] {
    def withTypeParams(params: List[TypeParam]): TypeId[Nothing] = copy(typeParams = params)
  }

  /** Manual construction: nominal type */
  def nominal[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam]
  ): TypeId[A] =
    NominalImpl(name, owner, typeParams).asInstanceOf[TypeId[A]]

  /** Manual construction: type alias */
  def alias[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    aliased: TypeRepr
  ): TypeId[A] =
    AliasImpl(name, owner, typeParams, aliased).asInstanceOf[TypeId[A]]

  /** Manual construction: opaque type */
  def opaque[A](
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

// ============================================================================
// TypeRepr: Type expressions
// ============================================================================

sealed trait TypeRepr

object TypeRepr {

  /**
   * Reference to a named type constructor (unapplied).
   *   - If id.arity == 0, this is already a proper type
   *   - If id.arity > 0, this is a type constructor
   */
  final case class Ref(id: TypeId[_]) extends TypeRepr

  /** Reference to a type parameter (can itself be a constructor) */
  final case class ParamRef(param: TypeParam) extends TypeRepr

  /**
   * Application of a type constructor to arguments. Examples: List[Int] →
   * Applied(Ref(listId), List(Ref(intId))) F[A] → Applied(ParamRef(F),
   * List(ParamRef(A)))
   */
  final case class Applied(
    tycon: TypeRepr,
    args: List[TypeRepr]
  ) extends TypeRepr

  /** Structural/refinement type: { def foo: Int; type T; ... } */
  final case class Structural(
    parents: List[TypeRepr],
    members: List[Member]
  ) extends TypeRepr

  /** Intersection type: A & B */
  final case class Intersection(left: TypeRepr, right: TypeRepr) extends TypeRepr

  /** Union type: A | B */
  final case class Union(left: TypeRepr, right: TypeRepr) extends TypeRepr

  /** Tuple type: (A, B, C) */
  final case class Tuple(elems: List[TypeRepr]) extends TypeRepr

  /** Function type: (A, B) => C */
  final case class Function(params: List[TypeRepr], result: TypeRepr) extends TypeRepr

  /** Singleton type: x.type */
  final case class Singleton(path: TermPath) extends TypeRepr

  /** Constant/literal type: 42, "foo", true */
  final case class Constant(value: Any) extends TypeRepr

  /** Top type */
  case object AnyType extends TypeRepr

  /** Bottom type */
  case object NothingType extends TypeRepr
}

// ============================================================================
// Member: Structural type members
// ============================================================================

sealed trait Member

object Member {
  final case class Val(
    name: String,
    tpe: TypeRepr,
    isVar: Boolean = false
  ) extends Member

  final case class Def(
    name: String,
    paramLists: List[List[Param]],
    result: TypeRepr
  ) extends Member

  final case class TypeMember(
    name: String,
    typeParams: List[TypeParam],
    lowerBound: Option[TypeRepr],
    upperBound: Option[TypeRepr]
  ) extends Member
}

final case class Param(name: String, tpe: TypeRepr)

// ============================================================================
// TermPath: For singleton types
// ============================================================================

final case class TermPath(segments: List[TermPath.Segment])

object TermPath {
  sealed trait Segment { def name: String }

  final case class Package(name: String) extends Segment
  final case class Term(name: String)    extends Segment
}
