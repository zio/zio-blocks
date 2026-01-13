package zio.blocks.typeid

/**
 * Represents the structure of a type as a pure data structure.
 * This is analogous to scala.quoted.TypeRepr but as an inspectable ADT.
 */
sealed trait TypeRepr

object TypeRepr {
  /**
   * A reference to a named type constructor (unapplied).
   * If id.arity == 0, this represents a proper type.
   * If id.arity > 0, this represents a type constructor.
   */
  final case class Ref(id: TypeId[? <: AnyKind]) extends TypeRepr

  /**
   * A reference to a type parameter.
   * The parameter can itself be a type constructor for higher-kinded types.
   */
  final case class ParamRef(param: TypeParam) extends TypeRepr

  /**
   * Application of a type constructor to type arguments.
   * Examples:
   *   - List[Int] → Applied(Ref(listId), List(Ref(intId)))
   *   - F[A] → Applied(ParamRef(F), List(ParamRef(A)))
   */
  final case class Applied(
    tycon: TypeRepr,
    args: List[TypeRepr]
  ) extends TypeRepr

  /**
   * A structural (refinement) type: { def foo: Int; type T; ... }
   */
  final case class Structural(
    parents: List[TypeRepr],
    members: List[Member]
  ) extends TypeRepr

  /**
   * An intersection type: A & B
   */
  final case class Intersection(left: TypeRepr, right: TypeRepr) extends TypeRepr

  /**
   * A union type: A | B (Scala 3)
   */
  final case class Union(left: TypeRepr, right: TypeRepr) extends TypeRepr

  /**
   * A tuple type: (A, B, C, ...)
   */
  final case class Tuple(elems: List[TypeRepr]) extends TypeRepr

  /**
   * A function type: (A, B) => C
   */
  final case class Function(params: List[TypeRepr], result: TypeRepr) extends TypeRepr

  /**
   * A singleton type: x.type
   */
  final case class Singleton(path: TermPath) extends TypeRepr

  /**
   * A constant (literal) type: 42, "hello", true
   */
  final case class Constant(value: Any) extends TypeRepr

  /**
   * The top type (Any).
   */
  case object AnyType extends TypeRepr

  /**
   * The bottom type (Nothing).
   */
  case object NothingType extends TypeRepr
}
