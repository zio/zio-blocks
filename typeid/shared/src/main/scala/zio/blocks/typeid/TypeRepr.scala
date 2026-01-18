package zio.blocks.typeid

/**
 * Represents type expressions in the Scala type system.
 *
 * TypeRepr can represent:
 *   - Simple type references (`Int`, `String`)
 *   - Applied types (`List[Int]`, `Map[String, Int]`)
 *   - Type parameter references (`A`, `F[_]`)
 *   - Structural/refinement types (`{ def foo: Int }`)
 *   - Intersection types (`A & B & C`)
 *   - Union types (`A | B | C`)
 *   - Tuple types (`(A, B, C)`)
 *   - Function types (`A => B`, `(A, B) ?=> C`)
 *   - Singleton types (`x.type`)
 *   - Literal/constant types (`42`, `"foo"`)
 *   - Type lambdas (`[X] =>> F[X]`)
 *   - Wildcards (`? <: Upper`, `? >: Lower`)
 *   - And more...
 */
sealed trait TypeRepr {

  /**
   * Returns true if this type expression references the given type parameter.
   */
  def containsParam(param: TypeParam): Boolean = this match {
    case TypeRepr.Ref(_)                          => false
    case TypeRepr.ParamRef(p, _)                  => p == param
    case TypeRepr.Applied(tycon, args)            => tycon.containsParam(param) || args.exists(_.containsParam(param))
    case TypeRepr.Structural(parents, _)          => parents.exists(_.containsParam(param))
    case TypeRepr.Intersection(types)             => types.exists(_.containsParam(param))
    case TypeRepr.Union(types)                    => types.exists(_.containsParam(param))
    case TypeRepr.Tuple(elems)                    => elems.exists(_.tpe.containsParam(param))
    case TypeRepr.Function(params, result)        => params.exists(_.containsParam(param)) || result.containsParam(param)
    case TypeRepr.ContextFunction(params, result) =>
      params.exists(_.containsParam(param)) || result.containsParam(param)
    case TypeRepr.TypeLambda(_, body) => body.containsParam(param)
    case TypeRepr.ByName(underlying)  => underlying.containsParam(param)
    case TypeRepr.Repeated(element)   => element.containsParam(param)
    case TypeRepr.Wildcard(bounds)    =>
      bounds.lower.exists(_.containsParam(param)) || bounds.upper.exists(_.containsParam(param))
    case TypeRepr.TypeProjection(qualifier, _) => qualifier.containsParam(param)
    case TypeRepr.TypeSelect(qualifier, _)     => qualifier.containsParam(param)
    case TypeRepr.Annotated(underlying, _)     => underlying.containsParam(param)
    case TypeRepr.ThisType(_)                  => false
    case TypeRepr.Singleton(_)                 => false
    case _: TypeRepr.Constant                  => false
    case TypeRepr.AnyType                      => false
    case TypeRepr.NothingType                  => false
    case TypeRepr.NullType                     => false
    case TypeRepr.UnitType                     => false
    case TypeRepr.AnyKindType                  => false
  }
}

object TypeRepr {

  // ============================================================================
  // Basic Type References
  // ============================================================================

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
   *
   * @param param
   *   The type parameter being referenced
   * @param binderDepth
   *   De Bruijn index for nested type lambdas (0 = innermost binder)
   */
  final case class ParamRef(param: TypeParam, binderDepth: Int = 0) extends TypeRepr

  // ============================================================================
  // Type Application
  // ============================================================================

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

  // ============================================================================
  // Structural Types
  // ============================================================================

  /**
   * Structural (refinement) type.
   *
   * Examples:
   *   - `{ def foo: Int }` →
   *     `Structural(Nil, List(Def("foo", Nil, Nil, Ref(intId))))`
   *   - `AnyRef { type T; val x: T }` →
   *     `Structural(List(Ref(anyRefId)), List(...))`
   */
  final case class Structural(parents: List[TypeRepr], members: List[Member]) extends TypeRepr

  // ============================================================================
  // Compound Types
  // ============================================================================

  /**
   * Intersection type: `A & B & C` (Scala 3) or `A with B with C` (Scala 2).
   *
   * The list contains at least 2 types. For single types, use the type
   * directly.
   */
  final case class Intersection(types: List[TypeRepr]) extends TypeRepr

  /**
   * Union type: `A | B | C` (Scala 3 only).
   *
   * The list contains at least 2 types. For single types, use the type
   * directly.
   */
  final case class Union(types: List[TypeRepr]) extends TypeRepr

  // ============================================================================
  // Tuple Types
  // ============================================================================

  /**
   * Tuple type: `(A, B, C)` or named tuples `(name: String, age: Int)` (Scala
   * 3.5+).
   *
   * @param elems
   *   The tuple elements, each with an optional label
   */
  final case class Tuple(elems: List[TupleElement]) extends TypeRepr

