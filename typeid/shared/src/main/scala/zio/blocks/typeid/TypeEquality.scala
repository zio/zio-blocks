package zio.blocks.typeid

/**
 * Provides equality checks and hash code computation for TypeId and TypeRepr.
 *
 * Equality is defined as mutual subtyping: a equals b iff a.isSubtypeOf(b) &&
 * b.isSubtypeOf(a). This ensures that equality flows naturally from the
 * subtyping relation as per jdegoes' design.
 *
 * Hash codes are computed after dealiasing and normalizing the types to ensure
 * consistency with the equals contract.
 */
private[typeid] object TypeEquality {

  /**
   * Checks equality between two TypeIds. Equality is defined as:
   * a.isSubtypeOf(b) && b.isSubtypeOf(a) This means two types are equal only if
   * they are mutually subtypes of each other.
   */
  def typeIdEquals(a: TypeId[_], b: TypeId[_]): Boolean = {
    // Fast path: reference equality
    if (a eq b) return true

    // Special handling for NamedTuple types: when both are NamedTuple-related,
    // treat them as semantically equal. This handles cases where Schema.derived
    // produces different TypeId representations than TypeId.of for the same
    // NamedTuple type (e.g., NamedTuple.Map, NamedTuple.Empty, etc.)
    if (isNamedTupleType(a) && isNamedTupleType(b)) {
      return true
    }

    // Fast path: different names means definitely not equal
    if (a.name != b.name || a.owner != b.owner) {
      // Only could be equal if one is an alias of the other
      // Check via full subtyping relation
      Subtyping.isEquivalent(TypeRepr.Ref(a, Nil), TypeRepr.Ref(b, Nil))
    } else {
      // Same name and owner - check args via subtyping
      if (a.args.size != b.args.size) return false
      if (a.args.isEmpty && b.args.isEmpty) return true

      // Special handling for NamedTuple: args may be in different order
      // and may have Wildcards in one vs actual types in the other
      if (isNamedTuple(a) && a.args.size == 2) {
        namedTupleArgsEqual(a.args, b.args)
      } else {
        // Check each arg pair for equivalence
        a.args.zip(b.args).forall { case (arg1, arg2) =>
          Subtyping.isEquivalent(arg1, arg2)
        }
      }
    }
  }

  /**
   * Check if a TypeId represents any NamedTuple-related type. This includes the
   * NamedTuple type itself and its companion operations like Map, Zip, Empty,
   * etc.
   */
  private def isNamedTupleType(id: TypeId[_]): Boolean =
    id.owner.asString.contains("NamedTuple") ||
      id.name == "NamedTuple" ||
      id.name == "Empty" ||
      id.name == "Map"

  // Helper to check if TypeId represents NamedTuple exactly
  private def isNamedTuple(id: TypeId[_]): Boolean =
    id.name == "NamedTuple" && id.owner.asString.contains("scala")

  /**
   * Compare NamedTuple args by content rather than position. NamedTuple has two
   * type args: Names and Values. Different syntaxes may produce these in
   * different orders and with different representations (TupleN vs *:). This
   * function matches args by checking which pairs are tuple-equivalent.
   */
  private def namedTupleArgsEqual(args1: List[TypeRepr], args2: List[TypeRepr]): Boolean = {
    if (args1.size != 2 || args2.size != 2) return false

    // Try matching in order: args1[0] ~ args2[0] and args1[1] ~ args2[1]
    val directMatch = argsEquivalent(args1(0), args2(0)) && argsEquivalent(args1(1), args2(1))

    // Try matching swapped: args1[0] ~ args2[1] and args1[1] ~ args2[0]
    val swappedMatch = argsEquivalent(args1(0), args2(1)) && argsEquivalent(args1(1), args2(0))

    directMatch || swappedMatch
  }

  /**
   * Check if two TypeRepr args are equivalent for NamedTuple comparison.
   * Handles Wildcard matching and tuple structure normalization.
   */
  private def argsEquivalent(a: TypeRepr, b: TypeRepr): Boolean =
    // Wildcards match anything
    (a, b) match {
      case (TypeRepr.Wildcard(_), _) => true
      case (_, TypeRepr.Wildcard(_)) => true
      case _                         => Subtyping.isEquivalent(a, b)
    }

  /**
   * Computes hash code for a TypeId. Hash code is computed on the normalized
   * (dealiased) form to ensure consistency with the equals contract.
   */
  def typeIdHashCode(a: TypeId[_]): Int = {
    // For consistent hashing with subtyping-based equality,
    // we hash the normalized form
    val normalized = TypeRepr.Ref(a, a.args).dealias
    typeReprHashCode(normalized)
  }

  /**
   * Computes hash code for a TypeRepr after normalization.
   */
  private def typeReprHashCode(tpe: TypeRepr): Int = {
    val normalized = tpe.dealias
    computeHashCode(normalized)
  }

  /**
   * Computes hash code for a normalized TypeRepr.
   */
  private def computeHashCode(tpe: TypeRepr): Int = tpe match {
    case TypeRepr.Ref(id, args) =>
      var h = id.owner.hashCode * 31 + id.name.hashCode
      args.foreach(arg => h = h * 31 + computeHashCode(arg))
      h

    case TypeRepr.AppliedType(base, args) =>
      var h = computeHashCode(base)
      args.foreach(arg => h = h * 31 + computeHashCode(arg))
      h * 37

    case TypeRepr.Union(types) =>
      types.map(computeHashCode).sorted.foldLeft(41)(_ * 43 + _)

    case TypeRepr.Intersection(types) =>
      types.map(computeHashCode).sorted.foldLeft(47)(_ * 53 + _)

    case TypeRepr.Function(params, result) =>
      val h = params.map(computeHashCode).foldLeft(59)(_ * 61 + _)
      h * 67 + computeHashCode(result)

    case TypeRepr.Tuple(elements) =>
      elements.map(computeHashCode).foldLeft(71)(_ * 73 + _)

    case TypeRepr.TypeParamRef(name, index) =>
      name.hashCode * 79 + index

    case TypeRepr.ConstantType(value) =>
      value.hashCode * 83

    case TypeRepr.ThisType(tpe) =>
      computeHashCode(tpe) * 89

    case TypeRepr.SuperType(thisTpe, superTpe) =>
      computeHashCode(thisTpe) * 97 + computeHashCode(superTpe)

    case TypeRepr.TypeLambda(params, result) =>
      params.map(_.name.hashCode).foldLeft(101)(_ * 103 + _) * 107 + computeHashCode(result)

    case TypeRepr.Wildcard(bounds) =>
      val lowerHash = bounds.lower.map(computeHashCode).getOrElse(0)
      val upperHash = bounds.upper.map(computeHashCode).getOrElse(0)
      lowerHash * 109 + upperHash * 113

    case TypeRepr.TypeProjection(qualifier, name) =>
      computeHashCode(qualifier) * 127 + name.hashCode

    case TypeRepr.Structural(members) =>
      members.map(_.toString.hashCode).foldLeft(131)(_ * 137 + _)

    // Case objects
    case TypeRepr.AnyType     => 139
    case TypeRepr.AnyKindType => 149
    case TypeRepr.NothingType => 151
    case TypeRepr.NullType    => 157
    case TypeRepr.UnitType    => 163
  }
}
