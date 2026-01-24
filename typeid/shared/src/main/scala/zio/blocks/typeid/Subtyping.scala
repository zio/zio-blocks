package zio.blocks.typeid

private[blocks] object Subtyping {

  final case class Context(
    assumptions: Set[(TypeRepr, TypeRepr)] = Set.empty,
    depth: Int = 0,
    maxDepth: Int = 100
  ) {
    def assume(sub: TypeRepr, sup: TypeRepr): Context =
      copy(assumptions = assumptions + ((sub, sup)))

    def isAssumed(sub: TypeRepr, sup: TypeRepr): Boolean =
      assumptions.contains((sub, sup))

    def deeper: Context  = copy(depth = depth + 1)
    def tooDeep: Boolean = depth >= maxDepth
  }

  // Well-known type hierarchy for runtime lookup (when macro doesn't capture all parents)
  private val knownHierarchy: Map[String, Set[String]] = Map(
    // String hierarchy
    "java.lang.String" -> Set(
      "java.lang.CharSequence",
      "java.io.Serializable",
      "java.lang.Comparable"
    ),
    // Primitives extend AnyVal
    "scala.Int"     -> Set("scala.AnyVal"),
    "scala.Long"    -> Set("scala.AnyVal"),
    "scala.Double"  -> Set("scala.AnyVal"),
    "scala.Float"   -> Set("scala.AnyVal"),
    "scala.Boolean" -> Set("scala.AnyVal"),
    "scala.Char"    -> Set("scala.AnyVal"),
    "scala.Byte"    -> Set("scala.AnyVal"),
    "scala.Short"   -> Set("scala.AnyVal"),
    "scala.Unit"    -> Set("scala.AnyVal"),
    // AnyVal extends Any
    "scala.AnyVal" -> Set("scala.Any"),
    // AnyRef extends Any
    "scala.AnyRef"       -> Set("scala.Any"),
    "java.lang.Object"   -> Set("scala.Any"),
    "java.lang.Runnable" -> Set("scala.Any")
  )

  // Check if subFullName is a known subtype of supFullName
  private def isKnownSubtype(subFullName: String, supFullName: String): Boolean = {
    if (subFullName == supFullName) return true

    knownHierarchy.get(subFullName) match {
      case Some(parents) =>
        parents.contains(supFullName) || parents.exists(p => isKnownSubtype(p, supFullName))
      case None => false
    }
  }

  // Helper to check if a TypeRepr represents Nothing
  private def isNothing(t: TypeRepr): Boolean = t match {
    case TypeRepr.NothingType  => true
    case TypeRepr.Ref(id, Nil) => id.fullName == "scala.Nothing"
    case _                     => false
  }

  // Helper to check if a TypeRepr represents Any
  private def isAny(t: TypeRepr): Boolean = t match {
    case TypeRepr.AnyType      => true
    case TypeRepr.Ref(id, Nil) => id.fullName == "scala.Any"
    case _                     => false
  }

  // Helper to check if a TypeRepr represents EmptyTuple
  private def isEmptyTuple(t: TypeRepr): Boolean = t match {
    case TypeRepr.Ref(id, Nil) =>
      id.fullName == "scala.EmptyTuple" || id.fullName == "scala.Tuple$package.EmptyTuple"
    case TypeRepr.Wildcard(_) =>
      true // wildcards at tail position match EmptyTuple structurally
    case _ => false
  }

  // Helper to check if TypeId represents the tuple cons operator *:
  private def isTupleCons(id: TypeId[_]): Boolean =
    id.name == "*:" && id.owner.asString.startsWith("scala")

  // Helper to check if TypeId represents a TupleN type
  private def isTupleN(id: TypeId[_]): Boolean =
    id.name.matches("Tuple\\d+") && id.owner.asString.startsWith("scala")

  // Helper to check if TypeId represents Tuple1
  private def isTuple1(id: TypeId[_]): Boolean =
    id.name == "Tuple1" && id.owner.asString.startsWith("scala")

  /**
   * Flatten a *: chain into a list of element types. Returns None if the type
   * is not a *: chain. For example: Int *: String *: EmptyTuple becomes
   * Some(List(Int, String))
   */
  private def flattenTupleCons(t: TypeRepr): Option[List[TypeRepr]] = t match {
    case TypeRepr.Ref(id, List(head, tail)) if isTupleCons(id) =>
      flattenTupleCons(tail).map(head :: _)
    case TypeRepr.Ref(id, Nil) if isEmptyTuple(TypeRepr.Ref(id, Nil)) =>
      Some(Nil)
    case TypeRepr.Wildcard(_) =>
      Some(Nil) // Wildcards at tail position treated as EmptyTuple
    case _ =>
      None
  }

  /**
   * Flatten a TupleN type into its element types. Returns None if not a TupleN.
   * For example: Tuple2[Int, String] becomes Some(List(Int, String))
   */
  private def flattenTupleN(t: TypeRepr): Option[List[TypeRepr]] = t match {
    case TypeRepr.Ref(id, args) if isTupleN(id) || isTuple1(id) =>
      Some(args)
    case _ =>
      None
  }

  /**
   * Check if two tuple representations are structurally equivalent. This
   * handles the case where *: chains and TupleN represent the same tuple
   * structure.
   */
  private def tupleStructurallyEqual(a: TypeRepr, b: TypeRepr): Boolean = {
    val aFlat = flattenTupleCons(a).orElse(flattenTupleN(a))
    val bFlat = flattenTupleCons(b).orElse(flattenTupleN(b))

    (aFlat, bFlat) match {
      case (Some(aElems), Some(bElems)) =>
        aElems.size == bElems.size && aElems.zip(bElems).forall { case (x, y) =>
          structurallyEqual(x, y) || tupleStructurallyEqual(x, y)
        }
      case _ => false
    }
  }

  /**
   * Structural equality check for TypeRepr that avoids triggering custom equals
   * methods. This is used to break the recursive cycle between TypeId.equals
   * and Subtyping.isSubtype. Uses nominal identity (owner + name) for TypeId
   * comparisons.
   */
  private def structurallyEqual(a: TypeRepr, b: TypeRepr): Boolean = (a, b) match {
    case (TypeRepr.Ref(id1, args1), TypeRepr.Ref(id2, args2)) =>
      // Compare TypeIds by nominal identity (owner + name), not by custom equals
      typeIdNominallyEqual(id1, id2) &&
      args1.size == args2.size &&
      args1.zip(args2).forall { case (a1, a2) => structurallyEqual(a1, a2) }

    case (TypeRepr.AppliedType(t1, a1), TypeRepr.AppliedType(t2, a2)) =>
      structurallyEqual(t1, t2) &&
      a1.size == a2.size &&
      a1.zip(a2).forall { case (x, y) => structurallyEqual(x, y) }

    case (TypeRepr.Union(ts1), TypeRepr.Union(ts2)) =>
      ts1.size == ts2.size && ts1.forall(t1 => ts2.exists(t2 => structurallyEqual(t1, t2)))

    case (TypeRepr.Intersection(ts1), TypeRepr.Intersection(ts2)) =>
      ts1.size == ts2.size && ts1.forall(t1 => ts2.exists(t2 => structurallyEqual(t1, t2)))

    case (TypeRepr.Function(p1, r1), TypeRepr.Function(p2, r2)) =>
      p1.size == p2.size &&
      p1.zip(p2).forall { case (x, y) => structurallyEqual(x, y) } &&
      structurallyEqual(r1, r2)

    case (TypeRepr.Tuple(e1), TypeRepr.Tuple(e2)) =>
      e1.size == e2.size && e1.zip(e2).forall { case (x, y) => structurallyEqual(x, y) }

    case (TypeRepr.TypeParamRef(n1, i1), TypeRepr.TypeParamRef(n2, i2)) =>
      n1 == n2 && i1 == i2

    case (TypeRepr.ConstantType(v1), TypeRepr.ConstantType(v2)) =>
      v1 == v2

    case (TypeRepr.Wildcard(b1), TypeRepr.Wildcard(b2)) =>
      b1.lower.zip(b2.lower).forall { case (x, y) => structurallyEqual(x, y) } &&
      b1.upper.zip(b2.upper).forall { case (x, y) => structurallyEqual(x, y) }

    case (TypeRepr.TypeProjection(q1, n1), TypeRepr.TypeProjection(q2, n2)) =>
      structurallyEqual(q1, q2) && n1 == n2

    case (TypeRepr.ThisType(t1), TypeRepr.ThisType(t2)) =>
      structurallyEqual(t1, t2)

    case (TypeRepr.SuperType(th1, su1), TypeRepr.SuperType(th2, su2)) =>
      structurallyEqual(th1, th2) && structurallyEqual(su1, su2)

    case (TypeRepr.TypeLambda(_, r1), TypeRepr.TypeLambda(_, r2)) =>
      structurallyEqual(r1, r2)

    // Case objects
    case (TypeRepr.AnyType, TypeRepr.AnyType)         => true
    case (TypeRepr.AnyKindType, TypeRepr.AnyKindType) => true
    case (TypeRepr.NothingType, TypeRepr.NothingType) => true
    case (TypeRepr.NullType, TypeRepr.NullType)       => true
    case (TypeRepr.UnitType, TypeRepr.UnitType)       => true

    // Fallback: check for tuple equivalence (*: vs TupleN)
    case _ => tupleStructurallyEqual(a, b)
  }

  /**
   * Nominal equality check for TypeIds (compares owner and name only). Does not
   * use TypeId.equals to avoid infinite recursion.
   */
  private def typeIdNominallyEqual(a: TypeId[_], b: TypeId[_]): Boolean =
    a.owner == b.owner && a.name == b.name

  /**
   * Structural equality check for lists of TypeReprs. Avoids triggering custom
   * equals on TypeRepr.
   */
  private def argsStructurallyEqual(args1: List[TypeRepr], args2: List[TypeRepr]): Boolean =
    args1.size == args2.size && args1.zip(args2).forall { case (a, b) => structurallyEqual(a, b) }

  def isSubtype(sub: TypeRepr, sup: TypeRepr)(implicit ctx: Context = Context()): Boolean = {
    if (ctx.tooDeep) return false // Fail safe

    if (ctx.isAssumed(sub, sup)) return true
    // Use reference equality first to avoid triggering custom equals
    if (sub.eq(sup)) return true
    // Then check structural equality (avoids infinite recursion)
    if (structurallyEqual(sub, sup)) return true

    val subNorm = sub.dealias
    val supNorm = sup.dealias

    // Check reference equality on normalized forms
    if (subNorm.eq(supNorm)) return true
    if (structurallyEqual(subNorm, supNorm)) return true

    // Handle Nothing and Any specially
    if (isNothing(subNorm)) return true
    if (isAny(supNorm)) return true

    val newCtx = ctx.assume(sub, sup).deeper

    (subNorm, supNorm) match {
      case (TypeRepr.NothingType, _) => true
      case (_, TypeRepr.AnyType)     => true
      case (_, TypeRepr.AnyKindType) => true // Assuming AnyKind is top

      // Intersection on RHS: A <: B & C => A <: B && A <: C
      case (_, TypeRepr.Intersection(ts)) =>
        ts.forall(t => isSubtype(subNorm, t)(newCtx))

      // Union on LHS: A | B <: C => A <: C && B <: C
      case (TypeRepr.Union(ts), _) =>
        ts.forall(t => isSubtype(t, supNorm)(newCtx))

      // Union on RHS: A <: B | C => A <: B || A <: C
      case (_, TypeRepr.Union(ts)) =>
        ts.exists(t => isSubtype(subNorm, t)(newCtx))

      // Intersection on LHS: A & B <: C => A <: C || B <: C
      case (TypeRepr.Intersection(ts), _) =>
        ts.exists(t => isSubtype(t, supNorm)(newCtx))

      // Applied Types (Generics) - e.g., List[String] <: List[CharSequence]
      case (TypeRepr.AppliedType(t1, args1), TypeRepr.AppliedType(t2, args2)) =>
        // First check if type constructors are compatible
        if (isSubtype(t1, t2)(newCtx)) {
          // Get TypeId from t2 (the supertype) to get variance info
          t2 match {
            case TypeRepr.Ref(id, _) => checkArgs(id.typeParams, args1, args2)(newCtx)
            case _                   => argsStructurallyEqual(args1, args2) // fallback to invariance if unknown
          }
        } else false

      // Nominal References - e.g., String <: CharSequence
      case (TypeRepr.Ref(id1, args1), TypeRepr.Ref(id2, args2)) =>
        val (base1, fullArgs1) = (id1.copy(args = Nil), id1.args ++ args1)
        val (base2, fullArgs2) = (id2.copy(args = Nil), id2.args ++ args2)

        if (typeIdNominallyEqual(base1, base2)) {
          // Same base type, check args with variance
          checkArgs(base1.typeParams, fullArgs1, fullArgs2)(newCtx)
        } else {
          // Different base types - check class hierarchy
          val subName = base1.fullName
          val supName = base2.fullName

          // First try the known hierarchy lookup (runtime fallback)
          if (isKnownSubtype(subName, supName)) {
            true
          } else {
            // Then check captured parents from macro
            id1.parents.exists(p => isSubtype(p, supNorm)(newCtx))
          }
        }

      // Function types: Function1[-A, +B]
      case (TypeRepr.Function(params1, result1), TypeRepr.Function(params2, result2)) =>
        // Contravariant in params, covariant in result
        params1.size == params2.size &&
        params1.zip(params2).forall { case (p1, p2) => isSubtype(p2, p1)(newCtx) } && // contravariant
        isSubtype(result1, result2)(newCtx) // covariant

      // Default fallback
      case _ => false
    }
  }

  def isEquivalent(a: TypeRepr, b: TypeRepr)(implicit ctx: Context = Context()): Boolean =
    isSubtype(a, b) && isSubtype(b, a)

  private def checkArgs(params: List[TypeParam], args1: List[TypeRepr], args2: List[TypeRepr])(implicit
    ctx: Context
  ): Boolean = {
    if (args1.size != args2.size) return false
    if (params.isEmpty && args1.nonEmpty) {
      // No variance info available, assume invariant
      return argsStructurallyEqual(args1, args2)
    }

    // If we have fewer params than args, extend with invariant
    val extendedParams = if (params.size < args1.size) {
      params ++ List.fill(args1.size - params.size)(TypeParam("_", 0, Variance.Invariant))
    } else {
      params
    }

    extendedParams.zip(args1.zip(args2)).forall { case (param, (a1, a2)) =>
      (a1, a2) match {
        // Case: List[_] <: List[_]
        case (TypeRepr.Wildcard(b1), TypeRepr.Wildcard(b2)) =>
          (for { u1 <- b1.upper; u2 <- b2.upper } yield isSubtype(u1, u2)).getOrElse(true) &&
          (for { l1 <- b1.lower; l2 <- b2.lower } yield isSubtype(l2, l1)).getOrElse(true)

        // Case: List[Int] <: List[_]
        case (t, TypeRepr.Wildcard(bounds)) =>
          bounds.lower.forall(l => isSubtype(l, t)) && bounds.upper.forall(u => isSubtype(t, u))

        // Case: List[_] <: List[Int] (Usually false unless bounds are tight)
        case (TypeRepr.Wildcard(bounds), t) =>
          bounds.upper.exists(u => isSubtype(u, t))

        case _ =>
          param.variance match {
            case Variance.Invariant     => isEquivalent(a1, a2)
            case Variance.Covariant     => isSubtype(a1, a2)
            case Variance.Contravariant => isSubtype(a2, a1)
          }
      }
    }
  }
}
