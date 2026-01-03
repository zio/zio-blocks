package zio.blocks.typeid

/**
 * Represents type expressions in the Scala type system.
 *
 * TypeRepr can represent:
 *   - Simple type references (`Int`, `String`)
 *   - Applied types (`List[Int]`, `Map[String, Int]`)
 *   - Type parameter references (`A`, `F[_]`)
 *   - Structural/refinement types (`{ def foo: Int }`)
 *   - Intersection types (`A & B`)
 *   - Union types (`A | B`)
 *   - Tuple types (`(A, B, C)`)
 *   - Function types (`A => B`)
 *   - Singleton types (`x.type`)
 *   - Literal types (`42`, `"foo"`)
 */
sealed trait TypeRepr {

  /**
   * Returns true if this type expression references the given type parameter.
   */
  def containsParam(param: TypeParam): Boolean = this match {
    case TypeRepr.Ref(_)                    => false
    case TypeRepr.ParamRef(p)               => p == param
    case TypeRepr.Applied(tycon, args)      => tycon.containsParam(param) || args.exists(_.containsParam(param))
    case TypeRepr.Structural(parents, _)    => parents.exists(_.containsParam(param))
    case TypeRepr.Intersection(left, right) => left.containsParam(param) || right.containsParam(param)
    case TypeRepr.Union(left, right)        => left.containsParam(param) || right.containsParam(param)
    case TypeRepr.Tuple(elems)              => elems.exists(_.containsParam(param))
    case TypeRepr.Function(params, result)  => params.exists(_.containsParam(param)) || result.containsParam(param)
    case TypeRepr.Singleton(_)              => false
    case TypeRepr.Constant(_)               => false
    case TypeRepr.AnyType                   => false
    case TypeRepr.NothingType               => false
  }
}

object TypeRepr {

  /**
   * Reference to a named type (possibly a type constructor if id.arity > 0).
   *
   * Examples:
   *   - `Int` → `Ref(intId)` where `intId.arity == 0`
   *   - `List` (unapplied) → `Ref(listId)` where `listId.arity == 1`
   */
  final case class Ref(id: TypeId[_]) extends TypeRepr

  /**
   * Reference to a type parameter.
   *
   * Used within type expressions to refer to type parameters. For example, in
   * `List[A]`, the `A` is a `ParamRef(TypeParam("A", 0))`.
   */
  final case class ParamRef(param: TypeParam) extends TypeRepr

  /**
   * Application of a type constructor to type arguments.
   *
   * Examples:
   *   - `List[Int]` → `Applied(Ref(listId), List(Ref(intId)))`
   *   - `Map[String, Int]` →
   *     `Applied(Ref(mapId), List(Ref(stringId), Ref(intId)))`
   *   - `F[A]` → `Applied(ParamRef(F), List(ParamRef(A)))`
   */
  final case class Applied(tycon: TypeRepr, args: List[TypeRepr]) extends TypeRepr

  /**
   * Structural (refinement) type.
   *
   * Examples:
   *   - `{ def foo: Int }` →
   *     `Structural(Nil, List(Def("foo", Nil, Ref(intId))))`
   *   - `AnyRef { type T; val x: T }` →
   *     `Structural(List(Ref(anyRefId)), List(...))`
   */
  final case class Structural(parents: List[TypeRepr], members: List[Member]) extends TypeRepr

  /**
   * Intersection type: `A & B` (Scala 3) or `A with B` (Scala 2).
   */
  final case class Intersection(left: TypeRepr, right: TypeRepr) extends TypeRepr

  /**
   * Union type: `A | B` (Scala 3 only).
   */
  final case class Union(left: TypeRepr, right: TypeRepr) extends TypeRepr

  /**
   * Tuple type: `(A, B, C)`.
   *
   * Represented as a list of element types.
   */
  final case class Tuple(elems: List[TypeRepr]) extends TypeRepr

  /**
   * Function type: `(A, B) => C`.
   *
   * @param params
   *   Parameter types
   * @param result
   *   Result type
   */
  final case class Function(params: List[TypeRepr], result: TypeRepr) extends TypeRepr

  /**
   * Singleton type: `x.type`.
   *
   * Represents the type of a specific term/value.
   */
  final case class Singleton(path: TermPath) extends TypeRepr

  /**
   * Constant/literal type: `42`, `"foo"`, `true`.
   *
   * Scala 3 supports literal types; in Scala 2.13 with -Xliteral-types.
   */
  final case class Constant(value: Any) extends TypeRepr

  /**
   * The top type: `Any`.
   */
  case object AnyType extends TypeRepr

  /**
   * The bottom type: `Nothing`.
   */
  case object NothingType extends TypeRepr

  // Convenience methods for constructing common types

  /**
   * Creates an intersection of multiple types.
   */
  def intersection(types: List[TypeRepr]): TypeRepr = types match {
    case Nil      => AnyType
    case t :: Nil => t
    case t :: ts  => ts.foldLeft(t)(Intersection.apply)
  }

  /**
   * Creates a union of multiple types.
   */
  def union(types: List[TypeRepr]): TypeRepr = types match {
    case Nil      => NothingType
    case t :: Nil => t
    case t :: ts  => ts.foldLeft(t)(Union.apply)
  }
}
