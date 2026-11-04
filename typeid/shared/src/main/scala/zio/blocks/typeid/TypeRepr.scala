/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.typeid

/**
 * A type expression, as stored inside [[TypeId]] values: the type arguments of
 * an applied type ([[TypeId#typeArgs]]), the type an alias stands for
 * ([[TypeId#aliasedTo]]) and the parents of a definition ([[TypeId#parents]]).
 * A named type is a [[TypeRepr.Ref]] to its id, and an applied type is an
 * [[TypeRepr.Applied]] of a constructor to arguments. Other cases cover type
 * parameters, unions and intersections, tuples, functions, structural types,
 * singleton and literal types, wildcards and type lambdas.
 *
 * The two macros do not produce the same cases. The Scala 2 macro writes named
 * and applied types, and writes `A with B` as a [[TypeRepr.Structural]] type
 * and a tuple or function type as an applied `TupleN` or `FunctionN`. The Scala
 * 3 macro also writes [[TypeRepr.Union]], [[TypeRepr.Intersection]],
 * [[TypeRepr.Tuple]], [[TypeRepr.Function]], [[TypeRepr.TypeLambda]],
 * [[TypeRepr.Wildcard]] and literal types. [[TypeIdPrinter]] renders any case
 * as Scala source.
 *
 * {{{
 * val args = TypeId.of[Map[String, List[Int]]].typeArgs
 * args.map(arg => TypeIdPrinter.render(arg)) // List(String, List[Int])
 *
 * args(1) match {
 *   case TypeRepr.Applied(TypeRepr.Ref(list), List(TypeRepr.Ref(int))) => list.name + " of " + int.name
 *   case other                                                          => other.toString
 * }
 * // "List of Int"
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/typeid/ TypeId]]
 */
sealed trait TypeRepr {

  /**
   * Returns `true` if this type expression contains a [[TypeRepr.ParamRef]] to
   * `param`, at any depth except inside the members of a
   * [[TypeRepr.Structural]] type. A [[TypeRepr.Ref]] to an id is not searched.
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

/**
 * The cases of [[TypeRepr]], and the constructors `intersection`, `union`,
 * `tuple` and `function`.
 */
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
   * A reference to a type parameter by its [[TypeParam]], as in the body of a
   * [[TypeLambda]]: the Scala 3 macro writes the alias
   * `type StrMap[V] = Map[String, V]` with `ParamRef(V, 0)` for `V`. The type
   * parameter of an enclosing method, as in `TypeId.of[List[A]]` inside
   * `def f[A]`, is a [[Ref]] to an id named `A` instead.
   *
   * @param param
   *   the type parameter being referenced
   * @param binderDepth
   *   the De Bruijn index of the binding type lambda (0 for the innermost); the
   *   macros write 0
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
   * A structural (refinement) type, such as `AnyRef { def name: String }`: its
   * parents and the members it declares. The Scala 2 macro also writes an
   * intersection `A with B` this way, with `A` and `B` as parents and no
   * members.
   *
   * The macros do not record method parameters: Scala 2 keeps only
   * parameterless members, as [[Member.Val]], and drops methods with
   * parameters; Scala 3 writes a method with parameters as a [[Member.Def]]
   * with no parameter lists.
   */
  final case class Structural(parents: List[TypeRepr], members: List[Member]) extends TypeRepr

  // ============================================================================
  // Compound Types
  // ============================================================================

  /**
   * An intersection type, `A & B` on Scala 3. Equality ignores the order of the
   * types, so `A & B` equals `B & A`. The Scala 3 macro produces it; the Scala
   * 2 macro writes `A with B` as a [[Structural]] type instead.
   *
   * The constructor accepts any list. [[TypeRepr.intersection]] returns the
   * type itself for a one-element list and [[AnyType]] for an empty one.
   */
  final class Intersection private (val types: List[TypeRepr]) extends TypeRepr {
    override def equals(other: Any): Boolean = other match {
      case that: Intersection => this.types.toSet == that.types.toSet
      case _                  => false
    }

    override def hashCode(): Int = types.toSet.hashCode()

    override def toString: String = TypeIdPrinter.render(this)
  }

  /**
   * Creates and matches an [[Intersection]].
   */
  object Intersection {

    /**
     * Creates an [[Intersection]] that holds `types` as given: it does not
     * flatten nested intersections, remove duplicates or check the length.
     * [[TypeRepr.intersection]] handles an empty list and a one-element list.
     */
    def apply(types: List[TypeRepr]): Intersection = new Intersection(types)

    /**
     * Gives the types of the [[Intersection]], in the order it was created
     * with.
     */
    def unapply(i: Intersection): Some[List[TypeRepr]] = Some(i.types)
  }

  /**
   * A union type, `A | B` on Scala 3, where only the Scala 3 macro produces it.
   * Equality ignores the order of the types, so `A | B` equals `B | A`.
   *
   * The constructor accepts any list. [[TypeRepr.union]] returns the type
   * itself for a one-element list and [[NothingType]] for an empty one.
   */
  final class Union private (val types: List[TypeRepr]) extends TypeRepr {
    override def equals(other: Any): Boolean = other match {
      case that: Union => this.types.toSet == that.types.toSet
      case _           => false
    }

    override def hashCode(): Int = types.toSet.hashCode()

    override def toString: String = TypeIdPrinter.render(this)
  }

  /**
   * Creates and matches a [[Union]].
   */
  object Union {

