package zio.blocks.typeid

/**
 * Represents a type expression, which can be a reference to a TypeId, a type
 * application, a compound type (intersection/union), etc.
 */
sealed trait TypeRepr {
  def substitute(substitutions: Map[TypeParam, TypeRepr]): TypeRepr =
    TypeRepr.substitute(this, substitutions)
}

object TypeRepr {

  // ==================== Type References ====================

  /** Reference to a type by its TypeId */
  final case class Ref(id: TypeId[? <: AnyKind]) extends TypeRepr

  /** Reference to a type parameter */
  final case class ParamRef(
    param: TypeParam,
    binderDepth: Int = 0
  ) extends TypeRepr

  /** Path-dependent type: qualifier.Member */
  final case class TypeSelect(
    qualifier: TermPath,
    memberName: String
  ) extends TypeRepr {
    def isStable: Boolean = qualifier.isStable
  }

  /** Type projection: Outer#Inner */
  final case class TypeProjection(
    prefix: TypeRepr,
    memberName: String
  ) extends TypeRepr

  // ==================== Type Application ====================

  /** Type constructor application: F[A, B] */
  final case class Applied(
    tycon: TypeRepr,
    args: List[TypeRepr]
  ) extends TypeRepr {
    def arity: Int = args.size
  }

  // ==================== Type Constructors ====================

  /** Type lambda: [X, Y] =>> F[X, Y] */
  final case class TypeLambda(
    params: List[TypeParam],
    body: TypeRepr
  ) extends TypeRepr {
    def arity: Int = params.size
  }

  // ==================== Compound Types ====================

  /** Intersection: A & B */
  final case class Intersection(components: List[TypeRepr]) extends TypeRepr {
    require(components.size >= 2, "Intersection requires at least 2 types")
    val sortedComponents: List[TypeRepr] = components.distinct.sortBy(_.toString)
  }

  object Intersection {
    def apply(left: TypeRepr, right: TypeRepr): Intersection = {
      val comps = (left, right) match {
        case (Intersection(l), Intersection(r)) => l ++ r
        case (Intersection(l), r)               => l :+ r
        case (l, Intersection(r))               => l :: r
        case (l, r)                             => List(l, r)
      }
      Intersection(comps.distinct.sortBy(_.toString))
    }
  }

  /** Union: A | B */
  final case class Union(components: List[TypeRepr]) extends TypeRepr {
    require(components.size >= 2, "Union requires at least 2 types")
    val sortedComponents: List[TypeRepr] = components.distinct.sortBy(_.toString)
  }

  object Union {
    def apply(left: TypeRepr, right: TypeRepr): Union = {
      val comps = (left, right) match {
        case (Union(l), Union(r)) => l ++ r
        case (Union(l), r)        => l :+ r
        case (l, Union(r))        => l :: r
        case (l, r)               => List(l, r)
      }
      Union(comps.distinct.sortBy(_.toString))
    }
  }

  /** Structural/refinement type: { def foo: Int } */
  final case class Structural(
    parents: List[TypeRepr],
    members: List[Member]
  ) extends TypeRepr

  // ==================== Function Types ====================

  /** Regular function: (A, B) => C */
  final case class Function(
    params: List[TypeRepr],
    result: TypeRepr
  ) extends TypeRepr {
    def arity: Int = params.size
  }

  /** Context function: (A, B) ?=> C */
  final case class ContextFunction(
    params: List[TypeRepr],
    result: TypeRepr
  ) extends TypeRepr

  /** Polymorphic function: [A] => A => A */
  final case class PolyFunction(
    typeParams: List[TypeParam],
    result: TypeRepr
  ) extends TypeRepr

  /** Dependent function: (x: A) => x.T */
  final case class DependentFunction(
    params: List[Param],
    result: TypeRepr
  ) extends TypeRepr

  // ==================== Special Parameter Types ====================

  /** By-name type: => A */
  final case class ByName(underlying: TypeRepr) extends TypeRepr

  /** Repeated/vararg type: A* */
  final case class Repeated(underlying: TypeRepr) extends TypeRepr

  // ==================== Tuple Types ====================

