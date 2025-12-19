package zio.blocks.typeid

sealed trait TypeRepr

object TypeRepr {

  /**
   * Reference to a named type constructor (unapplied).
   *   - If id.arity == 0, this is already a proper type
   *   - If id.arity > 0, this is a type constructor
   */
  final case class Ref(id: TypeId[_ <: AnyKind]) extends TypeRepr

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