    /**
     * Creates a [[Union]] that holds `types` as given: it does not flatten
     * nested unions, remove duplicates or check the length. [[TypeRepr.union]]
     * handles an empty list and a one-element list.
     */
    def apply(types: List[TypeRepr]): Union = new Union(types)

    /**
     * Gives the types of the [[Union]], in the order it was created with.
     */
    def unapply(u: Union): Some[List[TypeRepr]] = Some(u.types)
  }

  // ============================================================================
  // Tuple Types
  // ============================================================================

  /**
   * A tuple type `(A, B, C)`, as the Scala 3 macro writes it inside a type,
   * with one element per position. Two ids are equal when their type arguments
   * differ only between this form and the applied `TupleN` form that the Scala
   * 2 macro writes, although the two `TypeRepr` values are not equal. A Scala 3
   * named tuple is not written this way: it is an applied `NamedTuple` whose
   * two arguments are tuples of the names (as string constants) and of the
   * types.
   *
   * @param elems
   *   the elements, in order; the macros leave their labels empty
   */
  final case class Tuple(elems: List[TupleElement]) extends TypeRepr

  // ============================================================================
  // Function Types
  // ============================================================================

  /**
   * A function type `(A, B) => C`, as the Scala 3 macro writes it inside a
   * type. The Scala 2 macro writes an applied `FunctionN` instead, and
   * `TypeId.of` on a function type gives the id of `FunctionN` on both
   * versions.
   *
   * @param params
   *   the parameter types
   * @param result
   *   the result type
   */
  final case class Function(params: List[TypeRepr], result: TypeRepr) extends TypeRepr

  /**
   * A context function type `(A, B) ?=> C` (Scala 3), whose arguments are
   * passed implicitly.
   */
  final case class ContextFunction(params: List[TypeRepr], result: TypeRepr) extends TypeRepr

  /**
   * A type lambda `[X, Y] =>> F[X, Y]` (Scala 3), an anonymous type-level
   * function. The Scala 3 macro writes the target of an alias with type
   * parameters this way.
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
   * A wildcard type argument with bounds: `?`, `? <: Upper` or `? >: Lower`.
   * The Scala 3 macro writes `List[?]` with one `Wildcard` argument.
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
   * A type with annotations attached: `A @annotation`. The macros drop
   * annotations on types inside a type expression, so derived ids do not
   * contain it.
   */
  final case class Annotated(underlying: TypeRepr, annotations: List[Annotation]) extends TypeRepr

  // ============================================================================
  // Constant/Literal Types
  // ============================================================================

  /**
   * A literal (constant) type, such as `42`, `"foo"` or `true`. The Scala 3
   * macro writes a literal type found inside a type this way, such as the names
   * of a named tuple. `TypeId.of` on a literal type itself gives the id of the
   * widened type, such as `Int` for `1`.
   */
  sealed trait Constant extends TypeRepr

  /**
   * The literal types: `IntConst`, `LongConst`, `FloatConst`, `DoubleConst`,
   * `BooleanConst`, `CharConst`, `StringConst`, `NullConst`, `UnitConst` and
   * `ClassOfConst`.
   */
  object Constant {

    /**
     * An `Int` literal type, such as `42`.
     */
    final case class IntConst(value: Int) extends Constant

    /**
     * A `Long` literal type, such as `42L`.
     */
    final case class LongConst(value: Long) extends Constant

    /**
     * A `Float` literal type, such as `1.5f`.
     */
    final case class FloatConst(value: Float) extends Constant

    /**
     * A `Double` literal type, such as `1.5`.
     */
    final case class DoubleConst(value: Double) extends Constant

    /**
     * A `Boolean` literal type, `true` or `false`.
     */
    final case class BooleanConst(value: Boolean) extends Constant

    /**
     * A `Char` literal type, such as `'a'`.
     */
    final case class CharConst(value: Char) extends Constant

    /**
     * A `String` literal type, such as `"foo"`.
     */
    final case class StringConst(value: String) extends Constant

    /**
     * The type of the `null` literal.
     */
    case object NullConst extends Constant

    /**
     * The type of the unit literal `()`.
     */
    case object UnitConst extends Constant

    /**
     * A class literal type, `classOf[T]`, where `tpe` is `T`.
     */
    final case class ClassOfConst(tpe: TypeRepr) extends Constant
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
   * Creates the intersection of `types`: [[AnyType]] for an empty list, the
   * type itself for one type, and an [[Intersection]] otherwise.
   */
  def intersection(types: List[TypeRepr]): TypeRepr = types match {
    case Nil      => AnyType
    case t :: Nil => t
    case ts       => Intersection(ts)
  }

  /**
   * Creates the union of `types`: [[NothingType]] for an empty list, the type
   * itself for one type, and a [[Union]] otherwise.
   */
  def union(types: List[TypeRepr]): TypeRepr = types match {
    case Nil      => NothingType
    case t :: Nil => t
    case ts       => Union(ts)
  }

  /**
   * Creates a [[Tuple]] of `types`, with no labels.
   */
  def tuple(types: List[TypeRepr]): TypeRepr =
    Tuple(types.map(t => TupleElement(None, t)))

  /**
   * Creates the [[Function]] type from `params` to `result`.
   */
  def function(params: List[TypeRepr], result: TypeRepr): TypeRepr =
    Function(params, result)
}

/**
 * One element of a [[TypeRepr.Tuple]].
 *
 * @param label
 *   an optional label; the macros leave it `None`, since a Scala 3 named tuple
 *   is an applied `NamedTuple`
 * @param tpe
 *   the type of the element
 */
final case class TupleElement(label: Option[String], tpe: TypeRepr)