  /** Tuple type with optional labels */
  final case class Tuple(elements: List[TupleElement]) extends TypeRepr {
    def arity: Int = elements.size
  }

  final case class TupleElement(
    label: Option[String],
    tpe: TypeRepr
  )

  object Tuple {
    def positional(types: TypeRepr*): Tuple =
      Tuple(types.map(t => TupleElement(None, t)).toList)

    def named(fields: (String, TypeRepr)*): Tuple =
      Tuple(fields.map { case (n, t) => TupleElement(Some(n), t) }.toList)
  }

  // ==================== Singleton & Literal Types ====================

  /** Singleton type: x.type */
  final case class Singleton(path: TermPath) extends TypeRepr {
    def isStable: Boolean = path.isStable
  }

  /** This type */
  final case class ThisType(owner: TypeId[? <: AnyKind]) extends TypeRepr

  /** Super type reference */
  final case class SuperType(
    thisType: TypeRepr,
    mixinTrait: Option[TypeRepr]
  ) extends TypeRepr

  /** Literal type: 42, "foo" */
  final case class ConstantType(value: Constant) extends TypeRepr

  // ==================== Match Types ====================

  /** Match type */
  final case class MatchType(
    bound: TypeRepr,
    scrutinee: TypeRepr,
    cases: List[MatchTypeCase]
  ) extends TypeRepr

  final case class MatchTypeCase(
    bindings: List[TypeParam],
    pattern: TypeRepr,
    result: TypeRepr
  )

  // ==================== Bounded/Wildcard Types ====================

  /** Wildcard: ? <: Upper */
  final case class Wildcard(bounds: TypeBounds) extends TypeRepr

  object Wildcard {
    val unbounded: Wildcard             = Wildcard(TypeBounds.empty)
    def `<:`(upper: TypeRepr): Wildcard = Wildcard(TypeBounds.upper(upper))
    def `>:`(lower: TypeRepr): Wildcard = Wildcard(TypeBounds.lower(lower))
  }

  // ==================== Recursive Types ====================

  /** Recursive type reference */
  final case class RecType(body: TypeRepr) extends TypeRepr
  case object RecThis                      extends TypeRepr

  // ==================== Top, Bottom, and Special Types ====================

  case object AnyType     extends TypeRepr
  case object AnyKindType extends TypeRepr
  case object NothingType extends TypeRepr
  case object NullType    extends TypeRepr
  case object UnitType    extends TypeRepr

  /** Annotated type */
  final case class Annotated(
    underlying: TypeRepr,
    annotations: List[Annotation]
  ) extends TypeRepr

  /** Substitution logic */
  def substitute(repr: TypeRepr, subs: Map[TypeParam, TypeRepr]): TypeRepr = {
    def go(r: TypeRepr): TypeRepr = r match {
      case ParamRef(param, 0)                 => subs.getOrElse(param, r)
      case Applied(tycon, args)               => Applied(go(tycon), args.map(go))
      case Intersection(comps)                => Intersection(comps.map(go))
      case Union(comps)                       => Union(comps.map(go))
      case TypeLambda(params, body)           => TypeLambda(params, go(body)) // Simplified
      case TypeProjection(prefix, memberName) => TypeProjection(go(prefix), memberName)
      case Function(params, result)           => Function(params.map(go), go(result))
      case ContextFunction(params, result)    => ContextFunction(params.map(go), go(result))
      case PolyFunction(tps, result)          => PolyFunction(tps, go(result))
      case DependentFunction(params, result)  => DependentFunction(params, go(result))
      case Tuple(elems)                       => Tuple(elems.map(e => e.copy(tpe = go(e.tpe))))
      case ByName(u)                          => ByName(go(u))
      case Repeated(u)                        => Repeated(go(u))
      case MatchType(bound, scrutinee, cases) =>
        MatchType(go(bound), go(scrutinee), cases.map(c => c.copy(pattern = go(c.pattern), result = go(c.result))))
      case Wildcard(bounds)            => Wildcard(TypeBounds(bounds.lower.map(go), bounds.upper.map(go)))
      case RecType(body)               => RecType(go(body))
      case Annotated(underlying, anns) => Annotated(go(underlying), anns)
      case _                           => r
    }
    go(repr)
  }
}