  // ============================================================================
  // Function Types
  // ============================================================================

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
   * Context function type: `(A, B) ?=> C` (Scala 3).
   *
   * Context functions provide their arguments implicitly.
   */
  final case class ContextFunction(params: List[TypeRepr], result: TypeRepr) extends TypeRepr

  /**
   * Type lambda: `[X, Y] =>> F[X, Y]` (Scala 3).
   *
   * Represents an anonymous type-level function.
   */
  final case class TypeLambda(params: List[TypeParam], body: TypeRepr) extends TypeRepr

  // ============================================================================
  // Parameter Modifiers
  // ============================================================================

  /**
   * By-name parameter type: `=> A`.
   *
   * The argument is evaluated lazily each time it's used.
   */
  final case class ByName(underlying: TypeRepr) extends TypeRepr

  /**
   * Repeated parameter type: `A*` (varargs).
   *
   * Used for variadic parameters.
   */
  final case class Repeated(element: TypeRepr) extends TypeRepr

  // ============================================================================
  // Wildcards and Bounds
  // ============================================================================

  /**
   * Wildcard type with bounds: `?`, `? <: Upper`, `? >: Lower`.
   *
   * Used in type arguments when the exact type is not specified.
   */
  final case class Wildcard(bounds: TypeBounds = TypeBounds.Unbounded) extends TypeRepr

  // ============================================================================
  // Path-Dependent and Singleton Types
  // ============================================================================

  /**
   * Singleton type: `x.type`.
   *
   * Represents the type of a specific term/value.
   */
  final case class Singleton(path: TermPath) extends TypeRepr

  /**
   * This type: `this.type` within a class/trait.
   *
   * Represents the singleton type of the enclosing instance.
   */
  final case class ThisType(owner: Owner) extends TypeRepr

  /**
   * Type projection: `Outer#Inner`.
   *
   * Refers to a type member of another type.
   */
  final case class TypeProjection(qualifier: TypeRepr, name: String) extends TypeRepr

  /**
   * Type selection: `qualifier.Member`.
   *
   * Path-dependent type access through a term.
   */
  final case class TypeSelect(qualifier: TypeRepr, name: String) extends TypeRepr

  // ============================================================================
  // Annotated Types
  // ============================================================================

  /**
   * Annotated type: `A @annotation`.
   *
   * A type with one or more annotations attached.
   */
  final case class Annotated(underlying: TypeRepr, annotations: List[Annotation]) extends TypeRepr

  // ============================================================================
  // Constant/Literal Types
  // ============================================================================

  /**
   * Constant/literal type: `42`, `"foo"`, `true`.
   *
   * Scala 3 supports literal types natively; Scala 2.13 with -Xliteral-types.
   */
  sealed trait Constant extends TypeRepr

  object Constant {
    final case class IntConst(value: Int)         extends Constant
    final case class LongConst(value: Long)       extends Constant
    final case class FloatConst(value: Float)     extends Constant
    final case class DoubleConst(value: Double)   extends Constant
    final case class BooleanConst(value: Boolean) extends Constant
    final case class CharConst(value: Char)       extends Constant
    final case class StringConst(value: String)   extends Constant
    case object NullConst                         extends Constant
    case object UnitConst                         extends Constant
    final case class ClassOfConst(tpe: TypeRepr)  extends Constant
  }

  // ============================================================================
  // Special Types
  // ============================================================================

  /** The top type: `Any`. */
  case object AnyType extends TypeRepr

  /** The bottom type: `Nothing`. */
  case object NothingType extends TypeRepr

  /** The null type: `Null`. */
  case object NullType extends TypeRepr

  /** The unit type: `Unit`. */
  case object UnitType extends TypeRepr

  /** Any kind type (for kind-polymorphic contexts). */
  case object AnyKindType extends TypeRepr

  // ============================================================================
  // Convenience Constructors
  // ============================================================================

  /**
   * Creates an intersection of multiple types.
   */
  def intersection(types: List[TypeRepr]): TypeRepr = types match {
    case Nil      => AnyType
    case t :: Nil => t
    case ts       => Intersection(ts)
  }

  /**
   * Creates a union of multiple types.
   */
  def union(types: List[TypeRepr]): TypeRepr = types match {
    case Nil      => NothingType
    case t :: Nil => t
    case ts       => Union(ts)
  }

  /**
   * Creates a simple tuple (unlabeled elements).
   */
  def tuple(types: List[TypeRepr]): TypeRepr =
    Tuple(types.map(t => TupleElement(None, t)))

  /**
   * Creates a simple function type.
   */
  def function(params: List[TypeRepr], result: TypeRepr): TypeRepr =
    Function(params, result)
}

/**
 * An element of a tuple type.
 *
 * In Scala 3.5+, tuple elements can have labels (named tuples):
 * `(name: String, age: Int)`
 *
 * @param label
 *   Optional label for named tuples
 * @param tpe
 *   The type of this element
 */
final case class TupleElement(label: Option[String], tpe: TypeRepr)
