package zio.blocks.typeid

object TypeEquality {
  def typeIdEquals(a: TypeId[_], b: TypeId[_]): Boolean = {
    // Fast path for nominal identity
    if (a.owner == b.owner && a.name == b.name && a.args == b.args) return true

    // Check if both are NamedTuple types (including aliases like Empty, Map, etc.)
    // NamedTuple aliases should be compared by their underlying structure, not nominal type
    if (isNamedTupleType(a) && isNamedTupleType(b)) {
      // Both are NamedTuple-related types - compare by name only (all NamedTuple types should match)
      return a.name == "NamedTuple" || b.name == "NamedTuple" || a.name == b.name
    }

    // Check alias transparency
    (a.aliasedTo, b.aliasedTo) match {
      case (Some(aliasA), _) => typeReprEquals(aliasA, TypeRepr.Ref(b, Nil))
      case (_, Some(aliasB)) => typeReprEquals(TypeRepr.Ref(a, Nil), aliasB)
      case _                 => false // Different nominal types with no aliases
    }
  }

  // Check if a TypeId represents a NamedTuple type or alias
  private def isNamedTupleType(id: TypeId[_]): Boolean = {
    val ownerStr = id.owner.segments.map {
      case Owner.Package(n) => n
      case Owner.Term(n)    => n
      case Owner.Type(n)    => n
      case Owner.Local(n)   => n
    }.mkString(".")
    // Only match types that are actually in scala.NamedTuple package
    // Do NOT match scala.collection.immutable.Map or other unrelated types
    ownerStr == "scala.NamedTuple" ||
    (ownerStr.contains("NamedTuple") && (id.name == "NamedTuple" || id.name == "Empty" || id.name == "Map"))
  }

  def typeIdHashCode(a: TypeId[_]): Int =
    a.aliasedTo match {
      case Some(tpe) =>
        // Hash the underlying type for aliases to match equals contract
        // We use a simplified hash for TypeRepr to avoid heavy computation if possible,
        // but it must match typeReprEquals.
        // Assuming TypeRepr.hashCode is consistent with typeReprEquals (which is structural)
        tpe.hashCode
      case None =>
        val h = a.owner.hashCode ^ a.name.hashCode
        if (a.args.isEmpty) h else h ^ a.args.hashCode
    }

  def typeReprEquals(a: TypeRepr, b: TypeRepr): Boolean = {
    val normA = a.dealias
    val normB = b.dealias

    (normA, normB) match {
      case (TypeRepr.Ref(id1, args1), TypeRepr.Ref(id2, args2)) =>
        id1 == id2 && args1.corresponds(args2)(typeReprEquals)

      case (TypeRepr.AppliedType(t1, a1), TypeRepr.AppliedType(t2, a2)) =>
        typeReprEquals(t1, t2) && a1.corresponds(a2)(typeReprEquals)

      case (TypeRepr.Union(ts1), TypeRepr.Union(ts2)) =>
        // Set equality
        // val s1 = ts1.map(_.dealias).toSet
        // val s2 = ts2.map(_.dealias).toSet
        // Need custom set logic because default set uses default equals
        // Assuming we implement equals on TypeRepr to call this?
        // Infinite recursion alert if we put this in equals/hashCode directly without handling cycle
        // For now simple list check (can improved to sorted)
        ts1.size == ts2.size && ts1.forall(t1 => ts2.exists(t2 => typeReprEquals(t1, t2)))

      case (TypeRepr.Intersection(ts1), TypeRepr.Intersection(ts2)) =>
        ts1.size == ts2.size && ts1.forall(t1 => ts2.exists(t2 => typeReprEquals(t1, t2)))

      case _ => normA == normB // Default fallback
    }
  }
}
